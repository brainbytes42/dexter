package de.panbytes.dexter.core.domain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class FeatureSpace {

    private final String name;
    private final List<Feature> features;

    public FeatureSpace(String name, List<Feature> features) {
        this.name = checkNotNull(name);
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

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof FeatureSpace) {
            FeatureSpace other = (FeatureSpace) o;
            return name.equals(other.name) && features.equals(other.features);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, features);
    }

    @Override
    public String toString() {
        return "FeatureSpace '"+name+"': "+features;
    }

    public static class Feature {

        private final String name;

        public Feature(String name) {
            this.name = checkNotNull(name);
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Feature && name.equals(((Feature) o).name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "<" + name + ">";
        }
    }
}
