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
import java.nio.ByteOrder;

/**
 * Tab helper to work on instances of ByteBuffer.
 * 
 * Only big endian or little endian versions are used, depending on
 * whether the ByteBuffer is respectively big endian or little endian.
 * This allows not to set ByteBuffer order in these methods.
 * 
 * Package-private.
 */
final class ByteBufferBTHelper extends BaseBTHelper {

    /*
     * For put operations, we could take care to put data of highest index
     * first, or to check ByteBuffer's limit first, not to corrupt
     * previous data in case ByteBuffer's limit would have been
     * changed (by mistake), and would/should cause operation to fail.
     * But that would complicate the code, and in such a case iterative
     * operations could fail late anyway, so we just forget about it.
     */

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    public static final ByteBufferBTHelper INSTANCE = new ByteBufferBTHelper();

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    @Override
    public boolean hasArray(Object tab) {
        final ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.hasArray();
    }

    @Override
    public byte[] array(Object tab) {
        final ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.array();
    }

    @Override
    public int arrayOffset(Object tab) {
        final ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.arrayOffset();
    }

    @Override
    public void limit(Object tab, int limit) {
        final ByteBuffer buffer = (ByteBuffer)tab;
        buffer.limit(limit);
    }

    @Override
    public void order(Object tab, ByteOrder order) {
        final ByteBuffer buffer = (ByteBuffer)tab;
        buffer.order(order);
    }

    /*
     * 
     */
    
    @Override
    public void checkLimitByteIndexByteSizeIfNeeded(int limit, int firstByteIndex, int byteSize) {
        // Checked by ByteBuffer.
    }

    @Override
    public void checkLimitBitPosBitSizeIfNeeded(int limit, long firstBitPos, long bitSize) {
        // Checked by ByteBuffer.
    }

    /*
     * 
     */

