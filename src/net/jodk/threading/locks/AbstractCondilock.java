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

import java.util.concurrent.locks.LockSupport;

import net.jodk.lang.InterfaceBooleanCondition;
import net.jodk.lang.NumbersUtils;

/**
 * Abstract class for condilocks, which allows for different types of
 * spinning waits, before eventual sleeping or blocking wait.
 * 
 * Thread interruption status and boolean condition are checked at each spin.
 * 
 * Spinning wait is defined by:
 * - a timeout (for now, never using deadline, since deadline time is usually
 *   not accurate enough), which can be approximated as infinite if huge,
 * - before each yield, a number of busy spins, function of the duration
 *   of the previous yield (if any)
 * 
 * Waits configuration is retrieved from overridable methods,
 * to allow for memory-cheap static configuration.
 */
abstract class AbstractCondilock extends AbstractCondition implements InterfaceCondilock {

    /*
     * In methods waiting for a boolean condition to be true,
     * after a wait, boolean condition is evaluated before
     * remaining timeout test, to make sure wait methods
     * never indicate it was false upon timeout, if it
     * turned true while waiting.
     * This also allows to make sure that the boolean condition
     * has always been evaluated just before the wait method
     * returns, whether it returns true or false, which could
     * be useful in some cases.
     * 
     * When spinning, checking interruption status before evaluation of boolean condition,
     * because in practice the boolean condition has already been evaluated
     * before call to this method, and we want to check interruption status
     * in between evaluations of boolean condition.
     */
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean ASSERTIONS = false;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    @Override
    public final void awaitWhileFalseInLockUninterruptibly(final InterfaceBooleanCondition booleanCondition) {
        /*
         * Similar to awaitUninterruptibly(), but waiting for a boolean condition.
         */
        boolean interrupted = false;
        while (true) {
            try {
                if (this.awaitUntilNanosTimeoutTimeWhileFalseInLock(booleanCondition, Long.MAX_VALUE)) {
                    break;
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------

    /**
     * Default sleep wait implementation.
     * Override this to implement your own sleep.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    protected void sleepingWait() throws InterruptedException {
        // Using parking wait over Thread.sleep(long), for its
        // (possibly) smaller granularity.
        //
        // Parking wait can be messed-up by someone calling
        // LockSupport.unpark(our_current_thread) frequently,
        // but we consider that should not happen or not hurt.
        //
        // In case parkNanos would be very accurate,
        // we make sure not to wait too small of a time,
        // else this would rather be a busy wait.
        LockSupport.parkNanos(100L * 1000L);
        throwIfInterrupted();
    }

    /*
     * configuration
     */
    
    /**
     * Default implementation returns 0.
     * 
     * @return The number of busy spins to do for starting spinning wait,
     *         i.e. before first yield if any.
     */
    protected long getNbrOfInitialBusySpins() {
        return 0L;
    }

    /**
     * Default implementation returns 0.
     * 
     * You need to override this method so that it returns a value < 0
     * to enable use of getNbrOfBusySpinsBeforeNextYield(long).
     * 
     * @return A value >= 0 to be used, in which case getNbrOfBusySpinsBeforeNextYield(long)
     *         is not used, else a value < 0, in which case getNbrOfBusySpinsBeforeNextYield(long)
     *         is used.
     */
    protected long getNbrOfBusySpinsAfterEachYield() {
        return 0L;
    }

    /**
     * Default implementation returns 0.
     * 
     * You need to override getNbrOfBusySpinsAfterEachYield() so that it returns
     * a value < 0 to enable use of this method.
     * 
     * @param previousYieldDurationNS Duration of previous yield,
     *        in nanoseconds, as a measure of CPU business.
     *        This duration is measured using timeoutTimeNS().
     * @return The number of busy spins to do before next yielding spin.
     *         Must be >= 0.
     */
    protected long getNbrOfBusySpinsBeforeNextYield(long previousYieldDurationNS) {
        return 0;
    }

    /**
     * Default implementation returns 0.
     * 
     * @return Max duration, in nanoseconds, for spinning. Must be >= 0.
     */
    protected long getMaxSpinningWaitNS() {
        return 0;
    }

    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE METHODS
    //--------------------------------------------------------------------------

    /*
     * spinning
     */

    /**
     * @throws InterruptedException if current thread is interrupted.
     */
    static void throwingYield() throws InterruptedException {
        throwIfInterrupted();
        Thread.yield();
        throwIfInterrupted();
    }
    
    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * TODO Always using timeout time to measure spinning wait duration,
     * for deadline time is typically not accurate enough.
     * 
     * If the specified timeout is <= 0, the condition is not evaluated and 0 is returned.
     * Else, if there is no spinning wait, the specified timeout is returned.
     * Else, spinning waits while regularly evaluating the boolean condition.
     * 
     * If timing is not done, can return a timeout higher than it should,
     * but that should not hurt, since timing is not done only for huge durations,
     * i.e. when this method only completes when boolean condition is true or
     * on InterruptedException.
     * 
     * @param timeoutNS Timeout, in nanoseconds, for whole wait.
     * @return Remaining time to wait (>=0) if condition was last evaluated as false,
     *         and Long.MIN_VALUE if condition was evaluated as true.
     * @throws InterruptedException if current thread is interrupted.
     */
    static long spinningWaitNanosWhileFalse(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            final long timeoutNS) throws InterruptedException {
        if (timeoutNS <= 0) {
            throwIfInterrupted();
            return 0;
        }

        final long maxSpinningWaitNS = condilock.getMaxSpinningWaitNS();
        if(ASSERTIONS)assert(maxSpinningWaitNS >= 0);
        if (maxSpinningWaitNS <= 0) {
            throwIfInterrupted();
            return timeoutNS;
        }

        /*
         * Busy spins and/or yielding spins.
         */

        // Not waiting more than specified timeout.
        final long reducedMaxSpinningWaitNS = Math.min(timeoutNS,maxSpinningWaitNS);
        
        final boolean timing = (reducedMaxSpinningWaitNS < INFINITE_TIMEOUT_THRESHOLD_NS);
        final long initialTimeoutTimeNS;
        if (timing) {
            initialTimeoutTimeNS = condilock.timeoutTimeNS();
        } else {
            initialTimeoutTimeNS = 0L;
        }
        // This call does at least one check of interruption status.
        if (spinningWaitNanosWhileFalse_someSpinning(
                condilock,
                booleanCondition,
                reducedMaxSpinningWaitNS,
                initialTimeoutTimeNS)) {
            return Long.MIN_VALUE;
        }
        // Here, "timing" should be true, since we don't do timing
        // (except eventually for yields timing) only if timeout
        // is approximated as infinite.
        
        // Might be negative.
        final long elapsedNS = (condilock.timeoutTimeNS() - initialTimeoutTimeNS);
        return Math.max(0, timeoutNS - elapsedNS);
    }

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * If timing is not done, can return a timeout higher than it should,
     * but that should not hurt, since timing is not done only for huge durations,
     * i.e. when this method only completes when boolean condition is true or
     * on InterruptedException.
     * 
     * @param endTimeoutTimeNS End timeout time, in nanoseconds, for whole wait.
     * @return An estimation (>= 0) of remaining time to wait if condition was last evaluated as false,
     *         and Long.MIN_VALUE if condition was evaluated as true.
     * @throws InterruptedException if current thread is interrupted.
     */
    static long spinningWaitUntilNanosWhileFalse_TT(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            final long endTimeoutTimeNS) throws InterruptedException {
        final long timeoutNS;
        final boolean timing = (endTimeoutTimeNS < INFINITE_DATE_THRESHOLD_NS);
        if (timing) {
            timeoutNS = NumbersUtils.minusBounded(endTimeoutTimeNS, condilock.timeoutTimeNS());
        } else {
            timeoutNS = Long.MAX_VALUE;
        }
        return spinningWaitNanosWhileFalse(condilock, booleanCondition, timeoutNS);
    }

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * If timing is not done, can return a timeout higher than it should,
     * but that should not hurt, since timing is not done only for huge durations,
     * i.e. when this method only completes when boolean condition is true or
     * on InterruptedException.
     * 
     * @param deadlineNS Deadline, in nanoseconds, for whole wait.
     * @return An estimation (>= 0) of remaining time to wait if condition was last evaluated as false,
     *         and Long.MIN_VALUE if condition was evaluated as true.
     * @throws InterruptedException if current thread is interrupted.
     */
    static long spinningWaitUntilNanosWhileFalse_DT(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            final long deadlineNS) throws InterruptedException {
        final long timeoutNS;
        final boolean timing = (deadlineNS < INFINITE_DATE_THRESHOLD_NS);
        if (timing) {
            timeoutNS = NumbersUtils.minusBounded(deadlineNS, condilock.deadlineTimeNS());
        } else {
            timeoutNS = Long.MAX_VALUE;
        }
        return spinningWaitNanosWhileFalse(condilock, booleanCondition, timeoutNS);
    }

    /*
     * sleeping
     */
    
    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    static boolean sleepingWaitNanosWhileFalse(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            final long timeoutNS) throws InterruptedException {
        final boolean timing = (timeoutNS < INFINITE_TIMEOUT_THRESHOLD_NS);
        if (timing) {
            /*
             * Using method based on end timeout time, for it is more accurate
             * than accumulating error with intermediate timeouts computations.
             */
            final long currentTimeoutTimeNS = condilock.timeoutTimeNS();
            final long endTimeoutTimeNS = NumbersUtils.plusBounded(currentTimeoutTimeNS, timeoutNS);
            return sleepingWaitUntilNanosWhileFalse_TT_timing(
                    condilock,
                    booleanCondition,
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS);
        } else {
            return sleepingWaitForeverWhileFalse(
                    condilock,
                    booleanCondition);
        }
    }

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    static boolean sleepingWaitUntilNanosWhileFalse_TT(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            final long endTimeoutTimeNS) throws InterruptedException {
        final boolean timing = (endTimeoutTimeNS < INFINITE_DATE_THRESHOLD_NS);
        if (timing) {
            final long currentTimeoutTimeNS = condilock.timeoutTimeNS();
            return sleepingWaitUntilNanosWhileFalse_TT_timing(
                    condilock,
                    booleanCondition,
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS);
        } else {
            return sleepingWaitForeverWhileFalse(
                    condilock,
                    booleanCondition);
        }
    }

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    static boolean sleepingWaitUntilNanosWhileFalse_DT(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            long deadlineNS) throws InterruptedException {
        
        throwIfInterrupted();
        
        while (true) {
            if (condilock.deadlineTimeNS() >= deadlineNS) {
                return false;
            }
            condilock.sleepingWait();
            if (booleanCondition.isTrue()) {
                return true;
            }
        }
    }

    /*
     * blocking
     */

    /**
     * Boolean condition is evaluated once before any timing consideration.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    static boolean blockingWaitNanosWhileFalse_TT_locked(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            long timeoutNS) throws InterruptedException {
        throwIfInterrupted();
        if (booleanCondition.isTrue()) {
            return true;
        }
        // Need to use timing if using max blocking wait chunks,
        // else argument for getMaxBlockingWaitChunkNS(long) will
        // always be 0.
        final boolean timing = (timeoutNS < INFINITE_TIMEOUT_THRESHOLD_NS) || condilock.useMaxBlockingWaitChunks();
        if (timing) {
            /*
             * Using method based on end timeout time, for it is more accurate
             * than accumulating error with intermediate timeouts computations.
             */
            final long currentTimeoutTimeNS = condilock.timeoutTimeNS();
            final long endTimeoutTimeNS = NumbersUtils.plusBounded(currentTimeoutTimeNS, timeoutNS);
            return blockingWaitUntilNanosWhileFalse_TT_locked_timing(
                    condilock,
                    booleanCondition,
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS);
        } else {
            return blockingWaitForeverWhileFalse_noMaxWaitChunks_locked(
                    condilock,
                    booleanCondition);
        }
    }

    /**
     * Boolean condition is evaluated once before any timing consideration.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    static boolean blockingWaitUntilNanosWhileFalse_TT_locked(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            long endTimeoutTimeNS) throws InterruptedException {
        throwIfInterrupted();
        if (booleanCondition.isTrue()) {
            return true;
        }
        // Need to use timing if using max blocking wait chunks,
        // else argument for getMaxBlockingWaitChunkNS(long) will
        // always be 0.
        final boolean timing = (endTimeoutTimeNS < INFINITE_DATE_THRESHOLD_NS) || condilock.useMaxBlockingWaitChunks();
        if (timing) {
            final long currentTimeoutTimeNS = condilock.timeoutTimeNS();
            return blockingWaitUntilNanosWhileFalse_TT_locked_timing(
                    condilock,
                    booleanCondition,
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS);
        } else {
            return blockingWaitForeverWhileFalse_noMaxWaitChunks_locked(
                    condilock,
                    booleanCondition);
        }
    }

    /**
     * Boolean condition is evaluated once before any timing consideration.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    static boolean blockingWaitUntilNanosWhileFalse_DT_locked(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            long deadlineNS) throws InterruptedException {
        throwIfInterrupted();
        if (booleanCondition.isTrue()) {
            return true;
        }
        // Need to use timing if using max blocking wait chunks,
        // else argument for getMaxBlockingWaitChunkNS(long) will
        // always be 0.
        final boolean timing = (deadlineNS < INFINITE_DATE_THRESHOLD_NS) || condilock.useMaxBlockingWaitChunks();
        if (timing) {
            final long deadlineTimeNS = condilock.deadlineTimeNS();
            return blockingWaitUntilNanosWhileFalse_DT_locked_timing(
                    condilock,
                    booleanCondition,
                    deadlineNS,
                    deadlineTimeNS);
        } else {
            /*
             * We don't bother taking care of getMaxDeadlineBlockingWaitChunkNS(),
             * since we approximated wait time as infinite anyway, and don't
             * support the case of current time reaching huge values.
             */
            return blockingWaitForeverWhileFalse_noMaxWaitChunks_locked(
                    condilock,
                    booleanCondition);
        }
    }
    
