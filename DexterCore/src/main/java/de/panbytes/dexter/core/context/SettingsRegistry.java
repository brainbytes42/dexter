package de.panbytes.dexter.core.context;

import de.panbytes.dexter.plugin.DexterPlugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO make reactive!
public class SettingsRegistry {

    private final GeneralSettings generalSettings;
    private final DomainSettings domainSettings;
    private final Map<DexterPlugin, Optional<SettingsStorage>> pluginSettings = new LinkedHashMap<>();

    public SettingsRegistry(GeneralSettings generalSettings, DomainSettings domainSettings) {
        this.generalSettings = checkNotNull(generalSettings);
        this.domainSettings = checkNotNull(domainSettings);
    }

    public GeneralSettings getGeneralSettings() {
        return this.generalSettings;
    }

    public DomainSettings getDomainSettings() {
        return this.domainSettings;
    }

    public Map<DexterPlugin, Optional<SettingsStorage>> getPluginSettings() {
        return Collections.unmodifiableMap(this.pluginSettings);
    }

    public void setPluginSettings(Map<DexterPlugin, Optional<SettingsStorage>> pluginSettings) {
        this.pluginSettings.clear();
        this.pluginSettings.putAll(pluginSettings);
    }
}
