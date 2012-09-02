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
 * Interface to handle rejected events, called in publish methods,
 * or by user on the output of shutdownNow method.
 * 
 * If the ring buffer uses lazySet for writes, some already-published
 * events might fail to be rejected, and end being up neither rejected
 * nor processed. To make up for that, ensure that publisher threads
 * do a volatile write, or similar, after publishing their last sequence
 * before shut down.
 */
public interface InterfaceRingBufferRejectedEventHandler {

    /*
     * Rejections could be implemented in non-service
     * ring buffers, that's why we use interface for
     * basic ring buffers.
     */
    
    /**
     * In the absence of other alternatives, this method may throw
     * a RejectedEventException.
     *
     * @param ringBuffer The ring buffer from which events corresponding
     *        to the specified sequences have been rejected.
     * @param sequencesRanges Rejected sequences ranges, as
     *        {min1,max1,min2,max2,etc.}, sorted by increasing
     *        sequences and non-contiguous.
     * @throws RejectedEventException if there is no remedy.
     */
    public void onRejectedEvents(
            final InterfaceRingBuffer ringBuffer,
            final long... sequencesRanges);
}
