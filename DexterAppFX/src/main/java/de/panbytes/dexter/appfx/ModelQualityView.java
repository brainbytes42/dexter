package de.panbytes.dexter.appfx;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ArrayTable;
import de.panbytes.dexter.appfx.misc.WindowSizePersistence;
import de.panbytes.dexter.core.ClassLabel;
import de.panbytes.dexter.core.DexterCore;
import de.panbytes.dexter.core.activelearning.ActiveLearning;
import de.panbytes.dexter.core.model.classification.ModelEvaluation;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.io.IOException;
import java.text.Collator;
import java.util.Comparator;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.converter.PercentageStringConverter;

public class ModelQualityView extends Application {

    //private final Observable<Optional<CrossValidation.CrossValidationResult>> crossValidationResults;
    private final DexterCore dexterCore;
    //    private final Subject<EventObject> requestCloseView = PublishSubject.create();
    private final Subject<EventObject> windowClosed = PublishSubject.create();
    private final CompositeDisposable lifecycleDisposable = new CompositeDisposable();
    private final Observable<Optional<ModelEvaluation>> modelEvaluation;
    private final DoubleProperty confusionMatrixHeight = new SimpleDoubleProperty(300);
    private final DoubleProperty accuracyMatrixHeight = new SimpleDoubleProperty(300);
    @FXML private BorderPane confusionMatrixPane;
    @FXML private BorderPane dataSetOverviewPane;
    @FXML private BorderPane modelEvaluationPane;
    @FXML private BorderPane uncertaintyPane;

    private ModelQualityView(Observable<Optional<ModelEvaluation>> modelEvaluation, DexterCore dexterCore) {
        this.modelEvaluation = checkNotNull(modelEvaluation);
        this.dexterCore = checkNotNull(dexterCore);
    }


