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
 * Condilock that has no lock, and sleep-waits, after eventual initial
 * spinning wait (busy and/or yielding).
 */
public class SmartSleepingCondilock extends SleepingCondilock {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private final long nbrOfInitialBusySpins;
    private final int bigYieldThresholdNS;
    private final long nbrOfBusySpinsAfterSmallYield;
    private final long maxSpinningWaitNS;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
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
     */
    public SmartSleepingCondilock(
            long nbrOfInitialBusySpins,
            int bigYieldThresholdNS,
            long nbrOfBusySpinsAfterSmallYield,
            long maxSpinningWaitNS) {
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
        this.nbrOfInitialBusySpins = nbrOfInitialBusySpins;
        this.bigYieldThresholdNS = bigYieldThresholdNS;
        this.nbrOfBusySpinsAfterSmallYield = nbrOfBusySpinsAfterSmallYield;
        this.maxSpinningWaitNS = maxSpinningWaitNS;
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
}