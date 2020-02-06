package de.panbytes.dexter.core.data


import de.panbytes.dexter.core.domain.FeatureSpace
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(3)
class MappedDataEntityTest extends Specification {

    @Shared
    double[] coordinates = [1, 2]
    @Shared
    def featureSpace = Stub(FeatureSpace) { match(_) >> { args -> args[0].length == 2 } }
    @Shared
    def domainDataEntity = new DomainDataEntity("Domain", "DomainDescription", [1, 2, 3] as double[], Stub(FeatureSpace) {
        match(_) >> { args -> args[0].length == 3 }
    })


    def mappedDataEntity = new MappedDataEntity("Mapped", "MappedDescription", coordinates, featureSpace, domainDataEntity)


    def "Constructor ensures non-null parameters"() {
        when:
            new MappedDataEntity(name, description, coords, space, mapped)

        then:
            thrown(NullPointerException)

        where:
            name   | description   | coords      | space        | mapped
            null   | "Description" | coordinates | featureSpace | domainDataEntity
            "Name" | null          | coordinates | featureSpace | domainDataEntity
            "Name" | "Description" | null        | featureSpace | domainDataEntity
            "Name" | "Description" | coordinates | null         | domainDataEntity
            "Name" | "Description" | coordinates | featureSpace | null

    }

    def "mapped data entity is set"() {
        expect:
            mappedDataEntity.mappedDataEntity == domainDataEntity

    }

    def "EnabledState is bound bidirectional to mapped Entity"() {

        expect: "initially, both are active"
            domainDataEntity.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            mappedDataEntity.enabledState.getValue() == DataNode.EnabledState.ACTIVE

        when: "mapped gets changed"
            mappedDataEntity.enabled = false

        then:
            domainDataEntity.enabledState.getValue() == DataNode.EnabledState.DISABLED
            mappedDataEntity.enabledState.getValue() == DataNode.EnabledState.DISABLED

        when: "domain gets changed"
            domainDataEntity.enabled = true

        then:
            domainDataEntity.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            mappedDataEntity.enabledState.getValue() == DataNode.EnabledState.ACTIVE

    }

    def "Binding of EnabledState works for initially disabled"() {
        given: "disabled domain entity"
            DataEntity domain = new DomainDataEntity("Domain", "DomainDescription", [1, 2, 3] as double[], Stub(FeatureSpace) {
                match(_) >> { args -> args[0].length == 3 }
            }).setEnabled(false)

        when:
            def mapped = new MappedDataEntity("Mapped", "MappedDescription", coordinates, featureSpace, domain)

        then:
            mapped.enabledState.getValue() == DataNode.EnabledState.DISABLED

    }

    def "coordinates may be changed"(){
        expect:
            mappedDataEntity.setCoordinates([3,4] as double[])
    }

    def "getting and setting classLabel is delegated to mapped entity"() {
        when:
            mappedDataEntity.setClassLabel(ClassLabel.labelFor("Foo"))

        then:
            domainDataEntity.classLabel.getValue().get() == ClassLabel.labelFor("Foo")
            mappedDataEntity.classLabel.getValue().get() == ClassLabel.labelFor("Foo")

    }

    def "fluent setters"() {
        expect:
            mappedDataEntity.setCoordinates([2,3] as double[]) == mappedDataEntity
            mappedDataEntity.setClassLabel(null) == mappedDataEntity

    }

}
