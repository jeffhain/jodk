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
import java.util.IdentityHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import net.jodk.lang.InterfaceBooleanCondition;
import net.jodk.lang.NumbersUtils;
import net.jodk.threading.PostPaddedAtomicInteger;
import net.jodk.threading.PostPaddedAtomicLong;
import net.jodk.threading.locks.InterfaceCondilock;
import net.jodk.threading.locks.MonitorCondilock;

/**
 * Abstract class to factor ring buffers code.
 * 
 * Ring buffer service:
 * - keeps track of created workers
 * - workers use ring buffer state
 * - workers to be run in specified executor's threads
 * - provides shutdown/shutdownNow/awaitTermination methods,
 *   and sequences rejection on publish or shutdownNow
 *   when shut down.
 * Non-service ring buffer:
 * - only keeps track of created workers if multicast
 * - each worker has its own state
 * - workers to be run in user's threads
 * 
 * These ring buffers's wait/signal policies are highly configurable,
 * since all waits/signals are done with specified implementations
 * of InterfaceCondilock.
 * 
 * For services, workers are launched using Executor.execute(Runnable) method.
 * This does not allow the user to handle an InterruptedException himself,
 * but anyway such an exception can only be thrown by worker's treatments
 * (since subscribers don't throw checked exceptions), so it should not be
 * interesting for the user.
 * Also, doing otherwise would require a dependency to the heavy ExecutorService
 * interface, for its submit(Callable) method.
 * In case of InterruptedException thrown by the worker, interruption status is
 * restored before the runnable completes, so that the user can take appropriate
 * action (like giving up, or clearing interruption status and running again for
 * worker to make progress).
 * Interruption status is cleared on worker's completion (running thread nullification),
 * in case it has been set by our workers interruption treatments when the worker
 * completes (in which case interruption is not needed).
 * Note that this might clear an interrupt done by external treatments, or interruption
 * status restored by user after catching an InterruptedException in its own treatments.
 * 
 * Even though these ring buffers don't hold events array internally as done in
 * LMAX's design, their memory usage is proportional to ring buffer capacity,
 * due to use of an internal array, except for the multicast single-publisher case,
 * which uses no internal array.
 * 
 * Worker's runXXX methods are thread-safe for consecutive calls
 * by multiple threads without memory synchronization in-between
 * (they start with a volatile read, and end up with a volatile write),
 * but not for simultaneous calls.
 * 
 * Worker's getMaxPassedSequence() returns a value updated:
 * - for multicast ring buffers, after reading a bounded batch of sequences
 *   (lazy set if readLazySets), and on worker's completion (non-lazy set),
 * - for unicast ring buffers, as it's not supposed to be of much use,
 *   only on worker's completion (non-lazy set).
 * 
 * For services, all worker's methods throw UnsupportedOperationException,
 * except getMaxPassedSequence(): we don't allow user to run the worker
 * (which is ran by provided executor), nor to change its state (which is
 * ring buffer state for all workers).
 * 
 * Some methods of thread-safe (i.e. multi-publisher) publish ports
 * use thread-local data.
 * 
 * For both multicast and unicast implementations:
 * - tryClaimSequence() claims while there is room, even if shut down:
 *   it always returns a value >= 0, or -1.
 * - tryClaimSequence(long) claims while there is room, even if shut down,
 *   but if there is no room waits, and then either return a value >= 0,
 *   -1, or -2 if wait is stopped due to shut down.
 * 
 * Main differences between multicast and unicast ring buffers,
 * regarding sequence publishing and processing:
 * - how publishers check that they can publish a sequence:
 *   - multicast: workers are ahead (check workers max passed sequences)
 *   - unicast: entry is writable for this sequence
 * - how workers check that they can read a sequence:
 *   - multicast: publishers are ahead (if multi-publisher, serialize entries before)
 *   - unicast: entry is readable for this sequence
 */
abstract class AbstractRingBuffer implements InterfaceRingBuffer {
    
    /*
     * For methods dealing with batches of (consecutive) sequences,
     * using "long minSequence, int nbrOfSequences" parameters
     * by default, and "long minSequence, long maxSequence"
     * in case the range could be above Integer.MAX_VALUE
     * (not always using this last solution, for (long,int)
     * is smaller than (long,long), and more on pair with
     * public API).
     */
    
