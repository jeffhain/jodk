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
package net.jodk.threading.ringbuffers;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import net.jodk.lang.IntWrapper;
import net.jodk.lang.InterfaceFactory;
import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;
import net.jodk.lang.Unchecked;
import net.jodk.test.TestUtils;
import net.jodk.threading.PostPaddedAtomicLong;
import net.jodk.threading.locks.Condilocks;
import net.jodk.threading.locks.InterfaceCondilock;
import net.jodk.threading.ringbuffers.InterfaceRingBuffer;
import net.jodk.threading.ringbuffers.InterfaceRingBufferPublishPort;
import net.jodk.threading.ringbuffers.InterfaceRingBufferSubscriber;
import net.jodk.threading.ringbuffers.MulticastRingBuffer;
import net.jodk.threading.ringbuffers.MulticastRingBufferService;
import net.jodk.threading.ringbuffers.RingBufferTestHelper;
import net.jodk.threading.ringbuffers.UnicastRingBuffer;
import net.jodk.threading.ringbuffers.UnicastRingBufferService;
import net.jodk.threading.ringbuffers.RingBufferWorkFlowBuilder.VirtualWorker;

/**
 * Class to bench performances of ring buffers.
 */
public class RingBuffersPerf {
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean ASSERTIONS = false;
    
    private static final boolean QUICK_BENCH = false;
    
    private static final boolean BENCH_LOCAL_PUBLISH_PORT = true;
    
    private static final MyClaimType ONLY_BENCH_CLAIM_TYPE = null;
    
    private static final int NBR_OF_PROC = Runtime.getRuntime().availableProcessors();
    private static final int CEILED_NBR_OF_PROC = NumbersUtils.ceilingPowerOfTwo(NBR_OF_PROC);

    private static final int MIN_PARALLELISM = 1;
    private static final int MAX_PARALLELISM = QUICK_BENCH ? Math.max(MIN_PARALLELISM,CEILED_NBR_OF_PROC/2) : 2 * CEILED_NBR_OF_PROC;

    /**
     * For non-monotonic claim.
     */
    private static final int NBR_OF_CELLS_PER_THREAD = 2;
    
    private static final boolean ONLY_BENCH_MIN_AND_MAX_PARALLELISMS = QUICK_BENCH;

    /**
     * Large enough to see interesting things happen,
     * regarding workers being late and trying to catch up
     * for example.
     * 
     * Not too large to avoid trouble in case of high parallelism,
     * and because it could slow things down.
     */
    private static final int MIN_BUFFER_CAPACITY_PER_THREAD = 16 * 1024;

    private static final long NBR_OF_EVENTS = 10L * 1000L * 1000L;
    private static final int NBR_OF_RUNS = 2;
    
    /*
     * Using a specific executors for each bench,
     * to avoid thread-local pollution.
     */
    
    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static enum MyClaimType {
        tryClaimSequence(1),
        tryClaimSequence_long(1),
        claimSequence(1),
        claimSequences_IntWrapper_10(10),
        claimSequences_IntWrapper_100(100);
        private final int desiredNbrOfSequences;
        private MyClaimType(int desiredNbrOfSequences) {
            this.desiredNbrOfSequences = desiredNbrOfSequences;
        }
        @Override
        public String toString() {
            if (this.ordinal() > claimSequence.ordinal()) {
                return "claimSequences("+this.desiredNbrOfSequences+")";
            } else {
                return super.toString();
            }
        }
    }

    private static class MyRingBufferHome {
        private final InterfaceFactory<RingBufferTestHelper> ringBufferHelperFactory;
        private final boolean optimizedLocalPublishPort;
        private final boolean multicast;
        private final String info;
        public MyRingBufferHome(
                final InterfaceFactory<RingBufferTestHelper> ringBufferHelperFactory,
                boolean optimizedLocalPublishPort,
                boolean multicast,
                final String info) {
            this.ringBufferHelperFactory = ringBufferHelperFactory;
            this.optimizedLocalPublishPort = optimizedLocalPublishPort;
            this.multicast = multicast;
            this.info = info;
        }
    }

