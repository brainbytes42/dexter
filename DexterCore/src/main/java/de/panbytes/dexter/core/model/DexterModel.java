package de.panbytes.dexter.core.model;

import de.panbytes.dexter.core.AppContext;
import de.panbytes.dexter.core.ClassLabel;
import de.panbytes.dexter.core.GeneralSettings;
import de.panbytes.dexter.core.activelearning.ActiveLearning;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.data.MappedDataEntity;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.core.model.classification.ClassificationModel;
import de.panbytes.dexter.lib.dimension.DimensionMapping;
import de.panbytes.dexter.lib.dimension.StochasticNeigborEmbedding;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkNotNull;

public class DexterModel {

    private static final Logger log = LoggerFactory.getLogger(DexterModel.class);


    private final DomainAdapter domainAdapter;
    private final AppContext appContext;
    private final GeneralSettings settings;

    private final Observable<Map<DataEntity, MappedDataEntity>> lowDimData;
    private final Observable<List<ClassLabel>> occurringLabels;

    private final Subject<Boolean> dimReductionEnabled = BehaviorSubject.createDefault(true);

    private final RxField<Optional<DataEntity>> currentInspectionEntity = RxField.initiallyEmpty();

    private final ClassificationModel classificationModel;
    private final ActiveLearning activeLearning;

    public DexterModel(DomainAdapter domainAdapter, AppContext appContext) {

        this.domainAdapter = checkNotNull(domainAdapter, "DomainAdapter may not be null!");
        this.appContext = checkNotNull(appContext, "AppContext may not be null!");
        this.settings = this.appContext.getSettingsRegistry().getGeneralSettings();

        this.lowDimData = initLowDimData(this.domainAdapter, this.settings, getDimReductionEnabled());

        this.lowDimData.subscribe(data -> System.out.println("DexterModel / LowDimData: " + data)); //TODO remove


        this.occurringLabels = initOccurringLabels(domainAdapter);

        this.classificationModel = new ClassificationModel(domainAdapter, appContext);
        this.activeLearning = new ActiveLearning(this.classificationModel, appContext);

    }

    private static Observable<List<ClassLabel>> initOccurringLabels(DomainAdapter domainAdapter) {
        return domainAdapter.getFilteredDomainData().<List<ClassLabel>>switchMap(entities -> entities.isEmpty()
                                                                                             ? Observable.just(Collections.emptyList())
                                                                                             : Observable.combineLatest(entities.stream()
                                                                                                                                .map(DataEntity::getClassLabel)
                                                                                                                                .map(RxFieldReadOnly::toObservable)
                                                                                                                                .collect(
                                                                                                                                        Collectors
                                                                                                                                                .toList()),
                                                                                                                        objects -> Arrays.stream(
                                                                                                                                objects)
                                                                                                                                         .map(Optional.class::cast)
                                                                                                                                         .filter(Optional::isPresent)
                                                                                                                                         .map(Optional::get)
                                                                                                                                         .map(ClassLabel.class::cast)
                                                                                                                                         .distinct()
                                                                                                                                         .sorted((label1, label2) -> label1
                                                                                                                                                 .getLabel()
                                                                                                                                                 .compareToIgnoreCase(
                                                                                                                                                         label2.getLabel()))
                                                                                                                                         .collect(
                                                                                                                                                 Collectors
                                                                                                                                                         .toList())))
                .distinctUntilChanged()
                .replay(1)
                .autoConnect(0);
    }

