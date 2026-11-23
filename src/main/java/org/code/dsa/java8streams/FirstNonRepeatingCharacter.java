package org.code.dsa.java8streams;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;

public class FirstNonRepeatingCharacter {

    public static Character findFirstNonRepeatingCharacter(String str) {

        char first = str.chars().mapToObj(c -> (char) c).collect(Collectors.groupingBy(c -> c , LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().filter(e ->  e.getValue() == 1)
                .map(e -> e.getKey()).findFirst().orElse('_');
        return  first;

    }

    public static void main(String[] args) {
        System.out.println(findFirstNonRepeatingCharacter("swiss"));
    }
}
