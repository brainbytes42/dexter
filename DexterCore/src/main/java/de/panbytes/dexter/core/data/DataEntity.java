package de.panbytes.dexter.core.data;

import com.google.common.base.Preconditions;
import de.panbytes.dexter.core.ClassLabel;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;

import java.util.Arrays;
import java.util.Optional;

/**
 * A {@code DataEntity} represents one point (potentially out of a set of data points), which is described by double
 * coordinates in some (maybe high-dimensional) space. It may contain some sort of class-label.
 *
 * @author Fabian Krippendorff
 */
public abstract class DataEntity extends DataNode {

    private final RxField<double[]> coordinates;

    /**
     * Create a new {@code DataEntity} with given name and description.
     *
     * @param name         the entity's name.
     * @param description  the entity's description.
     * @param coordinates  the entity's coordinates
     * @param featureSpace the entity's feature space
     * @throws NullPointerException if name, description or coordinates are null.
     * @see DataNode#DataNode(String, String, FeatureSpace)
     */
    protected DataEntity(String name, String description, double[] coordinates, FeatureSpace featureSpace) {
        super(name, description, featureSpace);

        Preconditions.checkArgument(featureSpace.match(coordinates),"Coordinates to be set are " + coordinates.length + "-dimensional, but FeatureSpace requests " + getFeatureSpace().getFeatureCount() + " dimensions!");

        this.coordinates = RxField.withInitialValue(coordinates).distinguishingValues(Arrays::equals).wrappingReturnedValues(double[]::clone);
    }

    /**
     * Create a new {@code DataEntity} with given name, description and state.
     *
     * @param name         the entity's name.
     * @param description  the entity's description.
     * @param coordinates  the entity's coordinates
     * @param featureSpace the entity's feature space
     * @param status       the entity's status
     * @throws NullPointerException if name, description or coordinates are null.
     * @see DataNode#DataNode(String, String, FeatureSpace)
     */
    protected DataEntity(String name, String description, double[] coordinates, FeatureSpace featureSpace, Status status) {
        super(name, description, featureSpace, status);

        Preconditions.checkArgument(featureSpace.match(coordinates),"Coordinates to be set are " + coordinates.length + "-dimensional, but FeatureSpace requests " + getFeatureSpace().getFeatureCount() + " dimensions!");

        this.coordinates = RxField.withInitialValue(coordinates).distinguishingValues(Arrays::equals).wrappingReturnedValues(double[]::clone);
    }

    /**
     * Get the entity's coordinates.
     *
     * @return a RxField containing the coordinates.
     */
    public final RxFieldReadOnly<double[]> getCoordinates() {
        return this.coordinates.toReadOnlyView();
    }

    /**
     * Set new coordinates for the entity.
     *
     * @param coordinates the new coordinates.
     * @throws NullPointerException     if coordinates are null.
     * @throws IllegalArgumentException if coordinates aren't matching FeatureSpace.
     */
    protected DataEntity setCoordinates(double[] coordinates) {
        if (getFeatureSpace().match(coordinates)) {
            this.coordinates.setValue(coordinates.clone());
        } else {
            throw new IllegalArgumentException(
                    "Coordinates to be set are " + coordinates.length + "-dimensional, but FeatureSpace requests " + getFeatureSpace().getFeatureCount() + " dimensions!");
        }

        return this;
    }

    /**
     * Get the entity's class label.
     *
     * @return the class label, if available.
     */
    public abstract RxFieldReadOnly<Optional<ClassLabel>> getClassLabel();

    /**
     * Set the entity's class label.
     *
     * @param classLabel the new label or null for no label.
     */
    public abstract DataEntity setClassLabel(ClassLabel classLabel);


    @Override
    public String toString() {
        return super.toString()+"[label="+getClassLabel().getValue()+"]";
    }
}
