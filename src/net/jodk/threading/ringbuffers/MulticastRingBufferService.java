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
 * In case of multiple publishers, a sequence might be published
 * before shutdown, and still be rejected, if its actual publication
 * occurs after shutdown, serialized with the publication of a lower
 * sequence published after shutdown.
 * 
 * If using lazy sets, an event might not be seen as published
 * on shutdown, and fail to be processed or rejected.
 * A work around is to make sure publisher threads flush their
 * writes into main memory before proceeding to shutdown.
 * 
 * On shut down, all workers are ensured to process up to the same
 * sequence (unless one throws and doesn't get called again, which
 * blocks the termination process).
 * To do that, shutdown() and shutdownNow(...) methods might block waiting
 * for workers to progress up to a same point, so they should not be called
 * from subscribers, which could cause dead locks.
 * 
 * See MulticastRingBuffer and AbstractRingBuffer Javadoc for more info.
 */
public class MulticastRingBufferService extends MulticastRingBuffer implements InterfaceRingBufferService {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @param bufferCapacity Buffer capacity. Must be a power of two.
     * @param singlePublisher True if single publisher, false otherwise.
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
    public MulticastRingBufferService(
            int bufferCapacity,
            boolean singlePublisher,
            boolean singleSubscriber,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock,
            final Executor executor,
            final InterfaceRingBufferRejectedEventHandler rejectedEventHandler) {
        super(
                true, // service
                //
                bufferCapacity,
                singlePublisher,
                singleSubscriber,
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
