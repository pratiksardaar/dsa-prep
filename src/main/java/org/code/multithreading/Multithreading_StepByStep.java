package org.code.multithreading;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MULTITHREADING — STEP BY STEP (run main, read output top to bottom)
 *
 * PART A  Thread basics      : lifecycle states, run() vs start(), start twice
 * PART B  synchronized       : mutual exclusion, reentrancy, different locks, static vs instance, exception
 * PART C  Monitor            : wait / notify / notifyAll, timed wait, producer-consumer
 * PART D  ReentrantLock      : holdCount, WAITING state, tryLock, lockInterruptibly, leaked lock, Condition
 * PART E  Stopping threads   : volatile flag, interrupt (busy / sleeping), join(timeout), daemon
 *
 * Thread states cheat sheet:
 *   NEW --start()--> RUNNABLE --(synchronized lock busy)----------> BLOCKED
 *                       |      --wait()/join()/lock()/await()-----> WAITING
 *                       |      --sleep(n)/wait(n)/join(n)---------> TIMED_WAITING
 *                       +------ run() returns or throws ----------> TERMINATED
 *
 * Every Java object has ONE monitor = (lock + ENTRY SET + WAIT SET)
 *   ENTRY SET : threads trying to get the lock     -> BLOCKED
 *   WAIT SET  : threads that called wait()         -> WAITING / TIMED_WAITING
 */
public class Multithreading_StepByStep {

    // ===================================================================================
    // main — call each step in order. Comment out steps to focus on one topic.
    // ===================================================================================
    public static void main(String[] args) throws Exception {
        // ---------- PART A: Thread basics ----------
        step01_lifecycleStates();
        step02_runVsStart();
        step03_startTwice();

        // ---------- PART B: synchronized ----------
        step04_mutualExclusion();
        step05_reentrancy();
        step06_differentLocksRunInParallel();
        step07_staticVsInstanceLock();
        step08_exceptionReleasesLock();

        // ---------- PART C: Monitor (wait / notify) ----------
        step09_waitNotifyWithoutLock();
        step10_waitReleasesLock_notifyDoesNot();
        step11_notifyVsNotifyAll();
        step12_timedWait();
        step13_producerConsumer();

        // ---------- PART D: ReentrantLock ----------
        step14_reentrantHoldCount();
        step15_reentrantLockWaitingState();
        step16_tryLockWithTimeout();
        step17_interruptibility_synchronized_vs_lockInterruptibly();
        step18_leakedLock_unlockByNonOwner();
        step19_conditionAwaitSignal();

        // ---------- PART E: Stopping threads ----------
        step20_stopWithVolatileFlag();
        step21_interruptBusyThread();
        step22_interruptSleepingThread();
        step23_joinWithTimeout();
        step24_daemonThread();          // keep last: JVM exits when main ends
    }

    // ===================================================================================
    // helpers
    // ===================================================================================
    private static final long T0 = System.currentTimeMillis();

    static void log(String msg) {
        System.out.printf("[+%5dms] %-10s | %s%n", System.currentTimeMillis() - T0, Thread.currentThread().getName(), msg);
    }

