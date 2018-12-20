package de.panbytes.dexter.ext.prefs;

import java.util.prefs.Preferences;

public class RxPreferenceFloat extends RxPreference<Float> {
    RxPreferenceFloat(float defaultValue, Class<?> associatedClass, String name) {
        super(defaultValue, associatedClass, name, Preferences::getFloat, Preferences::putFloat);
    }
}
