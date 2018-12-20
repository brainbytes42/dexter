package de.panbytes.dexter.core

import de.panbytes.dexter.core.data.DataNode
import de.panbytes.dexter.core.domain.FeatureSpace
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(3)
class DataNodeTest extends Specification {

    @Shared
    def featureSpace = Stub(FeatureSpace)


    def "Instantiation with parameters and default values"() {
        when:
            def node = new DataNode("Name", "Description", featureSpace) {}

        then:
            // Parameters
            node.name.getValue() == "Name"
            node.description.getValue() == "Description"
            node.featureSpace == featureSpace

            // Defaults
            node.childNodes.getValue() == []
            node.enabledState.getValue() == DataNode.EnabledState.ACTIVE

    }

    def "Constructor ensures valid values"() {
        when:
            new DataNode(n, d, f) {}

        then:
            thrown(NullPointerException)

        where:
            n      | d             | f
            null   | "Description" | featureSpace
            "Name" | null          | featureSpace
            "Name" | "Description" | null

    }

    def "Fluent Setters return Self-Reference"() {
        given:
            def node = new DataNode("Name", "Description", featureSpace) {}

        expect:
            node.setEnabled(true) == node
            node.setChildNodes([]) == node

    }

    def "Changes to EnabledState are propagated only for distinct values"() {
        given:
            def node = new DataNode("Name", "Description", featureSpace) {}
            def observed = []
            node.enabledState.toObservable().subscribe({ next -> observed << next })

        when:
            node.enabled = true // already set as default
            node.enabled = false
            node.enabled = false
            node.enabled = true

        then:
            observed == [DataNode.EnabledState.ACTIVE, DataNode.EnabledState.DISABLED, DataNode.EnabledState.ACTIVE]

    }

    def "Changes to List of Children are propagated only for differing Lists"() {
        given:
            def node = new DataNode("root", "Description", featureSpace) {}
            def observed = []
            node.childNodes.toObservable().subscribe({ next -> observed << next })

            def nodeA = new DataNode("A", "", featureSpace) {}
            def nodeB = new DataNode("B", "", featureSpace) {}
            def nodeC = new DataNode("C", "", featureSpace) {}

        when:
            node.childNodes = [nodeA, nodeB]
            node.childNodes = [nodeA, nodeB]
            node.childNodes = [nodeA, nodeB, nodeC]
            node.childNodes = [nodeA, nodeB, nodeC]
            node.childNodes = [nodeA, nodeB]
            node.childNodes = [nodeA, nodeC]

        then:
            observed == [
                    [],
                    [nodeA, nodeB],
                    [nodeA, nodeB, nodeC],
                    [nodeA, nodeB],
                    [nodeA, nodeC]
            ]

    }

    def "EnabledState is pushed to child nodes"() {
        given:
            def node = new DataNode("root", "Description", featureSpace) {}
            def nodeA = new DataNode("A", "", featureSpace) {}
            def nodeB = new DataNode("B", "", featureSpace) {}
            def nodeBa = new DataNode("Ba", "", featureSpace) {}

            node.childNodes = [nodeA, nodeB]
            nodeB.childNodes = [nodeBa]

        when:
            node.enabled = false

        then:
            nodeA.enabledState.getValue() == DataNode.EnabledState.DISABLED
            nodeB.enabledState.getValue() == DataNode.EnabledState.DISABLED
            nodeBa.enabledState.getValue() == DataNode.EnabledState.DISABLED

        when:
            node.enabled = true

        then:
            nodeA.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            nodeB.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            nodeBa.enabledState.getValue() == DataNode.EnabledState.ACTIVE

    }

    def "EnabledState is pulled from child nodes"() {
        given:
            def node = new DataNode("root", "Description", featureSpace) {}
            def nodeA = new DataNode("A", "", featureSpace) {}
            def nodeB = new DataNode("B", "", featureSpace) {}
            def nodeBa = new DataNode("Ba", "", featureSpace) {}
            def nodeBb = new DataNode("Bb", "", featureSpace) {}

            node.childNodes = [nodeA, nodeB]
            nodeB.childNodes = [nodeBa, nodeBb]

            /*
            root
              \- A
              \- B
                 \- Ba
                 \- Bb
             */

        when:
            nodeBa.enabled = false

        then:
            node.enabledState.getValue() == DataNode.EnabledState.PARTIAL // one child partial
            nodeA.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            nodeB.enabledState.getValue() == DataNode.EnabledState.PARTIAL // one child disabled
            nodeBa.enabledState.getValue() == DataNode.EnabledState.DISABLED
            nodeBb.enabledState.getValue() == DataNode.EnabledState.ACTIVE

        when:
            nodeBb.enabled = false

        then:
            node.enabledState.getValue() == DataNode.EnabledState.PARTIAL
            nodeA.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            nodeB.enabledState.getValue() == DataNode.EnabledState.DISABLED // both children are disabled
            nodeBa.enabledState.getValue() == DataNode.EnabledState.DISABLED
            nodeBb.enabledState.getValue() == DataNode.EnabledState.DISABLED

        when:
            nodeB.enabled = true

        then:
            node.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            nodeA.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            nodeB.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            nodeBa.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            nodeBb.enabledState.getValue() == DataNode.EnabledState.ACTIVE

    }

    def "Binding of EnabledState is repealed for removed children"() {
        given:
            def node = new DataNode("root", "Description", featureSpace) {}
            def nodeA = new DataNode("A", "", featureSpace) {}
            def nodeB = new DataNode("B", "", featureSpace) {}

            node.childNodes = [nodeA, nodeB]
            node.enabled = false

            node.childNodes = [nodeA]

        when: "changing removed child"
            nodeB.enabled = true

        then:
            node.enabledState.getValue() == DataNode.EnabledState.DISABLED
            nodeA.enabledState.getValue() == DataNode.EnabledState.DISABLED

            nodeB.enabledState.getValue() == DataNode.EnabledState.ACTIVE

        when: "changing root"
            nodeB.enabled = false // reset
            node.enabled = true

        then:
            node.enabledState.getValue() == DataNode.EnabledState.ACTIVE
            nodeA.enabledState.getValue() == DataNode.EnabledState.ACTIVE

            nodeB.enabledState.getValue() == DataNode.EnabledState.DISABLED

    }

    def "EnabledState may change when adding or removing children"() {
        given:
            def node = new DataNode("root", "Description", featureSpace) {}
            def nodeA = new DataNode("A", "", featureSpace) {}
            def nodeB = new DataNode("B", "", featureSpace) {}

            node.childNodes = [nodeA, nodeB]

            nodeB.enabled = false // root -> PARTIAL

        when: "remove disabled node"
            node.childNodes = [nodeA]

        then:
            node.enabledState.getValue() == DataNode.EnabledState.ACTIVE

        when: "add disabled node"
            node.childNodes = [nodeA, nodeB]

        then:
            node.enabledState.getValue() == DataNode.EnabledState.PARTIAL

    }

}
