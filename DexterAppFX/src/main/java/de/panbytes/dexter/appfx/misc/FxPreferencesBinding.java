package de.panbytes.dexter.appfx.misc;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FxPreferencesBinding {

    private static final Logger log = LoggerFactory.getLogger(FxPreferencesBinding.class);
    private final Preferences prefs;

    public FxPreferencesBinding(Preferences prefs) {
        this.prefs = prefs;
    }

    public Preferences getPreferences() {
        return prefs;
    }

    public void bind(String key, ToggleButton toggleButton) {
        bind(key, toggleButton::isSelected, toggleButton::setSelected, toggleButton::selectedProperty, Objects::toString,
             s -> s == null || s.equals("null") ? null : Boolean.valueOf(s));
    }

    public void bind(String key, CheckBox checkBox) {
        bind(key, checkBox::isSelected, checkBox::setSelected, checkBox::selectedProperty, Objects::toString,
             s -> s == null || s.equals("null") ? null : Boolean.valueOf(s));
    }

    public void bind(String key, Spinner<Integer> spinner) {
        bind(key, spinner::getValue, spinner.getValueFactory()::setValue, spinner::valueProperty, Objects::toString,
             s -> s == null || s.equals("null") ? null : Integer.valueOf(s));
    }

    public <T> void bind(String key, ComboBoxBase<T> comboBox, Function<T, String> toString, Function<String, T> fromString) {
        bind(key, comboBox::getValue, comboBox::setValue, comboBox::valueProperty, toString, fromString);
    }

    public void bind(String key, ComboBoxBase<String> comboBox) {
        bind(key, comboBox::getValue, comboBox::setValue, comboBox::valueProperty, Function.identity(), Function.identity());
    }

    public void bind(String key, TextInputControl textInput) {
        bind(key, textInput::getText, textInput::setText, textInput::textProperty);
    }

    public void bind(String key, Supplier<String> getter, Consumer<String> setter, Supplier<ObservableValue<String>> observable) {
        bind(key, getter, setter, observable, Function.identity(), Function.identity());
    }

    public <T> void bind(String key, Supplier<T> getter, Consumer<T> setter, Supplier<ObservableValue<T>> observable, Function<T, String> toString,
        Function<String, T> fromString) {
        try {
            final String defaultValue = toString.apply(getter.get());
            final Supplier<T> prefValueSupplier = () -> fromString.apply(prefs.get(key, defaultValue));

            setter.accept(prefValueSupplier.get());
            observable.get().addListener((obs, oldValue, newValue) -> prefs.put(key, toString.apply(newValue)));
            prefs.addPreferenceChangeListener(prefChanged -> {
                if (prefChanged.getKey().equals(key)) {
                    Platform.runLater(() -> {
                        final T newValue = prefValueSupplier.get();
                        if (!Objects.equals(getter.get(), newValue)) {
                            setter.accept(newValue);
                        }
                    });
                }
            });
        } catch (Exception e) {
            log.warn("Binding for '" + key + "' failed!", e);
        }
    }

}
