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
import java.nio.channels.FileChannel;

import net.jodk.lang.RethrowException;

/**
 * Provides versions of some JDK methods throwing checked exceptions,
 * that throw a wrapping RethrowException instead.
 */
public class UncheckedIO {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Calls FileChannel.size().
     * 
     * @return channel.size()
     * @throws RethrowException if an exception is thrown internally.
     */
    public static long size(FileChannel channel) {
        try {
            return channel.size();
        } catch (IOException e) {
            throw new RethrowException(e);
        }
    }

    /**
     * Calls FileChannel.position(long).
     * 
     * @return The specified channel.
     * @throws RethrowException if an exception is thrown internally.
     */
    public static FileChannel position(FileChannel channel, long newPosition) {
        try {
            return channel.position(newPosition);
        } catch (IOException e) {
            throw new RethrowException(e);
        }
    }

    /**
     * Calls FileChannel.position().
     * 
     * @return channel.position()
     * @throws RethrowException if an exception is thrown internally.
     */
    public static long position(FileChannel channel) {
        try {
            return channel.position();
        } catch (IOException e) {
            throw new RethrowException(e);
        }
    }
}
