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

import java.util.ConcurrentModificationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import net.jodk.lang.RethrowException;
import net.jodk.lang.Unchecked;
import net.jodk.threading.locks.InterfaceCheckerLock;
import net.jodk.threading.locks.ReentrantCheckerLock;

import junit.framework.TestCase;

public class ReentrantCheckerLockTest extends TestCase {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_ReentrantCheckerLock() {
        final ReentrantCheckerLock lock = new ReentrantCheckerLock();
        assertEquals(null,lock.getName());

        useLock(lock);
    }

    public void test_ReentrantCheckerLock_String() {
        for (String name : new String[]{null,"myName"}) {
            final ReentrantCheckerLock lock = new ReentrantCheckerLock(name);
            assertEquals(name,lock.getName());

            useLock(lock);
        }
    }

    public void test_ReentrantCheckerLock_boolean() {
        for (boolean fair : new boolean[]{false,true}) {
            final ReentrantCheckerLock lock = new ReentrantCheckerLock(fair);
            assertEquals(fair,lock.isFair());

            useLock(lock);
        }
    }

    public void test_ReentrantCheckerLock_String_boolean() {
        for (String name : new String[]{null,"myName"}) {
            for (boolean fair : new boolean[]{false,true}) {
                final ReentrantCheckerLock lock = new ReentrantCheckerLock(name,fair);
                assertEquals(name,lock.getName());
                assertEquals(fair,lock.isFair());

                useLock(lock);
            }
        }
    }

    public void test_ReentrantCheckerLock_String_boolean_InterfaceCheckerLock() {
        for (String name : new String[]{null,"myName"}) {
            for (boolean fair : new boolean[]{false,true}) {
                for (InterfaceCheckerLock smallerLock : new InterfaceCheckerLock[]{null,new ReentrantCheckerLock()}) {
                    final ReentrantCheckerLock lock = new ReentrantCheckerLock(name,fair,smallerLock);
                    assertEquals(name,lock.getName());
                    assertEquals(fair,lock.isFair());
                    assertEquals(smallerLock,lock.getSmallerLock());

                    useLock(lock);
                }
            }
        }
    }

    public void test_toString() {
        for (String name : new String[]{null,"myName"}) {
            for (boolean fair : new boolean[]{false,true}) {
                for (InterfaceCheckerLock smallerLock : new InterfaceCheckerLock[]{null,new ReentrantCheckerLock()}) {
                    final ReentrantCheckerLock lock = new ReentrantCheckerLock(name,fair,smallerLock);
                    for (boolean locked : new boolean[]{false,true}) {
                        final String string;
                        if (locked) {
                            lock.lock();
                            try {
                                string = lock.toString();
                            } finally {
                                lock.unlock();
                            }
                        } else {
                            string = lock.toString();
                        }
                        String expected =
                                lock.getClass().getName()+"@"+Integer.toHexString(lock.hashCode())
                                +(locked ? "[Locked by thread " + Thread.currentThread().getName() + "]" : "[Unlocked]");
                        if (name != null) {
                            expected += "["+name+"]";
                        }
                        if (smallerLock != null) {
                            final String littleString =
                                    smallerLock.getClass().getName()
                                    +"@"
                                    +Integer.toHexString(smallerLock.hashCode());
                            expected += "[smallerLock="+littleString+"]";
                        }
                        assertEquals(expected,string);
                    }
                }
            }
        }
    }

    public void test_getName() {
        // already covered in constructors tests
    }

    public void test_getSmallerLock() {
        // already covered in constructors tests
    }

    public void test_getOwnerBestEffort() {
        // already covered in constructors tests (usage test)
    }

    public void test_lockMethods() {
        final ReentrantCheckerLock smallerLock = new ReentrantCheckerLock();
        final ReentrantCheckerLock lock = new ReentrantCheckerLock(null,false,smallerLock);

        for (int lockType=0;lockType<=3;lockType++) {

            /*
             * current thread holding smaller lock, but also already holding lock
             */

            lock.lock();
            try {
                smallerLock.lock();
                try {
                    lock(lock, lockType);
                    try {
                    } finally {
                        lock.unlock();
                    }
                } finally {
                    smallerLock.unlock();
                }
            } finally {
                lock.unlock();
            }

            /*
             * current thread holding smaller lock, and not already holding lock
             */

            smallerLock.lock();
            try {
                try {
                    lock(lock, lockType);
                    assertTrue(false);
                } catch (IllegalStateException e) {
                    // ok
                }
            } finally {
                smallerLock.unlock();
            }
        }
    }

