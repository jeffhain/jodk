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

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.jodk.lang.LangUtils;

/**
 * Logger which log methods are designed to usually entail a very small overhead
 * and minimal memory synchronization (at most a volatile lazy set, and possibly
 * a volatile read).
 * 
 * Configuration is public and non-final, for setting before or while debugging,
 * static, for simplicity, and non-volatile, for performances reasons, so its
 * modifications are typically not immediately visible across threads.
 * 
 * This logger is especially useful to debug concurrent treatments,
 * in particular heisenbugs that direct calls to System.out or other
 * logging facilities would prevent to happen.
 * 
 * Logs are added to a (thread-)local logger instance, and flushed periodically by a
 * single daemon thread, or after a number of logs done by a same logger instance,
 * or on user command.
 * 
 * Flushing thread refers to whatever thread calls flushPendingLogs() method
 * (the daemon thread is by nature a flushing thread).
 * 
 * Instance log methods are not thread-safe, but allow for very efficient logging.
 * For a logger instance, the (current) thread that makes use of them is referred
 * to as the logging thread.
 * 
 * Static log method is thread-safe and uses a default thread-local logger instance.
 * 
 * Object's toString is computed on flush into print stream,
 * not at log time; but it's of course possible to log
 * an object's toString as a regular log message.
 * 
 * If the last object to log is a Throwable, not logging its toString(), but
 * the same content than obtained in System.err by Throwable.printStackTrace().
 * This makes log(...) method behave somehow like usual log treatments
 * (log(Object,Throwable)), except that if only a Throwable is logged
 * it still prints its stack trace.
 * 
 * Logs are preceded by logger (instance) id, which is a unique long,
 * but date is logged before logger id, to allow for easy temporal sorting.
 */
public class HeisenLogger {
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static PrintStream PRINT_STREAM = System.out;
    public static PrintStream getPrintStream() {
        return PRINT_STREAM;
    }
    public static void setPrintStream(final PrintStream printStream) {
        PRINT_STREAM = printStream;
    }

    /**
     * If false, loggers are never removed from shared set,
     * which can cause a memory leak and longer flushes.
     * If true, causes a volatile read (and add into set
     * if it is surely not in it) in logLocalLogs() method.
     */
    private static boolean REMOVE_FLUSHED_LOGGERS = true;
    public static boolean getRemoveFlushedLoggers() {
        return REMOVE_FLUSHED_LOGGERS;
    }
    public static void setRemoveFlushedLoggers(boolean removeFlushedLoggers) {
        REMOVE_FLUSHED_LOGGERS = removeFlushedLoggers;
    }

    /**
     * False by default because System.nanoTime() can be quite slow.
     */
    private static boolean DO_LOG_DATE = false;
    public static boolean getDoLogDate() {
        return DO_LOG_DATE;
    }
    public static void setDoLogDate(boolean doLogDate) {
        DO_LOG_DATE = doLogDate;
    }

    /**
     * False by default because threads toString() is relatively costly,
     * and logger id is logged anyway, which already allows for some kind
     * of log source identification.
     */
    private static boolean DO_LOG_THREAD = false;
    public static boolean getDoLogThread() {
        return DO_LOG_THREAD;
    }
    public static void setDoLogThread(boolean doLogThread) {
        DO_LOG_THREAD = doLogThread;
    }

    /**
     * Logs are sorted by date only for a same flush to print stream.
     */
    private static boolean DO_SORT_IF_DATED = true;
    public static boolean getDoSortIfDated() {
        return DO_SORT_IF_DATED;
    }
    public static void setDoSortIfDated(boolean doSortIfDated) {
        DO_SORT_IF_DATED = doSortIfDated;
    }

    /**
     * For daemon thread.
     */
    private static long FLUSH_SLEEP_MS = 10L;
    public static long getFlushSleepMS() {
        return FLUSH_SLEEP_MS;
    }
    public static void setFlushSleepMS(long flushSleepMS) {
        FLUSH_SLEEP_MS = flushSleepMS;
    }

