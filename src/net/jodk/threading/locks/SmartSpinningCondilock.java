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

/**
 * Condilock that has no lock, and only spin-waits (busy-spins and/or yield-spins)
 * while it waits for a boolean condition to be true.
 */
public class SmartSpinningCondilock extends SpinningCondilock {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private final long nbrOfNonTimedConsecutiveBusySpins;

    private final long nbrOfNonTimedYieldsAndConsecutiveBusySpins;

    private final int bigYieldThresholdNS;
    
    private final long nbrOfBusySpinsAfterSmallYield;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates a condilock with no busy spinning.
     */
    public SmartSpinningCondilock() {
        this(0L); // nbrOfBusySpinsBeforeEachYield
    }
    
    /**
     * @param nbrOfBusySpinsBeforeEachYield Number of non-timed busy spins done
     *        before timed spinning, and then number of timed busy spins done
     *        after each timed yield. Must be >= 0.
     */
    public SmartSpinningCondilock(long nbrOfBusySpinsBeforeEachYield) {
        this(
                nbrOfBusySpinsBeforeEachYield, // nbrOfNonTimedConsecutiveBusySpins
                0L, // nbrOfNonTimedYieldsAndConsecutiveBusySpins
                Integer.MAX_VALUE, // bigYieldThresholdNS (all yields considered small)
                nbrOfBusySpinsBeforeEachYield); // nbrOfBusySpinsAfterSmallYield
    }

    /**
     * First two parameters configure non-timed spinning,
     * and the two others configure timed spinning.
     * In the case of this class, timed spinning has an actual timeout
     * of Long.MAX_VALUE, and timing is only used to measure yields durations.
     * 
     * @param nbrOfNonTimedConsecutiveBusySpins Number of non-timed busy spins. Must be >= 0.
     * @param nbrOfNonTimedYieldsAndConsecutiveBusySpins Number of non-timed yielding spins,
     *        each followed by the specified number of consecutive busy spins. Must be >= 0.
     * @param bigYieldThresholdNS Duration of a timed yield, in nanoseconds, above which
     *        the CPUs are considered busy, in which case no busy spinning is done before
     *        next yield, if any. Must be >= 0.
     * @param nbrOfBusySpinsAfterSmallYield Number of timed busy spins done after a timed
     *        yield that was considered small. Must be >= 0.
     */
    public SmartSpinningCondilock(
            long nbrOfNonTimedConsecutiveBusySpins,
            long nbrOfNonTimedYieldsAndConsecutiveBusySpins,
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield) {
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
        this.nbrOfNonTimedConsecutiveBusySpins = nbrOfNonTimedConsecutiveBusySpins;
        this.nbrOfNonTimedYieldsAndConsecutiveBusySpins = nbrOfNonTimedYieldsAndConsecutiveBusySpins;
        this.bigYieldThresholdNS = bigYieldThresholdNS;
        this.nbrOfBusySpinsAfterSmallYield = nbrOfBusySpinsAfterSmallYield;
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
}