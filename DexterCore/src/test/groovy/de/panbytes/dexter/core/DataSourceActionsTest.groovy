package de.panbytes.dexter.core

import de.panbytes.dexter.core.domain.DataSourceActions
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(3)
class DataSourceActionsTest extends Specification {

    def factory = new DataSourceActions()

    def addActions = [Stub(DataSourceActions.AddAction), Stub(DataSourceActions.AddAction)]


    def "Factory has initially no Actions and default is empty."() {
        expect:
            factory.addActions.blockingFirst().empty
            !factory.defaultAddAction.blockingFirst().present
    }

    def "When setting Actions, Default is set to first element"() {
        when:
            factory.addActions = addActions

        then:
            factory.addActions.blockingFirst() == addActions
            factory.defaultAddAction.blockingFirst().get() == addActions[0]

    }

    @Ignore("Refactor to check RESULT (blackbox), or remove delegate method")
    def "Setting Actions with varargs delegates to collection-version"() {
        given:
            DataSourceActions factory = Spy()
        when:
            factory.setAddActions(addActions[0],addActions[1])
        then:
            1* factory.setAddActions(addActions)

    }

    def "When setting default Action, Default is added as first element if not contained"() {
        def defaultAddAction = Stub(DataSourceActions.AddAction)

        when: "setting when AddAction-List is empty"
            factory.defaultAddAction = defaultAddAction

        then:
            factory.defaultAddAction.blockingFirst().get() == defaultAddAction
            factory.addActions.blockingFirst() == [defaultAddAction]

        when: "setting default when Actions are available"
            factory.addActions = addActions
            factory.defaultAddAction = defaultAddAction

        then:
            factory.defaultAddAction.blockingFirst().get() == defaultAddAction
            factory.addActions.blockingFirst() == [defaultAddAction] + addActions

        when: "setting default, that was added but no default before, leaves list untouched"
            factory.addActions = addActions
            factory.defaultAddAction = addActions[1]
        then:
            factory.defaultAddAction.blockingFirst().get() == addActions[1]
            factory.addActions.blockingFirst() == addActions

    }

    def "When setting Actions-List without current default Action, Default is set to first Element"() {
        def defaultAddAction = Stub(DataSourceActions.AddAction)

        given:
            factory.addActions = [defaultAddAction] + addActions

        when:
            factory.addActions = addActions

        then:
            factory.defaultAddAction.blockingFirst().get() == addActions[0]
            factory.addActions.blockingFirst() == addActions

    }

    def "When setting empty Actions-List, Default is empty, too."() {
        given:
            factory.addActions = addActions

        when:
            factory.addActions = []

        then:
            !factory.defaultAddAction.blockingFirst().present
            factory.addActions.blockingFirst() == []

    }

    def "When setting default Action null, it throws an Exception"() {
        when:
            factory.defaultAddAction = null

        then:
            thrown(NullPointerException)

    }

    def "When setting Actions, List and List-Elements may not be null"() {
        when:
            factory.addActions = cr

        then:
            thrown(ex)
            factory.addActions.blockingFirst().empty
            !factory.defaultAddAction.blockingFirst().present

        where:
            cr                                        | ex
            null                                      | NullPointerException
            [null]                                    | NullPointerException
            [Stub(DataSourceActions.AddAction), null] | NullPointerException
    }

}
