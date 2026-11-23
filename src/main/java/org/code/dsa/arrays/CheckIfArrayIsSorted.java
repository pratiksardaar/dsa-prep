package org.code.dsa.arrays;

public class CheckIfArrayIsSorted {

    public static boolean checkIfArrayIsSorted(int[] arr) {

        for(int i=0; i<arr.length-1;i++) {
            if(arr[i+1] < arr[i]) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println(checkIfArrayIsSorted(new int[]{0,1,2,3,4,5,6}));
    }
}
