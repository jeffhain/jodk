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
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

/**
 * Interface to eventually map/unmap a FileChannel with a ByteBuffer.
 * 
 * Allows to use hacked unmapping for known FileChannel implementations
 * (Bug ID: 4724038), or to use map/unmap treatments designed for
 * a custom FileChannel implementation.
 */
interface InterfaceMBBHelper {
    
    /**
     * This method does not need to check the usual preconditions,
     * such as the channel being readable etc.
     * 
     * @param channel A FileChannel.
     * @return True if map and unmap operations are supported by this instance
     *         for the specified FileChannel and MapMode, false otherwise.
     */
    public boolean canMapAndUnmap(FileChannel channel, MapMode mode);
    
    /**
     * @param channel The FileChannel on which the mapping is to be done.
     * @param mode One of the constants READ_ONLY, READ_WRITE, or PRIVATE
     *        defined in the MapMode class, according to whether the file
     *        is to be mapped read-only, read/write, or privately (copy-on-write),
     *        respectively.
     * @param position The position within the file at which the mapped region
     *        is to start; must be non-negative.
     * @param size The size of the region to be mapped; must be non-negative and
     *        no greater than Integer.MAX_VALUE.
     * @return The mapped ByteBuffer.
     * @throws NonReadableChannelException if the mode is READ_ONLY but
     *         this channel was not opened for reading.
     * @throws NonWritableChannelException if the mode is READ_WRITE or PRIVATE
     *         but this channel was not opened for both reading and writing.
     * @throws IllegalArgumentException if the preconditions on the parameters do not hold.
     * @throws IOException if some other I/O error occurs.
     */
    public ByteBuffer map(
            FileChannel channel,
            MapMode mode,
            long position,
            long size) throws IOException;
    
    /**
     * @param channel A FileChannel.
     * @param mbb The mapped ByteBuffer to unmap.
     */
    public void unmap(FileChannel channel, ByteBuffer mbb);
}
