package de.panbytes.dexter.plugin;

import com.google.common.collect.ImmutableSet;
import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.data.DataNode.Status;
import de.panbytes.dexter.core.data.DataSource;
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
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.FileChooser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelExportPlugin extends DexterPlugin {

    private static final Logger log = LoggerFactory.getLogger(ModelExportPlugin.class);

    private final char CSV_DELIMITER = ';';
    private final NumberFormat CSV_NUMBER_FORMAT = new DecimalFormat("0.0000");

    public ModelExportPlugin(DomainAdapter domainAdapter, DexterModel dexterModel) {
        super("Export Model", "Write Model (Classification, ...) to File.");

        setActions(Arrays.asList(new WriteCsvAction(dexterModel, false), new WriteCsvAction(dexterModel, true), new WriteCsvAction(domainAdapter)));

    }


    enum CsvHeader {
        DATA_SOURCE_NAME, ENTITY_NAME, STATUS, INITIAL_CLASS, CLASS_CHANGED, CURRENT_CLASS, UNCERTAINTY, SUGGESTED_CLASS, PROBABILITY_SUGGESTED_CLASS
    }

    public class WriteCsvAction extends Action {

        private final Single<List<Map<CsvHeader, Object>>> entityMaps;
        private final String filenameSuffix;
        private final RxPreferenceString fileChooserInitialDir = RxPreferenceString
            .createForIdentifier(WriteCsvAction.class, "fileChooserInitialDir")
            .buildWithDefaultValue("");

        private WriteCsvAction(DexterModel dexterModel, boolean classChangedOnly) {
            super("Classification Model" + (classChangedOnly ? " (changed only)" : ""),
                  "Write Model to CSV-File." + (classChangedOnly ? " Only export entities with changed class-label." : ""));
            entityMaps = entityMapsForUncertainties(dexterModel, classChangedOnly);
            filenameSuffix = classChangedOnly ? " # changed only" : "";
        }

        private WriteCsvAction(DomainAdapter domainAdapter) {
            super("Non-Active (rejected / invalid)", "Write Model to CSV-File. The model won't contain uncertainties, but it includes rejected entities.");
            entityMaps = entityMapsForStatus(domainAdapter, ImmutableSet.of(Status.REJECTED, Status.INVALID));
            filenameSuffix = " # rejected-invalid";
        }

        @Override
        public void performAction(Object source) {

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

            fileChooser.setInitialFileName("Dexter - Model (" + LocalDate.now() + ")" + filenameSuffix);
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File file = fileChooser.showSaveDialog(source instanceof Node ? ((Node) source).getScene().getWindow() : null);

            if (file != null) {
                fileChooserInitialDir.setValue(file.getParent());
                Schedulers.computation().scheduleDirect(() -> entityMaps.subscribe(maps -> writeCsv(maps, file.toPath()), //
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
}
