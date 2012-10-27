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

/**
 * Very basic and general utility methods.
 * This class must only depend on the JDK.
 */
public class LangUtils {

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    public static final String LINE_SEPARATOR = System.getProperty("line.separator");
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * @param key Property's key.
     * @param defaultValue Value to return if there is no property for the specified key.
     * @return Property's value or default value if there is none.
     */
    public static String getProperty(
            final String key,
            final String defaultValue) {
        final String tmp = System.getProperty(key);
        if (tmp != null) {
            return tmp;
        } else {
            return defaultValue;
        }
    }
    
    /**
     * Uses Boolean.parseBoolean(String).
     * 
     * @param key Property's key.
     * @param defaultValue Value to return if there is no property for the specified key.
     * @return Property's value or default value if there is none.
     */
    public static boolean getBooleanProperty(
            final String key,
            boolean defaultValue) {
        final String tmp = System.getProperty(key);
        if (tmp != null) {
            return Boolean.parseBoolean(tmp);
        } else {
            return defaultValue;
        }
    }
    
    /**
     * Uses Integer.parseInt(String).
     * 
     * @param key Property's key.
     * @param defaultValue Value to return if there is no property for the specified key.
     * @return Property's value or default value if there is none.
     */
    public static int getIntProperty(
            final String key,
            int defaultValue) {
        final String tmp = System.getProperty(key);
        if (tmp != null) {
            return Integer.parseInt(tmp);
        } else {
            return defaultValue;
        }
    }
    
    /**
     * Uses Long.parseLong(String).
     * 
     * @param key Property's key.
     * @param defaultValue Value to return if there is no property for the specified key.
     * @return Property's value or default value if there is none.
     */
    public static long getLongProperty(
            final String key,
            long defaultValue) {
        final String tmp = System.getProperty(key);
        if (tmp != null) {
            return Long.parseLong(tmp);
        } else {
            return defaultValue;
        }
    }
    
    /**
     * Uses Float.parseFloat(String).
     * 
     * @param key Property's key.
     * @param defaultValue Value to return if there is no property for the specified key.
     * @return Property's value or default value if there is none.
     */
    public static float getFloatProperty(
            final String key,
            float defaultValue) {
        final String tmp = System.getProperty(key);
        if (tmp != null) {
            return Float.parseFloat(tmp);
        } else {
            return defaultValue;
        }
    }
    
    /**
     * Uses Double.parseDouble(String).
     * 
     * @param key Property's key.
     * @param defaultValue Value to return if there is no property for the specified key.
     * @return Property's value or default value if there is none.
     */
    public static double getDoubleProperty(
            final String key,
            double defaultValue) {
        final String tmp = System.getProperty(key);
        if (tmp != null) {
            return Double.parseDouble(tmp);
        } else {
            return defaultValue;
        }
    }
    
    /*
     * 
     */
    
    /**
     * @return True if assertions are enabled (at least for this class),
     *         false otherwise.
     */
    public static boolean assertionsEnabled() {
        // Computing it on each call, in case a time comes
        // where it might get changed at runtime.
        boolean tmp = false;
        assert(tmp = !tmp);
        return tmp;
    }

    /**
     * Useful methods for assertions that are always made,
     * either because they don't cost much, or are done in tests.
     * 
     * @param value A boolean value.
     * @return True if the specified boolean value is true.
     * @throws AssertionError if the specified boolean value is false.
     */
    public static boolean azzert(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
        return true;
    }
    
    /**
     * @return True if the specified objects are equal or both null.
     */
    public static boolean equalOrBothNull(final Object a, final Object b) {
        if (a == b) {
            return true;
        } else {
            return (a != null) ? a.equals(b) : false;
        }
    }

    /**
     * @param ref A reference.
     * @return True if the specified reference is non-null.
     * @throws NullPointerException if the specified reference is null.
     */
    public static boolean checkNonNull(Object ref) {
        if (ref == null) {
            throw new NullPointerException();
        }
        return true;
    }
    
    /**
     * @param limit Limit of an indexed collection.
     * @param from First index.
     * @param length Number of consecutive indexes from the first one included.
     * @return True if [from,from+length-1] is included in [0,limit[.
     * @throws IndexOutOfBoundsException if limit < 0, or from < 0, or length < 0,
     *         or from+length overflows, or from+length > limit.
     */
    public static boolean checkBounds(int limit, int from, int length) {
        // Similar code can be found in Buffer class, but it's
        // package-private.
        if ((limit|length|from|(from+length)|(limit-(from+length))) < 0) {
            throwIOOBE(limit, from, length);
        }
        return true;
    }

    /**
     * @param limit Limit of an indexed collection.
     * @param from First index.
     * @param length Number of consecutive indexes from the first one included.
     * @return True if [from,from+length-1] is included in [0,limit[.
     * @throws IndexOutOfBoundsException if limit < 0, or from < 0, or length < 0,
     *         or from+length overflows, or from+length > limit.
     */
    public static boolean checkBounds(long limit, long from, long length) {
        if ((limit|length|from|(from+length)|(limit-(from+length))) < 0) {
            throwIOOBE(limit, from, length);
        }
        return true;
    }
    
