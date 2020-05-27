package de.panbytes.dexter.core

import de.panbytes.dexter.core.data.DataSource
import de.panbytes.dexter.core.domain.DataSourceActions
import de.panbytes.dexter.core.domain.DomainAdapter
import de.panbytes.dexter.core.domain.FeatureSpace
import spock.lang.Specification

import java.util.function.Function

class DataSourceActions_AddActionTest extends Specification {

    DomainAdapter domainAdapter = Spy(constructorArgs: ["MyAdapter", ""])
    FeatureSpace featureSpace = new FeatureSpace("FeatureSpace", [Mock(FeatureSpace.Feature)])
    DataSource dataSource1 = new DataSource("TestSource1", "", featureSpace)
    DataSource dataSource2 = new DataSource("TestSource2", "", featureSpace)

    Function<Collection<DataSource>, DataSourceActions.AddAction> actionSupplier = { srcs ->
        new DataSourceActions.AddAction("MyAction", "Add Source", domainAdapter) {
            @Override
            Optional<Collection<DataSource>> createDataSources(DataSourceActions.AddAction.ActionContext context) {
                return srcs
            }
        }
    }

    def "createAndAdd creates DataSource and offers it to root Source for adding"() {

        when:
            def result = actionSupplier.apply([dataSource1]).createAndAdd(null)

        then:
            result.state == DataSourceActions.AddAction.Result.State.SUCCESS
            result.createdSources == [dataSource1]
            domainAdapter.rootDataSource.blockingFirst().get() == domainAdapter.rootDataSource.blockingFirst().get() // subscribe two times
            domainAdapter.rootDataSource.blockingFirst().get().childDataSources.getValue() == [dataSource1]

        when: "add another source"
            def result2 = actionSupplier.apply([dataSource2]).createAndAdd(null)

        then:
            result2.state == DataSourceActions.AddAction.Result.State.SUCCESS
            result2.createdSources == [dataSource2]
            domainAdapter.rootDataSource.blockingFirst().get().childDataSources.getValue() == [dataSource1, dataSource2]
    }

}
