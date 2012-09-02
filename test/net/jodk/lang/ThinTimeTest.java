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
package net.jodk.lang;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.jodk.lang.ThinTime;

import junit.framework.TestCase;

public class ThinTimeTest extends TestCase {

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    /**
     * Allows to test static behavior.
     */
    private class MyThinTime extends ThinTime {
        private long sctm;
        private long snt;
        public MyThinTime(
                double futureToleranceRatio,
                long initialMinSCTMJumpMS,
                long systemTimeZeroMS) {
            super(
                    futureToleranceRatio,
                    initialMinSCTMJumpMS,
                    systemTimeZeroMS);
        }
        protected long getSCTM() {
            return sctm;
        }
        protected long getSNT() {
            return snt;
        }
    }
    
    private static class MyCallerRunnable implements Runnable {
        private final long nbrOfCalls;
        public MyCallerRunnable(long nbrOfCalls) {
            this.nbrOfCalls = nbrOfCalls;
        }
        @Override
        public void run() {
            long previousRefNS = System.currentTimeMillis() * (1000L * 1000L);
            long previousCTN = ThinTime.currentTimeNanos();

            for (int i=0;i<this.nbrOfCalls;i++) {
                final long forwardToleranceNS = 50L * 1000L * 1000L;

                long ref1NS = previousRefNS;
                long ctn = ThinTime.currentTimeNanos();
                long ref2NS = System.currentTimeMillis() * (1000L * 1000L);
                long deltaRefNS = ref2NS - ref1NS;
                if (deltaRefNS < 0) {
                    System.err.println("time backward jump : "+deltaRefNS+" ns");
                    assertTrue(false);
                } else {
                    if (Math.abs(deltaRefNS) > (1000L * 1000L * 1000L)) {
                        System.err.println("spent more than 1 second (maybe too many threads) : "+deltaRefNS+" ns");
                        assertTrue(false);
                    } else {
                        if (ctn < previousCTN) {
                            // If actual time backward jumps a bit, but we don't
                            // notice, ctn might also backward jump and we might
                            // notice it, so it's not necessarily abnormal.
                            System.err.println("ctn backward jump : "+(ctn - previousCTN)+" ns");
                            assertTrue(false);
                        }
                        if (ctn < ref1NS) {
                            System.err.println("ctn ["+ctn+"] < ref1NS ["+ref1NS+"]");
                            assertTrue(false);
                        } else if (ctn > ref2NS + forwardToleranceNS) {
                            long surplus = (ctn - (ref2NS + forwardToleranceNS));
                            System.err.println("ctn ["+ctn+"] > ref2NS ["+ref2NS+"] + forwardToleranceNS ["+forwardToleranceNS+"] by "+surplus+" ns");
                            assertTrue(false);
                        }
                    }
                }
                previousRefNS = ref2NS;
                previousCTN = ctn;
            }
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    // We consider the average granularity of ThinTime (for currentTimeMicros
    // and currentTimeNanos) must be at least 10 microseconds.
    private static final long MIN_THIN_TIME_GRANULARITY_NS = 10L * 1000L;
    
    private static final long S_TO_MS = 1000L;
    private static final long MS_TO_NS = 1000L * 1000L;
    private static final long S_TO_NS = 1000L * 1000L * 1000L;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public void test_currentTimeMillis() {

        final long toleranceMS = 1000L;
        assertTrue(Math.abs(ThinTime.currentTimeMillis() - System.currentTimeMillis()) < toleranceMS);
    }
    
    public void test_currentTimeMicros_granularity() {

        final long toleranceMS = 1000L;
        assertTrue(Math.abs(ThinTime.currentTimeMicros()/1000L - System.currentTimeMillis()) < toleranceMS);
        
        final int nbrOfRounds = 100000;
        
        long previousTime = 0;
        long time;
        long dateA;
        long dateB;
        
        int nbrOfChanges;
        
        nbrOfChanges = 0;
        dateA = System.nanoTime();
        for (int i=0;i<nbrOfRounds;i++) {
            time = ThinTime.currentTimeMicros();
            if (time != previousTime) {
                nbrOfChanges++;
            }
            previousTime = time;
        }
        dateB = System.nanoTime();
        assertTrue(nbrOfChanges >= (long)((dateB - dateA)/(double)MIN_THIN_TIME_GRANULARITY_NS));
    }
    
    public void test_currentTimeNanos_granularity() {

        final long toleranceMS = 1000L;
        assertTrue(Math.abs(ThinTime.currentTimeNanos()/1000000L - System.currentTimeMillis()) < toleranceMS);
        
        final int nbrOfRounds = 100000;
        long previousTime = 0;
        long time;
        long dateA;
        long dateB;
        
        int nbrOfChanges;
        
        nbrOfChanges = 0;
        dateA = System.nanoTime();
        for (int i=0;i<nbrOfRounds;i++) {
            time = ThinTime.currentTimeNanos();
            if (time != previousTime) {
                nbrOfChanges++;
            }
            previousTime = time;
        }
        dateB = System.nanoTime();
        assertTrue(nbrOfChanges >= (long)((dateB - dateA)/(double)MIN_THIN_TIME_GRANULARITY_NS));
    }

    public void test_sequentialBehavior_minSCTMJump() {
        final double futureToleranceRatio = 2.0;
        final long initialMinSCTMJumpMS = 1000L;
        final long systemTimeZeroMS = 123;
        MyThinTime tt = new MyThinTime(
                futureToleranceRatio,
                initialMinSCTMJumpMS,
                systemTimeZeroMS);
        
        assertEquals(initialMinSCTMJumpMS, tt.getMinSCTMJumpMS());

        // doesn't get lower on first call,
        // which initializes previous SCTM value
        tt.sctm += 100L;
        tt.currentTimeNanos_();
        assertEquals(initialMinSCTMJumpMS, tt.getMinSCTMJumpMS());

        // gets lower
        tt.sctm += 100L;
        tt.currentTimeNanos_();
        assertEquals(100L, tt.getMinSCTMJumpMS());
        
        // gets lower again
        tt.sctm += 10L;
        tt.currentTimeNanos_();
        assertEquals(10L, tt.getMinSCTMJumpMS());
        
        // doesn't grow
        tt.sctm += 100L;
        tt.currentTimeNanos_();
        assertEquals(10L, tt.getMinSCTMJumpMS());
    }

    public void test_sequentialBehavior_regular() {
        final double futureToleranceRatio = 2.0;
        final long initialMinSCTMJumpMS = 1000L;
        final long systemTimeZeroMS = 123;
        MyThinTime tt = new MyThinTime(
                futureToleranceRatio,
                initialMinSCTMJumpMS,
                systemTimeZeroMS);
        
        long expectedNS;
        long expectedRefSCTM;
        long expectedRefSNT;
        long minSCTMJumpMS;

        // initial time
        expectedNS = (tt.sctm - systemTimeZeroMS) * MS_TO_NS;
        expectedRefSCTM = -systemTimeZeroMS;
        expectedRefSNT = 0;
        minSCTMJumpMS = initialMinSCTMJumpMS;
        // computing ctn
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call didn't change time ref
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());

        // sctm and snt both getting ahead 10 seconds
        tt.sctm += 10 * S_TO_MS;
        tt.snt += 10 * S_TO_NS;
        expectedNS += 10 * S_TO_NS;
        // computing ctn
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call didn't change time ref
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump (smaller than our jump)
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());
    }