    /**
     * @return A string representing the specified thread, with thread id information
     *         in first position (not provided by Thread.toString()).
     */
    public static String toString(final Thread thread) {
        if (thread == null) {
            return ""+null;
        } else {
            final String threadName = thread.getName();
            // No grow if low id/priority values and group name <= thread name.
            StringBuilder sb = new StringBuilder(15+2*threadName.length());
            sb.append("Thread[");
            sb.append(thread.getId());
            sb.append(",");
            sb.append(threadName);
            sb.append(",");
            sb.append(thread.getPriority());
            ThreadGroup group = thread.getThreadGroup();
            if (group != null) {
                sb.append(",");
                sb.append(group.getName());
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * @param e A Throwable.
     * @return Corresponding String, as obtained in System.err
     *         by e.printStackTrace(), or "null" if the specified
     *         Throwable is null.
     */
    public static String toStringStackTrace(Throwable e) {
        final StringBuilder sb = new StringBuilder();
        appendStackTrace(sb, e);
        return sb.toString();
    }

    /**
     * @param sb StringBuilder where to append the String corresponding
     *        to the specified Throwable, as obtained in System.err
     *        by e.printStackTrace(), or "null" if the specified Throwable
     *        is null.
     * @param e A Throwable.
     */
    public static void appendStackTrace(
            StringBuilder sb,
            Throwable e) {
        sb.append(e);
        if (e != null) {
            sb.append(LangUtils.LINE_SEPARATOR);
            appendStackTraceOnly(sb, e, null);
        }
    }

    /*
     * threading
     */
    
    /**
     * Clears interruption status before throwing InterruptedException.
     * @throws InterruptedException if current thread is interrupted.
     */
    public static void throwIfInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }
    
    /**
     * @param ns Duration to sleep, in nanoseconds.
     * @throws InterruptedException if current thread is interrupted.
     */
    public static void sleepNS(long ns) throws InterruptedException {
        if (ns <= 0) {
            throwIfInterrupted();
            return;
        }
        long millis = ns/1000000;
        int nanos = (int)(ns - millis*1000000);
        Thread.sleep(millis, nanos);
    }

    /**
     * Current thread must own the monitor of the specified object.
     * 
     * @param waitObject Object to wait on.
     * @param ns Duration to wait, in nanoseconds.
     * @throws IllegalMonitorStateException if current thread does not own
     *         the monitor of the specified object.
     * @throws InterruptedException if current thread is interrupted.
     */
    public static void waitNS(Object waitObject, long ns) throws InterruptedException {
        if (ns >= Long.MAX_VALUE/2) {
            // Avoiding division and timing overhead.
            waitObject.wait();
        } else {
            if (ns <= 0) {
                // Doing this for homogeneity whatever the duration.
                if (!Thread.holdsLock(waitObject)) {
                    throw new IllegalMonitorStateException();
                }
                throwIfInterrupted();
                return;
            }
            long millis = ns/1000000;
            int nanos = (int)(ns - millis*1000000);
            waitObject.wait(millis, nanos);
        }
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * int version, for wrapping if from+length < 0, as for long version.
     */
    private static void throwIOOBE(int limit, int from, int length) {
        throw new IndexOutOfBoundsException("[from..from+length[ (["+from+".."+(from+length)+"[) must be in [0..limit[ ([0.."+limit+"[)");
    }

    private static void throwIOOBE(long limit, long from, long length) {
        throw new IndexOutOfBoundsException("[from..from+length[ (["+from+".."+(from+length)+"[) must be in [0..limit[ ([0.."+limit+"[)");
    }

    /**
     * This treatment is recursive.
     * 
     * Only appends stack trace, without exception's toString as header.
     * 
     * @param causedTrace Can be null, if the specified exception did not cause another.
     */
    private static void appendStackTraceOnly(
            StringBuilder sb,
            Throwable e,
            StackTraceElement[] causedTrace) {
        final StackTraceElement[] trace = e.getStackTrace();
        
        int m = trace.length-1;
        
        // Compute number of frames in common between this and caused.
        if (causedTrace != null) {
            int n = causedTrace.length-1;
            while ((m >= 0) && (n >=0) && trace[m].equals(causedTrace[n])) {
                m--;
                n--;
            }
        }
        final int framesInCommon = trace.length-1 - m;
        
        if (causedTrace != null) {
            sb.append("Caused by: " + e);
            sb.append(LangUtils.LINE_SEPARATOR);
        }
        for (int i=0;i<=m;i++) {
            sb.append("\tat " + trace[i]);
            sb.append(LangUtils.LINE_SEPARATOR);
        }
        if (framesInCommon != 0) {
            sb.append("\t... " + framesInCommon + " more");
            sb.append(LangUtils.LINE_SEPARATOR);
        }

        // Recurse if we have a cause.
        final Throwable cause = e.getCause();
        if (cause != null) {
            appendStackTraceOnly(sb, cause, trace);
        }
    }
}
