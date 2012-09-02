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
package net.jodk.threading.locks;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.jodk.lang.InterfaceBooleanCondition;
import net.jodk.lang.Unchecked;
import net.jodk.test.TestUtils;

/**
 * Class to bench performances of condilocks.
 */
public class CondilocksPerf {
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------
    
    private static final int NBR_OF_CALLS = 10 * 1000 * 1000;
    private static final int NBR_OF_RUNS = 2;
    
    private static final int MIN_NBR_FOR_TRUE = 1;
    private static final int MAX_NBR_FOR_TRUE = 3;
    
    private static final long INFINITE_NS = Long.MAX_VALUE;
    
    /**
     * Large enough to hold through bench,
     * small enough not to be approximated as infinite.
     */
    private static final long FINITE_NS = 24L * 3600L * (1000L * 1000L * 1000L);

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MyCondilockHome {
        final InterfaceCondilock condilock;
        final String description;
        public MyCondilockHome(
                InterfaceCondilock condilock,
                String description) {
            this.condilock = condilock;
            this.description = description;
        }
    }

    /*
     * 
     */
    
    private static class MyBC implements InterfaceBooleanCondition {
        /**
         * Number of isTrue() calls, inclusive, up to returning true.
         */
        int nbrForTrue;
        @Override
        public boolean isTrue() {
            return (--this.nbrForTrue <= 0);
        }
    }

