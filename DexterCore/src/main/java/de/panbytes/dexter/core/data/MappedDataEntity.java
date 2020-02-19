package de.panbytes.dexter.core.data;

import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;

import io.reactivex.Observable;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public final class MappedDataEntity extends DataEntity {

    private final DataEntity mappedDataEntity;

    /**
     * Create a new {@code DataEntity} with given name, description and state.
     * <p>
     * This Entity's EnabledState is bound bidirectional to the underlying mapped DataEntity, i.e. any changes to the mapped Entity are
     * reflected to this and vice versa.
     *
     * @param name             the entity's name.
     * @param description      the entity's description.
     * @param coordinates      the entity's coordinates
     * @param featureSpace     the entity's featureSpace
     * @param mappedDataEntity the DataEntity being mapped by this entity.
     * @throws NullPointerException if name, description, coordinates or mappedDataEntity are null.
     * @see DataEntity#DataEntity(String, String, double[], FeatureSpace)
     */
    public MappedDataEntity(String name, String description, double[] coordinates, FeatureSpace featureSpace, DataEntity mappedDataEntity) {
        super(name, description, coordinates, featureSpace);

        this.mappedDataEntity = checkNotNull(mappedDataEntity);

        // bidirectional binding of EnabledState to mapped Entity
        this.mappedDataEntity.getEnabledState().toObservable().subscribe(state -> this.setEnabled(state == EnabledState.ACTIVE));
        this.getEnabledState().toObservable().subscribe(state -> this.mappedDataEntity.setEnabled(state == EnabledState.ACTIVE));
    }

    public MappedDataEntity(double[] coordinates, FeatureSpace featureSpace, DataEntity mappedDataEntity) {
        this(mappedDataEntity.getName().getValue(), mappedDataEntity.getDescription().getValue(), coordinates, featureSpace,
             mappedDataEntity);
    }

    /**
     * Get the entity's class label, which is defined by the mapped entity (delegate).
     *
     * @return the (mapped entity's) class label, if available.
     */
    @Override
    public RxFieldReadOnly<Optional<ClassLabel>> getClassLabel() {
        return mappedDataEntity.getClassLabel();
    }

    /**
     * Set the entity's class label.
     *
     * @param classLabel the new label or null for no label.
     */
    @Override
    public DataEntity setClassLabel(ClassLabel classLabel) {
        mappedDataEntity.setClassLabel(classLabel);
        return this;
    }

    @Override
    public Observable<Boolean> isInspected() {
        return mappedDataEntity.isInspected();
    }

    @Override
    public void setInspected(boolean inspected) {
        mappedDataEntity.setInspected(inspected);
    }

    @Override
    public DataSource getGeneratingDataSource() {
        return getMappedDataEntity().getGeneratingDataSource();
    }

    /**
     * Set new coordinates for the entity.
     *
     * @param coordinates the new coordinates.
     * @throws NullPointerException if coordinates are null.
     */
    @Override
    public DataEntity setCoordinates(double[] coordinates) {
        return super.setCoordinates(coordinates); // override to make the method public.
    }

    public DataEntity getMappedDataEntity() {
        return this.mappedDataEntity;
    }
}