    private static class MyPublisherRunnable implements Runnable {
        private final InterfaceRingBufferPublishPort publishPort;
        private final MyClaimType claimType;
        private final long nbrOfEvents;
        private final IntWrapper tmpIntWrapper = new IntWrapper();
        private MyPublisherRunnable(
                final InterfaceRingBufferPublishPort publishPort,
                final MyClaimType claimType,
                long nbrOfEvents) {
            this.publishPort = publishPort;
            this.claimType = claimType;
            this.nbrOfEvents = nbrOfEvents;
        }
        @Override
        public void run() {
            /*
             * We consider ring buffer is never shut down,
             * i.e. no sequence = no room, and we keep trying.
             */
            try {
                final InterfaceRingBufferPublishPort publisher = this.publishPort;
                if (this.claimType == MyClaimType.tryClaimSequence) {
                    for (long i=0;i<this.nbrOfEvents;i++) {
                        long sequence;
                        while ((sequence = publisher.tryClaimSequence()) < 0) {
                            Thread.yield();
                        }
                        if(ASSERTIONS)assert(sequence >= 0);
                        publisher.publish(sequence);
                    }
                } else if (this.claimType == MyClaimType.tryClaimSequence_long) {
                    // Only 1 second, which should rarely be reached for these benches,
                    // and which should avoid eventual optimizations (that we don't
                    // want to interfere) that would just skip timeout management
                    // in case of huge timeout.
                    final long timeoutNS = 1000L * 1000L * 1000L;
                    for (long i=0;i<this.nbrOfEvents;i++) {
                        long sequence;
                        while ((sequence = publisher.tryClaimSequence(timeoutNS)) < 0) {
                            System.out.println("rare: claim timed out");
                        }
                        if(ASSERTIONS)assert(sequence >= 0);
                        publisher.publish(sequence);
                    }
                } else if (this.claimType == MyClaimType.claimSequence) {
                    for (long i=0;i<this.nbrOfEvents;i++) {
                        final long sequence = publisher.claimSequence();
                        if(ASSERTIONS)assert(sequence >= 0);
                        publisher.publish(sequence);
                    }
                } else if ((this.claimType == MyClaimType.claimSequences_IntWrapper_10)
                        || (this.claimType == MyClaimType.claimSequences_IntWrapper_100)) {
                    final int maxDesiredNbrOfSequences = this.claimType.desiredNbrOfSequences;
                    long nbrOfEventsToPublish = this.nbrOfEvents;
                    final IntWrapper tmpInt = this.tmpIntWrapper;
                    while (nbrOfEventsToPublish > 0) {
                        final int desiredNbrOfSequences = (int)Math.min(nbrOfEventsToPublish, maxDesiredNbrOfSequences);
                        tmpInt.value = desiredNbrOfSequences;
                        final long minSequence = publisher.claimSequences(tmpInt);
                        if(ASSERTIONS)assert(minSequence >= 0);
                        final int actualNbrOfSequences = tmpInt.value;
                        nbrOfEventsToPublish -= actualNbrOfSequences;
                        publisher.publish(
                                minSequence,
                                actualNbrOfSequences);
                    }
                } else {
                    throw new UnsupportedOperationException(this.claimType+" unsupported");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class MySubscriber implements InterfaceRingBufferSubscriber {
        final PostPaddedAtomicLong volatileNbrOfLocalEvents = new PostPaddedAtomicLong();
        int nbrOfLocalEvents = 0;
        @Override
        public void readEvent(long sequence, int eventIndex) {
            ++this.nbrOfLocalEvents;
        }
        @Override
        public boolean processReadEvent() {
            return false;
        }
        @Override
        public void onBatchEnd() {
            this.volatileNbrOfLocalEvents.lazySet(this.nbrOfLocalEvents);
        }
    }
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println(TestUtils.getJVMInfo());
        newRun(args);
    }

    public static void newRun(String[] args) {
        new RingBuffersPerf().run(args);
    }
    
    public RingBuffersPerf() {
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private void run(String[] args) {
        // XXX
        System.out.println("--- "+RingBuffersPerf.class.getSimpleName()+"... ---");
        System.out.println("number of events = "+NBR_OF_EVENTS);
        System.out.println("Wait types: Bu = Busy, Y = Yielding, S = Sleeping, B = Blocking, BE = Blocking Elusive");
        System.out.println("HW : with single head worker");
        System.out.println("TW : with single tail worker");
        System.out.println("AW : with ahead workers (each worker depends on the previously created one)");
        System.out.println("t = time elapsed up to last worker done");

        benchThroughput();

        System.out.println("--- ..."+RingBuffersPerf.class.getSimpleName()+" ---");
    }

    /*
     * bench throughput
     */
    
    private static void benchThroughput() {
        int nbrOfPublishers;
        int nbrOfSubscribers;
        int parallelism = MIN_PARALLELISM;
        while (parallelism <= MAX_PARALLELISM) {
            System.out.println("");
            System.out.println("parallelism = "+parallelism);
            if (true && parallelism >= 2) {
                if (true) {
                    benchThroughput(nbrOfPublishers = 1, nbrOfSubscribers = parallelism);
                }
                if (true && parallelism >= 4) {
                    benchThroughput(nbrOfPublishers = 2, nbrOfSubscribers = parallelism);
                }
                if (true) {
                    benchThroughput(nbrOfPublishers = parallelism, nbrOfSubscribers = 1);
                }
                if (true && parallelism >= 4) {
                    benchThroughput(nbrOfPublishers = parallelism, nbrOfSubscribers = 2);
                }
            }
            if (true) {
                benchThroughput(nbrOfPublishers = parallelism, nbrOfSubscribers = parallelism);
            }
            if (ONLY_BENCH_MIN_AND_MAX_PARALLELISMS) {
                if (parallelism == MAX_PARALLELISM) {
                    break;
                }
                parallelism = MAX_PARALLELISM;
            } else {
                parallelism *= 2;
            }
        }
    }

    private static void benchThroughput(
            int nbrOfPublishers,
            int nbrOfSubscribers) {
        System.out.println("");
        
        final int bufferCapacity = computeBufferCapacity(nbrOfPublishers, nbrOfSubscribers);
        final boolean singlePublisher = (nbrOfPublishers == 1);
        final boolean singleSubscriber = (nbrOfSubscribers == 1);
        
        ArrayList<MyRingBufferHome> ringBuffersData = new ArrayList<MyRingBufferHome>();

        /*
         * multicast ring buffers
         */

        if (true) {
            for (boolean writeLazySets : new boolean[]{false,true}) {
                if (true) {
                    ringBuffersData.add(newRBH_MRB_YBE_YBE(bufferCapacity, singlePublisher, singleSubscriber, writeLazySets));
                }
                if (true) {
                    ringBuffersData.add(newRBH_MRBS_YBE_YBE(bufferCapacity, singlePublisher, singleSubscriber, writeLazySets));
                }
            }
        }

        /*
         * unicast ring buffers
         */

        if (true) {
            for (boolean writeLazySets : new boolean[]{false,true}) {
                if (true) {
                    ringBuffersData.add(newRBH_URB_monotonic_YBE_YBE(bufferCapacity, singlePublisher, singleSubscriber, writeLazySets));
                }
                if (true && (nbrOfPublishers > 1)) {
                    ringBuffersData.add(newRBH_URB_nonMonotonic_YBE_YBE(bufferCapacity, nbrOfPublishers, singleSubscriber, writeLazySets));
                }
                if (true) {
                    ringBuffersData.add(newRBH_URBS_monotonic_YBE_YBE(bufferCapacity, singlePublisher, singleSubscriber, writeLazySets));
                }
                if (true && (nbrOfPublishers > 1)) {
                    ringBuffersData.add(newRBH_URBS_nonMonotonic_YBE_YBE(bufferCapacity, nbrOfPublishers, singleSubscriber, writeLazySets));
                }
            }
        }
        
        /*
         * 
         */
        
        for (MyClaimType claimType : MyClaimType.values()) {
            if ((ONLY_BENCH_CLAIM_TYPE != null) && (claimType != ONLY_BENCH_CLAIM_TYPE)) {
                continue;
            }
            if (claimType == MyClaimType.tryClaimSequence) {
                System.out.println("claimType = "+claimType+" (yielding while fails)");
            } else {
                System.out.println("claimType = "+claimType);
            }
            for (MyRingBufferHome ringBufferData : ringBuffersData) {
                
                /*
                 * without dependent workers
                 */
                
                if (true) {
                    boolean withAheadWorkers = false;
                    boolean ensureSingleHeadWorker = false;
                    boolean ensureSingleTailWorker = false;
                    benchThroughput(
                            ringBufferData,
                            nbrOfPublishers,
                            nbrOfSubscribers,
                            withAheadWorkers,
                            ensureSingleHeadWorker,
                            ensureSingleTailWorker,
                            claimType);
                }
                
                /*
                 * with dependent workers
                 */
                
                if (ringBufferData.multicast && (nbrOfSubscribers > 1)) {
                    for (boolean withAheadWorkers : new boolean[]{false,true}) {
                        for (boolean ensureSingleHeadWorker : new boolean[]{false,true}) {
                            for (boolean ensureSingleTailWorker : new boolean[]{false,true}) {
                                if (!(withAheadWorkers || ensureSingleHeadWorker || ensureSingleTailWorker)) {
                                    // No dependent workers: already benched.
                                    continue;
                                }
                                if ((withAheadWorkers || (nbrOfSubscribers == 2)) && (ensureSingleHeadWorker || ensureSingleTailWorker)) {
                                    // Already single head and tail workers: no need to ensure.
                                    continue;
                                }
                                benchThroughput(
                                        ringBufferData,
                                        nbrOfPublishers,
                                        nbrOfSubscribers,
                                        withAheadWorkers,
                                        ensureSingleHeadWorker,
                                        ensureSingleTailWorker,
                                        claimType);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void benchThroughput(
            final MyRingBufferHome ringBufferData,
            int nbrOfPublishers,
            int nbrOfSubscribers,
            boolean withAheadWorkers,
            boolean ensureSingleHeadWorker,
            boolean ensureSingleTailWorker,
            final MyClaimType claimType) {
        // Short log for local prot if logged with main port,
        // not to have too many chars in log file.
        boolean didLogWithMainPort = false;
        for (int k=0;k<NBR_OF_RUNS;k++) {
            didLogWithMainPort = true;
            benchThroughput(
                    ringBufferData,
                    nbrOfPublishers,
                    nbrOfSubscribers,
                    withAheadWorkers,
                    ensureSingleHeadWorker,
                    ensureSingleTailWorker,
                    claimType,
                    false, // useOptimizedLocalPublishPort
                    false, // unused
                    k+1);
        }
        if (BENCH_LOCAL_PUBLISH_PORT && ringBufferData.optimizedLocalPublishPort && (nbrOfPublishers > 1)) {
            for (int k=0;k<NBR_OF_RUNS;k++) {
                benchThroughput(
                        ringBufferData,
                        nbrOfPublishers,
                        nbrOfSubscribers,
                        withAheadWorkers,
                        ensureSingleHeadWorker,
                        ensureSingleTailWorker,
                        claimType,
                        true, // useOptimizedLocalPublishPort
                        didLogWithMainPort,
                        k+1);
            }
        }
    }

    private static void benchThroughput(
            final MyRingBufferHome ringBufferData,
            int nbrOfPublishers,
            int nbrOfSubscribers,
            boolean withAheadWorkers,
            boolean ensureSingleHeadWorker,
            boolean ensureSingleTailWorker,
            final MyClaimType claimType,
            boolean useOptimizedLocalPublishPort,
            boolean didLogWithMainPort,
            int runNum) {
        LangUtils.azzert(!((!ringBufferData.multicast) && (withAheadWorkers || ensureSingleHeadWorker || ensureSingleTailWorker)));
        LangUtils.azzert(!(withAheadWorkers && (ensureSingleHeadWorker || ensureSingleTailWorker)));
        
        System.gc();

        /**
         * Not using a long-lived global executor,
         * to make sure eventual thread-local data of successive
         * benched instances don't pile-up in its threads.
         * Also, it allows to measure duration by
         * awaiting for its termination.
         */
        final ExecutorService executorForCalls = Executors.newCachedThreadPool();

        final RingBufferTestHelper ringBufferHelper = ringBufferData.ringBufferHelperFactory.newInstance();
        final InterfaceRingBuffer ringBuffer = ringBufferHelper.getRingBuffer();

        final boolean multicast = ringBufferData.multicast;

        /*
         * Creating subscribers before (might be needed by some implementations).
         */

        RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
        
        final ArrayList<MySubscriber> subscribers = new ArrayList<MySubscriber>();
        final ArrayList<VirtualWorker> vworkers = new ArrayList<VirtualWorker>();
        VirtualWorker previousVWorker = null;
        for (int i=0;i<nbrOfSubscribers;i++) {
            final MySubscriber subscriber = new MySubscriber();
            subscribers.add(subscriber);
            // Need to create workers through helper, which keeps track of them for shut down.
            VirtualWorker vworker;
            if (withAheadWorkers && (previousVWorker != null)) {
                VirtualWorker[] aheadVWorkers = new VirtualWorker[]{previousVWorker};
                vworker = builder.newVWorker(subscriber, aheadVWorkers);
            } else {
                vworker = builder.newVWorker(subscriber);
            }
            vworkers.add(vworker);
            previousVWorker = vworker;
        }
        if (ensureSingleHeadWorker) {
            builder.ensureSingleHeadWorker();
        }
        if (ensureSingleTailWorker) {
            builder.ensureSingleTailWorker();
        }
        
        builder.apply(ringBufferHelper);
        
        /*
         * 
         */

        final long minNbrOfEventsPerPublisher = NBR_OF_EVENTS/nbrOfPublishers;
        final ArrayList<MyPublisherRunnable> publishers = new ArrayList<MyPublisherRunnable>();
        for (int i=0;i<nbrOfPublishers;i++) {
            final InterfaceRingBufferPublishPort publishPort = (useOptimizedLocalPublishPort ? ringBuffer.newLocalPublishPort() : ringBuffer);
            final long nbrOfEventsForPublisher;
            if (i == 0) {
                nbrOfEventsForPublisher = NBR_OF_EVENTS - (nbrOfPublishers-1) * minNbrOfEventsPerPublisher;
            } else {
                nbrOfEventsForPublisher = minNbrOfEventsPerPublisher;
            }
            publishers.add(
                    new MyPublisherRunnable(
                            publishPort,
                            claimType,
                            nbrOfEventsForPublisher));
        }

        /*
         * 
         */
        
        ringBufferHelper.startWorkersForBench();
        
        /*
         * 
         */

        final long nbrOfSubscriberEvents = multicast ? NumbersUtils.timesExact(NBR_OF_EVENTS,nbrOfSubscribers) : NBR_OF_EVENTS;

        long a = System.nanoTime();
        for (Runnable publisher : publishers) {
            executorForCalls.execute(publisher);
        }

        Unchecked.shutdownAndAwaitTermination(executorForCalls);
        long b = System.nanoTime();

        // Not shutting down ring buffer services yet, nor passivating
        // workers of non-service ring buffers, for we allow for
        // shutdown/passivate implementations to brutally stop
        // (some ring buffers frameworks might not allow for proper
        // shutdown or worker passivation methods).
        // ===> Instead, we add a volatile lazySet in onBatchEnd(),
        // and spy volatile value here.
        waitForSubscribersEnd(subscribers,nbrOfSubscriberEvents);
        long c = System.nanoTime();
        ringBufferHelper.shutdownAndAwaitTermination();

        String header =
                claimType.toString()+" : "
                        +nbrOfPublishers+" pub(s), "
                        +nbrOfSubscribers+" sub(s)";
        if (withAheadWorkers) {
            header += ", AW";
        }
        if (ensureSingleHeadWorker) {
            header += ", HW";
        }
        if (ensureSingleTailWorker) {
            header += ", TW";
        }
        if (useOptimizedLocalPublishPort) {
            if (didLogWithMainPort) {
                // short log
                header = "local port : ";
            } else {
                header += " : "+ringBufferData.info+" - local port : ";
            }
        } else {
            header += " : "+ringBufferData.info+" : ";
        }
        System.out.println(header+"t = "+TestUtils.nsToSRounded(c-a)+" s");
    }
    
    /*
     * condilocks
     */

    private static InterfaceCondilock newYieldingBlockingElusiveCondilock(boolean lazySets) {
        return Condilocks.newSmartCondilock(lazySets);
    }
    
    /*
     * misc
     */

    private static String writeLazySetString(boolean writeLazySets) {
        return writeLazySets ? " - writeLazySets" : "";
    }
    
    private static int computeBufferCapacity(
            int nbrOfPublishers,
            int nbrOfSubscribers) {
        return NumbersUtils.ceilingPowerOfTwo(
                        NumbersUtils.timesExact(
                                MIN_BUFFER_CAPACITY_PER_THREAD,
                                Math.max(nbrOfPublishers, nbrOfSubscribers)));
    }

    private static ThreadPoolExecutor newTPE() {
        return (ThreadPoolExecutor)Executors.newCachedThreadPool();
    }

    private static long sum(ArrayList<MySubscriber> subscribers) {
        long sum = 0;
        for (MySubscriber subscriber : subscribers) {
            sum += subscriber.volatileNbrOfLocalEvents.get();
        }
        return sum;
    }

    private static void waitForSubscribersEnd(
            final ArrayList<MySubscriber> subscribers,
            long value) {
        while (sum(subscribers) < value) {
            try {
                // Less CPU consuming than a yield,
                // and should not bias timing too much.
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    /*
     * multicast ring buffers
     */

    private static MyRingBufferHome newRBH_MRB_YBE_YBE(
            final int bufferCapacity,
            final boolean singlePublisher,
            final boolean singleSubscriber,
            final boolean writeLazySets) {
        final InterfaceFactory<RingBufferTestHelper> ringBufferHelperFactory = new InterfaceFactory<RingBufferTestHelper>() {
            @Override
            public RingBufferTestHelper newInstance() {
                return new RingBufferTestHelper(
                        new MulticastRingBuffer(
                                bufferCapacity,
                                singlePublisher,
                                singleSubscriber,
                                true, // readLazySets
                                writeLazySets,
                                newYieldingBlockingElusiveCondilock(true),
                                newYieldingBlockingElusiveCondilock(writeLazySets)),
                                newTPE());
            }
        };
        return new MyRingBufferHome(
                ringBufferHelperFactory,
                true, // optimizedLocalPublishPort
                true, // multicast
                "MRB"+writeLazySetString(writeLazySets)+" - Y/BE - Y/BE");
    }

    private static MyRingBufferHome newRBH_MRBS_YBE_YBE(
            final int bufferCapacity,
            final boolean singlePublisher,
            final boolean singleSubscriber,
            final boolean writeLazySets) {
        final InterfaceFactory<RingBufferTestHelper> ringBufferHelperFactory = new InterfaceFactory<RingBufferTestHelper>() {
            @Override
            public RingBufferTestHelper newInstance() {
                final ThreadPoolExecutor workersTPE = newTPE();
                return new RingBufferTestHelper(
                        new MulticastRingBufferService(
                                bufferCapacity,
                                singlePublisher,
                                singleSubscriber,
                                true, // readLazySets
                                writeLazySets,
                                newYieldingBlockingElusiveCondilock(true),
                                newYieldingBlockingElusiveCondilock(writeLazySets),
                                workersTPE,
                                new AbortingRingBufferRejectedEventHandler()),
                                workersTPE);
            }
        };
        return new MyRingBufferHome(
                ringBufferHelperFactory,
                true, // optimizedLocalPublishPort
                true, // multicast
                "MRBS"+writeLazySetString(writeLazySets)+" - Y/BE - Y/BE");
    }

    /*
     * unicast ring buffers
     */

    private static MyRingBufferHome newRBH_URB_monotonic_YBE_YBE(
            final int bufferCapacity,
            final boolean singlePublisher,
            final boolean singleSubscriber,
            final boolean writeLazySets) {
        final InterfaceFactory<RingBufferTestHelper> ringBufferHelperFactory = new InterfaceFactory<RingBufferTestHelper>() {
            @Override
            public RingBufferTestHelper newInstance() {
                return new RingBufferTestHelper(
                        new UnicastRingBuffer(
                                bufferCapacity,
                                (singlePublisher ? 0 : 1),
                                singleSubscriber,
                                true, // readLazySets
                                writeLazySets,
                                newYieldingBlockingElusiveCondilock(true),
                                newYieldingBlockingElusiveCondilock(writeLazySets)),
                                newTPE());
            }
        };
        return new MyRingBufferHome(
                ringBufferHelperFactory,
                true, // optimizedLocalPublishPort
                false, // multicast
                "URB monotonic"+writeLazySetString(writeLazySets)+" - Y/BE - Y/BE");
    }

    private static MyRingBufferHome newRBH_URB_nonMonotonic_YBE_YBE(
            final int bufferCapacity,
            final int nbrOfPublishers,
            final boolean singleSubscriber,
            final boolean writeLazySets) {
        final InterfaceFactory<RingBufferTestHelper> ringBufferHelperFactory = new InterfaceFactory<RingBufferTestHelper>() {
            @Override
            public RingBufferTestHelper newInstance() {
                return new RingBufferTestHelper(
                        new UnicastRingBuffer(
                                bufferCapacity,
                                (NBR_OF_CELLS_PER_THREAD*nbrOfPublishers), // pubSeqNbrOfAtomicCounters
                                singleSubscriber,
                                true, // readLazySets
                                writeLazySets,
                                newYieldingBlockingElusiveCondilock(true),
                                newYieldingBlockingElusiveCondilock(writeLazySets)),
                                newTPE());
            }
        };
        return new MyRingBufferHome(
                ringBufferHelperFactory,
                true, // optimizedLocalPublishPort
                false, // multicast
                "URB non monotonic"+writeLazySetString(writeLazySets)+" - Y/BE - Y/BE");
    }

    private static MyRingBufferHome newRBH_URBS_monotonic_YBE_YBE(
            final int bufferCapacity,
            final boolean singlePublisher,
            final boolean singleSubscriber,
            final boolean writeLazySets) {
        final InterfaceFactory<RingBufferTestHelper> ringBufferHelperFactory = new InterfaceFactory<RingBufferTestHelper>() {
            @Override
            public RingBufferTestHelper newInstance() {
                final ThreadPoolExecutor workersTPE = newTPE();
                return new RingBufferTestHelper(
                        new UnicastRingBufferService(
                                bufferCapacity,
                                (singlePublisher ? 0 : 1),
                                singleSubscriber,
                                true, // readLazySets
                                writeLazySets,
                                newYieldingBlockingElusiveCondilock(true),
                                newYieldingBlockingElusiveCondilock(writeLazySets),
                                workersTPE,
                                new AbortingRingBufferRejectedEventHandler()),
                                workersTPE);
            }
        };
        return new MyRingBufferHome(
                ringBufferHelperFactory,
                true, // optimizedLocalPublishPort
                false, // multicast
                "URBS monotonic"+writeLazySetString(writeLazySets)+" - Y/BE - Y/BE");
    }
    
    private static MyRingBufferHome newRBH_URBS_nonMonotonic_YBE_YBE(
            final int bufferCapacity,
            final int nbrOfPublishers,
            final boolean singleSubscriber,
            final boolean writeLazySets) {
        final InterfaceFactory<RingBufferTestHelper> ringBufferHelperFactory = new InterfaceFactory<RingBufferTestHelper>() {
            @Override
            public RingBufferTestHelper newInstance() {
                final ThreadPoolExecutor workersTPE = newTPE();
                return new RingBufferTestHelper(
                        new UnicastRingBufferService(
                                bufferCapacity,
                                (NBR_OF_CELLS_PER_THREAD*nbrOfPublishers), // pubSeqNbrOfAtomicCounters
                                singleSubscriber,
                                true, // readLazySets
                                writeLazySets,
                                newYieldingBlockingElusiveCondilock(true),
                                newYieldingBlockingElusiveCondilock(writeLazySets),
                                workersTPE,
                                new AbortingRingBufferRejectedEventHandler()),
                                workersTPE);
            }
        };
        return new MyRingBufferHome(
                ringBufferHelperFactory,
                true, // optimizedLocalPublishPort
                false, // multicast
                "URBS non monotonic"+writeLazySetString(writeLazySets)+" - Y/BE - Y/BE");
    }
}
