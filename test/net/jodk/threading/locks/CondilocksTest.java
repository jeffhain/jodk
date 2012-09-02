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
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.jodk.lang.InterfaceBooleanCondition;
import net.jodk.lang.NumbersUtils;
import net.jodk.lang.Unchecked;

import junit.framework.TestCase;

/**
 * Test some methods of known condilocks.
 */
public class CondilocksTest extends TestCase {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    /**
     * To avoid wrapping.
     */
    private static final long INITIAL_NANO_TIME_NS = System.nanoTime();
    
    private static final long TOLERANCE_MS = 100L;
    private static final long TOLERANCE_NS = TOLERANCE_MS * 1000L * 1000L;
    
    private static final long SMALL_WAIT_MS = 2 * TOLERANCE_MS;
    private static final long SMALL_WAIT_NS = 2 * TOLERANCE_NS;

    /**
     * One hour, to make sure we wait for long enough for the tests,
     * but also don't use optimized treatments that would simply consider
     * that the wait is eternal, and would not bother timing it.
     */
    private static final long LARGE_WAIT_NS = 3600L * 1000L * 1000L * 1000L;

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    private interface MyInterfaceWaiter {
        public void awaitInLock() throws InterruptedException;
        public void stopWaitWithSignal();
    }

    /*
     * 
     */
    
