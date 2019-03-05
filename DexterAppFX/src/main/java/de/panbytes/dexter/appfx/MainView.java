/**
 *
 */

package de.panbytes.dexter.appfx;


import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Table;
import de.panbytes.dexter.appfx.scene.chart.InteractiveScatterChart;
import de.panbytes.dexter.appfx.settings.SettingsView;
import de.panbytes.dexter.core.AppContext;
import de.panbytes.dexter.core.ClassLabel;
import de.panbytes.dexter.core.DataSourceActions;
import de.panbytes.dexter.core.DexterCore;
import de.panbytes.dexter.core.activelearning.ActiveLearning;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.core.model.DexterModel;
import de.panbytes.dexter.core.model.classification.ModelEvaluation;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.observers.JavaFxObserver;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.ValueAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;

import java.io.IOException;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The Controller for the Main View, coupled with JavaFX's FXML-Layout.
 *
 * @author Fabian Krippendorff
 */
public class MainView {

    public static final String EMPTY_CLASS_LABEL = "--";
    private static final Logger log = LoggerFactory.getLogger(MainView.class);


    private final DexterCore dexterCore;
    private final DexterModel dexterModel;
    private final DomainAdapter domainAdapter;
    private final AppContext appContext;
    @FXML
    SplitPane mainSplitPane;
    @FXML
    private Button evaluateModelButton;
    @FXML
    private Button pickUnlabeledButton;
    @FXML
    private SplitMenuButton checkLabelButton;
    @FXML
    private MenuItem clearCheckLabelHistoryMenuItem;
    @FXML
    private FlowPane classFilterPane;
    @FXML
    private MenuBar menuBar;
    @FXML
    private BorderPane statusBar;
    @FXML
    private TextField filterTextField;
    @FXML
    private SplitMenuButton addDataSourcesSplitMenuButton;
    @FXML
    private Button removeDataSourcesButton;
    @FXML
    private InteractiveScatterChart<Double, Double> scatterChart;
    @FXML
    private TreeView<DataSource> dataSourceTree;
    @FXML
    private ToggleButton enableDimReductionButton;
    private Map<DataEntity, Data<Double, Double>> entity2chartData = new HashMap<>();

    /**
     * Setup the Main MainView.
     *
     * @param dexterCore
     */
    public MainView(DexterCore dexterCore) {

        this.dexterCore = checkNotNull(dexterCore);
        this.dexterModel = dexterCore.getDexterModel();
        this.domainAdapter = dexterCore.getDomainAdapter();
        this.appContext = dexterCore.getAppContext();

        log.debug("Created MainView {}.", this);

    }

