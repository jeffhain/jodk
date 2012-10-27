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
import java.nio.ReadOnlyBufferException;

/**
 * Utility class for instances of ByteBuffer.
 * 
 * See ByteArrayUtils Javadoc for info about endianness.
 * 
 * For performance reasons, for put operations,
 * if the byte range is invalid, exception might
 * be thrown only after some data has been written.
 */
public class ByteBufferUtils {

    /*
     * If the specified ByteBuffer is read-only, writing methods
     * throw ReadOnlyBufferException right away, whatever the arguments,
     * to behave as if they were ByteBuffer's instance methods.
     */
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final BaseBTHelper BA_HELPER = BaseBTHelper.INSTANCE;
    
    private static final ByteBufferBTHelper BB_HELPER = ByteBufferBTHelper.INSTANCE;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /*
     * byte padding
     */

    /**
     * Sets bits in [bitPos,last_bit_of_bitPosMinus1_byte] to zero.
     * 
     * @param bitPos Position of first bit to set to zero.
     * @return Number of bits padded, i.e. a value in [0,7].
     * @throws ReadOnlyBufferException if the specified ByteBuffer is read-only.
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    public static int padByteAtBit(ByteBuffer buffer, long bitPos) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            if (buffer.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        return ByteTabUtils.padByteAtBit(tabHelper, tab, tabOffset, buffer.limit(), bitPos, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /*
     * bit
     */

    /**
     * @throws ReadOnlyBufferException if the specified ByteBuffer is read-only.
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    public static void putBitAtBit(ByteBuffer buffer, long bitPos, boolean value) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            if (buffer.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        ByteTabUtils.putBitAtBit(tabHelper, tab, tabOffset, buffer.limit(), bitPos, value, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    public static boolean getBitAtBit(ByteBuffer buffer, long bitPos) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        return ByteTabUtils.getBitAtBit(tabHelper, tab, tabOffset, buffer.limit(), bitPos, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /*
     * put at bitwise
     */

    /**
     * @throws ReadOnlyBufferException if the specified ByteBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static void putIntSignedAtBit(ByteBuffer buffer, long firstBitPos, int signedValue, int bitSize) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            if (buffer.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        ByteTabUtils.putIntSignedAtBit(tabHelper, tab, tabOffset, buffer.limit(), firstBitPos, signedValue, bitSize, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws ReadOnlyBufferException if the specified ByteBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static void putLongSignedAtBit(ByteBuffer buffer, long firstBitPos, long signedValue, int bitSize) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            if (buffer.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        ByteTabUtils.putLongSignedAtBit(tabHelper, tab, tabOffset, buffer.limit(), firstBitPos, signedValue, bitSize, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws ReadOnlyBufferException if the specified ByteBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static void putIntUnsignedAtBit(ByteBuffer buffer, long firstBitPos, int unsignedValue, int bitSize) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            if (buffer.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        ByteTabUtils.putIntUnsignedAtBit(tabHelper, tab, tabOffset, buffer.limit(), firstBitPos, unsignedValue, bitSize, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws ReadOnlyBufferException if the specified ByteBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static void putLongUnsignedAtBit(ByteBuffer buffer, long firstBitPos, long unsignedValue, int bitSize) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            if (buffer.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        ByteTabUtils.putLongUnsignedAtBit(tabHelper, tab, tabOffset, buffer.limit(), firstBitPos, unsignedValue, bitSize, buffer.order() == ByteOrder.BIG_ENDIAN);
    }
    
    /*
     * get at bitwise
     */

    /**
     * @throws IllegalArgumentException if a signed int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,32].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static int getIntSignedAtBit(ByteBuffer buffer, long firstBitPos, int bitSize) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        return ByteTabUtils.getIntSignedAtBit(tabHelper, tab, tabOffset, buffer.limit(), firstBitPos, bitSize, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws IllegalArgumentException if a signed long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,64].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static long getLongSignedAtBit(ByteBuffer buffer, long firstBitPos, int bitSize) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        return ByteTabUtils.getLongSignedAtBit(tabHelper, tab, tabOffset, buffer.limit(), firstBitPos, bitSize, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws IllegalArgumentException if an unsigned int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,31].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static int getIntUnsignedAtBit(ByteBuffer buffer, long firstBitPos, int bitSize) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        return ByteTabUtils.getIntUnsignedAtBit(tabHelper, tab, tabOffset, buffer.limit(), firstBitPos, bitSize, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /**
     * @throws IllegalArgumentException if an unsigned long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,63].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public static long getLongUnsignedAtBit(ByteBuffer buffer, long firstBitPos, int bitSize) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        return ByteTabUtils.getLongUnsignedAtBit(tabHelper, tab, tabOffset, buffer.limit(), firstBitPos, bitSize, buffer.order() == ByteOrder.BIG_ENDIAN);
    }

    /*
     * put floating point
     */

    /**
     * @throws ReadOnlyBufferException if the specified ByteBuffer is read-only.
     * @throws IndexOutOfBoundsException if specified bits (32 from first) are out of range.
     */
    public static void putFloatAt(ByteBuffer buffer, long firstBitPos, float value) {
        putIntSignedAtBit(buffer, firstBitPos, Float.floatToRawIntBits(value), 32);
    }

