package de.panbytes.dexter.core

import de.panbytes.dexter.util.Named
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(3)
class Named_BaseImplTest extends Specification {

    def "Fields are initialized properly"() {
        when:
            def named = new Named.BaseImpl("Name", "Description"){}
        then:
            named.name.getValue() == "Name"
            named.description.getValue() == "Description"

    }

}
