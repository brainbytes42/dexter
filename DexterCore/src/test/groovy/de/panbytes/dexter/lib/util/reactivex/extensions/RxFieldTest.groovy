package de.panbytes.dexter.lib.util.reactivex.extensions

import io.reactivex.subjects.ReplaySubject
import io.reactivex.subjects.Subject
import spock.lang.Specification

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

class RxFieldTest extends Specification {

    public static final int initialValue = 42
    RxField<Integer> rxField = RxField.withInitialValue(initialValue)

    def "initial value is returned as current value"() {
        expect:
            rxField.getValue() == initialValue
    }

    def "empty initial value modeled by optional.empty"() {
        given:
            RxField<Optional<Integer>> empty = RxField.initiallyEmpty()

        expect:
            empty.getValue() == Optional.empty()
    }

    def "current value is returned"() {
        given:
            rxField.setValue(1)
        expect:
            rxField.getValue() == 1
    }

    def "observable streaming values, starting with initial value"() {
        given:
            def observable = rxField.toObservable()
            def results = []
            observable.subscribe({ next -> results << next })

        when:
            rxField.setValue(1)
            rxField.setValue(2)

        then:
            results == [initialValue, 1, 2]
    }

    def "observable streaming distinct values"() {
        given:
            def observable = rxField.toObservable()
            def results = []
            observable.subscribe({ next -> results << next })

        when:
            rxField.setValue(initialValue)
            rxField.setValue(1)
            rxField.setValue(1)
            rxField.setValue(2)
            rxField.setValue(1)

        then:
            results == [initialValue, 1, 2, 1]
    }

    def "observable starting with current value when subscribing"() {
        given:
            def observable = rxField.toObservable()
            def results = []

            rxField.setValue(1)
            rxField.setValue(2)

        when:
            observable.subscribe({ next -> results << next })
            rxField.setValue(3)

        then:
            results == [2, 3]
    }

    def "observable starting with current value when re-subscribing"() {
        given:
            def observable = rxField.toObservable()
            def results = []

            def disposable = observable.subscribe()
            rxField.setValue(1)
            disposable.dispose()

            rxField.setValue(2)

        when:
            observable.subscribe({ next -> results << next })
            rxField.setValue(3)

        then:
            results == [2, 3]
    }

    def "read-only view has same results"() {
        given:
            def view = rxField.toReadOnlyView()
            def results = []
            view.toObservable().subscribe({ results << it })

        when:
            rxField.setValue(1)

        then:
            view.getValue() == 1
            results == [initialValue, 1]
    }

    def "returned observable stream is resilient to multi-threading wrt. take()-operator"() {
        when:
            def failures = 0

            int testIterationsForMultithreading = 10000
            def numberOfThreads = 20
            def takeAmount = 10

            for (int i = 0; i < testIterationsForMultithreading; i++) {

                try {
                    RxField<Integer> rxField = RxField.withInitialValue(0)

                    // count calls to subscriber - should be equal to takeAmount.
                    def received = new AtomicInteger()
                    rxField.toObservable().take(takeAmount).subscribe({ __ -> received.incrementAndGet() })

                    IntStream.range(0, numberOfThreads).parallel().forEach({ t -> rxField.setValue(t) })

                    if (received.get() != takeAmount) {
                        failures++
                    }

                } catch (Exception ignored) {
                    failures++;
                }

            }

        then:
            failures == 0
    }

    def "returned observable stream is resilient to multi-threading wrt. received values"() {
        when:
            def failures = 0

            int testIterationsForMultithreading = 1000
            def numberOfThreads = 20

            for (int i = 0; i < testIterationsForMultithreading; i++) {

                try {
                    RxField<Integer> rxField = RxField.withInitialValue(0)

                    Subject<Integer> replay = ReplaySubject.create()
                    rxField.toObservable().subscribe(replay)

                    IntStream.range(0, numberOfThreads).parallel().forEach({ rxField.setValue(it) })

                    replay.onComplete()
                    List<Integer> fieldValues = replay.toList().timeout(100, TimeUnit.MILLISECONDS).blockingGet()

                    if (fieldValues.size() != numberOfThreads + 1) {
                        failures++
                    } else if (fieldValues.contains(null)) {
                        failures++
                    }

                } catch (Exception ignored) {
                    failures++;
                }

            }

        then:
            failures == 0
    }

    def "getValue() is resilient to multi-threading"() {
        when:
            def failures = 0

            int testIterationsForMultithreading = 1000
            def numberOfThreads = 20

            for (int i = 0; i < testIterationsForMultithreading; i++) {

                try {
                    RxField<Integer> rxField = RxField.withInitialValue(0)

                    rxField.toObservable().filter({ it != rxField.getValue() }).subscribe({ failures++ })

                    boolean nonNull = IntStream.range(0, numberOfThreads)
                            .parallel()
                            .peek({ rxField.setValue(it) })
                            .allMatch(Objects.&nonNull);

                    if (!nonNull) {
                        failures++
                    }

                } catch (Exception ignored) {
                    failures++;
                }

            }

        then:
            failures == 0
    }

    def "comparer for distinct values can be changed"() {
        given:
            def rxField = RxField.withInitialValue("Hello")

            def results = []
            rxField.toObservable().subscribe({ results << it })

            rxField.setValue("Hi")
            rxField.setValue("Welcome")

        when:
            def fieldReturned = rxField.distinguishingValues({ s1, s2 -> s1.substring(0, 1).equals(s2.substring(0, 1)) })
            rxField.setValue("Hello")
            rxField.setValue("Hi")
            rxField.setValue("Welcome")

        then:
           fieldReturned==rxField
            results == ["Hello", "Hi", "Welcome", "Hello",/* 'Hi' is missing */ "Welcome"]
    }

    def "wrapper wraps returned values"() {
        given:
            def rxField = RxField.withInitialValue(0).wrappingReturnedValues({ it + 1 })

        // twice each to see unwanted recursive effects
            def resultsObserved1 = []
            rxField.toObservable().subscribe({ next -> resultsObserved1 << next })
            def resultsObserved2 = []
            rxField.toObservable().subscribe({ next -> resultsObserved2 << next })
            def resultsGetter1 = []
            def resultsGetter2 = []

        when:
            resultsGetter1 << rxField.getValue()
            resultsGetter2 << rxField.getValue()

            rxField.setValue(1)

            resultsGetter1 << rxField.getValue()
            resultsGetter2 << rxField.getValue()

        then:
            resultsObserved1 == [1,2]
            resultsObserved2 == [1,2]
            resultsGetter1 == [1,2]
            resultsGetter2 == [1,2]

    }

}