    /**
     * Setup DataBinding & Co. - called by JavaFX after bootstrapping of FXML is finished.
     */
    @FXML
    private void initialize() {

        log.debug("Initialize {}", this);


        /* Progress Monitor*/
        initProgressMonitor();

        /*
         * Databinding
         */

        initializeScatterChart();

        // link the treeView
        BiMap<DataSource, TreeItem<DataSource>> dataSourceTreeItemMap = HashBiMap.create();
        TreeItem<DataSource> rootDataSourceTreeItem = new CheckBoxTreeItem<>();
        this.dataSourceTree.setRoot(rootDataSourceTreeItem);
        rootDataSourceTreeItem.valueProperty()
            .bind(JavaFxObserver.toNullableBinding(this.domainAdapter.getRootDataSource()));
        this.domainAdapter.getRootDataSource()
            .observeOn(JavaFxScheduler.platform())
            .subscribe(dataSourceOpt -> dataSourceOpt.ifPresent(dataSource -> {
                dataSourceTreeItemMap.put(dataSource, rootDataSourceTreeItem);
                createTreeItemsForDataSources(dataSource, rootDataSourceTreeItem,
                    dataSourceTreeItemMap);
            }));

        // set the cell factory
        this.dataSourceTree.setCellFactory(CheckBoxTreeCell.forTreeView());

        this.dataSourceTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        this.removeDataSourcesButton.disableProperty()
            .bind(this.dataSourceTree.getSelectionModel().selectedItemProperty().isNull());


        /*
         * DataSourceFactoryButton
         */
        this.domainAdapter.getDataSourceActions().getDefaultAddAction()
            .subscribe(defaultActionOpt -> {
                this.addDataSourcesSplitMenuButton.setDisable(!defaultActionOpt.isPresent());
                if (defaultActionOpt.isPresent()) {
                    defaultActionOpt.get().getName().toObservable()
                        .subscribe(name -> this.addDataSourcesSplitMenuButton.setText(name));
                    defaultActionOpt.get()
                        .getDescription()
                        .toObservable()
                        .subscribe(descr -> this.addDataSourcesSplitMenuButton
                            .setTooltip(new Tooltip(descr)));
                    JavaFxObservable.actionEventsOf(this.addDataSourcesSplitMenuButton)
                        .observeOn(Schedulers.io())
                        .map(e -> defaultActionOpt.get()
                            .createAndAdd(new DataSourceActions.AddAction.ActionContext(
                                this.addDataSourcesSplitMenuButton)))
                        .observeOn(JavaFxScheduler.platform())
                        .subscribe(result -> displayAddActionResultMessage(result));
                } else {
                    this.addDataSourcesSplitMenuButton.setText("N/A");
                    this.addDataSourcesSplitMenuButton
                        .setTooltip(new Tooltip("No Action available."));
                    this.addDataSourcesSplitMenuButton.setOnAction(null);
                }
            });

        this.domainAdapter.getDataSourceActions().getAddActions().subscribe(addActions -> {

            addActions.forEach(action -> {
                Label label = new Label();
                label.textProperty()
                    .bind(JavaFxObserver.toBinding(action.getName().toObservable()));
                label.tooltipProperty().bind(JavaFxObserver
                    .toBinding(action.getDescription().toObservable().map(Tooltip::new)));
                CustomMenuItem menuItem = new CustomMenuItem(label);
                JavaFxObservable.actionEventsOf(menuItem)
                    .observeOn(Schedulers.io())
                    .map(e -> action
                        .createAndAdd(new DataSourceActions.AddAction.ActionContext(menuItem)))
                    .observeOn(JavaFxScheduler.platform())
                    .subscribe(this::displayAddActionResultMessage);
                this.addDataSourcesSplitMenuButton.getItems().add(menuItem);
            });
        });

        JavaFxObservable.valuesOf(this.enableDimReductionButton.selectedProperty())
            .subscribe(this.dexterCore.getDexterModel().getDimReductionEnabled());


        /*
        Plugin-Menu
         */
        Menu pluginMenu = new Menu("Plugins");
        this.menuBar.getMenus().add(pluginMenu);

        AtomicReference<CompositeDisposable> pluginActionsDisposable = new AtomicReference<>(
            new CompositeDisposable());
        this.appContext.getPluginRegistry()
            .getPlugins()
            .toObservable()
            .doOnNext(__ -> pluginActionsDisposable.getAndSet(new CompositeDisposable()).dispose())
            .map(pluginList -> pluginList.stream().map(plugin -> {

                Menu subMenu = new Menu();
                subMenu.textProperty()
                    .bind(JavaFxObserver.toBinding(plugin.getName().toObservable()));

                pluginActionsDisposable.get()
                    .add(plugin.getActions().map(actions -> actions.stream().map(action -> {
                        MenuItem menuItem = new MenuItem();
                        menuItem.textProperty()
                            .bind(JavaFxObserver.toBinding(action.getName().toObservable()));
                        menuItem.setOnAction(event -> action.performAction(event.getSource()));
                        return menuItem;
                    }).collect(Collectors.toList()))
                        .subscribe(menuItems -> subMenu.getItems().setAll(menuItems)));

                return subMenu;

            }).collect(Collectors.toList()))
            .observeOn(JavaFxScheduler.platform())
            .doAfterNext(pluginList -> pluginMenu.setDisable(pluginList.isEmpty()))
            .subscribe(subMenus -> pluginMenu.getItems().setAll(subMenus));


        /*
        Filter
         */
        AtomicReference<Map<Optional<ClassLabel>, Boolean>> classLabelEnabledFilterModel = new AtomicReference<>(
            Collections.emptyMap());

        this.domainAdapter.getClassLabels()
            .doOnNext(classLabels -> {
                classLabelEnabledFilterModel.updateAndGet(old -> {
                    Map<Optional<ClassLabel>, Boolean> update = new ConcurrentHashMap<>(old);
                    boolean rememberPreviousLabelStates = true;
                    if (!rememberPreviousLabelStates) {
                        update.keySet().retainAll(classLabels);
                    }
                    classLabels
                        .forEach(label -> update.putIfAbsent(label, true)); // enabled by default
                    return update;
                });

            })
            .observeOn(JavaFxScheduler.platform())
            .switchMap(classLabels -> {
                this.classFilterPane.getChildren().clear();
                return Observable.merge(classLabels.stream()
                    .filter(classLabel -> classLabel.map(lbl -> !lbl.getLabel()
                        .equals(this.appContext.getSettingsRegistry()
                            .getDomainSettings()
                            .rejectedClassLabel()
                            .getValue()))
                        .orElse(true))
                    .map(classLabel -> {
                        CheckBox checkBox = new CheckBox(classLabel.map(ClassLabel::getLabel)
                            .orElse(EMPTY_CLASS_LABEL));
                        checkBox.setSelected(
                            classLabelEnabledFilterModel.get().get(classLabel));
                        checkBox.setMnemonicParsing(false);
                        this.classFilterPane.getChildren().add(checkBox);
                        return JavaFxObservable.valuesOf(checkBox.selectedProperty())
                            .doOnNext(
                                selected -> classLabelEnabledFilterModel
                                    .get()
                                    .put(classLabel, selected));
                    })
                    .collect(Collectors.toList()))
                    .debounce(100, TimeUnit.MILLISECONDS)
                    .startWith(false /*just as trigger*/);

            })
            .map(__ -> new DomainAdapter.FilterModule("Class Filter", "Filter by Class-Label") {
                @Override
                public boolean accept(DomainDataEntity entity) {
                    return classLabelEnabledFilterModel.get()
                        .getOrDefault(entity.getClassLabel().getValue(), false);
                }
            }).observeOn(Schedulers.io())
            .scan(Collections.<DomainAdapter.FilterModule>emptyList(),
                (prev, curr) -> Arrays.asList(prev.isEmpty() ? null : prev.get(1), curr))
            .skip(1)
            .subscribe(filterUpdate -> this.domainAdapter
                .replaceDataFilter(filterUpdate.get(0), filterUpdate.get(1)));

        //                          .subscribe(__ -> {
        //            this.domainAdapter.setDataFilters(Collections.singletonList(
        //                    // TODO add instead of replacing everything else!
        //                    new DomainAdapter.FilterModule("Class Filter", "Filter by Class-Label") {
        //                        @Override
        //                        public boolean accept(DomainDataEntity entity) {
        //                                return classLabelEnabledFilterModel.get().getOrDefault(entity.getClassLabel().getValue(),false);
        //                        }
        //                    }));
        //        });



        /*
        Active Learning: Pick Unlabeled
         */
        this.pickUnlabeledButton.disableProperty()
            .bind(JavaFxObserver.toBinding(
                this.dexterModel.getActiveLearning().getClassificationUncertainty()
                    .map(Collection::isEmpty)));

        JavaFxObservable.actionEventsOf(this.pickUnlabeledButton)
            .switchMap(actionEvent -> this.dexterModel.getActiveLearning()
                .getClassificationUncertainty()
                .firstElement()
                .toObservable())
            .filter(list -> !list.isEmpty())
            .map(list -> list.get(0))
            .map(ActiveLearning.AbstractUncertainty::getDataEntity)
            .subscribe(
                leastConfident -> InspectionView.createAndShow(this.dexterCore, leastConfident));


        /*
        Active Learning: Check Label
         */
        this.checkLabelButton.disableProperty()
            .bind(JavaFxObserver.toBinding(
                this.dexterModel.getActiveLearning().getExistingLabelsUncertainty()
                    .map(Collection::isEmpty)));

        JavaFxObservable.actionEventsOf(this.checkLabelButton)
            .switchMap(actionEvent -> this.dexterModel.getActiveLearning()
                .getExistingLabelsUncertainty()
                .firstElement()
                .toObservable())
            .filter(list -> !list.isEmpty())
            .map(list -> list.get(0))
            .map(ActiveLearning.AbstractUncertainty::getDataEntity)
            .subscribe(
                leastConfident -> InspectionView.createAndShow(this.dexterCore, leastConfident));

        // reset history
        JavaFxObservable.actionEventsOf(this.clearCheckLabelHistoryMenuItem)
            .subscribe(actionEvent -> this.appContext.getInspectionHistory().clear());


        /*
        Evaluate Model
         */
        JavaFxObservable.actionEventsOf(this.evaluateModelButton)
            .subscribe(actionEvent -> ModelQualityView
                .createAndShow(this.dexterModel.getClassificationModel()
                        .getCrossValidationResults()
                        .map(opt -> opt.map(ModelEvaluation::new)),
                    this.dexterCore));


    }

