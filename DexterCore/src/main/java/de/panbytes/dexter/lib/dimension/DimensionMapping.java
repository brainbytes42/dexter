package de.panbytes.dexter.lib.dimension;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class DimensionMapping {


    public final MappingProcessor map(double[][] inputMatrix, Context context) {
        return mapAsync(inputMatrix, context, Runnable::run);
    }

    public final MappingProcessor mapAsync(double[][] inputMatrix, Context context) {
        return mapAsync(inputMatrix, context, Executors.newSingleThreadExecutor());
    }

    public final MappingProcessor mapAsync(double[][] inputMatrix, Context context, Executor executor) {
        AbstractMappingProcessor mappingProcessor = createMappingProcessor(inputMatrix, context);
        CompletableFuture<double[][]> completableFuture = null;
        try {
            completableFuture = CompletableFuture.supplyAsync(mappingProcessor::process,
                                                              executor);
        } catch (Exception e) {
            completableFuture = CompletableFuture.completedFuture(null);
            e.printStackTrace();
        }
        mappingProcessor.setCompletableFuture(completableFuture);
        return mappingProcessor;
    }

    protected abstract AbstractMappingProcessor createMappingProcessor(double[][] inputMatrix, Context context);


    public interface IntermediateResultListener {
        void update(double[][] newIntermediateResult, MappingProcessor source);
    }

    public interface MappingProcessor {

        double[][] getResult();

        Optional<double[][]> getLastIntermediateResult();

        CompletableFuture<double[][]> getCompletableFuture();
        //
        //        ProgressInfo getProgress();
        //
        //        boolean cancel();

        void addIntermediateResultListener(IntermediateResultListener listener);


        class ProgressInfo {

            private Double totalWork = null;
            private Double workDone = null;

            private String message = null;

            public Optional<Double> getTotalWork() {
                return Optional.ofNullable(totalWork);
            }

            public Optional<Double> getWorkDone() {
                return Optional.ofNullable(workDone);
            }

            public Optional<Double> getProgress() {
                return getWorkDone().flatMap(done -> getTotalWork().map(total -> done / total));
            }

            public Optional<String> getMessage() {
                return Optional.ofNullable(message);
            }

            protected void setMessage(String message) {
                this.message = message;
            }

            protected void setProgress(long workDone, long max) {
                setProgress(Long.valueOf(workDone).doubleValue(), Long.valueOf(max).doubleValue());
            }

            protected void setProgress(double workDone, double totalWork) {

                // ensure finite values
                if (!Double.isFinite(workDone)) {
                    workDone = -1;
                }
                if (!Double.isFinite(totalWork)) {
                    totalWork = -1;
                }

                // restrict work done to total work (not more than 100%)
                workDone = Math.min(workDone, totalWork);

                // set positive values or null else.
                this.workDone = workDone >= 0 ? workDone : null;
                this.totalWork = totalWork >= 0 ? totalWork : null;

            }

        }

    }

    public static class Context {
        public static final Context EMPTY = new Context();

    }

    protected abstract class AbstractMappingProcessor implements MappingProcessor {

        private CompletableFuture<double[][]> completableFuture;
        private Collection<IntermediateResultListener> intermediateResultListeners = new CopyOnWriteArraySet<>();

        @Override
        public CompletableFuture<double[][]> getCompletableFuture() {
            return completableFuture;
        }

        public final void setCompletableFuture(CompletableFuture<double[][]> completableFuture) {
            this.completableFuture = checkNotNull(completableFuture);
        }

        @Override
        public final double[][] getResult() {
            return this.completableFuture.join();
        }

        @Override
        public void addIntermediateResultListener(IntermediateResultListener listener) {
            this.intermediateResultListeners.add(listener);
        }

        protected void notifyNewIntermediateResult() {
            this.intermediateResultListeners.forEach(
                    listener -> listener.update(getLastIntermediateResult().get(), this));
        }

        protected abstract double[][] process();
    }

}
