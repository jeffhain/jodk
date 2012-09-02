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
 * Interface for multicast or unicast ring buffer services.
 * 
 * Inspired by ExecutorService.
 */
public interface InterfaceRingBufferService extends InterfaceRingBuffer {
    
    /**
     * @param rejectedEventHandler Handler for events rejected on publish.
     *        Must not be null.
     */
    public void setRejectedEventHandler(final InterfaceRingBufferRejectedEventHandler rejectedEventHandler);

    /**
     * This getter makes it easy to use this handler on shutdownNow method's output.
     * 
     * @return Current rejected event handler (never null).
     */
    public InterfaceRingBufferRejectedEventHandler getRejectedEventHandler();
    
    /**
     * Initiates an orderly shutdown in which previously published
     * events are processed, but no new event will be accepted.
     * 
     * Depending on implementation, race between publishers and
     * workers might lead to events published after shut down
     * to be processed before they could be rejected by publishers.
     * 
     * Invocation has no additional effect if already shut down.
     *
     * This method does not wait for previously published events to
     * be processed. Use awaitTermination method to do that.
     */
    public void shutdown();

    /**
     * Attempts to stop all actively processing subscribers.
     *
     * This method does not wait for previously published events to
     * be processed. Use awaitTermination method to do that.
     *
     * There are no guarantees beyond best-effort attempts to stop
     * actively processing subscribers. For example, an implementation
     * can cancel via thread interruption, so any subscribers that
     * fails to respond to interrupts may never terminate.
     * 
     * If this ring buffer uses lazySet for writes, some already-published
     * events might fail to be rejected, and end up being neither rejected
     * nor processed. To make up for that, ensure that publisher threads
     * do a volatile write, or similar, after publishing their last sequence
     * before shut down.
     * 
     * @param interruptIfPossible If true, and implementation supports interruption,
     *        interrupts running workers threads. If false, and implementation can only
     *        disable through interruption, it still does interrupt running workers threads.
     * @return An array containing sequences ranges for published sequences which events
     *         won't have been processed (other that ranges provided to rejected event handler
     *         by publish methods), as (min1,max1,min2,max2,etc.), sorted by increasing
     *         sequences and non-contiguous, or null if no such sequence exist or if call
     *         to this method was not effective (due to not being the first one).
     */
    public long[] shutdownNow(boolean interruptIfPossible);
    
    /**
     * @return True if this ring buffer has been shut down, false otherwise.
     */
    public boolean isShutdown();

    /**
     * @return True if all workers have completed following shut down,
     *         and terminated() hook completed, false otherwise.
     */
    public boolean isTerminated();
    
    /**
     * Blocks until all workers have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is interrupted,
     * whichever happens first.
     *
     * @param timeout The maximum time to wait, in nanoseconds.
     * @return True if this ring buffer terminated before
     *         the specified timeout elapsed, false otherwise.
     * @throws InterruptedException if interrupted while waiting.
     */
    public boolean awaitTermination(long timeoutNS) throws InterruptedException;
}
