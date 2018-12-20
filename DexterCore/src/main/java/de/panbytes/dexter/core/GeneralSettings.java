package de.panbytes.dexter.core;

import de.panbytes.dexter.ext.prefs.RxPreference;
import de.panbytes.dexter.ext.prefs.RxPreferenceBoolean;
import de.panbytes.dexter.ext.prefs.RxPreferenceDouble;
import javafx.scene.Node;

import java.util.Optional;
import java.util.function.Supplier;

public class GeneralSettings implements SettingsStorage {


    private RxPreferenceDouble perplexity = RxPreference.createForIdentifier(GeneralSettings.class, "tsnePerplexity").buildWithDefaultValue(30.);
    private RxPreferenceBoolean classificationOnFilteredData = RxPreference.createForIdentifier(GeneralSettings.class, "classificationOnFilteredData")
                                                                           .buildWithDefaultValue(true);
    private Supplier<Node> viewSupplier;

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
