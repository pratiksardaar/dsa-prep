package org.code.dsa.arrays;

public class isPrime {

    public static boolean isPrime(int n) {
        int count = 0;
        if( n == 1) return true;
        for (int i = 1; i <= n; i++) {
            if(n % i == 0) count++;
        }
        if(count == 2) return true;
        return false;
    }

    public static void main(String[] args) {
        int[] arr = {1,2,3,4,5,6,7,8,9,10};
        for(int num:arr) {
            System.out.println(isPrime(num));
        }
    }

}
