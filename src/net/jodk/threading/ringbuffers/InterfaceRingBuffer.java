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
 * Interface for multicast or unicast ring buffers.
 */
public interface InterfaceRingBuffer extends InterfaceRingBufferPublishPort, InterfaceRingBufferWorkerFactory {

    /**
     * Events indexes are in [0,bufferCapacity[.
     * 
     * @return The number of events that can be pending (published
     *         but not yet processed) in this ring buffer.
     */
    public int getBufferCapacity();
    
    /**
     * This method is often useful because of Java not allowing
     * (or at least not explicitly and deterministically allowing)
     * to create temporary objects on the stack, but it might also
     * be useful for other reasons, such as holding a local state
     * in the publish port.
     * 
     * @return A non thread-safe publish port, which implementation
     *         might be faster than ring buffer's main one.
     */
    public InterfaceRingBufferPublishPort newLocalPublishPort();
}
