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
package net.jodk.test;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

import net.jodk.lang.LangUtils;
import net.jodk.lang.Unchecked;

public class TestUtils {

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final String JVM_INFO = readJVMInfo();
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * @return JVM info to serve as header for tests logs.
     */
    public static String getJVMInfo() {
        return JVM_INFO;
    }
    
    /**
     * @param ns A duration in nanoseconds.
     * @return The specified duration in seconds, rounded to 3 digits past comma.
     */
    public static double nsToSRounded(long ns) {
        return Math.round(ns/1e6)/1e3;
    }
    
    /**
     * Sleeps in chunks of 10ms, to prevent the risk of a GC
     * eating the whole sleeping duration, and not letting
     * program enough duration to make progress.
     * 
     * Useful to ensure GC-proof-ness of tests sleeping for
     * some time to let concurrent treatment make progress.
     * 
     * @param ms Duration to sleep for, in milliseconds.
     */
    public static void sleepMSInChunks(long ms) {
        final long chunkMS = 10;
        while (ms >= chunkMS) {
            Unchecked.sleepMS(chunkMS);
            ms -= chunkMS;
        }
    }
    
    /**
     * Sleeps 100 ms, flushes System.out and System.err,
     * and triggers a GC.
     */
    public static void settle() {
        // Wait first, in case some short busy-waits or alike are still busy.
        // 100ms should be enough.
        sleepMSInChunks(100);
        // err flush last for it helps error appearing last.
        System.out.flush();
        System.err.flush();
        System.gc();
    }

    /**
     * Settles and prints a new line.
     */
    public static void settleAndNewLine() {
        settle();
        System.out.println("");
        System.out.flush();
    }

    /**
     * @return A new temp file, configured to be deleted on exit
     *         (not deleted if not properly closed or JVM crash).
     */
    public static File newTempFile() {
        try {
            File file = File.createTempFile("jodk_test_", ".tmp");
            file.deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private static String readJVMInfo() {
        StringBuilder sb = new StringBuilder();
        final String[] SYSTEM_PROPERTIES = new String[] {
                "java.vm.name",
                "java.runtime.version",
                "java.class.version",
                "os.name",
                "os.arch",
                "os.version",
                "sun.arch.data.model"
        };
        for (int i=0;i<SYSTEM_PROPERTIES.length;i++) {
            sb.append(SYSTEM_PROPERTIES[i]+"="+System.getProperty(SYSTEM_PROPERTIES[i]));
            sb.append(LangUtils.LINE_SEPARATOR);
        }
        sb.append("availableProcessors: "+Runtime.getRuntime().availableProcessors());
        sb.append(LangUtils.LINE_SEPARATOR);
        sb.append("JVM input arguments: "+ManagementFactory.getRuntimeMXBean().getInputArguments());
        sb.append(LangUtils.LINE_SEPARATOR);
        return sb.toString();
    }
}
