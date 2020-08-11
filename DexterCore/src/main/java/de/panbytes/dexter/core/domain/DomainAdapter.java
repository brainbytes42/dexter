package de.panbytes.dexter.core.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DataNode;
import de.panbytes.dexter.core.data.DataNode.EnabledState;
import de.panbytes.dexter.core.data.DataNode.Status;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.model.DexterModel;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import de.panbytes.dexter.util.Named;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DomainAdapter extends Named.BaseImpl implements Named {

    private static final Logger log = LoggerFactory.getLogger(DomainAdapter.class);

    private final DataSourceActions dataSourceActions = new DataSourceActions();
    private final AppContext appContext;
    private final RxField<Optional<FeatureSpace>> featureSpace;
    private final Observable<Optional<DataSource>> rootDataSource;
    private final Observable<List<DomainDataEntity>> domainData;

    private final CompositeDisposable disposable = new CompositeDisposable();

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
        this.disposable.addAll( //
            this.featureSpace.toObservable().subscribe(featureSpace -> log.info("FeatureSpace: " + featureSpace)), //
            this.rootDataSource.subscribe(dataSource -> log.info("RootDataSource: " + dataSource)) //
        );


        /*
        Assemble filteredDomainData: ( getDataSet + updatingFiltersList ) <- domainDataFilterFunction
         */
        // active domain data
        Observable<List<DomainDataEntity>> oldDomainData = this.rootDataSource.switchMap(
            dataSourceOptional -> dataSourceOptional.map(DataSource::getSubtreeDataEntities)
                                                    .orElse(Observable.just(Collections.emptyList()))
                                                    .switchMap(entities -> RxJavaUtils.combineLatest(entities, entity -> entity.getEnabledState()
                                                                                                                               .toObservable()
                                                                                                                               .map(state -> state.equals(
                                                                                                                                   EnabledState.ACTIVE)
                                                                                                                                   ? Optional.of(entity)
                                                                                                                                   : Optional.<DomainDataEntity>empty())))
                                                    .map(entityOpt -> entityOpt.stream()
                                                                               .filter(Optional::isPresent)
                                                                               .map(Optional::get)
                                                                               .collect(Collectors.toList())))
                                                                           .observeOn(Schedulers.io())
                                                                           .replay(1)
                                                                           .autoConnect(0)
                                                                           .distinctUntilChanged();
        this.domainData = this.rootDataSource.switchMap(dataSourceOpt -> dataSourceOpt.map(DataSource::getSubtreeDataEntities)
                                                                                      .map(entities -> entities.compose(
                                                                                          RxJavaUtils.deepFilter(DataNode::getStatus,
                                                                                              status -> status == Status.ACTIVE)))
                                                                                      .orElse(Observable.just(Collections.emptyList())))
                                             .distinctUntilChanged()
                                             .doOnNext(entities -> log.debug("DomainAdapter has {} DataEntities.", entities.size()))
                                             .replay(1)
                                             .refCount();

        // changed labels -> inspection history
        this.domainData.switchMap(entities -> Observable.fromIterable(entities)
                                                        .flatMap(entity -> entity.getClassLabel().toObservable().skip(1).map(__ -> entity)))
                       .subscribe(changedLabelEntity -> this.appContext.getInspectionHistory().markInspected(changedLabelEntity));

    }


    private static Observable<List<DomainDataEntity>> filterLabeled(boolean labeled, List<DomainDataEntity> entities) {
        return RxJavaUtils.combineLatest(entities, entity -> entity.getClassLabel()
                                                                   .toObservable()
                                                                   .map(labelOpt -> labelOpt.isPresent() == labeled ? Optional.of(entity)
                                                                       : Optional.<DomainDataEntity>empty()))
                          .map(entityOpt -> entityOpt.stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()));
    }

    public AppContext getAppContext() {
        return this.appContext;
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

    public Observable<Set<ClassLabel>> getAllClassLabels() {
        return getDomainData().switchMap(
            entities -> RxJavaUtils.combineLatest(entities, t -> t.getClassLabel().toObservable()).map(
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
                             .switchMap(entities -> RxJavaUtils.combineLatest(entities, e->e.getClassLabel().toObservable()).map(
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

    public Observable<Optional<Node>> getDomainInspectionView(DataEntity inspectionTarget) {
        return Observable.just(Optional.empty());
    }

    /**
     * Will be called once after DexterCore is initialized to provide the model to the DomainAdapter if necessary.
     * @param dexterModel
     */
    public void initDexterModel(DexterModel dexterModel) {
        // do nothing by default - only relevant if extending class needs the model
    }

    @Deprecated
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
