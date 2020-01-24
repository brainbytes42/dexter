package de.panbytes.dexter.core.model.classification;

import static com.google.common.base.Preconditions.checkNotNull;

import de.panbytes.dexter.core.AppContext;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.schedulers.Schedulers;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.trees.RandomForest;

public class ClassificationModel {

    public static final int CROSS_VALIDATION_RUNS = 3;
    public static final int CROSS_VALIDATION_FOLDS = 10;
    private static final Logger log = LoggerFactory.getLogger(ClassificationModel.class);
    private static final Supplier<weka.classifiers.Classifier> WEKA_CLASSIFIER_SUPPLIER = RandomForest::new;
    private final AppContext appContext;
    private final Observable<Optional<Map<DataEntity, Classifier.ClassificationResult>>> classificationResults;
    private final Observable<Optional<CrossValidation.CrossValidationResult>> crossValidationResults;

    public ClassificationModel(Observable<? extends Collection<? extends DataEntity>> inputData, AppContext appContext) {

        checkNotNull(inputData, "InputData may not be null!");

        // store the context-reference
        this.appContext = checkNotNull(appContext, "AppContext may not be null!");

        // trigger inputData for changed coordinates, even if data set stays the same!
        // publish to be used by both labeled and unlabeled data without doubling the work.
        ConnectableObservable<? extends Collection<? extends DataEntity>> inputTriggeredOnChangedCoordinates = inputData.observeOn(Schedulers.computation()).switchMap(
            entities -> RxJavaUtils.combineLatest(entities, DataEntity::coordinatesObs).map(__ -> entities).debounce(250, TimeUnit.MILLISECONDS)).publish();

        ConnectableObservable<List<DataEntity>> labeledData = inputTriggeredOnChangedCoordinates.compose(
            RxJavaUtils.deepFilter(DataEntity::classLabelObs, Optional::isPresent)).debounce(250, TimeUnit.MILLISECONDS).publish();

        ConnectableObservable<List<DataEntity>> unlabeledData = inputTriggeredOnChangedCoordinates.compose(
            RxJavaUtils.deepFilter(DataEntity::classLabelObs, label -> !label.isPresent())).debounce(250, TimeUnit.MILLISECONDS).publish();

        inputTriggeredOnChangedCoordinates.connect();

        // initialize the results
        this.classificationResults = initClassificationResults(labeledData, unlabeledData);
        this.crossValidationResults = initCrossValidationResults(labeledData);
        labeledData.connect();
        unlabeledData.connect();

        //TODO remove
        this.classificationResults.subscribe(resultMap -> log.debug("ClassificationModel / ClassificationResults: {}", resultMap));

        //TODO remove
        this.crossValidationResults.subscribe(resultOpt -> {
            if (resultOpt.isPresent()) {
                log.debug("ClassificationModel / CrossValidation: {}", resultOpt.get().getClassificationResults());
            } else {
                System.out.println("ClassificationModel / No CrossValidationResult available ...");
            }
        });


    }

    public Observable<Optional<Map<DataEntity, Classifier.ClassificationResult>>> getClassificationResults() {
        return this.classificationResults;
    }

    public Observable<Optional<CrossValidation.CrossValidationResult>> getCrossValidationResults() {
        return this.crossValidationResults;
    }

