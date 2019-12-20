package de.panbytes.dexter.core

import de.panbytes.dexter.core.data.DataSource
import de.panbytes.dexter.core.data.DomainDataEntity
import de.panbytes.dexter.core.domain.FeatureSpace
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(3)
class DataSourceTest extends Specification {

    FeatureSpace featureSpace = Stub(FeatureSpace) { match(_) >> { args -> args[0].length == 2 } }

    def dataSource = new TestDataSource("Test", featureSpace);

    def s1 = new TestDataSource("S1", featureSpace)
    def s2 = new TestDataSource("S2", featureSpace)
    def s3 = new TestDataSource("S3", featureSpace)

    def e1 = new DomainDataEntity("E1", "", [1, 2] as double[], featureSpace)
    def e2 = new DomainDataEntity("E2", "", [3, 4] as double[], featureSpace)
    def e3 = new DomainDataEntity("E3", "", [5, 6] as double[], featureSpace)

    static class TestDataSource extends DataSource {
        protected TestDataSource(String name, FeatureSpace featureSpace) {
            super(name, "", featureSpace)
        }

        protected TestDataSource(String name, String description, FeatureSpace featureSpace) {
            super(name, description, featureSpace)
        }
    }


    def "DataSource is initially empty"() {
        expect:
            dataSource.childNodes.getValue().empty
            dataSource.childDataSources.getValue().empty
            dataSource.generatedDataEntities.getValue().empty

    }

    def "Get/Set child DataSources"() {
        when: "first source"
            dataSource.setChildDataSources([s1])
        then:
            dataSource.childDataSources.getValue() == [s1]
            dataSource.childNodes.getValue() == [s1]

        when: "second and third source"
            dataSource.setChildDataSources([s2, s3])
        then:
            dataSource.childDataSources.getValue() == [s2, s3]
            dataSource.childNodes.getValue() == [s2, s3]

        when: "all sources"
            dataSource.setChildDataSources([s1, s2, s3])
        then:
            dataSource.childDataSources.getValue() == [s1, s2, s3]
            dataSource.childNodes.getValue() == [s1, s2, s3]

    }

    def "Adding ChildDataSource (single) delegates to Setter"() {

        when:
            dataSource.addChildDataSource(s1)
        then:
            dataSource.childDataSources.getValue() == [s1]
            dataSource.childNodes.getValue() == [s1]

        when:
            dataSource.addChildDataSource(s2)
        then:
            dataSource.childDataSources.getValue() == [s1,s2]
            dataSource.childNodes.getValue() == [s1,s2]


        when:
            dataSource.addChildDataSource(s1) // duplicate
        then:
            dataSource.childDataSources.getValue() == [s1,s2]
            dataSource.childNodes.getValue() == [s1,s2]

    }

    def "Adding ChildDataSource (multiple) delegates to Setter"() {

        when:
            dataSource.addChildDataSources([s1,s2])
        then:
            dataSource.childDataSources.getValue() == [s1,s2]
            dataSource.childNodes.getValue() == [s1,s2]

        when:
            dataSource.addChildDataSources([s3])
        then:
            dataSource.childDataSources.getValue() == [s1,s2,s3]
            dataSource.childNodes.getValue() == [s1,s2,s3]


        when:
            dataSource.addChildDataSources([s1]) // duplicate
        then:
            dataSource.childDataSources.getValue() == [s1,s2,s3]
            dataSource.childNodes.getValue() == [s1,s2,s3]

    }

    @Ignore("Not yet implemented")
    def "Removing ChildDataSource"() {
        expect:
            false //TODO
    }

    def "changed dataSpources only trigger if list differs"() {
        given:
            def observed = []
            dataSource.childDataSources.toObservable().subscribe({ next -> observed << next })

        when:
            dataSource.childDataSources = [s1]
            dataSource.childDataSources = [s1]
            dataSource.childDataSources = [s1, s2]
            dataSource.childDataSources = [s1]

        then:
            observed == [[], [s1], [s1, s2], [s1]]

    }

    def "child DataSources collection is copied on read and provided as read-only"() {
        given:
            def sources = [s1]
            dataSource.setChildDataSources(sources)

        when: "copy of input"
            sources[0] = s2
        then:
            dataSource.childDataSources.getValue() == [s1]

        when: "immutable output"
            sources = dataSource.childDataSources.getValue()
            sources[0] = s3
        then:
            thrown(UnsupportedOperationException)
            dataSource.childDataSources.getValue() == [s1]

    }

    def "Ensure DataSources to be non-null"() {
        given:
            dataSource.childDataSources = [s1]

        when:
            dataSource.childDataSources = null
        then:
            thrown(NullPointerException)
            dataSource.childDataSources.getValue() == [s1]
            dataSource.childNodes.getValue() == [s1]

        when:
            dataSource.childDataSources = [s1, s2, null, s3]
        then:
            thrown(NullPointerException)
            dataSource.childDataSources.getValue() == [s1]
            dataSource.childNodes.getValue() == [s1]

    }

    def "Ensure DataSources to match FeatureSpace"() {
        given:
            def nonMatchingSource = new TestDataSource("X", "", Stub(FeatureSpace) { getFeatureCount() >>> 3 })

            dataSource.childDataSources = [s1]

        when:
            dataSource.childDataSources = [s1, s2, nonMatchingSource, s3]

        then:
            thrown(IllegalArgumentException)
            dataSource.childDataSources.getValue() == [s1]
            dataSource.childNodes.getValue() == [s1]

    }


