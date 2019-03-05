package de.panbytes.dexter.ext.prefs;

import java.util.prefs.Preferences;

public class RxPreferenceByteArray extends RxPreference<byte[]> {
    RxPreferenceByteArray(byte[] defaultValue, Class<?> associatedClass, String name,
        String additionalIdentifier) {
        super(defaultValue, associatedClass, name, additionalIdentifier, Preferences::getByteArray, Preferences::putByteArray);
    }
}
