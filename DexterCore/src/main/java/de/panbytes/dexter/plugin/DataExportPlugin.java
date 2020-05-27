package de.panbytes.dexter.plugin;

import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.core.model.DexterModel;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataExportPlugin extends DexterPlugin {

    private final String CSV_DELIMITER = ";";
    private final NumberFormat CSV_NUMBER_FORMAT = new DecimalFormat("0.##########E0");

    public DataExportPlugin(DomainAdapter domainAdapter, DexterModel dexterModel) {
        super("Export data set", "Write (filtered) domain data to File.");

        setActions(Arrays.asList(new WriteCsvAction(domainAdapter, dexterModel, true), new WriteCsvAction(domainAdapter, dexterModel, false)));

    }


    public class WriteCsvAction extends Action {

        private final DomainAdapter domainAdapter;
        private final DexterModel dexterModel;
        private final boolean filteredDataOnly;

        private WriteCsvAction(DomainAdapter domainAdapter, DexterModel dexterModel, boolean filteredDataOnly) {
            super("Write CSV " + (filteredDataOnly ? "(filtered data)" : "(full data)"),
                  "Write " + (filteredDataOnly ? "filtered" : "unfiltered") + " Data as CSV.");
            this.domainAdapter = domainAdapter;
            this.dexterModel = dexterModel;
            this.filteredDataOnly = filteredDataOnly;
        }

        @Override
        public void performAction(Object source) {

            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName("Dexter - Export of Domain Data (" + LocalDate.now() + ")");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File file = fileChooser.showSaveDialog(source instanceof Node ? ((Node) source).getScene().getWindow() : null);

            if (file != null) {
                Schedulers.computation().scheduleDirect(() -> writeDomainDataToCsv(file.toPath()));
            }

        }

        private void writeDomainDataToCsv(Path path) {

            Optional<FeatureSpace> featureSpaceOptional = this.domainAdapter.getFeatureSpace().getValue();

            if (featureSpaceOptional.isPresent()) {

                Observable.using(() -> Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                                                               StandardOpenOption.TRUNCATE_EXISTING),

                                 writer -> Observable.concat(

                                         // HEADER
                                         Observable.just(new StringBuilder().append("DataSource")
                                                                            .append(DataExportPlugin.this.CSV_DELIMITER)
                                                                            .append("Entity")
                                                                            .append(DataExportPlugin.this.CSV_DELIMITER)
                                                                            .append("Class")
                                                                            .append(DataExportPlugin.this.CSV_DELIMITER)
                                                                            .append(featureSpaceOptional.get()
                                                                                                        .getFeatures()
                                                                                                        .stream()
                                                                                                        .map(FeatureSpace.Feature::getName)
                                                                                                        .collect(Collectors.joining(
                                                                                                                DataExportPlugin.this.CSV_DELIMITER)))
                                                                            .toString()),

                                         // LINES (filtered or not filtered)
                                         (this.filteredDataOnly
                                          ? this.dexterModel.getFilteredDomainData()
                                          : this.domainAdapter.getRootDataSource()
                                                              .switchMap(
                                                                      dataSourceOpt -> dataSourceOpt.map(DataSource::getSubtreeDataEntities)
                                                                                                    .orElseThrow(
                                                                                                            () -> new IllegalStateException(
                                                                                                                    "FeatureSpace is available, but DataSource is not!"))))
                                                 .firstElement() // current data
                                                 .flatMapObservable(dataEntities -> Observable.fromIterable(dataEntities.stream()
                                                                                                                        .sorted(Comparator.<DomainDataEntity, String>comparing(
                                                                                                                                entity -> entity
                                                                                                                                        .getGeneratingDataSource()
                                                                                                                                        .toString())
                                                                                                                                        .thenComparing(
                                                                                                                                                Object::toString))
                                                                                                                        .collect(
                                                                                                                                Collectors.toList()))
                                                                                              .map(entity -> Stream.concat(Stream.of(
                                                                                                      entity.getGeneratingDataSource()
                                                                                                            .toString(), entity.toString(),
                                                                                                      entity.getClassLabel()
                                                                                                            .getValue()
                                                                                                            .map(ClassLabel::toString)
                                                                                                            .orElse("")), Arrays.stream(
                                                                                                      entity.getCoordinates().getValue())
                                                                                                                                .mapToObj(
                                                                                                                                        DataExportPlugin.this.CSV_NUMBER_FORMAT::format))
                                                                                                                   .collect(
                                                                                                                           Collectors.joining(
                                                                                                                                   DataExportPlugin.this.CSV_DELIMITER))))


                                 ).doOnNext(line -> writer.append(line).append(System.lineSeparator())),

                                 BufferedWriter::close).subscribe(__ -> {}, Throwable::printStackTrace); //TODO exception-handling?!


            } else {

                new Alert(Alert.AlertType.WARNING,
                          "No FeatureSpace available - could not write any Data.\n" + "(Besides, there should be no Data without FeatureSpace...)",
                          ButtonType.OK).showAndWait();

            }
        }
    }
}
