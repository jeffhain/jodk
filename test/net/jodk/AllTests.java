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

import net.jodk.io.ByteArrayUtilsTest;
import net.jodk.io.ByteBufferUtilsTest;
import net.jodk.io.ByteOrderUtilsTest;
import net.jodk.io.ByteTabUtilsTest;
import net.jodk.io.DataBufferTest;
import net.jodk.lang.FastMathTest;
import net.jodk.lang.LangUtilsTest;
import net.jodk.lang.NumbersUtilsTest;
import net.jodk.lang.ThinTimeTest;
import net.jodk.threading.AtomicUtilsTest;
import net.jodk.threading.HeisenLoggerTest;
import net.jodk.threading.LongCounterTest;
import net.jodk.threading.locks.CondilocksTest;
import net.jodk.threading.locks.LocksUtilsTest;
import net.jodk.threading.locks.ReentrantCheckerLockTest;
import net.jodk.threading.ringbuffers.RingBufferWorkFlowBuilderTest;
import net.jodk.threading.ringbuffers.RingBuffersTest;
import net.jodk.threading.ringbuffers.RingBuffersUtilsTest;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

public class AllTests {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public static void main(String [] args) {
        TestRunner.main(new String[]{"-c",AllTests.class.getName()});
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite("Test suite for "+AllTests.class.getPackage());

        /*
         * tests per package and per lexicographic order
         */
        
        suite.addTestSuite(ByteArrayUtilsTest.class);
        suite.addTestSuite(ByteBufferUtilsTest.class);
        suite.addTestSuite(ByteOrderUtilsTest.class);
        suite.addTestSuite(ByteTabUtilsTest.class);
        suite.addTestSuite(DataBufferTest.class);
        
        suite.addTestSuite(FastMathTest.class);
        suite.addTestSuite(LangUtilsTest.class);
        suite.addTestSuite(NumbersUtilsTest.class);
        suite.addTestSuite(ThinTimeTest.class);

        suite.addTestSuite(AtomicUtilsTest.class);
        suite.addTestSuite(HeisenLoggerTest.class);
        suite.addTestSuite(LongCounterTest.class);

        suite.addTestSuite(CondilocksTest.class);
        suite.addTestSuite(LocksUtilsTest.class);
        suite.addTestSuite(ReentrantCheckerLockTest.class);

        suite.addTestSuite(RingBuffersTest.class);
        suite.addTestSuite(RingBuffersUtilsTest.class);
        suite.addTestSuite(RingBufferWorkFlowBuilderTest.class);
        
        return suite;
    }
}