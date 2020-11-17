/**
 *
 */

package de.panbytes.dexter.appfx;


import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Predicates;
import com.google.common.collect.BiMap;
import com.google.common.collect.Comparators;
import com.google.common.collect.HashBiMap;
import de.panbytes.dexter.appfx.scene.chart.InteractiveScatterChart;
import de.panbytes.dexter.appfx.settings.SettingsView;
import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.domain.DataSourceActions;
import de.panbytes.dexter.core.DexterCore;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel.AbstractUncertainty;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel.ClassificationUncertainty;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel.CrossValidationUncertainty;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.model.DexterModel;
import de.panbytes.dexter.core.model.FilterManager.FilterModule;
import de.panbytes.dexter.core.model.classification.ModelEvaluation;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.observers.JavaFxObserver;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.application.Platform;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Controller for the Main View, coupled with JavaFX's FXML-Layout.
 *
 * @author Fabian Krippendorff
 */
public class MainView {

    public static final String EMPTY_CLASS_LABEL = "--";
    private static final Logger log = LoggerFactory.getLogger(MainView.class);
    public static final int ACTIVE_LEARNING_BATCH_SIZE = 50;


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
    private Button pickUnlabeledMultiButton;
    @FXML
    private Button checkLabelButton;
    @FXML
    private SplitMenuButton checkLabelMultiButton;
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
        rootDataSourceTreeItem.valueProperty().bind(JavaFxObserver.toNullableBinding(this.domainAdapter.getRootDataSource()));
        this.domainAdapter.getRootDataSource().observeOn(JavaFxScheduler.platform()).subscribe(dataSourceOpt -> dataSourceOpt.ifPresent(dataSource -> {
            dataSourceTreeItemMap.put(dataSource, rootDataSourceTreeItem);
            createTreeItemsForDataSources(dataSource, rootDataSourceTreeItem, dataSourceTreeItemMap);
        }));

        // set the cell factory
        this.dataSourceTree.setCellFactory(CheckBoxTreeCell.forTreeView());

        this.dataSourceTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        this.removeDataSourcesButton.disableProperty().bind(this.dataSourceTree.getSelectionModel().selectedItemProperty().isNull());


        /*
         * DataSourceFactoryButton
         */
        this.domainAdapter.getDataSourceActions().getDefaultAddAction().subscribe(defaultActionOpt -> {
            this.addDataSourcesSplitMenuButton.setDisable(!defaultActionOpt.isPresent());
            if (defaultActionOpt.isPresent()) {
                defaultActionOpt.get().getName().toObservable().subscribe(name -> this.addDataSourcesSplitMenuButton.setText(name));
                defaultActionOpt.get().getDescription().toObservable().subscribe(descr -> this.addDataSourcesSplitMenuButton.setTooltip(new Tooltip(descr)));
                JavaFxObservable.actionEventsOf(this.addDataSourcesSplitMenuButton)
                                .observeOn(Schedulers.io())
                                .map(
                                    e -> defaultActionOpt.get().createAndAdd(new DataSourceActions.AddAction.ActionContext(this.addDataSourcesSplitMenuButton)))
                                .observeOn(JavaFxScheduler.platform())
                                .subscribe(this::displayAddActionResultMessage);
            } else {
                this.addDataSourcesSplitMenuButton.setText("N/A");
                this.addDataSourcesSplitMenuButton.setTooltip(new Tooltip("No Action available."));
                this.addDataSourcesSplitMenuButton.setOnAction(null);
            }
        });

        this.domainAdapter.getDataSourceActions().getAddActions().subscribe(addActions -> {

            addActions.forEach(action -> {
                Label label = new Label();
                label.textProperty().bind(JavaFxObserver.toBinding(action.getName().toObservable()));
                label.tooltipProperty().bind(JavaFxObserver.toBinding(action.getDescription().toObservable().map(Tooltip::new)));
                CustomMenuItem menuItem = new CustomMenuItem(label);
                JavaFxObservable.actionEventsOf(menuItem)
                                .observeOn(Schedulers.io())
                                .map(e -> action.createAndAdd(new DataSourceActions.AddAction.ActionContext(menuItem)))
                                .observeOn(JavaFxScheduler.platform())
                                .subscribe(this::displayAddActionResultMessage);
                this.addDataSourcesSplitMenuButton.getItems().add(menuItem);
            });
        });

