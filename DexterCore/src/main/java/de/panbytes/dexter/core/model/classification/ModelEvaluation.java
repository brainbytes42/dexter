package de.panbytes.dexter.core.model.classification;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import de.panbytes.dexter.core.ClassLabel;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModelEvaluation {

    private final Map<ClassLabel, Long> entityLabelOccurrences;
    private final CrossValidation.CrossValidationResult crossValidationResult;
    private final List<ClassLabel> labels;
    private final ArrayTable<ClassLabel, ClassLabel, Long> confusionMatrix;
    private final ArrayTable<ClassLabel, ResultClassification, Long> resultClassificationTable;
    private final ArrayTable<ClassLabel, QualityMeasure, Double> qualityMeasures;
    private final Map<QualityMeasure, Double> qualityMeasuresWeightedAverage;

    public ModelEvaluation(CrossValidation.CrossValidationResult crossValidationResult) {

        Preconditions.checkArgument(crossValidationResult.getClassificationResults()
                                                         .asMap()
                                                         .entrySet()
                                                         .parallelStream()
                                                         .mapToInt(entry -> entry.getValue().size())
                                                         .distinct()
                                                         .count() == 1, "There are different numbers of classifications for each entity!");

        this.crossValidationResult = crossValidationResult;

        this.entityLabelOccurrences = countEntityLabelOccurrences(crossValidationResult);

        this.labels = extractLabelsSorted(this.entityLabelOccurrences);

        this.confusionMatrix = calculateConfusionMatrix(crossValidationResult, this.labels);

        this.resultClassificationTable = calculateResultClassificationTable(this.confusionMatrix, this.labels);

        this.qualityMeasures = calculateQualityMeasures(this.resultClassificationTable, this.labels);

        this.qualityMeasuresWeightedAverage = calculateQualityWeightedAverage(this.qualityMeasures, this.entityLabelOccurrences);

    }

    public CrossValidation.CrossValidationResult getCrossValidationResult() {
        return this.crossValidationResult;
    }

    private ConcurrentMap<ClassLabel, Long> countEntityLabelOccurrences(CrossValidation.CrossValidationResult crossValidation) {
        return crossValidation.getClassificationResults()
                              .keySet()
                              .parallelStream()
                              .collect(Collectors.groupingByConcurrent(entity -> entity.getClassLabel()
                                                                                       .getValue()
                                                                                       .orElseThrow(() -> new IllegalStateException(
                                                                                               "Entity in CrossValidation without Label isn't allowed: " + entity)),
                                                                       Collectors.counting()));
    }

    private List<ClassLabel> extractLabelsSorted(Map<ClassLabel, Long> labelOccurrences) {
        return labelOccurrences.keySet().stream().sorted(Comparator.comparing(ClassLabel::getLabel)).collect(Collectors.toList());
    }

    private ArrayTable<ClassLabel, ClassLabel, Long> calculateConfusionMatrix(CrossValidation.CrossValidationResult crossValidation,
                                                                              List<ClassLabel> labels) {

        final ArrayTable<ClassLabel, ClassLabel, AtomicLong> confusionCounter = ArrayTable.create(labels, labels);
        for (int i = 0; i < labels.size(); i++) {
            for (int j = 0; j < labels.size(); j++) {
                confusionCounter.set(i, j, new AtomicLong());
            }
        }
        crossValidation.getClassificationResults()
                       .entries()
                       .parallelStream()
                       .forEach(entry -> confusionCounter.get(entry.getKey()
                                                                   .getClassLabel()
                                                                   .getValue()
                                                                   .orElseThrow(() -> new IllegalStateException(
                                                                           "Entity in CrossValidation without Label isn't allowed: " + entry
                                                                                   .getKey())), entry.getValue()
                                                                                                     .getMostProbableClassLabel()
                                                                                                     .orElseThrow(
                                                                                                             () -> new IllegalStateException(
                                                                                                                     "Entity in CrossValidation wasn't classified, which isn't allowed: " + entry
                                                                                                                             .getKey())))
                                                         .incrementAndGet());

        return confusionCounter.cellSet()
                               .stream()
                               .collect(Tables.toTable(Table.Cell::getRowKey, Table.Cell::getColumnKey, cell -> cell.getValue().get(),
                                                       () -> ArrayTable.create(labels, labels)));
    }

    private ArrayTable<ClassLabel, ResultClassification, Long> calculateResultClassificationTable(ArrayTable<ClassLabel, ClassLabel, Long> confusion,
                                                                                                  List<ClassLabel> labels) {
        final ArrayTable<ClassLabel, ResultClassification, Long> resultClassificationTable = ArrayTable.create(labels, Arrays.asList(
                ResultClassification.values()));

        labels.parallelStream().forEach(currentLabel -> {

            final int labelIdx = labels.indexOf(currentLabel);

            for (ResultClassification resultClassification : ResultClassification.values()) {

                long value = 0;

                switch (resultClassification) {
                    case TRUE_POSITIVE:
                        value = confusion.get(currentLabel, currentLabel);
                        break;
                    case TRUE_NEGATIVE:
                        for (ClassLabel rowLabel : labels) {
                            if (rowLabel.equals(currentLabel)) continue;

                            for (ClassLabel colLabel : labels) {
                                if (colLabel.equals(currentLabel)) continue;

                                value += confusion.get(rowLabel, colLabel);
                            }
                        }
                        break;
                    case FALSE_POSITIVE:
                        for (ClassLabel rowLabel : labels) {
                            if (rowLabel.equals(currentLabel)) continue;

                            value += confusion.get(rowLabel, currentLabel);
                        }
                        break;
                    case FALSE_NEGATIVE:
                        for (ClassLabel colLabel : labels) {
                            if (colLabel.equals(currentLabel)) continue;

                            value += confusion.get(currentLabel, colLabel);
                        }
                        break;
                }

                @Nullable Long check = resultClassificationTable.put(currentLabel, resultClassification, value);
                if (check != null) {
                    throw new IllegalStateException("CorrectnessTable wasn't empty for " + currentLabel + " / " + resultClassification);
                }
            }
        });

        return resultClassificationTable;
    }

    private ArrayTable<ClassLabel, QualityMeasure, Double> calculateQualityMeasures(Table<ClassLabel, ResultClassification, Long> resultClassificationTable,
                                                                                    List<ClassLabel> labels) {
        final ArrayTable<ClassLabel, QualityMeasure, Double> qualityMeasureTable = ArrayTable.create(labels, Arrays.asList(
                QualityMeasure.values()));

        labels.parallelStream().forEach(label -> {

            for (QualityMeasure qualityMeasure : QualityMeasure.values()) {

                qualityMeasureTable.put(label, qualityMeasure, qualityMeasure.calc(resultClassificationTable.row(label)));

            }

        });

        return qualityMeasureTable;
    }

    private Map<QualityMeasure, Double> calculateQualityWeightedAverage(Table<ClassLabel, QualityMeasure, Double> qualityMeasures,
                                                                        Map<ClassLabel, Long> labelOccurrences) {

        final long totalElements = labelOccurrences.values().stream().mapToLong(val -> val).sum();

        return qualityMeasures.columnKeySet()
                              .parallelStream()
                              .collect(Collectors.toMap(measure -> measure, measure -> labelOccurrences.entrySet()
                                                                                                       .stream()
                                                                                                       .mapToDouble(
                                                                                                               entry -> entry.getValue() * qualityMeasures
                                                                                                                       .get(entry.getKey(),
                                                                                                                            measure))
                                                                                                       .sum() / totalElements));
    }

    @Deprecated
    public void print() {

        //        private final Map<ClassLabel, Long> labelOccurrences;
        //        private final List<ClassLabel> labels;
        //        private final AtomicLong[][] confusionMatrix;
        //        private final Table<ClassLabel, ResultClassification, Long> resultClassificationTable;
        //        private final Table<ClassLabel, QualityMeasure, Double> qualityMeasures;
        //        private final Map<QualityMeasure, Double> qualityMeasuresWeightedAverage;

        System.out.println("labelOccurrences");
        System.out.println(entityLabelOccurrences);
        System.out.println();

        System.out.println("labels");
        System.out.println(labels);
        System.out.println();

        System.out.println("confusionMatrix");
        System.out.println(confusionMatrix);
        System.out.println();

        System.out.println("resultClassificationTable");
        System.out.println(resultClassificationTable);
        System.out.println();

        System.out.println("qualityMeasures");
        System.out.println(qualityMeasures);
        System.out.println();

        System.out.println("qualityMeasuresWeightedAverage");
        System.out.println(qualityMeasuresWeightedAverage);
        System.out.println();

    }

    public Map<ClassLabel, Long> getEntityLabelOccurrences() {
        return this.entityLabelOccurrences;
    }

    public List<ClassLabel> getLabels() {
        return this.labels;
    }

    public ArrayTable<ClassLabel, ClassLabel, Long> getConfusionMatrix() {
        return this.confusionMatrix;
    }

    public ArrayTable<ClassLabel, ResultClassification, Long> getResultClassificationTable() {
        return this.resultClassificationTable;
    }

    public ArrayTable<ClassLabel, QualityMeasure, Double> getQualityMeasures() {
        return this.qualityMeasures;
    }

    public Map<QualityMeasure, Double> getQualityMeasuresWeightedAverage() {
        return this.qualityMeasuresWeightedAverage;
    }

    private enum ResultClassification {
        TRUE_POSITIVE, TRUE_NEGATIVE, FALSE_POSITIVE, FALSE_NEGATIVE;
    }

    public enum QualityMeasure {

        PRECISION("Precision",
                  "Sensitive for falsely positive classifications: Correct positive classifications vs. all positive classifications, i.e. TP/(TP+FP).",
                  resultClassifications -> resultClassifications.get(ResultClassification.TRUE_POSITIVE) == 0
                                           ? 0
                                           : resultClassifications.get(ResultClassification.TRUE_POSITIVE) / (double) (resultClassifications
                                                   .get(ResultClassification.TRUE_POSITIVE) + resultClassifications.get(
                                                   ResultClassification.FALSE_POSITIVE))),

        TRUE_POSITIVE_RATE_RECALL("Recall / TP Rate",
                                  "Sensitive for missed classifications: Correct positive classifications vs. real positives, i.e. TP/(TP+FN).",
                                  resultClassifications -> resultClassifications.get(ResultClassification.TRUE_POSITIVE) == 0
                                                           ? 0
                                                           : resultClassifications.get(
                                                                   ResultClassification.TRUE_POSITIVE) / (double) (resultClassifications.get(
                                                                   ResultClassification.TRUE_POSITIVE) + resultClassifications.get(
                                                                   ResultClassification.FALSE_NEGATIVE))),

        F_MEASURE("F-Measure", "Combined Accuracy-Measure: Harmonic Average of Precision and Recall.", resultClassifications -> {
            final double precision = PRECISION.calc(resultClassifications);
            final double recall = TRUE_POSITIVE_RATE_RECALL.calc(resultClassifications);
            return (precision + recall) == 0 ? 0 : 2 * precision * recall / (precision + recall);
        }),

        FALSE_POSITIVE_RATE("FP Rate",
                            "Describes the probability of falsely positive classifications: False positive classifications vs. real negatives, i.e. FP/(FP+TN)",
                            resultClassifications -> resultClassifications.get(ResultClassification.FALSE_POSITIVE) == 0
                                                     ? 0
                                                     : resultClassifications.get(
                                                             ResultClassification.FALSE_POSITIVE) / (double) (resultClassifications.get(
                                                             ResultClassification.FALSE_POSITIVE) + resultClassifications.get(
                                                             ResultClassification.TRUE_NEGATIVE)));


        final Function<Map<ResultClassification, Long>, Double> measureFunction;
        private final String displayString;
        private final String displayDescription;
        private final Function<Double, String> valueFormatter;

        QualityMeasure(String displayString,
                       String displayDescription,
                       Function<Map<ResultClassification, Long>, Double> measureFunction,
                       Function<Double, String> valueFormatter) {
            this.displayString = displayString;
            this.displayDescription = displayDescription;
            this.measureFunction = measureFunction;
            this.valueFormatter = valueFormatter;
        }

        /**
         * defaults value formatter to one decimal digit percentage.
         *
         * @param displayString
         * @param measureFunction
         */
        QualityMeasure(String displayString, String displayDescription, Function<Map<ResultClassification, Long>, Double> measureFunction) {
            this(displayString, displayDescription, measureFunction, value -> String.format("%.1f %%", value * 100));
        }

        private double calc(Map<ResultClassification, Long> resultClassifications) {
            return this.measureFunction.apply(resultClassifications);
        }

        public String format(Double value) {
            return this.valueFormatter.apply(value);
        }


        public String getDisplayString() {
            return this.displayString;
        }

        public String getDisplayDescription() {
            return this.displayDescription;
        }

    }


}
