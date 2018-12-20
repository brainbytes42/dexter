package de.panbytes.dexter.core.activelearning;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Table;
import de.panbytes.dexter.core.AppContext;
import de.panbytes.dexter.core.ClassLabel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.core.model.classification.ClassificationModel;
import de.panbytes.dexter.ext.task.ObservableTask;
import de.panbytes.dexter.ext.task.TaskMonitor;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.*;
import static de.panbytes.dexter.core.model.classification.CrossValidation.CrossValidationResult;

public class ActiveLearning {

    public static final ClassificationUncertaintyMeasure CLASSIFICATION_UNCERTAINTY_MEASURE = ClassificationUncertaintyMeasure.ENTROPY;
    private static final Logger log = LoggerFactory.getLogger(ActiveLearning.class);
    private Observable<Map<DataEntity, ClassLabel>> modelSuggestedLabels;
    private AppContext appContext;
    @Deprecated private Supplier<Classifier> classifierSupplier;
    @Deprecated private List<ClassLabel> classLabels;
    private Observable<List<ClassificationUncertainty>> classificationUncertainty;
    private Observable<List<CrossValidationUncertainty>> existingLabelsUncertainty;


    public ActiveLearning(Supplier<Classifier> classifierSupplier, List<Optional<ClassLabel>> classLabelsOpt) {
        this.classifierSupplier = checkNotNull(classifierSupplier);
        this.classLabels = classLabelsOpt.stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
    }

