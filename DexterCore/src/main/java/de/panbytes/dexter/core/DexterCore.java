package de.panbytes.dexter.core;

import static com.google.common.base.Preconditions.checkNotNull;

import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.model.DexterModel;
import de.panbytes.dexter.plugin.DataExportPlugin;
import de.panbytes.dexter.plugin.ModelExportPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * @author Fabian Krippendorff
 */
public class DexterCore {

    /**
     * The Logger for DexterCore.
     */
    private static final Logger log = LoggerFactory.getLogger(DexterCore.class);

    static {
        /*
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

        this.domainAdapter = checkNotNull(domainAdapter, "DomainAdapter may not be null!");
        this.appContext = checkNotNull(appContext, "AppContext may not be null!");

        // create model
        this.dexterModel = new DexterModel(domainAdapter, appContext);

        // provide model to domain adapter
        domainAdapter.initDexterModel(this.dexterModel);

        // register export plugins
        appContext.getPluginRegistry().add(new ModelExportPlugin(this.domainAdapter, this.dexterModel, this.appContext));
        appContext.getPluginRegistry().add(new DataExportPlugin(this.domainAdapter, this.dexterModel));

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
