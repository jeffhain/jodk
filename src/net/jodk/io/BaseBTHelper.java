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

import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;

import net.jodk.lang.LangUtils;

/**
 * This class provides generic methods to deal with byte tabs, typically simple
 * get/put operations on one to many bytes, for use by more complicated
 * (bitwise) treatments, which allows to factorize them (else they would have
 * to be copied-pasted-modified for each type of tab, which would make them
 * a bit faster, but would be ugly).
 * 
 * Default implementation works on byte arrays, and must be overriden to work
 * on other types of tabs, such as ByteBuffers (This is more efficient than
 * using an interface and generic types. Also, using specific buffer parameters
 * instead of "Object tab", which needs to be casted, doesn't seem to speed
 * things up much, and often even seems to slow things down (?)).
 * 
 * Notation: using "tab" when the type is Object,
 * and "buffer" when the type is specific.
 * 
 * Known depending classes summary:
 * - BaseBTHelper : to work on byte array's tabs
 * - ByteBufferBTHelper : to work on ByteBuffer's tabs (extends BaseBTHelper)
 * - ByteTabUtils : uses BaseBTHelper (or sub-classes) to work on Object tabs
 * - ByteArrayUtils : uses ByteTabUtils + BaseBTHelper
 * - ByteBufferUtils : uses ByteTabUtils + ByteBufferBTHelper
 * - DataBuffer : uses ByteTabUtils + (BaseBTHelper | ByteBufferBTHelper)
 * 
 * For put or get operations, bits are provided as LSBits of primitive types.
 * 
 * For get operations, out of range MSBits are set to most significant used bit.
 * This makes these treatments homogeneous with auto-cast (from byte to int,
 * int to long, etc.), and should remove the need for more masks that it adds
 * the need for.
 * 
 * For put operations, out of range MSBits can be of any value.
 * 
 * Put and get operations must throw IndexOutOfBoundException if a byte index
 * corresponding to the specified byte range is out of bounds, possibly
 * only after some data has been put (for performance reasons), for it
 * is checked by underlying byte array or ByteBuffer in practice, and
 * we don't want to enforce redundant checks.
 */
class BaseBTHelper {

    /*
     * Not providing an isReadOnly(Object) method, since this information
     * might not be held in the specified tab (byte array for example).
     */
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    public static final BaseBTHelper INSTANCE = new BaseBTHelper();

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @return True if the specified tab is backed by an accessible array
     *         and is not read-only, false otherwise.
     */
    public boolean hasArray(Object tab) {
        // byte array never read-only by itself
        return true;
    }

    /**
     * @throws UnsupportedOperationException if the specified tab is
     *         not backed by an accessible array.
     * @throws ReadOnlyBufferException if the specified tab is backed
     *         by an accessible array but is read-only.
     */
    public byte[] array(Object tab) {
        return (byte[])tab;
    }

    /**
     * @throws UnsupportedOperationException if the specified tab is
     *         not backed by an accessible array.
     * @throws ReadOnlyBufferException if the specified tab is backed
     *         by an accessible array but is read-only.
     */
    public int arrayOffset(Object tab) {
        return 0;
    }

    /**
     * Sets limit into the specified tab, if it makes use of it.
     */
    public void limit(Object tab, int limit) {
        // Doing nothing.
    }

    /**
     * Sets order into the specified tab, if it makes use of it.
     */
    public void order(Object tab, ByteOrder order) {
        // Doing nothing.
    }
    
    /*
     * Not just doing a test on limit AFTER put (or get),
     * so that one could design a helper that does (redundant)
     * check here, which would prevent (most) rogue writes.
     */
    
    /**
     * Checks bounds only if not already done by the tab on put/get operations,
     * i.e. if the tab does not hold and checks the limit, else
     * should do nothing.
     */
    public void checkLimitByteIndexByteSizeIfNeeded(int limit, int firstByteIndex, int byteSize) {
        LangUtils.checkBounds(limit, firstByteIndex, byteSize);
    }

