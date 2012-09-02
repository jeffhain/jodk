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
package net.jodk.threading.ringbuffers.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.jodk.lang.Unchecked;
import net.jodk.test.TestUtils;
import net.jodk.threading.HeisenLogger;
import net.jodk.threading.InterfaceRejectedExecutionHandler;
import net.jodk.threading.locks.InterfaceCondilock;
import net.jodk.threading.locks.MonitorCondilock;
import junit.framework.TestCase;

public class RingBufferExecutorServicesTest extends TestCase {

    /*
     * Only relatively basic tests, for our implementation
     * doesn't have much code.
     */
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean DEBUG = false;
    
    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private enum MyExecuteType {
        tryExecute,
        tryExecute_long,
        execute,
    }
    
    private static class MyRBESHome {
        final RingBufferExecutorService rbes;
        final boolean multicast;
        final int nbrOfPubSeqCounters;
        final int nbrOfWorkers;
        public MyRBESHome(
                RingBufferExecutorService rbes,
                boolean multicast,
                int nbrOfPubSeqCounters,
                int nbrOfWorkers) {
            this.rbes = rbes;
            this.multicast = multicast;
            this.nbrOfPubSeqCounters = nbrOfPubSeqCounters;
            this.nbrOfWorkers = nbrOfWorkers;
        }
        public String toString() {
            return "[rbes="+rbes.getClass().getSimpleName()
                    +",multicast="+this.multicast
                    +",nbrOfPubSeqCounters="+this.nbrOfPubSeqCounters
                    +",nbrOfWorkers="+this.nbrOfWorkers+"]";
        }
    }
    
    private static class MyRunnable implements Runnable {
        static final AtomicLong idProvider = new AtomicLong(1);
        final long id = idProvider.getAndIncrement();
        final AtomicBoolean block;
        volatile boolean executed;
        public MyRunnable() {
            this(true);
        }
        public MyRunnable(boolean block) {
            this.block = new AtomicBoolean(block);
        }
        @Override
        public String toString() {
            return Long.toString(this.id);
        }
        @Override
        public void run() {
            this.executed = true;
            synchronized (block) {
                while (block.get()) {
                    try {
                        block.wait();
                    } catch (InterruptedException e) {
                        // (happens in case of shutdownNow())
                        // We keep blocking even if interrupted,
                        // to make tests easier to understand
                        // (runnables unblocked only explicitly).
                        // quiet
                    }
                }
            }
        }
    }
    