    /**
     * To avoid too many logs piling-up, especially if daemon thread
     * can't keep up.
     * This value is used per-logger, to avoid usage of a concurrent counter.
     * Max number of logs stored is approximately this value times the
     * number of loggers.
     */
    private static long FLUSH_EVERY = 1000L;
    public static long getFlushEvery() {
        return FLUSH_EVERY;
    }
    public static void setFlushEvery(long flushEvery) {
        FLUSH_EVERY = flushEvery;
    }

    //--------------------------------------------------------------------------
    // PUBLIC CLASSES
    //--------------------------------------------------------------------------

    /**
     * Holds a non-volatile reference to a logger instance.
     * 
     * Making sure this name doesn't start with "l",
     * else it can come up first in auto-completion
     * while looking for log method.
     */
    public static class RefLocal {
        private HeisenLogger logger;
    }

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MyLogData {
        private final String loggerId;
        private final long dateNS;
        private final Thread thread;
        private final Object[] messages;
        /**
         * Next log data, if any, for same batch.
         * Written by logging thread, before lazy set
         * that makes it visible to flush treatments.
         */
        private MyLogData next;
        public MyLogData(
                final String loggerId,
                long dateNS,
                final Thread thread,
                final Object... messages) {
            this.loggerId = loggerId;
            this.dateNS = dateNS;
            this.thread = thread;
            this.messages = messages;
        }
        public void appendString(StringBuilder sb) {
            // Date logged first, to allow for easy sorting by date.
            if (DO_LOG_DATE) {
                sb.append(this.dateNS);
                sb.append(" ");
            }
            sb.append(this.loggerId);
            if (this.thread != null) {
                sb.append(" ");
                sb.append(this.thread);
            }
            final Object[] messages = this.messages;
            final int nMinusOne = messages.length-1;
            for (int i=0;i<nMinusOne;i++) {
                sb.append(" ; ");
                sb.append(messages[i]);
            }
            if (nMinusOne >= 0) {
                sb.append(" ; ");
                final Object lastMessage = messages[nMinusOne];
                if (lastMessage instanceof Throwable) {
                    sb.append(LINE_SEPARATOR);
                    LangUtils.appendStackTrace(sb, (Throwable)lastMessage);
                } else {
                    sb.append(lastMessage);
                }
            }
            sb.append(LINE_SEPARATOR);
        }
    }
    