    /*
     * 
     */

    static long getNbrOfBusySpinsAfterEachYield(
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield) {
        if (bigYieldThresholdNS == Integer.MAX_VALUE) {
            // No need to bother timing yields.
            return nbrOfBusySpinsAfterSmallYield;
        }
        if ((bigYieldThresholdNS|nbrOfBusySpinsAfterSmallYield) == 0) {
            // No need to bother timing yields.
            return 0;
        }
        return -1;
    }
    
    static long getNbrOfBusySpinsBeforeNextYield(
            long previousYieldDurationNS,
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield) {
        if (previousYieldDurationNS >= bigYieldThresholdNS) {
            // Too busy CPU: letting CPU to other threads.
            return 0;
        } else {
            // Short yield: busy spinning.
            return nbrOfBusySpinsAfterSmallYield;
        }
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * Busy spins and/or yielding spins.
     * 
     * @return True if the specified boolean condition was last evaluated as true,
     *         false otherwise (i.e. if max spinning wait time elapsed).
     * @throws InterruptedException if current thread is interrupted.
     */
    private static boolean spinningWaitNanosWhileFalse_someSpinning(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            final long maxSpinningWaitNS,
            long currentTimeoutTimeNS) throws InterruptedException {
        
        final long endTimeoutTimeNS = NumbersUtils.plusBounded(currentTimeoutTimeNS,maxSpinningWaitNS);
        
        final long nbrOfInitialBusySpins = condilock.getNbrOfInitialBusySpins();
        if(ASSERTIONS)assert(nbrOfInitialBusySpins >= 0);
        
        // Only allows to avoid timing while busy-spinning,
        // for we need to time yield duration anyway.
        final boolean timing = (maxSpinningWaitNS < INFINITE_TIMEOUT_THRESHOLD_NS);
        
        final long nbrOfBusySpinsAfterEachYield = condilock.getNbrOfBusySpinsAfterEachYield();
        final boolean yieldTiming = (nbrOfBusySpinsAfterEachYield < 0);
        
        /*
         * 
         */
        
        long nbrOfBusySpinsBeforeNextYield = nbrOfInitialBusySpins;
        // Initialized to avoid javac error.
        long beforeYieldNS = 0L;
        // Loop for busy spins and/or yielding spins.
        while (true) {
            // Loop for busy spins.
            for (long i=0;i<nbrOfBusySpinsBeforeNextYield;i++) {
                // Busy spin.
                throwIfInterrupted();
                if (booleanCondition.isTrue()) {
                    return true;
                }
                if (timing) {
                    currentTimeoutTimeNS = condilock.timeoutTimeNS();
                    // Timeout check.
                    if (currentTimeoutTimeNS >= endTimeoutTimeNS) {
                        return false;
                    }
                }
            }
            // Yielding spin.
            if (yieldTiming) {
                // Not calling timing method if nowNS is fresh
                // (only treatments known to be fast have been called since its computation).
                beforeYieldNS = (timing && (nbrOfBusySpinsBeforeNextYield > 0)) ? currentTimeoutTimeNS : condilock.timeoutTimeNS();
            }
            throwingYield();
            if (booleanCondition.isTrue()) {
                return true;
            }
            if (timing || yieldTiming) {
                currentTimeoutTimeNS = condilock.timeoutTimeNS();
            }
            if (timing) {
                // Timeout check.
                if (currentTimeoutTimeNS >= endTimeoutTimeNS) {
                    return false;
                }
            }
            if (yieldTiming) {
                // Max in case of backward-jumping time.
                final long previousYieldDurationNS = Math.max(0, currentTimeoutTimeNS - beforeYieldNS);
                nbrOfBusySpinsBeforeNextYield = condilock.getNbrOfBusySpinsBeforeNextYield(previousYieldDurationNS);
            } else {
                nbrOfBusySpinsBeforeNextYield = nbrOfBusySpinsAfterEachYield;
            }
            if(ASSERTIONS)assert(nbrOfBusySpinsBeforeNextYield >= 0);
        }
    }

    /*
     * sleeping
     */

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * @param currentTimeoutTimeNS A recent result of timeoutTimeNS() method.
     * @throws InterruptedException if current thread is interrupted.
     */
    private static boolean sleepingWaitUntilNanosWhileFalse_TT_timing(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            final long endTimeoutTimeNS,
            long currentTimeoutTimeNS) throws InterruptedException {
        
        throwIfInterrupted();
        
        while (true) {
            if (currentTimeoutTimeNS >= endTimeoutTimeNS) {
                return false;
            }
            condilock.sleepingWait();
            if (booleanCondition.isTrue()) {
                return true;
            }
            currentTimeoutTimeNS = condilock.timeoutTimeNS();
        }
    }

    /**
     * Wait done before any boolean condition evaluation.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    private static boolean sleepingWaitForeverWhileFalse(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition) throws InterruptedException {
        while (true) {
            condilock.sleepingWait();
            if (booleanCondition.isTrue()) {
                return true;
            }
        }
    }

    /*
     * blocking, timeout time, timing
     */
    
    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * @param currentTimeoutTimeNS A recent result of timeoutTimeNS() method.
     * @throws InterruptedException if current thread is interrupted.
     */
    private static boolean blockingWaitUntilNanosWhileFalse_TT_locked_timing(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            final long endTimeoutTimeNS,
            long currentTimeoutTimeNS) throws InterruptedException {
        
        throwIfInterrupted();
        
        final long initialTimeoutTimeNS = currentTimeoutTimeNS;
        while (true) {
            if (currentTimeoutTimeNS >= endTimeoutTimeNS) {
                return false;
            }
            final long elapsedTimeoutTimeNS = Math.max(0L, currentTimeoutTimeNS - initialTimeoutTimeNS);
            awaitUntilNanos_TT_OrLess(
                    condilock,
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS,
                    elapsedTimeoutTimeNS);
            if (booleanCondition.isTrue()) {
                return true;
            }
            currentTimeoutTimeNS = condilock.timeoutTimeNS();
        }
    }
    
    /*
     * blocking, deadline time, timing
     */
    
    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * @param currentDeadlineTimeNS A recent result of deadlineTimeNS() method.
     * @throws InterruptedException if current thread is interrupted.
     */
    private static boolean blockingWaitUntilNanosWhileFalse_DT_locked_timing(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition,
            final long deadlineNS,
            long currentDeadlineTimeNS) throws InterruptedException {
        
        throwIfInterrupted();
        
        final long initialDeadlineTimeNS = currentDeadlineTimeNS;
        while (true) {
            if (currentDeadlineTimeNS >= deadlineNS) {
                return false;
            }
            final long elapsedDeadlineTimeNS = Math.max(0L, currentDeadlineTimeNS - initialDeadlineTimeNS);
            awaitUntilNanos_DT_OrLessOrLess(
                    condilock,
                    deadlineNS,
                    currentDeadlineTimeNS,
                    elapsedDeadlineTimeNS);
            if (booleanCondition.isTrue()) {
                return true;
            }
            currentDeadlineTimeNS = condilock.deadlineTimeNS();
        }
    }
    
    /*
     * blocking, forever, no max wait chunks
     */
    
    /**
     * Wait done before any boolean condition evaluation.
     * 
     * Must not be used if useMaxBlockingWaitChunks() returned true,
     * for it does not make use of getMaxBlockingWaitChunkNS(long).
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    private static boolean blockingWaitForeverWhileFalse_noMaxWaitChunks_locked(
            final AbstractCondilock condilock,
            final InterfaceBooleanCondition booleanCondition) throws InterruptedException {
        while (true) {
            condilock.awaitNanosNoEstimate(Long.MAX_VALUE);
            if (booleanCondition.isTrue()) {
                return true;
            }
        }
    }
}
