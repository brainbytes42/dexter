package de.panbytes.dexter.core.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import de.panbytes.dexter.core.AppContext;
import de.panbytes.dexter.core.ClassLabel;
import de.panbytes.dexter.core.DataSourceActions;
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
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DomainAdapter extends Named.BaseImpl implements Named {

    private static final Logger log = LoggerFactory.getLogger(DomainAdapter.class);

    private final DataSourceActions dataSourceActions = new DataSourceActions();
    private final RxFieldCollection<List<FilterModule>, FilterModule> filterModules = RxFieldCollection.withInitialValue(new ArrayList<>(),
                                                                                                                         ArrayList::new);
    private final AppContext appContext;
    private final RxField<Optional<FeatureSpace>> featureSpace;
    private final Observable<Optional<DataSource>> rootDataSource;
    private final Observable<List<DomainDataEntity>> filteredDomainData;
    private final Observable<List<DomainDataEntity>> domainData;

    private final CompositeDisposable disposable = new CompositeDisposable();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public DomainAdapter(String name, String description, AppContext appContext) {
        super(name, description);

        this.appContext = checkNotNull(appContext, "AppContext may not be null!");


        /*
        Update rootDataSource for new FeatureSpaces (root is empty, if featurespace is empty!)
         */
        this.featureSpace = RxField.initiallyEmpty();
        this.rootDataSource = this.featureSpace.toObservable()
                                               .map(featureSpaceOptional -> featureSpaceOptional.map(
                                                       featureSpace -> new DataSource("Root", "", featureSpace)))
                                               .replay(1)
                                               .autoConnect(0);

        // logging...
        this.featureSpace.toObservable()
                         .subscribe(featureSpace -> log.info("FeatureSpace: " + featureSpace));
        this.rootDataSource.subscribe(dataSource -> log.info("RootDataSource: " + dataSource));

        /*
        Filter for Rejected Data
         */
        RxFieldReadOnly<String> rejectedClassLabel = this.appContext.getSettingsRegistry()
                                                                    .getDomainSettings()
                                                                    .rejectedClassLabel();
        RejectLabelFilterModule rejectedFilter = new RejectLabelFilterModule("Filter Rejected",
            "Exclude rejected Entities", rejectedClassLabel.getValue());
        rejectedClassLabel.toObservable().subscribe(rejectedFilter::setRejectedLabel);
        addDataFilter(rejectedFilter);


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

    public Observable<Set<ClassLabel>> getAllClassLabels() {
        return getDomainData().switchMap(
            entities -> RxJavaUtils.combineLatest(entities, t -> t.getClassLabel().toObservable(),
                labelOpts -> labelOpts.stream()
                                      .filter(Optional::isPresent)
                                      .map(Optional::get)
                                      .collect(Collectors.toSet())))
                              .distinctUntilChanged()
                              .replay(1)
                              .refCount();
    }

    @Deprecated
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

        protected final void notifyForUpdate(){
            this.updates.onNext(this);
        }
    }

    private static class RejectLabelFilterModule extends FilterModule {

        private String rejectedLabel;

        RejectLabelFilterModule(String name, String description, String rejectedLabel) {
            super(name, description);
            this.rejectedLabel = checkNotNull(rejectedLabel);
        }

        public void setRejectedLabel(String rejectedLabel) {
            this.rejectedLabel = checkNotNull(rejectedLabel);
            notifyForUpdate();
        }

        @Override
        public boolean accept(DomainDataEntity entity) {
            return entity.getClassLabel()
                         .getValue()
                         .map(lbl -> !lbl.getLabel().equals(this.rejectedLabel))
                         .orElse(true);
        }
    }
}
