package de.panbytes.dexter.core.context;

import de.panbytes.dexter.ext.prefs.RxPreference;
import de.panbytes.dexter.ext.prefs.RxPreferenceBoolean;
import de.panbytes.dexter.ext.prefs.RxPreferenceDouble;
import javafx.scene.Node;

import java.util.Optional;
import java.util.function.Supplier;

public class GeneralSettings implements SettingsStorage {

    private final RxPreferenceDouble perplexity;
    private final RxPreferenceBoolean classificationOnFilteredData;

    private Supplier<Node> viewSupplier;

    public GeneralSettings(String domainIdentifier) {

        this.perplexity = RxPreference.createForIdentifier(GeneralSettings.class, domainIdentifier, "tsnePerplexity").buildWithDefaultValue(30.);
        this.classificationOnFilteredData = RxPreference.createForIdentifier(GeneralSettings.class, domainIdentifier, "classificationOnFilteredData")
                                                                               .buildWithDefaultValue(true);
    }

    @Override
    public Optional<Node> getSettingsView() {
        return Optional.ofNullable(viewSupplier.get());
    }


    public RxPreferenceDouble getPerplexity() {
        return this.perplexity;
    }

    public RxPreferenceBoolean getClassificationOnFilteredData() {
        return this.classificationOnFilteredData;
    }

    public void setSettingsViewSupplier(Supplier<Node> viewSupplier) {
        this.viewSupplier = viewSupplier;
    }
}
