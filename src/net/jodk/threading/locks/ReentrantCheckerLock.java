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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An extension of ReentrantLock to implement InterfaceCheckerLock.
 * 
 * Can be named (useful for locking investigations), and have a reference to
 * another lock that must never be held by a thread that attempts to lock
 * this lock (useful to detect deadlock possibilities).
 */
public class ReentrantCheckerLock extends ReentrantLock implements InterfaceCheckerLock {

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final long serialVersionUID = 1L;

    private final String name;

    /**
     * A lock that must not be held by current thread on any
     * lock attempt of this lock.
     */
    private final InterfaceCheckerLock smallerLock;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates a non fair lock with no name.
     */
    public ReentrantCheckerLock() {
        this(null, false, null);
    }

    /**
     * Creates a non fair lock with the specified name.
     * 
     * @param name Lock's name.
     */
    public ReentrantCheckerLock(final String name) {
        this(name, false, null);
    }

    /**
     * Creates a lock with no name and the specified fairness.
     * 
     * @param fair Lock's fairness.
     */
    public ReentrantCheckerLock(boolean fair) {
        this(null, fair, null);
    }

    /**
     * Creates a lock with the specified name and fairness.
     * 
     * @param name Lock's name.
     * @param fair Lock's fairness.
     */
    public ReentrantCheckerLock(
            final String name,
            boolean fair) {
        this(name, fair, null);
    }

    /**
     * Creates a lock with the specified name, fairness, and smaller lock.
     * 
     * @param name Lock's name.
     * @param fair Lock's fairness.
     * @param smallerLock A lock that must not be held by current thread
     *        on any lock attempt of this lock. Can be null.
     */
    public ReentrantCheckerLock(
            final String name,
            boolean fair,
            final InterfaceCheckerLock smallerLock) {
        super(fair);
        this.name = name;
        this.smallerLock = smallerLock;
    }

    @Override
    public String toString() {
        final String superString = super.toString();
        if ((this.name == null) && (this.smallerLock == null)) {
            return superString;
        }
        
        /*
         * Computing string length.
         */
        
        int stringLength = superString.length();
        
        if (this.name != null) {
            stringLength += (2+this.name.length());
        }
        
        String C1 = null;
        String tmp1 = null;
        String tmp2 = null;
        if (this.smallerLock != null) {
            C1 = "[smallerLock=";
            // Simple toString for smaller lock, to avoid too long string
            // in case of long chain of smaller locks.
            tmp1 = this.smallerLock.getClass().getName();
            tmp2 = Integer.toHexString(this.smallerLock.hashCode());
            stringLength += (C1.length()+2) + tmp1.length() + tmp2.length();
        }
        
        /*
         * 
         */
        
        final StringBuilder sb = new StringBuilder(stringLength);
        
        sb.append(superString);
        
        if (this.name != null) {
            sb.append("[");
            sb.append(this.name);
            sb.append("]");
        }
        
        if (this.smallerLock != null) {
            sb.append(C1);
            sb.append(tmp1);
            sb.append("@");
            sb.append(tmp2);
            sb.append("]");
        }
        
        final String result = sb.toString();
        if (result.length() != stringLength) {
            throw new AssertionError(sb.length()+" != "+stringLength);
        }
        return result;
    }

    /**
     * @return Lock's name, or null if it has none.
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return Smaller lock, or null if there is none.
     */
    public InterfaceCheckerLock getSmallerLock() {
        return this.smallerLock;
    }

    @Override
    public Thread getOwnerBestEffort() {
        return super.getOwner();
    }

    @Override
    public void lock() {
        this.checkSmallerLockBeforeLockIfNeeded();
        super.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        this.checkSmallerLockBeforeLockIfNeeded();
        super.lockInterruptibly();
    }

    @Override
    public boolean tryLock() {
        this.checkSmallerLockBeforeLockIfNeeded();
        return super.tryLock();
    }

    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        this.checkSmallerLockBeforeLockIfNeeded();
        return super.tryLock(timeout, unit);
    }

    @Override
    public boolean checkNotLocked() {
        return LocksUtils.checkNotLocked(this);
    }

    @Override
    public boolean checkNotHeldByAnotherThread() {
        return LocksUtils.checkNotHeldByAnotherThread(this);
    }

    @Override
    public boolean checkHeldByCurrentThread() {
        return LocksUtils.checkHeldByCurrentThread(this);
    }

    @Override
    public boolean checkNotHeldByCurrentThread() {
        return LocksUtils.checkNotHeldByCurrentThread(this);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * If smaller lock exists, and this lock is not held by current thread,
     * checks that smaller lock is not already held by current thread,
     * i.e. that this lock will not be acquired from within smaller lock.
     */
    private boolean checkSmallerLockBeforeLockIfNeeded() {
        if (this.smallerLock != null) {
            if (!this.isHeldByCurrentThread()) {
                // lock not yet acquired: checking smaller lock is not held
                this.smallerLock.checkNotHeldByCurrentThread();
            }
        }
        return true;
    }
}
