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
 * Worker for a ring buffer (service or not).
 * 
 * No method is provided to await for worker termination,
 * for if runXXX method throws an exception it might be
 * called again to continue work, or not, which would
 * need to be taken into account to know whether the worker
 * terminated or not, and a simple termination method wouldn't
 * be aware of it.
 */
public interface InterfaceRingBufferWorker {

    /**
     * Not thread-safe with respect to {reset,runXXX,setStateXXX} methods.
     * 
     * Resets this worker's state to RUNNING (i.e. if idle,
     * does not return but waits for more events).
     */
    public void reset();
    
    /**
     * Can be called concurrently with worker being used (thread in runXXX method).
     * 
     * After call to this method, depending on implementation, this worker keeps
     * processing events until no more to process, or until no more to process
     * that have been visibly published before call to this method.
     * 
     * Has no effect (other than possible volatile semantics)
     * if state is already TERMINATE_WHEN_IDLE or TERMINATE_ASAP.
     */
    public void setStateTerminateWhenIdle();
    
    /**
     * Can be called concurrently with worker being used (thread in runXXX method).
     * 
     * Causes this worker to return ASAP, without processing any more event.
     * 
     * Has no effect (other than possible volatile semantics)
     * if state is already TERMINATE_ASAP.
     */
    public void setStateTerminateASAP();
    
    /**
     * Not thread-safe with respect to {reset,runXXX} methods.
     * 
     * Runs the worker from the specified sequence.
     * 
     * Depending on implementation, might not allow to jump backward,
     * i.e. might only support startSequence >= max passed sequence + 1.
     * 
     * @param startSequence Sequence to process from (>= 0).
     */
    public void runWorkerFrom(long startSequence) throws InterruptedException;
    
    /**
     * Not thread-safe with respect to {reset,runXXX} methods.
     * 
     * Runs the worker from max passed sequence + 1.
     * 
     * Should be equivalent to "worker.runWorkerFrom(worker.getMaxPassedSequence()+1)",
     * but with possibly lower overhead.
     */
    public void runWorker() throws InterruptedException;
    
    /**
     * Can be called concurrently with worker being used (thread in runXXX method).
     * 
     * @return Max passed sequence, possibly with some lag,
     *         to allow for quick batches or other optimizations.
     */
    public long getMaxPassedSequence();
}
