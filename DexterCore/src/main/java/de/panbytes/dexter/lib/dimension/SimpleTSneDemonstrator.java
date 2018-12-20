package de.panbytes.dexter.lib.dimension;

import com.google.common.collect.Multimap;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.NormalizedRandomGenerator;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Deprecated
public class SimpleTSneDemonstrator {

    //TODO seeded!
    private static final NormalizedRandomGenerator RANDOM_GAUSSIAN = new GaussianRandomGenerator(
            new JDKRandomGenerator(42));
    private static boolean showGui = true;

    public static void main(String[] args) {

        int targetDimensions = 2;

        Map<RealVector, String> hypercubeData = new DataGenerator(RANDOM_GAUSSIAN).generateHypercubeData(4, 30)
                                                                                  .entrySet()
                                                                                  .stream()
                                                                                  .collect(Collectors.toMap(
                                                                                          entry -> MatrixUtils.createRealVector(
                                                                                                  entry.getKey()),
                                                                                          Map.Entry::getValue));

        List<RealVector> hypercubeVectors = new ArrayList<>(hypercubeData.keySet());
        double[][] hypercubeMatrix = hypercubeVectors.stream().map(v -> v.toArray()).toArray(double[][]::new);


        //        double[][] mappedHypercube = new SimpleTSne().map(hypercubeMatrix, 2);
        DimensionMapping.MappingProcessor mappingProcessor = new StochasticNeigborEmbedding().mapAsync(hypercubeMatrix,
                                                                                                       DimensionMapping.Context.EMPTY);

        System.out.println("mapping..."+mappingProcessor.getCompletableFuture().isDone());

        while (!mappingProcessor.getCompletableFuture().isDone()) {

            System.out.println(mappingProcessor.getLastIntermediateResult().isPresent());

            mappingProcessor.getLastIntermediateResult().ifPresent(intermediateMapping -> {

                Map<RealVector, String> mappedHypercubeWithLabels = new HashMap<>();
                for (int i = 0; i < intermediateMapping.length; i++) {
                    mappedHypercubeWithLabels.put(MatrixUtils.createRealVector(intermediateMapping[i]),
                                                  hypercubeData.get(hypercubeVectors.get(i)));
                }

                showVisualization(mappedHypercubeWithLabels);
            });

            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }

        double[][] mappedHypercube = mappingProcessor.getResult();


        Map<RealVector, String> mappedHypercubeWithLabels = new HashMap<>();
        for (int i = 0; i < mappedHypercube.length; i++) {
            mappedHypercubeWithLabels.put(MatrixUtils.createRealVector(mappedHypercube[i]),
                                          hypercubeData.get(hypercubeVectors.get(i)));
        }

        showVisualization(mappedHypercubeWithLabels);


        //        showVisualization(hypercubeData);
        //        try {
        //            Thread.sleep(10000);
        //        } catch (InterruptedException e) {
        //            e.printStackTrace();
        //        }
        //
        //        System.out.println(hypercubeData.entrySet().stream().map(e -> e.getValue()).collect(Collectors.toList()));
        //
        //        performTSne(targetDimensions, hypercubeData, null, true);

    }

    public static List<RealVector> performTSne(int targetDimensions, Map<RealVector, String> inputData) {
        return performTSne(targetDimensions, inputData, null, false);
    }

    public static List<RealVector> performTSne(int targetDimensions,
                                               Map<RealVector, String> inputData,
                                               List<RealVector> initSolution,
                                               boolean showGui) {

        SimpleTSneDemonstrator.showGui = showGui;


        List<RealVector> dataSet = new ArrayList<>(inputData.keySet());


        printMatrixForMatlab(vectorListToMatrix(dataSet), "X");

        //        printMatrix(vectorListToMatrix(dataSet), "pre-normalized");

        dataSet = normalize(dataSet);
        //        dataSet = normalizeOriginal(dataSet);

        printMatrix(vectorListToMatrix(dataSet), "normalized");

        // TODO ? log?
        // if perplexity > number of possible neighbors, binary search converges at number of possible neigbors.
        final double targetPerplexity = 20;// FastMath.min(20, dataSet.size() - 1);

        // Distance Matrix
        RealMatrix squareDistancesX = calculateSquareDistanceMatrix(dataSet);
        //        printMatrix(squareDistancesX, "squareDistancesX");


        RealMatrix probabilityMatrixP = MatrixUtils.createRealMatrix(dataSet.size(), dataSet.size());
        IntStream.range(0, squareDistancesX.getRowDimension())./*parallel().*/forEach(i -> {
            double sigma = 1;
            double[] probabilities = gaussianConditionalProbabilities(squareDistancesX.getRow(i), i, sigma);

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

                probabilities = gaussianConditionalProbabilities(squareDistancesX.getRow(i), i, sigma);
                perplexity = perplexity(probabilities);
                diff = perplexity - targetPerplexity; //TODO logU -> log(target)?

                //                        System.out.printf("... Perplexity(%.3f) = %.3f %n", sigma, perplexity);
            }

            probabilityMatrixP.setRow(i, probabilities);
        });

