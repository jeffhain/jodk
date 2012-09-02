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

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import net.jodk.lang.NumbersUtils;
import net.jodk.threading.PostPaddedAtomicLong;

/**
 * Condilock based on a Lock and a Condition.
 * Allows for configuration of spinning wait (busy and/or yielding),
 * and in-lock signaling elision.
 */
public class SmartLockCondilock extends LockCondilock {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private final long nbrOfInitialBusySpins;
    private final int bigYieldThresholdNS;
    private final long nbrOfBusySpinsAfterSmallYield;
    private final long maxSpinningWaitNS;

    private final boolean elusiveInLockSignaling;
    private final long initialBlockingWaitChunkNS;
    private final double blockingWaitChunkIncreaseRate;

    private final PostPaddedAtomicLong nbrOfLockersToSignal = new PostPaddedAtomicLong();
    /**
     * To avoid some volatile reads.
     */
    private long nbrOfLockersToSignal_inLock;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates a condilock based on a default lock.
     */
    public SmartLockCondilock(
            long nbrOfInitialBusySpins,
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield,
            long maxSpinningWaitNS,
            //
            boolean elusiveInLockSignaling,
            long initialBlockingWaitChunkNS,
            double blockingWaitChunkIncreaseRate) {
        this(
                newDefaultLock(),
                //
                nbrOfInitialBusySpins,
                bigYieldThresholdNS,
                nbrOfBusySpinsAfterSmallYield,
                maxSpinningWaitNS,
                //
                elusiveInLockSignaling,
                initialBlockingWaitChunkNS,
                blockingWaitChunkIncreaseRate);
    }
    
    /**
     * Creates a condilock based on the specified lock
     * and a new condition obtained from it.
     */
    public SmartLockCondilock(
            final Lock lock,
            //
            long nbrOfInitialBusySpins,
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield,
            long maxSpinningWaitNS,
            //
            boolean elusiveInLockSignaling,
            long initialBlockingWaitChunkNS,
            double blockingWaitChunkIncreaseRate) {
        this(
                lock,
                lock.newCondition(),
                //
                nbrOfInitialBusySpins,
                bigYieldThresholdNS,
                nbrOfBusySpinsAfterSmallYield,
                maxSpinningWaitNS,
                //
                elusiveInLockSignaling,
                initialBlockingWaitChunkNS,
                blockingWaitChunkIncreaseRate);
    }
    
    /**
     * @param lock Lock to use for locking.
     * @param condition A condition based on the specified lock.
     * @param nbrOfInitialBusySpins Number of busy spins done to start spinning wait.
     *        Must be >= 0.
     * @param bigYieldThresholdNS Duration of a yield, in nanoseconds, from which
     *        the CPU is considered busy, in which case no busy spinning is done before
     *        next yield, if any. Must be >= 0.
     *        If 0, all yields are considered big, and if Integer.MAX_VALUE,
     *        all yields are considered small, which allows not to bother
     *        timing yields duration.
     * @param nbrOfBusySpinsAfterSmallYield Number of busy spins done after a
     *        yield that was considered small. Must be >= 0.
     * @param maxSpinningWaitNS Max duration, in nanoseconds, for spinning wait.
     *        Must be >= 0.
     * @param elusiveInLockSignaling If true, signalXXXInLock methods actually
     *        only signal if there is at least one thread holding the lock in an
     *        awaitXXXWhileFalseInLock method, that possibly did already evaluate
     *        the boolean condition from within the lock yet (and might be going
     *        to wait or already started to wait).
     * @param initialBlockingWaitChunkNS Max duration, in nanoseconds, for an isolated
     *        or in-loop-and-initial blocking wait.
     *        If Long.MAX_VALUE, normal timeouts are used, and timing methods might
     *        be not used (less overhead) if these timeouts can be approximated as infinite.
     * @param blockingWaitChunkIncreaseRate Rate (>=0) at which used blocking wait chunk
     *        increases (used chunk = initial chunk + rate * elapsed time).
     */
    public SmartLockCondilock(
            final Lock lock,
            final Condition condition,
            //
            long nbrOfInitialBusySpins,
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield,
            long maxSpinningWaitNS,
            //
            boolean elusiveInLockSignaling,
            long initialBlockingWaitChunkNS,
            double blockingWaitChunkIncreaseRate) {
        super(
                lock,
                condition);
        if (nbrOfInitialBusySpins < 0) {
            throw new IllegalArgumentException();
        }
        if (bigYieldThresholdNS < 0) {
            throw new IllegalArgumentException();
        }
        if (nbrOfBusySpinsAfterSmallYield < 0) {
            throw new IllegalArgumentException();
        }
        if (maxSpinningWaitNS < 0) {
            throw new IllegalArgumentException();
        }
        if (initialBlockingWaitChunkNS < 0) {
            throw new IllegalArgumentException();
        }
        if (!(blockingWaitChunkIncreaseRate >= 0.0)) {
            throw new IllegalArgumentException();
        }
        this.nbrOfInitialBusySpins = nbrOfInitialBusySpins;
        this.bigYieldThresholdNS = bigYieldThresholdNS;
        this.nbrOfBusySpinsAfterSmallYield = nbrOfBusySpinsAfterSmallYield;
        this.maxSpinningWaitNS = maxSpinningWaitNS;
        this.elusiveInLockSignaling = elusiveInLockSignaling;
        this.initialBlockingWaitChunkNS = initialBlockingWaitChunkNS;
        // abs to take care of -0.0
        this.blockingWaitChunkIncreaseRate = Math.abs(blockingWaitChunkIncreaseRate);
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
    protected long getNbrOfInitialBusySpins() {
        return this.nbrOfInitialBusySpins;
    }

    @Override
    protected long getNbrOfBusySpinsAfterEachYield() {
        return getNbrOfBusySpinsAfterEachYield(
                this.bigYieldThresholdNS,
                this.nbrOfBusySpinsAfterSmallYield);
    }
    
    @Override
    protected long getNbrOfBusySpinsBeforeNextYield(long previousYieldDurationNS) {
        return getNbrOfBusySpinsBeforeNextYield(
                previousYieldDurationNS,
                this.bigYieldThresholdNS,
                this.nbrOfBusySpinsAfterSmallYield);
    }
    
    @Override
    protected long getMaxSpinningWaitNS() {
        return this.maxSpinningWaitNS;
    }
    
    /*
     * 
     */
    
    @Override
    protected boolean useMaxBlockingWaitChunks() {
        return (this.initialBlockingWaitChunkNS < INFINITE_TIMEOUT_THRESHOLD_NS);
    }
    
    @Override
    protected long getMaxBlockingWaitChunkNS(long elapsedTimeNS) {
        return NumbersUtils.plusBounded(
                this.initialBlockingWaitChunkNS,
                Math.round(elapsedTimeNS * this.blockingWaitChunkIncreaseRate));
    }
    
    /*
     * 
     */

    @Override
    protected void afterLockWaitingForBooleanCondition() {
        if (this.elusiveInLockSignaling) {
            // Can't use lazySet here, for current thread
            // might then check boolean condition before
            // the set appears to signaling-or-not thread,
            // which might read zero and consider there is
            // no waiter-or-soon-waiting thread to signal.
            this.nbrOfLockersToSignal.set(++this.nbrOfLockersToSignal_inLock);
        }
    }
    
    @Override
    protected void beforeUnlockWaitingForBooleanCondition() {
        if (this.elusiveInLockSignaling) {
            // lazySet OK since unlocking will come just after.
            this.nbrOfLockersToSignal.lazySet(--this.nbrOfLockersToSignal_inLock);
        }
    }
}