package de.panbytes.dexter.appfx;

import de.panbytes.dexter.core.ClassLabel;
import de.panbytes.dexter.core.DexterCore;
import de.panbytes.dexter.core.activelearning.ActiveLearning;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.model.classification.Classifier;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.observers.JavaFxObserver;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.rxjavafx.sources.Change;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.controlsfx.control.SegmentedButton;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class InspectionView {

    private static final String CHECKMARK = "\u2713";
    private final DexterCore dexterCore;
    private final DataEntity inspectionTarget;
    private final Subject<EventObject> requestCloseView = PublishSubject.create();
    private final Subject<EventObject> windowClosed = PublishSubject.create();
    private final CompositeDisposable lifecycleDisposable = new CompositeDisposable();
    @FXML private TextField uncertaintyTextField;
    @FXML private StackedBarChart<Double, String> probabilityChart;
    @FXML private BorderPane rootPane;
    @FXML private TitledPane domainInspectionDisplayContainer;
    @FXML private Button closeInspectionButton;
    @FXML private MenuButton labelAsMenuButton;
    @FXML private CustomMenuItem enterNewLabelCustomMenuItem;
    @FXML private TextField enterNewLabelCustomMenuItemTextField;
    @FXML private Button enterNewLabelCustomMenuItemOkButton;
    @FXML private TextField entityNameTextField;
    @FXML private TextArea entityDescriptionTextArea;
    @FXML private TextField entityClassificationTextField;
    @FXML private ButtonBar mainButtons;

    private InspectionView(DexterCore dexterCore, DataEntity inspectionTarget) {
        this.dexterCore = dexterCore;
        this.inspectionTarget = inspectionTarget;
    }

    public static InspectionView createAndShow(DexterCore dexterCore, DataEntity inspectionTarget) {
        FXMLLoader fxmlLoader = new FXMLLoader(InspectionView.class.getResource("InspectionView.fxml"));

        InspectionView inspectionView = new InspectionView(dexterCore, inspectionTarget);
        fxmlLoader.setControllerFactory(__ -> inspectionView);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);

            JavaFxObservable.eventsOf(stage, WindowEvent.WINDOW_HIDDEN).subscribe(inspectionView.windowClosed);

            inspectionView.lifecycleDisposable.add(inspectionView.requestCloseView.subscribe(__ -> stage.close()));

            try {
                stage.setScene(new Scene(fxmlLoader.load()));
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
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
        this.entityClassificationTextField.textProperty()
                                          .bind(JavaFxObserver.toBinding(Observable.combineLatest(this.inspectionTarget.getClassLabel()
                                                                                                                       .toObservable()
                                                                                                                       .map(lbl -> lbl.map(
                                                                                                                               ClassLabel::toString)
                                                                                                                                      .orElse(MainView.EMPTY_CLASS_LABEL)),
                                                                                                  this.dexterCore.getAppContext()
                                                                                                                 .getInspectionHistory()
                                                                                                                 .getLabeledEntities()
                                                                                                                 .toObservable(),
                                                                                                  (label, history) -> label + (history.contains(
                                                                                                          this.inspectionTarget)
                                                                                                                               ? "  (" + CHECKMARK + ")"
                                                                                                                               : ""))));

        this.uncertaintyTextField.textProperty().bind(JavaFxObserver.toBinding(
        this.inspectionTarget.getClassLabel()
                                                          .toObservable()
                                                          .switchMap(classLabelOpt -> classLabelOpt.isPresent()
                                                                                      ? this.dexterCore.getDexterModel()
                                                                                                       .getActiveLearning()
                                                                                                       .getExistingLabelsUncertainty()
                                                                                                       .map(Collection::stream)
                                                                                                       .map(stream -> stream.filter(
                                                                                                               uncertainty -> uncertainty.getDataEntity()
                                                                                                                                         .equals(this.inspectionTarget))
                                                                                                                            .findAny())
                                                                                                       .map(opt -> opt.map(
                                                                                                               ActiveLearning.CrossValidationUncertainty::getUncertaintyValue))
                                                                                      : this.dexterCore.getDexterModel()
                                                                                                       .getActiveLearning()
                                                                                                       .getClassificationUncertainty()
                                                                                                       .map(Collection::stream)
                                                                                                       .map(stream -> stream.filter(
                                                                                                               uncertainty -> uncertainty.getDataEntity()
                                                                                                                                         .equals(this.inspectionTarget))
                                                                                                                            .findAny())
                                                                                                       .map(opt -> opt.map(
                                                                                                               ActiveLearning.ClassificationUncertainty::getUncertaintyValue)))
                                                          .observeOn(JavaFxScheduler.platform())
                                                          .map(uncertainty -> uncertainty.map(val -> val * 100)
                                                                                               .map(u -> String.format("%.1f %%", u))
                                                                                               .orElse("??"))));


        this.lifecycleDisposable.add(this.inspectionTarget.getClassLabel().toObservable().switchMap(classLabelOpt -> {
            if (classLabelOpt.isPresent()) {
                return this.dexterCore.getDexterModel()
                                      .getClassificationModel()
                                      .getCrossValidationResults()
                                      .map(crossValidationOpt -> crossValidationOpt.flatMap(crossValidationResult -> Optional.ofNullable(
                                              crossValidationResult.getClassificationResults().get(this.inspectionTarget)))
                                                                                   .map(classificationResults -> classificationResults.stream()
                                                                                                                                      .map(classificationResult -> classificationResult
                                                                                                                                              .getClassLabelProbabilities()
                                                                                                                                              .entrySet()
                                                                                                                                              .stream()
                                                                                                                                              .map(entry -> new XYChart.Data<>(
                                                                                                                                                      entry.getValue(),
                                                                                                                                                      (entry.getKey()
                                                                                                                                                            .equals(classLabelOpt
                                                                                                                                                                            .get())
                                                                                                                                                       ? "\u2B9E" + "  "
                                                                                                                                                       // arrow current label
                                                                                                                                                       : "") + entry
                                                                                                                                                              .getKey()
                                                                                                                                                              .getLabel()))
                                                                                                                                              .collect(
                                                                                                                                                      Collectors
                                                                                                                                                              .toCollection(
                                                                                                                                                                      FXCollections::observableArrayList)))
                                                                                                                                      .map(chartData -> new XYChart.Series<>(
                                                                                                                                              "",
                                                                                                                                              chartData))
                                                                                                                                      .collect(
                                                                                                                                              Collectors
                                                                                                                                                      .toCollection(
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
                                                                                                                                        .map(entry -> new XYChart.Data<>(
                                                                                                                                                entry.getValue(),
                                                                                                                                                entry.getKey()
                                                                                                                                                     .getLabel()))
                                                                                                                                        .collect(
                                                                                                                                                Collectors
                                                                                                                                                        .toCollection(
                                                                                                                                                                FXCollections::observableArrayList)))
                                                                                 .map(chartData -> new XYChart.Series<>("", chartData))
                                                                                 .map(FXCollections::singletonObservableList));
            }
        }).subscribe(seriesListOpt -> {
            this.probabilityChart.setDisable(!seriesListOpt.isPresent());
            this.probabilityChart.setData(seriesListOpt.orElse(FXCollections.emptyObservableList()));


            // sorting
            final Map<String, Double> sums = this.probabilityChart.getData()
                                                                  .stream()
                                                                  .map(XYChart.Series::getData)
                                                                  .flatMap(Collection::stream)
                                                                  .collect(Collectors.groupingBy(XYChart.Data::getYValue,
                                                                                                 Collectors.summingDouble(
                                                                                                         XYChart.Data::getXValue)));

            ((CategoryAxis) this.probabilityChart.getYAxis()).setCategories(sums.entrySet()
                                                                                .stream()
                                                                                .sorted(Comparator.comparing(Map.Entry::getValue))
                                                                                .map(Map.Entry::getKey)
                                                                                .collect(Collectors.collectingAndThen(Collectors.toList(),
                                                                                                                      FXCollections::observableList)));

            this.probabilityChart.getYAxis().setAutoRanging(true); // otherwise labels aren't changed by sorting, but only bars!
        }));

    }

    private void initDomainView() {
        this.domainInspectionDisplayContainer.setContent(
                this.dexterCore.getDomainAdapter().getDomainInspectionView(this.inspectionTarget).orElseGet(() -> {
                    Label missingInspectionViewLabel = new Label("<No Inspection View available!>");
                    missingInspectionViewLabel.setStyle("-fx-background-color: #ffff99;");
                    missingInspectionViewLabel.setPadding(new Insets(150.0));
                    return missingInspectionViewLabel;
                }));
    }

    private void initMainButtons() {
        this.lifecycleDisposable.add(this.inspectionTarget.getClassLabel().toObservable().subscribe(currentLabel -> {
            SegmentedButton segmentedButton = new SegmentedButton();

            if (currentLabel.isPresent()) {
                // Button: Confirm current Label
                ToggleButton confirmLabelButton = new ToggleButton("Confirm current Label [" + currentLabel.get().getLabel() + "]");
                confirmLabelButton.addEventHandler(ActionEvent.ACTION, __ -> confirmLabelButton.setSelected(false)); // make non-toggle

                JavaFxObservable.actionEventsOf(confirmLabelButton)
                                // manually add to inspection history, as label isn't changed (which otherwise would trigger history)
                                .doOnNext(__ -> this.dexterCore.getAppContext().getInspectionHistory().markInspected(this.inspectionTarget))
                                .subscribe(this.requestCloseView);

                segmentedButton.getButtons().addAll(confirmLabelButton);
            }

            // Button: Label as suggested by Model

            ToggleButton labelAsSuggestedButton = new ToggleButton("Label as suggested by Model"); // TODO
            this.lifecycleDisposable.add(this.dexterCore.getDexterModel()
                                                        .getActiveLearning()
                                                        .getModelSuggestedLabels()
                                                        .map(suggestionMap -> Optional.ofNullable(suggestionMap.get(this.inspectionTarget)))
                                                        .subscribe(suggestion -> {
                                                            if (suggestion.isPresent()) {
                                                                labelAsSuggestedButton.setText(
                                                                        "Label as suggested by Model [" + suggestion.get()
                                                                                                                    .getLabel() + "]");
                                                                labelAsSuggestedButton.setDisable(
                                                                        currentLabel.isPresent() && currentLabel.get()
                                                                                                                .equals(suggestion.get()));
                                                                labelAsSuggestedButton.setOnAction(
                                                                        __ -> this.inspectionTarget.setClassLabel(suggestion.get()));
                                                                //TODO handler: label as
                                                            } else {
                                                                labelAsSuggestedButton.setText(
                                                                        "Label as suggested by Model [" + "n/a" + "]");
                                                                labelAsSuggestedButton.setDisable(true);
                                                                labelAsSuggestedButton.setOnAction(null);
                                                            }
                                                        }));

            labelAsSuggestedButton.addEventHandler(ActionEvent.ACTION, __ -> labelAsSuggestedButton.setSelected(false)); // make non-toggle

            //TODO
            JavaFxObservable.actionEventsOf(labelAsSuggestedButton).doOnNext(__ -> {
                if (currentLabel.isPresent()) {

                } else {

                }
            }).subscribe(this.requestCloseView);

            segmentedButton.getButtons().addAll(labelAsSuggestedButton);

            //            } else {
            //                // Button: Confirm suggested
            //                ToggleButton confirmSuggestionButton = new ToggleButton("Confirm suggested Label [" + "TODO" + "]"); // TODO
            //                confirmSuggestionButton.addEventHandler(ActionEvent.ACTION,
            //                                                        __ -> confirmSuggestionButton.setSelected(false)); // make non-toggle
            //                confirmSuggestionButton.setDisable(true);
            //
            //                JavaFxObservable.actionEventsOf(confirmSuggestionButton).subscribe(requestCloseView);
            //
            //                segmentedButton.getButtons().addAll(confirmSuggestionButton);
            //            }


            // Button: REJECT
            ToggleButton rejectEntityButton = new ToggleButton("Reject Entity");
            rejectEntityButton.addEventHandler(ActionEvent.ACTION, __ -> rejectEntityButton.setSelected(false)); // make non-toggle

            JavaFxObservable.actionEventsOf(rejectEntityButton)
                            .doOnNext(actionEvent -> this.inspectionTarget.setClassLabel(ClassLabel.labelFor(this.dexterCore.getAppContext()
                                                                                                                            .getSettingsRegistry()
                                                                                                                            .getDomainSettings()
                                                                                                                            .rejectedClassLabel()
                                                                                                                            .getValue())))
                            .subscribe(this.requestCloseView);

            segmentedButton.getButtons().addAll(rejectEntityButton);


            // ASSEMBLE ButtonBar
            ButtonBar.setButtonData(segmentedButton, ButtonBar.ButtonData.OK_DONE);
            ButtonBar.setButtonUniformSize(segmentedButton, false);
            this.mainButtons.getButtons().addAll(segmentedButton);
        }));


        this.lifecycleDisposable.add(
                JavaFxObservable.changesOf(this.labelAsMenuButton.showingProperty()).filter(Change::getNewVal).subscribe(__ -> {
                    Platform.runLater(() -> { // necessary to first get the menu visible.
                        this.enterNewLabelCustomMenuItemTextField.requestFocus();
                    });
                }));
        this.enterNewLabelCustomMenuItemTextField.setOnAction(__ -> {/* do nothing - workaround to not weirdly trigger above MenuItem */});
        this.enterNewLabelCustomMenuItemOkButton.setOnAction(__ -> {/* do nothing - workaround to not weirdly trigger above MenuItem */});

        this.lifecycleDisposable.add(this.dexterCore.getDomainAdapter()
                                                    .getClassLabels()
                                                    .map(listWithOptional -> listWithOptional.stream()
                                                                                             .filter(Optional::isPresent)
                                                                                             .map(Optional::get)
                                                                                             .filter(label -> !label.getLabel()
                                                                                                                    .equals(this.dexterCore.getAppContext()
                                                                                                                                           .getSettingsRegistry()
                                                                                                                                           .getDomainSettings()
                                                                                                                                           .rejectedClassLabel()
                                                                                                                                           .getValue()))
                                                                                             .collect(Collectors.toList()))
                                                    .observeOn(JavaFxScheduler.platform())
                                                    .subscribe(classLabels -> {
                                                        this.labelAsMenuButton.getItems().retainAll(this.enterNewLabelCustomMenuItem);
                                                        this.labelAsMenuButton.getItems().add(0, new SeparatorMenuItem());

                                                        this.labelAsMenuButton.getItems().addAll(0, classLabels.stream().map(classLabel -> {
                                                            MenuItem menuItem = new MenuItem(classLabel.getLabel());
                                                            this.lifecycleDisposable.add(JavaFxObservable.actionEventsOf(menuItem)
                                                                                                         .doOnNext(__ -> System.out.println(
                                                                                                                 "Label as " + classLabel.getLabel()))
                                                                                                         .doAfterNext(
                                                                                                                 __ -> this.labelAsMenuButton
                                                                                                                         .getScene()
                                                                                                                         .getWindow()
                                                                                                                         .hide())
                                                                                                         .subscribe(
                                                                                                                 __ -> this.inspectionTarget
                                                                                                                         .setClassLabel(
                                                                                                                                 classLabel))); // TODO
                                                            return menuItem;
                                                        }).collect(Collectors.toList()));
                                                    }));

        JavaFxObservable.actionEventsOf(this.closeInspectionButton).subscribe(this.requestCloseView);

        this.lifecycleDisposable.add(Observable.merge(JavaFxObservable.actionEventsOf(this.enterNewLabelCustomMenuItemTextField),
                                                      JavaFxObservable.actionEventsOf(this.enterNewLabelCustomMenuItemOkButton))
                                               .map(__ -> this.enterNewLabelCustomMenuItemTextField.getText())
                                               .doAfterNext(__ -> this.enterNewLabelCustomMenuItemTextField.setText(""))
                                               .doAfterNext(__ -> this.labelAsMenuButton.hide())
                                               .doAfterNext(
                                                       labelText -> System.out.println("Entered new Label: '" + labelText + "'")) //TODO
                                               .doAfterNext(__ -> this.labelAsMenuButton.getScene().getWindow().hide())
                                               .map(labelText -> labelText.isEmpty()
                                                                 ? Optional.<ClassLabel>empty()
                                                                 : Optional.of(ClassLabel.labelFor(labelText)))
                                               .subscribe(newLabel -> this.inspectionTarget.setClassLabel(newLabel.orElse(null)))); //TODO


    }


}
