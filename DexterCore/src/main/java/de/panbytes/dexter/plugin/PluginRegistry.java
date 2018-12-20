package de.panbytes.dexter.plugin;

import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PluginRegistry {


    private final RxField<List<DexterPlugin>> plugins = RxField.withInitialValue(Collections.emptyList());


    public boolean add(DexterPlugin dexterPlugin) {
        synchronized (this.plugins) {
            ArrayList<DexterPlugin> updated = new ArrayList<>(plugins.getValue());
            boolean add = updated.add(dexterPlugin);
            this.plugins.setValue(Collections.unmodifiableList(updated));
            return add;
        }
    }

    public boolean remove(Object o) {
        synchronized (this.plugins) {
            ArrayList<DexterPlugin> updated = new ArrayList<>(plugins.getValue());
            boolean remove = updated.remove(o);
            plugins.setValue(Collections.unmodifiableList(updated));
            return remove;
        }
    }

    public void clear() {
        synchronized (this.plugins) {
            plugins.setValue(Collections.emptyList());
        }
    }

    public RxFieldReadOnly<List<DexterPlugin>> getPlugins() {
        return this.plugins.toReadOnlyView();
    }
}