    /*
     * A claimed sequence is a sequence which has been (exclusively) acquired
     * (from claim counter, also called sequencer) and which corresponding
     * slot in entries array is free.
     */

    /*
     * MPS can stand together for MaxPublishedSequence or MaxPassedSequence.
     * To avoid confusion, we only use this acronym for the second (and most
     * common) case.
     */
    
    /*
     * It could be possible to use non-monotonic publication sequencer
     * for MulticastRingBuffer, by:
     * - adding a state in entries long (similarly to UnicastRingBuffer)
     * - having each worker check entry before eventually reading it:
     *   if its sequence is of current round, reads it, else, i.e. if it
     *   has not been published yet, changes its status with a CAS to
     *   ensure no publisher will write it.
     * 
     * It would also be possible to have a single RingBuffer class that
     * could run in either unicast or multicast mode, for example by having
     * UnicastRingBuffer's workers not skip an entry if it is in "being read"
     * status, but read it, and not change it to "writable" once read, but just
     * wait for publishers to catch-up and write it again.
     * 
     * But these modifications should makes these classes even
     * more messier than they are, and possibly slower as well,
     * even though if could lead to discoveries of interesting
     * similarities between multicast and unicast ring buffers.
     */

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean ASSERTIONS = false;
    
    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE CLASSES
    //--------------------------------------------------------------------------

