package de.panbytes.dexter.util;

import com.google.common.collect.Streams;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.Single;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class RxJavaUtils {

    /**
     * Note: {@code Single.fromCallable(task)} will throw an {@link io.reactivex.exceptions.UndeliverableException} if the callable throws after dispose! Note:
     * runs on no special scheduler!
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
                if (!handled && errorHandler != null) {
                    errorHandler.accept(t);
                }
            }
        });
    }

    /**
     * Combine the latest item from each iterable's source-observable into a list of items. For an empty Iterable, an empty List will be returned (this differs
     * from {@link Observable#combineLatest(Iterable, io.reactivex.functions.Function)}!. Nothing will be returned, if not all observables emit something!
     *
     * @param sources the observable sources.
     * @param <T> the item's type.
     * @return a list containing the latest items or being empty for an empty input.
     */
    public static <T> Observable<List<T>> combineLatest(Iterable<? extends ObservableSource<? extends T>> sources) {
        return combineLatest(sources, Function.identity());
    }

    /**
     * Using the sourceToObservable-function, extract an observable from each iterable element, then combine the latest elements from each observable. For an
     * empty Iterable, an empty List will be returned (this differs from {@link Observable#combineLatest(Iterable, io.reactivex.functions.Function)}!. Nothing
     * will be returned, if not all observables emit something!
     *
     * @param sources the sources, from which the observables will be extracted,
     * @param sourceToObservable the extractor-function providing an observable
     * @param <T> the source's type.
     * @param <R> the extracted type.
     * @return a list containing the latest observable items or being empty for an empty input.
     */
    @SuppressWarnings("unchecked")
    public static <T, R> Observable<List<R>> combineLatest(Iterable<? extends T> sources,
        Function<? super T, ? extends ObservableSource<? extends R>> sourceToObservable) {
        try {
            List<? extends ObservableSource<? extends R>> extracted = Streams.stream(sources).map(sourceToObservable).collect(Collectors.toList());
            return Observable.combineLatest(extracted, Arrays::asList).map(objects -> (List<R>) objects).defaultIfEmpty(Collections.emptyList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Provide a filtering of an iterable input based on some observable contained in each iterable.
     * <ul>
     * <li>The transformed Observable will provide a List containing all items for which the predicate evaluated to true on their extracted observable
     * value.</li>
     * <li>The Observable will fire on each change in the original Iterable or any of the extracted observables, which means that consecutive emissions may be
     * identical ({@link Observable#distinctUntilChanged()} might be the right choice).</li>
     * <li>For an empty input Iterable, an empty List will be emitted.</li>
     * <li>If any of the deep Observables remains empty, nothing will be emitted, like for {@link Observable#combineLatest(Iterable,
     * io.reactivex.functions.Function, int)}!</li>
     * </ul>
     *
     * @param extractObservable the Function to get deep to the boolean-observable on which the filtering is applied (true = keep)
     * @param <T> the iterable's item-type
     * @return an {@link ObservableTransformer} to be used in e.g. {@link Observable#compose(ObservableTransformer)}.
     */
    public static <T> ObservableTransformer<Iterable<? extends T>, List<T>> deepFilter(
        Function<? super T, ? extends Observable<Boolean>> extractObservable) {

        return upstream -> upstream.compose(deepFilter(extractObservable, Boolean::booleanValue));
    }

 /**
     * Provide a filtering of an iterable input based on some observable contained in each iterable.
     * <ul>
     * <li>The transformed Observable will provide a List containing all items for which the predicate evaluated to true on their extracted observable
     * value.</li>
     * <li>The Observable will fire on each change in the original Iterable or any of the extracted observables, which means that consecutive emissions may be
     * identical ({@link Observable#distinctUntilChanged()} might be the right choice).</li>
     * <li>For an empty input Iterable, an empty List will be emitted.</li>
     * <li>If any of the deep Observables remains empty, nothing will be emitted, like for {@link Observable#combineLatest(Iterable,
     * io.reactivex.functions.Function, int)}!</li>
     * </ul>
     *
     * @param extractObservable the Function to get deep to the observable on which the filtering is applied
     * @param predicate predicate whether the observed value will be kept in the result
     * @param <T> the iterable's item-type
     * @param <P> the deep observable's type used by the predicate
     * @return an {@link ObservableTransformer} to be used in e.g. {@link Observable#compose(ObservableTransformer)}.
     */
    public static <T, P> ObservableTransformer<Iterable<? extends T>, List<T>> deepFilter(
        Function<? super T, ? extends Observable<? extends P>> extractObservable, Predicate<? super P> predicate) {

        return upstream -> upstream.compose(deepFilter(extractObservable, predicate, Collectors.toList()));
    }

    /**
     * Provide a filtering of an iterable input based on some observable contained in each iterable.
     * <ul>
     * <li>The transformed Observable will provide a Collection provided by the collector containing all items for which the predicate evaluated to true on their extracted observable
     * value.</li>
     * <li>The Observable will fire on each change in the original Iterable or any of the extracted observables, which means that consecutive emissions may be
     * identical ({@link Observable#distinctUntilChanged()} might be the right choice).</li>
     * <li>For an empty input Iterable, an empty List will be emitted.</li>
     * <li>If any of the deep Observables remains empty, nothing will be emitted, like for {@link Observable#combineLatest(Iterable,
     * io.reactivex.functions.Function, int)}!</li>
     * </ul>
     *
     * @param extractObservable the Function to get deep to the observable on which the filtering is applied
     * @param predicate predicate whether the observed value will be kept in the result
     * @param collector the {@link Collector} to assemble the resulting collection.
     * @param <T> the iterable's item-type
     * @param <P> the deep observable's type used by the predicate
     * @param <R> the result's type
     * @return an {@link ObservableTransformer} to be used in e.g. {@link Observable#compose(ObservableTransformer)}.
     */
    public static <T, P, R> ObservableTransformer<Iterable<? extends T>, R> deepFilter(
        Function<? super T, ? extends Observable<? extends P>> extractObservable, Predicate<? super P> predicate, Collector<? super T, ?, R> collector) {

        return upstream -> upstream.switchMap(iterable -> RxJavaUtils.combineLatest(iterable,
            item -> extractObservable.apply(item).map(value -> predicate.test(value) ? Optional.of(item) : Optional.<T>empty()))
                                                                     .debounce(100, TimeUnit.MILLISECONDS)
                                                                     .map(presentItemsAreActive -> presentItemsAreActive.stream()
                                                                                                                        .filter(Optional::isPresent)
                                                                                                                        .map(Optional::get)
                                                                                                                        .collect(collector)));
    }

}
