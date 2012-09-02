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
 * To avoid the overhead of timing method, some parts of spinning wait are non-timed.
 * 
 * Spinning wait is defined as:
 * - non-timed spinning:
 *   - a number of consecutive busy-spins
 *   - the number of times a yield is done followed by the previous number of consecutive busy spins
 * - timed spinning (busy and/or yielding):
 *   - a timeout
 *   - before each yield, a number of busy spins,
 *     function of the duration of the previous yield (if any)
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

    /**
     * We always need timing for blocking waits for a boolean condition,
     * even if time to wait is huge, for it always makes use of
     * getMaxBlockingWaitChunkNS(long) method, which requires elapsed time.
     */
    private static final boolean ALWAYS_TIME_BLOCKING_WAIT_WHILE_FALSE = true;
    
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

    /*
     * spinning
     */

    /**
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final void throwingYield() throws InterruptedException {
        throwIfInterrupted();
        Thread.yield();
        throwIfInterrupted();
    }
    
    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * If the specified timeout is <= 0, the condition is not evaluated and 0 is returned.
     * Else, if there is no spinning wait, the specified timeout is returned.
     * Else, spinning waits while regularly evaluating the boolean condition.
     * 
     * @param timeoutNS Timeout, in nanoseconds, for whole wait.
     * @return Remaining time to wait (>=0) if condition was last evaluated as false,
     *         and Long.MIN_VALUE if condition was evaluated as true.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final long spinningWaitNanosWhileFalse_notLocked(
            final InterfaceBooleanCondition booleanCondition,
            final long timeoutNS) throws InterruptedException {
        if (timeoutNS <= 0) {
            throwIfInterrupted();
            return 0;
        }

        /*
         * Non timed busy spins and/or non timed yielding spins.
         */

        if (this.spinningWaitNanosWhileFalse_notLocked_nonTimed(booleanCondition)) {
            return Long.MIN_VALUE;
        }

        /*
         * Timed busy spins and/or timed yielding spins.
         */

        final long maxTimedSpinningWaitNS = this.getMaxTimedSpinningWaitNS();
        if(ASSERTIONS)assert(maxTimedSpinningWaitNS >= 0);
        if (maxTimedSpinningWaitNS > 0) {
            // Not waiting more than specified timeout.
            final long reducedMaxTimedSpinningWaitNS = Math.min(timeoutNS,maxTimedSpinningWaitNS);
            // This call does at least one check of interruption status.
            if (this.spinningWaitNanosWhileFalse_notLocked_timed(
                    booleanCondition,
                    reducedMaxTimedSpinningWaitNS)) {
                return Long.MIN_VALUE;
            }
            // We consider that (exactly) the same amount of actual time
            // elapsed (saves us additional calls to timing method, and
            // should not hurt).
            // Returned value is >= 0.
            return timeoutNS - reducedMaxTimedSpinningWaitNS;
        } else {
            throwIfInterrupted();
            return timeoutNS;
        }
    }

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * TODO If timing is not done, can return a timeout higher than it should,
     *      but that should not hurt, since timing is not done only for huge durations.
     * 
     * @param endTimeoutTimeNS End timeout time, in nanoseconds, for whole wait.
     * @return Remaining time to wait (>=0) if condition was last evaluated as false,
     *         and Long.MIN_VALUE if condition was evaluated as true.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final long spinningWaitUntilNanosTimeoutTimeWhileFalse_notLocked(
            final InterfaceBooleanCondition booleanCondition,
            final long endTimeoutTimeNS) throws InterruptedException {
        final long timeoutNS;
        final boolean timing = (endTimeoutTimeNS < INFINITE_DATE_THRESHOLD_NS);
        if (timing) {
            timeoutNS = NumbersUtils.minusBounded(endTimeoutTimeNS, this.timeoutTimeNS());
        } else {
            timeoutNS = Long.MAX_VALUE;
        }
        return this.spinningWaitNanosWhileFalse_notLocked(booleanCondition, timeoutNS);
    }

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * TODO If timing is not done, can return a timeout higher than it should,
     *      but that should not hurt, since timing is not done only for huge durations.
     * 
     * @param deadlineNS Deadline, in nanoseconds, for whole wait.
     * @return Remaining time (in timeout time) to wait (>=0) if condition was last evaluated as false,
     *         and Long.MIN_VALUE if condition was evaluated as true.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final long spinningWaitUntilNanosWhileFalse_notLocked(
            final InterfaceBooleanCondition booleanCondition,
            final long deadlineNS) throws InterruptedException {
        // TODO Using timeout and not deadline for busy wait,
        // for deadline time is typically not accurate enough.
        final long timeoutNS;
        final boolean timing = (deadlineNS < INFINITE_DATE_THRESHOLD_NS);
        if (timing) {
            timeoutNS = NumbersUtils.minusBounded(deadlineNS, this.deadlineTimeNS());
        } else {
            timeoutNS = Long.MAX_VALUE;
        }
        return this.spinningWaitNanosWhileFalse_notLocked(booleanCondition, timeoutNS);
    }

    /*
     * sleeping
     */

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

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final boolean sleepingWaitNanosWhileFalse(
            final InterfaceBooleanCondition booleanCondition,
            final long timeoutNS) throws InterruptedException {
        final boolean timing = (timeoutNS < INFINITE_TIMEOUT_THRESHOLD_NS);
        if (timing) {
            /*
             * Using method based on end timeout time, for it is more accurate
             * than accumulating error with intermediate timeouts computations.
             */
            final long currentTimeoutTimeNS = this.timeoutTimeNS();
            final long endTimeoutTimeNS = NumbersUtils.plusBounded(currentTimeoutTimeNS, timeoutNS);
            return this.sleepingWaitUntilNanosTimeoutTimeWhileFalse_timing(
                    booleanCondition,
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS);
        } else {
            return this.sleepingWaitWhileFalse_noTiming(booleanCondition);
        }
    }

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final boolean sleepingWaitUntilNanosTimeoutTimeWhileFalse(
            final InterfaceBooleanCondition booleanCondition,
            final long endTimeoutTimeNS) throws InterruptedException {
        final boolean timing = (endTimeoutTimeNS < INFINITE_DATE_THRESHOLD_NS);
        if (timing) {
            final long currentTimeoutTimeNS = this.timeoutTimeNS();
            return this.sleepingWaitUntilNanosTimeoutTimeWhileFalse_timing(
                    booleanCondition,
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS);
        } else {
            return this.sleepingWaitWhileFalse_noTiming(booleanCondition);
        }
    }

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final boolean sleepingWaitUntilNanosWhileFalse(
            final InterfaceBooleanCondition booleanCondition,
            long deadlineNS) throws InterruptedException {
        throwIfInterrupted();
        while (true) {
            if (this.deadlineTimeNS() >= deadlineNS) {
                return false;
            }
            this.sleepingWait();
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
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final boolean blockingWaitNanosWhileFalse_locked(
            final InterfaceBooleanCondition booleanCondition,
            long timeoutNS) throws InterruptedException {
        throwIfInterrupted();
        if (booleanCondition.isTrue()) {
            return true;
        }
        final boolean timing = ALWAYS_TIME_BLOCKING_WAIT_WHILE_FALSE || (timeoutNS < INFINITE_TIMEOUT_THRESHOLD_NS);
        if (timing) {
            /*
             * Using method based on end timeout time, for it is more accurate
             * than accumulating error with intermediate timeouts computations.
             */
            final long currentTimeoutTimeNS = this.timeoutTimeNS();
            final long endTimeoutTimeNS = NumbersUtils.plusBounded(currentTimeoutTimeNS, timeoutNS);
            return this.blockingWaitUntilNanosTimeoutTimeWhileFalse_locked_timing(
                    booleanCondition,
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS);
        } else {
            return this.blockingWaitWhileFalse_locked_noTiming(booleanCondition);
        }
    }

    /**
     * Boolean condition is evaluated once before any timing consideration.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final boolean blockingWaitUntilNanosTimeoutTimeWhileFalse_locked(
            final InterfaceBooleanCondition booleanCondition,
            long endTimeoutTimeNS) throws InterruptedException {
        throwIfInterrupted();
        if (booleanCondition.isTrue()) {
            return true;
        }
        final boolean timing = ALWAYS_TIME_BLOCKING_WAIT_WHILE_FALSE || (endTimeoutTimeNS < INFINITE_DATE_THRESHOLD_NS);
        if (timing) {
            final long currentTimeoutTimeNS = this.timeoutTimeNS();
            return this.blockingWaitUntilNanosTimeoutTimeWhileFalse_locked_timing(
                    booleanCondition,
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS);
        } else {
            return this.blockingWaitWhileFalse_locked_noTiming(booleanCondition);
        }
    }

    /**
     * Boolean condition is evaluated once before any timing consideration.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final boolean blockingWaitUntilNanosWhileFalse_locked(
            final InterfaceBooleanCondition booleanCondition,
            long deadlineNS) throws InterruptedException {
        throwIfInterrupted();
        if (booleanCondition.isTrue()) {
            return true;
        }
        final long initialTimeoutNS = NumbersUtils.minusBounded(deadlineNS, this.deadlineTimeNS());
        long timeoutNS = initialTimeoutNS;
        while (true) {
            if (timeoutNS <= 0) {
                return false;
            }
            final long elapsedTimeoutTimeNS = Math.max(0L, initialTimeoutNS - timeoutNS);
            timeoutNS = this.awaitUntilNanos_OrLess(
                    deadlineNS,
                    timeoutNS,
                    elapsedTimeoutTimeNS);
            if (booleanCondition.isTrue()) {
                return true;
            }
        }
    }

    /*
     * configuration
     */

    /**
     * Default implementation returns 0.
     * Override this method to specify another value.
     * 
     * Duration of these spins is not taken into account in timeouts computation
     * (used before any call to timeoutTimeNS()).
     * 
     * @return Max number of non-timed consecutive busy spins. Must be >= 0.
     */
    protected long getNbrOfNonTimedConsecutiveBusySpins() {
        return 0;
    }

    /**
     * Default implementation returns 0.
     * Override this method to specify another value.
     * 
     * @return Max number of non-timed yielding spins, each followed by
     *         getNbrOfNonTimedConsecutiveBusySpins() consecutive busy spins.
     *         Must be >= 0.
     */
    protected long getNbrOfNonTimedYieldsAndConsecutiveBusySpins() {
        return 0;
    }

    /**
     * Default implementation returns 0.
     * Override this method to specify another value.
     * 
     * @param previousYieldDurationNS Duration of previous yield,
     *        in nanoseconds, as a measure of threading contention.
     *        This duration is measured using timeoutTimeNS().
     *        Long.MAX_VALUE if there was no previous yield,
     *        i.e. for first timed spinning.
     * @return The number of timed busy spins to do before next timed yielding spin.
     *         Must be >= 0.
     */
    protected long getNbrOfTimedBusySpinsBeforeNextTimedYield(long previousYieldDurationNS) {
        return 0;
    }

    /**
     * Default implementation returns 0.
     * Override this method to specify another value.
     * 
     * @return Max duration, in nanoseconds, for timed spinning. Must be >= 0.
     */
    protected long getMaxTimedSpinningWaitNS() {
        return 0;
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * Non timed busy spins and/or non timed yielding spins.
     * 
     * @return True if the specified boolean condition was last evaluated as true,
     *         false otherwise.
     * @throws InterruptedException if current thread is interrupted.
     */
    private boolean spinningWaitNanosWhileFalse_notLocked_nonTimed(
            final InterfaceBooleanCondition booleanCondition) throws InterruptedException {
        final long nbrOfNonTimedConsecutiveBusySpins = this.getNbrOfNonTimedConsecutiveBusySpins();
        long nbrOfNonTimedYieldsAndConsecutiveBusySpins = this.getNbrOfNonTimedYieldsAndConsecutiveBusySpins();
        if(ASSERTIONS)assert(nbrOfNonTimedConsecutiveBusySpins >= 0);
        if(ASSERTIONS)assert(nbrOfNonTimedYieldsAndConsecutiveBusySpins >= 0);
        // Loop for busy spins and/or yielding spins.
        while (true) {
            // Loop for busy spins.
            for (long i=0;i<nbrOfNonTimedConsecutiveBusySpins;i++) {
                throwIfInterrupted();
                if (booleanCondition.isTrue()) {
                    return true;
                }
            }
            // <= instead of ==, for robustness.
            if (nbrOfNonTimedYieldsAndConsecutiveBusySpins <= 0) {
                return false;
            }
            // Yielding spin.
            this.throwingYield();
            if (booleanCondition.isTrue()) {
                return true;
            }
            --nbrOfNonTimedYieldsAndConsecutiveBusySpins;
        }
    }

    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * Timed busy spins and/or timed yielding spins.
     * 
     * @return True if the specified boolean condition was last evaluated as true,
     *         false otherwise (i.e. if max spinning wait time elapsed).
     * @throws InterruptedException if current thread is interrupted.
     */
    private boolean spinningWaitNanosWhileFalse_notLocked_timed(
            final InterfaceBooleanCondition booleanCondition,
            final long maxTimedSpinningWaitNS) throws InterruptedException {
        // Only allows to avoid timing while busy-spinning,
        // for we need to time yield duration anyway.
        final boolean timing = (maxTimedSpinningWaitNS < INFINITE_TIMEOUT_THRESHOLD_NS);
        long previousYieldDurationNS = Long.MAX_VALUE;
        long beforeYieldNS;
        long currentTimeoutTimeNS = this.timeoutTimeNS();
        final long endTimeoutTimeNS = NumbersUtils.plusBounded(currentTimeoutTimeNS,maxTimedSpinningWaitNS);
        // Loop for busy spins and/or yielding spins.
        while (true) {
            final long spinsBeforeNextYield = this.getNbrOfTimedBusySpinsBeforeNextTimedYield(previousYieldDurationNS);
            if(ASSERTIONS)assert(spinsBeforeNextYield >= 0);
            // Loop for busy spins.
            for (long i=0;i<spinsBeforeNextYield;i++) {
                // Busy spin.
                throwIfInterrupted();
                if (booleanCondition.isTrue()) {
                    return true;
                }
                if (timing) {
                    currentTimeoutTimeNS = this.timeoutTimeNS();
                    // Timeout check.
                    if (currentTimeoutTimeNS >= endTimeoutTimeNS) {
                        return false;
                    }
                }
            }
            // Yielding spin.
            // Not calling timing method if nowNS is fresh
            // (only treatments known to be fast have been called since its computation).
            beforeYieldNS = (timing && (spinsBeforeNextYield > 0)) ? currentTimeoutTimeNS : this.timeoutTimeNS();
            this.throwingYield();
            if (booleanCondition.isTrue()) {
                return true;
            }
            currentTimeoutTimeNS = this.timeoutTimeNS();
            // Timeout check.
            if (currentTimeoutTimeNS >= endTimeoutTimeNS) {
                return false;
            }
            // Max in case of backward-jumping time.
            previousYieldDurationNS = Math.max(0, currentTimeoutTimeNS - beforeYieldNS);
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
    private boolean sleepingWaitUntilNanosTimeoutTimeWhileFalse_timing(
            final InterfaceBooleanCondition booleanCondition,
            final long endTimeoutTimeNS,
            long currentTimeoutTimeNS) throws InterruptedException {
        throwIfInterrupted();
        while (true) {
            if (currentTimeoutTimeNS >= endTimeoutTimeNS) {
                return false;
            }
            this.sleepingWait();
            if (booleanCondition.isTrue()) {
                return true;
            }
            currentTimeoutTimeNS = this.timeoutTimeNS();
        }
    }

    /**
     * Wait done before any boolean condition evaluation.
     * @throws InterruptedException if current thread is interrupted.
     */
    private boolean sleepingWaitWhileFalse_noTiming(final InterfaceBooleanCondition booleanCondition) throws InterruptedException {
        while (true) {
            this.sleepingWait();
            if (booleanCondition.isTrue()) {
                return true;
            }
        }
    }

    /*
     * blocking
     */
    
    /**
     * Time is tested, and wait done, before any boolean condition evaluation.
     * 
     * @param currentTimeoutTimeNS A recent result of timeoutTimeNS() method.
     * @throws InterruptedException if current thread is interrupted.
     */
    private boolean blockingWaitUntilNanosTimeoutTimeWhileFalse_locked_timing(
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
            this.awaitUntilNanosTimeoutTime_OrLess(
                    endTimeoutTimeNS,
                    currentTimeoutTimeNS,
                    elapsedTimeoutTimeNS);
            if (booleanCondition.isTrue()) {
                return true;
            }
            currentTimeoutTimeNS = this.timeoutTimeNS();
        }
    }

    /**
     * Wait done before any boolean condition evaluation.
     * @throws InterruptedException if current thread is interrupted.
     */
    private boolean blockingWaitWhileFalse_locked_noTiming(final InterfaceBooleanCondition booleanCondition) throws InterruptedException {
        while (true) {
            this.await();
            if (booleanCondition.isTrue()) {
                return true;
            }
        }
    }
}
