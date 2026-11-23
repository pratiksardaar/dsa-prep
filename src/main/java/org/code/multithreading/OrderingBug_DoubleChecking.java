package org.code.multithreading;

import javax.security.auth.login.Configuration;

class Config {
    private final String url;
    private final int timeout;
    Config() {
        System.out.println("Config created by " + Thread.currentThread().getName());
        this.url = "db://localhost";
        this.timeout = 30;
    }
    @Override
    public String toString() {
        return "Config{url=" + url + ", timeout=" + timeout + "}";
    }
}


public class OrderingBug_DoubleChecking {

    public static volatile Config instance;

    static Config get(){

        Config c = instance;
        if(c ==  null) {

            synchronized (OrderingBug_DoubleChecking.class) {
                c = instance;
                if(c == null) instance = c = new Config();
            }

        }
        return c;
    }

    public static void main(String[] args) throws InterruptedException {
        Runnable r = () -> System.out.println(Thread.currentThread().getName()
                + " got " + System.identityHashCode(get()));
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(r, "T" + i);
            threads[i].start();
        }
        for (Thread t : threads) t.join();
    }
}
