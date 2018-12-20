package de.panbytes.dexter.lib.util.reactivex.extensions;

import io.reactivex.Observable;

public interface RxFieldReadOnly<T> {

    /**
     * Returns the current value.
     * <p>
     * Implementations are supposed to model this as thread-safe operation.
     *
     * @return the current value.
     */
    T getValue();

    /**
     * Returns an {@link Observable} streaming the values set to this field.
     * <p>
     * Implementations are supposed to model this adhering to the following contract:
     * <ul>
     * <li>On subscribing to the observable, the subscriber receives the current value. Any previous values are <em>not</em> contained.</li>
     * <li>Following values are emitted as soon as the value changes.</li>
     * <li>Only values ​​that are distinct to the previous one are emitted.</li>
     * </ul>
     *
     * @return the values as {@link Observable}, beginning with the current value.
     */
    Observable<T> toObservable();

}