    def "Get/Set DataEntities"() {
        when: "first source"
            dataSource.setGeneratedDataEntities([e1])
        then:
            dataSource.generatedDataEntities.getValue() == [e1]
            dataSource.childNodes.getValue() == [e1]

        when: "second and third source"
            dataSource.setGeneratedDataEntities([e2, e3])
        then:
            dataSource.generatedDataEntities.getValue() == [e2, e3]
            dataSource.childNodes.getValue() == [e2, e3]

        when: "all sources"
            dataSource.setGeneratedDataEntities([e1, e2, e3])
        then:
            dataSource.generatedDataEntities.getValue() == [e1, e2, e3]
            dataSource.childNodes.getValue() == [e1, e2, e3]

    }

    def "changed DataEntities only trigger if list differs"() {
        given:
            def observed = []
            dataSource.generatedDataEntities.toObservable().subscribe({ next -> observed << next })

        when:
            dataSource.generatedDataEntities = [e1]
            dataSource.generatedDataEntities = [e1]
            dataSource.generatedDataEntities = [e1, e2]
            dataSource.generatedDataEntities = [e1]

        then:
            observed == [[], [e1], [e1, e2], [e1]]

    }

    def "child DataEntities collection is copied on read and provided as read-only"() {
        given:
            def entities = [e1]
            dataSource.setGeneratedDataEntities(entities)

        when: "copy of input"
            entities[0] = e2
        then:
            dataSource.generatedDataEntities.getValue() == [e1]

        when: "immutable output"
            entities = dataSource.generatedDataEntities.getValue()
            entities[0] = e3
        then:
            thrown(UnsupportedOperationException)
            dataSource.generatedDataEntities.getValue() == [e1]

    }

    def "Ensure DataEntities to be non-null"() {
        given:
            dataSource.generatedDataEntities = [e1]

        when:
            dataSource.generatedDataEntities = null
        then:
            thrown(NullPointerException)
            dataSource.generatedDataEntities.getValue() == [e1]
            dataSource.childNodes.getValue() == [e1]

        when:
            dataSource.generatedDataEntities = [e1, e2, null, e3]
        then:
            thrown(NullPointerException)
            dataSource.generatedDataEntities.getValue() == [e1]
            dataSource.childNodes.getValue() == [e1]

    }

    def "Ensure DataEntities to match FeatureSpace"() {
        given:
            def nonMatchingSpace = new DomainDataEntity("X1", "", [7, 8, 9] as double[], Stub(FeatureSpace) {
                match(_) >> { args -> args[0].length == 3 }
            })

            dataSource.generatedDataEntities = [e1]

        when:
            dataSource.generatedDataEntities = [e1, e2, nonMatchingSpace, e3]
        then:
            thrown(IllegalArgumentException)
            dataSource.generatedDataEntities.getValue() == [e1]
            dataSource.childNodes.getValue() == [e1]

    }

//    def "DataEntity is set Source when added to DataSource"() {
//        expect "not yet added - entity has no source":
//            e1.generatingDataSource == Optional.empty()
//
//        when "added to source":
//            s1.generatedDataEntities = [e1]
//        then "entity knows it's source":
//            e1.generatingDataSource.get() == s1
//
//    }

    def "ChildNodes are put together from first ChildSources and then DataEntities, each section in its individual order"() {
        when:
            dataSource.generatedDataEntities = [e2]
            dataSource.childDataSources = [s2]
            dataSource.generatedDataEntities = [e2, e1]
            dataSource.childDataSources = [s1, s2]

        then:
            dataSource.childNodes.getValue() == [s1, s2, e2, e1]

    }


    def "DataEntities are aggregated for Subtree"() {
        given:
            def e0 = new DomainDataEntity("E1", "", [1, 2] as double[], featureSpace)

            e1 = new DomainDataEntity("E1", "", [1, 2] as double[], featureSpace)
            s1.generatedDataEntities = [e1]

            e2 = new DomainDataEntity("E2", "", [3, 4] as double[], featureSpace)
            s2.generatedDataEntities = [e2]
            s2.childDataSources = [s3]

            e3 = new DomainDataEntity("E3", "", [5, 6] as double[], featureSpace)
            s3.generatedDataEntities = [e3]

        expect:
            dataSource.subtreeDataEntities.blockingFirst() == []

        when:
            dataSource.childDataSources = [s1]
        then:
            dataSource.subtreeDataEntities.blockingFirst() == [e1]

        when:
            dataSource.childDataSources = [s1, s2]
        then:
            dataSource.subtreeDataEntities.blockingFirst() == [e1, e2, e3]

        when:
            dataSource.generatedDataEntities = [e0]
        then:
            dataSource.subtreeDataEntities.blockingFirst() == [e0, e1, e2, e3]

        when:
            dataSource.generatedDataEntities = []
        then:
            dataSource.subtreeDataEntities.blockingFirst() == [e1, e2, e3]

        when:
            dataSource.childDataSources = []
        then:
            dataSource.subtreeDataEntities.blockingFirst() == []

    }


}
