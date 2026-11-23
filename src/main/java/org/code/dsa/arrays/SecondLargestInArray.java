package org.code.dsa.arrays;

public class SecondLargestInArray {

    public static int findSecondLargestInArray(int[] arr) {
        int largest = arr[0];
        int secondLargest =  arr[0];

        for(int num:  arr) {
            if (num > largest) {
                secondLargest = largest;
                largest = num;
            } else if(num > secondLargest && num != largest) {
                secondLargest = num;
            }
        }
        return secondLargest;
    }

    public static void main(String[] args) {
        System.out.println(findSecondLargestInArray(new int[]{100, 105,107,1000}));
    }
}
