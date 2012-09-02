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
     * Most wait methods declared final, which allows for use of static
     * implementations without fear of having to call an instance method
     * that might have been overriden.
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
    static final long INFINITE_TIMEOUT_THRESHOLD_NS = Long.MAX_VALUE/2;

    /**
     * Threshold (inclusive), in nanoseconds, for a date to be considered infinite.
     */
    static final long INFINITE_DATE_THRESHOLD_NS = Long.MAX_VALUE;

    /**
     * Argument for first call to getMaxBlockingWaitChunkNS(long).
     */
    static final long NO_ELAPSED_TIMEOUT_TIME_NS = 0L;
    
    /**
     * To avoid wrapping.
     * This also allows to make sure that when we consider an end timeout time
     * can be approximated as infinite, it is indeed very far from current
     * timeout time.
     */
    private static final long INITIAL_NANO_TIME_NS = System.nanoTime();
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Implementation for InterfaceCondilock.
     * 
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
     * 
     * @return Time, in nanoseconds, used for deadlines measurements.
     */
    public long deadlineTimeNS() {
        /*
         * Not using ThinTime.
         * If using ThinTime, would need to use an instance specific to this
         * condition, to avoid contention of all conditions on a same instance.
         * If using ThinTime, could in some places rely on deadlineTimeNS()
         * where we currently rely on timeoutTimeNS() for its accuracy.
         */
        return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    }

    /**
     * @throws InterruptedException if current thread is interrupted.
     */
    @Override
    public final void await() throws InterruptedException {
        awaitForever_TT_OrLess(this, NO_ELAPSED_TIMEOUT_TIME_NS);
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
                awaitForever_TT_OrLess(this, NO_ELAPSED_TIMEOUT_TIME_NS);
                // signaled or spurious wake-up
                // (or short wait due to getMaxBlockingWaitChunkNS(long))
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
     * @return An estimation of remaining timeout, or the specified
     *         timeout if it was approximated as infinite.
     * @throws InterruptedException if current thread is interrupted.
     */
    @Override
    public final long awaitNanos(long timeoutNS) throws InterruptedException {
        final boolean timing = (timeoutNS < INFINITE_TIMEOUT_THRESHOLD_NS);
        if (timing) {
            // No problem if wraps, since it should wrap back
            // when we remove current time.
            final long endTimeoutTimeNS = this.timeoutTimeNS() + timeoutNS;
            awaitNanos_TT_OrLess(this, timeoutNS, NO_ELAPSED_TIMEOUT_TIME_NS);
            return endTimeoutTimeNS - this.timeoutTimeNS();
        } else {
            awaitForever_TT_OrLess(this, NO_ELAPSED_TIMEOUT_TIME_NS);
            return timeoutNS;
        }
    }

    /**
     * Implementation for InterfaceCondilock.
     * 
     * @param endTimeoutTimeNS Timeout time to wait for, in nanoseconds.
     *        This is not a timeout, but a time compared to timeoutTimeNS(),
     *        to compute the timeout to wait.
     * @throws InterruptedException if current thread is interrupted.
     */
    public final void awaitUntilNanosTimeoutTime(long endTimeoutTimeNS) throws InterruptedException {
        final boolean timing = (endTimeoutTimeNS < INFINITE_DATE_THRESHOLD_NS);
        if (timing) {
            awaitUntilNanos_TT_OrLess(this, endTimeoutTimeNS, this.timeoutTimeNS(), NO_ELAPSED_TIMEOUT_TIME_NS);
        } else {
            awaitForever_TT_OrLess(this, NO_ELAPSED_TIMEOUT_TIME_NS);
        }
    }

    /**
     * @throws InterruptedException if current thread is interrupted.
     */
    @Override
    public final boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        final long timeoutNS = TimeUnit.NANOSECONDS.convert(timeout, unit);
        return (this.awaitNanos(timeoutNS) > 0);
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
     * @return True if this method returned before the specified deadline could be reached,
     *         false otherwise.
     * @throws InterruptedException if current thread is interrupted.
     */
    public final boolean awaitUntilNanos(long deadlineNS) throws InterruptedException {
        final boolean timing = (deadlineNS < INFINITE_DATE_THRESHOLD_NS);
        if (timing) {
            awaitUntilNanos_DT_OrLessOrLess(this, deadlineNS, this.deadlineTimeNS(), NO_ELAPSED_TIMEOUT_TIME_NS);
            return (deadlineNS > this.deadlineTimeNS());
        } else {
            awaitForever_DT_OrLessOrLess(this, NO_ELAPSED_TIMEOUT_TIME_NS);
            return true;
        }
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Method for timeout waits that don't need estimation of remaining wait time.
     * 
     * Should never use it directly, but always through xxx_OrLess methods,
     * which take care of eventual max wait time.
     * 
     * @throws InterruptedException if current thread is interrupted.
     */
    protected abstract void awaitNanosNoEstimate(long timeoutNS) throws InterruptedException;

    /*
     * configuration
     */
    
    /**
     * Default implementation returns false.
     * 
     * You need to override this method so that it returns true
     * to enable use of getMaxBlockingWaitChunkNS(long).
     * 
     * @return True if must use getMaxBlockingWaitChunkNS(long)
     *         for blocking waits, false otherwise, i.e. if do
     *         not need to wait for smaller chunks than specified
     *         timeouts.
     */
    protected boolean useMaxBlockingWaitChunks() {
        return false;
    }
    
    /**
     * Default implementation returns Long.MAX_VALUE.
     * 
     * You need to override useMaxBlockingWaitChunks() so that
     * it returns true to enable use of this method.
     * 
     * Useful not to wait for too long if wait stop signals
     * might not be done or be missed.
     * 
     * @param elapsedTimeNS Duration (>=0), in nanoseconds, elapsed since
     *        blocking wait loop start.
     *        Can be used to enlarge wait chunk as this number grows,
     *        typically because the more time has been waited, the
     *        less a wait stop is likely to have been missed (which
     *        might not be the case though, if all waiters are signaled
     *        but only one can actually stop waiting each time).
     * @return Max duration, in nanoseconds, for next blocking wait
     *         (whether waiting for a timeout or a deadline).
     */
    protected long getMaxBlockingWaitChunkNS(long elapsedTimeNS) {
        return Long.MAX_VALUE;
    }
    
    /**
     * Default implementation returns Long.MAX_VALUE.
     * 
     * Useful not to become aware too late of eventual
     * system time jumps (is used in addition to getMaxBlockingWaitChunkNS(long)).
     * 
     * @return Max duration, in nanoseconds, for each blocking wait for a deadline.
     */
    protected long getMaxDeadlineBlockingWaitChunkNS() {
        return Long.MAX_VALUE;
    }

    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE METHODS
    //--------------------------------------------------------------------------

    static void throwIfInterrupted() throws InterruptedException {
        LangUtils.throwIfInterrupted();
    }
    
    /*
     * timeout time waits
     */
    
    /**
     * @param timeoutNS Recent computation of remaining timeout.
     * @param elapsedTimeNS Argument for getMaxBlockingWaitChunkNS(long) method.
     * @throws InterruptedException if current thread is interrupted.
     */
    static void awaitNanos_TT_OrLess(
            final AbstractCondition condition,
            long timeoutNS,
            long elapsedTimeNS) throws InterruptedException {
        if (condition.useMaxBlockingWaitChunks()) {
            final long reducedTimeoutNS = Math.min(timeoutNS, condition.getMaxBlockingWaitChunkNS(elapsedTimeNS));
            condition.awaitNanosNoEstimate(reducedTimeoutNS);
        } else {
            condition.awaitNanosNoEstimate(timeoutNS);
        }
    }

    /**
     * @param endTimeoutTimeNS Timeout time to wait for, in nanoseconds.
     * @param currentTimeoutTimeNS A recent result of timeoutTimeNS() method.
     * @param elapsedTimeNS Argument for getMaxBlockingWaitChunkNS(long) method.
     * @throws InterruptedException if current thread is interrupted.
     */
    static void awaitUntilNanos_TT_OrLess(
            final AbstractCondition condition,
            long endTimeoutTimeNS,
            long currentTimeoutTimeNS,
            long elapsedTimeNS) throws InterruptedException {
        final long timeoutNS = NumbersUtils.minusBounded(endTimeoutTimeNS,currentTimeoutTimeNS);
        awaitNanos_TT_OrLess(condition, timeoutNS, elapsedTimeNS);
    }
    
    /**
     * @param elapsedTimeNS Argument for getMaxBlockingWaitChunkNS(long) method.
     * @throws InterruptedException if current thread is interrupted.
     */
    static void awaitForever_TT_OrLess(
            final AbstractCondition condition,
            long elapsedTimeNS) throws InterruptedException {
        if (condition.useMaxBlockingWaitChunks()) {
            final long timeoutNS = condition.getMaxBlockingWaitChunkNS(elapsedTimeNS);
            condition.awaitNanosNoEstimate(timeoutNS);
        } else {
            condition.awaitNanosNoEstimate(Long.MAX_VALUE);
        }
    }

    /*
     * deadline time waits
     */

    /**
     * @param timeoutNS A recent computation of remaining timeout.
     * @param elapsedTimeNS Argument for getMaxBlockingWaitChunkNS(long) method.
     * @throws InterruptedException if current thread is interrupted.
     */
    static void awaitNanos_DT_OrLessOrLess(
            final AbstractCondition condition,
            long timeoutNS,
            long elapsedTimeNS) throws InterruptedException {
        final long reducedTimeoutNS = Math.min(timeoutNS, condition.getMaxDeadlineBlockingWaitChunkNS());
        awaitNanos_TT_OrLess(condition, reducedTimeoutNS, elapsedTimeNS);
    }

    /**
     * @param deadlineNS Deadline time to wait for, in nanoseconds.
     * @param currentDeadlineTimeNS A recent result of deadlineTimeNS() method.
     * @param elapsedTimeNS Argument for getMaxBlockingWaitChunkNS(long) method.
     * @throws InterruptedException if current thread is interrupted.
     */
    static void awaitUntilNanos_DT_OrLessOrLess(
            final AbstractCondition condition,
            long deadlineNS,
            long currentDeadlineTimeNS,
            long elapsedTimeNS) throws InterruptedException {
        final long timeoutNS = NumbersUtils.minusBounded(deadlineNS,currentDeadlineTimeNS);
        awaitNanos_DT_OrLessOrLess(condition, timeoutNS, elapsedTimeNS);
    }

    /**
     * @param elapsedTimeNS Argument for getMaxBlockingWaitChunkNS(long) method.
     * @throws InterruptedException if current thread is interrupted.
     */
    static void awaitForever_DT_OrLessOrLess(
            final AbstractCondition condition,
            long elapsedTimeNS) throws InterruptedException {
        final long timeoutNS = condition.getMaxDeadlineBlockingWaitChunkNS();
        awaitNanos_TT_OrLess(condition, timeoutNS, elapsedTimeNS);
    }
}