    @Override
    public void put16Bits(Object tab, int index, short bits, boolean bigEndian) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putShort(index, bits);
    }

    @Override
    public void put32Bits(Object tab, int index, int bits, boolean bigEndian) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putInt(index, bits);
    }

    @Override
    public void put64Bits(Object tab, int index, long bits, boolean bigEndian) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putLong(index, bits);
    }

    @Override
    public short get16Bits(Object tab, int index, boolean bigEndian) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.getShort(index);
    }

    @Override
    public int get32Bits(Object tab, int index, boolean bigEndian) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.getInt(index);
    }

    @Override
    public long get64Bits(Object tab, int index, boolean bigEndian) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.getLong(index);
    }

    /*
     * put
     */
    
    @Override
    public void put8Bits(Object tab, int index, byte bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.put(index,bits);
    }

    @Override
    public void put16Bits_bigEndian(Object tab, int index, short bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putShort(index, bits);
    }

    @Override
    public void put16Bits_littleEndian(Object tab, int index, short bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putShort(index, bits);
    }

    @Override
    public void put24Bits_bigEndian(Object tab, int index, int bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putShort(index,(short)(bits>>8));
        buffer.put(index+2,(byte)bits);
    }

    @Override
    public void put24Bits_littleEndian(Object tab, int index, int bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putShort(index,(short)bits);
        buffer.put(index+2,(byte)(bits>>16));
    }

    @Override
    public void put32Bits_bigEndian(Object tab, int index, int bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putInt(index, bits);
    }

    @Override
    public void put32Bits_littleEndian(Object tab, int index, int bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putInt(index, bits);
    }

    @Override
    public void put40Bits_bigEndian(Object tab, int index, long bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int hi = (int)(bits>>32);
        int lo = (int)bits;
        buffer.put(index, (byte)hi);
        buffer.putInt(index+1, lo);
    }

    @Override
    public void put40Bits_littleEndian(Object tab, int index, long bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int lo = (int)bits;
        int hi = (int)(bits>>32);
        buffer.putInt(index, lo);
        buffer.put(index+4, (byte)hi);
    }

    @Override
    public void put48Bits_bigEndian(Object tab, int index, long bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int hi = (int)(bits>>32);
        int lo = (int)bits;
        buffer.putShort(index, (short)hi);
        buffer.putInt(index+2, lo);
    }

    @Override
    public void put48Bits_littleEndian(Object tab, int index, long bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int lo = (int)bits;
        int hi = (int)(bits>>32);
        buffer.putInt(index, lo);
        buffer.putShort(index+4, (short)hi);
    }

    @Override
    public void put56Bits_bigEndian(Object tab, int index, long bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int hi = (int)(bits>>32);
        int lo = (int)bits;
        buffer.put(index, (byte)(hi>>16));
        buffer.putShort(index+1, (short)hi);
        buffer.putInt(index+3, lo);
    }

    @Override
    public void put56Bits_littleEndian(Object tab, int index, long bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int lo = (int)bits;
        int hi = (int)(bits>>32);
        buffer.putInt(index, lo);
        buffer.putShort(index+4, (short)hi);
        buffer.put(index+6, (byte)(hi>>16));
    }

    @Override
    public void put64Bits_bigEndian(Object tab, int index, long bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putLong(index, bits);
    }

    @Override
    public void put64Bits_littleEndian(Object tab, int index, long bits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putLong(index, bits);
    }

    @Override
    public void put72Bits_bigEndian(Object tab, int index, long msbits, byte lsbits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.putLong(index, msbits);
        buffer.put(index+8, lsbits);
    }

    @Override
    public void put72Bits_littleEndian(Object tab, int index, long msbits, byte lsbits) {
        ByteBuffer buffer = (ByteBuffer)tab;
        buffer.put(index, lsbits);
        buffer.putLong(index+1, msbits);
    }

    /*
     * get
     */

    @Override
    public byte get8Bits(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.get(index);
    }

    @Override
    public short get8BitsAnd8Bits(Object tab, int msbitsIndex, int lsbitsIndex) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return (short)((buffer.get(msbitsIndex)<<8) | (buffer.get(lsbitsIndex)&0xFF));
    }

    @Override
    public short get16Bits_bigEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.getShort(index);
    }

    @Override
    public short get16Bits_littleEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.getShort(index);
    }

    @Override
    public int get24Bits_bigEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return (buffer.getShort(index)<<8)
                | (buffer.get(index+2)&0xFF);
    }

    @Override
    public int get24Bits_littleEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return (buffer.get(index)&0xFF)
                | (buffer.getShort(index+1)<<8);
    }

    @Override
    public int get32Bits_bigEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.getInt(index);
    }

    @Override
    public int get32Bits_littleEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.getInt(index);
    }

    @Override
    public long get40Bits_bigEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int hi = buffer.get(index);
        int lo = buffer.getInt(index+1);
        return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
    }

    @Override
    public long get40Bits_littleEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int lo = buffer.getInt(index);
        int hi = buffer.get(index+4);
        return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
    }

    @Override
    public long get48Bits_bigEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int hi = buffer.getShort(index);
        int lo = buffer.getInt(index+2);
        return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
    }

    @Override
    public long get48Bits_littleEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int lo = buffer.getInt(index);
        int hi = buffer.getShort(index+4);
        return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
    }

    @Override
    public long get56Bits_bigEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int hi = (buffer.get(index)<<16)
                | (buffer.getShort(index+1)&0xFFFF);
        int lo = buffer.getInt(index+3);
        return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
    }

    @Override
    public long get56Bits_littleEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        int lo = buffer.getInt(index);
        int hi = (buffer.getShort(index+4)&0xFFFF)
                | (buffer.get(index+6)<<16);
        return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
    }

    @Override
    public long get64Bits_bigEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.getLong(index);
    }

    @Override
    public long get64Bits_littleEndian(Object tab, int index) {
        ByteBuffer buffer = (ByteBuffer)tab;
        return buffer.getLong(index);
    }
}
