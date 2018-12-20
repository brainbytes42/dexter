package de.panbytes.dexter.core

import de.panbytes.dexter.core.ClassLabel
import spock.lang.Specification

import java.lang.reflect.Modifier

class ClassLabelTest extends Specification {

    def "Label String is stored"() {
        when:
            def label = ClassLabel.labelFor("A")
        then:
            label.label == "A"

    }

    def "ClassLabel has only private constructor to ensure only one label for a given string"() {
        expect:
            Arrays.asList(ClassLabel.class.getConstructors())
                    .stream()
                    .noneMatch({ c -> c.getModifiers() != Modifier.PRIVATE })

    }

    def "Create only one Instance per Label-String"() {
        when: "same string"
            def firstEqual = ClassLabel.labelFor("A")
            def secondEqual = ClassLabel.labelFor("A")
        then:
            firstEqual == secondEqual

        when: "different strings"
            def firstDifferent = ClassLabel.labelFor("A")
            def secondDifferent = ClassLabel.labelFor("B")
        then:
            firstDifferent != secondDifferent

    }

    def "Null is not allowed as Label String"() {
        when:
            ClassLabel.labelFor(null)
        then:
            thrown(NullPointerException)

    }

}
