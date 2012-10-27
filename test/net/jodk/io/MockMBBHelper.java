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
package net.jodk.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;

import net.jodk.io.mock.InterfaceMockBuffer;
import net.jodk.io.mock.MockFileChannel;
import net.jodk.lang.NumbersUtils;

/**
 * Only works for MockFileChannel instances,
 * which only allows for READ_ONLY mode.
 */
class MockMBBHelper implements InterfaceMBBHelper {
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public MockMBBHelper() {
    }

    @Override
    public boolean canMapAndUnmap(FileChannel channel, MapMode mode) {
        return (channel.getClass() == MockFileChannel.class)
        && (mode == MapMode.READ_ONLY);
    }

    @Override
    public ByteBuffer map(FileChannel channel, MapMode mode, long position, long size) throws IOException {
        if (mode != MapMode.READ_ONLY) {
            throw new IllegalArgumentException();
        }
        
        final MockFileChannel mockChannel = (MockFileChannel)channel;
        
        /*
         * code derived from sun.nio.ch.FileChannelImpl
         */

        if (!mockChannel.isOpen()) {
            throw new ClosedChannelException();
        }
        if (position < 0) {
            throw new IllegalArgumentException("Negative position");
        }
        if (position + size < 0) {
            throw new IllegalArgumentException("Position + size overflow");
        }
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Size exceeds Integer.MAX_VALUE");
        }
        if ((mode != MapMode.READ_ONLY) && !mockChannel.getWritable()) {
            // TODO throws if mode is null, unlike spec says
            throw new NonWritableChannelException();
        }
        if (!mockChannel.getReadable()) {
            // TODO throws even if not READ_ONLY (which is good), unlike spec says
            throw new NonReadableChannelException();
        }

        /*
         * eventually growing channel
         */
        
        final InterfaceMockBuffer mockBuffer = mockChannel.getBackingMockBuffer();
        if (mockChannel.size() < position + size) {
            if (!mockChannel.getWritable()) {
                throw new IOException("Channel not open for writing - cannot extend file to required size");
            }
            mockBuffer.limit(position + size);
        }
        assert(mockChannel.size() >= position + size);

        /*
         * 
         */
        
        ByteBuffer result = ByteBuffer.allocateDirect(NumbersUtils.asInt(size));
        for (int i=0;i<result.limit();i++) {
            result.put(i, mockBuffer.get(position + i));
        }
        
        if (mode == MapMode.READ_WRITE) {
            // Memory not shared with channel, so can't be in write mode.
            throw new AssertionError();
        } else if (mode == MapMode.READ_ONLY) {
            result = result.asReadOnlyBuffer();
        } else if (mode == MapMode.PRIVATE) {
            // ok
        } else {
            throw new AssertionError(mode);
        }

        return result;
    }

    @Override
    public void unmap(FileChannel channel, ByteBuffer mbb) {
        // Nothing to unmap.
    }
}