    public ActiveLearning(ClassificationModel classificationModel, AppContext appContext) {

        this.appContext = appContext;

        this.classificationUncertainty = classificationModel.getClassificationResults()
                                                            .map(classificationResultOpt -> classificationResultOpt.map(
                                                                    classificationResult -> classificationResult.entrySet()
                                                                                                                .stream()
                                                                                                                .map(entityResultEntry -> new ClassificationUncertainty(
                                                                                                                        entityResultEntry.getKey(),
                                                                                                                        entityResultEntry.getValue(),
                                                                                                                        CLASSIFICATION_UNCERTAINTY_MEASURE))
                                                                                                                .sorted(Comparator.comparing(
                                                                                                                        ClassificationUncertainty::getUncertaintyValue,Comparator.reverseOrder()))
                                                                                                                .collect(
                                                                                                                        Collectors.toList()))
                                                                                                                   .orElse(Collections.emptyList()))
                                                            .doOnNext(uncertainties -> log.debug(uncertainties.isEmpty()
                                                                                                 ? "No uncertain classification results."
                                                                                                 : "Uncertain classification results: " + uncertainties))
                                                            .replay(1)
                                                            .autoConnect();

        classificationModel.getCrossValidationResults();

        this.existingLabelsUncertainty = Observable.combineLatest(classificationModel.getCrossValidationResults(),
                                                                  this.appContext.getInspectionHistory()
                                                                                 .getLabeledEntities()
                                                                                 .toObservable(),
                                                                  (crossValidationResultOpt, checkedEntities) -> {
                                                                      return crossValidationResultOpt.map(
                                                                              CrossValidationResult::getClassificationResults)
                                                                                                     .map(ImmutableMultimap::asMap)
                                                                                                     .map(ImmutableMap::entrySet)
                                                                                                     .map(resultEntries -> resultEntries.stream()
                                                                                                                                        .filter(entry -> !checkedEntities
                                                                                                                                                .contains(
                                                                                                                                                        entry.getKey()))
                                                                                                                                        .map(entry -> new CrossValidationUncertainty(
                                                                                                                                                entry.getKey(),
                                                                                                                                                entry.getValue()))
                                                                                                                                        .sorted(Comparator
                                                                                                                                                        .comparing(
                                                                                                                                                                CrossValidationUncertainty::getUncertaintyValue,Comparator.reverseOrder()))
                                                                                                                                        .collect(
                                                                                                                                                Collectors
                                                                                                                                                        .toList()))
                                                                                                     .orElse(Collections.emptyList());
                                                                  })
                                                   .doOnNext(uncertainties -> log.debug(uncertainties.isEmpty()
                                                                                        ? "No uncertain cross-validation results."
                                                                                        : "Uncertain cross-validation results: " + uncertainties))
                                                   .replay(1)
                                                   .autoConnect();


        final Observable<Map<DataEntity, ClassLabel>> suggestionsForUnlabeled = classificationModel.getClassificationResults()
                                                                                                   .map(optional -> optional.map(
                                                                                                           resultMap -> resultMap.entrySet()
                                                                                                                                 .stream()
                                                                                                                                 .filter(entry -> entry
                                                                                                                                         .getValue()
                                                                                                                                         .getMostProbableClassLabel()
                                                                                                                                         .isPresent())
                                                                                                                                 .collect(
                                                                                                                                         Collectors
                                                                                                                                                 .toMap(Map.Entry::getKey,
                                                                                                                                                        entry -> entry
                                                                                                                                                                .getValue()
                                                                                                                                                                .getMostProbableClassLabel()
                                                                                                                                                                .orElseThrow(
                                                                                                                                                                        () -> new IllegalStateException(
                                                                                                                                                                                "Filtered for present Optional, but found non-present!")))))
                                                                                                                            .orElse(Collections
                                                                                                                                            .emptyMap()));
        final Observable<Map<DataEntity, ClassLabel>> suggestionsForLabeled = classificationModel.getCrossValidationResults()
                                                                                                 .map(crossValidation -> crossValidation.map(
                                                                                                         CrossValidationResult::getClassificationResults)
                                                                                                                                        .map(ImmutableMultimap::asMap)
                                                                                                                                        .map(Map::entrySet)
                                                                                                                                        .map(entries -> entries
                                                                                                                                                .stream()
                                                                                                                                                .collect(
                                                                                                                                                        Collectors
                                                                                                                                                                .toMap(Map.Entry::getKey,
                                                                                                                                                                       entry -> entry
                                                                                                                                                                               .getValue()
                                                                                                                                                                               .stream()
                                                                                                                                                                               .collect(
                                                                                                                                                                                       Collectors
                                                                                                                                                                                               .groupingBy(
                                                                                                                                                                                                       de.panbytes.dexter.core.model.classification.Classifier.ClassificationResult::getMostProbableClassLabel,
                                                                                                                                                                                                       Collectors
                                                                                                                                                                                                               .counting()))
                                                                                                                                                                               .entrySet()
                                                                                                                                                                               .stream()
                                                                                                                                                                               .sorted(Comparator
                                                                                                                                                                                               .comparing(
                                                                                                                                                                                                       Map.Entry::getValue,
                                                                                                                                                                                                       Comparator
                                                                                                                                                                                                               .reverseOrder()))
                                                                                                                                                                               .map(Map.Entry::getKey)
                                                                                                                                                                               .findFirst()
                                                                                                                                                                               .flatMap(
                                                                                                                                                                                       opt -> opt))))
                                                                                                                                        .map(optMap -> optMap
                                                                                                                                                .entrySet()
                                                                                                                                                .stream()
                                                                                                                                                .filter(entry -> entry
                                                                                                                                                        .getValue()
                                                                                                                                                        .isPresent())
                                                                                                                                                .collect(
                                                                                                                                                        Collectors
                                                                                                                                                                .toMap(Map.Entry::getKey,
                                                                                                                                                                       entry -> entry
                                                                                                                                                                               .getValue()
                                                                                                                                                                               .orElseThrow(
                                                                                                                                                                                       () -> new IllegalStateException(
                                                                                                                                                                                               "Filtered for Optional.isPresent, but found empty!")))))
                                                                                                                                        .orElse(Collections
                                                                                                                                                        .emptyMap()));
        this.modelSuggestedLabels = Observable.combineLatest(suggestionsForUnlabeled, suggestionsForLabeled, (first, second) -> {
            final Map<DataEntity, ClassLabel> map = new HashMap<>();
            map.putAll(first);
            map.putAll(second);
            checkState(first.size() + second.size() == map.size(),
                       "Not all elements from input-maps are contained in output, sizes differ!");
            return map;
        }).distinctUntilChanged();
    }

