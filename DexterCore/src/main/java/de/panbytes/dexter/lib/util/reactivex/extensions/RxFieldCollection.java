package de.panbytes.dexter.lib.util.reactivex.extensions;


import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class RxFieldCollection<C extends Collection<T>, T> extends RxField<C> {

    private Function<C, C> copyFunction;
    private C collection;

    protected RxFieldCollection(C initialValue, Function<C, C> copyFunction) {
        super(initialValue);

        // without copy, mutations of collection are performed in super's value as well,
        // therefore, they are filtered by distinctUntilChanged, as super's value is equal to the changed collection.
        this.collection = copyFunction.apply(initialValue);

        this.copyFunction = copyFunction;
    }


    /**
     * Create a new RxField with the given initial value.
     *
     * @param initialValue the field's initial value, which may not be {@code null}.
     *                     <p>
     *                     If you need to model {@code null}-values, think about using {@link java.util.Optional} as wrapper
     *                     (see {@link #initiallyEmpty()}).
     * @param <C>          the collection's type.
     * @param <T>          the collection-element's type.
     * @return the new field-instance.
     * @throws NullPointerException if value is {@code null}.
     */
    public static <C extends Collection<T>, T> RxFieldCollection<C, T> withInitialValue(C initialValue, Function<C, C> copyFunction) {
        return new RxFieldCollection<>(initialValue, copyFunction);
    }

    /**
     * Sets a new value to this field. It uses the copyFunction to create a copy of the collection. This is necessary to filter duplicate
     * (and only duplicate) emissions in the observable stream.
     * <p>
     * This operation is thread-safe.
     *
     * @param value the value.
     * @throws NullPointerException if value is {@code null}.
     */
    @Override
    public synchronized void setValue(C value) {
        this.collection = this.copyFunction.apply(value);
        super.setValue(value);
        //super.setValue(this.copyFunction.apply(value));
    }

    /**
     * Delegates to current collection's {@link Collection#add(Object)} and trigger's {@link #setValue(Object)}
     * for the current collection if anything was changed.
     */
    public synchronized boolean add(T t) {
        boolean add = this.collection.add(t);
        if (add) setValue(this.collection);
        return add;
    }

    /**
     * Delegates to current collection's {@link Collection#remove(Object)} and trigger's {@link #setValue(Object)}
     * for the current collection if anything was changed.
     */
    public synchronized boolean remove(Object o) {
        boolean remove = this.collection.remove(o);
        if (remove) setValue(this.collection);
        return remove;
    }


    /**
     * replaces the old value by the new one, not necessary in the same place (atomic remove and add).
     *
     * @param old
     * @param update
     * @return true, if anything changed, even if only removed or only added.
     */
    public synchronized boolean replace(Object old, T update) {
        boolean changed = this.collection.remove(old);
        changed |= this.collection.add(update);
        if (changed) setValue(this.collection);
        return changed;
    }

    /**
     * Delegates to current collection's {@link Collection#addAll(Collection)}  and trigger's {@link #setValue(Object)}
     * for the current collection if anything was changed.
     */
    public synchronized boolean addAll(Collection<? extends T> c) {
        boolean addAll = this.collection.addAll(c);
        if (addAll) setValue(this.collection);
        return addAll;
    }

    /**
     * Delegates to current collection's {@link Collection#removeAll(Collection)}  and trigger's {@link #setValue(Object)}
     * for the current collection if anything was changed.
     */
    public synchronized boolean removeAll(Collection<?> c) {
        boolean removeAll = this.collection.removeAll(c);
        if (removeAll) setValue(this.collection);
        return removeAll;
    }

    /**
     * Delegates to current collection's {@link Collection#removeIf(Predicate)}  and trigger's {@link #setValue(Object)}
     * for the current collection if anything was changed.
     */
    public synchronized boolean removeIf(Predicate<? super T> filter) {
        boolean removeIf = this.collection.removeIf(filter);
        if (removeIf) setValue(this.collection);
        return removeIf;
    }

    /**
     * Delegates to current collection's {@link Collection#retainAll(Collection)}  and trigger's {@link #setValue(Object)}
     * for the current collection if anything was changed.
     */
    public synchronized boolean retainAll(Collection<?> c) {
        boolean retainAll = this.collection.retainAll(c);
        if (retainAll) setValue(this.collection);
        return retainAll;
    }

    /**
     * Delegates to current collection's {@link Collection#clear()} and trigger's {@link #setValue(Object)}
     * for the current collection if anything was changed.
     */
    public synchronized void clear() {
        if (!this.collection.isEmpty()) {
            this.collection.clear();
            setValue(this.collection);
        }
    }

}
