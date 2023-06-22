package de.panbytes.dexter.appfx;

import static com.google.common.base.Verify.verify;

import com.google.common.base.Preconditions;
import de.panbytes.dexter.appfx.misc.WindowSizePersistence;
import de.panbytes.dexter.appfx.settings.DexterGeneralSettingsView;
import de.panbytes.dexter.core.DexterCore;
import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.context.DomainSettings;
import de.panbytes.dexter.core.context.GeneralSettings;
import de.panbytes.dexter.core.domain.DomainAdapter;
import io.reactivex.rxjavafx.observers.JavaFxObserver;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Fabian Krippendorff
 */
public class DexterApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(DexterApp.class);

    private DexterCore dexterCore;
    private MainView mainView;

    private static final AtomicReference<DexterCore> dexterCoreAtomicReference = new AtomicReference<>();

    public static <D extends DomainAdapter> void launchApp(@Nonnull DomainSettings domainSettings, @Nonnull Function<AppContext, D> domainAdapterFactory,
                                                                 @Nullable Consumer<DexterCore> initCallback) {

        Preconditions.checkNotNull(domainSettings, "DomainSettings may not be null!");
        Preconditions.checkNotNull(domainAdapterFactory, "DomainAdapter-Factory may not be null!");

        GeneralSettings generalSettings = createGeneralSettings(domainSettings.getDomainIdentifier());
        AppContext appContext = new AppContext(generalSettings, domainSettings);

        D domainAdapter = domainAdapterFactory.apply(appContext);

        DexterCore dexterCore = new DexterCore(domainAdapter, appContext);

        if (initCallback != null) {
            initCallback.accept(dexterCore);
        }

        boolean successful = DexterApp.dexterCoreAtomicReference.compareAndSet(null, dexterCore);
        if (!successful) {
            throw new IllegalStateException("DexterCore has already been set!");
        }

        launch();
    }


    /*
     * (non-Javadoc)
     *
     * @see javafx.application.Application#init()
     */
    @Override
    public final void init() {

        /* NOT on JavaFX-Thread */

        if (DexterApp.dexterCoreAtomicReference.get() == null) {
            throw new java.lang.IllegalStateException("DexterCore has not yet been initialized! Use launchApp(.) to start the App.");
        }
        this.dexterCore = dexterCoreAtomicReference.get();

        log.debug("Application has been initialized.");
    }

    private static GeneralSettings createGeneralSettings(String domainIdentifier) {
        GeneralSettings generalSettings = new GeneralSettings(domainIdentifier);
        generalSettings.setSettingsViewSupplier(
            new DexterGeneralSettingsView(generalSettings)::createView);
        return generalSettings;
    }


    @Override
    public final void start(final Stage primaryStage) throws Exception {

        /* ON JavaFX-Thread */

        /*
         * Load the FXML and set the scene to the stage.
         * This links to the MainView's Controller, class de.panbytes.dexter.appfx.MainView.
         */
        Scene scene = createSceneFromFxml(DexterApp.class.getResource("MainView.fxml"));
        primaryStage.setScene(scene);
        primaryStage.show();

        /* set window-title */
        primaryStage.titleProperty()
            .bind(JavaFxObserver.toBinding(this.dexterCore.getDomainAdapter()
                .getName()
                .toObservable()
                .map(name -> "Dexter [" + name + "]")
                .observeOn(JavaFxScheduler.platform())));


        /* Remember Window-Size & -Position etc. */
        Map<String, DoubleProperty> propertiesMap = new HashMap<>();
        this.mainView.mainSplitPane.getDividers().stream().findFirst()
            .ifPresent(divider -> propertiesMap.put("mainSplitPane", divider.positionProperty()));
        WindowSizePersistence.loadAndSaveOnClose(primaryStage,
            DexterApp.class.getSimpleName() + "." + dexterCore.getAppContext().getDomainIdentifier(),propertiesMap);

        /* Close App (including other windows) when main window is closed */
        primaryStage.addEventHandler(WindowEvent.WINDOW_HIDDEN,
                __ -> {
                    log.info("Main Window closed - exit Application.");
                    Platform.exit();
                    System.exit(0);
                });

        log.debug("Application is up and running.");

    }


    private Scene createSceneFromFxml(URL resource) throws IOException {
        // Setup the Main GUI FXML-Loader
        final FXMLLoader fxmlLoader = new FXMLLoader(resource);
        log.trace("Allocate FXML at {} and setup an FXMLLoader ({}).", fxmlLoader.getLocation(),
            fxmlLoader);

        /*
         * custom MainView-Factory to inject DexterCore into the MainView
         */
        fxmlLoader.setControllerFactory(type -> {
            this.mainView = new MainView(this.dexterCore);

            verify(type.isInstance(mainView),
                "Mismatch for Type of Controller: FXML expects '%s', but is '%s'!", type,
                mainView.getClass());

            return mainView;
        });

        return new Scene(fxmlLoader.load());
    }

    @Override
    public final void stop() throws Exception {
        super.stop();

        log.debug("Application has been stopped.");

        onStop();
    }

    protected void onStop() {
    }

}
