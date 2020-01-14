package de.panbytes.dexter.core;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * @author Fabian Krippendorff
 */
public class ClassLabel implements Comparable<ClassLabel> {

    private static volatile Map<String, ClassLabel> labelMap = new HashMap<>();
    private final String label;

    private ClassLabel(String label) {
        this.label = label;
    }

    public static ClassLabel labelFor(String label) {
        Preconditions.checkNotNull(label, "Label must not be null.");
        ClassLabel classLabel = labelMap.get(label);
        if (classLabel == null) {
            synchronized (ClassLabel.class) {
                if (labelMap.get(label) == null) {
                    classLabel = new ClassLabel(label);
                    labelMap.put(label, classLabel);
                } else {
                    // in case a second thread got into here...
                    classLabel = labelMap.get(label);
                }
            }
        }
        return classLabel;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return getLabel();
    }

    @Override
    public int compareTo(@Nonnull ClassLabel other) {
        return this.getLabel().compareToIgnoreCase(other.getLabel());
    }

}
