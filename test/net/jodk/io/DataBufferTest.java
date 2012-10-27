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

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;

public class DataBufferTest extends AbstractBufferOpTezt {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    /**
     * Enough for a long and 2 bytes.
     */
    private static final int DEFAULT_LIMIT = 10;
    
    /**
     * Float.floatToRawIntBits(Float.intBitsToFloat(int))
     * and
     * Double.doubleToRawLongBits(Double.longBitsToDouble(long))
     * are not identity on some architectures (in case of NaN,
     * first bit of the mantissa is set to 1 by either method),
     * so we skip these cases.
     */
    private static final boolean AVOID_NAN_BUG = true;

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MyTab implements InterfaceTab {
        final DataBuffer buffer;
        /**
         * Stored aside from buffer, for set and null check
         * to be done on actual operation.
         */
        ByteOrder order;
        public MyTab(final DataBuffer buffer) {
            this.buffer = buffer;
            this.order = buffer.order();
        }
        @Override
        public boolean isReadOnly() {
            return this.buffer.isReadOnly();
        }
        @Override
        public boolean isDirect() {
            return this.buffer.isDirect();
        }
        @Override
        public boolean hasArray() {
            return this.buffer.hasArray();
        }
        @Override
        public boolean hasArrayWritableOrNot() {
            return this.buffer.hasArrayWritableOrNot();
        }
        @Override
        public InterfaceTab asReadOnly() {
            return new MyTab(this.buffer.asReadOnlyBuffer());
        }
        @Override
        public int limit() {
            return this.buffer.limit();
        }
        @Override
        public void order(ByteOrder order) {
            this.order = order;
        }
        @Override
        public void orderAndSetInBufferIfAny(ByteOrder order) {
            this.order = order;
            this.buffer.order(order);
        }
        @Override
        public ByteOrder order() {
            // Must be identical when queried.
            LangUtils.azzert(this.order == this.buffer.order());
            return this.order;
        }
        @Override
        public void put(int index, byte value) {
            this.buffer.putByteAt(index, value);
        }
        @Override
        public byte get(int index) {
            return this.buffer.getByteAt(index);
        }
        protected void myBufferOrder(ByteOrder order) {
            this.buffer.orderIgnoreBitPosition(order);
        }
        protected void myBufferBitPosition(long firstBitPos) {
            try {
                this.buffer.bitPosition(firstBitPos);
            } catch (IllegalArgumentException e) {
                throw new IndexOutOfBoundsException();
            }
        }
    }

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_allocateBA_int() {
        try {
            DataBuffer.allocateBA(-1);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
        
        /*
         * random
         */

        for (int capacity : new int[]{0,17}) {
            final ByteOrder expectedOrder = ByteOrder.BIG_ENDIAN;
            final int expectedCapacity = capacity;
            final int expectedLimit = expectedCapacity;

            DataBuffer db = DataBuffer.allocateBA(capacity);

            assertFalse(db.isReadOnly());
            assertFalse(db.isDirect());
            assertTrue(db.hasArray());
            assertFalse(db.hasByteBuffer());

            assertEquals(expectedOrder, db.order());
            assertEquals(expectedCapacity, db.capacity());
            assertEquals(expectedLimit, db.limit());
            assertEquals(0L, db.bitPosition());

            assertEquals(expectedCapacity, db.array().length);
            assertEquals(0, db.arrayOffset());
        }
    }

    public void test_allocateBB_int() {
        try {
            DataBuffer.allocateBB(-1);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
        
        /*
         * random
         */

        for (int capacity : new int[]{0,17}) {
            final ByteOrder expectedOrder = ByteOrder.BIG_ENDIAN;
            final int expectedCapacity = capacity;
            final int expectedLimit = expectedCapacity;

            DataBuffer db = DataBuffer.allocateBB(capacity);

            assertFalse(db.isReadOnly());
            assertFalse(db.isDirect());
            assertTrue(db.hasArray());
            assertTrue(db.hasByteBuffer());

            assertEquals(expectedOrder, db.order());
            assertEquals(expectedCapacity, db.capacity());
            assertEquals(expectedLimit, db.limit());
            assertEquals(0L, db.bitPosition());

            ByteBuffer bb = db.byteBuffer();

            assertSame(bb.array(), db.array());
            assertEquals(bb.arrayOffset(), db.arrayOffset());

            assertEquals(expectedOrder, bb.order());
            assertEquals(expectedCapacity, bb.capacity());
            assertEquals(expectedLimit, bb.limit());
        }
    }

    public void test_allocateBBDirect_int() {
        try {
            DataBuffer.allocateBBDirect(-1);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }

        /*
         * random
         */
        
        for (int capacity : new int[]{0,17}) {
            final ByteOrder expectedOrder = ByteOrder.BIG_ENDIAN;
            final int expectedCapacity = capacity;
            final int expectedLimit = expectedCapacity;

            DataBuffer db = DataBuffer.allocateBBDirect(capacity);

            assertFalse(db.isReadOnly());
            assertTrue(db.isDirect());
            assertFalse(db.hasArray());
            assertTrue(db.hasByteBuffer());

            assertEquals(expectedOrder, db.order());
            assertEquals(expectedCapacity, db.capacity());
            assertEquals(expectedLimit, db.limit());
            assertEquals(0L, db.bitPosition());

            ByteBuffer bb = db.byteBuffer();

            assertFalse(bb.isReadOnly());
            assertTrue(bb.isDirect());
            assertFalse(bb.hasArray());

            assertEquals(expectedOrder, bb.order());
            assertEquals(expectedCapacity, bb.capacity());
            assertEquals(expectedLimit, bb.limit());
        }
    }

    /*
     * 
     */

    public void test_newInstance_byteArray_and_wrap_byteArray() {
        try {
            DataBuffer.newInstance((byte[])null);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        try {
            DataBuffer.wrap((byte[])null);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        
        /*
         * random
         */

        for (boolean newInstance : new boolean[]{false,true}) {
            for (int capacity : new int[]{0,17}) {
                final ByteOrder expectedOrder = ByteOrder.BIG_ENDIAN;
                final int expectedCapacity = capacity;
                final int expectedLimit = expectedCapacity;

                byte[] ba = new byte[capacity];
                DataBuffer db = newInstance ? DataBuffer.newInstance(ba) : DataBuffer.wrap(ba);

                assertFalse(db.isReadOnly());
                assertFalse(db.isDirect());
                assertTrue(db.hasArray());
                assertFalse(db.hasByteBuffer());

                assertEquals(expectedOrder, db.order());
                assertEquals(expectedCapacity, db.capacity());
                assertEquals(expectedLimit, db.limit());
                assertEquals(0L, db.bitPosition());

                assertSame(ba, db.array());
                assertEquals(0, db.arrayOffset());
            }
        }
    }

    public void test_newInstance_byteArray_int_int() {
        try {
            DataBuffer.newInstance((byte[])null,-1,-1);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        
        try {
            DataBuffer.newInstance(new byte[0],0,-1);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            DataBuffer.newInstance(new byte[0],-1,0);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            DataBuffer.newInstance(new byte[0],1,0);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        
        /*
         * random
         */

        for (int capacity : new int[]{0,17}) {
            for (int offset : new int[]{0,3}) {
                for (int capacityToArrayCapacity : new int[]{0,5}) {
                    int arrayCapacity = offset + capacity + capacityToArrayCapacity;

                    final ByteOrder expectedOrder = ByteOrder.BIG_ENDIAN;
                    final int expectedCapacity = capacity;
                    final int expectedLimit = expectedCapacity;

                    byte[] ba = new byte[arrayCapacity];
                    DataBuffer db = DataBuffer.newInstance(ba,offset,capacity);

                    assertFalse(db.isReadOnly());
                    assertFalse(db.isDirect());
                    assertTrue(db.hasArray());
                    assertFalse(db.hasByteBuffer());

                    assertEquals(expectedOrder, db.order());
                    assertEquals(expectedCapacity, db.capacity());
                    assertEquals(expectedLimit, db.limit());
                    assertEquals(0L, db.bitPosition());

                    assertSame(ba, db.array());
                    assertEquals(offset, db.arrayOffset());
                }
            }
        }
    }

    public void test_newInstance_ByteBuffer() {
        try {
            DataBuffer.newInstance((ByteBuffer)null);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        
        for (int limit : new int[]{0,17}) {
            for (ByteBuffer bb : variousBB(limit)) {
                final ByteOrder expectedOrder = bb.order();
                final int expectedCapacity = bb.capacity();
                final int expectedLimit = expectedCapacity;

                DataBuffer db = DataBuffer.newInstance(bb);

                assertEquals(bb.isReadOnly(), db.isReadOnly());
                assertEquals(bb.isDirect(), db.isDirect());
                assertEquals(bb.hasArray(), db.hasArray());
                assertTrue(db.hasByteBuffer());

                assertEquals(expectedOrder, db.order());
                assertEquals(expectedCapacity, db.capacity());
                assertEquals(expectedLimit, db.limit());
                assertEquals(0L, db.bitPosition());

                if (db.hasArray()) {
                    assertSame(bb.array(), db.array());
                    assertSame(bb.arrayOffset(), db.arrayOffset());
                }
                
                assertSame(bb, db.byteBuffer());
                assertSame(expectedLimit, bb.limit());
            }
        }
    }

    /*
     * 
     */

    public void test_wrap_byteArray_int_int() {
        try {
            DataBuffer.wrap((byte[])null,-1,-1);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        
        try {
            DataBuffer.wrap(new byte[0],0,-1);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            DataBuffer.wrap(new byte[0],-1,0);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            DataBuffer.wrap(new byte[0],1,0);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        
        /*
         * random
         */

        for (int length : new int[]{0,17}) {
            for (int position : new int[]{0,3}) {
                for (int limitToArrayCapacity : new int[]{0,5}) {
                    final int limit = position + length;
                    int arrayCapacity = limit + limitToArrayCapacity;

                    final ByteOrder expectedOrder = ByteOrder.BIG_ENDIAN;
                    final int expectedCapacity = arrayCapacity;
                    final int expectedLimit = limit;

                    byte[] ba = new byte[arrayCapacity];
                    DataBuffer db = DataBuffer.wrap(ba,position,length);

                    assertFalse(db.isReadOnly());
                    assertFalse(db.isDirect());
                    assertTrue(db.hasArray());
                    assertFalse(db.hasByteBuffer());

                    assertEquals(expectedOrder, db.order());
                    assertEquals(expectedCapacity, db.capacity());
                    assertEquals(expectedLimit, db.limit());
                    assertEquals(position * 8L, db.bitPosition());

                    assertSame(ba, db.array());
                    assertEquals(0, db.arrayOffset());
                }
            }
        }
    }

    public void test_wrap_ByteBuffer() {
        try {
            DataBuffer.wrap((ByteBuffer)null);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        
        for (int limit : new int[]{0,17}) {
            for (ByteBuffer bb : variousBB(limit)) {
                final ByteOrder expectedOrder = bb.order();
                final int expectedCapacity = bb.capacity();
                final int expectedLimit = bb.limit();
                final int expectedPosition = bb.position();

                DataBuffer db = DataBuffer.wrap(bb);

                assertEquals(bb.isReadOnly(), db.isReadOnly());
                assertEquals(bb.isDirect(), db.isDirect());
                assertEquals(bb.hasArray(), db.hasArray());
                assertTrue(db.hasByteBuffer());

                assertEquals(expectedOrder, db.order());
                assertEquals(expectedCapacity, db.capacity());
                assertEquals(expectedLimit, db.limit());
                assertEquals(expectedPosition * 8L, db.bitPosition());

                if (db.hasArray()) {
                    assertSame(bb.array(), db.array());
                    assertSame(bb.arrayOffset(), db.arrayOffset());
                }
                
                assertSame(bb, db.byteBuffer());
                assertSame(expectedLimit, bb.limit());
                assertSame(expectedPosition, bb.position());
            }
        }
    }

    /*
     * 
     */
    
    public void test_slice() {
        for (int limit : new int[]{0,10}) {
            for (DataBuffer db : variousDB(limit)) {
                for (long bitPosition : new long[]{0L,1L,7L,8L,9L}) {
                    if (bitPosition > db.bitLimit()) {
                        continue;
                    }
                    // position taken into account
                    db.bitPosition(bitPosition);

                    final ByteOrder expectedOrder = db.order();
                    final int expectedCapacity = db.limit() - db.positionSup();
                    final int expectedLimit = expectedCapacity;

                    final int expectedOffsetBonus = db.positionSup();

                    DataBuffer created = db.slice();

                    assertNotSame(db, created);

                    assertEquals(db.isReadOnly(), created.isReadOnly());
                    assertEquals(db.isDirect(), created.isDirect());
                    assertEquals(db.hasArray(), created.hasArray());
                    assertEquals(db.hasByteBuffer(), created.hasByteBuffer());

                    assertEquals(expectedOrder, created.order());
                    assertEquals(expectedCapacity, created.capacity());
                    assertEquals(expectedLimit, created.limit());
                    assertEquals(0L, created.bitPosition());

                    if (created.hasArray()) {
                        assertSame(db.array(), created.array());
                        assertEquals(db.arrayOffset() + expectedOffsetBonus, created.arrayOffset());
                    }

                    if (created.hasByteBuffer()) {
                        assertNotSame(db.byteBuffer(), created.byteBuffer());
                        assertEquals(expectedOrder, created.byteBuffer().order());
                        assertEquals(expectedCapacity, created.byteBuffer().capacity());
                        assertEquals(expectedLimit, created.byteBuffer().limit());
                        // Initial ByteBuffer's position has been set before call to ByteBuffer.slice().
                        assertEquals(db.positionSup(), db.byteBuffer().position());
                    }

                    assertNoMark(created);
                }

                {
                    db.mark();
                    DataBuffer sliced = db.slice();
                    assertNoMark(sliced);
                }
            }
        }
    }

    public void test_duplicate() {
        test_duplicate_or_asReadOnlyBuffer(true);
    }
    public void test_asReadOnlyBuffer() {
        test_duplicate_or_asReadOnlyBuffer(false);
    }
    public void test_duplicate_or_asReadOnlyBuffer(boolean testDuplicate) {
        for (int limit : new int[]{0,10}) {
            for (DataBuffer db : variousDB(limit)) {
                for (long bitPosition : new long[]{0L,9L}) {
                    if (bitPosition > db.bitLimit()) {
                        continue;
                    }
                    // position taken into account
                    db.bitPosition(bitPosition);

                    final ByteOrder expectedOrder = db.order();
                    final int expectedCapacity = db.capacity();
                    final int expectedLimit = db.limit();
                    final long expectedBitPosition = db.bitPosition();

                    DataBuffer created = testDuplicate ? db.duplicate() : db.asReadOnlyBuffer();

                    assertNotSame(db, created);
                    assertEquals((testDuplicate ? db.isReadOnly() : true), created.isReadOnly());
                    assertEquals(db.isDirect(), created.isDirect());
                    assertEquals((testDuplicate ? db.hasArray() : false), created.hasArray());
                    assertEquals(db.hasByteBuffer(), created.hasByteBuffer());

                    assertEquals(expectedOrder, created.order());
                    assertEquals(expectedCapacity, created.capacity());
                    assertEquals(expectedLimit, created.limit());
                    assertEquals(expectedBitPosition, created.bitPosition());

                    if (created.hasArray()) {
                        assertSame(db.array(), created.array());
                        assertEquals(db.arrayOffset(), created.arrayOffset());
                    }

                    if (created.hasByteBuffer()) {
                        assertNotSame(db.byteBuffer(), created.byteBuffer());
                        assertEquals(expectedOrder, created.byteBuffer().order());
                        assertEquals(expectedCapacity, created.byteBuffer().capacity());
                        assertEquals(expectedLimit, created.byteBuffer().limit());
                    }

                    assertNoMark(created);
                }

                for (long markedBitPosition : new long[]{0L,9L}) {
                    if (markedBitPosition+1 > db.bitLimit()) {
                        continue;
                    }
                    db.bitPosition(markedBitPosition);

                    db.mark();
                    db.bitPosition(markedBitPosition+1);

                    DataBuffer created = testDuplicate ? db.duplicate() : db.asReadOnlyBuffer();

                    assertEquals(markedBitPosition+1, created.bitPosition());
                    created.reset();
                    assertEquals(markedBitPosition, created.bitPosition());
                }
            }
        }
    }

    /*
     * 
     */

    public void test_setBackingBuffer_byteArray() {
        for (DataBuffer db : variousDB()) {
            if (db.isReadOnly()) {
                try {
                    db.setBackingBuffer((byte[])null);
                    assertTrue(false);
                } catch (ReadOnlyBufferException e) {
                    // ok
                }
            } else {
                try {
                    db.setBackingBuffer((byte[])null);
                    assertTrue(false);
                } catch (NullPointerException e) {
                    // ok
                }

                for (int capacity : new int[]{0,17}) {
                    final ByteOrder expectedOrder = db.order();
                    final int expectedCapacity = capacity;
                    final int expectedLimit = expectedCapacity;

                    byte[] ba = new byte[capacity];
                    db.setBackingBuffer(ba);

                    assertFalse(db.isReadOnly());
                    assertFalse(db.isDirect());
                    assertTrue(db.hasArray());
                    assertFalse(db.hasByteBuffer());

                    assertEquals(expectedOrder, db.order());
                    assertEquals(expectedCapacity, db.capacity());
                    assertEquals(expectedLimit, db.limit());
                    assertEquals(0L, db.bitPosition());

                    assertSame(ba, db.array());
                    assertEquals(0, db.arrayOffset());
                }
            }
        }
    }

    public void test_setBackingBuffer_byteArray_int_int() {
        for (DataBuffer db : variousDB()) {
            if (db.isReadOnly()) {
                try {
                    db.setBackingBuffer((byte[])null,-1,-1);
                    assertTrue(false);
                } catch (ReadOnlyBufferException e) {
                    // ok
                }
            } else {
                try {
                    db.setBackingBuffer((byte[])null,-1,-1);
                    assertTrue(false);
                } catch (NullPointerException e) {
                    // ok
                }

                for (int capacity : new int[]{0,17}) {
                    for (int offset : new int[]{0,3}) {
                        for (int capacityToArrayCapacity : new int[]{0,5}) {
                            int arrayCapacity = offset + capacity + capacityToArrayCapacity;

                            final ByteOrder expectedOrder = db.order();
                            final int expectedCapacity = capacity;
                            final int expectedLimit = expectedCapacity;

                            byte[] ba = new byte[arrayCapacity];
                            db.setBackingBuffer(ba, offset, capacity);

                            assertFalse(db.isReadOnly());
                            assertFalse(db.isDirect());
                            assertTrue(db.hasArray());
                            assertFalse(db.hasByteBuffer());

                            assertEquals(expectedOrder, db.order());
                            assertEquals(expectedCapacity, db.capacity());
                            assertEquals(expectedLimit, db.limit());
                            assertEquals(0L, db.bitPosition());

                            assertSame(ba, db.array());
                            assertEquals(offset, db.arrayOffset());
                        }
                    }
                }
            }
        }
    }

    public void test_setBackingBuffer_ByteBuffer() {
        for (DataBuffer db : variousDB()) {
            if (db.isReadOnly()) {
                try {
                    db.setBackingBuffer((ByteBuffer)null);
                    assertTrue(false);
                } catch (ReadOnlyBufferException e) {
                    // ok
                }
            } else {
                try {
                    db.setBackingBuffer((ByteBuffer)null);
                    assertTrue(false);
                } catch (NullPointerException e) {
                    // ok
                }

                for (ByteBuffer bb : variousBB()) {
                    final ByteOrder expectedOrder = bb.order();
                    final int expectedCapacity = bb.capacity();
                    final int expectedLimit = expectedCapacity;

                    final int bbPosition = bb.position();
                    db.setBackingBuffer(bb);
                    // unchanged
                    assertEquals(bbPosition, bb.position());

                    // Yes, DataBuffer remains writable, even though we allow read-only ByteBuffers.
                    assertFalse(db.isReadOnly());
                    assertEquals(bb.isDirect(), db.isDirect());
                    assertTrue(db.hasByteBuffer());
                    assertEquals(bb.hasArray(), db.hasArray());

                    assertEquals(expectedOrder, db.order());
                    assertEquals(expectedCapacity, db.capacity());
                    assertEquals(expectedLimit, db.limit());
                    assertEquals(0L, db.bitPosition());

                    if (db.hasArray()) {
                        assertSame(bb.array(), db.array());
                        assertEquals(bb.arrayOffset(), db.arrayOffset());
                    }

                    assertSame(bb, db.byteBuffer());
                    assertEquals(expectedOrder, bb.order());
                    assertEquals(expectedCapacity, bb.capacity());
                    assertEquals(expectedLimit, bb.limit());
                }
            }
        }
    }

    /*
     * 
     */

    public void test_hashCode() {
        for (DataBuffer db : variousDB()) {
            ByteBuffer bb = cloneByteBuffer(db);

            for (long bp=0;bp<=db.bitLimit();bp++) {
                db.bitPosition(bp);
                bb.position(db.positionInf());

                assertEquals(bb.hashCode(), db.hashCode());
            }
        }
    }

    public void test_equals_Object_and_compareTo_DataBuffer() {
        for (DataBuffer db : variousDB()) {
            assertFalse(db.equals(null));
            assertFalse(db.equals(new Object()));
            assertFalse(db.equals(cloneByteBuffer(db)));
            try {
                db.compareTo(null);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
        }

        // A few longs, plus additional bytes.
        final int limit = 8 * 3 + 7;
        for (DataBuffer[] pair : variousDBPairs(limit,limit)) {
            DataBuffer db1 = pair[0];
            DataBuffer db2 = pair[1];
            if (db1.isReadOnly() || db2.isReadOnly()) {
                // Making our own read-only buffers,
                // for we want to set their content.
                continue;
            }
            if ((db1.limit() == 0) || (db2.limit() == 0)) {
                // Can't do much.
                continue;
            }

            for (int k=0;k<100;k++) {

                fillZero(db1);
                fillZero(db2);

                db1.orderIgnoreBitPosition(randomOrder());
                db1.bitPosition(random.nextInt((int)(db1.bitLimit()+1)));

                // Same order, or not.
                if (random.nextBoolean()) {
                    db2.orderIgnoreBitPosition(db1.order());
                } else {
                    db2.orderIgnoreBitPosition(ByteOrderUtils.opposite(db1.order()));
                }

                // Same number of bits remaining, or not.
                if (random.nextBoolean()) {
                    if (db1.limit() < db2.limit()) {
                        db2.bitPosition(db2.bitLimit() - db1.bitsRemaining());
                    } else {
                        db1.bitPosition(db1.bitLimit() - db2.bitsRemaining());
                    }
                    LangUtils.azzert(db1.bitsRemaining() == db2.bitsRemaining());
                } else {
                    db2.bitPosition(random.nextInt((int)(db2.bitLimit()+1)));
                }

                final int cmpByteSize = Math.min(db1.limit()-db1.positionInf(), db2.limit()-db2.positionInf());
                if (cmpByteSize != 0) {
                    // Eventually setting a random byte to 1.
                    if (random.nextBoolean()) {
                        int index = random.nextInt(cmpByteSize);
                        db2.putByteAt(db2.positionInf()+index, (byte)1);
                    }
                    if (random.nextBoolean()) {
                        int index = random.nextInt(cmpByteSize);
                        db1.putByteAt(db1.positionInf()+index, (byte)1);
                    }
                }

                // Read-only versions (our asReadOnlyBuffer preserves order), or not.
                DataBuffer db1ToUse = random.nextBoolean() ? db1 : db1.asReadOnlyBuffer();
                DataBuffer db2ToUse = random.nextBoolean() ? db2 : db2.asReadOnlyBuffer();

                ByteBuffer bb1 = cloneByteBuffer(db1ToUse);
                ByteBuffer bb2 = cloneByteBuffer(db2ToUse);

                final boolean expectedEq = bb1.equals(bb2) && (db1ToUse.bitsRemaining() == db2ToUse.bitsRemaining());
                final boolean dbEq = db1ToUse.equals(db2ToUse);
                assertEquals(expectedEq, dbEq);

                final int bbCmp = bb1.compareTo(bb2);
                final int expectedCmp = (bbCmp != 0) ? bbCmp : NumbersUtils.signum(db1ToUse.bitsRemaining() - db2ToUse.bitsRemaining());
                final int dbCmp = db1ToUse.compareTo(db2ToUse);
                assertEqualsSignum(expectedCmp, dbCmp);
            }
        }
    }

    /*
     * 
     */

    public void test_isReadOnly() {
        // Already covered by tests of methods creating DataBuffers.
    }

    public void test_isDirect() {
        // Already covered by tests of methods creating DataBuffers.
    }

    /*
     * 
     */

    public void test_hasArray_and_array_and_arrayOffset() {
        // Already quite covered in test for methods
        // creating DataBuffers backed by specified
        // byte arrays or ByteBuffers.
        // Here we test mostly the exceptional cases.
        for (DataBuffer db : variousDB()) {
            if (db.hasArray()) {
                if (db.hasByteBuffer()) {
                    assertSame(db.byteBuffer().array(), db.array());
                    assertEquals(db.byteBuffer().arrayOffset(), db.arrayOffset());
                } else {
                    assertNotNull(db.array());
                    assertTrue(db.arrayOffset() >= 0);
                }
            } else {
                try {
                    db.array();
                    assertTrue(false);
                } catch (ReadOnlyBufferException e) {
                    assertTrue(db.isReadOnly());
                } catch (UnsupportedOperationException e) {
                    // DataBuffer can be writable, but backing ByteBuffer read-only.
                    assertTrue(db.isDirect() || db.byteBuffer().isReadOnly());
                }
                try {
                    db.arrayOffset();
                    assertTrue(false);
                } catch (ReadOnlyBufferException e) {
                    assertTrue(db.isReadOnly());
                } catch (UnsupportedOperationException e) {
                    // DataBuffer can be writable, but backing ByteBuffer read-only.
                    assertTrue(db.isDirect() || db.byteBuffer().isReadOnly());
                }
            }
        }
    }

    public void test_hasByteBuffer_and_byteBuffer() {
        // Already quite covered in test for methods
        // creating DataBuffers backed by specified
        // byte arrays or ByteBuffers.
        // Here we test mostly the exceptional cases.
        for (DataBuffer db : variousDB()) {
            if (db.hasByteBuffer()) {
                ByteBuffer bb = db.byteBuffer();
                assertNotNull(bb);
                assertEquals(bb.isReadOnly(), db.isReadOnly());
                assertEquals(bb.isDirect(), db.isDirect());
                assertEquals(bb.hasArray(), db.hasArray());
            } else {
                try {
                    db.byteBuffer();
                    assertTrue(false);
                } catch (UnsupportedOperationException e) {
                    // ok
                }
            }
        }
    }

    /*
     * 
     */

    public void test_mark_and_reset() {
        for (DataBuffer db : variousDB()) {
            assertNoMark(db);
            db.bitPosition(13);

            assertSame(db, db.mark());

            db.bitPosition(17);

            assertSame(db, db.reset());

            assertEquals(13, db.bitPosition());
        }
    }

    public void test_clear() {
        for (DataBuffer db : variousDB()) {
            db.limit(db.capacity()-1);
            db.bitPosition(1);
            db.mark();

            assertSame(db, db.clear());

            assertEquals(db.capacity(), db.limit());
            assertEquals(db.bitCapacity(), db.bitLimit());
            assertEquals(0, db.bitPosition());
            assertNoMark(db);
        }
    }

    public void test_flip() {
        for (DataBuffer db : variousDB()) {
            db.limit(db.capacity()-1);
            db.bitPosition(3);
            db.mark();

            assertSame(db, db.flip());

            assertEquals(1, db.limit());
            assertEquals(8L, db.bitLimit());
            assertEquals(0, db.bitPosition());
            assertNoMark(db);
        }
    }

    public void test_rewind() {
        for (DataBuffer db : variousDB()) {
            db.limit(db.capacity()-1);
            db.bitPosition(1);
            db.mark();

            assertSame(db, db.rewind());

            assertEquals(db.capacity()-1, db.limit());
            assertEquals(0, db.bitPosition());
            assertNoMark(db);
        }
    }

    public void test_compact() {
        for (DataBuffer db : variousDB()) {
            if (db.isReadOnly()) {
                try {
                    db.compact();
                    assertTrue(false);
                } catch (ReadOnlyBufferException e) {
                    // ok
                }
            } else {
                final ByteOrder initialOrder = db.order();
                final int initialCapacity = db.capacity();
                final int initialLimit = db.limit();
                final long initialBitPosition = db.bitPosition();

                byte[] expectedContent = cloneContentUpToCapacity(db);
                final long bitSize = db.bitLimit() - db.bitPosition();
                ByteArrayUtils.arrayCopyBits(expectedContent, initialBitPosition, expectedContent, 0L, bitSize, initialOrder);

                db.mark();

                assertSame(db, db.compact());

                assertNoMark(db);

                assertEquals(initialOrder, db.order());
                assertEquals(initialCapacity, db.capacity());
                assertEquals(initialCapacity, db.limit());
                assertEquals(initialLimit * 8L - initialBitPosition, db.bitPosition());

                // Also checking that past-limit content is not messed-up.
                db.limit(db.capacity());
                for (int i=0;i<db.capacity();i++) {
                    assertEquals(expectedContent[i], db.getByteAt(i));
                }
            }
        }
    }

    /*
     * 
     */

    public void test_order_and_order_ByteOrder_and_orderIgnoreBitPosition_ByteOrder() {
        for (DataBuffer db : variousDB()) {
            // change inside of a byte
            db.bitPosition(1L);
            try {
                db.order(ByteOrderUtils.opposite(db.order()));
                assertTrue(false);
            } catch (IllegalStateException e) {
                // ok
            }
            assertSame(db, db.orderIgnoreBitPosition(ByteOrderUtils.opposite(db.order())));

            // change at byte border
            for (int p=0;p<db.limit();p++) {
                db.bitPosition(p * 8L);
                final ByteOrder newOrder = ByteOrderUtils.opposite(db.order());
                // Sometimes a method, sometimes the other.
                if ((p&1) == 0) {
                    assertSame(db, db.order(newOrder));
                } else {
                    assertSame(db, db.orderIgnoreBitPosition(newOrder));
                }
                assertEquals(newOrder, db.order());
                // Backing ByteBuffer's limit setting.
                if (db.hasByteBuffer()) {
                    assertEquals(newOrder, db.byteBuffer().order());
                }
            }
        }
    }

    /*
     * 
     */

    public void test_bitsRemaining() {
        for (DataBuffer db : variousDB()) {
            assertEquals(db.bitLimit()-db.bitPosition(), db.bitsRemaining());
            db.limit(db.capacity()-1);
            assertEquals(db.bitLimit()-db.bitPosition(), db.bitsRemaining());
            db.bitPosition(1);
            assertEquals(db.bitLimit()-db.bitPosition(), db.bitsRemaining());
        }
    }

    public void test_hasBitsRemaining() {
        for (DataBuffer db : variousDB()) {
            assertTrue(db.hasBitsRemaining());
            db.bitPosition(db.bitLimit());
            assertFalse(db.hasBitsRemaining());
        }
    }

    public void test_capacity() {
        // Already covered by tests of methods creating DataBuffers.
    }

    public void test_bitCapacity() {
        for (DataBuffer db : variousDB()) {
            assertEquals(db.capacity() * 8L, db.bitCapacity());
        }
    }

    public void test_limit_and_bitLimit_and_limit_int() {
        for (DataBuffer db : variousDB()) {
            final int capacity = db.capacity();

            // too small
            try {
                db.limit(-1);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }

            // too large
            try {
                db.limit(capacity + 1);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }

            // position reworked, mark cleared
            db.bitPosition(17L);
            db.mark();
            assertSame(db, db.limit(2));
            assertEquals(2, db.limit());
            assertEquals(16L, db.bitLimit());
            assertEquals(16L, db.bitPosition());
            assertNoMark(db);

            // position not reworked, mark not cleared
            db.bitPosition(11L);
            db.mark();
            assertSame(db, db.limit(3));
            assertEquals(3, db.limit());
            assertEquals(24L, db.bitLimit());
            assertEquals(11L, db.bitPosition());
            db.bitPosition(12L);
            db.reset();
            assertEquals(11L, db.bitPosition());

            // Backing ByteBuffer's limit setting.
            if (db.hasByteBuffer()) {
                assertSame(db, db.limit(1));
                assertEquals(1, db.byteBuffer().limit());

                assertSame(db, db.limit(capacity));
                assertEquals(capacity, db.byteBuffer().limit());
            }
        }
    }

    public void test_bitPosition_and_bitPosition_long() {
        for (DataBuffer db : variousDB()) {
            assertEquals(0, db.bitPosition());

            // too small
            try {
                db.bitPosition(-1L);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
            assertEquals(0, db.bitPosition());

            // too large
            try {
                db.bitPosition(db.bitLimit() + 1);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
            assertEquals(0, db.bitPosition());

            // mark cleared
            assertSame(db, db.bitPosition(17L));
            assertEquals(17L, db.bitPosition());
            db.mark();
            assertSame(db, db.bitPosition(13L));
            assertEquals(13L, db.bitPosition());
            assertNoMark(db);

            // mark not cleared
            assertSame(db, db.bitPosition(11L));
            assertEquals(11L, db.bitPosition());
            db.mark();
            assertSame(db, db.bitPosition(14L));
            assertEquals(14L, db.bitPosition());
            db.reset();
            assertEquals(11L, db.bitPosition());
        }
    }

    public void test_positionInf_and_positionSup() {
        for (DataBuffer db : variousDB()) {
            final long bitLimit = db.bitLimit();
            for (long bitPosition=0;bitPosition<=bitLimit;bitPosition++) {
                db.bitPosition(bitPosition);
                assertEquals(bitPosition/8,db.positionInf());
                assertEquals((bitPosition+7)/8,db.positionSup());
            }
        }
    }

    /*
     * 
     */

    public void test_put_byteArray_and_put_byteArray_int_int() {
        for (DataBuffer db : variousDB()) {
            if (db.isReadOnly()) {
                try {
                    db.put((byte[])null);
                    assertTrue(false);
                } catch (ReadOnlyBufferException e) {
                    // ok
                }
                try {
                    db.put((byte[])null,-1,-1);
                    assertTrue(false);
                } catch (ReadOnlyBufferException e) {
                    // ok
                }
                continue;
            }

            /*
             * null
             */

            try {
                db.put((byte[])null);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
            try {
                db.put((byte[])null,-1,-1);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }

            /*
             * bad length
             */

            try {
                db.put(new byte[1],0,-1);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * bad byte range
             */

            try {
                db.put(new byte[1],-1,0);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                db.put(new byte[1],0,2);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                db.put(new byte[1],1,1);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * random
             */

            for (int k=0;k<1000;k++) {
                final int arrayCapacity = random.nextInt(13);
                final byte[] src = new byte[arrayCapacity];
                fillRandom(src);
                db.bitPosition(random.nextInt(8));

                final int offset = (arrayCapacity == 0) ? 0 : random.nextInt(src.length);
                final int length = (arrayCapacity == 0) ? 0 : random.nextInt(src.length - offset);
                final long bitSize = length * 8L;

                if (src.length * 8L > db.bitsRemaining()) {
                    try {
                        db.put(src);
                    } catch (BufferOverflowException e) {
                        // ok
                    }
                }

                if (bitSize > db.bitsRemaining()) {
                    try {
                        db.put(src, offset, length);
                    } catch (BufferOverflowException e) {
                        // ok
                    }
                } else {
                    final ByteOrder initialOrder = db.order();
                    final int initialCapacity = db.capacity();
                    final int initialLimit = db.limit();
                    final long initialBitPosition = db.bitPosition();

                    byte[] expectedContent = cloneContentUpToCapacity(db);
                    ByteArrayUtils.arrayCopyBits(src, offset * 8L, expectedContent, initialBitPosition, bitSize, initialOrder);

                    if ((offset == 0) && (length == src.length) && random.nextBoolean()) {
                        assertSame(db, db.put(src));
                    } else {
                        assertSame(db, db.put(src, offset, length));
                    }

                    assertEquals(initialOrder, db.order());
                    assertEquals(initialCapacity, db.capacity());
                    assertEquals(initialLimit, db.limit());
                    assertEquals(initialBitPosition + bitSize, db.bitPosition());

                    // Also checking that past-limit content is not messed-up.
                    db.limit(db.capacity());
                    for (int i=0;i<db.capacity();i++) {
                        assertEquals(expectedContent[i], db.getByteAt(i));
                    }
                }
            }
        }
    }

    /*
     * 
     */

    public void test_get_byteArray_and_get_byteArray_int_int() {
        for (DataBuffer db : variousDB()) {

            /*
             * null
             */

            try {
                db.get((byte[])null);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
            try {
                db.get((byte[])null,-1,-1);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }

            /*
             * bad length
             */

            try {
                db.get(new byte[1],0,-1);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * bad byte range
             */

            try {
                db.get(new byte[1],-1,0);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                db.get(new byte[1],0,2);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
            try {
                db.get(new byte[1],1,1);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }

            /*
             * random
             */

            for (int k=0;k<1000;k++) {
                final int arrayCapacity = random.nextInt(13);
                final byte[] dst = new byte[arrayCapacity];
                fillRandom(dst);
                if (!db.isReadOnly()) {
                    fillRandom(db);
                }
                db.bitPosition(random.nextInt(8));

                final int offset = (arrayCapacity == 0) ? 0 : random.nextInt(dst.length);
                final int length = (arrayCapacity == 0) ? 0 : random.nextInt(dst.length - offset);
                final long bitSize = length * 8L;

                if (dst.length * 8L > db.bitsRemaining()) {
                    try {
                        db.get(dst);
                    } catch (BufferUnderflowException e) {
                        // ok
                    }
                }

                if (bitSize > db.bitsRemaining()) {
                    try {
                        db.get(dst, offset, length);
                    } catch (BufferUnderflowException e) {
                        // ok
                    }
                } else {
                    final ByteOrder initialOrder = db.order();
                    final int initialCapacity = db.capacity();
                    final int initialLimit = db.limit();
                    final long initialBitPosition = db.bitPosition();

                    byte[] initialDBContent = cloneContentUpToCapacity(db);
                    byte[] expectedContent = cloneContent(dst);
                    ByteArrayUtils.arrayCopyBits(initialDBContent, initialBitPosition, expectedContent, offset * 8L, bitSize, initialOrder);

                    if ((offset == 0) && (length == dst.length) && random.nextBoolean()) {
                        assertSame(db, db.get(dst));
                    } else {
                        assertSame(db, db.get(dst, offset, length));
                    }

                    assertEquals(initialOrder, db.order());
                    assertEquals(initialCapacity, db.capacity());
                    assertEquals(initialLimit, db.limit());
                    assertEquals(initialBitPosition + bitSize, db.bitPosition());

                    // DataBuffer content must not have changed
                    // (we didn't provide get its backing array, if any).
                    db.limit(db.capacity());
                    for (int i=0;i<initialDBContent.length;i++) {
                        assertEquals(initialDBContent[i], db.getByteAt(i));
                    }
                    db.limit(initialLimit);

                    // Retrieved content.
                    for (int i=0;i<dst.length;i++) {
                        assertEquals(expectedContent[i], dst[i]);
                    }
                }
            }
        }
    }

    /*
     * 
     */

    public void test_putByteAt_int_byte_to_putLongAt_int_long() {
        for (final int bitZise : new int[]{8,16,32,64}) {
            test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
                @Override
                public boolean isSigned() { return true; }
                @Override
                public boolean isPosBitwise() { return false; }
                @Override
                public boolean isSizeBitwise() { return false; }
                @Override
                public int getMinBitSize() { return bitZise; }
                @Override
                public int getMaxBitSize() { return bitZise; }
                @Override
                public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                    if (tab.isReadOnly()) {
                        // Put method will throw ReadOnlyBufferException, whatever the arguments.
                    } else {
                        tab.myBufferOrder(tab.order);

                        LangUtils.azzert((firstBitPos&7) == 0);
                        LangUtils.azzert(bitSize == bitZise);
                        if (bitZise == 8) {
                            LangUtils.azzert(value == (byte)value);
                        } else if (bitZise == 16) {
                            LangUtils.azzert(value == (short)value);
                            LangUtils.azzert(value == (short)(char)value);
                        } else if (bitZise == 32) {
                            LangUtils.azzert(value == (int)value);
                        }
                    }

                    final int index = (int)(firstBitPos/8L);
                    final long previousBitPosition = tab.buffer.bitPosition();
                    if (bitZise == 8) {
                        assertSame(tab.buffer, tab.buffer.putByteAt(index, (byte)value));
                    } else if (bitZise == 16) {
                        if (random.nextBoolean()) {
                            assertSame(tab.buffer, tab.buffer.putShortAt(index, (short)value));
                        } else {
                            assertSame(tab.buffer, tab.buffer.putCharAt(index, (char)value));
                        }
                    } else if (bitZise == 32) {
                        if (random.nextBoolean()) {
                            assertSame(tab.buffer, tab.buffer.putIntAt(index, (int)value));
                        } else {
                            float fp = Float.intBitsToFloat((int)value);
                            if (AVOID_NAN_BUG && Float.isNaN(fp)) {
                                assertSame(tab.buffer, tab.buffer.putIntAt(index, (int)value));
                            } else {
                                assertSame(tab.buffer, tab.buffer.putFloatAt(index, fp));
                            }
                        }
                    } else {
                        if (random.nextBoolean()) {
                            assertSame(tab.buffer, tab.buffer.putLongAt(index, value));
                        } else {
                            double fp = Double.longBitsToDouble(value);
                            if (AVOID_NAN_BUG && Double.isNaN(fp)) {
                                assertSame(tab.buffer, tab.buffer.putLongAt(index, value));
                            } else {
                                assertSame(tab.buffer, tab.buffer.putDoubleAt(index, fp));
                            }
                        }
                    }
                    assertEquals(previousBitPosition,tab.buffer.bitPosition());
                }
            });
        }
    }

    /*
     * 
     */

    public void test_getByteAt_int_to_getLongAt_int() {
        for (final int bitZise : new int[]{8,16,32,64}) {
            test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
                @Override
                public boolean isSigned() { return true; }
                @Override
                public boolean isPosBitwise() { return false; }
                @Override
                public boolean isSizeBitwise() { return false; }
                @Override
                public int getMinBitSize() { return bitZise; }
                @Override
                public int getMaxBitSize() { return bitZise; }
                @Override
                public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                    tab.myBufferOrder(tab.order);

                    LangUtils.azzert((firstBitPos&7) == 0);
                    LangUtils.azzert(bitSize == bitZise);

                    final int index = (int)(firstBitPos/8L);
                    final long previousBitPosition = tab.buffer.bitPosition();
                    final long result;
                    if (bitZise == 8) {
                        result = tab.buffer.getByteAt(index);
                    } else if (bitZise == 16) {
                        if (random.nextBoolean()) {
                            result = tab.buffer.getShortAt(index);
                        } else {
                            result = (short)tab.buffer.getCharAt(index);
                        }
                    } else if (bitZise == 32) {
                        if (random.nextBoolean()) {
                            result = tab.buffer.getIntAt(index);
                        } else {
                            float fp = tab.buffer.getFloatAt(index);
                            if (AVOID_NAN_BUG && Float.isNaN(fp)) {
                                result = tab.buffer.getIntAt(index);
                            } else {
                                result = Float.floatToRawIntBits(fp);
                            }
                        }
                    } else {
                        if (random.nextBoolean()) {
                            result = tab.buffer.getLongAt(index);
                        } else {
                            double fp = tab.buffer.getDoubleAt(index);
                            if (AVOID_NAN_BUG && Double.isNaN(fp)) {
                                result = tab.buffer.getLongAt(index);
                            } else {
                                result = Double.doubleToRawLongBits(fp);
                            }
                        }
                    }
                    assertEquals(previousBitPosition,tab.buffer.bitPosition());
                    return result;
                }
            });
        }
    }

    /*
     * 
     */

    public void test_putByte_byte_to_putLong_long() {
        final AtomicBoolean didThrow_BufferOverflowException = new AtomicBoolean();
        for (final int bitZise : new int[]{8,16,32,64}) {
            test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
                @Override
                public boolean isSigned() { return true; }
                @Override
                public boolean isPosBitwise() { return true; }
                @Override
                public boolean isSizeBitwise() { return false; }
                @Override
                public int getMinBitSize() { return bitZise; }
                @Override
                public int getMaxBitSize() { return bitZise; }
                @Override
                public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                    if (tab.isReadOnly()) {
                        // Put method will throw ReadOnlyBufferException, whatever the arguments.
                    } else {
                        tab.myBufferOrder(tab.order);
                        tab.myBufferBitPosition(firstBitPos);

                        LangUtils.azzert(bitSize == bitZise);

                        if (bitZise == 8) {
                            LangUtils.azzert(value == (byte)value);
                        } else if (bitZise == 16) {
                            LangUtils.azzert(value == (short)value);
                            LangUtils.azzert(value == (short)(char)value);
                        } else if (bitZise == 32) {
                            LangUtils.azzert(value == (int)value);
                        }
                    }

                    try {
                        if (bitZise == 8) {
                            assertSame(tab.buffer, tab.buffer.putByte((byte)value));
                        } else if (bitZise == 16) {
                            if (random.nextBoolean()) {
                                assertSame(tab.buffer, tab.buffer.putShort((short)value));
                            } else {
                                assertSame(tab.buffer, tab.buffer.putChar((char)value));
                            }
                        } else if (bitZise == 32) {
                            if (random.nextBoolean()) {
                                assertSame(tab.buffer, tab.buffer.putInt((int)value));
                            } else {
                                float fp = Float.intBitsToFloat((int)value);
                                if (AVOID_NAN_BUG && Float.isNaN(fp)) {
                                    assertSame(tab.buffer, tab.buffer.putInt((int)value));
                                } else {
                                    assertSame(tab.buffer, tab.buffer.putFloat(fp));
                                }
                            }
                        } else {
                            if (random.nextBoolean()) {
                                assertSame(tab.buffer, tab.buffer.putLong(value));
                            } else {
                                double fp = Double.longBitsToDouble(value);
                                if (AVOID_NAN_BUG && Double.isNaN(fp)) {
                                    assertSame(tab.buffer, tab.buffer.putLong(value));
                                } else {
                                    assertSame(tab.buffer, tab.buffer.putDouble(fp));
                                }
                            }
                        }
                    } catch (BufferOverflowException e) {
                        didThrow_BufferOverflowException.set(true);
                        throw new IndexOutOfBoundsException();
                    }
                    assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
                }
            });
            assertTrue(didThrow_BufferOverflowException.get());
        }
    }

    /*
     * 
     */

    public void test_getByte_to_getLong() {
        final AtomicBoolean didThrow_BufferUnderflowException = new AtomicBoolean();
        for (final int bitZise : new int[]{8,16,32,64}) {
            test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
                @Override
                public boolean isSigned() { return true; }
                @Override
                public boolean isPosBitwise() { return true; }
                @Override
                public boolean isSizeBitwise() { return false; }
                @Override
                public int getMinBitSize() { return bitZise; }
                @Override
                public int getMaxBitSize() { return bitZise; }
                @Override
                public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                    tab.myBufferOrder(tab.order);
                    tab.myBufferBitPosition(firstBitPos);

                    LangUtils.azzert(bitSize == bitZise);

                    final long result;
                    try {
                        if (bitZise == 8) {
                            result = tab.buffer.getByte();
                        } else if (bitZise == 16) {
                            if (random.nextBoolean()) {
                                result = tab.buffer.getShort();
                            } else {
                                result = (short)tab.buffer.getChar();
                            }
                        } else if (bitZise == 32) {
                            if (random.nextBoolean()) {
                                result = tab.buffer.getInt();
                            } else {
                                float fp = tab.buffer.getFloat();
                                if (AVOID_NAN_BUG && Float.isNaN(fp)) {
                                    tab.buffer.bitPosition(tab.buffer.bitPosition()-bitZise);
                                    result = tab.buffer.getInt();
                                } else {
                                    result = Float.floatToRawIntBits(fp);
                                }
                            }
                        } else {
                            if (random.nextBoolean()) {
                                result = tab.buffer.getLong();
                            } else {
                                double fp = tab.buffer.getDouble();
                                if (AVOID_NAN_BUG && Double.isNaN(fp)) {
                                    tab.buffer.bitPosition(tab.buffer.bitPosition()-bitZise);
                                    result = tab.buffer.getLong();
                                } else {
                                    result = Double.doubleToRawLongBits(fp);
                                }
                            }
                        }
                    } catch (BufferUnderflowException e) {
                        didThrow_BufferUnderflowException.set(true);
                        throw new IndexOutOfBoundsException();
                    }
                    assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
                    return result;
                }
            });
            assertTrue(didThrow_BufferUnderflowException.get());
        }
    }

    /*
     * 
     */

    public void test_padByteAtBit_long() {
        test_padByteAtBitOperation(new InterfacePadByteAtBitOperation<MyTab>() {
            @Override
            public int padByteAtBit(MyTab tab, long bitPos) {
                if (tab.isReadOnly()) {
                    // Padding method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                }

                final long previousBitPosition = tab.buffer.bitPosition();
                final int result = tab.buffer.padByteAtBit(bitPos);
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
                return result;
            }
        });
    }

    public void test_padLastByte() {
        test_padByteAtBitOperation(new InterfacePadByteAtBitOperation<MyTab>() {
            @Override
            public int padByteAtBit(MyTab tab, long bitPos) {
                if (tab.isReadOnly()) {
                    // Padding method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                    tab.myBufferBitPosition(bitPos);
                }

                final int result = tab.buffer.padLastByte();
                assertEquals(bitPos,tab.buffer.bitPosition());
                return result;
            }
        });
    }

    public void test_putBytePadding() {
        test_padByteAtBitOperation(new InterfacePadByteAtBitOperation<MyTab>() {
            @Override
            public int padByteAtBit(MyTab tab, long bitPos) {
                if (tab.isReadOnly()) {
                    // Padding method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                    tab.myBufferBitPosition(bitPos);
                }

                final long previousBitPosition = tab.buffer.bitPosition();
                final long expectedBitPosition = tab.buffer.positionSup() * 8L;
                assertSame(tab.buffer, tab.buffer.putBytePadding());
                assertEquals(expectedBitPosition,tab.buffer.bitPosition());
                return (int)(expectedBitPosition - previousBitPosition);
            }
        });
    }

    /*
     * 
     */

    public void test_putBitAtBit_long_boolean() {
        test_putBitAtBitOperation(new InterfacePutBitAtBitOperation<MyTab>() {
            @Override
            public void putBitAtBit(MyTab tab, long bitPos, boolean value) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                }

                final long previousBitPosition = tab.buffer.bitPosition();
                assertSame(tab.buffer, tab.buffer.putBitAtBit(bitPos, value));
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
            }
        });
    }

    public void test_putBit_boolean() {
        final AtomicBoolean didThrow_BufferOverflowException = new AtomicBoolean();
        test_putBitAtBitOperation(new InterfacePutBitAtBitOperation<MyTab>() {
            @Override
            public void putBitAtBit(MyTab tab, long bitPos, boolean value) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                    tab.myBufferBitPosition(bitPos);
                }

                DataBuffer result = null;
                try {
                    result = tab.buffer.putBit(value);
                } catch (BufferOverflowException e) {
                    didThrow_BufferOverflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertSame(tab.buffer, result);
                assertEquals(bitPos+1,tab.buffer.bitPosition());
            }
        });
        assertTrue(didThrow_BufferOverflowException.get());
    }

    public void test_getBitAtBit_long() {
        test_getBitAtBitOperation(new InterfaceGetBitAtBitOperation<MyTab>() {
            @Override
            public boolean getBitAtBit(MyTab tab, long bitPos) {
                tab.myBufferOrder(tab.order);
                
                final long previousBitPosition = tab.buffer.bitPosition();
                final boolean result = tab.buffer.getBitAtBit(bitPos);
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
                return result;
            }
        });
    }

    public void test_getBit() {
        final AtomicBoolean didThrow_BufferUnderflowException = new AtomicBoolean();
        test_getBitAtBitOperation(new InterfaceGetBitAtBitOperation<MyTab>() {
            @Override
            public boolean getBitAtBit(MyTab tab, long bitPos) {
                tab.myBufferOrder(tab.order);
                tab.myBufferBitPosition(bitPos);
                
                boolean result = false;
                try {
                    result = tab.buffer.getBit();
                } catch (BufferUnderflowException e) {
                    didThrow_BufferUnderflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertEquals(bitPos+1,tab.buffer.bitPosition());
                return result;
            }
        });
        assertTrue(didThrow_BufferUnderflowException.get());
    }

    /*
     * 
     */

    public void test_putIntSignedAtBit_long_int_int() {
        test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return true; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 32; }
            @Override
            public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);

                    LangUtils.azzert(value == (int)value);
                }

                final long previousBitPosition = tab.buffer.bitPosition();
                assertSame(tab.buffer, tab.buffer.putIntSignedAtBit(firstBitPos, (int)value, bitSize));
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
            }
        });
    }

