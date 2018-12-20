package de.panbytes.dexter.ext.prefs;

import java.util.prefs.Preferences;

public class RxPreferenceInt extends RxPreference<Integer> {
    RxPreferenceInt(int defaultValue, Class<?> associatedClass, String name) {
        super(defaultValue, associatedClass, name, Preferences::getInt, Preferences::putInt);
    }
}
