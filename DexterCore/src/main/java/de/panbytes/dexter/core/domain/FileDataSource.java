package de.panbytes.dexter.core.domain;


import de.panbytes.dexter.core.data.DataNode;
import de.panbytes.dexter.core.data.DataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Fabian Krippendorff
 * <p>
 * TODO: non-abstract
 */
public abstract class FileDataSource extends DataSource {

    private final Path filePath;

    /**
     * Create a new {@code DataSource} with the given name.
     *
     * @param name         the DataSource's name.
     * @param description
     * @param featureSpace
     * @param path
     * @throws NullPointerException if the name is null.
     * @see DataNode#DataNode(String, String, FeatureSpace)
     */
    protected FileDataSource(String name, String description, FeatureSpace featureSpace, Path path) {
        super(name, description, featureSpace);
        this.filePath = checkNotNull(path, "Path may not be null!");
    }

    public final Path getFilePath() {
        return filePath;
    }


    @Override
    public String toString() {
        return filePath.getFileName().toString();
    }

    /**
     * create a backup auf the file referenced by the datasource's file-path.
     * @throws IOException
     */
    protected void createFileBackup() throws IOException {
        Path file = this.getFilePath();
        Files.copy(file, Files.createDirectories(file.resolveSibling(".backup"))
                              .resolve(file.getFileName().toString() + ".backup_" + DateTimeFormatter.ofPattern("yyyy-MM-dd_AAAAAAAA") // date and milliseconds of that tay
                                                                                                     .withZone(ZoneId.systemDefault())
                                                                                                     .format(Instant.now())));
    }
}
