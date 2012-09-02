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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import net.jodk.lang.RethrowException;
import net.jodk.lang.Unchecked;

/**
 * Class to test or bench ring buffers a same way,
 * whether they are services or not.
 */
public class RingBufferTestHelper implements InterfaceRingBufferWorkerFactory {

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    protected final InterfaceRingBuffer ringBuffer;
    protected final ExecutorService workersExecutor;
    protected final boolean shutdownWorkersExecutor;

    private final ArrayList<InterfaceRingBufferWorker> workers = new ArrayList<InterfaceRingBufferWorker>();

    private final ArrayList<Future<Void>> workersFutures = new ArrayList<Future<Void>>();

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates a helper that shutdowns workers executor
     * on call to shutdownAndAwaitTermination().
     * 
     * @param ringBuffer Ring buffer (service or not).
     * @param workersExecutor Workers executor service.
     *        Must not be null.
     */
    public RingBufferTestHelper(
            final InterfaceRingBuffer ringBuffer,
            final ExecutorService workersExecutor) {
        this(
                ringBuffer,
                workersExecutor,
                true);
    }

    /**
     * @param ringBuffer Ring buffer (service or not).
     * @param workersExecutor Workers executor service.
     *        Can be null only if ring buffer is a service,
     *        and shutdownWorkersExecutor is false.
     * @param shutdownWorkersExecutor If true, workers executor
     *        is shutdown and its termination is waited on, at the
     *        end of shutdownAndAwaitTermination() method, after
     *        shutdown and termination of ring buffer service.
     */
    public RingBufferTestHelper(
            final InterfaceRingBuffer ringBuffer,
            final ExecutorService workersExecutor,
            boolean shutdownWorkersExecutor) {
        this.ringBuffer = ringBuffer;
        this.workersExecutor = workersExecutor;
        this.shutdownWorkersExecutor = shutdownWorkersExecutor;
    }

    public boolean isService() {
        return (this.ringBuffer instanceof InterfaceRingBufferService);
    }

    public InterfaceRingBuffer getRingBuffer() {
        return this.ringBuffer;
    }

    /**
     * @throws ClassCastException if the ring buffer is not a service.
     */
    public InterfaceRingBufferService getRingBufferService() {
        return asService(this.ringBuffer);
    }

    /**
     * Must create workers through this method,
     * not through backing's ring buffer's one.
     */
    @Override
    public InterfaceRingBufferWorker newWorker(
            InterfaceRingBufferSubscriber subscriber,
            InterfaceRingBufferWorker... aheadWorkers) {
        final InterfaceRingBufferWorker worker = this.ringBuffer.newWorker(subscriber, aheadWorkers);
        this.workers.add(worker);
        return worker;
    }

    public void startWorkersIfNonService() {
        if (this.ringBuffer instanceof InterfaceRingBufferService) {
            // workers will be started on publish
        } else {
            for (final InterfaceRingBufferWorker worker : this.workers) {
                this.workersFutures.add(
                        this.workersExecutor.submit(
                                new Callable<Void>() {
                                    @Override
                                    public Void call() throws InterruptedException {
                                        worker.runWorker();
                                        return null;
                                    }
                                }));
            }
        }
    }

    /**
     * Starts workers if ring buffer is not a service,
     * then publishes a dummy event to start workers
     * if ring buffer is a service, and then waits
     * a bit to make sure (hopefully) that all workers
     * are ready to process events (else some implementations
     * might be advantaged for benches depending on when workers
     * are ready (especially if benching with as many publishers
     * than subscribers, which could randomly cause ring buffer
     * to be sometimes rather empty and sometimes rather full)).
     * 
     * Dummy event is published even if ring buffer is not a service,
     * to make sure publications are identical in both cases.
     */
    public void startWorkersForBench() {
        this.startWorkersIfNonService();

        /*
         * Publishing dummy event event if workers are run
         * on user threads, to make sure all ring buffers
         * are treated equally regarding event publishing.
         */

        final boolean dummyEvent = true;
        if (dummyEvent) {
            final long dummySeq = ringBuffer.claimSequence();
            if (dummySeq < 0) {
                throw new RuntimeException("dummySeq = "+dummySeq);
            } else {
                ringBuffer.publish(dummySeq);
            }
        }

        /*
         * Should be enough for worker threads to be started.
         */

        Unchecked.sleepMS(100);
    }

    public void setWorkersStateTerminateWhenIdle() {
        this.setWorkersStateTerminateWhenIdle(false);
    }
    
    public void setWorkersStateTerminateWhenIdle(boolean yieldBetweenWorkers) {
        for (InterfaceRingBufferWorker worker : this.workers) {
            worker.setStateTerminateWhenIdle();
            if (yieldBetweenWorkers) {
                // Yielding, which can help cause trouble (for tests).
                Thread.yield();
            }
        }
    }

    public void setWorkersStateTerminateASAP() {
        this.setWorkersStateTerminateASAP(false);
    }

    public void setWorkersStateTerminateASAP(boolean yieldBetweenWorkers) {
        for (InterfaceRingBufferWorker worker : this.workers) {
            worker.setStateTerminateASAP();
            if (yieldBetweenWorkers) {
                // Yielding, which can help cause trouble (for tests).
                Thread.yield();
            }
        }
    }

    public void shutdownAndAwaitTermination() {
        if (this.ringBuffer instanceof InterfaceRingBufferService) {
            asService(this.ringBuffer).shutdown();
            try {
                asService(this.ringBuffer).awaitTermination(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                throw new RethrowException(e);
            }
        } else {
            this.setWorkersStateTerminateWhenIdle();
            for (Future<Void> future : this.workersFutures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    throw new RethrowException(e);
                } catch (ExecutionException e) {
                    throw new RethrowException(e);
                }
            }
        }
        if (this.shutdownWorkersExecutor) {
            Unchecked.shutdownAndAwaitTermination(this.workersExecutor);
        }
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private static InterfaceRingBufferService asService(final InterfaceRingBuffer ringBuffer) {
        return (InterfaceRingBufferService)ringBuffer;
    }
}
