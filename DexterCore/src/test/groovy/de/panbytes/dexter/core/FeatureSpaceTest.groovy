package de.panbytes.dexter.core

import de.panbytes.dexter.core.domain.FeatureSpace
import spock.lang.Shared
import spock.lang.Specification

class FeatureSpaceTest extends Specification {


    @Shared
    def featureA = new FeatureSpace.Feature("Feature A", "")
    @Shared
    def featureB = new FeatureSpace.Feature("Feature B", "")
    @Shared
    def featureC = new FeatureSpace.Feature("Feature C", "")

    def featureList = [featureA, featureB, featureC]

    def featureSpace = new FeatureSpace("Feature Space", "My Features", featureList)

    def "Constructor sets all values"() {
        expect:
            featureSpace.features == featureList
            featureSpace.featureCount == 3
            featureSpace.name.getValue() == "Feature Space"
            featureSpace.description.getValue() == "My Features"
    }

    def "List of Features is copied and immutable"() {
        when:
            featureList.remove(0)
        then:
            featureList == [featureB, featureC]
            featureSpace.features == [featureA, featureB, featureC]
            featureSpace.featureCount == 3

        when:
            featureSpace.features.remove(0)
        then:
            thrown(UnsupportedOperationException)

    }

    def "List of Features is non-null and contains no null"() {
        when:
            new FeatureSpace("Name", "Description", list)

        then:
            thrown(NullPointerException)

        where:
            list                       | _
            null                       | _
            [featureA, null, featureC] | _

    }

    def "matching coordinates have similar length as features-list"() {
        expect:
            featureSpace.match(coord) == match

        where:
            coord                    | match
            [1, 2, 3] as double[]    | true
            [] as double[]           | false
            [1, 2] as double[]       | false
            [1, 2, 3, 4] as double[] | false

    }

    def "matching coordinates are non-null"() {
        when:
            featureSpace.match(null)

        then:
            thrown(NullPointerException)

    }

    def "feature space is at least one-dimensional"() {
        when:
            new FeatureSpace("Feature Space", "My Features", [])
        then:
            thrown(IllegalArgumentException)

        expect:
            new FeatureSpace("Feature Space", "My Features", [featureA])

    }

}
