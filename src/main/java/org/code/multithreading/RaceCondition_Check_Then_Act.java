package org.code.multithreading;

public class RaceCondition_Check_Then_Act {
    private final Object lock = new Object();//private lock ; no outsider can lock on it
    private long balance;

    RaceCondition_Check_Then_Act(long balance) {
        this.balance = balance;
    }

    boolean debit(long amount) {
        synchronized (lock) { //check + act = Onr atomic step
            if (balance < amount) return false;
            balance -= amount;
            return true;
        }

    }

    // BROKEN version: check and act are two separate steps, so threads can interleave between them.
    boolean debitUnsafe(long amount) {
        if (balance < amount) return false;          // CHECK
        try { Thread.sleep(1); } catch (InterruptedException ignored) { } // widen the race window
        balance -= amount;                           // ACT
        return true;
    }

    long getBalance() {
        synchronized (lock) {
            return balance;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Demo: run 10 threads, each trying to debit 100 from a balance of 100.
    // Correct result: exactly 1 succeeds, balance = 0.
    // ---------------------------------------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Step 1: single thread sanity check ===");
        RaceCondition_Check_Then_Act acc = new RaceCondition_Check_Then_Act(100);
        System.out.println("debit 60  -> " + acc.debit(60) + ", balance = " + acc.getBalance()); // true, 40
        System.out.println("debit 50  -> " + acc.debit(50) + ", balance = " + acc.getBalance()); // false, 40
        System.out.println("debit 40  -> " + acc.debit(40) + ", balance = " + acc.getBalance()); // true, 0

        System.out.println("\n=== Step 2: FAILURE scenario (debitUnsafe, no lock) ===");
        runScenario(false);

        System.out.println("\n=== Step 3: SUCCESS scenario (debit, synchronized) ===");
        runScenario(true);
    }

    private static void runScenario(boolean safe) throws InterruptedException {
        RaceCondition_Check_Then_Act account = new RaceCondition_Check_Then_Act(100);
        int threadCount = 10;
        java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try { startGate.await(); } catch (InterruptedException e) { return; } // wait for "go"
                boolean ok = safe ? account.debit(100) : account.debitUnsafe(100);
                if (ok) successes.incrementAndGet();
                System.out.println("  " + Thread.currentThread().getName() + " debit(100) -> " + ok);
            }, "T" + i);
            threads[i].start();
        }

        startGate.countDown();               // release all threads at the same moment
        for (Thread t : threads) t.join();   // wait for all to finish

        System.out.println("Successful debits = " + successes.get() + " (expected 1)");
        System.out.println("Final balance     = " + account.getBalance() + " (expected 0)");
        System.out.println(successes.get() == 1 && account.getBalance() == 0
                ? "RESULT: CORRECT ✅" : "RESULT: RACE CONDITION — overdrawn ❌");
    }
}
