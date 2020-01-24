package de.panbytes.dexter.core.model;

import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldCollection;
import de.panbytes.dexter.util.Named;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FilterManager<T> {

    private final Observable<List<T>> output;
    private final RxFieldCollection<Set<FilterModule<? super T>>, FilterModule<? super T>> filterModules = RxFieldCollection.withInitialValue(
        Collections.emptySet(), HashSet::new);

    public FilterManager(Observable<? extends Iterable<T>> input) {

        // use only enabled filters
        Observable<Set<FilterModule<? super T>>> activeFilters = this.filterModules.toObservable()
                                                                                   .compose(RxJavaUtils.deepFilter(FilterModule::isEnabled, enabled -> enabled,
                                                                                       Collectors.toSet()));

        output = Observable.switchOnNext(
            // - combine input-items and filters to an observable filtered result-list
            // - switch to new result-observable if items or filters are changing
            Observable.combineLatest(input, activeFilters, (currentItems, currentFilters) -> {

                // the filtered results (Observable<List<T>>) for current items and filters
                return Observable.fromIterable(currentItems).flatMapSingle(item -> {

                    // Observable for item-result from applying all filters (Optional.empty if rejected)
                    return Observable.fromIterable(currentFilters)
                                     .map(filter -> filter.apply(item))
                                     .toList()
                                     .map(resultsForItem -> Observable.combineLatest(resultsForItem,
                                         results -> Stream.of(results).allMatch(match -> (boolean) match)).defaultIfEmpty(true) // default if no filter present
                                                                       .map(match -> match ? Optional.of(item) : Optional.<T>empty()).distinctUntilChanged());

                }).toList().flatMapObservable(resultObservablesList -> {

                    // remove rejected items from result
                    return Observable.combineLatest(resultObservablesList,
                        results -> Stream.of(results).map(obj -> (Optional<T>) obj).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()))
                                     .defaultIfEmpty(Collections.emptyList()); // default if no item is present (empty combineLatest!)

                });

            })).distinctUntilChanged().replay(1).refCount();

    }

    public Observable<List<T>> getOutput() {
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
