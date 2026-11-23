package org.code.dsa.strings;

public class ReverseStringKeepSymbols {

    public static String reverseStringKeepingSymbols(String str) {
        char[] c = str.toCharArray();
        int left =0;
        int right = c.length-1;

        while(left < right) {
            if(!Character.isLetterOrDigit(c[left])) {
                left++;
            }else if (!Character.isLetterOrDigit(c[right])){
                right--;
            } else {
                char temp = c[left];
                c[left] =  c[right];
                c[right] =  temp;
                left++;
                right--;
            }

        }
        return new String(c);
    }

    public static void main(String[] args) {
        System.out.println(reverseStringKeepingSymbols("a@b*c8j&h#i"));
    }
}
