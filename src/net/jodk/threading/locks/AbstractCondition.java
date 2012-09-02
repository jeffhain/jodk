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

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;

/**
 * Abstract class for conditions, which allows for the definition of a
 * max wait time, for deadline waits not to become aware too late of
 * eventual system time jumps.
 * 
 * Max wait time configuration is retrieved from an overridable method,
 * to allow for memory-cheap static configuration.
 */
abstract class AbstractCondition implements Condition {
    
    /*
     * Some methods declared final, to make sure they
     * don't get overriden by mistake. Might want to
     * remove final depending on need.
     */
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    /*
     * Infinite time threshold should not be used to ignore non-reduced timeouts
     * when waiting for a deadline, since deadline time can typically do
     * unbounded jumps.
     */
    
    /**
     * Threshold (inclusive), in nanoseconds, for a timeout to be considered infinite.
     * 
     * Not just using Long.MAX_VALUE due to some wait methods eventually
     * decreasing remaining timeout for other wait methods.
     */
    protected static final long INFINITE_TIMEOUT_THRESHOLD_NS = Long.MAX_VALUE/2;

    /**
     * Threshold (inclusive), in nanoseconds, for a date to be considered infinite.
     */
    protected static final long INFINITE_DATE_THRESHOLD_NS = Long.MAX_VALUE;

    protected static final long NO_ELAPSED_TIMEOUT_TIME_NS = 0L;
    
    /**
     * To avoid wrapping.
     */
    private static final long INITIAL_NANO_TIME_NS = System.nanoTime();
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Implementation for InterfaceCondilock.
     * @return Time, in nanoseconds, used for timeout measurements.
     */
    public long timeoutTimeNS() {
        /*
         * Using System.nanoTime() here, over System.currentTimeMillis():
         * - advantages:
         *   - (mandatory, for homogeneity with JDK's conditions)
         *     it doesn't take care of system time jumps,
         *   - more precise, and more accurate for small durations,
         * - disadvantages:
         *   - slower (more or less) on some systems,
         *   - has a drift (more or less) on some systems, making
         *     estimation of remaining wait time inaccurate for long timeouts.
         */
        return System.nanoTime() - INITIAL_NANO_TIME_NS;
    }
    
    /**
     * Implementation for InterfaceCondilock.
     * @return Time, in nanoseconds, used for deadlines measurements.
     */
    public long deadlineTimeNS() {
        /*
         * Not using ThinTime.
         * If using ThinTime, would need to use an instance specific to this
         * condition, to avoid contention of all conditions on a same instance.
         */
        return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    }

    /**
     * @throws InterruptedException if current thread is interrupted.
     */
    @Override
    public final void await() throws InterruptedException {
        this.awaitNanosNoEstimate_OrLess(Long.MAX_VALUE,NO_ELAPSED_TIMEOUT_TIME_NS);
    }

