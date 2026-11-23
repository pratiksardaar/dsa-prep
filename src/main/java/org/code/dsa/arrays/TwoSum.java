package org.code.dsa.arrays;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class TwoSum {

    public static void findTwoSum(int[] arr, int target) {
        Set<Integer> seen =  new HashSet<>();
        Set<List<Integer>> uniquePairs =  new LinkedHashSet<>();
        for(int num : arr) {
            int complement =  target- num;
            if(seen.contains(complement)) {
                uniquePairs.add(List.of(Math.min(num, complement), Math.max(num, complement)));
                System.out.println(num + " ," + complement);

            }
            seen.add(num);
        }
        System.out.println(uniquePairs);
    }

    public static void main(String[] args){
        int[] arr = {0,1,1,2,3,4,5,6, 6,7};
        findTwoSum(arr, 7);

    }

}