        JavaFxObservable.valuesOf(this.enableDimReductionButton.selectedProperty()).subscribe(this.dexterCore.getDexterModel().getDimReductionEnabled());


        /*
        Plugin-Menu
         */
        Menu pluginMenu = new Menu("Plugins");
        this.menuBar.getMenus().add(pluginMenu);

        AtomicReference<CompositeDisposable> pluginActionsDisposable = new AtomicReference<>(new CompositeDisposable());
        this.appContext.getPluginRegistry()
                       .getPlugins()
                       .toObservable()
                       .doOnNext(__ -> pluginActionsDisposable.getAndSet(new CompositeDisposable()).dispose())
                       .map(pluginList -> pluginList.stream().map(plugin -> {

                           Menu subMenu = new Menu();
                           subMenu.textProperty().bind(JavaFxObserver.toBinding(plugin.getName().toObservable()));

                           pluginActionsDisposable.get().add(plugin.getActions().map(actions -> actions.stream().map(action -> {
                               MenuItem menuItem = new MenuItem();
                               menuItem.textProperty().bind(JavaFxObserver.toBinding(action.getName().toObservable()));
                               menuItem.setOnAction(event -> action.performAction(event.getSource()));
                               return menuItem;
                           }).collect(Collectors.toList())).subscribe(menuItems -> subMenu.getItems().setAll(menuItems)));

                           return subMenu;

                       }).collect(Collectors.toList()))
                       .observeOn(JavaFxScheduler.platform())
                       .doAfterNext(pluginList -> pluginMenu.setDisable(pluginList.isEmpty()))
                       .subscribe(subMenus -> pluginMenu.getItems().setAll(subMenus));


        /*
        Filter: Select enabled Labels
         */
        this.dexterModel.getFilterManager().getFilterModules().add(new FilterModule<DataEntity>("Class Filter", "Filter by Label") {

            Observable<Set<Optional<ClassLabel>>> enabledClassLabels = //
                domainAdapter.getRootDataSource() //
                             .map(__ -> HashBiMap.<Optional<ClassLabel>, CheckBox>create()) // checkbox-mapping: remember states; create new when root changes
                             .switchMap(checkBoxesForLabels -> //
                                 domainAdapter.getDomainData()
                                              .observeOn(Schedulers.computation())
                                              .switchMap(entities -> // set of current class labels from data entities
                                                  RxJavaUtils.combineLatest(entities, DataEntity::classLabelObs).map(HashSet::new).distinctUntilChanged())
                                              .map(currentLabels -> //
                                                  currentLabels.stream() //
                                                               // get (or create) check boxes for the current class labels
                                                               // (by reusing previous checkboxes, the previous state is preserved)
                                                               .sorted(Comparators.emptiesFirst(ClassLabel::compareTo)) //
                                                               .map(classLabel -> //
                                                                   checkBoxesForLabels.computeIfAbsent(classLabel,
                                                                       //
                                                                       newLabel -> {
                                                                           CheckBox newCheckBox = new CheckBox(
                                                                               newLabel.map(ClassLabel::getLabel).orElse(EMPTY_CLASS_LABEL));
                                                                           newCheckBox.setSelected(true); // enabled by default
                                                                           newCheckBox.setMnemonicParsing(false);
                                                                           return newCheckBox;
                                                                       })).collect(Collectors.toList()))
                                              .doOnNext(checkBoxes -> // insert into GUI
                                                  Platform.runLater(() -> classFilterPane.getChildren().setAll(checkBoxes)))
                                              .compose(
                                                  // listen to check boxes selection and filter for selected checkboxes
                                                  RxJavaUtils.deepFilter(
                                                      checkBox -> JavaFxObservable.valuesOf(checkBox.selectedProperty()).observeOn(Schedulers.computation())))
                                              .map(selectedCheckBoxes -> // map the currently selected checkboxes to their respective labels
                                                  selectedCheckBoxes.stream()
                                                                    .map(checkBox -> checkBoxesForLabels.inverse().get(checkBox))
                                                                    .filter(Objects::nonNull)
                                                                    .collect(Collectors.toSet()))) //
                             .distinctUntilChanged() //
                             .doOnNext(enabledClassLabels -> log.debug("Enabled Class Labels for Filter are: {}", enabledClassLabels)).replay(1) //
                             .refCount();

            @Override
            public Observable<Boolean> apply(DataEntity target) {
                return Observable.combineLatest(target.classLabelObs(), enabledClassLabels, (targetLabel, enabledSet) -> enabledSet.contains(targetLabel))
                                 // debounce is necessary, especially if an existing entity gets a new label, as then both combine-inputs will fire.
                                 .debounce(100, TimeUnit.MILLISECONDS) //
                                 .distinctUntilChanged();
            }
        });





