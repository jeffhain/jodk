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
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import junit.framework.TestCase;

public class MockFileLockTest extends TestCase {

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    private static class MyFileChannel extends FileChannel {
        @Override
        public int read(ByteBuffer dst) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public long position() throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public FileChannel position(long newPosition) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public long size() throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public FileChannel truncate(long size) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public void force(boolean metaData) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        protected void implCloseChannel() throws IOException {
        }
    }
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_MockFileLock_FileChannel_long_long_boolean() {
        for (FileChannel channel : new FileChannel[]{null,new MyFileChannel()}) {
            for (long position : new long[]{Long.MIN_VALUE,-1,0}) {
                for (long size : new long[]{-1,position-1}) {
                    for (boolean shared : new boolean[]{false,true}) {
                        try {
                            new MockFileLock(channel, position, size, shared);
                            assertTrue(false);
                        } catch (IllegalArgumentException e) {
                            // ok
                        }
                    }
                }
            }
        }
        
        FileChannel channel = new MyFileChannel();
        for (long position : new long[]{0L,Long.MAX_VALUE}) {
            for (boolean shared : new boolean[]{false,true}) {
                final long size = Long.MAX_VALUE - position;
                MockFileLock mock = new MockFileLock(channel, position, size, shared);
                assertSame(channel, mock.channel());
                assertEquals(position, mock.position());
                assertEquals(size, mock.size());
                assertEquals(shared, mock.isShared());
                assertTrue(mock.isValid());
            }
        }
    }
    
    public void test_release_and_isValid() {
        for (boolean closeFirst : new boolean[]{false,true}) {
            FileChannel channel = new MyFileChannel();
            MockFileLock mock = new MockFileLock(channel, 0L, Long.MAX_VALUE, false);
            
            assertTrue(mock.isValid());
            if (closeFirst) {
                try {
                    channel.close();
                } catch (IOException e) {
                    assertTrue(false);
                }
                assertFalse(mock.isValid());
                try {
                    mock.release();
                    assertTrue(false);
                } catch (IOException e) {
                    assertSame(e.getClass(), ClosedChannelException.class);
                    // ok
                }
            } else {
                try {
                    mock.release();
                } catch (IOException e) {
                    assertTrue(false);
                }
            }
            assertFalse(mock.isValid());
        }
    }
}
