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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.jodk.lang.IntWrapper;
import net.jodk.lang.InterfaceFactory;
import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;
import net.jodk.lang.RethrowException;
import net.jodk.lang.Unchecked;
import net.jodk.test.ProcessorsUser;
import net.jodk.test.TestUtils;
import net.jodk.threading.HeisenLogger;
import net.jodk.threading.HeisenLogger.RefLocal;
import net.jodk.threading.locks.Condilocks;
import net.jodk.threading.locks.InterfaceCondilock;
import net.jodk.threading.locks.SmartMonitorCondilock;
import net.jodk.threading.ringbuffers.InterfaceRingBuffer;
import net.jodk.threading.ringbuffers.InterfaceRingBufferPublishPort;
import net.jodk.threading.ringbuffers.InterfaceRingBufferRejectedEventHandler;
import net.jodk.threading.ringbuffers.InterfaceRingBufferService;
import net.jodk.threading.ringbuffers.InterfaceRingBufferSubscriber;
import net.jodk.threading.ringbuffers.InterfaceRingBufferWorker;
import net.jodk.threading.ringbuffers.MulticastRingBuffer;
import net.jodk.threading.ringbuffers.MulticastRingBufferService;
import net.jodk.threading.ringbuffers.RingBufferTestHelper;
import net.jodk.threading.ringbuffers.UnicastRingBuffer;
import net.jodk.threading.ringbuffers.UnicastRingBufferService;
import net.jodk.threading.ringbuffers.RingBufferWorkFlowBuilder.VirtualWorker;

import junit.framework.TestCase;

/**
 * Tests various kinds of ring buffers.
 */
public class RingBuffersTest extends TestCase {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_PUB = true && DEBUG;
    private static final boolean DEBUG_SUB = true && DEBUG;

    private static final boolean QUICK_TESTS = false;
    
    private static final long TOLERANCE_MS = 100L;
    
    /**
     * For each test, timeout, in seconds, after which
     * logs are done if test is not done yet, to help
     * reproduce the problem.
     */
    private static final int TEST_NOT_DONE_WARNING_TIMEOUT_S = DEBUG ? 10 : 1;
    private static final String DONE_STEP = "DONE";
    
    /**
     * Must be large enough to allow for messy interleaving.
     * 
     * Must be small enough not to cause overflows in our tests,
     * or uselessly slow tests.
     */
    private static final int MAX_NBR_OF_PUB_OR_SUB = 8;

    /**
     * Max number of atomic counters is (1<<MAX_NBR_OF_ATOMIC_COUNTERS_POT).
     */
    private static final int MAX_NBR_OF_ATOMIC_COUNTERS_POT = 2;

    /**
     * Max buffer capacity is (1<<MAX_BUFFER_CAPACITY_POT).
     * This value must be > 0.
     */
    private static final int MAX_BUFFER_CAPACITY_POT = 4;

    /**
     * Number of events per publisher, is this value times buffer capacity.
     * 
     * Must be small enough for each run not to take too long,
     * and allow for many test configurations, and for not
     * to total number of events to overflow int type.
     * 
     * Must be large enough for a few ring buffer rounds, and for early
     * stops to have a chance to occur before all events have been processed,
     * including in case of small capacity and small wait before stop.
     */
    private static final int NBR_OF_BUFFER_CAPACITIES_PER_PUBLISHER = 16;

    /**
     * A long time, for test to obviously block in case
     * wait is not correctly stopped, but not too long
     * to avoid timeout measurement to be optimized away
     * by some implementations.
     */
    private static final long CLAIM_TIMEOUT_NS = 24L * 3600L * (1000L*1000L*1000L);

    private static final int DIVISOR = QUICK_TESTS ? 10 : 1;
    
    private static final int NBR_OF_RUNS_MC_NO_EARLY_STOPS_NO_EX = 10 * 1000 / DIVISOR;
    private static final int NBR_OF_RUNS_UC_NO_EARLY_STOPS_NO_EX = 50 * 1000 / DIVISOR;
    
    private static final int NBR_OF_RUNS_EARLY_STOPS_NO_EX = 20 * 1000 / DIVISOR;
    
    private static final int NBR_OF_RUNS_NO_EARLY_STOPS_EX = 5 * 1000 / DIVISOR;
    private static final int NBR_OF_RUNS_EARLY_STOPS_EX = 10 * 1000 / DIVISOR;

    /**
     * Small enough to prevent explosion of ring buffer-specific
     * thread-local objects in executors threads, and large enough
     * to prevent JVM crash due to too many (native) threads created
     * (in a short time?).
     */
    private static final int NBR_OF_RUNS_PER_EXECUTOR = 100;

    /**
     * Low enough to allow for some early non exceptional calls.
     * High enough to allow for eventual early exceptional calls,
     * and not-too-rare simultaneous exceptions.
     */
    private static final double EXCEPTION_PROBABILITY = 0.1;

    private int randomBufferCapacityMonotonic() {
        return 1<<defaultRandom.nextInt(MAX_BUFFER_CAPACITY_POT+1);
    }
    
    /**
     * >= 2, to allow for non monotonic claim.
     */
    private int randomBufferCapacityNonMonotonic() {
        return 1<<(1+defaultRandom.nextInt(MAX_BUFFER_CAPACITY_POT));
    }
    
    private int randomNbrOfAtomicCounters() {
        return 1<<defaultRandom.nextInt(MAX_NBR_OF_ATOMIC_COUNTERS_POT+1);
    }
    
    private int randomNbrOfAtomicCountersNonMonotonic(int bufferCapacity) {
        return Math.min(bufferCapacity, Math.max(2, this.randomNbrOfAtomicCounters()));
    }
    
    private boolean randomReadLazySets() {
        return defaultRandom.nextBoolean();
    }
    
    private boolean randomWriteLazySets() {
        return defaultRandom.nextBoolean();
    }

    private int randomNbrOfPubOrSub() {
        return 1 + defaultRandom.nextInt(MAX_NBR_OF_PUB_OR_SUB);
    }

    private class MyTestContext {
        private final MyRingBufferHome home; 
        private final boolean stopBeforePubEnd;
        private final double exceptionProbability;
        //
        private final MyRingBufferTestHelper helper; 
        private final InterfaceRingBuffer ringBuffer;
        private final boolean useLocalPublishPort;
        private final int nbrOfPublishers;
        private final int nbrOfSubscribers;
        private final long baseRandomSeed;
        //
        private final boolean useShutdown;
        private final boolean useShutdownNow;
        private final boolean shutdownFirstIfBoth;
        //
        private final boolean useSetStateTerminateWhenIdle;
        private final boolean useSetStateTerminateASAP;
        private final boolean terminateWhenIdleFirstIfBoth;
        //
        private final boolean withAheadWorkers;
        private final boolean ensureSingleHeadWorker;
        private final boolean ensureSingleTailWorker;
        //
        private final long nbrOfEventsPerPublisher;
        private final int totalNbrOfEvents;
        public MyTestContext(
                final MyRingBufferHome home,
                boolean stopBeforePubEnd,
                double exceptionProbability) {
            
            final MyRingBufferTestHelper helper = home.helperFactory.newInstance();
            final InterfaceRingBuffer ringBuffer = helper.getRingBuffer();

            final int nbrOfPublishers = home.singlePublisher ? 1 : randomNbrOfPubOrSub();
            final int nbrOfSubscribers = home.singleSubscriber ? 1 : randomNbrOfPubOrSub();

            final boolean useLocalPublishPort = (!home.singlePublisher) && defaultRandom.nextBoolean();

            // If stop before publication end, and not a service, not allowing dependent workers,
            // else some workers might wait forever for a passivated ahead worker to make progress.
            final boolean canHaveDependentWorkers =
                    home.multicast
                    && (nbrOfSubscribers > 1)
                    && (!(stopBeforePubEnd && (!helper.isService())));
            final boolean withAheadWorkers = canHaveDependentWorkers && defaultRandom.nextBoolean();
            // If ahead workers, necessarily one head worker and one tail worker already.
            final boolean ensureSingleHeadWorker =
                    canHaveDependentWorkers
                    && (!withAheadWorkers)
                    && (nbrOfSubscribers > 1)
                    && defaultRandom.nextBoolean();
            final boolean ensureSingleTailWorker =
                    canHaveDependentWorkers
                    && (!withAheadWorkers)
                    && (nbrOfSubscribers > 1)
                    && defaultRandom.nextBoolean();
            final boolean withDependentWorkers = (withAheadWorkers || ensureSingleHeadWorker || ensureSingleTailWorker);

            final boolean useShutdown;
            final boolean useShutdownNow;
            final boolean shutdownFirstIfBoth;
            final boolean useSetStateTerminateWhenIdle;
            final boolean useSetStateTerminateASAP;
            final boolean terminateWhenIdleFirstIfBoth;
            if (helper.isService()) {
                useShutdown = defaultRandom.nextBoolean();
                useShutdownNow = (!useShutdown) || defaultRandom.nextBoolean();
                shutdownFirstIfBoth = (useShutdown && useShutdownNow) && defaultRandom.nextBoolean();

                useSetStateTerminateWhenIdle = false;
                useSetStateTerminateASAP = false;
                terminateWhenIdleFirstIfBoth = false;
            } else {
                useShutdown = false;
                useShutdownNow = false;
                shutdownFirstIfBoth = false;

                // If with dependent workers, not allowing call to setStateTerminateASAP,
                // else some workers might wait forever for a terminating ahead worker to make progress.
                if (withDependentWorkers) {
                    useSetStateTerminateWhenIdle = true;
                    useSetStateTerminateASAP = false;
                } else {
                    useSetStateTerminateWhenIdle = defaultRandom.nextBoolean();
                    useSetStateTerminateASAP = (!useSetStateTerminateWhenIdle) || defaultRandom.nextBoolean();
                }
                terminateWhenIdleFirstIfBoth = (useSetStateTerminateWhenIdle && useSetStateTerminateASAP) && defaultRandom.nextBoolean();
            }

            /*
             * 
             */

            final int nbrOfEventsPerPublisher = NumbersUtils.timesExact(NBR_OF_BUFFER_CAPACITIES_PER_PUBLISHER, ringBuffer.getBufferCapacity());

            /**
             * Total number of events in [0,Integer.MAX_VALUE], i.e. sequences (if any)
             * in [0,Integer.MAX_VALUE[, for we use an array indexed by sequence as a
             * sequence set (to help avoid out of memory exception).
             */
            final int totalNbrOfEvents = NumbersUtils.timesExact(nbrOfEventsPerPublisher,nbrOfPublishers);

            final long baseRandomSeed = newRandomSeed();
            
            /*
             * 
             */
            
            this.home = home;
            this.stopBeforePubEnd = stopBeforePubEnd;
            this.exceptionProbability = exceptionProbability;
            //
            this.helper = helper;
            this.ringBuffer = ringBuffer;
            this.useLocalPublishPort = useLocalPublishPort;
            this.nbrOfPublishers = nbrOfPublishers;
            this.nbrOfSubscribers = nbrOfSubscribers;
            this.baseRandomSeed = baseRandomSeed;
            this.useShutdown = useShutdown;
            this.useShutdownNow = useShutdownNow;
            this.shutdownFirstIfBoth = shutdownFirstIfBoth;
            this.useSetStateTerminateWhenIdle = useSetStateTerminateWhenIdle;
            this.useSetStateTerminateASAP = useSetStateTerminateASAP;
            this.terminateWhenIdleFirstIfBoth = terminateWhenIdleFirstIfBoth;
            this.withAheadWorkers = withAheadWorkers;
            this.ensureSingleHeadWorker = ensureSingleHeadWorker;
            this.ensureSingleTailWorker = ensureSingleTailWorker;
            this.nbrOfEventsPerPublisher = nbrOfEventsPerPublisher;
            this.totalNbrOfEvents = totalNbrOfEvents;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sbAppendLine(sb,"---");
            sbAppendLine(sb,"ring buffer class = "+ringBuffer.getClass());
            sbAppendLine(sb,"service = "+helper.isService());
            sbAppendLine(sb,"multicast = "+home.multicast);
            sbAppendLine(sb,"singlePublisher = "+home.singlePublisher);
            sbAppendLine(sb,"singleSubscriber = "+home.singleSubscriber);
            sbAppendLine(sb,"monotonicPubSeq = "+home.monotonicPubSeq);
            sbAppendLine(sb,"info = "+home.info);
            sbAppendLine(sb,"---");
            sbAppendLine(sb,"bufferCapacity = "+ringBuffer.getBufferCapacity());
            sbAppendLine(sb,"useLocalPublishPort = "+useLocalPublishPort);
            sbAppendLine(sb,"nbrOfPublishers = "+nbrOfPublishers);
            sbAppendLine(sb,"nbrOfSubscribers = "+nbrOfSubscribers);
            sbAppendLine(sb,"stopBeforePubEnd = "+stopBeforePubEnd);
            sbAppendLine(sb,"exceptionProbability = "+exceptionProbability);
            sbAppendLine(sb,"baseRandomSeed = "+baseRandomSeed);
            sbAppendLine(sb,"---");
            if (helper.isService()) {
                sbAppendLine(sb,"useShutdown = "+useShutdown);
                sbAppendLine(sb,"useShutdownNow = "+useShutdownNow);
                if (useShutdown && useShutdownNow) {
                    sbAppendLine(sb,"shutdownFirst = "+shutdownFirstIfBoth);
                }
            } else {
                sbAppendLine(sb,"useSetStateTerminateWhenIdle = "+useShutdown);
                sbAppendLine(sb,"useSetStateTerminateASAP = "+useShutdownNow);
                if (useShutdown && useShutdownNow) {
                    sbAppendLine(sb,"terminateWhenIdleFirst = "+shutdownFirstIfBoth);
                }
            }
            if (home.multicast && (nbrOfSubscribers > 1)) {
                sbAppendLine(sb,"---");
                sbAppendLine(sb,"withAheadWorkers = "+withAheadWorkers);
                sbAppendLine(sb,"ensureSingleHeadWorker = "+ensureSingleHeadWorker);
                sbAppendLine(sb,"ensureSingleTailWorker = "+ensureSingleTailWorker);
            }
            sbAppendLine(sb,"---");
            sbAppendLine(sb,"nbrOfEventsPerPublisher = "+nbrOfEventsPerPublisher);
            sbAppendLine(sb,"totalNbrOfEvents = "+totalNbrOfEvents);
            sbAppendLine(sb,"---");
            return sb.toString();
        }
        private void sbAppendLine(StringBuilder sb, String str) {
            sb.append(str);
            sb.append(LangUtils.LINE_SEPARATOR);
        }
        private long newRandomSeed() {
            return System.nanoTime();
        }
    }

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static enum MyClaimType {
        tryClaimSequence,
        tryClaimSequence_long,
        claimSequence,
        claimSequences_IntWrapper
    }