        /*
        Active Learning: Pick Unlabeled
         */
        this.pickUnlabeledButton.disableProperty()
                                .bind(JavaFxObserver.toBinding(
                                    this.dexterModel.getActiveLearningModel().getClassificationUncertainty().map(Collection::isEmpty)));
        this.pickUnlabeledMultiButton.disableProperty()
                                .bind(JavaFxObserver.toBinding(
                                    this.dexterModel.getActiveLearningModel().getClassificationUncertainty().map(Collection::isEmpty)));

        JavaFxObservable.actionEventsOf(this.pickUnlabeledButton)
                        .switchMap(actionEvent -> this.dexterModel.getActiveLearningModel().getClassificationUncertainty().firstElement().toObservable())
                        // TODO: isInspected() isn't used currently
                        // .map(list->list.stream().filter(uncertainty->uncertainty.getDataEntity().isInspected().blockingFirst()).findFirst())
                        .map(list->list.stream().filter(uncertainty->!this.dexterCore.getAppContext().getInspectionHistory().getLabeledEntities().blockingFirst().contains(uncertainty.getDataEntity())).findFirst())
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(ActiveLearningModel.AbstractUncertainty::getDataEntity)
                        .subscribe(leastConfident -> InspectionView.createAndShow(this.dexterCore, leastConfident));

        JavaFxObservable.actionEventsOf(this.pickUnlabeledMultiButton)
                        .switchMap(actionEvent -> this.dexterModel.getActiveLearningModel().getClassificationUncertainty().firstElement().toObservable())
                        // TODO: isInspected() isn't used currently
                        // .map(list->list.stream().filter(uncertainty->uncertainty.getDataEntity().isInspected().blockingFirst()).findFirst())
                        .map(list->list.stream().filter(uncertainty->!this.dexterCore.getAppContext().getInspectionHistory().getLabeledEntities().blockingFirst().contains(uncertainty.getDataEntity())).limit(
                            ACTIVE_LEARNING_BATCH_SIZE).sorted(Comparator.comparingDouble(
                            ClassificationUncertainty::getUncertaintyValue)).map(ActiveLearningModel.AbstractUncertainty::getDataEntity).collect(Collectors.toList()))
                        .subscribe(leastConfidents -> leastConfidents.forEach(leastConfident->InspectionView.createAndShow(this.dexterCore, leastConfident)));


        /*
        Active Learning: Check Label
         */
        ConnectableObservable<List<CrossValidationUncertainty>> uncheckedUncertainties = Observable.combineLatest(
            this.dexterModel.getActiveLearningModel().getExistingLabelsUncertainty(), this.appContext.getInspectionHistory().getLabeledEntities(),
            (crossValidationUncertainties, checkedEntities) -> crossValidationUncertainties.stream()
                                                                                           .filter(Predicates.compose(checkedEntities::contains,
                                                                                               AbstractUncertainty::getDataEntity).negate())
                                                                                           .collect(Collectors.toList())).publish();

