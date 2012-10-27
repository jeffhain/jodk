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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Doesn't lock anything, but pretends to.
 */
public class MockFileLock extends FileLock {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    protected volatile boolean released;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @param position Must be >= 0.
     * @param size Must be >= 0, and position+size must be >= 0.
     * @throws IllegalArgumentException if either position, size, or position+size is < 0.
     */
    public MockFileLock(
            FileChannel channel,
            long position,
            long size,
            boolean shared) {
        super(
                channel,
                position,
                size,
                shared);
    }
    
    /**
     * @throws ClosedChannelException if the channel that was used
     *         to acquire this lock is no longer open.
     */
    @Override
    public void release() throws IOException {
        if (!this.channel().isOpen()) {
            throw new ClosedChannelException();
        }
        this.released = true;
    }
    
    @Override
    public boolean isValid() {
        return (!this.released) && this.channel().isOpen();
    }
}
