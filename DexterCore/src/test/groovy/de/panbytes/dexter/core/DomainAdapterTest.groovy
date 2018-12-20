package de.panbytes.dexter.core

import de.panbytes.dexter.core.data.DataSource
import de.panbytes.dexter.core.data.DomainDataEntity
import de.panbytes.dexter.core.domain.DomainAdapter
import de.panbytes.dexter.core.domain.FeatureSpace
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DomainAdapterTest extends Specification {

    @Shared
    FeatureSpace featureSpace = Stub() {
        match(_) >> true
    }

    DomainAdapter domainAdapter = Spy(constructorArgs: ["Adapter", ""]) {

    }

    DataSource dataSource = Spy(constructorArgs: ["Src", "", featureSpace]) {
        getFeatureSpace() >> featureSpace
    }

    DataSourceActions.AddAction addAction = Spy(constructorArgs: ["Creator", "", domainAdapter]) {
        createDataSources(_) >> [dataSource]
    }

    def "Initialization of FeatureSpace and RootDataSource"() {
        when:
            domainAdapter.featureSpace = featureSpace

        then:
            domainAdapter.featureSpace.getValue().get() == featureSpace
            domainAdapter.rootDataSource.blockingFirst().get() instanceof DataSource

    }

    def "Using AddAction to add DataSource"() {

        given:
            domainAdapter.featureSpace = featureSpace
            domainAdapter.dataSourceActions.defaultAddAction = addAction

        when:
            def result = domainAdapter.dataSourceActions.defaultAddAction.blockingFirst().get().createAndAdd(null)

        then:
            result.state == DataSourceActions.AddAction.Result.State.SUCCESS
            result.createdSources == [dataSource]
            domainAdapter.rootDataSource.blockingFirst().get().childDataSources.getValue() == [dataSource]

    }


    def "Initialization Progress without prior Knowledge of FeatureSpace"() {

        given:
            domainAdapter.dataSourceActions.defaultAddAction = addAction

        when:
            domainAdapter.dataSourceActions.defaultAddAction.firstElement().subscribe({
                creatorOpt ->
                    creatorOpt.map({
                        creator ->
                            // eg. UI interaction: user triggers creation of DS.
                            creator.createAndAdd(null)
                    })
            })

        then:
            domainAdapter.rootDataSource.blockingFirst().present
            domainAdapter.rootDataSource.blockingFirst().get().childDataSources.getValue() == [dataSource]

    }


    @Unroll
    def "filtered domain data emits correct datasets (case #x.caseDescr)"() {
        given:
            DataSource dataSource = new DataSource("", "", featureSpace) {
                void setData(List<DomainDataEntity> data) {
                    setGeneratedDataEntities(data)
                }
            }
            domainAdapter.setFeatureSpace(featureSpace)
            domainAdapter.getRootDataSource().blockingFirst().get().addChildDataSource(dataSource)


            def results = []
            domainAdapter.filteredDomainData.subscribe({ it -> results << it })


        when:
            if (x.filters1 != null)
                domainAdapter.dataFilters = x.filters1
            if (x.data1 != null)
                dataSource.data = x.data1
            if (x.filters2 != null)
                domainAdapter.dataFilters = x.filters2
            if (x.data2 != null)
                dataSource.data = x.data2
            if (x.filters3 != null)
                domainAdapter.dataFilters = x.filters3
            if (x.data3 != null)
                dataSource.data = x.data3
            if (x.filters4 != null)
                domainAdapter.dataFilters = x.filters4
            if (x.data4 != null)
                dataSource.data = x.data4
        then:
            results == x.expected

        where:
            x << {
                def a = new DomainDataEntity("A", "", [0] as double[], featureSpace, dataSource)
                def b = new DomainDataEntity("B", "", [0] as double[], featureSpace, dataSource)
                def c = new DomainDataEntity("C", "", [0] as double[], featureSpace, dataSource)
                def foo = new DomainDataEntity("foo", "", [0] as double[], featureSpace, dataSource)
                def bar = new DomainDataEntity("bar", "", [0] as double[], featureSpace, dataSource)
                def foobar = new DomainDataEntity("foobar", "", [0] as double[], featureSpace, dataSource)

                def filterFoo = new DomainAdapter.FilterModule("", "") {
                    @Override
                    boolean accept(DomainDataEntity entity) {
                        // reject "foo"
                        return !entity.name.getValue().contains("foo")
                    }
                }
                def filterBar = new DomainAdapter.FilterModule("", "") {
                    @Override
                    boolean accept(DomainDataEntity entity) {
                        // reject "bar"
                        return !entity.name.getValue().contains("bar")
                    }
                }

                [
                        [
                                caseDescr: "No Filters, changing Data",
                                data1    : [a, b],
                                data2    : [a, b, c],
                                data3    : [],
                                expected : [[], [a, b], [a, b, c], []]
                        ], [
                                caseDescr: "Filter first, changing non-filtered Data",
                                filters1 : [filterFoo, filterBar],
                                data1    : [a, b],
                                data2    : [a, b, c],
                                data3    : [],
                                expected : [[], [a, b], [a, b, c], []]
                        ], [
                                caseDescr: "Filter first, changing filtered Data",
                                filters1 : [filterFoo, filterBar],
                                data1    : [a, b, foo],
                                data2    : [a, b, bar], // should not trigger a new result
                                data3    : [a, b, c, bar],
                                expected : [[], [a, b], [a, b, c]]
                        ], [
                                caseDescr: "Data first, changing filters",
                                data1    : [a, b, c, foo, bar, foobar],
                                filters2 : [filterFoo],
                                filters3 : [filterBar],
                                filters4 : [],
                                expected : [[], [a, b, c, foo, bar, foobar], [a, b, c, bar], [a, b, c, foo], [a, b, c, foo, bar, foobar]]
                        ], [
                                caseDescr: "Data first, changing irrelevant filters",
                                data1    : [a, b, c],
                                filters2 : [filterFoo], // should not trigger a new result
                                filters3 : [filterFoo, filterBar], // should not trigger a new result
                                filters4 : [], // should not trigger a new result
                                expected : [[], [a, b, c]]
                        ]
                ]
            }()

//        when:
//            domainAdapter.dataFilters = [filterX, filterY]
//            dataSource.data = [A, B, C]
//        then:
//            results == [[], [A, B, C]]


    }


}
