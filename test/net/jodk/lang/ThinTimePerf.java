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

import net.jodk.lang.ThinTime;
import net.jodk.test.TestUtils;

public class ThinTimePerf {
 
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------
    
    private static final int NBR_OF_PROC = Runtime.getRuntime().availableProcessors();
    private static final int CEILED_NBR_OF_PROC = NumbersUtils.ceilingPowerOfTwo(NBR_OF_PROC);
    
    private static final int MIN_PARALLELISM = 1;
    private static final int MAX_PARALLELISM = 2 * CEILED_NBR_OF_PROC;
    
    private static final int NBR_OF_CALLS = 10 * 1000 * 1000;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println(TestUtils.getJVMInfo());
        newRun(args);
    }

    public static void newRun(String[] args) {
        new ThinTimePerf().run(args);
    }
    
    public ThinTimePerf() {
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private void run(String[] args) {
        // XXX
        System.out.println("--- "+ThinTimePerf.class.getSimpleName()+"... ---");
        System.out.println("number of calls = "+NBR_OF_CALLS);
        
        test_granularity();
        
        test_throughput();
        
        System.out.println("--- ..."+ThinTimePerf.class.getSimpleName()+" ---");
    }

    private static void test_granularity() {
        
        final int nbrOfCalls = NBR_OF_CALLS;
        
        System.out.println("");
        System.out.println("--- testing granularity ---");
        System.out.println("");
        
        long previousTime = 0;
        long time;
        long a;
        long b;
        
        int nbrOfChanges;
        
        /*
         * System
         */
        
        nbrOfChanges = 0;
        a = System.nanoTime();
        for (int i=0;i<nbrOfCalls;i++) {
            time = System.currentTimeMillis();
            if (time != previousTime) {
                nbrOfChanges++;
            }
            previousTime = time;
        }
        b = System.nanoTime();
        System.out.println("System.currentTimeMillis() took "+TestUtils.nsToSRounded(b-a)+" s");
        System.out.println("    nbrOfChanges = "+nbrOfChanges);
        
        nbrOfChanges = 0;
        a = System.nanoTime();
        for (int i=0;i<nbrOfCalls;i++) {
            time = System.nanoTime();
            if (time != previousTime) {
                nbrOfChanges++;
            }
            previousTime = time;
        }
        b = System.nanoTime();
        System.out.println("System.nanoTime() took "+TestUtils.nsToSRounded(b-a)+" s");
        System.out.println("    nbrOfChanges = "+nbrOfChanges);

        /*
         * ThinTime
         */

        nbrOfChanges = 0;
        a = System.nanoTime();
        for (int i=0;i<nbrOfCalls;i++) {
            time = ThinTime.currentTimeMillis();
            if (time != previousTime) {
                nbrOfChanges++;
            }
            previousTime = time;
        }
        b = System.nanoTime();
        System.out.println("ThinTime.currentTimeMillis() took "+TestUtils.nsToSRounded(b-a)+" s");
        System.out.println("    nbrOfChanges = "+nbrOfChanges);

        nbrOfChanges = 0;
        a = System.nanoTime();
        for (int i=0;i<nbrOfCalls;i++) {
            time = ThinTime.currentTimeMicros();
            if (time != previousTime) {
                nbrOfChanges++;
            }
            previousTime = time;
        }
        b = System.nanoTime();
        System.out.println("ThinTime.currentTimeMicros() took "+TestUtils.nsToSRounded(b-a)+" s");
        System.out.println("    nbrOfChanges = "+nbrOfChanges);

        nbrOfChanges = 0;
        a = System.nanoTime();
        for (int i=0;i<nbrOfCalls;i++) {
            time = ThinTime.currentTimeNanos();
            if (time != previousTime) {
                nbrOfChanges++;
            }
            previousTime = time;
        }
        b = System.nanoTime();
        System.out.println("ThinTime.currentTimeNanos() took "+TestUtils.nsToSRounded(b-a)+" s");
        System.out.println("    nbrOfChanges = "+nbrOfChanges);
    }
    
    private static void test_throughput() {
        final int nbrOfCalls = NBR_OF_CALLS;
        
        System.out.println("");
        System.out.println("--- testing throughput ---");

        for (int p=MIN_PARALLELISM;p<=MAX_PARALLELISM;p*=2) {
            
            System.out.println("");
            for (int k=0;k<4;k++) {
                final ExecutorService executor = Executors.newCachedThreadPool();
                
                final int minNbrOfCallsPerThread = nbrOfCalls/p;
                final int nbrOfCallsForFirstThread = nbrOfCalls - (p-1)*minNbrOfCallsPerThread;
                
                long a = System.nanoTime();
                for (int r=0;r<p;r++) {
                    final int nbrOfCallsForThread = (r == 0) ? nbrOfCallsForFirstThread : minNbrOfCallsPerThread;
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            for (int i=0;i<nbrOfCallsForThread;i++) {
                                ThinTime.currentTimeNanos();
                            }
                        }
                    });
                }
                Unchecked.shutdownAndAwaitTermination(executor);
                long b = System.nanoTime();
                
                System.out.println(p+" thread(s) : ThinTime.currentTimeNanos() took "+TestUtils.nsToSRounded(b-a)+" s");
            }
        }
    }
}
