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
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import net.jodk.lang.NumbersUtils;
import net.jodk.threading.InterfaceRejectedExecutionHandler;
import net.jodk.threading.locks.InterfaceCondilock;
import net.jodk.threading.ringbuffers.AbortingRingBufferRejectedEventHandler;
import net.jodk.threading.ringbuffers.InterfaceRingBuffer;
import net.jodk.threading.ringbuffers.InterfaceRingBufferRejectedEventHandler;
import net.jodk.threading.ringbuffers.InterfaceRingBufferService;
import net.jodk.threading.ringbuffers.InterfaceRingBufferSubscriber;
import net.jodk.threading.ringbuffers.MulticastRingBufferService;
import net.jodk.threading.ringbuffers.RingBuffersUtils;
import net.jodk.threading.ringbuffers.UnicastRingBufferService;

/**
 * An executor service based on a ring buffer.
 * 
 * If the ring buffer is full, execute(Runnable) method
 * blocks uninterruptibly, which can cause deadlocks,
 * for example if this executor is being used from its
 * own threads.
 */
public class RingBufferExecutorService extends AbstractExecutorService {
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean ASSERTIONS = false;

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    private static class MySubscriber implements InterfaceRingBufferSubscriber {
        private final Runnable[] eventByIndex;
        private Runnable runnable;
        public MySubscriber(final Runnable[] eventByIndex) {
            this.eventByIndex = eventByIndex;
        }
        @Override
        public void readEvent(long sequence, int eventIndex) {
            this.runnable = this.eventByIndex[eventIndex];
            this.eventByIndex[eventIndex] = null;
        }
        @Override
        public boolean processReadEvent() {
            final Runnable runnable = this.runnable;
            // Nullification before run, in case of exception.
            this.runnable = null;
            runnable.run();
            return false;
        }
        @Override
        public void onBatchEnd() {
        }
    }
    
    /**
     * Sub-classing for terminated hook.
     */
    private static class MyUnicastRingBufferService extends UnicastRingBufferService {
        private RingBufferExecutorService parent;
        public MyUnicastRingBufferService(
                int bufferCapacity,
                int pubSeqNbrOfAtomicCounters,
                boolean singleSubscriber,
                boolean readLazySets,
                boolean writeLazySets,
                InterfaceCondilock readWaitCondilock,
                InterfaceCondilock writeWaitCondilock,
                Executor executor) {
            super(
                    bufferCapacity,
                    pubSeqNbrOfAtomicCounters,
                    singleSubscriber,
                    readLazySets,
                    writeLazySets,
                    readWaitCondilock,
                    writeWaitCondilock,
                    executor,
                    new AbortingRingBufferRejectedEventHandler());
        }
        public void setParent(final RingBufferExecutorService parent) {
            this.parent = parent;
        }
        @Override
        protected void terminated() {
            super.terminated();
            this.parent.terminated();
        }
    }