    private static class MyWaiter_await implements MyInterfaceWaiter {
        private final InterfaceCondilock condilock;
        public MyWaiter_await(final InterfaceCondilock condilock) {
            this.condilock = condilock;
        }
        @Override
        public void awaitInLock() throws InterruptedException {
            try {
                this.condilock.callInLock(new Callable<Void>() {
                    @Override
                    public Void call() throws InterruptedException {
                        condilock.await();
                        return null;
                    }
                });
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        @Override
        public void stopWaitWithSignal() {
            this.condilock.signalAllInLock();
        }
    }

    private static class MyWaiter_awaitUninterruptibly implements MyInterfaceWaiter {
        private final InterfaceCondilock condilock;
        public MyWaiter_awaitUninterruptibly(final InterfaceCondilock condilock) {
            this.condilock = condilock;
        }
        @Override
        public void awaitInLock() throws InterruptedException {
            this.condilock.runInLock(new Runnable() {
                @Override
                public void run() {
                    // To check that interruption status is restored on normal completion,
                    // supposing noone else can clear it than actual wait method,
                    // and not to add this check in other test methods.
                    final boolean wasInterrupted = Thread.currentThread().isInterrupted();
                    condilock.awaitUninterruptibly();
                    if (wasInterrupted) {
                        assertTrue(Thread.interrupted());
                    }
                }
            });
        }
        @Override
        public void stopWaitWithSignal() {
            this.condilock.signalAllInLock();
        }
    }

    /*
     * 
     */

    private static class MyWaiter_awaitWhileFalseInLockUninterruptibly implements MyInterfaceWaiter {
        private final InterfaceCondilock condilock;
        private volatile boolean stop;
        public MyWaiter_awaitWhileFalseInLockUninterruptibly(final InterfaceCondilock condilock) {
            this.condilock = condilock;
        }
        @Override
        public void awaitInLock() {
            this.condilock.awaitWhileFalseInLockUninterruptibly(
                    new InterfaceBooleanCondition() {
                        @Override
                        public boolean isTrue() {
                            return stop;
                        }
                    });
        }
        @Override
        public void stopWaitWithSignal() {
            this.stop = true;
            this.condilock.signalAllInLock();
        }
    }

    private static class MyWaiter_awaitNanosWhileFalseInLock implements MyInterfaceWaiter {
        private final InterfaceCondilock condilock;
        private final long timeoutNS;
        private volatile boolean stop;
        public MyWaiter_awaitNanosWhileFalseInLock(
                final InterfaceCondilock condilock,
                long timeoutNS) {
            this.condilock = condilock;
            this.timeoutNS = timeoutNS;
        }
        @Override
        public void awaitInLock() throws InterruptedException {
            this.condilock.awaitNanosWhileFalseInLock(
                    new InterfaceBooleanCondition() {
                        @Override
                        public boolean isTrue() {
                            return stop;
                        }
                    },
                    this.timeoutNS);
        }
        @Override
        public void stopWaitWithSignal() {
            this.stop = true;
            this.condilock.signalAllInLock();
        }
    }

    private static class MyWaiter_awaitUntilNanosTimeoutTimeWhileFalseInLock implements MyInterfaceWaiter {
        private final InterfaceCondilock condilock;
        private final long timeoutNS;
        private volatile boolean stop;
        public MyWaiter_awaitUntilNanosTimeoutTimeWhileFalseInLock(
                final InterfaceCondilock condilock,
                long timeoutNS) {
            this.condilock = condilock;
            this.timeoutNS = timeoutNS;
        }
        @Override
        public void awaitInLock() throws InterruptedException {
            this.condilock.awaitUntilNanosTimeoutTimeWhileFalseInLock(
                    new InterfaceBooleanCondition() {
                        @Override
                        public boolean isTrue() {
                            return stop;
                        }
                    },
                    NumbersUtils.plusBounded(this.condilock.timeoutTimeNS(), this.timeoutNS));
        }
        @Override
        public void stopWaitWithSignal() {
            this.stop = true;
            this.condilock.signalAllInLock();
        }
    }

    private static class MyWaiter_awaitUntilNanosWhileFalseInLock implements MyInterfaceWaiter {
        private final InterfaceCondilock condilock;
        private final long timeoutNS;
        private volatile boolean stop;
        public MyWaiter_awaitUntilNanosWhileFalseInLock(
                final InterfaceCondilock condilock,
                long timeoutNS) {
            this.condilock = condilock;
            this.timeoutNS = timeoutNS;
        }
        @Override
        public void awaitInLock() throws InterruptedException {
            this.condilock.awaitUntilNanosWhileFalseInLock(
                    new InterfaceBooleanCondition() {
                        @Override
                        public boolean isTrue() {
                            return stop;
                        }
                    },
                    // TODO Test might fail if system time jumps around, since deadline time
                    // is (most likely) system time.
                    // Could have a thread running in parallel, checking that System.nanoTime()
                    // and System.currentTimeMillis() advance at same speed, and invalidating
                    // test if a jump is detected during it.
                    NumbersUtils.plusBounded(this.condilock.deadlineTimeNS(), this.timeoutNS));
        }
        @Override
        public void stopWaitWithSignal() {
            this.stop = true;
            this.condilock.signalAllInLock();
        }
    }

    /*
     * 
     */
    
    /**
     * To lower the amount of code in test treatments.
     */
    private static class MyWaiterHelper {
        private final MyInterfaceWaiter waiter;
        private final AtomicReference<Thread> runningThread = new AtomicReference<Thread>();
        
        private volatile boolean stopIfInterruptedOnNormalWaitEnd = false;
        private volatile boolean rewaitIfNormalWaitEndAndDidntStop = false;
        
        private volatile boolean normalWaitEndEncountered = false;
        private volatile boolean interruptedExceptionThrown = false;
        private volatile boolean interruptedStatusSetOnLastNormalWaitEnd = false;
        private final AtomicLong actualEndDateNS = new AtomicLong(Long.MAX_VALUE);
        public MyWaiterHelper(final MyInterfaceWaiter waiter) {
            this.waiter = waiter;
        }
        public void launchWait(final Executor executor) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    runningThread.set(Thread.currentThread());
                    try {
                        do {
                            waiter.awaitInLock();
                            normalWaitEndEncountered = true;
                            if (stopIfInterruptedOnNormalWaitEnd) {
                                if (Thread.interrupted()) {
                                    interruptedStatusSetOnLastNormalWaitEnd = true;
                                    break;
                                }
                            } else {
                                interruptedStatusSetOnLastNormalWaitEnd = Thread.currentThread().isInterrupted();
                            }
                        } while (rewaitIfNormalWaitEndAndDidntStop);
                    } catch (InterruptedException e) {
                        interruptedExceptionThrown = true;
                    }
                    actualEndDateNS.set(nowNS());
                }
            });
        }
        /**
         * @return Interruption date, in nanoseconds.
         */
        public long interruptRunnerWhenKnown() {
            Thread runner;
            while ((runner = this.runningThread.get()) == null) {
                Thread.yield();
            }
            final long interruptDateNS = nowNS();
            runner.interrupt();
            return interruptDateNS;
        }
        public long getEndDateNSBlocking() {
            long endDateNS;
            while ((endDateNS = this.actualEndDateNS.get()) == Long.MAX_VALUE) {
                Thread.yield();
            }
            return endDateNS;
        }
        /**
         * @return True if (possibly multiple) waiting(s) has terminated,
         *         false otherwise (possibly because we didn't start to wait yet).
         */
        public boolean waitingNotTerminated() {
            return (this.actualEndDateNS.get() == Long.MAX_VALUE);
        }
        public boolean isNormalWaitEndEncountered() {
            return this.normalWaitEndEncountered;
        }
        public boolean isInterruptedExceptionThrown() {
            return this.interruptedExceptionThrown;
        }
        public boolean isInterruptedStatusSetOnLastNormalWaitEnd() {
            return this.interruptedStatusSetOnLastNormalWaitEnd;
        }
    }
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void testInterruptionHandling_await() {
        for (final InterfaceCondilock condilock : newAllCondilocks()) {
            testCallableThrowsOnlyIfInterrupted(
                    condilock,
                    new Callable<Void>() {
                        @Override
                        public Void call() throws InterruptedException {
                            condilock.await();
                            return null;
                        }
                    });
        }
    }

    public void testInterruptionHandling_awaitUninterruptibly() {
        for (final InterfaceCondilock condilock : newAllCondilocks()) {
            final ExecutorService executor = Executors.newCachedThreadPool();
            signalAfter(condilock, executor, TOLERANCE_MS);
            Thread.currentThread().interrupt();
            condilock.runInLock(new Runnable() {
                @Override
                public void run() {
                    condilock.awaitUninterruptibly();
                }
            });
            // Checking interruption status has been restored.
            assertTrue(Thread.interrupted());
            Unchecked.shutdownAndAwaitTermination(executor);
        }
    }

    public void testInterruptionHandling_awaitNanos_long() {
        for (final InterfaceCondilock condilock : newAllCondilocks()) {
            for (long timeoutNS=-SMALL_WAIT_NS;timeoutNS<=SMALL_WAIT_NS;timeoutNS+=SMALL_WAIT_NS) {
                final long tns = timeoutNS;
                testCallableThrowsOnlyIfInterrupted(
                        condilock,
                        new Callable<Void>() {
                            @Override
                            public Void call() throws InterruptedException {
                                condilock.awaitNanos(tns);
                                return null;
                            }
                        });
            }
        }
    }

    public void testInterruptionHandling_await_long_TimeUnit() {
        for (final InterfaceCondilock condilock : newAllCondilocks()) {
            for (long timeoutNS=-SMALL_WAIT_NS;timeoutNS<=SMALL_WAIT_NS;timeoutNS+=SMALL_WAIT_NS) {
                final long tns = timeoutNS;
                testCallableThrowsOnlyIfInterrupted(
                        condilock,
                        new Callable<Void>() {
                            @Override
                            public Void call() throws InterruptedException {
                                condilock.await(tns,TimeUnit.NANOSECONDS);
                                return null;
                            }
                        });
            }
        }
    }

    public void testInterruptionHandling_awaitUntil_Date() {
        for (final InterfaceCondilock condilock : newAllCondilocks()) {
            for (long timeoutNS=-SMALL_WAIT_NS;timeoutNS<=SMALL_WAIT_NS;timeoutNS+=SMALL_WAIT_NS) {
                final long tns = timeoutNS;
                testCallableThrowsOnlyIfInterrupted(
                        condilock,
                        new Callable<Void>() {
                            @Override
                            public Void call() throws InterruptedException {
                                final Date date = new Date();
                                date.setTime((condilock.deadlineTimeNS() + tns)/1000000L);
                                condilock.awaitUntil(date);
                                return null;
                            }
                        });
            }
        }
    }

    public void testInterruptionHandling_awaitUntilNanos_long() {
        for (final InterfaceCondilock condilock : newAllCondilocks()) {
            for (long timeoutNS=-SMALL_WAIT_NS;timeoutNS<=SMALL_WAIT_NS;timeoutNS+=SMALL_WAIT_NS) {
                final long tns = timeoutNS;
                testCallableThrowsOnlyIfInterrupted(
                        condilock,
                        new Callable<Void>() {
                            @Override
                            public Void call() throws InterruptedException {
                                condilock.awaitUntilNanos(condilock.deadlineTimeNS() + tns);
                                return null;
                            }
                        });
            }
        }
    }

    public void testInterruptionHandling_awaitUntilNanosTimeoutTime_long() {
        for (final InterfaceCondilock condilock : newAllCondilocks()) {
            for (long timeoutNS=-SMALL_WAIT_NS;timeoutNS<=SMALL_WAIT_NS;timeoutNS+=SMALL_WAIT_NS) {
                final long tns = timeoutNS;
                testCallableThrowsOnlyIfInterrupted(
                        condilock,
                        new Callable<Void>() {
                            @Override
                            public Void call() throws InterruptedException {
                                condilock.awaitUntilNanosTimeoutTime(condilock.timeoutTimeNS() + tns);
                                return null;
                            }
                        });
            }
        }
    }
    
    /*
     * 
     */
    
    public void testBoundedWait_await() {
        for (InterfaceCondilock condilock : newNonBlockingCondilocks()) {
            testBoundedWait(new MyWaiter_await(condilock), 0L);
        }
        
        testBoundedWait(new MyWaiter_await(newSmartMonitorCondilock(0L,1L)), 1L);
        testBoundedWait(new MyWaiter_await(newSmartMonitorCondilock(0L,SMALL_WAIT_NS)), SMALL_WAIT_NS);
        
        testBoundedWait(new MyWaiter_await(newSmartLockCondilock(0L,1L)), 1L);
        testBoundedWait(new MyWaiter_await(newSmartLockCondilock(0L,SMALL_WAIT_NS)), SMALL_WAIT_NS);
    }

    public void testBoundedWait_awaitUninterruptibly() {
        for (InterfaceCondilock condilock : newNonBlockingCondilocks()) {
            testBoundedWait(new MyWaiter_awaitUninterruptibly(condilock), 0L);
        }
        
        testBoundedWait(new MyWaiter_awaitUninterruptibly(newSmartMonitorCondilock(0L,1L)), 1L);
        testBoundedWait(new MyWaiter_awaitUninterruptibly(newSmartMonitorCondilock(0L,SMALL_WAIT_NS)), SMALL_WAIT_NS);
        
        testBoundedWait(new MyWaiter_awaitUninterruptibly(newSmartLockCondilock(0L,1L)), 1L);
        testBoundedWait(new MyWaiter_awaitUninterruptibly(newSmartLockCondilock(0L,SMALL_WAIT_NS)), SMALL_WAIT_NS);
    }
    
    /*
     * 
     */
    
    public void testFixedWait_awaitNanosWhileFalseInLock() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            testFixedWait(new MyWaiter_awaitNanosWhileFalseInLock(condilock, -SMALL_WAIT_NS), 0L);
            testFixedWait(new MyWaiter_awaitNanosWhileFalseInLock(condilock, 1L), 1L);
            testFixedWait(new MyWaiter_awaitNanosWhileFalseInLock(condilock, SMALL_WAIT_NS), SMALL_WAIT_NS);
        }
    }

    public void testFixedWait_awaitUntilNanosTimeoutTimeWhileFalseInLock() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            testFixedWait(new MyWaiter_awaitUntilNanosTimeoutTimeWhileFalseInLock(condilock, -SMALL_WAIT_NS), 0L);
            testFixedWait(new MyWaiter_awaitUntilNanosTimeoutTimeWhileFalseInLock(condilock, 1L), 1L);
            testFixedWait(new MyWaiter_awaitUntilNanosTimeoutTimeWhileFalseInLock(condilock, SMALL_WAIT_NS), SMALL_WAIT_NS);
        }
    }

    public void testFixedWait_awaitUntilNanosWhileFalseInLock() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            testFixedWait(new MyWaiter_awaitUntilNanosWhileFalseInLock(condilock, -SMALL_WAIT_NS), 0L);
            testFixedWait(new MyWaiter_awaitUntilNanosWhileFalseInLock(condilock, 1L), 1L);
            testFixedWait(new MyWaiter_awaitUntilNanosWhileFalseInLock(condilock, SMALL_WAIT_NS), SMALL_WAIT_NS);
        }
    }

    /*
     * 
     */

    public void testWaitNotStoppedByInterruption_awaitUninterruptibly() {
        // Only using blocking condilocks, else wait method returns
        // too fast to make check relevant.
        for (InterfaceCondilock condilock : newBlockingCondilocks()) {
            boolean possibleSpuriousWaitStop = true;
            testWaitNotStoppedByInterruption(new MyWaiter_awaitUninterruptibly(condilock), possibleSpuriousWaitStop);
        }
    }

    public void testWaitNotStoppedByInterruption_awaitWhileFalseInLockUninterruptibly() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            boolean possibleSpuriousWaitStop = false;
            testWaitNotStoppedByInterruption(new MyWaiter_awaitWhileFalseInLockUninterruptibly(condilock), possibleSpuriousWaitStop);
        }
    }

    /*
     * 
     */
    
    public void testWaitStoppedByInterruption_await() {
        // Even testing non-blocking condilocks, which await() method returns ASAP,
        // to make sure they throw InterruptedException if called while current thread
        // is interrupted.
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            // JDK's conditions await() method might not have spurious wake-ups,
            // but we don't count on that for any of our custom await() methods.
            boolean possibleSpuriousWaitStop = true;
            testWaitStoppedByInterruption(new MyWaiter_await(condilock), possibleSpuriousWaitStop);
        }
    }

    public void testWaitStoppedByInterruption_awaitNanosWhileFalseInLock() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            boolean possibleSpuriousWaitStop = false;
            testWaitStoppedByInterruption(new MyWaiter_awaitNanosWhileFalseInLock(condilock, LARGE_WAIT_NS), possibleSpuriousWaitStop);
            testWaitStoppedByInterruption(new MyWaiter_awaitNanosWhileFalseInLock(condilock, Long.MAX_VALUE), possibleSpuriousWaitStop);
        }
    }

    public void testWaitStoppedByInterruption_awaitUntilNanosTimeoutTimeWhileFalseInLock() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            boolean possibleSpuriousWaitStop = false;
            testWaitStoppedByInterruption(new MyWaiter_awaitUntilNanosTimeoutTimeWhileFalseInLock(condilock, LARGE_WAIT_NS), possibleSpuriousWaitStop);
            testWaitStoppedByInterruption(new MyWaiter_awaitUntilNanosTimeoutTimeWhileFalseInLock(condilock, Long.MAX_VALUE), possibleSpuriousWaitStop);
        }
    }

    public void testWaitStoppedByInterruption_awaitUntilNanosWhileFalseInLock() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            boolean possibleSpuriousWaitStop = false;
            testWaitStoppedByInterruption(new MyWaiter_awaitUntilNanosWhileFalseInLock(condilock, LARGE_WAIT_NS), possibleSpuriousWaitStop);
            testWaitStoppedByInterruption(new MyWaiter_awaitUntilNanosWhileFalseInLock(condilock, Long.MAX_VALUE), possibleSpuriousWaitStop);
        }
    }

    /*
     * 
     */

    public void testWaitStoppedBySignal_await() {
        for (InterfaceCondilock condilock : newBlockingOrSemiBlockingCondilocks()) {
            boolean possibleSpuriousWaitStop = true;
            testWaitStoppedBySignal(new MyWaiter_await(condilock), possibleSpuriousWaitStop);
        }
    }

    public void testWaitStoppedBySignal_awaitUninterruptibly() {
        for (InterfaceCondilock condilock : newBlockingOrSemiBlockingCondilocks()) {
            boolean possibleSpuriousWaitStop = true;
            testWaitStoppedBySignal(new MyWaiter_awaitUninterruptibly(condilock), possibleSpuriousWaitStop);
        }
    }

    public void testWaitStoppedBySignal_awaitWhileFalseInLockUninterruptibly() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            boolean possibleSpuriousWaitStop = false;
            testWaitStoppedBySignal(new MyWaiter_awaitWhileFalseInLockUninterruptibly(condilock), possibleSpuriousWaitStop);
        }
    }

    public void testWaitStoppedBySignal_awaitNanosWhileFalseInLock() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            boolean possibleSpuriousWaitStop = false;
            testWaitStoppedBySignal(new MyWaiter_awaitNanosWhileFalseInLock(condilock, LARGE_WAIT_NS), possibleSpuriousWaitStop);
            testWaitStoppedBySignal(new MyWaiter_awaitNanosWhileFalseInLock(condilock, Long.MAX_VALUE), possibleSpuriousWaitStop);
        }
    }

    public void testWaitStoppedBySignal_awaitUntilNanosTimeoutTimeWhileFalseInLock() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            boolean possibleSpuriousWaitStop = false;
            testWaitStoppedBySignal(new MyWaiter_awaitUntilNanosTimeoutTimeWhileFalseInLock(condilock, LARGE_WAIT_NS), possibleSpuriousWaitStop);
            testWaitStoppedBySignal(new MyWaiter_awaitUntilNanosTimeoutTimeWhileFalseInLock(condilock, Long.MAX_VALUE), possibleSpuriousWaitStop);
        }
    }

    public void testWaitStoppedBySignal_awaitUntilNanosWhileFalseInLock() {
        for (InterfaceCondilock condilock : newAllCondilocks()) {
            boolean possibleSpuriousWaitStop = false;
            testWaitStoppedBySignal(new MyWaiter_awaitUntilNanosWhileFalseInLock(condilock, LARGE_WAIT_NS), possibleSpuriousWaitStop);
            testWaitStoppedBySignal(new MyWaiter_awaitUntilNanosWhileFalseInLock(condilock, Long.MAX_VALUE), possibleSpuriousWaitStop);
        }
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /*
     * test methods
     */

    private static void testCallableThrowsOnlyIfInterrupted(
            final InterfaceCondilock condilock,
            final Callable<Void> callable) {
        testCallableDoesNotThrowIfNotInterrupted(
                condilock,
                callable);
        testCallableThrowsIfInterrupted(
                condilock,
                callable);
    }
    
    /**
     * Tests that the specified callable does not throw InterruptedException
     * if calling thread is not interrupted.
     */
    private static void testCallableDoesNotThrowIfNotInterrupted(
            final InterfaceCondilock condilock,
            final Callable<Void> callable) {
        assertFalse(Thread.interrupted());
        
        final ExecutorService executor = Executors.newCachedThreadPool();

        signalAfter(condilock, executor, TOLERANCE_MS);
        try {
            condilock.callInLock(callable);
        } catch (InterruptedException e) {
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(false);
        }

        Unchecked.shutdownAndAwaitTermination(executor);
    }

    /**
     * Tests that the specified callable throws InterruptedException
     * if calling thread is interrupted.
     */
    private static void testCallableThrowsIfInterrupted(
            final InterfaceCondilock condilock,
            final Callable<Void> callable) {
        assertFalse(Thread.interrupted());
        
        Thread.currentThread().interrupt();
        try {
            condilock.callInLock(callable);
            assertTrue(false);
        } catch (InterruptedException e) {
            // quiet
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    /**
     * Tests that the specified waiter doesn't wait for more than the specified max duration.
     */
    private static void testBoundedWait(
            final MyInterfaceWaiter waiter,
            long maxDurationNS) {
        long a = nowNS();
        try {
            waiter.awaitInLock();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long b = nowNS();
        assertTrue(b-a <= maxDurationNS + TOLERANCE_NS);
    }

    /**
     * Tests that the specified waiter waits for the specified duration.
     */
    private static void testFixedWait(
            final MyInterfaceWaiter waiter,
            long durationNS) {
        long a = nowNS();
        try {
            waiter.awaitInLock();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long b = nowNS();
        assertTrue(Math.abs((b-a) - durationNS) <= TOLERANCE_NS);
    }

    /**
     * Tests that interruption doesn't end the wait,
     * and that interruption status is restored before returning.
     */
    private static void testWaitNotStoppedByInterruption(
            final MyInterfaceWaiter waiter,
            boolean possibleSpuriousWaitStop) {
        final ExecutorService executor = Executors.newCachedThreadPool();
        
        final MyWaiterHelper helper = new MyWaiterHelper(waiter);
        
        helper.stopIfInterruptedOnNormalWaitEnd = true; // Our only way to stop.
        helper.rewaitIfNormalWaitEndAndDidntStop = possibleSpuriousWaitStop; // In case of spurious wait stop before interruption.
        helper.launchWait(executor);
        
        sleepMS(SMALL_WAIT_MS);
        
        if (possibleSpuriousWaitStop) {
            if (helper.isNormalWaitEndEncountered()) {
                // Didn't interrupt yet.
                assertFalse(helper.isInterruptedStatusSetOnLastNormalWaitEnd());
                System.out.println("rare : waiting ended before interruption");
                return;
            }
        } else {
            assertTrue(helper.waitingNotTerminated());
        }

        /*
         * Checking interruption doesn't stop the wait, with soft-check
         * in case of possible spurious wake-up, which we suppose rare,
         * else this test is irrelevant.
         * (Can have thread interrupted just after a spurious wait stop,
         * which would look like having the wait being stopped by interruption.)
         */
        
        helper.interruptRunnerWhenKnown();
        
        sleepMS(SMALL_WAIT_MS);
        
        assertFalse(helper.isInterruptedExceptionThrown());
        if (possibleSpuriousWaitStop) {
            if (helper.isNormalWaitEndEncountered()) {
                System.out.println("rare : waiting ended after interruption");
                return;
            }
        } else {
            assertTrue(helper.waitingNotTerminated());
            assertFalse(helper.isNormalWaitEndEncountered());
        }
        
        /*
         * Stopping the wait, and checking interruption status has been restored.
         */
        
        final long stopDateNS = nowNS();
        waiter.stopWaitWithSignal();

        assertTrue(Math.abs(helper.getEndDateNSBlocking() - stopDateNS) <= TOLERANCE_NS);

        assertFalse(helper.isInterruptedExceptionThrown());
        assertTrue(helper.isInterruptedStatusSetOnLastNormalWaitEnd());
        
        Unchecked.shutdownAndAwaitTermination(executor);
    }

    /**
     * Tests that interruption stops the wait, by making wait method throw InterruptedException.
     */
    private static void testWaitStoppedByInterruption(
            final MyInterfaceWaiter waiter,
            boolean possibleSpuriousWaitStop) {
        final ExecutorService executor = Executors.newCachedThreadPool();
        
        final MyWaiterHelper helper = new MyWaiterHelper(waiter);
        
        helper.stopIfInterruptedOnNormalWaitEnd = false; // Only stopping due to condilock's InterruptedException.
        helper.rewaitIfNormalWaitEndAndDidntStop = possibleSpuriousWaitStop;
        helper.launchWait(executor);
        
        sleepMS(SMALL_WAIT_MS);
        
        assertTrue(helper.waitingNotTerminated());
        
        final long interruptDateNS = helper.interruptRunnerWhenKnown();
        
        assertTrue(Math.abs(helper.getEndDateNSBlocking() - interruptDateNS) <= TOLERANCE_NS);
        
        assertTrue(helper.isInterruptedExceptionThrown());
        if (possibleSpuriousWaitStop) {
            // Can have waiting thread interrupted right after wait method (normally) ended,
            // so having interruption status even though it has not been set by wait method after
            // catching an InterruptedException (case of non-blocking condilocks, or blocking
            // condilocks not waiting for a boolean condition).
        } else {
            assertFalse(helper.isInterruptedStatusSetOnLastNormalWaitEnd());
        }
        
        Unchecked.shutdownAndAwaitTermination(executor);
    }

    /**
     * Tests that wait stop ends the wait (supposing spurious wake-ups can't stop it,
     * i.e. that we are waiting for a boolean condition).
     */
    private static void testWaitStoppedBySignal(
            final MyInterfaceWaiter waiter,
            boolean possibleSpuriousWaitStop) {
        final ExecutorService executor = Executors.newCachedThreadPool();
        
        final MyWaiterHelper helper = new MyWaiterHelper(waiter);
        
        helper.stopIfInterruptedOnNormalWaitEnd = false;
        helper.rewaitIfNormalWaitEndAndDidntStop = possibleSpuriousWaitStop;
        helper.launchWait(executor);
        
        sleepMS(SMALL_WAIT_MS);
        
        assertTrue(helper.waitingNotTerminated()); // Rewaiting if spurious, so must still be waiting.
        
        if (possibleSpuriousWaitStop) {
            // Wait stop due to signal, must not be considered as a spurious wake-up.
            helper.rewaitIfNormalWaitEndAndDidntStop = false;
        }
        final long stopDateNS = nowNS();
        waiter.stopWaitWithSignal();
        
        assertTrue(Math.abs(helper.getEndDateNSBlocking() - stopDateNS) <= TOLERANCE_NS);
        
        assertFalse(helper.isInterruptedExceptionThrown()); // No interruption.
        assertFalse(helper.isInterruptedStatusSetOnLastNormalWaitEnd()); // No interruption.
        
        Unchecked.shutdownAndAwaitTermination(executor);
    }

    /*
     * condilocks bulks
     */

    private static ArrayList<InterfaceCondilock> newNonBlockingCondilocks() {
        final ArrayList<InterfaceCondilock> list = new ArrayList<InterfaceCondilock>();
        list.add(newSpinningCondilock());
        list.add(newSleepingCondilock());
        return list;
    }

    /**
     * Semi-blocking: blocking but not for long.
     */
    private static ArrayList<InterfaceCondilock> newSemiBlockingCondilocks() {
        final ArrayList<InterfaceCondilock> list = new ArrayList<InterfaceCondilock>();
        list.add(newSmartMonitorCondilock(0L,1L));
        list.add(newSmartMonitorCondilock(SMALL_WAIT_NS,1L));
        list.add(newSmartLockCondilock(0L,1L));
        list.add(newSmartLockCondilock(SMALL_WAIT_NS,1L));
        return list;
    }

    private static ArrayList<InterfaceCondilock> newBlockingCondilocks() {
        final ArrayList<InterfaceCondilock> list = new ArrayList<InterfaceCondilock>();
        list.add(newSmartMonitorCondilock(0L,Long.MAX_VALUE));
        list.add(newSmartMonitorCondilock(SMALL_WAIT_NS,Long.MAX_VALUE));
        list.add(newSmartLockCondilock(0L,Long.MAX_VALUE));
        list.add(newSmartLockCondilock(SMALL_WAIT_NS,Long.MAX_VALUE));
        return list;
    }

    private static ArrayList<InterfaceCondilock> newNonBlockingOrSemiBlockingCondilocks() {
        final ArrayList<InterfaceCondilock> list = new ArrayList<InterfaceCondilock>();
        list.addAll(newNonBlockingCondilocks());
        list.addAll(newSemiBlockingCondilocks());
        return list;
    }

    private static ArrayList<InterfaceCondilock> newBlockingOrSemiBlockingCondilocks() {
        final ArrayList<InterfaceCondilock> list = new ArrayList<InterfaceCondilock>();
        list.addAll(newBlockingCondilocks());
        list.addAll(newSemiBlockingCondilocks());
        return list;
    }

    private static ArrayList<InterfaceCondilock> newAllCondilocks() {
        final ArrayList<InterfaceCondilock> list = new ArrayList<InterfaceCondilock>();
        list.addAll(newNonBlockingCondilocks());
        list.addAll(newSemiBlockingCondilocks());
        list.addAll(newBlockingCondilocks());
        return list;
    }

    /*
     * condilocks
     */

    private static InterfaceCondilock newSpinningCondilock() {
        return new SpinningCondilock();
    }

    private static InterfaceCondilock newSleepingCondilock() {
        return new SleepingCondilock();
    }

    private static InterfaceCondilock newSmartMonitorCondilock(
            long maxTimedSpinningWaitNS,
            long maxBlockingWaitChunkNS) {
        return new SmartMonitorCondilock(
                0L, // nbrOfNonTimedConsecutiveBusySpins
                0L, // nbrOfNonTimedYieldsAndConsecutiveBusySpins
                0, // bigYieldThresholdNS
                0L, // nbrOfBusySpinsAfterSmallYield
                maxTimedSpinningWaitNS, // maxTimedSpinningWaitNS
                false, // elusiveInLockSignaling
                maxBlockingWaitChunkNS, // initialBlockingWaitChunkNS
                0.0); // blockingWaitChunkIncreaseRate
    }

    private static InterfaceCondilock newSmartLockCondilock(
            long maxTimedSpinningWaitNS,
            long maxBlockingWaitChunkNS) {
        return new SmartLockCondilock(
                0L, // nbrOfNonTimedConsecutiveBusySpins
                0L, // nbrOfNonTimedYieldsAndConsecutiveBusySpins
                0, // bigYieldThresholdNS
                0L, // nbrOfBusySpinsAfterSmallYield
                maxTimedSpinningWaitNS, // maxTimedSpinningWaitNS
                false, // elusiveInLockSignaling
                maxBlockingWaitChunkNS, // initialBlockingWaitChunkNS
                0.0); // blockingWaitChunkIncreaseRate
    }

    /*
     * misc
     */
    
    private static void sleepMS(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private static long nowNS() {
        return System.nanoTime() - INITIAL_NANO_TIME_NS;
    }
    
    private static void signalAfter(
            final InterfaceCondilock condilock,
            final Executor executor,
            final long ms) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                // For signal to happen after wait
                // (useful is wait blocks for a long
                // time or forever).
                try {
                    Thread.sleep(ms);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                condilock.signalAllInLock();
            }
        });
    }
}
