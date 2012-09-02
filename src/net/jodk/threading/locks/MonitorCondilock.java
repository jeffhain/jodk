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
import net.jodk.lang.LangUtils;

/**
 * Condilock based on an object's monitor.
 */
public class MonitorCondilock extends AbstractCondilock {

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private final Object mutex;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates a condilock based on a default mutex.
     */
    public MonitorCondilock() {
        this(new Object());
    }

    /**
     * @param mutex Object which monitor is to be used for locking.
     */
    public MonitorCondilock(final Object mutex) {
        this.mutex = mutex;
    }

    /**
     * @return Object which monitor is used for locking.
     */
    public Object getMutex() {
        return this.mutex;
    }

    /*
     * 
     */

    @Override
    public void runInLock(Runnable runnable) {
        synchronized (this.mutex) {
            runnable.run();
        }
    }

    @Override
    public <V> V callInLock(Callable<V> callable) throws Exception {
        synchronized (this.mutex) {
            return callable.call();
        }
    }

    /*
     * 
     */

    @Override
    public void signal() {
        this.mutex.notify();
    }

    @Override
    public void signalAll() {
        this.mutex.notifyAll();
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
            synchronized (this.mutex) {
                this.afterLockWaitingForBooleanCondition();
                try {
                    return blockingWaitNanosWhileFalse_TT_locked(this, booleanCondition, timeoutNS);
                } finally {
                    this.beforeUnlockWaitingForBooleanCondition();
                }
            }
        }
        return true;
    }

    @Override
    public boolean awaitUntilNanosTimeoutTimeWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long endTimeoutTimeNS) throws InterruptedException {
        if (!booleanCondition.isTrue()) {
            long timeoutNS;
            if ((timeoutNS = spinningWaitUntilNanosWhileFalse_TT(this, booleanCondition, endTimeoutTimeNS)) <= 0) {
                return (timeoutNS < 0);
            }
            synchronized (this.mutex) {
                this.afterLockWaitingForBooleanCondition();
                try {
                    return blockingWaitUntilNanosWhileFalse_TT_locked(this, booleanCondition, endTimeoutTimeNS);
                } finally {
                    this.beforeUnlockWaitingForBooleanCondition();
                }
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
            synchronized (this.mutex) {
                this.afterLockWaitingForBooleanCondition();
                try {
                    return blockingWaitUntilNanosWhileFalse_DT_locked(this, booleanCondition, deadlineNS);
                } finally {
                    this.beforeUnlockWaitingForBooleanCondition();
                }
            }
        }
        return true;
    }

    @Override
    public void signalInLock() {
        synchronized (this.mutex) {
            this.mutex.notify();
        }
    }

    @Override
    public void signalAllInLock() {
        synchronized (this.mutex) {
            this.mutex.notifyAll();
        }
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------

    @Override
    protected void awaitNanosNoEstimate(long timeoutNS) throws InterruptedException {
        // No need to test if timeout is eternal: this method already
        // handles the case of huge timeouts.
        LangUtils.waitNS(this.mutex, timeoutNS);
    }

    /**
     * Must be called right after lock has been acquired,
     * when awaiting on boolean condition.
     */
    protected void afterLockWaitingForBooleanCondition() {
    }

    /**
     * Must be called right before unlock,
     * when awaiting on boolean condition.
     * 
     * Must not be called if afterLockWaitingForBooleanCondition()
     * threw an exception.
     */
    protected void beforeUnlockWaitingForBooleanCondition() {
    }
}