        this.checkLabelButton.disableProperty().bind(JavaFxObserver.toBinding(uncheckedUncertainties.map(Collection::isEmpty)));
        this.checkLabelMultiButton.disableProperty().bind(JavaFxObserver.toBinding(uncheckedUncertainties.map(Collection::isEmpty)));

        JavaFxObservable.actionEventsOf(this.checkLabelButton)
                        .withLatestFrom(uncheckedUncertainties, (actionEvent, crossValidationUncertainties) -> crossValidationUncertainties)
                        // TODO: isInspected() isn't used currently
                        // .map(list->list.stream().filter(uncertainty->uncertainty.getDataEntity().isInspected().blockingFirst()).findFirst())
                        .map(list->list.stream().filter(uncertainty->!this.dexterCore.getAppContext().getInspectionHistory().getLabeledEntities().blockingFirst().contains(uncertainty.getDataEntity())).findFirst())
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(ActiveLearningModel.AbstractUncertainty::getDataEntity)
                        .subscribe(leastConfident -> InspectionView.createAndShow(this.dexterCore, leastConfident));

        JavaFxObservable.actionEventsOf(this.checkLabelMultiButton)
                        .withLatestFrom(uncheckedUncertainties, (actionEvent, crossValidationUncertainties) -> crossValidationUncertainties)
                        // TODO: isInspected() isn't used currently
                        // .map(list->list.stream().filter(uncertainty->uncertainty.getDataEntity().isInspected().blockingFirst()).findFirst())
                        .map(list->list.stream().filter(uncertainty->!this.dexterCore.getAppContext().getInspectionHistory().getLabeledEntities().blockingFirst().contains(uncertainty.getDataEntity())).limit(
                            ACTIVE_LEARNING_BATCH_SIZE).sorted(Comparator.comparingDouble(CrossValidationUncertainty::getUncertaintyValue)).map(ActiveLearningModel.AbstractUncertainty::getDataEntity).collect(Collectors.toList()))
                        .subscribe(leastConfidents -> leastConfidents.stream().forEach(leastConfident -> InspectionView.createAndShow(this.dexterCore, leastConfident)));

        uncheckedUncertainties.connect();

        // reset history
        JavaFxObservable.actionEventsOf(this.clearCheckLabelHistoryMenuItem).subscribe(actionEvent -> this.appContext.getInspectionHistory().clear());


