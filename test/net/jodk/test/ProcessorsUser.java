package net.jodk.test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Makes sure that all available processors are somehow busy.
 * Useful for testing concurrent treatments, to have their thread
 * eventually paused.
 */
public class ProcessorsUser {

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    private static class MyRunnable implements Runnable {
        @Override
        public void run() {
            try {
                this.runInterruptible();
            } catch (InterruptedException e) {
                // ok stopping
                System.out.println(Thread.currentThread()+" interrupted");
            }
        }
        public void runInterruptible() throws InterruptedException {
            // Threshold such as processors are often busy, but not always,
            // to let most CPU available for functional treatments.
            // TODO Adjust that depending on CPU/OS.
            final long bigYieldThresholdNS = 1L * 1000L;
            while (true) {
                if (keepBusy.get() != 0) {
                    long a = System.nanoTime();
                    Thread.yield();
                    long b = System.nanoTime();
                    long ns = b-a;
                    if (ns < bigYieldThresholdNS) {
                        // Small yield: processor mostly free: we will yield again.
                        continue;
                    } else {
                        // Big yield: processor somehow busy: we will wait a bit.
                        Thread.sleep(1L);
                    }
                } else {
                    synchronized (keepBusy) {
                        while (keepBusy.get() == 0) {
                            keepBusy.wait();
                        }
                    }
                }
            }
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final int NBR_OF_PROCS = Runtime.getRuntime().availableProcessors();
    
    private static final ExecutorService EXECUTOR;
    static {
        final AtomicLong idProvider = new AtomicLong();
        final ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                final Thread thread = new Thread(
                        runnable,
                        ProcessorsUser.class.getSimpleName()+"-"+idProvider.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        EXECUTOR = Executors.newCachedThreadPool(threadFactory);
    }
    
    private static final AtomicInteger keepBusy = new AtomicInteger();
    
    private static final AtomicInteger started = new AtomicInteger();
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public static void start() {
        keepBusy.set(1);
        synchronized (keepBusy) {
            keepBusy.notifyAll();
        }
        
        if (started.compareAndSet(0, 1)) {
            for (int i=0;i<NBR_OF_PROCS;i++) {
                EXECUTOR.execute(new MyRunnable());
            }
        }
    }

    public static void stop() {
        keepBusy.set(0);
        synchronized (keepBusy) {
            keepBusy.notifyAll();
        }
    }
}
