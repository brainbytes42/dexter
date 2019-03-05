package de.panbytes.dexter.core.domain;

import de.panbytes.dexter.core.*;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DataNode;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldCollection;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import de.panbytes.dexter.util.Named;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import io.reactivex.functions.BiFunction;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import javafx.scene.Node;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class DomainAdapter extends Named.BaseImpl implements Named {

    private final DataSourceActions dataSourceActions = new DataSourceActions();
    private final RxFieldCollection<List<FilterModule>, FilterModule> filterModules = RxFieldCollection.withInitialValue(new ArrayList<>(),
                                                                                                                         ArrayList::new);
    private final Observable<Optional<DataSource>> rootDataSource;
    private final Observable<List<DomainDataEntity>> filteredDomainData;
    private final RxField<Optional<FeatureSpace>> featureSpace = RxField.initiallyEmpty();
    private final AppContext appContext;
    private final Observable<List<DomainDataEntity>> domainData;

    public DomainAdapter(String name, String description, AppContext appContext) {
        super(name, description);

        this.appContext = checkNotNull(appContext, "AppContext may not be null!");


        /*
        Update rootDataDource for new FeatureSpaces
         */
        this.rootDataSource = this.featureSpace.toObservable()
                                               .map(featureSpaceOptional -> featureSpaceOptional.map(
                                                       featureSpace -> new DataSource("Root", "", featureSpace)))
                                               .replay(1)
                                               .autoConnect(0);

        this.rootDataSource.subscribe(dataSource -> System.out.println("DomainAdapter / RootDataSource: " + dataSource)); //TODO remove
        this.featureSpace.toObservable()
                         .subscribe(featureSpace -> System.out.println("DomainAdapter / FeatureSpace: " + featureSpace)); //TODO remove

        /*
        Default Filter for Rejected Data
         */
        this.appContext.getSettingsRegistry()
                       .getDomainSettings()
                       .rejectedClassLabel()
                       .toObservable()
                       .map(rejectedLabel -> new FilterModule("Filter Rejected", "Exclude rejected Entities") {
                           @Override
                           public boolean accept(DomainDataEntity entity) {
                               return entity.getClassLabel().getValue().map(lbl -> !lbl.getLabel().equals(rejectedLabel)).orElse(true);
                           }
                       })
                       .scan(Collections.<FilterModule>emptyList(),
                             (prev, curr) -> Arrays.asList(prev.isEmpty() ? null : prev.get(1), curr))
                       .skip(1)
                       .subscribe(filterUpdate -> replaceDataFilter(filterUpdate.get(0), filterUpdate.get(1)));


        /*
        Assemble filteredDomainData: ( domainData + updatingFiltersList ) <- domainDataFilterFunction
         */
        // active domain data
        this.domainData = this.rootDataSource.switchMap(dataSourceOptional -> dataSourceOptional.map(DataSource::getSubtreeDataEntities)
                                                                                                .orElse(Observable.just(
                                                                                                        Collections.emptyList()))
                                                                                                .switchMap(
                                                                                                        entities -> RxJavaUtils.combineLatest(
                                                                                                                entities.stream()
                                                                                                                        .map(entity -> entity
                                                                                                                                .getEnabledState()
                                                                                                                                .toObservable()
                                                                                                                                .map(state ->
                                                                                                                                             state.equals(
                                                                                                                                                     DataNode.EnabledState.ACTIVE)
                                                                                                                                             ? Optional
                                                                                                                                                     .of(entity)
                                                                                                                                             : Optional.<DomainDataEntity>empty()))
                                                                                                                        .collect(
                                                                                                                                Collectors.toList()),
                                                                                                                entityOpt -> entityOpt.stream()
                                                                                                                                      .filter(Optional::isPresent)
                                                                                                                                      .map(Optional::get)
                                                                                                                                      .collect(
                                                                                                                                              Collectors
                                                                                                                                                      .toList()))))
                                             .observeOn(Schedulers.io())
                                             .replay(1)
                                             .autoConnect(0)
                                             .distinctUntilChanged();

        this.domainData.subscribe(entities -> System.out.println("DomainAdapter / DomainData: " + entities.size())); // TODO remove

        Observable<List<FilterModule>> updatingFiltersList = this.filterModules.toObservable()
                                                                               .observeOn(Schedulers.io())
                                                                               .debounce(100, TimeUnit.MILLISECONDS)
                                                                               .switchMap(filtersList -> {
                                                                                   return filtersList.isEmpty()
                                                                                          ? Observable.just(Collections.emptyList())
                                                                                          : Observable.combineLatest(filtersList.stream()
                                                                                                                                .map(filter -> filter
                                                                                                                                        .getUpdates()
                                                                                                                                        .startWith(
                                                                                                                                                filter))
                                                                                                                                .collect(
                                                                                                                                        Collectors
                                                                                                                                                .toList()),
                                                                                                                     FilterObjects -> Arrays
                                                                                                                             .stream(FilterObjects)
                                                                                                                             .map(FilterModule.class::cast)
                                                                                                                             .collect(
                                                                                                                                     Collectors
                                                                                                                                             .toList()));
                                                                               });

        BiFunction<List<DomainDataEntity>, List<FilterModule>, List<DomainDataEntity>> domainDataFilterFunction = (dataEntities, filters) -> {
            List<DomainDataEntity> dataEntities2 = new ArrayList<>(dataEntities);
            List<FilterModule> filters2 = new ArrayList<>(filters);
            try {
                final Stream<DomainDataEntity> domainDataEntityStream1 = dataEntities2.parallelStream()
                                                                                      .filter(entity -> filters2.stream()
                                                                                                                .filter(FilterModule::isEnabled)
                                                                                                                .allMatch(filter -> {
                                                                                                                    try {
                                                                                                                        return filter.accept(
                                                                                                                                entity);
                                                                                                                    } catch (Exception e) {
                                                                                                                        System.err.println(
                                                                                                                                "EXCEPTION in FILTER: " + e);
                                                                                                                        return false; // TODO
                                                                                                                    }
                                                                                                                }));
                return domainDataEntityStream1.collect(Collectors.toList());
            } catch (Exception e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        };

        this.filteredDomainData = Observable.combineLatest(domainData, updatingFiltersList, domainDataFilterFunction)
                                            .distinctUntilChanged()
                                            .replay(1)
                                            .autoConnect(0);

        this.filteredDomainData.subscribe(
                entities -> System.out.println("DomainAdapter / FilteredDomainData: " + entities.size())); //TODO remove


        // changed labels -> inspection history
        this.domainData.switchMap(entities -> Observable.fromIterable(entities)
                                                        .flatMap(entity -> entity.getClassLabel().toObservable().skip(1).map(__ -> entity)))
                       .subscribe(changedLabelEntity -> this.appContext.getInspectionHistory().markInspected(changedLabelEntity));

    }

    private static Observable<List<DomainDataEntity>> filterLabeled(boolean labeled, List<DomainDataEntity> entities) {
        return RxJavaUtils.combineLatest(entities.stream()
                                                 .map(entity -> entity.getClassLabel()
                                                                      .toObservable()
                                                                      .map(labelOpt -> labelOpt.isPresent() == labeled
                                                                                       ? Optional.of(entity)
                                                                                       : Optional.<DomainDataEntity>empty()))
                                                 .collect(Collectors.toList()), entityOpt -> entityOpt.stream()
                                                                                                      .filter(Optional::isPresent)
                                                                                                      .map(Optional::get)
                                                                                                      .collect(Collectors.toList()));
    }

    protected AppContext getAppContext() {
        return this.appContext;
    }

    public final RxFieldReadOnly<List<FilterModule>> getFilterModules() {
        return this.filterModules.toReadOnlyView();
    }

    public final RxFieldReadOnly<Optional<FeatureSpace>> getFeatureSpace() {
        return featureSpace.toReadOnlyView();
    }

    public final void setFeatureSpace(FeatureSpace featureSpace) {
        this.featureSpace.setValue(Optional.ofNullable(featureSpace));
    }

    public final DataSourceActions getDataSourceActions() {
        return this.dataSourceActions;
    }

    public final Observable<Optional<DataSource>> getRootDataSource() {
        return this.rootDataSource.hide();
    }

    public void addDataFilter(FilterModule filter) {
        this.filterModules.add(filter);
    }

    public void removeDataFilter(FilterModule filter) {
        this.filterModules.remove(filter);
    }

    public void replaceDataFilter(FilterModule old, FilterModule update) {
        this.filterModules.replace(old, update);
    }

    /**
     * Return the currently active domain data (EnabledState == ACTIVE)
     *
     * @return
     */
    public Observable<List<DomainDataEntity>> getDomainData() {
        return this.domainData.hide();
    }

    public Observable<List<DomainDataEntity>> getDomainDataLabeled(boolean labeled) {
        return this.domainData.switchMap(entities -> filterLabeled(labeled, entities)).hide();
    }

    /**
     * Return the domain Data (see getDomainData), but filtered by FilterModules.
     *
     * @return
     */
    public Observable<List<DomainDataEntity>> getFilteredDomainData() {
        return this.filteredDomainData.hide();
    }

    public Observable<List<DomainDataEntity>> getFilteredDomainDataLabeled(boolean labeled) {
        return this.filteredDomainData.switchMap(entities -> filterLabeled(labeled, entities)).hide();
    }

    public Observable<List<Optional<ClassLabel>>> getClassLabels() {
        return getRootDataSource().switchMap(dataSourceOpt -> dataSourceOpt.map(dataSource -> {
            return dataSource.getSubtreeDataEntities()
                             .switchMap(entities -> RxJavaUtils.combineLatest(entities.stream()
                                                                                      .map(DataEntity::getClassLabel)
                                                                                      .map(RxFieldReadOnly::toObservable)
                                                                                      .collect(Collectors.toList()),
                                                                              optLabels -> optLabels.stream()
                                                                                                    .distinct()
                                                                                                    .sorted(Comparator.comparing(
                                                                                                            optional -> optional.map(
                                                                                                                    ClassLabel::getLabel)
                                                                                                                                .orElse("")
                                                                                                                                .toLowerCase()))
                                                                                                    .collect(Collectors.toList())));
        }).orElse(Observable.just(Collections.emptyList())));
    }

    public Optional<Node> getDomainInspectionView(DataEntity inspectionTarget) {
        return Optional.empty();
    }

    public static abstract class FilterModule extends BaseImpl implements Named {

        private final Subject<Boolean> enabled = BehaviorSubject.createDefault(true);

        private final Subject<FilterModule> updates = PublishSubject.create();

        protected FilterModule(String name, String description) {
            super(name, description);

            enabled().map(__ -> this).subscribe(this.updates);
        }

        public abstract boolean accept(DomainDataEntity entity);

        Observable<FilterModule> getUpdates() {
            return this.updates.hide();
        }

        Observable<Boolean> enabled() {
            return this.enabled.distinctUntilChanged().hide();
        }

        boolean isEnabled() {
            return this.enabled.blockingFirst();
        }
    }

}
