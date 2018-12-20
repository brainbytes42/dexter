package de.panbytes.dexter.core.model.classification;

import de.panbytes.dexter.core.ClassLabel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.ext.task.ObservableTask;
import io.reactivex.Scheduler;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public interface Classifier {

    ClassificationResult classify(DataEntity dataEntity);

    default Map<DataEntity, ClassificationResult> classify(Collection<DataEntity> dataEntities) {
        return dataEntities.parallelStream().collect(Collectors.toMap(Function.identity(), this::classify));
    }


    abstract class TrainingTask extends ObservableTask<Classifier> {
        private final Collection<DataEntity> trainingSet;
        private final FeatureSpace featureSpace;

        public TrainingTask(String name, String description, Scheduler scheduler, Collection<DataEntity> trainingSet, FeatureSpace featureSpace) {
            super(name, description, scheduler);
            trainingSet.forEach(dataEntity -> checkArgument(dataEntity.getFeatureSpace().equals(featureSpace),
                                                            "DataEntity %s has different FeatureSpace!", dataEntity));
            this.trainingSet = checkNotNull(trainingSet);
            this.featureSpace = checkNotNull(featureSpace);
        }

        public Collection<DataEntity> getTrainingSet() {
            return this.trainingSet;
        }

        public FeatureSpace getFeatureSpace() {
            return this.featureSpace;
        }
    }


    class ClassificationResult {
        private final Map<ClassLabel, Double> classLabelProbabilities = new HashMap<>();

        /**
         * set probability to Double.POSITIVE_INFINITY
         * @param classLabel
         */
        public ClassificationResult(ClassLabel classLabel) {
            this.classLabelProbabilities.put(classLabel, Double.POSITIVE_INFINITY);
        }

        public ClassificationResult(Map<ClassLabel, Double> classLabelProbabilities) {
            this.classLabelProbabilities.putAll(classLabelProbabilities);
        }

        public static ClassificationResult unclassified() {
            return new ClassificationResult(Collections.emptyMap());
        }

        public Map<ClassLabel, Double> getClassLabelProbabilities() {
            return Collections.unmodifiableMap(this.classLabelProbabilities);
        }

        public Optional<ClassLabel> getMostProbableClassLabel() {
            return this.classLabelProbabilities.entrySet()
                                               .stream()
                                               .filter(entry -> entry.getValue() >= 0)
                                               .max(Comparator.comparing(Map.Entry::getValue))
                                               .map(Map.Entry::getKey);
        }

        public OptionalDouble getClassificationProbability(){
            return this.classLabelProbabilities.entrySet()
                                               .stream().mapToDouble(Map.Entry::getValue)
                                               .filter(p -> p >= 0)
                                               .max();
        }

        @Override
        public String toString() {
            return ClassificationResult.class.getSimpleName() + "<" + this.classLabelProbabilities.entrySet()
                                                                                                  .stream()
                                                                                                  .max(Comparator.comparing(
                                                                                                          Map.Entry::getValue))
                                                                                                  .map(entry -> String.format("%s|%.0f%%",
                                                                                                                              entry.getKey(),
                                                                                                                              entry.getValue() * 100))
                                                                                                  .orElse("---") + ">";
        }
    }
}
