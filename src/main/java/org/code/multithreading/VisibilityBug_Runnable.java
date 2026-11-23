package org.code.multithreading;

public class VisibilityBug_Runnable implements Runnable{
    private volatile boolean running = true;
    public void run(){
        while (running){}
    }

    void stop(){
        running = false;
    }

    public static void main(String[] args) throws InterruptedException {
        VisibilityBug_Runnable task = new VisibilityBug_Runnable();
        Thread worker = new Thread(task, "worker");

        worker.start();          // worker starts looping on `running`
        Thread.sleep(1000);      // let it run for 1 second

        System.out.println(">>> main: calling stop()");
        task.stop();             // main thread sets running = false

        worker.join(3000);       // wait up to 3s for the worker to finish

        if (worker.isAlive()) {
            System.out.println(">>> BUG: worker never saw running=false, still running!");
            System.exit(1);      // kill the stuck thread
        } else {
            System.out.println(">>> worker stopped normally");
        }
    }
}
