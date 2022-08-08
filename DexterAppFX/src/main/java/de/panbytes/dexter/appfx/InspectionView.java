package de.panbytes.dexter.appfx;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import com.ibm.icu.text.RuleBasedNumberFormat;
import de.panbytes.dexter.appfx.misc.WindowSizePersistence;
import de.panbytes.dexter.appfx.scene.control.ProgressBarWithLabel;
import de.panbytes.dexter.core.DexterCore;
import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DataNode.Status;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel;
import de.panbytes.dexter.core.model.classification.Classifier;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.observers.JavaFxObserver;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.io.IOException;
import java.text.Format;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InspectionView {

    private static final Logger log = LoggerFactory.getLogger(InspectionView.class);

    @Deprecated
    private static final String CHECKMARK = "\u2713";
    private final DexterCore dexterCore;
    private final DataEntity inspectionTarget;
    private final Subject<Object> requestCloseView = PublishSubject.create();
    private final Subject<EventObject> windowClosed = PublishSubject.create();
    private final CompositeDisposable lifecycleDisposable = new CompositeDisposable();
    @FXML
    private TextField entityStatusTextField;
    @FXML
    private ToggleButton useSuggestedLabelButton;
    @FXML
    private ToggleButton rejectEntityButton;
    @FXML
    private ToggleButton confirmLabelButton;
    @FXML
    private ProgressBarWithLabel uncertaintyProgressBar;
    @FXML
    private StackedBarChart<Double, String> probabilityChart;
    @FXML
    private BorderPane rootPane;
    @FXML
    private TitledPane domainInspectionDisplayContainer;
    @FXML
    private Button closeInspectionButton;
    @FXML
    private MenuButton labelAsMenuButton;
    @FXML
    private CustomMenuItem enterNewLabelCustomMenuItem;
    @FXML
    private TextField enterNewLabelCustomMenuItemTextField;
    @FXML
    private Button enterNewLabelCustomMenuItemOkButton;
    @FXML
    private TextField entityNameTextField;
    @FXML
    private TextArea entityDescriptionTextArea;
    @FXML
    private TextField entityClassificationTextField;
    @FXML
    private ButtonBar mainButtons;

    private InspectionView(DexterCore dexterCore, DataEntity inspectionTarget) {
        this.dexterCore = dexterCore;
        this.inspectionTarget = inspectionTarget;
    }

    public static InspectionView createAndShow(DexterCore dexterCore, DataEntity inspectionTarget) {
        FXMLLoader fxmlLoader = new FXMLLoader(Preconditions.checkNotNull(InspectionView.class.getResource("InspectionView.fxml"), "fxml resource is null!"));

        InspectionView inspectionView = new InspectionView(dexterCore, inspectionTarget);
        fxmlLoader.setControllerFactory(__ -> inspectionView);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            //            stage.initModality(Modality.APPLICATION_MODAL);

            JavaFxObservable.eventsOf(stage, WindowEvent.WINDOW_HIDDEN).subscribe(inspectionView.windowClosed);

            inspectionView.lifecycleDisposable.add(inspectionView.requestCloseView.subscribe(__ -> stage.close()));

            WindowSizePersistence.loadAndSaveOnClose(stage, InspectionView.class.getSimpleName() + "." + dexterCore.getAppContext()
                                                                                                                   .getSettingsRegistry()
                                                                                                                   .getDomainSettings()
                                                                                                                   .getDomainIdentifier());

            try {
                stage.setScene(new Scene(fxmlLoader.load()));
                stage.show();

            } catch (IOException e) {
                log.warn("Could not load View!", e);
            }
        });

        return inspectionView;
    }

    @FXML
    private void initialize() {

        initGeneralView();
        initDomainView();
        initMainButtons();


        /*
        Selection
         */
        this.dexterCore.getDexterModel().setCurrentInspectionEntity(this.inspectionTarget);
        this.lifecycleDisposable.add(this.windowClosed.subscribe(__ -> {
            this.dexterCore.getDexterModel().setCurrentInspectionEntity(null);
            this.lifecycleDisposable.dispose();
        }));

    }

    private void initGeneralView() {
        this.entityNameTextField.textProperty().bind(JavaFxObserver.toBinding(this.inspectionTarget.getName().toObservable()));

        this.entityDescriptionTextArea.textProperty().bind(JavaFxObserver.toBinding(this.inspectionTarget.getDescription().toObservable()));

        this.entityStatusTextField.textProperty().bind(JavaFxObserver.toBinding(this.inspectionTarget.getStatus().map(Enum::toString)));
        this.lifecycleDisposable.add(inspectionTarget.getStatus().map(status -> {
            switch (status) {
                case ACTIVE:
                    return "ForestGreen";
                case DISABLED:
                    return "LightSteelBlue";
                case REJECTED:
                case INVALID:
                default:
                    return "OrangeRed";
            }
        }).map(color -> "-fx-text-inner-color: " + color + ";").subscribe(entityStatusTextField::setStyle));

        this.entityClassificationTextField.textProperty()
                                          .bind(JavaFxObserver.toBinding(Observable.combineLatest(this.inspectionTarget.getClassLabel()
                                                                                                                       .toObservable()
                                                                                                                       .map(lbl -> lbl.map(ClassLabel::toString)
                                                                                                                                      .orElse(
                                                                                                                                          MainView.EMPTY_CLASS_LABEL)),
                                                                                                  this.dexterCore.getAppContext()
                                                                                                                 .getInspectionHistory()
                                                                                                                 .getLabeledEntities(),
                                                                                                  (label, history) -> label + (
                                                                                                      history.contains(this.inspectionTarget) ? "  ("
                                                                                                                                                + CHECKMARK
                                                                                                                                                + ")" : ""))));

        this.uncertaintyProgressBar.progressProperty()
                                   .bind(JavaFxObserver.toBinding(this.inspectionTarget.getClassLabel()
                                                                                       .toObservable()
                                                                                       .switchMap(classLabelOpt -> classLabelOpt.isPresent()
                                                                                                                   ? this.dexterCore.getDexterModel()
                                                                                                                                    .getActiveLearningModel()
                                                                                                                                    .getExistingLabelsUncertainty()
                                                                                                                                    .map(Collection::stream)
                                                                                                                                    .map(
                                                                                                                                        stream -> stream.filter(
                                                                                                                                            uncertainty -> uncertainty
                                                                                                                                                .getDataEntity()
                                                                                                                                                .equals(
                                                                                                                                                    this.inspectionTarget))
                                                                                                                                                        .findAny())
                                                                                                                                    .map(opt -> opt.map(
                                                                                                                                        ActiveLearningModel.CrossValidationUncertainty::getUncertaintyValue))
                                                                                                                   : this.dexterCore.getDexterModel()
                                                                                                                                    .getActiveLearningModel()
                                                                                                                                    .getClassificationUncertainty()
                                                                                                                                    .map(Collection::stream)
                                                                                                                                    .map(
                                                                                                                                        stream -> stream.filter(
                                                                                                                                            uncertainty -> uncertainty
                                                                                                                                                .getDataEntity()
                                                                                                                                                .equals(
                                                                                                                                                    this.inspectionTarget))
                                                                                                                                                        .findAny())
                                                                                                                                    .map(opt -> opt.map(
                                                                                                                                        ActiveLearningModel.ClassificationUncertainty::getUncertaintyValue)))
                                                                                       .observeOn(JavaFxScheduler.platform())
                                                                                       .map(uncertainty -> uncertainty.orElse(Double.NaN))));

        Format ordinalFormat = new RuleBasedNumberFormat(Locale.UK, RuleBasedNumberFormat.ORDINAL);
        this.lifecycleDisposable.add(this.inspectionTarget.getClassLabel().toObservable().switchMap(classLabelOpt -> {
            Platform.runLater(() -> probabilityChart.getXAxis().setLabel("Probability" + (classLabelOpt.isPresent() ? " (aggregated)" : "")));
            if (classLabelOpt.isPresent()) {
                return this.dexterCore.getDexterModel()
                                      .getClassificationModel()
                                      .getCrossValidationResults()
                                      .map(crossValidationOpt -> crossValidationOpt.flatMap(crossValidationResult -> Optional.ofNullable(
                                          crossValidationResult.getClassificationResults().get(this.inspectionTarget)))
                                                                                   .map(classificationResults -> Streams.mapWithIndex(
                                                                                       classificationResults.stream()
                                                                                                            .map(
                                                                                                                classificationResult -> classificationResult.getClassLabelProbabilities()
                                                                                                                                                            .entrySet()
                                                                                                                                                            .stream()
                                                                                                                                                            .map(
                                                                                                                                                                entry -> new XYChart.Data<>(
                                                                                                                                                                    entry
                                                                                                                                                                        .getValue()
                                                                                                                                                                    / classificationResults
                                                                                                                                                                        .size(),
                                                                                                                                                                    (entry
                                                                                                                                                                         .getKey()
                                                                                                                                                                         .equals(
                                                                                                                                                                             classLabelOpt
                                                                                                                                                                                 .get())
                                                                                                                                                                     ?
                                                                                                                                                                     "\u2B9E"
                                                                                                                                                                     + "  "
                                                                                                                                                                     // arrow current label
                                                                                                                                                                     : "")
                                                                                                                                                                    + entry
                                                                                                                                                                        .getKey()
                                                                                                                                                                        .getLabel()))
                                                                                                                                                            .collect(
                                                                                                                                                                Collectors
                                                                                                                                                                    .toCollection(
                                                                                                                                                                        FXCollections::observableArrayList))),
                                                                                       (chartData, index) -> new XYChart.Series<>("Cross-Validation ("
                                                                                                                                  + ordinalFormat.format(
                                                                                           index + 1)
                                                                                                                                  + " iteration)", chartData))
                                                                                                                        .collect(Collectors.toCollection(
                                                                                                                            FXCollections::observableArrayList))));
            } else {
                return this.dexterCore.getDexterModel()
                                      .getClassificationModel()
                                      .getClassificationResults()
                                      .map(classificationOpt -> classificationOpt.flatMap(
                                          classificationResult -> Optional.ofNullable(classificationResult.get(this.inspectionTarget)))
                                                                                 .map(Classifier.ClassificationResult::getClassLabelProbabilities)
                                                                                 .map(classLabelProbabilities -> classLabelProbabilities.entrySet()
                                                                                                                                        .stream()
                                                                                                                                        .map(
                                                                                                                                            entry -> new XYChart.Data<>(
                                                                                                                                                entry.getValue(),
                                                                                                                                                entry.getKey()
                                                                                                                                                     .getLabel()))
                                                                                                                                        .collect(
                                                                                                                                            Collectors.toCollection(
                                                                                                                                                FXCollections::observableArrayList)))
                                                                                 .map(chartData -> new XYChart.Series<>("Classification", chartData))
                                                                                 .map(FXCollections::singletonObservableList));
            }
        }).observeOn(JavaFxScheduler.platform()).subscribe(seriesListOpt -> {
            this.probabilityChart.setDisable(!seriesListOpt.isPresent());
            this.probabilityChart.setData(seriesListOpt.orElse(FXCollections.emptyObservableList()));

            // sorting
            final Map<String, Double> sums = this.probabilityChart.getData()
                                                                  .stream()
                                                                  .map(XYChart.Series::getData)
                                                                  .flatMap(Collection::stream)
                                                                  .collect(Collectors.groupingBy(XYChart.Data::getYValue,
                                                                                                 Collectors.summingDouble(XYChart.Data::getXValue)));

            ((CategoryAxis) this.probabilityChart.getYAxis()).setCategories(sums.entrySet()
                                                                                .stream()
                                                                                .sorted(Comparator.comparing(Map.Entry::getValue))
                                                                                .map(Map.Entry::getKey)
                                                                                .collect(Collectors.collectingAndThen(Collectors.toList(),
                                                                                                                      FXCollections::observableList)));

            this.probabilityChart.getYAxis().setAutoRanging(true); // otherwise labels aren't changed by sorting, but only bars!

            // adapt chart-height to ensure visible labels; numbers found by trial & error for current layout... :-/
            this.probabilityChart.setPrefHeight(Math.max(((CategoryAxis) this.probabilityChart.getYAxis()).getCategories().size() * 20 + 180, 150));

        }));

    }

    private void initDomainView() {
        Disposable disposable = this.dexterCore.getDomainAdapter()
                                               .getDomainInspectionView(this.inspectionTarget)
                                               .defaultIfEmpty(Optional.empty())
                                               .map(viewOpt -> viewOpt.orElseGet(() -> {
                                                   Label missingInspectionViewLabel = new Label("<No Inspection View available!>");
                                                   missingInspectionViewLabel.setStyle("-fx-background-color: #ffff99;");
                                                   missingInspectionViewLabel.setPadding(new Insets(150.0));
                                                   return missingInspectionViewLabel;
                                               }))
                                               .observeOn(JavaFxScheduler.platform())
                                               .subscribe(domainInspectionDisplayContainer::setContent);
        this.lifecycleDisposable.add(disposable);

    }

    private void initMainButtons() {

        Observable<Optional<ClassLabel>> classLabelObs = this.inspectionTarget.getClassLabel().toObservable();
        Observable<Optional<ClassLabel>> suggestionObs = this.dexterCore.getDexterModel()
                                                                        .getActiveLearningModel()
                                                                        .getModelSuggestedLabels()
                                                                        .map(suggestionMap -> Optional.ofNullable(suggestionMap.get(this.inspectionTarget)));
        Disposable disposable;

        //
        // Label As Menu-Button
        //
        disposable = this.dexterCore.getDomainAdapter().getAllClassLabels().map(TreeSet::new).map(classLabels -> {
            Function<ClassLabel, MenuItem> label2MenuItem = classLabel -> {
                MenuItem menuItem = new MenuItem(classLabel.getLabel());
                this.lifecycleDisposable.add(JavaFxObservable.actionEventsOf(menuItem)
                                                             .doAfterNext(this.requestCloseView::onNext)
                                                             .subscribe(__ -> this.inspectionTarget.setClassLabel(classLabel)));
                return menuItem;
            };
            return classLabels.stream().map(label2MenuItem).collect(Collectors.toList());
        }).observeOn(JavaFxScheduler.platform()).subscribe(menuItems -> {
            this.labelAsMenuButton.getItems().setAll(menuItems);
            this.labelAsMenuButton.getItems().add(new SeparatorMenuItem());
            this.labelAsMenuButton.getItems().add(this.enterNewLabelCustomMenuItem);
        });
        this.lifecycleDisposable.add(disposable);

        disposable = Observable.merge(JavaFxObservable.actionEventsOf(this.enterNewLabelCustomMenuItemTextField),
                                      JavaFxObservable.actionEventsOf(this.enterNewLabelCustomMenuItemOkButton))
                               .map(__ -> this.enterNewLabelCustomMenuItemTextField.getText())
                               .doOnNext(__ -> this.enterNewLabelCustomMenuItemTextField.clear())
                               .doOnNext(__ -> this.labelAsMenuButton.hide())
                               .doAfterNext(this.requestCloseView::onNext)
                               .map(labelText -> labelText.isEmpty() ? Optional.<ClassLabel>empty() : Optional.of(ClassLabel.labelFor(labelText)))
                               .subscribe(newLabel -> this.inspectionTarget.setClassLabel(newLabel.orElse(null)));
        this.lifecycleDisposable.add(disposable);

        //
        // Button: Confirm current Label
        //
        disposable = classLabelObs.map(opt -> "Confirm _current Label" + opt.map(lbl -> " [" + lbl.getLabel() + "]").orElse(""))
                                  .observeOn(JavaFxScheduler.platform())
                                  .subscribe(text -> this.confirmLabelButton.setText(text));
        this.lifecycleDisposable.add(disposable);

        disposable = classLabelObs.map(opt -> !opt.isPresent())
                .observeOn(JavaFxScheduler.platform())
                .subscribe(disable -> this.confirmLabelButton.setDisable(disable));
        this.lifecycleDisposable.add(disposable);

        disposable = JavaFxObservable.actionEventsOf(this.confirmLabelButton)
                                     // make non-toggle
                                     .doOnNext(__ -> this.confirmLabelButton.setSelected(false))
                                     // close window
                                     .doAfterNext(this.requestCloseView::onNext)
                                     // TODO (?) convert to current label (not yet necessary...)
                                     //   .withLatestFrom(classLabelObs, (evt,lbl)->lbl)
                                     // manually add to inspection history, as label isn't changed (which otherwise would trigger history)
                                     .subscribe(__ -> this.dexterCore.getAppContext().getInspectionHistory().markInspected(this.inspectionTarget));
        this.lifecycleDisposable.add(disposable);

        //
        // Button: Use / change to suggested Label
        //
        disposable = Observable.combineLatest(classLabelObs, suggestionObs, //
                                              (label, suggestion) -> label.map(__ -> "Change to").orElse("Use") + " _suggested Label" + suggestion.map(
                                                  s -> " [" + s + "]").orElse(""))
                               .observeOn(JavaFxScheduler.platform())
                               .subscribe(text -> this.useSuggestedLabelButton.setText(text));
        this.lifecycleDisposable.add(disposable);

        disposable = Observable.combineLatest(classLabelObs, suggestionObs, //
                                              (label, suggestion) -> !suggestion.isPresent() || suggestion.equals(label))
                               .observeOn(JavaFxScheduler.platform())
                               .startWith(true)
                               .subscribe(disable -> this.useSuggestedLabelButton.setDisable(disable));
        this.lifecycleDisposable.add(disposable);

        disposable = JavaFxObservable.actionEventsOf(this.useSuggestedLabelButton)
                                     // make non-toggle
                                     .doOnNext(__ -> this.useSuggestedLabelButton.setSelected(false))
                                     // close window
                                     .doAfterNext(this.requestCloseView::onNext)
                                     // convert to current suggestion
                                     .withLatestFrom(suggestionObs, (evt, suggestion) -> suggestion)
                                     // labeling
                                     .subscribe(suggestion -> this.inspectionTarget.setClassLabel(
                                         suggestion.orElseThrow(() -> new IllegalStateException("Suggestion for Labeling is expected to be present!"))));
        this.lifecycleDisposable.add(disposable);

        //
        // Button: REJECT
        //
        disposable = this.inspectionTarget.getStatus()
                                          .map(Status::isProtectedState)
                                          .observeOn(JavaFxScheduler.platform())
                                          .subscribe(this.rejectEntityButton::setDisable);
        this.lifecycleDisposable.add(disposable);

        disposable = JavaFxObservable.actionEventsOf(this.rejectEntityButton)
                                     // make non-toggle
                                     .doOnNext(__ -> this.rejectEntityButton.setSelected(false))
                                     // close window
                                     .doAfterNext(this.requestCloseView::onNext)
                                     // set status
                                     .subscribe(__ -> this.inspectionTarget.setStatus(Status.REJECTED));
        this.lifecycleDisposable.add(disposable);

        //
        // Button: Close
        //
        disposable = JavaFxObservable.actionEventsOf(this.closeInspectionButton).subscribe(this.requestCloseView::onNext);
        this.lifecycleDisposable.add(disposable);

    }


}
