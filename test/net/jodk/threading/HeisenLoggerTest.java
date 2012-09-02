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
package net.jodk.threading;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.jodk.lang.LongWrapper;
import net.jodk.lang.Unchecked;
import net.jodk.test.ProcessorsUser;
import net.jodk.threading.HeisenLogger;

import junit.framework.TestCase;

public class HeisenLoggerTest extends TestCase {

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MyNoOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
        }
    }

    private static class MyPrintStream extends PrintStream {
        private final ConcurrentHashMap<LongWrapper, Boolean> linesCounters = new ConcurrentHashMap<LongWrapper, Boolean>();
        /**
         * One counter per flushing thread (daemon thread, or logging threads).
         */
        private final ThreadLocal<LongWrapper> tlLong = new ThreadLocal<LongWrapper>() {
            @Override
            public LongWrapper initialValue() {
                final LongWrapper counter = new LongWrapper();
                linesCounters.put(counter, Boolean.TRUE);
                return counter;
            }
        };
        public MyPrintStream() {
            super(new MyNoOutputStream());
        }
        @Override
        public void print(String s) {
            // One separator for each line.
            final int nbrOfLines = computeNbrOfLineSeparator(s);
            (tlLong.get().value) += nbrOfLines;
        }
        @Override
        public void flush() {
            // nothing to flush
        }
        public long computeNbrOfChars() {
            long sum = 0;
            for (Map.Entry<LongWrapper,Boolean> entry : linesCounters.entrySet()) {
                final LongWrapper counter = entry.getKey();
                sum += counter.value;
            }
            return sum;
        }
        private static int computeNbrOfLineSeparator(final String s) {
            int result = 0;
            int fromIndex = 0;
            while ((fromIndex = 1 + s.indexOf(LINE_SEPARATOR, fromIndex)) != 0) {
                ++result;
            }
            return result;
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    
    /*
     * Supposing noone did change and not restore defaults.
     */
    
    private static final PrintStream DEFAULT_PRINT_STREAM = HeisenLogger.getPrintStream();
    private static final boolean DEFAULT_REMOVE_FLUSHED_LOGGERS = HeisenLogger.getRemoveFlushedLoggers();
    private static final boolean DEFAULT_DO_LOG_DATE = HeisenLogger.getDoLogDate();
    private static final boolean DEFAULT_DO_LOG_THREAD = HeisenLogger.getDoLogThread();
    private static final boolean DEFAULT_DO_SORT_IF_DATED = HeisenLogger.getDoSortIfDated();
    private static final long DEFAULT_FLUSH_SLEEP_MS = HeisenLogger.getFlushSleepMS();
    private static final long DEFAULT_FLUSH_EVERY = HeisenLogger.getFlushEvery();

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    @Override
    public void setUp() throws Exception {
        ProcessorsUser.start();
    }

    @Override
    public void tearDown() throws Exception {
        ProcessorsUser.stop();
        resetDefaults();
    }

    public void test_defaults() {
        assertEquals(System.out,HeisenLogger.getPrintStream());
        assertEquals(true,HeisenLogger.getRemoveFlushedLoggers());
        assertEquals(false,HeisenLogger.getDoLogDate());
        assertEquals(false,HeisenLogger.getDoLogThread());
        assertEquals(true,HeisenLogger.getDoSortIfDated());
        assertEquals(10L,HeisenLogger.getFlushSleepMS());
        assertEquals(1000L,HeisenLogger.getFlushEvery());
    }

    public void test_logsAndFlush_logThread() {
        HeisenLogger.setDoLogThread(true);
        private_test_logsAndFlush_noConfig();
    }

    public void test_logsAndFlush_noRemoveAndNoDate() {
        HeisenLogger.setRemoveFlushedLoggers(false);
        HeisenLogger.setDoLogDate(false);
        HeisenLogger.setDoSortIfDated(false);
        private_test_logsAndFlush_noConfig();
    }

    public void test_logsAndFlush_noRemoveAndDateAndNoSort() {
        HeisenLogger.setRemoveFlushedLoggers(false);
        HeisenLogger.setDoLogDate(true);
        HeisenLogger.setDoSortIfDated(false);
        private_test_logsAndFlush_noConfig();
    }

    public void test_logsAndFlush_noRemoveAndDateAndSort() {
        HeisenLogger.setRemoveFlushedLoggers(false);
        HeisenLogger.setDoLogDate(true);
        HeisenLogger.setDoSortIfDated(true);
        private_test_logsAndFlush_noConfig();
    }

    public void test_logsAndFlush_removeAndNoDate() {
        HeisenLogger.setRemoveFlushedLoggers(true);
        HeisenLogger.setDoLogDate(false);
        HeisenLogger.setDoSortIfDated(false);
        private_test_logsAndFlush_noConfig();
    }

    public void test_logsAndFlush_removeAndDateAndNoSort() {
        HeisenLogger.setRemoveFlushedLoggers(true);
        HeisenLogger.setDoLogDate(true);
        HeisenLogger.setDoSortIfDated(false);
        private_test_logsAndFlush_noConfig();
    }

    public void test_logsAndFlush_removeAndDateAndSort() {
        HeisenLogger.setRemoveFlushedLoggers(true);
        HeisenLogger.setDoLogDate(true);
        HeisenLogger.setDoSortIfDated(true);
        private_test_logsAndFlush_noConfig();
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private void private_test_logsAndFlush_noConfig() {
        final int nbrOfRuns = 100;
        final int nbrOfThreads = 4;
        final long nbrOfLogsPerThread = 10L * 1000L;

        // Else won't ever flush on log.
        assertTrue(nbrOfLogsPerThread > HeisenLogger.getFlushEvery());

        for (int k=0;k<nbrOfRuns;k++) {
            final MyPrintStream printStream = new MyPrintStream();
            HeisenLogger.setPrintStream(printStream);

            final ExecutorService executor = Executors.newCachedThreadPool();

            for (int n=0;n<nbrOfThreads;n++) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        for (long i=0;i<nbrOfLogsPerThread;i++) {
                            HeisenLogger.log(i);
                        }
                    }
                });
            }

            Unchecked.shutdownAndAwaitTermination(executor);

            // Should flush everything here, since publisher threads are now idle.
            HeisenLogger.flushPendingLogsAndStream();

            assertEquals(nbrOfThreads * nbrOfLogsPerThread, printStream.computeNbrOfChars());
        }
    }
    
    private static void resetDefaults() {
        HeisenLogger.setPrintStream(DEFAULT_PRINT_STREAM);
        HeisenLogger.setRemoveFlushedLoggers(DEFAULT_REMOVE_FLUSHED_LOGGERS);
        HeisenLogger.setDoLogDate(DEFAULT_DO_LOG_DATE);
        HeisenLogger.setDoLogThread(DEFAULT_DO_LOG_THREAD);
        HeisenLogger.setDoSortIfDated(DEFAULT_DO_SORT_IF_DATED);
        HeisenLogger.setFlushSleepMS(DEFAULT_FLUSH_SLEEP_MS);
        HeisenLogger.setFlushEvery(DEFAULT_FLUSH_EVERY);
    }
}
