package de.panbytes.dexter.core;

import de.panbytes.dexter.ext.task.TaskMonitor;
import de.panbytes.dexter.plugin.PluginRegistry;

public class AppContext {

    private final TaskMonitor taskMonitor;
    private final PluginRegistry pluginRegistry;
    private final SettingsRegistry settingsRegistry;
    private final InspectionHistory inspectionHistory;

    AppContext(GeneralSettings generalSettings, DomainSettings domainSettings) {
        this.taskMonitor = new TaskMonitor();
        this.pluginRegistry = new PluginRegistry();
        this.settingsRegistry = new SettingsRegistry(generalSettings, domainSettings);
        this.inspectionHistory = new InspectionHistory();
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