        //                printMatrix(probabilityMatrixP, "probabilityMatrixP");


        RealMatrix symmetrizedProbabilities = symmetrizedConditionalProbabilities(probabilityMatrixP);
        printMatrix(symmetrizedProbabilities, "symmetricProbabilities", true);


        // init Solution
        // TODO *0.0001 necessary?
        List<RealVector> Y;
        if (initSolution == null) {
            Y = Stream.generate(() -> {
                return MatrixUtils.createRealVector(DoubleStream.generate(RANDOM_GAUSSIAN::nextNormalizedDouble)
                                                                .map(val -> val * 0.0001)
                                                                .limit(targetDimensions)
                                                                .toArray());
            }).limit(dataSet.size()).collect(Collectors.toList());
        } else {
            Y = initSolution;
        }


        printMatrixForMatlab(vectorListToMatrix(Y), "Y_init");
        printMatrix(vectorListToMatrix(Y), "Y_init", true);


        Map<RealVector, String> labelsY = new HashMap<>();
        List<Map.Entry<RealVector, String>> mapping = new ArrayList<>(inputData.entrySet());
        for (int i = 0; i < Y.size(); i++) {
            labelsY.put(Y.get(i), mapping.get(i).getValue());
        }

        if (Visualization.instance == null) {
            showVisualization(labelsY);
        } else {
            Visualization.instance.setData(labelsY);
        }

        List<RealVector> previousY = null;
        List<RealVector> prePreviousY = null;

        // gradient descent
        int maxIterations = 150;

        // TODO P-Value-Lying?!


        double eta = 100; // learning rate
        double[] alpha = new double[maxIterations]; // momentum
        Arrays.fill(alpha, 0.5);
        if (maxIterations > 250) {
            Arrays.fill(alpha, 250, maxIterations, 0.8);
        }

        RealMatrix P = symmetrizedProbabilities;
        RealMatrix exaggeratedP = MatrixUtils.createRealMatrix(symmetrizedProbabilities.getData());
        exaggeratedP.walkInOptimizedOrder(new DefaultRealMatrixChangingVisitor() {
            @Override
            public double visit(int row, int column, double value) {
                return value * 4;
            }
        });
        int lieP = 50;
        int lieQ = 0;


        for (int t = 0; t < maxIterations; t++) {

            // TODO remove!
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            RealMatrix squareDistancesY = calculateSquareDistanceMatrix(Y);
            //                        printMatrix(squareDistancesY, "squareDistancesY(" + t + ")", true);

            RealMatrix Q = MatrixUtils.createRealMatrix(studentTProbabilities(squareDistancesY.getData()));
            //                        printMatrix(Q, "Q(" + t + ")",true);

            RealMatrix exaggeratedQ = MatrixUtils.createRealMatrix(Q.getData());
            exaggeratedQ.walkInOptimizedOrder(new DefaultRealMatrixChangingVisitor() {
                @Override
                public double visit(int row, int column, double value) {
                    return value * 0.25;
                }
            });

            RealVector[] gradY = new RealVector[dataSet.size()];
            for (int i = 0; i < dataSet.size(); i++) {
                gradY[i] = MatrixUtils.createRealVector(new double[targetDimensions]);
                for (int j = 0; j < dataSet.size(); j++) {
                    gradY[i] = gradY[i].add(Y.get(i)
                                             .subtract(Y.get(j))
                                             .mapMultiplyToSelf((t < lieP ? exaggeratedP : P).getEntry(i, j) - (t < lieQ
                                                                                                                ? exaggeratedQ
                                                                                                                : Q).getEntry(
                                                     i, j))
                                             .mapMultiplyToSelf(1 / (1 + squareDistancesY.getEntry(i, j))));
                }
                gradY[i].mapMultiplyToSelf(-4); // TODO gradient pointing in wrong direction, inserted minus to solve.
            }

            //            System.out.println("gradY("+t+") = "+Arrays.toString(gradY));

            prePreviousY = previousY;
            previousY = new ArrayList<>(Y);

            for (int i = 0; i < Y.size(); i++) {
                RealVector etaTerm = gradY[i].mapMultiply(eta);
                RealVector alphaTerm = prePreviousY != null
                                       ? previousY.get(i)
                                                  .subtract(prePreviousY.get(i))
                                                  .mapMultiplyToSelf(alpha[t])
                                       : MatrixUtils.createRealVector(new double[targetDimensions]);


                Y.set(i, previousY.get(i).add(etaTerm).add(alphaTerm));

                if (i < 2) {
                    //                    System.out.println("~~~");
                    //                    System.out.println("i = " + i);
                    //                    System.out.println("Y_"+i+"(t-2) = " + prePreviousY);
                    //                    System.out.println("Y_"+i+"(t-1) = " + previousY);
                    //                    System.out.println("etaTerm = " + etaTerm);
                    //                    System.out.println("alphaTerm = " + alphaTerm);
                    //                    System.out.println("Y_"+i+"("+t+") = " + Y.get(i));
                    //                    System.out.println("~~~");
                }
            }


            if (t % 1 == 0) {
                System.out.printf("t = %d %n", t);

                labelsY.clear();
                for (int i = 0; i < Y.size(); i++) {
                    labelsY.put(Y.get(i), mapping.get(i).getValue());
                }
                if (Visualization.instance != null) {
                    //                Visualization.instance.addData(labelsY);
                    Visualization.instance.setData(labelsY);
                }
            }

        }

