package de.panbytes.dexter.util;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ListUtil {

    /**
     * Partitions the list into the given number of folds, providing an as evenly as possible distribution of partition sizes.
     *
     * @param inputList the list to be partitioned
     * @param folds     the number of partitions
     * @param <T>       the element's type
     * @return a list of partitions, which are filled as evenly as possible.
     * If list's size isn't divisible by number of partitions, the first partitions will contain one extra element
     * - so the size of all partitions differs at most by 1 element.
     * The list of partitions and each sublist are unmodifiable.
     * @throws NullPointerException if input list is null
     * @throws IllegalArgumentException if number of folds is <= 0
     * @throws UnsupportedOperationException when attempting to change list of partitions or any partition
     */
    public static <T> List<List<T>> partition(List<T> inputList, int folds) {

        Objects.requireNonNull(inputList, "List to be partitioned may not be null!");
        if (folds <= 0) throw new IllegalArgumentException("Number of Folds expected positive, but was " + folds);

        List<T> list = Collections.unmodifiableList(new ArrayList<>(inputList));

        int elementsPerFold = list.size() / folds;
        return IntStream.range(0, folds).mapToObj(fold -> {
            final boolean foldWithAdditionalElement = fold < list.size() % folds;
            final int fromIndex = fold * elementsPerFold + (foldWithAdditionalElement ? fold : list.size() % folds);
            final int toIndex = fromIndex + elementsPerFold + (foldWithAdditionalElement ? 1 : 0);
            return list.subList(fromIndex, toIndex);
        }).collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

    }

}