    /**
     * To sort logs by date.
     */
    private static class MyLogDataComparator implements Comparator<MyLogData> {
        @Override
        public int compare(MyLogData a, MyLogData b) {
            if (a.dateNS < b.dateNS) {
                return -1;
            } else if (a.dateNS > b.dateNS) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    /**
     * Extended AtomicReference holds reference to eventual next
     * batch of log data for same logger.
     */
    private static class MyLogBatch extends AtomicReference<MyLogBatch> {
        private static final long serialVersionUID = 1L;
        /**
         * Before first log data for this batch.
         * (Using such a header to avoid an if statement
         * in logLocal(Object...) method. Also implies
         * to always have a log batch instance in pending
         * batch reference.)
         */
        private final MyLogData beforeFirstLogData = new MyLogData(null,0L, null);
        /**
         * Only used by logging thread.
         */
        private MyLogData lastLogData = this.beforeFirstLogData;
        /**
         * Only usable by logging thread.
         */
        public boolean isEmpty() {
            // Empty if only the header in the list.
            return this.lastLogData == this.beforeFirstLogData;
        }
        /**
         * Only usable by logging thread.
         */
        public void addLog(final MyLogData logData) {
            this.lastLogData.next = logData;
            this.lastLogData = logData;
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    /*
     * static members
     */

    private static final String LINE_SEPARATOR = LangUtils.LINE_SEPARATOR;
    
    private static final Lock MAIN_LOCK = new ReentrantLock();

    private static final MyLogDataComparator LOG_DATA_COMPARATOR = new MyLogDataComparator();

    /**
     * To have a date starting from zero, which is handy and avoids wrapping.
     * Using a common initial date for all loggers, to allow for easy date
     * comparisons for logs from different loggers.
     */
    private static final long INITIAL_DATE_NS = System.nanoTime();

    /**
     * Guarded by main lock.
     */
    private static final StringBuilder FLUSH_BUFFER = new StringBuilder();

    private static final AtomicReference<Thread> DAEMON;
    private static final NullElseAtomicReference<Thread> FAST_DAEMON;
    static {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (true) {
                    flushPendingLogsAndStream();
                    try {
                        Thread.sleep(FLUSH_SLEEP_MS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        // ok stopping
                        break;
                    }
                }
            }
        };
        final Thread thread = new Thread(runnable, HeisenLogger.class.getSimpleName());
        thread.setDaemon(true);
        DAEMON = new AtomicReference<Thread>(thread);
        FAST_DAEMON = new NullElseAtomicReference<Thread>(DAEMON);
    }

    private static final ThreadLocal<HeisenLogger> tlLogger = new ThreadLocal<HeisenLogger>() {
        @Override
        public HeisenLogger initialValue() {
            return newLogger();
        }
    };

    /**
     * Set of local loggers that might contain pending logs.
     */
    private static final ConcurrentHashMap<HeisenLogger, Boolean> pendingLoggerSet = new ConcurrentHashMap<HeisenLogger, Boolean>();

    private static final AtomicLong LOGGER_ID_PROVIDER = new AtomicLong();

    /*
     * instance fields
     */

    /**
     * For logger (and thread, if thread-specific) identification in logs.
     */
    private final String loggerId = Long.toString(LOGGER_ID_PROVIDER.incrementAndGet());

    /**
     * Used to remove this logger from the set once it has been flushed
     * (without using weak references).
     * Note that the default value (0) is the right one in case we don't
     * remove flushed loggers: in that case, we don't have to do a volatile
     * (non-lazy) set have this value properly initialized.
     */
    private final AtomicInteger inPendingSet = new AtomicInteger();

    /**
     * Can't use first pending batch instead, because it could be null,
     * and logging thread would need to initialize it, i.e. read it
     * and then eventually set it, which would require additional
     * memory synchronizations with flushing thread.
     * 
     * Only used in main lock (after construction).
     */
    private MyLogBatch beforeNextPendingBatch = new MyLogBatch();

    /**
     * Last batch from this logger to be pending
     * for being logged by flushing thread.
     * 
     * Only used by logging thread.
     */
    private MyLogBatch lastPendingBatch = this.beforeNextPendingBatch;

    /**
     * Batch of local logs, that only become pending, and visible
     * to flushing thread, after call to logLocalLogs().
     * 
     * Only used by logging thread.
     */
    private MyLogBatch localBatch = new MyLogBatch();

    /**
     * Only used by logging thread.
     */
    private long nbrOfLogsWithoutFlush = 0;

    /*
     * temps
     */

    /**
     * For sorting.
     * Guarded by main lock.
     */
    private static MyLogData[] tmpLogDataArray = new MyLogData[4];

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /*
     * static methods
     */

    /**
     * A possible use-case is to create a logger for a few local logs,
     * and actually make them visible to flush treatments (using logLocalLogs()
     * method) only if desired.
     * 
     * @return A new (and non thread-safe) logger.
     */
    public static HeisenLogger newLogger() {
        final HeisenLogger logger = new HeisenLogger();
        if (!REMOVE_FLUSHED_LOGGERS) {
            // Won't be removed from set after flush, in which case we allow
            // logLocalLogs() method not to bother checking if it must be added
            // or not in the set, so we must do it here.
            putInPendingLoggerSet(logger);
        }
        return logger;
    }

    /**
     * @return Default thread-local logger.
     */
    public static HeisenLogger getDefaultThreadLocalLogger() {
        return tlLogger.get();
    }

    /**
     * This treatment makes it easy to quickly retrieve default thread-local
     * logger across treatments known to always be used by a same thread,
     * by reducing thread-local usage.
     * 
     * @param ref A RefLocal, updated with default thread-local
     *        logger if empty.
     * @return Logger, if any, referenced by the specified RefLocal,
     *         else default thread-local logger.
     */
    public static HeisenLogger getDefaultThreadLocalLogger(RefLocal ref) {
        HeisenLogger logger = ref.logger;
        if (logger == null) {
            logger = tlLogger.get();
            ref.logger = logger;
        }
        return logger;
    }

    /**
     * Together "main" (handy) and "convenience" (just calls other methods) log method.
     * 
     * Equivalent to successive calls to getDefaultThreadLocalLogger(),
     * logLocal(Object...) and then logLocalLogs().
     */
    public static void log(Object... messages) {
        final HeisenLogger logger = getDefaultThreadLocalLogger();
        logger.logLocal(messages);
        logger.logLocalLogs();
    }
    
    /**
     * Flush pending logs into print stream.
     * 
     * Will miss local logs, and last pending logs done by other threads that
     * did not synchronize main memory with their memory yet.
     */
    public static void flushPendingLogs() {
        MAIN_LOCK.lock();
        try {
            final boolean doSort = (DO_LOG_DATE && DO_SORT_IF_DATED);
            MyLogData[] logDataArray = tmpLogDataArray;
            int nbrOfLogsToSort = 0;

            FLUSH_BUFFER.setLength(0);

            final Iterator<Map.Entry<HeisenLogger, Boolean>> it = pendingLoggerSet.entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<HeisenLogger, Boolean> entry;
                try {
                    entry = it.next();
                } catch (NoSuchElementException e) {
                    // quiet
                    break;
                }
                final HeisenLogger logger = entry.getKey();

                // Need to remove thread data before iterating on its logs,
                // else logs added after iteration and before removal might be lost.
                if (REMOVE_FLUSHED_LOGGERS) {
                    it.remove();
                    // Need to set it after remove, else logging thread might
                    // add it to the set between calls to setNotInPendingSet()
                    // and remove(), and the removal will go unnoticed.
                    logger.setNotInPendingSet();
                }

                MyLogBatch anteNextPending = logger.beforeNextPendingBatch;
                MyLogBatch logBatch;
                // Getting log data lazily set by user thread.
                while ((logBatch = anteNextPending.get()) != null) {
                    // Batch never empty here, for only added if non-empty.
                    MyLogData logData = logBatch.beforeFirstLogData.next;
                    while (true) {
                        if (doSort) {
                            if (nbrOfLogsToSort == logDataArray.length - 1) {
                                if (logDataArray.length == Integer.MAX_VALUE) {
                                    // Already did last growth.
                                    // Not trying fancy sorts, just giving up.
                                    throw new UnsupportedOperationException((nbrOfLogsToSort+1L)+" logs to sort");
                                }
                                int newCapacity = 2*logDataArray.length;
                                if (newCapacity < 0) {
                                    // Overflow: last growth.
                                    newCapacity = Integer.MAX_VALUE;
                                }
                                final MyLogData[] newLogDataArray = new MyLogData[newCapacity];
                                System.arraycopy(logDataArray, 0, newLogDataArray, 0, nbrOfLogsToSort);
                                logDataArray = newLogDataArray;
                                tmpLogDataArray = newLogDataArray;
                            }
                            logDataArray[nbrOfLogsToSort++] = logData;
                        } else {
                            logData.appendString(FLUSH_BUFFER);
                        }
                        final MyLogData nextLogData = logData.next;
                        if (nextLogData == null) {
                            break;
                        }
                        logData.next = null; // helping GC
                        logData = nextLogData;
                    }

                    // Lazy set OK, since done AFTER first lazy set's result
                    // became visible (if current thread is not log's user thread),
                    // and BEFORE another thread would eventually read this value,
                    // due to main lock which will synchronize main memory with
                    // current thread memory when we will release it.
                    anteNextPending.lazySet(null); // helping GC

                    anteNextPending = logBatch;
                }

                logger.beforeNextPendingBatch = anteNextPending;
            }

            /*
             * 
             */

            if (nbrOfLogsToSort != 0) {
                Arrays.sort(
                        logDataArray,
                        0,
                        nbrOfLogsToSort,
                        LOG_DATA_COMPARATOR);

                for (int i=0;i<nbrOfLogsToSort;i++) {
                    final MyLogData logData = logDataArray[i];
                    logData.appendString(FLUSH_BUFFER);
                }
            }

            /*
             * 
             */

            // Print in main lock,
            // to make sure logs are done in proper sequence.
            PRINT_STREAM.print(FLUSH_BUFFER.toString());
        } finally {
            MAIN_LOCK.unlock();
        }
    }

    /**
     * Flush pending logs into print stream,
     * and flushes print stream.
     * 
     * Will miss local logs, and last pending logs done by other threads that
     * did not synchronize main memory with their memory yet.
     */
    public static void flushPendingLogsAndStream() {
        flushPendingLogs();
        PRINT_STREAM.flush();
    }
    
    /**
     * Useful if wanting to create timestamps
     * homogeneous with this class ones.
     * 
     * @return Value subtracted from System.nanoTime()
     *         to obtain logs timestamps.
     */
    public static long getInitialDateNS() {
        return INITIAL_DATE_NS;
    }

    /*
     * instance methods
     */

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName());
        sb.append("@");
        sb.append(Integer.toHexString(this.hashCode()));
        sb.append("[removed=");
        sb.append(this.isNotInPendingSet());
        sb.append("]");
        return sb.toString();
    }

