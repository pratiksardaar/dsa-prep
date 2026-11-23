package org.code.multithreading;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BasicMultithreading {

    public static void main(String[] args) throws InterruptedException, ExecutionException {

        Thread worker = new Thread(()-> System.out.println("Hello From " + Thread.currentThread().getName()), "worker-1");
        worker.start();

        worker.join();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()
        ) {
            Future<Integer> f = pool.submit(() -> 42);
            System.out.println(f.get());
        }

        Thread poller = new Thread(() ->  {

           while (!Thread.currentThread().isInterrupted()) {
               try {
                   pollQueue();
                   Thread.sleep(100);
               }catch (InterruptedException e){
                   Thread.currentThread().interrupt();
               }
           }
        });
        poller.start();
        poller.interrupt();
    }


    static final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    static void pollQueue() {
        String item = queue.poll();          // built-in: returns null if empty
        if (item != null) {
            System.out.println("Processing " + item);
        }
    }


}
