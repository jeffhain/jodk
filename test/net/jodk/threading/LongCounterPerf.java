/*
 * Copyright 2012 Jeff Hain
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jodk.threading;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.jodk.lang.InterfaceFactory;
import net.jodk.lang.NumbersUtils;
import net.jodk.lang.Unchecked;
import net.jodk.test.TestUtils;
import net.jodk.threading.LongCounter;
import net.jodk.threading.LongCounter.InterfaceHoleListener;

public class LongCounterPerf {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final int NBR_OF_PROC = Runtime.getRuntime().availableProcessors();
    private static final int CEILED_NBR_OF_PROC = NumbersUtils.ceilingPowerOfTwo(NBR_OF_PROC);
    
    private static final int MIN_PARALLELISM = 1;
    private static final int MAX_PARALLELISM = 2 * CEILED_NBR_OF_PROC;
    
    private static final long NBR_OF_CALLS = 100L * 1000L * 1000L;

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MyHoleListener implements InterfaceHoleListener {
        private long nbrOfHoles;
        @Override
        public void onHole(long hole) {
            ++this.nbrOfHoles;
        }
    }

    private static class MyHoleListenerFactory implements InterfaceFactory<MyHoleListener> {
        private final ArrayList<MyHoleListener> holeListenerList = new ArrayList<MyHoleListener>();
        @Override
        public MyHoleListener newInstance() {
            final MyHoleListener instance = new MyHoleListener();
            holeListenerList.add(instance);
            return instance;
        }
        public long getTotalNbrOfHoles() {
            long totalNbrOfHoles = 0;
            for (MyHoleListener holeListener : holeListenerList) {
                totalNbrOfHoles += holeListener.nbrOfHoles;
            }
            return totalNbrOfHoles;
        }
    }

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println(TestUtils.getJVMInfo());
        newRun(args);
    }

    public static void newRun(String[] args) {
        new LongCounterPerf().run(args);
    }
    
    public LongCounterPerf() {
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private void run(String[] args) {
        // XXX
        System.out.println("--- "+LongCounterPerf.class.getSimpleName()+"... ---");
        System.out.println("number of calls = "+NBR_OF_CALLS);

        final long nbrOfCalls = NBR_OF_CALLS;
        for (int n=MIN_PARALLELISM;n<=MAX_PARALLELISM;n*=2) {
            launchBenches(nbrOfCalls, n);
        }

        System.out.println("--- ..."+LongCounterPerf.class.getSimpleName()+" ---");
    }

    private static void launchBenches(
            final long nbrOfCalls,
            final int nbrOfThreads) {

        launchBenches(nbrOfCalls, nbrOfThreads, nbrOfThreads);
        if (nbrOfThreads > 1) {
            // Sometimes more than twice the number of concurrent threads,
            // as done by default in LongCounter constructor,
            // for once only is a bit tight.
            launchBenches(nbrOfCalls, nbrOfThreads, 2*nbrOfThreads);
        }
    }
    
    private static void launchBenches(
            final long nbrOfCalls,
            final int nbrOfThreads,
            final int nbrOfCells) {

        System.out.println("");

        /*
         * AtomicLong
         */

        {
            final AtomicLong counter = new AtomicLong();
            benchConcurrentCalls(new InterfaceFactory<Runnable>() {
                public String toString() { return "AtomicLong.getAndIncrement()"; }
                public Runnable newInstance() { return new Runnable() {
                    public void run() { counter.getAndIncrement(); }
                };}
            }, nbrOfCalls, nbrOfThreads);
        }

        /*
         * LongCounter (non monotonic)
         */

        {
            final LongCounter counter = new LongCounter(0,nbrOfCells);
            benchConcurrentCalls(new InterfaceFactory<Runnable>() {
                public String toString() { return "LongCounter[nbrOfCells="+nbrOfCells+"].getAndIncrement()"; }
                public Runnable newInstance() { return new Runnable() {
                    public void run() { counter.getAndIncrement(); }
                };}
            }, nbrOfCalls, nbrOfThreads);
        }
        {
            final LongCounter counter = new LongCounter(0,nbrOfCells);
            benchConcurrentCalls(new InterfaceFactory<Runnable>() {
                public String toString() { return "LongCounter[nbrOfCells="+nbrOfCells+"].getAndIncrement(LocalData)"; }
                public Runnable newInstance() { return new Runnable() {
                    private final LongCounter.LocalData localData = counter.newLocalData();
                    public void run() { counter.getAndIncrement(this.localData); }
                };}
            }, nbrOfCalls, nbrOfThreads);
        }
        {
            final LongCounter counter = new LongCounter(0,nbrOfCells);
            benchConcurrentCalls(new InterfaceFactory<Runnable>() {
                public String toString() { return "LongCounter[nbrOfCells="+nbrOfCells+"].weakGetAndIncrement(LocalData)"; }
                public Runnable newInstance() { return new Runnable() {
                    private final LongCounter.LocalData localData = counter.newLocalData();
                    public void run() { counter.weakGetAndIncrement(this.localData); }
                };}
            }, nbrOfCalls, nbrOfThreads);
        }

        /*
         * LongCounter (monotonic)
         */

        {
            final LongCounter counter = new LongCounter(0,nbrOfCells);
            final MyHoleListenerFactory holeListenerFactory = new MyHoleListenerFactory();
            benchConcurrentCalls(new InterfaceFactory<Runnable>() {
                public String toString() { return "LongCounter[nbrOfCells="+nbrOfCells+"].getAndIncrementMonotonic(InterfaceHoleListener)"; }
                public Runnable newInstance() { return new Runnable() {
                    private final MyHoleListener holeListener = holeListenerFactory.newInstance();
                    public void run() { counter.getAndIncrementMonotonic(this.holeListener); }
                };}
            }, nbrOfCalls, nbrOfThreads);
            printHolesRatioIfNonZero(holeListenerFactory, nbrOfCalls);
        }
        {
            final LongCounter counter = new LongCounter(0,nbrOfCells);
            final MyHoleListenerFactory holeListenerFactory = new MyHoleListenerFactory();
            benchConcurrentCalls(new InterfaceFactory<Runnable>() {
                public String toString() { return "LongCounter[nbrOfCells="+nbrOfCells+"].getAndIncrementMonotonic(LocalData,InterfaceHoleListener)"; }
                public Runnable newInstance() { return new Runnable() {
                    private final LongCounter.LocalData localData = counter.newLocalData();
                    private final MyHoleListener holeListener = holeListenerFactory.newInstance();
                    public void run() { counter.getAndIncrementMonotonic(this.localData,this.holeListener); }
                };}
            }, nbrOfCalls, nbrOfThreads);
            printHolesRatioIfNonZero(holeListenerFactory, nbrOfCalls);
        }
        {
            final LongCounter counter = new LongCounter(0,nbrOfCells);
            final MyHoleListenerFactory holeListenerFactory = new MyHoleListenerFactory();
            benchConcurrentCalls(new InterfaceFactory<Runnable>() {
                public String toString() { return "LongCounter[nbrOfCells="+nbrOfCells+"].weakGetAndIncrementMonotonic(LocalData,InterfaceHoleListener)"; }
                public Runnable newInstance() { return new Runnable() {
                    private final LongCounter.LocalData localData = counter.newLocalData();
                    private final MyHoleListener holeListener = holeListenerFactory.newInstance();
                    public void run() { counter.weakGetAndIncrementMonotonic(this.localData,this.holeListener); }
                };}
            }, nbrOfCalls, nbrOfThreads);
            printHolesRatioIfNonZero(holeListenerFactory, nbrOfCalls);
        }
    }

    /**
     * @param runnableFactory Factory to create one runnable per thread.
     */
    private static void benchConcurrentCalls(
            final InterfaceFactory<Runnable> runnableFactory,
            final long nbrOfCalls,
            int nbrOfThreads) {
        final ExecutorService executor = Executors.newFixedThreadPool(nbrOfThreads);

        // Neglecting the rest.
        final long nbrOfCallsPerThread = nbrOfCalls/nbrOfThreads;

        final AtomicInteger nbrOfReadyThreads = new AtomicInteger();

        long a = System.nanoTime();
        for (int i=0;i<nbrOfThreads;i++) {
            executor.execute(new Runnable() {
                private final Runnable runnable = runnableFactory.newInstance();
                @Override
                public void run() {
                    nbrOfReadyThreads.incrementAndGet();
                    synchronized (nbrOfReadyThreads) {
                        while (nbrOfReadyThreads.get() != 0) {
                            Unchecked.wait(nbrOfReadyThreads);
                        }
                    }
                    final Runnable runnable = this.runnable;
                    for (int j=0;j<nbrOfCallsPerThread;j++) {
                        runnable.run();
                    }
                }
            });
        }
        // Waiting for everyone to be ready (else early threads
        // might start to do a lot of their work before late
        // ones did any).
        while (nbrOfReadyThreads.get() != nbrOfThreads) {
            Thread.yield();
        }
        // Unleashing everyone.
        synchronized (nbrOfReadyThreads) {
            nbrOfReadyThreads.set(0);
            nbrOfReadyThreads.notifyAll();
        }
        Unchecked.shutdownAndAwaitTermination(executor);
        long b = System.nanoTime();

        System.out.println(nbrOfThreads+" thread(s) : "+runnableFactory+" took "+TestUtils.nsToSRounded(b-a)+" s");
    }

    private static void printHolesRatioIfNonZero(
            final MyHoleListenerFactory holeListenerFactory,
            long nbrOfCalls) {
        long totalNbrOfHoles = holeListenerFactory.getTotalNbrOfHoles();
        if (totalNbrOfHoles != 0) {
            System.out.println("    holes ratio = "+(totalNbrOfHoles/(double)nbrOfCalls));
        }
    }
}
