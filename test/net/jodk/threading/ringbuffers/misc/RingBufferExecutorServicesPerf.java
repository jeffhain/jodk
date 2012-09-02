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
package net.jodk.threading.ringbuffers.misc;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import net.jodk.lang.InterfaceFactory;
import net.jodk.lang.NumbersUtils;
import net.jodk.lang.Unchecked;
import net.jodk.test.TestUtils;
import net.jodk.threading.AbortingRejectedExecutionHandler;
import net.jodk.threading.locks.Condilocks;
import net.jodk.threading.locks.InterfaceCondilock;

public class RingBufferExecutorServicesPerf {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean BENCH_JDK = true;

    private static final int NBR_OF_PROC = Runtime.getRuntime().availableProcessors();
    private static final int CEILED_NBR_OF_PROC = NumbersUtils.ceilingPowerOfTwo(NBR_OF_PROC);

    private static final int MIN_PARALLELISM = 1;
    private static final int MAX_PARALLELISM = 2 * CEILED_NBR_OF_PROC;

    /**
     * For non-monotonic claim.
     */
    private static final int NBR_OF_CELLS_PER_THREAD = 2;
    
    private static final int MIN_RING_BUFFER_CAPACITY_PER_THREAD = 16 * 1024;

    private static final long NBR_OF_CALLS = 10L * 1000L * 1000L;
    private static final int NBR_OF_RUNS = 2;

    /**
     * False to be fair, else might miss rejections on shut down.
     */
    private static final boolean WRITE_LAZY_SETS = false;
    
    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MyExecutorServiceHelper {
        private final ExecutorService executorService;
        private final ExecutorService backingExecutorService;
        private final ExecutorService executorServiceForCalls;
        /**
         * @param backingExecutorService ExecutorService used for worker threads.
         *        Must be specific to the specified executor service (to avoid
         *        thread-local pollution).
         */
        public MyExecutorServiceHelper(
                final ExecutorService executorServiceForCalls,
                final ExecutorService executorService,
                final ExecutorService backingExecutorService) {
            if (executorServiceForCalls == null) {
                throw new NullPointerException();
            }
            if (backingExecutorService == null) {
                throw new NullPointerException();
            }
            this.executorServiceForCalls = executorServiceForCalls;
            this.executorService = executorService;
            this.backingExecutorService = backingExecutorService;
        }
        /**
         * Constructor for executor services using their own threads.
         */
        public MyExecutorServiceHelper(
                final ExecutorService executorServiceForCalls,
                final ExecutorService executorService) {
            if (executorServiceForCalls == null) {
                throw new NullPointerException();
            }
            this.executorServiceForCalls = executorServiceForCalls;
            this.executorService = executorService;
            this.backingExecutorService = null;
        }
        /**
         * @return Executor service to use for calls.
         */
        public ExecutorService getExecutorServiceForCalls() {
            return this.executorServiceForCalls;
        }
        public ExecutorService getExecutorService() {
            return this.executorService;
        }
        /**
         * @return Backing executor service, used for worker threads.
         */
        public ExecutorService getBackingExecutorService() {
            return this.backingExecutorService;
        }
    }

    private static class MyExecutorData {
        private final InterfaceFactory<MyExecutorServiceHelper> executorHelperFactory;
        private final int nbrOfWorkers;
        private final String executorInfo;
        public MyExecutorData(
                final InterfaceFactory<MyExecutorServiceHelper> executorHelperFactory,
                int nbrOfWorkers,
                final String executorInfo) {
            this.executorHelperFactory = executorHelperFactory;
            this.nbrOfWorkers = nbrOfWorkers;
            this.executorInfo = executorInfo;
        }
    }

    private static class MyPublisherRunnable implements Runnable {
        private final AtomicInteger countDown;
        private final Executor executor;
        private final long nbrOfCalls;
        public MyPublisherRunnable(
                final AtomicInteger countDown,
                final Executor executor,
                long nbrOfCalls) {
            this.countDown = countDown;
            this.executor = executor;
            this.nbrOfCalls = nbrOfCalls;
        }
        @Override
        public void run() {
            for (int i=0;i<this.nbrOfCalls;i++) {
                this.executor.execute(EMPTY_RUNNABLE);
            }
            decrementAndNotifyAllIfZero(this.countDown);
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final Runnable EMPTY_RUNNABLE = new Runnable() {
        @Override
        public void run() {
        }
    };
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println(TestUtils.getJVMInfo());
        newRun(args);
    }