    private Observable<Optional<CrossValidation.CrossValidationResult>> initCrossValidationResults(Observable<List<DataEntity>> labeledData) {
        //        return this.settings.getClassificationOnFilteredData().toObservable().switchMap(filteredOnly -> {
        //            if (filteredOnly) {
        //                return this.domainAdapter.getFilteredDomainDataLabeled(labeled);
        //            } else {
        //                return this.domainAdapter.getDomainDataLabeled(labeled);
        //            }
        //        }).switchMap(entities -> // trigger for changed coordinates!
        //                             entities.isEmpty()
        //                             ? Observable.just(Collections.<DomainDataEntity>emptyList())
        //                             : Observable.merge(
        //                                     entities.stream().map(entity -> entity.getCoordinates().toObservable()).collect(Collectors.toList()))
        //                                         .debounce(250, TimeUnit.MILLISECONDS)
        //                                         .map(__ -> entities));
        return labeledData.switchMapSingle(labeledEntities -> {
            if (labeledEntities.size() >= ClassificationModel.CROSS_VALIDATION_FOLDS) {
                log.debug("Creating cross-validation for {} entities...", labeledEntities.size());

                final CrossValidation crossValidation = new CrossValidation(Schedulers.computation(), labeledEntities, labeledEntities.get(0).getFeatureSpace(),
                    WEKA_CLASSIFIER_SUPPLIER, ClassificationModel.CROSS_VALIDATION_FOLDS, ClassificationModel.CROSS_VALIDATION_RUNS);
                return crossValidation.result().map(Optional::of).doOnSubscribe(disposable -> this.appContext.getTaskMonitor().addTask(crossValidation));

            } else {
                log.debug("Not enough labeled entities available ({} < {} folds) for cross-validation...", labeledEntities.size(),
                    ClassificationModel.CROSS_VALIDATION_FOLDS);

                return Single.just(Optional.<CrossValidation.CrossValidationResult>empty());

            }
        }).replay(1).autoConnect();
    }

    private Observable<Optional<Map<DataEntity, Classifier.ClassificationResult>>> initClassificationResults(Observable<List<DataEntity>> labeledData,
        Observable<List<DataEntity>> unlabeledData) {

        //        return this.settings.getClassificationOnFilteredData().toObservable().switchMap(filteredOnly -> {
        //            if (filteredOnly) {
        //                return this.domainAdapter.getFilteredDomainDataLabeled(labeled);
        //            } else {
        //                return this.domainAdapter.getDomainDataLabeled(labeled);
        //            }
        //        }).switchMap(entities -> // trigger for changed coordinates!
        //                             entities.isEmpty()
        //                             ? Observable.just(Collections.<DomainDataEntity>emptyList())
        //                             : Observable.merge(
        //                                     entities.stream().map(entity -> entity.getCoordinates().toObservable()).collect(Collectors.toList()))
        //                                         .debounce(250, TimeUnit.MILLISECONDS)
        //                                         .map(__ -> entities));
        final Observable<Optional<Single<Classifier>>> trainedClassifier = labeledData.map(labeledEntities -> {
            if (labeledEntities.size() > 0) {
                log.debug("Creating classifier for {} entities...", labeledEntities.size());
                return Optional.of(new WekaClassification(Schedulers.computation(), new HashSet<>(labeledEntities), labeledEntities.get(0).getFeatureSpace(),
                    WEKA_CLASSIFIER_SUPPLIER));
            } else {
                log.debug("No labeled entities available to train a classifier...");
                return Optional.<WekaClassification>empty();
            }
        }).map(classifier -> classifier.map(wekaClassification -> {
            return wekaClassification.result().doOnSubscribe(disposable -> this.appContext.getTaskMonitor().addTask(wekaClassification));
        }));

        //        return this.settings.getClassificationOnFilteredData().toObservable().switchMap(filteredOnly -> {
        //            if (filteredOnly) {
        //                return this.domainAdapter.getFilteredDomainDataLabeled(labeled);
        //            } else {
        //                return this.domainAdapter.getDomainDataLabeled(labeled);
        //            }
        //        }).switchMap(entities -> // trigger for changed coordinates!
        //                             entities.isEmpty()
        //                             ? Observable.just(Collections.<DomainDataEntity>emptyList())
        //                             : Observable.merge(
        //                                     entities.stream().map(entity -> entity.getCoordinates().toObservable()).collect(Collectors.toList()))
        //                                         .debounce(250, TimeUnit.MILLISECONDS)
        //                                         .map(__ -> entities));
        return Observable.combineLatest(unlabeledData, trainedClassifier, (unlabeledEntities, classifierOpt) -> {
            if (unlabeledEntities.size() > 0 && classifierOpt.isPresent()) {
                log.debug("Classifying {} unlabeled entities...", unlabeledEntities.size());
                return classifierOpt.get().map(classifier -> classifier.classify(new HashSet<>(unlabeledEntities))).map(Optional::of).toObservable();
            } else {
                return Observable.just(Optional.<Map<DataEntity, Classifier.ClassificationResult>>empty());
            }
        }).switchMap(obs -> obs).replay(1).autoConnect();

    }

}
