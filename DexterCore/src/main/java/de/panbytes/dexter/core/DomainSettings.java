package de.panbytes.dexter.core;

import de.panbytes.dexter.ext.prefs.RxPreferenceString;
import javafx.scene.Node;

import java.util.Optional;

public class DomainSettings implements SettingsStorage {


    private final RxPreferenceString rejectedClassLabel = rejectedClassLabel_create();

    @Override
    public Optional<Node> getSettingsView() {
        return Optional.empty();
    }


    public final RxPreferenceString rejectedClassLabel() {
        return this.rejectedClassLabel;
    }

    protected RxPreferenceString rejectedClassLabel_create() {
        return RxPreferenceString.createForIdentifier(DomainSettings.class, "rejectedClassLabel")
                                 .buildWithDefaultValue("REJECTED");
    }

}