    public void test_checkNotLocked() {
        final ReentrantCheckerLock lock = new ReentrantCheckerLock();

        boolean held;
        boolean byCurrentThread;

        /*
         * not held
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                assertTrue(lock.checkNotLocked());
            }
        }, lock, held = false, byCurrentThread = false);

        /*
         * held by current thread
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.checkNotLocked();
                    assertTrue(false);
                } catch (IllegalStateException e) {
                    // ok
                }
            }
        }, lock, held = true, byCurrentThread = true);

        /*
         * held by another thread
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.checkNotLocked();
                    assertTrue(false);
                } catch (IllegalStateException e) {
                    // ok
                }
            }
        }, lock, held = true, byCurrentThread = false);
    }

    public void test_checkNotHeldByAnotherThread() {
        final ReentrantCheckerLock lock = new ReentrantCheckerLock();

        boolean held;
        boolean byCurrentThread;

        /*
         * not held
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                assertTrue(lock.checkNotHeldByAnotherThread());
            }
        }, lock, held = false, byCurrentThread = false);

        /*
         * held by current thread
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                assertTrue(lock.checkNotHeldByAnotherThread());
            }
        }, lock, held = true, byCurrentThread = true);

        /*
         * held by another thread
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.checkNotHeldByAnotherThread();
                    assertTrue(false);
                } catch (ConcurrentModificationException e) {
                    // ok
                }
            }
        }, lock, held = true, byCurrentThread = false);
    }

    public void test_checkHeldByCurrentThread() {
        final ReentrantCheckerLock lock = new ReentrantCheckerLock();

        boolean held;
        boolean byCurrentThread;

        /*
         * not held
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.checkHeldByCurrentThread();
                    assertTrue(false);
                } catch (ConcurrentModificationException e) {
                    // ok
                }
            }
        }, lock, held = false, byCurrentThread = false);

        /*
         * held by current thread
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                assertTrue(lock.checkHeldByCurrentThread());
            }
        }, lock, held = true, byCurrentThread = true);

        /*
         * held by another thread
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.checkHeldByCurrentThread();
                    assertTrue(false);
                } catch (ConcurrentModificationException e) {
                    // ok
                }
            }
        }, lock, held = true, byCurrentThread = false);
    }

    public void test_checkNotHeldByCurrentThread() {
        final ReentrantCheckerLock lock = new ReentrantCheckerLock();

        boolean held;
        boolean byCurrentThread;

        /*
         * not held
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                assertTrue(lock.checkNotHeldByCurrentThread());
            }
        }, lock, held = false, byCurrentThread = false);

        /*
         * held by current thread
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.checkNotHeldByCurrentThread();
                    assertTrue(false);
                } catch (IllegalStateException e) {
                    // ok
                }
            }
        }, lock, held = true, byCurrentThread = true);

        /*
         * held by another thread
         */

        callRunnableWhenLockInState(new Runnable() {
            @Override
            public void run() {
                assertTrue(lock.checkNotHeldByCurrentThread());
            }
        }, lock, held = true, byCurrentThread = false);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * Tests usage.
     */
    private static void useLock(ReentrantCheckerLock lock) {
        assertFalse(lock.isLocked());
        assertFalse(lock.isHeldByCurrentThread());
        assertEquals(0,lock.getQueueLength());
        assertEquals(0,lock.getHoldCount());
        assertEquals(null,lock.getOwnerBestEffort());
        lock.checkNotHeldByAnotherThread();
        lock.checkNotHeldByCurrentThread();
        lock.checkNotLocked();
        lock.lock();
        try {
            assertTrue(lock.isLocked());
            assertTrue(lock.isHeldByCurrentThread());
            assertEquals(0,lock.getQueueLength());
            assertEquals(1,lock.getHoldCount());
            assertEquals(Thread.currentThread(),lock.getOwnerBestEffort());
            lock.checkHeldByCurrentThread();
        } finally {
            lock.unlock();
        }
        assertFalse(lock.isLocked());
        assertFalse(lock.isHeldByCurrentThread());
        assertEquals(0,lock.getQueueLength());
        assertEquals(0,lock.getHoldCount());
        assertEquals(null,lock.getOwnerBestEffort());
        lock.checkNotHeldByAnotherThread();
        lock.checkNotHeldByCurrentThread();
        lock.checkNotLocked();
    }

    /**
     * Lock type:
     * 0 = lock()
     * 1 = lockInterruptibly()
     * 2 = tryLock()
     * 3 = tryLock(long,TimeUnit)
     */
    private static void lock(Lock lock, int lockType) {
        if (lockType == 0) {
            lock.lock();
        } else if (lockType == 1) {
            try {
                lock.lockInterruptibly();
            } catch (InterruptedException e) {
                throw new RethrowException(e);
            }
        } else if (lockType == 2) {
            final boolean didIt = lock.tryLock();
            assertTrue(didIt);
        } else if (lockType == 3) {
            final boolean didIt;
            try {
                didIt = lock.tryLock(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                throw new RethrowException(e);
            }
            assertTrue(didIt);
        } else {
            throw new UnsupportedOperationException(lockType+" unsupported");
        }
    }

    private void callRunnableWhenLockInState(
            final Runnable runnable,
            final InterfaceCheckerLock lock,
            boolean held,
            boolean byCurrentThread) {
        ExecutorService executor = null;
        final AtomicBoolean otherThreadHoldsLock = new AtomicBoolean();
        if (held) {
            if (byCurrentThread) {
                lock.lock();
            } else {
                executor = Executors.newCachedThreadPool();
                executor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                lock.lock();
                                try {
                                    otherThreadHoldsLock.set(true);
                                    synchronized (otherThreadHoldsLock) {
                                        while (otherThreadHoldsLock.get()) {
                                            Unchecked.wait(otherThreadHoldsLock);
                                        }
                                    }
                                } finally {
                                    lock.unlock();
                                }
                            }
                        });
                while (!otherThreadHoldsLock.get()) {
                    Unchecked.sleepMS(1L);
                }
            }
        }
        runnable.run();
        if (held) {
            if (byCurrentThread) {
                lock.unlock();
            } else {
                otherThreadHoldsLock.set(false);
                synchronized (otherThreadHoldsLock) {
                    otherThreadHoldsLock.notifyAll();
                }
                Unchecked.shutdownAndAwaitTermination(executor);
            }
        }
    }
}
