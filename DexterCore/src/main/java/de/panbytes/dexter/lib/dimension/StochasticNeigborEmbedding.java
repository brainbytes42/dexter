package de.panbytes.dexter.lib.dimension;

import com.jujutsu.tsne.FastTSne;
import com.jujutsu.tsne.TSne;
import com.jujutsu.tsne.TSneConfiguration;
import com.jujutsu.tsne.barneshut.BHTSne;
import com.jujutsu.tsne.barneshut.ParallelBHTsne;
import com.jujutsu.utils.TSneUtils;
import de.panbytes.dexter.ext.task.ObservableTask;
import de.panbytes.dexter.ext.task.TaskMonitor;
import de.panbytes.dexter.lib.Preprocessing;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.NormalizedRandomGenerator;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.manifold.TSNE;
import smile.projection.PCA;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StochasticNeigborEmbedding extends DimensionMapping {

    private static final Logger log = LoggerFactory.getLogger(StochasticNeigborEmbedding.class);

    private int targetDimensions = 2; //TODO configurable!


    // private AffinityCalculator lowDim

    // private AffinityCalculator highDim

    // AffinityCalculator: calculateAffinities

    @Override
    protected AbstractMappingProcessor createMappingProcessor(double[][] inputMatrix, Context context) {
        return new BarnesHutTsneLibProcessor(inputMatrix, context);
//                        return new SimpleTSneProcessor(inputMatrix, context);
    }

    public static class SimpleTSneContext extends Context {
        private double[][] initialSolution;
        private double perplexity;

        public void setInitialSolution(double[][] initialSolution) {
            this.initialSolution = initialSolution;
        }

        public void setPerplexity(double perplexity) {
            this.perplexity = perplexity;
        }
    }

    private class BarnesHutTsneLibProcessor extends AbstractMappingProcessor {

        private final double[][] inputMatrix;
        private final SimpleTSneContext context;
        private TSne tsne;

        public BarnesHutTsneLibProcessor(double[][] inputMatrix, Context context) {
            super();
            this.inputMatrix = inputMatrix;
            this.context = (SimpleTSneContext) context; //TODO cast here?
        }

        @Override
        public Optional<double[][]> getLastIntermediateResult() {
            return Optional.empty();
        }

        @Override
        public void cancel() {
            log.debug("Cancelling t-SNE...");
            tsne.abort();
        }

        @Override
        protected double[][] process() {

            ObservableTask<double[][]> task = new ObservableTask<double[][]>("t-SNE", "Reducing dimensionality using t-SNE",
                                                                             Schedulers.from(Runnable::run)) {

                @Override
                protected double[][] runTask() throws Exception {
                    int initial_dims = 30;

                    boolean barnesHut = true;
                    if (barnesHut) {
                        boolean parallel = true;
                        if (parallel) {
                            tsne = new ParallelBHTsne();
                        } else {
                            tsne = new BHTSne();
                        }
                    } else {
                        tsne = new FastTSne();
                    }

                    setMessage("normalizing...");

                    RealMatrix normalized = MatrixUtils.createRealMatrix(inputMatrix);
                    for (int i = 0; i < normalized.getColumnDimension(); i++) {
                        double[] n = StatUtils.normalize(normalized.getColumn(i));
                        if (Arrays.stream(n).anyMatch(Double::isNaN)) Arrays.fill(n, 0);
                        normalized.setColumn(i, n);
                    }

                    setMessage("preprocessing (PCA)...");

                    double[][] X;
                    boolean fixed = false;
                    if (fixed) {
                        X = new PCA(normalized.getData()).setProjection(initial_dims).project(normalized.getData());
                    } else {
                        X = new PCA(normalized.getData()).setProjection(0.95).project(normalized.getData());
                    }

                    log.info("DATASET: (" + X.length + "x" + X[0].length + ")");


                    setMessage("running t-SNE...");

                    log.debug("Running {} for {} data entities, reducing {} dims to 2 and using Perplexity {}.", tsne.getClass().getSimpleName(), X.length, initial_dims, context.perplexity);

                    TSneConfiguration config = TSneUtils.buildConfig(X, 2, initial_dims, context.perplexity, 1000, false, 0.5, false);
                    double[][] Y = tsne.tsne(config);

                    return Y;
                }
            };

            TaskMonitor.evilReference.addTask(task);

            return task.result().blockingGet();
        }
    }

    private class SimpleTSneProcessor extends AbstractMappingProcessor {

        //TODO seeded!

        private final NormalizedRandomGenerator RANDOM_GAUSSIAN = new GaussianRandomGenerator(new JDKRandomGenerator(42));
        private final double[][] inputMatrix;
        private final ProgressInfo progress = new ProgressInfo();
        private double[][] intermediateResult = null;

        // if perplexity > number of possible neighbors, binary search converges at number of possible neigbors.
        private double targetPerplexity = 20;// FastMath.min(20, dataSet.size() - 1);
        private double[][] initialSolution = null;
        private TSNE tsne;


        SimpleTSneProcessor(double[][] inputMatrix, Context context) {
            super();
            validateInputMatrix(inputMatrix);
            this.inputMatrix = inputMatrix;

            if (context instanceof SimpleTSneContext) {
                this.initialSolution = ((SimpleTSneContext) context).initialSolution;
            }
        }


        // TODO ? log?


        @Override
        public void cancel() {
            log.warn("Cancelling this implementation of t-SNE is not possible!");
        }

        @Override
        protected double[][] process() {

            System.out.println("Processing " + inputMatrix.length + " data items.");

            // TODO -> 255 special for MNIST
            // normalize
            RealMatrix normalizedInput;
            if (true) {
                normalizedInput = Preprocessing.normalize(MatrixUtils.createRealMatrix(inputMatrix));
            } else if (false) {
                normalizedInput = MatrixUtils.createRealMatrix(inputMatrix);
                for (int i = 0; i < normalizedInput.getColumnDimension(); i++) {
                    double mean = StatUtils.mean(normalizedInput.getColumn(i));
                    normalizedInput.setColumn(i, Arrays.stream(normalizedInput.getColumn(i)).map(val -> val - mean).toArray());
                }
            } else {
                normalizedInput = MatrixUtils.createRealMatrix(inputMatrix);
                for (int i = 0; i < normalizedInput.getColumnDimension(); i++) {
                    double min = StatUtils.min(normalizedInput.getColumn(i));
                    double max = StatUtils.max(normalizedInput.getColumn(i));
                    normalizedInput.setColumn(i, Arrays.stream(normalizedInput.getColumn(i)).map(val -> val / 255.0).toArray());
                }
            }

            for (int i = 0; i < normalizedInput.getColumnDimension(); i++) {
                double[][] colsAsRows = normalizedInput.transpose().getData();
                colsAsRows = Arrays.stream(colsAsRows)
                                   .filter(col -> Arrays.stream(col).noneMatch(val -> !Double.isFinite(val)))
                                   .toArray(double[][]::new);
                normalizedInput = MatrixUtils.createRealMatrix(colsAsRows).transpose();
            }

            normalizedInput.walkInOptimizedOrder(new DefaultRealMatrixPreservingVisitor() {
                @Override
                public void visit(int row, int column, double value) {
                    if (!Double.isFinite(value)) {
                        throw new IllegalStateException(
                                "Detected at least one non-finite value (" + value + ") after normalization (row: " + row + " / col: " + column + ")!");
                    }
                }
            });


            //            PCA testPca = new PCA(normalizedInput.getData());
            //            System.out.println(testPca.project(normalizedInput.getData()).length);
            //            System.out.println(Arrays.toString(testPca.getVariance()));
            //            System.out.println(Arrays.toString(testPca.getVarianceProportion()));
            //            System.out.println(Arrays.toString(testPca.getCumulativeVarianceProportion()));
            //            System.exit(0);

            // TODO remove shortcut
            if (true) {
                double[][] project = new PCA(normalizedInput.getData()).setProjection(0.95).project(normalizedInput.getData());
                System.out.println("dims: " + project[0].length);
                boolean smile = true;
                if (smile) {
                    tsne = new InitSmileTSNE(project, new PCA(project).setProjection(2).project(project), 2, targetPerplexity, 200, 1);
                    int maxIter = 1000;
                    for (int iter = 0; iter < maxIter; iter++) {
                        intermediateResult = new double[tsne.getCoordinates().length][];
                        for (int i = 0; i < tsne.getCoordinates().length; i++) {
                            intermediateResult[i] = Arrays.copyOf(tsne.getCoordinates()[i], tsne.getCoordinates()[i].length);
                        }
                        notifyNewIntermediateResult();
                        tsne.learn(1);
                        if ((iter + 1) % 50 == 0) {
                            System.out.println("t-SNE " + (iter + 1) + "/" + maxIter);
                        }
                    }
                    return tsne.getCoordinates();
                } else {
                    // limit precision
                    int p = 6;
                    for (int i = 0; i < project.length; i++) {
                        for (int j = 0; j < project[i].length; j++) {
                            project[i][j] = Math.round(project[i][j] * Math.pow(10, p)) / Math.pow(10, p);
                        }
                    }

                    FastTSne tSne = new FastTSne();
                    double[][] result = tSne.tsne(TSneUtils.buildConfig(project, 2, 30, 20, 1000));
                    return result;
                }
            }
            //
            //            SingularValueDecomposition singularValueDecomposition = new SingularValueDecomposition(normalizedInput);
            //            System.out.println(Arrays.toString(singularValueDecomposition.getSingularValues()));
            //            System.exit(0);


            // TODO: PCA
            System.out.println("PCA-DIM_input: " + inputMatrix[0].length);
            // TODO
            //  java.lang.ArrayIndexOutOfBoundsException: 98
            //	at smile.math.matrix.JMatrix.tql2(JMatrix.java:1734)
            //	at smile.math.matrix.JMatrix.eigen(JMatrix.java:1353)
            //	at smile.projection.PCA.<init>(PCA.java:173)
            //	at smile.projection.PCA.<init>(PCA.java:107)
            PCA pca = new PCA(normalizedInput.getData());
            System.out.println("PCA: " + Arrays.toString(pca.getVarianceProportion()));
            pca.setProjection(0.95);
            System.out.println("PCA: " + Arrays.toString(pca.getVarianceProportion()));
            double[][] pcaResults = pca.project(normalizedInput.getData());
            // TODO (bypass pca):
            //            double[][] pcaResults = normalizedInput.getData();
            System.out.println("PCA-DIM_output: " + pcaResults[0].length);

            // affinities (probability-matrix)
            RealMatrix highDimAffinities = computeHighDimAffinities(MatrixUtils.createRealMatrix(pcaResults));


            // init Solution
            if (initialSolution == null) {
                boolean usePcaForInit = true;
                if (usePcaForInit) {
                    initialSolution = new PCA(normalizedInput.getData()).setProjection(2).project(normalizedInput.getData());
                } else {
                    initialSolution = Stream.generate(() -> DoubleStream.generate(RANDOM_GAUSSIAN::nextNormalizedDouble)
                                                                        .map(val -> val * 0.0001)
                                                                        .limit(targetDimensions)
                                                                        .toArray()).limit(inputMatrix.length).toArray(double[][]::new);
                }
            }

            RealMatrix Y = MatrixUtils.createRealMatrix(initialSolution);


            // gradient descent
            int maxIterations = 1000;


            double eta = 100; // learning rate

            double[] alpha = new double[maxIterations]; // momentum
            Arrays.fill(alpha, 0.5);
            if (maxIterations > 250) {
                Arrays.fill(alpha, 250, maxIterations, 0.8);
            }


            RealMatrix P = highDimAffinities;


            // TODO P-Value-Lying?!
            RealMatrix exaggeratedP = MatrixUtils.createRealMatrix(highDimAffinities.getData());
            exaggeratedP.walkInOptimizedOrder(new DefaultRealMatrixChangingVisitor() {
                @Override
                public double visit(int row, int column, double value) {
                    return value * 4;
                }
            });
            int lieP = 50; //TODO 50
            int lieQ = 0;


            RealMatrix previousY = null;
            RealMatrix prePreviousY = null;

            for (int t = 0; t < maxIterations; t++) {

                if (t % 10 == 0) {
                    System.out.printf("Iteration: %d/%d %n", t, maxIterations);
                }

                RealMatrix squareDistancesY = computeSquaredDistances(Y);

                RealMatrix Q = MatrixUtils.createRealMatrix(studentTProbabilities(squareDistancesY.getData()));

                RealMatrix exaggeratedQ = MatrixUtils.createRealMatrix(Q.getData());
                exaggeratedQ.walkInOptimizedOrder(new DefaultRealMatrixChangingVisitor() {
                    @Override
                    public double visit(int row, int column, double value) {
                        return value * 0.25;
                    }
                });

                List<RealVector> gradients = new ArrayList<>();
                for (int i = 0; i < Y.getRowDimension(); i++) {
                    RealVector gradient = MatrixUtils.createRealVector(new double[targetDimensions]);

                    for (int j = 0; j < Y.getRowDimension(); j++) {
                        gradient = gradient.add(Y.getRowVector(i)
                                                 .subtract(Y.getRowVector(j))
                                                 .mapMultiplyToSelf((t < lieP ? exaggeratedP : P).getEntry(i, j) - (t < lieQ
                                                                                                                    ? exaggeratedQ
                                                                                                                    : Q).getEntry(i, j))
                                                 .mapMultiplyToSelf(1 / (1 + squareDistancesY.getEntry(i, j))));
                    }
                    ;
                    gradient.mapMultiplyToSelf(-4); // TODO gradient pointing in wrong direction, inserted minus to solve.

                    gradients.add(gradient);
                }


                prePreviousY = previousY;
                previousY = Y.copy();
                Y = MatrixUtils.createRealMatrix(Y.getRowDimension(), Y.getColumnDimension());

                for (int i = 0; i < Y.getRowDimension(); i++) {
                    RealVector etaTerm = gradients.get(i).mapMultiply(eta);
                    RealVector alphaTerm = prePreviousY != null
                                           ? previousY.getRowVector(i)
                                                      .subtract(prePreviousY.getRowVector(i))
                                                      .mapMultiplyToSelf(alpha[t])
                                           : MatrixUtils.createRealVector(new double[targetDimensions]);

                    Y.setRowVector(i, previousY.getRowVector(i).add(etaTerm).add(alphaTerm));
                }

                intermediateResult = Y.getData();
                notifyNewIntermediateResult();

            }


            return Y.getData();
        }

        private double[] gaussianConditionalProbabilities(double[] squaredDistancesToI, int givenI, double sigmaI) {

            double inverseSquareSigmaI = 0.5 / (sigmaI * sigmaI);

            double denominator = 0;
            for (int k = 0; k < squaredDistancesToI.length; k++) {
                if (k != givenI) {
                    denominator += FastMath.exp(-squaredDistancesToI[k] * inverseSquareSigmaI);
                }
            }
            double inverseDenominator = 1 / denominator;

            double[] probabilities = new double[squaredDistancesToI.length];
            for (int j = 0; j < probabilities.length; j++) {
                if (j != givenI) {
                    probabilities[j] = FastMath.exp(-squaredDistancesToI[j] * inverseSquareSigmaI) * inverseDenominator;
                } else {
                    probabilities[j] = 0;
                }
            }

            //        System.out.printf("Sum(P) = %f %n", Arrays.stream(probabilities).sum());

            return probabilities;
        }

        private double perplexity(double[] probabilitiesForI) {

            double shannonEntropy = -Arrays.stream(probabilitiesForI).map(p -> p * FastMath.log(2, p)).filter(Double::isFinite).sum();

            return FastMath.pow(2, shannonEntropy);
        }

        private void validateInputMatrix(double[][] inputMatrix) {
            if (inputMatrix == null) {
                throw new NullPointerException("InputMatrix may not be null!");
            } else if (inputMatrix.length == 0) {
                throw new IllegalArgumentException("InputMatrix may not be empty!");
            } else {
                int dim = inputMatrix[0].length;
                if (dim == 0) {
                    throw new IllegalArgumentException("First Row of InputMatrix may not be empty!");
                } else if (Arrays.stream(inputMatrix).filter(row -> row.length != dim).findAny().isPresent()) {
                    throw new IllegalArgumentException("InputMatrix has Rows with differing Dimensionality!");
                }
            }
        }

        private RealMatrix computeHighDimAffinities(RealMatrix inputMatrix) {

            RealMatrix normalizedInput = Preprocessing.normalize(inputMatrix);


            // squared distances
            RealMatrix distances = computeSquaredDistances(normalizedInput);


            // create a new matrix with same dimensions as distance-matrix, as affinities are based on distances.
            RealMatrix probabilityMatrixP = MatrixUtils.createRealMatrix(distances.getRowDimension(), distances.getColumnDimension());

            IntStream.range(0, distances.getRowDimension())./*parallel().*/forEach(i -> {
                double sigma = 1;
                double[] probabilities = gaussianConditionalProbabilities(distances.getRow(i), i, sigma);

                // optimization of perplexity
                int maxSteps = 50;
                double eps = 0.01;

                double perplexity = perplexity(probabilities);
                double diff = perplexity - targetPerplexity; //TODO logU -> log(target)?

                double sigmaMin = Double.NaN, sigmaMax = Double.NaN;

                System.out.printf("i = %d: initial Perplexity(%.3f) = %.3f <=> diff = %.3f %n", i, sigma, perplexity, diff);

                for (int s = 0; s < maxSteps && FastMath.abs(diff) > eps; s++) {

                    if (diff > 0) {
                        sigmaMax = sigma;
                        if (Double.isFinite(sigmaMin)) {
                            sigma = 0.5 * (sigma + sigmaMin);
                        } else {
                            sigma *= 0.5;
                        }
                    } else {
                        sigmaMin = sigma;
                        if (Double.isFinite(sigmaMax)) {
                            sigma = 0.5 * (sigma + sigmaMax);
                        } else {
                            sigma *= 2;
                        }
                    }

                    probabilities = gaussianConditionalProbabilities(distances.getRow(i), i, sigma);
                    perplexity = perplexity(probabilities);
                    diff = perplexity - targetPerplexity; //TODO logU -> log(target)?

                    //                        System.out.printf("... Perplexity(%.3f) = %.3f %n", sigma, perplexity);
                }

                probabilityMatrixP.setRow(i, probabilities);
            });


            // make symmetric!

            int dim = probabilityMatrixP.getRowDimension();
            RealMatrix symmetricProbabilities = MatrixUtils.createRealMatrix(dim, dim);

            double sumP = 0;
            for (int i = 0; i < dim; i++) {
                for (int j = i + 1; j < dim; j++) {
                    double symmP = 0.5 * (probabilityMatrixP.getEntry(i, j) + probabilityMatrixP.getEntry(j, i));
                    symmetricProbabilities.setEntry(i, j, symmP);
                    symmetricProbabilities.setEntry(j, i, symmP);

                    sumP += 2 * symmP;
                }
            }

            // TODO normalization for row or overall (as with t-distr.)?
            //        for (int i = 0; i < symmetricProbabilities.getRowDimension(); i++) {
            //            double deviation = 1 / Arrays.stream(symmetricProbabilities.getRow(i)).sum();
            //            symmetricProbabilities.setRow(i, Arrays.stream(symmetricProbabilities.getRow(i))
            //                                                   .createProcessor(p -> p * deviation)
            //                                                   .toArray());
            //        }
            symmetricProbabilities = symmetricProbabilities.scalarMultiply(1 / sumP);

            // TODO for i!=j ensure !=0? (max(Double.min))
            symmetricProbabilities = symmetricProbabilities.scalarAdd(Double.MIN_VALUE);

            return symmetricProbabilities;

        }

        private RealMatrix computeSquaredDistances(RealMatrix inputMatrix) {
            int numSamples = inputMatrix.getRowDimension();

            // prepare a square-matrix to hold the results
            RealMatrix distances = MatrixUtils.createRealMatrix(numSamples, numSamples);

            // derive squared distance from non-squared distance-measure to be flexible
            DistanceMeasure squaredEuclideanDistance = new EuclideanDistance() {
                @Override
                public double compute(double[] a, double[] b) throws DimensionMismatchException {
                    double result = super.compute(a, b);
                    return result * result;
                }
            };

            // run through all possible combinations of samples and compute their (symmetric) distance.
            IntStream.range(0, numSamples).parallel().forEach(row -> {
                IntStream.range(row + 1, numSamples).parallel().forEach(col -> {
                    double distance = squaredEuclideanDistance.compute(inputMatrix.getRow(row), inputMatrix.getRow(col));
                    distances.setEntry(row, col, distance);
                    distances.setEntry(col, row, distance); // symmetric!
                });
            });
            return distances;
        }

        private double[][] studentTProbabilities(double[][] squaredDistances) {

            double denominator = 0;
            for (int k = 0; k < squaredDistances.length; k++) {
                for (int l = k + 1; l < squaredDistances.length; l++) {
                    denominator += 1 / (1 + squaredDistances[k][l]);
                }
            }
            denominator *= 2; // symmetric!

            double[][] probabilities = new double[squaredDistances.length][squaredDistances.length];
            //                        double sumQ = 0;
            for (int i = 0; i < probabilities.length; i++) {
                for (int j = i + 1; j < probabilities.length; j++) {
                    double q = 1 / (denominator * (1 + squaredDistances[i][j]));
                    probabilities[i][j] = q;
                    probabilities[j][i] = q;
                    //                                                sumQ+=2*q;//symmetric
                }
            }

            // TODO why?
            for (int i = 0; i < probabilities.length; i++) {
                probabilities[i][i] = Double.MIN_VALUE;
            }

            return probabilities;
        }

        @Override
        public Optional<double[][]> getLastIntermediateResult() {
            return Optional.ofNullable(intermediateResult);
        }

    }


}
