package de.panbytes.dexter.core.model;

import static com.google.common.base.Preconditions.checkNotNull;

import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.context.GeneralSettings;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.model.classification.ClassificationModel;
import de.panbytes.dexter.core.model.visualization.VisualizationModel;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DexterModel {

    private static final Logger log = LoggerFactory.getLogger(DexterModel.class);

    private final Subject<Boolean> dimReductionEnabled = BehaviorSubject.createDefault(true);

    private final RxField<Optional<DataEntity>> currentInspectionEntity = RxField.initiallyEmpty();

    private final VisualizationModel visualizationModel;
    private final ClassificationModel classificationModel;
    private final ActiveLearningModel activeLearningModel;

    private final FilterManager<DomainDataEntity> filterManager;

    public Observable<List<DomainDataEntity>> getFilteredDomainData() {
        return filteredDomainData;
    }

    private final Observable<List<DomainDataEntity>> filteredDomainData;

    public DexterModel(DomainAdapter domainAdapter, AppContext appContext) {

        checkNotNull(domainAdapter, "DomainAdapter may not be null!");
        checkNotNull(appContext, "AppContext may not be null!");

        GeneralSettings settings = appContext.getSettingsRegistry().getGeneralSettings();

        // TODO work in progress
        this.filterManager = new FilterManager<>(domainAdapter.getDomainData());
        filteredDomainData = this.filterManager.getOutput().debounce(10, TimeUnit.MILLISECONDS);
        filteredDomainData.subscribe(filterResults -> log.trace("FilterResults: " + filterResults));

        this.visualizationModel = new VisualizationModel(filteredDomainData, settings, getDimReductionEnabled());

        // decide whether filtered data is used for classification
        Observable<List<DomainDataEntity>> dataForClassification = settings.getClassificationOnFilteredData()
                                                                           .toObservable()
                                                                           .switchMap(filteredOnly -> filteredOnly ? filteredDomainData
                                                                               : domainAdapter.getDomainData());
        this.classificationModel = new ClassificationModel(dataForClassification, appContext);
        this.activeLearningModel = new ActiveLearningModel(this.classificationModel, appContext);
    }

    @Deprecated // TODO: pause cpu-intensive models by disposing (or switch-mapping)...?
    public Subject<Boolean> getDimReductionEnabled() {
        return dimReductionEnabled;
    }

    @Deprecated // TODO: multiple simultaneous inspections...?! => open inspection views...?
    public RxFieldReadOnly<Optional<DataEntity>> getCurrentInspectionEntity() {
        return this.currentInspectionEntity.toReadOnlyView();
    }

    @Deprecated // TODO: multiple simultaneous inspections...?! => open inspection views...?
    public void setCurrentInspectionEntity(DataEntity inspectionEntity) {
        this.currentInspectionEntity.setValue(Optional.ofNullable(inspectionEntity));
    }

    public VisualizationModel getVisualizationModel() {
        return visualizationModel;
    }

    public ClassificationModel getClassificationModel() {
        return classificationModel;
    }

    public ActiveLearningModel getActiveLearningModel() {
        return activeLearningModel;
    }

    public FilterManager<DomainDataEntity> getFilterManager() {
        return filterManager;
    }

}
