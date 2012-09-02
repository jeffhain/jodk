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

public class MulticastRingBufferServiceSample {

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

        System.out.println("--- "+MulticastRingBufferServiceSample.class.getSimpleName()+"... ---");

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
         * Creating ring buffer service.
         */

        // Smaller than the number of events, to have intermediate batches ends.
        final int bufferCapacity = 32;
        final boolean singlePublisher = true;
        final boolean singleSubscriber = false;
        final InterfaceRingBufferService ringBuffer = RingBuffers.newMulticastRingBufferService(
                bufferCapacity,
                singlePublisher,
                singleSubscriber,
                executor);

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
         * Publishing events.
         */

        final int nbrOfEvents = 100;
        for (int i=0;i<nbrOfEvents;i++) {
            final long sequence = ringBuffer.claimSequence();
            if (sequence < 0) {
                // Someone did shut down the service
                // (doesn't happen in this sample).
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
         * Shutting down ring buffer service (will stop when idle)
         * and waiting for it to be done.
         */

        ringBuffer.shutdown();
        try {
            ringBuffer.awaitTermination(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        /*
         * 
         */

        System.out.flush();

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

        System.out.println("--- ..."+MulticastRingBufferServiceSample.class.getSimpleName()+" ---");
    }
}
