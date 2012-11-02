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
package net.jodk.io.mock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;
import net.jodk.lang.Unchecked;
import junit.framework.TestCase;

public class MockFileChannelTest extends TestCase {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------
    
    private static final long TOLERANCE_MS = 100L;
    
    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    private static class MyReadableByteChannel implements ReadableByteChannel {
        boolean open = true;
        final ByteBufferMockBuffer content;
        public MyReadableByteChannel(int capacity) {
            this.content = new ByteBufferMockBuffer(capacity);
        }
        @Override
        public String toString() {
            return this.content.toString();
        }
        @Override
        public boolean isOpen() {
            return this.open;
        }
        @Override
        public void close() {
            this.open = false;
        }
        @Override
        public int read(ByteBuffer dst) {
            final ByteBuffer src = this.content.getBackingByteBuffer();
            if (src.remaining() == 0) {
                return -1;
            }
            int n = 0;
            while ((src.remaining() != 0) && (dst.remaining() != 0)) {
                dst.put(src.get());
                ++n;
            }
            return n;
        }
    };

    private static class MyWritableByteChannel implements WritableByteChannel {
        boolean open = true;
        final ByteBufferMockBuffer content;
        public MyWritableByteChannel(int capacity) {
            this.content = new ByteBufferMockBuffer(capacity);
        }
        @Override
        public String toString() {
            return this.content.toString();
        }
        @Override
        public boolean isOpen() {
            return this.open;
        }
        @Override
        public void close() {
            this.open = false;
        }
        @Override
        public int write(ByteBuffer src) {
            final ByteBuffer dst = this.content.getBackingByteBuffer();
            int n = 0;
            while ((src.remaining() != 0) && (dst.remaining() != 0)) {
                dst.put(src.get());
                ++n;
            }
            return n;
        }
    };

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    /**
     * Offset not to hit array bounds,
     * which allows to check for adjacent values
     * of considered ranges.
     */
    private static final long SML = 1;
    
    private static final int TMP_CHUNK_SIZE = MockFileChannel.TMP_CHUNK_SIZE;
    
    /**
     * Enough to allow for a few chunks, for copies-by-chunk.
     */
    private static final int DEFAULT_CAPACITY = 3*TMP_CHUNK_SIZE;
    
