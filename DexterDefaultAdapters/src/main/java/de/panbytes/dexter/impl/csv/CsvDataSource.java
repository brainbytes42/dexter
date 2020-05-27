package de.panbytes.dexter.impl.csv;

import com.google.common.base.Preconditions;
import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.core.domain.FileDataSource;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Observable;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class CsvDataSource extends FileDataSource {

    private static final Logger log = LoggerFactory.getLogger(CsvDataSource.class);

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.withDelimiter(';')
                                                                 .withTrim()
                                                                 .withFirstRecordAsHeader()
                                                                 .withCommentMarker('#');


    private CsvDataSource(Path path, FeatureSpace featureSpace, List<DataEntityBuilder> data) {

        super(path.getFileName().toString(), path.toString(), featureSpace, path);


        setGeneratedDataEntities(data.parallelStream().map(builder -> {
            final DomainDataEntity dataEntity = new DomainDataEntity(builder.id, path.toString(), builder.coordinates, featureSpace, this);
            if (builder.label != null && !builder.label.isEmpty()) dataEntity.setClassLabel(ClassLabel.labelFor(builder.label));
            return dataEntity;
        }).collect(Collectors.toList()));


        this.getGeneratedDataEntities()
            .toObservable()
            .switchMap(entities -> Observable.merge(entities.stream()
                                                            .map(entity -> entity.getClassLabel()
                                                                                 .toObservable()
                                                                                 .skip(1 /*first element is initial state!*/)
                                                                                 .map(label -> entity))
                                                            .collect(Collectors.toList())))
            .doOnNext(entity -> createFileBackup())
            .subscribe(entity -> writeChangedLabelToFile(entity.getName().getValue(),
                                                         entity.getClassLabel().getValue().map(ClassLabel::getLabel).orElse("")));
    }

    static Optional<DataSource> create(Path path, DomainAdapter domainAdapter) {

        DataSource newDataSource = null;

        try (CSVParser parser = CSV_FORMAT.parse(new FileReader(path.toFile()))) {

            final List<String> header = parser.getHeaderMap()
                                              .entrySet()
                                              .stream()
                                              .sorted(Comparator.comparing(Map.Entry::getValue))
                                              .map(Map.Entry::getKey)
                                              .collect(Collectors.toList());

            List<FeatureSpace.Feature> features = header.subList(2, header.size())
                                                        .stream()
                                                        .map(s -> new FeatureSpace.Feature(s))
                                                        .collect(Collectors.toList());
            domainAdapter.setFeatureSpace(
                    domainAdapter.getFeatureSpace().getValue().orElseGet(() -> new FeatureSpace("CSV", features)));

            final FeatureSpace featureSpace = domainAdapter.getFeatureSpace()
                                                           .toObservable()
                                                           .filter(Optional::isPresent)
                                                           .map(Optional::get)
                                                           .blockingFirst();
            final List<String> featureSpaceFeatures = featureSpace.getFeatures()
                                                                  .parallelStream()
                                                                  .map(FeatureSpace.Feature::getName)
                                                                  .map(RxFieldReadOnly::getValue)
                                                                  .collect(Collectors.toList());
            Preconditions.checkState(featureSpaceFeatures.equals(header.subList(2, header.size())),
                                     "FeatureSpace does not match File's Header.");


            final List<DataEntityBuilder> data = StreamSupport.stream(parser.spliterator(), true).map(record -> {
                final String id = record.get(0);
                final String label = record.get(1);
                final double[] coordinates = featureSpaceFeatures.stream().map(record::get).mapToDouble(Double::parseDouble).toArray();
                return new DataEntityBuilder(id, label, coordinates);
            }).collect(Collectors.toList());

            newDataSource = new CsvDataSource(path, featureSpace, data);


        } catch (IOException e) {
            log.warn("Could not read File as DataSource (" + path + ")", e);
        }

        return Optional.ofNullable(newDataSource);
    }

    private void writeChangedLabelToFile(String id, String newLabel) throws IOException {

        final List<String> updatedContent = Files.lines(getFilePath()).map(line -> {
            final String[] split = line.split(Character.toString(CSV_FORMAT.getDelimiter()), 3); // max 3 chunks: id, label, content
            return split[0].trim().equals(id)
                   ? split[0] + CSV_FORMAT.getDelimiter() + newLabel + CSV_FORMAT.getDelimiter() + split[2] // replace label
                   : line;
        }).collect(Collectors.toList());

        Files.write(getFilePath(), updatedContent);

    }

    private static class DataEntityBuilder {
        private final String id;
        private final String label;
        private final double[] coordinates;

        DataEntityBuilder(String id, String label, double[] coordinates) {
            this.id = id;
            this.label = label;
            this.coordinates = coordinates;
        }
    }
}
