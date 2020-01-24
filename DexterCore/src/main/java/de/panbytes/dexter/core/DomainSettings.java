package de.panbytes.dexter.core;

import static com.google.common.base.Preconditions.checkNotNull;

import de.panbytes.dexter.ext.prefs.RxPreferenceString;
import javafx.scene.Node;

import java.util.Optional;

public class DomainSettings implements SettingsStorage {

    private final String domainIdentifier;
    private final RxPreferenceString rejectedClassLabel;

    public DomainSettings(String domainIdentifier) {
        this.domainIdentifier = checkNotNull(domainIdentifier);
        this.rejectedClassLabel = RxPreferenceString.createForIdentifier(DomainSettings.class,
            domainIdentifier, "rejectedClassLabel").buildWithDefaultValue("REJECTED");
    }

    @Override
    public Optional<Node> getSettingsView() {
        return Optional.empty();
    }

    public final RxPreferenceString rejectedClassLabel() {
        return this.rejectedClassLabel;
    }

    public final String getDomainIdentifier() {
        return domainIdentifier;
    }
}
