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
import net.jodk.io.ByteCopyUtilsTest;
import net.jodk.io.ByteOrderUtilsTest;
import net.jodk.io.ByteTabUtilsTest;
import net.jodk.io.DataBufferTest;
import net.jodk.io.mock.ByteBufferMockBufferTest;
import net.jodk.io.mock.MockFileChannelTest;
import net.jodk.io.mock.MockFileLockTest;
import net.jodk.io.mock.PositionMockContentTest;
import net.jodk.io.mock.VirtualMockBufferTest;
import net.jodk.lang.FastMathTest;
import net.jodk.lang.LangUtilsTest;
import net.jodk.lang.NumbersUtilsTest;
import net.jodk.lang.ThinTimeTest;
import net.jodk.threading.AtomicUtilsTest;
import net.jodk.threading.HeisenLoggerTest;
import net.jodk.threading.LongCounterTest;
import net.jodk.threading.NonNullElseAtomicReferenceTest;
import net.jodk.threading.NullElseAtomicReferenceTest;
import net.jodk.threading.locks.CondilocksTest;
import net.jodk.threading.locks.LocksUtilsTest;
import net.jodk.threading.locks.ReentrantCheckerLockTest;
import net.jodk.threading.ringbuffers.RingBufferWorkFlowBuilderTest;
import net.jodk.threading.ringbuffers.RingBuffersTest;
import net.jodk.threading.ringbuffers.RingBuffersUtilsTest;
import net.jodk.threading.ringbuffers.misc.RingBufferExecutorServicesTest;

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
        
        // io
        
        suite.addTestSuite(ByteArrayUtilsTest.class);
        suite.addTestSuite(ByteBufferUtilsTest.class);
        suite.addTestSuite(ByteCopyUtilsTest.class);
        suite.addTestSuite(ByteOrderUtilsTest.class);
        suite.addTestSuite(ByteTabUtilsTest.class);
        suite.addTestSuite(DataBufferTest.class);
        
        // io.mock
        
        suite.addTestSuite(ByteBufferMockBufferTest.class);
        suite.addTestSuite(MockFileChannelTest.class);
        suite.addTestSuite(MockFileLockTest.class);
        suite.addTestSuite(PositionMockContentTest.class);
        suite.addTestSuite(VirtualMockBufferTest.class);
        
        // lang
        
        suite.addTestSuite(FastMathTest.class);
        suite.addTestSuite(LangUtilsTest.class);
        suite.addTestSuite(NumbersUtilsTest.class);
        suite.addTestSuite(ThinTimeTest.class);

        // threading

        suite.addTestSuite(AtomicUtilsTest.class);
        suite.addTestSuite(HeisenLoggerTest.class);
        suite.addTestSuite(LongCounterTest.class);
        suite.addTestSuite(NonNullElseAtomicReferenceTest.class);
        suite.addTestSuite(NullElseAtomicReferenceTest.class);

        // threading.locks
        
        suite.addTestSuite(CondilocksTest.class);
        suite.addTestSuite(LocksUtilsTest.class);
        suite.addTestSuite(ReentrantCheckerLockTest.class);

        // threading.ringbuffers
        
        suite.addTestSuite(RingBuffersTest.class);
        suite.addTestSuite(RingBuffersUtilsTest.class);
        suite.addTestSuite(RingBufferWorkFlowBuilderTest.class);

        // threading.ringbuffers.misc
        
        suite.addTestSuite(RingBufferExecutorServicesTest.class);

        return suite;
    }
}