    @FXML
    @Deprecated
    public void checkLabel(ActionEvent actionEvent) {
        this.domainAdapter.getFilteredDomainData().firstElement().subscribe(entities -> {

            List<DomainDataEntity> labeledNotYetCheckedEntities = new ArrayList<>(entities);
            labeledNotYetCheckedEntities
                .removeIf(entity -> !entity.getClassLabel().getValue().isPresent());
            labeledNotYetCheckedEntities
                .removeAll(this.appContext.getInspectionHistory().getLabeledEntities().getValue());

            System.out.println("LabeledNotChecked: " + labeledNotYetCheckedEntities.size());

            if (!labeledNotYetCheckedEntities.isEmpty()) {

                FeatureSpace featureSpace = this.domainAdapter.getFeatureSpace()
                    .getValue()
                    .orElseThrow(() -> new IllegalStateException(
                        "Expect FeatureSpace to be available!"));

                ActiveLearning activeLearning = new ActiveLearning(RandomForest::new,
                    this.domainAdapter.getClassLabels().blockingFirst());
                Evaluation evaluation = activeLearning
                    .evaluateClassification(featureSpace, labeledNotYetCheckedEntities);

                System.out.println(evaluation.toSummaryString());
                System.out.println("----------------------------");
                System.out.println(evaluation.toMatrixString());
                System.out.println("----------------------------");

                Table<DomainDataEntity, ClassLabel, Double> probabilities = activeLearning
                    .classificationProbabilities(featureSpace,
                        labeledNotYetCheckedEntities,
                        labeledNotYetCheckedEntities,
                        appContext.getTaskMonitor());

                //            System.out.println(probabilities);

                List<Map.Entry<DomainDataEntity, Map<ClassLabel, Double>>> probabilitiesSortedByDoubt = probabilities
                    .rowMap()
                    .entrySet()
                    .stream()
                    .sorted(Comparator.comparing(
                        entry -> entry.getKey()
                            .getClassLabel()
                            .getValue()
                            .map(lbl -> entry
                                .getValue()
                                .get(lbl))
                            .orElse(0.)))
                    .collect(
                        Collectors.toList());
                probabilitiesSortedByDoubt.forEach(
                    entry -> System.out.printf("Entry (is %s): %s  -->  %f %n",
                        entry.getKey().getClassLabel().getValue(), entry,
                        entry.getKey()
                            .getClassLabel()
                            .getValue()
                            .map(lbl -> entry.getValue().get(lbl))
                            .orElse(0.)));

                probabilitiesSortedByDoubt.stream().findFirst().ifPresent(entry -> {
                    InspectionView.createAndShow(this.dexterCore, entry.getKey());
                });

            }
        });
    }

