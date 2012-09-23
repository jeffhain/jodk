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

import java.util.concurrent.Executor;

import net.jodk.threading.locks.InterfaceCondilock;

/**
 * Unicast ring buffer service, using threads of a specified Executor.
 * 
 * If using lazy sets, an event might not be seen as published
 * on shutdown, and fail to be processed or rejected.
 * A work around is to make sure publisher threads flush their
 * writes into main memory before proceeding to shutdown.
 * 
 * See UnicastRingBuffer and AbstractRingBuffer Javadoc for more info.
 */
public class UnicastRingBufferService extends UnicastRingBuffer implements InterfaceRingBufferService {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @param bufferCapacity Buffer capacity. Must be a power of two.
     * @param pubSeqNbrOfAtomicCounters Number of atomic counters for publication sequencer,
     *        which is used to claim sequences before publication.
     *        Use 0 for single-publisher, 1 for multi-publisher with monotonic claim,
     *        or a power of two >= 2 for non-monotonic claim.
     *        For non-monotonic claim, can help to have this value larger
     *        (like twice) than actual parallelism.
     * @param singleSubscriber True if single subscriber, false otherwise.
     * @param readLazySets If true, lazySet is used to indicate that a sequence has been read,
     *        which implies that readWaitCondilock must handle possible delays between the
     *        lazySet and its visibility of the new volatile value, which typically implies
     *        waking-up from wait from time to time to re-check the value.
     * @param writeLazySets Same as for readLazySets, but when indicating that a sequence
     *        has been written, and must be handled by writeWaitCondilock.
     * @param readWaitCondilock Condilock to wait for a sequence to be read.
     *        Mostly used by publishers.
     * @param writeWaitCondilock Condilock to wait for a sequence to be written.
     *        Mostly used by subscribers.
     * @param executor Executor in which workers are run. Must provide enough threads.
     * @param rejectedEventHandler Must not be null.
     */
    public UnicastRingBufferService(
            int bufferCapacity,
            int pubSeqNbrOfAtomicCounters,
            boolean singleSubscriber,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock,
            final Executor executor,
            final InterfaceRingBufferRejectedEventHandler rejectedEventHandler) {
        super(
                bufferCapacity,
                pubSeqNbrOfAtomicCounters,
                singleSubscriber,
                true, // service
                readLazySets,
                writeLazySets,
                readWaitCondilock,
                writeWaitCondilock,
                executor,
                rejectedEventHandler);
    }

    /**
     * Attempts to interrupt threads
     * that are currently running workers.
     */
    public void interruptWorkers() {
        this.interruptWorkersImpl();
    }

    @Override
    public void setRejectedEventHandler(final InterfaceRingBufferRejectedEventHandler rejectedEventHandler) {
        this.setRejectedEventHandlerImpl(rejectedEventHandler);
    }

    @Override
    public InterfaceRingBufferRejectedEventHandler getRejectedEventHandler() {
        return this.getRejectedEventHandlerImpl();
    }

    @Override
    public void shutdown() {
        this.shutdownImpl();
    }

    /**
     * @param interruptIfPossible If true, attempts to interrupt running workers.
     */
    @Override
    public long[] shutdownNow(boolean interruptIfPossible) {
        return this.shutdownNowImpl(interruptIfPossible);
    }

    @Override
    public boolean isShutdown() {
        return this.isShutdownImpl();
    }

    @Override
    public boolean isTerminated() {
        return this.isTerminatedImpl();
    }

    @Override
    public boolean awaitTermination(long timeoutNS) throws InterruptedException {
        return this.awaitTerminationImpl(timeoutNS);
    }
}
