package de.panbytes.dexter.ext.prefs;

import java.util.prefs.Preferences;

public class RxPreferenceString extends RxPreference<String> {
    RxPreferenceString(String defaultValue, Class<?> associatedClass, String name,
        String additionalIdentifier) {
        super(defaultValue, associatedClass, name, additionalIdentifier, Preferences::get, Preferences::put);
    }
}
