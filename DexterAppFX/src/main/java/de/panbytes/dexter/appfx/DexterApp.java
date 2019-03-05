/**
 *
 */
package de.panbytes.dexter.appfx;

import de.panbytes.dexter.appfx.misc.WindowSizePersistence;
import de.panbytes.dexter.appfx.settings.DexterGeneralSettingsView;
import de.panbytes.dexter.core.AppContext;
import de.panbytes.dexter.core.DexterCore;
import de.panbytes.dexter.core.DomainSettings;
import de.panbytes.dexter.core.domain.DomainAdapter;
import io.reactivex.rxjavafx.observers.JavaFxObserver;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

import static com.google.common.base.Verify.verify;

/**
 * @author Fabian Krippendorff
 */
public abstract class DexterApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(DexterApp.class);

    private DexterCore dexterCore;
    private MainView mainView;


    /*
     * (non-Javadoc)
     *
     * @see javafx.application.Application#init()
     */
    @Override
    public final void init() throws Exception {

        /* NOT on JavaFX-Thread */

        super.init();

        this.dexterCore = new DexterCore(this::createDomainAdapter, createDomainSettings());

        this.dexterCore.getAppContext()
            .getSettingsRegistry()
            .getGeneralSettings()
            .setSettingsViewSupplier(new DexterGeneralSettingsView(
                this.dexterCore.getAppContext().getSettingsRegistry()
                    .getGeneralSettings())::createView);

        log.debug("Application has been initialized.");

        onInit();
    }

    protected DomainSettings createDomainSettings() {
        return new DomainSettings(getDomainIdentifier());
    }

    protected abstract String getDomainIdentifier();

    protected abstract DomainAdapter createDomainAdapter(AppContext appContext);

    protected void onInit() {
    }

    @Override
    public final void start(final Stage primaryStage) throws Exception {

        /* ON JavaFX-Thread */

        /*
         * Load the FXML and set the scene to the stage.
         */
        Scene scene = createSceneFromFxml(DexterApp.class.getResource("MainView.fxml"));
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.titleProperty()
            .bind(JavaFxObserver.toBinding(this.dexterCore.getDomainAdapter()
                .getName()
                .toObservable()
                .map(name -> "Dexter [" + name + "]")
                .observeOn(JavaFxScheduler.platform())));


        /* Remember Window-Size & -Position etc. */
        Map<String, DoubleProperty> propertiesMap = new HashMap<>();
        this.mainView.mainSplitPane.getDividers().stream().findFirst()
            .ifPresent(divider -> {
                propertiesMap.put("mainSplitPane", divider.positionProperty());
            });
        WindowSizePersistence.loadAndSaveOnClose(primaryStage,
            DexterApp.class.getSimpleName() + "." + getDomainIdentifier(),propertiesMap);



        log.debug("Application is up and running.");

        onStart();

    }


    protected void onStart() {
    }

    private Scene createSceneFromFxml(URL resource) throws IOException {
        // Setup the Main GUI FXML-Loader
        final FXMLLoader fxmlLoader = new FXMLLoader(resource);
        log.debug("Allocate FXML at {} and setup an FXMLLoader ({}).", fxmlLoader.getLocation(),
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
