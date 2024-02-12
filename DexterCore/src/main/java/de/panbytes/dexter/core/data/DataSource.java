package de.panbytes.dexter.core.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Observable;
import io.reactivex.functions.Function;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*

TODO:
- Exception Recovery
- AutoCloseable (?)
- Trigger reading data

 */

public class DataSource extends DataNode {

    private static final Logger log = LoggerFactory.getLogger(DataSource.class);

    private final RxField<Set<DataSource>> childDataSources = RxField.withInitialValue(Collections.emptySet());
    private final RxField<Set<DomainDataEntity>> generatedDataEntities = RxField.withInitialValue(Collections.emptySet());
    private final Observable<Set<DomainDataEntity>> subtreeDataEntities;

    /**
     * Create a new {@code DataSource} with the given name.
     *
     * @param name the DataSource's name.
     * @throws NullPointerException if the name is null.
     * @see DataNode#DataNode(String, String, FeatureSpace)
     */
    public DataSource(String name, String description, FeatureSpace featureSpace) {
        this(name, description, featureSpace, Status.ACTIVE);
    }

    /**
     * Create a new {@code DataSource} with the given name.
     *
     * @param name the DataSource's name.
     * @param status sthe initial status.
     * @throws NullPointerException if the name is null.
     * @see DataNode#DataNode(String, String, FeatureSpace, Status)
     */
    public DataSource(String name, String description, FeatureSpace featureSpace, Status status) {
        super(name, description, featureSpace, status);

        // log changes of generated entities or child-DataSources
        childDataSources.toObservable().observeOn(Schedulers.io())
                        .subscribe(dataSources -> log.debug("DataSource '{}' has {} Child-DataSources.", getName().getValue(), dataSources.size()));
        generatedDataEntities.toObservable().observeOn(Schedulers.io())
                             .subscribe(domainDataEntities -> log.debug("DataSource '{}' directly provides {} Entities.", getName().getValue(), domainDataEntities.size()));

        // data binding
        bindChildNodesToChildSourcesAndDataEntities();
        this.subtreeDataEntities = bindSubtreeDataEntities();

        // log changes of subtree
        subtreeDataEntities.observeOn(Schedulers.io()).subscribe(domainDataEntities -> log.debug("DataSource '{}' provides {} Entities for whole subtree.", getName().getValue(), domainDataEntities.size()));
    }

    private Observable<Set<DomainDataEntity>> bindSubtreeDataEntities() {

        Observable<Set<DomainDataEntity>> childSubtreeEntities = this.childDataSources.toObservable().switchMap(childSources -> {

            Set<Observable<Set<DomainDataEntity>>> childObservables = childSources
                .stream()
                .map(DataSource::getSubtreeDataEntities)
                .collect(Collectors.toSet());

            Function<Object[], Set<DomainDataEntity>> mergeFunction = arrayOfSets ->
                    Arrays.stream(arrayOfSets).map(obj->(Set<DomainDataEntity>)obj).flatMap(Collection::stream).collect(Collectors.toSet());

            return childSources.isEmpty() ? Observable.just(Collections.emptySet()) : Observable.combineLatest(childObservables, mergeFunction);

        });

        return Observable.combineLatest(this.generatedDataEntities.toObservable(), childSubtreeEntities, (generatedEntities, childEntities) -> Stream
            .concat(generatedEntities.stream(), childEntities.stream())
            .collect(Collectors.toSet()))
                         .replay(1).refCount();
    }

    public final RxFieldReadOnly<Set<DataSource>> getChildDataSources() {
        return this.childDataSources.toReadOnlyView();
    }

    protected final void setChildDataSources(Collection<DataSource> dataSources) {
        checkNotNull(dataSources).forEach(Preconditions::checkNotNull);
        dataSources.forEach(dataSource -> checkArgument(getFeatureSpace().equals(dataSource.getFeatureSpace()),
                                                        "FeatureSpace doesn't match for added DataSource ["
                                                        + dataSource
                                                        + "]: expected ["
                                                        + getFeatureSpace()
                                                        + "], but was: ["
                                                        + dataSource.getFeatureSpace()
                                                        + "]"));

        this.childDataSources.setValue(Collections.unmodifiableSet(new HashSet<>(dataSources)));
    }

    public void addChildDataSource(DataSource dataSource) {
        ArrayList<DataSource> newDataSources = new ArrayList<>(this.childDataSources.getValue());
        if (!newDataSources.contains(dataSource)) {
            newDataSources.add(dataSource);
        }
        setChildDataSources(newDataSources);
    }

    public void addChildDataSources(Collection<DataSource> dataSources) {
        ArrayList<DataSource> newDataSources = new ArrayList<>(this.childDataSources.getValue());
        ArrayList<DataSource> addNoDuplicates = new ArrayList<>(dataSources);
        addNoDuplicates.removeIf(newDataSources::contains);
        newDataSources.addAll(addNoDuplicates);
        setChildDataSources(newDataSources);
    }

    public void removeChildDataSource(DataSource dataSource) {
        ArrayList<DataSource> newDataSources = new ArrayList<>(this.childDataSources.getValue());
        newDataSources.remove(dataSource);
        setChildDataSources(newDataSources);
    }

    public final RxFieldReadOnly<Set<DomainDataEntity>> getGeneratedDataEntities() {
        return this.generatedDataEntities.toReadOnlyView();
    }

    protected final void setGeneratedDataEntities(Set<? extends DomainDataEntity> generatedDataEntities) {
        log.trace("Checking {} DataEntities generated by DataSource: {}", generatedDataEntities.size(), this);
        checkNotNull(generatedDataEntities).forEach(Preconditions::checkNotNull);
        generatedDataEntities.forEach(dataEntity -> checkArgument(getFeatureSpace().equals(dataEntity.getFeatureSpace()),
                                                                  "FeatureSpace doesn't match for added DataEntity: " + dataEntity));
        generatedDataEntities.forEach(
            dataEntity -> checkArgument(this.equals(dataEntity.getGeneratingDataSource()), "DataEntity wasn't created by this DataSource: " + dataEntity));

        log.debug("Setting {} DataEntities generated by DataSource: {}", generatedDataEntities.size(), this);
        this.generatedDataEntities.setValue(Collections.unmodifiableSet(new HashSet<>(generatedDataEntities)));
    }

    public final Observable<Set<DomainDataEntity>> getSubtreeDataEntities() {
        return this.subtreeDataEntities.distinctUntilChanged().hide();
    }

    /**
     * Wires {@link #childDataSources} and {@link #generatedDataEntities} together to keep the children maintained by {@link DataNode} updated.
     * <p>
     * The updated List always contains first any {@link #childDataSources} and after that any {@link #generatedDataEntities} (only immediate, not by child
     * nodes).
     */
    private void bindChildNodesToChildSourcesAndDataEntities() {
        Observable
            .combineLatest(childDataSources.toObservable(), generatedDataEntities.toObservable(),
                           (childSources, dataEntities) -> Stream.concat(childSources.stream(), dataEntities.stream()).collect(Collectors.toList()))
            .subscribe(this::setChildNodes);
    }
}