    /**
     * @throws ReadOnlyBufferException if the specified ByteBuffer is read-only.
     * @throws IndexOutOfBoundsException if specified bits (64 from first) are out of range.
     */
    public static void putDoubleAt(ByteBuffer buffer, long firstBitPos, double value) {
        putLongSignedAtBit(buffer, firstBitPos, Double.doubleToRawLongBits(value), 64);
    }

    /*
     * get floating point
     */

    /**
     * @throws IndexOutOfBoundsException if specified bits (32 from first) are out of range.
     */
    public static float getFloatAt(ByteBuffer buffer, long firstBitPos) {
        return Float.intBitsToFloat(getIntSignedAtBit(buffer, firstBitPos, 32));
    }

    /**
     * @throws IndexOutOfBoundsException if specified bits (64 from first) are out of range.
     */
    public static double getDoubleAt(ByteBuffer buffer, long firstBitPos) {
        return Double.longBitsToDouble(getLongSignedAtBit(buffer, firstBitPos, 64));
    }

    /*
     * buffer copy
     */

    /**
     * Equivalent to calling bufferCopyBits(...) version with "* 8L"
     * applied on indexes and size arguments.
     * 
     * You might want to use
     * ByteCopyUtils.readAllAtAndWriteAllAt(...)
     * or
     * ByteCopyUtils.readAtAndWriteAt(...)
     * methods instead of this one, for they can be much faster due to use of
     * native copies, and also allow copies between ByteBuffers of different
     * orders, but they might create garbage, and might not handle some cases
     * of overlapping src and dst memory.
     * 
     * @param src The source buffer.
     * @param srcFirstByteIndex Starting byte index in the source buffer.
     * @param dst The destination buffer.
     * @param dstFirstByteIndex Starting byte index in the destination buffer.
     * @param byteSize The number of bytes to copy.
     * @throws NullPointerException if either src or dst is null.
     * @throws ReadOnlyBufferException if dst is read-only and byteSize > 0.
     * @throws IllegalArgumentException if src and dst don't have the same byte order
     *         or if byteSize is < 0.
     * @throws IndexOutOfBoundsException if the specified bytes are out of range.
     */
    public static void bufferCopy(
            ByteBuffer src,
            int srcFirstByteIndex,
            ByteBuffer dst,
            int dstFirstByteIndex,
            int byteSize) {
        bufferCopyBits(
                src,
                (((long)srcFirstByteIndex)<<3),
                dst,
                (((long)dstFirstByteIndex)<<3),
                (((long)byteSize)<<3));
    }
    
    /**
     * If some memory is shared by the specified buffers, in a way that can't
     * be known by this treatment, the resulting content of destination buffer
     * is undefined. If both src and dst are heap buffers, and their backing
     * array is accessible, then this treatment knows how they share data, and
     * takes care to copy such as if the array is shared, data to copy is not
     * erased with copied data.
     * 
     * @param src The source buffer.
     * @param srcFirstBitPos Starting bit position in the source buffer.
     * @param dst The destination buffer.
     * @param dstFirstBitPos Starting bit position in the destination buffer.
     * @param bitSize The number of bits to copy.
     * @throws NullPointerException if either src or dst is null.
     * @throws ReadOnlyBufferException if dst is read-only and bitSize > 0.
     * @throws IllegalArgumentException if src and dst don't have the same byte order
     *         or if bitSize is < 0.
     * @throws IndexOutOfBoundsException if the specified bits are out of range.
     */
    public static void bufferCopyBits(
            ByteBuffer src,
            long srcFirstBitPos,
            ByteBuffer dst,
            long dstFirstBitPos,
            long bitSize) {
        // Implicit null checks.
        final ByteOrder srcOrder = src.order();
        final ByteOrder dstOrder = dst.order();
        
        if (dst.isReadOnly() && (bitSize > 0)) {
            throw new ReadOnlyBufferException();
        }
        
        if (srcOrder != dstOrder) {
            throw new IllegalArgumentException("src order ["+srcOrder+"] != dst order ["+dstOrder+"]");
        }

        ByteTabUtils.tabCopyBits(
                BB_HELPER,
                src,
                0,
                (((long)src.limit())<<3),
                srcFirstBitPos,
                BB_HELPER,
                dst,
                0,
                (((long)dst.limit())<<3),
                dstFirstBitPos,
                bitSize,
                srcOrder == ByteOrder.BIG_ENDIAN);
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
     * @throws IndexOutOfBoundsException if byteSize < 0, or the specified bytes are out of range.
     */
    public static String toString(ByteBuffer buffer, int firstByteIndex, int byteSize, int radix) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        return ByteTabUtils.toString(tabHelper, tab, tabOffset, buffer.limit(), firstByteIndex, byteSize, radix);
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
    public static String toStringBits(ByteBuffer buffer, long firstBitPos, long bitSize, boolean bigEndian) {
        final BaseBTHelper tabHelper;
        final Object tab;
        final int tabOffset;
        if (buffer.hasArray()) {
            tabHelper = BA_HELPER;
            tab = buffer.array();
            tabOffset = buffer.arrayOffset();
        } else {
            tabHelper = BB_HELPER;
            tab = buffer;
            tabOffset = 0;
        }
        return ByteTabUtils.toStringBits(tabHelper, tab, tabOffset, (((long)buffer.limit())<<3), firstBitPos, bitSize, bigEndian);
    }
}
