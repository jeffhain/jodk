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

import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;

/**
 * Utility methods for byte tabs, such as byte arrays or ByteBuffer instances.
 */
public class ByteTabUtils {

    /*
     * Not checking order != null in these methods,
     * for it's not needed for ByteBuffer case:
     * that must be checked aside for byte array case.
     * 
     * Using int for some byte values, for it should not be slower,
     * and else we would have to do &0xFF when byte is auto-casted in int
     * (to make sure sign bit doesn't put 1 where we want 0).
     * 
     * _noCheck methods usually take byte indexes or bit positions with offset
     * added (non-zero only for byte array case), since they are called after
     * bounds checks relative to limit.
     */

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean ASSERTIONS = false;

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    /**
     * Max byte size in byte arrays.
     */
    public static final int MAX_BYTE_SIZE = Integer.MAX_VALUE;

    /**
     * Max byte index (inclusive).
     */
    public static final int MAX_BYTE_INDEX = MAX_BYTE_SIZE-1;

    /**
     * Max bit size in byte arrays: max byte size * 8L.
     */
    public static final long MAX_BIT_SIZE = MAX_BYTE_SIZE * 8L;

    /**
     * Max bit position (inclusive).
     * This is also max bit index.
     */
    public static final long MAX_BIT_POS = MAX_BIT_SIZE-1;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /*
     * bit pos and bit size
     */

    /**
     * @param bitPos A bit position, in [0,MAX_BIT_POSITION+8].
     * @return Index of the corresponding byte.
     *         The returned index is Integer.MAX_VALUE (i.e. an exclusive index)
     *         if the specified bit position is in [MAX_BIT_POSITION+1,MAX_BIT_POSITION+8].
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    public static int computeByteIndex(long bitPos) {
        if (!NumbersUtils.isInRange(0, MAX_BIT_POS+8, bitPos)) {
            throw new IndexOutOfBoundsException("bit position ["+bitPos+"] must be in [0,"+(MAX_BIT_POS+8)+"]");
        }
        // bitPos/8
        // Also works for bitPos = 0: returns 0.
        return (int)(bitPos>>3);
    }

    /**
     * @param bitSize A size in number of bits, in [0,MAX_BIT_SIZE].
     * @return Corresponding size in number of bytes, considering
     *         first bit (if any) aligned with a byte start.
     * @throws IllegalArgumentException if the specified bit size is out of range.
     */
    public static int computeByteSize(long bitSize) {
        NumbersUtils.checkIsInRange(0, MAX_BIT_SIZE, bitSize);
        // (bitSize+7)/8
        return (int)((bitSize+7)>>3);
    }

    /**
     * @param firstBitPos Position of the first bit.
     * @param bitSize Number of bits from the specified first bit.
     * @return Corresponding size in number of bytes.
     * @throws IllegalArgumentException if bitSize < 0.
     * @throws IndexOutOfBoundsException if firstBitPos < 0, or if firstBitPos+bitSize overflows,
     *         or if firstBitPos+bitSize > MAX_BIT_SIZE.
     */
    public static int computeByteSize(long firstBitPos, long bitSize) {
        LangUtils.checkBounds(MAX_BIT_SIZE, firstBitPos, bitSize);
        if (bitSize == 0) {
            return 0;
        }
        final int firstByteIndex = (int)(firstBitPos>>3);
        final int lastByteIndex = (int)((firstBitPos+bitSize-1)>>3);
        return lastByteIndex - firstByteIndex + 1;
    }

    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE METHODS
    //--------------------------------------------------------------------------

    /*
     * byte padding
     */

