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
import java.util.ArrayList;
import java.util.Random;

import net.jodk.lang.NumbersUtils;

import junit.framework.TestCase;

/**
 * Abstract class to test operations on buffers.
 */
public abstract class AbstractBufferOpTezt extends TestCase {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final int NBR_OF_CALLS = 10 * 1000;

    private static final int NBR_OF_TAB_COPY_LIMITS = 50;

    protected final Random random = new Random(123456789L);

    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE CLASSES
    //--------------------------------------------------------------------------

    interface InterfaceTab {
        public boolean isReadOnly();
        public boolean isDirect();
        public boolean hasArray();
        /**
         * @return True if tab's treatments can access the backing array, if any,
         *         even if tab is read-only, false otherwise.
         */
        public boolean hasArrayWritableOrNot();
        /**
         * Must preserver order.
         * @return Tab as read-only tab, or null if no read-only versions can exist.
         */
        public InterfaceTab asReadOnly();
        public int limit();
        /**
         * @param order Can be null (eventual null check to be done on actual operation call).
         */
        public void order(ByteOrder order);
        /**
         * Also sets the order in the backing buffer.
         */
        public void orderAndSetInBufferIfAny(ByteOrder order);
        public ByteOrder order();
        public void put(int index, byte value);
        public byte get(int index);
    }

    /*
     * 
     */

    interface InterfacePadByteAtBitOperation<T extends InterfaceTab> {
        public int padByteAtBit(T tab, long bitPos);
    }

    /*
     * 
     */

    interface InterfacePutBitAtBitOperation<T extends InterfaceTab> {
        public void putBitAtBit(T tab, long bitPos, boolean value);
    }

    interface InterfaceGetBitAtBitOperation<T extends InterfaceTab> {
        public boolean getBitAtBit(T tab, long bitPos);
    }

    /*
     * 
     */

    /**
     * If isSizeBitwise() returns true, treatment must throw an
     * exception if specified bit size is not in range, else
     * only bit sizes in range and multiple of 8 are provided
     * (this supposes treatments using bytewise sizes don't ask
     * the size to the user, but have it hard-coded).
     * In both cases, an exception must be thrown if the specified
     * value does not fit in the specified bit size is.
     */
    interface InterfacePutOrGetLongAtBitOperation<T extends InterfaceTab> {
        /**
         * @return True if signed, i.e. if MSBit is sign bit, false otherwise.
         */
        public boolean isSigned();
        /**
         * If this method returns false, treatment will only be used
         * with byte-aligned first bit positions: no exception is expected
         * otherwise.
         * 
         * @return True if first bit position must start a byte, false otherwise.
         */
        public boolean isPosBitwise();
        /**
         * If this method returns false, treatment will only be used
         * with bit sizes that are multiple of 8: no exception is expected
         * otherwise.
         * 
         * @return True if bit size must be a multiple of 8, false otherwise.
         */
        public boolean isSizeBitwise();
        /**
         * If isSizeBitwise() returns true, this value must be >= 1,
         * else it must be >= 8 and a multiple of 8.
         * 
         * @return Min bit size.
         */
        public int getMinBitSize();
        /**
         * If isSizeBitwise() returns true, this value must be <= 64
         * if signed, and <= 63 if unsigned, else it must be >= 8
         * and a multiple of 8.
         * 
         * @return Max bit size.
         */
        public int getMaxBitSize();
    }

    interface InterfacePutLongAtBitOperation<T extends InterfaceTab> extends InterfacePutOrGetLongAtBitOperation<T> {
        public void putLongAtBit(T tab, long firstBitPos, long value, int bitSize);
    }

    interface InterfaceGetLongAtBitOperation<T extends InterfaceTab> extends InterfacePutOrGetLongAtBitOperation<T> {
        public long getLongAtBit(T tab, long firstBitPos, int bitSize);
    }

    /*
     * 
     */

    interface InterfaceCopyBytesOperation<T extends InterfaceTab> {
        public void copyBytes(T src, int srcFirstByteIndex, T dst, int dstFirstByteIndex, int byteSize);
    }

    interface InterfaceCopyBitsOperation<T extends InterfaceTab> {
        public void copyBits(T src, long srcFirstBitPos, T dst, long dstFirstBitPos, long bitSize);
    }

    /*
     * 
     */

    interface InterfaceToStringOperation<T extends InterfaceTab> {
        public String toString(T tab, int firstByteIndex, int byteSize, int radix);
    }

    interface InterfaceToStringBitsOperation<T extends InterfaceTab> {
        public String toStringBits(T tab, long firstBitPos, long bitSize, boolean bigEndian);
    }

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public AbstractBufferOpTezt() {
    }

    /*
     * 
     */

    public void test_padByteAtBitOperation(InterfacePadByteAtBitOperation op) {
        final int limit = 12;
        for (InterfaceTab tab : newTabs(limit)) {

            /*
             * read-only
             */

            if (tab.isReadOnly()) {
                // ReadOnlyBufferException whatever the arguments.
                tab.order(null);
                try {
                    op.padByteAtBit(tab, -1L);
                    assertFalse(true);
                } catch (ReadOnlyBufferException e) {
                    // ok
                }
                continue;
            }

            /*
             * 
             */

            final long hugeBitPos = 2*(limit*8L);
            for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN}) {
                tab.order(order);
                for (long bitPos=-hugeBitPos;bitPos<=hugeBitPos;bitPos++) {
                    if ((bitPos < 0) || (bitPos > limit*8L)) {
                        try {
                            op.padByteAtBit(tab,bitPos);
                            assertTrue(false);
                        } catch (IndexOutOfBoundsException e) {
                            // ok
                        }
                    }
                }
            }

