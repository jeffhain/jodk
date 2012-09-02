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

import net.jodk.lang.NumbersUtils;
import net.jodk.threading.PostPaddedAtomicLong;

/**
 * Condilock based on an object's monitor.
 * Allows for configuration of spinning wait (busy and/or yielding,
 * non timed and/or timed), and in-lock signaling elision.
 */
public class SmartMonitorCondilock extends MonitorCondilock {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private final long nbrOfNonTimedConsecutiveBusySpins;

    private final long nbrOfNonTimedYieldsAndConsecutiveBusySpins;
    
    private final long maxTimedSpinningWaitNS;

    private final int bigYieldThresholdNS;
    
    private final long nbrOfBusySpinsAfterSmallYield;
    
    private final boolean elusiveInLockSignaling;
    
    private final long initialBlockingWaitChunkNS;
    private final double blockingWaitChunkIncreaseRate;

    private final PostPaddedAtomicLong nbrOfLockersToSignal = new PostPaddedAtomicLong();

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates a condilock based on a default mutex.
     */
    public SmartMonitorCondilock(
            long nbrOfBusySpinsBeforeEachYield,
            long maxTimedSpinningWaitNS) {
        this(
                new Object(),
                nbrOfBusySpinsBeforeEachYield,
                maxTimedSpinningWaitNS);
    }
    
    /**
     * @param mutex Object which monitor is to be used for locking.
     * @param nbrOfBusySpinsBeforeEachYield Number of non-timed busy spins done
     *        before timed spinning, and then number of timed busy spins done
     *        after each timed yield. Must be >= 0.
     * @param maxTimedSpinningWaitNS Max duration, in nanoseconds, for timed spinning
     *        (busy or yielding) wait. Must be >= 0.
     */
    public SmartMonitorCondilock(
            final Object mutex,
            long nbrOfBusySpinsBeforeEachYield,
            long maxTimedSpinningWaitNS) {
        this(
                mutex,
                nbrOfBusySpinsBeforeEachYield,
                maxTimedSpinningWaitNS,
                false, // elusiveInLockSignaling
                Long.MAX_VALUE, // initialBlockingWaitChunkNS
                0.0); // blockingWaitChunkIncreaseRate
    }

    /*
     * 
     */
    
    /**
     * Creates a condilock based on a default mutex.
     */
    public SmartMonitorCondilock(
            long nbrOfBusySpinsBeforeEachYield,
            long maxTimedSpinningWaitNS,
            boolean elusiveInLockSignaling,
            long initialBlockingWaitChunkNS,
            double blockingWaitChunkIncreaseRate) {
        this(
                new Object(),
                nbrOfBusySpinsBeforeEachYield,
                maxTimedSpinningWaitNS,
                elusiveInLockSignaling,
                initialBlockingWaitChunkNS,
                blockingWaitChunkIncreaseRate);
    }
    
    /**
     * @param mutex Object which monitor is to be used for locking.
     * @param nbrOfBusySpinsBeforeEachYield Number of non-timed busy spins done
     *        before timed spinning, and then number of timed busy spins done
     *        after each timed yield. Must be >= 0.
     * @param maxTimedSpinningWaitNS Max duration, in nanoseconds, for timed spinning
     *        (busy or yielding) wait. Must be >= 0.
     * @param elusiveInLockSignaling If true, signalXXXInLock methods actually
     *        only signal if there is at least one thread holding the lock in an
     *        awaitXXXWhileFalseInLock method, that possibly did already evaluate
     *        the boolean condition from within the lock yet (and might be going
     *        to wait or started to wait already).
     * @param initialBlockingWaitChunkNS Max duration, in nanoseconds, for an isolated
     *        or in-loop-and-initial blocking wait.
     * @param blockingWaitChunkIncreaseRate Rate (>=0) at which used blocking wait chunk
     *        increases (used chunk = initial chunk + rate * elapsed time).
     */
    public SmartMonitorCondilock(
            final Object mutex,
            long nbrOfBusySpinsBeforeEachYield,
            long maxTimedSpinningWaitNS,
            boolean elusiveInLockSignaling,
            long initialBlockingWaitChunkNS,
            double blockingWaitChunkIncreaseRate) {
        this(
                mutex,
                nbrOfBusySpinsBeforeEachYield, // nbrOfNonTimedConsecutiveBusySpins
                0L, // nbrOfNonTimedYieldsAndConsecutiveBusySpins
                Integer.MAX_VALUE, // bigYieldThresholdNS (all yields considered small)
                nbrOfBusySpinsBeforeEachYield, // nbrOfBusySpinsAfterSmallYield
                maxTimedSpinningWaitNS,
                elusiveInLockSignaling,
                initialBlockingWaitChunkNS,
                blockingWaitChunkIncreaseRate);
    }