    /**
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     * @return Number of bits padded, i.e. a value in [0,7].
     */
    static int padByteAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long bitPos, boolean bigEndian) {
        /*
         * Not using putIntAtBit_bitSizeChecked(...),
         * to avoid always-false bytewise-case test.
         */
        final int bitPosInByte = ((int)bitPos)&7;
        if (bitPosInByte != 0) {
            final int bitSize = 8-bitPosInByte;
            helper.checkLimitBitPosBitSizeIfNeeded(limit, bitPos, bitSize);
            putIntAtBit_noCheck(helper, tab, (((long)offset)<<3)+bitPos, 0, bitSize, bigEndian);
            return bitSize;
        } else {
            LangUtils.checkBounds((((long)limit)<<3), bitPos, 0);
            return 0;
        }
    }

    /*
     * bit
     * 
     * A bit pleonastic to use "putBitAtBit" or "getBitAtBit",
     * but is homogeneous with other names (i.e. "XXXAt"
     * for byte index, and "XXXAtBit" for bit position).
     */

    /**
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    static void putBitAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long bitPos, boolean value, boolean bigEndian) {
        helper.checkLimitBitPosBitSizeIfNeeded(limit, bitPos, 1);

        final int byteIndex = (int)(bitPos>>3);
        final int bitPositionInByte = ((int)bitPos)&7;
        byte byteElement = helper.get8Bits(tab, offset+byteIndex);

        final int bitIndexInByte;
        if (bigEndian) {
            bitIndexInByte = 7 - bitPositionInByte;
        } else {
            bitIndexInByte = bitPositionInByte;
        }

        if (value) {
            byteElement = (byte)(byteElement | 1<<bitIndexInByte);
        } else {
            byteElement = (byte)(byteElement & ~(1<<bitIndexInByte));
        }

        helper.put8Bits(tab, offset+byteIndex, byteElement);
    }

    /**
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    static boolean getBitAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long bitPos, boolean bigEndian) {
        helper.checkLimitBitPosBitSizeIfNeeded(limit, bitPos, 1);

        final int byteIndex = (int)(bitPos>>3);
        final int bitPositionInByte = ((int)bitPos)&7;
        byte byteElement = helper.get8Bits(tab, offset+byteIndex);

        final int bitIndexInByte;
        if (bigEndian) {
            bitIndexInByte = 7 - bitPositionInByte;
        } else {
            bitIndexInByte = bitPositionInByte;
        }

        return ((byteElement>>bitIndexInByte) & 1) != 0;
    }

    /*
     * put signed
     */

    /**
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    static void putIntSignedAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, int signedValue, int bitSize, boolean bigEndian) {
        if (bitSize != 32) {
            NumbersUtils.checkIsInRangeSigned(signedValue, bitSize);
        }
        putIntAtBit_bitSizeChecked(helper, tab, offset, limit, firstBitPos, signedValue, bitSize, bigEndian);
    }

    /**
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    static void putLongSignedAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, long signedValue, int bitSize, boolean bigEndian) {
        if (bitSize != 64) {
            NumbersUtils.checkIsInRangeSigned(signedValue, bitSize);
        }
        putLongAtBit_bitSizeChecked(helper, tab, offset, limit, firstBitPos, signedValue, bitSize, bigEndian);
    }

    /*
     * put unsigned
     */

    /**
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    static void putIntUnsignedAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, int unsignedValue, int bitSize, boolean bigEndian) {
        NumbersUtils.checkIsInRangeUnsigned(unsignedValue, bitSize);
        putIntAtBit_bitSizeChecked(helper, tab, offset, limit, firstBitPos, unsignedValue, bitSize, bigEndian);
    }

    /**
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    static void putLongUnsignedAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, long unsignedValue, int bitSize, boolean bigEndian) {
        NumbersUtils.checkIsInRangeUnsigned(unsignedValue, bitSize);
        putLongAtBit_bitSizeChecked(helper, tab, offset, limit, firstBitPos, unsignedValue, bitSize, bigEndian);
    }

    /*
     * get signed
     */

    /**
     * @throws IllegalArgumentException if a signed int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,32].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    static int getIntSignedAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, int bitSize, boolean bigEndian) {
        if (bitSize != 32) {
            NumbersUtils.checkBitSizeForSignedInt(bitSize);
        }
        return getIntSignedAtBit_bitSizeChecked(helper, tab, offset, limit, firstBitPos, bitSize, bigEndian);
    }

    /**
     * @throws IllegalArgumentException if a signed long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,64].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    static long getLongSignedAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, int bitSize, boolean bigEndian) {
        if (bitSize != 64) {
            NumbersUtils.checkBitSizeForSignedLong(bitSize);
        }
        return getLongSignedAtBit_bitSizeChecked(helper, tab, offset, limit, firstBitPos, bitSize, bigEndian);
    }

    /*
     * get unsigned
     */

    /**
     * @throws IllegalArgumentException if an unsigned int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,31].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    static int getIntUnsignedAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, int bitSize, boolean bigEndian) {
        NumbersUtils.checkBitSizeForUnsignedInt(bitSize);
        return getIntSignedAtBit_bitSizeChecked(helper, tab, offset, limit, firstBitPos, bitSize, bigEndian) & ((-1)>>>(32-bitSize));
    }

    /**
     * @throws IllegalArgumentException if an unsigned long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,63].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    static long getLongUnsignedAtBit(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, int bitSize, boolean bigEndian) {
        NumbersUtils.checkBitSizeForUnsignedLong(bitSize);
        return getLongSignedAtBit_bitSizeChecked(helper, tab, offset, limit, firstBitPos, bitSize, bigEndian) & ((-1L)>>>(64-bitSize));
    }

    /*
     * tab copy
     */

    /**
     * @param src Must not be null.
     * @param dest Must not be null.
     * @throws IllegalArgumentException if bitSize < 0.
     * @throws IndexOutOfBoundsException if the specified bits are out of range.
     */
    static void tabCopyBits(
            BaseBTHelper srcHelper,
            Object src,
            int srcOffset,
            long srcBitLimit,
            long srcFirstBitPos,
            BaseBTHelper destHelper,
            Object dest,
            int destOffset,
            long destBitLimit,
            long destFirstBitPos,
            long bitSize,
            boolean bigEndian) {
        LangUtils.checkBounds(srcBitLimit, srcFirstBitPos, bitSize);
        LangUtils.checkBounds(destBitLimit, destFirstBitPos, bitSize);
        if (bitSize == 0) {
            // Just needed to do parameters check.
            return;
        }
        
        /*
         * Using byte arrays if possible, for it's faster,
         * and in case of heap buffers, we need both byte arrays
         * and their offset to make sure not to erase data to copy
         * with copied data.
         */

        if ((srcHelper != BaseBTHelper.INSTANCE)
                && srcHelper.hasArray(src)) {
            final int arrayOffset = srcHelper.arrayOffset(src);
            src = srcHelper.array(src);
            srcHelper = BaseBTHelper.INSTANCE;
            
            srcOffset += arrayOffset;
        }

        if ((destHelper != BaseBTHelper.INSTANCE)
                && destHelper.hasArray(dest)) {
            final int arrayOffset = destHelper.arrayOffset(dest);
            dest = destHelper.array(dest);
            destHelper = BaseBTHelper.INSTANCE;
            
            destOffset += arrayOffset;
        }
        
        /*
         * 
         */
        
        srcFirstBitPos += (((long)srcOffset)<<3);
        destFirstBitPos += (((long)destOffset)<<3);
        
        final long srcToDestBitPosShift = destFirstBitPos - srcFirstBitPos;

        // No need to take absolute value before doing "&7",
        // since we compare against zero.
        if ((srcToDestBitPosShift&7) == 0) {
            tabCopyBits_noCheck_bitShiftMultipleOf8(
                    srcHelper,
                    src,
                    srcFirstBitPos,
                    destHelper,
                    dest,
                    destFirstBitPos,
                    bitSize,
                    bigEndian);
        } else {
            tabCopyBits_noCheck_general(
                    srcHelper,
                    src,
                    srcFirstBitPos,
                    destHelper,
                    dest,
                    destFirstBitPos,
                    bitSize,
                    bigEndian);
        }
    }

    /*
     * toString
     */

    /**
     * toString with digits in big endian order only, but with custom radix.
     * 
     * @throws IllegalArgumentException if byteSize < 0, or if the specified radix is not in [2,36].
     * @throws IndexOutOfBoundsException if the specified bytes are out of range.
     */
    static String toString(BaseBTHelper helper, Object tab, int offset, int limit, int firstByteIndex, int byteSize, int radix) {
        LangUtils.checkBounds(limit, firstByteIndex, byteSize);

        // Checks radix.
        final int paddingUpTo = NumbersUtils.computeNbrOfDigits(0xFF, radix);

        final int lastByteIndex = firstByteIndex + byteSize - 1;

        final String C1 = "(bytes ";
        final String C2 = "..";
        final String C3 = "(msb..lsb))[";
        final String C4 = "]";

        final int maxNbrOfCharsForHeaderPlusTailer =
                (C1.length() + C2.length() + C3.length() + C4.length())
                +NumbersUtils.computeNbrOfDigits(firstByteIndex, 10)
                +NumbersUtils.computeNbrOfChars(lastByteIndex, 10) ;
        // +1 for the comma.
        final int maxNbrOfCharsPerByte = paddingUpTo+1;
        // For no comma before first byte.
        final int oneIfSomeBytes = (byteSize != 0) ? 1 : 0;
        // Exception if too large.
        final int nbrOfChars = NumbersUtils.asInt(maxNbrOfCharsForHeaderPlusTailer + (maxNbrOfCharsPerByte * (long)byteSize) - oneIfSomeBytes);

        final StringBuilder sb = new StringBuilder(nbrOfChars);
        sb.append(C1);
        sb.append(firstByteIndex);
        sb.append(C2);
        sb.append(lastByteIndex);
        sb.append(C3);
        for (int i=firstByteIndex;i<=lastByteIndex;i++) {
            final int unsignedByte = NumbersUtils.byteAsUnsigned(helper.get8Bits(tab, offset+i));
            sb.append(NumbersUtils.toString(unsignedByte,radix,paddingUpTo));
            if (i < lastByteIndex) {
                sb.append(',');
            }
        }
        sb.append(C4);
        if (sb.length() != nbrOfChars) {
            // String builder sized exactly, to ensure both
            // no grow and optimal memory usage.
            throw new AssertionError(sb.length()+" != "+nbrOfChars);
        }
        return sb.toString();
    }

    /**
     * toString with digits in binary format only, to display bits, but with custom endianness.
     * 
     * @throws IllegalArgumentException if bitSize < 0.
     * @throws IndexOutOfBoundsException if the specified bits are out of range.
     */
    static String toStringBits(BaseBTHelper helper, Object tab, int offset, long bitLimit, long firstBitPos, long bitSize, boolean bigEndian) {
        LangUtils.checkBounds(bitLimit, firstBitPos, bitSize);

        final long lastBitPos = firstBitPos + bitSize - 1;

        final String C1 = "(bits pos ";
        final String C2 = "..";
        final String C3 = bigEndian ? "(msb..lsb))[" : "(lsb..msb))[";
        final String C4 = "]";

        final int maxNbrOfCharsForHeaderPlusTailer =
                (C1.length() + C2.length() + C3.length() + C4.length())
                +NumbersUtils.computeNbrOfDigits(firstBitPos, 10)
                +NumbersUtils.computeNbrOfChars(lastBitPos, 10) ;
        // +1 for the comma.
        final int maxNbrOfCharsPerByte = 8+1;
        // +1 for the comma.
        final int byteSize = computeByteSize(firstBitPos,bitSize);
        // For no comma before first byte.
        final int oneIfSomeBits = (bitSize != 0) ? 1 : 0;
        // Exception if too large.
        final int nbrOfChars = NumbersUtils.asInt(maxNbrOfCharsForHeaderPlusTailer + (maxNbrOfCharsPerByte * (long)byteSize) - oneIfSomeBits);

        final StringBuilder sb = new StringBuilder(nbrOfChars);

        sb.append(C1);
        sb.append(firstBitPos);
        sb.append(C2);
        sb.append(lastBitPos);
        sb.append(C3);
        final int firstByteIndex = (int)(firstBitPos >> 3);
        final int lastByteIndex = (int)(lastBitPos >> 3);
        final int firstBitPosInByte = ((int)firstBitPos) & 7;
        final int lastBitPosInByte = ((int)lastBitPos) & 7;
        final int lastBitPosInByteExcl = lastBitPosInByte+1;
        if ((firstByteIndex == lastByteIndex) && (bitSize != 0)) {
            // single byte
            sb.append(NumbersUtils.toStringBits(helper.get8Bits(tab,offset+firstByteIndex),firstBitPosInByte,lastBitPosInByteExcl,bigEndian,true));
        } else if (firstByteIndex < lastByteIndex) {
            // first byte
            sb.append(NumbersUtils.toStringBits(helper.get8Bits(tab,offset+firstByteIndex),firstBitPosInByte,8,bigEndian,true));
            sb.append(",");
            // intermediary bytes
            for (int i=firstByteIndex+1;i<lastByteIndex;i++) {
                sb.append(NumbersUtils.toStringBits(helper.get8Bits(tab,offset+i),0,8,bigEndian,false));
                sb.append(",");
            }
            // last byte
            sb.append(NumbersUtils.toStringBits(helper.get8Bits(tab,offset+lastByteIndex),0,lastBitPosInByteExcl,bigEndian,true));
        }
        sb.append(C4);
        if (sb.length() != nbrOfChars) {
            // String builder sized exactly, to ensure both
            // no grow and optimal memory usage.
            throw new AssertionError(sb.length()+" != "+nbrOfChars);
        }
        return sb.toString();
    }

    /*
     * put int
     */

    static void putIntAtBit_bitSizeChecked(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, int value, int bitSize, boolean bigEndian) {
        helper.checkLimitBitPosBitSizeIfNeeded(limit, firstBitPos, bitSize);

        if (((((int)firstBitPos)&7)|(bitSize&7)) == 0) {
            final int firstByteIndex = (int)(firstBitPos>>3);
            putIntAt_noCheck(helper, tab, offset+firstByteIndex, value, bitSize, bigEndian);
        } else {
            putIntAtBit_noCheck(helper, tab, (((long)offset)<<3)+firstBitPos, value, bitSize, bigEndian);
        }
    }

    static void putIntAt_noCheck(BaseBTHelper helper, Object tab, int firstByteIndex, int value, int bitSize, boolean bigEndian) {
        final int byteSizeM1 = ((bitSize-1)>>3);
        switch (byteSizeM1) {
        case 0: helper.put8Bits(tab, firstByteIndex, (byte)value); break;
        case 1: helper.put16Bits(tab, firstByteIndex, (short)value, bigEndian); break;
        case 2: helper.put24Bits(tab, firstByteIndex, value, bigEndian); break;
        case 3: helper.put32Bits(tab, firstByteIndex, value, bigEndian); break;
        default:
            throw new AssertionError();
        }
    }

    static void putIntAtBit_noCheck(
            BaseBTHelper helper,
            Object tab,
            long firstBitPos,
            int value,
            int bitSize,
            boolean bigEndian) {
        final int firstByteIndex = (int)(firstBitPos>>3);
        final int lastByteIndex = (int)((firstBitPos+bitSize-1)>>3);
        final int firstBitPosInByte = ((int)firstBitPos)&7;
        final int byteSizeM1 = lastByteIndex - firstByteIndex;
        if (bigEndian) {
            switch (byteSizeM1) {
            case 0: put_1_to_8_bits_over_1_byte_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,(byte)value,bitSize); break;
            case 1: put_2_to_16_bits_over_2_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,(short)value,bitSize); break;
            case 2: put_10_to_24_bits_over_3_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 3: put_18_to_32_bits_over_4_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 4: put_26_to_40_bits_over_5_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,(long)value,bitSize); break;
            default:
                throw new AssertionError();
            }
        } else {
            switch (byteSizeM1) {
            case 0: put_1_to_8_bits_over_1_byte_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,(byte)value,bitSize); break;
            case 1: put_2_to_16_bits_over_2_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,(short)value,bitSize); break;
            case 2: put_10_to_24_bits_over_3_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 3: put_18_to_32_bits_over_4_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 4: put_26_to_40_bits_over_5_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,(long)value,bitSize); break;
            default:
                throw new AssertionError();
            }
        }
    }

    /*
     * put long
     */

    static void putLongAtBit_bitSizeChecked(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, long value, int bitSize, boolean bigEndian) {
        helper.checkLimitBitPosBitSizeIfNeeded(limit, firstBitPos, bitSize);

        if (((((int)firstBitPos)&7)|(bitSize&7)) == 0) {
            final int firstByteIndex = (int)(firstBitPos>>3);
            putLongAt_noCheck(helper, tab, offset+firstByteIndex, value, bitSize, bigEndian);
        } else {
            putLongAtBit_noCheck(helper, tab, (((long)offset)<<3)+firstBitPos, value, bitSize, bigEndian);
        }
    }

    static void putLongAt_noCheck(BaseBTHelper helper, Object tab, int firstByteIndex, long value, int bitSize, boolean bigEndian) {
        final int byteSizeM1 = ((bitSize-1)>>3);
        switch (byteSizeM1) {
        case 0: helper.put8Bits(tab, firstByteIndex, (byte)value); break;
        case 1: helper.put16Bits(tab, firstByteIndex, (short)value, bigEndian); break;
        case 2: helper.put24Bits(tab, firstByteIndex, (int)value, bigEndian); break;
        case 3: helper.put32Bits(tab, firstByteIndex, (int)value, bigEndian); break;
        case 4: helper.put40Bits(tab, firstByteIndex, value, bigEndian); break;
        case 5: helper.put48Bits(tab, firstByteIndex, value, bigEndian); break;
        case 6: helper.put56Bits(tab, firstByteIndex, value, bigEndian); break;
        case 7: helper.put64Bits(tab, firstByteIndex, value, bigEndian); break;
        default:
            throw new AssertionError();
        }
    }

    static void putLongAtBit_noCheck(
            BaseBTHelper helper,
            Object tab,
            long firstBitPos,
            long value,
            int bitSize,
            boolean bigEndian) {
        final int firstByteIndex = (int)(firstBitPos>>3);
        final int lastByteIndex = (int)((firstBitPos+bitSize-1)>>3);
        final int firstBitPosInByte = ((int)firstBitPos)&7;
        final int byteSizeM1 = lastByteIndex - firstByteIndex;
        if (bigEndian) {
            switch (byteSizeM1) {
            case 0: put_1_to_8_bits_over_1_byte_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,(byte)value,bitSize); break;
            case 1: put_2_to_16_bits_over_2_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,(short)value,bitSize); break;
            case 2: put_10_to_24_bits_over_3_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,(int)value,bitSize); break;
            case 3: put_18_to_32_bits_over_4_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,(int)value,bitSize); break;
            case 4: put_26_to_40_bits_over_5_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 5: put_34_to_48_bits_over_6_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 6: put_42_to_56_bits_over_7_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 7: put_50_to_64_bits_over_8_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 8: put_58_to_64_bits_over_9_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            default:
                throw new AssertionError();
            }
        } else {
            switch (byteSizeM1) {
            case 0: put_1_to_8_bits_over_1_byte_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,(byte)value,bitSize); break;
            case 1: put_2_to_16_bits_over_2_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,(short)value,bitSize); break;
            case 2: put_10_to_24_bits_over_3_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,(int)value,bitSize); break;
            case 3: put_18_to_32_bits_over_4_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,(int)value,bitSize); break;
            case 4: put_26_to_40_bits_over_5_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 5: put_34_to_48_bits_over_6_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 6: put_42_to_56_bits_over_7_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 7: put_50_to_64_bits_over_8_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            case 8: put_58_to_64_bits_over_9_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,value,bitSize); break;
            default:
                throw new AssertionError();
            }
        }
    }

    /*
     * get int signed
     */

    static int getIntSignedAtBit_bitSizeChecked(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, int bitSize, boolean bigEndian) {
        helper.checkLimitBitPosBitSizeIfNeeded(limit, firstBitPos, bitSize);

        int result;
        if (((((int)firstBitPos)&7)|(bitSize&7)) == 0) {
            final int firstByteIndex = (int)(firstBitPos>>3);
            result = getIntSignedAt_noCheck(helper, tab, offset+firstByteIndex, bitSize, bigEndian);
        } else {
            result = getIntSignedAtBit_noCheck(helper, tab, (((long)offset)<<3)+firstBitPos, bitSize, bigEndian);
        }
        return result;
    }

    static int getIntSignedAt_noCheck(
            BaseBTHelper helper,
            Object tab,
            int firstByteIndex,
            int bitSize,
            boolean bigEndian) {
        final int byteSizeM1 = ((bitSize-1)>>3);
        switch (byteSizeM1) {
        case 0: return helper.get8Bits(tab, firstByteIndex);
        case 1: return helper.get16Bits(tab, firstByteIndex, bigEndian);
        case 2: return helper.get24Bits(tab, firstByteIndex, bigEndian);
        case 3: return helper.get32Bits(tab, firstByteIndex, bigEndian);
        default:
            throw new AssertionError();
        }
    }

    static int getIntSignedAtBit_noCheck(
            BaseBTHelper helper,
            Object tab,
            long firstBitPos,
            int bitSize,
            boolean bigEndian) {
        final int firstByteIndex = (int)(firstBitPos>>3);
        final int lastByteIndex = (int)((firstBitPos+bitSize-1)>>3);
        final int firstBitPosInByte = ((int)firstBitPos)&7;
        final int byteSizeM1 = lastByteIndex - firstByteIndex;
        if (bigEndian) {
            switch (byteSizeM1) {
            case 0: return get_1_to_8_bits_over_1_byte_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 1: return get_2_to_16_bits_over_2_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 2: return get_10_to_24_bits_over_3_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 3: return get_18_to_32_bits_over_4_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 4: return (int)get_26_to_40_bits_over_5_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            default:
                throw new AssertionError();
            }
        } else {
            switch (byteSizeM1) {
            case 0: return get_1_to_8_bits_over_1_byte_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 1: return get_2_to_16_bits_over_2_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 2: return get_10_to_24_bits_over_3_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 3: return get_18_to_32_bits_over_4_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 4: return (int)get_26_to_40_bits_over_5_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            default:
                throw new AssertionError();
            }
        }
    }

    /*
     * get long signed
     */

    static long getLongSignedAtBit_bitSizeChecked(BaseBTHelper helper, Object tab, int offset, int limit, long firstBitPos, int bitSize, boolean bigEndian) {
        helper.checkLimitBitPosBitSizeIfNeeded(limit, firstBitPos, bitSize);

        long result;
        if (((((int)firstBitPos)&7)|(bitSize&7)) == 0) {
            final int firstByteIndex = (int)(firstBitPos>>3);
            result = getLongSignedAt_noCheck(helper, tab, offset+firstByteIndex, bitSize, bigEndian);
        } else {
            result = getLongSignedAtBit_noCheck(helper, tab, (((long)offset)<<3)+firstBitPos, bitSize, bigEndian);
        }
        return result;
    }

    static long getLongSignedAt_noCheck(BaseBTHelper helper, Object tab, int firstByteIndex, int bitSize, boolean bigEndian) {
        final int byteSizeM1 = ((bitSize-1)>>3);
        switch (byteSizeM1) {
        case 0: return helper.get8Bits(tab, firstByteIndex);
        case 1: return helper.get16Bits(tab, firstByteIndex, bigEndian);
        case 2: return helper.get24Bits(tab, firstByteIndex, bigEndian);
        case 3: return helper.get32Bits(tab, firstByteIndex, bigEndian);
        case 4: return helper.get40Bits(tab, firstByteIndex, bigEndian);
        case 5: return helper.get48Bits(tab, firstByteIndex, bigEndian);
        case 6: return helper.get56Bits(tab, firstByteIndex, bigEndian);
        case 7: return helper.get64Bits(tab, firstByteIndex, bigEndian);
        default:
            throw new AssertionError();
        }
    }

    static long getLongSignedAtBit_noCheck(BaseBTHelper helper, Object tab, long firstBitPos, int bitSize, boolean bigEndian) {
        final int firstByteIndex = (int)(firstBitPos>>3);
        final int lastByteIndex = (int)((firstBitPos+bitSize-1)>>3);
        final int firstBitPosInByte = ((int)firstBitPos)&7;
        final int byteSizeM1 = lastByteIndex - firstByteIndex;
        if (bigEndian) {
            switch (byteSizeM1) {
            case 0: return get_1_to_8_bits_over_1_byte_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 1: return get_2_to_16_bits_over_2_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 2: return get_10_to_24_bits_over_3_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 3: return get_18_to_32_bits_over_4_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 4: return get_26_to_40_bits_over_5_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 5: return get_34_to_48_bits_over_6_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 6: return get_42_to_56_bits_over_7_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 7: return get_50_to_64_bits_over_8_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 8: return get_58_to_64_bits_over_9_bytes_bigEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            default:
                throw new AssertionError();
            }
        } else {
            switch (byteSizeM1) {
            case 0: return get_1_to_8_bits_over_1_byte_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 1: return get_2_to_16_bits_over_2_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 2: return get_10_to_24_bits_over_3_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 3: return get_18_to_32_bits_over_4_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 4: return get_26_to_40_bits_over_5_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 5: return get_34_to_48_bits_over_6_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 6: return get_42_to_56_bits_over_7_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 7: return get_50_to_64_bits_over_8_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            case 8: return get_58_to_64_bits_over_9_bytes_littleEndian(helper,tab,firstByteIndex,firstBitPosInByte,bitSize);
            default:
                throw new AssertionError();
            }
        }
    }

    /*
     * tab copy
     */

    static void tabCopyBits_noCheck_bitShiftMultipleOf8(
            BaseBTHelper srcHelper,
            Object src,
            long srcFirstBitPos,
            BaseBTHelper destHelper,
            Object dest,
            long destFirstBitPos,
            long bitSize,
            boolean bigEndian) {
        if ((src == dest) && (srcFirstBitPos == destFirstBitPos)) {
            // Quick case.
            return;
        }
        
        if (bitSize <= 8) {
            int bits = getIntSignedAtBit_noCheck(srcHelper, src, srcFirstBitPos, (int)bitSize, bigEndian);
            putIntAtBit_noCheck(destHelper, dest, destFirstBitPos, bits, (int)bitSize, bigEndian);
            return;
        }

        // Else doesn't work.
        if(ASSERTIONS)assert(bitSize > 6);

        final long srcLastBitPos = srcFirstBitPos + bitSize - 1;
        final long destLastBitPos = destFirstBitPos + bitSize - 1;

        final int srcFirstByteIndex = (int)(srcFirstBitPos>>3);
        final int destFirstByteIndex = (int)(destFirstBitPos>>3);
        final int srcLastByteIndex = (int)((srcLastBitPos)>>3);
        final int destLastByteIndex = (int)((destLastBitPos)>>3);

        final int srcFirstBitPosMod8 = (((int)srcFirstBitPos)&7);
        final int srcLastBitPosMod8 = (((int)srcLastBitPos)&7);

        final boolean srcStartsOnAByte = (srcFirstBitPosMod8 == 0);
        final boolean srcEndsOnAByte = (srcLastBitPosMod8 == 7);

        final int oneIfFirstByteApart = (srcStartsOnAByte ? 0 : 1);
        final int oneIfLastByteApart = (srcEndsOnAByte ? 0 : 1);

        // Need to retrieve values before an eventual intermediate copy erase them.
        final short srcInitialFirstAndLastBytes = srcHelper.get8BitsAnd8Bits(src,srcFirstByteIndex,srcLastByteIndex);
        final byte srcInitialFirstByte = (byte)(srcInitialFirstAndLastBytes>>8);
        final byte srcInitialLastByte = (byte)srcInitialFirstAndLastBytes;
        final short destInitialFirstAndLastBytes = destHelper.get8BitsAnd8Bits(dest,destFirstByteIndex,destLastByteIndex);
        final byte destInitialFirstByte = (byte)(destInitialFirstAndLastBytes>>8);
        final byte destInitialLastByte = (byte)destInitialFirstAndLastBytes;

        final int srcNbrOfBytes = srcLastByteIndex - srcFirstByteIndex + 1;

        final int byteSizeForBytewiseCopy = srcNbrOfBytes-(oneIfFirstByteApart+oneIfLastByteApart);
        if (byteSizeForBytewiseCopy != 0) {
            final int srcBytePos = srcFirstByteIndex+oneIfFirstByteApart;
            final int destBytePos = destFirstByteIndex+oneIfFirstByteApart;
            tabBytewiseCopy(srcHelper, src, srcBytePos, destHelper, dest, destBytePos, byteSizeForBytewiseCopy, bigEndian);
        }
        if (!srcStartsOnAByte) {
            final int mask1;
            if (bigEndian) {
                mask1 = byteMask1From0ToIndex(7-srcFirstBitPosMod8);
            } else {
                mask1 = byteMask1FromIndexTo7(srcFirstBitPosMod8);
            }
            destHelper.put8Bits(dest,destFirstByteIndex,(byte)((destInitialFirstByte & (~mask1))|(srcInitialFirstByte & mask1)));
        }
        if (!srcEndsOnAByte) {
            final int mask1;
            if (bigEndian) {
                mask1 = byteMask1FromIndexTo7(7-srcLastBitPosMod8);
            } else {
                mask1 = byteMask1From0ToIndex(srcLastBitPosMod8);
            }
            destHelper.put8Bits(dest,destLastByteIndex,(byte)((destInitialLastByte & (~mask1))|(srcInitialLastByte & mask1)));
        }
    }

    static void tabCopyBits_noCheck_general(
            BaseBTHelper srcHelper,
            Object src,
            long srcFirstBitPos,
            BaseBTHelper destHelper,
            Object dest,
            long destFirstBitPos,
            long bitSize,
            boolean bigEndian) {
        if ((src == dest) && (srcFirstBitPos == destFirstBitPos)) {
            // Quick case.
            return;
        }
        
        if (bitSize <= 32) {
            final int bits = getIntSignedAtBit_noCheck(srcHelper, src, srcFirstBitPos, (int)bitSize, bigEndian);
            putIntAtBit_noCheck(destHelper, dest, destFirstBitPos, bits, (int)bitSize, bigEndian);
            return;
        }

        if (bitSize <= 64) {
            final long bits = getLongSignedAtBit_noCheck(srcHelper, src, srcFirstBitPos, (int)bitSize, bigEndian);
            putLongAtBit_noCheck(destHelper, dest, destFirstBitPos, bits, (int)bitSize, bigEndian);
            return;
        }

        final long srcLastBitPos = srcFirstBitPos + bitSize - 1;
        final long destLastBitPos = destFirstBitPos + bitSize - 1;

        final int srcFirstByteIndex = (int)(srcFirstBitPos>>3);
        final int destFirstByteIndex = (int)(destFirstBitPos>>3);
        final int srcLastByteIndex = (int)((srcLastBitPos)>>3);
        final int destLastByteIndex = (int)((destLastBitPos)>>3);

        final int srcFirstBitPosMod8 = (((int)srcFirstBitPos)&7);

        final long srcToDestBitPosShift = destFirstBitPos - srcFirstBitPos;

        final int destFirstBitPosMod8 = (((int)destFirstBitPos)&7);
        final int destLastBitPosMod8 = (((int)destLastBitPos)&7);

        final boolean destStartsOnAByte = (destFirstBitPosMod8 == 0);
        final boolean destEndsOnAByte = (destLastBitPosMod8 == 7);

        final int destFirstPlainByteIndex = destFirstByteIndex + (destStartsOnAByte ? 0 : 1);
        final int destLastPlainByteIndex = destLastByteIndex - (destEndsOnAByte ? 0 : 1);

        if (srcToDestBitPosShift > 0) {

            /*
             * copying from last to first
             */

            final int srcToDestLastByteShift = destLastByteIndex - srcLastByteIndex;

            // in [-7,7]
            final int srcToDestBitPosShiftAfterLastByteShift = (int)(srcToDestBitPosShift - (((long)srcToDestLastByteShift)<<3));
            if(ASSERTIONS)assert((srcToDestBitPosShiftAfterLastByteShift >= -7) && (srcToDestBitPosShiftAfterLastByteShift <= 7));

            int srcI;
            if (destEndsOnAByte) {
                srcI = srcLastByteIndex;
            } else {
                final int srcLastBitPosMod8 = ((int)srcLastBitPos)&7;
                copyLastBits(srcHelper, src, srcLastByteIndex, srcLastBitPosMod8, destHelper, dest, destLastByteIndex, destLastBitPosMod8, bigEndian);

                srcI = computeByteIndex(srcLastBitPos - (destLastBitPosMod8+1));
            }

            if (destFirstPlainByteIndex <= destLastPlainByteIndex) {
                final int srcFirstBitPosInLeftByte = (srcFirstBitPosMod8 - destFirstBitPosMod8)&7;
                int destI = destLastPlainByteIndex;
                if (bigEndian) {
                    while (destI >= destFirstPlainByteIndex + 7) {
                        final long bits = get_58_to_64_bits_over_9_bytes_bigEndian(srcHelper, src, srcI-8, srcFirstBitPosInLeftByte, 64);
                        destHelper.put64Bits_bigEndian(dest, destI-7, bits);
                        srcI -= 8;
                        destI -= 8;
                    }
                    while (destI >= destFirstPlainByteIndex) {
                        final byte bits = (byte)get_2_to_16_bits_over_2_bytes_bigEndian(srcHelper, src, srcI-1, srcFirstBitPosInLeftByte, 8);
                        destHelper.put8Bits(dest, destI, bits);
                        srcI--;
                        destI--;
                    }
                } else {
                    while (destI >= destFirstPlainByteIndex + 7) {
                        final long bits = get_58_to_64_bits_over_9_bytes_littleEndian(srcHelper, src, srcI-8, srcFirstBitPosInLeftByte, 64);
                        destHelper.put64Bits_littleEndian(dest, destI-7, bits);
                        srcI -= 8;
                        destI -= 8;
                    }
                    while (destI >= destFirstPlainByteIndex) {
                        final byte bits = (byte)get_2_to_16_bits_over_2_bytes_littleEndian(srcHelper, src, srcI-1, srcFirstBitPosInLeftByte, 8);
                        destHelper.put8Bits(dest, destI, bits);
                        srcI--;
                        destI--;
                    }
                }
            }

            if (!destStartsOnAByte) {
                copyFirstBits(srcHelper, src, srcFirstByteIndex, srcFirstBitPosMod8, destHelper, dest, destFirstByteIndex, destFirstBitPosMod8, bigEndian);
            }
        } else {
            /*
             * copying from first to last
             */

            final int srcToDestFirstByteShift = destFirstByteIndex - srcFirstByteIndex;

            // in [-7,7]
            final int srcToDestBitPosShiftAfterFirstByteShift = (int)(srcToDestBitPosShift - (((long)srcToDestFirstByteShift)<<3));
            if(ASSERTIONS)assert((srcToDestBitPosShiftAfterFirstByteShift >= -7) && (srcToDestBitPosShiftAfterFirstByteShift <= 7));

            int srcI;
            if (destStartsOnAByte) {
                srcI = srcFirstByteIndex;
            } else {
                copyFirstBits(srcHelper, src, srcFirstByteIndex, srcFirstBitPosMod8, destHelper, dest, destFirstByteIndex, destFirstBitPosMod8, bigEndian);

                srcI = computeByteIndex(srcFirstBitPos + (8-destFirstBitPosMod8));
            }

            if (destFirstPlainByteIndex <= destLastPlainByteIndex) {
                final int srcFirstBitPosInLeftByte = (srcFirstBitPosMod8 - destFirstBitPosMod8)&7;
                int destI = destFirstPlainByteIndex;
                if (bigEndian) {
                    // Not doing "destI + 7" because it could overflow.
                    while (destI <= destLastPlainByteIndex - 7) {
                        final long bits = get_58_to_64_bits_over_9_bytes_bigEndian(srcHelper, src, srcI, srcFirstBitPosInLeftByte, 64);
                        destHelper.put64Bits_bigEndian(dest, destI, bits);
                        srcI += 8;
                        destI += 8;
                    }
                    while (destI <= destLastPlainByteIndex) {
                        final byte bits = (byte)get_2_to_16_bits_over_2_bytes_bigEndian(srcHelper, src, srcI, srcFirstBitPosInLeftByte, 8);
                        destHelper.put8Bits(dest, destI, bits);
                        srcI++;
                        destI++;
                    }
                } else {
                    // Not doing "destI + 7" because it could overflow.
                    while (destI <= destLastPlainByteIndex - 7) {
                        final long bits = get_58_to_64_bits_over_9_bytes_littleEndian(srcHelper, src, srcI, srcFirstBitPosInLeftByte, 64);
                        destHelper.put64Bits_littleEndian(dest, destI, bits);
                        srcI += 8;
                        destI += 8;
                    }
                    while (destI <= destLastPlainByteIndex) {
                        final byte bits = (byte)get_2_to_16_bits_over_2_bytes_littleEndian(srcHelper, src, srcI, srcFirstBitPosInLeftByte, 8);
                        destHelper.put8Bits(dest, destI, bits);
                        srcI++;
                        destI++;
                    }
                }
            }

            if (!destEndsOnAByte) {
                final int srcLastBitPosMod8 = ((int)srcLastBitPos)&7;
                copyLastBits(srcHelper, src, srcLastByteIndex, srcLastBitPosMod8, destHelper, dest, destLastByteIndex, destLastBitPosMod8, bigEndian);
            }
        }
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /*
     * Many little treatments specific to a byte size.
     * Could have more generic treatments, using loops and ifs,
     * and only using helper's methods to deal with 8 bits
     * at a time, but would be slower (especially because
     * helper's indirection slows things down).
     */

    /*
     * put bits
     */

    private static void put_1_to_8_bits_over_1_byte_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, byte bits, int bitSize) {
        final byte previous8Bits = helper.get8Bits(tab, firstByteIndex);
        final int previous8BitsMask = (0xFF<<(8-firstBitPosInByte)) | (0xFF>>>(firstBitPosInByte+bitSize));
        helper.put8Bits(tab, firstByteIndex, (byte)((previous8Bits & previous8BitsMask) | (((bits<<(8-bitSize))&0xFF)>>>firstBitPosInByte)));
    }

    private static void put_1_to_8_bits_over_1_byte_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, byte bits, int bitSize) {
        final byte previous8Bits = helper.get8Bits(tab, firstByteIndex);
        final int previous8BitsMask = (0xFF>>>(8-firstBitPosInByte)) | (0xFF<<(firstBitPosInByte+bitSize));
        helper.put8Bits(tab, firstByteIndex, (byte)((previous8Bits & previous8BitsMask) | ((bits&(0xFF>>>(8-bitSize)))<<firstBitPosInByte)));
    }

    private static void put_2_to_16_bits_over_2_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, short bits, int bitSize) {
        final short previous16Bits = helper.get16Bits_bigEndian(tab, firstByteIndex);
        final int previous16BitsMask = (0xFFFF<<(16-firstBitPosInByte)) | (0xFFFF>>>(firstBitPosInByte+bitSize));
        helper.put16Bits_bigEndian(tab, firstByteIndex, (short)((previous16Bits & previous16BitsMask) | (((bits<<(16-bitSize))&0xFFFF)>>>firstBitPosInByte)));
    }

    private static void put_2_to_16_bits_over_2_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, short bits, int bitSize) {
        final short previous16Bits = helper.get16Bits_littleEndian(tab, firstByteIndex);
        final int previous16BitsMask = (0xFFFF>>>(16-firstBitPosInByte)) | (0xFFFF<<(firstBitPosInByte+bitSize));
        helper.put16Bits_littleEndian(tab, firstByteIndex, (short)((previous16Bits & previous16BitsMask) | ((bits&(0xFFFF>>>(16-bitSize)))<<firstBitPosInByte)));
    }

    private static void put_10_to_24_bits_over_3_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+2);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF<<(8-firstBitPosInByte));// no need for it: &0xFF;
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF>>>(lastBitPosInByte+1));
        final int keptBits = ((left8Bits & leftMask)<<16) | (right8Bits & rightMask);
        helper.put24Bits_bigEndian(tab, firstByteIndex, (keptBits | (((bits<<(24-bitSize))&0xFFFFFF)>>>firstBitPosInByte)));
    }

    private static void put_10_to_24_bits_over_3_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+2);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF>>>(8-firstBitPosInByte));
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF<<(lastBitPosInByte+1));// no need for it: &0xFF;
        final int keptBits = (left8Bits & leftMask) | ((right8Bits & rightMask)<<16);
        helper.put24Bits_littleEndian(tab, firstByteIndex, (keptBits | ((bits&(0xFFFFFF>>>(24-bitSize)))<<firstBitPosInByte)));
    }

    private static void put_18_to_32_bits_over_4_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+3);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF<<(8-firstBitPosInByte));// no need for it: &0xFF;
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF>>>(lastBitPosInByte+1));
        final int keptBits = ((left8Bits & leftMask)<<24) | (right8Bits & rightMask);
        helper.put32Bits_bigEndian(tab, firstByteIndex, (keptBits | (((bits<<(32-bitSize))&0xFFFFFFFF)>>>firstBitPosInByte)));
    }

    private static void put_18_to_32_bits_over_4_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+3);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF>>>(8-firstBitPosInByte));
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF<<(lastBitPosInByte+1));// no need for it: &0xFF;
        final int keptBits = (left8Bits & leftMask) | ((right8Bits & rightMask)<<24);
        helper.put32Bits_littleEndian(tab, firstByteIndex, (keptBits | ((bits&(0xFFFFFFFF>>>(32-bitSize)))<<firstBitPosInByte)));
    }

    private static void put_26_to_40_bits_over_5_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+4);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF<<(8-firstBitPosInByte));// no need for it: &0xFF;
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF>>>(lastBitPosInByte+1));
        final long keptBits = (((long)(left8Bits & leftMask))<<32) | (long)(right8Bits & rightMask);
        helper.put40Bits_bigEndian(tab, firstByteIndex, (keptBits | (((bits<<(40-bitSize))&0xFFFFFFFFFFL)>>>firstBitPosInByte)));
    }

    private static void put_26_to_40_bits_over_5_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+4);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF>>>(8-firstBitPosInByte));
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF<<(lastBitPosInByte+1));// no need for it: &0xFF;
        final long keptBits = (long)(left8Bits & leftMask) | (((long)(right8Bits & rightMask))<<32);
        helper.put40Bits_littleEndian(tab, firstByteIndex, (keptBits | ((bits&(0xFFFFFFFFFFL>>>(40-bitSize)))<<firstBitPosInByte)));
    }

    private static void put_34_to_48_bits_over_6_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+5);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF<<(8-firstBitPosInByte));// no need for it: &0xFF;
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF>>>(lastBitPosInByte+1));
        final long keptBits = (((long)(left8Bits & leftMask))<<40) | (long)(right8Bits & rightMask);
        helper.put48Bits_bigEndian(tab, firstByteIndex, (keptBits | (((bits<<(48-bitSize))&0xFFFFFFFFFFFFL)>>>firstBitPosInByte)));
    }

    private static void put_34_to_48_bits_over_6_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+5);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF>>>(8-firstBitPosInByte));
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF<<(lastBitPosInByte+1));// no need for it: &0xFF;
        final long keptBits = (long)(left8Bits & leftMask) | (((long)(right8Bits & rightMask))<<40);
        helper.put48Bits_littleEndian(tab, firstByteIndex, (keptBits | ((bits&(0xFFFFFFFFFFFFL>>>(48-bitSize)))<<firstBitPosInByte)));
    }

    private static void put_42_to_56_bits_over_7_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+6);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF<<(8-firstBitPosInByte));// no need for it: &0xFF;
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF>>>(lastBitPosInByte+1));
        final long keptBits = (((long)(left8Bits & leftMask))<<48) | (long)(right8Bits & rightMask);
        helper.put56Bits_bigEndian(tab, firstByteIndex, (keptBits | (((bits<<(56-bitSize))&0xFFFFFFFFFFFFFFL)>>>firstBitPosInByte)));
    }

    private static void put_42_to_56_bits_over_7_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+6);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF>>>(8-firstBitPosInByte));
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF<<(lastBitPosInByte+1));// no need for it: &0xFF;
        final long keptBits = (long)(left8Bits & leftMask) | (((long)(right8Bits & rightMask))<<48);
        helper.put56Bits_littleEndian(tab, firstByteIndex, (keptBits | ((bits&(0xFFFFFFFFFFFFFFL>>>(56-bitSize)))<<firstBitPosInByte)));
    }

    private static void put_50_to_64_bits_over_8_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+7);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF<<(8-firstBitPosInByte));// no need for it: &0xFF;
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF>>>(lastBitPosInByte+1));
        final long keptBits = (((long)(left8Bits & leftMask))<<56) | (long)(right8Bits & rightMask);
        helper.put64Bits_bigEndian(tab, firstByteIndex, (keptBits | ((bits<<(64-bitSize))>>>firstBitPosInByte)));
    }

    private static void put_50_to_64_bits_over_8_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+7);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF>>>(8-firstBitPosInByte));
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF<<(lastBitPosInByte+1));// no need for it: &0xFF;
        final long keptBits = (long)(left8Bits & leftMask) | (((long)(right8Bits & rightMask))<<56);
        helper.put64Bits_littleEndian(tab, firstByteIndex, (keptBits | ((bits&(0xFFFFFFFFFFFFFFFFL>>>(64-bitSize)))<<firstBitPosInByte)));
    }

    private static void put_58_to_64_bits_over_9_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+8);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF<<(8-firstBitPosInByte));// no need for it: &0xFF;
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF>>>(lastBitPosInByte+1));
        final long keptMSBits = (((long)(left8Bits & leftMask))<<56);
        final int keptLSBits = (right8Bits & rightMask);
        final long msbits = (keptMSBits | ((bits<<(64-bitSize))>>>firstBitPosInByte));
        final byte lsbits = (byte)(keptLSBits | ((((int)bits)<<(7-lastBitPosInByte))&(~rightMask)));
        helper.put72Bits_bigEndian(tab, firstByteIndex, msbits, lsbits);
    }

    private static void put_58_to_64_bits_over_9_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, long bits, int bitSize) {
        final short leftAndRightBits = helper.get8BitsAnd8Bits(tab, firstByteIndex, firstByteIndex+8);
        final byte left8Bits = (byte)(leftAndRightBits>>8);
        final byte right8Bits = (byte)leftAndRightBits;
        final int leftMask = (0xFF>>>(8-firstBitPosInByte));
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        final int rightMask = (0xFF<<(lastBitPosInByte+1));// no need for it: &0xFF;
        final int keptLSBits = (left8Bits & leftMask);
        final long keptMSBits = (((long)(right8Bits & rightMask))<<56);
        final byte lsbits = (byte)(keptLSBits | ((((int)bits)<<firstBitPosInByte)&(~leftMask)));
        final long msbits = (keptMSBits | ((bits&(0xFFFFFFFFFFFFFFFFL>>>(64-bitSize)))>>>(8-firstBitPosInByte)));
        helper.put72Bits_littleEndian(tab, firstByteIndex, msbits, lsbits);
    }

    /*
     * get bits
     * 
     * Returning int instead of byte or short, since value
     * would be expanded to int (or long) afterward anyway.
     */

    private static int get_1_to_8_bits_over_1_byte_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final byte bits = helper.get8Bits(tab, firstByteIndex);
        return ((bits<<(24+firstBitPosInByte))>>(32-bitSize));
    }

    private static int get_1_to_8_bits_over_1_byte_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final byte bits = helper.get8Bits(tab, firstByteIndex);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return (bits<<((24+7)-lastBitPosInByte))>>(32-bitSize);
    }

    private static int get_2_to_16_bits_over_2_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final short bits = helper.get16Bits_bigEndian(tab, firstByteIndex);
        return ((bits<<(16+firstBitPosInByte))>>(32-bitSize));
    }

    private static int get_2_to_16_bits_over_2_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final short bits = helper.get16Bits_littleEndian(tab, firstByteIndex);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return (bits<<((16+7)-lastBitPosInByte))>>(32-bitSize);
    }

    private static int get_10_to_24_bits_over_3_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final int bits = helper.get24Bits_bigEndian(tab, firstByteIndex);
        return ((bits<<(8+firstBitPosInByte))>>(32-bitSize));
    }

    private static int get_10_to_24_bits_over_3_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final int bits = helper.get24Bits_littleEndian(tab, firstByteIndex);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return (bits<<((8+7)-lastBitPosInByte))>>(32-bitSize);
    }

    private static int get_18_to_32_bits_over_4_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final int bits = helper.get32Bits_bigEndian(tab, firstByteIndex);
        return ((bits<<firstBitPosInByte)>>(32-bitSize));
    }

    private static int get_18_to_32_bits_over_4_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final int bits = helper.get32Bits_littleEndian(tab, firstByteIndex);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return (bits<<(7-lastBitPosInByte))>>(32-bitSize);
    }

    private static long get_26_to_40_bits_over_5_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final long bits = helper.get40Bits_bigEndian(tab, firstByteIndex);
        return ((bits<<(24+firstBitPosInByte))>>(64-bitSize));
    }

    private static long get_26_to_40_bits_over_5_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final long bits = helper.get40Bits_littleEndian(tab, firstByteIndex);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return (bits<<((24+7)-lastBitPosInByte))>>(64-bitSize);
    }

    private static long get_34_to_48_bits_over_6_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final long bits = helper.get48Bits_bigEndian(tab, firstByteIndex);
        return ((bits<<(16+firstBitPosInByte))>>(64-bitSize));
    }

    private static long get_34_to_48_bits_over_6_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final long bits = helper.get48Bits_littleEndian(tab, firstByteIndex);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return (bits<<((16+7)-lastBitPosInByte))>>(64-bitSize);
    }

    private static long get_42_to_56_bits_over_7_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final long bits = helper.get56Bits_bigEndian(tab, firstByteIndex);
        return ((bits<<(8+firstBitPosInByte))>>(64-bitSize));
    }

    private static long get_42_to_56_bits_over_7_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final long bits = helper.get56Bits_littleEndian(tab, firstByteIndex);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return (bits<<((8+7)-lastBitPosInByte))>>(64-bitSize);
    }

    private static long get_50_to_64_bits_over_8_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final long bits = helper.get64Bits_bigEndian(tab, firstByteIndex);
        return ((bits<<firstBitPosInByte)>>(64-bitSize));
    }

    private static long get_50_to_64_bits_over_8_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final long bits = helper.get64Bits_littleEndian(tab, firstByteIndex);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return (bits<<(7-lastBitPosInByte))>>(64-bitSize);
    }

    private static long get_58_to_64_bits_over_9_bytes_bigEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final long msbits = helper.get64Bits_bigEndian(tab, firstByteIndex);
        final byte lsbits = helper.get8Bits(tab, firstByteIndex+8);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return ((msbits<<firstBitPosInByte)>>(64-bitSize)) | ((lsbits&0xFF)>>>(7-lastBitPosInByte));
    }

    private static long get_58_to_64_bits_over_9_bytes_littleEndian(BaseBTHelper helper, Object tab, int firstByteIndex, int firstBitPosInByte, int bitSize) {
        final byte lsbits = helper.get8Bits(tab, firstByteIndex);
        final long msbits = helper.get64Bits_littleEndian(tab, firstByteIndex+1);
        final int lastBitPosInByte = (firstBitPosInByte+bitSize-1)&7;
        return ((lsbits&0xFF)>>>firstBitPosInByte) | ((msbits<<(7-lastBitPosInByte))>>(64-bitSize));
    }

    /*
     * tab copy
     */

     /**
      * A sort of arraycopy that works on tabs.
      * 
      * Precondition: if byteSize > 0, dest, or the buffer it is backing, must be writable.
      */
    private static void tabBytewiseCopy(
            BaseBTHelper srcHelper,
            Object src,
            int srcFirstByteIndex,
            BaseBTHelper destHelper,
            Object dest,
            int destFirstByteIndex,
            int byteSize,
            boolean bigEndian) {
        final boolean srcHasArray = srcHelper.hasArray(src);
        final boolean destHasArray = destHelper.hasArray(dest);
        if (srcHasArray && destHasArray) {
            System.arraycopy(
                    srcHelper.array(src),
                    srcHelper.arrayOffset(src) + srcFirstByteIndex,
                    destHelper.array(dest),
                    destHelper.arrayOffset(dest) + destFirstByteIndex,
                    byteSize);
            return;
        }
        // Copying from first to last or last to first,
        // to ensure we don't erase bytes to copy with copied bytes
        // if both buffers are identical.
        // Copy is only allowed between ByteBuffers of same order,
        // so we can use long get/put.
        if (destFirstByteIndex - srcFirstByteIndex > 0) {
            // last to first
            int i=byteSize;
            if (bigEndian) {
                while (i > 7) {
                    i -= 8;
                    destHelper.put64Bits_bigEndian(dest,destFirstByteIndex + i, srcHelper.get64Bits_bigEndian(src,srcFirstByteIndex + i));
                }
            } else {
                while (i > 7) {
                    i -= 8;
                    destHelper.put64Bits_littleEndian(dest,destFirstByteIndex + i, srcHelper.get64Bits_littleEndian(src,srcFirstByteIndex + i));
                }
            }
            while (i > 0) {
                i--;
                destHelper.put8Bits(dest,destFirstByteIndex + i, srcHelper.get8Bits(src,srcFirstByteIndex + i));
            }
        } else {
            // First to last
            int i=0;
            if (bigEndian) {
                while (i < byteSize - 7) {
                    destHelper.put64Bits_bigEndian(dest,destFirstByteIndex + i, srcHelper.get64Bits_bigEndian(src,srcFirstByteIndex + i));
                    i += 8;
                }
            } else {
                while (i < byteSize - 7) {
                    destHelper.put64Bits_littleEndian(dest,destFirstByteIndex + i, srcHelper.get64Bits_littleEndian(src,srcFirstByteIndex + i));
                    i += 8;
                }
            }
            while (i < byteSize) {
                destHelper.put8Bits(dest,destFirstByteIndex + i, srcHelper.get8Bits(src,srcFirstByteIndex + i));
                i++;
            }
        }
    }

    /*
     * masks
     */

    /**
     * @param fromBitIndex Must be in [0,7].
     */
    private static byte byteMask1FromIndexTo7(int fromBitIndex) {
        return (byte)(0xFF<<fromBitIndex);
    }

    /**
     * @param toBitIndex Must be in [0,7].
     */
    private static byte byteMask1From0ToIndex(int toBitIndex) {
        // No need to use unsigned shift, since 0xFF
        // is an int.
        return (byte)(0xFF>>(7-toBitIndex));
    }

    /*
     * first/last bits copy
     */

    /**
     * Copies the 1 to 7 bits that end up in dest first byte.
     */
    private static void copyFirstBits(
            BaseBTHelper srcHelper,
            Object src,
            int srcFirstByteIndex,
            int srcFirstBitPosMod8,
            BaseBTHelper destHelper,
            Object dest,
            int destFirstByteIndex,
            int destFirstBitPosMod8,
            boolean bigEndian) {
        if (bigEndian) {
            copyFirstBits_bigEndian(srcHelper, src, srcFirstByteIndex, srcFirstBitPosMod8, destHelper, dest, destFirstByteIndex, destFirstBitPosMod8);
        } else {
            copyFirstBits_littleEndian(srcHelper, src, srcFirstByteIndex, srcFirstBitPosMod8, destHelper, dest, destFirstByteIndex, destFirstBitPosMod8);
        }
    }

    private static void copyFirstBits_bigEndian(
            BaseBTHelper srcHelper,
            Object src,
            int srcFirstByteIndex,
            int srcFirstBitPosMod8,
            BaseBTHelper destHelper,
            Object dest,
            int destFirstByteIndex,
            int destFirstBitPosMod8) {
        final byte srcBitsAtDestPos;
        if (srcFirstBitPosMod8 - destFirstBitPosMod8 > 0) {
            final int srcFirstBitPosInByte = srcFirstBitPosMod8 - destFirstBitPosMod8;
            srcBitsAtDestPos = (byte)get_2_to_16_bits_over_2_bytes_bigEndian(srcHelper, src, srcFirstByteIndex, srcFirstBitPosInByte, 8);
        } else {
            srcBitsAtDestPos = (byte)(srcHelper.get8Bits(src,srcFirstByteIndex)>>(destFirstBitPosMod8-srcFirstBitPosMod8));
        }
        final byte destBitsMask1 = byteMask1From0ToIndex(7-destFirstBitPosMod8);
        destHelper.put8Bits(dest,destFirstByteIndex,(byte)((destHelper.get8Bits(dest,destFirstByteIndex) & (~destBitsMask1)) | (srcBitsAtDestPos & destBitsMask1)));
    }

    private static void copyFirstBits_littleEndian(
            BaseBTHelper srcHelper,
            Object src,
            int srcFirstByteIndex,
            int srcFirstBitPosMod8,
            BaseBTHelper destHelper,
            Object dest,
            int destFirstByteIndex,
            int destFirstBitPosMod8) {
        final byte srcBitsAtDestPos;
        if (srcFirstBitPosMod8 - destFirstBitPosMod8 > 0) {
            final int srcFirstBitPosInByte = srcFirstBitPosMod8-destFirstBitPosMod8;
            srcBitsAtDestPos = (byte)get_2_to_16_bits_over_2_bytes_littleEndian(srcHelper, src, srcFirstByteIndex, srcFirstBitPosInByte, 8);
        } else {
            srcBitsAtDestPos = (byte)(srcHelper.get8Bits(src,srcFirstByteIndex)<<(destFirstBitPosMod8-srcFirstBitPosMod8));
        }
        final byte destBitsMask1 = byteMask1FromIndexTo7(destFirstBitPosMod8);
        destHelper.put8Bits(dest,destFirstByteIndex,(byte)((destHelper.get8Bits(dest,destFirstByteIndex) & (~destBitsMask1)) | (srcBitsAtDestPos & destBitsMask1)));
    }

    /**
     * Copies the 1 to 7 bits that end up in dest last byte.
     */
    private static void copyLastBits(
            BaseBTHelper srcHelper,
            Object src,
            int srcLastByteIndex,
            int srcLastBitPosMod8,
            BaseBTHelper destHelper,
            Object dest,
            int destLastByteIndex,
            int destLastBitPosMod8,
            boolean bigEndian) {
        if (bigEndian) {
            copyLastBits_bigEndian(srcHelper, src, srcLastByteIndex, srcLastBitPosMod8, destHelper, dest, destLastByteIndex, destLastBitPosMod8);
        } else {
            copyLastBits_littleEndian(srcHelper, src, srcLastByteIndex, srcLastBitPosMod8, destHelper, dest, destLastByteIndex, destLastBitPosMod8);
        }
    }

    private static void copyLastBits_bigEndian(
            BaseBTHelper srcHelper,
            Object src,
            int srcLastByteIndex,
            int srcLastBitPosMod8,
            BaseBTHelper destHelper,
            Object dest,
            int destLastByteIndex,
            int destLastBitPosMod8) {
        final byte srcBitsAtDestPos;
        if (srcLastBitPosMod8 - destLastBitPosMod8 < 0) {
            final int srcFirstBitPosInByte = 8 - (destLastBitPosMod8 - srcLastBitPosMod8);
            srcBitsAtDestPos = (byte)get_2_to_16_bits_over_2_bytes_bigEndian(srcHelper, src, srcLastByteIndex-1, srcFirstBitPosInByte, 8);
        } else {
            srcBitsAtDestPos = (byte)(srcHelper.get8Bits(src,srcLastByteIndex)<<(srcLastBitPosMod8 - destLastBitPosMod8));
        }
        final byte destBitsMask1 = byteMask1FromIndexTo7(7-destLastBitPosMod8);
        destHelper.put8Bits(dest,destLastByteIndex,(byte)((destHelper.get8Bits(dest,destLastByteIndex) & (~destBitsMask1)) | (srcBitsAtDestPos & destBitsMask1)));
    }

    private static void copyLastBits_littleEndian(
            BaseBTHelper srcHelper,
            Object src,
            int srcLastByteIndex,
            int srcLastBitPosMod8,
            BaseBTHelper destHelper,
            Object dest,
            int destLastByteIndex,
            int destLastBitPosMod8) {
        final byte srcBitsAtDestPos;
        if (srcLastBitPosMod8 - destLastBitPosMod8 < 0) {
            final int srcFirstBitPosInByte = 8 - (destLastBitPosMod8 - srcLastBitPosMod8);
            srcBitsAtDestPos = (byte)get_2_to_16_bits_over_2_bytes_littleEndian(srcHelper, src, srcLastByteIndex-1, srcFirstBitPosInByte, 8);
        } else {
            srcBitsAtDestPos = (byte)(srcHelper.get8Bits(src,srcLastByteIndex)>>(srcLastBitPosMod8 - destLastBitPosMod8));
        }
        final byte destBitsMask1 = byteMask1From0ToIndex(destLastBitPosMod8);
        destHelper.put8Bits(dest,destLastByteIndex,(byte)((destHelper.get8Bits(dest,destLastByteIndex) & (~destBitsMask1)) | (srcBitsAtDestPos & destBitsMask1)));
    }
}
