package de.panbytes.dexter.impl.csv;

import de.panbytes.dexter.core.AppContext;
import de.panbytes.dexter.core.DataSourceActions;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.ext.prefs.RxPreferenceString;
import de.panbytes.dexter.ext.task.ObservableTask;
import io.reactivex.schedulers.Schedulers;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AddCsvFileAction extends DataSourceActions.AddAction {

    private static final Logger log = LoggerFactory.getLogger(AddCsvFileAction.class);

    private final RxPreferenceString initialFileChooserDir = RxPreferenceString.createForIdentifier(AddCsvFileAction.class,
                                                                                                    "initialFileChooserDir")
                                                                               .buildWithDefaultValue(System.getProperty("user.home"));

    private final AppContext appContext;


    AddCsvFileAction(CsvDomainAdapter domainAdapter, AppContext appContext) {
        super("Add File", "Add CSV-File (Format: ID;CLASS;Feature1;...;FeatureN)", domainAdapter);
        this.appContext = appContext;
    }

    @Override
    protected Collection<DataSource> createDataSources(ActionContext context) {

        final List<File> files = selectFiles(context);

        ObservableTask<Collection<DataSource>> createDataSourcesTask = new ObservableTask<Collection<DataSource>>("Create DataSources",
                                                                                                                  "Create DataSources for selected Files",
                                                                                                                  Schedulers.io()) {
            @Override
            protected Collection<DataSource> runTask() {

                AtomicInteger done = new AtomicInteger();

                return files.parallelStream()
                            .map(File::toPath)
                            .peek(path -> setMessage("Reading " + path.getFileName()))
                            .map(path1 -> CsvDataSource.create(path1, getDomainAdapter()))
                            .peek(path -> setProgress(done.incrementAndGet(), files.size()))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toList());

            }

        };

        this.appContext.getTaskMonitor().addTask(createDataSourcesTask);

        return createDataSourcesTask.result().blockingGet();

    }

    private List<File> selectFiles(ActionContext context) {

        final FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File as DataSource");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*"));

        try {
            final Path initialDir = Paths.get(this.initialFileChooserDir.getValue());
            if (Files.isDirectory(initialDir)) {
                fileChooser.setInitialDirectory(initialDir.toFile());
            }
        } catch (Exception e) {
            log.debug("Could not set Path \"" + this.initialFileChooserDir.getValue() + "\" as initial directory for FileChooser: ", e);
        }

        final Window ownerWindow = context.getSource().map(s -> s instanceof Node ? ((Node) s).getScene().getWindow() : null).orElse(null);

        FutureTask<List<File>> chooserTask = new FutureTask<>(() -> fileChooser.showOpenMultipleDialog(ownerWindow));
        Platform.runLater(chooserTask);

        List<File> files = Collections.emptyList();

        try {
            files = chooserTask.get();

            // update initial dir
            if (files != null && files.size() > 0) {
                final Path parent = files.get(0).toPath().normalize().getParent();
                if (parent != null) this.initialFileChooserDir.setValue(parent.toString());
            }

        } catch (InterruptedException | ExecutionException e) {
            log.warn("Exception in FileChooser: ", e);
        }

        return files;
    }


}
