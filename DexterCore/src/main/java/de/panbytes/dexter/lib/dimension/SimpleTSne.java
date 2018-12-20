package de.panbytes.dexter.lib.dimension;

import de.panbytes.dexter.lib.Preprocessing;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.DefaultRealMatrixChangingVisitor;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.NormalizedRandomGenerator;
import org.apache.commons.math3.util.FastMath;
import smile.projection.PCA;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Deprecated
public class SimpleTSne {

    //TODO seeded!
    private static final NormalizedRandomGenerator RANDOM_GAUSSIAN = new GaussianRandomGenerator(
            new JDKRandomGenerator(42));

    // TODO ? log?
    // if perplexity > number of possible neighbors, binary search converges at number of possible neigbors.

    private double targetPerplexity = 20;// FastMath.min(20, dataSet.size() - 1);


    public double[][] map(double[][] inputMatrix, int targetDimensions) {

        // normalize
        RealMatrix normalizedInput = Preprocessing.normalize(MatrixUtils.createRealMatrix(inputMatrix));

        // TODO: PCA
        System.out.println("PCA-DIM_input: "+inputMatrix[0].length);
        PCA pca = new PCA(normalizedInput.getData());
        System.out.println("PCA: " + Arrays.toString(pca.getVarianceProportion()));
        pca.setProjection(2);
        System.out.println("PCA: " + Arrays.toString(pca.getVarianceProportion()));
        double[][] pcaResults = pca.project(normalizedInput.getData());
        System.out.println("PCA-DIM_output: "+pcaResults[0].length);

        // affinities (probability-matrix)
        RealMatrix highDimAffinities = computeHighDimAffinities(MatrixUtils.createRealMatrix(pcaResults));





        // init Solution
        double[][] initialSolution = null;

        if (initialSolution == null) {
            initialSolution = Stream.generate(() -> DoubleStream.generate(RANDOM_GAUSSIAN::nextNormalizedDouble)
                                                                .map(val -> val * 0.0001)
                                                                .limit(targetDimensions)
                                                                .toArray())
                                    .limit(inputMatrix.length)
                                    .toArray(double[][]::new);
        }

        RealMatrix Y = MatrixUtils.createRealMatrix(initialSolution);







        // gradient descent
        int maxIterations = 150;




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
        int lieP = 50;
        int lieQ = 0;


        RealMatrix previousY = null;
        RealMatrix prePreviousY = null;

        for (int t = 0; t < maxIterations; t++) {

            if(t%10==0)
                System.out.printf("Iteration: %d/%d %n",t,maxIterations);

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
                    gradient = gradient.add(Y.getRowVector(i).subtract(Y.getRowVector(j))
                                             .mapMultiplyToSelf((t < lieP ? exaggeratedP : P).getEntry(i, j) - (t < lieQ
                                                                                                                ? exaggeratedQ
                                                                                                                : Q).getEntry(
                                                     i, j))
                                             .mapMultiplyToSelf(1 / (1 + squareDistancesY.getEntry(i, j))));
                };
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

        }


        return Y.getData();
    }

    private static double[] gaussianConditionalProbabilities(double[] squaredDistancesToI, int givenI, double sigmaI) {

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

    private static double perplexity(double[] probabilitiesForI) {

        double shannonEntropy = -Arrays.stream(probabilitiesForI)
                                       .map(p -> p * FastMath.log(2, p))
                                       .filter(Double::isFinite)
                                       .sum();

        return FastMath.pow(2, shannonEntropy);
    }

    private RealMatrix computeHighDimAffinities(RealMatrix inputMatrix) {

        RealMatrix normalizedInput = Preprocessing.normalize(inputMatrix);


        // squared distances
        RealMatrix distances = computeSquaredDistances(normalizedInput);


        // create a new matrix with same dimensions as distance-matrix, as affinities are based on distances.
        RealMatrix probabilityMatrixP = MatrixUtils.createRealMatrix(distances.getRowDimension(),
                                                                     distances.getColumnDimension());

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
                double distance = squaredEuclideanDistance.compute(inputMatrix.getRow(row),
                                                                   inputMatrix.getRow(col));
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
}
