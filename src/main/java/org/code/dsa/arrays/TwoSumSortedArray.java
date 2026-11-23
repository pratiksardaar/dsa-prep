package org.code.dsa.arrays;

public class TwoSumSortedArray {

    public static void twoSumSortedArray(int[] arr, int target){

        int start =0;
        int end = arr.length-1;

        while(start < end) {
            int targetSum = arr[start] + arr[end];
            if( targetSum ==  target) {
                System.out.println(arr[start] + " ," + arr[end]);
                start++;
                end--;
            } else if(targetSum > target) {
                end--;
            } else {
                start++;
            }
        }

    }

    public static void main(String[] args) {
        int arr[] = {0,1,2,3,4,5,6,7};
        twoSumSortedArray(arr, 7);
    }
}