    private Observable<Map<DataEntity, MappedDataEntity>> initLowDimData(DomainAdapter domainAdapter,
                                                                         GeneralSettings settings,
                                                                         Subject<Boolean> dimReductionEnabled) {
        return dimReductionEnabled.distinctUntilChanged().switchMap(enabled -> {
            if (enabled) {
                return domainAdapter.getFilteredDomainData().debounce(100, TimeUnit.MILLISECONDS).switchMap(domainDataEntities -> {
                    return Observable.<Map<DataEntity, MappedDataEntity>>create(emitter -> {

                        System.out.println("DexterModel / DomainData: " + domainDataEntities.size());

                        if (domainDataEntities.size() > 0) {

                            Map<DataEntity, MappedDataEntity> previousMapping = new HashMap<>();
                            try {
                                if (this.lowDimData != null) {
                                    previousMapping.putAll(
                                            this.lowDimData.timeout(100, TimeUnit.MILLISECONDS).blockingFirst(Collections.emptyMap()));
                                }
                            } catch (Exception __) {
                                __.printStackTrace();
                                // ignored, no previous mapping available.
                            }
                            previousMapping.entrySet().removeIf(entry -> !domainDataEntities.contains(entry.getKey()));


                            List<DomainDataEntity> allEntities = new ArrayList<>(domainDataEntities);
                            double[][] dataMatrix = allEntities.stream()
                                                               .map(DataEntity::getCoordinates)
                                                               .map(RxFieldReadOnly::getValue)
                                                               .toArray(double[][]::new);
                            double[][] initialSolutionMatrix = allEntities.stream()
                                                                          .map(entity -> previousMapping.containsKey(entity)
                                                                                         ? previousMapping.get(entity)
                                                                                                          .getCoordinates()
                                                                                                          .getValue()
                                                                                         : previousMapping.entrySet()
                                                                                                          .stream()
                                                                                                          .sorted((e1, e2) -> {
                                                                                                              double d1 = new EuclideanDistance()
                                                                                                                      .compute(
                                                                                                                              entity.getCoordinates()
                                                                                                                                    .getValue(),
                                                                                                                              e1.getKey()
                                                                                                                                .getCoordinates()
                                                                                                                                .getValue());
                                                                                                              double d2 = new EuclideanDistance()
                                                                                                                      .compute(
                                                                                                                              entity.getCoordinates()
                                                                                                                                    .getValue(),
                                                                                                                              e2.getKey()
                                                                                                                                .getCoordinates()
                                                                                                                                .getValue());
                                                                                                              return (int) Math.signum(
                                                                                                                      d1 - d2);
                                                                                                          })
                                                                                                          .findFirst()
                                                                                                          .map(e -> e.getValue()
                                                                                                                     .getCoordinates()
                                                                                                                     .getValue())
                                                                                                          .orElse(new double[]{new GaussianRandomGenerator(
                                                                                                                  new JDKRandomGenerator()).nextNormalizedDouble() * 0.0001, new GaussianRandomGenerator(
                                                                                                                  new JDKRandomGenerator()).nextNormalizedDouble() * 0.0001}))
                                                                          .toArray(double[][]::new);

                            try {

                                StochasticNeigborEmbedding.SimpleTSneContext context = new StochasticNeigborEmbedding.SimpleTSneContext();
                                context.setInitialSolution(initialSolutionMatrix);
                                context.setPerplexity(settings.getPerplexity().getValue());

                                DimensionMapping.MappingProcessor mappingProcessor = new StochasticNeigborEmbedding().mapAsync(dataMatrix,
                                                                                                                               context);
                                System.out.println("MAPPING! @ " + Thread.currentThread()); //TODO remove

                                FeatureSpace featureSpace = new FeatureSpace("2D-Mapping", "Mapping to two dimensions", Arrays.asList(
                                        new FeatureSpace.Feature("x1", "first of two dimensions"),
                                        new FeatureSpace.Feature("x2", "second of two dimensions")));

                                Observable.<double[][]>create(listenerEmitter -> mappingProcessor.addIntermediateResultListener(
                                        (newIntermediateResult, source) -> listenerEmitter.onNext(newIntermediateResult))).forEachWhile(
                                        newIntermediateResult -> {
                                            emitter.onNext(IntStream.range(0, newIntermediateResult.length)
                                                                    .mapToObj(i -> new MappedDataEntity(newIntermediateResult[i],
                                                                                                        featureSpace, allEntities.get(i)))
                                                                    .collect(Collectors.toMap(MappedDataEntity::getMappedDataEntity,
                                                                                              Function.identity())));
                                            return !emitter.isDisposed() && !mappingProcessor.getCompletableFuture().isDone();
                                        });


                                double[][] mappedCoordinates = mappingProcessor.getResult();

                                emitter.onNext(IntStream.range(0, mappedCoordinates.length)
                                                        .mapToObj(i -> new MappedDataEntity(mappedCoordinates[i], featureSpace,
                                                                                            allEntities.get(i)))
                                                        .collect(Collectors.toMap(MappedDataEntity::getMappedDataEntity,
                                                                                  Function.identity())));
                            } catch (Exception e) {
                                log.warn("Could not map coordinates!", e);
                                emitter.onNext(Collections.emptyMap());
                            }
                        } else {
                            emitter.onNext(Collections.emptyMap());
                        }
                        emitter.onComplete();
                    });

                });
            } else {
                return Observable.empty();
            }
        }).distinctUntilChanged().replay(1).autoConnect(0);
    }


    public Observable<Map<DataEntity, MappedDataEntity>> getLowDimData() {
        return lowDimData;
    }

    public Observable<List<ClassLabel>> getOccurringLabels() {
        return occurringLabels;
    }

    public Subject<Boolean> getDimReductionEnabled() {
        return dimReductionEnabled;
    }

    public RxFieldReadOnly<Optional<DataEntity>> getCurrentInspectionEntity() {
        return this.currentInspectionEntity.toReadOnlyView();
    }

    public void setCurrentInspectionEntity(DataEntity inspectionEntity) {
        this.currentInspectionEntity.setValue(Optional.ofNullable(inspectionEntity));
    }

    public ActiveLearning getActiveLearning() {
        return activeLearning;
    }

    public ClassificationModel getClassificationModel() {
        return classificationModel;
    }

    //TODO
    @Deprecated
    class SelectionModel {
        Collection<DomainDataEntity> selectedEntities;
    }

}
