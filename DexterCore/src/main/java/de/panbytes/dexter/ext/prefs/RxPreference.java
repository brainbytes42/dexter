package de.panbytes.dexter.ext.prefs;

import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import io.reactivex.functions.BiPredicate;

import java.util.function.Function;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default implementation uses java's Preferences model. Find values in Windows registry: Computer\HKEY_CURRENT_USER\Software\JavaSoft\Prefs\
 * @param <T>
 */
public class RxPreference<T> extends RxField<T> implements RxPreferenceReadOnly<T> {

    private static final Logger log = LoggerFactory.getLogger(RxPreference.class);

    private final T defaultValue;

    RxPreference(T defaultValue,
        Class<?> associatedClass,
        String name,
        String additionalIdentifier, PreferenceReader<T> preferenceReader,
        PreferenceWriter<T> preferenceWriter) {

        // initial value is stored value if available, otherwise default value.
        super(preferenceReader.read(getPreferences(associatedClass, additionalIdentifier), name, defaultValue));

        this.defaultValue = checkNotNull(defaultValue,"Default value may not be null for preference!");

        // write new values
        // TODO error-handling
        toObservable().skip(1)
                      .subscribe(newValue -> preferenceWriter.write(getPreferences(associatedClass, additionalIdentifier), name,
                                                                    newValue));

        // include changes from outside, defaulting to current value
        getPreferences(associatedClass, additionalIdentifier).addPreferenceChangeListener(evt -> {
            if (evt.getKey().equals(name)) {
                setValue(preferenceReader.read(getPreferences(associatedClass, additionalIdentifier), name, getValue()));
            }
        });

    }

    public void resetToDefault(){
        setValue(this.defaultValue);
    }

    private static Preferences getPreferences(Class<?> associatedClass, String additionalIdentifier) {
        Preferences preferences = Preferences.userNodeForPackage(associatedClass)
            .node(associatedClass.getSimpleName());
        return additionalIdentifier!=null ? preferences.node(additionalIdentifier) : preferences;
    }

    public static <T> PreferenceBuilderStepDefaultValue<T> createForIdentifier(Class<?> associatedClass, String name) {
        return new Builder<T>(associatedClass, null, name);
    }

    public static <T> PreferenceBuilderStepDefaultValue<T> createForIdentifier(Class<?> associatedClass, String additionalIdentifier, String name) {
        return new Builder<T>(associatedClass, additionalIdentifier, name);
    }

    @Override
    public RxPreference<T> distinguishingValues(BiPredicate<T, T> comparer) {
        return (RxPreference<T>) super.distinguishingValues(comparer);
    }

    @Override
    public RxPreference<T> wrappingReturnedValues(Function<T, T> wrapper) {
        return (RxPreference<T>) super.wrappingReturnedValues(wrapper);
    }

    @Override
    public RxPreferenceReadOnly<T> toReadOnlyView() {
        return (RxPreferenceReadOnly<T>) super.toReadOnlyView();
    }

    interface PreferenceReader<T> {
        T read(Preferences preferences, String key, T defaultValue);
    }

    interface PreferenceWriter<T> {
        void write(Preferences preferences, String key, T value);
    }

    public interface PreferenceBuilderStepDefaultValue<T> {
        PreferenceBuilderStepMarshalling<T> withDefaultValue(T defaultValue);

        RxPreferenceBoolean buildWithDefaultValue(boolean defaultValue);

        RxPreferenceByteArray buildWithDefaultValue(byte[] defaultValue);

        RxPreferenceDouble buildWithDefaultValue(double defaultValue);

        RxPreferenceFloat buildWithDefaultValue(float defaultValue);

        RxPreferenceInt buildWithDefaultValue(int defaultValue);

        RxPreferenceLong buildWithDefaultValue(long defaultValue);

        RxPreferenceString buildWithDefaultValue(String defaultValue);
    }

    public interface PreferenceBuilderFinal<T> {
        RxPreference<T> build();
    }

    public interface PreferenceBuilderStepMarshalling<T> {
        PreferenceBuilderFinal<T> withMarshalling(Function<T,String> marshaller,Function<String,T> unmarshaller);
    }

    static class Builder<T> implements PreferenceBuilderStepDefaultValue<T>, PreferenceBuilderStepMarshalling<T>, PreferenceBuilderFinal<T> {
        private final Class<?> associatedClass;
        private final String additionalIdentifier;
        private final String name;
        private T defaultValue;
        private PreferenceReader<T> reader;
        private PreferenceWriter<T> writer;

        Builder(Class<?> associatedClass, String additionalIdentifier, String name) {
            this.associatedClass = associatedClass;
            this.additionalIdentifier = additionalIdentifier;
            this.name = name;
        }

        @Override
        public PreferenceBuilderStepMarshalling<T> withDefaultValue(T defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        @Override
        public RxPreferenceBoolean buildWithDefaultValue(boolean defaultValue) {
            return new RxPreferenceBoolean(defaultValue, this.associatedClass, this.name, this.additionalIdentifier);
        }

        @Override
        public RxPreferenceByteArray buildWithDefaultValue(byte[] defaultValue) {
            return new RxPreferenceByteArray(defaultValue, this.associatedClass, this.name, this.additionalIdentifier);
        }

        @Override
        public RxPreferenceDouble buildWithDefaultValue(double defaultValue) {
            return new RxPreferenceDouble(defaultValue, this.associatedClass, this.name, this.additionalIdentifier);
        }

        @Override
        public RxPreferenceFloat buildWithDefaultValue(float defaultValue) {
            return new RxPreferenceFloat(defaultValue, this.associatedClass, this.name, this.additionalIdentifier);
        }

        @Override
        public RxPreferenceInt buildWithDefaultValue(int defaultValue) {
            return new RxPreferenceInt(defaultValue, this.associatedClass, this.name, this.additionalIdentifier);
        }

        @Override
        public RxPreferenceLong buildWithDefaultValue(long defaultValue) {
            return new RxPreferenceLong(defaultValue, this.associatedClass, this.name, this.additionalIdentifier);
        }

        @Override
        public RxPreferenceString buildWithDefaultValue(String defaultValue) {
            return new RxPreferenceString(defaultValue, this.associatedClass, this.name, this.additionalIdentifier);
        }

        @Override
        public RxPreference<T> build() {
            return new RxPreference<>(this.defaultValue, this.associatedClass, this.name,
                additionalIdentifier, this.reader, this.writer);
        }

        @Override
        public Builder<T> withMarshalling(Function<T,String> marshaller, Function<String,T> unmarshaller) {
            this.reader = (preferences, key, def) -> {
                try {
                    return unmarshaller.apply(preferences.get(key, marshaller.apply(def)));
                } catch (Exception e) {
                    log.warn("Recovered from Exception in unmarshalling of preference using default value! Key was '"+key+"'; default was '"+def+"'.",e);
                    return def;
                }
            };
            this.writer = (preferences, key, val) -> {
                try {
                    preferences.put(key, marshaller.apply(val));
                } catch (Exception e) {
                    log.warn("Exception on writing preference! Key was '"+key+"'; value was '"+val+"'.",e);
                }
            };
            return this;
        }

    }

}