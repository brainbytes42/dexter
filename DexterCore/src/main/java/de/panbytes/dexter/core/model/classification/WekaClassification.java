package de.panbytes.dexter.core.model.classification;

import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class WekaClassification extends Classifier.TrainingTask {

    private static final Logger log = LoggerFactory.getLogger(WekaClassification.class);

    private final List<ClassLabel> classLabels;
    private final Supplier<weka.classifiers.Classifier> wekaClassifierSupplier;

    public WekaClassification(Scheduler scheduler,
                              Collection<DataEntity> trainingSet,
                              FeatureSpace featureSpace,
                              Supplier<weka.classifiers.Classifier> wekaClassifierSupplier) {
        super("Build classifier", "Building classifier using WEKA.", scheduler, trainingSet, featureSpace);
        this.wekaClassifierSupplier = checkNotNull(wekaClassifierSupplier);

        this.classLabels = trainingSet.parallelStream()
                                      .map(DataEntity::getClassLabel)
                                      .map(RxFieldReadOnly::getValue)
                                      .filter(Optional::isPresent)
                                      .map(Optional::get)
                                      .distinct()
                                      .collect(Collectors.toList());
    }

    private Instances buildTrainingSet(Collection<? extends DataEntity> trainingSet, FeatureSpace featureSpace) {
        checkNotNull(trainingSet);
        checkNotNull(featureSpace);

        List<Attribute> featureAttributes = featureSpace.getFeatures()
                                                        .stream()
                                                        .map(feature -> new Attribute(feature.getName()))
                                                        .collect(Collectors.toList());


        Attribute classAttribute = new Attribute("class", this.classLabels.stream().map(ClassLabel::getLabel).collect(Collectors.toList()));

        ArrayList<Attribute> attributes = new ArrayList<>(featureAttributes);
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

    @Override
    protected Classifier runTask() throws Exception {

        log.trace("Building Classifier...");

        setMessage("Assemble training set...");
        final Instances trainingSet = buildTrainingSet(getTrainingSet(), getFeatureSpace());

        setMessage("Build classifier...");
        final weka.classifiers.Classifier classifier = this.wekaClassifierSupplier.get();
        classifier.buildClassifier(trainingSet);

        return new WekaClassifier(trainingSet, classifier, classLabels);
    }

    private static class WekaClassifier implements Classifier {

        private final Instances trainingSet;
        private final weka.classifiers.Classifier classifier;
        private final List<ClassLabel> classLabels;

        WekaClassifier(Instances trainingSet, weka.classifiers.Classifier classifier, List<ClassLabel> classLabels) {
            this.trainingSet = trainingSet;
            this.classifier = classifier;
            this.classLabels = classLabels;
        }

        @Override
        public ClassificationResult classify(DataEntity dataEntity) {
            return classify(Collections.singleton(dataEntity)).get(dataEntity);
        }

        @Override
        public Map<DataEntity, ClassificationResult> classify(Collection<DataEntity> dataEntities) {

            log.trace("Classifying {} entities...", dataEntities.size());

            List<DataEntity> dataEntitiesList = new ArrayList<>(dataEntities); // need ordering

            Instances evaluationSet = new Instances(this.trainingSet, dataEntitiesList.size());
            evaluationSet.setRelationName("evaluation");
            evaluationSet.addAll(dataEntitiesList.stream().map(dataEntity -> {
                double[] coordinates = dataEntity.getCoordinates().getValue();
                double[] values = Arrays.copyOf(coordinates, coordinates.length + 1);
                values[values.length - 1] = Utils.missingValue(); // classification
                return new DenseInstance(1, values);
            }).collect(Collectors.toList()));

            Map<DataEntity, ClassificationResult> resultMap = new HashMap<>(evaluationSet.size());

            for (int i = 0; i < evaluationSet.size(); i++) {

                Instance instance = evaluationSet.get(i);

                try {
                    final double[] distribution = this.classifier.distributionForInstance(instance);
                    Map<ClassLabel, Double> result = new HashMap<>(distribution.length);
                    for (int j = 0; j < distribution.length; j++) {
                        result.put(this.classLabels.get(j), distribution[j]);
                    }
                    resultMap.put(dataEntitiesList.get(i), new ClassificationResult(result));

                } catch (Exception e1) {
                    log.trace("Could not compute probability distribution for classification of " + dataEntitiesList.get(
                            i) + ". Trying simple classification instead.", e1);
                    try {
                        final double classification = this.classifier.classifyInstance(instance);
                        if (classification == Utils.missingValue()) {
                            resultMap.put(dataEntitiesList.get(i), ClassificationResult.unclassified());
                        } else {
                            final ClassLabel classLabel = this.classLabels.stream()
                                                                          .filter(lbl -> lbl.getLabel()
                                                                                            .equals(evaluationSet.classAttribute()
                                                                                                                 .value((int) classification)))
                                                                          .findAny()
                                                                          .orElseThrow(() -> new IllegalStateException(
                                                                                  "Resulting Label not found!"));
                            resultMap.put(dataEntitiesList.get(i), new ClassificationResult(classLabel));
                        }
                    } catch (Exception e2) {
                        log.warn("Could not compute classification for " + dataEntitiesList.get(i), e2);
                    }
                }

            }

            return resultMap;
        }
    }
}
