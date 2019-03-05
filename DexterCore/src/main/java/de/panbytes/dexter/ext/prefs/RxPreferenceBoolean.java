package de.panbytes.dexter.ext.prefs;

import java.util.prefs.Preferences;

public class RxPreferenceBoolean extends RxPreference<Boolean> {
    RxPreferenceBoolean(boolean defaultValue, Class<?> associatedClass, String name,
        String additionalIdentifier) {
        super(defaultValue, associatedClass, name, additionalIdentifier, Preferences::getBoolean, Preferences::putBoolean);
    }
}
