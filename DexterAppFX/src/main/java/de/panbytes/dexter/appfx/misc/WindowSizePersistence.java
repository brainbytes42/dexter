package de.panbytes.dexter.appfx.misc;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.collections.ObservableList;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

public class WindowSizePersistence {

    private final Window window;
    private final Preferences node;

    public WindowSizePersistence(Window window, String identifier) {
        this.window = window;
        this.node = Preferences.userNodeForPackage(WindowSizePersistence.class).node(identifier);
    }

    public static WindowSizePersistence loadAndSaveOnClose(Window window, String identifier) {
        return loadAndSaveOnClose(window, identifier, null);
    }

    public static WindowSizePersistence loadAndSaveOnClose(
        Window window, String identifier,
        Map<String, DoubleProperty> additionalProperties) {
        WindowSizePersistence windowSizePersistence = new WindowSizePersistence(window,
            identifier);
        windowSizePersistence.restoreWindowSize(additionalProperties);
        window.addEventHandler(WindowEvent.WINDOW_HIDING,
            event -> windowSizePersistence.saveWindowSize(additionalProperties));
        return windowSizePersistence;
    }

    public void saveWindowSize() {
        saveWindowSize(null);
    }

    public void saveWindowSize(Map<String, DoubleProperty> additionalProperties) {

        if (additionalProperties != null) {
            additionalProperties
                .forEach((name, property) -> node.putDouble(name, property.doubleValue()));
        }

        if (window instanceof Stage) {
            node.putBoolean("window.maximized", ((Stage) window).isMaximized());

            ((Stage) window).setMaximized(false); // needed to get non-maximized size
        }

        node.putDouble("window.x", window.getX());
        node.putDouble("window.y", window.getY());
        node.putDouble("window.width", window.getWidth());
        node.putDouble("window.height", window.getHeight());

        try {
            node.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace(); //TODO
        }
    }

    public void restoreWindowSize() {
        restoreWindowSize(null);
    }

    public void restoreWindowSize(Map<String, DoubleProperty> additionalProperties) {

        double x = node.getDouble("window.x", Double.NaN);
        double y = node.getDouble("window.y", Double.NaN);
        double width = node.getDouble("window.width", Double.NaN);
        double height = node.getDouble("window.height", Double.NaN);
        boolean maximized = node.getBoolean("window.maximized", false);

        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(width) && Double
            .isFinite(height)) {

            Screen primaryScreen = Screen.getPrimary();
            ObservableList<Screen> screens = Screen.getScreensForRectangle(x, y, width, height);
            double minX = screens.stream()
                .mapToDouble(screen -> screen.getVisualBounds().getMinX()).min()
                .orElse(primaryScreen.getVisualBounds().getMinX());
            double maxX = screens.stream()
                .mapToDouble(screen -> screen.getVisualBounds().getMaxX()).max()
                .orElse(primaryScreen.getVisualBounds().getMaxX());
            double minY = screens.stream()
                .mapToDouble(screen -> screen.getVisualBounds().getMinY()).min()
                .orElse(primaryScreen.getVisualBounds().getMinY());
            double maxY = screens.stream()
                .mapToDouble(screen -> screen.getVisualBounds().getMaxY()).max()
                .orElse(primaryScreen.getVisualBounds().getMaxY());

            window.setX(Math.max(minX, Math.min(x, maxX - width)));
            window.setY(Math.max(minY, Math.min(y, maxY - height)));
            window.setWidth(Math.min(width, maxX - minX));
            window.setHeight(Math.min(height, maxY - minY));

            if (window instanceof Stage) {
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Platform.runLater(() -> {
                        ((Stage) window).setMaximized(maximized);

                        if (additionalProperties != null) {
                            additionalProperties
                                .forEach((name, property) -> {
                                    double readValue = node.getDouble(name, Double.NaN);
                                    if (Double.isFinite(readValue)) {
                                        property.setValue(readValue);
                                    }
                                });
                        }
                    });
                }).start();
            }

        }


    }

}