    /*
     * 
     */
    
    /**
     * Creates a condilock based on a default mutex.
     */
    public SmartMonitorCondilock(
            long nbrOfNonTimedConsecutiveBusySpins,
            long nbrOfNonTimedYieldsAndConsecutiveBusySpins,
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield,
            long maxTimedSpinningWaitNS,
            boolean elusiveInLockSignaling,
            long initialBlockingWaitChunkNS,
            double blockingWaitChunkIncreaseRate) {
        this(
                new Object(),
                nbrOfNonTimedConsecutiveBusySpins,
                nbrOfNonTimedYieldsAndConsecutiveBusySpins,
                bigYieldThresholdNS,
                nbrOfBusySpinsAfterSmallYield,
                maxTimedSpinningWaitNS,
                elusiveInLockSignaling,
                initialBlockingWaitChunkNS,
                blockingWaitChunkIncreaseRate);
    }
    
    /**
     * @param mutex Object which monitor is to be used for locking.
     * @param nbrOfNonTimedConsecutiveBusySpins Number of non-timed busy spins. Must be >= 0.
     * @param nbrOfNonTimedYieldsAndConsecutiveBusySpins Number of non-timed yielding spins,
     *        each followed by the specified number of consecutive busy spins. Must be >= 0.
     * @param bigYieldThresholdNS Duration of a timed yield, in nanoseconds, above which
     *        the CPUs are considered busy, in which case no busy spinning is done before
     *        next yield, if any. Must be >= 0.
     * @param nbrOfBusySpinsAfterSmallYield Number of timed busy spins done after a timed
     *        yield that was considered small. Must be >= 0.
     * @param maxTimedSpinningWaitNS Max duration, in nanoseconds, for timed spinning wait.
     *        Must be >= 0.
     * @param elusiveInLockSignaling If true, signalXXXInLock methods actually
     *        only signal if there is at least one thread holding the lock in an
     *        awaitXXXWhileFalseInLock method, that possibly did already evaluate
     *        the boolean condition from within the lock yet (and might be going
     *        to wait or started to wait already).
     * @param initialBlockingWaitChunkNS Max duration, in nanoseconds, for an isolated
     *        or in-loop-and-initial blocking wait.
     * @param blockingWaitChunkIncreaseRate Rate (>=0) at which used blocking wait chunk
     *        increases (used chunk = initial chunk + rate * elapsed time).
     */
    public SmartMonitorCondilock(
            final Object mutex,
            long nbrOfNonTimedConsecutiveBusySpins,
            long nbrOfNonTimedYieldsAndConsecutiveBusySpins,
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield,
            long maxTimedSpinningWaitNS,
            boolean elusiveInLockSignaling,
            long initialBlockingWaitChunkNS,
            double blockingWaitChunkIncreaseRate) {
        super(mutex);
        if (nbrOfNonTimedConsecutiveBusySpins < 0) {
            throw new IllegalArgumentException();
        }
        if (nbrOfNonTimedYieldsAndConsecutiveBusySpins < 0) {
            throw new IllegalArgumentException();
        }
        if (bigYieldThresholdNS < 0) {
            throw new IllegalArgumentException();
        }
        if (nbrOfBusySpinsAfterSmallYield < 0) {
            throw new IllegalArgumentException();
        }
        if (maxTimedSpinningWaitNS < 0) {
            throw new IllegalArgumentException();
        }
        if (initialBlockingWaitChunkNS < 0) {
            throw new IllegalArgumentException();
        }
        if (!(blockingWaitChunkIncreaseRate >= 0.0)) {
            throw new IllegalArgumentException();
        }
        this.nbrOfNonTimedConsecutiveBusySpins = nbrOfNonTimedConsecutiveBusySpins;
        this.nbrOfNonTimedYieldsAndConsecutiveBusySpins = nbrOfNonTimedYieldsAndConsecutiveBusySpins;
        this.bigYieldThresholdNS = bigYieldThresholdNS;
        this.nbrOfBusySpinsAfterSmallYield = nbrOfBusySpinsAfterSmallYield;
        this.maxTimedSpinningWaitNS = maxTimedSpinningWaitNS;
        this.elusiveInLockSignaling = elusiveInLockSignaling;
        this.initialBlockingWaitChunkNS = initialBlockingWaitChunkNS;
        this.blockingWaitChunkIncreaseRate = blockingWaitChunkIncreaseRate;
    }
    
