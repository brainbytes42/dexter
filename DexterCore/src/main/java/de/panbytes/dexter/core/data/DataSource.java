package de.panbytes.dexter.core.data;

import com.google.common.base.Preconditions;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Observable;
import io.reactivex.functions.Function;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/*

TODO:
- Exception Recovery
- AutoCloseable (?)
- Trigger reading data

 */

public class DataSource extends DataNode {

    private final RxField<List<DataSource>> childDataSources = RxField.withInitialValue(Collections.emptyList());
    private final RxField<List<DomainDataEntity>> generatedDataEntities = RxField.withInitialValue(Collections.emptyList());
    private final Observable<List<DomainDataEntity>> subtreeDataEntities;

    /**
     * Create a new {@code DataSource} with the given name.
     *
     * @param name the DataSource's name.
     * @throws NullPointerException if the name is null.
     * @see DataNode#DataNode(String, String, FeatureSpace)
     */
    public DataSource(String name, String description, FeatureSpace featureSpace) {
        super(name, description, featureSpace);

        bindChildNodesToChildSourcesAndDataEntities();

        this.subtreeDataEntities = bindSubtreeDataEntities();
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

        bindChildNodesToChildSourcesAndDataEntities();

        this.subtreeDataEntities = bindSubtreeDataEntities();
    }

    private Observable<List<DomainDataEntity>> bindSubtreeDataEntities() {

        Observable<List<DomainDataEntity>> childSubtreeEntities = this.childDataSources.toObservable().switchMap(childSources -> {

            List<Observable<List<DomainDataEntity>>> childObservables = childSources.stream()
                                                                                    .map(DataSource::getSubtreeDataEntities)
                                                                                    .collect(Collectors.toList());

            Function<Object[], List<DomainDataEntity>> listMerger = arrayOfLists -> Arrays.stream(arrayOfLists)
                                                                                          .map(List.class::cast)
                                                                                          .flatMap(List::stream)
                                                                                          .map(DomainDataEntity.class::cast)
                                                                                          .collect(Collectors.toList());

            return childSources.isEmpty()
                   ? Observable.just(Collections.emptyList())
                   : Observable.combineLatest(childObservables, listMerger);

        });

        return Observable.combineLatest(this.generatedDataEntities.toObservable(), childSubtreeEntities,
                                        (generatedEntities, childEntities) -> Stream.concat(generatedEntities.stream(),
                                                                                            childEntities.stream())
                                                                                    .collect(Collectors.toList()));
    }

    public final RxFieldReadOnly<List<DataSource>> getChildDataSources() {return this.childDataSources.toReadOnlyView();}

    protected final void setChildDataSources(Collection<DataSource> dataSources) {
        checkNotNull(dataSources).forEach(Preconditions::checkNotNull);
        dataSources.forEach(dataSource -> checkArgument(getFeatureSpace().equals(dataSource.getFeatureSpace()),
                                                        "FeatureSpace doesn't match for added DataSource: " + dataSource));

        this.childDataSources.setValue(Collections.unmodifiableList(new ArrayList<>(dataSources)));
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

    public final RxFieldReadOnly<List<DomainDataEntity>> getGeneratedDataEntities() {
        return this.generatedDataEntities.toReadOnlyView();
    }

    protected final void setGeneratedDataEntities(List<DomainDataEntity> generatedDataEntities) {
        checkNotNull(generatedDataEntities).forEach(Preconditions::checkNotNull);
        generatedDataEntities.forEach(dataEntity -> checkArgument(getFeatureSpace().equals(dataEntity.getFeatureSpace()),
                                                                  "FeatureSpace doesn't match for added DataEntity: " + dataEntity));
         generatedDataEntities.forEach(dataEntity -> checkArgument(this.equals(dataEntity.getGeneratingDataSource()),
                                                                  "DataEntity wasn't created by this DataSource: " + dataEntity));

        this.generatedDataEntities.setValue(Collections.unmodifiableList(new ArrayList<>(generatedDataEntities)));
    }

    public final Observable<List<DomainDataEntity>> getSubtreeDataEntities() {
        return this.subtreeDataEntities.distinctUntilChanged().hide();
    }

    /**
     * Wires {@link #childDataSources} and {@link #generatedDataEntities} together to keep the children maintained by {@link DataNode} updated.
     * <p>
     * The updated List always contains first any {@link #childDataSources} and after that any {@link #generatedDataEntities} (only immediate, not by child nodes).
     */
    private void bindChildNodesToChildSourcesAndDataEntities() {
        Observable.combineLatest(childDataSources.toObservable(), generatedDataEntities.toObservable(),
                                 (childSources, dataEntities) -> Stream.concat(childSources.stream(), dataEntities.stream())
                                                                       .collect(Collectors.toList())).subscribe(this::setChildNodes);
    }
}
