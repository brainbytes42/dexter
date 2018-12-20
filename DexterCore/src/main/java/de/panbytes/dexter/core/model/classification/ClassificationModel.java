package de.panbytes.dexter.core.model.classification;

import de.panbytes.dexter.core.AppContext;
import de.panbytes.dexter.core.GeneralSettings;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.domain.DomainAdapter;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.trees.RandomForest;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class ClassificationModel {

    public static final int CROSS_VALIDATION_RUNS = 3;
    public static final int CROSS_VALIDATION_FOLDS = 10;
    private static final Logger log = LoggerFactory.getLogger(ClassificationModel.class);
    private static final Supplier<weka.classifiers.Classifier> WEKA_CLASSIFIER_SUPPLIER = RandomForest::new;
    private final DomainAdapter domainAdapter;
    private final AppContext appContext;
    private final GeneralSettings settings;
    private final Observable<Optional<Map<DataEntity, Classifier.ClassificationResult>>> classificationResults;
    private final Observable<Optional<CrossValidation.CrossValidationResult>> crossValidationResults;

    public ClassificationModel(DomainAdapter domainAdapter, AppContext appContext) {

        this.domainAdapter = checkNotNull(domainAdapter, "DomainAdapter may not be null!");
        this.appContext = checkNotNull(appContext, "AppContext may not be null!");
        this.settings = this.appContext.getSettingsRegistry().getGeneralSettings();

        this.classificationResults = initClassificationResults();
        this.crossValidationResults = initCrossValidationResults();

        this.classificationResults.subscribe(
                resultMap -> System.out.println("ClassificationModel / ClassificationResults: " + resultMap)); //TODO remove

        this.crossValidationResults.subscribe(resultOpt -> {
            if (resultOpt.isPresent()) {
                System.out.println("ClassificationModel / CrossValidation:");
                System.out.println(resultOpt.get().getClassificationResults());
            } else {
                System.out.println("ClassificationModel / No CrossValidationResult available ...");
            }
        }); //TODO remove


    }

    public Observable<Optional<Map<DataEntity, Classifier.ClassificationResult>>> getClassificationResults() {
        return this.classificationResults;
    }

    public Observable<Optional<CrossValidation.CrossValidationResult>> getCrossValidationResults() {
        return this.crossValidationResults;
    }

    private Observable<Optional<CrossValidation.CrossValidationResult>> initCrossValidationResults() {
        return getData(true).observeOn(Schedulers.io()).switchMapSingle(labeledEntities -> {
            if (labeledEntities.size() >= ClassificationModel.CROSS_VALIDATION_FOLDS) {
                log.debug("Creating cross-validation for {} entities...", labeledEntities.size());

                final CrossValidation crossValidation = new CrossValidation(Schedulers.computation(), labeledEntities,
                                                                            labeledEntities.get(0).getFeatureSpace(),
                                                                            WEKA_CLASSIFIER_SUPPLIER,
                                                                            ClassificationModel.CROSS_VALIDATION_FOLDS,
                                                                            ClassificationModel.CROSS_VALIDATION_RUNS);
                return crossValidation.result()
                                      .map(Optional::of)
                                      .doOnSubscribe(disposable -> this.appContext.getTaskMonitor().addTask(crossValidation));

            } else {
                log.debug("Not enough labeled entities available ({} < {} folds) for cross-validation...", labeledEntities.size(),
                          ClassificationModel.CROSS_VALIDATION_FOLDS);

                return Single.just(Optional.<CrossValidation.CrossValidationResult>empty());

            }
        }).replay(1).autoConnect();
    }

    private Observable<Optional<Map<DataEntity, Classifier.ClassificationResult>>> initClassificationResults() {

        final Observable<Optional<Single<Classifier>>> trainedClassifier = getData(true).map(labeledEntities -> {
            if (labeledEntities.size() > 0) {
                log.debug("Creating classifier for {} entities...", labeledEntities.size());
                return Optional.of(new WekaClassification(Schedulers.computation(), new HashSet<>(labeledEntities),
                                                          labeledEntities.get(0).getFeatureSpace(), WEKA_CLASSIFIER_SUPPLIER));
            } else {
                log.debug("No labeled entities available to train a classifier...");
                return Optional.<WekaClassification>empty();
            }
        }).map(classifier -> classifier.map(wekaClassification -> {
            return wekaClassification.result().doOnSubscribe(disposable -> this.appContext.getTaskMonitor().addTask(wekaClassification));
        }));


        return Observable.combineLatest(getData(false), trainedClassifier, (unlabeledEntities, classifierOpt) -> {
            if (unlabeledEntities.size() > 0 && classifierOpt.isPresent()) {
                log.debug("Classifying {} unlabeled entities...", unlabeledEntities.size());
                return classifierOpt.get()
                                    .map(classifier -> classifier.classify(new HashSet<>(unlabeledEntities)))
                                    .map(Optional::of)
                                    .toObservable();
            } else {
                return Observable.just(Optional.<Map<DataEntity, Classifier.ClassificationResult>>empty());
            }
        }).switchMap(obs -> obs).replay(1).autoConnect();

    }

    private Observable<List<DomainDataEntity>> getData(boolean labeled) {
        return this.settings.getClassificationOnFilteredData().toObservable().switchMap(filteredOnly -> {
            if (filteredOnly) {
                return this.domainAdapter.getFilteredDomainDataLabeled(labeled);
            } else {
                return this.domainAdapter.getDomainDataLabeled(labeled);
            }
        }).switchMap(entities -> // trigger for changed coordinates!
                             entities.isEmpty()
                             ? Observable.just(Collections.<DomainDataEntity>emptyList())
                             : Observable.merge(
                                     entities.stream().map(entity -> entity.getCoordinates().toObservable()).collect(Collectors.toList()))
                                         .debounce(250, TimeUnit.MILLISECONDS)
                                         .map(__ -> entities));
    }
}
