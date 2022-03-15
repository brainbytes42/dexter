package de.panbytes.dexter.core.model.classification;

import com.google.common.collect.ImmutableListMultimap;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.ext.task.ObservableTask;
import de.panbytes.dexter.util.ListUtil;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class CrossValidation extends ObservableTask<CrossValidation.CrossValidationResult> {

    private static final Logger log = LoggerFactory.getLogger(CrossValidation.class);

    private final List<DataEntity> dataSet;
    private final int folds;
    private final int runs;
    private final FeatureSpace featureSpace;
    private final Supplier<weka.classifiers.Classifier> wekaClassifierSupplier;

    public CrossValidation(Scheduler scheduler,
                           Collection<? extends DataEntity> dataSet,
                           FeatureSpace featureSpace,
                           Supplier<weka.classifiers.Classifier> wekaClassifierSupplier,
                           int folds,
                           int runs) {

        super("Cross-Validation", "Performing Cross-Validation", scheduler);

        // validation...
        checkNotNull(dataSet);
        checkNotNull(featureSpace);
        checkNotNull(wekaClassifierSupplier);
        checkArgument(folds >= 2, "Number of folds must be at least 2!");
        checkArgument(folds <= dataSet.size(), "Can't have more folds than instances!");
        checkArgument(runs >= 1, "CrossValidation has to be run at least once (runs >= 1)");

        this.dataSet = new ArrayList<>(dataSet);
        this.featureSpace = featureSpace;
        this.wekaClassifierSupplier = wekaClassifierSupplier;
        this.folds = folds;
        this.runs = runs;
    }

    public List<DataEntity> getDataSet() {
        return this.dataSet;
    }

    public int getFolds() {
        return this.folds;
    }

    public int getRuns() {
        return this.runs;
    }

    @Override
    protected CrossValidationResult runTask() throws Exception {

        log.trace("Running CrossValidation {} times in {} folds for {} entities...", this.runs, this.folds, this.dataSet.size());

        final int totalWork = this.runs * this.folds;
        final AtomicInteger workDone = new AtomicInteger(0);

        return IntStream.range(0, this.runs)
                        .parallel()
                        .mapToObj(run -> {

                            try {
                                checkCanceled();

                                final List<DataEntity> shuffledDataSet = new ArrayList<>(this.dataSet);
                                Collections.shuffle(shuffledDataSet);

                                final List<List<DataEntity>> partitions = ListUtil.partition(shuffledDataSet, this.folds);

                                return partitions.parallelStream().map(currentTestData -> {

                                    Map<DataEntity, Classifier.ClassificationResult> classificationResult;

                                    try {
                                        checkCanceled();
                                        setProgress(workDone.incrementAndGet(), totalWork);

                                        List<DataEntity> trainingSet = new ArrayList<>(this.dataSet.size());
                                        partitions.stream().filter(list -> list != currentTestData).forEach(trainingSet::addAll);

                                        final Classifier classifier = new WekaClassification(Schedulers.computation(), trainingSet,
                                                                                             this.featureSpace, this.wekaClassifierSupplier)
                                                .runTask();

                                        classificationResult = classifier.classify(currentTestData);


                                    } catch (Exception e) {
                                        if(e instanceof InterruptedException){
                                            log.debug("CrossValidation: Processing fold was interrupted (cancelled).");
                                            log.trace("CrossValidation: Processing fold was interrupted.", e);
                                        } else {
                                            log.warn("CrossValidation: Exception while processing fold", e);
                                        }
                                        classificationResult = Collections.emptyMap();
                                    }

                                    return classificationResult;

                                }).<Map<DataEntity, Classifier.ClassificationResult>>collect(HashMap::new, Map::putAll, Map::putAll);

                            } catch (InterruptedException e) {
                                log.debug("CrossValidation: Exception while processing run", e);
                                return Collections.<DataEntity, Classifier.ClassificationResult>emptyMap();
                            }

                        })
                        .map(classificationResultMap -> new CrossValidationResult(classificationResultMap, this))
                        .reduce(new CrossValidationResult(Collections.emptyMap(), this), CrossValidationResult::new);
    }

    public static class CrossValidationResult {

        private final ImmutableListMultimap<DataEntity, Classifier.ClassificationResult> results;
        private final CrossValidation crossValidation;

        /**
         * Create from Classification-Results
         *
         * @param classificationResultMap
         * @param crossValidation
         */
        CrossValidationResult(Map<DataEntity, Classifier.ClassificationResult> classificationResultMap, CrossValidation crossValidation) {

            this.crossValidation = checkNotNull(crossValidation);

            this.results = ImmutableListMultimap.<DataEntity, Classifier.ClassificationResult>builder().putAll(
                    checkNotNull(classificationResultMap).entrySet()).build();
        }

        /**
         * Create by merging
         *
         * @param resultA
         * @param resultB
         */
        CrossValidationResult(CrossValidationResult resultA, CrossValidationResult resultB) {

            // ensure both results from same validation
            checkArgument(resultA.crossValidation == resultB.crossValidation, "Expect both results from same CrossValidation!");

            // ensure the same set of entities for both results being merged (but allow inequality if one is empty)
            checkArgument(
                    resultA.results.keySet().equals(resultB.results.keySet()) || resultA.results.isEmpty() || resultB.results.isEmpty(),
                    "CrossValidationResults to be merged are expected to provide same key-set (same set of entities). This does not apply if at least one result is empty.");

            this.crossValidation = resultA.crossValidation;

            this.results = ImmutableListMultimap.<DataEntity, Classifier.ClassificationResult>builder().putAll(resultA.results)
                                                                                                       .putAll(resultB.results)
                                                                                                       .build();


        }

        public ImmutableListMultimap<DataEntity, Classifier.ClassificationResult> getClassificationResults() {
            return this.results;
        }

        public CrossValidation getCrossValidation() {
            return this.crossValidation;
        }
    }
}
