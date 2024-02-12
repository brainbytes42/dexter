package de.panbytes.dexter.lib.dimension;


import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.correlation.Covariance;
import smile.feature.extraction.PCA;

import java.util.Arrays;
import java.util.stream.Collectors;

public class PrincipalComponentAnalysis {

    public static void main(String[] args) {

        //create points in a double array
        double[][] pointsArray = new double[][]{new double[]{1.8, 4.2}, new double[]{2.2, 3.8}, new double[]{2.2, 4.0}, new double[]{5.8, 8.0}, new double[]{6.0, 8.0}
                //                new double[] { 7, 4, 3},
                //                new double[] { 4, 1, 8},
                //                new double[] { 6,3,5 },
                //                new double[] {  8,6,1},
                //                new double[] {  8,5,7},
                //                new double[] {  7,2,9},
                //                new double[] {  5,3,3},
                //                new double[] {  9,5,8},
                //                new double[] {  7,4,5},
                //                new double[] { 8,2,2 }
        };

        RealMatrix realMatrix = MatrixUtils.createRealMatrix(pointsArray);

        RealMatrix normalized = new BlockRealMatrix(realMatrix.getRowDimension(), realMatrix.getColumnDimension());
        for (int i = 0; i < realMatrix.getColumnDimension(); i++) {
            normalized.setColumn(i, StatUtils.normalize(realMatrix.getColumn(i)));
        }


        PCA pca = PCA.fit(normalized.getData());
        pca = pca.getProjection(2);
        double[][] project = pca.apply(normalized.getData());
        System.out.println("PCA:");
        System.out.println(Arrays.stream(project).map(Arrays::toString).collect(Collectors.joining(System.lineSeparator())));
        System.out.println("Eigenvectors sorted by Eigenvalues:");
        System.out.println(pca.loadings());
        System.out.println("---------------------------");


        //create real matrix

        //create covariance matrix of points, then find eigen vectors
        //see https://stats.stackexchange.com/questions/2691/making-sense-of-principal-component-analysis-eigenvectors-eigenvalues



        Covariance covariance = new Covariance(normalized);
        RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();

//        System.out.println(covarianceMatrix);

        EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);

        System.out.println("EV1: "+ed.getEigenvector(0));
        System.out.println("EV2: "+ed.getEigenvector(1));
        System.out.println("V: "+ed.getV());
        System.out.println("EVs: "+Arrays.toString(ed.getRealEigenvalues()));

        System.out.println(realMatrix.multiply(MatrixUtils.createColumnRealMatrix(ed.getEigenvector(0).toArray())));
        System.out.println(realMatrix.multiply(MatrixUtils.createColumnRealMatrix(ed.getEigenvector(1).toArray())));
        System.out.println(realMatrix.transpose().preMultiply(ed.getEigenvector(0)));
        System.out.println(realMatrix.transpose().preMultiply(ed.getEigenvector(1)));
        System.out.println(realMatrix.operate(ed.getEigenvector(0)));
        System.out.println(realMatrix.operate(ed.getEigenvector(1)));


    }

    //    public static void main(String[] args) {
    //
    //        List<Row> data = Arrays.asList(
    //                RowFactory.create(Vectors.sparse(5, new int[]{1, 3}, new double[]{1.0, 7.0})),
    //                RowFactory.create(Vectors.dense(2.0, 0.0, 3.0, 4.0, 5.0)),
    //                RowFactory.create(Vectors.dense(4.0, 0.0, 0.0, 6.0, 7.0))
    //        );
    //
    //        StructType schema = new StructType(new StructField[]{
    //                new StructField("features", new VectorUDT(), false, Metadata.empty()),
    //        });
    //
    //        SparkSession spark = SparkSession
    //                .builder()
    //                .appName("Java Spark SQL basic example")
    ////                .config("spark.some.config.option", "some-value")
    //                .getOrCreate();
    //
    //        Dataset<Row> df = spark.createDataFrame(data, schema);
    //
    //        PCAModel pca = new PCA()
    //                .setInputCol("features")
    //                .setOutputCol("pcaFeatures")
    //                .setK(3)
    //                .fit(df);
    //
    //        Dataset<Row> result = pca.transform(df).select("pcaFeatures");
    //        result.show(false);
    //
    //    }

    //    public static void main(String[] args) {
    //        SparkConf conf = new SparkConf().setAppName("PCAExample").setMaster("local");
    //        try (JavaSparkContext sc = new JavaSparkContext(conf)) {
    //            //Create points as Spark Vectors
    //            List<Vector> vectors = Arrays.asList(
    //                    Vectors.dense(-1.0, -1.0 ),
    //                    Vectors.dense( -1.0, 1.0 ),
    //                    Vectors.dense( 1.0, 1.0 ));
    //
    //            //Create Spark MLLib RDD
    //            JavaRDD<Vector> distData = sc.parallelize(vectors);
    //            RDD<Vector> vectorRDD = distData.rdd();
    //
    //            //Execute PCA Projection to 2 dimensions
    //            PCA pca = new PCA(2);
    //            PCAModel pcaModel = pca.fit(vectorRDD);
    //            Matrix matrix = pcaModel.pc();
    //        }
    //    }

}
