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
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MulticastRingBufferSample {

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MySubscriber implements InterfaceRingBufferSubscriber {
        private final String name;
        private final long[] events;
        private long readEvent = -1;
        public MySubscriber(
                final String name,
                final long[] events) {
            this.name = name;
            this.events = events;
        }
        @Override
        public void readEvent(long sequence, int index) {
            if ((sequence & 0xF) == 10) {
                throw new RuntimeException(this.name+" : readEvent trouble with "+sequence);
            }
            this.readEvent = this.events[index];
        }
        @Override
        public boolean processReadEvent() {
            System.out.println(this.name+" processing event "+this.readEvent);
            if ((this.readEvent & 0xF) == 11) {
                throw new RuntimeException(this.name+" : processReadEvent trouble with "+this.readEvent);
            }
            return false;
        }
        @Override
        public void onBatchEnd() {
            System.out.println(this.name+" batch end");
        }
    };

    //--------------------------------------------------------------------------
    // PUBLIC TREATMENTS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {

        System.out.println("--- "+MulticastRingBufferSample.class.getSimpleName()+"... ---");

        // Executor to run workers.
        final ExecutorService executor = new ThreadPoolExecutor(
                0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>()) {
            @Override
            public void execute(final Runnable command) {
                super.execute(new Runnable() {
                    public void run() {
                        while (true) {
                            try {
                                command.run();
                                if (Thread.interrupted()) {
                                    // Has been interrupted: we will run again
                                    // for worker to eventually make progress.
                                } else {
                                    // Completed normally and not interrupted:
                                    // we are done.
                                    // Note: if a subscriber asks for no more processing,
                                    // we will end up here and this will block the ring buffer
                                    // until worker is called again.
                                    break;
                                }
                            } catch (Exception e) {
                                // Handle exception here.
                                System.out.println("worker threw an exception : "+e.getMessage());
                                // Will run again, to make
                                // sure worker continues to work
                                // and does not block the ring buffer.
                            }
                        }
                    }
                });
            }
        };

        /*
         * Creating ring buffer.
         */

        // Smaller than the number of events, to have intermediate batches ends.
        final int bufferCapacity = 32;
        final boolean singlePublisher = true;
        final boolean singleSubscriber = false;
        final InterfaceRingBuffer ringBuffer = RingBuffers.newMulticastRingBuffer(
                bufferCapacity,
                singlePublisher,
                singleSubscriber);

        /*
         * Events buffer (Here, using sequences as events).
         * Could use multiple events arrays, for example
         * two arrays of longs if an event is two longs.
         */

        final long[] events = new long[bufferCapacity];

        /*
         * Creating workers.
         */

        final ArrayList<InterfaceRingBufferWorker> workers = new ArrayList<InterfaceRingBufferWorker>();

        // in parallel
        workers.add(ringBuffer.newWorker(new MySubscriber("S_1_1",events)));
        workers.add(ringBuffer.newWorker(new MySubscriber("S_1_2",events)));

        // after S_1_1 and S_1_2
        workers.add(ringBuffer.newWorker(new MySubscriber("S_2",events),workers.get(0),workers.get(1)));

        // in parallel after S_2
        workers.add(ringBuffer.newWorker(new MySubscriber("S_3_1",events),workers.get(2)));
        workers.add(ringBuffer.newWorker(new MySubscriber("S_3_2",events),workers.get(2)));

        /*
         * Multiple runs.
         */

        final int nbrOfRuns = 2;
        for (int k=0;k<nbrOfRuns;k++) {

            System.out.println("--- run "+(k+1)+" ---");

            final AtomicInteger nbrOfWorkersDone = new AtomicInteger();

            /*
             * Launching workers, with adapter to notify when all are done.
             */

            for (final InterfaceRingBufferWorker worker : workers) {
                // Need to reset in case worker state has been changed already.
                worker.reset();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        boolean interrupted = false;
                        try {
                            worker.runWorker();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                        if (interrupted) {
                            // Restoring interruption status
                            // (we handle interruptions in executor's
                            // wrapping runnable).
                            Thread.currentThread().interrupt();
                        } else {
                            if (nbrOfWorkersDone.incrementAndGet() == workers.size()) {
                                synchronized (nbrOfWorkersDone) {
                                    nbrOfWorkersDone.notifyAll();
                                }
                            }
                        }
                    }
                });
            }

            /*
             * Publishing events.
             */

            final int nbrOfEvents = 100;
            for (int i=0;i<nbrOfEvents;i++) {
                final long sequence = ringBuffer.claimSequence();
                if (sequence < 0) {
                    // Someone shut down publication
                    // (can't happen with our implementation).
                    // Stopping publication.
                    break;
                } else {
                    try {
                        final int index = ringBuffer.sequenceToIndex(sequence);
                        events[index] = sequence;
                    } finally {
                        ringBuffer.publish(sequence);
                    }
                }
            }

            /*
             * Telling workers to stop when idle.
             */

            for (InterfaceRingBufferWorker worker : workers) {
                worker.setStateTerminateWhenIdle();
            }

            /*
             * Waiting for workers to be done.
             */

            synchronized (nbrOfWorkersDone) {
                while (nbrOfWorkersDone.get() != workers.size()) {
                    try {
                        nbrOfWorkersDone.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            /*
             * 
             */

            System.out.flush();
        }

        /*
         * Shutting down executor service and waiting for it to be done,
         * to make sure everything is clean at the end.
         */

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE,TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("--- ..."+MulticastRingBufferSample.class.getSimpleName()+" ---");
    }
}
