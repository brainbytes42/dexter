package de.panbytes.dexter.core.domain;

import com.google.common.base.Preconditions;
import de.panbytes.dexter.util.Named;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class FeatureSpace extends Named.BaseImpl implements Named {

    private final List<Feature> features;

    public FeatureSpace(String name, String description, List<Feature> features) {
        super(name, description);
        checkNotNull(features).forEach(Preconditions::checkNotNull);
        checkArgument(!features.isEmpty(), "Features may not be empty, FeatureSpace needs to be at least one-dimensional.");
        this.features = Collections.unmodifiableList(new ArrayList<>(features));
    }

    public int getFeatureCount() {
        return features.size();
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public boolean match(double[] coordinates) {
        return checkNotNull(coordinates).length == getFeatureCount();
    }

    public static class Feature extends Named.BaseImpl implements Named {
        public Feature(String name, String description) {
            super(name, description);
        }
    }
}
