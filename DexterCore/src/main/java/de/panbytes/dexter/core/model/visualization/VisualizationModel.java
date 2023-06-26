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
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.reactivex.schedulers.Schedulers;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisualizationModel {

    private static final Logger log = LoggerFactory.getLogger(VisualizationModel.class);

    private final Observable<Map<DataEntity, MappedDataEntity>> lowDimData;
    private final AppContext appContext;

    public VisualizationModel(Observable<? extends Collection<? extends DataEntity>> domainData, AppContext appContext,
                              Observable<Boolean> dimReductionEnabled) {
        this.appContext = appContext;

        GeneralSettings settings = appContext.getSettingsRegistry().getGeneralSettings();

        AtomicReference<Map<DataEntity, MappedDataEntity>> previousMappingReference = new AtomicReference<>();

        this.lowDimData = Observable.combineLatest(domainData.debounce(500, TimeUnit.MILLISECONDS), dimReductionEnabled.distinctUntilChanged().debounce(500, TimeUnit.MILLISECONDS),
//                        (dataEntities, enabled) -> enabled?Optional.of(dataEntities):Optional.<Collection<DataEntity>>empty())
//                .filter(Optional::isPresent)
//                .map(Optional::get)
//                .debounce(500, TimeUnit.MILLISECONDS)
//                .doOnNext(domainDataEntities -> log.debug("Next DomainData: {}", domainDataEntities.map(Collection::size)))
//                .switchMap(domainDataEntitiesOpt -> {

                (domainDataEntities, enabled) -> {

                    if(!enabled){

                        Map<DataEntity, MappedDataEntity> prevMapping = new HashMap<>(previousMappingReference.get());

                        if(prevMapping.keySet().containsAll(domainDataEntities)) {
                            prevMapping.keySet().retainAll(domainDataEntities);
                            return Observable.just(prevMapping);
                        } else {
                            return Observable.<Map<DataEntity, MappedDataEntity>>empty();
                        }

                    }else {


//        this.lowDimData = dimReductionEnabled.distinctUntilChanged().switchMap(enabled -> {
//            if (enabled) {
//                return domainData.debounce(500, TimeUnit.MILLISECONDS)
//                        .doOnNext(dataEntities -> log.debug("NEXT DD {}", dataEntities.size()))
//                        .switchMap(domainDataEntities -> {
                        AtomicReference<DimensionMapping.MappingProcessor> mappingProcessor = new AtomicReference<>();
                        // this Observable is for just *one* run and provides intermediate steps until the run finishes.
                        return Observable.<Map<DataEntity, MappedDataEntity>>create(emitter -> {

                            log.debug("DexterModel / DomainData: {}", domainDataEntities.size());

                            if (domainDataEntities.size() > 0) {

                                Map<DataEntity, MappedDataEntity> previousMapping = Optional.ofNullable(previousMappingReference.get()).map(HashMap::new).orElse(new HashMap<>());
                                previousMapping.entrySet().removeIf(entry -> !domainDataEntities.contains(entry.getKey()));

                                List<DataEntity> allEntities = new ArrayList<>(domainDataEntities);
                                double[][] dataMatrix = allEntities.stream()
                                        .map(DataEntity::getCoordinates)
                                        .map(RxFieldReadOnly::getValue)
                                        .toArray(double[][]::new);
                                double[][] initialSolutionMatrix = allEntities.stream()
                                        .map(entity -> previousMapping.containsKey(entity) ? previousMapping.get(entity)
                                                .getCoordinates()
                                                .getValue()
                                                : previousMapping.entrySet()
                                                .stream()
                                                .sorted((e1, e2) -> {
                                                    double d1 = new EuclideanDistance().compute(
                                                            entity.getCoordinates().getValue(),
                                                            e1.getKey().getCoordinates().getValue());
                                                    double d2 = new EuclideanDistance().compute(
                                                            entity.getCoordinates().getValue(),
                                                            e2.getKey().getCoordinates().getValue());
                                                    return (int) Math.signum(d1 - d2);
                                                })
                                                .findFirst()
                                                .map(e -> e.getValue().getCoordinates().getValue())
                                                .orElse(new double[]{new GaussianRandomGenerator(
                                                        new JDKRandomGenerator()).nextNormalizedDouble() * 0.0001,
                                                        new GaussianRandomGenerator(
                                                                new JDKRandomGenerator()).nextNormalizedDouble()
                                                                * 0.0001}))
                                        .toArray(double[][]::new);

                                try {

                                    StochasticNeigborEmbedding.SimpleTSneContext context = new StochasticNeigborEmbedding.SimpleTSneContext();
                                    context.setInitialSolution(initialSolutionMatrix);
                                    context.setPerplexity(settings.getPerplexity().getValue());

                                    mappingProcessor.set(new StochasticNeigborEmbedding().mapAsync(dataMatrix, context));

                                    FeatureSpace featureSpace = new FeatureSpace("2D-Mapping",
                                            Arrays.asList(new FeatureSpace.Feature("x1"),
                                                    new FeatureSpace.Feature("x2")));

                                    Observable.<double[][]>create(listenerEmitter -> mappingProcessor.get().addIntermediateResultListener(
                                            (newIntermediateResult, source) -> listenerEmitter.onNext(newIntermediateResult))).forEachWhile(newIntermediateResult -> {
                                        emitter.onNext(IntStream.range(0, newIntermediateResult.length)
                                                .mapToObj(i -> new MappedDataEntity(newIntermediateResult[i], featureSpace, allEntities.get(i)))
                                                .collect(Collectors.toMap(MappedDataEntity::getMappedDataEntity, Function.identity())));
                                        return !emitter.isDisposed() && !mappingProcessor.get().getCompletableFuture().isDone();
                                    });

                                    double[][] mappedCoordinates = mappingProcessor.get().getResult();

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
                            Optional.ofNullable(mappingProcessor.get()).ifPresent(DimensionMapping.MappingProcessor::cancel);
                        }).unsubscribeOn(Schedulers.io());

//                });
//            } else {
//                return Observable.empty();
//            }

                    }
        }).flatMap(Observable::distinctUntilChanged).distinctUntilChanged().doOnNext(newValue -> {
                    if (dimReductionEnabled.blockingFirst()) {
                        previousMappingReference.set(newValue);
                    }
                })
                .map(Map::entrySet).compose(RxJavaUtils.deepFilter(dataEntityMappedDataEntityEntry -> dataEntityMappedDataEntityEntry.getKey().getStatus(), status -> status == DataNode.Status.ACTIVE)).map(entries -> (Map<DataEntity, MappedDataEntity>) ImmutableMap.<DataEntity, MappedDataEntity>ofEntries(entries.toArray(new Map.Entry[0])))
                .replay(1).autoConnect(0);

    }

    public Observable<Map<DataEntity, MappedDataEntity>> getLowDimData() {
        return lowDimData;
    }
}