    /**
     * To test big values of position/limit.
     * Far enough from Long.MAX_VALUE not to have trouble.
     */
    private static final long BIG = Long.MAX_VALUE - 2L*DEFAULT_CAPACITY;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public void test_MockFileChannel_InterfaceMockBuffer_3boolean() {
        try {
            new MockFileChannel(null, true, true, false);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        
        VirtualMockBuffer buffer = new VirtualMockBuffer(2*DEFAULT_CAPACITY);
        for (boolean readable : new boolean[]{false,true}) {
            for (boolean writable : new boolean[]{false,true}) {
                for (boolean appendMode : new boolean[]{false,true}) {
                    MockFileChannel channel = new MockFileChannel(buffer, readable, writable, appendMode);
                    assertSame(buffer, channel.getBackingMockBuffer());
                    assertEquals(readable, channel.getReadable());
                    assertEquals(writable, channel.getWritable());
                    assertEquals(appendMode, channel.getAppendMode());
                }
            }
        }
    }

    public void test_toString() {
        for (MockFileChannel channel : newChannelsVirtual()) {
            final String expected =
                "[buffer="+channel.getBackingMockBuffer().toString()
                +",position="+position(channel)
                +",readable="+channel.getReadable()
                +",writable="+channel.getWritable()
                +",appendMode="+channel.getAppendMode()+"]";
            assertEquals(expected, channel.toString());
        }
    }
    
    public void test_getBackingMockBuffer() {
        // already covered
    }
    
    public void test_getReadable() {
        // already covered
    }
    
    public void test_getWritable() {
        // already covered
    }
    
    public void test_getAppendMode() {
        // already covered
    }
    
    public void test_position_and_position_long() {
        for (MockFileChannel channel : newChannelsVirtual()) {
            /*
             * open
             */
            
            for (long pos : new long[]{Long.MIN_VALUE,-1}) {
                try {
                    channel.position(pos);
                    assertTrue(false);
                } catch (IllegalArgumentException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
            for (long pos : new long[]{0,10,100,Long.MAX_VALUE}) {
                FileChannel channelGot = null;
                try {
                    channelGot = channel.position(pos);
                } catch (IOException e) {
                    assertTrue(false);
                }
                assertSame(channel, channelGot);
                long posGot = Long.MIN_VALUE;
                try {
                    posGot = channel.position();
                } catch (IOException e) {
                    assertTrue(false);
                }
                final long expectedPos;
                if (channel.getAppendMode()) {
                    expectedPos = size(channel);
                } else {
                    expectedPos = pos;
                }
                assertEquals(expectedPos, posGot);
            }
            
            /*
             * closed
             */
            
            close(channel);
            
            try {
                channel.position();
                assertTrue(false);
            } catch (ClosedChannelException e) {
                // ok
            } catch (IOException e) {
                assertTrue(false);
            }
            for (long pos : new long[]{Long.MIN_VALUE,-1}) {
                try {
                    channel.position(pos);
                    assertTrue(false);
                } catch (ClosedChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
        }
    }

    public void test_size() {
        for (MockFileChannel channel : newChannelsVirtual()) {
            /*
             * open
             */
            
            for (long size : new long[]{0,1,Long.MAX_VALUE}) {
                channel.getBackingMockBuffer().limit(size);
                long sizeGot = Long.MIN_VALUE;
                try {
                    sizeGot = channel.size();
                } catch (IOException e) {
                    assertTrue(false);
                }
                assertEquals(size, sizeGot);
            }
            
            /*
             * closed
             */
            
            close(channel);
            
            try {
                channel.size();
                assertTrue(false);
            } catch (ClosedChannelException e) {
                // ok
            } catch (IOException e) {
                assertTrue(false);
            }
        }
    }
    
    public void test_truncate_long() {
        for (MockFileChannel channel : newChannelsVirtual()) {
            final InterfaceMockBuffer channelBuffer = channel.getBackingMockBuffer();
            final long initialLimit = 10;
            channelBuffer.limit(initialLimit);
            
            /*
             * bad size
             */

            for (long size : new long[]{Long.MIN_VALUE,-1}) {
                try {
                    channel.truncate(size);
                    assertTrue(false);
                } catch (IllegalArgumentException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }

            /*
             * open
             */
            
            for (long size : new long[]{0,1,initialLimit,2*initialLimit,4*initialLimit,Long.MAX_VALUE}) {
                // Ensuring initial size.
                channel.getBackingMockBuffer().limit(initialLimit);
                
                // Setting position to check it is taken down to size,
                // whether or not truncation occurs.
                final long oldPos = 3*initialLimit;
                try {
                    channel.position(oldPos);
                } catch (IOException e1) {
                    assertTrue(false);
                }
                
                FileChannel channelGot = null;
                if ((size <= initialLimit) && (!channel.getWritable())) {
                    try {
                        channelGot = channel.truncate(size);
                        assertTrue(false);
                    } catch (NonWritableChannelException e) {
                        // ok
                    } catch (IOException e) {
                        assertTrue(false);
                    }
                    continue;
                } else {
                    try {
                        channelGot = channel.truncate(size);
                    } catch (IOException e) {
                        assertTrue(false);
                    }
                    assertSame(channel, channelGot);
                }
                
                long sizeGot = Long.MIN_VALUE;
                try {
                    sizeGot = channel.size();
                } catch (IOException e) {
                    assertTrue(false);
                }
                long posGot = Long.MIN_VALUE;
                try {
                    posGot = channel.position();
                } catch (IOException e) {
                    assertTrue(false);
                }
                if (size < initialLimit) {
                    assertEquals(size, sizeGot);
                } else {
                    assertEquals(initialLimit, sizeGot);
                }
                final long expectedPos;
                if (channel.getAppendMode()) {
                    expectedPos = size(channel);
                } else {
                    if (false) { // TODO not the case due to truncate(long) bug, see impl
                        // position downed if > size
                        expectedPos = Math.min(size, oldPos);
                    } else {
                        expectedPos = (size <= initialLimit) ? Math.min(size, oldPos) : oldPos;
                    }
                }
                assertEquals(expectedPos, posGot);
            }

            /*
             * closed
             */
            
            close(channel);
            
            for (long size : new long[]{Long.MIN_VALUE,0,Long.MAX_VALUE}) {
                try {
                    channel.truncate(size);
                    assertTrue(false);
                } catch (ClosedChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
        }
    }
    
    public void test_tryLockAlien_long_long_boolean() {
        this.test_alienLocking(true);
    }
    
    public void test_lockAlien_long_long_boolean() {
        this.test_alienLocking(false);
    }
    
    public void test_alienLocking(boolean isTryLock) {
        
        /*
         * pos/size/readable/writable/closing
         */
        
        for (MockFileChannel channel : newChannelsVirtual()) {
            for (boolean closed : new boolean[]{false,true}) {
                if (closed) {
                    /*
                     * closing (doesn't prevent alien locking)
                     */
                    close(channel);
                }
                for (long position : new long[]{Long.MIN_VALUE,-1,0,1,10,Long.MAX_VALUE}) {
                    for (long size : new long[]{Long.MIN_VALUE,-1,0,1,10,Long.MAX_VALUE}) {
                        for (boolean shared : new boolean[]{false,true}) {
                            Class<?> expectedClass = null;
                            {
                                boolean posSizeOK = consistentBounds(Long.MAX_VALUE, position, size);
                                if (!posSizeOK) {
                                    expectedClass = IllegalArgumentException.class;
                                }
                            }
                            FileLock lock = null;
                            try {
                                lock = lockOrTryAlien(isTryLock, channel, position, size, shared);
                                assertNotNull(lock);
                                assertNull(expectedClass);
                            } catch (Exception e) {
                                assertEquals(expectedClass, e.getClass());
                            }
                            if (lock != null) {
                                // To avoid conflict with subsequent locks.
                                release(lock);
                            }
                        }
                    }
                }
            }
        }
        
        /*
         * no conflict
         */
        
        for (MockFileChannel channel : newChannelsVirtual()) {
            for (long position : new long[]{0,1}) {
                for (long size : new long[]{0,1}) {
                    for (boolean shared : new boolean[]{false,true}) {
                        final FileLock lock;
                        try {
                            lock = lockOrTryAlien(isTryLock, channel, position, size, shared);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        assertNotNull(lock);
                        assertTrue(lock.isValid());
                        assertEquals(position, lock.position());
                        assertEquals(size, lock.size());
                        assertEquals(shared, lock.isShared());
                        try {
                            lock.release();
                        } catch (IOException e) {
                            assertTrue(false);
                        }
                        assertFalse(lock.isValid());
                    }
                }
            }
            /*
             * Still valid after closing.
             */
            final FileLock lock;
            try {
                lock = lockOrTryAlien(isTryLock, channel, 0L, 1L, false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertTrue(lock.isValid());
            close(channel);
            assertTrue(lock.isValid());
            try {
                lock.release();
            } catch (IOException e) {
                assertTrue(false);
            }
            assertFalse(lock.isValid());
        }
    }
    
    public void test_tryLock_long_long_boolean() {
        this.test_locking(true);
    }
    
    public void test_lock_long_long_boolean() {
        this.test_locking(false);
    }
    
    public void test_locking(boolean isTryLock) {
        
        /*
         * pos/size/readable/writable/closing
         */
        
        for (MockFileChannel channel : newChannelsVirtual()) {
            /*
             * open
             */
            for (long position : new long[]{Long.MIN_VALUE,-1,0,1,10,Long.MAX_VALUE}) {
                for (long size : new long[]{Long.MIN_VALUE,-1,0,1,10,Long.MAX_VALUE}) {
                    for (boolean shared : new boolean[]{false,true}) {
                        Class<?> expectedClass = null;
                        if (shared && (!channel.getReadable())) {
                            expectedClass = NonReadableChannelException.class;
                        } else if ((!shared) && (!channel.getWritable())) {
                            expectedClass = NonWritableChannelException.class;
                        } else {
                            boolean posSizeOK = consistentBounds(Long.MAX_VALUE, position, size);
                            if (!posSizeOK) {
                                expectedClass = IllegalArgumentException.class;
                            }
                        }
                        FileLock lock = null;
                        try {
                            lock = lockOrTry(isTryLock, channel, position, size, shared);
                            assertNotNull(lock);
                            assertNull(expectedClass);
                        } catch (Exception e) {
                            assertEquals(expectedClass, e.getClass());
                        }
                        if (lock != null) {
                            // To avoid conflict with subsequent locks.
                            release(lock);
                        }
                    }
                }
            }
            /*
             * closed
             */
            close(channel);
            for (long position : new long[]{Long.MIN_VALUE,-1,0,1,10,Long.MAX_VALUE}) {
                for (long size : new long[]{Long.MIN_VALUE,-1,0,1,10,Long.MAX_VALUE}) {
                    for (boolean shared : new boolean[]{false,true}) {
                        try {
                            lockOrTry(isTryLock, channel, position, size, shared);
                            assertTrue(false);
                        } catch (ClosedChannelException e) {
                            // ok
                        } catch (IOException e) {
                            assertTrue(false);
                        }
                    }
                }
            }
        }
        
        /*
         * no conflict
         */
        
        for (MockFileChannel channel : newChannelsVirtual()) {
            for (long position : new long[]{0,1}) {
                for (long size : new long[]{0,1}) {
                    for (boolean shared : new boolean[]{false,true}) {
                        if (!canLock(channel, shared)) {
                            continue;
                        }
                        final FileLock lock;
                        try {
                            lock = channel.tryLock(position,size,shared);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        assertNotNull(lock);
                        assertTrue(lock.isValid());
                        assertEquals(position, lock.position());
                        assertEquals(size, lock.size());
                        assertEquals(shared, lock.isShared());
                        try {
                            lock.release();
                        } catch (IOException e) {
                            assertTrue(false);
                        }
                        assertFalse(lock.isValid());
                    }
                }
            }
            if (canLock(channel, false)) {
                /*
                 * No longer valid after closing.
                 */
                final FileLock lock;
                try {
                    lock = lockOrTry(isTryLock, channel, 0L, 1L, false);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                assertTrue(lock.isValid());
                close(channel);
                assertFalse(lock.isValid());
            }
        }
        
        /*
         * conflicts (i.e. overlap and at least one exclusive)
         * 
         * If tryLock and conflicts with alien, returns null.
         * If lock and conflicts with alien, blocks.
         */
        
        for (MockFileChannel channel : newChannelsVirtual()) {
            if (!(channel.getReadable() && channel.getWritable())) {
                // Not bothering with limited rights.
                continue;
            }
            final FileLock lock_0_9_exclusive = acquire(channel,0,10,false);
            final FileLock lock_10_19_shared = acquire(channel,10,10,true);

            /*
             * overlaping exclusive non-alien : conflict
             */
            
            for (boolean shared : new boolean[]{false,true}) {
                try {
                    lockOrTry(isTryLock, channel, 5, 1, shared);
                    assertTrue(false);
                } catch (OverlappingFileLockException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
            
            /*
             * overlaping exclusive alien : conflict
             */
            
            for (boolean shared : new boolean[]{false,true}) {
                final FileLock alienLock_20_29_exclusive = acquireAlien(channel,20,10,false);
                final AtomicBoolean completed = new AtomicBoolean();
                if (isTryLock) {
                    // will return null
                } else {
                    // will block
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Unchecked.sleepMS(TOLERANCE_MS);
                            assertFalse(completed.get());
                            release(alienLock_20_29_exclusive);
                        }
                    }).start();
                }

                try {
                    FileLock lock = lockOrTry(isTryLock, channel, 25, 1, shared);
                    completed.set(true);
                    if (isTryLock) {
                        assertNull(lock);
                    } else {
                        assertNotNull(lock);
                        release(lock);
                    }
                } catch (IOException e) {
                    assertTrue(false);
                }

                if (isTryLock) {
                    release(alienLock_20_29_exclusive);
                }
            }

            /*
             * overlaping shared non-alien : conflict only if exclusive
             */

            {
                try {
                    lockOrTry(isTryLock, channel, 15, 1, false);
                    assertTrue(false);
                } catch (OverlappingFileLockException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }

                final FileLock lock;
                try {
                    lock = lockOrTry(isTryLock, channel, 15, 1, true);
                    assertNotNull(lock);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                release(lock);
            }
            
            /*
             * overlaping shared alien : conflict only if exclusive
             */

            {
                final FileLock alienLock_30_39_shared = acquireAlien(channel,30,10,true);
                
                final AtomicBoolean completed = new AtomicBoolean();
                if (isTryLock) {
                    // will return null
                } else {
                    // will block
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Unchecked.sleepMS(TOLERANCE_MS);
                            assertFalse(completed.get());
                            release(alienLock_30_39_shared);
                        }
                    }).start();
                }
                
                try {
                    FileLock lock = lockOrTry(isTryLock, channel, 35, 1, false);
                    completed.set(true);
                    if (isTryLock) {
                        assertNull(lock);
                    } else {
                        assertNotNull(lock);
                        release(lock);
                    }
                } catch (IOException e) {
                    assertTrue(false);
                }

                final FileLock lock;
                try {
                    lock = lockOrTry(isTryLock, channel, 35, 1, true);
                    assertNotNull(lock);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                release(lock);
                
                release(alienLock_30_39_shared);
            }

            /*
             * 
             */
            
            release(lock_0_9_exclusive);
            release(lock_10_19_shared);
        }
    }
    
    public void test_read_ByteBuffer_exceptions() {
        for (MockFileChannel src : newChannelsVirtual()) {
            InterfaceMockBuffer srcBuffer = src.getBackingMockBuffer();
            ByteBuffer dst = ByteBuffer.allocate(10);
            
            position(src,BIG-10);
            srcBuffer.limit(BIG);
            dst.position(3);
            dst.limit(5);
            
            final long srcPos = position(src);
            final long srcSize = size(src);
            final int dstPos = dst.position();
            final int dstLim = dst.limit();
            
            /*
             * open
             */
            
            if (src.getReadable()) {
                try {
                    src.read((ByteBuffer)null);
                    assertTrue(false);
                } catch (NullPointerException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            } else {
                try {
                    src.read(dst);
                    assertTrue(false);
                } catch (NonReadableChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
                try {
                    src.read((ByteBuffer)null);
                    assertTrue(false);
                } catch (NonReadableChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
            
            assertEquals(srcPos, position(src));
            assertEquals(srcSize, size(src));
            assertEquals(dstPos, dst.position());
            assertEquals(dstLim, dst.limit());
            
            /*
             * closed
             */
            
            close(src);
            
            try {
                src.read(dst);
                assertTrue(false);
            } catch (ClosedChannelException e) {
                // ok
            } catch (IOException e) {
                assertTrue(false);
            }
            try {
                src.read((ByteBuffer)null);
                assertTrue(false);
            } catch (ClosedChannelException e) {
                // ok
            } catch (IOException e) {
                assertTrue(false);
            }
        }
    }
    
    public void test_read_ByteBuffer_long_exceptions() {
        for (MockFileChannel src : newChannelsVirtual()) {
            InterfaceMockBuffer srcBuffer = src.getBackingMockBuffer();
            ByteBuffer dst = ByteBuffer.allocate(10);
            
            position(src,BIG-10);
            srcBuffer.limit(BIG);
            dst.position(3);
            dst.limit(5);
            
            final long srcPos = position(src);
            final long srcSize = size(src);
            final int dstPos = dst.position();
            final int dstLim = dst.limit();
            
            /*
             * open
             */
            
            try {
                src.read((ByteBuffer)null,0L);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            } catch (IOException e) {
                assertTrue(false);
            }
            for (long srcPosArg : new long[]{Long.MIN_VALUE,-1}) {
                try {
                    src.read(dst,srcPosArg);
                    assertTrue(false);
                } catch (IllegalArgumentException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
            if (!src.getReadable()) {
                for (long srcPosArg : new long[]{0,Long.MAX_VALUE}) {
                    try {
                        src.read(dst,srcPosArg);
                        assertTrue(false);
                    } catch (NonReadableChannelException e) {
                        // ok
                    } catch (IOException e) {
                        assertTrue(false);
                    }
                }
            }
            
            assertEquals(srcPos, position(src));
            assertEquals(srcSize, size(src));
            assertEquals(dstPos, dst.position());
            assertEquals(dstLim, dst.limit());
            
            /*
             * closed
             */
            
            close(src);
            
            if (src.getReadable()) {
                try {
                    src.read(dst,0L);
                    assertTrue(false);
                } catch (ClosedChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            } else {
                try {
                    src.read(dst,0L);
                    assertTrue(false);
                } catch (NonReadableChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
        }
    }

    public void test_read_arrayOfByteBuffer_int_int_exceptions() {
        for (MockFileChannel src : newChannelsVirtual()) {
            InterfaceMockBuffer srcBuffer = src.getBackingMockBuffer();
            
            final long srcPos = BIG-10;
            position(src,srcPos);
            srcBuffer.limit(BIG);
            
            for (boolean closed : new boolean[]{false,true}) {
                if (closed) {
                    close(src);
                }
                
                try {
                    src.read((ByteBuffer[])null,-1,-1);
                    assertTrue(false);
                } catch (NullPointerException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }

                for (int offset : new int[]{Integer.MIN_VALUE,-1,0,1,2,3,Integer.MAX_VALUE}) {
                    for (int length : new int[]{Integer.MIN_VALUE,-1,0,1,2,3,Integer.MAX_VALUE}) {
                        for (boolean nullBB : new boolean[]{false,true}) {
                            if (!closed) {
                                // reset pos
                                position(src,srcPos);
                            }
                            
                            // null at first pos, because late ByteBuffers might not be used
                            // if src gets empty.
                            final ByteBuffer[] dsts = new ByteBuffer[]{(nullBB ? null : ByteBuffer.allocate(10)),ByteBuffer.allocate(10)};
                            boolean consistent = consistentBounds(dsts.length, offset, length);
                            final Class<?> expectedClass;
                            if (consistent) {
                                if (closed) {
                                    expectedClass = ClosedChannelException.class;
                                } else {
                                    if (src.getReadable()) {
                                        if (nullBB && (offset == 0) && (length > 0)) {
                                            expectedClass = NullPointerException.class;
                                        } else {
                                            expectedClass = null;
                                        }
                                    } else {
                                        expectedClass = NonReadableChannelException.class;
                                    }
                                }
                            } else {
                                expectedClass = IndexOutOfBoundsException.class;
                            }

                            try {
                                src.read(dsts, offset, length);
                                assertNull(expectedClass);
                            } catch (Exception e) {
                                assertEquals(expectedClass, e.getClass());
                            }
                        }
                    }
                }
            }
        }
    }

    public void test_read_ByteBuffer_copies() {
        this.test_reading(false, false);
    }

    public void test_read_ByteBuffer_long_copies() {
        this.test_reading(false, true);
    }

    public void test_read_arrayOfByteBuffer_int_int_copies() {
        this.test_reading(true, false);
    }

    public void test_reading(
            boolean multiDst,
            boolean srcPosSpecified) {
        if (multiDst && srcPosSpecified) {
            throw new IllegalArgumentException();
        }
        
        for (MockFileChannel src : newChannelsVirtual()) {
            if (!src.getReadable()) {
                continue;
            }
            
            final long srcPosNotToUse = (BIG>>4);
            
            InterfaceMockBuffer srcBuffer = src.getBackingMockBuffer();
            final int dstCap = 10;
            final int dstLim = 5;
            final int dstPos = 3;
            final int dstRem = dstLim - dstPos;
            ByteBuffer dst = ByteBuffer.allocate(dstCap);
            // {srcPos,srcLim,ret}
            for (long[] values : new long[][]{
                    // eos (-1)
                    new long[]{10,10,-1},
                    new long[]{11,10,-1},
                    // read limited by dst
                    new long[]{SML,SML+dstRem+1,dstRem},
                    // read limited by src
                    new long[]{SML,SML+dstRem-1,dstRem-1}}) {
                for (long offset : new long[]{0,BIG}) {
                    final long srcPos = values[0] + offset;
                    final long srcLim = values[1] + offset;
                    final int expectedRet;
                    if (src.getAppendMode() && (!srcPosSpecified)) {
                        expectedRet = -1;
                    } else {
                        expectedRet = NumbersUtils.asInt(values[2]);
                    }

                    srcBuffer.limit(srcLim);
                    if (srcPosSpecified) {
                        position(src, srcPosNotToUse);
                    } else {
                        position(src, srcPos);
                    }
                    dst.limit(dstLim);
                    dst.position(dstPos);

                    // No need to fill src, which always returns (byte)position.
                    fill(dst);

                    long ret = Long.MIN_VALUE;
                    try {
                        if (srcPosSpecified) {
                            ret = src.read(dst, srcPos);
                        } else {
                            if (multiDst) {
                                ret = readIntoMultipleBBAndCheck(src, srcBuffer, dst);
                            } else {
                                ret = src.read(dst);
                            }
                        }
                        // ok
                    } catch (IOException e) {
                        assertTrue(false);
                    }
                    assertEquals(expectedRet, ret);
                    final int nRead = Math.max(0,expectedRet);
                    final long expectedSrcFinalPos;
                    if (src.getAppendMode()) {
                        expectedSrcFinalPos = size(src);
                    } else {
                        if (srcPosSpecified) {
                            expectedSrcFinalPos = srcPosNotToUse;
                        } else {
                            expectedSrcFinalPos = srcPos + nRead;
                        }
                    }
                    assertEquals(expectedSrcFinalPos, position(src));
                    assertEquals(dstPos + nRead, dst.position());
                    checkChannelReadCopy(srcBuffer, srcPos, dst, dstPos, expectedRet);
                }
            }
        }
    }
    
    public void test_write_ByteBuffer_exceptions() {
        for (MockFileChannel dst : newChannelsPureVirtual()) {
            InterfaceMockBuffer dstBuffer = dst.getBackingMockBuffer();
            ByteBuffer src = ByteBuffer.allocate(10);
            
            src.position(3);
            src.limit(5);
            position(dst,BIG-10);
            dstBuffer.limit(BIG);
            
            final int srcPos = src.position();
            final int srcLim = src.limit();
            final long dstPos = position(dst);
            final long dstSize = size(dst);
            
            /*
             * open
             */
            
            if (dst.getWritable()) {
                try {
                    dst.write((ByteBuffer)null);
                    assertTrue(false);
                } catch (NullPointerException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            } else {
                try {
                    dst.write(src);
                    assertTrue(false);
                } catch (NonWritableChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
                try {
                    dst.write((ByteBuffer)null);
                    assertTrue(false);
                } catch (NonWritableChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
            
            assertEquals(srcPos, src.position());
            assertEquals(srcLim, src.limit());
            assertEquals(dstPos, position(dst));
            assertEquals(dstSize, size(dst));
            
            /*
             * closed
             */
            
            close(dst);
            
            try {
                dst.write(src);
                assertTrue(false);
            } catch (ClosedChannelException e) {
                // ok
            } catch (IOException e) {
                assertTrue(false);
            }
            try {
                dst.write((ByteBuffer)null);
                assertTrue(false);
            } catch (ClosedChannelException e) {
                // ok
            } catch (IOException e) {
                assertTrue(false);
            }
        }
    }
    
    public void test_write_ByteBuffer_long_exceptions() {
        for (MockFileChannel dst : newChannelsPureVirtual()) {
            InterfaceMockBuffer dstBuffer = dst.getBackingMockBuffer();
            ByteBuffer src = ByteBuffer.allocate(10);
            
            src.position(3);
            src.limit(5);
            position(dst,BIG-10);
            dstBuffer.limit(BIG);
            
            final int srcPos = src.position();
            final int srcLim = src.limit();
            final long dstPos = position(dst);
            final long dstSize = size(dst);
            
            /*
             * open
             */
            
            try {
                dst.write((ByteBuffer)null,0L);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            } catch (IOException e) {
                assertTrue(false);
            }
            for (long dstPosArg : new long[]{Long.MIN_VALUE,-1}) {
                try {
                    dst.write(src,dstPosArg);
                    assertTrue(false);
                } catch (IllegalArgumentException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
            if (!dst.getWritable()) {
                for (long dstPosArg : new long[]{0,Long.MAX_VALUE}) {
                    try {
                        dst.write(src,dstPosArg);
                        assertTrue(false);
                    } catch (NonWritableChannelException e) {
                        // ok
                    } catch (IOException e) {
                        assertTrue(false);
                    }
                }
            }
            
            assertEquals(srcPos, src.position());
            assertEquals(srcLim, src.limit());
            assertEquals(dstPos, position(dst));
            assertEquals(dstSize, size(dst));
            
            /*
             * closed
             */
            
            close(dst);
            
            if (dst.getWritable()) {
                try {
                    dst.write(src,0L);
                    assertTrue(false);
                } catch (ClosedChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            } else {
                try {
                    dst.write(src,0L);
                    assertTrue(false);
                } catch (NonWritableChannelException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
        }
    }

    public void test_write_arrayOfByteBuffer_int_int_exceptions() {
        for (MockFileChannel dst : newChannelsPureVirtual()) {
            InterfaceMockBuffer dstBuffer = dst.getBackingMockBuffer();
            
            final long dstPos = BIG-10;
            position(dst,dstPos);
            dstBuffer.limit(BIG);
            
            for (boolean closed : new boolean[]{false,true}) {
                if (closed) {
                    close(dst);
                }
                
                try {
                    dst.write((ByteBuffer[])null,-1,-1);
                    assertTrue(false);
                } catch (NullPointerException e) {
                    // ok
                } catch (IOException e) {
                    assertTrue(false);
                }

                for (int offset : new int[]{Integer.MIN_VALUE,-1,0,1,2,3,Integer.MAX_VALUE}) {
                    for (int length : new int[]{Integer.MIN_VALUE,-1,0,1,2,3,Integer.MAX_VALUE}) {
                        for (boolean nullBB : new boolean[]{false,true}) {
                            if (!closed) {
                                // reset pos
                                position(dst,dstPos);
                            }
                            
                            final ByteBuffer[] srcs = new ByteBuffer[]{(nullBB ? null : ByteBuffer.allocate(10)),ByteBuffer.allocate(10)};
                            boolean consistent = consistentBounds(srcs.length, offset, length);
                            final Class<?> expectedClass;
                            if (consistent) {
                                if (closed) {
                                    expectedClass = ClosedChannelException.class;
                                } else {
                                    if (dst.getWritable()) {
                                        if (nullBB && (offset == 0) && (length > 0)) {
                                            expectedClass = NullPointerException.class;
                                        } else {
                                            expectedClass = null;
                                        }
                                    } else {
                                        expectedClass = NonWritableChannelException.class;
                                    }
                                }
                            } else {
                                expectedClass = IndexOutOfBoundsException.class;
                            }

                            try {
                                dst.write(srcs, offset, length);
                                assertNull(expectedClass);
                            } catch (Exception e) {
                                assertEquals(expectedClass, e.getClass());
                            }
                        }
                    }
                }
            }
        }
    }

    public void test_write_ByteBuffer_copies() {
        this.test_writing(false, false);
    }

    public void test_write_ByteBuffer_long_copies() {
        this.test_writing(false, true);
    }

    public void test_write_arrayOfByteBuffer_int_int_copies() {
        this.test_writing(true, false);
    }

    public void test_writing(
            boolean multiSrc,
            boolean dstPosSpecified) {
        if (multiSrc && dstPosSpecified) {
            throw new IllegalArgumentException();
        }
        
        for (MockFileChannel dst : newChannelsVirtual()) {
            if (!dst.getWritable()) {
                continue;
            }
            
            final long dstPosNotToUse = (BIG>>4);
            
            VirtualMockBuffer dstBuffer = (VirtualMockBuffer)dst.getBackingMockBuffer();
            final int srcCap = 10;
            final int srcLim = 7;
            ByteBuffer src = ByteBuffer.allocate(srcCap);
            // {srcPos,dstPos(unless append mode)}
            for (long[] values : new long[][]{
                    // nothing to write
                    new long[]{srcLim,10},
                    // write
                    new long[]{3,4},
                    new long[]{1,5}}) {
                for (long offset : new long[]{0,BIG}) {
                    final int srcPos = NumbersUtils.asInt(values[0]);
                    final int srcRem = srcLim - srcPos;
                    final long dstPos = values[1] + offset;
                    
                    src.limit(srcLim);
                    src.position(srcPos);
                    
                    final long dstInitialPos;
                    if (dstPosSpecified) {
                        dstInitialPos = dstPosNotToUse;
                    } else {
                        if (dst.getAppendMode()) {
                            dstInitialPos = dstPosNotToUse;
                        } else {
                            dstInitialPos = dstPos;
                        }
                    }
                    position(dst, dstInitialPos);
                    
                    final long expectedDstCopyPos;
                    if (dstPosSpecified) {
                        expectedDstCopyPos = dstPos;
                    } else {
                        if (dst.getAppendMode()) {
                            try {
                                // To avoid overflow, default size being Long.MAX_VALUE.
                                dst.truncate(BIG);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            expectedDstCopyPos = size(dst);
                        } else {
                            expectedDstCopyPos = dstInitialPos;
                        }
                    }

                    fill(src);
                    // No need to fill dst, which always returns (byte)position
                    // where no byte has been put.

                    dstBuffer.deleteLocalBuffer();
                    
                    long nWritten = Long.MIN_VALUE;
                    try {
                        if (dstPosSpecified) {
                            nWritten = dst.write(src, dstPos);
                        } else {
                            if (multiSrc) {
                                nWritten = writeFromMultipleBBAndCheck(src, dst, dstBuffer);
                            } else {
                                nWritten = dst.write(src);
                            }
                        }
                        // ok
                    } catch (IOException e) {
                        assertTrue(false);
                    }
                    assertEquals(srcRem, nWritten);
                    assertEquals(srcPos + nWritten, src.position());
                    final long expectedDstFinalPos;
                    if (dst.getAppendMode()) {
                        expectedDstFinalPos = size(dst);
                    } else {
                        if (nWritten == 0) {
                            expectedDstFinalPos = dstInitialPos;
                        } else {
                            if (dstPosSpecified) {
                                expectedDstFinalPos = dstInitialPos;
                            } else {
                                expectedDstFinalPos = expectedDstCopyPos + nWritten;
                            }
                        }
                    }
                    assertEquals(expectedDstFinalPos, position(dst));
                    checkChannelWriteCopy(src, srcPos, dstBuffer, expectedDstCopyPos, srcRem);
                }
            }
        }
    }
    
    public void test_transferTo_long_long_WritableByteChannel_exceptions() {
        for (MockFileChannel src : newChannelsVirtual()) {
            for (boolean srcClosed : new boolean[]{false,true}) {
                if (srcClosed) {
                    close(src);
                }
                for (WritableByteChannel dst : newWritableChannelsAndNull()) {
                    for (boolean dstClosed : new boolean[]{false,true}) {
                        if (dstClosed && (dst != null)) {
                            close(dst);
                        }

                        for (long srcPosBase : new long[]{Long.MIN_VALUE,-1,0,1,2}) {
                            for (long count : new long[]{Long.MIN_VALUE,-1,0,1,2}) {
                                for (long offset : new long[]{0,BIG}) {
                                    final long srcPos = srcPosBase + offset;

                                    final boolean consistent = (srcPos >= 0) && (count >= 0);
                                    final Class<?> expectedClass;
                                    if (srcClosed) {
                                        expectedClass = ClosedChannelException.class;
                                    }  else if (dst == null) {
                                        expectedClass = NullPointerException.class;
                                    } else if (dstClosed && (dst != null)) {
                                        expectedClass = ClosedChannelException.class;
                                    } else if (!src.getReadable()) {
                                        expectedClass = NonReadableChannelException.class;
                                    } else if ((dst instanceof MockFileChannel)
                                            && (!((MockFileChannel)dst).getWritable())) {
                                        expectedClass = NonWritableChannelException.class;
                                    } else if (!consistent) {
                                        expectedClass = IllegalArgumentException.class;
                                    } else {
                                        expectedClass = null;
                                    }

                                    // To avoid UnsupportedOperationException
                                    // if reaching local buffer end.
                                    if (dst instanceof MockFileChannel) {
                                        MockFileChannel mdst = (MockFileChannel)dst;
                                        ((VirtualMockBuffer)mdst.getBackingMockBuffer()).deleteLocalBuffer();
                                        if (mdst.getAppendMode()) {
                                            // Else WritableByteChannel.write(ByteBuffer), called by transfer method,
                                            // does pos=lim=Long.MAX_VALUE, and the transfert overflows.
                                            mdst.getBackingMockBuffer().limit(BIG);
                                        }
                                    }
                                    
                                    try {
                                        src.transferTo(srcPos, count, dst);
                                        assertNull(expectedClass);
                                    } catch (Exception e) {
                                        assertEquals(expectedClass, e.getClass());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void test_transferFrom_WritableByteChannel_long_long_exceptions() {
        for (MockFileChannel dst : newChannelsVirtual()) {
            for (boolean dstClosed : new boolean[]{false,true}) {
                if (dstClosed) {
                    close(dst);
                }
                for (ReadableByteChannel src : newReadableChannelsAndNull()) {
                    for (boolean srcClosed : new boolean[]{false,true}) {
                        if (srcClosed && (src != null)) {
                            close(src);
                        }

                        for (long dstPosBase : new long[]{Long.MIN_VALUE,-1,0,1,2}) {
                            for (long count : new long[]{Long.MIN_VALUE,-1,0,1,2}) {
                                for (long offset : new long[]{0,BIG}) {
                                    final long dstPos = dstPosBase + offset;

                                    final boolean consistent = (dstPos >= 0) && (count >= 0);
                                    final Class<?> expectedClass;
                                    if (dstClosed) {
                                        expectedClass = ClosedChannelException.class;
                                    }  else if (src == null) {
                                        expectedClass = NullPointerException.class;
                                    } else if (srcClosed && (src != null)) {
                                        expectedClass = ClosedChannelException.class;
                                    } else if (!dst.getWritable()) {
                                        expectedClass = NonWritableChannelException.class;
                                    } else if (!consistent) {
                                        expectedClass = IllegalArgumentException.class;
                                    } else if ((src instanceof MockFileChannel)
                                            && (!((MockFileChannel)src).getReadable())) {
                                        expectedClass = NonReadableChannelException.class;
                                    } else {
                                        expectedClass = null;
                                    }

                                    // To avoid UnsupportedOperationException
                                    // if reaching local buffer end.
                                    ((VirtualMockBuffer)dst.getBackingMockBuffer()).deleteLocalBuffer();
                                    
                                    try {
                                        dst.transferFrom(src, dstPos, count);
                                        assertNull(expectedClass);
                                    } catch (Exception e) {
                                        assertEquals(expectedClass, e.getClass());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void test_transferTo_long_long_WritableByteChannel_copies() {
        for (MockFileChannel src : newChannelsVirtual()) {
            if (!src.getReadable()) {
                continue;
            }

            final int dstCap = DEFAULT_CAPACITY;
            final MyWritableByteChannel dst = new MyWritableByteChannel(dstCap);
            final ByteBuffer dstBuffer = dst.content.getBackingByteBuffer();
            
            final int dstPos = 3;
            
            // Actual src position.
            final long srcPosNotToUse = (BIG>>4);

            for (long srcPosBase : new long[]{0,1,2}) {
                for (int srcRemFromPos : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                    for (int dstRem : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                        for (int count : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                            for (long srcPosOffset : new long[]{0,BIG}) {
                                final long srcPos = srcPosBase + srcPosOffset;

                                final long srcLim = srcPos + srcRemFromPos;
                                final int dstLim = dstPos + dstRem;
                                
                                src.getBackingMockBuffer().limit(srcLim);
                                position(src,srcPosNotToUse);
                                dstBuffer.limit(dstLim);
                                dstBuffer.position(dstPos);
                                
                                final int expectedNTransfered = Math.min(Math.min(srcRemFromPos, dstRem), count);
                                
                                // No need to fill src, which always returns (byte)position.
                                fill(dstBuffer);
                                
                                /*
                                 * transfer
                                 */
                                
                                long nTransfered = Integer.MIN_VALUE;
                                try {
                                    nTransfered = src.transferTo(srcPos, count, dst);
                                } catch (IOException e) {
                                    assertTrue(false);
                                }
                                
                                /*
                                 * checks
                                 */
                                
                                assertEquals(expectedNTransfered, nTransfered);
                                if (src.getAppendMode()) {
                                    assertEquals(size(src), position(src));
                                } else {
                                    assertEquals(srcPosNotToUse, position(src));
                                }
                                assertEquals(dstPos + nTransfered, dstBuffer.position());
                                
                                checkChannelReadCopy(
                                        src.getBackingMockBuffer(),
                                        srcPos,
                                        dstBuffer,
                                        dstPos,
                                        expectedNTransfered);
                            }
                        }
                    }
                }
            }
        }
    }

    public void test_transferFrom_WritableByteChannel_long_long_copies() {
        for (MockFileChannel dst : newChannelsVirtual()) {
            if (!dst.getWritable()) {
                continue;
            }
            
            VirtualMockBuffer dstBuffer = (VirtualMockBuffer)dst.getBackingMockBuffer();
            
            final int srcCap = DEFAULT_CAPACITY;
            final MyReadableByteChannel src = new MyReadableByteChannel(srcCap);
            final ByteBuffer srcBuffer = src.content.getBackingByteBuffer();
            
            final int srcPos = 3;
            
            // Actual dst position.
            final long dstPosNotToUse = (BIG>>4);
            
            for (long dstPosBase : new long[]{1,2}) {
                for (int srcRem : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                    for (int dstRemFromPos : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                        for (int count : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                            for (long dstPosOffset : new long[]{0,BIG}) {
                                final long dstPos = dstPosBase + dstPosOffset;

                                final int srcLim = srcPos + srcRem;
                                final long dstLim = dstPos + dstRemFromPos;
                                
                                srcBuffer.limit(srcLim);
                                srcBuffer.position(srcPos);
                                dst.getBackingMockBuffer().limit(dstLim);
                                position(dst,dstPosNotToUse);
                                
                                final int expectedNTransfered;
                                if (dstPos > dstLim) {
                                    expectedNTransfered = 0;
                                } else {
                                    expectedNTransfered = NumbersUtils.asInt(Math.min(srcRem, count));
                                }

                                fill(srcBuffer);
                                // No need to fill dst, which always returns (byte)position
                                // where no byte has been put.

                                dstBuffer.deleteLocalBuffer();
                                
                                /*
                                 * transfer
                                 */

                                long nTransfered = Integer.MIN_VALUE;
                                try {
                                    nTransfered = dst.transferFrom(src, dstPos, count);
                                } catch (IOException e) {
                                    assertTrue(false);
                                }
                                
                                /*
                                 * checks
                                 */
                                
                                assertEquals(expectedNTransfered, nTransfered);
                                assertEquals(srcPos + nTransfered, srcBuffer.position());
                                if ((expectedNTransfered == 0) || (dstPos > dstLim)) {
                                    assertEquals(dstLim, size(dst));
                                } else {
                                    assertEquals(Math.max(dstLim, dstPos + expectedNTransfered), size(dst));
                                }
                                if (dst.getAppendMode()) {
                                    assertEquals(size(dst), position(dst));
                                } else {
                                    assertEquals(dstPosNotToUse, position(dst));
                                }
                                
                                checkChannelWriteCopy(
                                        srcBuffer,
                                        srcPos,
                                        dst.getBackingMockBuffer(),
                                        dstPos,
                                        expectedNTransfered);
                            }
                        }
                    }
                }
            }
        }
    }
    
    public void test_transferTo_long_long_WritableByteChannel_copyToItself() {
        for (MockFileChannel channel : newChannelsVirtual()) {
            if (!(channel.getReadable() && channel.getWritable())) {
                continue;
            }
            
            final VirtualMockBuffer mb = (VirtualMockBuffer)channel.getBackingMockBuffer();
            
            final int channelInitialPos = 3;

            // dst pos sometimes <, sometimes >
            for (long srcPosBase : new long[]{channelInitialPos-1,channelInitialPos,channelInitialPos+1}) {
                for (int channelRemDst : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                    for (int count : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                        for (long srcPosOffset : new long[]{0,BIG}) {
                            final long srcPos = srcPosBase + srcPosOffset;

                            final int channelLimDst = channelInitialPos + channelRemDst;

                            mb.limit(channelLimDst);
                            position(channel,channelInitialPos);

                            // If append mode, enlarging is done for writing,
                            // but src input is computed before enlargement.
                            final long srcRemFromPos = Math.max(0, channelLimDst - srcPos);

                            final long dstPos;
                            if (channel.getAppendMode()) {
                                dstPos = channelLimDst;
                            } else {
                                dstPos = channelInitialPos;
                            }
                            
                            final int expectedNTransfered;
                            if (channel.getAppendMode()) {
                                expectedNTransfered = NumbersUtils.asInt(Math.min(srcRemFromPos, count));
                            } else {
                                expectedNTransfered = NumbersUtils.asInt(Math.min(Math.min(srcRemFromPos,channelRemDst), count));
                            }

                            // No need to fill channel, which always returns (byte)position.

                            mb.deleteLocalBuffer();
                            
                            /*
                             * transfer (might increase limit)
                             */

                            long nTransfered = Integer.MIN_VALUE;
                            try {
                                nTransfered = channel.transferTo(srcPos, count, channel);
                            } catch (IOException e) {
                                assertTrue(false);
                            }

                            /*
                             * checks
                             */

                            assertEquals(expectedNTransfered, nTransfered);
                            
                            final long expectedPos;
                            if (channel.getAppendMode()) {
                                expectedPos = size(channel);
                            } else {
                                if (nTransfered == 0) {
                                    expectedPos = channelInitialPos;
                                } else {
                                    expectedPos = dstPos + nTransfered;
                                }
                            }
                            assertEquals(expectedPos, position(channel));
                            
                            final long expectedLim;
                            if (channel.getAppendMode()) {
                                expectedLim = channelLimDst + nTransfered;
                            } else {
                                expectedLim = channelLimDst;
                            }
                            assertEquals(expectedLim, size(channel));

                            {
                                mb.limit(Long.MAX_VALUE);
                                for (int i=-1;i<=expectedNTransfered;i++) {
                                    final boolean copied = (i >= 0) && (i < expectedNTransfered);
                                    final long srcI = srcPos + i;
                                    final long dstI = dstPos + i;
                                    final byte expectedB;
                                    if (copied) {
                                        expectedB = (byte)srcI;
                                    } else {
                                        expectedB = (byte)dstI;
                                    }
                                    assertEquals(expectedB, mb.get(dstI));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void test_transferFrom_ReadableByteChannel_long_long_copyFromItself() {
        for (MockFileChannel channel : newChannelsVirtual()) {
            if (!(channel.getReadable() && channel.getWritable())) {
                continue;
            }
            
            final VirtualMockBuffer mb = (VirtualMockBuffer)channel.getBackingMockBuffer();
            
            final int channelInitialPos = 3;

            // src pos sometimes <, sometimes >
            for (long dstPosBase : new long[]{channelInitialPos-1,channelInitialPos,channelInitialPos+1}) {
                for (int channelRemSrc : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                    for (int count : new int[]{0,1,2,TMP_CHUNK_SIZE+1,2*TMP_CHUNK_SIZE+1}) {
                        for (long dstPosOffset : new long[]{0,BIG}) {
                            final long dstPos = dstPosBase + dstPosOffset;

                            final int channelLimSrc = channelInitialPos + channelRemSrc;

                            mb.limit(channelLimSrc);
                            position(channel,channelInitialPos);

                            final long srcPos = channelInitialPos;
                            
                            final int expectedNTransfered;
                            if (channel.getAppendMode() || (dstPos > channelLimSrc)) {
                                expectedNTransfered = 0;
                            } else {
                                expectedNTransfered = NumbersUtils.asInt(Math.min(channelRemSrc, count));
                            }

                            // No need to fill channel, which always returns (byte)position.

                            mb.deleteLocalBuffer();
                            
                            /*
                             * transfer (might increase limit)
                             */
                            
                            long nTransfered = Integer.MIN_VALUE;
                            try {
                                nTransfered = channel.transferFrom(channel, dstPos, count);
                            } catch (IOException e) {
                                assertTrue(false);
                            }

                            /*
                             * checks
                             */

                            assertEquals(expectedNTransfered, nTransfered);
                            
                            final long expectedPos;
                            if (channel.getAppendMode()) {
                                expectedPos = size(channel);
                            } else {
                                if (nTransfered == 0) {
                                    expectedPos = channelInitialPos;
                                } else {
                                    expectedPos = srcPos + nTransfered;
                                }
                            }
                            assertEquals(expectedPos, position(channel));
                            
                            final long expectedLim = ((expectedNTransfered == 0) ? channelLimSrc : Math.max(channelLimSrc, dstPos + expectedNTransfered));
                            assertEquals(expectedLim, size(channel));
                            
                            {
                                mb.limit(Long.MAX_VALUE);
                                for (int i=-1;i<=expectedNTransfered;i++) {
                                    final boolean copied = (i >= 0) && (i < expectedNTransfered);
                                    final long srcI = srcPos + i;
                                    final long dstI = dstPos + i;
                                    final byte expectedB;
                                    if (copied) {
                                        expectedB = (byte)srcI;
                                    } else {
                                        expectedB = (byte)dstI;
                                    }
                                    assertEquals(expectedB, mb.get(dstI));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void test_force_boolean() {
        for (MockFileChannel channel : newChannelsVirtual()) {
            for (boolean closed : new boolean[]{false,true}) {
                if (closed) {
                    close(channel);
                }
                for (boolean metaData : new boolean[]{false,true}) {
                    if (closed) {
                        try {
                            channel.force(metaData);
                            assertTrue(false);
                        } catch (ClosedChannelException e) {
                            // ok
                        } catch (IOException e) {
                            assertTrue(false);
                        }
                    } else {
                        try {
                            channel.force(metaData);
                        } catch (IOException e) {
                            assertTrue(false);
                        }
                    }
                }
            }
        }
    }

    public void test_map_MapMode_long_long() {
        for (MockFileChannel channel : newChannelsVirtual()) {
            for (boolean closed : new boolean[]{false,true}) {
                if (closed) {
                    close(channel);
                }
                
                try {
                    channel.map(MapMode.READ_ONLY, 0L, 1L);
                    assertTrue(false);
                } catch (UnsupportedOperationException e) {
                    // ok
                }
            }
        }
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private static FileLock lockOrTryAlien(boolean isTryLock, MockFileChannel channel, long position, long size, boolean shared) throws IOException {
        if (isTryLock) {
            return channel.tryLockAlien(position,size,shared);
        } else {
            return channel.lockAlien(position,size,shared);
        }
    }

    private static FileLock lockOrTry(boolean isTryLock, FileChannel channel, long position, long size, boolean shared) throws IOException {
        if (isTryLock) {
            return channel.tryLock(position,size,shared);
        } else {
            return channel.lock(position,size,shared);
        }
    }

    private static FileLock acquireAlien(MockFileChannel channel, long position, long size, boolean shared) {
        try {
            return channel.lockAlien(position,size,shared);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static FileLock acquire(FileChannel channel, long position, long size, boolean shared) {
        try {
            return channel.lock(position,size,shared);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void release(FileLock lock) {
        try {
            lock.release();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static long position(FileChannel channel) {
        try {
            return channel.position();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static void position(FileChannel channel, long pos) {
        try {
            channel.position(pos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static long size(FileChannel channel) {
        try {
            return channel.size();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static void close(Channel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fills with (byte)position.
     */
    private static void fill(ByteBuffer buffer) {
        final int limit = buffer.limit();
        buffer.limit(buffer.capacity());
        for (int i=0;i<buffer.capacity();i++) {
            buffer.put(i, (byte)i);
        }
        buffer.limit(limit);
    }
    
    private static void checkChannelReadCopy(
            InterfaceMockBuffer srcBuffer,
            long srcPos,
            ByteBuffer dst,
            int dstPos,
            int nCopied) {
        /*
         * Initially:
         * src = [0,1,2,3,...,127,-128,-127,-126,...]
         * dst = [0,1,2,3,...,127,-128,-127,-126,...]
         * Checking copied range, and adjacent bytes.
         */
        final long srcLim = srcBuffer.limit();
        final int dstLim = dst.limit();
        srcBuffer.limit(Long.MAX_VALUE);
        dst.limit(dst.capacity());
        for (int i=-1;i<=nCopied;i++) {
            final boolean copied = (i >= 0) && (i < nCopied);
            final long srcI = (srcPos + i);
            final int dstI = (dstPos + i);
            final byte expectedDstIValue;
            if (copied) {
                expectedDstIValue = (byte)srcI;
            } else {
                expectedDstIValue = (byte)dstI;
            }
            assertEquals(expectedDstIValue, dst.get(dstI));
        }
        srcBuffer.limit(srcLim);
        dst.limit(dstLim);
    }

    private static void checkChannelWriteCopy(
            ByteBuffer src,
            int srcPos,
            InterfaceMockBuffer dstBuffer,
            long dstPos,
            int nCopied) {
        /*
         * Initially:
         * src = [0,1,2,3,...,127,-128,-127,-126,...]
         * dst = [0,1,2,3,...,127,-128,-127,-126,...]
         * Checking copied range, with adjacent bytes.
         */
        final int srcLim = src.limit();
        final long dstLim = dstBuffer.limit();
        src.limit(src.capacity());
        dstBuffer.limit(Long.MAX_VALUE);
        for (int i=-1;i<=nCopied;i++) {
            final boolean copied = (i >= 0) && (i < nCopied);
            final int srcI = (srcPos + i);
            final long dstI = (dstPos + i);
            final byte expectedDstIValue;
            if (copied) {
                expectedDstIValue = (byte)srcI;
            } else {
                expectedDstIValue = (byte)dstI;
            }
            assertEquals(expectedDstIValue, dstBuffer.get(dstI));
        }
        src.limit(srcLim);
        dstBuffer.limit(dstLim);
    }

    /**
     * @return FileChannel.read(ByteBuffer[],int,int) result.
     */
    private static long readIntoMultipleBBAndCheck(
            final FileChannel src,
            final InterfaceMockBuffer srcBuffer,
            final ByteBuffer dst) throws IOException {
        final long srcPos = position(src);
        final int dstLim = dst.limit();
        final int dstPos = dst.position();
        final int dstRem = dstLim - dstPos;
        
        final int dst1Rem;
        final int dst2Rem;
        if (dstRem == 0) {
            dst1Rem = 0;
            dst2Rem = 0;
        } else if (dstRem == 1) {
            dst1Rem = 1;
            dst2Rem = 0;
        } else {
            dst1Rem = dstRem-1;
            dst2Rem = 1;
        }

        /*
         * copying into dst1 and dst2
         */
        
        final ByteBuffer dst1 = ByteBuffer.allocate(dst.capacity());
        final int dst1Lim = dstLim;
        final int dst1Pos = dst1Lim - dst1Rem;
        dst1.limit(dst1Lim);
        dst1.position(dst1Pos);
        
        final ByteBuffer dst2 = ByteBuffer.allocate(dst.capacity());
        final int dst2Lim = dstLim;
        final int dst2Pos = dst2Lim - dst2Rem;
        dst2.limit(dst2Lim);
        dst2.position(dst2Pos);

        fill(dst1);
        fill(dst2);
        
        /*
         * read
         */

        final long nRead = src.read(new ByteBuffer[]{null,dst1,dst2,null}, 1, 2);
        
        /*
         * checking dst1 and dst2
         */

        assertEquals(dst2Lim, dst2.limit());
        assertEquals(dst1Lim, dst1.limit());
        
        final int nReadTo1 = dst1Rem - dst1.remaining();
        final int nReadTo2 = dst2Rem - dst2.remaining();

        final int expectedNReadTo1;
        final int expectedNReadTo2;
        if (nRead <= 0) {
            expectedNReadTo1 = 0;
            expectedNReadTo2 = 0;
        } else {
            expectedNReadTo1 = NumbersUtils.asInt(Math.min(nRead, dst1Rem));
            expectedNReadTo2 = NumbersUtils.asInt(nRead - nReadTo1);
        }
        assertEquals(expectedNReadTo1, nReadTo1);
        assertEquals(expectedNReadTo2, nReadTo2);
        
        checkChannelReadCopy(
                srcBuffer,
                srcPos,
                dst1,
                dst1Pos,
                nReadTo1);
        checkChannelReadCopy(
                srcBuffer,
                srcPos + nReadTo1,
                dst2,
                dst2Pos,
                nReadTo2);
        
        /*
         * copying bytes put in dst1 and dst2, into dst
         */
        
        for (int i=0;i<nReadTo1;i++) {
            dst.put(dst1.get(dst1Pos + i));
        }
        for (int i=0;i<nReadTo2;i++) {
            dst.put(dst2.get(dst2Pos + i));
        }
        
        return nRead;
    }

    /**
     * @return FileChannel.write(ByteBuffer[],int,int) result.
     */
    private static long writeFromMultipleBBAndCheck(
            final ByteBuffer src,
            final FileChannel dst,
            final InterfaceMockBuffer dstBuffer) throws IOException {
        final int srcLim = src.limit();
        final int srcPos = src.position();
        final int srcRem = srcLim - srcPos;
        
        /*
         * putting src bytes to copy into src1 and src2
         */
        
        final int src1Rem;
        final int src2Rem;
        if (srcRem == 0) {
            src1Rem = 0;
            src2Rem = 0;
        } else if (srcRem == 1) {
            src1Rem = 1;
            src2Rem = 0;
        } else {
            src1Rem = srcRem-1;
            src2Rem = 1;
        }
        
        final ByteBuffer src1 = ByteBuffer.allocate(src.capacity());
        final int src1Lim = srcLim;
        final int src1Pos = src1Lim - src1Rem;
        src1.limit(src1Lim);
        src1.position(src1Pos);
        
        final ByteBuffer src2 = ByteBuffer.allocate(src.capacity());
        final int src2Lim = srcLim;
        final int src2Pos = src2Lim - src2Rem;
        src2.limit(src2Lim);
        src2.position(src2Pos);
        
        fill(src1);
        fill(src2);

        for (int i=0;i<src1Rem;i++) {
            src1.put(src1Pos + i, src.get());
        }
        for (int i=0;i<src2Rem;i++) {
            src2.put(src2Pos + i, src.get());
        }

        /*
         * write
         */
        
        final long nWritten = dst.write(new ByteBuffer[]{null,src1,src2,null}, 1, 2);
        
        /*
         * checking src1 and src2
         */

        assertEquals(src1Lim, src1.limit());
        assertEquals(src2Lim, src2.limit());
        
        // all written
        assertEquals(0, src1.remaining());
        assertEquals(0, src2.remaining());
        
        return nWritten;
    }

    private static boolean consistentBounds(int limit, int from, int length) {
        try {
            LangUtils.checkBounds(limit, from, length);
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    private static boolean consistentBounds(long limit, long from, long length) {
        try {
            LangUtils.checkBounds(limit, from, length);
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    private static ArrayList<MockFileChannel> newChannelsVirtual() {
        ArrayList<MockFileChannel> channels = new ArrayList<MockFileChannel>();
        for (boolean readable : new boolean[]{false,true}) {
            for (boolean writable : new boolean[]{false,true}) {
                for (boolean appendMode : new boolean[]{false,true}) {
                    VirtualMockBuffer buffer = new VirtualMockBuffer(2*DEFAULT_CAPACITY);
                    MockFileChannel channel = new MockFileChannel(buffer, readable, writable, appendMode);
                    channels.add(channel);
                }
            }
        }
        return channels;
    }

    /**
     * Channels which backing mock buffer's put methods never does anything.
     * Useful for successive puts at random locations, when not caring about
     * channel's content.
     */
    private static ArrayList<MockFileChannel> newChannelsPureVirtual() {
        ArrayList<MockFileChannel> channels = new ArrayList<MockFileChannel>();
        for (boolean readable : new boolean[]{false,true}) {
            for (boolean writable : new boolean[]{false,true}) {
                for (boolean appendMode : new boolean[]{false,true}) {
                    VirtualMockBuffer buffer = new VirtualMockBuffer(0) {
                        @Override
                        public void put(long position, byte b) {
                            // quiet
                        }
                        @Override
                        public void put(long position, ByteBuffer src) {
                            // quiet
                        }
                    };
                    MockFileChannel channel = new MockFileChannel(buffer, readable, writable, appendMode);
                    channels.add(channel);
                }
            }
        }
        return channels;
    }
    
    /**
     * @return Channels for testing methods using readable byte channels,
     *         which include null and non-readable channels so that exceptions
     *         can be tested.
     */
    private static ArrayList<ReadableByteChannel> newReadableChannelsAndNull() {
        ArrayList<ReadableByteChannel> channels = new ArrayList<ReadableByteChannel>();
        channels.add(null);
        channels.add(new MyReadableByteChannel(DEFAULT_CAPACITY));
        channels.addAll(newChannelsVirtual());
        return channels;
    }

    /**
     * @return Channels for testing methods using writable byte channels,
     *         which include null and non-writable channels so that exceptions
     *         can be tested.
     */
    private static ArrayList<WritableByteChannel> newWritableChannelsAndNull() {
        ArrayList<WritableByteChannel> channels = new ArrayList<WritableByteChannel>();
        channels.add(null);
        channels.add(new MyWritableByteChannel(DEFAULT_CAPACITY));
        channels.addAll(newChannelsVirtual());
        return channels;
    }

    private static boolean canLock(MockFileChannel channel, boolean shared) {
        if (shared && (!channel.getReadable())) {
            return false;
        }
        if ((!shared) && (!channel.getWritable())) {
            return false;
        }
        return true;
    }
}
