package de.panbytes.dexter.ext.prefs;

import java.util.prefs.Preferences;

public class RxPreferenceLong extends RxPreference<Long> {
    RxPreferenceLong(long defaultValue, Class<?> associatedClass, String name) {
        super(defaultValue, associatedClass, name, Preferences::getLong, Preferences::putLong);
    }
}
