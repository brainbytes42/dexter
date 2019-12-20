package de.panbytes.dexter.core;

import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.model.DexterModel;
import de.panbytes.dexter.plugin.DataExportPlugin;
import de.panbytes.dexter.plugin.DexterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Fabian Krippendorff
 */
public class DexterCore {

    /**
     * The Logger for DexterCore.
     */
    private static final Logger log = LoggerFactory.getLogger(DexterCore.class);

    static {
        /**
         * install jul-to-slf4j bridge
         *
         * @see http://www.slf4j.org/legacy.html
         */
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        log.trace("Translating Java-Logging to SLF4J.");
    }


    private final AppContext appContext;
    private final DomainAdapter domainAdapter;
    private final DexterModel dexterModel;

    public DexterCore(final DomainAdapter domainAdapter, AppContext appContext) {

        this.domainAdapter = checkNotNull(domainAdapter, "DomainAdapter is null!");
        this.appContext = appContext;

        this.dexterModel = new DexterModel(this.domainAdapter, this.appContext);

        // register export plugin
        this.appContext.getPluginRegistry().add(new DataExportPlugin(this.domainAdapter));

        // register plugins with settingsRegistry
        this.appContext.getPluginRegistry().getPlugins().toObservable().subscribe(plugins -> {
            LinkedHashMap<DexterPlugin, Optional<SettingsStorage>> pluginSettings = new LinkedHashMap<>();
            plugins.forEach(plugin -> pluginSettings.put(plugin, plugin.getPluginSettings()));
            this.appContext.getSettingsRegistry().setPluginSettings(pluginSettings);
        });
    }

    public AppContext getAppContext() {
        return this.appContext;
    }

    public DomainAdapter getDomainAdapter() {
        return this.domainAdapter;
    }

    public DexterModel getDexterModel() {
        return this.dexterModel;
    }

}
