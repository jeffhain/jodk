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

import net.jodk.lang.IntWrapper;

/**
 * Interface for publishing events in a ring buffer.
 * 
 * Methods defined aside from ring buffer interface,
 * to allow for ring buffers to provide optimized
 * non-thread-safe publish ports.
 */
public interface InterfaceRingBufferPublishPort {
    
    /*
     * Batch claim/publish methods only deal with contiguous sequences,
     * for simplicity.
     */
    
    /*
     * sequenceToIndex method should behave identically
     * for all publish ports of a same ring buffer,
     * but it is defined here for convenience,
     * since it is typically used at publishing.
     */
    
    /**
     * @param sequence A sequence (>= 0).
     * @return Index (in [0,bufferCapacity-1]) corresponding to the specified sequence.
     */
    public int sequenceToIndex(long sequence);
    
    /*
     * claiming
     * 
     * Allowing shut down ring buffer services to return a sequence < 0 (non-valid),
     * otherwise they could wait forever for an acquired sequence's slot to become
     * available for publishing; but also allowing them to still provide valid
     * sequences when shut down, for their events shall be rejected on publish
     * anyway.
     * As a result, if the ring buffer is a service, you might want to check
     * for shut down status whenever a claim method returns a sequence < 0.
     */
    
    /**
     * @return Claimed sequence (>= 0), or -1 if no sequence was available,
     *         or -2 if the ring buffer is shut down (optional: an implementation
     *         is allowed to claim even if shut down, since sequence can still
     *         be rejected on publish).
     */
    public long tryClaimSequence();
    
    /**
     * @param timeoutNS Timeout, in nanoseconds, for this sequence claim.
     * @return Claimed sequence (>= 0), or -1 if no sequence was available
     *         before the specified timeout elapsed, or -2 if the ring buffer
     *         is shut down (optional: an implementation is allowed to claim
     *         even if shut down, since sequence can still be rejected on publish).
     * @throws InterruptedException if current thread is interrupted,
     *         possibly unless this treatment doesn't actually wait.
     */
    public long tryClaimSequence(long timeoutNS) throws InterruptedException;

    /**
     * Blocks uninterruptibly until a sequence can be claimed,
     * or until shut down.
     * For interruptible claiming, use tryClaimSequence(Long.MAX_VALUE) instead.
     * 
     * @return Claimed sequence (>= 0), or -2 if the ring buffer
     *         is shut down.
     */
    public long claimSequence();
    
    /**
     * Blocks uninterruptibly until at least one sequence,
     * or the desired amount of sequence (up to the implementation),
     * can be claimed, or until shut down.
     * 
     * Note: Using a too large batch size, with respect to ring buffer capacity
     * and the number of publishers, can lower the throughput, by not
     * allowing some publisher threads to publish in parallel, i.e. making
     * publishing more sequential.
     * 
     * @param nbrOfSequences (in/out) Desired number of sequences,
     *        eventually reduced depending on available sequences.
     *        For consistency, output value must be 0 if returned
     *        value is < 0.
     * @return Min claimed sequence (>= 0), max being (min+(nbrOfSequences(out)-1)),
     *         or -2 if the ring buffer is shut down.
     */
    public long claimSequences(IntWrapper nbrOfSequences);

    /*
     * publishing
     */
    
    /**
     * @param sequence Sequence (>= 0) to publish.
     */
    public void publish(long sequence);

    /**
     * @param minSequence Min sequence (>= 0) to publish.
     * @param nbrOfSequences Number (>= 0) of consecutive sequences to publish.
     */
    public void publish(
            long minSequence,
            int nbrOfSequences);
}