    /**
     * Checks bounds only if not already done by the tab on put/get operations,
     * i.e. if the tab does not hold and checks the limit, else
     * should do nothing.
     */
    public void checkLimitBitPosBitSizeIfNeeded(int limit, long firstBitPos, long bitSize) {
        final int firstByteIndex = (int)(firstBitPos>>3);
        final int lastByteIndex = (int)((firstBitPos+bitSize-1)>>3);
        final int byteSize = lastByteIndex - firstByteIndex + 1;
        LangUtils.checkBounds(limit, firstByteIndex, byteSize);
    }

    /*
     * put/get methods that can be overriden
     * to avoid big endian/little endian indirection
     * for tabs that hold and use their own order,
     * and which usage (in the put/get method) is
     * identical whatever the order.
     * 
     * Methods not corresponding to primitive types
     * are marked final in case it could help to get
     * them optimized/inlined, but could remove
     * final if the need to override them occurs.
     */

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put16Bits(Object tab, int index, short bits, boolean bigEndian) {
        if (bigEndian) {
            put16Bits_bigEndian(tab, index, bits);
        } else {
            put16Bits_littleEndian(tab, index, bits);
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public final void put24Bits(Object tab, int index, int bits, boolean bigEndian) {
        if (bigEndian) {
            put24Bits_bigEndian(tab, index, bits);
        } else {
            put24Bits_littleEndian(tab, index, bits);
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put32Bits(Object tab, int index, int bits, boolean bigEndian) {
        if (bigEndian) {
            put32Bits_bigEndian(tab, index, bits);
        } else {
            put32Bits_littleEndian(tab, index, bits);
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public final void put40Bits(Object tab, int index, long bits, boolean bigEndian) {
        if (bigEndian) {
            put40Bits_bigEndian(tab, index, bits);
        } else {
            put40Bits_littleEndian(tab, index, bits);
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public final void put48Bits(Object tab, int index, long bits, boolean bigEndian) {
        if (bigEndian) {
            put48Bits_bigEndian(tab, index, bits);
        } else {
            put48Bits_littleEndian(tab, index, bits);
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public final void put56Bits(Object tab, int index, long bits, boolean bigEndian) {
        if (bigEndian) {
            put56Bits_bigEndian(tab, index, bits);
        } else {
            put56Bits_littleEndian(tab, index, bits);
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put64Bits(Object tab, int index, long bits, boolean bigEndian) {
        if (bigEndian) {
            put64Bits_bigEndian(tab, index, bits);
        } else {
            put64Bits_littleEndian(tab, index, bits);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public short get16Bits(Object tab, int index, boolean bigEndian) {
        if (bigEndian) {
            return get16Bits_bigEndian(tab, index);
        } else {
            return get16Bits_littleEndian(tab, index);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public final int get24Bits(Object tab, int index, boolean bigEndian) {
        if (bigEndian) {
            return get24Bits_bigEndian(tab, index);
        } else {
            return get24Bits_littleEndian(tab, index);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public int get32Bits(Object tab, int index, boolean bigEndian) {
        if (bigEndian) {
            return get32Bits_bigEndian(tab, index);
        } else {
            return get32Bits_littleEndian(tab, index);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public final long get40Bits(Object tab, int index, boolean bigEndian) {
        if (bigEndian) {
            return get40Bits_bigEndian(tab, index);
        } else {
            return get40Bits_littleEndian(tab, index);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public final long get48Bits(Object tab, int index, boolean bigEndian) {
        if (bigEndian) {
            return get48Bits_bigEndian(tab, index);
        } else {
            return get48Bits_littleEndian(tab, index);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public final long get56Bits(Object tab, int index, boolean bigEndian) {
        if (bigEndian) {
            return get56Bits_bigEndian(tab, index);
        } else {
            return get56Bits_littleEndian(tab, index);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long get64Bits(Object tab, int index, boolean bigEndian) {
        if (bigEndian) {
            return get64Bits_bigEndian(tab, index);
        } else {
            return get64Bits_littleEndian(tab, index);
        }
    }

    /*
     * put
     */

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified byte is out of range.
     */
    public void put8Bits(Object tab, int index, byte bits) {
        final byte[] buffer = (byte[])tab;
        try {
            buffer[index] = bits;
        } catch (ArrayIndexOutOfBoundsException e) {
            // Will throw appropriate exception.
            LangUtils.checkBounds(buffer.length, index, 1);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put16Bits_bigEndian(Object tab, int index, short bits) {
        final byte[] buffer = (byte[])tab;
        try {
            buffer[index] = (byte)(bits>>8);
            buffer[index+1] = (byte)bits;
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 2);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put16Bits_littleEndian(Object tab, int index, short bits) {
        final byte[] buffer = (byte[])tab;
        try {
            buffer[index] = (byte)bits;
            buffer[index+1] = (byte)(bits>>8);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 2);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put24Bits_bigEndian(Object tab, int index, int bits) {
        final byte[] buffer = (byte[])tab;
        try {
            buffer[index] = (byte)(bits>>16);
            buffer[index+1] = (byte)(bits>>8);
            buffer[index+2] = (byte)bits;
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 3);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put24Bits_littleEndian(Object tab, int index, int bits) {
        final byte[] buffer = (byte[])tab;
        try {
            buffer[index] = (byte)bits;
            buffer[index+1] = (byte)(bits>>8);
            buffer[index+2] = (byte)(bits>>16);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 3);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put32Bits_bigEndian(Object tab, int index, int bits) {
        final byte[] buffer = (byte[])tab;
        try {
            buffer[index] = (byte)(bits>>24);
            buffer[index+1] = (byte)(bits>>16);
            buffer[index+2] = (byte)(bits>>8);
            buffer[index+3] = (byte)bits;
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 4);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put32Bits_littleEndian(Object tab, int index, int bits) {
        final byte[] buffer = (byte[])tab;
        try {
            buffer[index] = (byte)bits;
            buffer[index+1] = (byte)(bits>>8);
            buffer[index+2] = (byte)(bits>>16);
            buffer[index+3] = (byte)(bits>>24);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 4);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put40Bits_bigEndian(Object tab, int index, long bits) {
        final byte[] buffer = (byte[])tab;
        final int hi = (int)(bits>>32);
        final int lo = (int)bits;
        try {
            buffer[index] = (byte)hi;
            buffer[index+1] = (byte)(lo>>24);
            buffer[index+2] = (byte)(lo>>16);
            buffer[index+3] = (byte)(lo>>8);
            buffer[index+4] = (byte)lo;
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 5);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put40Bits_littleEndian(Object tab, int index, long bits) {
        final byte[] buffer = (byte[])tab;
        final int lo = (int)bits;
        final int hi = (int)(bits>>32);
        try {
            buffer[index] = (byte)lo;
            buffer[index+1] = (byte)(lo>>8);
            buffer[index+2] = (byte)(lo>>16);
            buffer[index+3] = (byte)(lo>>24);
            buffer[index+4] = (byte)hi;
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 5);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put48Bits_bigEndian(Object tab, int index, long bits) {
        final byte[] buffer = (byte[])tab;
        final int hi = (int)(bits>>32);
        final int lo = (int)bits;
        try {
            buffer[index] = (byte)(hi>>8);
            buffer[index+1] = (byte)hi;
            buffer[index+2] = (byte)(lo>>24);
            buffer[index+3] = (byte)(lo>>16);
            buffer[index+4] = (byte)(lo>>8);
            buffer[index+5] = (byte)lo;
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 6);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put48Bits_littleEndian(Object tab, int index, long bits) {
        final byte[] buffer = (byte[])tab;
        final int lo = (int)bits;
        final int hi = (int)(bits>>32);
        try {
            buffer[index] = (byte)lo;
            buffer[index+1] = (byte)(lo>>8);
            buffer[index+2] = (byte)(lo>>16);
            buffer[index+3] = (byte)(lo>>24);
            buffer[index+4] = (byte)hi;
            buffer[index+5] = (byte)(hi>>8);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 6);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put56Bits_bigEndian(Object tab, int index, long bits) {
        final byte[] buffer = (byte[])tab;
        final int hi = (int)(bits>>32);
        final int lo = (int)bits;
        try {
            buffer[index] = (byte)(hi>>16);
            buffer[index+1] = (byte)(hi>>8);
            buffer[index+2] = (byte)hi;
            buffer[index+3] = (byte)(lo>>24);
            buffer[index+4] = (byte)(lo>>16);
            buffer[index+5] = (byte)(lo>>8);
            buffer[index+6] = (byte)lo;
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 7);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put56Bits_littleEndian(Object tab, int index, long bits) {
        final byte[] buffer = (byte[])tab;
        final int lo = (int)bits;
        final int hi = (int)(bits>>32);
        try {
            buffer[index] = (byte)lo;
            buffer[index+1] = (byte)(lo>>8);
            buffer[index+2] = (byte)(lo>>16);
            buffer[index+3] = (byte)(lo>>24);
            buffer[index+4] = (byte)hi;
            buffer[index+5] = (byte)(hi>>8);
            buffer[index+6] = (byte)(hi>>16);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 7);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put64Bits_bigEndian(Object tab, int index, long bits) {
        final byte[] buffer = (byte[])tab;
        final int hi = (int)(bits>>32);
        final int lo = (int)bits;
        try {
            buffer[index] = (byte)(hi>>24);
            buffer[index+1] = (byte)(hi>>16);
            buffer[index+2] = (byte)(hi>>8);
            buffer[index+3] = (byte)hi;
            buffer[index+4] = (byte)(lo>>24);
            buffer[index+5] = (byte)(lo>>16);
            buffer[index+6] = (byte)(lo>>8);
            buffer[index+7] = (byte)lo;
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 8);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put64Bits_littleEndian(Object tab, int index, long bits) {
        final byte[] buffer = (byte[])tab;
        final int lo = (int)bits;
        final int hi = (int)(bits>>32);
        try {
            buffer[index] = (byte)lo;
            buffer[index+1] = (byte)(lo>>8);
            buffer[index+2] = (byte)(lo>>16);
            buffer[index+3] = (byte)(lo>>24);
            buffer[index+4] = (byte)hi;
            buffer[index+5] = (byte)(hi>>8);
            buffer[index+6] = (byte)(hi>>16);
            buffer[index+7] = (byte)(hi>>24);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 8);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put72Bits_bigEndian(Object tab, int index, long msbits, byte lsbits) {
        final byte[] buffer = (byte[])tab;
        final int hi = (int)(msbits>>32);
        final int lo = (int)msbits;
        try {
            buffer[index] = (byte)(hi>>24);
            buffer[index+1] = (byte)(hi>>16);
            buffer[index+2] = (byte)(hi>>8);
            buffer[index+3] = (byte)hi;
            buffer[index+4] = (byte)(lo>>24);
            buffer[index+5] = (byte)(lo>>16);
            buffer[index+6] = (byte)(lo>>8);
            buffer[index+7] = (byte)lo;
            buffer[index+8] = lsbits;
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 9);
            throw new AssertionError();
        }
    }

    /**
     * @throws ReadOnlyBufferException if the specified tab is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public void put72Bits_littleEndian(Object tab, int index, long msbits, byte lsbits) {
        final byte[] buffer = (byte[])tab;
        final int lo = (int)msbits;
        final int hi = (int)(msbits>>32);
        try {
            buffer[index] = lsbits;
            buffer[index+1] = (byte)lo;
            buffer[index+2] = (byte)(lo>>8);
            buffer[index+3] = (byte)(lo>>16);
            buffer[index+4] = (byte)(lo>>24);
            buffer[index+5] = (byte)hi;
            buffer[index+6] = (byte)(hi>>8);
            buffer[index+7] = (byte)(hi>>16);
            buffer[index+8] = (byte)(hi>>24);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 9);
            throw new AssertionError();
        }
    }

    /*
     * get
     */

    /**
     * @throws IndexOutOfBoundsException if specified byte is out of range.
     */
    public byte get8Bits(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            return buffer[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 1);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public short get8BitsAnd8Bits(Object tab, int msbitsIndex, int lsbitsIndex) {
        final byte[] buffer = (byte[])tab;
        try {
            return (short)((buffer[msbitsIndex]<<8) | (buffer[lsbitsIndex]&0xFF));
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, msbitsIndex, 1);
            LangUtils.checkBounds(buffer.length, lsbitsIndex, 1);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public short get16Bits_bigEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            return (short)((buffer[index]<<8)
                    | (buffer[index+1]&0xFF));
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 2);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public short get16Bits_littleEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            return (short)((buffer[index]&0xFF)
                    | (buffer[index+1]<<8));
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 2);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public int get24Bits_bigEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            return (buffer[index]<<16)
                    | ((buffer[index+1]&0xFF)<<8)
                    | (buffer[index+2]&0xFF);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 3);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public int get24Bits_littleEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            return (buffer[index]&0xFF)
                    | ((buffer[index+1]&0xFF)<<8)
                    | (buffer[index+2]<<16);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 3);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public int get32Bits_bigEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            return (buffer[index]<<24)
                    | ((buffer[index+1]&0xFF)<<16)
                    | ((buffer[index+2]&0xFF)<<8)
                    | (buffer[index+3]&0xFF);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 4);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public int get32Bits_littleEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            return (buffer[index]&0xFF)
                    | ((buffer[index+1]&0xFF)<<8)
                    | ((buffer[index+2]&0xFF)<<16)
                    | (buffer[index+3]<<24);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 4);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long get40Bits_bigEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            final int hi = buffer[index];
            final int lo = (buffer[index+1]<<24)
                    | ((buffer[index+2]&0xFF)<<16)
                    | ((buffer[index+3]&0xFF)<<8)
                    | (buffer[index+4]&0xFF);
            return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 5);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long get40Bits_littleEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            final int lo = (buffer[index]&0xFF)
                    | ((buffer[index+1]&0xFF)<<8)
                    | ((buffer[index+2]&0xFF)<<16)
                    | (buffer[index+3]<<24);
            final int hi = buffer[index+4];
            return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 5);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long get48Bits_bigEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            final int hi = (buffer[index]<<8)
                    | (buffer[index+1]&0xFF);
            final int lo = (buffer[index+2]<<24)
                    | ((buffer[index+3]&0xFF)<<16)
                    | ((buffer[index+4]&0xFF)<<8)
                    | (buffer[index+5]&0xFF);
            return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 6);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long get48Bits_littleEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            final int lo = (buffer[index]&0xFF)
                    | ((buffer[index+1]&0xFF)<<8)
                    | ((buffer[index+2]&0xFF)<<16)
                    | (buffer[index+3]<<24);
            final int hi = (buffer[index+4]&0xFF)
                    | (buffer[index+5]<<8);
            return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 6);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long get56Bits_bigEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            final int hi = (buffer[index]<<16)
                    | ((buffer[index+1]&0xFF)<<8)
                    | (buffer[index+2]&0xFF);
            final int lo = (buffer[index+3]<<24)
                    | ((buffer[index+4]&0xFF)<<16)
                    | ((buffer[index+5]&0xFF)<<8)
                    | (buffer[index+6]&0xFF);
            return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 7);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long get56Bits_littleEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            final int lo = (buffer[index]&0xFF)
                    | ((buffer[index+1]&0xFF)<<8)
                    | ((buffer[index+2]&0xFF)<<16)
                    | (buffer[index+3]<<24);
            final int hi = (buffer[index+4]&0xFF)
                    | ((buffer[index+5]&0xFF)<<8)
                    | (buffer[index+6]<<16);
            return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 7);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long get64Bits_bigEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            final int hi = (buffer[index]<<24)
                    | ((buffer[index+1]&0xFF)<<16)
                    | ((buffer[index+2]&0xFF)<<8)
                    | (buffer[index+3]&0xFF);
            final int lo = (buffer[index+4]<<24)
                    | ((buffer[index+5]&0xFF)<<16)
                    | ((buffer[index+6]&0xFF)<<8)
                    | (buffer[index+7]&0xFF);
            return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 8);
            throw new AssertionError();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long get64Bits_littleEndian(Object tab, int index) {
        final byte[] buffer = (byte[])tab;
        try {
            final int lo = (buffer[index]&0xFF)
                    | ((buffer[index+1]&0xFF)<<8)
                    | ((buffer[index+2]&0xFF)<<16)
                    | (buffer[index+3]<<24);
            final int hi = (buffer[index+4]&0xFF)
                    | ((buffer[index+5]&0xFF)<<8)
                    | ((buffer[index+6]&0xFF)<<16)
                    | (buffer[index+7]<<24);
            return (((long)hi)<<32) | (((long)lo)&0xFFFFFFFFL);
        } catch (ArrayIndexOutOfBoundsException e) {
            LangUtils.checkBounds(buffer.length, index, 8);
            throw new AssertionError();
        }
    }
}
