package de.panbytes.dexter.core.model;

import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldCollection;
import de.panbytes.dexter.util.Named;
import io.reactivex.Observable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FilterManager<T> {

    private final Observable<List<T>> output;
    private final RxFieldCollection<Set<FilterModule<? super T>>, FilterModule<? super T>> filterModules = RxFieldCollection.withInitialValue(
        Collections.emptySet(), Collections::unmodifiableSet);

    public FilterManager(Observable<? extends Iterable<T>> input) {

        Observable<Set<FilterModule<T>>> activeFilters;
        activeFilters = this.filterModules.toObservable()
                                          .map(filters -> filters.stream()
                                                                 .map(filterModule -> filterModule.isEnabled()
                                                                                                  .map(enabled -> enabled ? Optional.of(filterModule)
                                                                                                      : Optional.<FilterModule<T>>empty()))
                                                                 .collect(Collectors.toSet()))
                                          .switchMap(mappedFilters -> {
                                              return Observable.combineLatest(mappedFilters, arr -> Stream.of(arr)
                                                                                                          .map(obj -> (Optional<FilterModule<T>>) obj)
                                                                                                          .filter(Optional::isPresent)
                                                                                                          .map(Optional::get)
                                                                                                          .collect(Collectors.toSet()))
                                                               .defaultIfEmpty(Collections.emptySet());
                                          });

        output = Observable.switchOnNext(
            // - combine input-items and filters to an observable filtered result-list
            // - switch to new result-observable if items or filters are changing
            Observable.combineLatest(input, activeFilters, (currentItems, currentFilters) -> {

                // the filtered results (Observable<List<T>>) for current items and filters
                return Observable.fromIterable(currentItems).flatMapSingle(item -> {

                    // Observable for thing-result from applying all filters (Optional.empty if rejected)
                    return Observable.fromIterable(currentFilters)
                                     //                                     .map(filter -> Observable.combineLatest(filter.apply(thing), filter.enabled,
                                     //                                         (filterResult, filterEnabled) -> filterResult || !filterEnabled))
                                     .map(filter -> filter.apply(item))
                                     .toList()
                                     .map(resultsForThing -> Observable.combineLatest(resultsForThing,
                                         results -> Stream.of(results).allMatch(match -> (boolean) match)).defaultIfEmpty(true) // default if no filter present
                                                                       .map(match -> match ? Optional.of(item) : Optional.<T>empty()).distinctUntilChanged());

                }).toList().flatMapObservable(resultObservablesList -> {

                    // remove rejected things from result
                    return Observable.combineLatest(resultObservablesList,
                        results -> Stream.of(results).map(obj -> (Optional<T>) obj).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()))
                                     .defaultIfEmpty(Collections.emptyList()); // default if no thing is present (empty combineLatest!)

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
