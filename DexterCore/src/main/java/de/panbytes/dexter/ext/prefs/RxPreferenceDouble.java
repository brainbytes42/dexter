package de.panbytes.dexter.ext.prefs;

import java.util.prefs.Preferences;

public class RxPreferenceDouble extends RxPreference<Double> {
    RxPreferenceDouble(double defaultValue, Class<?> associatedClass, String name,
        String additionalIdentifier) {
        super(defaultValue, associatedClass, name, additionalIdentifier, Preferences::getDouble, Preferences::putDouble);
    }
}
