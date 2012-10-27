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

import net.jodk.lang.LangUtils;

/**
 * Utility methods for byte arrays.
 * 
 * Two ways of writing or reading data are supported:
 * 1) "big endian": data bits are stored, in bytes of increasing index:
 *      - from data MSBit to data LSBit,
 *      - from byte MSBit to byte LSBit.
 * 2) "little endian": data bits are stored, in bytes of increasing index:
 *      - from data LSBit to data MSBit,
 *      - from byte LSBit to byte MSBit.
 *
 * Bits designation:
 * A byte index designates a same byte, whether one works in big endian or in little endian.
 * Similarly, we use the term "bit index" to designate a bit independently of considered endianness:
 * The bit of index (i) in a byte, is the bit corresponding to the (2^i) value, i.e. the bit for which
 * it takes (i+1) right-shifts (>>) (byte being represented in big endian) operations to make it disappear.
 * For bit designation relative to big or little endianness, we use the term "bit position", as follows:
 * bit of position (p) in a byte means:
 *      - big endian: the bit of index (i=7-p) in this byte,
 *      - little endian: the bit of index (i=p) in this byte.
 * In other words, in big or little endian bit-representation, the n'th bit (n starting at 0)
 * is the bit of position (p=n).
 * Also:
 * Bit of index (i) means bit of index (i % 8) in byte of index (i / 8);
 * Bit of position (p) means bit of position (p % 8) in byte of index (p / 8);
 * 
 * We could also have used opposite convention regarding "bit index" and "bit position" usage.
 * That would have made APIs of bytewise and bitwise operations more similar (always dealing
 * with indexes). But we consider it's not a problem to leak bytes and bits difference of
 * nature through the API, and we prefer to keep the term "index" tied with physical memory,
 * regardless of how it is interpreted.
 * 
 * For performance reasons, for put operations,
 * if the byte range is invalid, exception might
 * be thrown only after some data has been written.
 */
public class ByteArrayUtils {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final BaseBTHelper HELPER = BaseBTHelper.INSTANCE;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /*
     * put at bytewise
     */
    
    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public static void putShortAt(byte[] buffer, int index, short value, ByteOrder order) {
        LangUtils.checkNonNull(order);
        HELPER.put16Bits(buffer, index, value, order == ByteOrder.BIG_ENDIAN);
    }
    
    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public static void putIntAt(byte[] buffer, int index, int value, ByteOrder order) {
        LangUtils.checkNonNull(order);
        HELPER.put32Bits(buffer, index, value, order == ByteOrder.BIG_ENDIAN);
    }
    
    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public static void putLongAt(byte[] buffer, int index, long value, ByteOrder order) {
        LangUtils.checkNonNull(order);
        HELPER.put64Bits(buffer, index, value, order == ByteOrder.BIG_ENDIAN);
    }
    
