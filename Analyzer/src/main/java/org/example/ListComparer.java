package org.example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListComparer {

    public static boolean haveSameElements(List<?> list1, List<?> list2) {
        if (list1.size() != list2.size()) return false;

        Map<Object, Integer> freq1 = getFrequencyMap(list1);
        Map<Object, Integer> freq2 = getFrequencyMap(list2);

        return freq1.equals(freq2);
    }

    private static Map<Object, Integer> getFrequencyMap(List<?> list) {
        Map<Object, Integer> freqMap = new HashMap<>();
        for (Object item : list) {
            freqMap.put(item, freqMap.getOrDefault(item, 0) + 1);
        }
        return freqMap;
    }
    public static boolean haveSameOrder(List<?> list1, List<?> list2) {
        return list1.equals(list2);
    }
}