    /**
     * called from pickSample
     *
     * @param featureSpace
     * @param trainingSetData
     * @param evaluationSetData
     * @param taskMonitor
     * @return
     * @throws Exception
     */
    @Deprecated
    public List<Double> calculateConfidence(FeatureSpace featureSpace,
                                            List<? extends DataEntity> trainingSetData,
                                            List<? extends DataEntity> evaluationSetData,
                                            TaskMonitor taskMonitor) throws Exception {

        checkArgument(featureSpace.getFeatureCount() > 0, "No features in feature space!");

        checkNotNull(evaluationSetData);
        evaluationSetData.forEach(dataEntity -> checkArgument(dataEntity.getFeatureSpace().equals(featureSpace),
                                                              "DataEntity  %s in evaluation set has different FeatureSpace!", dataEntity));


        ObservableTask<List<Double>> task = new ObservableTask<List<Double>>("Building Model for Active Learning",
                                                                             "Finding most informative Instance",
                                                                             Schedulers.computation()) {
            @Override
            protected List<Double> runTask() throws Exception {

                setMessage("Build classifier...");

                Instances trainingSet = buildTrainingSet(featureSpace, trainingSetData);
                Classifier classifier = classifierSupplier.get();
                classifier.buildClassifier(trainingSet);


                setMessage("Assemble evaluation...");

                Instances evaluationSet = new Instances(trainingSet, evaluationSetData.size());
                evaluationSet.setRelationName("evaluation");
                evaluationSet.addAll(evaluationSetData.parallelStream().map(dataEntity -> {
                    double[] coordinates = dataEntity.getCoordinates().getValue();
                    double[] values = Arrays.copyOf(coordinates, coordinates.length + 1);
                    values[values.length - 1] = Utils.missingValue();
                    return new DenseInstance(1, values);
                }).collect(Collectors.toList()));


                setMessage("Perform evaluation...");

                AtomicInteger progress = new AtomicInteger();
                return evaluationSet.parallelStream()
                                    .peek(__ -> setProgress(progress.incrementAndGet(), evaluationSet.size()))
                                    .map(instance -> {
                                        try {
                                            double[] distribution = classifier.distributionForInstance(instance);

                                            // LEAST CONFIDENT
                                            // return Doubles.max(distribution);

                                            // ENTROPY
                                            System.out.println(Arrays.toString(distribution));
                                            return Arrays.stream(distribution)
                                                         .filter(Double::isFinite)
                                                         .map(p -> Math.max(p, Double.MIN_VALUE))
                                                         .map(p -> p * Math.log(p))
                                                         .sum();

                                        } catch (Exception e) {
                                            e.printStackTrace(); //TODO
                                            return Double.NaN;
                                        }
                                    })
                                    .collect(Collectors.toList());
            }
        };
        taskMonitor.addTask(task);

        return task.result().blockingGet();

    }

    public Evaluation evaluateClassification(FeatureSpace featureSpace, List<? extends DataEntity> trainingSetData) throws Exception {

        Instances trainingSet = buildTrainingSet(featureSpace, trainingSetData);

        Evaluation evaluation = new Evaluation(trainingSet);
        evaluation.crossValidateModel(this.classifierSupplier.get(), trainingSet, 10, new Random(1)); // TODO magic values?

        return evaluation;
    }