            for (int i=0;i<NBR_OF_CALLS;i++) {
                fillTabRandom(tab);
                final ByteOrder order = randomOrder();
                tab.order(order);
                final int index = random.nextInt(limit);
                final int bitPosInByte = random.nextInt(8);
                final long bitPos = index * 8L + bitPosInByte;
                final int expected = (bitPosInByte == 0) ? 0 : 8-bitPosInByte;
                assertEquals(expected,op.padByteAtBit(tab,bitPos));
                final int padBitSize = (bitPosInByte == 0) ? 0 : 8-bitPosInByte;
                if (order == ByteOrder.BIG_ENDIAN) {
                    assertEquals(0, tab.get(index)&(0xFF>>>(8-padBitSize)));
                } else {
                    assertEquals(0, tab.get(index)&((0xFF<<(8-padBitSize))&0xFF));
                }
            }
        }
    }

    /*
     * 
     */

    public void test_putBitAtBitOperation(InterfacePutBitAtBitOperation op) {
        final int limit = 12;
        for (InterfaceTab tab : this.newTabs(limit)) {

            /*
             * read-only
             */

            if (tab.isReadOnly()) {
                // ReadOnlyBufferException whatever the arguments.
                tab.order(null);
                try {
                    op.putBitAtBit(tab, -1L, false);
                    assertFalse(true);
                } catch (ReadOnlyBufferException e) {
                    // ok
                }
                continue;
            }

            /*
             * null order
             */

            tab.order(null);
            try {
                op.putBitAtBit(tab, 0L, false);
                assertFalse(true);
            } catch (NullPointerException e) {
                // ok
            }

            /*
             * bad bit pos
             */

            tab.order(ByteOrder.BIG_ENDIAN);
            try {
                op.putBitAtBit(tab, -1L, false);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.putBitAtBit(tab, limit * 8L, false);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * random
             */

            for (int i=0;i<NBR_OF_CALLS;i++) {
                final ByteOrder order = randomOrder();
                tab.order(order);

                final boolean value = random.nextBoolean();
                final long bitPos = random.nextInt(tab.limit() * 8);

                final String tabBitsBefore = toBits(tab,order);
                final String valueBits = value ? "1" : "0";
                final String expectedBits = expectedBits(valueBits, 0, tabBitsBefore, NumbersUtils.asInt(bitPos), 1);

                op.putBitAtBit(tab, bitPos, value);

                final String tabBitsAfter = toBits(tab,order);
                final boolean ok = tabBitsAfter.equals(expectedBits);
                if (!ok) {
                    System.out.println("order     = "+order);
                    System.out.println("bitPos    = "+bitPos);
                    System.out.println("value     = "+value);
                    System.out.println("valueBits = "+valueBits);
                    System.out.println("tabBitsBefore = "+tabBitsBefore);
                    System.out.println("expectedBits  = "+expectedBits);
                    System.out.println("tabBitsAfter  = "+tabBitsAfter);
                    System.out.flush();
                }
                assertTrue(ok);
            }
        }
    }

    public void test_getBitAtBitOperation(InterfaceGetBitAtBitOperation op) {
        final int limit = 12;
        for (InterfaceTab tab : this.newTabs(limit)) {
            /*
             * null order
             */

            tab.order(null);
            try {
                op.getBitAtBit(tab,0L);
                assertFalse(true);
            } catch (NullPointerException e) {
                // ok
            }

            /*
             * bad bit pos
             */

            tab.order(ByteOrder.BIG_ENDIAN);
            try {
                op.getBitAtBit(tab,-1L);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.getBitAtBit(tab,limit * 8L);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * random
             */

            for (int i=0;i<NBR_OF_CALLS;i++) {
                final ByteOrder order = randomOrder();
                tab.order(order);

                if (!tab.isReadOnly()) {
                    fillTabRandom(tab);
                }

                final long bitPos = random.nextInt(limit * 8);

                final String tabBitsBefore = toBits(tab, order);

                final boolean expectedValue = (tabBitsBefore.charAt(NumbersUtils.asInt(bitPos)) == '1');

                final boolean value = op.getBitAtBit(tab,bitPos);

                final String tabBitsAfter = toBits(tab, order);
                final boolean ok1 = (value == expectedValue);
                final boolean ok2 = tabBitsAfter.equals(tabBitsBefore);
                if (!(ok1 && ok2)) {
                    System.out.println("order  = "+order);
                    System.out.println("bitPos = "+bitPos);
                    System.out.println("expectedValue = "+expectedValue);
                    System.out.println("value         = "+value);
                    System.out.println("tabBitsBefore = "+tabBitsBefore);
                    System.out.println("tabBitsAfter  = "+tabBitsAfter);
                    System.out.flush();
                }
                assertTrue(ok1);
                assertTrue(ok2);
            }
        }
    }

    /*
     * 
     */

    protected void test_putLongAtBitOperation(final InterfacePutLongAtBitOperation op) {
        final int limit = 12;
        checkOpConstraints(limit, op);

        final boolean signed = op.isSigned();
        final boolean posBitwise = op.isPosBitwise();
        final boolean sizeBitwise = op.isSizeBitwise();
        final int minBitSize = op.getMinBitSize();
        final int maxBitSize = op.getMaxBitSize();

        for (InterfaceTab tab : this.newTabs(limit)) {
            long firstBitPos;
            long value;
            int bitSize;

            /*
             * read-only
             */

            if (tab.isReadOnly()) {
                // ReadOnlyBufferException whatever the arguments.
                tab.order(null);
                try {
                    op.putLongAtBit(tab, firstBitPos = -1L, value = Long.MIN_VALUE, bitSize = -1);
                    assertFalse(true);
                } catch (ReadOnlyBufferException e) {
                    // ok
                }
                continue;
            }

            /*
             * null order
             */

            tab.order(null);
            try {
                // min bit size if bytewise, else some assertions might complain
                op.putLongAtBit(tab, firstBitPos = 0L, value = 0L, bitSize = sizeBitwise ? 0 : minBitSize);
                assertFalse(true);
            } catch (NullPointerException e) {
                // ok
            }

            /*
             * 
             */

            tab.order(ByteOrder.BIG_ENDIAN);

            /*
             * bad value
             */

            if (!signed) {
                try {
                    op.putLongAtBit(tab, firstBitPos = 0L, value = -1L, bitSize = minBitSize);
                    assertFalse(true);
                } catch (IllegalArgumentException e) {
                    // ok
                }
            }

            if (sizeBitwise) {
                /*
                 * bad bit size (in itself)
                 */

                try {
                    op.putLongAtBit(tab, firstBitPos = 0L, value = 0L, bitSize = 0);
                    assertFalse(true);
                } catch (IllegalArgumentException e) {
                    // ok
                }
                try {
                    op.putLongAtBit(tab, firstBitPos = 0L, value = 0L, bitSize = minBitSize-1);
                    assertFalse(true);
                } catch (IllegalArgumentException e) {
                    // ok
                }
                try {
                    op.putLongAtBit(tab, firstBitPos = 0L, value = 0L, bitSize = maxBitSize+1);
                    assertFalse(true);
                } catch (IllegalArgumentException e) {
                    // ok
                }

                /*
                 * bad bit size (for the value)
                 */

                if (signed) {
                    if (minBitSize != 64) {
                        final long tooSmall = NumbersUtils.minSignedLongForBitSize(minBitSize) - 1L;
                        try {
                            op.putLongAtBit(tab, firstBitPos = 0L, value = tooSmall, bitSize = minBitSize);
                            assertFalse(true);
                        } catch (IllegalArgumentException e) {
                            // ok
                        }
                        final long tooLarge = NumbersUtils.maxSignedLongForBitSize(minBitSize) + 1L;
                        try {
                            op.putLongAtBit(tab, firstBitPos = 0L, value = tooLarge, bitSize = minBitSize);
                            assertFalse(true);
                        } catch (IllegalArgumentException e) {
                            // ok
                        }
                    }
                } else {
                    if (minBitSize != 63) {
                        final long tooLarge = NumbersUtils.maxUnsignedLongForBitSize(minBitSize) + 1L;
                        try {
                            op.putLongAtBit(tab, firstBitPos = 0L, value = tooLarge, bitSize = minBitSize);
                            assertFalse(true);
                        } catch (IllegalArgumentException e) {
                            // ok
                        }
                    }
                }
            } else {
                // Supposing user can't give (a bad) byte size.
            }

            /*
             * bad bit range
             */

            try {
                op.putLongAtBit(tab, firstBitPos = (posBitwise ? -1L : -8L), value = 0L, bitSize = minBitSize);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.putLongAtBit(tab, firstBitPos = limit*8L, value = 0L, bitSize = minBitSize);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.putLongAtBit(tab, firstBitPos = limit*8L - maxBitSize + (posBitwise ? 1L : 8L), value = 0L, bitSize = maxBitSize);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * random
             */

            // To get rid of sign bit.
            final int unsignedShift = signed ? 0 : 1;
            // bit size factor
            final int bsf = sizeBitwise ? 1 : 8;

            for (int i=0;i<NBR_OF_CALLS;i++) {
                final ByteOrder order = randomOrder();
                tab.order(order);

                bitSize = (minBitSize/bsf + random.nextInt(maxBitSize/bsf-minBitSize/bsf+1)) * bsf;
                value = ((random.nextLong()>>>unsignedShift)>>(64-unsignedShift-bitSize));
                firstBitPos = (random.nextInt() & (0x1F/bsf)) * bsf;

                final String tabBitsBefore = toBits(tab,order);
                final String valueBits = toBits(value, bitSize, order);
                final String expectedBits = expectedBits(valueBits, 0, tabBitsBefore, NumbersUtils.asInt(firstBitPos), bitSize);

                op.putLongAtBit(tab, firstBitPos, value, bitSize);

                final String tabBitsAfter = toBits(tab,order);
                final boolean ok = tabBitsAfter.equals(expectedBits);
                if (!ok) {
                    System.out.println("order       = "+order);
                    System.out.println("firstBitPos = "+firstBitPos);
                    System.out.println("bitSize     = "+bitSize);
                    System.out.println("value       = "+value);
                    System.out.println("valueBits   = "+valueBits);
                    System.out.println("tabBitsBefore = "+tabBitsBefore);
                    System.out.println("expectedBits  = "+expectedBits);
                    System.out.println("tabBitsAfter  = "+tabBitsAfter);
                    System.out.flush();
                }
                assertTrue(ok);
            }
        }
    }

    /*
     * 
     */

    protected void test_getLongAtBitOperation(InterfaceGetLongAtBitOperation op) {
        final int limit = 12;
        checkOpConstraints(limit, op);

        final boolean signed = op.isSigned();
        final boolean posBitwise = op.isPosBitwise();
        final boolean sizeBitwise = op.isSizeBitwise();
        final int minBitSize = op.getMinBitSize();
        final int maxBitSize = op.getMaxBitSize();

        for (InterfaceTab tab : this.newTabs(limit)) {
            long firstBitPos;
            int bitSize;

            /*
             * null order
             */

            tab.order(null);
            try {
                // min bit size if bytewise, else some assertions might complain
                op.getLongAtBit(tab, firstBitPos = 0L, bitSize = sizeBitwise ? 0 : minBitSize);
                assertFalse(true);
            } catch (NullPointerException e) {
                // ok
            }

            /*
             * 
             */

            tab.order(ByteOrder.BIG_ENDIAN);

            if (sizeBitwise) {
                /*
                 * bad bit size
                 */

                try {
                    op.getLongAtBit(tab, firstBitPos = 0L, bitSize = 0);
                    assertFalse(true);
                } catch (IllegalArgumentException e) {
                    // ok
                }
                try {
                    op.getLongAtBit(tab, firstBitPos = 0L, bitSize = minBitSize-1);
                    assertFalse(true);
                } catch (IllegalArgumentException e) {
                    // ok
                }
                try {
                    op.getLongAtBit(tab, firstBitPos = 0L, bitSize = maxBitSize+1);
                    assertFalse(true);
                } catch (IllegalArgumentException e) {
                    // ok
                }
            } else {
                // Supposing user can't give (a bad) byte size.
            }

            /*
             * bad bit range
             */

            try {
                op.getLongAtBit(tab, firstBitPos = (posBitwise ? -1L : -8L), bitSize = minBitSize);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.getLongAtBit(tab, firstBitPos = limit*8L, bitSize = minBitSize);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.getLongAtBit(tab, firstBitPos = limit*8L - maxBitSize + (posBitwise ? 1L : 8L), bitSize = maxBitSize);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * random
             */

            // bit size factor
            final int bsf = sizeBitwise ? 1 : 8;

            for (int i=0;i<NBR_OF_CALLS;i++) {
                final ByteOrder order = randomOrder();
                tab.order(order);

                if (!tab.isReadOnly()) {
                    fillTabRandom(tab);
                }

                bitSize = (minBitSize/bsf + random.nextInt(maxBitSize/bsf-minBitSize/bsf+1)) * bsf;
                firstBitPos = (random.nextInt() & 0x1F/bsf) * bsf;

                final String tabBitsBefore = toBits(tab, order);

                final String expectedReadBits = tabBitsBefore.substring(NumbersUtils.asInt(firstBitPos), NumbersUtils.asInt(firstBitPos+bitSize));
                final long expectedValue = fromBits(expectedReadBits, signed, order);

                final long value = op.getLongAtBit(tab, firstBitPos, bitSize);
                final String readBits = toBits(value, bitSize, order);

                final String tabBitsAfter = toBits(tab, order);
                final boolean ok1 = (value == expectedValue);
                final boolean ok2 = tabBitsAfter.equals(tabBitsBefore);
                if (!(ok1 && ok2)) {
                    System.out.println("order       = "+order);
                    System.out.println("firstBitPos = "+firstBitPos);
                    System.out.println("bitSize     = "+bitSize);
                    System.out.println("expectedValue    = "+expectedValue);
                    System.out.println("value            = "+value);
                    System.out.println("expectedReadBits = "+expectedReadBits);
                    System.out.println("readBits         = "+readBits);
                    System.out.println("tabBitsBefore = "+tabBitsBefore);
                    System.out.println("tabBitsAfter  = "+tabBitsAfter);
                    System.out.flush();
                }
                assertTrue(ok1);
                assertTrue(ok2);
            }
        }
    }

    /*
     * 
     */

    public void test_copyBytesOperation(InterfaceCopyBytesOperation op) {

        /*
         * nulls
         */

        for (InterfaceTab src : newTabs(0,newTabs(1,null))) {
            for (InterfaceTab dst : newTabs(0,newTabs(1,null))) {
                for (ByteOrder srcOrder : new ByteOrder[]{null,ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN}) {
                    for (ByteOrder dstOrder : new ByteOrder[]{null,ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN}) {
                        if (src != null) {
                            src.order(srcOrder);
                        }
                        if (dst != null) {
                            dst.order(dstOrder);
                        }
                        if ((src == null) || (dst == null) || (srcOrder == null) || (dstOrder == null)) {
                            try {
                                op.copyBytes(src, 0, dst, 0, 0);
                                assertTrue(false);
                            } catch (NullPointerException e) {
                                // ok
                            }
                            // Even with weird indexes and size.
                            // Exception for nulls has priority over
                            // exceptions for bit size and bit range.
                            try {
                                op.copyBytes(src, -1, dst, -1, -1);
                                assertTrue(false);
                            } catch (NullPointerException e) {
                                // ok
                            }
                        }
                    }
                }
            }
        }

        /*
         * bad bit size and bit range, and tab properties not allowing copy
         */

        for (int srcLength : new int[]{0,1,2}) {
            for (int dstLength : new int[]{0,1,2}) {
                for (InterfaceTab src : newTabs(srcLength)) {
                    for (InterfaceTab dst : newTabs(dstLength)) {
                        for (boolean srcTooLow : new boolean[]{false,true}) {
                            for (boolean dstTooLow : new boolean[]{false,true}) {
                                for (boolean byteSizeTooHigh : new boolean[]{false,true}) {
                                    final int srcFirstByteIndex = srcTooLow ? -1 : 0;
                                    final int dstFirstByteIndex = dstTooLow ? -1 : 0;

                                    // Making sure orders are identical.
                                    ByteOrder order = randomOrder();
                                    src.order(order);
                                    dst.order(order);

                                    final int byteSize = (byteSizeTooHigh ? 1 : 0)
                                            + Math.min(
                                                    srcLength - srcFirstByteIndex,
                                                    dstLength - dstFirstByteIndex);
                                    if ((!(dst.isReadOnly() && (byteSize > 0))) // Would throw ReadOnlyBufferException.
                                            && (srcTooLow || dstTooLow || byteSizeTooHigh)) {
                                        // Bad byte size, bad byte range.
                                        // Exception for bad byte size (on its own),
                                        // has priority over exception on byte range.
                                        try {
                                            op.copyBytes(src, srcFirstByteIndex, dst, dstFirstByteIndex, -1);
                                            assertTrue(false);
                                        } catch (IndexOutOfBoundsException e) {
                                            // ok
                                        }
                                        // Bad byte range.
                                        try {
                                            op.copyBytes(src, srcFirstByteIndex, dst, dstFirstByteIndex, byteSize);
                                            assertTrue(false);
                                        } catch (IndexOutOfBoundsException e) {
                                            // ok
                                        }
                                    } else {
                                        // Bad byte size.
                                        try {
                                            op.copyBytes(src, srcFirstByteIndex, dst, dstFirstByteIndex, -1);
                                            assertTrue(false);
                                        } catch (IndexOutOfBoundsException e) {
                                            // ok
                                        }
                                        // Good byte size and byte range.
                                        copyWithExceptionTest(op, src, srcFirstByteIndex, dst, dstFirstByteIndex, byteSize);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /*
         * different byte orders
         */

        for (InterfaceTab[] pair : newTabPairs(1,1)) {
            final InterfaceTab src = pair[0];
            final InterfaceTab dst = pair[1];
            if (dst.isReadOnly()) {
                continue;
            }
            if (src.order() != dst.order()) {
                try {
                    op.copyBytes(src, 0, dst, 0, 1);
                    assertTrue(false);
                } catch (IllegalArgumentException e) {
                    // ok
                }
            }
        }

        /*
         * random
         */

        final ArrayList<ByteOrder> orders = new ArrayList<ByteOrder>();
        orders.add(ByteOrder.BIG_ENDIAN);
        orders.add(ByteOrder.LITTLE_ENDIAN);

        final boolean smallLengths = false;

        final int lengthLo = smallLengths ? 2 : 10;
        // Enough for 3 longs, in case implementation uses longs.
        final int lengthHi = smallLengths ? 8 : 64 * 3;

        for (int k=0;k<NBR_OF_TAB_COPY_LIMITS;k++) {
            // Sometimes small, sometimes large.
            final int srcLimit = 1 + random.nextInt(lengthLo) + (random.nextBoolean() ? random.nextInt(lengthHi) : 0);
            final int dstLimit = 1 + random.nextInt(lengthLo) + (random.nextBoolean() ? random.nextInt(lengthHi) : 0);
            
            for (InterfaceTab[] pair : newTabPairs(srcLimit,dstLimit)) {
                final InterfaceTab src = pair[0];
                final InterfaceTab dst = pair[1];
                if (dst.isReadOnly()) {
                    // Tested above; would just slow things down.
                    continue;
                }
                if ((src.limit() == 0) || (dst.limit() == 0)) {
                    // Can't do much.
                    continue;
                }

                final boolean shared = (pair.length == 3);
                
                // Erasing risk: considering that copy is done by iterating in a direction such
                // as if src and dst are identical, data to copy is not erased with copied data.
                final boolean copyErasingRisk = shared && (!(src.hasArrayWritableOrNot() && dst.hasArrayWritableOrNot()));

                /*
                 * src
                 */
                final int srcByteLength = src.limit();
                final int srcFirstByteIndex = random.nextInt(srcByteLength);
                /*
                 * dst
                 */
                final int dstByteLength = dst.limit();
                final int dstFirstByteIndex = random.nextInt(dstByteLength);
                /*
                 * byte size
                 */
                final int maxByteSize = Math.min(srcByteLength - srcFirstByteIndex, dstByteLength - dstFirstByteIndex);
                final int byteSize = 1 + random.nextInt(maxByteSize);
                /*
                 * order
                 */
                final ByteOrder order = randomOrder();
                /*
                 * Need to set order before converting buffers to strings.
                 */
                src.order(order);
                dst.order(order);
                /*
                 * computing expected result
                 */
                final String srcBits = toBits(src, order);
                final String dstBits = toBits(dst, order);
                /*
                 * copy
                 */
                if (!copyWithExceptionTest(op, src, srcFirstByteIndex, dst, dstFirstByteIndex, byteSize)) {
                    continue;
                }
                /*
                 * checks
                 */
                if (!shared) {
                    // src must not have been modified
                    assertEquals(toBits(src, order), srcBits);
                }
                if (copyErasingRisk) {
                    // Copy might have had trouble, but that's in the spec.
                } else {
                    final String expectedBits = expectedBits(srcBits, srcFirstByteIndex * 8, dstBits, dstFirstByteIndex * 8, byteSize * 8);
                    final String resultBits = toBits(dst, order);
                    final boolean ok = resultBits.equals(expectedBits);
                    if (!ok) {
                        System.out.println("shared            = "+shared);
                        System.out.println("order             = "+order);
                        System.out.println("srcFirstByteIndex = "+srcFirstByteIndex);
                        System.out.println("dstFirstByteIndex = "+dstFirstByteIndex);
                        System.out.println("byteSize          = "+byteSize);
                        System.out.println("srcBits      = "+srcBits);
                        System.out.println("dstBits      = "+dstBits);
                        System.out.println("expectedBits = "+expectedBits);
                        System.out.println("resultBits   = "+resultBits);
                        System.out.flush();
                    }
                    assertTrue(ok);
                }
            }
        }
    }

    public void test_copyBitsOperation(InterfaceCopyBitsOperation op) {

        /*
         * nulls
         */

        for (InterfaceTab src : newTabs(0,newTabs(1,null))) {
            for (InterfaceTab dst : newTabs(0,newTabs(1,null))) {
                for (ByteOrder srcOrder : new ByteOrder[]{null,ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN}) {
                    for (ByteOrder dstOrder : new ByteOrder[]{null,ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN}) {
                        if (src != null) {
                            src.order(srcOrder);
                        }
                        if (dst != null) {
                            dst.order(dstOrder);
                        }
                        if ((src == null) || (dst == null) || (srcOrder == null) || (dstOrder == null)) {
                            try {
                                op.copyBits(src, 0L, dst, 0L, 0L);
                                assertTrue(false);
                            } catch (NullPointerException e) {
                                // ok
                            }
                            // Even with weird indexes and size.
                            // Exception for nulls has priority over
                            // exceptions for bit size and bit range.
                            try {
                                op.copyBits(src, -1L, dst, -1L, -1L);
                                assertTrue(false);
                            } catch (NullPointerException e) {
                                // ok
                            }
                        }
                    }
                }
            }
        }

        /*
         * bad bit size and bit range, and tab properties not allowing copy
         */

        for (int srcLength : new int[]{0,1,2}) {
            for (int dstLength : new int[]{0,1,2}) {
                for (InterfaceTab src : newTabs(srcLength)) {
                    for (InterfaceTab dst : newTabs(dstLength)) {
                        for (boolean srcTooLow : new boolean[]{false,true}) {
                            for (boolean dstTooLow : new boolean[]{false,true}) {
                                for (boolean bitSizeTooHigh : new boolean[]{false,true}) {
                                    final long srcFirstBitPos = srcTooLow ? -1 : 0;
                                    final long dstFirstBitPos = dstTooLow ? -1 : 0;

                                    // Making sure orders are identical.
                                    ByteOrder order = randomOrder();
                                    src.order(order);
                                    dst.order(order);

                                    final long bitSize = (bitSizeTooHigh ? 1 : 0)
                                            + Math.min(
                                                    srcLength * 8L - srcFirstBitPos,
                                                    dstLength * 8L - dstFirstBitPos);
                                    if ((!(dst.isReadOnly() && (bitSize > 0))) // Would throw ReadOnlyBufferException.
                                            && (srcTooLow || dstTooLow || bitSizeTooHigh)) {
                                        // Bad bit size, bad bit range.
                                        // Exception for bad bit size (on its own),
                                        // has priority over exception on bit range.
                                        try {
                                            op.copyBits(src, srcFirstBitPos, dst, dstFirstBitPos, -1L);
                                            assertTrue(false);
                                        } catch (IndexOutOfBoundsException e) {
                                            // ok
                                        }
                                        // Bad bit range.
                                        try {
                                            op.copyBits(src, srcFirstBitPos, dst, dstFirstBitPos, bitSize);
                                            assertTrue(false);
                                        } catch (IndexOutOfBoundsException e) {
                                            // ok
                                        }
                                    } else {
                                        // Bad bit size.
                                        try {
                                            op.copyBits(src, srcFirstBitPos, dst, dstFirstBitPos, -1L);
                                            assertTrue(false);
                                        } catch (IndexOutOfBoundsException e) {
                                            // ok
                                        }
                                        // Good bit size and bit range.
                                        copyWithExceptionTest(op, src, srcFirstBitPos, dst, dstFirstBitPos, bitSize);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /*
         * different byte orders
         */

        for (InterfaceTab[] pair : newTabPairs(1,1)) {
            final InterfaceTab src = pair[0];
            final InterfaceTab dst = pair[1];
            if (dst.isReadOnly()) {
                continue;
            }
            if (src.order() != dst.order()) {
                try {
                    op.copyBits(src, 0L, dst, 0L, 1L);
                    assertTrue(false);
                } catch (IllegalArgumentException e) {
                    // ok
                }
            }
        }

        /*
         * random
         */

        final ArrayList<ByteOrder> orders = new ArrayList<ByteOrder>();
        orders.add(ByteOrder.BIG_ENDIAN);
        orders.add(ByteOrder.LITTLE_ENDIAN);

        final boolean smallLengths = false;

        final int lengthLo = smallLengths ? 2 : 10;
        // Enough for 3 longs, in case implementation uses longs.
        final int lengthHi = smallLengths ? 8 : 64 * 3;

        for (int k=0;k<NBR_OF_TAB_COPY_LIMITS;k++) {
            // Sometimes small, sometimes large.
            final int srcLimit = 1 + random.nextInt(lengthLo) + (random.nextBoolean() ? random.nextInt(lengthHi) : 0);
            final int dstLimit = 1 + random.nextInt(lengthLo) + (random.nextBoolean() ? random.nextInt(lengthHi) : 0);
            
            for (InterfaceTab[] pair : newTabPairs(srcLimit,dstLimit)) {
                final InterfaceTab src = pair[0];
                final InterfaceTab dst = pair[1];
                if (dst.isReadOnly()) {
                    // Tested above; would just slow things down.
                    continue;
                }
                if ((src.limit() == 0) || (dst.limit() == 0)) {
                    // Can't do much.
                    continue;
                }

                final boolean shared = (pair.length == 3);
                
                // Erasing risk: considering that copy is done by iterating in a direction such
                // as if src and dst are identical, data to copy is not erased with copied data.
                final boolean copyErasingRisk = shared && (!(src.hasArrayWritableOrNot() && dst.hasArrayWritableOrNot()));

                /*
                 * src
                 */
                final int srcBitLength = src.limit() * 8;
                final int srcFirstBitPos = random.nextInt(srcBitLength);
                /*
                 * dst
                 */
                final int dstBitLength = dst.limit() * 8;
                final int dstFirstBitPos = random.nextInt(dstBitLength);
                /*
                 * bit size
                 */
                final int maxBitSize = Math.min(srcBitLength - srcFirstBitPos, dstBitLength - dstFirstBitPos);
                final int bitSize = 1 + random.nextInt(maxBitSize);
                /*
                 * order
                 */
                final ByteOrder order = randomOrder();
                /*
                 * Need to set order before converting buffers to strings.
                 */
                src.order(order);
                dst.order(order);
                /*
                 * computing expected result
                 */
                final String srcBits = toBits(src, order);
                final String dstBits = toBits(dst, order);
                /*
                 * copy
                 */
                if (!copyWithExceptionTest(op, src, srcFirstBitPos, dst, dstFirstBitPos, bitSize)) {
                    continue;
                }
                /*
                 * checks
                 */
                if (!shared) {
                    // src must not have been modified
                    assertEquals(toBits(src, order), srcBits);
                }
                if (copyErasingRisk) {
                    // Copy might have had trouble, but that's in the spec.
                } else {
                    final String expectedBits = expectedBits(srcBits, srcFirstBitPos, dstBits, dstFirstBitPos, bitSize);
                    final String resultBits = toBits(dst, order);
                    final boolean ok = resultBits.equals(expectedBits);
                    if (!ok) {
                        System.out.println("shared         = "+shared);
                        System.out.println("order          = "+order);
                        System.out.println("srcFirstBitPos = "+srcFirstBitPos);
                        System.out.println("dstFirstBitPos = "+dstFirstBitPos);
                        System.out.println("bitSize        = "+bitSize);
                        System.out.println("srcBits      = "+srcBits);
                        System.out.println("dstBits      = "+dstBits);
                        System.out.println("expectedBits = "+expectedBits);
                        System.out.println("resultBits   = "+resultBits);
                        System.out.flush();
                    }
                    assertTrue(ok);
                }
            }
        }
    }

    public void test_toStringOperation(InterfaceToStringOperation op) {
        final int limit = 4;
        for (InterfaceTab tab : newTabs(limit)) {
            int radix;
            int firstByteIndex;
            int byteSize;

            /*
             * bad number of bytes
             */

            try {
                op.toString(tab, firstByteIndex = 0, byteSize = -1, radix = 10);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * bad byte range
             */

            try {
                op.toString(tab, firstByteIndex = -1, byteSize = 0, radix = 10);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.toString(tab, firstByteIndex = 0, byteSize = limit+1, radix = 10);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.toString(tab, firstByteIndex = limit, byteSize = 1, radix = 10);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.toString(tab, firstByteIndex = limit+1, byteSize = 0, radix = 10);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * random
             */

            for (int i=0;i<NBR_OF_CALLS;i++) {
                // Whatever the order, toString is done in big endian,
                // for some people might have trouble reading numbers backward
                // or even just think about it.
                final ByteOrder order = randomOrder();
                tab.order(order);

                firstByteIndex = random.nextInt(limit);
                // Allowing 0 for number of bytes.
                byteSize = random.nextInt(limit - firstByteIndex + 1);
                radix = Character.MIN_RADIX + random.nextInt(Character.MAX_RADIX-Character.MIN_RADIX);

                final int lastByteIndex = firstByteIndex + byteSize - 1;
                final int maxByteStrLength = Integer.toString(255,radix).length();
                String expected = "(bytes "+firstByteIndex+".."+lastByteIndex+"(msb..lsb))[";
                for (int j=firstByteIndex;j<=lastByteIndex;j++) {
                    // Mask because we read bits as unsigned.
                    String byteStr = Integer.toString((tab.get(j)&0xFF),radix).toUpperCase();
                    final int padSize = maxByteStrLength-byteStr.length();
                    for (int k=0;k<padSize;k++) {
                        byteStr = "0"+byteStr;
                    }
                    expected += byteStr;
                    if (j < lastByteIndex) {
                        expected += ",";
                    }
                }
                expected += "]";
                assertEquals(expected,op.toString(tab, firstByteIndex, byteSize, radix));
            }
        }
    }

    public void test_toStringBitsOperation(InterfaceToStringBitsOperation op) {
        final int limit = 4;
        for (InterfaceTab tab : newTabs(limit)) {
            long firstBitPos;
            long bitSize;
            boolean bigEndian;

            /*
             * bad number of bits
             */

            try {
                op.toStringBits(tab, firstBitPos = 0, bitSize = -1, bigEndian = true);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * bad bit range
             */

            try {
                op.toStringBits(tab, firstBitPos = -1, bitSize = 0, bigEndian = true);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.toStringBits(tab, firstBitPos = 0, bitSize = limit*8+1, bigEndian = true);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.toStringBits(tab, firstBitPos = limit*8, bitSize = 1, bigEndian = true);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                op.toStringBits(tab, firstBitPos = limit*8+1, bitSize = 0, bigEndian = true);
                assertFalse(true);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * random
             */

            for (int i=0;i<NBR_OF_CALLS;i++) {
                // Order not used: only bigEndian flag
                // (people might want to inspect tab's content
                // in multiple ways, whatever current tab's order is).
                final ByteOrder order = randomOrder();
                tab.order(order);

                firstBitPos = random.nextInt(limit*8);
                // Allowing 0 for number of bits.
                bitSize = random.nextInt((limit*8) - (int)firstBitPos + 1);
                bigEndian = random.nextBoolean();

                final long lastBitPos = firstBitPos + bitSize - 1;
                String expected = "(bits pos "+firstBitPos+".."+lastBitPos;
                if (bigEndian) {
                    expected += "(msb..lsb))[";
                } else {
                    expected += "(lsb..msb))[";
                }
                if (firstBitPos <= lastBitPos) {
                    final int firstByteIndex = ByteTabUtils.computeByteIndex(firstBitPos);
                    final int lastByteIndex = ByteTabUtils.computeByteIndex(lastBitPos);
                    for (int j=firstByteIndex;j<=lastByteIndex;j++) {
                        final int firstBitPosInByte = (j != firstByteIndex) ? 0 : (((int)firstBitPos)&7);
                        final int lastBitPosInByte = (j != lastByteIndex) ? 7 : (((int)lastBitPos)&7);
                        final String byteStr = NumbersUtils.toStringBits(tab.get(j), firstBitPosInByte, lastBitPosInByte+1, bigEndian, true);
                        expected += byteStr;
                        if (j < lastByteIndex) {
                            expected += ",";
                        }
                    }
                }
                expected += "]";
                assertEquals(expected,op.toStringBits(tab, firstBitPos, bitSize, bigEndian));
            }
        }
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------

    /**
     * @return Tabs, with random content.
     */
    protected abstract InterfaceTab[] newTabs(int limit, InterfaceTab... with);

    /**
     * @param targetRelativeOffset Target relative offset for the returned tab,
     *        i.e. relative to input tab's offset: if tab type or limit doesn't
     *        allow to use this offset, a lower or zero relative offset can be used instead.
     * @return A tab that shares the data of the specified tab,
     *         and is of the same king (direct or not, read-only or not,
     *         same order) but with possibly different offset (and limit).
     */
    protected abstract InterfaceTab newSharingTab(InterfaceTab tab, int targetRelativeOffset);

    protected ByteOrder randomOrder() {
        return random.nextBoolean() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
    }

    /**
     * This treatments returns all pairs of tabs provided by iterating twice
     * on newTabs(limit1) and then newTabs(limit2), plus additional pairs,
     * to test particular cases, such as tabs sharing data in some way
     * (with zero offset or not).
     * 
     * Some tabs can occur multiple times, in a same or different pairs.
     * 
     * If the pair array is of length 3, it indicates that both buffers
     * share data, else they don't share data.
     * 
     * Since tabs that share data are sometimes ensured to have different offsets,
     * and since opposite pairs are also used, limits can be different than specified
     * for pairs containing sharing buffers.
     * 
     * @return Pairs of tabs, to use for treatments taking two tabs as arguments.
     */
    protected final InterfaceTab[][] newTabPairs(int limit1, int limit2) {
        final ArrayList<InterfaceTab[]> tabPairs = new ArrayList<InterfaceTab[]>();
        for (InterfaceTab tab1 : newTabs(limit1)) {

            /*
             * Pairs of sharing tabs of same kind, but with identical or different offset,
             * and identical or different order.
             * 
             * Storing opposite pairs, to have opposite relative offsets.
             */
            
            for (int targetRelativeOffset=0;targetRelativeOffset<=1;targetRelativeOffset++) {
                final InterfaceTab tab2Same = this.newSharingTab(tab1, targetRelativeOffset);
                final InterfaceTab tab2Diff = this.newSharingTab(tab1, targetRelativeOffset);
                tab2Diff.orderAndSetInBufferIfAny(ByteOrderUtils.opposite(tab1.order()));
                
                tabPairs.add(new InterfaceTab[]{tab1,tab2Same,null});
                tabPairs.add(new InterfaceTab[]{tab2Same,tab1,null});
                tabPairs.add(new InterfaceTab[]{tab1,tab2Diff,null});
                tabPairs.add(new InterfaceTab[]{tab2Diff,tab1,null});
                
                /*
                 * If tab1 is writable, having other tab of same kind (offset,order),
                 * but read-only, to have (writable,read-only) pairs.
                 */
                
                if (!tab1.isReadOnly()) {
                    final InterfaceTab tab2SameR = this.newSharingTab(tab1, targetRelativeOffset).asReadOnly();
                    if (tab2SameR == null) {
                        // Read-only not supported.
                    } else {
                        final InterfaceTab tab2DiffR = this.newSharingTab(tab1, targetRelativeOffset).asReadOnly();
                        tab2DiffR.orderAndSetInBufferIfAny(ByteOrderUtils.opposite(tab1.order()));
                        
                        tabPairs.add(new InterfaceTab[]{tab1,tab2SameR,null});
                        tabPairs.add(new InterfaceTab[]{tab2SameR,tab1,null});
                        tabPairs.add(new InterfaceTab[]{tab1,tab2DiffR,null});
                        tabPairs.add(new InterfaceTab[]{tab2DiffR,tab1,null});
                    }
                }
            }
            
            /*
             * Regular pairs.
             */

            for (InterfaceTab tab2 : newTabs(limit2)) {
                // Opposite pair implicitly done, due to double-loop.
                tabPairs.add(new InterfaceTab[]{tab1,tab2});
            }
        }
        return tabPairs.toArray(new InterfaceTab[tabPairs.size()][]);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private static void checkOpConstraints(
            int limit,
            InterfacePutOrGetLongAtBitOperation op) {
        final boolean signed = op.isSigned();
        final boolean sizeBitwise = op.isSizeBitwise();
        final int minBitSize = op.getMinBitSize();
        final int maxBitSize = op.getMaxBitSize();

        // Constraints for the test.
        assertTrue(limit * 8L >= maxBitSize + 2);
        assertTrue(minBitSize >= 1);
        if (signed) {
            assertTrue(maxBitSize <= 64);
        } else {
            assertTrue(maxBitSize <= 63);
        }
        assertTrue(minBitSize <= maxBitSize);
        if (!sizeBitwise) {
            assertEquals(0, minBitSize&7);
            assertEquals(0, maxBitSize&7);
            assertTrue(maxBitSize >= 8);
            if (signed) {
                assertTrue(maxBitSize <= 64);
            } else {
                assertTrue(maxBitSize <= 56);
            }
        }
    }

    /**
     * Tests that exceptions are properly throw if copy can't be done (besides valid bit ranges).
     * 
     * @return True if could copy, false if a handled exception has been thrown.
     */
    private static boolean copyWithExceptionTest(
            InterfaceCopyBytesOperation op,
            InterfaceTab src,
            int srcFirstByteIndex,
            InterfaceTab dst,
            int dstFirstByteIndex,
            int byteSize) {
        if ((byteSize > 0)
                && dst.isReadOnly()) {
            try {
                op.copyBytes(src, srcFirstByteIndex, dst, dstFirstByteIndex, byteSize);
                assertTrue(false);
            } catch (ReadOnlyBufferException e) {
                // ok
            }
            return false;
        } else {
            op.copyBytes(src, srcFirstByteIndex, dst, dstFirstByteIndex, byteSize);
            return true;
        }
    }

    /**
     * Tests that exceptions are properly throw if copy can't be done (besides valid bit ranges).
     * 
     * @return True if could copy, false if a handled exception has been thrown.
     */
    private static boolean copyWithExceptionTest(
            InterfaceCopyBitsOperation op,
            InterfaceTab src,
            long srcFirstBitPos,
            InterfaceTab dst,
            long dstFirstBitPos,
            long bitSize) {
        if ((bitSize > 0)
                && dst.isReadOnly()) {
            try {
                op.copyBits(src, srcFirstBitPos, dst, dstFirstBitPos, bitSize);
                assertTrue(false);
            } catch (ReadOnlyBufferException e) {
                // ok
            }
            return false;
        } else {
            op.copyBits(src, srcFirstBitPos, dst, dstFirstBitPos, bitSize);
            return true;
        }
    }

    private void fillTabRandom(InterfaceTab tab) {
        for (int i=0;i<tab.limit();i++) {
            tab.put(i,(byte)random.nextInt());
        }
    }

    /*
     * toBits methods use tab's order, if a tab is specified
     */

    private String toBits(long value, int bitSize, ByteOrder order) {
        final boolean bigEndian = (order == ByteOrder.BIG_ENDIAN);
        final String allBits = NumbersUtils.toStringBits(value,0,64,bigEndian,false);
        if (bigEndian) {
            return allBits.substring(64-bitSize, allBits.length());
        } else {
            return allBits.substring(0, bitSize);
        }
    }

    private long fromBits(String bits, boolean signed, ByteOrder order) {
        if (bits.length() > 64) {
            throw new IllegalArgumentException();
        }
        if ((bits.length() == 64) && (!signed)) {
            throw new IllegalArgumentException();
        }

        /*
         * will use bits in big endian
         */

        final String bitsBigEndian;
        if (order == ByteOrder.BIG_ENDIAN) {
            bitsBigEndian = bits;
        } else {
            final StringBuilder sb = new StringBuilder();
            for (int i=0;i<bits.length();i++) {
                sb.append(bits.charAt((bits.length()-1)-i));
            }
            bitsBigEndian = sb.toString();
        }

        /*
         * 
         */

        final boolean negative = signed && (bitsBigEndian.charAt(0) == '1');
        // If negative, will put zeroes, else will put ones.
        long result = negative ? -1L : 0;
        final int maxI = bits.length()-1;
        // from MSBit to LSBit
        for (int i=0;i<=maxI;i++) {
            final long bit = (long)(bitsBigEndian.charAt(i) - '0');
            if (negative) {
                result &= ((bit<<(maxI-i)) | ~(1L<<(maxI-i)));
            } else {
                result |= (bit<<(maxI-i));
            }
        }

        return result;
    }

    private String toBits(InterfaceTab tab, ByteOrder order) {
        if (order == null) {
            throw new NullPointerException();
        }
        final StringBuilder sb = new StringBuilder(tab.limit() * 8);
        for (int i=0;i<tab.limit();i++) {
            final byte b = tab.get(i);
            for (int j=0;j<8;j++) {
                if (order == ByteOrder.BIG_ENDIAN) {
                    sb.append(((b>>(7-j))&1));
                } else {
                    sb.append(((b>>j)&1));
                }
            }
        }
        return sb.toString();
    }

    private String expectedBits(
            final String srcBits,
            int srcBitPos,
            final String dstBits,
            int dstBitPos,
            int bitSize) {
        final StringBuilder sb = new StringBuilder(dstBits.length());
        for (int dstI=0;dstI<dstBits.length();dstI++) {
            if ((dstI >= dstBitPos) && (dstI <= dstBitPos + bitSize - 1)) {
                final int srcI = srcBitPos + (dstI - dstBitPos);
                sb.append(srcBits.charAt(srcI));
            } else {
                sb.append(dstBits.charAt(dstI));
            }
        }
        return sb.toString();
    }
}