    public static void newRun(String[] args) {
        new RingBufferExecutorServicesPerf().run(args);
    }
    
    public RingBufferExecutorServicesPerf() {
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private void run(String[] args) {
        // XXX
        System.out.println("--- "+RingBufferExecutorServicesPerf.class.getSimpleName()+"... ---");
        System.out.println("number of calls = "+NBR_OF_CALLS);
        System.out.println("Wait types: Bu = Busy, Y = Yielding, B = Blocking, BE = Blocking Elusive");
        System.out.println("t1 = time elapsed up to last execute call done");
        System.out.println("t2 = time elapsed up to last runnable call done");
        if (BENCH_JDK) {
            System.out.println("If ForkJoinPool is called from itself, threads done publishing");
            System.out.println("will contribute as workers, which biases the bench.");
        }

        benchThroughput();

        System.out.println("--- ..."+RingBufferExecutorServicesPerf.class.getSimpleName()+" ---");
    }

    private static void benchThroughput() {
        int nbrOfPublishers;
        int nbrOfWorkers;
        for (int parallelism=MIN_PARALLELISM;parallelism<=MAX_PARALLELISM;parallelism*=2) {
            System.out.println("");
            System.out.println("parallelism = "+parallelism);
            if (true && parallelism >= 2) {
                if (true) {
                    benchThroughput(nbrOfPublishers = 1, nbrOfWorkers = parallelism);
                }
                if (true && parallelism >= 4) {
                    benchThroughput(nbrOfPublishers = 2, nbrOfWorkers = parallelism);
                }
                if (true) {
                    benchThroughput(nbrOfPublishers = parallelism, nbrOfWorkers = 1);
                }
                if (true && parallelism >= 4) {
                    benchThroughput(nbrOfPublishers = parallelism, nbrOfWorkers = 2);
                }
            }
            if (true) {
                benchThroughput(nbrOfPublishers = parallelism, nbrOfWorkers = parallelism);
            }
        }
    }

    private static void benchThroughput(
            int nbrOfPublishers,
            int nbrOfWorkers) {
        System.out.println("");
        final ArrayList<MyExecutorData> executorsData = newExecutorsData(
                nbrOfPublishers,
                nbrOfWorkers);
        for (MyExecutorData executorData : executorsData) {
            benchThroughput(
                    executorData,
                    nbrOfPublishers,
                    NBR_OF_CALLS);
        }
    }

    /**
     * Doing multiple sub benches, to avoid using huge memory by scheduling
     * millions of works in one shot.
     */
    private static void benchThroughput(
            final MyExecutorData executorData,
            int nbrOfPublishers,
            long nbrOfCalls) {
        for (int k=0;k<NBR_OF_RUNS;k++) {

            System.gc();

            final MyExecutorServiceHelper executorServiceHelper = executorData.executorHelperFactory.newInstance();
            final ExecutorService executorService = executorServiceHelper.getExecutorService();
            final ExecutorService executorForCalls = executorServiceHelper.getExecutorServiceForCalls();

            final AtomicInteger publishersCountDown = new AtomicInteger(nbrOfPublishers);
            
            long a = System.nanoTime();
            {
                long minNbrOfCallsPer = nbrOfCalls/nbrOfPublishers;
                long nbrOfCallsForFirst = nbrOfCalls - minNbrOfCallsPer * (nbrOfPublishers-1);
                for (int i=0;i<nbrOfPublishers;i++) {
                    long nbrOfCallsFor = (i == 0) ? nbrOfCallsForFirst : minNbrOfCallsPer;
                    executorForCalls.execute(new MyPublisherRunnable(publishersCountDown, executorService, nbrOfCallsFor));
                }
            }

            waitWhileNotZero(publishersCountDown);
            long b = System.nanoTime();

            Unchecked.shutdownAndAwaitTermination(executorService);
            long c = System.nanoTime();
            if (executorServiceHelper.getBackingExecutorService() != null) {
                Unchecked.shutdownAndAwaitTermination(executorServiceHelper.getBackingExecutorService());
            }

            // Shutdown at last, in case it is also the executor for worker threads.
            Unchecked.shutdownAndAwaitTermination(executorForCalls);
            
            final String header =
                    "execute(Runnable) : "
                            +nbrOfPublishers+" pub(s), "
                            +executorData.nbrOfWorkers+" worker(s) : "
                            +executorData.executorInfo
                            +" : (run "+(k+1)+")";
            System.out.println(header+" t1 = "+TestUtils.nsToSRounded(b-a)+" s");
            System.out.println(header+" t2 = "+TestUtils.nsToSRounded(c-a)+" s");
        }
    }

    /*
     * 
     */

    private static ArrayList<MyExecutorData> newExecutorsData(
            final int nbrOfPublishers,
            final int nbrOfWorkers) {

        final int bufferCapacity =
                NumbersUtils.ceilingPowerOfTwo(
                        NumbersUtils.timesExact(
                                MIN_RING_BUFFER_CAPACITY_PER_THREAD,
                                Math.max(nbrOfPublishers, nbrOfWorkers)));
        final boolean singlePublisher = (nbrOfPublishers == 1);
        final int pubSeqNbrOfAtomicCountersM = singlePublisher ? 0 : 1;
        final int pubSeqNbrOfAtomicCountersNM = NBR_OF_CELLS_PER_THREAD * nbrOfPublishers;

        final boolean readLazySets = true;
        final boolean writeLazySets = WRITE_LAZY_SETS;

        ArrayList<MyExecutorData> executorsData = new ArrayList<MyExecutorData>();
        if (BENCH_JDK) {
            executorsData.add(newED_ThreadPoolExecutor(nbrOfWorkers));
            executorsData.add(newED_ForkJoinPool(nbrOfPublishers, nbrOfWorkers));
            executorsData.add(newED_ForkJoinPool_calledFromItself(nbrOfPublishers, nbrOfWorkers));
        }
        if (true) {
            executorsData.add(newED_RBES_unicast_monotonic_YBE_YBE(bufferCapacity, pubSeqNbrOfAtomicCountersM, nbrOfWorkers, readLazySets, writeLazySets));
        }
        if (true && (nbrOfPublishers > 1)) {
            executorsData.add(newED_RBES_unicast_nonMonotonic_YBE_YBE(bufferCapacity, pubSeqNbrOfAtomicCountersNM, nbrOfWorkers, readLazySets, writeLazySets));
        }
        if (true && (nbrOfWorkers == 1)) {
            executorsData.add(newED_RBES_multicast_YBE_YBE(bufferCapacity, singlePublisher, nbrOfWorkers, readLazySets, writeLazySets));
        }
        return executorsData;
    }

    /*
     * 
     */

    private static InterfaceCondilock newYieldingBlockingElusiveCondilock(boolean lazySets) {
        return Condilocks.newSmartCondilock(lazySets);
    }

    /*
     * 
     */

    private static ExecutorService newES() {
        return Executors.newCachedThreadPool();
    }
    
    private static void decrementAndNotifyAllIfZero(AtomicInteger countDown) {
        if (countDown.decrementAndGet() == 0) {
            synchronized (countDown) {
                countDown.notifyAll();
            }
        }
    }

    private static void waitWhileNotZero(AtomicInteger countDown) {
        if (countDown.get() != 0) {
            synchronized (countDown) {
                while (countDown.get() != 0) {
                    try {
                        countDown.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
    
    /*
     * 
     */
    
    private static MyExecutorData newED_ThreadPoolExecutor(final int nbrOfWorkers) {
        final InterfaceFactory<MyExecutorServiceHelper> executorHelperFactory = new InterfaceFactory<MyExecutorServiceHelper>() {
            @Override
            public MyExecutorServiceHelper newInstance() {
                return new MyExecutorServiceHelper(
                        Executors.newCachedThreadPool(),
                        Executors.newFixedThreadPool(nbrOfWorkers));
            }
        };
        return new MyExecutorData(
                executorHelperFactory,
                nbrOfWorkers,
                "ThreadPoolExecutor");
    }
    
    private static MyExecutorData newED_ForkJoinPool(
            final int nbrOfPublishers,
            final int nbrOfWorkers) {
        final InterfaceFactory<MyExecutorServiceHelper> executorHelperFactory = new InterfaceFactory<MyExecutorServiceHelper>() {
            @Override
            public MyExecutorServiceHelper newInstance() {
                return new MyExecutorServiceHelper(
                        Executors.newCachedThreadPool(),
                        new ForkJoinPool(nbrOfWorkers));
            }
        };
        return new MyExecutorData(
                executorHelperFactory,
                nbrOfWorkers,
                "ForkJoinPool");
    }
    
    private static MyExecutorData newED_ForkJoinPool_calledFromItself(
            final int nbrOfPublishers,
            final int nbrOfWorkers) {
        final InterfaceFactory<MyExecutorServiceHelper> executorHelperFactory = new InterfaceFactory<MyExecutorServiceHelper>() {
            @Override
            public MyExecutorServiceHelper newInstance() {
                final ForkJoinPool fjp = new ForkJoinPool(nbrOfPublishers + nbrOfWorkers);
                return new MyExecutorServiceHelper(
                        fjp,
                        fjp);
            }
        };
        return new MyExecutorData(
                executorHelperFactory,
                nbrOfWorkers,
                "ForkJoinPool (called from itself)");
    }
    
    private static MyExecutorData newED_RBES_unicast_monotonic_YBE_YBE(
            final int bufferCapacity,
            final int pubSeqNbrOfAtomicCounters,
            final int nbrOfWorkers,
            final boolean readLazySets,
            final boolean writeLazySets) {
        final InterfaceFactory<MyExecutorServiceHelper> executorHelperFactory = new InterfaceFactory<MyExecutorServiceHelper>() {
            @Override
            public MyExecutorServiceHelper newInstance() {
                final ExecutorService workersES = newES();
                return new MyExecutorServiceHelper(
                        Executors.newCachedThreadPool(),
                        new URBExecutorService(
                                bufferCapacity,
                                pubSeqNbrOfAtomicCounters,
                                readLazySets,
                                writeLazySets,
                                newYieldingBlockingElusiveCondilock(readLazySets),
                                newYieldingBlockingElusiveCondilock(writeLazySets),
                                workersES,
                                nbrOfWorkers,
                                new AbortingRejectedExecutionHandler()),
                        workersES);
            }
        };
        return new MyExecutorData(
                executorHelperFactory,
                nbrOfWorkers,
                "RBES - unicast - monotonic - Y/BE - Y/BE");
    }
    
    private static MyExecutorData newED_RBES_unicast_nonMonotonic_YBE_YBE(
            final int bufferCapacity,
            final int pubSeqNbrOfAtomicCounters,
            final int nbrOfWorkers,
            final boolean readLazySets,
            final boolean writeLazySets) {
        final InterfaceFactory<MyExecutorServiceHelper> executorHelperFactory = new InterfaceFactory<MyExecutorServiceHelper>() {
            @Override
            public MyExecutorServiceHelper newInstance() {
                final ExecutorService workersES = newES();
                return new MyExecutorServiceHelper(
                        Executors.newCachedThreadPool(),
                        new URBExecutorService(
                                bufferCapacity,
                                pubSeqNbrOfAtomicCounters,
                                readLazySets,
                                writeLazySets,
                                newYieldingBlockingElusiveCondilock(readLazySets),
                                newYieldingBlockingElusiveCondilock(writeLazySets),
                                workersES,
                                nbrOfWorkers,
                                new AbortingRejectedExecutionHandler()),
                        workersES);
            }
        };
        return new MyExecutorData(
                executorHelperFactory,
                nbrOfWorkers,
                "RBES - unicast - non monotonic - Y/BE - Y/BE");
    }
    
    private static MyExecutorData newED_RBES_multicast_YBE_YBE(
            final int bufferCapacity,
            final boolean singlePublisher,
            final int nbrOfWorkers,
            final boolean readLazySets,
            final boolean writeLazySets) {
        final InterfaceFactory<MyExecutorServiceHelper> executorHelperFactory = new InterfaceFactory<MyExecutorServiceHelper>() {
            @Override
            public MyExecutorServiceHelper newInstance() {
                final ExecutorService workersES = newES();
                return new MyExecutorServiceHelper(
                        Executors.newCachedThreadPool(),
                        new MRBExecutorService(
                                bufferCapacity,
                                singlePublisher,
                                readLazySets,
                                writeLazySets,
                                newYieldingBlockingElusiveCondilock(readLazySets),
                                newYieldingBlockingElusiveCondilock(writeLazySets),
                                workersES,
                                new AbortingRejectedExecutionHandler()),
                        workersES);
            }
        };
        return new MyExecutorData(
                executorHelperFactory,
                nbrOfWorkers,
                "RBES - multicast - Y/BE - Y/BE");
    }
}