    private static class MyREH implements InterfaceRejectedExecutionHandler {
        volatile ExecutorService es;
        final Vector<Runnable> rejected = new Vector<Runnable>();
        public void reset() {
            this.es = null;
            this.rejected.clear();
        }
        /**
         * Synchronized for it might be called concurrently.
         */
        @Override
        public synchronized void onRejectedExecution(ExecutorService executor, Runnable... runnables) {
            this.es = executor;
            for (Runnable runnable : runnables) {
                this.rejected.add(runnable);
            }
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private static final long TOLERANCE_MS = 100L;

    private static final int MAX_NBR_OF_ATOMIC_COUNTERS = 2;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public void test_tryExecute_Runnable() {
        test_execute_method(MyExecuteType.tryExecute);
    }
    
    public void test_tryExecute_Runnable_long() {
        test_execute_method(MyExecuteType.tryExecute_long);
    }
    
    public void test_execute_Runnable() {
        test_execute_method(MyExecuteType.execute);
    }
    
    /*
     * 
     */
    
    public void test_tryExecute_Runnable_and_shutdown() {
        test_execute_method_and_shut_down(MyExecuteType.tryExecute, false);
    }
    
    public void test_tryExecute_Runnable_and_shutdownNow() {
        test_execute_method_and_shut_down(MyExecuteType.tryExecute, true);
    }
    
    public void test_tryExecute_Runnable_long_and_shutdown() {
        test_execute_method_and_shut_down(MyExecuteType.tryExecute_long, false);
    }
    
    public void test_tryExecute_Runnable_long_and_shutdownNow() {
        test_execute_method_and_shut_down(MyExecuteType.tryExecute_long, true);
    }
    
    public void test_execute_Runnable_and_shutdown() {
        test_execute_method_and_shut_down(MyExecuteType.execute, false);
    }
    
    public void test_execute_Runnable_and_shutdownNow() {
        test_execute_method_and_shut_down(MyExecuteType.execute, true);
    }
    
    /*
     * 
     */
    
    public void test_execute_method(final MyExecuteType executeType) {
        final int bufferCapacity = 4;
        final ExecutorService executor = Executors.newCachedThreadPool();
        final MyREH reh = new MyREH();
        
        for (final MyRBESHome rbesh : newRBESHs(bufferCapacity, executor, reh)) {
            final RingBufferExecutorService rbes = rbesh.rbes;
            reh.reset();
            
            if (DEBUG) {
                HeisenLogger.log("--------------------------------------------------------------------------------");
                HeisenLogger.log("executeType = "+executeType);
                HeisenLogger.log("rbesh = "+rbesh);
                HeisenLogger.flushPendingLogsAndStream();
            }
            
            /*
             * null
             */
            
            try {
                executeOrTry(rbes, null, executeType);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
            
            /*
             * Submission when full, and then execution
             * in case of blocking execute method.
             */

            // Vector for it might be used from multiple threads.
            // Also using toArray() before iterations, to avoid
            // eventual ConcurrentModificationException.
            final Vector<MyRunnable> runnables = new Vector<MyRunnable>();

            /*
             * Making sure the ring buffer is full.
             */

            fillRingBuffer(rbes, runnables);

            /*
             * New runnable when ring buffer is full,
             * then unblocking first runnable to allow
             * for blocking execute methods to submit
             * successfully.
             */

            final AtomicBoolean executeDone = new AtomicBoolean();
            Thread tmpThread = null;
            if (isTryExecute(executeType)) {
                // Execute method will return ASAP,
                // with planning not done.
            } else {
                tmpThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Should be enough for execute to be called and block.
                        TestUtils.sleepMSInChunks(TOLERANCE_MS);
                        assertFalse(executeDone.get());

                        /*
                         * Unblocking runnables (unblocking just first one
                         * is not enough, since ring buffer might process
                         * events in batch, and only update workers max
                         * passed sequences after the batch).
                         */

                        unblockRunnables(runnables);
                    }
                });
                tmpThread.start();
            }
            // This one won't block (and we won't try to unblock it either).
            final MyRunnable runnable = new MyRunnable(false);

            boolean didPlanOrRej = executeOrTry(rbes, runnable, executeType, runnables);
            executeDone.set(true);

            if (isTryExecute(executeType)) {
                assertFalse(didPlanOrRej);

                unblockRunnables(runnables);
            } else {
                try {
                    tmpThread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            // Should be enough for runnables to be executed.
            TestUtils.sleepMSInChunks(TOLERANCE_MS);

            assertTrue(executeDone.get());
            for (Object object : runnables.toArray()) {
                assertTrue(((MyRunnable)object).executed);
            }
            
            assertNull(reh.es);
            assertEquals(0, reh.rejected.size());
            
            // To free executor's workers.
            rbes.shutdown();
        }
        
        Unchecked.shutdownAndAwaitTermination(executor);
    }
    
    public void test_execute_method_and_shut_down(
            final MyExecuteType executeType,
            final boolean useShutdownNow) {
        final int bufferCapacity = 4;
        final ExecutorService executor = Executors.newCachedThreadPool();
        final MyREH reh = new MyREH();
        
        for (final MyRBESHome rbesh : newRBESHs(bufferCapacity, executor, reh)) {
            final RingBufferExecutorService rbes = rbesh.rbes;
            reh.reset();

            if (DEBUG) {
                HeisenLogger.log("--------------------------------------------------------------------------------");
                HeisenLogger.log("executeType = "+executeType);
                HeisenLogger.log("useShutdownNow = "+useShutdownNow);
                HeisenLogger.log("rbesh = "+rbesh);
                HeisenLogger.flushPendingLogsAndStream();
            }

            /*
             * Submission when full, and then shut down.
             */

            // Vector for it might be used from multiple threads.
            final Vector<MyRunnable> runnables = new Vector<MyRunnable>();

            /*
             * Making sure the ring buffer is full.
             */

            fillRingBuffer(rbes, runnables);

            /*
             * For blocking execute methods, new runnable
             * when ring buffer is full, then shut down
             * while the execute method is blocking.
             */

            final AtomicBoolean runnablesUnblocked = new AtomicBoolean();
            final AtomicBoolean executeDone = new AtomicBoolean();
            Thread tmpThread = null;
            if (isTryExecute(executeType)) {
                /*
                 * shut down and unblocking runnables if multicast,
                 * to unblock shut down
                 */

                shutDownAndCheckAndUnblockIfMulticast(
                        rbesh,
                        useShutdownNow,
                        runnables,
                        runnablesUnblocked);
                // Execute method will return ASAP,
                // with planning not done.
            } else {
                tmpThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Should be enough for execute to be called and block.
                        TestUtils.sleepMSInChunks(TOLERANCE_MS);
                        assertFalse(executeDone.get());

                        /*
                         * shut down and eventually unblocking runnables
                         * to unblock shut down
                         */

                        shutDownAndCheckAndUnblockIfMulticast(
                                rbesh,
                                useShutdownNow,
                                runnables,
                                runnablesUnblocked);

                        if ((executeType == MyExecuteType.execute)
                                && (!runnablesUnblocked.get())
                                && (!useShutdownNow)) {
                            /*
                             * Need to unblock runnables now,
                             * else claimSequence() might block,
                             * waiting for a readable sequence to
                             * be read or rejected, in treatments
                             * for lost acquired sequences, to be
                             * able to properly reject its acquired
                             * sequence, and execute will never return
                             * (in spite of shutdown()).
                             */
                            unblockRunnables(runnables);
                        }
                    }
                });
                tmpThread.start();
            }
            final MyRunnable notExecuted1 = new MyRunnable();
            boolean didPlanOrRej = executeOrTry(rbes, notExecuted1, executeType);
            executeDone.set(true);