    /*
     * 
     */
    
    /**
     * If elusive in-lock signaling is activated,
     * actually signals only if needed by threads
     * using awaitXXXWhileFalseInLock methods.
     */
    @Override
    public void signalInLock() {
        if ((!this.elusiveInLockSignaling) || (this.nbrOfLockersToSignal.get() != 0)) {
            super.signalInLock();
        }
    }

    /**
     * If elusive in-lock signaling is activated,
     * actually signals only if needed by threads
     * using awaitXXXWhileFalseInLock methods.
     */
    @Override
    public void signalAllInLock() {
        if ((!this.elusiveInLockSignaling) || (this.nbrOfLockersToSignal.get() != 0)) {
            super.signalAllInLock();
        }
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    @Override
    protected long getNbrOfNonTimedConsecutiveBusySpins() {
        return this.nbrOfNonTimedConsecutiveBusySpins;
    }

    @Override
    protected long getNbrOfNonTimedYieldsAndConsecutiveBusySpins() {
        return this.nbrOfNonTimedYieldsAndConsecutiveBusySpins;
    }

    @Override
    protected long getNbrOfTimedBusySpinsBeforeNextTimedYield(long previousYieldDurationNS) {
        if (previousYieldDurationNS > this.bigYieldThresholdNS) {
            // First yield, or too busy CPU: letting CPU to other threads.
            return 0;
        } else {
            // Short yield: busy spinning.
            return this.nbrOfBusySpinsAfterSmallYield;
        }
    }
    
    @Override
    protected long getMaxTimedSpinningWaitNS() {
        return this.maxTimedSpinningWaitNS;
    }
    
    @Override
    protected long getMaxBlockingWaitChunkNS(long elapsedTimeoutTimeNS) {
        return NumbersUtils.plusBounded(
                this.initialBlockingWaitChunkNS,
                Math.round(elapsedTimeoutTimeNS * this.blockingWaitChunkIncreaseRate));
    }
    
    /*
     * 
     */

    @Override
    protected void afterLockWaitingForBooleanCondition() {
        if (this.elusiveInLockSignaling) {
            // In lock: no need to CAS.
            // Can't use lazySet here, for current thread
            // might then check boolean condition before
            // the set appears to signaling-or-not thread,
            // which might read zero and consider there is
            // no waiter-or-soon-waiting thread to signal.
            this.nbrOfLockersToSignal.set(this.nbrOfLockersToSignal.get()+1);
        }
    }
    
    @Override
    protected void beforeUnlockWaitingForBooleanCondition() {
        if (this.elusiveInLockSignaling) {
            // In lock: no need to CAS.
            // lazySet OK since unlocking will come just after.
            this.nbrOfLockersToSignal.lazySet(this.nbrOfLockersToSignal.get()-1);
        }
    }
}