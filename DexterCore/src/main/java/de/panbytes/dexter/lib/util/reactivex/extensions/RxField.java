package de.panbytes.dexter.lib.util.reactivex.extensions;

import io.reactivex.Observable;
import io.reactivex.functions.BiPredicate;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class combines the classical approach of stateful fields ("POJO") with the reactive world,
 * modeling listeners for changed values as {@link Observable}.
 * <p>
 * The RxField is thread-safe.
 *
 * @param <T> The type of stored values.
 */
public class RxField<T> implements RxFieldReadOnly<T> {

    private final BehaviorSubject<T> rawBehaviorSubject;
    private final Subject<T> serializedSubject;
    private final Observable<T> publicObservable;

    private final AtomicReference<BiPredicate<T, T>> distinctValuesComparer = new AtomicReference<>(Objects::equals);
    private final AtomicReference<Function<T, T>> returnValueWrapper = new AtomicReference<>(Function.identity());

    protected RxField(T initialValue) {
        this.rawBehaviorSubject = BehaviorSubject.createDefault(initialValue);
        this.serializedSubject = this.rawBehaviorSubject.toSerialized(); // this is the key to thread-safety!
        this.publicObservable = this.serializedSubject.distinctUntilChanged((t1, t2) -> this.distinctValuesComparer.get().test(t1, t2))
                                                      .map(t -> this.returnValueWrapper.get().apply(t))
                                                      .replay(1)
                                                      .refCount();
    }

    /**
     * Create a new RxField with the given initial value.
     *
     * @param initialValue the field's initial value, which may not be {@code null}.
     *                     <p>
     *                     If you need to model {@code null}-values, think about using {@link java.util.Optional} as wrapper
     *                     (see {@link #initiallyEmpty()}).
     * @param <T>          the value's type.
     * @return the new field-instance.
     * @throws NullPointerException if value is {@code null}.
     */
    public static <T> RxField<T> withInitialValue(T initialValue) {
        return new RxField<>(initialValue);
    }

    /**
     * Create a new RxField which is initially empty, modeled by {@link Optional#empty()}.
     *
     * @param <T> the value's type (which will be wrapped by {@link Optional}).
     * @return the new field-instance.
     * @throws NullPointerException if value is {@code null} - use {@link Optional#empty()} instead.
     */
    public static <T> RxField<Optional<T>> initiallyEmpty() {
        return new RxField<>(Optional.empty());
    }

    /**
     * Returns the current value.
     * <p>
     * This is a thread-safe operation.
     *
     * @return the current value.
     */
    @Override
    public T getValue() {
        return this.returnValueWrapper.get().apply(this.rawBehaviorSubject.getValue());
    }

    /**
     * Sets a new value to this field.
     * <p>
     * This operation is thread-safe.
     *
     * @param value the value.
     * @throws NullPointerException if value is {@code null}.
     */
    public void setValue(T value) {
        Objects.requireNonNull(value);
        this.serializedSubject.onNext(value);
    }

    /**
     * Set a custom comparison-method to distinguish different values,
     * necessary for the {@link Observable} to emit only distinct values (see {@link Observable#distinctUntilChanged(BiPredicate)}).
     *
     * @param comparer the comparer - default is {@code Objects::equals}.
     * @return the field itself.
     * @throws NullPointerException if comparer is {@code null}.
     */
    public RxField<T> distinguishingValues(BiPredicate<T, T> comparer) {
        checkNotNull(comparer);
        this.distinctValuesComparer.set(comparer);
        return this;
    }

    /**
     * Set a custom wrapper, mapping return values ({@link #getValue()} and entities in {@link #toObservable()}) to some object of same type.
     * By default this is just {@link Function#identity()}, but may be eg. a function wrapping collections to be immutable.
     *
     * @param wrapper the wrapper-function.
     * @return the field itself.
     * @throws NullPointerException if wrapper is {@code null}.
     */
    public RxField<T> wrappingReturnedValues(Function<T, T> wrapper) {
        checkNotNull(wrapper);
        this.returnValueWrapper.set(wrapper);
        return this;
    }

    /**
     * Returns an {@link Observable} streaming the values set to this field.
     * <ul>
     * <li>On subscribing to the observable, the subscriber receives the current value. Any previous values are <em>not</em> contained.</li>
     * <li>Following values are emitted as soon as the value changes.</li>
     * <li>Only values ​​that are distinct to the previous one are emitted.</li>
     * </ul>
     *
     * @return the values as {@link Observable}, beginning with the current value.
     */
    @Override
    public Observable<T> toObservable() {
        return this.publicObservable;
    }

    /**
     * Return a view to this {@link RxField}, that has no mutator-methods.
     *
     * @return a read-only view for this field.
     */
    public RxFieldReadOnly<T> toReadOnlyView() {
        return this;
    }

}
