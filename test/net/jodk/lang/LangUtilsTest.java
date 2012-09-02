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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.TestCase;

public class LangUtilsTest extends TestCase {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final long TOLERANCE_NS = 500L * 1000L * 1000L;

    private static final int NBR_OF_CASES = 100 * 1000;
    
    private final Random random = new Random(123456789L);

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MyRecordingPrintStream extends PrintStream {
        public MyRecordingPrintStream() {
            super(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    // no write
                }
            }, false);
        }
        final StringBuilder recorded = new StringBuilder();
        @Override
        public void println(Object x) {
            recorded.append(x);
            recorded.append(LangUtils.LINE_SEPARATOR);
        }
        @Override
        public void println(String x) {
            recorded.append(x);
            recorded.append(LangUtils.LINE_SEPARATOR);
        }
    };
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final String UNDEFINED_PROPERTY = "undefined property";
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_getProperty_2String() {
        assertEquals(System.getProperty("line.separator"),LangUtils.getProperty("line.separator", null));
        
        assertEquals("someString",LangUtils.getProperty(UNDEFINED_PROPERTY, "someString"));
    }

    public void test_getBooleanProperty_String_boolean() {
        assertEquals(true,LangUtils.getBooleanProperty(UNDEFINED_PROPERTY, true));
        assertEquals(false,LangUtils.getBooleanProperty(UNDEFINED_PROPERTY, false));
    }

    public void test_getIntProperty_String_int() {
        assertEquals(17,LangUtils.getIntProperty(UNDEFINED_PROPERTY, 17));
    }

    public void test_getLongProperty_String_long() {
        assertEquals(17L,LangUtils.getLongProperty(UNDEFINED_PROPERTY, 17L));
    }

    public void test_getFloatProperty_String_float() {
        assertEquals(17.0f,LangUtils.getFloatProperty(UNDEFINED_PROPERTY, 17.0f));
    }

    public void test_getDoubleProperty_String_double() {
        assertEquals(17.0,LangUtils.getDoubleProperty(UNDEFINED_PROPERTY, 17.0));
    }

    public void test_assertionsEnabled() {
        // Supposing same enabling for assertions
        // in LangUtils and in this class.
        boolean enabled = false;
        assert(enabled = !enabled);
        assertEquals(enabled,LangUtils.assertionsEnabled());
    }

    public void test_azzert_boolean() {
        try {
            LangUtils.azzert(false);
            assertTrue(false);
        } catch (AssertionError e) {
            // ok
        }
        assertTrue(LangUtils.azzert(true));
    }

    public void test_equalOrBothNull_2Object() {
        assertTrue(LangUtils.equalOrBothNull(null, null));
        assertTrue(LangUtils.equalOrBothNull(1, 1));

        assertFalse(LangUtils.equalOrBothNull(null, 1));
        assertFalse(LangUtils.equalOrBothNull(1, null));
        assertFalse(LangUtils.equalOrBothNull(1, 1L));
    }
    
    public void test_checkNonNull_Object() {
        try {
            LangUtils.checkNonNull(null);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        
        assertTrue(LangUtils.checkNonNull(new Object()));
        assertTrue(LangUtils.checkNonNull(""));
    }

    public void test_checkBounds_3int() {
        assertTrue(LangUtils.checkBounds(10, 0, 10));
        assertTrue(LangUtils.checkBounds(10, 10, 0));
        assertTrue(LangUtils.checkBounds(10, 5, 5));
        assertTrue(LangUtils.checkBounds(10, 9, 1));

        assertTrue(LangUtils.checkBounds(Integer.MAX_VALUE, 0, Integer.MAX_VALUE));
        assertTrue(LangUtils.checkBounds(Integer.MAX_VALUE, Integer.MAX_VALUE-1, 1));

        // limit < 0
        try {
            LangUtils.checkBounds(-1, 0, 1);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
        
        // length < 0
        try {
            LangUtils.checkBounds(10, 5, -1);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
        // length and from < 0: length priority
        try {
            LangUtils.checkBounds(10, -1, -1);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }

        // from < 0
        try {
            LangUtils.checkBounds(10, -1, 5);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }

        // from+length overflow
        try {
            LangUtils.checkBounds(10, Integer.MAX_VALUE, 1);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            LangUtils.checkBounds(10, 1, Integer.MAX_VALUE);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }

        // from+length > limit
        try {
            LangUtils.checkBounds(10, 0, 11);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            LangUtils.checkBounds(10, 11, 0);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        
        /*
         * random
         */
        
        for (int i=0;i<NBR_OF_CASES;i++) {
            // Bit shift so that it's not too rare to end up with
            // large or small magnitudes.
            final int limit = (random.nextInt()>>Math.min(31,random.nextInt(5)*8));
            final int from = (random.nextInt()>>Math.min(31,random.nextInt(5)*8));
            final int length = (random.nextInt()>>Math.min(31,random.nextInt(5)*8));
            try {
                LangUtils.checkBounds(limit,from,length);
                assertTrue(limit >= 0);
                assertTrue(from >= 0);
                assertTrue(length >= 0);
                assertTrue(from+length >= 0);
                assertTrue(from+length <= limit);
            } catch (IllegalArgumentException e) {
                assertTrue((limit < 0) || (length < 0));
            } catch (IndexOutOfBoundsException e) {
                assertTrue(length >= 0);
                assertTrue(limit >= 0);
                assertTrue((from < 0) || (from+length < 0) || (from+length > limit));
            }
        }
    }

    public void test_checkBounds_3long() {
        assertTrue(LangUtils.checkBounds(10L, 0L, 10L));
        assertTrue(LangUtils.checkBounds(10L, 10L, 0L));
        assertTrue(LangUtils.checkBounds(10L, 5L, 5L));
        assertTrue(LangUtils.checkBounds(10L, 9L, 1L));

        assertTrue(LangUtils.checkBounds(Long.MAX_VALUE, 0L, Long.MAX_VALUE));
        assertTrue(LangUtils.checkBounds(Long.MAX_VALUE, Long.MAX_VALUE-1L, 1L));

        // limit < 0
        try {
            LangUtils.checkBounds(-1L, 0L, 1L);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
        
        // length < 0
        try {
            LangUtils.checkBounds(10L, 5L, -1L);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
        // length and from < 0: length priority
        try {
            LangUtils.checkBounds(10L, -1L, -1L);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }

        // from < 0
        try {
            LangUtils.checkBounds(10L, -1L, 5L);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }

        // from+length overflow
        try {
            LangUtils.checkBounds(10L, Long.MAX_VALUE, 1L);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            LangUtils.checkBounds(10L, 1L, Long.MAX_VALUE);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }

        // from+length > limit
        try {
            LangUtils.checkBounds(10L, 0L, 11L);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            LangUtils.checkBounds(10L, 11L, 0L);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        
        /*
         * random
         */
        
        for (int i=0;i<NBR_OF_CASES;i++) {
            // Bit shift so that it's not too rare to end up with
            // large or small magnitudes.
            final long limit = (random.nextLong()>>Math.min(63,random.nextInt(5)*16));
            final long from = (random.nextLong()>>Math.min(63,random.nextInt(5)*16));
            final long length = (random.nextLong()>>Math.min(63,random.nextInt(5)*16));
            try {
                LangUtils.checkBounds(limit,from,length);
                assertTrue(limit >= 0);
                assertTrue(from >= 0);
                assertTrue(length >= 0);
                assertTrue(from+length >= 0);
                assertTrue(from+length <= limit);
            } catch (IllegalArgumentException e) {
                assertTrue((limit < 0) || (length < 0));
            } catch (IndexOutOfBoundsException e) {
                assertTrue(length >= 0);
                assertTrue(limit >= 0);
                assertTrue((from < 0) || (from+length < 0) || (from+length > limit));
            }
        }
    }

    public void test_toString_Thread() {
        assertEquals("null",LangUtils.toString((Thread)null));

        Thread thread = new Thread();
        long id = thread.getId();
        String name = "myName";
        thread.setName(name);
        int priority = thread.getPriority();
        if (thread.getThreadGroup() != null) {
            String groupName = thread.getThreadGroup().getName();
            assertEquals("Thread["+id+","+name+","+priority+","+groupName+"]",LangUtils.toString(thread));
        } else {
            assertEquals("Thread["+id+","+name+","+priority+"]",LangUtils.toString(thread));
        }
    }

    public void test_toStringStackTrace_Throwable() {
        assertEquals("null", LangUtils.toStringStackTrace(null));
        
        {
            MyRecordingPrintStream stream = new MyRecordingPrintStream();
            RuntimeException e = new RuntimeException();
            e.printStackTrace(stream);
            assertEquals(stream.recorded.toString(), LangUtils.toStringStackTrace(e));
        }
        
        {
            MyRecordingPrintStream stream = new MyRecordingPrintStream();
            RuntimeException c1 = new RuntimeException();
            IllegalArgumentException e = new IllegalArgumentException(c1);
            e.printStackTrace(stream);
            assertEquals(stream.recorded.toString(), LangUtils.toStringStackTrace(e));
        }
        
        {
            MyRecordingPrintStream stream = new MyRecordingPrintStream();
            IllegalStateException c2 = new IllegalStateException();
            RuntimeException c1 = new RuntimeException(c2);
            IllegalArgumentException e = new IllegalArgumentException(c1);
            e.printStackTrace(stream);
            assertEquals(stream.recorded.toString(), LangUtils.toStringStackTrace(e));
        }
    }

    public void test_appendStackTrace_StringBuilder_Throwable() {
        try {
            LangUtils.appendStackTrace(null, new RuntimeException());
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        /*
         * 
         */
        
        {
            StringBuilder sb = new StringBuilder("header");
            LangUtils.appendStackTrace(sb, null);
            assertEquals("headernull", sb.toString());
        }
        
        {
            StringBuilder sb = new StringBuilder("header");
            MyRecordingPrintStream stream = new MyRecordingPrintStream();
            
            RuntimeException e = new RuntimeException();
            
            e.printStackTrace(stream);
            LangUtils.appendStackTrace(sb,e);
            assertEquals("header"+stream.recorded.toString(), sb.toString());
        }
        
        {
            StringBuilder sb = new StringBuilder("header");
            MyRecordingPrintStream stream = new MyRecordingPrintStream();
            
            RuntimeException c1 = new RuntimeException();
            IllegalArgumentException e = new IllegalArgumentException(c1);
            
            e.printStackTrace(stream);
            LangUtils.appendStackTrace(sb,e);
            assertEquals("header"+stream.recorded.toString(), sb.toString());
        }

        {
            StringBuilder sb = new StringBuilder("header");
            MyRecordingPrintStream stream = new MyRecordingPrintStream();
            
            IllegalStateException c2 = new IllegalStateException();
            RuntimeException c1 = new RuntimeException(c2);
            IllegalArgumentException e = new IllegalArgumentException(c1);
            
            e.printStackTrace(stream);
            LangUtils.appendStackTrace(sb,e);
            assertEquals("header"+stream.recorded.toString(), sb.toString());
        }
    }

    /*
     * 
     */
    
    public void test_throwIfInterrupted() {
        // Supposing we are not interrupted.
        assertFalse(Thread.currentThread().isInterrupted());

        // Not interrupted.
        try {
            LangUtils.throwIfInterrupted();
            // ok
        } catch (InterruptedException e) {
            assertTrue(false);
        }

        // Interrupted.
        Thread.currentThread().interrupt();
        try {
            LangUtils.throwIfInterrupted();
            assertTrue(false);
        } catch (InterruptedException e) {
            // ok
        }
        // Interruption status cleared before exception thrown.
        assertFalse(Thread.currentThread().isInterrupted());
    }

    public void test_sleepNS_long() {
        // Supposing we are not interrupted.
        assertFalse(Thread.currentThread().isInterrupted());

        // Not interrupted (no duration).
        try {
            LangUtils.sleepNS(0L);
            // ok
        } catch (InterruptedException e) {
            assertTrue(false);
        }

        // Not interrupted (duration).
        try {
            LangUtils.sleepNS(10L);
            // ok
        } catch (InterruptedException e) {
            assertTrue(false);
        }

        // Interrupted (no duration).
        Thread.currentThread().interrupt();
        try {
            LangUtils.sleepNS(0L);
            assertTrue(false);
        } catch (InterruptedException e) {
            // ok
        }
        assertFalse(Thread.currentThread().isInterrupted());

        // Interrupted (duration).
        Thread.currentThread().interrupt();
        try {
            LangUtils.sleepNS(10L);
            assertTrue(false);
        } catch (InterruptedException e) {
            // ok
        }
        assertFalse(Thread.currentThread().isInterrupted());

        // Duration.
        final long durationNS = 2 * TOLERANCE_NS;
        long a = System.nanoTime();
        try {
            LangUtils.sleepNS(durationNS);
            // ok
        } catch (InterruptedException e) {
            assertTrue(false);
        }
        long b = System.nanoTime();
        assertEquals((double)durationNS,(double)(b-a),(double)TOLERANCE_NS);
    }

    public void test_waitNS_Object_long() {
        // Supposing we are not interrupted.
        assertFalse(Thread.currentThread().isInterrupted());

        // To stop long waits.
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        // Needed in case notify happens before wait start.
        final AtomicBoolean waitDone = new AtomicBoolean();

        final Object waitObject = new Object();

        // Monitor not taken (no duration).
        try {
            LangUtils.waitNS(waitObject,0L);
            assertTrue(false);
        } catch (InterruptedException e) {
            assertTrue(false);
        } catch (IllegalMonitorStateException e) {
            // ok
        }

        // Monitor not taken (short duration).
        try {
            LangUtils.waitNS(waitObject,10L);
            assertTrue(false);
        } catch (InterruptedException e) {
            assertTrue(false);
        } catch (IllegalMonitorStateException e) {
            // ok
        }

        // Monitor not taken (long duration).
        try {
            LangUtils.waitNS(waitObject,Long.MAX_VALUE);
            assertTrue(false);
        } catch (InterruptedException e) {
            assertTrue(false);
        } catch (IllegalMonitorStateException e) {
            // ok
        }

        // Not interrupted (no duration).
        try {
            synchronized (waitObject) {
                LangUtils.waitNS(waitObject,0L);
            }
            // ok
        } catch (InterruptedException e) {
            assertTrue(false);
        }

        // Not interrupted (small duration).
        try {
            synchronized (waitObject) {
                LangUtils.waitNS(waitObject,10L);
            }
            // ok
        } catch (InterruptedException e) {
            assertTrue(false);
        }

        // Not interrupted (long duration).
        waitDone.set(false);
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                while (!waitDone.get()) {
                    synchronized (waitObject) {
                        waitObject.notifyAll();
                    }
                    Unchecked.sleepMS(100);
                }
            }
        }, TOLERANCE_NS, TimeUnit.NANOSECONDS);
        try {
            synchronized (waitObject) {
                LangUtils.waitNS(waitObject,Long.MAX_VALUE);
            }
            waitDone.set(true);
            // ok
        } catch (InterruptedException e) {
            waitDone.set(true);
            assertTrue(false);
        }

        // Interrupted (no duration).
        Thread.currentThread().interrupt();
        try {
            synchronized (waitObject) {
                LangUtils.waitNS(waitObject,0L);
            }
            assertTrue(false);
        } catch (InterruptedException e) {
            // ok
        }
        assertFalse(Thread.currentThread().isInterrupted());

        // Interrupted (short duration).
        Thread.currentThread().interrupt();
        try {
            synchronized (waitObject) {
                LangUtils.waitNS(waitObject,10L);
            }
            assertTrue(false);
        } catch (InterruptedException e) {
            // ok
        }
        assertFalse(Thread.currentThread().isInterrupted());

        // Interrupted (long duration).
        // (No need to schedule a signal, due to interruption)
        Thread.currentThread().interrupt();
        try {
            synchronized (waitObject) {
                LangUtils.waitNS(waitObject,Long.MAX_VALUE);
            }
            assertTrue(false);
        } catch (InterruptedException e) {
            // ok
        }
        assertFalse(Thread.currentThread().isInterrupted());

        // Monitor not taken and interrupted (monitor priority) (no duration).
        Thread.currentThread().interrupt();
        try {
            LangUtils.waitNS(waitObject,0L);
            assertTrue(false);
        } catch (InterruptedException e) {
            assertTrue(false);
        } catch (IllegalMonitorStateException e) {
            // ok
        }
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted(); // clearing

        // Monitor not taken and interrupted (monitor priority) (small duration).
        Thread.currentThread().interrupt();
        try {
            LangUtils.waitNS(waitObject,10L);
            assertTrue(false);
        } catch (InterruptedException e) {
            assertTrue(false);
        } catch (IllegalMonitorStateException e) {
            // ok
        }
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted(); // clearing

        // Monitor not taken and interrupted (monitor priority) (long duration).
        Thread.currentThread().interrupt();
        try {
            LangUtils.waitNS(waitObject,Long.MAX_VALUE);
            assertTrue(false);
        } catch (InterruptedException e) {
            assertTrue(false);
        } catch (IllegalMonitorStateException e) {
            // ok
        }
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted(); // clearing

        // Duration.
        final long durationNS = 2 * TOLERANCE_NS;
        long a = System.nanoTime();
        try {
            synchronized (waitObject) {
                LangUtils.waitNS(waitObject,durationNS);
            }
            // ok
        } catch (InterruptedException e) {
            assertTrue(false);
        }
        long b = System.nanoTime();
        assertEquals((double)durationNS,(double)(b-a),(double)TOLERANCE_NS);

        Unchecked.shutdownAndAwaitTermination(executor);
    }
}
