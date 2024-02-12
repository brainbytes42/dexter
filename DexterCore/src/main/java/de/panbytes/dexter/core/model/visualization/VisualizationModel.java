package de.panbytes.dexter.core.model.visualization;

import com.google.common.collect.ImmutableMap;
import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.context.GeneralSettings;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DataNode;
import de.panbytes.dexter.core.data.MappedDataEntity;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.lib.dimension.DimensionMapping;
import de.panbytes.dexter.lib.dimension.StochasticNeigborEmbedding;
import de.panbytes.dexter.lib.dimension.UmapMapping;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.distance.Distance;
import smile.math.distance.MahalanobisDistance;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VisualizationModel {

    private static final Logger log = LoggerFactory.getLogger(VisualizationModel.class);

    private final Observable<Map<DataEntity, MappedDataEntity>> lowDimData;
    private final AppContext appContext;

    public VisualizationModel(Observable<? extends Collection<? extends DataEntity>> domainData, AppContext appContext,
                              Observable<Boolean> dimReductionEnabled) {
        this.appContext = appContext;

        GeneralSettings settings = appContext.getSettingsRegistry().getGeneralSettings();

        AtomicReference<Map<DataEntity, MappedDataEntity>> previousMappingReference = new AtomicReference<>();

        this.lowDimData = Observable.combineLatest(
                                            domainData.debounce(500, TimeUnit.MILLISECONDS),
                                            dimReductionEnabled.distinctUntilChanged().debounce(500, TimeUnit.MILLISECONDS),

                                            (domainDataEntities, enabled) -> {

                                                log.debug("trigger VisualizationModel...");

                                                if (!enabled) {

                                                    Map<DataEntity, MappedDataEntity> prevMapping = new HashMap<>(previousMappingReference.get());

                                                    if (prevMapping.keySet().containsAll(domainDataEntities)) {
                                                        prevMapping.keySet().retainAll(domainDataEntities);
                                                        return Observable.just(prevMapping);
                                                    } else {
                                                        return Observable.<Map<DataEntity, MappedDataEntity>>empty();
                                                    }

                                                } else {

                                                    AtomicReference<DimensionMapping.MappingProcessor> mappingProcessor = new AtomicReference<>();
                                                    // this Observable is for just *one* run and provides intermediate steps until the run finishes.
                                                    return Observable.<Map<DataEntity, MappedDataEntity>>create(
                                                            emitter -> {

                                                                log.debug("DexterModel / DomainData: {}", domainDataEntities.size());

                                                                if (domainDataEntities.size() > 0) {

                                                                    Map<DataEntity, MappedDataEntity> previousMapping = Optional.ofNullable(previousMappingReference.get())
                                                                                                                                .map(HashMap::new)
                                                                                                                                .orElse(new HashMap<>());
                                                                    previousMapping.entrySet()
                                                                                   .removeIf(entry -> !domainDataEntities.contains(entry.getKey()));

                                                                    List<DataEntity> allEntities = new ArrayList<>(domainDataEntities);
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
                                                                                                                                           .min((e1, e2) -> {
                                                                                                                                               double d1 = new EuclideanDistance().compute(
                                                                                                                                                       entity.getCoordinates()
                                                                                                                                                             .getValue(),
                                                                                                                                                       e1.getKey()
                                                                                                                                                         .getCoordinates()
                                                                                                                                                         .getValue());
                                                                                                                                               double d2 = new EuclideanDistance().compute(
                                                                                                                                                       entity.getCoordinates()
                                                                                                                                                             .getValue(),
                                                                                                                                                       e2.getKey()
                                                                                                                                                         .getCoordinates()
                                                                                                                                                         .getValue());
                                                                                                                                               return (int) Math.signum(d1 - d2);
                                                                                                                                           })
                                                                                                                                           .map(e -> e.getValue()
                                                                                                                                                      .getCoordinates()
                                                                                                                                                      .getValue())
                                                                                                                                           .orElse(new double[]{new GaussianRandomGenerator(
                                                                                                                                                   new JDKRandomGenerator()).nextNormalizedDouble() * 0.0001,
                                                                                                                                                   new GaussianRandomGenerator(
                                                                                                                                                           new JDKRandomGenerator()).nextNormalizedDouble()
                                                                                                                                                           * 0.0001}))
                                                                                                                  .toArray(double[][]::new);

                                                                    try {

                                                                        log.trace("Starting Dim-Mapping for Data {}x{}", dataMatrix.length, dataMatrix[0].length);

                                                                        boolean umap = false;
                                                                        if (umap) {

                                                                            Distance distance = null;
                                                                            boolean mahalanobis = false;
                                                                            if (mahalanobis) {
                                                                                double[][] cov = new Covariance(dataMatrix).getCovarianceMatrix()
                                                                                                                           .getData();
                                                                                distance = new MahalanobisDistance(cov);
                                                                                log.trace("Mahalanobis distance was created.");
                                                                            }
                                                                            mappingProcessor.set(new UmapMapping().mapAsync(dataMatrix, new UmapMapping.UmapContext(5, distance, 0.1, 1.0)));

                                                                        } else { // t-SNE
                                                                            StochasticNeigborEmbedding.SimpleTSneContext context = new StochasticNeigborEmbedding.SimpleTSneContext();
                                                                            context.setInitialSolution(initialSolutionMatrix);
                                                                            context.setPerplexity(settings.getPerplexity().getValue());

                                                                            mappingProcessor.set(new StochasticNeigborEmbedding().mapAsync(dataMatrix, context));

                                                                        }

                                                                        FeatureSpace featureSpace = new FeatureSpace("2D-Mapping",
                                                                                Arrays.asList(new FeatureSpace.Feature("x1"),
                                                                                        new FeatureSpace.Feature("x2")));

                                                                        Observable.<double[][]>create(listenerEmitter -> mappingProcessor.get()
                                                                                                                                         .addIntermediateResultListener(
                                                                                                                                                 (newIntermediateResult, source) -> listenerEmitter.onNext(newIntermediateResult)))
                                                                                  .forEachWhile(newIntermediateResult -> {
                                                                                      log.trace("New intermediate mapping result: l={}", newIntermediateResult.length);
                                                                                      emitter.onNext(IntStream.range(0, newIntermediateResult.length)
                                                                                                              .mapToObj(i -> new MappedDataEntity(newIntermediateResult[i], featureSpace, allEntities.get(i)))
                                                                                                              .collect(Collectors.toMap(MappedDataEntity::getMappedDataEntity, Function.identity())));
                                                                                      return !emitter.isDisposed() && !mappingProcessor.get()
                                                                                                                                       .getCompletableFuture()
                                                                                                                                       .isDone();
                                                                                  });

                                                                        double[][] mappedCoordinates = mappingProcessor.get().getResult();
                                                                        log.trace("Mapping done.");

                                                                        emitter.onNext(IntStream.range(0, mappedCoordinates.length)
                                                                                                .mapToObj(i -> new MappedDataEntity(mappedCoordinates[i], featureSpace, allEntities.get(i)))
                                                                                                .collect(Collectors.toMap(MappedDataEntity::getMappedDataEntity, Function.identity())));
                                                                    } catch (Exception e) {
                                                                        log.warn("Could not map coordinates!", e);
                                                                        appContext.getErrorHandler().onNext(new AppContext.ErrorContext(this, e));
                                                                        emitter.onNext(Collections.emptyMap());
                                                                    }
                                                                } else {
                                                                    log.debug("Updating Dim.Reduction is disabled.");
                                                                    emitter.onNext(Collections.emptyMap());
                                                                }
                                                                emitter.onComplete();
                                                            }).subscribeOn(Schedulers.io()).doOnDispose(() -> {
                                                        log.debug("DISPOSE_single");
                                                        Optional.ofNullable(mappingProcessor.get())
                                                                .ifPresent(DimensionMapping.MappingProcessor::cancel);
                                                    }).unsubscribeOn(Schedulers.io());

                                                }
                                            })
                                    .flatMap(Observable::distinctUntilChanged)
                                    .distinctUntilChanged()
                                    .doOnNext(newValue -> {
                                        if (dimReductionEnabled.blockingFirst()) {
                                            previousMappingReference.set(newValue);
                                        }
                                    })
                                    .map(Map::entrySet)
                                    .compose(RxJavaUtils.deepFilter(dataEntityMappedDataEntityEntry -> dataEntityMappedDataEntityEntry.getKey()
                                                                                                                                      .getStatus(), status -> status == DataNode.Status.ACTIVE))
                                    .map(entries -> (Map<DataEntity, MappedDataEntity>) ImmutableMap.<DataEntity, MappedDataEntity>ofEntries(entries.toArray(new Map.Entry[0])))
                                    .replay(1)
                                    .autoConnect(0);

    }

    public Observable<Map<DataEntity, MappedDataEntity>> getLowDimData() {
        return lowDimData;
    }
}
