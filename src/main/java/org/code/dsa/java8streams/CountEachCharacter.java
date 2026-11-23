package org.code.dsa.java8streams;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CountEachCharacter {

    public static void countEachCharacter(String str) {
        Map<Character, Integer> countMap =  new HashMap<>();

        for (char c: str.toCharArray()) {
            countMap.put(c, countMap.getOrDefault(c, 0) +1);
        }
        //System.out.println(countMap);

        // using Streams

        Map<Character, Long> countMapV2 = str.chars().mapToObj(c -> (char) c).
                collect(Collectors.groupingBy(c -> c, LinkedHashMap::new, Collectors.counting()));
        System.out.println(countMapV2);
    }

    public static void main(String[] args) {
        countEachCharacter("banana");
    }
}