    private void createTreeItemsForDataSources(DataSource currentDataSource,
        TreeItem<DataSource> currentTreeItem,
        BiMap<DataSource, TreeItem<DataSource>> dataSourceTreeItemMap) {
        currentDataSource.getChildDataSources().toObservable().subscribe(children -> {
            currentTreeItem.setExpanded(true);
            currentTreeItem.getChildren().clear();
            currentTreeItem.getChildren().addAll(children.stream().map(childSource -> {
                TreeItem<DataSource> childItem = dataSourceTreeItemMap.get(childSource);
                if (childItem == null) {
                    childItem = new CheckBoxTreeItem<>(childSource);

                    // TODO: elements will never get removed from that map!
                    dataSourceTreeItemMap.put(childSource, childItem);
                }

                createTreeItemsForDataSources(childSource, childItem, dataSourceTreeItemMap);

                return childItem;
            }).collect(Collectors.toList()));
        });
    }

    private void initProgressMonitor() {

        CompositeDisposable taskUpdates = new CompositeDisposable();

        this.appContext.getTaskMonitor().getCurrentTasks().toObservable().map(tasks -> {

            log.debug("{} Tasks to monitor: {}", tasks.size(), tasks);

            VBox progressPane = new VBox();
            progressPane.setAlignment(Pos.CENTER_LEFT);

            taskUpdates.clear();

            tasks.stream().map(task -> {
                HBox hBox = new HBox(5);
                hBox.setAlignment(Pos.CENTER_LEFT);

                taskUpdates.add(Observable
                    .combineLatest(task.getName().toObservable(), task.getProgress().toObservable(),
                        task.getMessage().toObservable(), task.getState().toObservable(),

                        (name, progressOpt, messageOpt, state) -> {

                            final Label label = new Label(name + ":");
                            final Node progressBar = progressOpt.<Node>map(
                                progress -> new StackPane(new ProgressBar(progress), new Label(
                                    String.format("%.2f %%", progress * 100)))).orElse(
                                new ProgressBar());
                            final Label message = new Label(
                                messageOpt.map(msg -> "[" + msg + "]").orElse(""));

                            return Arrays.asList(label, progressBar, message);
                        })
                    .observeOn(JavaFxScheduler.platform())
                    .subscribe(newChildren -> hBox.getChildren().setAll(newChildren)));

                return hBox;
            }).collect(Collectors.collectingAndThen(Collectors.toList(),
                hBoxes -> progressPane.getChildren().setAll(hBoxes)));

            return progressPane;

        }).observeOn(JavaFxScheduler.platform())
            .subscribe(progressPane -> this.statusBar.setCenter(progressPane));

    }

