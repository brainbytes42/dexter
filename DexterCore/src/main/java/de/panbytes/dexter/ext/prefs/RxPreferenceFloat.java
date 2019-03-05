package de.panbytes.dexter.ext.prefs;

import java.util.prefs.Preferences;

public class RxPreferenceFloat extends RxPreference<Float> {
    RxPreferenceFloat(float defaultValue, Class<?> associatedClass, String name,
        String additionalIdentifier) {
        super(defaultValue, associatedClass, name, additionalIdentifier, Preferences::getFloat, Preferences::putFloat);
    }
}
