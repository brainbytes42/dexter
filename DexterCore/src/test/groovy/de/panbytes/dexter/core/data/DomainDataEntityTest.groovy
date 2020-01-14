package de.panbytes.dexter.core.data

import de.panbytes.dexter.core.ClassLabel
import de.panbytes.dexter.core.data.DataSource
import de.panbytes.dexter.core.data.DomainDataEntity
import de.panbytes.dexter.core.domain.FeatureSpace
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

@Timeout(3)
class DomainDataEntityTest extends Specification {

    @Shared
    double[] coordinates = [1, 2, 3]
    @Shared
    FeatureSpace featureSpace = Stub() {
        match(_) >> { args -> args[0].length == 3 }
    }

    DomainDataEntity domainDataEntity = new DomainDataEntity("Name", "Description", coordinates, featureSpace)


    @Unroll
    def "Constructor ensures non-null parameters"() {
        when:
            new DomainDataEntity(name, description, coords, space)

        then:
            thrown(NullPointerException)

        where:
            name   | description   | coords      | space
            null   | "Description" | coordinates | featureSpace
            "Name" | null          | coordinates | featureSpace
            "Name" | "Description" | null        | featureSpace
            "Name" | "Description" | coordinates | null

    }

    def "classLabel has fluent setter"() {
        when:
            def result = domainDataEntity.setClassLabel(null)

        then:
            result != null
            result instanceof DomainDataEntity

    }

    def "classLabel provides distinct values"() {
        given:
            def observed = []
            domainDataEntity.classLabel.toObservable().subscribe({ next -> observed << next })

        when:
            domainDataEntity.setClassLabel(null)
            domainDataEntity.setClassLabel(ClassLabel.labelFor("A"))
            domainDataEntity.setClassLabel(ClassLabel.labelFor("A"))
            domainDataEntity.setClassLabel(ClassLabel.labelFor("B"))
            domainDataEntity.setClassLabel(ClassLabel.labelFor("A"))
            domainDataEntity.setClassLabel(null)

        then:
            observed == [
                    Optional.empty(),
                    Optional.of(ClassLabel.labelFor("A")),
                    Optional.of(ClassLabel.labelFor("B")),
                    Optional.of(ClassLabel.labelFor("A")),
                    Optional.empty(),
            ]

    }

    def "generating source is readable"() {
        given:
            DataSource generatingSource = Mock()
        when:
            domainDataEntity.setGeneratingDataSource(generatingSource)
        then:
            domainDataEntity.generatingDataSource.get() == generatingSource
    }


}
