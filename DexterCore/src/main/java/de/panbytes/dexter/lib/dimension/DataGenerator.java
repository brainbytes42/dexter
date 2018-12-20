package de.panbytes.dexter.lib.dimension;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.NormalizedRandomGenerator;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DataGenerator {

    public final NormalizedRandomGenerator RANDOM_GAUSSIAN;

    public DataGenerator() {
        this.RANDOM_GAUSSIAN = new GaussianRandomGenerator(new JDKRandomGenerator());
    }

    public DataGenerator(NormalizedRandomGenerator randomGaussian) {
        this.RANDOM_GAUSSIAN = randomGaussian;
    }

    public static void main(String[] args) {
//        new DataGenerator().generateHypercubeData(3, 2);
//        main2(null);


        Double[] d = new Double[]{4.,-2.,5.,-1.};
        test(d);


    }

    private static void test(Double[] d) {
        Arrays.stream(d).forEach(System.out::println);
    }

    public Map<double[], String> generateHypercubeData(int dimensions, int pointsPerCluster) {

        int numBubbles = (int) FastMath.pow(2, dimensions);

        Map<double[], String> data = new HashMap<>();

        double scaleGaussian = 0.1; //0.1
        System.out.println(IntStream.range(0, numBubbles)
                                    .mapToObj(Integer::toBinaryString)
                                    .map(binaryString -> new StringBuilder(binaryString).insert(0, Stream.generate(
                                            () -> "0")
                                                                                                         .limit(dimensions - binaryString
                                                                                                                 .length())
                                                                                                         .collect(
                                                                                                                 Collectors
                                                                                                                         .joining()))
                                                                                        .toString())
                                    .map(binaryString -> Arrays.stream(binaryString.split(""))
                                                               .mapToInt(Integer::parseInt)
                                                               .toArray())
                                    .flatMap(vertexCoord -> Stream.generate(() -> Arrays.stream(vertexCoord)
                                                                                        .mapToDouble(
                                                                                                dim -> dim + scaleGaussian * RANDOM_GAUSSIAN
                                                                                                        .nextNormalizedDouble())
                                                                                        .toArray())
                                                                  .limit(pointsPerCluster)
                                                                  .collect(Collectors.toMap(generated -> generated,
                                                                                            generated -> Arrays.toString(
                                                                                                    vertexCoord)))
                                                                  .entrySet()
                                                                  .stream())
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        for (int i = 0; i < numBubbles; i++) {
            String binaryString = Integer.toBinaryString(i);
            binaryString = StringUtils.leftPad(binaryString, dimensions, '0');
            int[] vertexCoord = binaryString.chars().map(c -> c - '0').toArray();

            //            System.out.println(Arrays.toString(ints));

            for (int j = 0; j < pointsPerCluster; j++) {
                double[] point = Arrays.stream(vertexCoord).mapToDouble(dim -> {
                    return dim + scaleGaussian * RANDOM_GAUSSIAN.nextNormalizedDouble();
                }).toArray();
                data.put(point, Arrays.toString(vertexCoord));

                //                System.out.println("`-> "+Arrays.toString(point));
            }
        }

        return data;
    }

    public static void main2(String[] args) {

        for (int inputNumber = 0; inputNumber < 16; inputNumber++) {

            int outputBitsLength = 7;
            int[] bits = new int[outputBitsLength];
            for (int i = 0; i < bits.length; i++) {
                int currentBitMarker = 1 << i;
                boolean currentBitSet = (inputNumber & currentBitMarker) != 0;
                bits[i] = currentBitSet?1:0;
            }

            System.out.println(inputNumber + " = " + Arrays.toString(bits));

        }
    }
}
