package de.panbytes.dexter.lib;

import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.IntStream;

public class Preprocessing {

    private static final Logger log = LoggerFactory.getLogger(Preprocessing.class);

    public static double[][] normalize(double[][] inputMatrix) {

        // use matrix in internal method-parameters already?
        RealMatrix matrix = MatrixUtils.createRealMatrix(inputMatrix);

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

        return matrix.getData();
    }

    public static RealMatrix normalize(RealMatrix inputMatrix) {

        RealMatrix outputMatrix = MatrixUtils.createRealMatrix(inputMatrix.getRowDimension(),
                                                               inputMatrix.getColumnDimension());

        IntStream.range(0, inputMatrix.getColumnDimension()).parallel().forEach(col -> {
            outputMatrix.setColumn(col, StatUtils.normalize(inputMatrix.getColumn(col)));
        });

        return outputMatrix;
    }

    /**
     * normalize and apply PCA
     */
    public static double[][] pca(double[][] inputData, double outputVariancePercent) {

        RealMatrix inputMatrix = MatrixUtils.createRealMatrix(inputData);
        log.trace("InputMatrix PCA: {}x{} (rowxcol)", inputMatrix.getRowDimension(), inputMatrix.getColumnDimension());

        RealMatrix normalizedInput = normalize(inputMatrix);

        //create covariance matrix of points, then find eigen vectors (using SVD, which is used mostly for this)
        //see https://stats.stackexchange.com/questions/2691/making-sense-of-principal-component-analysis-eigenvectors-eigenvalues

        Covariance covariance = new Covariance(normalizedInput);
        RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
        SingularValueDecomposition svd = new SingularValueDecomposition(covarianceMatrix);

        double[] eigenValues = svd.getSingularValues();
        RealMatrix eigenVectors = svd.getV();

        double sumEV = Arrays.stream(eigenValues).sum();

        double sum = 0;
        int lastVectorToUse=0;
        while (lastVectorToUse < eigenValues.length) {
            sum += eigenValues[lastVectorToUse];
            if(sum >= sumEV* outputVariancePercent) break;
            lastVectorToUse++;
        }

        RealMatrix eigenVectorsSelected = eigenVectors.getSubMatrix(0, eigenValues.length-1, 0, lastVectorToUse);

        // https://machinelearningmastery.com/calculate-principal-component-analysis-scratch-python/
        RealMatrix result = eigenVectorsSelected.transpose().multiply(normalizedInput.transpose());
        return result.transpose().getData();
    }
}
