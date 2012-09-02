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

/**
 * Interface for whatever treatment that creates workers
 * for a ring buffer.
 */
public interface InterfaceRingBufferWorkerFactory {

    /*
     * Asking for ahead workers, and not ahead subscribers,
     * for in case of non-service one typically wants to
     * at least keep track of the workers, so they should
     * be available from some collection.
     */
    
    /**
     * Method to create a worker for a specified subscriber,
     * and eventually specify ahead subscribers.
     * 
     * @param subscriber A subscriber.
     * @param aheadWorkers Workers that must process events before the specified subscriber.
     *        Mostly relevant for multicast ring buffers.
     * @return Worker created for the specified subscriber.
     * @throws IllegalStateException if this factory can't accept
     *         new subscribers.
     * @throws IllegalArgumentException if a worker has already been
     *         created by this factory for the specified subscriber, or
     *         if an ahead worker is unknown for this factory, or if ahead
     *         workers are specified and this factory doesn't support them.
     * @throws UnsupportedOperationException if this factory can't create more
     *         workers.
     */
    public InterfaceRingBufferWorker newWorker(
            final InterfaceRingBufferSubscriber subscriber,
            final InterfaceRingBufferWorker... aheadWorkers);
}