    @Override
    public final void awaitUninterruptibly() {
        // Preferring to have an exception in case
        // of interruption, than checking interruption
        // status at start all the time (which is
        // done by await() already).
        boolean interrupted = false;
        while (true) {
            try {
                this.await();
                // signaled or spurious wake-up
                // (or short wait due to getMaxBlockingWaitChunkNS)
                break;
            } catch (InterruptedException e) {
                // Not restoring interruption status here,
                // else would keep spinning on InterruptedException
                // being thrown.
                interrupted = true;
            }
        }
        if (interrupted) {
            // Restoring interruption status.
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * @throws InterruptedException if current thread is interrupted.
     */
    @Override
    public final long awaitNanos(long timeoutNS) throws InterruptedException {
        // No problem if wraps, since it should wrap back
        // when we remove current time.
        final long endTimeoutTimeNS = this.timeoutTimeNS() + timeoutNS;
        this.awaitNanosNoEstimate_OrLess(timeoutNS,NO_ELAPSED_TIMEOUT_TIME_NS);
        return endTimeoutTimeNS - this.timeoutTimeNS();
    }

    /**
     * Implementation for InterfaceCondilock.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    public final void awaitUntilNanosTimeoutTime(long endTimeoutTimeNS) throws InterruptedException {
        this.awaitUntilNanosTimeoutTime_OrLess(
                endTimeoutTimeNS,
                this.timeoutTimeNS(),
                NO_ELAPSED_TIMEOUT_TIME_NS);
    }

    /**
     * @throws InterruptedException if current thread is interrupted.
     */
    @Override
    public final boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        final long timeoutNS = TimeUnit.NANOSECONDS.convert(timeout, unit);
        final boolean timing = (timeoutNS < INFINITE_TIMEOUT_THRESHOLD_NS);
        if (timing) {
            return (this.awaitNanos(timeoutNS) > 0);
        } else {
            this.await();
            return true;
        }
    }

    /**
     * @throws InterruptedException if current thread is interrupted.
     */
    @Override
    public final boolean awaitUntil(final Date deadline) throws InterruptedException {
        final long deadlineNS = TimeUnit.MILLISECONDS.toNanos(deadline.getTime());
        return this.awaitUntilNanos(deadlineNS);
    }

    /**
     * Implementation for InterfaceCondilock.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    public final boolean awaitUntilNanos(long deadlineNS) throws InterruptedException {
        final long nowNS = this.deadlineTimeNS();
        final long timeoutNS = NumbersUtils.minusBounded(deadlineNS, nowNS);
        return this.awaitUntilNanos_OrLess(deadlineNS, timeoutNS, NO_ELAPSED_TIMEOUT_TIME_NS) > 0;
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------

    protected static void throwIfInterrupted() throws InterruptedException {
        LangUtils.throwIfInterrupted();
    }
    
    /**
     * Method to use for all waits for a timeout, for it takes
     * care of eventual max wait time for timeout.
     * 
     * @param timeoutNS Recent computation of remaining timeout.
     * @param elapsedTimeoutTimeNS Parameter for getMaxBlockingWaitChunkNS(long) method.
     * @return An estimation of remaining timeout, in nanoseconds.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final void awaitNanosNoEstimate_OrLess(
            long timeoutNS,
            long elapsedTimeoutTimeNS) throws InterruptedException {
        final long reducedTimeoutNS = Math.min(timeoutNS, this.getMaxBlockingWaitChunkNS(elapsedTimeoutTimeNS));
        this.awaitNanosNoEstimate(reducedTimeoutNS);
    }

    /**
     * @param endTimeoutTimeNS Timeout time to wait for, in nanoseconds.
     * @param currentTimeoutTimeNS A recent result of timeoutTimeNS() method.
     * @param elapsedTimeoutTimeNS Parameter for getMaxBlockingWaitChunkNS(long) method.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final void awaitUntilNanosTimeoutTime_OrLess(
            long endTimeoutTimeNS,
            long currentTimeoutTimeNS,
            long elapsedTimeoutTimeNS) throws InterruptedException {
        final long timeoutNS = NumbersUtils.minusBounded(endTimeoutTimeNS,currentTimeoutTimeNS);
        this.awaitNanosNoEstimate_OrLess(timeoutNS,elapsedTimeoutTimeNS);
    }

    /**
     * Method to use for all waits for a deadline, for it takes
     * care of eventual max wait time for deadline.
     * 
     * @param deadlineNS Deadline to wait for, in nanoseconds.
     * @param timeoutNS A recent computation of remaining timeout.
     * @param elapsedTimeoutTimeNS Parameter for getMaxBlockingWaitChunkNS(long) method.
     * @return An estimation of remaining timeout, in nanoseconds.
     * @throws InterruptedException if current thread is interrupted.
     */
    protected final long awaitUntilNanos_OrLess(
            long deadlineNS,
            long timeoutNS,
            long elapsedTimeoutTimeNS) throws InterruptedException {
        if (timeoutNS <= 0) {
            throwIfInterrupted();
            return 0;
        }
        final long reducedTimeoutNS = Math.min(timeoutNS, this.getMaxDeadlineBlockingWaitChunkNS());
        this.awaitNanosNoEstimate_OrLess(reducedTimeoutNS,elapsedTimeoutTimeNS);
        return NumbersUtils.minusBounded(deadlineNS, this.deadlineTimeNS());
    }

    /**
     * Method for timeout waits that don't need estimation of remaining wait time.
     * Should never use it directly, but always through awaitNanosNoEstimate_OrLess
     * or awaitUntilNanos_OrLess methods, which take care of eventual max wait time.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    protected abstract void awaitNanosNoEstimate(long timeoutNS) throws InterruptedException;

    /**
     * Useful not to wait for too long if wait stop signals
     * might not be done or be missed.
     * 
     * This default implementation returns Long.MAX_VALUE.
     * 
     * @param elapsedTimeoutTimeNS Duration (>=0), in nanoseconds, and in
     *        timeout time, elapsed since blocking wait loop start.
     *        Can be used to enlarge wait chunk as this number grows,
     *        typically because the more time has been waited, the
     *        less a wait stop is likely to have been missed (which
     *        might not be the case though, if all waiters are signaled
     *        but only one can actually stop waiting each time).
     * @return Max duration, in nanoseconds, for next blocking wait
     *         (whether waiting for a timeout or a deadline).
     */
    protected long getMaxBlockingWaitChunkNS(long elapsedTimeoutTimeNS) {
        return Long.MAX_VALUE;
    }
    
    /**
     * Useful not to become aware too late of eventual
     * system time jumps (is used in addition to getMaxBlockingWaitChunkNS()).
     * 
     * This default implementation returns Long.MAX_VALUE.
     * 
     * @return Max duration, in nanoseconds, for each blocking wait for a deadline.
     */
    protected long getMaxDeadlineBlockingWaitChunkNS() {
        return Long.MAX_VALUE;
    }
}