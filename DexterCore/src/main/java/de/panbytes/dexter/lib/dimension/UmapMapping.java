package de.panbytes.dexter.lib.dimension;

import de.panbytes.dexter.ext.task.ObservableTask;
import de.panbytes.dexter.ext.task.TaskMonitor;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.StatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.feature.extraction.PCA;
import smile.manifold.UMAP;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Optional;

/**
 * @author Fabian Krippendorff (SZFG/TKAS)
 */
public class UmapMapping extends DimensionMapping {

    private static final Logger log = LoggerFactory.getLogger(UmapMapping.class);

    protected AbstractMappingProcessor createMappingProcessor(double[][] inputMatrix, Context context) {
        return new UmapProcessor(inputMatrix, context);
    }

    private class UmapProcessor extends AbstractMappingProcessor {
        private final double[][] inputMatrix;
        private final UmapContext context;

        public UmapProcessor(double[][] inputMatrix, Context context) {
            this.inputMatrix = inputMatrix;
            this.context = (UmapContext) context;
        }

        @Override
        public Optional<double[][]> getLastIntermediateResult() {
            return Optional.empty(); // TODO
        }

        @Override
        public void cancel() {
            //TODO
            throw new UnsupportedOperationException("Not yet implemented!");
        }

        @Override
        protected double[][] process() {

            ObservableTask<double[][]> task = new ObservableTask<double[][]>("UMAP", "Reducing dimensionality using UMAP",
                    Schedulers.from(Runnable::run)) {

                @Override
                protected double[][] runTask() throws Exception {

                    setMessage("normalizing...");
                    RealMatrix normalized = MatrixUtils.createRealMatrix(inputMatrix);
                    for (int i = 0; i < normalized.getColumnDimension(); i++) {
                        double[] n = StatUtils.normalize(normalized.getColumn(i));
                        if (Arrays.stream(n).anyMatch(Double::isNaN)) Arrays.fill(n, 0);
                        normalized.setColumn(i, n);
                    }

                    setMessage("preprocessing (PCA)...");
                    double[][] data = PCA.fit(normalized.getData()).getProjection(0.95).apply(normalized.getData());
                    log.info("PCA-Mapping: (" + normalized.getData().length + "x" + normalized.getData()[0].length + ") -> (" + data.length + "x" + data[0].length + ")");

                    setMessage("running UMAP...");
                    return UMAP.of(data, context.distance, context.kNearestNeighbours, 2, data.length > 10000 ? 200 : 500, 1.0, context.minDist, context.spread, 5, 1.0).coordinates;
                }

            };

            TaskMonitor.evilReference.addTask(task);

            return task.result().blockingGet();

        }
    }

    public static class UmapContext extends Context {
        /**
         * k-nearest neighbors. Larger values result in more global views of the manifold, while smaller values result in more local data being preserved. Generally in the range 2 to 100.
         */
        private final int kNearestNeighbours;
        /**
         * distance – the distance function.
         */
        private final Distance<double[]> distance;
        /**
         * minDist – The desired separation between close points in the embedding space. Smaller values will result in a more clustered/clumped embedding where nearby points on the manifold are drawn closer together, while larger values will result on a more even disperse of points. The value should be set no-greater than and relative to the spread value, which determines the scale at which embedded points will be spread out. default 0.1.
         */
        private final double minDist;
        /**
         * spread – The effective scale of embedded points. In combination with minDist, this determines how clustered/clumped the embedded points are. default 1.0.
         */
        private final double spread;

        /**
         *
         * @param kNearestNeighbours Larger values result in more global views of the manifold, while smaller values result in more local data being preserved. Generally in the range 2 to 100.
         * @param distance the distance function (null -> Euclidean).
         * @param minDist The desired separation between close points in the embedding space. Smaller values will result in a more clustered/clumped embedding where nearby points on the manifold are drawn closer together, while larger values will result on a more even disperse of points. The value should be set no-greater than and relative to the spread value, which determines the scale at which embedded points will be spread out. default 0.1.
         * @param spread The effective scale of embedded points. In combination with minDist, this determines how clustered/clumped the embedded points are. default 1.0.
         */
        public UmapContext(@Nullable Integer kNearestNeighbours, @Nullable Distance<double[]> distance, @Nullable Double minDist, @Nullable Double spread) {
            this.kNearestNeighbours = Optional.ofNullable(kNearestNeighbours).orElse(15);
            this.distance = Optional.ofNullable(distance).orElse(new EuclideanDistance());
            this.minDist = Optional.ofNullable(minDist).orElse(0.1);
            this.spread = Optional.ofNullable(spread).orElse(1.0);
        }
    }
}
