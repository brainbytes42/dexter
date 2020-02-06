package de.panbytes.dexter.appfx.settings;

import de.panbytes.dexter.core.context.SettingsRegistry;
import io.reactivex.rxjavafx.observers.JavaFxObserver;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class SettingsView {

    private final SettingsRegistry settingsRegistry;

    @FXML private TreeView<String> settingsTree;
    @FXML private TitledPane settingsViewContainer;

    public SettingsView(SettingsRegistry settingsRegistry) {
        this.settingsRegistry = settingsRegistry;
    }

    @FXML
    private void initialize() {

        Map<TreeItem<String>, Supplier<Optional<Node>>> itemMap = new HashMap<>();

        TreeItem<String> root = new TreeItem<>();
        this.settingsTree.setRoot(root);

        TreeItem<String> generalSettings = new TreeItem<>("Dexter");
        itemMap.put(generalSettings, this.settingsRegistry.getGeneralSettings()::getSettingsView);
        root.getChildren().add(generalSettings);

        TreeItem<String> domainSettings = new TreeItem<>("Domain");
        itemMap.put(domainSettings, this.settingsRegistry.getDomainSettings()::getSettingsView);
        root.getChildren().add(domainSettings);

        TreeItem<String> pluginSettings = new TreeItem<>("Plugins");
        itemMap.put(pluginSettings, () -> Optional.of(new Label("Choose plugin to see available Settings.")));
        pluginSettings.setExpanded(true);
        this.settingsRegistry.getPluginSettings().forEach((named, settingsStorageOpt) -> {
            TreeItem<String> item = new TreeItem<>();
            item.valueProperty().bind(JavaFxObserver.toBinding(named.getName().toObservable()));
            settingsStorageOpt.ifPresent(settings -> itemMap.put(item, settings::getSettingsView));
            pluginSettings.getChildren().add(item);
        });
        root.getChildren().add(pluginSettings);

        this.settingsTree.getSelectionModel().selectedItemProperty().addListener((__, ___, newSelection) -> {
            this.settingsViewContainer.textProperty().bind(newSelection.valueProperty());
            this.settingsViewContainer.setContent(
                    Optional.ofNullable(itemMap.get(newSelection)).flatMap(Supplier::get).orElse(new Label("No settings available.")));
        });

        this.settingsTree.getSelectionModel().select(generalSettings);
    }
}
