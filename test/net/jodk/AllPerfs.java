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
package net.jodk;

import net.jodk.io.BufferOpPerf;
import net.jodk.io.DataBufferPerf;
import net.jodk.lang.FastMathPerf;
import net.jodk.lang.NumbersUtilsPerf;
import net.jodk.lang.ThinTimePerf;
import net.jodk.test.TestUtils;
import net.jodk.threading.HeisenLoggerPerf;
import net.jodk.threading.LongCounterPerf;
import net.jodk.threading.locks.CondilocksPerf;
import net.jodk.threading.ringbuffers.RingBuffersPerf;
import net.jodk.threading.ringbuffers.misc.RingBufferExecutorServicesPerf;

/**
 * perf = bench or accuracy
 * 
 * Notes:
 * - Doug Lea advice for ForkJoin usage (should also help in other cases):
 *   -XX:+UseG1GC -XX:-UseBiasedLocking
 * - to use xxx.jar containing JDK package code: -Xbootclasspath/p:lib/xxx.jar
 */
public class AllPerfs {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public static void main(String[] args) {
        System.out.println(TestUtils.getJVMInfo());

        /*
         * perfs per package and per lexicographic order
         * by default
         */

        // FastMath first, to bench class load.
        FastMathPerf.newRun(args);
        
        // io
        
        BufferOpPerf.newRun(args);
        DataBufferPerf.newRun(args);
        
        // lang
        
        NumbersUtilsPerf.newRun(args);
        ThinTimePerf.newRun(args);
        
        // threading
        
        HeisenLoggerPerf.newRun(args);
        LongCounterPerf.newRun(args);
        
        // threading.locks
        
        CondilocksPerf.newRun(args);
        
        // threading.ringbuffers
        
        RingBuffersPerf.newRun(args);
        
        // threading.ringbuffers.misc
        
        RingBufferExecutorServicesPerf.newRun(args);
    }
}
