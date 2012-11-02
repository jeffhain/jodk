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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import net.jodk.lang.NumbersUtils;

/**
 * Actual FileChannel holder, for tests.
 */
class FCHolder {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private final File file;
    private final String mode;
    private final RandomAccessFile rac;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Opens RAC in rw mode.
     */
    public FCHolder(File file) {
        this(file, "rw");
    }
    
    public FCHolder(File file, String mode) {
        this.file = file;
        this.mode = mode;
        try {
            this.rac = new RandomAccessFile(file, mode);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    
    public String getMode() {
        return this.mode;
    }
    
    public void setSize(int size) {
        try {
            final FileChannel fc = this.getFC();
            final int initialSize = NumbersUtils.asInt(fc.size());
            if (initialSize > size) {
                fc.truncate(size);
            } else if (initialSize < size) {
                final int nToAdd = size-initialSize;
                final ByteBuffer tmpSrc = ByteBuffer.allocateDirect(Math.min(nToAdd, 1024));
                writeUsingBB(fc, tmpSrc, initialSize, nToAdd);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Also fills content with (byte)position.
     */
    public void setSizeAndFill(int size) {
        try {
            final FileChannel fc = this.getFC();
            final long initialSize = fc.size();
            if (initialSize > size) {
                fc.truncate(size);
            }
            if (size > 0) {
                // Growing by chunks, not to allocate a huge ByteBuffer.
                // Multiple of 256 so that first byte = (last byte + 1) modulo 256.
                final int maxChunkSize = 4 * 256;
                final ByteBuffer tmpSrc = ByteBuffer.allocateDirect(Math.min(size, maxChunkSize));
                for (int i=0;i<tmpSrc.limit();i++) {
                    tmpSrc.put(i, (byte)i);
                }
                writeUsingBB(fc, tmpSrc, 0, size);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public FileChannel getFC() {
        return this.rac.getChannel();
    }
    
    /**
     * Does not delete the file if mode is not writable.
     */
    public void release() {
        final boolean writable = this.mode.contains("w");
        try {
            // truncate + delete, in case
            // one has less lag than the other.
            if (writable) {
                this.rac.getChannel().truncate(0);
            }
            this.rac.close();
            if (writable) {
                // Might fail if already deleted,
                // for example if used by another holder,
                // but it's ok.
                this.file.delete();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Writes count bytes from dstPos,
     * using content in [0,min(remaining_to_put,bb.capacity())].
     */
    private void writeUsingBB(
            final FileChannel fc,
            final ByteBuffer bb,
            final long dstPos,
            final int count) throws IOException {
        int nDone = 0;
        while (nDone < count) {
            final int nRemaining = (count-nDone);
            final int tmpN = Math.min(nRemaining, bb.capacity());
            bb.limit(tmpN);
            bb.position(0);
            final int tmpNWritten = fc.write(bb, nDone);
            if (tmpNWritten != tmpN) {
                throw new BufferOverflowException();
            }
            nDone += tmpN;
        }
    }
}

