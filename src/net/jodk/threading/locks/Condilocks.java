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
 * Provides static methods to create condilocks.
 */
public class Condilocks {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * This condilock should be a good compromise if you have no clue:
     * - adapted for overloaded CPUs (no busy spin (but some yielding)),
     * - decent throughput (yielding wait before blocking wait, and elusive signaling),
     * - decent latency (no sleeping wait) (but in case of lazy sets, this condilock
     *   might cause some latency if it enters blocking wait, see downsides below),
     * - adapted for use cases where one does not want GC to run
     *   (a condilock based on ReentrantLock could be more efficient,
     *   but would potentially create a lot of Node objects),
     * - 100 microseconds of yielding spins corresponds to a switch
     *   to non-blocking wait above a rate of 10000 events per second,
     *   which should not be far from the tipping point where
     *   blocking wait starts to slow downs events rate noticeably.
     * 
     * Main downsides in case of lazy sets:
     * - undue waits: a wait might not be awaken as one could
     *   expect (re-wait after wake-up, new value not being visible),
     *   and boolean condition only be checked after small waits.
     * - waiting threads are never totally idle, only doing
     *   small waits (useful in case of undue wait); but the
     *   delay between these waits increases geometrically.
     * 
     * @param lazySets True if this condilock must handle waits for lazily set
     *        volatile variables, i.e. if its blocking waits must wake-up from
     *        time to time for re-check, false otherwise.
     */
    public static InterfaceCondilock newSmartCondilock(boolean lazySets) {
        return new SmartMonitorCondilock(
                new Object(), // mutex
                0L, // nbrOfBusySpinsBeforeEachYield
                100L*1000L, // maxTimedSpinningWaitNS
                true, // elusiveInLockSignaling
                (lazySets ? 1L : Long.MAX_VALUE), // initialBlockingWaitChunkNS
                (lazySets ? 0.1 : 0.0)); // blockingWaitChunkIncreaseRate
    }
}
