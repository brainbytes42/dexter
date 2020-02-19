package de.panbytes.dexter.plugin;

import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.model.DexterModel;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel.AbstractUncertainty;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel.ClassificationUncertainty;
import de.panbytes.dexter.core.model.activelearning.ActiveLearningModel.CrossValidationUncertainty;
import de.panbytes.dexter.core.model.classification.Classifier.ClassificationResult;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.FileChooser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public class ModelExportPlugin extends DexterPlugin {

    private final char CSV_DELIMITER = ';';
    private final NumberFormat CSV_NUMBER_FORMAT = new DecimalFormat("0.0000");

    public ModelExportPlugin(DomainAdapter domainAdapter, DexterModel dexterModel) {
        super("Export Model", "Write Model (Classification, ...) to File.");

        setActions(Collections.singletonList(new WriteCsvAction(domainAdapter, dexterModel)));

    }


    public class WriteCsvAction extends Action {

        private final DomainAdapter domainAdapter;
        private final DexterModel dexterModel;

        private WriteCsvAction(DomainAdapter domainAdapter, DexterModel dexterModel) {
            super("Write Model",
                  "Write Model to CSV-File.");
            this.domainAdapter = domainAdapter;
            this.dexterModel = dexterModel;
        }

        @Override
        public void performAction(Object source) {

            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName("Dexter - Model (" + LocalDate.now() + ")");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File file = fileChooser.showSaveDialog(source instanceof Node ? ((Node) source).getScene().getWindow() : null);

            if (file != null) {
                Schedulers.computation().scheduleDirect(() -> writeModelToCsv(file.toPath()));
            }

        }

        private void writeModelToCsv(Path path) {

            final Single<List<Map<CsvHeader, Object>>> csvEntityMaps = Observable.combineLatest(
                this.dexterModel.getActiveLearningModel().getExistingLabelsUncertainty(),
                this.dexterModel.getActiveLearningModel().getClassificationUncertainty(), //
                (existingLabelsUncertainties, classificationUncertainties) -> //
                    Stream.concat(existingLabelsUncertainties.stream(), classificationUncertainties.stream())
                          .sorted(Comparator.comparing(AbstractUncertainty::getUncertaintyValue, Comparator.reverseOrder())) // sorts concatenated stream!
                          .map(uncertainty -> {

                              final DataEntity dataEntity = uncertainty.getDataEntity();

                              Map<CsvHeader, Object> entityOutputMap = new LinkedHashMap<>(CsvHeader.values().length);

                              entityOutputMap.put(CsvHeader.DATA_SOURCE_NAME, dataEntity.getGeneratingDataSource().getName().getValue());
                              entityOutputMap.put(CsvHeader.ENTITY_NAME, dataEntity.getName().getValue());
                              dataEntity.getStatus().firstOrError().map(Objects::toString).subscribe(s -> entityOutputMap.put(CsvHeader.STATUS, s));

                              entityOutputMap.put(CsvHeader.CURRENT_CLASS, dataEntity.getClassLabel().getValue().map(Objects::toString).orElse(""));
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
                          .collect(Collectors.toList())).firstOrError();

            csvEntityMaps.subscribe(maps -> outputCsv(maps, path), //
                                    throwable -> {
                                        Alert alert = new Alert(AlertType.WARNING);
                                        alert.setHeaderText("Writing Model failed!");
                                        alert.setContentText(throwable.getMessage());
                                        alert.showAndWait();
                                    });

        }


        private void outputCsv(List<Map<CsvHeader, Object>> entityOutputMaps, Path path) throws IOException {

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

    enum CsvHeader {
        DATA_SOURCE_NAME, ENTITY_NAME, STATUS, CURRENT_CLASS, UNCERTAINTY, SUGGESTED_CLASS, PROBABILITY_SUGGESTED_CLASS
    }
}
