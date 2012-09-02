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

import net.jodk.threading.locks.Condilocks;

/**
 * Provides static methods to create ring buffers.
 * 
 * See ring buffers constructors for arguments description.
 */
public class RingBuffers {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /*
     * multicast
     */
    
    /**
     * @return A non-service multicast ring buffer, using lazy sets.
     */
    public static InterfaceRingBuffer newMulticastRingBuffer(
            int bufferCapacity,
            boolean singlePublisher,
            boolean singleSubscriber) {
        final boolean readLazySets = true;
        final boolean writeLazySets = true;
        return new MulticastRingBuffer(
                bufferCapacity,
                singlePublisher,
                singleSubscriber,
                readLazySets,
                writeLazySets,
                Condilocks.newSmartCondilock(readLazySets),
                Condilocks.newSmartCondilock(writeLazySets));
    }

    /**
     * @return A multicast ring buffer service, using lazy sets for reads.
     */
    public static InterfaceRingBufferService newMulticastRingBufferService(
            int bufferCapacity,
            boolean singlePublisher,
            boolean singleSubscriber,
            Executor executor) {
        final boolean readLazySets = true;
        // False else might miss rejections.
        final boolean writeLazySets = false;
        return new MulticastRingBufferService(
                bufferCapacity,
                singlePublisher,
                singleSubscriber,
                readLazySets,
                writeLazySets,
                Condilocks.newSmartCondilock(readLazySets),
                Condilocks.newSmartCondilock(writeLazySets),
                executor,
                new AbortingRingBufferRejectedEventHandler());
    }
    
    /*
     * unicast
     */

    /**
     * @return A non-service unicast ring buffer, using lazy sets.
     */
    public static InterfaceRingBuffer newUnicastRingBuffer(
            int bufferCapacity,
            boolean singlePublisher,
            boolean singleSubscriber) {
        final boolean readLazySets = true;
        final boolean writeLazySets = true;
        return new UnicastRingBuffer(
                bufferCapacity,
                (singlePublisher ? 0 : 1),
                singleSubscriber,
                readLazySets,
                writeLazySets,
                Condilocks.newSmartCondilock(readLazySets),
                Condilocks.newSmartCondilock(writeLazySets));
    }

    /**
     * @return A unicast ring buffer service, using lazy sets for reads.
     */
    public static InterfaceRingBufferService newUnicastRingBufferService(
            int bufferCapacity,
            boolean singlePublisher,
            boolean singleSubscriber,
            Executor executor) {
        final boolean readLazySets = true;
        // False else might miss rejections.
        final boolean writeLazySets = false;
        return new UnicastRingBufferService(
                bufferCapacity,
                (singlePublisher ? 0 : 1),
                singleSubscriber,
                readLazySets,
                writeLazySets,
                Condilocks.newSmartCondilock(readLazySets),
                Condilocks.newSmartCondilock(writeLazySets),
                executor,
                new AbortingRingBufferRejectedEventHandler());
    }
}