    /**
     * Sub-classing for terminated hook.
     */
    private static class MyMulticastRingBufferService extends MulticastRingBufferService {
        private RingBufferExecutorService parent;
        public MyMulticastRingBufferService(
                int bufferCapacity,
                boolean singlePublisher,
                boolean readLazySets,
                boolean writeLazySets,
                InterfaceCondilock readWaitCondilock,
                InterfaceCondilock writeWaitCondilock,
                Executor executor) {
            super(
                    bufferCapacity,
                    singlePublisher,
                    true, // singleSubscriber
                    readLazySets,
                    writeLazySets,
                    readWaitCondilock,
                    writeWaitCondilock,
                    executor,
                    new AbortingRingBufferRejectedEventHandler());
        }
        public void setParent(final RingBufferExecutorService parent) {
            this.parent = parent;
        }
        @Override
        protected void terminated() {
            super.terminated();
            this.parent.terminated();
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    /**
     * As done in ThreadPoolExecutor.
     */
    private static final boolean TRY_INTERRUPT_ON_SHUTDOWN = true;
    
    private final InterfaceRingBufferService ringBufferService;
    
    private final Runnable[] eventByIndex;
    
    /**
     * Never null after construction.
     */
    private volatile InterfaceRejectedExecutionHandler rejectedExecutionHandler;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates an executor service based on a unicast ring buffer service.
     */
    public RingBufferExecutorService(
            int bufferCapacity,
            int pubSeqNbrOfAtomicCounters,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock,
            final Executor executor,
            //
            int nbrOfWorkers,
            final InterfaceRejectedExecutionHandler rejectedExecutionHandler) {
        this(
                new MyUnicastRingBufferService(
                        bufferCapacity,
                        pubSeqNbrOfAtomicCounters,
                        (nbrOfWorkers == 1), // singleSubscriber
                        readLazySets,
                        writeLazySets,
                        readWaitCondilock,
                        writeWaitCondilock,
                        executor),
                        //
                        nbrOfWorkers,
                        rejectedExecutionHandler);
    }

    /**
     * Creates an executor service based on a multicast ring buffer service,
     * with only a single worker.
     * 
     * Typically has a better throughput than unicast-based executor,
     * in case of many publishers (and single worker), due to multicast
     * ring buffer's batch effect.
     */
    public RingBufferExecutorService(
            int bufferCapacity,
            boolean singlePublisher,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock,
            final Executor executor,
            //
            final InterfaceRejectedExecutionHandler rejectedExecutionHandler) {
        this(
                new MyMulticastRingBufferService(
                        bufferCapacity,
                        singlePublisher,
                        readLazySets,
                        writeLazySets,
                        readWaitCondilock,
                        writeWaitCondilock,
                        executor),
                        //
                        1, // nbrOfWorkers
                        rejectedExecutionHandler);
    }

    /**
     * @return Current rejected execution handler (never null).
     */
    public InterfaceRejectedExecutionHandler getRejectedExecutionHandler() {
        return this.rejectedExecutionHandler;
    }

    /**
     * @param rejectedExecutionHandler Must not be null.
     */
    public void setRejectedExecutionHandler(final InterfaceRejectedExecutionHandler rejectedExecutionHandler) {
        if (rejectedExecutionHandler == null) {
            throw new NullPointerException();
        }
        this.rejectedExecutionHandler = rejectedExecutionHandler;
    }
    
    /**
     * Blocks uninterruptibly, until room or shut down.
     */
    @Override
    public void execute(Runnable runnable) {
        final InterfaceRingBufferService ringBuffer = this.ringBufferService;
        final long sequence = ringBuffer.claimSequence();
        if (sequence < 0) {
            // Shut down.
            this.rejectedExecutionHandler.onRejectedExecution(this, runnable);
        } else {
            try {
                final int index = ringBuffer.sequenceToIndex(sequence);
                this.eventByIndex[index] = runnable;
            } finally {
                ringBuffer.publish(sequence);
            }
        }
    }

    @Override
    public void shutdown() {
        this.ringBufferService.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        final long[] abortedSequencesRanges = this.ringBufferService.shutdownNow(TRY_INTERRUPT_ON_SHUTDOWN);
        if (abortedSequencesRanges != null) {
            final int nbrOfRunnables = NumbersUtils.asInt(RingBuffersUtils.computeNbrOfSequences(abortedSequencesRanges));
            final ArrayList<Runnable> list = new ArrayList<Runnable>(nbrOfRunnables);
            final long min = abortedSequencesRanges[0];
            final long max = abortedSequencesRanges[1];
            for (long seq=min;seq<=max;seq++) {
                final int index = this.ringBufferService.sequenceToIndex(seq); 
                final Runnable runnable = this.eventByIndex[index];
                this.eventByIndex[index] = null;
                if(ASSERTIONS)assert(runnable != null);
                list.add(runnable);
            }
            return list;
        } else {
            return new ArrayList<Runnable>(0);
        }
    }

    @Override
    public boolean isShutdown() {
        return this.ringBufferService.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return this.ringBufferService.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return this.ringBufferService.awaitTermination(unit.toNanos(timeout));
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    /**
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
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Internal constructor.
     * 
     * Sets parent and rejected event handler into the specified ring buffer service.
     * 
     * @param ringBufferService Ring buffer service.
     * @param nbrOfWorkers Number of workers to create.
     * @param rejectedExecutionHandler Must not be null.
     */
    private RingBufferExecutorService(
            final InterfaceRingBufferService ringBufferService,
            //
            int nbrOfWorkers,
            final InterfaceRejectedExecutionHandler rejectedExecutionHandler) {
        if (nbrOfWorkers < 0) {
            throw new IllegalArgumentException("number of workers ["+nbrOfWorkers+"] must be >= 0");
        }
        if (ringBufferService instanceof MyUnicastRingBufferService) {
            ((MyUnicastRingBufferService)ringBufferService).setParent(this);
        } else {
            ((MyMulticastRingBufferService)ringBufferService).setParent(this);
        }
        ringBufferService.setRejectedEventHandler(newRejectedEventHandler());
        this.setRejectedExecutionHandler(rejectedExecutionHandler);
        this.ringBufferService = ringBufferService;
        final Runnable[] eventByIndex = new Runnable[ringBufferService.getBufferCapacity()];
        this.eventByIndex = eventByIndex;
        for (int i=0;i<nbrOfWorkers;i++) {
            ringBufferService.newWorker(new MySubscriber(eventByIndex));
        }
    }

    /**
     * For rejections on publish.
     */
    private InterfaceRingBufferRejectedEventHandler newRejectedEventHandler() {
        return new InterfaceRingBufferRejectedEventHandler() {
            @Override
            public void onRejectedEvents(
                    final InterfaceRingBuffer ringBuffer,
                    final long... sequencesRanges) {
                if(ASSERTIONS)assert(ringBuffer == ringBufferService);
                rejectRunnables(sequencesRanges);
            }
        };
    }
    
    private void rejectRunnables(final long... sequencesRanges) {
        final int nbrOfRanges = RingBuffersUtils.computeNbrOfRanges(sequencesRanges);
        final int nbrOfRunnables = NumbersUtils.asInt(RingBuffersUtils.computeNbrOfSequences(sequencesRanges));
        final Runnable[] runnables = new Runnable[nbrOfRunnables];
        int arrayIndex = 0;
        for (int i=0;i<nbrOfRanges;i++) {
            final long min = sequencesRanges[2*i];
            final long max = sequencesRanges[2*i+1];
            for (long seq=min;seq<=max;seq++) {
                final int index = this.ringBufferService.sequenceToIndex(seq);
                final Runnable runnable = this.eventByIndex[index];
                this.eventByIndex[index] = null;
                if(ASSERTIONS)assert(runnable != null);
                runnables[arrayIndex++] = runnable;
            }
        }
        this.rejectedExecutionHandler.onRejectedExecution(this, runnables);
    }
}