        /*
        Evaluate Model
         */
        JavaFxObservable.actionEventsOf(this.evaluateModelButton)
                        .subscribe(actionEvent -> ModelQualityView.createAndShow(
                            this.dexterModel.getClassificationModel().getCrossValidationResults().map(opt -> opt.map(ModelEvaluation::new)), this.dexterCore));


    }



    private void createTreeItemsForDataSources(DataSource currentDataSource, TreeItem<DataSource> currentTreeItem,
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

                taskUpdates.add(Observable.combineLatest(task.getName().toObservable(), task.getProgress().toObservable(), task.getMessage().toObservable(),
                    task.getState().toObservable(),

                    (name, progressOpt, messageOpt, state) -> {

                        final Label label = new Label(name + ":");
                        final Node progressBar = progressOpt.<Node>map(
                            progress -> new StackPane(new ProgressBar(progress), new Label(String.format("%.2f %%", progress * 100)))).orElse(
                            new ProgressBar());
                        final Label message = new Label(messageOpt.map(msg -> "[" + msg + "]").orElse(""));

                        return Arrays.asList(label, progressBar, message);
                    }).observeOn(JavaFxScheduler.platform()).subscribe(newChildren -> hBox.getChildren().setAll(newChildren)));

                return hBox;
            }).collect(Collectors.collectingAndThen(Collectors.toList(), hBoxes -> progressPane.getChildren().setAll(hBoxes)));

            return progressPane;

        }).observeOn(JavaFxScheduler.platform()).subscribe(progressPane -> this.statusBar.setCenter(progressPane));

    }

    private void initializeScatterChart() {
        CompositeDisposable innerDisposables = new CompositeDisposable();
        List<Optional<ClassLabel>> labelsHistory = new ArrayList<>();
        Observable<ObservableList<Series<Double, Double>>> chartDataObs = this.dexterModel.getVisualizationModel()
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
                                                                                                      .map(entityMapping -> entityMapping.getKey()
                                                                                                                                         .getClassLabel()
                                                                                                                                         .toObservable())
                                                                                                      .collect(Collectors.toList());
                                                                                                  return Observable.merge(
                                                                                                      labelObservables) // just another trigger for re-calculation
                                                                                                                   .debounce(100, TimeUnit.MILLISECONDS)
                                                                                                                   .doOnNext(__ -> innerDisposables.clear())
                                                                                                                   .map(__ -> entityMappingMap.entrySet()
                                                                                                                                              .stream()
                                                                                                                                              .collect(
                                                                                                                                                  Collectors.groupingBy(
                                                                                                                                                      entry -> entry
                                                                                                                                                          .getKey()
                                                                                                                                                          .getClassLabel()
                                                                                                                                                          .getValue())))
                                                                                                                   .map(label2EntityMappingsMap -> {
                                                                                                                       return label2EntityMappingsMap.entrySet()
                                                                                                                                                     .stream()
                                                                                                                                                     .sorted(
                                                                                                                                                         Comparator
                                                                                                                                                             .comparing(
                                                                                                                                                                 label2EntityMappings -> {
                                                                                                                                                                     Collator collator = Collator
                                                                                                                                                                         .getInstance();
                                                                                                                                                                     return label2EntityMappings
                                                                                                                                                                         .getKey()
                                                                                                                                                                         .map(
                                                                                                                                                                             ClassLabel::getLabel)
                                                                                                                                                                         .map(
                                                                                                                                                                             collator::getCollationKey)
                                                                                                                                                                         .orElse(
                                                                                                                                                                             collator
                                                                                                                                                                                 .getCollationKey(
                                                                                                                                                                                     ""));
                                                                                                                                                                 }))
                                                                                                                                                     .map(
                                                                                                                                                         label2EntityMappings -> {
                                                                                                                                                             Series<Double, Double> series = new Series<>();
                                                                                                                                                             series
                                                                                                                                                                 .setName(
                                                                                                                                                                     label2EntityMappings
                                                                                                                                                                         .getKey()
                                                                                                                                                                         .map(
                                                                                                                                                                             ClassLabel::getLabel)
                                                                                                                                                                         .orElse(
                                                                                                                                                                             MainView.EMPTY_CLASS_LABEL));

                                                                                                                                                             label2EntityMappings
                                                                                                                                                                 .getValue()
                                                                                                                                                                 .forEach(
                                                                                                                                                                     entityMapping -> {
                                                                                                                                                                         Data<Double, Double> dataItem = new Data<>();
                                                                                                                                                                         this.entity2chartData
                                                                                                                                                                             .put(
                                                                                                                                                                                 entityMapping
                                                                                                                                                                                     .getKey(),
                                                                                                                                                                                 dataItem); // TODO remove obsolete DataItems!?
                                                                                                                                                                         innerDisposables
                                                                                                                                                                             .add(
                                                                                                                                                                                 entityMapping
                                                                                                                                                                                     .getValue()
                                                                                                                                                                                     .getCoordinates()
                                                                                                                                                                                     .toObservable()
                                                                                                                                                                                     .observeOn(
                                                                                                                                                                                         JavaFxScheduler
                                                                                                                                                                                             .platform())
                                                                                                                                                                                     .subscribe(
                                                                                                                                                                                         doubles -> {
                                                                                                                                                                                             dataItem
                                                                                                                                                                                                 .setXValue(
                                                                                                                                                                                                     doubles[0]);
                                                                                                                                                                                             dataItem
                                                                                                                                                                                                 .setYValue(
                                                                                                                                                                                                     doubles[1]);
                                                                                                                                                                                         }));

                                                                                                                                                                         Observable<Node> dataNode = JavaFxObservable
                                                                                                                                                                             .nullableValuesOf(
                                                                                                                                                                                 dataItem
                                                                                                                                                                                     .nodeProperty())
                                                                                                                                                                             .filter(
                                                                                                                                                                                 Optional::isPresent)
                                                                                                                                                                             .map(
                                                                                                                                                                                 Optional::get);

                                                                                                                                                                         innerDisposables
                                                                                                                                                                             .add(
                                                                                                                                                                                 dataNode
                                                                                                                                                                                     .switchMap(
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
                                                                                                                                                                             .add(
                                                                                                                                                                                 dataNode
                                                                                                                                                                                     .switchMap(
                                                                                                                                                                                         node -> JavaFxObservable
                                                                                                                                                                                             .emitOnChanged(
                                                                                                                                                                                                 node.getStyleClass()))
                                                                                                                                                                                     .filter(
                                                                                                                                                                                         styleList -> !styleList
                                                                                                                                                                                             .isEmpty())
                                                                                                                                                                                     .firstElement()
                                                                                                                                                                                     .subscribe(
                                                                                                                                                                                         styleList -> {

                                                                                                                                                                                             if (!labelsHistory
                                                                                                                                                                                                 .contains(
                                                                                                                                                                                                     label2EntityMappings
                                                                                                                                                                                                         .getKey())) {
                                                                                                                                                                                                 labelsHistory
                                                                                                                                                                                                     .add(
                                                                                                                                                                                                         label2EntityMappings
                                                                                                                                                                                                             .getKey());
                                                                                                                                                                                             }

                                                                                                                                                                                             int serNr = labelsHistory
                                                                                                                                                                                                 .indexOf(
                                                                                                                                                                                                     label2EntityMappings
                                                                                                                                                                                                         .getKey());

                                                                                                                                                                                             styleList
                                                                                                                                                                                                 .setAll(
                                                                                                                                                                                                     "chart-symbol",
                                                                                                                                                                                                     "series"
                                                                                                                                                                                                         + serNr,
                                                                                                                                                                                                     "default-color"
                                                                                                                                                                                                         +
                                                                                                                                                                                                         serNr
                                                                                                                                                                                                             % 8);

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

                                                                                                                                                                         series
                                                                                                                                                                             .getData()
                                                                                                                                                                             .add(
                                                                                                                                                                                 dataItem);
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
            List<Data<Double, Double>> selection = optEntity.flatMap(key -> Optional.ofNullable(this.entity2chartData.get(key)))
                                                            .map(Collections::singletonList)
                                                            .orElse(Collections.emptyList());
            this.scatterChart.setCurrentSelection(selection);
        });
    }

    private void displayAddActionResultMessage(DataSourceActions.AddAction.Result result) {
        switch (result.getState()) {
            case SUCCESS:
                if (result.getCreatedSources().orElseThrow(() -> new IllegalStateException("Result for Success has empty Optional of created sources!")).isEmpty()) {
                    new Alert(AlertType.INFORMATION, "DataSource was successfully added but contains no Data: " + result.getMessage(),
                        ButtonType.OK).showAndWait();
                }  // else: do nothing
                break;
            case CANCELLED: // do nothing
                break;
            case CREATION_FAILED:
            case ADDING_FAILED:
                new Alert(AlertType.WARNING, "DataSource could not be added (" + result.getState() + "): " + result.getMessage(), ButtonType.OK).showAndWait();
                break;
        }
    }


    @FXML
    public void removeDataSource(final ActionEvent event) {
        new ArrayList<>(this.dataSourceTree.getSelectionModel().getSelectedItems()).forEach(selection -> {
            TreeItem<DataSource> parent = selection.getParent();
            if (parent != null) {
                parent.getValue().removeChildDataSource(selection.getValue());
            } else {
                log.warn("Removing DataSource {} not possible, as it has no parent in GUI-Tree.", selection.getValue());
            }
        });
        this.dataSourceTree.getSelectionModel().clearSelection();
    }


    @FXML
    public void showSettingsDialog(ActionEvent actionEvent) {
        FXMLLoader fxmlLoader = new FXMLLoader(SettingsView.class.getResource("SettingsView.fxml"));

        fxmlLoader.setControllerFactory(__ -> new SettingsView(this.appContext.getSettingsRegistry()));

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
