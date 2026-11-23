package org.code.multithreading;

public class LazySingleton {

    private LazySingleton(){}

    private static class Holder {
        static final Config INSTANCE =  new Config();
    }
    static Config get() { return Holder.INSTANCE;
    }
}
