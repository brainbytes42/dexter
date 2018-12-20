package de.panbytes.dexter.lib;

import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import java.util.Arrays;
import java.util.stream.IntStream;

public class Preprocessing {

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

    @Deprecated
    public static double[][] pca(double[][] inputMatrix) {

        RealMatrix realMatrix = MatrixUtils.createRealMatrix(inputMatrix);

        //create covariance matrix of points, then find eigen vectors
        //see https://stats.stackexchange.com/questions/2691/making-sense-of-principal-component-analysis-eigenvectors-eigenvalues

        Covariance covariance = new Covariance(realMatrix);
        RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
        EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);

        double eigenvaluesSum = 0;
        for (int i = 0; i < ed.getRealEigenvalues().length; i++) {
            eigenvaluesSum+=ed.getRealEigenvalue(i);
        }
        double[] eigenvaluesAggregatingSums = new double[ed.getRealEigenvalues().length];


        //        ed.
        return inputMatrix; //TODO
    }
}
