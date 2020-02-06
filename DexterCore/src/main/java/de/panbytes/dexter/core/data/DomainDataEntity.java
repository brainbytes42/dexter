package de.panbytes.dexter.core.data;

import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public class DomainDataEntity extends DataEntity {

    private static final Logger log = LoggerFactory.getLogger(DomainDataEntity.class);

    private final RxField<Optional<ClassLabel>> classLabel = RxField.initiallyEmpty();
    private final RxField<Boolean> inspected = RxField.withInitialValue(false);
    private DataSource generatingDataSource = null;

    /**
     * Create a new {@code DataEntity} with given name, description and state.
     *
     * @param name         the entity's name.
     * @param description  the entity's description.
     * @param coordinates  the entity's coordinates
     * @param featureSpace the entity's feature space
     * @throws NullPointerException if name, description, coordinates, featureSpace or generatingDataSource are null.
     * @see DataEntity#DataEntity(String, String, double[], FeatureSpace)
     */
    public DomainDataEntity(String name, String description, double[] coordinates, FeatureSpace featureSpace, DataSource generatingDataSource) {
        super(name, description, coordinates, featureSpace);
        this.generatingDataSource = checkNotNull(generatingDataSource);
    }

    /**
     * Get the {@link DataSource} that created this entity.
     *
     * @return the generating DataSource.
     */
//    public Optional<DataSource> getGeneratingDataSource() {
//        return Optional.ofNullable(this.generatingDataSource);
//    }
    public final DataSource getGeneratingDataSource() {
        return this.generatingDataSource;
    }

//    final synchronized void setGeneratingDataSource(DataSource generatingDataSource) {
//        this.generatingDataSource = generatingDataSource;

        // TODO is this necessary?
//        if (this.generatingDataSource == null) {
//            this.generatingDataSource = checkNotNull(generatingDataSource);
//        } else if (this.generatingDataSource == generatingDataSource) {
//            log.debug("Ignored attempt to set same generating DataSource again!");
//        } else {
//            throw new IllegalStateException("Generating DataSource has already been set!");
//        }
//    }

    /**
     * Get the entity's class label.
     *
     * @return the class label, if available.
     */
    @Override
    public final RxFieldReadOnly<Optional<ClassLabel>> getClassLabel() {
        return this.classLabel.toReadOnlyView();
    }

    /**
     * Set the entity's class label.
     *
     * @param classLabel the new label or null for no label.
     */
    @Override
    public final DataEntity setClassLabel(ClassLabel classLabel) {
        this.classLabel.setValue(Optional.ofNullable(classLabel));
        return this;
    }

    @Override
    public final Observable<Boolean> isInspected() {
        return inspected.toObservable();
    }

    @Override
    public final void setInspected(boolean inspected) {
        this.inspected.setValue(inspected);
    }

}
