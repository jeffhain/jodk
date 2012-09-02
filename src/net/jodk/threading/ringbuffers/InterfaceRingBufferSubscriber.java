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
 * Event read and processing is done in two different methods,
 * which allows for subscribers not to block events flow
 * of unicast ring buffers if event processing takes time.
 * This makes our unicast ring buffers differ from LMAX's WorkProcessor,
 * for which a blocking worker blocks the whole ring buffer.
 */
public interface InterfaceRingBufferSubscriber {

    /**
     * Must be as quick as possible.
     */
    public void readEvent(long sequence, int index);
    
    /**
     * Called after each call to readEvent that did not throw an exception.
     * 
     * @return True if this subscriber asks to stop processing events
     *         after this one (and until a new run of the worker),
     *         false otherwise.
     */
    public boolean processReadEvent();

    /**
     * Called before each wait (busy or not) for an event, or in case of
     * exceptional worker termination, if at least one readEvent(...)
     * did complete normally (since previous onBatchEnd() call if any).
     * (If calling onBatchEnd() only after a normal completion of processReadEvent(),
     * users that want it called even if their functional code throws, might want
     * to put it in readEvent(...), or call onBatchEnd() themselves. Those that don't
     * want it called if their code throws, can just ignore the call if didn't complete
     * normally since last call.)
     * 
     * Note that if events publication rate is high enough,
     * subscribers rarely have to wait for an event, and this
     * method might not be called for a long time, if ever
     * until worker completes.
     * This behavior differs from LMAX's design, where batch end
     * happens each time a worker processed up to the max available
     * sequence it previously computed.
     * 
     * If some treatment, like a flush, must be done after a number of events,
     * it's better not to rely on this method, or to call it explicitly
     * in processReadEvent() after a number of calls.
     */
    public void onBatchEnd();
}
