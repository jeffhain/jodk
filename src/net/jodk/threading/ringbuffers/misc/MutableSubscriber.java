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

import net.jodk.threading.PostPaddedAtomicReference;
import net.jodk.threading.ringbuffers.InterfaceRingBufferSubscriber;

/**
 * Subscriber wrapper that allows to dynamically change subscriber.
 */
public class MutableSubscriber implements InterfaceRingBufferSubscriber {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private final PostPaddedAtomicReference<InterfaceRingBufferSubscriber> subscriber =
            new PostPaddedAtomicReference<InterfaceRingBufferSubscriber>();
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public MutableSubscriber() {
    }

    public MutableSubscriber(final InterfaceRingBufferSubscriber subscriber) {
        this.setSubscriber(subscriber);
    }
    
    public void setSubscriber(final InterfaceRingBufferSubscriber subscriber) {
        this.subscriber.set(subscriber);
    }
    
    public InterfaceRingBufferSubscriber getSubscriber() {
        return this.subscriber.get();
    }
    
    @Override
    public void readEvent(long sequence, int index) {
        final InterfaceRingBufferSubscriber subscriber = this.subscriber.get();
        if (subscriber != null) {
            subscriber.readEvent(sequence, index);
        }
    }

    @Override
    public boolean processReadEvent() {
        final InterfaceRingBufferSubscriber subscriber = this.subscriber.get();
        if (subscriber != null) {
            return subscriber.processReadEvent();
        } else {
            return false;
        }
    }

    @Override
    public void onBatchEnd() {
        final InterfaceRingBufferSubscriber subscriber = this.subscriber.get();
        if (subscriber != null) {
            subscriber.onBatchEnd();
        }
    }
}
