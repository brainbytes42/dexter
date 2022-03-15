package de.panbytes.dexter.ext.task;

import com.google.common.util.concurrent.Uninterruptibles;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import de.panbytes.dexter.util.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class ObservableTask<V> extends Named.BaseImpl implements Named {

    private static final Logger log = LoggerFactory.getLogger(ObservableTask.class);

    private final Observable<V> results;
    private final PublishSubject<V> partialResults = PublishSubject.create();
    private final RxField<State> state = RxField.withInitialValue(State.READY);
    private final RxField<Optional<Double>> progress = RxField.initiallyEmpty();
    private final RxField<Optional<String>> message = RxField.initiallyEmpty();
    private boolean requestCancel = false;
    private boolean canceled = false;
    private Supplier<Boolean> cancelIndicator;


    public ObservableTask(String name, String description, Scheduler scheduler) {
        super(name, description);

        log.trace("Creating ObservableTask: {}", this);

        checkNotNull(scheduler, "Scheduler is null!");

        AtomicBoolean isFresh = new AtomicBoolean(true);
        AtomicReference<V> value = new AtomicReference<>(null);
        AtomicReference<Throwable> exception = new AtomicReference<>(null);

        this.results = Observable.<V>create(emitter -> {

            // we need synchronization to ensure consistency inside this block
            // if a second subscriber comes into play right after the one before was disposed.
            synchronized (this) {

                if (isFresh.getAndSet(false)) {
                    // if the task is called for the first time, just do the processing.

                    try {

                        // listen for and emit partial results
                        emitter.setDisposable(this.partialResults.subscribe(emitter::onNext, emitter::onError));

                        // do the real work, run the task!
                        this.state.setValue(State.IN_PROGRESS);
                        value.set(this.runTask());
                        this.state.setValue(State.SUCCESSFUL);

                        // emit and close
                        emitter.onNext(value.get());
                        emitter.onComplete();

                        log.trace("Finished ObservableTask: {}", this);

                    } catch (Throwable t) {
                        // execution failed as of cancelling or failure

                        if (t instanceof InterruptedException) {
                            this.state.setValue(State.CANCELED);
                        } else {
                            this.state.setValue(State.FAILED);
                        }

                        log.trace("ObservableTask {}: {}", state.getValue().toString(), this);

                        // remember the exception for later subscribers
                        exception.set(t);

                        // swallows exceptions that are thrown after connection was disposed (there is no subscriber to deliver the exception to)
                        // (e.g. canceling task with Exception, because connection was disposed -> nowhere to deliver the exception, but also no relevance)
                        emitter.tryOnError(t);
                    }


                } else {
                    // task has been started before

                    if (value.get() != null) {

                        // computation was successful previously - just return the result
                        emitter.onNext(value.get());
                        emitter.onComplete();

                        log.trace("ObservableTask has been finished already: {}", this);

                    } else {

                        // if the task was interrupted before (by canceling or another exception),
                        // emit an error as the state is not guaranteed to be valid for another run
                        emitter.onError(new IllegalStateException(
                                "Task has been left in potentially invalid state by failure or cancellation in previous invocation, see cause.",
                                exception.get()));

                    }
                }
            }
        }).subscribeOn(scheduler).replay(1).refCount();

    }

    @Deprecated
    public static void main(String[] args) throws InterruptedException {

        ObservableTask<String> task = new ObservableTask<String>("Test", "", Schedulers.computation()) {

            public String runTask() throws Exception {
                System.out.println("TASK running on " + Thread.currentThread());
                boolean fail = false;
                if (fail) {
                    Uninterruptibles.sleepUninterruptibly(50, TimeUnit.MILLISECONDS);
                    System.out.println("creating exception");
                    throw new RuntimeException("FooBar! :-D");
                }
                for (int i = 0; i < 10; i++) {
                    checkCanceled();
                    Uninterruptibles.sleepUninterruptibly(20, TimeUnit.MILLISECONDS);

                    System.out.println("         " + i + " * " + Thread.currentThread() + " | " + Thread.currentThread().isInterrupted());
                    setPartialResult("partial: " + i);
                }
                System.out.println("TASK returning...");
                return "Finished!";
            }

        };

        System.out.println("-- PRE --");

        Disposable disposableA = task.result()
                                     .subscribe(s -> System.out.println("A: " + s),
                                                throwable -> System.out.println("A Exception: " + throwable + ": " + throwable.getCause()));
        System.out.println("subscribed A.");

        TimeUnit.MILLISECONDS.sleep(50);
        //        disposableA.dispose();
        //        System.out.println("disposed A.");

        //        TimeUnit.MILLISECONDS.sleep(50);

        Disposable disposableB = task.results()
                                     .subscribe(s -> System.out.println("B: " + s),
                                                throwable -> System.out.println("B Exception: " + throwable + ": " + throwable.getCause()));
        System.out.println("subscribed B.");

        TimeUnit.MILLISECONDS.sleep(50);
        //        TimeUnit.MILLISECONDS.sleep(1500);

        //        disposableA.dispose();
        //        System.out.println("disposed A.");
        //                        task.requestCancel = true;


        TimeUnit.MILLISECONDS.sleep(2000);

        Disposable disposableC = task.results()
                                     .subscribe(s -> System.out.println("C: " + s),
                                                throwable -> System.out.println("C Exception: " + throwable + ": " + throwable.getCause()));
        System.out.println("subscribed C: " + disposableC.isDisposed());

        TimeUnit.MILLISECONDS.sleep(50);

        System.out.println(disposableC.isDisposed());
        disposableC.dispose();
        System.out.println("disposed C.");

        try {
            System.out.println("FINAL: " + task.results().blockingLast());
        } catch (Exception e) {
            System.out.println("CAUGHT: " + e);
        }


    }

    public RxFieldReadOnly<Optional<Double>> getProgress() {
        return this.progress.toReadOnlyView();
    }

    public RxFieldReadOnly<Optional<String>> getMessage() {
        return this.message.toReadOnlyView();
    }

    protected final void setMessage(String message) {

        this.message.setValue(Optional.of(message));

    }

    public RxFieldReadOnly<State> getState() {
        return this.state.toReadOnlyView();
    }

    /**
     * Trigger execution by subscribing to returned Single.
     * Result is cached, execution will be performed only once.
     *
     * @return
     */
    public final Single<V> result() {
        return this.results.lastOrError();
    }

    public final Observable<V> results() {
        return this.results;
    }

    protected abstract V runTask() throws Exception;

    protected final void checkCanceled() throws InterruptedException {
        if (this.canceled) {
            throw new InterruptedException("Task has been canceled: " + this);
        } else if (Thread.interrupted() /*Clears interrupted status!*/) {
            this.canceled = true;
            throw new InterruptedException("Thread has been interrupted for " + this);
        } else if (this.requestCancel) {
            this.canceled = true;
            this.requestCancel = false;
            throw new InterruptedException("Cancel has been requested for " + this);
        } else if (this.cancelIndicator != null && this.cancelIndicator.get()) {
            this.canceled = true;
            throw new InterruptedException("External Cancellation was applied for " + this);
        }
    }

    protected final void setCancelIndicator(Supplier<Boolean> cancelIndicator) {
        this.cancelIndicator = cancelIndicator;
    }

    protected final void setPartialResult(V value) {
        this.partialResults.onNext(checkNotNull(value, "partial result"));
    }

    protected final void setProgress(double workDone, double totalWork) {

        double progress = -1;

        if (Double.isFinite(workDone) && Double.isFinite(totalWork) && Math.min(workDone, totalWork) >= 0) {
            progress = Math.min(workDone / totalWork, 1.0);
        }

        if (progress >= 0) {
            this.progress.setValue(Optional.of(progress));
        } else {
            this.progress.setValue(Optional.empty());
        }

    }

    protected final void setMessageEmpty() {

        this.message.setValue(Optional.empty());

    }

    @Override
    public String toString() {
        return super.toString() + "_" + getState().getValue();
    }

    public enum State {

        READY, /*INDETERMINED,*/ IN_PROGRESS, SUCCESSFUL, CANCELED, FAILED

    }


}
