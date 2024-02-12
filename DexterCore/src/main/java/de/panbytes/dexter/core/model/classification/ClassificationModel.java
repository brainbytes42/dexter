package de.panbytes.dexter.core.model.classification;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.trees.RandomForest;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public class ClassificationModel {

    private static final Logger log = LoggerFactory.getLogger(ClassificationModel.class);
    private static final Supplier<weka.classifiers.Classifier> WEKA_CLASSIFIER_SUPPLIER = RandomForest::new;
    private final AppContext appContext;
    private final Observable<Optional<Map<DataEntity, Classifier.ClassificationResult>>> classificationResults;
    private final Observable<Optional<CrossValidation.CrossValidationResult>> crossValidationResults;
    private final Observable<? extends Set<? extends DataEntity>> inputData;

    public ClassificationModel(Observable<? extends Set<? extends DataEntity>> inputData, AppContext appContext, Observable<Boolean> modelUpdateEnabled) {

        this.inputData = checkNotNull(inputData, "InputData may not be null!");

        // store the context-reference
        this.appContext = checkNotNull(appContext, "AppContext may not be null!");

        // filter entities based on class label (e.g. has class label <=> Optional::isPresent)
        Function<Predicate<Optional<ClassLabel>>,
                ObservableTransformer<Set<? extends DataEntity>, Set<DataEntity>>> labelFilterTrafo =
                labelFilterPredicate ->
                        upstream -> upstream.compose(RxJavaUtils.deepFilter(DataEntity::classLabelObs, labelFilterPredicate))
                                            .debounce(250, TimeUnit.MILLISECONDS)
                                            .distinctUntilChanged();

        // trigger update if coordinates changed, even if dataset remained the same
        ObservableTransformer<Set<DataEntity>, Set<DataEntity>> triggerCoordinatesChanges =
                upstream -> upstream.switchMap(entities -> RxJavaUtils.combineLatest(entities, DataEntity::coordinatesObs)
                                                                      .map(__ -> entities)
                                                                      .debounce(250, TimeUnit.MILLISECONDS));

        // compose transformers to get labeled and unlabeled data
        Observable<Set<DataEntity>> labeledData = inputData.observeOn(Schedulers.io())
                                                           .compose(labelFilterTrafo.apply(Optional::isPresent))
                                                           .compose(triggerCoordinatesChanges)
                                                           .doOnNext(labeledEntities -> log.trace("ClassificationModel has {} labeled entities available.", labeledEntities.size()))
                                                           .replay(1).autoConnect();
        Observable<Set<DataEntity>> unlabeledData = inputData.observeOn(Schedulers.io())
                                                             .compose(labelFilterTrafo.apply(Predicates.not(Optional::isPresent)))
                                                             .compose(triggerCoordinatesChanges)
                                                             .doOnNext(unlabeledEntities -> log.trace("ClassificationModel has {} unlabeled entities available.", unlabeledEntities.size()))
                                                             .replay(1).autoConnect();

        // initialize the results
        this.classificationResults = initClassificationResults(labeledData, unlabeledData, modelUpdateEnabled);
        this.crossValidationResults = initCrossValidationResults(labeledData, modelUpdateEnabled);

    }

    public Observable<Optional<Map<DataEntity, Classifier.ClassificationResult>>> getClassificationResults() {
        return this.classificationResults;
    }

    public Observable<Optional<CrossValidation.CrossValidationResult>> getCrossValidationResults() {
        return this.crossValidationResults;
    }

    private Observable<Optional<CrossValidation.CrossValidationResult>> initCrossValidationResults(Observable<Set<DataEntity>> labeledData, Observable<Boolean> updateEnabled) {

        final Observable<Integer> crossValidationRuns = appContext.getSettingsRegistry().getGeneralSettings().getCrossValidationRuns().toObservable();
        final Observable<Integer> crossValidationFolds = appContext.getSettingsRegistry().getGeneralSettings().getCrossValidationFolds().toObservable();


        Observable<Optional<CrossValidation>> crossvalidation = Observable
                .combineLatest(labeledData, crossValidationRuns, crossValidationFolds, (data, cvRuns, cvFolds) -> {
                    if (!data.isEmpty()) {
                        try {
                            final CrossValidation cv = new CrossValidation(Schedulers.computation(), data, data.iterator().next().getFeatureSpace(), WEKA_CLASSIFIER_SUPPLIER,
                                    cvFolds, cvRuns);
                            return Optional.of(cv);
                        } catch (Exception e) {
                            log.warn("Could not create CrossValidation!", e);
                        }
                    }
                    return Optional.<CrossValidation>empty();
                })
                .debounce(__ -> updateEnabled.filter(Boolean::booleanValue));  // pause ("debounce") while updates are disabled

        return crossvalidation.switchMapSingle(cvOpt ->
                        cvOpt.map(cv -> cv
                                        .result() // triggers processing
                                        .doOnSubscribe(__ -> log.debug("Running CrossValidation..."))
                                        .doOnSubscribe(__ -> this.appContext.getTaskMonitor().addTask(cv))
                                        .map(Optional::of))
                                .orElse(Single.just(Optional.empty()))
                )
                .doOnNext(resultOpt -> log.debug("CrossValidation Results: {}", resultOpt.map(
                        result -> String.format("%,d Entities mapped.", result.getClassificationResults().size())).orElse("n/a")))
                .onErrorReturn(throwable -> {
                    log.warn("Error on CrossValidation!", throwable);
                    appContext.getErrorHandler().onNext(new AppContext.ErrorContext(this, throwable));
                    return Optional.empty();
                })
                .startWith(Optional.empty())
                .replay(1)
                .autoConnect();


//        return crossvalidation
//                .switchMap(cvOpt -> cvOpt
//                        .map(crossValidation -> updateEnabled.distinctUntilChanged().switchMapMaybe(enabled -> { // do not update if disabled!
//                            if (enabled) {
//                                return crossValidation
//                                        .result()
//                                        .map(Optional::of)
//                                        .doOnSubscribe(disposable -> this.appContext.getTaskMonitor().addTask(crossValidation))
//                                        .toMaybe();
//                            } else {
//                                log.debug("Model-Update is disabled: Omit CrossValidation.");
//                                return Maybe.empty();
//                            }
//                        }).distinctUntilChanged().replay(1).autoConnect(0))
//                        .orElse(Observable.just(Optional.empty())))
//                .doOnNext(resultOpt->log.debug("CrossValidation Results: {}", resultOpt.map(result->String.format("%,d Entities mapped.",result.getClassificationResults().size())).orElse("n/a")))
//                .replay(1)
//                .autoConnect();



//        return Observable
//            .combineLatest(labeledData, crossValidationRuns, crossValidationFolds, (data, cvRuns, cvFolds) -> {
//                if (!data.isEmpty()) {
//                    try {
//                        final CrossValidation cv = new CrossValidation(Schedulers.computation(), data, data.get(0).getFeatureSpace(), WEKA_CLASSIFIER_SUPPLIER,
//                                                                       cvFolds, cvRuns);
//                        return Optional.of(cv);
//                    } catch (Exception e) {
//                        log.warn("Could not create CrossValidation!", e);
//                    }
//                }
//                return Optional.<CrossValidation>empty();
//            })
//            .switchMap(cvOpt -> cvOpt
//                .map(crossValidation -> updateEnabled.distinctUntilChanged().switchMapMaybe(enabled -> { // do not update if disabled!
//                    if (enabled) {
//                        return crossValidation
//                                .result()
//                                .map(Optional::of)
//                                .doOnSubscribe(disposable -> this.appContext.getTaskMonitor().addTask(crossValidation))
//                                .toMaybe();
//                    } else {
//                        log.debug("Model-Update is disabled: Omit CrossValidation.");
//                        return Maybe.empty();
//                    }
//                }).distinctUntilChanged().replay(1).autoConnect(0))
//                .orElse(Observable.just(Optional.empty())))
//            .doOnNext(resultOpt->log.debug("CrossValidation Results: {}", resultOpt.map(result->String.format("%,d Entities mapped.",result.getClassificationResults().size())).orElse("n/a")))
//            .replay(1)
//            .autoConnect();


//        return labeledData.switchMapSingle(labeledEntities -> {
//            if (labeledEntities.size() >= ClassificationModel.CROSS_VALIDATION_FOLDS) {
//                log.debug("Creating cross-validation for {} entities...", labeledEntities.size());
//
//                final CrossValidation crossValidation = new CrossValidation(Schedulers.computation(), labeledEntities, labeledEntities.get(0).getFeatureSpace(),
//                                                                            WEKA_CLASSIFIER_SUPPLIER, ClassificationModel.CROSS_VALIDATION_FOLDS,
//                                                                            ClassificationModel.CROSS_VALIDATION_RUNS);
//                return crossValidation.result().map(Optional::of).doOnSubscribe(disposable -> this.appContext.getTaskMonitor().addTask(crossValidation));
//
//            } else {
//                log.debug("Not enough labeled entities available ({} < {} folds) for cross-validation...", labeledEntities.size(),
//                          ClassificationModel.CROSS_VALIDATION_FOLDS);
//
//                return Single.just(Optional.<CrossValidation.CrossValidationResult>empty());
//
//            }
//        }).replay(1).autoConnect();
    }

    private Observable<Optional<Map<DataEntity, Classifier.ClassificationResult>>> initClassificationResults(Observable<Set<DataEntity>> labeledData,
                                                             Observable<Set<DataEntity>> unlabeledData, Observable<Boolean> updateEnabled) {

        Observable<Boolean> enabledAndNecessary = Observable.combineLatest(updateEnabled, unlabeledData, (enabled, unlabeled) -> enabled && !unlabeled.isEmpty()).replay(1).autoConnect();

        Observable<Optional<Classifier>> trainedClassifier = labeledData.debounce(__ -> enabledAndNecessary.filter(Boolean::booleanValue).delay(250, TimeUnit.MILLISECONDS))  // pause ("debounce") while updates are disabled
                .switchMapSingle(labeledEntities -> {
                    if (!labeledEntities.isEmpty()) {
                        log.debug("Preparing classifier for {} entities...", labeledEntities.size());
                        WekaClassification classification = new WekaClassification(Schedulers.computation(), new HashSet<>(labeledEntities), labeledEntities.iterator().next().getFeatureSpace(), WEKA_CLASSIFIER_SUPPLIER);
                        Single<Classifier> classifier = classification
                                .result() // triggers training
                                .doOnSubscribe(__ -> log.debug("Running Classification..."))
                                .doOnSubscribe(__ -> this.appContext.getTaskMonitor().addTask(classification));
                        return classifier.map(Optional::of);
                    } else {
                        log.debug("No labeled entities available to train a classifier...");
                        return Single.just(Optional.<Classifier>empty());
                    }
                });

        return Observable.combineLatest(unlabeledData, trainedClassifier, (unlabeledEntities, classifierOpt) -> {
                    if (!unlabeledEntities.isEmpty()) {
                        return classifierOpt.map(classifier -> {
                            log.debug("Classifiying {} unlabeled entities...", unlabeledEntities.size());
                            return classifier.classify(new HashSet<>(unlabeledEntities));
                        });
                    } else {
                        return Optional.<Map<DataEntity, Classifier.ClassificationResult>>empty();
                    }
                }).doOnNext(results -> log.debug("Classification Results: {}", results.map(
                        map -> String.format("%,d Entities mapped.", map.size())).orElse("n/a")))
                .onErrorReturn(throwable -> {
                    log.warn("Error on Classification!", throwable);
                    appContext.getErrorHandler().onNext(new AppContext.ErrorContext(this, throwable));
                    return Optional.empty();
                })
                .startWith(Optional.empty())
                .replay(1).autoConnect();

    }

    public Observable<? extends Collection<? extends DataEntity>> getInputData() {
        return inputData;
    }
}
