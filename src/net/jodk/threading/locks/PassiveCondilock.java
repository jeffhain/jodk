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

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import net.jodk.lang.InterfaceBooleanCondition;

/**
 * Condilock that does not actually wait, signal, or lock.
 * 
 * Useful to deal with concurrency-designed treatments
 * when running the whole application in a single thread.
 */
public class PassiveCondilock implements InterfaceCondilock {
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Does nothing.
     */
    @Override
    public void await() throws InterruptedException {
    }

    /**
     * Does nothing.
     */
    @Override
    public void awaitUninterruptibly() {
    }

    /**
     * Does nothing else than returning zero.
     * @return zero.
     */
    @Override
    public long awaitNanos(long timeoutNS) throws InterruptedException {
        return 0L;
    }

    /**
     * Does nothing else than returning true.
     * @return true.
     */
    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

    /**
     * Does nothing else than returning true.
     * @return true.
     */
    @Override
    public boolean awaitUntil(Date deadline) throws InterruptedException {
        return true;
    }

    /**
     * Does nothing.
     */
    @Override
    public void signal() {
    }

    /**
     * Does nothing.
     */
    @Override
    public void signalAll() {
    }
    
    /*
     * 
     */
    
    /**
     * Does not lock before running runnable.
     * @param runnable Runnable to run.
     */
    public void runInLock(final Runnable runnable) {
        runnable.run();
    }

    /**
     * Does not lock before calling callable.
     * @param callable Callable to call.
     */
    public <V> V callInLock(final Callable<V> callable) throws Exception {
        return callable.call();
    }

    /*
     * 
     */

    /**
     * @return 0.
     */
    @Override
    public long timeoutTimeNS() {
        return 0L;
    }

    /**
     * @return 0.
     */
    @Override
    public long deadlineTimeNS() {
        return 0L;
    }

    /**
     * Does nothing.
     */
    @Override
    public void awaitUntilNanosTimeoutTime(long endTimeoutTimeNS) throws InterruptedException {
    }

    /**
     * Does nothing else than returning true.
     * @return true.
     */
    @Override
    public boolean awaitUntilNanos(long deadlineNS) throws InterruptedException {
        return true;
    }

    /**
     * @throws IllegalStateException if the specified condition is not true.
     */
    @Override
    public void awaitWhileFalseInLockUninterruptibly(final InterfaceBooleanCondition booleanCondition) {
        if (!booleanCondition.isTrue()) {
            throw new IllegalStateException("boolean condition must be true");
        }
    }

    /**
     * Does nothing else than returning boolean condition's state.
     * @return True if the boolean condition is true, false otherwise.
     */
    @Override
    public boolean awaitNanosWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long timeoutNS) throws InterruptedException {
        return booleanCondition.isTrue();
    }

    /**
     * Does nothing else than returning boolean condition's state.
     * @return True if the boolean condition is true, false otherwise.
     */
    @Override
    public boolean awaitUntilNanosTimeoutTimeWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long endTimeoutTimeNS) throws InterruptedException {
        return booleanCondition.isTrue();
    }

    /**
     * Does nothing else than returning boolean condition's state.
     * @return True if the boolean condition is true, false otherwise.
     */
    @Override
    public boolean awaitUntilNanosWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long deadlineNS) throws InterruptedException {
        return booleanCondition.isTrue();
    }

    /**
     * Does nothing.
     */
    @Override
    public void signalInLock() {
    }
    
    /**
     * Does nothing.
     */
    @Override
    public void signalAllInLock() {
    }
}