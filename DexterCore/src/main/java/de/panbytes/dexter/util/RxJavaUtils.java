package de.panbytes.dexter.util;

import com.google.common.collect.Streams;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class RxJavaUtils {

    /**
     * Note: {@code Single.fromCallable(task)} will throw an {@link io.reactivex.exceptions.UndeliverableException} if the callable throws after dispose!
     * Note: runs on no special scheduler!
     *
     * @return
     */
    public static <V> Single<V> singleFromCallable(Callable<V> callable) {
        return singleFromCallable(callable, null);
    }

    public static <V> Single<V> singleFromCallable(Callable<V> callable, Consumer<Throwable> errorHandler) {
        return Single.create(emitter -> {
            try {
                emitter.onSuccess(Objects.requireNonNull(callable.call(), "Callable returned null!"));
            } catch (Throwable t) {
                boolean handled = emitter.tryOnError(t); // swallows exception after dispose
                if (!handled && errorHandler != null) errorHandler.accept(t);
            }
        });
    }

    /**
     * Combine the latest item from each iterable's source-observable into a list of items.
     * For an empty Iterable, an empty List will be returned (this differs from {@link Observable#combineLatest(Iterable, Function)}!.
     * Nothing will be returned, if not all observables emit something!
     * @param sources the observable sources.
     * @param <T> the item's type.
     * @return a list containing the latest items or being empty for an empty input.
     */
    public static <T> Observable<List<T>> combineLatest(Iterable<? extends ObservableSource<? extends T>> sources) {
        return combineLatest(sources, java.util.function.Function.identity());
    }

    /**
     * Using the sourceToObservable-function, extract an observable from each iterable element, then combine the latest elements from each observable.
     * For an empty Iterable, an empty List will be returned (this differs from {@link Observable#combineLatest(Iterable, Function)}!.
     * Nothing will be returned, if not all observables emit something!
     * @param sources the sources, from which the observables will be extracted,
     * @param sourceToObservable the extractor-function providing an observable
     * @param <T> the source's type.
     * @param <R> the extracted type.
     * @return a list containing the latest observable items or being empty for an empty input.
     */
    @SuppressWarnings("unchecked")
    public static <T, R> Observable<List<R>> combineLatest(Iterable<? extends T> sources,
        java.util.function.Function<? super T, ? extends ObservableSource<? extends R>> sourceToObservable) {
        try {
            List<? extends ObservableSource<? extends R>> extracted = Streams.stream(sources).map(sourceToObservable).collect(Collectors.toList());
            return Observable.combineLatest(extracted, Arrays::asList).map(objects -> (List<R>) objects).defaultIfEmpty(Collections.emptyList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
