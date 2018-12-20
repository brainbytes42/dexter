package de.panbytes.dexter.ext.task

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class ObservableTaskTest extends Specification {

    class SuccessfulTask<V> extends ObservableTask<V> {
        private final Scheduler testScheduler
        private final List<V> values
        private final long delaySecondsBeforeSteps

        SuccessfulTask(Scheduler taskScheduler, Scheduler testScheduler, List<V> values, long delaySecondsBeforeSteps) {
            super("SuccessfulTask", "", taskScheduler)
            this.testScheduler = testScheduler
            this.values = values
            this.delaySecondsBeforeSteps = delaySecondsBeforeSteps
        }

        @Override
        protected V runTask() throws Exception {
            Observable.fromIterable(values.subList(0, values.size() - 1)).delay(delaySecondsBeforeSteps, TimeUnit.SECONDS, testScheduler).subscribe({
                setPartialResult(it)
            })
            Single.just("").subscribeOn(testScheduler).b
            return values.last()
        }
    }

    class FailingTask<V> extends ObservableTask<V> {
        private final Scheduler testScheduler
        private final long delaySeconds

        FailingTask(Scheduler testScheduler, long delaySeconds) {
            super("FailingTask", "", Schedulers.newThread())
            println "create FailingTask"
            this.testScheduler = testScheduler
            this.delaySeconds = delaySeconds
        }

        @Override
        protected V runTask() throws Exception {
            println "running..."
            Completable.complete().delay(delaySeconds, TimeUnit.SECONDS, testScheduler).blockingAwait()
            println "now throw!"
            throw new RuntimeException("MyException")
        }
    }


    def setup() {
    }

    /* TODO Aspects to test:

    - single subscriber
    - multiple parallel subscribers (immediate and delayed)
    - late-coming subscriber after finished
    - disposing last subscriber cancels
    - disposing one of many subscribers continues
    - at most one execution
    - failing task (single / multi subscribers, late-coming)
    - canceling
    - canceled task (new subscribers)

    - progress
    - message (incl. set empty)
    - state

     */

    def "test"() {
        given:
            def testScheduler = new TestScheduler()
        when:
            def test = new FailingTask(testScheduler, 1).result().test()
            println "..."
            testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)
        then:
            testScheduler.scheduleDirect({
                println "THEN"
                test.assertErrorMessage("MyException")
            })
//            testScheduler.triggerActions()
        Thread.sleep(2000)
    }


}