    public Table<DomainDataEntity, ClassLabel, Double> classificationProbabilities(FeatureSpace featureSpace,
                                                                                   List<? extends DomainDataEntity> trainingSetData,
                                                                                   List<? extends DomainDataEntity> evaluationSetData,
                                                                                   TaskMonitor taskMonitor) {

        checkArgument(featureSpace.getFeatureCount() > 0, "No features in feature space!");

        checkNotNull(trainingSetData);
        trainingSetData.forEach(dataEntity -> checkArgument(dataEntity.getFeatureSpace().equals(featureSpace),
                                                            "DataEntity  %s in training set has different FeatureSpace!", dataEntity));
        checkNotNull(evaluationSetData);
        evaluationSetData.forEach(dataEntity -> checkArgument(dataEntity.getFeatureSpace().equals(featureSpace),
                                                              "DataEntity  %s in evaluation set has different FeatureSpace!", dataEntity));

        ObservableTask<Table<DomainDataEntity, ClassLabel, Double>> task = new ObservableTask<Table<DomainDataEntity, ClassLabel, Double>>(
                "Building Model for Active Learning", "Computing classification probabilities.", Schedulers.computation()) {
            @Override
            protected Table<DomainDataEntity, ClassLabel, Double> runTask() throws Exception {

                setMessage("Build classifier...");

                Instances trainingSet = buildTrainingSet(featureSpace, trainingSetData);
                Classifier classifier = classifierSupplier.get();
                classifier.buildClassifier(trainingSet);


                setMessage("Assemble evaluation...");

                Instances evaluationSet = new Instances(trainingSet, evaluationSetData.size());
                evaluationSet.setRelationName("evaluation");
                evaluationSet.addAll(evaluationSetData.parallelStream().map(dataEntity -> {
                    double[] coordinates = dataEntity.getCoordinates().getValue();
                    double[] values = Arrays.copyOf(coordinates, coordinates.length + 1);
                    values[values.length - 1] = Utils.missingValue(); // classification
                    return new DenseInstance(1, values);
                }).collect(Collectors.toList()));


                setMessage("Calculate probabilities...");

                Table<DomainDataEntity, ClassLabel, Double> table = ArrayTable.create(evaluationSetData, ActiveLearning.this.classLabels);

                for (int i = 0; i < evaluationSet.size(); i++) {
                    setProgress(i + 1, evaluationSet.size());

                    double[] distribution = classifier.distributionForInstance(evaluationSet.get(i));

                    for (int j = 0; j < classLabels.size(); j++) {
                        //                        System.out.printf("i: %d  j: %d  featurespace: %d  distribution: %d  evalSet: %d  evalSetData: %d  %n", i, j, featureSpace.getFeatureCount(), distribution.length,evaluationSet.size(),evaluationSetData.size());
                        table.put(evaluationSetData.get(i), classLabels.get(j), distribution[j]);
                    }

                }

                return table;

            }
        };
        taskMonitor.addTask(task);

        return task.result().blockingGet();
    }