    /*
     * get at bytewise
     */
    
    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public static short getShortAt(byte[] buffer, int index, ByteOrder order) {
        LangUtils.checkNonNull(order);
        return HELPER.get16Bits(buffer, index, order == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public static int getIntAt(byte[] buffer, int index, ByteOrder order) {
        LangUtils.checkNonNull(order);
        return HELPER.get32Bits(buffer, index, order == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public static long getLongAt(byte[] buffer, int index, ByteOrder order) {
        LangUtils.checkNonNull(order);
        return HELPER.get64Bits(buffer, index, order == ByteOrder.BIG_ENDIAN);
    }

    /*
     * byte padding
     */

    /**
     * Sets bits in [bitPos,last_bit_of_bitPosMinus1_byte] to zero.
     * 
     * @param bitPos Position of first bit to set to zero.
     * @return Number of bits padded, i.e. a value in [0,7].
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    public static int padByteAtBit(byte[] buffer, long bitPos, ByteOrder order) {
        LangUtils.checkNonNull(order);
        return ByteTabUtils.padByteAtBit(HELPER, buffer, 0, buffer.length, bitPos, order == ByteOrder.BIG_ENDIAN);
    }

    /*
     * bit
     */

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    public static void putBitAtBit(byte[] buffer, long bitPos, boolean value, ByteOrder order) {
        LangUtils.checkNonNull(order);
        ByteTabUtils.putBitAtBit(HELPER, buffer, 0, buffer.length, bitPos, value, order == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    public static boolean getBitAtBit(byte[] buffer, long bitPos, ByteOrder order) {
        LangUtils.checkNonNull(order);
        return ByteTabUtils.getBitAtBit(HELPER, buffer, 0, buffer.length, bitPos, order == ByteOrder.BIG_ENDIAN);
    }

    /*
     * put at bitwise
     */

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static void putIntSignedAtBit(byte[] buffer, long firstBitPos, int signedValue, int bitSize, ByteOrder order) {
        LangUtils.checkNonNull(order);
        ByteTabUtils.putIntSignedAtBit(HELPER, buffer, 0, buffer.length, firstBitPos, signedValue, bitSize, order == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static void putLongSignedAtBit(byte[] buffer, long firstBitPos, long signedValue, int bitSize, ByteOrder order) {
        LangUtils.checkNonNull(order);
        ByteTabUtils.putLongSignedAtBit(HELPER, buffer, 0, buffer.length, firstBitPos, signedValue, bitSize, order == ByteOrder.BIG_ENDIAN);
    }
    
    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static void putIntUnsignedAtBit(byte[] buffer, long firstBitPos, int unsignedValue, int bitSize, ByteOrder order) {
        LangUtils.checkNonNull(order);
        ByteTabUtils.putIntUnsignedAtBit(HELPER, buffer, 0, buffer.length, firstBitPos, unsignedValue, bitSize, order == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static void putLongUnsignedAtBit(byte[] buffer, long firstBitPos, long unsignedValue, int bitSize, ByteOrder order) {
        LangUtils.checkNonNull(order);
        ByteTabUtils.putLongUnsignedAtBit(HELPER, buffer, 0, buffer.length, firstBitPos, unsignedValue, bitSize, order == ByteOrder.BIG_ENDIAN);
    }

    /*
     * get at bitwise
     */

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IllegalArgumentException if a signed int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,32].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static int getIntSignedAtBit(byte[] buffer, long firstBitPos, int bitSize, ByteOrder order) {
        LangUtils.checkNonNull(order);
        return ByteTabUtils.getIntSignedAtBit(HELPER, buffer, 0, buffer.length, firstBitPos, bitSize, order == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IllegalArgumentException if a signed long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,64].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static long getLongSignedAtBit(byte[] buffer, long firstBitPos, int bitSize, ByteOrder order) {
        LangUtils.checkNonNull(order);
        return ByteTabUtils.getLongSignedAtBit(HELPER, buffer, 0, buffer.length, firstBitPos, bitSize, order == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IllegalArgumentException if an unsigned int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,31].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static int getIntUnsignedAtBit(byte[] buffer, long firstBitPos, int bitSize, ByteOrder order) {
        LangUtils.checkNonNull(order);
        return ByteTabUtils.getIntUnsignedAtBit(HELPER, buffer, 0, buffer.length, firstBitPos, bitSize, order == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IllegalArgumentException if an unsigned long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,63].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static long getLongUnsignedAtBit(byte[] buffer, long firstBitPos, int bitSize, ByteOrder order) {
        LangUtils.checkNonNull(order);
        return ByteTabUtils.getLongUnsignedAtBit(HELPER, buffer, 0, buffer.length, firstBitPos, bitSize, order == ByteOrder.BIG_ENDIAN);
    }

    /*
     * put at floating point
     */

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bits (32 from first) are out of range.
     */
    public static void putFloatAt(byte[] buffer, long firstBitPos, float value, ByteOrder order) {
        putIntSignedAtBit(buffer, firstBitPos, Float.floatToRawIntBits(value), 32, order);
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bits (64 from first) are out of range.
     */
    public static void putDoubleAt(byte[] buffer, long firstBitPos, double value, ByteOrder order) {
        putLongSignedAtBit(buffer, firstBitPos, Double.doubleToRawLongBits(value), 64, order);
    }

    /*
     * get at floating point
     */

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bits (32 from first) are out of range.
     */
    public static float getFloatAt(byte[] buffer, long firstBitPos, ByteOrder order) {
        return Float.intBitsToFloat(getIntSignedAtBit(buffer, firstBitPos, 32, order));
    }

    /**
     * @throws NullPointerException if the specified order is null.
     * @throws IndexOutOfBoundsException if specified bits (64 from first) are out of range.
     */
    public static double getDoubleAt(byte[] buffer, long firstBitPos, ByteOrder order) {
        return Double.longBitsToDouble(getLongSignedAtBit(buffer, firstBitPos, 64, order));
    }

    /*
     * array copy
     */

    /**
     * Bitwise version of System.arraycopy, which handles (as well) the case of
     * identical array objects with overlapping copy ranges.
     * 
     * For non-byte-aligned bit shifts of many bits, i.e. if
     * ((srcFirstBitPos - dstFirstBitPos) % 8 != 0) and bitSize is large,
     * this treatment is typically a few dozen times slower than System.arraycopy.
     * 
     * For byte-aligned bit shifts, it is several times slower
     * if bitSize is small, but about as fast if bitSize is large,
     * since System.arraycopy is used for most of the copy.
     * 
     * @param src The source array.
     * @param srcFirstBitPos Starting bit position in the source array.
     * @param dst The destination array.
     * @param dstFirstBitPos Starting bit position in the destination array.
     * @param bitSize The number of bits to copy.
     * @param order Byte order to use.
     * @throws NullPointerException if either src, dst or order is null.
     * @throws IndexOutOfBoundsException if bitSize < 0, or the specified bits are out of range.
     */
    public static void arrayCopyBits(
            byte[] src,
            long srcFirstBitPos,
            byte[] dst,
            long dstFirstBitPos,
            long bitSize,
            ByteOrder order) {
        LangUtils.checkNonNull(src);
        LangUtils.checkNonNull(dst);
        LangUtils.checkNonNull(order);
        
        ByteTabUtils.tabCopyBits(
                HELPER,
                src,
                0,
                (((long)src.length)<<3),
                srcFirstBitPos,
                HELPER,
                dst,
                0,
                (((long)dst.length)<<3),
                dstFirstBitPos,
                bitSize,
                order == ByteOrder.BIG_ENDIAN);
    }

    /*
     * toString
     */

    /**
     * toString with digits in big endian order only, but with custom radix.
     * 
     * @param firstByteIndex Index of the first byte to put in resulting string.
     * @param byteSize Number of bytes to put in resulting string.
     * @param radix Radix of digits put in resulting string.
     * @return A string containing the specified data range in the specified format.
     * @throws IllegalArgumentException if the specified radix is not in [2,36].
     * @throws IndexOutOfBoundsException if byteSize < 0,, or the specified bytes are out of range.
     */
    public static String toString(byte[] buffer, int firstByteIndex, int byteSize, int radix) {
        return ByteTabUtils.toString(HELPER, buffer, 0, buffer.length, firstByteIndex, byteSize, radix);
    }

    /**
     * toString with digits in binary format only, to display bits, but with custom endianness.
     * 
     * @param firstBitPos Position of the first bit to put in resulting string.
     * @param bitSize Number of bits to put in resulting string.
     * @param bigEndian Whether the bits should be displayed in big endian or
     *                  little endian order.
     * @return A string containing the specified data range in the specified format.
     * @throws IndexOutOfBoundsException if bitSize < 0, or the specified bits are out of range.
     */
    public static String toStringBits(byte[] buffer, long firstBitPos, long bitSize, boolean bigEndian) {
        return ByteTabUtils.toStringBits(HELPER, buffer, 0, (((long)buffer.length)<<3), firstBitPos, bitSize, bigEndian);
    }
}