    public void test_putLongSignedAtBit_long_long_int() {
        test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return true; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 64; }
            @Override
            public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                }

                final long previousBitPosition = tab.buffer.bitPosition();
                assertSame(tab.buffer, tab.buffer.putLongSignedAtBit(firstBitPos, value, bitSize));
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
            }
        });
    }

    public void test_putIntUnsignedAtBit_long_int_int() {
        test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return false; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 31; }
            @Override
            public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);

                    LangUtils.azzert(value == (int)value);
                }

                final long previousBitPosition = tab.buffer.bitPosition();
                assertSame(tab.buffer, tab.buffer.putIntUnsignedAtBit(firstBitPos, (int)value, bitSize));
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
            }
        });
    }

    public void test_putLongUnsignedAtBit_long_long_int() {
        test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return false; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 63; }
            @Override
            public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                }

                final long previousBitPosition = tab.buffer.bitPosition();
                assertSame(tab.buffer, tab.buffer.putLongUnsignedAtBit(firstBitPos, value, bitSize));
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
            }
        });
    }

    /*
     * 
     */

    public void test_putIntSigned_int_int() {
        final AtomicBoolean didThrow_BufferOverflowException = new AtomicBoolean();
        test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return true; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 32; }
            @Override
            public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                    tab.myBufferBitPosition(firstBitPos);

                    LangUtils.azzert(value == (int)value);
                }

                DataBuffer result = null;
                try {
                    result = tab.buffer.putIntSigned((int)value, bitSize);
                } catch (BufferOverflowException e) {
                    didThrow_BufferOverflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertSame(tab.buffer, result);
                assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
            }
        });
        assertTrue(didThrow_BufferOverflowException.get());
    }

    public void test_putLongSigned_long_int() {
        final AtomicBoolean didThrow_BufferOverflowException = new AtomicBoolean();
        test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return true; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 64; }
            @Override
            public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                    tab.myBufferBitPosition(firstBitPos);
                }

                DataBuffer result = null;
                try {
                    result = tab.buffer.putLongSigned(value, bitSize);
                } catch (BufferOverflowException e) {
                    didThrow_BufferOverflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertSame(tab.buffer, result);
                assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
            }
        });
        assertTrue(didThrow_BufferOverflowException.get());
    }

    public void test_putIntUnsigned_int_int() {
        final AtomicBoolean didThrow_BufferOverflowException = new AtomicBoolean();
        test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return false; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 31; }
            @Override
            public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                    tab.myBufferBitPosition(firstBitPos);

                    LangUtils.azzert(value == (int)value);
                }

                DataBuffer result = null;
                try {
                    result = tab.buffer.putIntUnsigned((int)value, bitSize);
                } catch (BufferOverflowException e) {
                    didThrow_BufferOverflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertSame(tab.buffer, result);
                assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
            }
        });
        assertTrue(didThrow_BufferOverflowException.get());
    }

    public void test_putLongUnsigned_long_int() {
        final AtomicBoolean didThrow_BufferOverflowException = new AtomicBoolean();
        test_putLongAtBitOperation(new InterfacePutLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return false; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 63; }
            @Override
            public void putLongAtBit(MyTab tab, long firstBitPos, long value, int bitSize) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                    tab.myBufferBitPosition(firstBitPos);
                }

                DataBuffer result = null;
                try {
                    result = tab.buffer.putLongUnsigned(value, bitSize);
                } catch (BufferOverflowException e) {
                    didThrow_BufferOverflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertSame(tab.buffer, result);
                assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
            }
        });
        assertTrue(didThrow_BufferOverflowException.get());
    }

    /*
     * 
     */

    public void test_getIntSignedAtBit_long_int() {
        test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return true; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 32; }
            @Override
            public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                tab.myBufferOrder(tab.order);
                final long previousBitPosition = tab.buffer.bitPosition();
                final long result = tab.buffer.getIntSignedAtBit(firstBitPos, bitSize);
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
                return result;
            }
        });
    }

    public void test_getLongSignedAtBit_long_int() {
        test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return true; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 64; }
            @Override
            public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                tab.myBufferOrder(tab.order);
                final long previousBitPosition = tab.buffer.bitPosition();
                final long result = tab.buffer.getLongSignedAtBit(firstBitPos, bitSize);
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
                return result;
            }
        });
    }

    public void test_getIntUnsignedAtBit_long_int() {
        test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return false; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 31; }
            @Override
            public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                tab.myBufferOrder(tab.order);
                final long previousBitPosition = tab.buffer.bitPosition();
                final long result = tab.buffer.getIntUnsignedAtBit(firstBitPos, bitSize);
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
                return result;
            }
        });
    }

    public void test_getLongUnsignedAtBit_long_int() {
        test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return false; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 63; }
            @Override
            public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                tab.myBufferOrder(tab.order);
                final long previousBitPosition = tab.buffer.bitPosition();
                final long result = tab.buffer.getLongUnsignedAtBit(firstBitPos, bitSize);
                assertEquals(previousBitPosition,tab.buffer.bitPosition());
                return result;
            }
        });
    }

    /*
     * 
     */

    public void test_getIntSigned_long_int() {
        final AtomicBoolean didThrow_BufferUnderflowException = new AtomicBoolean();
        test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return true; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 32; }
            @Override
            public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                tab.myBufferOrder(tab.order);
                tab.myBufferBitPosition(firstBitPos);
                
                long result = 0;
                try {
                    result = tab.buffer.getIntSigned(bitSize);
                } catch (BufferUnderflowException e) {
                    didThrow_BufferUnderflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
                return result;
            }
        });
        assertTrue(didThrow_BufferUnderflowException.get());
    }

    public void test_getLongSigned_long_int() {
        final AtomicBoolean didThrow_BufferUnderflowException = new AtomicBoolean();
        test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return true; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 64; }
            @Override
            public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                tab.myBufferOrder(tab.order);
                tab.myBufferBitPosition(firstBitPos);
                
                long result = 0;
                try {
                    result = tab.buffer.getLongSigned(bitSize);
                } catch (BufferUnderflowException e) {
                    didThrow_BufferUnderflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
                return result;
            }
        });
        assertTrue(didThrow_BufferUnderflowException.get());
    }

    public void test_getIntUnsigned_long_int() {
        final AtomicBoolean didThrow_BufferUnderflowException = new AtomicBoolean();
        test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return false; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 31; }
            @Override
            public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                tab.myBufferOrder(tab.order);
                tab.myBufferBitPosition(firstBitPos);
                
                long result = 0;
                try {
                    result = tab.buffer.getIntUnsigned(bitSize);
                } catch (BufferUnderflowException e) {
                    didThrow_BufferUnderflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
                return result;
            }
        });
        assertTrue(didThrow_BufferUnderflowException.get());
    }

    public void test_getLongUnsigned_long_int() {
        final AtomicBoolean didThrow_BufferUnderflowException = new AtomicBoolean();
        test_getLongAtBitOperation(new InterfaceGetLongAtBitOperation<MyTab>() {
            @Override
            public boolean isSigned() { return false; }
            @Override
            public boolean isPosBitwise() { return true; }
            @Override
            public boolean isSizeBitwise() { return true; }
            @Override
            public int getMinBitSize() { return 1; }
            @Override
            public int getMaxBitSize() { return 63; }
            @Override
            public long getLongAtBit(MyTab tab, long firstBitPos, int bitSize) {
                tab.myBufferOrder(tab.order);
                tab.myBufferBitPosition(firstBitPos);
                
                long result = 0;
                try {
                    result = tab.buffer.getLongUnsigned(bitSize);
                } catch (BufferUnderflowException e) {
                    didThrow_BufferUnderflowException.set(true);
                    throw new IndexOutOfBoundsException();
                }
                assertEquals(firstBitPos+bitSize,tab.buffer.bitPosition());
                return result;
            }
        });
        assertTrue(didThrow_BufferUnderflowException.get());
    }

    /*
     * 
     */

    public void test_toString() {
        for (DataBuffer db : variousDB()) {
            final int capacity = db.capacity();
            for (int limit=0;limit<=capacity;limit++) {
                db.limit(limit);
                final long bitLimit = limit * 8L;
                for (long bitPos=0;bitPos<=bitLimit;bitPos++) {
                    db.bitPosition(bitPos);
                    final String expected =
                            db.getClass().getName()
                            +"[bit pos="+bitPos
                            +" bit lim="+bitLimit
                            +" bit cap="+db.bitCapacity()+"]";
                    assertEquals(expected,db.toString());
                }
            }
        }
    }

    /*
     * 
     */

    public void test_bufferCopy_DataBuffer_int_DataBuffer_int_int() {
        test_copyBytesOperation(new InterfaceCopyBytesOperation<MyTab>() {
            @Override
            public void copyBytes(MyTab src, int srcFirstByteIndex, MyTab dst, int dstFirstByteIndex, int byteSize) {
                src.myBufferOrder(src.order);
                dst.myBufferOrder(dst.order);
                DataBuffer.bufferCopy(src.buffer, srcFirstByteIndex, dst.buffer, dstFirstByteIndex, byteSize);
            }
        });
    }

    public void test_bufferCopyBits_DataBuffer_long_DataBuffer_long_long() {
        test_copyBitsOperation(new InterfaceCopyBitsOperation<MyTab>() {
            @Override
            public void copyBits(MyTab src, long srcFirstBitPos, MyTab dst, long dstFirstBitPos, long bitSize) {
                src.myBufferOrder(src.order);
                dst.myBufferOrder(dst.order);
                DataBuffer.bufferCopyBits(src.buffer, srcFirstBitPos, dst.buffer, dstFirstBitPos, bitSize);
            }
        });
    }

    /*
     * 
     */

    public void test_toString_int_int_int() {
        test_toStringOperation(new InterfaceToStringOperation<MyTab>() {
            @Override
            public String toString(MyTab tab, int firstByteIndex, int byteSize, int radix) {
                return tab.buffer.toString(firstByteIndex, byteSize, radix);
            }
        });
    }

    public void test_toStringBits_long_long_boolean() {
        test_toStringBitsOperation(new InterfaceToStringBitsOperation<MyTab>() {
            @Override
            public String toStringBits(MyTab tab, long firstBitPos, long bitSize, boolean bigEndian) {
                return tab.buffer.toStringBits(firstBitPos, bitSize, bigEndian);
            }
        });
    }

    /*
     * 
     */

    public void test_toStringBinaryBytes() {
        for (DataBuffer db : variousDB()) {
            assertEquals(db.toString(0, db.limit(), 2), db.toStringBinaryBytes());
        }
    }

    public void test_toStringOctalBytes() {
        for (DataBuffer db : variousDB()) {
            assertEquals(db.toString(0, db.limit(), 8), db.toStringOctalBytes());
        }
    }

    public void test_toStringDecimalBytes() {
        for (DataBuffer db : variousDB()) {
            assertEquals(db.toString(0, db.limit(), 10), db.toStringDecimalBytes());
        }
    }

    public void test_toStringHexadecimalBytes() {
        for (DataBuffer db : variousDB()) {
            assertEquals(db.toString(0, db.limit(), 16), db.toStringHexadecimalBytes());
        }
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------

    @Override
    protected InterfaceTab[] newTabs(int limit, InterfaceTab... with) {
        final ArrayList<InterfaceTab> tabs = new ArrayList<InterfaceTab>();

        for (DataBuffer db : variousDB(limit)) {
            tabs.add(new MyTab(db));
        }

        /*
         * additional tabs
         */

        if (with != null) {
            for (InterfaceTab tab : with) {
                tabs.add(tab);
            }
        }

        /*
         * 
         */

        return tabs.toArray(new InterfaceTab[tabs.size()]);
    }

    @Override
    protected InterfaceTab newSharingTab(InterfaceTab tab, int targetRelativeOffset) {
        final MyTab bat = (MyTab)tab;

        // DataBuffer's duplicate preserves order.
        DataBuffer buffer = bat.buffer.duplicate();
        final int relativeOffset = Math.min(targetRelativeOffset, buffer.limit());
        if (relativeOffset != 0) {
            buffer.bitPosition(relativeOffset * 8L);
            // DataBuffer's slice preserves order.
            buffer = buffer.slice();
        }

        return new MyTab(buffer);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private static void assertEqualsSignum(int a, int b) {
        assertEquals(NumbersUtils.signum(a), NumbersUtils.signum(b));
    }

    private static void assertNoMark(DataBuffer db) {
        try {
            db.reset();
            assertTrue(false);
        } catch (InvalidMarkException e) {
            // ok
        }
    }

    /*
     * 
     */

    private byte[] fillRandom(byte[] buffer) {
        for (int i=0;i<buffer.length;i++) {
            buffer[i] = (byte)random.nextInt();
        }
        return buffer;
    }

    private ByteBuffer fillRandom(ByteBuffer buffer) {
        final int limit = buffer.limit();
        buffer.limit(buffer.capacity());
        for (int i=0;i<buffer.limit();i++) {
            buffer.put(i,(byte)random.nextInt());
        }
        buffer.limit(limit);
        return buffer;
    }

    private DataBuffer fillRandom(DataBuffer buffer) {
        final int limit = buffer.limit();
        buffer.limit(buffer.capacity());
        for (int i=0;i<buffer.limit();i++) {
            buffer.putByteAt(i,(byte)random.nextInt());
        }
        buffer.limit(limit);
        return buffer;
    }

    private DataBuffer fillZero(DataBuffer buffer) {
        final int limit = buffer.limit();
        buffer.limit(buffer.capacity());
        for (int i=0;i<buffer.limit();i++) {
            buffer.putByteAt(i,(byte)0);
        }
        buffer.limit(limit);
        return buffer;
    }

    /*
     * 
     */

    /**
     * @return A new byte array containing the same bytes than the specified
     *         byte array.
     */
    private byte[] cloneContent(byte[] ba) {
        byte[] clone = new byte[ba.length];
        System.arraycopy(ba, 0, clone, 0, ba.length);
        return clone;
    }

    /**
     * @return A new byte array containing the same bytes than the specified
     *         DataBuffer in [0,capacity-1] byte range.
     */
    private byte[] cloneContentUpToCapacity(DataBuffer db) {
        byte[] clone = new byte[db.capacity()];
        final int limit = db.limit();
        db.limit(db.capacity());
        for (int i=0;i<clone.length;i++) {
            clone[i] = db.getByteAt(i);
        }
        db.limit(limit);
        return clone;
    }

    /**
     * @return A ByteBuffer of same kind (read-only or not, direct or not),
     *         with same capacity, limit, order, position set with positionInf(),
     *         and no mark.
     */
    private ByteBuffer cloneByteBuffer(DataBuffer db) {
        ByteBuffer bb;
        if (db.isDirect()) {
            bb = ByteBuffer.allocateDirect(db.capacity());
        } else {
            bb = ByteBuffer.allocate(db.capacity());
        }

        // Copying all bytes.
        final long bitPosition = db.bitPosition();
        final int limit = db.limit();
        db.bitPosition(0L);
        db.limit(db.capacity());
        for (int i=0;i<db.capacity();i++) {
            bb.put(i,db.getByteAt(i));
        }
        db.bitPosition(bitPosition);
        db.limit(limit);

        if (db.isDirect()) {
            bb = bb.asReadOnlyBuffer();
        }

        bb.limit(db.limit());
        bb.position(db.positionInf());
        bb.order(db.order());

        return bb;
    }

    /*
     * 
     */

    private ByteBuffer[] variousBB() {
        return variousBB(DEFAULT_LIMIT);
    }

    /**
     * @return Various ByteBuffers with non-zero offset and
     *         limit-to-capacity, various order, random content,
     *         position at 0, and no mark.
     */
    private ByteBuffer[] variousBB(int limit) {
        ArrayList<ByteBuffer> bbs = new ArrayList<ByteBuffer>();
        
        // To make sure limit is used, and not capacity.
        final int limitToCapacity = 2;
        // To make sure offset is used.
        final int offset = 3;

        final int capacity = limit + limitToCapacity;
        final int arrayCapacity = offset + capacity;
        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN}) {
            ArrayList<ByteBuffer> localBBs = new ArrayList<ByteBuffer>();

            localBBs.add(fillRandom(ByteBuffer.allocate(arrayCapacity)));
            localBBs.add(fillRandom(ByteBuffer.allocateDirect(arrayCapacity)));
            localBBs.add(fillRandom(ByteBuffer.allocate(arrayCapacity)).asReadOnlyBuffer());
            localBBs.add(fillRandom(ByteBuffer.allocateDirect(arrayCapacity)).asReadOnlyBuffer());
            if (offset != 0) {
                for (int i=0;i<localBBs.size();i++) {
                    ByteBuffer bb = localBBs.get(i);
                    bb.position(offset);
                    bb = bb.slice();
                    localBBs.set(i, bb);
                }
            }

            if (limit != capacity) {
                for (ByteBuffer bb : localBBs) {
                    bb.limit(limit);
                }
            }

            for (ByteBuffer bb : localBBs) {
                bb.order(order);
            }

            bbs.addAll(localBBs);
        }

        for (ByteBuffer bb : bbs) {
            LangUtils.azzert(bb.limit() == limit);
        }

        return bbs.toArray(new ByteBuffer[bbs.size()]);
    }

    /*
     * 
     */

    private DataBuffer[] variousDB() {
        return variousDB(DEFAULT_LIMIT);
    }

    /**
     * @return Various DataBuffers with non-zero offset and
     *         limit-to-capacity, various order, random content,
     *         position at 0, and no mark.
     */
    private DataBuffer[] variousDB(int limit) {
        ArrayList<DataBuffer> dbs = new ArrayList<DataBuffer>();

        /*
         * byte[]-backed DataBuffers
         */
        
        // To make sure limit is used, and not capacity.
        final int limitToCapacity = 2;
        // To make sure offset is used.
        final int offset = 3;
        // To hopefully generate trouble if array capacity is used
        // somewhere instead of buffer capacity.
        // This is only relevant for byte[]-backed DataBuffers, since
        // ByteBuffers always use last array byte as bound for their capacity.
        final int capacityToArrayCapacity = 5;

        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN}) {
            final int capacity = limit + limitToCapacity;
            ArrayList<DataBuffer> localDBs = new ArrayList<DataBuffer>();

            final int arrayCapacity = offset + capacity + capacityToArrayCapacity;

            localDBs.add(DataBuffer.newInstance(fillRandom(new byte[arrayCapacity]), offset, capacity));
            localDBs.add(DataBuffer.newInstance(fillRandom(new byte[arrayCapacity]), offset, capacity).asReadOnlyBuffer());

            if (limit != capacity) {
                for (DataBuffer db : localDBs) {
                    db.limit(limit);
                }
            }

            for (DataBuffer db : localDBs) {
                db.order(order);
            }

            dbs.addAll(localDBs);
        }

        /*
         * ByteBuffer-backed DataBuffers
         */

        for (ByteBuffer bb : variousBB(limit)) {
            DataBuffer db = DataBuffer.newInstance(bb);
            db.limit(limit);
            dbs.add(db);
        }

        /*
         * 
         */

        for (DataBuffer db : dbs) {
            LangUtils.azzert(db.limit() == limit);
        }

        return dbs.toArray(new DataBuffer[dbs.size()]);
    }
    
    /*
     * 
     */

    /**
     * Just a wrapper on newTabPairs(int,int),
     * see this method for info.
     */
    public DataBuffer[][] variousDBPairs(int limit1, int limit2) {
        ArrayList<DataBuffer[]> pairs = new ArrayList<DataBuffer[]>();
        for (InterfaceTab[] tabPair : newTabPairs(limit1,limit2)) {
            DataBuffer db1 = ((MyTab)tabPair[0]).buffer;
            DataBuffer db2 = ((MyTab)tabPair[1]).buffer;
            final DataBuffer[] pair = new DataBuffer[tabPair.length];
            pair[0] = db1;
            pair[1] = db2;
            pairs.add(pair);
        }
        return pairs.toArray(new DataBuffer[pairs.size()][]);
    }
}
