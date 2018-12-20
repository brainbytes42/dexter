package de.panbytes.dexter.core;

import javafx.scene.Node;

import java.util.Optional;

public interface SettingsStorage {

    default Optional<Node> getSettingsView() {
        return Optional.empty();
    }

}
