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

import net.jodk.threading.ringbuffers.InterfaceRingBufferSubscriber;

/**
 * Subscriber wrapper that allows to handle exceptions
 * from the subscriber, instead of letting them go up
 * to worker's caller.
 */
public class ExceptionHandlingSubscriber implements InterfaceRingBufferSubscriber {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private final InterfaceRingBufferSubscriber subscriber;
    
    private final InterfaceRingBufferExceptionHandler exceptionHandler;
    
    private long lastSequence;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public ExceptionHandlingSubscriber(
            final InterfaceRingBufferSubscriber subscriber,
            final InterfaceRingBufferExceptionHandler exceptionHandler) {
        this.subscriber = subscriber;
        this.exceptionHandler = exceptionHandler;
    }
    
    @Override
    public void readEvent(long sequence, int index) {
        this.lastSequence = sequence;
        try {
            this.subscriber.readEvent(sequence, index);
        } catch (Exception e) {
            this.exceptionHandler.handle(e, sequence);
        }
    }

    @Override
    public boolean processReadEvent() {
        try {
            return this.subscriber.processReadEvent();
        } catch (Exception e) {
            this.exceptionHandler.handle(e, this.lastSequence);
            return false;
        }
    }

    @Override
    public void onBatchEnd() {
        try {
            this.subscriber.onBatchEnd();
        } catch (Exception e) {
            this.exceptionHandler.handle(e, this.lastSequence);
        }
    }
}