            // Shut down has now been called:
            // - synchronously if tryExecuteXXX,
            // - to unblock execute(Runnable) otherwise.
            assertTrue(rbes.isShutdown());

            if (notExecuted1.executed) {
                // Rare because workers need to wake up and process
                // some runnables before trying to pick last submitted one,
                // which is quickly rejected by publish if needed.
                HeisenLogger.log("rare : runnable executed concurrently with shut down");
            } else {
                checkRejectionOrNot(executeType, rbes, didPlanOrRej, 0, reh, notExecuted1);
            }

            /*
             * Waiting for termination (so that no worker
             * risks to pick a subsequently submitted runnable
             * before it gets rejected.
             */

            // Waiting for eventual concurrent unblocking to be done.
            if (tmpThread != null) {
                try {
                    tmpThread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            
            // Unblocking runnables if needed,
            // to allow for termination.
            if (!runnablesUnblocked.get()) {
                unblockRunnables(runnables);
            }
            
            // Should be enough for RBES to terminate.
            TestUtils.sleepMSInChunks(TOLERANCE_MS);

            assertTrue(rbes.isTerminated());

            /*
             * 
             */

            if (useShutdownNow) {
                /*
                 * No runnable executed after shutdownNow(),
                 * even if has been successfully submitted
                 * (execution planned) before.
                 */
                for (int i=0;i<runnables.size();i++) {
                    final boolean callExpected = (i < rbesh.nbrOfWorkers);
                    assertEquals(callExpected, runnables.get(i).executed);
                }
            } else {
                /*
                 * These having been successfully submitted
                 * prior to shut down, they'll all be executed.
                 */
                for (Object object : runnables.toArray()) {
                    assertTrue(((MyRunnable)object).executed);
                }
            }

            /*
             * Ring buffer empty, but shut down, and terminated
             * (no risk of worker picking a runnable before
             * rejection): runnable won't be executed.
             */

            final int previousRejSize = reh.rejected.size();
            
            final MyRunnable notExecuted2 = new MyRunnable();
            didPlanOrRej = executeOrTry(rbes, notExecuted2, executeType);

            assertFalse(notExecuted2.executed);
            checkRejectionOrNot(executeType, rbes, didPlanOrRej, previousRejSize, reh, notExecuted2);
        }
        
        Unchecked.shutdownAndAwaitTermination(executor);
    }
    
    /*
     * 
     */
    
    public void test_interruptWorkers() {
        final ExecutorService executor = Executors.newCachedThreadPool();
        for (final MyRBESHome rbesh : newRBESHs(4, executor, new MyREH())) {
            final RingBufferExecutorService rbes = rbesh.rbes;

            if (DEBUG) {
                HeisenLogger.log("--------------------------------------------------------------------------------");
                HeisenLogger.log("rbesh = "+rbesh);
                HeisenLogger.flushPendingLogsAndStream();
            }

            /*
             * Runnable that blocks until interrupted.
             */
            
            final AtomicBoolean workerInterrupted = new AtomicBoolean();
            rbes.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (workerInterrupted) {
                        while (true) {
                            try {
                                workerInterrupted.wait();
                            } catch (InterruptedException e) {
                                workerInterrupted.set(true);
                                workerInterrupted.notifyAll();
                                break;
                            }
                        }
                    }
                }
            });
            
