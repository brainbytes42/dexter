package de.panbytes.dexter.core.data;

import static com.google.common.base.Preconditions.checkNotNull;

import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Observable;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DomainDataEntity extends DataEntity {

    private static final Logger log = LoggerFactory.getLogger(DomainDataEntity.class);
    protected final ClassLabel initialClassLabel;

    private final RxField<Optional<ClassLabel>> classLabel = RxField.initiallyEmpty();
    private final RxField<Boolean> inspected = RxField.withInitialValue(false);
    private DataSource generatingDataSource = null;

    /**
     * Create a new {@code DataEntity} with given name, description and state.
     *
     * @param name the entity's name.
     * @param description the entity's description.
     * @param coordinates the entity's coordinates
     * @param featureSpace the entity's feature space
     * @throws NullPointerException if name, description, coordinates, featureSpace or generatingDataSource are null.
     * @see DataEntity#DataEntity(String, String, double[], FeatureSpace)
     */
    public DomainDataEntity(String name, String description, double[] coordinates, FeatureSpace featureSpace, DataSource generatingDataSource,
        ClassLabel initialClassLabel) {
        super(name, description, coordinates, featureSpace);
        this.generatingDataSource = checkNotNull(generatingDataSource);
        this.initialClassLabel = initialClassLabel;
        this.classLabel.setValue(Optional.ofNullable(initialClassLabel));
    }

    /**
     * Get the {@link DataSource} that created this entity.
     *
     * @return the generating DataSource.
     */
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
        final Optional<ClassLabel> newClassLabel = Optional.ofNullable(classLabel);
        log.info("Set ClassLabel for '{}' ({}) to '{}' (was '{}').", getName().getValue(), getGeneratingDataSource().getName().getValue(),
                 newClassLabel.map(ClassLabel::getLabel).orElse("--"), this.classLabel.getValue().map(ClassLabel::getLabel).orElse("--"));
        this.classLabel.setValue(newClassLabel);
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

    @Override
    public Optional<ClassLabel> getInitialClassLabel() {
        return Optional.ofNullable(initialClassLabel);
    }
}
