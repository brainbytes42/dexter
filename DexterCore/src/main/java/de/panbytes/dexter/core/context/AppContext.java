package de.panbytes.dexter.core.context;

import de.panbytes.dexter.ext.task.TaskMonitor;
import de.panbytes.dexter.plugin.DexterPlugin;
import de.panbytes.dexter.plugin.PluginRegistry;
import java.util.LinkedHashMap;
import java.util.Optional;

public class AppContext {

    private final TaskMonitor taskMonitor;
    private final PluginRegistry pluginRegistry;
    private final SettingsRegistry settingsRegistry;
    private final InspectionHistory inspectionHistory;

    public AppContext(GeneralSettings generalSettings, DomainSettings domainSettings) {
        this.taskMonitor = new TaskMonitor();
        this.pluginRegistry = new PluginRegistry();
        this.settingsRegistry = new SettingsRegistry(generalSettings, domainSettings);
        this.inspectionHistory = new InspectionHistory();

        registerPluginsWithSettingsRegistry(this.pluginRegistry, this.settingsRegistry);
    }

    private void registerPluginsWithSettingsRegistry(PluginRegistry pluginRegistry, SettingsRegistry settingsRegistry) {
        pluginRegistry.getPlugins().toObservable().subscribe(plugins -> {
            LinkedHashMap<DexterPlugin, Optional<SettingsStorage>> pluginSettings = new LinkedHashMap<>();
            plugins.forEach(plugin -> pluginSettings.put(plugin, plugin.getPluginSettings()));
            settingsRegistry.setPluginSettings(pluginSettings);
        });
    }

    public TaskMonitor getTaskMonitor() {
        return this.taskMonitor;
    }

    public PluginRegistry getPluginRegistry() {
        return this.pluginRegistry;
    }

    public SettingsRegistry getSettingsRegistry() {
        return this.settingsRegistry;
    }

    public InspectionHistory getInspectionHistory(){
        return this.inspectionHistory;
    }

}