    static abstract class MyAbstractWorker implements InterfaceRingBufferWorker {
        final AbstractRingBuffer ringBuffer;
        final boolean service;
        final boolean readLazySets;
        /**
         * Ring buffer state for ring buffer services,
         * else worker-specific state.
         * 
         * We only detect termination through this state,
         * even though in case of service we also could consider
         * being terminating as soon as we encounter a rejected
         * event status, to avoid tests that would be useless
         * for the general (non-terminating) case.
         */
        final PostPaddedAtomicInteger state;
        final InterfaceRingBufferSubscriber subscriber;
        /**
         * Mutex to guard running thread nullification.
         */
        final Object runningThreadNullificationMutex = new Object();
        volatile Thread runningThread;
        /**
         * True if need to call onBatchEnd(), before a wait or when done,
         * i.e. if at least one call to readEvent(...) has completed normally,
         * since last call to onBatchEnd() if any.
         */
        boolean needToCallOnBatchEnd;
        /**
         * For multicast workers, this value is regularly updated, even though
         * some lag is allowed, for quick batches.
         * 
         * For unicast workers, this value is not supposed to be regularly updated
         * (since it shouldn't be much useful, and would hurt performances).
         */
        private final PostPaddedAtomicLong maxPassedSequence = new PostPaddedAtomicLong(INITIAL_SEQUENCE-1);
        /**
         * For internal set/get of max passed sequence, that are not meant
         * to be visible from outside.
         */
        private long maxPassedSequenceLocal = INITIAL_SEQUENCE-1;
        /**
         * True if a call to runWorkerForService() has started.
         */
        private volatile boolean started = false;
        /**
         * Volatile in case of successive calls to runWorkerForService() by different threads.
         * True if runWorkerForService() completed normally, after which runWorkerForService() must not be called.
         */
        private volatile boolean done = false;
        public MyAbstractWorker(
                AbstractRingBuffer ringBuffer,
                final PostPaddedAtomicInteger state,
                final InterfaceRingBufferSubscriber subscriber) {
            this.ringBuffer = ringBuffer;
            this.service = ringBuffer.service;
            this.readLazySets = ringBuffer.readLazySets;
            this.state = state;
            this.subscriber = subscriber;
        }
        /**
         * Using this method over directly invoking interrupt() on worker
         * thread, makes sure the worker doesn't get interrupted if it's
         * done processing events.
         */
        public final void interruptRunningThreadIfAny() {
            synchronized (this.runningThreadNullificationMutex) {
                final Thread runningThread = this.runningThread;
                if (runningThread != null) {
                    // Since we are in runStateAndMutex,
                    // we are sure we don't interrupt thread
                    // after it exited run method.
                    try {
                        runningThread.interrupt();
                    } catch (SecurityException e) {
                        // quiet
                    }
                }
            }
        }
        @Override
        public void reset() {
            if (this.service) {
                throw new UnsupportedOperationException();
            }
            this.state.set(STATE_RUNNING);
            // No need to signal subscriber threads,
            // since this method is to be called when
            // worker is idle.
        }
        @Override
        public final void setStateTerminateWhenIdle() {
            if (this.service) {
                throw new UnsupportedOperationException();
            }
            if (this.state.compareAndSet(STATE_RUNNING, STATE_TERMINATE_WHEN_IDLE)) {
                this.ringBuffer.signalSubscribers();
            }
        }
        @Override
        public final void setStateTerminateASAP() {
            if (this.service) {
                throw new UnsupportedOperationException();
            }
            // Order matters in case of concurrent calls.
            if (this.state.compareAndSet(STATE_TERMINATE_WHEN_IDLE, STATE_TERMINATE_ASAP)
                    || this.state.compareAndSet(STATE_RUNNING, STATE_TERMINATE_ASAP)) {
                this.ringBuffer.signalSubscribers();
            }
        }
        @Override
        public final void runWorker() throws InterruptedException {
            if (this.service) {
                throw new UnsupportedOperationException();
            }
            
            this.runWorkerImplAfterStateAndMPSUpdates(this.state.get());
        }
        @Override
        public final long getMaxPassedSequence() {
            return this.maxPassedSequence.get();
        }
        public final void setMaxPassedSequence(long maxPassedSequence) {
            this.maxPassedSequenceLocal = maxPassedSequence;
            if (this.readLazySets) {
                this.maxPassedSequence.lazySet(maxPassedSequence);
            } else {
                this.maxPassedSequence.set(maxPassedSequence);
            }
        }
        /**
         * Efficient way of updating max passed sequence internally,
         * if update does not need to be visible outside.
         */
        public final void setMaxPassedSequenceLocal(long maxPassedSequence) {
            this.maxPassedSequenceLocal = maxPassedSequence;
        }
        /**
         * Only valid in runWorkerImplAfterStateAndMPSUpdates() method or
         * sub-methods, and for local worker.
         */
        public final long getMaxPassedSequenceLocal() {
            return this.maxPassedSequenceLocal;
        }
        /**
         * @return Max passed sequence counter, to avoid the overhead
         *         of getMaxPassedSequence() method.
         */
        protected final PostPaddedAtomicLong getMPSCounter() {
            return this.maxPassedSequence;
        }
        /**
         * A volatile read must be done before calling this method,
         * which can be done with state retrieval.
         * 
         * This method does a volatile write at the end,
         * by setting max passed sequence, then calls
         * afterLastMaxPassedSequenceSetting().
         */
        protected final void runWorkerImplAfterStateAndMPSUpdates(int recentState) throws InterruptedException {
            try {
                this.innerRunWorker(recentState);
            } finally {
                try {
                    this.callOnBatchEndIfNeeded();
                } finally {
                    // Volatile write at last.
                    // Never using lazySet for this write, as it's the last one
                    // before an eventual subsequent call by another thread
                    // without memory synchronization in between.
                    this.maxPassedSequence.set(this.getMaxPassedSequenceLocal());
                    this.afterLastMaxPassedSequenceSetting();
                }
            }
        }
        /**
         * Implementation for Runnable.run(), for ring buffer services
         * to execute workers.
         * 
         * Not making workers implement Runnable, to make sure run
         * doesn't get called by user.
         * 
         * Must not be called concurrently.
         * 
         * Can be called again if threw an exception.
         * Must not be called again after normal completion.
         * 
         * @throws IllegalStateException if called again after normal completion.
         * @throws InterruptedException if throws by regular worker treatments.
         */
        protected final void runWorkerForService() throws InterruptedException {
            if (!this.service) {
                throw new UnsupportedOperationException();
            }
            if (this.done) {
                throw new IllegalStateException("worker done");
            }
            
            if (!this.started) {
                this.ringBuffer.nbrOfRunningWorkers.incrementAndGet();
                
                // This counter needs to be incremented after
                // number of running workers, for it might be checked
                // whether or not to wait for no more running workers.
                this.ringBuffer.nbrOfStartedWorkers.incrementAndGet();
                this.started = true;
            }

            /*
             * NB: Couldn't rely on checking state for shutdown BEFORE
             * running thread setting, because state could become shutdown
             * between the check and the setting, and eventual interruption
             * not be done (running thread not being set), which could let
             * worker start processing (not being interrupted) until it
             * would eventually check state for shutdown.
             */
            
            /**
             * Main lock must also be acquired in shutdown treatments,
             * to make sure no new running thread is set between interruption
             * permission checks and actual interruptions (NB: the opposite
             * can occur and doesn't hurt: we might check interruption permission
             * for a thread, which reference then gets nullified before interruption
             * attempt).
             * 
             * Main lock must also be acquired while starting (submiting) workers,
             * and released after state (ring buffer state, since ring buffer
             * can only be a service here) setting to RUNNING, so worker treatments
             * past this lock/unlock can suppose that state is at least RUNNING.
             */
            this.ringBuffer.mainLock.lock();
            try {
                this.runningThread = Thread.currentThread();
            } finally {
                this.ringBuffer.mainLock.unlock();
            }
            
            boolean completedNormally = false;
            try {
                this.runWorkerImplAfterStateAndMPSUpdates(this.state.get());
                
                completedNormally = true;
            } finally {
                synchronized (this.runningThreadNullificationMutex) {
                    // Clearing eventual interruption status, that could
                    // have been set by call to interruptRunningThreadIfAny(),
                    // and is no longer needed since worker is now completing.
                    // Note: This might clear an interrupt done by external treatments.
                    // try/catch to make sure we go further.
                    try {
                        Thread.interrupted();
                    } catch (SecurityException se) {
                        // quiet
                    }
                    this.runningThread = null;
                }
                if (completedNormally) {
                    this.ringBuffer.nbrOfRunningWorkers.decrementAndGet();
                    try {
                        this.ringBuffer.terminateIfNeeded(false);
                    } finally {
                        // Done even if terminated() did throw
                        // an exception.
                        this.done = true;
                    }
                } else {
                    // An exception has been thrown by the subscriber
                    // (unless our code is bugged and did it):
                    // considering this worker is not done, and is
                    // ready for more work.
                }
            }
        }
        /**
         * Should not set any non-volatile value, except if
         * a volatile write or equivalent is done afterwards.
         */
        protected abstract void afterLastMaxPassedSequenceSetting();
        /**
         * Called while running thread is set, and before termination.
         * 
         * @param recentState Recently retrieved value of state.
         */
        protected abstract void innerRunWorker(int recentState) throws InterruptedException;
        /**
         * To call only before an actual wait (for whatever: for an event, for a lock, etc.),
         * and at worker termination. Not doing unnecessary calls ensure the user can rely
         * on onBatchEnd not being called abusively, which can be helpful in some cases.
         */
        protected final void callOnBatchEndIfNeeded() {
            if (this.needToCallOnBatchEnd) {
                this.callOnBatchEnd();
            }
        }
        protected final void callOnBatchEnd() {
            // Set before, in case onBatchEnd throws an exception.
            this.needToCallOnBatchEnd = false;
            this.subscriber.onBatchEnd();
        }
    }

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    private class TerminationBooleanCondilock implements InterfaceBooleanCondition {
        @Override
        public boolean isTrue() {
            return (ringBufferState.get() == STATE_TERMINATED);
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    static final long INITIAL_SEQUENCE = 0;
    static final long SEQUENCE_NO_ROOM = -1;
    static final long SEQUENCE_SHUT_DOWN = -2;
    /**
     * For whatever usage.
     */
    static final long SEQUENCE_NOT_SET = Long.MIN_VALUE;

    /*
     * Ring buffer state.
     */
    
    static final int STATE_PENDING = -1;
    /**
     * 0 for most usual state, for possibly faster check against non-zero values.
     */
    static final int STATE_RUNNING = 0;
    static final int STATE_TERMINATE_WHEN_IDLE = 1;
    static final int STATE_TERMINATE_ASAP = 2;
    static final int STATE_TERMINATE_BEING_CALLED = 3;
    static final int STATE_TERMINATED = 4;

    final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Used for ring buffer services, or for multicast ring buffers
     * to make sure that no worker gets created once publishing
     * started (need to know all workers from start, since their
     * max passed sequences are read by publishers).
     * 
     * For multicast non-service ring buffers, can only be
     * PENDING or RUNNING.
     * 
     * Always set in main mutex, to make sure it doesn't change
     * while being read in main mutex.
     */
    final PostPaddedAtomicInteger ringBufferState;

    final int indexMask;
    final boolean singlePublisher;
    final boolean singleSubscriber;
    final boolean multicast;
    final boolean service;
    /**
     * If true, read wait condilock must handle waits for lazy sets,
     * i.e. stop to wait sometimes to check whether it did miss
     * a change or not.
     */
    final boolean readLazySets;
    /**
     * If true, write wait condilock must handle waits for lazy sets,
     * i.e. stop to wait sometimes to check whether it did miss
     * a change or not.
     */
    final boolean writeLazySets;

    /**
     * Condilock to wait on for an event to be read.
     */
    final InterfaceCondilock readWaitCondilock;
    
    /**
     * Condilock to wait on for an event to be written.
     */
    final InterfaceCondilock writeWaitCondilock;

    /*
     * for ring buffer services
     */

    final Executor executor;
    
    /**
     * Never null if service.
     */
    private volatile InterfaceRingBufferRejectedEventHandler rejectedEventHandler;
    
    /**
     * Set in main lock, just before executing workers (if service),
     * if the ring buffer is a service, or if it is multicast, for
     * non-service multicast ring buffers.
     */
    volatile int nbrOfWorkersAtStart;

    /*
     * 
     */
    
    private static final RuntimePermission modifyThreadPermission = new RuntimePermission("modifyThread");
    
    private final InterfaceBooleanCondition terminationBooleanCondition;
    private final InterfaceCondilock terminationCondilock;

    /*
     * 
     */

    /**
     * Guarded by main lock.
     * Non-null only if multicast or service.
     * Not nullified on start, because used
     * to eventually interrupt workers.
     */
    final ArrayList<MyAbstractWorker> workers;

    /**
     * Guarded by main lock.
     * Initially non-null only if multicast or service.
     * Nullified on start, for GC.
     */
    private IdentityHashMap<MyAbstractWorker,Void> workersSet;
    
    /**
     * Guarded by main lock.
     * Initially non-null only if multicast or service.
     * Nullified on start, for GC.
     */
    private IdentityHashMap<InterfaceRingBufferSubscriber,MyAbstractWorker> subscribersSet;

    /**
     * Guarded by main lock.
     * Only used if single subscriber.
     */
    private boolean workerCreated = false;

    /**
     * Non-null only if service.
     */
    private final AtomicInteger nbrOfStartedWorkers;
    
    /**
     * Non-null only if service.
     * 
     * A worker is considered running from its start
     * up to its normal completion.
     * After exceptional completion,
     * it can be called again to continue work.
     */
    private final AtomicInteger nbrOfRunningWorkers;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @param multicast True if this ring buffer is multicast, false otherwise.
     * @param service True if this ring buffer is a service, false otherwise.
     * @param bufferCapacity Must be a power of two.
     * @param singlePublisher True if single publisher (not thread-safe), false otherwise.
     * @param singleSubscriber True if single subscriber (single worker), false otherwise.
     * @param lazySets True if volatile lazySet can be used instead of volatile set,
     *        in some places where it could cause unbounded waits with blocking condilocks,
     *        if their signalXXXInLock methods are elusive and don't ensure a write
     *        memory barrier: if lazySets is true, and a blocking condilock's signaling method
     *        is elusive and doesn't ensure a write memory barrier, this condilock should
     *        typically only do in-lock waits for a short time, for it might proceed to
     *        undesired and unsignaled in-lock waits.
     */
    public AbstractRingBuffer(
            boolean multicast,
            boolean service,
            //
            int bufferCapacity,
            boolean singlePublisher,
            boolean singleSubscriber,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock,
            final Executor executor,
            final InterfaceRingBufferRejectedEventHandler rejectedEventHandler) {
        if (!NumbersUtils.isPowerOfTwo(bufferCapacity)) {
            throw new IllegalArgumentException("buffer capacity ["+bufferCapacity+"] must be a power of two");
        }
        if (service) {
            if (executor == null) {
                throw new NullPointerException("executor is null");
            }
            if (rejectedEventHandler == null) {
                throw new NullPointerException("rejected event handler is null");
            }
        } else {
            if (executor != null) {
                throw new IllegalArgumentException("Can't specify an executor for a non-service ring buffer");
            }
            if (rejectedEventHandler != null) {
                throw new IllegalArgumentException("Can't specify a rejected event handler for a non-service ring buffer");
            }
        }
        if (readWaitCondilock == null) {
            throw new NullPointerException("read wait condilock is null");
        }
        if (writeWaitCondilock == null) {
            throw new NullPointerException("write wait condilock is null");
        }

        this.indexMask = bufferCapacity-1;
        this.singlePublisher = singlePublisher;
        this.singleSubscriber = singleSubscriber;
        this.multicast = multicast;
        this.service = service;
        this.readLazySets = readLazySets;
        this.writeLazySets = writeLazySets;
        
        this.readWaitCondilock = readWaitCondilock;
        this.writeWaitCondilock = writeWaitCondilock;
        
        this.executor = executor;
        
        if (service) {
            this.nbrOfStartedWorkers = new AtomicInteger();
            this.nbrOfRunningWorkers = new AtomicInteger();
        } else {
            this.nbrOfStartedWorkers = null;
            this.nbrOfRunningWorkers = null;
        }
        
        if (service || this.multicast) {
            this.ringBufferState = new PostPaddedAtomicInteger(STATE_PENDING);
            this.workers = new ArrayList<MyAbstractWorker>();
        } else {
            this.ringBufferState = null;
            this.workers = null;
        }
        
        if (service) {
            this.setRejectedEventHandlerImpl(rejectedEventHandler);
        }
        
        if (service || this.multicast) {
            this.subscribersSet = new IdentityHashMap<InterfaceRingBufferSubscriber,MyAbstractWorker>();
            this.workersSet = new IdentityHashMap<MyAbstractWorker,Void>();
        }
        
        if (service) {
            this.terminationBooleanCondition = new TerminationBooleanCondilock();
            this.terminationCondilock = new MonitorCondilock();
        } else {
            this.terminationBooleanCondition = null;
            this.terminationCondilock = null;
        }
    }

    @Override
    public int getBufferCapacity() {
        return this.indexMask+1;
    }
    
    @Override
    public InterfaceRingBufferWorker newWorker(
            final InterfaceRingBufferSubscriber subscriber,
            final InterfaceRingBufferWorker... aheadWorkers) {
        if (subscriber == null) {
            throw new NullPointerException("subscriber is null");
        }
        for (InterfaceRingBufferWorker aw : aheadWorkers) {
            if (aw == null) {
                throw new NullPointerException("null ahead worker");
            }
        }
        if ((!this.multicast) && (aheadWorkers.length != 0)) {
            throw new IllegalArgumentException("can't specify ahead workers for a unicast ring buffer");
        }
        final MyAbstractWorker worker;
        final boolean serviceOrMulticast = this.service || this.multicast;
        if (serviceOrMulticast || this.singleSubscriber) {
            this.mainLock.lock();
            try {
                if (this.singleSubscriber && this.workerCreated) {
                    throw new IllegalStateException("worker already created");
                }
                if (serviceOrMulticast) {
                    // Need to check state in main lock,
                    // to make sure no subscriber gets registered
                    // after getting into running state.
                    if (this.ringBufferState.get() >= STATE_RUNNING) {
                        throw new IllegalStateException("ring buffer already went running");
                    }
                    // Checking parameters validity after ring buffer state,
                    // for exceptions must occur in that order, and it allows
                    // to nullify now useless collections when ring buffer
                    // goes running.
                    if (this.subscribersSet.containsKey(subscriber)) {
                        throw new IllegalArgumentException("worker already created for subscriber "+subscriber);
                    }
                    for (InterfaceRingBufferWorker w : aheadWorkers) {
                        if (!this.workersSet.containsKey(w)) {
                            throw new IllegalArgumentException("worker "+w+" does not belong to this ring buffer service");
                        }
                    }
                    if (this.workers.size() == Integer.MAX_VALUE) {
                        throw new UnsupportedOperationException("full");
                    }
                    worker = this.newWorkerRaw(subscriber, aheadWorkers);
                    this.workers.add(worker);
                    this.workersSet.put(worker,null);
                    this.subscribersSet.put(subscriber,null);
                } else {
                    // Specifying ahead workers array in case impl
                    // wants to make use of it for some reason.
                    if(ASSERTIONS)assert(aheadWorkers.length == 0);
                    worker = this.newWorkerRaw(subscriber, aheadWorkers);
                }
                if (this.singleSubscriber) {
                    // Did not throw.
                    this.workerCreated = true;
                }
            } finally {
                this.mainLock.unlock();
            }
        } else {
            worker = this.newWorkerRaw(subscriber, aheadWorkers);
        }
        return worker;
    }
    
    @Override
    public int sequenceToIndex(long sequence) {
        return (int)sequence & this.indexMask;
    }
    
    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Only for ring buffer services.
     * 
     * Called outside main lock (unlike ThreadPoolExecutor.terminated()).
     * 
     * Can be called by shutdown thread, or by a worker thread.
     * 
     * Method invoked just before the ring buffer state is set to terminated
     * (as done in ThreadPoolExecutor).
     */
    protected void terminated() {
    }

    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * Signals all condilocks subscribers might be waiting on.
     */
    abstract void signalSubscribers();

    void signalAllInLockWriteRead() {
        this.writeWaitCondilock.signalAllInLock();
        this.readWaitCondilock.signalAllInLock();
    }

    /*
     * 
     */
    
    /**
     * Only for ring buffer services.
     * 
     * Checks access before actual interruptions, if any.
     * 
     * Worker's waits are not uninterruptible, so this call
     * might cause ring buffer to be blocked, unless workers
     * are called again afterward (and after interruption status
     * clearing, to avoid infinite loop).
     * 
     * If the runnable given to the Executor completes with interrupted
     * status set because of these interruptions, that means an actual
     * InterruptedException has been thrown by worker's treatments, since
     * interruption status is cleared at the time each worker completes
     * (normally or not) and becomes no longer running, i.e. no longer
     * interruptible by this treatment.
     */
    void interruptWorkersImpl() {
        if (!this.service) {
            // For non-services, we don't keep track of running thread
            // (could be much of an overhead).
            throw new UnsupportedOperationException();
        }
        this.mainLock.lock();
        try {
            checkShutdownAccessIfNeeded(true);
            for (MyAbstractWorker worker : this.workers) {
                worker.interruptRunningThreadIfAny();
            }
        } finally {
            this.mainLock.unlock();
        }
    }

    /*
     * Implementations of service-specific methods.
     */

    void setRejectedEventHandlerImpl(final InterfaceRingBufferRejectedEventHandler rejectedEventHandler) {
        if (rejectedEventHandler == null) {
            throw new NullPointerException();
        }
        this.rejectedEventHandler = rejectedEventHandler;
    }
    
    /**
     * @return Current rejected event handler (never null if service).
     */
    InterfaceRingBufferRejectedEventHandler getRejectedEventHandlerImpl() {
        return this.rejectedEventHandler;
    }

    boolean isShutdownImpl() {
        return isShutdown(this.ringBufferState.get());
    }

    boolean isTerminatedImpl() {
        return isTerminated(this.ringBufferState.get());
    }

    boolean awaitTerminationImpl(long timeoutNS) throws InterruptedException {
        // Boolean condition is immutable, so no need to create a new one each time.
        return this.terminationCondilock.awaitNanosWhileFalseInLock(
                this.terminationBooleanCondition,
                timeoutNS);
    }
    
    /*
     * 
     */

    /**
     * Only for ring buffer services.
     * 
     * Called on any shutdown attempt, in main lock.
     * 
     * Default implementation returns true if the specified interruption
     * parameter is true, false otherwise.
     * 
     * Overriding this method allows to disable permissions check
     * on shutdown attempt, since worker threads might belong to
     * a thread pool executor and not have to be considered
     * "modified" if just no longer used for this ring buffer.
     * 
     * @param interruption True if shutdown involves interruption
     *        of running worker threads, false otherwise.
     * @return True if the ring buffer must check modifyThread permission,
     *         and call SecurityManager.checkAccess on all running worker
     *         threads, before any shutdown attempt, false otherwise.
     */
    boolean mustCheckPermissionsOnShutdown(boolean interruption) {
        return interruption;
    }

    /**
     * Must be called just before the ring buffer
     * goes running.
     */
    void beforeRunning() {
        // For GC.
        this.workersSet = null;
        this.subscribersSet = null;
    }
    
    abstract MyAbstractWorker newWorkerRaw(
            final InterfaceRingBufferSubscriber subscriber,
            final InterfaceRingBufferWorker... aheadWorkers);
    
    static boolean isShutdown(int state) {
        return state > STATE_RUNNING;
    }

    static boolean isTerminated(int state) {
        return state == STATE_TERMINATED;
    }
    
    /**
     * Only for ring buffer services.
     * At least one worker must have been created.
     */
    void setNbrOfStartedWorkersAndExecuteWorkers_mainLockLocked() {
        if(ASSERTIONS)assert(this.service);
        if(ASSERTIONS)assert(this.mainLock.isHeldByCurrentThread());
        if(ASSERTIONS)assert(this.workers.size() != 0);
        
        this.nbrOfWorkersAtStart = this.workers.size();
        for (final MyAbstractWorker worker : this.workers) {
            this.executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (worker.done) {
                                // Since we don't use Callable, we have to restore interruption
                                // status in case of InterruptedException.
                                // This prevents the user from figuring out whether the interruption
                                // status comes from an actual exception (in which case a new run is
                                // possible, after interruption status clearing, to eventually make
                                // progress), or from thread having been interrupted just before normal
                                // completion (in which case a new run would throw IllegalStateException).
                                // In case of user re-call-if-interrupted, that would occur after
                                // normal completion, we therefore return here to avoid an
                                // IllegalStateException.
                                // NB: if user doesn't clear interruption status before re-call,
                                // this will make an infinite loop.
                                return;
                            }
                            try {
                                worker.runWorkerForService();
                                // This assertions allows to check that we
                                // properly clear interruption status on running
                                // thread nullification.
                                // TODO This assertion only holds for interruptions
                                // done by our running workers interruption treatment:
                                // another facility might interrupt this thread
                                // any time. Only have it true for tests.
                                if(false)assert(!Thread.currentThread().isInterrupted());
                            } catch (InterruptedException e) {
                                // Restoring interruption status,
                                // for user to take appropriate action.
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
        }
    }
    
    /**
     * Only for ring buffer services.
     */
    void checkShutdownAccessIfNeeded(boolean interruption) {
        if(ASSERTIONS)assert(this.service);
        final SecurityManager security = System.getSecurityManager();
        if ((security != null) && this.mustCheckPermissionsOnShutdown(interruption)) {
            security.checkPermission(modifyThreadPermission);
            this.mainLock.lock();
            try {
                // Possibly no subscriber yet, in case of shutdown
                // before subscribers registration.
                for (MyAbstractWorker worker : this.workers) {
                    final Thread runningThread = worker.runningThread;
                    if (runningThread != null) {
                        security.checkAccess(runningThread);
                    } else {
                        // Running thread not set yet,
                        // or subscriber runnable ended.
                    }
                }
            } finally {
                this.mainLock.unlock();
            }
        }
    }
    
    /**
     * Only for ring buffer services.
     * 
     * Called on shutdown, or on subscriber's runnable ending.
     * Doesn't terminate if ring buffer is not shutdown.
     */
    void terminateIfNeeded(boolean neverRunning) {
        if(ASSERTIONS)assert(this.service);
        if (neverRunning
                // Need to read number of started workers before
                // number of running workers, since otherwise they
                // could all start in the mean time.
                || ((this.nbrOfStartedWorkers.get() == this.nbrOfWorkersAtStart)
                        && (this.nbrOfRunningWorkers.get() == 0))) {
            
            // Order matters in case of concurrent calls.
            if (this.ringBufferState.compareAndSet(STATE_TERMINATE_ASAP, STATE_TERMINATE_BEING_CALLED)
                    || this.ringBufferState.compareAndSet(STATE_TERMINATE_WHEN_IDLE, STATE_TERMINATE_BEING_CALLED)) {
                try {
                    terminated();
                } finally {
                    this.ringBufferState.set(STATE_TERMINATED);
                    this.terminationCondilock.signalAllInLock();
                }
            }
        }
    }
}