    /**
     * call() implements the wait.
     */
    private static abstract class MyAbstractRunnable implements Runnable, Callable<Void> {
        final MyBC bc = new MyBC();
        final MyCondilockHome ch;
        final int nbrOfCalls;
        final int nbrForTrue;
        public MyAbstractRunnable(
                MyCondilockHome ch,
                int nbrOfCalls,
                int nbrForTrue) {
            this.ch = ch;
            this.nbrOfCalls = nbrOfCalls;
            this.nbrForTrue = nbrForTrue;
        }
        @Override
        public void run() {
            for (int i=0;i<this.nbrOfCalls;i++) {
                this.bc.nbrForTrue = this.nbrForTrue;
                try {
                    this.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    
    /*
     * 
     */

    private interface MyInterfaceRunnableFactory {
        public boolean hasTiming();
        public MyAbstractRunnable newRunnable(
                MyCondilockHome ch,
                int nbrOfCalls,
                int nbrForTrue,
                boolean finiteWait);
    }

    private static class MyRF_awaitWhileFalseInLockUninterruptibly implements MyInterfaceRunnableFactory {
        @Override
        public boolean hasTiming() {
            return false;
        }
        @Override
        public MyAbstractRunnable newRunnable(
                MyCondilockHome ch,
                int nbrOfCalls,
                int nbrForTrue,
                boolean finiteWait) {
            return new MyAbstractRunnable(ch, nbrOfCalls, nbrForTrue) {
                @Override
                public String toString() {
                    return this.ch.condilock.getClass().getSimpleName()+".awaitWhileFalseInLockUninterruptibly(bc) - "+this.ch.description;
                }
                @Override
                public Void call() throws Exception {
                    this.ch.condilock.awaitWhileFalseInLockUninterruptibly(this.bc);
                    return null;
                }
            };
        }
    }

    private static class MyRF_awaitNanosWhileFalseInLock implements MyInterfaceRunnableFactory {
        @Override
        public boolean hasTiming() {
            return true;
        }
        @Override
        public MyAbstractRunnable newRunnable(
                MyCondilockHome ch,
                int nbrOfCalls,
                int nbrForTrue,
                final boolean finiteWait) {
            return new MyAbstractRunnable(ch, nbrOfCalls, nbrForTrue) {
                final long timeoutNS = finiteWait ? FINITE_NS : INFINITE_NS;
                @Override
                public String toString() {
                    return this.ch.condilock.getClass().getSimpleName()
                            +".awaitNanosWhileFalseInLock(bc,"
                            +w(nbrForTrue,finiteWait)+") - "+this.ch.description;
                }
                @Override
                public Void call() throws Exception {
                    this.ch.condilock.awaitNanosWhileFalseInLock(this.bc, this.timeoutNS);
                    return null;
                }
            };
        }
    }

    private static class MyRF_awaitUntilNanosTimeoutTimeWhileFalseInLock implements MyInterfaceRunnableFactory {
        @Override
        public boolean hasTiming() {
            return true;
        }
        @Override
        public MyAbstractRunnable newRunnable(
                MyCondilockHome ch,
                int nbrOfCalls,
                int nbrForTrue,
                final boolean finiteWait) {
            return new MyAbstractRunnable(ch, nbrOfCalls, nbrForTrue) {
                final long timeoutTimeNS = finiteWait ? ch.condilock.timeoutTimeNS() + FINITE_NS : INFINITE_NS;
                @Override
                public String toString() {
                    return this.ch.condilock.getClass().getSimpleName()
                            +".awaitUntilNanosTimeoutTimeWhileFalseInLock(bc,"
                            +w(nbrForTrue,finiteWait)+") - "+this.ch.description;
                }
                @Override
                public Void call() throws Exception {
                    this.ch.condilock.awaitUntilNanosTimeoutTimeWhileFalseInLock(this.bc, this.timeoutTimeNS);
                    return null;
                }
            };
        }
    }

    private static class MyRF_awaitUntilNanosWhileFalseInLock implements MyInterfaceRunnableFactory {
        @Override
        public boolean hasTiming() {
            return true;
        }
        @Override
        public MyAbstractRunnable newRunnable(
                MyCondilockHome ch,
                int nbrOfCalls,
                int nbrForTrue,
                final boolean finiteWait) {
            return new MyAbstractRunnable(ch, nbrOfCalls, nbrForTrue) {
                final long deadlineNS = finiteWait ? ch.condilock.deadlineTimeNS() + FINITE_NS : INFINITE_NS;
                @Override
                public String toString() {
                    return this.ch.condilock.getClass().getSimpleName()
                            +".awaitUntilNanosWhileFalseInLock(bc,"
                            +w(nbrForTrue,finiteWait)+") - "+this.ch.description;
                }
                @Override
                public Void call() throws Exception {
                    this.ch.condilock.awaitUntilNanosWhileFalseInLock(this.bc, this.deadlineNS);
                    return null;
                }
            };
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
        new CondilocksPerf().run(args);
    }
    
    public CondilocksPerf() {
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private void run(String[] args) {
        // XXX
        System.out.println("--- "+CondilocksPerf.class.getSimpleName()+"... ---");
        System.out.println("number of calls = "+NBR_OF_CALLS);
        System.out.println("n = number of checks, inclusive, until boolean condition returns true");
        System.out.println("p = parallelism (1, or 2 for contention on blocking condilocks)");
        System.out.println("timeouts/deadlines: na = not applicable, 1d = one day later, oo = infinite");

        benchThroughput();

        System.out.println("--- ..."+CondilocksPerf.class.getSimpleName()+" ---");
    }

    private void benchThroughput() {
        for (int nbrForTrue=MIN_NBR_FOR_TRUE;nbrForTrue<=MAX_NBR_FOR_TRUE;nbrForTrue++) {
            benchThroughput(nbrForTrue);
        }
    }
    
    private void benchThroughput(int nbrForTrue) {
        ArrayList<MyInterfaceRunnableFactory> rfs = new ArrayList<MyInterfaceRunnableFactory>();
        
        rfs.add(new MyRF_awaitWhileFalseInLockUninterruptibly());
        rfs.add(new MyRF_awaitNanosWhileFalseInLock());
        rfs.add(new MyRF_awaitUntilNanosTimeoutTimeWhileFalseInLock());
        rfs.add(new MyRF_awaitUntilNanosWhileFalseInLock());
        
        for (MyInterfaceRunnableFactory rf : rfs) {
            benchThroughput(
                    nbrForTrue,
                    rf);
        }
    }

    private void benchThroughput(
            int nbrForTrue,
            MyInterfaceRunnableFactory rf) {
        if (nbrForTrue == 1) {
            /*
             * no wait (true on first check)
             */
            
            System.out.println("");
            
            for (MyCondilockHome ch : newCHS_spinning()) {
                benchThroughput(rf, ch, 1, nbrForTrue);
            }
            System.out.println("---");
            for (MyCondilockHome ch : newCHS_blocking()) {
                benchThroughput(rf, ch, 1, nbrForTrue);
            }
        } else {
            System.out.println("");
            
            for (MyCondilockHome ch : newCHS_spinning()) {
                benchThroughput(rf, ch, 1, nbrForTrue);
            }

            // To make sure condilocks don't actually start blocking wait,
            // and go out of locked block ASAP.
            if (nbrForTrue <= 2) {
                System.out.println("---");
                for (MyCondilockHome ch : newCHS_blocking()) {
                    benchThroughput(rf, ch, 1, nbrForTrue);
                }
                // parallelism to make sure synchronized blocks are not optimized away.
                System.out.println("---");
                for (MyCondilockHome ch : newCHS_blocking()) {
                    benchThroughput(rf, ch, 2, nbrForTrue);
                }
            }
        }
    }

    private void benchThroughput(
            MyInterfaceRunnableFactory rf,
            MyCondilockHome ch,
            int nbrOfThreads,
            int nbrForTrue) {
        for (boolean finite : (rf.hasTiming() && (nbrForTrue != 1)) ? new boolean[]{true,false} : new boolean[]{false}) {
            for (int k=0;k<NBR_OF_RUNS;k++) {
                final ExecutorService executor = Executors.newCachedThreadPool();

                final int nbrOfCallsPerThread = NBR_OF_CALLS/nbrOfThreads;
                String descr = null;
                
                long a = System.nanoTime();
                for (int i=0;i<nbrOfThreads;i++) {
                    Runnable runnable = rf.newRunnable(
                            ch,
                            nbrOfCallsPerThread,
                            nbrForTrue,
                            finite);
                    executor.execute(runnable);
                    descr = runnable.toString();
                }
                Unchecked.shutdownAndAwaitTermination(executor);
                long b = System.nanoTime();
                System.out.println("n="+nbrForTrue+", p="+nbrOfThreads+", "+descr+", took "+TestUtils.nsToSRounded(b-a)+" s");
            }
        }
    }
    
    /*
     * 
     */
    
    private static String w(int nbrForTrue, boolean finiteWait) {
        return (nbrForTrue == 1) ? "na" : (finiteWait ? "1d" : "oo");
    }
    
    /*
     * 
     */
    
    private static ArrayList<MyCondilockHome> newCHS_spinning() {
        ArrayList<MyCondilockHome> chs = new ArrayList<MyCondilockHome>();
        
        if (true) {
            chs.add(newCH_SpinningCondilock_spinning());
        }
        if (true) {
            chs.add(newCH_SmartSpinningCondilock_spinning());
        }
        if (true) {
            for (boolean monitor : new boolean[]{true,false}) {
                chs.add(newCH_SmartBlockingCondilock_spinning_busy(monitor));
                chs.add(newCH_SmartBlockingCondilock_spinning_yields(monitor));
                chs.add(newCH_SmartBlockingCondilock_spinning_yieldThenBusy(monitor));
            }
        }
        
        return chs;
    }
    
    private static ArrayList<MyCondilockHome> newCHS_blocking() {
        ArrayList<MyCondilockHome> benchables = new ArrayList<MyCondilockHome>();
        
        if (true) {
            benchables.add(newCH_MonitorCondilock_blocking());
        }
        if (true) {
            benchables.add(newCH_LockCondilock_blocking());
        }
        if (true) {
            for (boolean monitor : new boolean[]{true,false}) {
                benchables.add(newCH_SmartBlockingCondilock_blocking(monitor));
                benchables.add(newCH_SmartBlockingCondilock_blockingElusive(monitor));
            }
        }
        
        return benchables;
    }
    
    /*
     * spinning
     */
    
    private static MyCondilockHome newCH_SpinningCondilock_spinning() {
        return new MyCondilockHome(
                new SpinningCondilock(),
                "spinning (yields)");
    }
    
    private static MyCondilockHome newCH_SmartSpinningCondilock_spinning() {
        return new MyCondilockHome(
                new SmartSpinningCondilock(
                        0L, // nbrOfInitialBusySpins
                        Integer.MAX_VALUE, // bigYieldThresholdNS
                        0L), // nbrOfBusySpinsAfterSmallYield
                "spinning (yields)");
    }

    private static MyCondilockHome newCH_SmartBlockingCondilock_spinning_busy(boolean monitor) {
        return newCH_SmartBlockingCondilock_spinning(monitor, true, false/*should have no impact*/);
    }
    
    private static MyCondilockHome newCH_SmartBlockingCondilock_spinning_yields(boolean monitor) {
        return newCH_SmartBlockingCondilock_spinning(monitor, false, false);
    }

    private static MyCondilockHome newCH_SmartBlockingCondilock_spinning_yieldThenBusy(boolean monitor) {
        return newCH_SmartBlockingCondilock_spinning(monitor, false, true);
    }

    /*
     * blocking
     */
    
    private static MyCondilockHome newCH_MonitorCondilock_blocking() {
        return new MyCondilockHome(
                new MonitorCondilock(),
                "blocking");
    }

    private static MyCondilockHome newCH_LockCondilock_blocking() {
        return new MyCondilockHome(
                new LockCondilock(),
                "blocking");
    }
    
    private static MyCondilockHome newCH_SmartBlockingCondilock_blocking(boolean monitor) {
        return newCH_SmartBlockingCondilock_Blocking(monitor, false);
    }
    
    private static MyCondilockHome newCH_SmartBlockingCondilock_blockingElusive(boolean monitor) {
        return newCH_SmartBlockingCondilock_Blocking(monitor, true);
    }
    
    /*
     * 
     */

    private static MyCondilockHome newCH_SmartBlockingCondilock_spinning(
            boolean monitor,
            boolean busyOnly,
            boolean busyAfterYield) {
        String description = "";
        if (busyOnly) {
            description += "spinning (busy)";
        } else {
            if (busyAfterYield) {
                // Should be faster, if not just one yield.
                description += "spinning (yield then busy)";
            } else {
                description += "spinning (yields)";
            }
        }
        return new MyCondilockHome(
                newSmartBlockingCondilock(
                        monitor,
                        //
                        (busyOnly ? Long.MAX_VALUE : 0L), // nbrOfInitialBusySpins
                        Integer.MAX_VALUE, // bigYieldThresholdNS (all small)
                        (busyAfterYield ? Long.MAX_VALUE : 0L), // nbrOfBusySpinsAfterSmallYield
                        // Will use min(INFINITE,specified timeout)
                        INFINITE_NS, // maxSpinningWaitNS
                        //
                        false, // elusiveInLockSignaling
                        Long.MAX_VALUE, // initialBlockingWaitChunkNS
                        0.0), // blockingWaitChunkIncreaseRate
                        description);
    }

    private static MyCondilockHome newCH_SmartBlockingCondilock_Blocking(
            boolean monitor,
            boolean elusiveInLockSignaling) {
        String description = "";
        if (elusiveInLockSignaling) {
            // Should be slower (volatile set).
            description += "blocking elusive";
        } else {
            description += "blocking";
        }
        return new MyCondilockHome(
                newSmartBlockingCondilock(
                        monitor,
                        //
                        0L, // nbrOfInitialBusySpins
                        Integer.MAX_VALUE, // bigYieldThresholdNS
                        0L, // nbrOfBusySpinsAfterSmallYield
                        0L, // maxSpinningWaitNS
                        //
                        elusiveInLockSignaling, // elusiveInLockSignaling
                        // Not to have to signal.
                        Long.MAX_VALUE, // initialBlockingWaitChunkNS
                        0.0), // blockingWaitChunkIncreaseRate
                        description);
    }
    
    /*
     * 
     */

    private static InterfaceCondilock newSmartBlockingCondilock(
            boolean monitor,
            //
            long nbrOfInitialBusySpins,
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield,
            long maxSpinningWaitNS,
            //
            boolean elusiveInLockSignaling,
            long initialBlockingWaitChunkNS,
            double blockingWaitChunkIncreaseRate) {
        if (monitor) {
            return new SmartMonitorCondilock(
                    nbrOfInitialBusySpins,
                    bigYieldThresholdNS,
                    nbrOfBusySpinsAfterSmallYield,
                    maxSpinningWaitNS,
                    //
                    elusiveInLockSignaling,
                    initialBlockingWaitChunkNS,
                    blockingWaitChunkIncreaseRate);
        } else {
            return new SmartLockCondilock(
                    nbrOfInitialBusySpins,
                    bigYieldThresholdNS,
                    nbrOfBusySpinsAfterSmallYield,
                    maxSpinningWaitNS,
                    //
                    elusiveInLockSignaling,
                    initialBlockingWaitChunkNS,
                    blockingWaitChunkIncreaseRate);
        }
    }
}