    private void initializeScatterChart() {
        CompositeDisposable innerDisposables = new CompositeDisposable();
        List<Optional<ClassLabel>> labelsHistory = new ArrayList<>();
        Observable<ObservableList<Series<Double, Double>>> chartDataObs = this.dexterModel
            .getLowDimData()
            .observeOn(Schedulers.computation())
            .sample(100, TimeUnit.MILLISECONDS, true)
            .switchMap(entityMappingMap -> {

                if (entityMappingMap.size() == 0) {
                    return Observable.just(
                        Collections.<Series<Double, Double>>emptyList());

                } else {

                    List<Observable<Optional<ClassLabel>>> labelObservables = entityMappingMap
                        .entrySet()
                        .stream()
                        .map(entityMapping -> entityMapping
                            .getKey()
                            .getClassLabel()
                            .toObservable())
                        .collect(Collectors.toList());
                    return Observable.merge(
                        labelObservables) // just another trigger for re-calculation
                        .debounce(100,
                            TimeUnit.MILLISECONDS)
                        .doOnNext(
                            __ -> innerDisposables
                                .clear())
                        .map(__ -> entityMappingMap
                            .entrySet()
                            .stream()
                            .collect(
                                Collectors
                                    .groupingBy(
                                        entry -> entry
                                            .getKey()
                                            .getClassLabel()
                                            .getValue())))
                        .map(label2EntityMappingsMap -> {
                            return label2EntityMappingsMap
                                .entrySet()
                                .stream()
                                .sorted(Comparator
                                    .comparing(
                                        label2EntityMappings -> {
                                            Collator collator = Collator
                                                .getInstance();
                                            return label2EntityMappings
                                                .getKey()
                                                .map(ClassLabel::getLabel)
                                                .map(collator::getCollationKey)
                                                .orElse(collator.getCollationKey(
                                                    ""));
                                        }))
                                .map(label2EntityMappings -> {
                                    Series<Double, Double> series = new Series<>();
                                    series.setName(
                                        label2EntityMappings
                                            .getKey()
                                            .map(ClassLabel::getLabel)
                                            .orElse(MainView.EMPTY_CLASS_LABEL));

                                    label2EntityMappings
                                        .getValue()
                                        .forEach(
                                            entityMapping -> {
                                                Data<Double, Double> dataItem = new Data<>();
                                                this.entity2chartData
                                                    .put(entityMapping
                                                            .getKey(),
                                                        dataItem); // TODO remove obsolete DataItems!?
                                                innerDisposables
                                                    .add(entityMapping
                                                        .getValue()
                                                        .getCoordinates()
                                                        .toObservable()
                                                        .observeOn(
                                                            JavaFxScheduler
                                                                .platform())
                                                        .subscribe(
                                                            doubles -> {
                                                                dataItem.setXValue(
                                                                    doubles[0]);
                                                                dataItem.setYValue(
                                                                    doubles[1]);
                                                            }));

                                                Observable<Node> dataNode = JavaFxObservable
                                                    .nullableValuesOf(
                                                        dataItem.nodeProperty())
                                                    .filter(Optional::isPresent)
                                                    .map(Optional::get);

                                                innerDisposables
                                                    .add(dataNode.switchMap(
                                                        node -> JavaFxObservable
                                                            .eventsOf(
                                                                node,
                                                                MouseEvent.MOUSE_CLICKED))
                                                        .subscribe(
                                                            mouseEvent -> {
                                                                // TODO move to domain adapter (?)
                                                                InspectionView
                                                                    .createAndShow(
                                                                        this.dexterCore,
                                                                        entityMapping
                                                                            .getKey());
                                                            }));

                                                innerDisposables
                                                    .add(dataNode.switchMap(
                                                        node -> JavaFxObservable
                                                            .emitOnChanged(
                                                                node.getStyleClass()))
                                                        .filter(styleList -> !styleList
                                                            .isEmpty())
                                                        .firstElement()
                                                        .subscribe(
                                                            styleList -> {

                                                                if (!labelsHistory
                                                                    .contains(
                                                                        label2EntityMappings
                                                                            .getKey())) {
                                                                    labelsHistory
                                                                        .add(label2EntityMappings
                                                                            .getKey());
                                                                }

                                                                int serNr = labelsHistory
                                                                    .indexOf(
                                                                        label2EntityMappings
                                                                            .getKey());

                                                                styleList
                                                                    .setAll("chart-symbol",
                                                                        "series" + serNr,
                                                                        "default-color"
                                                                            + serNr % 8);

                                                                if (!label2EntityMappings
                                                                    .getKey()
                                                                    .isPresent()) {
                                                                    if (!styleList
                                                                        .contains(
                                                                            "series-class-not-labeled")) {
                                                                        styleList
                                                                            .add(
                                                                                "series-class-not-labeled");
                                                                    }
                                                                }

                                                            }));

                                                series.getData()
                                                    .add(dataItem);
                                            });

                                    return series;
                                })
                                .collect(
                                    Collectors
                                        .toList());
                        });
                }
            })
            .map(FXCollections::observableList)
            .observeOn(JavaFxScheduler.platform());
        this.scatterChart.dataProperty().bind(JavaFxObserver.toBinding(chartDataObs));
        this.scatterChart.setAnimated(false);
        this.scatterChart.setHorizontalZeroLineVisible(false);
        this.scatterChart.setVerticalZeroLineVisible(false);
        //        scatterChart.setHorizontalGridLinesVisible(false);
        //        scatterChart.setVerticalGridLinesVisible(false);
        this.scatterChart.getXAxis().setTickLabelsVisible(false);
        this.scatterChart.getYAxis().setTickLabelsVisible(false);
        this.scatterChart.getXAxis().setTickMarkVisible(false);
        this.scatterChart.getYAxis().setTickMarkVisible(false);
        ((ValueAxis) this.scatterChart.getXAxis()).setMinorTickVisible(false);
        ((ValueAxis) this.scatterChart.getYAxis()).setMinorTickVisible(false);

        this.dexterModel.getCurrentInspectionEntity().toObservable().subscribe(optEntity -> {
            List<Data<Double, Double>> selection = optEntity.map(this.entity2chartData::get)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
            this.scatterChart.setCurrentSelection(selection);
        });
    }

