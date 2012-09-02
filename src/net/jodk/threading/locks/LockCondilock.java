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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.jodk.lang.InterfaceBooleanCondition;

/**
 * Condilock based on a Lock and a Condition.
 */
public class LockCondilock extends AbstractCondilock {
    
    /*
     * If max system time to wait is Long.MAX_VALUE, unlike what is done in awaitUntil(Date),
     * we don't make awaitUntilNanos(long) method call super.awaitUntil(Date), to avoid
     * the creation of a Date object, and just rely on superclass's implementation of
     * awaitUntilNanos(long).
     */
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private final Lock lock;
    
    private final Condition condition;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Creates a condilock based on a default lock.
     */
    public LockCondilock() {
        this(newDefaultLock());
    }

    /**
     * Creates a condilock based on the specified lock
     * and a new condition obtained from it.
     */
    public LockCondilock(final Lock lock) {
        this(
                lock,
                lock.newCondition());
    }

    /**
     * @param lock Lock to use for locking.
     * @param condition A condition based on the specified lock.
     */
    public LockCondilock(
            final Lock lock,
            final Condition condition) {
        this.lock = lock;
        this.condition = condition;
    }

    /**
     * @return Lock used for locking.
     */
    public Lock getLock() {
        return this.lock;
    }
    
    /**
     * @return Condition used for waiting.
     */
    public Condition getCondition() {
        return this.condition;
    }

    /*
     * 
     */

    @Override
    public void runInLock(Runnable runnable) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <V> V callInLock(Callable<V> callable) throws Exception {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return callable.call();
        } finally {
            lock.unlock();
        }
    }

    /*
     * 
     */
    
    @Override
    public void signal() {
        this.condition.signal();
    }
    
    @Override
    public void signalAll() {
        this.condition.signalAll();
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
            final Lock lock = this.lock;
            lock.lock();
            try {
                this.afterLockWaitingForBooleanCondition();
                try {
                    return blockingWaitNanosWhileFalse_TT_locked(this, booleanCondition, timeoutNS);
                } finally {
                    this.beforeUnlockWaitingForBooleanCondition();
                }
            } finally {
                lock.unlock();
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
            final Lock lock = this.lock;
            lock.lock();
            try {
                this.afterLockWaitingForBooleanCondition();
                try {
                    return blockingWaitUntilNanosWhileFalse_TT_locked(this, booleanCondition, endTimeoutTimeNS);
                } finally {
                    this.beforeUnlockWaitingForBooleanCondition();
                }
            } finally {
                lock.unlock();
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
            final Lock lock = this.lock;
            lock.lock();
            try {
                this.afterLockWaitingForBooleanCondition();
                try {
                    return blockingWaitUntilNanosWhileFalse_DT_locked(this, booleanCondition, deadlineNS);
                } finally {
                    this.beforeUnlockWaitingForBooleanCondition();
                }
            } finally {
                lock.unlock();
            }
        }
        return true;
    }

    @Override
    public void signalInLock() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.condition.signal();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void signalAllInLock() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    protected static Lock newDefaultLock() {
        return new ReentrantLock();
    }
    
    @Override
    protected void awaitNanosNoEstimate(long timeoutNS) throws InterruptedException {
        final boolean timing = (timeoutNS < INFINITE_TIMEOUT_THRESHOLD_NS);
        if (timing) {
            // This is the only place where backing's condition timing
            // is used. Note that we always use our own timing above it.
            long unused = this.condition.awaitNanos(timeoutNS);
        } else {
            this.condition.await();
        }
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