    static void title(String s) {
        System.out.println("\n==================== " + s + " ====================");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ===================================================================================
    // PART A — THREAD BASICS
    // ===================================================================================

    /** NEW -> RUNNABLE -> TIMED_WAITING -> TERMINATED */
    static void step01_lifecycleStates() throws InterruptedException {
        title("STEP 01: Lifecycle states");
        Thread t = new Thread(() -> {
            log("run() started");
            sleep(300);
            log("run() finishing");
        }, "Life");
        log("after new       : " + t.getState());
        t.start();
        log("after start()   : " + t.getState());
        sleep(100);
        log("while sleeping  : " + t.getState());
        t.join();                                       // main WAITS until Life ends
        log("after join()    : " + t.getState());
    }

    /** run() is just a method call on the current thread; start() creates a new thread. */
    static void step02_runVsStart() throws InterruptedException {
        title("STEP 02: run() vs start()");
        Thread r = new Thread(() -> log("executing run()"), "NewThread");
        log("r.run()   -> NO new thread, runs on main:");
        r.run();
        log("r.start() -> runs on a NEW thread:");
        r.start();
        r.join();
    }

    /** A Thread object can be started only once. */
    static void step03_startTwice() throws InterruptedException {
        title("STEP 03: start() twice");
        Thread t = new Thread(() -> log("ran once"), "Once");
        t.start();
        t.join();
        try { t.start(); }
        catch (IllegalThreadStateException e) { log("2nd start() -> IllegalThreadStateException"); }
    }

    // ===================================================================================
    // PART B — synchronized (intrinsic / monitor lock)
    //   synchronized(obj){}          -> locks obj
    //   synchronized void m()        -> locks 'this'
    //   static synchronized void m() -> locks ClassName.class
    // ===================================================================================

    private static final Object LOCK = new Object();

    static void criticalSection(long holdMs) {
        log("trying to enter synchronized(LOCK) ...");
        synchronized (LOCK) {                                       // LOCK (or BLOCK here)
            log("ACQUIRED lock (holdsLock=" + Thread.holdsLock(LOCK) + "), working " + holdMs + "ms");
            sleep(holdMs);
            log("leaving block -> RELEASE lock");
        }                                                           // UNLOCK (automatic)
    }

    /** Only one thread inside at a time; the other waits in state BLOCKED. */
    static void step04_mutualExclusion() throws InterruptedException {
        title("STEP 04: synchronized — mutual exclusion (2nd thread BLOCKED)");
        Thread a = new Thread(() -> criticalSection(800), "T-A");
        Thread b = new Thread(() -> criticalSection(200), "T-B");
        a.start();
        sleep(100);                                     // T-A gets the lock first
        b.start();
        sleep(100);
        log("T-A=" + a.getState() + ", T-B=" + b.getState() + "  <-- T-B is waiting for the monitor");
        a.join();
        b.join();
    }

    static class Resource {
        synchronized void outer() {
            log("in outer(): holds 'this' = " + Thread.holdsLock(this));
            inner();                                    // needs the SAME lock again
            log("back in outer()");
        }

        synchronized void inner() {
            log("in inner(): RE-ENTERED same monitor, no self-deadlock");
        }

        synchronized void instanceMethod() {
            log("instanceMethod(): holds 'this', working 500ms");
            sleep(500);
            log("instanceMethod(): done");
        }

        static synchronized void staticMethod() {
            log("staticMethod(): holds Resource.class -> ran WITHOUT waiting for 'this'");
        }
    }

    /** synchronized is reentrant: the owner can enter again. */
    static void step05_reentrancy() throws InterruptedException {
        title("STEP 05: synchronized — reentrancy");
        Resource res = new Resource();
        Thread t = new Thread(res::outer, "T-R");
        t.start();
        t.join();
    }

    /** Different objects = different locks = no waiting. */
    static void step06_differentLocksRunInParallel() throws InterruptedException {
        title("STEP 06: synchronized — different lock objects run in parallel");
        Object l1 = new Object(), l2 = new Object();
        long start = System.currentTimeMillis();
        Thread p1 = new Thread(() -> { synchronized (l1) { log("holding l1 500ms"); sleep(500); } }, "T-P1");
        Thread p2 = new Thread(() -> { synchronized (l2) { log("holding l2 500ms"); sleep(500); } }, "T-P2");
        p1.start(); p2.start();
        p1.join(); p2.join();
        log("total ~" + (System.currentTimeMillis() - start) + "ms (≈500 not 1000) -> no contention");
    }

    /** static synchronized locks the Class object, instance synchronized locks 'this'. */
    static void step07_staticVsInstanceLock() throws InterruptedException {
        title("STEP 07: synchronized — static vs instance use DIFFERENT locks");
        Resource res = new Resource();
        Thread i = new Thread(res::instanceMethod, "T-INST");
        Thread s = new Thread(Resource::staticMethod, "T-STAT");
        i.start();
        sleep(100);
        s.start();
        i.join(); s.join();
    }

    /** Leaving a synchronized block by exception still releases the lock. */
    static void step08_exceptionReleasesLock() throws InterruptedException {
        title("STEP 08: synchronized — exception still RELEASES the lock");
        Thread ex = new Thread(() -> {
            synchronized (LOCK) {
                log("acquired lock, throwing exception");
                throw new RuntimeException("boom");
            }
        }, "T-EX");
        ex.setUncaughtExceptionHandler((t, e) -> log("thread died with: " + e.getMessage()));
        ex.start();
        ex.join();
        criticalSection(0);                             // main can get it -> it was released
    }

    // ===================================================================================
    // PART C — MONITOR: wait / notify / notifyAll
    //   - must hold the monitor to call them
    //   - wait()   RELEASES the lock, thread goes to WAIT SET (WAITING)
    //   - notify() does NOT release the lock; woken thread goes WAITING -> BLOCKED -> RUNNABLE
    // ===================================================================================

    private static final Object MONITOR = new Object();
    private static boolean ready = false;               // guarded by MONITOR
    private static boolean go = false;                  // guarded by MONITOR

    static void step09_waitNotifyWithoutLock() {
        title("STEP 09: wait()/notify() without holding the lock");
        try { MONITOR.wait(); }
        catch (IllegalMonitorStateException e) { log("wait() outside synchronized -> IllegalMonitorStateException"); }
        catch (InterruptedException ignored) { }
        try { MONITOR.notify(); }
        catch (IllegalMonitorStateException e) { log("notify() outside synchronized -> IllegalMonitorStateException"); }
    }

    static void step10_waitReleasesLock_notifyDoesNot() throws InterruptedException {
        title("STEP 10: wait() RELEASES the lock; notify() does NOT");
        Thread waiter = new Thread(() -> {
            synchronized (MONITOR) {
                log("ACQUIRED monitor");
                while (!ready) {                        // ALWAYS a while loop, never if
                    log("ready=false -> wait() (RELEASES monitor)");
                    try { MONITOR.wait(); } catch (InterruptedException e) { return; }
                    log("returned from wait() -> lock RE-ACQUIRED, re-checking condition");
                }
                log("ready=true -> doing work");
            }
            log("left synchronized -> released monitor");
        }, "Waiter");
        waiter.start();
        sleep(200);
        log("Waiter state = " + waiter.getState() + "  (in WAIT SET)");

        synchronized (MONITOR) {
            log("main ACQUIRED monitor while Waiter is 'inside' synchronized -> proves wait() released it");
            ready = true;
            MONITOR.notify();
            log("notify() called, main STILL holds the lock ...");
            sleep(300);
            log("Waiter state = " + waiter.getState() + "  (woken, now in ENTRY SET waiting for lock)");
            log("main leaving synchronized -> RELEASE");
        }
        waiter.join();
    }

    static void step11_notifyVsNotifyAll() throws InterruptedException {
        title("STEP 11: notify() wakes ONE, notifyAll() wakes ALL");
        Thread[] ws = new Thread[3];
        for (int i = 0; i < 3; i++) {
            ws[i] = new Thread(() -> {
                synchronized (MONITOR) {
                    try { while (!go) MONITOR.wait(); } catch (InterruptedException e) { return; }
                    log("woke up and proceeding");
                }
            }, "W" + i);
            ws[i].start();
        }
        sleep(200);
        log("states: " + ws[0].getState() + ", " + ws[1].getState() + ", " + ws[2].getState());

        synchronized (MONITOR) { go = true; MONITOR.notify(); log("notify()"); }
        sleep(200);
        log("states: " + ws[0].getState() + ", " + ws[1].getState() + ", " + ws[2].getState()
                + "  <-- 2 still WAITING though go=true (nobody told them)");

        synchronized (MONITOR) { MONITOR.notifyAll(); log("notifyAll()"); }
        for (Thread t : ws) t.join();
    }

    static void step12_timedWait() throws InterruptedException {
        title("STEP 12: wait(timeout) -> TIMED_WAITING, may return with NO notify");
        Thread timed = new Thread(() -> {
            synchronized (MONITOR) {
                log("wait(500) ...");
                try { MONITOR.wait(500); } catch (InterruptedException e) { return; }
                log("returned with NO notify -> that's why you re-check the condition in a while loop");
            }
        }, "Timed");
        timed.start();
        sleep(150);
        log("Timed state = " + timed.getState());
        timed.join();
    }

    /** Classic monitor usage: bounded buffer with wait/notifyAll. */
    static class BoundedBuffer {
        private final LinkedList<Integer> q = new LinkedList<>();
        private final int cap;

        BoundedBuffer(int cap) { this.cap = cap; }

        synchronized void put(int v) throws InterruptedException {
            while (q.size() == cap) {
                log("buffer FULL " + q + " -> wait()");
                wait();
                log("woke up, buffer=" + q);
            }
            q.addLast(v);
            log("put " + v + " -> " + q);
            notifyAll();                                // wake consumers
        }

        synchronized int take() throws InterruptedException {
            while (q.isEmpty()) {
                log("buffer EMPTY -> wait()");
                wait();
            }
            int v = q.removeFirst();
            log("took " + v + " -> " + q);
            notifyAll();                                // wake producers
            return v;
        }
    }

    static void step13_producerConsumer() throws InterruptedException {
        title("STEP 13: Producer / Consumer (bounded buffer, cap=2)");
        BoundedBuffer buf = new BoundedBuffer(2);
        Thread producer = new Thread(() -> {
            try { for (int i = 1; i <= 5; i++) buf.put(i); } catch (InterruptedException ignored) { }
        }, "Producer");
        Thread consumer = new Thread(() -> {
            try { for (int i = 1; i <= 5; i++) { sleep(300); buf.take(); } } catch (InterruptedException ignored) { }
        }, "Consumer");
        producer.start(); consumer.start();
        producer.join(); consumer.join();
    }

    // ===================================================================================
    // PART D — ReentrantLock
    //   ALWAYS: lock.lock(); try { ... } finally { lock.unlock(); }
    //   Extras over synchronized: tryLock, lockInterruptibly, fairness, many Conditions, inspection
    // ===================================================================================

    static void step14_reentrantHoldCount() throws InterruptedException {
        title("STEP 14: ReentrantLock — reentrancy & hold count");
        ReentrantLock lock = new ReentrantLock();
        lock.lock(); log("lock() #1 -> holdCount=" + lock.getHoldCount());
        lock.lock(); log("lock() #2 -> holdCount=" + lock.getHoldCount());
        lock.lock(); log("lock() #3 -> holdCount=" + lock.getHoldCount());
        lock.unlock(); log("unlock()  -> holdCount=" + lock.getHoldCount() + ", isLocked=" + lock.isLocked());
        tryFromOtherThread(lock);                       // still held -> false
        lock.unlock();
        lock.unlock(); log("unlock() x2 -> holdCount=" + lock.getHoldCount() + ", isLocked=" + lock.isLocked());
        tryFromOtherThread(lock);                       // free -> true
    }

    private static void tryFromOtherThread(ReentrantLock lock) throws InterruptedException {
        Thread t = new Thread(() -> {
            boolean got = lock.tryLock();
            log("tryLock() -> " + got);
            if (got) lock.unlock();
        }, "Other");
        t.start();
        t.join();
    }

    /** A thread waiting on lock() is WAITING (parked), not BLOCKED like synchronized. */
    static void step15_reentrantLockWaitingState() throws InterruptedException {
        title("STEP 15: ReentrantLock — waiting thread is WAITING, visible in queue");
        ReentrantLock lock = new ReentrantLock();
        Thread holder = new Thread(() -> {
            lock.lock();
            try { log("got lock, holding 800ms"); sleep(800); }
            finally { lock.unlock(); log("unlock()"); }
        }, "Holder");
        Thread waiter = new Thread(() -> {
            log("calling lock() ...");
            lock.lock();
            try { log("got lock"); }
            finally { lock.unlock(); log("unlock()"); }
        }, "Waiter");
        holder.start(); sleep(100); waiter.start(); sleep(100);
        log("Waiter state=" + waiter.getState() + ", queueLength=" + lock.getQueueLength()
                + ", hasQueuedThread(Waiter)=" + lock.hasQueuedThread(waiter));
        holder.join(); waiter.join();
    }

    static void step16_tryLockWithTimeout() throws InterruptedException {
        title("STEP 16: ReentrantLock — tryLock(timeout): give up instead of waiting forever");
        ReentrantLock lock = new ReentrantLock();
        Thread holder = new Thread(() -> {
            lock.lock();
            try { log("got lock, holding 800ms"); sleep(800); }
            finally { lock.unlock(); log("unlock()"); }
        }, "Holder");
        Thread trier = new Thread(() -> {
            try {
                log("tryLock(200ms) ...");
                if (lock.tryLock(200, TimeUnit.MILLISECONDS)) {
                    try { log("got it"); } finally { lock.unlock(); }
                } else {
                    log("tryLock(200ms) -> FALSE (timed out), doing something else");
                }
                log("tryLock(2s) ...");
                if (lock.tryLock(2, TimeUnit.SECONDS)) {
                    try { log("tryLock(2s) -> TRUE"); } finally { lock.unlock(); log("unlock()"); }
                }
            } catch (InterruptedException e) { log("interrupted"); }
        }, "Trier");
        holder.start(); sleep(100); trier.start();
        holder.join(); trier.join();
    }

    static void step17_interruptibility_synchronized_vs_lockInterruptibly() throws InterruptedException {
        title("STEP 17a: thread waiting on synchronized CANNOT be interrupted");
        Object mon = new Object();
        Thread syncHolder = new Thread(() -> { synchronized (mon) { log("holding monitor 800ms"); sleep(800); } }, "SyncHold");
        Thread syncWaiter = new Thread(() -> {
            log("entering synchronized(mon) ...");
            synchronized (mon) {
                log("finally got monitor. interrupted flag=" + Thread.currentThread().isInterrupted());
            }
        }, "SyncWait");
        syncHolder.start(); sleep(100); syncWaiter.start(); sleep(100);
        syncWaiter.interrupt();
        log("interrupt() sent to SyncWait");
        sleep(200);
        log("SyncWait state=" + syncWaiter.getState() + "  <-- interrupt ignored, still stuck");
        syncHolder.join(); syncWaiter.join();

        title("STEP 17b: thread waiting on lockInterruptibly() CAN be interrupted");
        ReentrantLock lock = new ReentrantLock();
        Thread lockHolder = new Thread(() -> {
            lock.lock();
            try { log("holding lock 800ms"); sleep(800); } finally { lock.unlock(); log("unlock()"); }
        }, "LockHold");
        Thread lockWaiter = new Thread(() -> {
            try {
                log("lockInterruptibly() ...");
                lock.lockInterruptibly();
                try { log("got lock"); } finally { lock.unlock(); }
            } catch (InterruptedException e) {
                log("InterruptedException -> stopped waiting, never got the lock");
            }
        }, "LockWait");
        lockHolder.start(); sleep(100); lockWaiter.start(); sleep(100);
        lockWaiter.interrupt();
        log("interrupt() sent to LockWait");
        lockHolder.join(); lockWaiter.join();
    }

    /** Forgetting unlock() leaks the lock forever; only the owner may unlock. */
    static void step18_leakedLock_unlockByNonOwner() throws InterruptedException {
        title("STEP 18: ReentrantLock — leaked lock & unlock() by non-owner");
        ReentrantLock leaky = new ReentrantLock();
        Thread owner = new Thread(leaky::lock, "Owner");        // locks and dies WITHOUT unlocking (bug!)
        owner.start(); owner.join();
        log("Owner is " + owner.getState() + " but isLocked=" + leaky.isLocked() + "  <-- LEAKED (no finally{unlock})");
        try { leaky.unlock(); }
        catch (IllegalMonitorStateException e) { log("main.unlock() -> IllegalMonitorStateException (only owner can unlock)"); }
    }

    /** Condition = ReentrantLock's version of wait/notify; await() releases the lock. */
    static void step19_conditionAwaitSignal() throws InterruptedException {
        title("STEP 19: ReentrantLock — Condition await() / signal()");
        ReentrantLock lock = new ReentrantLock();
        Condition readyCond = lock.newCondition();
        boolean[] flag = {false};                               // guarded by lock
        Thread awaiter = new Thread(() -> {
            lock.lock();
            try {
                log("holds lock");
                while (!flag[0]) {
                    log("flag=false -> await() (RELEASES lock)");
                    readyCond.await();
                    log("returned from await() -> lock RE-ACQUIRED, holdCount=" + lock.getHoldCount());
                }
                log("flag=true -> working");
            } catch (InterruptedException e) {
                log("interrupted");
            } finally {
                lock.unlock();
                log("unlock()");
            }
        }, "Awaiter");
        awaiter.start(); sleep(200);
        log("Awaiter state=" + awaiter.getState() + ", isLocked=" + lock.isLocked() + "  <-- await released it");
        lock.lock();
        try {
            log("main got lock. waiters on condition=" + lock.getWaitQueueLength(readyCond));
            flag[0] = true;
            readyCond.signal();
            log("signal() sent, main still holds lock 300ms (Awaiter can't continue yet)");
            sleep(300);
        } finally {
            lock.unlock();
            log("main unlock()");
        }
        awaiter.join();
    }

    // ===================================================================================
    // PART E — STOPPING THREADS
    //   No safe "kill". You ASK the thread to stop; it stops itself.
    //   interrupt(): sets a flag; sleep()/wait()/join() throw InterruptedException and CLEAR the flag.
    // ===================================================================================

    private static volatile boolean running = true;     // volatile -> worker SEES main's write

    static void step20_stopWithVolatileFlag() throws InterruptedException {
        title("STEP 20: stop with a volatile flag");
        Thread worker = new Thread(() -> {
            int n = 0;
            while (running) { n++; sleep(100); }
            log("saw running=false after " + n + " iterations -> exiting cleanly");
        }, "FlagWkr");
        worker.start();
        sleep(450);
        log("setting running=false");
        running = false;
        worker.join();
    }

    static void step21_interruptBusyThread() throws InterruptedException {
        title("STEP 21: interrupt() a busy CPU thread — it must CHECK isInterrupted()");
        Thread busy = new Thread(() -> {
            long n = 0;
            while (!Thread.currentThread().isInterrupted()) n++;
            log("isInterrupted()=true after " + n + " loops -> exiting");
        }, "Busy");
        busy.start();
        sleep(100);
        log("busy.interrupt()");
        busy.interrupt();
        busy.join();
    }

    static void step22_interruptSleepingThread() throws InterruptedException {
        title("STEP 22: interrupt() a sleeping thread — InterruptedException, flag CLEARED");
        Thread sleeper = new Thread(() -> {
            try {
                log("sleeping 10s ...");
                Thread.sleep(10_000);
                log("woke normally (won't happen)");
            } catch (InterruptedException e) {
                log("InterruptedException! isInterrupted()=" + Thread.currentThread().isInterrupted() + " (JVM cleared it)");
                Thread.currentThread().interrupt();     // restore so callers know
                log("restored flag -> isInterrupted()=" + Thread.currentThread().isInterrupted());
            }
            log("cleanup & exit");
        }, "Sleeper");
        sleeper.start();
        sleep(200);
        log("Sleeper state=" + sleeper.getState() + " -> interrupt()");
        sleeper.interrupt();
        sleeper.join();
    }

    static void step23_joinWithTimeout() throws InterruptedException {
        title("STEP 23: join(timeout) — wait, but not forever");
        Thread slow = new Thread(() -> sleep(1000), "Slow");
        slow.start();
        slow.join(300);
        log("join(300) returned; Slow alive=" + slow.isAlive() + " -> main moves on");
        slow.join();
        log("join() returned; Slow alive=" + slow.isAlive());
    }

    static void step24_daemonThread() {
        title("STEP 24: daemon thread dies with the JVM");
        Thread daemon = new Thread(() -> {
            while (true) { log("daemon tick"); sleep(150); }
        }, "Daemon");
        daemon.setDaemon(true);                         // must be set BEFORE start()
        daemon.start();
        sleep(400);
        log("main ends -> JVM exits; daemon killed (no finally, no cleanup)");
    }
}