    static ModelQualityView createAndShow(Observable<Optional<ModelEvaluation>> modelEvaluation, DexterCore dexterCore) {
        FXMLLoader fxmlLoader = new FXMLLoader(ModelQualityView.class.getResource("ModelQualityView.fxml"));

        ModelQualityView modelQualityView = new ModelQualityView(modelEvaluation, dexterCore);
        fxmlLoader.setControllerFactory(__ -> modelQualityView);

        Platform.runLater(() -> {
            Stage stage = new Stage();
//            stage.initModality(Modality.APPLICATION_MODAL);

            JavaFxObservable.eventsOf(stage, WindowEvent.WINDOW_HIDDEN).subscribe(modelQualityView.windowClosed);

            //            modelQualityView.requestCloseView.subscribe(__ -> stage.close());

            WindowSizePersistence.loadAndSaveOnClose(stage,
                ModelQualityView.class.getSimpleName() + "." + dexterCore.getAppContext()
                    .getSettingsRegistry().getDomainSettings().getDomainIdentifier());

            try {
                final Scene scene = new Scene(fxmlLoader.load());
                //                scene.getStylesheets().add(ModelQualityView.class.getResource("ModelQualityView.css").toExternalForm());

                stage.setScene(scene);
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return modelQualityView;
    }

    @FXML
    private void initialize() {

        this.lifecycleDisposable.add(this.windowClosed.subscribe(evt -> this.lifecycleDisposable.dispose()));

        initConfusionMatrixView();
        initDataSetOverviewView();
        initModelEvaluationView();
        initClassificationUncertaintyView();

    }

    private void initClassificationUncertaintyView() {

        final int bucketSize = 10;

        Observable.combineLatest(this.dexterCore.getDexterModel().getActiveLearning().getExistingLabelsUncertainty(),
                                 this.dexterCore.getDexterModel().getActiveLearning().getClassificationUncertainty(),
                                 (existingUncertainty, unlabeledUncertainty) -> {

                                     final HashMap<String, List<Double>> uncertainties = new HashMap<>();
                                     uncertainties.put("labeled", existingUncertainty.stream()
                                                                                     .map(ActiveLearning.CrossValidationUncertainty::getUncertaintyValue)
                                                                                     .collect(Collectors.toList()));
                                     uncertainties.put("unlabeled", unlabeledUncertainty.stream()
                                                                                        .map(ActiveLearning.ClassificationUncertainty::getUncertaintyValue)
                                                                                        .collect(Collectors.toList()));


                                     return uncertainties;
                                 }).map(uncertainties -> {
            final ObservableList<XYChart.Series<String, Number>> series = uncertainties.entrySet()
                                                                                       .stream()
                                                                                       .filter(entry->!entry.getValue().isEmpty())
                                                                                       .sorted(Comparator.comparing(Map.Entry::getKey))
                                                                                       .map(entry -> {
                                                                                           final HashMap<Double, Long> histogram = new HashMap<>(
                                                                                                   entry.getValue()
                                                                                                        .stream()
                                                                                                        .collect(Collectors.groupingBy(
                                                                                                                p -> Math.max(
                                                                                                                        0, Math.min(
                                                                                                                                100 - bucketSize,
                                                                                                                                Math.floor(
                                                                                                                                        p * 100 / bucketSize) * bucketSize)),
                                                                                                                Collectors.counting())));
                                                                                           for (int i = 0; i < 100 / bucketSize; i++) {
                                                                                               histogram.putIfAbsent(
                                                                                                       (double) (i * bucketSize), 0L);
                                                                                           }
                                                                                           final ObservableList<XYChart.Data<String, Number>> data = histogram
                                                                                                   .entrySet()
                                                                                                   .stream()
                                                                                                   .sorted(Comparator.comparing(
                                                                                                           Map.Entry::getKey,
                                                                                                           Comparator.reverseOrder()))
                                                                                                   .map(bucket -> new XYChart.Data<String, Number>(
                                                                                                           String.format("> %.0f %%",
                                                                                                                         bucket.getKey()),
                                                                                                           bucket.getValue() / (double) histogram
                                                                                                                   .values()
                                                                                                                   .stream()
                                                                                                                   .mapToLong(val -> val)
                                                                                                                   .sum()))
                                                                                                   .collect(Collectors.collectingAndThen(
                                                                                                           Collectors.toList(),
                                                                                                           FXCollections::observableList));
                                                                                           return new XYChart.Series<>(entry.getKey(),
                                                                                                                       data);
                                                                                       })
                                                                                       .collect(Collectors.collectingAndThen(
                                                                                               Collectors.toList(),
                                                                                               FXCollections::observableList));
            NumberAxis numberAxis = new NumberAxis();
            numberAxis.setLabel("count / total");
            numberAxis.setTickLabelFormatter(new PercentageStringConverter());
            CategoryAxis categoryAxis = new CategoryAxis();
            categoryAxis.setLabel("uncertainty-range");
            return new BarChart<>(categoryAxis, numberAxis, series);
        }).observeOn(JavaFxScheduler.platform()).subscribe(chart -> {
            chart.prefHeightProperty().bind(Bindings.max(250, this.accuracyMatrixHeight));
            this.uncertaintyPane.setCenter(chart);
        });


        //        final XYChart.Series<String, Number> seriesLabeled = new XYChart.Series<>();
        //        seriesLabeled.setName("labeled");
        //        seriesLabeled.dataProperty()
        //                     .bind(JavaFxObserver.toBinding(this.dexterCore.getDexterModel()
        //                                                                   .getActiveLearning()
        //                                                                   .getExistingLabelsUncertainty()
        //                                                                   .map(Collection::stream)
        //                                                                   .map(stream -> stream.map(
        //                                                                           ActiveLearning.CrossValidationUncertainty::getUncertaintyValue)
        //                                                                                        // TODO below is same as for unlabeled...
        //                                                                                        .map(p -> p * 100 - Double.MIN_VALUE/*make 1 -> 0.9999..*/)
        //                                                                                        .collect(Collectors.groupingBy(p -> Math.floor(
        //                                                                                                p / bucketSize) * bucketSize,
        //                                                                                                                       Collectors.counting())))
        //                                                                   .map(countMap -> {
        //                                                                       final HashMap<Double, Long> histogram = new HashMap<>(countMap);
        //                                                                       for (int i = 0; i < 100 / bucketSize; i++) {
        //                                                                           histogram.putIfAbsent((double) (i * bucketSize), 0L);
        //                                                                       }
        //                                                                       return histogram;
        //                                                                   })
        //                                                                   .observeOn(JavaFxScheduler.platform())
        //                                                                   .map(histogram -> histogram.entrySet()
        //                                                                                              .stream()
        //                                                                                              .sorted(Comparator.comparing(
        //                                                                                                      Map.Entry::getKey,
        //                                                                                                      Comparator.reverseOrder()))
        //                                                                                              .map(entry -> new XYChart.Data<String, Number>(
        //                                                                                                      String.format("> %.1f %%",
        //                                                                                                                    entry.getKey()),
        //                                                                                                      entry.getValue() / (double) histogram.values()
        //                                                                                                                                           .stream()
        //                                                                                                                                           .mapToLong(
        //                                                                                                                                                   val -> val)
        //                                                                                                                                           .sum()))
        //                                                                                              .peek(data -> System.out.println(
        //                                                                                                      "LABELED: " + data))
        //                                                                                              .collect(Collectors.collectingAndThen(
        //                                                                                                      Collectors.toList(),
        //                                                                                                      FXCollections::observableList)))));
        //
        //        final XYChart.Series<String, Number> seriesUnlabeled = new XYChart.Series<>();
        //        seriesUnlabeled.setName("unlabeled");
        //        /*seriesUnlabeled.dataProperty()
        //                       .bind(JavaFxObserver.toBinding(*/
        //        this.dexterCore.getDexterModel()
        //                       .getActiveLearning()
        //                       .getClassificationUncertainty()
        //                       .map(Collection::stream)
        //                       .map(stream -> stream.map(ActiveLearning.ClassificationUncertainty::getUncertaintyValue)
        //                                            .map(p -> p * 100 - Double.MIN_VALUE)
        //                                            .collect(Collectors.groupingBy(p -> Math.floor(p / bucketSize) * bucketSize,
        //                                                                           Collectors.counting())))
        //                       .map(countMap -> {
        //                           final HashMap<Double, Long> histogram = new HashMap<>(countMap);
        //                           for (int i = 0; i < 100 / bucketSize; i++) {
        //                               histogram.putIfAbsent((double) (i * bucketSize), 0L);
        //                           }
        //                           return histogram;
        //                       })
        //                       .map(histogram -> histogram.entrySet()
        //                                                  .stream()
        //                                                  .sorted(Comparator.comparing(Map.Entry::getKey, Comparator.reverseOrder()))
        //                                                  .map(entry -> new XYChart.Data<String, Number>(String.format("> %.1f %%", entry.getKey()),
        //                                                                                                 entry.getValue() / (double) histogram.values()
        //                                                                                                                                      .stream()
        //                                                                                                                                      .mapToLong(
        //                                                                                                                                              val -> val)
        //                                                                                                                                      .sum()))
        //                                                  .peek(x -> System.out.println("UNLABELED: " + x))
        //                                                  .collect(/*Collectors.collectingAndThen(*/
        //                                                          Collectors.toList()/*,
        //                                                                                                         FXCollections::observableList)*/))
        //                       .observeOn(JavaFxScheduler.platform())
        //                       .subscribe(list -> seriesUnlabeled.getData().setAll(list));
        //
        //        final BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        //        chart.getData().add(seriesLabeled);
        //        chart.getData().add(seriesUnlabeled);
        //
        //        chart.prefHeightProperty().bind(Bindings.max(350, this.confusionMatrixHeight));
        //
        //        this.uncertaintyPane.setCenter(chart);

    }

    private void initModelEvaluationView() {
        this.lifecycleDisposable.add(this.modelEvaluation.observeOn(JavaFxScheduler.platform()).subscribe(modelEvaluationOpt -> {
            if (modelEvaluationOpt.isPresent()) {

                final ModelEvaluation modelEvaluation = modelEvaluationOpt.get();

                final GridPane gridPane = new GridPane();
                gridPane.getStyleClass().addAll("table");
                this.accuracyMatrixHeight.bind(gridPane.heightProperty());

                final ArrayTable<ClassLabel, ModelEvaluation.QualityMeasure, Double> qualityMeasures = modelEvaluation.getQualityMeasures();

                // header
                {
                    final Label classHeaderLabel = new Label("Class");
                    classHeaderLabel.getStyleClass().addAll("header");
                    final BorderPane classHeaderLabelPane = new BorderPane(classHeaderLabel);
                    classHeaderLabel.getStyleClass().addAll("header");
                    classHeaderLabelPane.getStyleClass().addAll("cell");
                    gridPane.add(classHeaderLabelPane, 0, 0);

                    final Label countHeaderLabel = new Label("Count");
                    countHeaderLabel.getStyleClass().addAll("header");
                    final BorderPane countHeaderLabelPane = new BorderPane(countHeaderLabel);
                    countHeaderLabelPane.getStyleClass().addAll("cell");
                    gridPane.add(countHeaderLabelPane, 1, 0);
                }

                for (int i = 0; i < qualityMeasures.columnKeyList().size(); i++) {
                    final ModelEvaluation.QualityMeasure qualityMeasure = qualityMeasures.columnKeyList().get(i);
                    final Label measureLabel = new Label(qualityMeasure.getDisplayString()+" \uD83D\uDEC8"); // label and info-icon
                    measureLabel.setTooltip(new Tooltip(qualityMeasure.getDisplayDescription()));
                    measureLabel.getStyleClass().addAll("header");
                    final BorderPane measureLabelPane = new BorderPane(measureLabel);
                    measureLabelPane.getStyleClass().addAll("cell");
                    gridPane.add(measureLabelPane, i + 2, 0);
                }

                // body
                for (int row = 0; row < qualityMeasures.rowKeyList().size(); row++) {
                    // info
                    Label classLabel = new Label(qualityMeasures.rowKeyList().get(row).getLabel());
                    //                    classLabel.getStyleClass().addAll("");
                    BorderPane classLabelPane = new BorderPane(classLabel);
                    classLabelPane.getStyleClass().addAll("cell");
                    gridPane.add(classLabelPane, 0, row + 1);

                    Label countLabel = new Label(
                            String.format("%d", modelEvaluation.getEntityLabelOccurrences().get(qualityMeasures.rowKeyList().get(row))));
                    //                    countLabel.getStyleClass().addAll("");
                    BorderPane countLabelPane = new BorderPane(countLabel);
                    countLabelPane.getStyleClass().addAll("cell");
                    gridPane.add(countLabelPane, 1, row + 1);


                    // measures
                    for (int col = 0; col < qualityMeasures.columnKeyList().size(); col++) {
                        final BorderPane measureLabelPane = new BorderPane(
                                new Label(qualityMeasures.columnKeyList().get(col).format(qualityMeasures.at(row, col))));
                        measureLabelPane.getStyleClass().addAll("cell");
                        gridPane.add(measureLabelPane, col + 2, row + 1);
                    }
                }

                // summary
                {
                    final Label weightedAverageLabel = new Label("W.Avg. / Total");
                    weightedAverageLabel.getStyleClass().addAll("header");
                    final BorderPane weightedAveragePane = new BorderPane(weightedAverageLabel);
                    weightedAveragePane.getStyleClass().addAll("cell");
                    gridPane.add(weightedAveragePane, 0, qualityMeasures.rowKeyList().size() + 1);

                    final Label countTotalLabel = new Label(String.format("%d", (Long) modelEvaluation.getEntityLabelOccurrences()
                                                                                                      .values()
                                                                                                      .stream()
                                                                                                      .mapToLong(val -> val)
                                                                                                      .sum()));
                    countTotalLabel.getStyleClass().addAll("header");
                    final BorderPane countTotalPane = new BorderPane(countTotalLabel);
                    countTotalPane.getStyleClass().addAll("cell");
                    gridPane.add(countTotalPane, 1, qualityMeasures.rowKeyList().size() + 1);
                }

                for (int col = 0; col < qualityMeasures.columnKeyList().size(); col++) {
                    final ModelEvaluation.QualityMeasure qualityMeasure = qualityMeasures.columnKeyList().get(col);
                    final Label summaryLabel = new Label(
                            qualityMeasure.format(modelEvaluation.getQualityMeasuresWeightedAverage().get(qualityMeasure)));
                    summaryLabel.getStyleClass().addAll("header");
                    final BorderPane summaryPane = new BorderPane(summaryLabel);
                    summaryPane.getStyleClass().addAll("cell");
                    gridPane.add(summaryPane, col + 2, qualityMeasures.rowKeyList().size() + 1);
                }

                this.modelEvaluationPane.setCenter(gridPane);

            } else {

                // there is no evaluation available
                this.modelEvaluationPane.setCenter(new Label("No evaluation available."));

            }
        }));
    }

    private void initDataSetOverviewView() {
        this.lifecycleDisposable.add(this.dexterCore.getAppContext()
                                                    .getSettingsRegistry()
                                                    .getGeneralSettings()
                                                    .getClassificationOnFilteredData()
                                                    .toObservable()
                                                    .switchMap(filteredOnly -> filteredOnly
                                                                               ? this.dexterCore.getDomainAdapter()
                                                                                                .getFilteredDomainData()
                                                                               : this.dexterCore.getDomainAdapter().getDomainData())
                                                    .flatMap(domainDataEntities -> RxJavaUtils.combineLatest(domainDataEntities,
                                                        entity -> entity.getClassLabel().toObservable())
                                                                                              .map(classLabelOpts -> classLabelOpts.parallelStream()
                                                                                                                                   .collect(
                                                                                                                                       Collectors.groupingBy(
                                                                                                                                           label -> label,
                                                                                                                                           Collectors.counting()))))
                                                    .debounce(100, TimeUnit.MILLISECONDS)
                                                    .subscribe(classLabelCount -> {

                                                        final ObservableList<XYChart.Data<Number, String>> dataList = classLabelCount.entrySet()
                                                                                                                                     .stream()
                                                                                                                                     .sorted(Comparator
                                                                                                                                                     .comparing(
                                                                                                                                                             entry -> {
                                                                                                                                                                 Collator collator = Collator
                                                                                                                                                                         .getInstance();
                                                                                                                                                                 return entry
                                                                                                                                                                         .getKey()
                                                                                                                                                                         .map(ClassLabel::getLabel)
                                                                                                                                                                         .map(collator::getCollationKey)
                                                                                                                                                                         .orElse(collator.getCollationKey(
                                                                                                                                                                                 ""));
                                                                                                                                                             },
                                                                                                                                                             Comparator
                                                                                                                                                                     .reverseOrder()))
                                                                                                                                     .map(entry -> new XYChart.Data<Number, String>(
                                                                                                                                             entry.getValue(),
                                                                                                                                             entry.getKey()
                                                                                                                                                  .map(ClassLabel::getLabel)
                                                                                                                                                  .orElse("<unlabeled>")))
                                                                                                                                     .reduce(FXCollections
                                                                                                                                                     .observableArrayList(),
                                                                                                                                             (list, data) -> {
                                                                                                                                                 list.add(
                                                                                                                                                         data);
                                                                                                                                                 return list;
                                                                                                                                             },
                                                                                                                                             FXCollections::concat);
                                                        final Map<Boolean, Long> entitySumsIsLabeled = classLabelCount.entrySet()
                                                                                                                      .stream()
                                                                                                                      .collect(
                                                                                                                              Collectors.partitioningBy(
                                                                                                                                      entry -> entry
                                                                                                                                              .getKey()
                                                                                                                                              .isPresent(),
                                                                                                                                      Collectors
                                                                                                                                              .summingLong(
                                                                                                                                                      Map.Entry::getValue)));
                                                        final BarChart<Number, String> barChart = new BarChart<>(new NumberAxis(),
                                                                                                                 new CategoryAxis(),
                                                                                                                 FXCollections.singletonObservableList(
                                                                                                                         new XYChart.Series<>(
                                                                                                                                 entitySumsIsLabeled
                                                                                                                                         .get(true) + " labeled / " + entitySumsIsLabeled
                                                                                                                                         .get(false) + " unlabeled entities",
                                                                                                                                 dataList)));

                                                        barChart.prefHeightProperty()
                                                                .bind(Bindings.max(dataList.size() * 15, this.confusionMatrixHeight));

                                                        Platform.runLater(() -> this.dataSetOverviewPane.setCenter(barChart));
                                                    }));
    }

    private void initConfusionMatrixView() {
        this.lifecycleDisposable.add(this.modelEvaluation.observeOn(Schedulers.computation()).subscribe(modelEvaluationOpt -> {

            if (modelEvaluationOpt.isPresent()) {

                final ModelEvaluation modelEvaluation = modelEvaluationOpt.get();

                final List<ClassLabel> classLabels = modelEvaluation.getLabels();
                final ArrayTable<ClassLabel, ClassLabel, Long> confusionMatrix = modelEvaluation.getConfusionMatrix();


                GridPane gridPane = new GridPane();
                gridPane.getStyleClass().addAll("confusionMatrix", "table");
                this.confusionMatrixHeight.bind(gridPane.heightProperty());

                // add the class labels
                Map<ClassLabel, Node> rowHeaderMap = new HashMap<>(classLabels.size());
                Map<ClassLabel, Node> colHeaderMap = new HashMap<>(classLabels.size());
                for (int i = 0; i < classLabels.size(); i++) {

                    // side == 0: row header
                    // side == 1: col header
                    for (int side = 0; side < 2; side++) {

                        final Label label = new Label(classLabels.get(i).getLabel());
                        label.getStyleClass().add("header");

                        final BorderPane labelPane = new BorderPane(label);
                        labelPane.getStyleClass().add("cell");

                        gridPane.add(labelPane, side == 0 ? 1 : i + 2, side == 0 ? i + 2 : 1);

                        // remember header cells for class labels
                        (side == 0 ? rowHeaderMap : colHeaderMap).put(classLabels.get(i), labelPane);
                    }

                }

                // add the arrow pointing from label col to label row
                final Label arrowLabel = new Label("\u2BA3");
                arrowLabel.getStyleClass().addAll("header", "arrow");
                gridPane.add(new BorderPane(arrowLabel), 1, 1);

                // add the column sum header
                final Label colSumLabel = new Label("\u2140");
                arrowLabel.getStyleClass().add("header");
                gridPane.add(new BorderPane(colSumLabel), 1, classLabels.size() + 2);

                // add the row sum header
                final Label rowSumLabel = new Label("\u2140");
                arrowLabel.getStyleClass().add("header");
                gridPane.add(new BorderPane(rowSumLabel), classLabels.size() + 2, 1);

                // add the description to the left
                final Label leftLabel = new Label("Given Label");
                leftLabel.getStyleClass().add("legend-left");
                gridPane.add(new BorderPane(new Group(leftLabel)), 0, 2, 1, classLabels.size());

                // add the description on top
                final Label topLabel = new Label("Classified as");
                topLabel.getStyleClass().add("legend-top");
                gridPane.add(new BorderPane(topLabel), 2, 0, classLabels.size(), 1);

                // add the content
                for (ClassLabel rowLabel : classLabels) {
                    for (ClassLabel colLabel : classLabels) {

                        final Label label = new Label(String.format("%d", confusionMatrix.get(rowLabel, colLabel)));

                        final BorderPane cell = new BorderPane(label);
                        cell.getStyleClass().add("cell");

                        if (rowLabel.equals(colLabel)) cell.getStyleClass().add("diagonal");

                        cell.setOnMouseEntered(event -> {
                            cell.getStyleClass().add("highlighted");
                            rowHeaderMap.get(rowLabel).getStyleClass().add("highlighted");
                            colHeaderMap.get(colLabel).getStyleClass().add("highlighted");
                        });
                        cell.setOnMouseExited(event -> {
                            cell.getStyleClass().remove("highlighted");
                            rowHeaderMap.get(rowLabel).getStyleClass().remove("highlighted");
                            colHeaderMap.get(colLabel).getStyleClass().remove("highlighted");
                        });

                        gridPane.add(cell, classLabels.indexOf(colLabel) + 2, classLabels.indexOf(rowLabel) + 2);
                    }
                }

                // add row- and col- sums
                for (ClassLabel classLabel : classLabels) {
                    final long rowSum = confusionMatrix.row(classLabel).values().stream().mapToLong(val -> val).sum();

                    final Label rowLabel = new Label(String.format("%d", rowSum));
                    rowLabel.getStyleClass().add("label-sum");
                    final BorderPane rowCell = new BorderPane(rowLabel);
                    rowCell.getStyleClass().add("cell");

                    gridPane.add(rowCell, classLabels.size() + 2, classLabels.indexOf(classLabel) + 2);


                    final long colSum = confusionMatrix.column(classLabel).values().stream().mapToLong(val -> val).sum();

                    final Label colLabel = new Label(String.format("%d", colSum));
                    colLabel.getStyleClass().add("label-sum");
                    final BorderPane colCell = new BorderPane(colLabel);
                    colCell.getStyleClass().add("cell");

                    gridPane.add(colCell, classLabels.indexOf(classLabel) + 2, classLabels.size() + 2);
                }

                // sum total
                final long sum = confusionMatrix.values().stream().mapToLong(val -> val).sum();
                final Label sumLabel = new Label(String.format("%d", sum));
                sumLabel.getStyleClass().addAll("label-sum", "label-sum-total");
                final BorderPane sumCell = new BorderPane(sumLabel);
                sumCell.getStyleClass().add("cell");
                gridPane.add(sumCell, classLabels.size() + 2, classLabels.size() + 2);

                // show settings
                Label showSettingsLabel = new Label(
                        String.format("%d-fold cross-validation repeated %d times \nfor %d samples each, results added up.",
                                      modelEvaluation.getCrossValidationResult().getCrossValidation().getFolds(),
                                      modelEvaluation.getCrossValidationResult().getCrossValidation().getRuns(),
                                      modelEvaluation.getCrossValidationResult().getCrossValidation().getDataSet().size()));
                showSettingsLabel.getStyleClass().add("settings");
                gridPane.add(new BorderPane(null, null, showSettingsLabel, null, null), 1, classLabels.size() + 3, Integer.MAX_VALUE, 1);


                // push to view
                Platform.runLater(() -> this.confusionMatrixPane.setCenter(gridPane));

            } else {

                // there is no evaluation available
                Platform.runLater(() -> this.confusionMatrixPane.setCenter(new Label("No cross-validation available.")));

            }

        }));
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        int rowCount = 15;
        int columnCount = 15;


        GridPane gridpane = new GridPane();

        Map<Integer, Node> headerMap = new HashMap<>();

        for (int i = 2; i < rowCount; i++) {
            final Label label = new Label(Character.toString((char) (i + 64)));
            label.getStyleClass().add("header");
            final BorderPane pane = new BorderPane(label);
            headerMap.put(i, pane);
            pane.getStyleClass().add("cell");
            gridpane.add(pane, 1, i);
        }
        for (int j = 2; j < columnCount; j++) {
            final Label label = new Label(String.valueOf((char) (j + 96)));
            label.getStyleClass().add("header");
            final BorderPane pane = new BorderPane(label);
            pane.getStyleClass().add("cell");
            gridpane.add(pane, j, 1);
        }

        final Label arrowLabel = new Label("\u2BA3");
        arrowLabel.getStyleClass().addAll("header", "arrow");
        gridpane.add(new BorderPane(arrowLabel), 1, 1);

        final Label leftLabel = new Label("Given Label");
        leftLabel.getStyleClass().add("legend-left");
        gridpane.add(new BorderPane(new Group(leftLabel)), 0, 2, 1, Integer.MAX_VALUE);

        final Label topLabel = new Label("Classified as");
        topLabel.getStyleClass().add("legend-top");
        gridpane.add(new BorderPane(topLabel), 2, 0, Integer.MAX_VALUE, 1);


        for (int i = 2; i < rowCount; i++) {
            for (int j = 2; j < columnCount; j++) {
                final Label label = new Label(String.format("%.1f %%", Double.NaN));
                final BorderPane pane = new BorderPane(label);
                pane.getStyleClass().add("cell");
                if (i == j) pane.getStyleClass().add("diagonal");
                int finalI = i;
                pane.setOnMouseEntered(event -> {
                    pane.getStyleClass().add("highlighted");
                    headerMap.get(finalI).getStyleClass().add("highlighted");
                });
                pane.setOnMouseExited(event -> {
                    pane.getStyleClass().remove("highlighted");
                    headerMap.get(finalI).getStyleClass().remove("highlighted");
                });
                gridpane.add(pane, j, i);
            }
        }

        gridpane.getStyleClass().add("confusionMatrix");


        final Scene scene = new Scene(gridpane);
        scene.getStylesheets().add(getClass().getResource("ModelQualityView.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();
    }

}