    public void test_sequentialBehavior_backwardJumps() {
        final double futureToleranceRatio = 2.0;
        final long initialMinSCTMJumpMS = 1000L;
        final long systemTimeZeroMS = 123;
        MyThinTime tt = new MyThinTime(
                futureToleranceRatio,
                initialMinSCTMJumpMS,
                systemTimeZeroMS);
        
        long expectedNS = tt.currentTimeNanos_();
        long expectedRefSCTM = tt.getTimeRef().getRefSCTM();
        long expectedRefSNT = tt.getTimeRef().getRefSNT();
        long minSCTMJumpMS = tt.getMinSCTMJumpMS();

        // snt jumped 1ns forward: cnt changes accordingly,
        // and time ref is not recomputed
        tt.snt += 1;
        expectedNS += 1;
        // computing ctn
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call didn't change time ref
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());

        // snt jumped back 1ns: not returning a time < to previously returned,
        // but since computed ctn is not < sctm, time ref is not recomputed
        tt.snt -= 1;
        // computing ctn
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call didn't change time ref
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());
        
        // snt jumped backward 1ns again: not returning a time < to previously returned,
        // and since computed ctn is < sctm, time ref is recomputed
        tt.snt -= 1;
        // computing ctn
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call changed time ref
        expectedRefSCTM = tt.sctm - systemTimeZeroMS;
        expectedRefSNT = tt.snt;
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());
        
        // sctm jumped backward, of exactly "future tolerance ratio * min SCTM jump":
        // ctn just goes backward 1ns (due to previous backward jump of stn),
        // and time ref is unchanged
        tt.sctm -= (long)(futureToleranceRatio * minSCTMJumpMS);
        expectedNS -= 1;
        // computing ctn
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call didn't change time ref
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());
        
        // sctm jumped backward 1ms, i.e. now computed ctn is past future tolerance.
        // Backward time jump is detected: time ref is recomputed.
        tt.sctm -= 1;
        expectedNS = (tt.sctm - systemTimeZeroMS) * MS_TO_NS;
        // computing ctn
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call did change time ref
        expectedRefSCTM = tt.sctm - systemTimeZeroMS;
        expectedRefSNT = tt.snt;
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump (1ms is smaller, but not considering backward jumps)
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());
    }

    public void test_sequentialBehavior_forwardJumps() {
        final double futureToleranceRatio = 2.0;
        final long initialMinSCTMJumpMS = 1000L;
        final long systemTimeZeroMS = 123;
        MyThinTime tt = new MyThinTime(
                futureToleranceRatio,
                initialMinSCTMJumpMS,
                systemTimeZeroMS);
        
        long deltaNS;
        
        long expectedNS = tt.currentTimeNanos_();
        long expectedRefSCTM = tt.getTimeRef().getRefSCTM();
        long expectedRefSNT = tt.getTimeRef().getRefSNT();
        long minSCTMJumpMS = tt.getMinSCTMJumpMS();
        
        // moving snt at future tolerance
        deltaNS = (long)(futureToleranceRatio * minSCTMJumpMS * MS_TO_NS);
        tt.snt += deltaNS;
        expectedNS += deltaNS;
        // computing ctn
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call didn't change time ref
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());

        // moving snt 1ns past future tolerance:
        // won't go past it, and will recompute time ref,
        // but won't return "sctm - offset" as current time,
        // since this would make returned time jump backward:
        // instead, returns the same as previously returned
        tt.snt += 1;
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call did change time ref
        expectedRefSCTM = tt.sctm - systemTimeZeroMS;
        expectedRefSNT = tt.snt;
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());

        // moving sctm 10s ahead, to get away of previous returned time,
        // and moving snt 1ns past future tolerance:
        // time ref will be recomputed
        tt.sctm += 10 * S_TO_MS;
        tt.snt += 10 * S_TO_NS + (long)(futureToleranceRatio * minSCTMJumpMS * MS_TO_NS) + 1;
        expectedNS = (tt.sctm - systemTimeZeroMS) * MS_TO_NS;
        assertEquals(expectedNS, tt.currentTimeNanos_());
        // call did change time ref
        expectedRefSCTM = tt.sctm - systemTimeZeroMS;
        expectedRefSNT = tt.snt;
        assertEquals(expectedRefSCTM, tt.getTimeRef().getRefSCTM());
        assertEquals(expectedRefSNT, tt.getTimeRef().getRefSNT());
        // call did't change min SCTM jump (which is smaller)
        assertEquals(minSCTMJumpMS, tt.getMinSCTMJumpMS());
    }
    
    /*
     * 
     */
    
    public static void test_currentTimeNanos_behavior() {
        final ExecutorService executor = Executors.newCachedThreadPool();
        
        final long nbrOfCalls = 1000L * 1000L;
        
        final int nbrOfThreads = 1 + Runtime.getRuntime().availableProcessors();
        
        for (int i=0;i<nbrOfThreads;i++) {
            executor.execute(new MyCallerRunnable(nbrOfCalls));
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
