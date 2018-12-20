package de.panbytes.dexter.lib.dimension;

import smile.manifold.TSNE;

import java.lang.reflect.Field;

class InitSmileTSNE extends TSNE {
    public InitSmileTSNE(double[][] X, double[][] initX, int d, double perplexity, double eta, int iterations) {
        super(X,d,perplexity,eta,iterations);
        try {
            Field coordinatesField = this.getClass().getSuperclass().getDeclaredField("coordinates");
            System.out.println(coordinatesField);
            coordinatesField.setAccessible(true);
            coordinatesField.set(this,initX);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
