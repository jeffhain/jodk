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
package net.jodk.threading.locks;

import java.util.concurrent.Callable;

import net.jodk.lang.InterfaceBooleanCondition;

/**
 * Condilock that has no lock, and always yield-spins
 * while it waits for a boolean condition to be true.
 */
public class SpinningCondilock extends AbstractCondilock {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public SpinningCondilock() {
    }

    /*
     * 
     */

    @Override
    public void runInLock(Runnable runnable) {
        runnable.run();
    }

    @Override
    public <V> V callInLock(Callable<V> callable) throws Exception {
        return callable.call();
    }

    /*
     * 
     */
    
    @Override
    public void signal() {
        // nothing to signal
    }
    
    @Override
    public void signalAll() {
        // nothing to signal
    }

    /*
     * 
     */
    
    @Override
    public boolean awaitNanosWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long timeoutNS) throws InterruptedException {
        if (!booleanCondition.isTrue()) {
            if ((timeoutNS = spinningWaitNanosWhileFalse(this, booleanCondition, timeoutNS)) <= 0) {
                return (timeoutNS < 0);
            }
        }
        return true;
    }

    @Override
    public boolean awaitUntilNanosTimeoutTimeWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long endTimeoutTimeNS)
            throws InterruptedException {
        if (!booleanCondition.isTrue()) {
            long timeoutNS;
            if ((timeoutNS = spinningWaitUntilNanosWhileFalse_TT(this, booleanCondition, endTimeoutTimeNS)) <= 0) {
                return (timeoutNS < 0);
            }
        }
        return true;
    }

    @Override
    public boolean awaitUntilNanosWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long deadlineNS) throws InterruptedException {
        if (!booleanCondition.isTrue()) {
            long timeoutNS;
            if ((timeoutNS = spinningWaitUntilNanosWhileFalse_DT(this, booleanCondition, deadlineNS)) <= 0) {
                return (timeoutNS < 0);
            }
        }
        return true;
    }

    @Override
    public void signalInLock() {
        // nothing to signal
    }
    
    @Override
    public void signalAllInLock() {
        // nothing to signal
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    @Override
    protected final void awaitNanosNoEstimate(long timeoutNS) throws InterruptedException {
        throwIfInterrupted();
        // no wait
    }
    
    @Override
    protected final long getMaxSpinningWaitNS() {
        return Long.MAX_VALUE;
    }
}