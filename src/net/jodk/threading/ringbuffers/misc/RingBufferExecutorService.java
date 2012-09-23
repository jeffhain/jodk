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
import java.util.concurrent.TimeUnit;

import net.jodk.lang.NumbersUtils;
import net.jodk.threading.InterfaceRejectedExecutionHandler;
import net.jodk.threading.ringbuffers.InterfaceRingBuffer;
import net.jodk.threading.ringbuffers.InterfaceRingBufferRejectedEventHandler;
import net.jodk.threading.ringbuffers.InterfaceRingBufferService;
import net.jodk.threading.ringbuffers.InterfaceRingBufferSubscriber;
import net.jodk.threading.ringbuffers.RingBuffersUtils;

/**
 * Executor service based on a ring buffer.
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
        final Runnable[] eventByIndex;
        private Runnable runnable;
        public MySubscriber(final Runnable[] eventByIndex) {
            this.eventByIndex = eventByIndex;
        }
        @Override
        public void readEvent(long sequence, int index) {
            this.runnable = this.eventByIndex[index];
            this.eventByIndex[index] = null;
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
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    /**
     * As done in ThreadPoolExecutor.
     */
    private static final boolean TRY_INTERRUPT_ON_SHUTDOWN = true;
    
    protected final InterfaceRingBufferService ringBufferService;
    
    private final Runnable[] eventByIndex;
    
    /**
     * Never null after construction.
     */
    private volatile InterfaceRejectedExecutionHandler rejectedExecutionHandler;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates an executor service based on the specified ring buffer service.
     * 
     * Sets rejected event handler in the specified ring buffer service.
     * Behavior is undefined if another rejected event handler is set afterward.
     */
    public RingBufferExecutorService(
            final InterfaceRingBufferService ringBufferService,
            int nbrOfWorkers,
            final InterfaceRejectedExecutionHandler rejectedExecutionHandler) {
        if (nbrOfWorkers < 0) {
            throw new IllegalArgumentException("number of workers ["+nbrOfWorkers+"] must be >= 0");
        }
        ringBufferService.setRejectedEventHandler(newRejectedEventHandler());
        this.setRejectedExecutionHandler(rejectedExecutionHandler);
        this.ringBufferService = ringBufferService;
        final int capacity = ringBufferService.getBufferCapacity();
        final Runnable[] eventByIndex = new Runnable[capacity];
        this.eventByIndex = eventByIndex;
        for (int i=0;i<nbrOfWorkers;i++) {
            ringBufferService.newWorker(new MySubscriber(eventByIndex));
        }
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

    /*
     * 
     */
    
    /**
     * @param runnable Runnable to run.
     * @return True if execution has been planned (but rejection
     *         is still possible), false otherwise.
     * @throws NullPointerException if the specified Runnable is null.
     */
    public boolean tryExecute(Runnable runnable) {
        if (runnable == null) {
            throw new NullPointerException();
        }
        final InterfaceRingBufferService ringBuffer = this.ringBufferService;
        final long sequence = ringBuffer.tryClaimSequence();
        if (sequence < 0) {
            // Full or shut down.
            return false;
        } else {
            try {
                final int index = ringBuffer.sequenceToIndex(sequence);
                this.eventByIndex[index] = runnable;
            } finally {
                ringBuffer.publish(sequence);
            }
            return true;
        }
    }

    /**
     * Blocks until timeout, or room, or shut down.
     * 
     * @param runnable Runnable to run.
     * @param timeoutNS Timeout, in nanoseconds, for this sequence claim.
     * @return True if execution has been planned (but rejection
     *         is still possible), false otherwise.
     * @throws NullPointerException if the specified Runnable is null.
     * @throws InterruptedException if current thread is interrupted,
     *         possibly unless this treatment doesn't actually wait.
     */
    public boolean tryExecute(Runnable runnable, long timeoutNS) throws InterruptedException {
        if (runnable == null) {
            throw new NullPointerException();
        }
        final InterfaceRingBufferService ringBuffer = this.ringBufferService;
        final long sequence = ringBuffer.tryClaimSequence(timeoutNS);
        if (sequence < 0) {
            // Full or shut down.
            return false;
        } else {
            try {
                final int index = ringBuffer.sequenceToIndex(sequence);
                this.eventByIndex[index] = runnable;
            } finally {
                ringBuffer.publish(sequence);
            }
            return true;
        }
    }

    /**
     * Blocks uninterruptibly, until room or shut down
     * (but might block even on shut down, depending
     * on ring buffer implementation, until some
     * workers make some progress).
     * 
     * @param runnable Runnable to run.
     * @throws NullPointerException if the specified Runnable is null.
     */
    @Override
    public void execute(Runnable runnable) {
        if (runnable == null) {
            throw new NullPointerException();
        }
        final InterfaceRingBufferService ringBuffer = this.ringBufferService;
        final long sequence = ringBuffer.claimSequence();
        if (sequence < 0) {
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
    
    /*
     * 
     */

    @Override
    public void shutdown() {
        this.ringBufferService.shutdown();
    }

    /**
     * Always returns a mutable list.
     */
    @Override
    public List<Runnable> shutdownNow() {
        final long[] abortedSequencesRanges = this.ringBufferService.shutdownNow(TRY_INTERRUPT_ON_SHUTDOWN);
        if (abortedSequencesRanges != null) {
            final int nbrOfRanges = RingBuffersUtils.computeNbrOfRanges(abortedSequencesRanges);
            final int nbrOfRunnables = NumbersUtils.asInt(RingBuffersUtils.computeNbrOfSequences(abortedSequencesRanges));
            final ArrayList<Runnable> list = new ArrayList<Runnable>(nbrOfRunnables);
            for (int i=0;i<nbrOfRanges;i++) {
                final long min = abortedSequencesRanges[2*i];
                final long max = abortedSequencesRanges[2*i+1];
                for (long seq=min;seq<=max;seq++) {
                    final int index = this.ringBufferService.sequenceToIndex(seq);
                    final Runnable runnable = this.eventByIndex[index];
                    this.eventByIndex[index] = null;
                    if(ASSERTIONS)assert(runnable != null);
                    list.add(runnable);
                }
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
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

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
