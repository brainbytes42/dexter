package de.panbytes.dexter.util;

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
     * If sources are empty, combiner is called with empty list.
     * @param sources
     * @param combiner
     * @param <T>
     * @param <R>
     * @return
     */
    public static <T, R> Observable<R> combineLatest(Iterable<? extends ObservableSource<? extends T>> sources,
                                                     Function<List<T>, ? extends R> combiner) {
        Function<? super Object[], ? extends R> combinerAdapter = objects -> combiner.apply(
                Arrays.stream(objects).map(obj -> (T) obj).collect(Collectors.toList()));
        try {
            return sources.iterator().hasNext()?Observable.combineLatest(sources, combinerAdapter):Observable.just(combiner.apply(Collections.emptyList()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T, E, R> Observable<R> combineLatest(Iterable<E> sources,
                                                        java.util.function.Function<E, ? extends ObservableSource<? extends T>> extractor,
                                                        Function<List<T>, ? extends R> combiner) {
        List<? extends ObservableSource<? extends T>> extracted = StreamSupport.stream(sources.spliterator(), true)
                                                                               .map(extractor)
                                                                               .collect(Collectors.toList());
        return combineLatest(extracted, combiner);
    }

}
