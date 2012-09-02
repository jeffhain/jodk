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

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import net.jodk.lang.LangUtils;

/**
 * A read-only DataBuffer. This class extends the corresponding
 * read/write class, overriding the mutation methods to throw a
 * ReadOnlyBufferException and overriding the view-buffer methods
 * to return an instance of this class rather than of the superclass.
 * 
 * Package-private.
 */
final class DataBufferR extends DataBuffer {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    @Override
    public DataBuffer slice() {
        return this.slice_forDataBufferR();
    }

    @Override
    public DataBuffer duplicate() {
        return this.duplicate_forDataBufferR();
    }

    @Override
    public DataBuffer asReadOnlyBuffer() {
        return this.duplicate_forDataBufferR();
    }

    /*
     * 
     */
    
    @Override
    public void setBackingBuffer(byte[] buffer) {
        throw new ReadOnlyBufferException();
    }
    
    @Override
    public void setBackingBuffer(byte[] buffer, int offset, int capacity) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public void setBackingBuffer(ByteBuffer buffer) {
        throw new ReadOnlyBufferException();
    }

    /*
     * 
     */
    
    @Override
    public boolean isReadOnly() {
        return true;
    }

    /*
     * 
     */

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        // Calling super.array() first for UnsupportedOperationException
        // to be thrown if needed (thrown first by ByteBuffer.array() as well).
        super.array();
        throw new ReadOnlyBufferException();
    }

    @Override
    public int arrayOffset() {
        // Calling super.arrayOffset() first for UnsupportedOperationException
        // to be thrown if needed (thrown first by ByteBuffer.array() as well).
        super.arrayOffset();
        throw new ReadOnlyBufferException();
    }

    /*
     * 
     */

    @Override
    public DataBuffer compact() {
        throw new ReadOnlyBufferException();
    }
    
    /*
     * 
     */
    
    @Override
    public DataBuffer put(byte[] src) {
        throw new ReadOnlyBufferException();
    }
    
    @Override
    public DataBuffer put(byte[] src, int offset, int length) {
        throw new ReadOnlyBufferException();
    }

    /*
     * 
     */

    @Override
    public DataBuffer putByteAt(int index, byte value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putShortAt(int index, short value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putCharAt(int index, char value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putIntAt(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putLongAt(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putFloatAt(int index, float value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putDoubleAt(int index, double value) {
        throw new ReadOnlyBufferException();
    }

    /*
     * 
     */

    @Override
    public DataBuffer putByte(byte value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putShort(short value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putChar(char value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putInt(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putLong(long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putFloat(float value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putDouble(double value) {
        throw new ReadOnlyBufferException();
    }

    /*
     * 
     */

    @Override
    public int padByteAtBit(long bitPos) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int padLastByte() {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putBytePadding() {
        throw new ReadOnlyBufferException();
    }
    
    /*
     * 
     */

    @Override
    public DataBuffer putBit(boolean value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putBitAtBit(long bitPos, boolean value) {
        throw new ReadOnlyBufferException();
    }

    /*
     * 
     */

    @Override
    public DataBuffer putIntSigned(int value, int bitSize) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putLongSigned(long value, int bitSize) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putIntUnsigned(int value, int bitSize) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putLongUnsigned(long value, int bitSize) {
        throw new ReadOnlyBufferException();
    }

    /*
     * 
     */

    @Override
    public DataBuffer putIntSignedAtBit(long firstBitPos, int value, int bitSize) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putLongSignedAtBit(long firstBitPos, long value, int bitSize) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putIntUnsignedAtBit(long firstBitPos, int value, int bitSize) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public DataBuffer putLongUnsignedAtBit(long firstBitPos, long value, int bitSize) {
        throw new ReadOnlyBufferException();
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    protected DataBufferR(
            byte[] ba,
            int baOffset,
            ByteBuffer bb,
            long bitMark,
            long bitPosition,
            int limit,
            int capacity,
            boolean bigEndian) {
        super(
                ba,
                baOffset,
                bb,
                bitMark,
                bitPosition,
                limit,
                capacity,
                bigEndian);
    }

    protected DataBufferR(byte[] buffer) {
        super(buffer);
    }
    
    protected DataBufferR(ByteBuffer byteBuffer) {
        super(byteBuffer);
        LangUtils.azzert(byteBuffer.isReadOnly());
    }
}