        return Y;
    }

    // ok
    private static List<RealVector> normalize(List<RealVector> dataSet) {

        RealMatrix matrix = vectorListToMatrix(dataSet);

        double[] mean = new double[matrix.getColumnDimension()];
        double[] stdDev = new double[matrix.getColumnDimension()];
        for (int col = 0; col < matrix.getColumnDimension(); col++) {
            mean[col] = Arrays.stream(matrix.getColumn(col)).sum() / matrix.getRowDimension();
            stdDev[col] = new StandardDeviation().evaluate(matrix.getColumn(col), mean[col]);
        }

        RealVector meanVec = MatrixUtils.createRealVector(mean);
        RealVector stdDevVec = MatrixUtils.createRealVector(stdDev);
        for (int row = 0; row < matrix.getRowDimension(); row++) {
            matrix.setRowVector(row, matrix.getRowVector(row).subtract(meanVec).ebeDivide(stdDevVec));
        }

        return Arrays.stream(matrix.getData()).map(MatrixUtils::createRealVector).collect(Collectors.toList());
    }

    private static List<RealVector> normalizeOriginal(List<RealVector> dataSet) {
        DoubleSummaryStatistics summary = dataSet.parallelStream()
                                                 .flatMapToDouble(v -> Arrays.stream(v.toArray()))
                                                 .summaryStatistics();
        RealMatrix normalizedMatrix = vectorListToMatrix(dataSet).scalarAdd(-summary.getMin())
                                                                 .scalarMultiply(
                                                                         1 / (summary.getMax() - summary.getMin()));
        for (int i = 0; i < normalizedMatrix.getColumnDimension(); i++) {
            summary = Arrays.stream(normalizedMatrix.getColumn(i)).summaryStatistics();
            normalizedMatrix.setColumnVector(i, normalizedMatrix.getColumnVector(i)
                                                                .mapSubtractToSelf(summary.getAverage()));
        }

        return Arrays.stream(normalizedMatrix.getData())
                     .map(MatrixUtils::createRealVector)
                     .collect(Collectors.toList());
    }

    private static RealMatrix vectorListToMatrix(List<RealVector> dataSet) {
        return MatrixUtils.createRealMatrix(dataSet.stream().map(RealVector::toArray).toArray(double[][]::new));
    }

    private static void printMatrixForMatlab(RealMatrix matrix, String varName) {
        NumberFormat numberFormat = new DecimalFormat("0.####E0", DecimalFormatSymbols.getInstance(Locale.US));
        System.out.println(
                varName + " = " + new RealMatrixFormat("[", " ];", " ", "", ";", " ", numberFormat).format(matrix));
    }

    private static RealMatrix symmetrizedConditionalProbabilities(RealMatrix conditionalProbabilities) {

        int dim = conditionalProbabilities.getRowDimension();
        RealMatrix symmetricProbabilities = MatrixUtils.createRealMatrix(dim, dim);

        double sumP = 0;
        for (int i = 0; i < dim; i++) {
            for (int j = i + 1; j < dim; j++) {
                double symmP = 0.5 * (conditionalProbabilities.getEntry(i, j) + conditionalProbabilities.getEntry(j,
                                                                                                                  i));
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

    private static void printMatrix(RealMatrix distancesOriginal, String title) {
        printMatrix(distancesOriginal, title, false);
    }

    private static void printMatrix(RealMatrix distancesOriginal, String title, boolean scientificNotation) {

        System.out.println("\n[" + title + ":]");

        NumberFormat numberFormat = scientificNotation ? new DecimalFormat("0.####E0") : NumberFormat.getInstance();
        if (numberFormat instanceof DecimalFormat) {
            ((DecimalFormat) numberFormat).setMinimumFractionDigits(7);
            ((DecimalFormat) numberFormat).setMaximumFractionDigits(9);
        }
        System.out.println(new RealMatrixFormat(" -------\n", " -------", "", "\n", "", "  ", numberFormat).format(
                distancesOriginal));
    }

    private static void showVisualization(Map<RealVector, String> labeledData) {

        if (!showGui) {
            return;
        }

        new Thread(() -> Visualization.launch(Visualization.class)).start();

        try {
            synchronized (Visualization.initLock) {
                while (Visualization.instance == null) {
                    Visualization.initLock.wait();
                }
                if (Visualization.instance != null) {
                    Visualization.instance.setData(labeledData);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static RealMatrix calculateSquareDistanceMatrix(List<RealVector> dataSet) {

        //        System.err.println("Calculating distance...");

        RealMatrix squareDistances = MatrixUtils.createRealMatrix(dataSet.size(), dataSet.size());

        IntStream.range(0, dataSet.size()).parallel().forEach(row -> {
            double[] rowData = new double[dataSet.size()];
            for (int col = 0; col < rowData.length; col++) {
                double distance = dataSet.get(row).getDistance(dataSet.get(col));
                rowData[col] = distance * distance;
            }
            squareDistances.setRow(row, rowData);
        });

        return squareDistances;
    }

    private static double calculateConditionalProbabilityForPickingNeighbor(List<RealVector> dataSet,
                                                                            RealVector xi,
                                                                            RealVector xj,
                                                                            double sigmaI) {

        double distIJ = xi.getDistance(xj); // TODO use matrix

        double numerator = FastMath.exp(-distIJ * distIJ / (2 * sigmaI * sigmaI));

        double denominator = dataSet.parallelStream().filter(v -> v != xi).mapToDouble(xk -> {
            double distIK = xi.getDistance(xk);
            return FastMath.exp(-distIK * distIK / (2 * sigmaI * sigmaI));
        }).sum();

        return numerator / denominator;
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

    private static double[][] studentTProbabilities(double[][] squaredDistances) {

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

        //        System.out.println("sumQ = "+sumQ);
        //
        //                double invSumP=1/sumQ; // inverse, symmetric
        //                for (int i = 0; i <probabilities.length; i++) {
        //                    for (int j = i+1; j < probabilities.length; j++) {
        //                        probabilities[i][j] *= invSumP;
        //                        probabilities[j][i] *= invSumP;
        //                    }
        //                }

        return probabilities;
    }

    private static double perplexity(double[] probabilitiesForI) {

        double shannonEntropy = -Arrays.stream(probabilitiesForI)
                                       .map(p -> p * FastMath.log(2, p))
                                       .filter(Double::isFinite)
                                       .sum();

        return FastMath.pow(2, shannonEntropy);
    }

    private static double calculateShannonEntropy(double[] probabilities_pji_overj) {
        return -Arrays.stream(probabilities_pji_overj).map(pji -> pji * FastMath.log(2, pji)).sum();
    }


    public static class Visualization extends Application {

        private static final Object initLock = new Object();
        private static boolean enabled = false;
        private static volatile Visualization instance;
        private final BorderPane root = new BorderPane();
        private final ScatterChart<Number, Number> chart = new ScatterChart<>(new NumberAxis(), new NumberAxis());
        private Multimap<String, RealVector> labels;

        private List<XYChart.Series<Number, Number>> seriesList = new ArrayList<>();
        private Map<String, XYChart.Series<Number, Number>> label2series = new HashMap<>();

        @Override
        public void start(Stage primaryStage) throws Exception {
            if (!SimpleTSneDemonstrator.showGui) {
                return;
            }
            synchronized (initLock) {
                instance = this;
                initLock.notifyAll();
            }

            root.centerProperty().set(chart);

            chart.setAnimated(false);

            primaryStage.setScene(new Scene(root, 600, 600));
            primaryStage.show();
        }

        public void setData(Map<RealVector, String> labeledData) {
            if (!SimpleTSneDemonstrator.showGui) {
                return;
            }
            Platform.runLater(() -> {
                chart.getData().forEach(series -> series.getData().clear());

                addData(labeledData);
            });
        }

        public void addData(Map<RealVector, String> labeledData) {
            if (!SimpleTSneDemonstrator.showGui) {
                return;
            }
            Map<RealVector, String> labeledDataCopy = new LinkedHashMap<>(labeledData);
            Platform.runLater(() -> {
                labeledDataCopy.forEach((vector, label) -> {
                    XYChart.Series<Number, Number> series = label2series.get(label);
                    boolean newSeries = false;
                    if (series == null) {
                        newSeries = true;

                        series = new XYChart.Series<>();
                        series.setName(label);

                        seriesList.add(series);
                        label2series.put(label, series);
                    }
                    series.getData().add(new XYChart.Data<Number, Number>(vector.getEntry(0), vector.getEntry(1)));

                    if (newSeries) {
                        chart.getData().add(series);
                    }
                });
                //                chart.getData().addAll(seriesList);
            });
        }

    }
}