    /**
     * Not thread-safe.
     * 
     * Lowest-overhead log method.
     * Does not use any lock or volatile variable.
     * 
     * Adds the specified log to local logs of this logger
     * (does not make it visible to flush treatments).
     */
    public void logLocal(Object... messages) {
        final MyLogData logData = new MyLogData(
                this.loggerId,
                (DO_LOG_DATE ? getDateNS() : 0L),
                (DO_LOG_THREAD ? Thread.currentThread() : null),
                messages);
        this.localBatch.addLog(logData);
        ++this.nbrOfLogsWithoutFlush;
    }

    /**
     * Not thread-safe.
     * 
     * Makes local logs pending, i.e. makes them visible
     * to flush treatments, possibly after some time,
     * since it is done using a lazy set.
     * 
     * First calls usually entail a CAS, for daemon thread start,
     * and some calls also entail both volatile write and
     * volatile read semantics, for set of active loggers
     * management, and eventual periodic flushing.
     */
    public void logLocalLogs() {

        /*
         * Moving batch from being local batch
         * to being last pending batch.
         */

        final MyLogBatch logBatch = this.localBatch;
        if (logBatch.isEmpty()) {
            // No local log.
            return;
        }
        this.localBatch = new MyLogBatch();

        this.lastPendingBatch.lazySet(logBatch);
        this.lastPendingBatch = logBatch;

        /*
         * 
         */

        startDaemonIfNeeded();

        /*
         * 
         */

        // Non-volatile test first.
        if (REMOVE_FLUSHED_LOGGERS && this.isNotInPendingSet()) {
            // Not put just after construction, and is not
            // in pending set: adding this to pending set.
            putInPendingLoggerSet(this);
        }

        if (this.nbrOfLogsWithoutFlush >= FLUSH_EVERY) {
            // Flushing everyone's pending logs.
            flushPendingLogsAndStream();
            this.nbrOfLogsWithoutFlush = 0;
        }
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /*
     * static methods
     */

    private static void startDaemonIfNeeded() {
        final Thread daemon = FAST_DAEMON.get();
        if ((daemon != null) && DAEMON.compareAndSet(daemon, null)) {
            daemon.start();
        }
    }

    private static void putInPendingLoggerSet(HeisenLogger logger) {
        // Need to set it before put, else flushing thread might
        // remove it from the set between calls to put(...)
        // and lazySetInPendingSet(), and the removal will go
        // unnoticed.
        // Lazy set OK because subsequent put ensures required
        // memory synchronization (before the actual put).
        logger.lazySetInPendingSet();
        pendingLoggerSet.put(logger, Boolean.TRUE);
    }

    private static long getDateNS() {
        return System.nanoTime() - INITIAL_DATE_NS;
    }

    /*
     * instance methods
     */

    private HeisenLogger() {
    }

    /**
     * @return True if is surely not in pending set,
     *         false if might or might not be in pending set.
     */
    private boolean isNotInPendingSet() {
        return this.inPendingSet.get() == 0;
    }

    private void setNotInPendingSet() {
        this.inPendingSet.set(0);
    }

    private void lazySetInPendingSet() {
        this.inPendingSet.lazySet(1);
    }
}