    private Instances buildTrainingSet(FeatureSpace featureSpace, List<? extends DataEntity> trainingSet) {
        checkNotNull(featureSpace);
        checkNotNull(trainingSet);
        trainingSet.forEach(
                dataEntity -> checkArgument(dataEntity.getFeatureSpace().equals(featureSpace), "DataEntity %s has different FeatureSpace!",
                                            dataEntity));


        List<Attribute> featureAttributes = featureSpace.getFeatures()
                                                        .stream()
                                                        .map(feature -> new Attribute(feature.getName().getValue()))
                                                        .collect(Collectors.toList());
        Attribute classAttribute = new Attribute("class", this.classLabels.stream().map(ClassLabel::getLabel).collect(Collectors.toList()));
        //                                                 trainingSet.parallelStream()
        //                                                            .map(dataEntity -> dataEntity.getClassLabel()
        //                                                                                         .getValue()
        //                                                                                         .map(ClassLabel::getLabel)
        //                                                                                         .orElse(null))
        //                                                            .filter(Objects::nonNull)
        //                                                            .distinct()
        //                                                            .collect(Collectors.toList()));

        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.addAll(featureAttributes);
        attributes.add(classAttribute);

        Instances dataset = new Instances("training", attributes, trainingSet.size());
        dataset.setClass(classAttribute);


        dataset.addAll(trainingSet.parallelStream().map(dataEntity -> dataEntity.getClassLabel().getValue().map(classLabel -> {
            double[] coordinates = dataEntity.getCoordinates().getValue();
            double[] values = Arrays.copyOf(coordinates, coordinates.length + 1);
            values[values.length - 1] = classAttribute.indexOfValue(classLabel.getLabel());
            return new DenseInstance(1, values);
        })).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()));
        return dataset;
    }

    /**
     * Classification uncertainties, sorted by uncertainty, most uncertain first
     *
     * @return
     */
    public Observable<List<ClassificationUncertainty>> getClassificationUncertainty() {
        return this.classificationUncertainty;
    }

    public Observable<List<CrossValidationUncertainty>> getExistingLabelsUncertainty() {
        return this.existingLabelsUncertainty;
    }

    public Observable<Map<DataEntity, ClassLabel>> getModelSuggestedLabels() {
        return modelSuggestedLabels;
    }

    /**
     * uncertainty, normalized in range of 0..1, where 1 is most uncertain
     */
    enum ClassificationUncertaintyMeasure {

        LEAST_CONFIDENT_PREDICTION(coll -> 1-Collections.max(coll)),

        ENTROPY(probabilities -> probabilities.stream()
                                              .filter(Double::isFinite)
                                              .mapToDouble(p -> Math.max(p, Double.MIN_VALUE))
                                              .map(p -> p * Math.log(p))
                                              .sum() *(-1) / Math.log(probabilities.size()));

        private final Function<Collection<Double>, Double> function;

        ClassificationUncertaintyMeasure(Function<Collection<Double>, Double> function) {this.function = function;}

        double apply(Collection<Double> probabilities) {return this.function.apply(probabilities);}
    }

    public class ClassificationUncertainty extends AbstractUncertainty {

        private final de.panbytes.dexter.core.model.classification.Classifier.ClassificationResult classificationResult;
        private final ClassificationUncertaintyMeasure uncertaintyMeasure;
        private final double uncertaintyValue;

        public ClassificationUncertainty(DataEntity dataEntity,
                                         de.panbytes.dexter.core.model.classification.Classifier.ClassificationResult classificationResult,
                                         ClassificationUncertaintyMeasure uncertaintyMeasure) {
            super(dataEntity);
            this.classificationResult = classificationResult;
            this.uncertaintyMeasure = uncertaintyMeasure;
            this.uncertaintyValue = uncertaintyMeasure.apply(classificationResult.getClassLabelProbabilities().values());
        }

        public ClassificationUncertaintyMeasure getUncertaintyMeasure() {
            return this.uncertaintyMeasure;
        }

        @Override
        public String toString() {
            return this.getClass()
                       .getSimpleName() + "(" + getDataEntity() + " -> " + this.uncertaintyMeasure + "=" + this.uncertaintyValue + " @ " + getClassificationResult() + ")";
        }

        @Override
        public double getUncertaintyValue() {
            return this.uncertaintyValue;
        }

        public de.panbytes.dexter.core.model.classification.Classifier.ClassificationResult getClassificationResult() {
            return this.classificationResult;
        }
    }

    public class CrossValidationUncertainty extends AbstractUncertainty {

        private final double uncertaintyValue;
        private final Collection<de.panbytes.dexter.core.model.classification.Classifier.ClassificationResult> classificationResults;

        CrossValidationUncertainty(DataEntity dataEntity,
                                   Collection<de.panbytes.dexter.core.model.classification.Classifier.ClassificationResult> classificationResults) {
            super(dataEntity);
            this.classificationResults = classificationResults;

            final ClassLabel givenClassLabel = dataEntity.getClassLabel()
                                                         .getValue()
                                                         .orElseThrow(() -> new IllegalArgumentException(
                                                                 "Expecting only labeled entities for CrossValidation!"));

            this.uncertaintyValue = classificationResults.stream().mapToDouble(classification -> {
                if (classification.getMostProbableClassLabel().map(givenClassLabel::equals).orElse(false)) {
                    return 1-classification.getClassificationProbability().orElse(1); // assume p=1 if missing, as label was correct.
                } else {
                    return 1;
                }
            }).sum() / classificationResults.size();
        }

        @Override
        public double getUncertaintyValue() {
            return this.uncertaintyValue;
        }

        @Override
        public String toString() {
            return this.getClass()
                       .getSimpleName() + "(" + getDataEntity().toString() + " -> uncertainty =" + this.uncertaintyValue + " from " + this.classificationResults + ")";
        }
    }

    public abstract class AbstractUncertainty {
        private final DataEntity dataEntity;

        AbstractUncertainty(DataEntity dataEntity) {
            this.dataEntity = dataEntity;
        }

        public DataEntity getDataEntity() {
            return this.dataEntity;
        }

        public abstract double getUncertaintyValue();
    }
}
