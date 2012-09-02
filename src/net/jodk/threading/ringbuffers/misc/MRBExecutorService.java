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

import java.util.concurrent.Executor;

import net.jodk.threading.InterfaceRejectedExecutionHandler;
import net.jodk.threading.locks.InterfaceCondilock;
import net.jodk.threading.ringbuffers.AbortingRingBufferRejectedEventHandler;
import net.jodk.threading.ringbuffers.MulticastRingBufferService;

/**
 * An executor service based on a MulticastRingBuffer.
 * 
 * Only works with a single worker.
 * 
 * Might have a better throughput than URBExecutorService,
 * depending on use-case.
 * 
 * Adds some functionalities over RingBufferExecutorService,
 * such as terminated() hook and interruptWorkers() method.
 * 
 * See RingBufferExecutorService Javadoc for more info.
 */
public class MRBExecutorService extends RingBufferExecutorService {

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    /**
     * Sub-classing for terminated hook.
     */
    private static class MyMulticastRingBufferService extends MulticastRingBufferService {
        private MRBExecutorService executorService;
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
        public void setExecutorService(final MRBExecutorService executorService) {
            this.executorService = executorService;
        }
        @Override
        protected void terminated() {
            super.terminated();
            this.executorService.terminated();
        }
    }
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public MRBExecutorService(
            int bufferCapacity,
            boolean singlePublisher,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock,
            final Executor executor,
            //
            final InterfaceRejectedExecutionHandler rejectedExecutionHandler) {
        super(
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
        ((MyMulticastRingBufferService)this.ringBufferService).setExecutorService(this);
    }

    /**
     * Attempts to interrupt threads
     * that are currently running workers.
     */
    public void interruptWorkers() {
        ((MulticastRingBufferService)this.ringBufferService).interruptWorkers();
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Called outside main lock (unlike ThreadPoolExecutor.terminated()).
     * 
     * Can be called by shut down thread, or by a worker thread.
     * 
     * Method invoked just before the ring buffer state is set to terminated
     * (as done in ThreadPoolExecutor).
     */
    protected void terminated() {
    }
}