    private void displayAddActionResultMessage(DataSourceActions.AddAction.Result result) {
        switch (result.getState()) {
            case SUCCESS:
                if (result.getCreatedSources().isEmpty()) {
                    new Alert(AlertType.INFORMATION,
                        "DataSource was successfully added but contains no Data: " + result
                            .getMessage(),
                        ButtonType.OK).showAndWait();
                }
                break;
            case CREATION_FAILED:
            case ADDING_FAILED:
                new Alert(AlertType.WARNING,
                    "DataSource could not be added (" + result.getState() + "): " + result
                        .getMessage(),
                    ButtonType.OK).showAndWait();
                break;
        }
    }


    @FXML
    public void removeDataSource(final ActionEvent event) {
        new ArrayList<>(this.dataSourceTree.getSelectionModel().getSelectedItems())
            .forEach(selection -> {
                TreeItem<DataSource> parent = selection.getParent();
                if (parent != null) {
                    parent.getValue().removeChildDataSource(selection.getValue());
                } else {
                    log.warn(
                        "Removing DataSource {} not possible, as it has no parent in GUI-Tree.",
                        selection.getValue());
                }
            });
        this.dataSourceTree.getSelectionModel().clearSelection();
    }


    @FXML
    public void showSettingsDialog(ActionEvent actionEvent) {
        FXMLLoader fxmlLoader = new FXMLLoader(SettingsView.class.getResource("SettingsView.fxml"));

        fxmlLoader
            .setControllerFactory(__ -> new SettingsView(this.appContext.getSettingsRegistry()));

        Stage stage = new Stage();
        stage.setTitle("Dexter [Settings]");
        stage.initModality(Modality.APPLICATION_MODAL);

        try {
            stage.setScene(new Scene(fxmlLoader.load()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