    /**
     * TPE which wraps runnables and callables to catch TestException
     * and eventually call them again.
     * 
     * This executor is used to start workers, on publish for services,
     * or in ring buffer helper for non-services.
     */
    private class MyTPE implements ExecutorService {
        private final ThreadPoolExecutor tpe;
        public MyTPE(final ThreadPoolExecutor tpe) {
            this.tpe = tpe;
        }
        /**
         * Used for non-service ring buffers, with worker's runWorker() wrapped in the specified Callable, 
         * or for ring buffer services, with a Callable wrapping worker's Runnable.
         */
        @Override
        public <T> Future<T> submit(final Callable<T> task) {
            return this.tpe.submit(
                    new Callable<T>() {
                        @Override
                        public T call() {
                            final HeisenLogger logger = DEBUG ? HeisenLogger.getDefaultThreadLocalLogger() : null;
                            try {
                                while (true) {
                                    try {
                                        if (DEBUG) {
                                            logger.logLocal("worker : CALLING WORKER");
                                            logger.logLocalLogs();
                                        }
                                        task.call();
                                        if (DEBUG) {
                                            logger.logLocal("worker : DONE NORMALLY");
                                        }
                                        break;
                                    } catch (TestException e) {
                                        if (workerCalledAgainIfTestException) {
                                            // Will call again.
                                            if (DEBUG) {
                                                logger.logLocal("worker : DONE EXCEPTIONALLY : will call again after exception : ",e);
                                            }
                                        } else {
                                            if (DEBUG) {
                                                logger.logLocal("worker : DONE EXCEPTIONALLY : ",e);
                                            }
                                            break;
                                        }
                                    } catch (InterruptedException e) {
                                        if (DEBUG) {
                                            logger.logLocal("worker : InterruptedException (calling again) : ",e);
                                        }
                                        // Will call again for worker to eventually make progress.
                                    } catch (Throwable e) {
                                        // Unexpected.
                                        // Log even if debug not activated.
                                        HeisenLogger.log("worker : DONE EXCEPTIONALLY (unexpected) : ",e);
                                        onError(e);
                                        throw new RethrowException(e);
                                    }
                                }
                            } finally {
                                if (DEBUG) {
                                    logger.logLocalLogs();
                                    HeisenLogger.flushPendingLogs();
                                }
                            }
                            return null;
                        }
                    });
        }
        /**
         * Used by ring buffer services, when they start workers on publish.
         */
        @Override
        public void execute(final Runnable runnable) {
            this.submit(new Callable<Void>() {
                @Override
                public Void call() throws InterruptedException {
                    runnable.run();
                    if (Thread.interrupted()) {
                        if (DEBUG) {
                            HeisenLogger.log("will call runnable again (was interrupted)");
                        }
                        throw new InterruptedException();
                    }
                    return null;
                }
            });
        }
        @Override
        public void shutdown() {
            this.tpe.shutdown();
        }
        @Override
        public List<Runnable> shutdownNow() {
            return this.tpe.shutdownNow();
        }
        @Override
        public boolean isShutdown() {
            return this.tpe.isShutdown();
        }
        @Override
        public boolean isTerminated() {
            return this.tpe.isTerminated();
        }
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return this.tpe.awaitTermination(timeout, unit);
        }
        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return this.tpe.submit(task, result);
        }
        @Override
        public Future<?> submit(Runnable task) {
            return this.tpe.submit(task);
        }
        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return this.tpe.invokeAll(tasks);
        }
        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            return this.tpe.invokeAll(tasks, timeout, unit);
        }
        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return this.tpe.invokeAny(tasks);
        }
        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return this.tpe.invokeAny(tasks, timeout, unit);
        }
    }

    private class MyPublisherRunnable implements Runnable {
        private final MyLocalRandom localRandom = new MyLocalRandom();
        private final AtomicInteger publisherCounter;
        /**
         * Null if not a service.
         */
        private final InterfaceRingBuffer ringBuffer;
        private final InterfaceRingBufferPublishPort publishPort;
        private final long nbrOfSequencesToPublish;
        private final boolean monotonicPubSeq;
        private final boolean canUseBlockingClaims;
        private final long[] events;
        private final ConcurrentLinkedQueue<Long> claimedSequences;
        /**
         * For publisher not to keep trying to publish forever,
         * when not using blocking claims and when no more workers
         * are active (case of early stop).
         */
        private volatile boolean stopPublishing = false;
        private final RefLocal loggerRef = new RefLocal();
        public MyPublisherRunnable(
                final AtomicInteger publisherCounter,
                final InterfaceRingBuffer ringBuffer,
                final InterfaceRingBufferPublishPort publishPort,
                long nbrOfSequencesToPublish,
                boolean monotonicPubSeq,
                boolean canUseBlockingClaims,
                final long[] events,
                final ConcurrentLinkedQueue<Long> claimedSequences) {
            if (nbrOfSequencesToPublish < 0) {
                throw new IllegalArgumentException();
            }
            this.publisherCounter = publisherCounter;
            this.ringBuffer = ringBuffer;
            this.publishPort = publishPort;
            this.nbrOfSequencesToPublish = nbrOfSequencesToPublish;
            this.monotonicPubSeq = monotonicPubSeq;
            this.canUseBlockingClaims = canUseBlockingClaims;
            this.events = events;
            this.claimedSequences = claimedSequences;
        }
        public void run() {
            final HeisenLogger logger = DEBUG_PUB ? HeisenLogger.getDefaultThreadLocalLogger(this.loggerRef) : null;
            long lastClaimed = Long.MIN_VALUE;
            try {
                final IntWrapper nbrOfSequencesWrapper = new IntWrapper();
                long remainingNbrOfSequencesToPublish = this.nbrOfSequencesToPublish;
                if (DEBUG_PUB) {
                    logger.logLocal("publisher run",remainingNbrOfSequencesToPublish);
                    logger.logLocalLogs();
                }
                while (remainingNbrOfSequencesToPublish != 0) {
                    if (this.stopPublishing) {
                        break;
                    }
                    /*
                     * Claiming.
                     */
                    final long minSequence = this.randomClaim(
                            this.canUseBlockingClaims,
                            remainingNbrOfSequencesToPublish,
                            nbrOfSequencesWrapper);
                    final int nbrOfSequences = nbrOfSequencesWrapper.value;
                    /*
                     * Yielding before retry if claim failed,
                     * unless shutdown in which case we give up.
                     */
                    if (minSequence < 0) {
                        if ((this.ringBuffer instanceof InterfaceRingBufferService)
                                && ((InterfaceRingBufferService)this.ringBuffer).isShutdown()) {
                            if (DEBUG_PUB) {
                                logger.logLocal("SHUTDOWN");
                                logger.logLocalLogs();
                            }
                            return;
                        }
                        Thread.yield();
                        continue;
                    }
                    final long maxSequence = minSequence + (nbrOfSequences-1);
                    if (DEBUG_PUB) {
                        logger.logLocal("claimed",minSequence,"..",maxSequence);
                        logger.logLocalLogs();
                    }
                    for (int i=0;i<nbrOfSequences;i++) {
                        final long sequence = minSequence + i;
                        this.claimedSequences.add(sequence);
                    }
                    if (this.monotonicPubSeq && (maxSequence < lastClaimed)) {
                        onError("monotonic claim, but max claimed ["+maxSequence+"] < last claimed ["+lastClaimed+"]");
                    }
                    lastClaimed = maxSequence;
                    /*
                     * Writing events (for subscribers to check they
                     * can effectively read them.
                     */
                    for (int i=0;i<nbrOfSequences;i++) {
                        final long sequence = minSequence + i;
                        final int index = this.publishPort.sequenceToIndex(sequence);
                        if (DEBUG_PUB) {
                            logger.logLocal("publisher : setting event for sequence ",sequence);
                        }
                        this.events[index] = sequence;
                    }
                    /*
                     * Publishing.
                     */
                    this.randomPublish(minSequence, nbrOfSequences);

                    remainingNbrOfSequencesToPublish -= nbrOfSequences;
                }
            } catch (Throwable e) {
                // Unexpected.
                // Log even if debug not activated.
                HeisenLogger.log("publisher : DONE EXCEPTIONALLY (unexpected) : ",e);
                onError(e);
                throw new RethrowException(e);
            } finally {
                if (DEBUG_PUB) {
                    logger.logLocal("PUBLISHER DONE");
                    logger.logLocalLogs();
                    HeisenLogger.flushPendingLogs();
                }
                if (publisherCounter.decrementAndGet() == 0) {
                    synchronized (publisherCounter) {
                        publisherCounter.notifyAll();
                    }
                }
            }
        }
        /**
         * @param nbrOfSequences (out) Number of claimed sequences.
         * @return Min claimed sequence (possibly < 0).
         */
        private long randomClaim(
                boolean canUseBlockingClaims,
                long remainingNbrOfSequencesToPublish,
                IntWrapper nbrOfSequences) throws InterruptedException {
            final long minSequence;

            final HeisenLogger logger = DEBUG_PUB ? HeisenLogger.getDefaultThreadLocalLogger(this.loggerRef) : null;

            final int nbrOfMethods = 4;
            final int methodIndex = canUseBlockingClaims ? this.localRandom.nextInt(nbrOfMethods) : 0;
            if (methodIndex == 0) {
                minSequence = this.publishPort.tryClaimSequence();
                if (DEBUG_PUB) {
                    // Just return part (else spams too much).
                    logger.logLocal("DID tryClaimSequence()",minSequence);
                }
                if (DEBUG_PUB) {
                    if ((minSequence < 0)
                            && (!canUseBlockingClaims)
                            && this.localRandom.nextBoolean()) {
                        logger.logLocal("sleep after failed claim");
                        logger.logLocalLogs();
                        // Eventually waiting some, to avoid busy-claiming
                        // and logs spam if only using this kind of claims
                        // and that it mostly fails.
                        Unchecked.sleepMS(10);
                    }
                }
                nbrOfSequences.value = 1;
            } else if (methodIndex == 1) {
                if (DEBUG_PUB) {
                    logger.logLocal("INC tryClaimSequence(long)");
                    logger.logLocalLogs();
                }
                minSequence = this.publishPort.tryClaimSequence(CLAIM_TIMEOUT_NS);
                if (DEBUG_PUB) {
                    logger.logLocal("DID tryClaimSequence(long)",minSequence);
                }
                nbrOfSequences.value = 1;
            } else if (methodIndex == 2) {
                if (DEBUG_PUB) {
                    logger.logLocal("INC claimSequence()");
                    logger.logLocalLogs();
                }
                minSequence = this.publishPort.claimSequence();
                if (DEBUG_PUB) {
                    logger.logLocal("DID claimSequence()",minSequence);
                }
                nbrOfSequences.value = 1;
            } else {
                if (DEBUG_PUB) {
                    logger.logLocal("INC claimSequences(IntWrapper)");
                    logger.logLocalLogs();
                }
                nbrOfSequences.value = 1 + this.localRandom.nextInt((int)Math.min(10, remainingNbrOfSequencesToPublish));
                minSequence = this.publishPort.claimSequences(nbrOfSequences);
                if (DEBUG_PUB) {
                    logger.logLocal("DID claimSequences(IntWrapper)",minSequence,nbrOfSequences.value);
                }
            }

            return minSequence;
        }
        private void randomPublish(
                long minSequence,
                int nbrOfSequences) throws InterruptedException {

            final HeisenLogger logger = DEBUG_PUB ? HeisenLogger.getDefaultThreadLocalLogger(this.loggerRef) : null;

            final int nbrOfMethods = 2;
            final int methodIndex = this.localRandom.nextInt(nbrOfMethods);
            if (methodIndex == 0) {
                for (int i=0;i<nbrOfSequences;i++) {
                    final long sequence = minSequence + i;
                    if (DEBUG_PUB) {
                        logger.logLocal("INC publish",sequence);
                        logger.logLocalLogs();
                    }
                    this.publishPort.publish(sequence);
                    if (DEBUG_PUB) {
                        logger.logLocal("DID publish");
                    }
                }
            } else {
                if (DEBUG_PUB) {
                    logger.logLocal("INC publish",minSequence,"..",(minSequence+nbrOfSequences-1));
                    logger.logLocalLogs();
                }
                this.publishPort.publish(minSequence,nbrOfSequences);
                if (DEBUG_PUB) {
                    logger.logLocal("DID publish");
                }
            }
        }
    }

    /*
     * 
     */

    /**
     * This subscriber never skips events.
     * Event skipping handling is tested through testing of MunicastRingBuffer.
     */
    private class MySubscriber implements InterfaceRingBufferSubscriber {
        private final int ringBufferCapacity;
        private final boolean multicast;
        private final long[] events;
        private final double exceptionProbability;
        private final MyLocalRandom localRandom;
        /**
         * Workers's getMaxPassedSequence() method allows for some lag,
         * but we consider the ring buffer implementation use them
         * to evaluate workers progression, and has that the lag is
         * the same for all other threads than the worker's thread.
         */
        private InterfaceRingBufferWorker[] aheadWorkers = null;
        private long firstReadSeq = Long.MAX_VALUE;
        private long lastReadSeq = Long.MIN_VALUE;
        /**
         * Only for unicast.
         */
        private final ArrayList<Long> readSeqs;
        private long lastReadSeqOnLastBatchEnd = Long.MIN_VALUE;
        private long nbrOfCalls_readEvent = 0;
        private long nbrOfCalls_processReadEvent = 0;
        private long nbrOfCalls_onBatchEnd = 0;
        private final RefLocal loggerRef = new RefLocal();
        public MySubscriber(
                int ringBufferCapacity,
                boolean multicast,
                final long[] events,
                double exceptionProbability,
                long randomSeed) {
            if (!NumbersUtils.isPowerOfTwo(ringBufferCapacity)) {
                throw new RuntimeException("ring buffer capacity ["+ringBufferCapacity+"] is not a power of two");
            }
            this.ringBufferCapacity = ringBufferCapacity;
            this.multicast = multicast;
            this.events = events;
            this.exceptionProbability = exceptionProbability;
            this.localRandom = (exceptionProbability != 0.0) ? new MyLocalRandom(randomSeed) : null;

            if (multicast) {
                this.readSeqs = null;
            } else {
                this.readSeqs = new ArrayList<Long>();
            }
        }
        public void setAheadWorkers(final InterfaceRingBufferWorker... aheadWorkers) {
            this.aheadWorkers = aheadWorkers;
        }
        @Override
        public void readEvent(long sequence, int index) {
            if (DEBUG_SUB) {
                HeisenLogger.getDefaultThreadLocalLogger(this.loggerRef).logLocal("worker : readEvent",sequence);
            }

            this.nbrOfCalls_readEvent++;

            if (this.aheadWorkers != null) {
                for (InterfaceRingBufferWorker aheadWorker : this.aheadWorkers) {
                    final long aheadMPS = aheadWorker.getMaxPassedSequence();
                    if (sequence > aheadMPS) {
                        onError("sequence ["+sequence+"] > max passed sequence ["+aheadMPS+"] of an ahead worker");
                    }
                }
            }

            final int expectedIndex = (int)sequence & (this.ringBufferCapacity-1);
            if (index != expectedIndex) {
                onError("index ["+index+"] should be "+expectedIndex+" for sequence "+sequence);
            }

            final long event = this.events[index];
            if (event != sequence) {
                onError("read event ["+event+"] should be "+sequence+" for sequence "+sequence);
            }

            final long first = this.firstReadSeq;
            final long last = this.lastReadSeq;
            if (first > last) {
                // First call.
                this.firstReadSeq = sequence;
                this.lastReadSeq = sequence;
            } else {
                // Monotonicity test.
                if (this.multicast) {
                    if (sequence != last+1) {
                        onError("sequence ["+sequence+"] != last ["+last+"] + 1");
                    }
                } else {
                    if (sequence < last) {
                        onError("sequence ["+sequence+"] < last ["+last+"]");
                    }
                }
                this.lastReadSeq = sequence;
            }
            if (this.readSeqs != null) {
                this.readSeqs.add(sequence);
            }
            this.eventuallyThrow();
        }
        @Override
        public boolean processReadEvent() {
            if (DEBUG_SUB) {
                HeisenLogger.getDefaultThreadLocalLogger(this.loggerRef).logLocal("worker : processReadEvent");
            }

            this.nbrOfCalls_processReadEvent++;

            if (this.exceptionProbability == 0.0) {
                final long reads = this.nbrOfCalls_readEvent;
                final long processes = this.nbrOfCalls_processReadEvent;
                if (reads != processes) {
                    onError("processReadEvent called for the "+processes+"th times, and readEvent called "+reads+" times");
                }
            }

            this.eventuallyThrow();
            
            return false;
        }
        @Override
        public void onBatchEnd() {
            if (DEBUG_SUB) {
                HeisenLogger.getDefaultThreadLocalLogger(this.loggerRef).logLocal("worker : onBatchEnd");
            }

            this.nbrOfCalls_onBatchEnd++;

            final long processes = this.nbrOfCalls_processReadEvent;
            final long batches = this.nbrOfCalls_onBatchEnd;
            if (batches > processes) {
                onError("onBatchEnd called for the "+batches+"th times, and processReadEvent called "+processes+" times");
            }

            this.lastReadSeqOnLastBatchEnd = this.lastReadSeq;

            this.eventuallyThrow();
        }
        private void eventuallyThrow() {
            if ((this.exceptionProbability != 0.0)
                    && (this.localRandom.nextDouble() < this.exceptionProbability)) {
                final TestException e = new TestException();
                if (DEBUG) {
                    HeisenLogger.getDefaultThreadLocalLogger(this.loggerRef).logLocal("worker : THROWING !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                }
                throw e;
            }
        }
    }

    /*
     * 
     */

    private static class MySequencesRange {
        private final long minSequence;
        private final long maxSequence;
        public MySequencesRange(
                long minSequence,
                long maxSequence) {
            this.minSequence = minSequence;
            this.maxSequence = maxSequence;
        }
    }

    private class MyRingBufferRejectedEventHandler implements InterfaceRingBufferRejectedEventHandler {
        private final ConcurrentLinkedQueue<MySequencesRange> rejOnPubRanges = new ConcurrentLinkedQueue<MySequencesRange>();
        private InterfaceRingBuffer ringBuffer;
        public void init(final InterfaceRingBuffer ringBuffer) {
            this.ringBuffer = ringBuffer;
        }
        @Override
        public void onRejectedEvents(
                final InterfaceRingBuffer ringBuffer,
                final long... sequencesRanges) {
            if (ringBuffer != this.ringBuffer) {
                throw new RuntimeException("ring buffer ["+ringBuffer+"] must be "+this.ringBuffer);
            }
            final HeisenLogger logger = DEBUG ? HeisenLogger.getDefaultThreadLocalLogger() : null;
            final int nbrOfRanges = sequencesRanges.length/2;
            final long[] localEvents = events;
            for (int i=0;i<nbrOfRanges;i++) {
                final long minSeq = sequencesRanges[2*i];
                final long maxSeq = sequencesRanges[2*i+1];
                if (DEBUG) {
                    logger.logLocal("publisher : rejected sub range",minSeq,"..",maxSeq);
                }
                for (long seq=minSeq;seq<=maxSeq;seq++) {
                    final int index = (int)seq & (localEvents.length-1);
                    final long event = localEvents[index];
                    if (event != seq) {
                        if (DEBUG) {
                            logger.logLocal("publisher : rejected event for sequence ",seq," is ",event);
                        }
                        onError("rejected event ["+event+"] should be "+seq+" for sequence "+seq);
                    }
                }
                this.rejOnPubRanges.add(new MySequencesRange(minSeq, maxSeq));
            }
        }
    }

    private static class MyRingBufferTestHelper extends RingBufferTestHelper {
        private final MyRingBufferRejectedEventHandler reh;
        public MyRingBufferTestHelper(
                final InterfaceRingBuffer ringBuffer,
                final ExecutorService workersExecutor,
                final MyRingBufferRejectedEventHandler reh) {
            super(
                    ringBuffer,
                    workersExecutor,
                    false); // shutdownWorkersExecutor
            this.reh = reh;
        }
    }

    private static class MyRingBufferHome {
        private final InterfaceFactory<MyRingBufferTestHelper> helperFactory;
        private final boolean multicast;
        private final boolean singlePublisher;
        private final boolean singleSubscriber;
        private final boolean monotonicPubSeq;
        /**
         * Typically due to write lazy sets.
         */
        private final boolean possibleNonHandledSequencesOnShutdown;
        private final String info;
        public MyRingBufferHome(
                final InterfaceFactory<MyRingBufferTestHelper> helperFactory,
                boolean multicast,
                boolean singlePublisher,
                boolean singleSubscriber,
                boolean monotonicPubSeq,
                boolean possibleNonHandledSequencesOnShutdown,
                final String info) {
            this.helperFactory = helperFactory;
            this.multicast = multicast;
            this.singlePublisher = singlePublisher;
            this.singleSubscriber = singleSubscriber;
            this.monotonicPubSeq = monotonicPubSeq;
            this.possibleNonHandledSequencesOnShutdown = possibleNonHandledSequencesOnShutdown;
            this.info = info;
        }
    }

    /*
     * 
     */

    private static class MySequenceCountMap {
        private final HashMap<Long, Integer> map = new HashMap<Long, Integer>();
        public int getSize() {
            return this.map.size();
        }
        public Set<Long> sequencesSet() {
            return this.map.keySet();
        }
        public void incr(Long sequence) {
            final Integer v = this.map.get(sequence);
            if (v == null) {
                this.map.put(sequence, Integer.valueOf(1));
            } else {
                this.map.put(sequence, Integer.valueOf(v.intValue() + 1));
            }
        }
        public int get(Long sequence) {
            final Integer v = this.map.get(sequence);
            if (v == null) {
                return Integer.valueOf(0);
            } else {
                return v.intValue();
            }
        }
    }

    /*
     * 
     */

    /**
     * Non thread-safe Random, that does not (directly) cause
     * memory synchronizations, to make sure we only rely on
     * memory operations done by the ring buffer.
     */
    private static class MyLocalRandom extends Random {
        private static final long serialVersionUID = 1L;
        private static final AtomicLong seedOffset = new AtomicLong();
        private static final long multiplier = 0x5DEECE66DL;
        private static final long addend = 0xBL;
        private static final long mask = (1L << 48) - 1;
        private long seed;
        public MyLocalRandom() {
            this(System.nanoTime() + seedOffset.incrementAndGet());
        }
        public MyLocalRandom(long seed) {
            this.seed = seed;
        }
        @Override
        protected int next(int bits) {
            this.seed = (this.seed * multiplier + addend) & mask;
            return (int)(this.seed >>> (48 - bits));
        }
    }

    /**
     * Exception thrown to test exception-related behavior.
     */
    private static class TestException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public TestException() {
            super("for test");
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private final Random defaultRandom = new Random(System.nanoTime());

    /**
     * Recycling executors to avoid JVM crash if creating too many threads.
     * Can lead to a high number of thread-local objects, but it's not a problem
     * since we don't do benches.
     * Recycling for a same test method only, and not statically, to reduce
     * per-thread thread-local explosion, and to make sure executor is shut down
     * via tearDown method.
     */
    private ThreadPoolExecutor publishersExecutor;
    private MyTPE workersExecutor;
    private ScheduledThreadPoolExecutor timeoutWarningExecutor;
    
    private final AtomicLong executorsUsageCounter = new AtomicLong();

    /**
     * Events for current test.
     * Using sequences as events.
     */
    private long[] events;

    /**
     * True if workers must be called again if they did throw a TestException.
     * Typically true if TestException are thrown (else treatments might stall).
     */
    private volatile boolean workerCalledAgainIfTestException;

    private volatile boolean interruptingWorkers;

    /**
     * Must be set prior to each test.
     */
    private volatile MyTestContext testContext;
    
    /**
     * Only reporting the first error that occurs.
     */
    private final AtomicReference<Throwable> firstError = new AtomicReference<Throwable>();

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    @Override
    public void setUp() throws Exception {
        this.newExecutors();
        ProcessorsUser.start();
    }

    @Override
    public void tearDown() throws Exception {
        ProcessorsUser.stop();
        this.deleteExecutors();
    }

    /*
     * multicast, no early stops
     */

    public void test_multicast_nonService_noEarlyStops() {
        for (int i=0;i<NBR_OF_RUNS_MC_NO_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_multicast_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_noExceptions(home);
            }
        }
    }

    public void test_multicast_service_noEarlyStops() {
        for (int i=0;i<NBR_OF_RUNS_MC_NO_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_multicast_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_noExceptions(home);
            }
        }
    }

    /*
     * multicast, early stops
     */

    public void test_multicast_nonService_earlyStops() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_multicast_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_noExceptions(home);
            }
        }
    }

    public void test_multicast_service_earlyStops() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_multicast_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_noExceptions(home);
            }
        }
    }

    /*
     * unicast, no early stops
     */

    public void test_unicast_pubM_nonService_noEarlyStops() {
        for (int i=0;i<NBR_OF_RUNS_UC_NO_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubM_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_noExceptions(home);
            }
        }
    }

    public void test_unicast_pubM_service_noEarlyStops() {
        for (int i=0;i<NBR_OF_RUNS_UC_NO_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubM_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_noExceptions(home);
            }
        }
    }

    public void test_unicast_pubNM_nonService_noEarlyStops() {
        for (int i=0;i<NBR_OF_RUNS_UC_NO_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubNM_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_noExceptions(home);
            }
        }
    }

    public void test_unicast_pubNM_service_noEarlyStops() {
        for (int i=0;i<NBR_OF_RUNS_UC_NO_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubNM_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_noExceptions(home);
            }
        }
    }

    /*
     * unicast, early stops
     */

    public void test_unicast_pubM_nonService_earlyStops() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubM_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_noExceptions(home);
            }
        }
    }

    public void test_unicast_pubM_service_earlyStops() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubM_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_noExceptions(home);
            }
        }
    }

    public void test_unicast_pubNM_nonService_earlyStops() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubNM_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_noExceptions(home);
            }
        }
    }
    
    public void test_unicast_pubNM_service_earlyStops() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_NO_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubNM_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_noExceptions(home);
            }
        }
    }

    /*
     * multicast, no early stops, exceptions
     */

    public void test_multicast_nonService_noEarlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_NO_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_multicast_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_exceptions(home);
            }
        }
    }

    public void test_multicast_service_noEarlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_NO_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_multicast_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_exceptions(home);
            }
        }
    }

    /*
     * multicast, early stops, exceptions
     */
    
    public void test_multicast_nonService_earlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_multicast_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_exceptions(home);
            }
        }
    }

    public void test_multicast_service_earlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_multicast_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_exceptions(home);
            }
        }
    }

    /*
     * unicast, no early stops, exceptions
     */

    public void test_unicast_pubM_nonService_noEarlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_NO_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubM_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_exceptions(home);
            }
        }
    }

    public void test_unicast_pubM_service_noEarlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_NO_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubM_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_exceptions(home);
            }
        }
    }

    public void test_unicast_pubNM_nonService_noEarlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_NO_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubNM_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_exceptions(home);
            }
        }
    }

    public void test_unicast_pubNM_service_noEarlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_NO_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubNM_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_noEarlyStops_exceptions(home);
            }
        }
    }
    
    /*
     * unicast, early stops, exceptions
     */
    
    public void test_unicast_pubM_nonService_earlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubM_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_exceptions(home);
            }
        }
    }

    public void test_unicast_pubM_service_earlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubM_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_exceptions(home);
            }
        }
    }

    public void test_unicast_pubNM_nonService_earlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubNM_nonService();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_exceptions(home);
            }
        }
    }

    public void test_unicast_pubNM_service_earlyStops_exceptions() {
        for (int i=0;i<NBR_OF_RUNS_EARLY_STOPS_EX;i++) {
            this.notifyExecutorsUsage();
            final ArrayList<MyRingBufferHome> homes = newRBHs_unicast_pubNM_service();
            for (MyRingBufferHome home : homes) {
                test_ringBuffer_earlyStops_exceptions(home);
            }
        }
    }
    
    /*
     * 
     */

    public void test_ringBuffer_noEarlyStops_noExceptions(final MyRingBufferHome home) {
        test_ringBuffer_tryCatch(home, false, 0.0);
    }

    public void test_ringBuffer_earlyStops_noExceptions(final MyRingBufferHome home) {
        test_ringBuffer_tryCatch(home, true, 0.0);
    }

    public void test_ringBuffer_noEarlyStops_exceptions(final MyRingBufferHome home) {
        test_ringBuffer_tryCatch(home, false, EXCEPTION_PROBABILITY);
    }

    public void test_ringBuffer_earlyStops_exceptions(final MyRingBufferHome home) {
        test_ringBuffer_tryCatch(home, true, EXCEPTION_PROBABILITY);
    }

    public void test_ringBuffer_tryCatch(
            final MyRingBufferHome home,
            boolean stopBeforePubEnd,
            double exceptionProbability) {
        try {
            test_ringBuffer(
                    home,
                    stopBeforePubEnd,
                    exceptionProbability);
        } catch (Throwable e) {
            HeisenLogger.log(e);
            throw new RuntimeException(e);
        } finally {
            HeisenLogger.getDefaultThreadLocalLogger().logLocalLogs();
            HeisenLogger.flushPendingLogsAndStream();
        }
    }
    
    /**
     * @param stopBeforePubEnd True if stopping before end of publications,
     *        by shutting down ring buffer services, or terminating workers
     *        for non-service ring buffers.
     * @param exceptionProbability Probability for an exception to be thrown
     *        by each subscriber's method, at each call.
     */
    public void test_ringBuffer(
            final MyRingBufferHome home,
            boolean stopBeforePubEnd,
            double exceptionProbability) {
        // XXX
        if (DEBUG) {
            HeisenLogger.flushPendingLogsAndStream();
            HeisenLogger.log("test_ringBuffer... --------------------------------------------------------------------------");
        }

        final boolean exceptions = (exceptionProbability != 0.0);
        // If exceptions, we also try to interrupt workers,
        // to end up with maximum mess.
        this.interruptingWorkers = exceptions;

        /*
         * Random configuration.
         */
        
        final MyTestContext context = new MyTestContext(
                home,
                stopBeforePubEnd,
                exceptionProbability);
        this.testContext = context;
        if (DEBUG) {
            HeisenLogger.log(context);
            HeisenLogger.flushPendingLogsAndStream();
        }
        
        final long startNS = System.nanoTime();
        final LinkedBlockingDeque<String> testSteps = new LinkedBlockingDeque<String>();
        final long timeoutMaxMS = System.nanoTime()/(1000L*1000L) + TEST_NOT_DONE_WARNING_TIMEOUT_S * 1000L;
        final ScheduledFuture<?> timeoutFuture = this.timeoutWarningExecutor.schedule(
                new Runnable() {
                    @Override
                    public void run() {
                        // Since we can't use ScheduledThreadPoolExecutor.setRemoveOnCancelPolicy,
                        // We wait here until test is done and then return.
                        synchronized (testSteps) {
                            while (!DONE_STEP.equals(testSteps.peekLast())) {
                                long remainingMS = timeoutMaxMS - System.nanoTime()/(1000L*1000L);
                                if (remainingMS <= 0) {
                                    break;
                                }
                                Unchecked.waitMS(testSteps, remainingMS);
                            }
                        }
                        
                        if (!DONE_STEP.equals(testSteps.peekLast())) {
                            HeisenLogger.log("TIMEOUT WARNING (test not done after "+TEST_NOT_DONE_WARNING_TIMEOUT_S+" s):");
                            HeisenLogger.log("context:");
                            HeisenLogger.log(context);
                            HeisenLogger.log("test steps:");
                            for (String step : testSteps) {
                                HeisenLogger.log(step);
                            }
                            HeisenLogger.flushPendingLogsAndStream();
                        }
                    }
                },
                0, // TEST_NOT_DONE_WARNING_TIMEOUT_S,
                TimeUnit.SECONDS);

        final MyRingBufferTestHelper helper = context.helper;
        final InterfaceRingBuffer ringBuffer = context.ringBuffer;
        final boolean useLocalPublishPort = context.useLocalPublishPort;
        final int nbrOfPublishers = context.nbrOfPublishers;
        final int nbrOfSubscribers = context.nbrOfSubscribers;
        //
        final long baseRandomSeed = context.baseRandomSeed;
        final boolean useShutdown = context.useShutdown;
        final boolean useShutdownNow = context.useShutdownNow;
        final boolean shutdownFirstIfBoth = context.shutdownFirstIfBoth;
        final boolean useSetStateTerminateWhenIdle = context.useSetStateTerminateWhenIdle;
        final boolean useSetStateTerminateASAP = context.useSetStateTerminateASAP;
        final boolean terminateWhenIdleFirstIfBoth = context.terminateWhenIdleFirstIfBoth;
        final boolean withAheadWorkers = context.withAheadWorkers;
        final long nbrOfEventsPerPublisher = context.nbrOfEventsPerPublisher;
        final long totalNbrOfEvents = context.totalNbrOfEvents;
        
        /*
         * 
         */

        final InterfaceRingBufferService ringBufferService;
        if (helper.isService()) {
            ringBufferService = helper.getRingBufferService();
        } else {
            ringBufferService = null;
        }

        final MyRingBufferRejectedEventHandler reh = helper.reh;

        /*
         * 
         */

        /**
         * Using sequences as events.
         */
        this.events = new long[ringBuffer.getBufferCapacity()];

        testSteps.add(datedLog("creating workers...",startNS));
        
        RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
        
        final ArrayList<MySubscriber> subscribers = new ArrayList<MySubscriber>();
        final ArrayList<VirtualWorker> vworkers = new ArrayList<VirtualWorker>();
        VirtualWorker previousVWorker = null;
        for (int i=0;i<nbrOfSubscribers;i++) {
            final long randomSeed = baseRandomSeed + i;
            final MySubscriber subscriber = new MySubscriber(
                    ringBuffer.getBufferCapacity(),
                    home.multicast,
                    this.events,
                    exceptionProbability,
                    randomSeed);
            final VirtualWorker vworker;
            if (withAheadWorkers && (previousVWorker != null)) {
                // Each worker behind previous one.
                final VirtualWorker[] aheadVWorkers = new VirtualWorker[]{previousVWorker};
                vworker = builder.newVWorker(subscriber,aheadVWorkers);
            } else {
                vworker = builder.newVWorker(subscriber);
            }
            subscribers.add(subscriber);
            vworkers.add(vworker);
            previousVWorker = vworker;
        }
        if (context.ensureSingleHeadWorker) {
            builder.ensureSingleHeadWorker();
        }
        if (context.ensureSingleTailWorker) {
            builder.ensureSingleTailWorker();
        }
        final boolean withDependentWorkers = (withAheadWorkers || context.ensureSingleHeadWorker || context.ensureSingleTailWorker);
        
        /*
         * 
         */
        
        builder.apply(helper);
        
        for (int i=0;i<nbrOfSubscribers;i++) {
            MySubscriber subscriber = subscribers.get(i);
            VirtualWorker vworker = vworkers.get(i);
            
            subscriber.setAheadWorkers(vworker.getAheadWorkers());
        }

        /*
         * 
         */

        final ConcurrentLinkedQueue<Long> claimedSequences = new ConcurrentLinkedQueue<Long>();

        /*
         * Starting workers if not service.
         */

        // Always calling again if exception.
        final boolean workerCalledAgainIfTestException = exceptions;
        this.workerCalledAgainIfTestException = workerCalledAgainIfTestException;
        final boolean possibleWorkerStop = exceptions && (!workerCalledAgainIfTestException);
        // A dependent worker might block waiting for a terminated ahead worker
        // to make progress.
        final boolean possibleWorkerBlock = (!helper.isService()) && withDependentWorkers;
        final boolean possibleWorkerStopOrBlock = possibleWorkerStop || possibleWorkerBlock;
        
        testSteps.add(datedLog("starting workers if non-service...",startNS));

        helper.startWorkersIfNonService();

        /*
         * Starting publication: from now on, exceptions might occur in workers.
         */

        // Using blocking claims, might block forever in some cases,
        // waiting for a sequence slot.
        final boolean canUseBlockingClaims =
                (helper.isService() || ((!stopBeforePubEnd) && (!useSetStateTerminateASAP)))
                && (!possibleWorkerStopOrBlock);

        testSteps.add(datedLog("starting publishers...",startNS));

        final AtomicInteger publisherCounter = new AtomicInteger(nbrOfPublishers);
        final ArrayList<MyPublisherRunnable> publishersRunnables = new ArrayList<MyPublisherRunnable>();
        for (int i=0;i<nbrOfPublishers;i++) {
            final MyPublisherRunnable publisherRunnable = new MyPublisherRunnable(
                    publisherCounter,
                    ringBuffer,
                    (useLocalPublishPort ? ringBuffer.newLocalPublishPort() : ringBuffer),
                    nbrOfEventsPerPublisher,
                    home.monotonicPubSeq,
                    canUseBlockingClaims,
                    events,
                    claimedSequences);
            this.publishersExecutor.execute(publisherRunnable);
            publishersRunnables.add(publisherRunnable);
        }

        /*
         * Interrupting a few times, to make sure our interruption treatment
         * clears interruption status on completion and only cause
         * interruption through InterruptedException.
         */
        
        if (this.interruptingWorkers) {
            for (int i=0;i<2;i++) {
                if (ringBuffer instanceof MulticastRingBufferService) {
                    yieldOrNot();
                    ((MulticastRingBufferService)ringBuffer).interruptWorkers();
                } else if (ringBuffer instanceof UnicastRingBufferService) {
                    yieldOrNot();
                    ((UnicastRingBufferService)ringBuffer).interruptWorkers();
                }
            }
        }
        
        // Eventually sleeping a bit, else early stop might
        // too often happen too early, before any event can
        // have been processed.
        if (stopBeforePubEnd
                && this.defaultRandom.nextBoolean()) {
            if (DEBUG) {
                HeisenLogger.log("small sleep before early stop");
            }
            Unchecked.sleepMS(1);
        }
        
        /*
         * 
         */

        final long[] sequencesRangesRejOnShut;
        if (helper.isService()) {
            if (!stopBeforePubEnd) {
                // Waiting for publisher to terminate,
                // before shut down(s).
                testSteps.add(datedLog("awaitZeroPublisher...",startNS));
                awaitZeroPublisher(publisherCounter);
            }
            // Shut down(s).
            if (useShutdown && shutdownFirstIfBoth) {
                yieldOrNot();
                testSteps.add(datedLog("shutdown...",startNS));
                ringBufferService.shutdown();
            }
            if (useShutdownNow) {
                yieldOrNot();
                testSteps.add(datedLog("shutdownNow(false)...",startNS));
                sequencesRangesRejOnShut = ringBufferService.shutdownNow(false);
            } else {
                sequencesRangesRejOnShut = null;
            }
            if (useShutdown && (!shutdownFirstIfBoth)) {
                yieldOrNot();
                testSteps.add(datedLog("shutdown...",startNS));
                ringBufferService.shutdown();
            }
            if (stopBeforePubEnd) {
                // Waiting for publisher to terminate,
                // after early shut down(s).
                testSteps.add(datedLog("awaitZeroPublisher...",startNS));
                awaitZeroPublisher(publisherCounter);
            }
        } else {
            final boolean yieldBetweenWorkers = true;
            if (!stopBeforePubEnd) {
                // Waiting for publisher to terminate,
                // before workers passivations.
                testSteps.add(datedLog("awaitZeroPublisher...",startNS));
                awaitZeroPublisher(publisherCounter);
            }
            // Passivations.
            if (useSetStateTerminateWhenIdle && terminateWhenIdleFirstIfBoth) {
                yieldOrNot();
                testSteps.add(datedLog("setWorkersStateTerminateWhenIdle...",startNS));
                helper.setWorkersStateTerminateWhenIdle(yieldBetweenWorkers);
            }
            if (useSetStateTerminateASAP) {
                yieldOrNot();
                testSteps.add(datedLog("setWorkersStateTerminateASAP...",startNS));
                helper.setWorkersStateTerminateASAP(yieldBetweenWorkers);
            }
            if (useSetStateTerminateWhenIdle && (!terminateWhenIdleFirstIfBoth)) {
                yieldOrNot();
                testSteps.add(datedLog("setWorkersStateTerminateWhenIdle...",startNS));
                helper.setWorkersStateTerminateWhenIdle();
            }
            if (stopBeforePubEnd) {
                if (!canUseBlockingClaims) {
                    for (MyPublisherRunnable publisherRunnable : publishersRunnables) {
                        publisherRunnable.stopPublishing = true;
                    }
                }
                // Waiting for publisher to terminate,
                // after early passivation(s).
                testSteps.add(datedLog("awaitZeroPublisher...",startNS));
                awaitZeroPublisher(publisherCounter);
            }
            sequencesRangesRejOnShut = null;
        }

        /*
         * 
         */

        // Calls shutdown or passivates workers, again maybe,
        // but that shouldn't hurt.
        if (DEBUG) {
            HeisenLogger.log(datedLog("shutdownAndAwaitTermination...",startNS));
        }
        testSteps.add(datedLog("shutdownAndAwaitTermination...",startNS));
        helper.shutdownAndAwaitTermination();
        if (DEBUG) {
            HeisenLogger.log(datedLog("...shutdownAndAwaitTermination",startNS));
        }

        testSteps.add(datedLog("checks...",startNS));

        /*
         * 
         */
        
        testSteps.add(datedLog("almost done...",startNS));
        testSteps.add(DONE_STEP);
        synchronized (testSteps) {
            testSteps.notifyAll();
        }
        try {
            timeoutFuture.get();
        } catch (InterruptedException e) {
            throw new RethrowException(e);
        } catch (ExecutionException e) {
            throw new RethrowException(e);
        }

        /*
         * Basic checks.
         */

        if (helper.isService()) {
            if (!stopBeforePubEnd) {
                azertEquals(0,reh.rejOnPubRanges.size());
            }
        }

        for (int i=0;i<subscribers.size();i++) {
            MySubscriber subscriber = subscribers.get(i);
            final boolean didRead = (subscriber.firstReadSeq <= subscriber.lastReadSeq);
            if (home.multicast) {
                if (stopBeforePubEnd) {
                    if (didRead) {
                        azertEquals(0L, subscriber.firstReadSeq);
                    }
                } else {
                    azertTrue(didRead);
                    azertEquals(0L, subscriber.firstReadSeq);
                    if (!(useShutdownNow || useSetStateTerminateASAP)) {
                        if (possibleWorkerStopOrBlock) {
                            azertTrue(totalNbrOfEvents-1 >= subscriber.lastReadSeq);
                            azertTrue(totalNbrOfEvents >= subscriber.nbrOfCalls_readEvent);
                        } else {
                            azertEquals(totalNbrOfEvents-1, subscriber.lastReadSeq);
                            azertEquals(totalNbrOfEvents, subscriber.nbrOfCalls_readEvent);
                        }
                    }
                }
            }
            if (exceptions) {
                // If exception occurs in readEvent, processReadEvent won't be called.
                azertTrue(subscriber.nbrOfCalls_readEvent >= subscriber.nbrOfCalls_processReadEvent);
            } else {
                azertEquals(subscriber.nbrOfCalls_readEvent, subscriber.nbrOfCalls_processReadEvent);
            }
            if (didRead) {
                if (exceptions) {
                    // If onBatchEnd called before last sequence, and
                    // then an exception is thrown when reading last read sequence,
                    // no call to onBatchEnd will be done for last read sequence.
                    azertTrue(subscriber.lastReadSeq >= subscriber.lastReadSeqOnLastBatchEnd);
                } else {
                    azertTrue(subscriber.nbrOfCalls_onBatchEnd > 0);
                    azertEquals(subscriber.lastReadSeq, subscriber.lastReadSeqOnLastBatchEnd);
                }
            }
        }

        /*
         * Number by sequence.
         */

        final MySequenceCountMap claimingsBySequence = new MySequenceCountMap();
        final MySequenceCountMap readsBySequence = new MySequenceCountMap();
        final MySequenceCountMap rejectionsOnPubBySequence = new MySequenceCountMap();
        final MySequenceCountMap rejectionsOnShutBySequence = new MySequenceCountMap();

        /*
         * Filling numbers.
         */

        for (Long sequence : claimedSequences) {
            claimingsBySequence.incr(sequence);
        }

        for (MySubscriber subscriber : subscribers) {
            if (subscriber.firstReadSeq <= subscriber.lastReadSeq) {
                if (home.multicast) {
                    for (long seq=subscriber.firstReadSeq;seq<=subscriber.lastReadSeq;seq++) {
                        readsBySequence.incr(seq);
                    }
                } else {
                    for (Long sequence : subscriber.readSeqs) {
                        readsBySequence.incr(sequence);
                    }
                }
            }
        }

        if (helper.isService()) {
            for (MySequencesRange range : reh.rejOnPubRanges) {
                final long minSeq = range.minSequence;
                final long maxSeq = range.maxSequence;
                azertTrue(minSeq <= maxSeq);
                for (long seq=minSeq;seq<=maxSeq;seq++) {
                    rejectionsOnPubBySequence.incr(seq);
                }
            }
        } else {
            // No rejection.
        }

        if (sequencesRangesRejOnShut != null) {
            final int length = sequencesRangesRejOnShut.length;
            azertTrue(NumbersUtils.isEven(length));
            final int nbrOfRanges = length/2;
            for (int i=0;i<nbrOfRanges;i++) {
                final long minSeq = sequencesRangesRejOnShut[2*i];
                final long maxSeq = sequencesRangesRejOnShut[2*i+1];
                azertTrue(minSeq <= maxSeq);
                for (long seq=minSeq;seq<=maxSeq;seq++) {
                    rejectionsOnShutBySequence.incr(seq);
                }
            }
        }

        /*
         * Checking numbers.
         */

        // If shut downs after all publishes, then as many sequences
        // claimed as events publishers tried to publish.
        if (!stopBeforePubEnd) {
            azertEquals(totalNbrOfEvents,claimingsBySequence.getSize());
        }

        // If only shutdown, or workers terminations when idle, have been used, and after all publishes,
        // then all events that publishers tried to publish, have been read.
        if ((!stopBeforePubEnd)
                && (!useShutdownNow)
                && (!useSetStateTerminateASAP)) {
            if (!possibleWorkerStopOrBlock) {
                azertEquals(totalNbrOfEvents,readsBySequence.getSize());
            }
        }

        final int expectedNbrOfReads = home.multicast ? nbrOfSubscribers : 1;

        final Set<Long> allSequences = new HashSet<Long>();
        allSequences.addAll(claimingsBySequence.sequencesSet());
        allSequences.addAll(readsBySequence.sequencesSet());
        allSequences.addAll(rejectionsOnPubBySequence.sequencesSet());
        allSequences.addAll(rejectionsOnShutBySequence.sequencesSet());

        for (Long seq : allSequences) {
            final int claimings = claimingsBySequence.get(seq);
            final int reads = readsBySequence.get(seq);
            final int rejsOnPub = rejectionsOnPubBySequence.get(seq);
            final int rejsOnShut = rejectionsOnShutBySequence.get(seq);

            boolean problem = false;
            if (claimings > 1) {
                System.err.println("sequence "+seq+" claimed "+claimings+" times");
                problem = true;
            }
            if ((claimings == 0) && ((reads != 0) || (rejsOnPub != 0) || (rejsOnShut != 0))) {
                System.err.println("sequence "+seq+" handled but not claimed");
                problem = true;
            }
            if ((claimings != 0) && ((reads == 0) && (rejsOnPub == 0) && (rejsOnShut == 0))) {
                if ((!helper.isService()) && (stopBeforePubEnd || useSetStateTerminateASAP)) {
                    // Normal: no rejection for non-service ring buffers
                    // (either on publish or on workers passivation).
                } else if (possibleWorkerStopOrBlock) {
                    // Normal: not read.
                } else if (helper.isService()
                        // Can't have non handled sequences if we don't stop before pub end,
                        // even in case of write lazy set, because we flush publisher
                        // threads memory before stopping.
                        && (home.possibleNonHandledSequencesOnShutdown && stopBeforePubEnd)) {
                    // Allowing for sequences published but not handled.
                    HeisenLogger.log("rare : sequence "+seq+" claimed but not handled");
                } else {
                    HeisenLogger.log("sequence "+seq+" claimed but not handled");
                    problem = true;
                }
            }
            if ((reads != 0) && (reads != expectedNbrOfReads)) {
                if ((!helper.isService()) && (stopBeforePubEnd || useSetStateTerminateASAP)) {
                    // Normal: no rejection for non-service ring buffers,
                    // nor coordination among workers (to make sure they
                    // all process the same last sequence) if multicast.
                } else if (possibleWorkerStopOrBlock) {
                    // Normal: not read.
                } else {
                    HeisenLogger.log("sequence "+seq+" read "+reads+" times instead of "+expectedNbrOfReads);
                    problem = true;
                }
            }
            if (rejsOnPub > 1) {
                HeisenLogger.log("sequence "+seq+" rejected on publication "+rejsOnPub+" times");
                problem = true;
            }
            if (rejsOnShut > 1) {
                HeisenLogger.log("sequence "+seq+" rejected on shut down "+rejsOnShut+" times");
                problem = true;
            }
            if ((reads != 0) && (rejsOnPub != 0)) {
                HeisenLogger.log("sequence "+seq+" read and rejected on publication");
                problem = true;
            }
            if ((reads != 0) && (rejsOnShut != 0)) {
                HeisenLogger.log("sequence "+seq+" read and rejected on shut down");
                problem = true;
            }
            if ((rejsOnPub != 0) && (rejsOnShut != 0)) {
                HeisenLogger.log("sequence "+seq+" rejected on publication and on shut down");
                problem = true;
            }

            if (problem) {
                System.err.println("sequence "+seq+" has been claimed "
                        +claimings+" times, read "
                        +reads+" times, rejected on publication "
                        +rejsOnPub+" times, and rejected on shut down "
                        +rejsOnShut+" times");
                if (true) {
                    System.err.println(context.toString());
                }
                if (true) {
                    final Long[] allSequencesArray = allSequences.toArray(new Long[allSequences.size()]);
                    Arrays.sort(allSequencesArray, 0, allSequencesArray.length);
                    for (Long s : allSequencesArray) {
                        System.err.println("sequence "+s+" has been claimed "
                                +claimingsBySequence.get(s)+" times, read "
                                +readsBySequence.get(s)+" times, rejected on publication "
                                +rejectionsOnPubBySequence.get(s)+" times, and rejected on shut down "
                                +rejectionsOnShutBySequence.get(s)+" times");
                    }
                }
                onError("sequences error");
            }
        }
        
        if (this.firstError.get() != null) {
            HeisenLogger.log("first error = "+this.firstError.get());
            HeisenLogger.flushPendingLogsAndStream();
            assertTrue(false);
        }
        
        if (DEBUG) {
            HeisenLogger.log("...test_ringBuffer --------------------------------------------------------------------------");
            HeisenLogger.flushPendingLogsAndStream();
        }
        // XXX
    }
    
    /*
     * specific tests
     */
    
    public void test_workers_getMaxPassedSequence() {
        for (boolean multicast : new boolean[]{false,true}) {
            test_workers_getMaxPassedSequence(multicast);
        }
    }

    public void test_workers_getMaxPassedSequence(final boolean multicast) {
        if (DEBUG) {
            HeisenLogger.log("multicast = "+multicast);
            HeisenLogger.flushPendingLogsAndStream();
        }
        
        final int bufferCapacity = 4;
        final boolean singlePublisher = true;
        final boolean singleSubscriber = true;
        final ExecutorService executor = Executors.newCachedThreadPool();

        final InterfaceRingBuffer ringBuffer;
        if (multicast) {
            ringBuffer = RingBuffers.newMulticastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
        } else {
            ringBuffer = RingBuffers.newUnicastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
        }
        
        /*
         * 
         */
        
        final AtomicLong blockReading = new AtomicLong();
        final InterfaceRingBufferWorker worker = ringBuffer.newWorker(new InterfaceRingBufferSubscriber() {
            @Override
            public void readEvent(long sequence, int index) {
                synchronized (blockReading) {
                    while (blockReading.get() == sequence) {
                        try {
                            blockReading.wait();
                        } catch (InterruptedException e) {
                            assertTrue(false);
                        }
                    }
                }
            }
            @Override
            public boolean processReadEvent() {
                return false;
            }
            @Override
            public void onBatchEnd() {
            }
        });

        long lastClaimed;
        
        /*
         * 
         */
        
        assertEquals(-1, worker.getMaxPassedSequence());

        /*
         * publishing two events
         */
        
        for (int i=0;i<2;i++) {
            lastClaimed = ringBuffer.claimSequence();
            ringBuffer.publish(lastClaimed);
        }

        /*
         * 
         */
        
        // Will block reading.
        executor.execute(new Runnable() {
            @Override
            public void run() {
                // Should be enough for worker to block reading 0.
                TestUtils.sleepMSInChunks(TOLERANCE_MS);
                // Not updated yet, since bounded batch is not done.
                assertEquals(-1, worker.getMaxPassedSequence());
                // Unblocking worker one event.
                blockReading.set(1);
                synchronized (blockReading) {
                    blockReading.notifyAll();
                }
                // Should be enough for worker to block reading 1.
                TestUtils.sleepMSInChunks(TOLERANCE_MS);
                if (multicast) {
                    // Not updated yet, since bounded batch is not done.
                    assertEquals(-1, worker.getMaxPassedSequence());
                } else {
                    // Not updated yet, since did not complete.
                    assertEquals(-1, worker.getMaxPassedSequence());
                }
                // Unblocking worker one event.
                blockReading.set(2);
                synchronized (blockReading) {
                    blockReading.notifyAll();
                }
                // Should be enough for worker to block waiting for more.
                TestUtils.sleepMSInChunks(TOLERANCE_MS);
                if (multicast) {
                    // Updated (worker now idle).
                    assertEquals(1, worker.getMaxPassedSequence());
                } else {
                    // Not updated yet, since did not complete.
                    assertEquals(-1, worker.getMaxPassedSequence());
                }
                // Releasing worker.
                worker.setStateTerminateASAP();
            }
        });
        try {
            worker.runWorker();
        } catch (InterruptedException e) {
            // quiet
        }
        // Updated, since did complete.
        assertEquals(1, worker.getMaxPassedSequence());

        /*
         * 
         */
        
        Unchecked.shutdownAndAwaitTermination(executor);
    }
    
    public void test_subscriberStop() {
        for (boolean multicast : new boolean[]{false,true}) {
            test_subscriberStop(multicast);
        }
    }

    public void test_subscriberStop(final boolean multicast) {
        if (DEBUG) {
            HeisenLogger.log("multicast = "+multicast);
            HeisenLogger.flushPendingLogsAndStream();
        }
        
        final int bufferCapacity = 4;
        final boolean singlePublisher = true;
        final boolean singleSubscriber = true;

        final InterfaceRingBuffer ringBuffer;
        if (multicast) {
            ringBuffer = RingBuffers.newMulticastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
        } else {
            ringBuffer = RingBuffers.newUnicastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
        }

        final AtomicBoolean subscriberStop = new AtomicBoolean();
        final InterfaceRingBufferWorker worker = ringBuffer.newWorker(new InterfaceRingBufferSubscriber() {
            @Override
            public void readEvent(long sequence, int index) {
            }
            @Override
            public boolean processReadEvent() {
                return subscriberStop.get();
            }
            @Override
            public void onBatchEnd() {
            }
        });

        long lastClaimed;
        long expectedMaxPassed;

        /*
         * no stop
         */
        
        // Filling ring buffer.
        for (int i=0;i<bufferCapacity;i++) {
            lastClaimed = ringBuffer.claimSequence();
            ringBuffer.publish(lastClaimed);
        }

        // For worker to return once idle.
        worker.setStateTerminateWhenIdle();
        
        // Will process all events.
        try {
            worker.runWorker();
        } catch (InterruptedException e) {
            assertTrue(false);
        }
        expectedMaxPassed = bufferCapacity-1;
        assertEquals(expectedMaxPassed, worker.getMaxPassedSequence());
        
        /*
         * stop
         */
        
        subscriberStop.set(true);
        
        // Filling ring buffer.
        for (int i=0;i<bufferCapacity;i++) {
            lastClaimed = ringBuffer.claimSequence();
            ringBuffer.publish(lastClaimed);
        }

        // Will process only first event, then return.
        try {
            worker.runWorker();
        } catch (InterruptedException e) {
            assertTrue(false);
        }
        expectedMaxPassed = bufferCapacity;
        assertEquals(expectedMaxPassed, worker.getMaxPassedSequence());
    }
    
    public void test_worker_reset_and_setStateXXX() {
        for (boolean multicast : new boolean[]{false,true}) {
            test_worker_reset_and_setStateXXX(multicast);
        }
    }

    public void test_worker_reset_and_setStateXXX(final boolean multicast) {
        if (DEBUG) {
            HeisenLogger.log("multicast = "+multicast);
            HeisenLogger.flushPendingLogsAndStream();
        }
        
        final int bufferCapacity = 4;
        final boolean singlePublisher = true;
        final boolean singleSubscriber = true;

        final InterfaceRingBuffer ringBuffer;
        if (multicast) {
            ringBuffer = RingBuffers.newMulticastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
        } else {
            ringBuffer = RingBuffers.newUnicastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
        }

        final AtomicBoolean interruptBeforeWait = new AtomicBoolean(true);
        final InterfaceRingBufferWorker worker = ringBuffer.newWorker(new InterfaceRingBufferSubscriber() {
            @Override
            public void readEvent(long sequence, int index) {
            }
            @Override
            public boolean processReadEvent() {
                return false;
            }
            @Override
            public void onBatchEnd() {
                if (interruptBeforeWait.get()) {
                    // To interrupt the worker when idle,
                    // instead of having wait forever
                    // (tests being done in a same thread).
                    // Works because onBatchEnd is not called
                    // abusively, but only before waiting.
                    Thread.currentThread().interrupt();
                }
            }
        });

        long lastClaimed;
        long expectedMaxPassed;

        /*
         * RUNNING
         */

        // Publishing an event.
        lastClaimed = ringBuffer.claimSequence();
        ringBuffer.publish(lastClaimed);

        // Will process the event, and then interrupt itself
        // instead of waiting for more (since state is RUNNING).
        try {
            worker.runWorker();
            assertTrue(false);
        } catch (InterruptedException e) {
            // ok
        }
        expectedMaxPassed = lastClaimed;
        assertEquals(expectedMaxPassed, worker.getMaxPassedSequence());

        /*
         * TERMINATE_WHEN_IDLE
         */

        worker.setStateTerminateWhenIdle();
        interruptBeforeWait.set(false);

        // Publishing an event.
        lastClaimed = ringBuffer.claimSequence();
        ringBuffer.publish(lastClaimed);

        // Will process the event.
        try {
            worker.runWorker();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        expectedMaxPassed = lastClaimed;
        assertEquals(expectedMaxPassed, worker.getMaxPassedSequence());

        /*
         * TERMINATE_ASAP
         */

        worker.setStateTerminateASAP();

        // Publishing an event.
        lastClaimed = ringBuffer.claimSequence();
        ringBuffer.publish(lastClaimed);

        // Won't process the event.
        try {
            worker.runWorker();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        expectedMaxPassed = lastClaimed-1;
        assertEquals(expectedMaxPassed, worker.getMaxPassedSequence());

        /*
         * TERMINATE_WHEN_IDLE: ignored (state doesn't go backward)
         */

        worker.setStateTerminateWhenIdle();

        // Won't process the event.
        try {
            worker.runWorker();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        expectedMaxPassed = lastClaimed-1;
        assertEquals(expectedMaxPassed, worker.getMaxPassedSequence());

        /*
         * reseting state back to RUNNING
         */

        worker.reset();
        interruptBeforeWait.set(true);

        // Will process the event, and then interrupt itself
        // instead of waiting for more (since state is RUNNING).
        try {
            worker.runWorker();
            assertTrue(false);
        } catch (InterruptedException e) {
            // ok
        }
        expectedMaxPassed = lastClaimed;
        assertEquals(expectedMaxPassed, worker.getMaxPassedSequence());

        /*
         * TERMINATE_WHEN_IDLE (again)
         */

        worker.setStateTerminateWhenIdle();
        interruptBeforeWait.set(false);

        // Publishing an event.
        lastClaimed = ringBuffer.claimSequence();
        ringBuffer.publish(lastClaimed);

        // Will process the event.
        try {
            worker.runWorker();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        expectedMaxPassed = lastClaimed;
        assertEquals(expectedMaxPassed, worker.getMaxPassedSequence());
    }

    public void test_IllegalStartException_ifPublishBeforeWorkerCreation() {
        for (boolean multicast : new boolean[]{false,true}) {
            for (boolean service : new boolean[]{false,true}) {
                test_IllegalStateException_ifPublishBeforeWorkerCreation(
                        multicast,
                        service);
            }
        }
    }
    
    public void test_IllegalStateException_ifPublishBeforeWorkerCreation(
            final boolean multicast,
            final boolean service) {
        if (DEBUG) {
            HeisenLogger.log("multicast = "+multicast);
            HeisenLogger.log("service = "+service);
            HeisenLogger.flushPendingLogsAndStream();
        }
        
        final int bufferCapacity = 4;
        final boolean singlePublisher = true;
        final boolean singleSubscriber = true;
        final ExecutorService executor = Executors.newCachedThreadPool();

        final InterfaceRingBuffer ringBuffer;
        if (multicast) {
            if (service) {
                ringBuffer = RingBuffers.newMulticastRingBufferService(bufferCapacity, singlePublisher, singleSubscriber, executor);
            } else {
                ringBuffer = RingBuffers.newMulticastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
            }
        } else {
            if (service) {
                ringBuffer = RingBuffers.newUnicastRingBufferService(bufferCapacity, singlePublisher, singleSubscriber, executor);
            } else {
                ringBuffer = RingBuffers.newUnicastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
            }
        }

        if (service || multicast) {
            try {
                long seq = ringBuffer.claimSequence();
                ringBuffer.publish(seq);
                assertTrue(false);
            } catch (IllegalStateException e) {
                // ok
            }
        } else {
            long maxPub = ringBuffer.claimSequence();
            try {
                ringBuffer.publish(maxPub);
                // ok
            } catch (IllegalStateException e) {
                assertTrue(false);
            }

            // Can create worker after start.
            final InterfaceRingBufferWorker worker = ringBuffer.newWorker(new InterfaceRingBufferSubscriber() {
                @Override
                public void readEvent(long sequence, int index) {
                }
                @Override
                public boolean processReadEvent() {
                    return false;
                }
                @Override
                public void onBatchEnd() {
                }
            });

            // For worker to return after read.
            worker.setStateTerminateWhenIdle();

            try {
                worker.runWorker();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertEquals(maxPub, worker.getMaxPassedSequence());
        }

        Unchecked.shutdownAndAwaitTermination(executor);
    }
    
    /**
     * Tests behavior when no room or shut down.
     */
    public void test_claimsWhenFullAndOrShutDown() {
        for (boolean multicast : new boolean[]{false,true}) {
            for (boolean singlePublisher : new boolean[]{false,true}) {
                // Need to test implementations for both monotonic and nonMonotonic cases.
                for (boolean nonMonotonicClaim : ((multicast || singlePublisher) ? new boolean[]{false} : new boolean[]{false,true})) {
                    for (MyClaimType claimType : MyClaimType.values()) {
                        test_claimsWhenFullAndOrShutDown(
                                multicast,
                                singlePublisher,
                                nonMonotonicClaim,
                                claimType);
                    }
                }
            }
        }
    }
    
    public void test_claimsWhenFullAndOrShutDown(
            final boolean multicast,
            final boolean singlePublisher,
            final boolean nonMonotonicClaim,
            final MyClaimType claimType) {
        if (DEBUG) {
            HeisenLogger.log("multicast = "+multicast);
            HeisenLogger.log("singlePublisher = "+singlePublisher);
            HeisenLogger.log("nonMonotonicClaim = "+nonMonotonicClaim);
            HeisenLogger.log("claimType = "+claimType);
            HeisenLogger.flushPendingLogsAndStream();
        }

        final int bufferCapacity = 4;
        final boolean singleSubscriber = true;
        final ExecutorService executor = Executors.newCachedThreadPool();

        final InterfaceRingBufferService ringBuffer;
        if (multicast) {
            ringBuffer = RingBuffers.newMulticastRingBufferService(bufferCapacity, singlePublisher, singleSubscriber, executor);
        } else {
            final boolean lazySets = false;
            ringBuffer = new UnicastRingBufferService(
                    bufferCapacity,
                    (singlePublisher ? 0 : (nonMonotonicClaim ? 2 : 1)),
                    singleSubscriber,
                    lazySets,
                    lazySets,
                    Condilocks.newSmartCondilock(lazySets),
                    Condilocks.newSmartCondilock(lazySets),
                    executor,
                    new AbortingRingBufferRejectedEventHandler());
        }

        // Initially blocks reading first sequence,
        // to allow for buffer filling.
        final AtomicLong blockReading = new AtomicLong();
        ringBuffer.newWorker(new InterfaceRingBufferSubscriber() {
            @Override
            public void readEvent(long sequence, int index) {
                synchronized (blockReading) {
                    while (blockReading.get() == sequence) {
                        try {
                            blockReading.wait();
                        } catch (InterruptedException e) {
                            // quiet
                        }
                    }
                }
            }
            @Override
            public boolean processReadEvent() {
                return false;
            }
            @Override
            public void onBatchEnd() {
            }
        });

        long expectedSeq;
        long lastClaimed;

        /*
         * Putting n-1 events into the ring buffer with events.
         * Worker will block on first one.
         */

        for (int i=1;i<bufferCapacity;i++) {
            lastClaimed = ringBuffer.claimSequence();
            ringBuffer.publish(lastClaimed);
        }

        /*
         * claiming last free slot.
         */

        lastClaimed = claimOneSequence(ringBuffer, claimType);
        expectedSeq = bufferCapacity-1;
        assertEquals(expectedSeq, lastClaimed);
        ringBuffer.publish(lastClaimed);

        if (isTryClaimXXX(claimType)) {
            /*
             * Trying to claim, but full.
             */

            lastClaimed = claimOneSequence(ringBuffer, claimType);
            expectedSeq = -1;
            assertEquals(expectedSeq, lastClaimed);
            if (false) { // < 0
                ringBuffer.publish(lastClaimed);
            }
        } else {
            // Would block forever.
        }

        /*
         * Trying to claim, but full and shut down.
         */

        if (multicast) {
            // For multicast case, we need to unblock worker
            // up to max published sequence, for shutdown to
            // complete.
            blockReading.set(bufferCapacity);
            synchronized (blockReading) {
                blockReading.notifyAll();
            }
        }

        // If multicast, will cause worker to return
        // after reading bufferCapacity-1.
        ringBuffer.shutdown();

        if (multicast) {
            // Since we let worker read first round,
            // we need to publish one more round for
            // ring buffer to become full again.
            for (int i=0;i<bufferCapacity;i++) {
                // Always works even if shut down.
                while ((lastClaimed = ringBuffer.tryClaimSequence()) < 0) {
                    // Worker didn't read up to bufferCapacity-1 yet.
                    Thread.yield();
                }
                expectedSeq = bufferCapacity+i;
                assertEquals(expectedSeq, lastClaimed);
                // Don't need to publish;
                // and would be rejected anyway.
            }
        }

        if (!isTryClaimXXX(claimType)) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // Should be enough to ensure that
                    // forever-blocking claims start to wait.
                    TestUtils.sleepMSInChunks(TOLERANCE_MS);
                    // Need to release the worker,
                    // because forever-blocking claim
                    // might wait for previous sequences
                    // to be read or rejected before indicating
                    // that its lost sequences are rejected.
                    blockReading.set(Long.MAX_VALUE);
                    synchronized (blockReading) {
                        blockReading.notifyAll();
                    }
                }
            });
        }
        lastClaimed = claimOneSequence(ringBuffer, claimType);
        if (claimType == MyClaimType.tryClaimSequence) {
            expectedSeq = -1;
        } else {
            expectedSeq = -2;
        }
        assertEquals(expectedSeq, lastClaimed);
        if (false) { // < 0
            ringBuffer.publish(lastClaimed);
        }

        /*
         * 
         */

        // Releasing worker.
        blockReading.set(Long.MAX_VALUE);
        synchronized (blockReading) {
            blockReading.notifyAll();
        }

        ringBuffer.shutdownNow(false);
        try {
            ringBuffer.awaitTermination(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        /*
         * 
         */

         Unchecked.shutdownAndAwaitTermination(executor);
    }

    /**
     * Tests publishers interruptions.
     */
    public void test_publishersInterruptions() {
        for (boolean multicast : new boolean[]{false,true}) {
            for (boolean service : new boolean[]{false,true}) {
                test_publishersInterruptions(
                        multicast,
                        service);
            }
        }
    }
    
    public void test_publishersInterruptions(
            final boolean multicast,
            final boolean service) {
        if (DEBUG) {
            HeisenLogger.log("multicast = "+multicast);
            HeisenLogger.log("service = "+service);
            HeisenLogger.flushPendingLogsAndStream();
        }

        // Large enough for first claims not to fill it.
        final int bufferCapacity = 8;
        final boolean singlePublisher = true;
        final boolean singleSubscriber = true;
        final ExecutorService executor = Executors.newCachedThreadPool();

        final InterfaceRingBuffer ringBuffer;
        if (multicast) {
            if (service) {
                ringBuffer = RingBuffers.newMulticastRingBufferService(bufferCapacity, singlePublisher, singleSubscriber, executor);
            } else {
                ringBuffer = RingBuffers.newMulticastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
            }
        } else {
            if (service) {
                ringBuffer = RingBuffers.newUnicastRingBufferService(bufferCapacity, singlePublisher, singleSubscriber, executor);
            } else {
                ringBuffer = RingBuffers.newUnicastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
            }
        }

        final AtomicLong blockReading = new AtomicLong();
        final InterfaceRingBufferWorker worker = ringBuffer.newWorker(new InterfaceRingBufferSubscriber() {
            @Override
            public void readEvent(long sequence, int index) {
                synchronized (blockReading) {
                    while (blockReading.get() == sequence) {
                        try {
                            blockReading.wait();
                        } catch (InterruptedException e) {
                            // ok stopping
                            // Restoring status so that worker doesn't keep waiting.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            @Override
            public boolean processReadEvent() {
                return false;
            }
            @Override
            public void onBatchEnd() {
            }
        });
        
        final AtomicReference<Thread> nonServiceThread = new AtomicReference<Thread>();
        if (!service) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    nonServiceThread.set(Thread.currentThread());
                    try {
                        worker.runWorker();
                    } catch (InterruptedException e) {
                        // quiet
                    }
                }
            });
        }
        
        long lastClaimed;
        long expectedClaim = 0;
        long tmpClaim;

        /*
         * tryClaimSequence (with room)
         */

        Thread.currentThread().interrupt();
        lastClaimed = ringBuffer.tryClaimSequence();
        assertEquals(expectedClaim++, lastClaimed);
        // Still interrupted.
        assertTrue(Thread.currentThread().isInterrupted());
        ringBuffer.publish(lastClaimed);
        // Still interrupted.
        assertTrue(Thread.currentThread().isInterrupted());

        /*
         * tryClaimSequence(long) (with room)
         */

        Thread.currentThread().interrupt();
        try {
            // Room, so does not bother waiting
            // nor checking interruption status.
            lastClaimed = ringBuffer.tryClaimSequence(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            assertTrue(false);
        }
        assertEquals(expectedClaim++, lastClaimed);
        // Still interrupted.
        assertTrue(Thread.currentThread().isInterrupted());
        ringBuffer.publish(lastClaimed);

        /*
         * claimSequence() (with room)
         */
        
        Thread.currentThread().interrupt();
        lastClaimed = ringBuffer.claimSequence();
        // Still interrupted.
        assertTrue(Thread.currentThread().isInterrupted());
        assertEquals(expectedClaim++, lastClaimed);
        ringBuffer.publish(lastClaimed);

        /*
         * claimSequences(IntWrapper) (with room)
         */
        
        Thread.currentThread().interrupt();
        IntWrapper nbrOfSequences = new IntWrapper();
        nbrOfSequences.value = 1;
        lastClaimed = ringBuffer.claimSequences(nbrOfSequences);
        assertEquals(expectedClaim++, lastClaimed);
        assertEquals(1, nbrOfSequences.value);
        // Still interrupted.
        assertTrue(Thread.currentThread().isInterrupted());
        ringBuffer.publish(lastClaimed);
        
        /*
         * filling ring buffer
         */
        
        while ((tmpClaim = ringBuffer.tryClaimSequence()) >= 0) {
            lastClaimed = tmpClaim;
            ringBuffer.publish(lastClaimed);
        }
        expectedClaim = lastClaimed+1;
        
        /*
         * tryClaimSequence (no room)
         */

        Thread.currentThread().interrupt();
        tmpClaim = ringBuffer.tryClaimSequence();
        // Still interrupted.
        assertTrue(Thread.currentThread().isInterrupted());
        assertEquals(-1, tmpClaim);

        /*
         * tryClaimSequence(long) (no room, zero timeout)
         */

        Thread.currentThread().interrupt();
        try {
            // No room, but zero timeout, so won't
            // bother waiting nor check interruption status.
            tmpClaim = ringBuffer.tryClaimSequence(0L);
        } catch (InterruptedException e) {
            assertTrue(false);
        }
        // Still interrupted.
        assertTrue(Thread.currentThread().isInterrupted());
        assertEquals(-1, tmpClaim);

        /*
         * tryClaimSequence(long) (no room, some timeout)
         */

        Thread.currentThread().interrupt();
        try {
            ringBuffer.tryClaimSequence(1L);
            assertTrue(false);
        } catch (InterruptedException e) {
            // ok
        }
        // Did clear interruption status.
        assertFalse(Thread.currentThread().isInterrupted());

        /*
         * claimSequence() and claimSequences(IntWrapper) (no room)
         */
        
        for (final boolean bulk : new boolean[]{false,true}) {
            final long nextToBlockOn = lastClaimed+1;
            // Claim will block, so we need to ensure worker
            // starts to read after some time.
            final AtomicBoolean didClaim = new AtomicBoolean();
            final AtomicBoolean runnableDone = new AtomicBoolean();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // Should be enough for claim to start to block.
                    TestUtils.sleepMSInChunks(TOLERANCE_MS);
                    assertFalse(didClaim.get());
                    // Will read current round.
                    blockReading.set(nextToBlockOn);
                    synchronized (blockReading) {
                        blockReading.notifyAll();
                    }
                    // Should be enough for worker
                    // to read the round.
                    TestUtils.sleepMSInChunks(TOLERANCE_MS);
                    runnableDone.set(true);
                    synchronized (runnableDone) {
                        runnableDone.notifyAll();
                    }
                }
            });
            Thread.currentThread().interrupt();
            if (bulk) {
                nbrOfSequences.value = 1;
                lastClaimed = ringBuffer.claimSequences(nbrOfSequences);
            } else {
                lastClaimed = ringBuffer.claimSequence();
            }
            didClaim.set(true);
            assertEquals(expectedClaim++, lastClaimed);
            if (bulk) {
                assertEquals(1, nbrOfSequences.value);
            }
            // Still interrupted.
            assertTrue(Thread.currentThread().isInterrupted());
            ringBuffer.publish(lastClaimed);
            
            // Clearing interruption status.
            Thread.interrupted();
            
            // Waiting for runnable to be done,
            // to avoid concurrent calls.
            synchronized (runnableDone) {
                while (!runnableDone.get()) {
                    Unchecked.wait(runnableDone);
                }
            }
            
            // Filling ring buffer.
            while ((tmpClaim = ringBuffer.tryClaimSequence()) >= 0) {
                lastClaimed = tmpClaim;
                ringBuffer.publish(lastClaimed);
            }
            expectedClaim = lastClaimed+1;
        }
        
        /*
         * 
         */
        
        // Releasing worker.
        blockReading.set(Long.MAX_VALUE);
        synchronized (blockReading) {
            blockReading.notifyAll();
        }

        // Clearing interruption status.
        Thread.interrupted();

        if (service) {
            final InterfaceRingBufferService ringBufferService = (InterfaceRingBufferService)ringBuffer;
            ringBufferService.shutdown();
            try {
                ringBufferService.awaitTermination(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            nonServiceThread.get().interrupt();
            // Should be enough for interruption to have effect.
            TestUtils.sleepMSInChunks(TOLERANCE_MS);
        }

        /*
         * 
         */

        Unchecked.shutdownAndAwaitTermination(executor);
    }
    
    /**
     * Tests workers interruptions from interruptRunningWorkers()
     * and shutdownNow(...).
     */
    public void test_workersInterruptions() {
        for (boolean multicast : new boolean[]{false,true}) {
            for (boolean interruptIfPossible : new boolean[]{false,true}) {
                test_workersInterruptions(
                        multicast,
                        interruptIfPossible);
            }
        }
    }
    
    public void test_workersInterruptions(
            final boolean multicast,
            final boolean interruptIfPossible) {
        if (DEBUG) {
            HeisenLogger.log("multicast = "+multicast);
            HeisenLogger.log("interruptIfPossible = "+interruptIfPossible);
            HeisenLogger.flushPendingLogsAndStream();
        }

        final int bufferCapacity = 4;
        final boolean singlePublisher = true;
        final boolean singleSubscriber = true;
        final ExecutorService executor = Executors.newCachedThreadPool();

        final InterfaceRingBufferService ringBuffer;
        if (multicast) {
            ringBuffer = RingBuffers.newMulticastRingBufferService(bufferCapacity, singlePublisher, singleSubscriber, executor);
        } else {
            ringBuffer = RingBuffers.newUnicastRingBufferService(bufferCapacity, singlePublisher, singleSubscriber, executor);
        }

        final AtomicBoolean blockReading = new AtomicBoolean(true);
        final AtomicBoolean workerInterrupted = new AtomicBoolean();
        ringBuffer.newWorker(new InterfaceRingBufferSubscriber() {
            @Override
            public void readEvent(long sequence, int index) {
                synchronized (blockReading) {
                    while (blockReading.get()) {
                        try {
                            blockReading.wait();
                        } catch (InterruptedException e) {
                            workerInterrupted.set(true);
                            // ok stopping
                            break;
                        }
                    }
                }
            }
            @Override
            public boolean processReadEvent() {
                return false;
            }
            @Override
            public void onBatchEnd() {
            }
        });
        
        long lastClaimed;

        /*
         * Publishing an event: worker will block reading it.
         */

        lastClaimed = ringBuffer.claimSequence();
        ringBuffer.publish(lastClaimed);

        // Should be enough for worker to start blocking.
        TestUtils.sleepMSInChunks(TOLERANCE_MS);

        /*
         * interrupting
         */
        
        assertFalse(workerInterrupted.get());
        if (multicast) {
            ((MulticastRingBufferService)ringBuffer).interruptWorkers();
        } else {
            ((UnicastRingBufferService)ringBuffer).interruptWorkers();
        }
        
        // Should be enough for interruption signal to have effect.
        TestUtils.sleepMSInChunks(TOLERANCE_MS);
        
        assertTrue(workerInterrupted.get());
        // Resetting flag.
        workerInterrupted.set(false);
        
        /*
         * Publishing an event, for worker to block again.
         */
        
        lastClaimed = ringBuffer.claimSequence();
        ringBuffer.publish(lastClaimed);

        // Should be enough for worker to start blocking.
        TestUtils.sleepMSInChunks(TOLERANCE_MS);

        /*
         * shutdownNow, with interruption or not.
         * If asked to interrupt, our implementations always do.
         */
        
        if (multicast && (!interruptIfPossible)) {
            // shutdownNow will block until subscriber completes,
            // so we will need to interrupt worker ourselves
            // to unblock it.
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // Should be enough for shutdownNow to block
                    // waiting for subscriber to complete.
                    TestUtils.sleepMSInChunks(TOLERANCE_MS);
                    // shutdownNow didn't interrupt the worker.
                    assertFalse(workerInterrupted.get());
                    // Unblocking worker.
                    ((MulticastRingBufferService)ringBuffer).interruptWorkers();
                    // Should be enough for interruption to have effect.
                    TestUtils.sleepMSInChunks(TOLERANCE_MS);
                    assertTrue(workerInterrupted.get());
                    workerInterrupted.set(false);
                }
            });
            ringBuffer.shutdownNow(interruptIfPossible);
            // shutdownNow completed due to our own interruption,
            // but we shouldn't have reset interruption flag yet.
            assertTrue(workerInterrupted.get());
        } else {
            ringBuffer.shutdownNow(interruptIfPossible);
            // Should be enough for interruption to have effect.
            TestUtils.sleepMSInChunks(TOLERANCE_MS);
            assertEquals(interruptIfPossible, workerInterrupted.get());
            workerInterrupted.set(false);
        }
        
        /*
         * 
         */
        
        // Releasing worker.
        blockReading.set(false);
        synchronized (blockReading) {
            blockReading.notifyAll();
        }

        try {
            ringBuffer.awaitTermination(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        /*
         * 
         */

         Unchecked.shutdownAndAwaitTermination(executor);
    }

    /**
     * Test timeouts handling for tryClaimSequence(long)
     * and awaitTermination(long).
     */
    public void test_timeouts() {
        for (boolean multicast : new boolean[]{false,true}) {
            for (boolean service : new boolean[]{false,true}) {
                test_timeouts(
                        multicast,
                        service);
            }
        }
    }
    
    public void test_timeouts(
            final boolean multicast,
            final boolean service) {
        if (DEBUG) {
            HeisenLogger.log("multicast = "+multicast);
            HeisenLogger.log("service = "+service);
            HeisenLogger.flushPendingLogsAndStream();
        }

        final int bufferCapacity = 4;
        final boolean singlePublisher = true;
        final boolean singleSubscriber = true;
        final ExecutorService executor = Executors.newCachedThreadPool();

        final InterfaceRingBuffer ringBuffer;
        if (multicast) {
            if (service) {
                ringBuffer = RingBuffers.newMulticastRingBufferService(bufferCapacity, singlePublisher, singleSubscriber, executor);
            } else {
                ringBuffer = RingBuffers.newMulticastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
            }
        } else {
            if (service) {
                ringBuffer = RingBuffers.newUnicastRingBufferService(bufferCapacity, singlePublisher, singleSubscriber, executor);
            } else {
                ringBuffer = RingBuffers.newUnicastRingBuffer(bufferCapacity, singlePublisher, singleSubscriber);
            }
        }

        final AtomicBoolean blockReading = new AtomicBoolean(true);
        // If non-service, worker will actually never run.
        ringBuffer.newWorker(new InterfaceRingBufferSubscriber() {
            @Override
            public void readEvent(long sequence, int index) {
                synchronized (blockReading) {
                    while (blockReading.get()) {
                        try {
                            blockReading.wait();
                        } catch (InterruptedException e) {
                            // ok stopping
                            break;
                        }
                    }
                }
            }
            @Override
            public boolean processReadEvent() {
                return false;
            }
            @Override
            public void onBatchEnd() {
            }
        });
        
        long lastClaimed;

        /*
         * Filling ring buffer.
         */
        
        for (int i=0;i<bufferCapacity;i++) {
            lastClaimed = ringBuffer.claimSequence();
            ringBuffer.publish(lastClaimed);
        }

        /*
         * tryClaimSequence timeout
         */
        
        final long timeoutNS = 2*(TOLERANCE_MS * 1000L * 1000L);
        
        long a = System.nanoTime();
        long seq;
        try {
            seq = ringBuffer.tryClaimSequence(timeoutNS);
        } catch (InterruptedException e) {
            throw new RuntimeException();
        }
        long b = System.nanoTime();
        assertEquals(-1, seq);
        long durationNS = (b-a);
        assertEquals(timeoutNS, durationNS, TOLERANCE_MS * 1e6);
        
        if (service) {
            final InterfaceRingBufferService ringBufferService = (InterfaceRingBufferService)ringBuffer;
            
            /*
             * awaitTermination timeout
             */
            
            a = System.nanoTime();
            boolean terminated;
            try {
                terminated = ringBufferService.awaitTermination(timeoutNS);
            } catch (InterruptedException e) {
                throw new RuntimeException();
            }
            b = System.nanoTime();
            assertEquals(false, terminated);
            durationNS = (b-a);
            assertEquals(timeoutNS, durationNS, TOLERANCE_MS * 1e6);
        }
        
        /*
         * Unblocking worker.
         */
        
        blockReading.set(false);
        synchronized (blockReading) {
            blockReading.notifyAll();
        }
        
        if (service) {
            /*
             * shut down
             */
            
            final InterfaceRingBufferService ringBufferService = (InterfaceRingBufferService)ringBuffer;
            ringBufferService.shutdown();
            boolean terminated;
            try {
                terminated = ringBufferService.awaitTermination(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                throw new RuntimeException();
            }
            assertTrue(terminated);
        }
        
        /*
         * 
         */

         Unchecked.shutdownAndAwaitTermination(executor);
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private static boolean isTryClaimXXX(MyClaimType claimType) {
        return (claimType == MyClaimType.tryClaimSequence)
                || (claimType == MyClaimType.tryClaimSequence_long);
    }
    
    /**
     * For timeout methods, uses 1ns as timeout, and rethrows if interrupted.
     */
    private static long claimOneSequence(InterfaceRingBufferPublishPort port, MyClaimType claimType) {
        if (claimType == MyClaimType.tryClaimSequence) {
            return port.tryClaimSequence();
        } else if (claimType == MyClaimType.tryClaimSequence_long) {
            try {
                return port.tryClaimSequence(1L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else if (claimType == MyClaimType.claimSequence) {
            return port.claimSequence();
        } else if (claimType == MyClaimType.claimSequences_IntWrapper) {
            IntWrapper nbrOfSequences = new IntWrapper();
            nbrOfSequences.value = 1;
            return port.claimSequences(nbrOfSequences);
        } else {
            throw new UnsupportedOperationException(claimType+" unsupported");
        }
    }
    
    /*
     * 
     */
    
    private static double getSNTSecondsSince(long startNS) {
        return ((System.nanoTime()-startNS)/1e9);
    }
    
    private static String datedLog(final String msg, long startNS) {
        return msg+"("+getSNTSecondsSince(startNS)+")";
    }
    
    private void onError(String error) {
        onError(new AssertionError(error));
    }
    private void onError(Throwable e) {
        if (this.firstError.compareAndSet(null, e)) {
            HeisenLogger.log("--- first error reported ---");
            HeisenLogger.log(e);
            HeisenLogger.log("testContext =",testContext);
            HeisenLogger.flushPendingLogsAndStream();
            System.out.flush();
            System.err.flush();
            assertTrue(false);
        }
    }

    private void azertTrue(boolean a) {
        if (!a) {
            onError("expected true, got false");
        }
    }
    
    /**
     * To avoid Integer/Long non-equal problem.
     */
    private void azertEquals(long a, long b) {
        azertEquals(new Long(a), new Long(b));
    }
    
    private void azertEquals(Object a, Object b) {
        if (!LangUtils.equalOrBothNull(a, b)) {
            onError("expected "+a+", got "+b);
        }
    }

    private void newExecutors() {
        this.publishersExecutor = (ThreadPoolExecutor)Executors.newCachedThreadPool();
        this.workersExecutor = new MyTPE((ThreadPoolExecutor)Executors.newCachedThreadPool());
        this.timeoutWarningExecutor = (ScheduledThreadPoolExecutor)Executors.newScheduledThreadPool(1);
        if (false) {
            // Method added in JDK 7, and since we want to be JDK 6 compliant
            // we will wait for timeout runnable to terminate instead.
            
            // Else timeout tasks will pile-up.
            //this.timeoutWarningExecutor.setRemoveOnCancelPolicy(true);
        }
    }

    private void deleteExecutors() {
        Unchecked.shutdownAndAwaitTermination(this.publishersExecutor);
        Unchecked.shutdownAndAwaitTermination(this.workersExecutor);
        Unchecked.shutdownAndAwaitTermination(this.timeoutWarningExecutor);
        this.publishersExecutor = null;
        this.workersExecutor = null;
        this.timeoutWarningExecutor = null;
    }

    private void renewExecutors() {
        deleteExecutors();
        newExecutors();
    }

    private void notifyExecutorsUsage() {
        if ((this.executorsUsageCounter.getAndIncrement() % NBR_OF_RUNS_PER_EXECUTOR) == NBR_OF_RUNS_PER_EXECUTOR-1) {
            this.renewExecutors();
        }
    }

    private void yieldOrNot() {
        if (this.defaultRandom.nextBoolean()) {
            Thread.yield();
        }
    }

    private static void awaitZeroPublisher(AtomicInteger counter) {
        if (DEBUG) {
            HeisenLogger.log("waiting for publishers to be done...");
        }
        awaitZero("publishers counter",counter);
        if (DEBUG) {
            HeisenLogger.log("...waiting for publishers to be done");
        }

    }

    private static void awaitZero(final String counterName, final AtomicInteger counter) {
        synchronized (counter) {
            int value;
            while ((value = counter.get()) != 0) {
                if (DEBUG) {
                    HeisenLogger.log(counterName+" = "+value);
                }
                Unchecked.waitMS(counter,1000L);
            }
        }
    }
    
    /*
     * 
     */

    /**
     * Should allow to trigger bugs caused by badly handled lazySets (in case of signal elision),
     * while still allowing to trigger bugs caused by missing signals (in case of blocking wait).
     */
    private static InterfaceCondilock newSpinningBlockingElusiveCondilock(boolean lazySets) {
        // If too small, very few chances for signal elision
        // (few threads using blocking wait), which allows to
        // test bugs caused by lazy sets, and the tests
        // are slow (lots of blocking waits).
        // If too large, very few chances for blocking waits,
        // which does not allow to test well that all needed
        // signals are done.
        final long maxSpinningWaitNS = 10L * 1000L;
        return new SmartMonitorCondilock(
                0L, // nbrOfInitialBusySpins
                Integer.MAX_VALUE, // bigYieldThresholdNS (all yields considered small)
                0L, // nbrOfBusySpinsAfterSmallYield
                maxSpinningWaitNS,
                //
                true, // elusiveInLockSignaling
                (lazySets ? 1L : Long.MAX_VALUE), // initialBlockingWaitChunkNS
                (lazySets ? 0.1 : 0.0)); // blockingWaitChunkIncreaseRate
    }

    /*
     * 
     */
    
    private ArrayList<MyRingBufferHome> newRBHs_multicast_nonService() {
        final boolean multicast = true;
        // Multicast ring buffers always monotonic.
        final boolean monotonicPubSeq = true;

        final int bufferCapacity = this.randomBufferCapacityMonotonic();
        final boolean readLazySets = this.randomReadLazySets();
        final boolean writeLazySets = this.randomWriteLazySets();
        final String lazySetsInfo = "readLazySets="+readLazySets+",writeLazySets="+writeLazySets;
        final boolean singlePublisher = this.defaultRandom.nextBoolean();
        final boolean singleSubscriber = this.defaultRandom.nextBoolean();

        final ArrayList<MyRingBufferHome> homes = new ArrayList<MyRingBufferHome>();

        if (true) {
            final InterfaceFactory<MyRingBufferTestHelper> helperFactory = new InterfaceFactory<MyRingBufferTestHelper>() {
                @Override
                public MyRingBufferTestHelper newInstance() {
                    final InterfaceRingBuffer ringBuffer = new MulticastRingBuffer(
                            bufferCapacity,
                            singlePublisher,
                            singleSubscriber,
                            readLazySets,
                            writeLazySets,
                            newSpinningBlockingElusiveCondilock(readLazySets),
                            newSpinningBlockingElusiveCondilock(writeLazySets));
                    return new MyRingBufferTestHelper(
                            ringBuffer,
                            workersExecutor,
                            null);
                }
            };
            homes.add(
                    new MyRingBufferHome(
                            helperFactory,
                            multicast,
                            singlePublisher,
                            singleSubscriber,
                            monotonicPubSeq,
                            false, // possibleNonHandledSequencesOnShutdown
                            lazySetsInfo));
        }

        return homes;
    }

    private ArrayList<MyRingBufferHome> newRBHs_multicast_service() {
        final boolean multicast = true;
        // Multicast ring buffers always monotonic.
        final boolean monotonicPubSeq = true;

        final int bufferCapacity = this.randomBufferCapacityMonotonic();
        final boolean readLazySets = this.randomReadLazySets();
        final boolean writeLazySets = this.randomWriteLazySets();
        final String lazySetsInfo = "readLazySets="+readLazySets+",writeLazySets="+writeLazySets;
        final boolean possibleNonHandledSequencesOnShutdown = writeLazySets;
        final boolean singlePublisher = this.defaultRandom.nextBoolean();
        final boolean singleSubscriber = this.defaultRandom.nextBoolean();

        final ArrayList<MyRingBufferHome> homes = new ArrayList<MyRingBufferHome>();

        if (true) {
            final InterfaceFactory<MyRingBufferTestHelper> helperFactory = new InterfaceFactory<MyRingBufferTestHelper>() {
                @Override
                public MyRingBufferTestHelper newInstance() {
                    final MyRingBufferRejectedEventHandler reh = new MyRingBufferRejectedEventHandler();
                    final InterfaceRingBuffer ringBuffer = new MulticastRingBufferService(
                            bufferCapacity,
                            singlePublisher,
                            singleSubscriber,
                            readLazySets,
                            writeLazySets,
                            newSpinningBlockingElusiveCondilock(readLazySets),
                            newSpinningBlockingElusiveCondilock(writeLazySets),
                            workersExecutor,
                            reh);
                    reh.init(ringBuffer);
                    return new MyRingBufferTestHelper(
                            ringBuffer,
                            workersExecutor,
                            reh);
                }
            };
            homes.add(
                    new MyRingBufferHome(
                            helperFactory,
                            multicast,
                            singlePublisher,
                            singleSubscriber,
                            monotonicPubSeq,
                            possibleNonHandledSequencesOnShutdown,
                            lazySetsInfo));
        }

        return homes;
    }

    private ArrayList<MyRingBufferHome> newRBHs_unicast_pubM_nonService() {
        final boolean multicast = false;

        final boolean readLazySets = this.randomReadLazySets();
        final boolean writeLazySets = this.randomWriteLazySets();
        final String lazySetsInfo = "readLazySets="+readLazySets+",writeLazySets="+writeLazySets;

        final ArrayList<MyRingBufferHome> homes = new ArrayList<MyRingBufferHome>();

        if (true) {
            final int bufferCapacity = this.randomBufferCapacityMonotonic();
            final boolean singlePublisher = this.defaultRandom.nextBoolean();
            final boolean singleSubscriber = this.defaultRandom.nextBoolean();
            final boolean monotonicPubSeq = true;
            final InterfaceFactory<MyRingBufferTestHelper> helperFactory = new InterfaceFactory<MyRingBufferTestHelper>() {
                @Override
                public MyRingBufferTestHelper newInstance() {
                    final InterfaceRingBuffer ringBuffer = new UnicastRingBuffer(
                            bufferCapacity,
                            (singlePublisher ? 0 : 1),
                            singleSubscriber,
                            readLazySets,
                            writeLazySets,
                            newSpinningBlockingElusiveCondilock(readLazySets),
                            newSpinningBlockingElusiveCondilock(writeLazySets));
                    return new MyRingBufferTestHelper(
                            ringBuffer,
                            workersExecutor,
                            null);
                }
            };
            homes.add(
                    new MyRingBufferHome(
                            helperFactory,
                            multicast,
                            singlePublisher,
                            singleSubscriber,
                            monotonicPubSeq,
                            false, // possibleNonHandledSequencesOnShutdown
                            lazySetsInfo));
        }

        return homes;
    }

    private ArrayList<MyRingBufferHome> newRBHs_unicast_pubNM_nonService() {
        final boolean multicast = false;

        final boolean readLazySets = this.randomReadLazySets();
        final boolean writeLazySets = this.randomWriteLazySets();
        final String lazySetsInfo = "readLazySets="+readLazySets+",writeLazySets="+writeLazySets;

        final ArrayList<MyRingBufferHome> homes = new ArrayList<MyRingBufferHome>();

        if (true) {
            final int bufferCapacity = this.randomBufferCapacityNonMonotonic();
            final int nbrOfAtomicCounters = this.randomNbrOfAtomicCountersNonMonotonic(bufferCapacity);
            final boolean singlePublisher = false;
            final boolean singleSubscriber = this.defaultRandom.nextBoolean();
            final boolean monotonicPubSeq = false;
            final InterfaceFactory<MyRingBufferTestHelper> helperFactory = new InterfaceFactory<MyRingBufferTestHelper>() {
                @Override
                public MyRingBufferTestHelper newInstance() {
                    final InterfaceRingBuffer ringBuffer = new UnicastRingBuffer(
                            bufferCapacity,
                            nbrOfAtomicCounters,
                            singleSubscriber,
                            readLazySets,
                            writeLazySets,
                            newSpinningBlockingElusiveCondilock(readLazySets),
                            newSpinningBlockingElusiveCondilock(writeLazySets));
                    return new MyRingBufferTestHelper(
                            ringBuffer,
                            workersExecutor,
                            null);
                }
            };
            homes.add(
                    new MyRingBufferHome(
                            helperFactory,
                            multicast,
                            singlePublisher,
                            singleSubscriber,
                            monotonicPubSeq,
                            false, // possibleNonHandledSequencesOnShutdown
                            lazySetsInfo+",nbrOfAtomicCounters="+nbrOfAtomicCounters));
        }

        return homes;
    }

    private ArrayList<MyRingBufferHome> newRBHs_unicast_pubM_service() {
        final boolean multicast = false;

        final boolean readLazySets = this.randomReadLazySets();
        final boolean writeLazySets = this.randomWriteLazySets();
        final String lazySetsInfo = "readLazySets="+readLazySets+",writeLazySets="+writeLazySets;
        final boolean possibleNonHandledSequencesOnShutdown = writeLazySets;

        final ArrayList<MyRingBufferHome> homes = new ArrayList<MyRingBufferHome>();

        if (true) {
            final int bufferCapacity = this.randomBufferCapacityMonotonic();
            final boolean singlePublisher = this.defaultRandom.nextBoolean();
            final boolean singleSubscriber = this.defaultRandom.nextBoolean();
            final boolean monotonicPubSeq = true;
            final InterfaceFactory<MyRingBufferTestHelper> helperFactory = new InterfaceFactory<MyRingBufferTestHelper>() {
                @Override
                public MyRingBufferTestHelper newInstance() {
                    final MyRingBufferRejectedEventHandler reh = new MyRingBufferRejectedEventHandler();
                    final InterfaceRingBuffer ringBuffer = new UnicastRingBufferService(
                            bufferCapacity,
                            (singlePublisher ? 0 : 1),
                            singleSubscriber,
                            readLazySets,
                            writeLazySets,
                            newSpinningBlockingElusiveCondilock(readLazySets),
                            newSpinningBlockingElusiveCondilock(writeLazySets),
                            workersExecutor,
                            reh);
                    reh.init(ringBuffer);
                    return new MyRingBufferTestHelper(
                            ringBuffer,
                            workersExecutor,
                            reh);
                }
            };
            homes.add(
                    new MyRingBufferHome(
                            helperFactory,
                            multicast,
                            singlePublisher,
                            singleSubscriber,
                            monotonicPubSeq,
                            possibleNonHandledSequencesOnShutdown,
                            lazySetsInfo));
        }

        return homes;
    }

    private ArrayList<MyRingBufferHome> newRBHs_unicast_pubNM_service() {
        final boolean multicast = false;

        final boolean readLazySets = this.randomReadLazySets();
        final boolean writeLazySets = this.randomWriteLazySets();
        final String lazySetsInfo = "readLazySets="+readLazySets+",writeLazySets="+writeLazySets;
        final boolean possibleNonHandledSequencesOnShutdown = writeLazySets;

        final ArrayList<MyRingBufferHome> homes = new ArrayList<MyRingBufferHome>();

        if (true) {
            final int bufferCapacity = this.randomBufferCapacityNonMonotonic();
            final int nbrOfAtomicCounters = this.randomNbrOfAtomicCountersNonMonotonic(bufferCapacity);
            final boolean singlePublisher = false;
            final boolean singleSubscriber = this.defaultRandom.nextBoolean();
            final boolean monotonicPubSeq = false;
            final InterfaceFactory<MyRingBufferTestHelper> helperFactory = new InterfaceFactory<MyRingBufferTestHelper>() {
                @Override
                public MyRingBufferTestHelper newInstance() {
                    final MyRingBufferRejectedEventHandler reh = new MyRingBufferRejectedEventHandler();
                    final InterfaceRingBuffer ringBuffer = new UnicastRingBufferService(
                            bufferCapacity,
                            nbrOfAtomicCounters,
                            singleSubscriber,
                            readLazySets,
                            writeLazySets,
                            newSpinningBlockingElusiveCondilock(readLazySets),
                            newSpinningBlockingElusiveCondilock(writeLazySets),
                            workersExecutor,
                            reh);
                    reh.init(ringBuffer);
                    return new MyRingBufferTestHelper(
                            ringBuffer,
                            workersExecutor,
                            reh);
                }
            };
            homes.add(
                    new MyRingBufferHome(
                            helperFactory,
                            multicast,
                            singlePublisher,
                            singleSubscriber,
                            monotonicPubSeq,
                            possibleNonHandledSequencesOnShutdown,
                            lazySetsInfo+",nbrOfAtomicCounters="+nbrOfAtomicCounters));
        }

        return homes;
    }
}
