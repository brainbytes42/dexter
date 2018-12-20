/**
 * 
 */
package de.panbytes.dexter.lib.dimension;

import com.jujutsu.tsne.FastTSne;
import com.jujutsu.utils.TSneUtils;
import org.apache.commons.math3.stat.descriptive.MultivariateSummaryStatistics;
import org.apache.commons.math3.util.MathArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Fabian Schink
 *
 */
@Deprecated
public class TSne {


  /**
   * The Logger for TSne.
   */
  private static final Logger log = LoggerFactory.getLogger(TSne.class);

  private final com.jujutsu.tsne.TSne tsne = new FastTSne();

  // TODO debug
  private double[][] tsneDummy(final double[][] xInput) {
    final double[][] X = xInput.clone();
    for (int row = 0; row < X.length; row++) {
      X[row] = new double[] {X[row][0], X[row][1]};
    }
    log.info("Dummy-t-SNE returned {} rows.", X.length);
    return X;
  }

  public double[][] tsne(final double[][] X, final int k, final int initial_dims, final double perplexity) {
    // return tsneDummy(X);
    return tsne.tsne(TSneUtils.buildConfig(X, k, initial_dims, perplexity,1000));
  }

  public double[][] tsne(final double[][] X, final int k, final int initial_dims, final double perplexity,
      final int maxIterations) {
    // return tsneDummy(X);
    return tsne.tsne(TSneUtils.buildConfig(X, k, initial_dims, perplexity, maxIterations));
  }

  public static double[][] normalizeMatrixColumns(final double[][] matrix) {

    final MultivariateSummaryStatistics stats =
        new MultivariateSummaryStatistics(matrix[0].length, false);
    for (int i = 0; i < matrix.length; i++) {
      MathArrays.checkNotNaN(matrix[i]);
      stats.addValue(matrix[i]);
    }

    final double[][] norm = new double[matrix.length][matrix[0].length];
    for (int i = 0; i < norm.length; i++) {
      for (int j = 0; j < norm[i].length; j++) {
        norm[i][j] = (matrix[i][j] - stats.getMean()[j]) / stats.getStandardDeviation()[j];
      }
    }
    return norm;

  }

}
