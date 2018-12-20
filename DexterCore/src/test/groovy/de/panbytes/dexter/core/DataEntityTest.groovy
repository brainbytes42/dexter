package de.panbytes.dexter.core

import de.panbytes.dexter.core.data.DataEntity
import de.panbytes.dexter.core.domain.FeatureSpace
import org.spockframework.mock.CannotCreateMockException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(3)
class DataEntityTest extends Specification {

    @Shared
    double[] coordinates = [1, 2, 3]
    @Shared
    def featureSpace = Stub(FeatureSpace) {
        match(_) >> { args -> args[0].length == 3 }
    }

    DataEntity dataEntity = Spy(constructorArgs: ["Name", "Description", coordinates, featureSpace])


    def "coordinates are set by constructor"() {
        expect:
            dataEntity.coordinates.getValue() == coordinates
    }

    def "constructor ensures non-null arguments"() {
        when:
            DataEntity d = Mock(constructorArgs: [name, descr, coord, features])

        then:
            def e = thrown(CannotCreateMockException)
            e.cause instanceof NullPointerException

        where:
            name   | descr         | coord       | features
            null   | "Description" | coordinates | featureSpace
            "Name" | null          | coordinates | featureSpace
            "Name" | "Description" | null        | featureSpace
            "Name" | "Description" | coordinates | null

    }

    def "constructor ensures coordinates matching feature space"() {
        when:
            DataEntity d = Mock(constructorArgs: ["Name", "Description", [1, 2] as double[], featureSpace]) // only 2 features - 3 needed.

        then:
            def ex = thrown(CannotCreateMockException)
            ex.cause instanceof IllegalArgumentException

    }

    def "changes to coordinates are reflected only for changed coordinates"() {
        given:
            def observed = []
            dataEntity.coordinates.toObservable().subscribe({ next -> observed << next })

        when:
            dataEntity.coordinates = [1, 2, 3] as double[]
            dataEntity.coordinates = [4, 5, 6] as double[]
            dataEntity.coordinates = [4, 5, 6] as double[]
            dataEntity.coordinates = [1, 2, 3] as double[]

        then:
            observed == [
                    [1, 2, 3] as double[],
                    [4, 5, 6] as double[],
                    [1, 2, 3] as double[]
            ]

    }

    def "coordinates ensured to be not null"() {
        when:
            dataEntity.coordinates = null

        then:
            thrown(NullPointerException)

    }

    def "coordinates matching feature space"() {
        when:
            dataEntity.coordinates = [1, 2] as double[] // only 2 features - 3 needed.

        then:
            thrown(IllegalArgumentException)

    }

    def "coordinates array is copied on get and set"() {
        given:
            double[] coordinates = [1, 2, 3] as double[]
            dataEntity.coordinates = coordinates

        when: "change input"
            coordinates[0] = 4

        then:
            dataEntity.coordinates.getValue() == [1, 2, 3] as double[]

        when: "change output"
            coordinates = dataEntity.coordinates.getValue()
            coordinates[1] = 5

        then:
            dataEntity.coordinates.getValue() == [1, 2, 3] as double[]

    }

    def "changes to coordinates are refused when new coordinates aren't matching featureSpace"() {
        when:
            dataEntity.coordinates = [1] as double[]

        then:
            thrown(IllegalArgumentException)

    }

    def "coordinates have fluent setter"() {
        when:
            def result = dataEntity.setCoordinates(coordinates)

        then:
            result != null
            result instanceof DataEntity

    }

}
