package de.panbytes.dexter.plugin;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DataNode.Status;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.model.DexterModel;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel.AbstractUncertainty;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel.ClassificationUncertainty;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel.CrossValidationUncertainty;
import de.panbytes.dexter.core.model.classification.Classifier.ClassificationResult;
import de.panbytes.dexter.ext.prefs.RxPreferenceString;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.FileChooser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModelExportPlugin extends DexterPlugin {

    private static final Logger log = LoggerFactory.getLogger(ModelExportPlugin.class);

    private final char CSV_DELIMITER = ';';
    private final NumberFormat CSV_NUMBER_FORMAT = new DecimalFormat("0.0000");

    private final RxPreferenceString fileChooserInitialDir = RxPreferenceString
            .createForIdentifier(WriteCsvAction.class, "fileChooserInitialDir")
            .buildWithDefaultValue("");

    private final DomainAdapter domainAdapter;
    private final DexterModel dexterModel;
    private final AppContext appContext;

    public ModelExportPlugin(DomainAdapter domainAdapter, DexterModel dexterModel, AppContext appContext) {
        super("Export / Apply Model", "Write Model (Classification, ...) to File or load previous changes.");
        this.domainAdapter = domainAdapter;
        this.dexterModel = dexterModel;
        this.appContext = appContext;

        setActions(Stream.concat(
                        Arrays.stream(WriteCsvActionMode.values()).map(WriteCsvAction::new),
                        Stream.of(new ReadCsvAction()))
                .collect(Collectors.toList())
        );
//        setActions(Arrays.asList(
//                new WriteCsvAction(false),
//                new WriteCsvAction(true),
//                new WriteCsvAction(),
//                new ReadCsvAction()
//                ));

    }


    enum CsvHeader {
        DATA_SOURCE_NAME, ENTITY_NAME, STATUS, INITIAL_CLASS, CLASS_CHANGED, INSPECTED, CURRENT_CLASS, UNCERTAINTY, SUGGESTED_CLASS, PROBABILITY_SUGGESTED_CLASS
    }

    enum WriteCsvActionMode{
        ACTIVE_CHANGED_ONLY, ACTIVE_ALL, REJECTED_AND_INVALID, ANY_CHANGE, ALL
    }

    public class WriteCsvAction extends Action {

        private final Single<List<Map<CsvHeader, Object>>> entityMaps;
        private final String filenameSuffix;

        private WriteCsvAction(WriteCsvActionMode mode) {
            super("WriteCsvAction["+mode+"]", "");
            filenameSuffix = " # " + mode.name();

            switch (mode) {
                case ACTIVE_CHANGED_ONLY: {
                    setName("Classification Model (Active; changed only)");
                    setDescription("Write Model (only active!) to CSV-File. Only export entities with changed class-label.");

                    entityMaps = entityMapsForUncertainties(dexterModel, true);

                    break;
                }
                case ACTIVE_ALL: {
                    setName("Classification Model (Active; all)");
                    setDescription("Write Model (only active!) to CSV-File.");

                    entityMaps = entityMapsForUncertainties(dexterModel, false);

                    break;
                }
                case REJECTED_AND_INVALID: {
                    setName("Non-Active (rejected / invalid)");
                    setDescription("Write Model to CSV-File. The model won't contain uncertainties, but it includes rejected entities.");

                    entityMaps = entityMapsForStatus(domainAdapter, ImmutableSet.of(Status.REJECTED, Status.INVALID));

                    break;
                }
                case ANY_CHANGE: {
                    setName("Any Change (labeled / rejected)");
                    setDescription("Write Model to CSV-File. Any changes (labels, rejects) are included.");

                    Single<List<Map<CsvHeader, Object>>> activeChanges = entityMapsForUncertainties(dexterModel, true);
                    Single<List<Map<CsvHeader, Object>>> rejected = entityMapsForStatus(domainAdapter, ImmutableSet.of(Status.REJECTED));

                    entityMaps = Single.zip(activeChanges, rejected, (act, rej) -> {
                        Function<Map<CsvHeader, Object>, String> toIdentifier = map -> map.get(CsvHeader.DATA_SOURCE_NAME) + ";" + map.get(CsvHeader.ENTITY_NAME);
                        Set<String> actEntities = act.stream().map(toIdentifier).collect(Collectors.toSet());

                        List<Map<CsvHeader, Object>> combined = new ArrayList<>(act);
                        combined.addAll(rej.stream().filter(map -> !actEntities.contains(toIdentifier.apply(map))).collect(Collectors.toList()));

                        return combined;
                    });

                    break;
                }
                case ALL: {
                    setName("All Entities");
                    setDescription("Write Model to CSV-File. Include all entities.");

                    Single<List<Map<CsvHeader, Object>>> active = entityMapsForUncertainties(dexterModel, false);
                    Single<List<Map<CsvHeader, Object>>> inactive = entityMapsForStatus(domainAdapter, ImmutableSet.of(Status.REJECTED, Status.DISABLED, Status.INVALID));

                    entityMaps = Single.zip(active, inactive, (act, inact) -> {
                        Function<Map<CsvHeader, Object>, String> toIdentifier = map -> map.get(CsvHeader.DATA_SOURCE_NAME) + ";" + map.get(CsvHeader.ENTITY_NAME);
                        Set<String> actEntities = act.stream().map(toIdentifier).collect(Collectors.toSet());

                        List<Map<CsvHeader, Object>> combined = new ArrayList<>(act);
                        combined.addAll(inact.stream().filter(map -> !actEntities.contains(toIdentifier.apply(map))).collect(Collectors.toList()));

                        return combined;
                    });

                    break;
                }
                default:
                    throw new IllegalStateException("Unknown mode!");
            }
        }

//        private WriteCsvAction(boolean classChangedOnly) {
//            super("Classification Model (Active" + (classChangedOnly ? ", changed only" : "")+")",
//                  "Write Model (only active!) to CSV-File." + (classChangedOnly ? " Only export entities with changed class-label." : ""));
//            entityMaps = entityMapsForUncertainties(dexterModel, classChangedOnly);
//            filenameSuffix = classChangedOnly ? " # changed only" : "";
//        }

//        private WriteCsvAction() {
//            super("Non-Active (rejected / invalid)", "Write Model to CSV-File. The model won't contain uncertainties, but it includes rejected entities.");
//            entityMaps = entityMapsForStatus(domainAdapter, ImmutableSet.of(Status.REJECTED, Status.INVALID));
//            filenameSuffix = " # rejected-invalid";
//        }

        @Override
        public void performAction(Object source) {

            FileChooser fileChooser = getFileChooser();
            fileChooser.setInitialFileName("Dexter - Model (" + LocalDate.now() + ")" + filenameSuffix);
            File file = fileChooser.showSaveDialog(source instanceof Node ? ((Node) source).getScene().getWindow() : null);

            if (file != null) {
                fileChooserInitialDir.setValue(file.getParent());
                Schedulers.io().scheduleDirect(() -> entityMaps.subscribe(maps -> writeCsv(maps, file.toPath()), //
                                                                                   throwable -> {
                                                                                       Alert alert = new Alert(AlertType.WARNING);
                                                                                       alert.setHeaderText("Writing Model failed!");
                                                                                       alert.setContentText(throwable.getMessage());
                                                                                       alert.showAndWait();
                                                                                   }));
            }

        }

        private Single<List<Map<CsvHeader, Object>>> entityMapsForUncertainties(DexterModel dexterModel, boolean classChangedOnly) {
            return Observable
                .combineLatest(dexterModel.getActiveLearningModel().getExistingLabelsUncertainty(),
                               dexterModel.getActiveLearningModel().getClassificationUncertainty(), //
                               (existingLabelsUncertainties, classificationUncertainties) -> //
                                   Stream
                                       .concat(existingLabelsUncertainties.stream(), classificationUncertainties.stream())
                                       .sorted(classChangedOnly ? // sorts concatenated stream!
                                               // Exporting only changed entities -> sort by name... (uncertainty is changed by model-modifications!)
                                               Comparator.comparing((AbstractUncertainty uncertainty) -> uncertainty.getDataEntity().getName().getValue()) :
                                               //... but for full model, sort by uncertainty.
                                               Comparator.comparing(AbstractUncertainty::getUncertaintyValue, Comparator.reverseOrder()))
                                       .map(uncertainty -> {

                                           final DataEntity dataEntity = uncertainty.getDataEntity();

                                           Map<CsvHeader, Object> entityOutputMap = createBasicEntityOutputMap(dataEntity);

                                           entityOutputMap.put(CsvHeader.UNCERTAINTY, CSV_NUMBER_FORMAT.format(uncertainty.getUncertaintyValue()));

                                           final Map<ClassLabel, Double> classLabelProbabilities;
                                           if (uncertainty instanceof ClassificationUncertainty) {
                                               final ClassificationResult classificationResult = ((ClassificationUncertainty) uncertainty).getClassificationResult();
                                               classLabelProbabilities = classificationResult.getClassLabelProbabilities();
                                           } else if (uncertainty instanceof CrossValidationUncertainty) {
                                               classLabelProbabilities = ((CrossValidationUncertainty) uncertainty).getAveragedClassificationResults();
                                           } else {
                                               throw new IllegalStateException("Unknown Uncertainty! (" + uncertainty.getClass() + ")");
                                           }
                                           classLabelProbabilities.entrySet().stream().max(Comparator.comparing(Entry::getValue)).ifPresent(entry -> {
                                               entityOutputMap.put(CsvHeader.SUGGESTED_CLASS, entry.getKey());
                                               entityOutputMap.put(CsvHeader.PROBABILITY_SUGGESTED_CLASS, CSV_NUMBER_FORMAT.format(entry.getValue()));
                                           });

                                           return entityOutputMap;
                                       })
                                       .filter(entityMap -> !classChangedOnly || !Objects.equals(entityMap.get(CsvHeader.INITIAL_CLASS),
                                                                                                 entityMap.get(CsvHeader.CURRENT_CLASS)))
                                       .collect(Collectors.toList()))
                .firstOrError();
        }

        private Single<List<Map<CsvHeader, Object>>> entityMapsForStatus(DomainAdapter domainAdapter, Set<Status> statuses) {
            return domainAdapter
                .getRootDataSource()
                .switchMap(dataSourceOpt -> dataSourceOpt
                    .map(DataSource::getSubtreeDataEntities)
                    .orElse(Observable.empty())
                    .map(domainDataEntities -> domainDataEntities
                        .stream()
                        .filter(entity -> statuses.contains(entity.getStatus().blockingFirst()))
                        .map(this::createBasicEntityOutputMap)
                        .collect(Collectors.toList())))
                .firstOrError();
        }

        private Map<CsvHeader, Object> createBasicEntityOutputMap(DataEntity dataEntity) {
            Map<CsvHeader, Object> entityOutputMap = new LinkedHashMap<>(CsvHeader.values().length);

            entityOutputMap.put(CsvHeader.DATA_SOURCE_NAME, dataEntity.getGeneratingDataSource().getName().getValue());
            entityOutputMap.put(CsvHeader.ENTITY_NAME, dataEntity.getName().getValue());
            dataEntity.getStatus().firstOrError().map(Objects::toString).subscribe(s -> entityOutputMap.put(CsvHeader.STATUS, s));

            final String initialClass = dataEntity.getInitialClassLabel().map(Objects::toString).orElse("");
            final String currentClass = dataEntity.getClassLabel().getValue().map(Objects::toString).orElse("");
            entityOutputMap.put(CsvHeader.INITIAL_CLASS, initialClass);
            entityOutputMap.put(CsvHeader.CLASS_CHANGED, initialClass.equals(currentClass) ? "equal" : "changed");
            entityOutputMap.put(CsvHeader.CURRENT_CLASS, currentClass);

            String inspected = Objects.toString(dataEntity.isInspected().blockingFirst() ||
                    appContext.getInspectionHistory().getLabeledEntities().blockingFirst().contains(dataEntity));
            entityOutputMap.put(CsvHeader.INSPECTED, inspected);

            return entityOutputMap;
        }


        private void writeCsv(List<Map<CsvHeader, Object>> entityOutputMaps, Path path) throws IOException {

            Writer out = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(CsvHeader.class).withDelimiter(CSV_DELIMITER))) {

                for (Map<CsvHeader, Object> entityOutputMap : entityOutputMaps) {
                    for (CsvHeader header : CsvHeader.values()) {
                        printer.print(entityOutputMap.get(header));
                    }
                    printer.println();
                }
            }
        }

    }

    private FileChooser getFileChooser() {
        FileChooser fileChooser = new FileChooser();

        final String initialDir = fileChooserInitialDir.getValue();
        try {
            final File initialDirFile = new File(initialDir);
            if (!initialDir.isEmpty() && initialDirFile.isDirectory()) {
                fileChooser.setInitialDirectory(initialDirFile);
            }
        } catch (Exception e) {
            log.warn("Failed setting initial Directory for model export to: " + initialDir, e);
        }

        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        return fileChooser;
    }

    public class ReadCsvAction extends Action {

        protected ReadCsvAction() {
            super("Import: Apply Model", "Load and apply model to current DataSources: Matching changes will be applied.");
        }

        @Override
        public void performAction(Object source) {
            Optional.ofNullable(getFileChooser().showOpenDialog(source instanceof Node ? ((Node) source).getScene().getWindow() : null))
                    .ifPresent(file -> {
                        fileChooserInitialDir.setValue(file.getParent());
                        Schedulers.io().scheduleDirect(() -> {
                            try {
                                boolean modelUpdateWasEnabled = dexterModel.getModelUpdateEnabled().blockingFirst();
                                if(modelUpdateWasEnabled) dexterModel.getModelUpdateEnabled().onNext(false);
                                boolean dimReductionWasEnabled = dexterModel.getDimReductionEnabled().blockingFirst();
                                if(dimReductionWasEnabled) dexterModel.getDimReductionEnabled().onNext(false);

                                applyModel(file);
                                Platform.runLater(() -> new Alert(AlertType.INFORMATION, "Changes have been applied.").showAndWait());

                                if(modelUpdateWasEnabled && dexterModel.getModelUpdateEnabled().blockingFirst()) dexterModel.getModelUpdateEnabled().onNext(true);
                                if(dimReductionWasEnabled && dexterModel.getDimReductionEnabled().blockingFirst()) dexterModel.getDimReductionEnabled().onNext(true);

                            } catch (Exception e) {
                                log.warn("Could not apply model from file '"+file+"'!", e);
                                Platform.runLater(() -> new Alert(AlertType.WARNING, "Could not apply model from file '"+file+"'!\n[See log for details.]").showAndWait());
                            }
                        });
                    });
        }

        private void applyModel(File file) throws IOException {
            List<String[]> lines = Files.readAllLines(file.toPath()).stream().map(s -> s.split(String.valueOf(CSV_DELIMITER))).collect(Collectors.toList());
            Preconditions.checkState(lines.size()>0,"File is empty!");

            List<String> header = Arrays.asList(lines.get(0));
            List<String[]> content = lines.subList(1, lines.size());

            Function<? super String[], Consumer<DataEntity>> createApplyToEntityConsumer = line -> {
                if (line[header.indexOf(CsvHeader.CLASS_CHANGED.name())].equals("changed")) {
                    return (Consumer<DataEntity>) dataEntity -> {
                        dataEntity.setClassLabel(ClassLabel.labelFor(line[header.indexOf(CsvHeader.CURRENT_CLASS.name())]));
                        dataEntity.setInspected(true);
                        appContext.getInspectionHistory().markInspected(dataEntity);
                    };
                } else if (line[header.indexOf(CsvHeader.STATUS.name())].equals(Status.REJECTED.name())) {
                    return (Consumer<DataEntity>) dataEntity -> {
                        dataEntity.setStatus(Status.REJECTED);
                        dataEntity.setInspected(true);
                        appContext.getInspectionHistory().markInspected(dataEntity);
                    };
                } else if (header.contains(CsvHeader.INSPECTED.name()) && line[header.indexOf(CsvHeader.INSPECTED.name())].equals(Objects.toString(true))) {
                    return (Consumer<DataEntity>) dataEntity -> {
                        dataEntity.setInspected(true);
                        appContext.getInspectionHistory().markInspected(dataEntity);
                    };
                } else {
                    throw new IllegalStateException("Unknown state to be applied!");
                }
            };

            Table<String, String, Consumer<DataEntity>> applyChangesTable = content.stream()
                    .filter(line -> line[header.indexOf(CsvHeader.CLASS_CHANGED.name())].equals("changed")
                            || line[header.indexOf(CsvHeader.STATUS.name())].equals(Status.REJECTED.name())
                            || header.contains(CsvHeader.INSPECTED.name()) && line[header.indexOf(CsvHeader.INSPECTED.name())].equals(Objects.toString(true)))
                    .collect(Tables.toTable(
                            (Function<? super String[], String>) line -> line[header.indexOf(CsvHeader.DATA_SOURCE_NAME.name())],
                            (Function<? super String[], String>) line -> line[header.indexOf(CsvHeader.ENTITY_NAME.name())],
                            createApplyToEntityConsumer,
                            (Supplier<Table<String, String, Consumer<DataEntity>>>) HashBasedTable::create));

            Optional<DataSource> rootDS = domainAdapter.getRootDataSource().blockingFirst();
            List<DomainDataEntity> currentDataEntities = rootDS.map(DataSource::getSubtreeDataEntities).map(Observable::blockingFirst).orElse(Collections.emptyList());

            for (DomainDataEntity dataEntity : currentDataEntities) {
                Optional.ofNullable(applyChangesTable.get(dataEntity.getGeneratingDataSource().getName().getValue(), dataEntity.getName().getValue()))
                        .ifPresent(applyChange -> applyChange.accept(dataEntity));
            }

        }
    }
}
