package de.panbytes.dexter.core.model;

import com.google.common.collect.Iterables;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldCollection;
import de.panbytes.dexter.util.Named;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FilterManager<T> {

    private static final Logger log = LoggerFactory.getLogger(FilterManager.class);
    private final Observable<Set<T>> output;
    private final RxFieldCollection<Set<FilterModule<? super T>>, FilterModule<? super T>> filterModules = RxFieldCollection.withInitialValue(
        Collections.emptySet(), HashSet::new);

    public FilterManager(Observable<? extends Iterable<T>> input) {

        // use only enabled filters
        Observable<Set<FilterModule<? super T>>> activeFilters = this.filterModules.toObservable()
                                                                                   .compose(RxJavaUtils.deepFilter(FilterModule::isEnabled, enabled -> enabled,
                                                                                       Collectors.toSet())).debounce(250, TimeUnit.MILLISECONDS);

        output = Observable.switchOnNext(
            // - combine input-items and filters to an observable filtered result-list
            // - switch to new result-observable if items or filters are changing
            Observable.combineLatest(input, activeFilters, (currentItems, currentFilters) -> {

                // the filtered results (Observable<List<T>>) for current items and filters
                return Observable.fromIterable(currentItems).subscribeOn(Schedulers.io()).flatMapSingle(item -> {

                    // Observable for item-result from applying all filters (Optional.empty if rejected)
                    return Observable.fromIterable(currentFilters)
                                     .subscribeOn(Schedulers.io())
                                     .map(filter -> filter.apply(item))
                                     .toList()
                                     .map(resultsForItem -> Observable.combineLatest(resultsForItem,
                                         results -> Stream.of(results).allMatch(match -> (boolean) match)).defaultIfEmpty(true) // default if no filter present
                                                                       .map(match -> match ? Optional.of(item) : Optional.<T>empty()).distinctUntilChanged());

                }).toList().flatMapObservable(resultObservablesList -> {

                    // remove rejected items from result
                    return Observable.combineLatest(resultObservablesList,
                        results -> Stream.of(results).map(obj -> (Optional<T>) obj).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet()))
                                     .defaultIfEmpty(Collections.emptySet()); // default if no item is present (empty combineLatest!)

                });

            })).debounce(500, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .replay(1).refCount();

    }

    public Observable<Set<T>> getOutput() {
        return output;
    }

    public RxFieldCollection<Set<FilterModule<? super T>>, FilterModule<? super T>> getFilterModules() {
        return filterModules;
    }

    public static abstract class FilterModule<T> extends Named.BaseImpl implements Named {

        private final RxField<Boolean> enabled = RxField.withInitialValue(true);

        public FilterModule(String name, String description) {
            super(name, description);
        }

        public abstract Observable<Boolean> apply(T target);

        public Observable<Boolean> isEnabled() {
            return enabled.toObservable();
        }

        public void setEnabled(boolean enabled) {
            this.enabled.setValue(enabled);
        }

    }
}