            // Should be enough for runnable to start blocking.
            TestUtils.sleepMSInChunks(TOLERANCE_MS);
            
            /*
             * Interrupting.
             */
            
            if (rbesh.multicast) {
                ((MRBExecutorService)rbes).interruptWorkers();
            } else {
                ((URBExecutorService)rbes).interruptWorkers();
            }

            /*
             * Waiting for interruption to unblock runnable.
             */
            
            final long timeoutNS = 10L * (TOLERANCE_MS * 1000L * 1000L);
            final long startNS = System.nanoTime();
            synchronized (workerInterrupted) {
                while ((!workerInterrupted.get()) && (System.nanoTime() - startNS < timeoutNS)) {
                    try {
                        workerInterrupted.wait(TOLERANCE_MS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            assertTrue(workerInterrupted.get());
            
            /*
             * 
             */
            
            // To free executor's workers.
            rbes.shutdown();
        }
        
        Unchecked.shutdownAndAwaitTermination(executor);
    }
    
    public void test_shutdown() {
        // already covered
    }

    public void test_shutdownNow() {
        // only special cases, general case already covered

        final ExecutorService executor = Executors.newCachedThreadPool();
        for (final MyRBESHome rbesh : newRBESHs(4, executor, new MyREH())) {
            final RingBufferExecutorService rbes = rbesh.rbes;

            if (DEBUG) {
                HeisenLogger.log("--------------------------------------------------------------------------------");
                HeisenLogger.log("rbesh = "+rbesh);
                HeisenLogger.flushPendingLogsAndStream();
            }
            
            /*
             * Result being mutable, must not reuse it if empty.
             */

            List<Runnable> aborted1 = rbes.shutdownNow();
            assertEquals(0, aborted1.size());
            aborted1.add(null);

            List<Runnable> aborted2 = rbes.shutdownNow();
            assertEquals(0, aborted2.size());
            assertNotSame(aborted1, aborted2);
        }
        Unchecked.shutdownAndAwaitTermination(executor);
    }

    public void test_isShutdown() {
        final ExecutorService executor = Executors.newCachedThreadPool();
        for (boolean useShutdownNow : new boolean[]{false,true}) {
            for (final MyRBESHome rbesh : newRBESHs(4, executor, new MyREH())) {
                final RingBufferExecutorService rbes = rbesh.rbes;

                if (DEBUG) {
                    HeisenLogger.log("--------------------------------------------------------------------------------");
                    HeisenLogger.log("rbesh = "+rbesh);
                    HeisenLogger.flushPendingLogsAndStream();
                }

                assertFalse(rbes.isShutdown());
                if (useShutdownNow) {
                    rbes.shutdownNow();
                } else {
                    rbes.shutdown();
                }
                assertTrue(rbes.isShutdown());
            }
        }
        Unchecked.shutdownAndAwaitTermination(executor);
    }

    public void test_isTerminated_and_awaitTermination_long_TimeUnit() {
        final ExecutorService executor = Executors.newCachedThreadPool();
        for (boolean delayedTermination : new boolean[]{false,true}) {
            for (boolean useShutdownNow : new boolean[]{false,true}) {
                for (final MyRBESHome rbesh : newRBESHs(4, executor, new MyREH())) {
                    
                    if (DEBUG) {
                        HeisenLogger.log("--------------------------------------------------------------------------------");
                        HeisenLogger.log("delayedTermination = "+delayedTermination);
                        HeisenLogger.log("useShutdownNow = "+useShutdownNow);
                        HeisenLogger.log("rbesh = "+rbesh);
                        HeisenLogger.flushPendingLogsAndStream();
                    }
                    
                    if (delayedTermination && rbesh.multicast) {
                        // Not bothering with blocking shut down methods.
                        continue;
                    }
                    
                    final RingBufferExecutorService rbes = rbesh.rbes;
                    
                    assertFalse(rbes.isTerminated());
                    
                    final Vector<MyRunnable> runnables = new Vector<MyRunnable>();
                    
                    if (delayedTermination) {
                        fillRingBuffer(rbes, runnables);
                        
                        shutDownAndCheck(rbesh, useShutdownNow, runnables);
                        
                        // Waiting a bit, in case it can help produce a bug.
                        TestUtils.sleepMSInChunks(TOLERANCE_MS);
                        // Not yet terminated, for workers are still busy
                        // (runnables blocked).
                        assertFalse(rbes.isTerminated());
                        try {
                            assertFalse(rbes.awaitTermination(1L, TimeUnit.NANOSECONDS));
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                        // Unblocking workers.
                        unblockRunnables(runnables);
                        
                        // Should be enough for termination to occur.
                        TestUtils.sleepMSInChunks(TOLERANCE_MS);
                    } else {
                        shutDownAndCheck(rbesh, useShutdownNow, runnables);
                    }
                    
                    assertTrue(rbes.isTerminated());
                    try {
                        assertTrue(rbes.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        Unchecked.shutdownAndAwaitTermination(executor);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private static boolean isTryExecute(MyExecuteType executeType) {
        return (executeType == MyExecuteType.tryExecute)
                || (executeType == MyExecuteType.tryExecute_long);
    }
    
    /**
     * Execute method with timeout blocks 1ns, and rethrows if interrupted.
     * 
     * @return True if did plan execution or reject runnable
     *         (always true for execute(Runnable)).
     */
    private static boolean executeOrTry(
            RingBufferExecutorService rbes,
            Runnable runnable,
            MyExecuteType executeType) {
        boolean didPlanOrRej = false;
        if (executeType == MyExecuteType.tryExecute) {
            didPlanOrRej = rbes.tryExecute(runnable);
        } else if (executeType == MyExecuteType.tryExecute_long) {
            try {
                didPlanOrRej = rbes.tryExecute(runnable, 1L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else if (executeType == MyExecuteType.execute) {
            rbes.execute(runnable);
            didPlanOrRej = true;
        } else {
            throw new UnsupportedOperationException();
        }
        return didPlanOrRej;
    }
    
    /**
     * Adds runnable to runnables if did plan execution.
     * 
     * Execute method with timeout blocks 1ns, and rethrows if interrupted.
     * 
     * @return True if did plan execution or reject runnable
     *         (always true for execute(Runnable)).
     */
    private static boolean executeOrTry(
            RingBufferExecutorService rbes,
            Runnable runnable,
            MyExecuteType executeType,
            Vector<MyRunnable> runnables) {
        boolean didPlanOrRej = executeOrTry(rbes, runnable, executeType);
        if (didPlanOrRej) {
            runnables.add((MyRunnable)runnable);
        }
        return didPlanOrRej;
    }
    
    private static void fillRingBuffer(
            RingBufferExecutorService rbes,
            Vector<MyRunnable> runnables) {
        /*
         * Adding as many runnables as there are blocking
         * might not work, since some implementations might
         * think they are full while they are not, due to
         * max passed sequence update policy, so we use
         * tryExecute(Runnable).
         */

        // Need to loop twice, for first loop might
        // stop when ring buffer is full, but some
        // added runnables are being picked by worker
        // threads, which makes it no longer full.
        for (int k=0;k<2;k++) {
            while (true) {
                MyRunnable runnable = new MyRunnable();
                // Trying multiple times, to cover all possible
                // free slots in case of non-monotonic claims.
                boolean didPlan = false;
                for (int i=0;i<MAX_NBR_OF_ATOMIC_COUNTERS;i++) {
                    didPlan = rbes.tryExecute(runnable);
                    if (didPlan) {
                        break;
                    }
                }
                if (didPlan) {
                    runnables.add(runnable);
                } else {
                    break;
                }
            }
            
            if (k == 0) {
                // Should be enough for each eventual
                // free workers to pick a runnable.
                TestUtils.sleepMSInChunks(TOLERANCE_MS);
            }
        }
    }
    
    private static void unblockRunnable(MyRunnable runnable) {
        runnable.block.set(false);
        synchronized (runnable.block) {
            runnable.block.notifyAll();
        }
    }

    private static void unblockRunnables(Vector<MyRunnable> runnables) {
        for (Object object : runnables.toArray()) {
            unblockRunnable((MyRunnable)object);
        }
    }

    private static void shutDownAndCheck(
            MyRBESHome rbesh,
            boolean useShutdownNow,
            Vector<MyRunnable> runnables) {
        final RingBufferExecutorService rbes = rbesh.rbes;
        if (useShutdownNow) {
            List<Runnable> aborted = rbes.shutdownNow();
            final int expectedNbrOfAborted = Math.max(0, runnables.size() - rbesh.nbrOfWorkers);
            assertEquals(expectedNbrOfAborted, aborted.size());
            for (int i=0;i<expectedNbrOfAborted;i++) {
                assertSame(runnables.get(i + rbesh.nbrOfWorkers), aborted.get(i));
            }
        } else {
            rbes.shutdown();
        }
    }
    
    /**
     * Returns only after shut down method completed.
     */
    private static void shutDownAndCheckAndUnblockIfMulticast(
            final MyRBESHome rbesh,
            boolean useShutdownNow,
            final Vector<MyRunnable> runnables,
            final AtomicBoolean runnablesUnblocked) {
        final AtomicBoolean shutDownCompleted = new AtomicBoolean();
        Thread tmpThread = null;
        if (rbesh.multicast) {
            // Need to unblock runnables, for shut down will wait
            // for blocked workers to make progress.
            // Must be done after shut down has been called and started to wait,
            // else runnable to reject might be executed.
            tmpThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    // Should be enough for shut down
                    // to start waiting.
                    TestUtils.sleepMSInChunks(TOLERANCE_MS);
                    assertFalse(shutDownCompleted.get());
                    unblockRunnables(runnables);
                    runnablesUnblocked.set(true);
                }
            });
            tmpThread.start();
        }
        shutDownAndCheck(rbesh, useShutdownNow, runnables);
        shutDownCompleted.set(true);

        if (tmpThread != null) {
            try {
                tmpThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    private static void checkRejectionOrNot(
            MyExecuteType executeType,
            RingBufferExecutorService rbes,
            boolean didPlanOrRej,
            int previousRejSize,
            MyREH reh,
            MyRunnable notExecuted) {
        if (isTryExecute(executeType)) {
            if (didPlanOrRej) {
                // Checking rejection.
                assertEquals(rbes, reh.es);
                assertEquals(previousRejSize+1, reh.rejected.size());
                assertTrue(reh.rejected.contains(notExecuted));
            } else {
                // Checking no rejection.
                assertEquals(previousRejSize, reh.rejected.size());
            }
        } else {
            // Always rejected (since execute(Runnable)
            // doesn't return a boolean saying whether
            // it has been planned for execution or not).
            assertTrue(didPlanOrRej);
            assertEquals(rbes, reh.es);
            assertEquals(previousRejSize+1, reh.rejected.size());
            assertTrue(reh.rejected.contains(notExecuted));
        }
    }
    
    /*
     * 
     */
    
    private static ArrayList<MyRBESHome> newRBESHs(
            final int bufferCapacity,
            final Executor executor,
            final InterfaceRejectedExecutionHandler rejectedExecutionHandler) {
        final ArrayList<MyRBESHome> list = new ArrayList<MyRBESHome>();
        
        final boolean readLazySets = false;
        final boolean writeLazySets = false;
        final InterfaceCondilock readWaitCondilock = new MonitorCondilock();
        final InterfaceCondilock writeWaitCondilock = new MonitorCondilock();
        
        if (true) {
            for (int pubSeqNbrOfAtomicCounters : new int[]{0,1,2}) {
                for (int nbrOfWorkers : new int[]{1,2}) {
                    RingBufferExecutorService rbes = new URBExecutorService(
                            bufferCapacity,
                            pubSeqNbrOfAtomicCounters,
                            readLazySets,
                            writeLazySets,
                            readWaitCondilock,
                            writeWaitCondilock,
                            executor,
                            //
                            nbrOfWorkers,
                            rejectedExecutionHandler);
                    list.add(
                            new MyRBESHome(
                                    rbes,
                                    false, // multicast
                                    pubSeqNbrOfAtomicCounters,
                                    nbrOfWorkers));
                }
            }
        }
        
        if (true) {
            for (boolean singlePublisher : new boolean[]{true,false}) {
                RingBufferExecutorService rbes = new MRBExecutorService(
                        bufferCapacity,
                        singlePublisher,
                        readLazySets,
                        writeLazySets,
                        readWaitCondilock,
                        writeWaitCondilock,
                        executor,
                        //
                        rejectedExecutionHandler);
                list.add(
                        new MyRBESHome(
                                rbes,
                                true, // multicast
                                (singlePublisher ? 0 : 1),
                                1)); // nbrOfWorkers
            }
        }

        return list;
    }
}
