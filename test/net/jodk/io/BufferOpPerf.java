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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import net.jodk.test.TestUtils;

/**
 * Performance test for operations on buffers
 * (ByteArrayUtils,ByteBufferUtils,DataBuffer).
 */
public class BufferOpPerf {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final int NBR_OF_CALLS = 10 * 1000 * 1000;
    private static final int NBR_OF_VALUES = 10 * 1000;

    private static final int CAPACITY_FOR_PUT_GET_BENCH = 100;

    // Different masks, to use about same memory range.
    private static final int MASK_FOR_AT_BIT = 0xFF;
    private static final int MASK_FOR_AT = (MASK_FOR_AT_BIT>>>3);

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private interface MyInterfaceBenchable<T> {
        public T newBuffer(int capacity);
        public void order(T buffer, ByteOrder order);
        /**
         * @throws UnsupportedOperationException if the specified buffer
         *         doesn't hold a position, or a bitwise position.
         */
        public void bitPosition(T buffer, long bitPosition);
        /*
         * Using buffers as class members, to avoid casts if used as parameters.
         * buffer1: user for operations involving a single buffer
         * buffer2: used with buffer1 for operations involving two buffers
         */
        public void setBuffer1(T buffer);
        public void setBuffer2(T buffer);
        public T getBuffer1();
        public T getBuffer2();
        /*
         * Unsupported operations must throw UnsupportedOperationException.
         * Non-at methods are to be used along with bitPosition(long) method.
         */
        public void putByteAt(int index, byte value);
        public void putIntAt(int index, int value);
        public void putLongAt(int index, long value);
        public byte getByteAt(int index);
        public int getIntAt(int index);
        public long getLongAt(int index);
        
        public void putByte(byte value);
        public void putInt(int value);
        public void putLong(long value);
        public byte getByte();
        public int getInt();
        public long getLong();
        
        public void putBitAtBit(long bitPosition, boolean value);
        public void putBit(boolean value);
        public boolean getBitAtBit(long bitPosition);
        public boolean getBit();
        
        public void putIntSignedAtBit(long firstBitPos, int value, int bitSize);
        public void putLongSignedAtBit(long firstBitPos, long value, int bitSize);
        public void putIntUnsignedAtBit(long firstBitPos, int value, int bitSize);
        public void putLongUnsignedAtBit(long firstBitPos, long value, int bitSize);
        public int getIntSignedAtBit(long firstBitPos, int bitSize);
        public long getLongSignedAtBit(long firstBitPos, int bitSize);
        public int getIntUnsignedAtBit(long firstBitPos, int bitSize);
        public long getLongUnsignedAtBit(long firstBitPos, int bitSize);
        /**
         * src and dst must have same order.
         */
        public void bufferCopy(int srcFirstByteIndex, int dstFirstByteIndex, int byteSize);
        public void bufferCopyBits(long srcFirstBitPos, long dstFirstBitPos, long bitSize);
    }
    
    /**
     * Hack to separate JDK and JODK stuffs.
     */
    private static MyInterfaceBenchable<?> SEPARATOR_BENCHABLE = new MyBABenchable();

    /**
     * For JDK's operations on byte arrays.
     */
    private static class MyBABenchable implements MyInterfaceBenchable<byte[]> {
        private final String string;
        private byte[] buffer1;
        private byte[] buffer2;
        public MyBABenchable() {
            this.string = "byte[]";
        }
        @Override
        public String toString() {
            return this.string;
        }
        @Override
        public byte[] newBuffer(int capacity) {
            return new byte[capacity];
        }
        @Override
        public void order(byte[] buffer, ByteOrder order) {
        }
        @Override
        public void bitPosition(byte[] buffer, long bitPosition) {
            throw new UnsupportedOperationException();
        }
        /*
         * 
         */
        @Override
        public void setBuffer1(byte[] buffer) {
            this.buffer1 = buffer;
        }
        @Override
        public void setBuffer2(byte[] buffer) {
            this.buffer2 = buffer;
        }
        @Override
        public byte[] getBuffer1() {
            return this.buffer1;
        }
        @Override
        public byte[] getBuffer2() {
            return this.buffer2;
        }
        /*
         * 
         */
        @Override
        public void putByteAt(int index, byte value) {
            this.buffer1[index] = value;
        }
        @Override
        public void putIntAt(int index, int value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putLongAt(int index, long value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public byte getByteAt(int index) {
            return this.buffer1[index];
        }
        @Override
        public int getIntAt(int index) {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getLongAt(int index) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putByte(byte value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putInt(int value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putLong(long value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public byte getByte() {
            throw new UnsupportedOperationException();
        }
        @Override
        public int getInt() {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getLong() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putBitAtBit(long bitPosition, boolean value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putBit(boolean value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean getBitAtBit(long bitPosition) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean getBit() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putIntSignedAtBit(long firstBitPos, int value, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putLongSignedAtBit(long firstBitPos, long value, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putIntUnsignedAtBit(long firstBitPos, int value, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putLongUnsignedAtBit(long firstBitPos, long value, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public int getIntSignedAtBit(long firstBitPos, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getLongSignedAtBit(long firstBitPos, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public int getIntUnsignedAtBit(long firstBitPos, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getLongUnsignedAtBit(long firstBitPos, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void bufferCopy(int srcFirstByteIndex, int dstFirstByteIndex, int byteSize) {
            System.arraycopy(this.buffer1, srcFirstByteIndex, this.buffer2, dstFirstByteIndex, byteSize);
        }
        @Override
        public void bufferCopyBits(long srcFirstBitPos, long dstFirstBitPos, long bitSize) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * For ByteArrayUtils operations.
     */
    private static class MyBAUtilsBenchable implements MyInterfaceBenchable<MyBAUtilsBenchable.MyBuffer> {
        static class MyBuffer {
            private final byte[] array;
            private ByteOrder order = ByteOrder.BIG_ENDIAN;
            public MyBuffer(int capacity) {
                this.array = new byte[capacity];
            }
        }
        private final String string;
        private MyBuffer buffer1;
        private MyBuffer buffer2;
        public MyBAUtilsBenchable() {
            this.string = "ByteArrayUtils";
        }
        @Override
        public String toString() {
            return this.string;
        }
        @Override
        public MyBuffer newBuffer(int capacity) {
            return new MyBuffer(capacity);
        }
        @Override
        public void order(MyBuffer buffer, ByteOrder order) {
            buffer.order = order;
        }
        @Override
        public void bitPosition(MyBuffer buffer, long bitPosition) {
            throw new UnsupportedOperationException();
        }
        /*
         * 
         */
        @Override
        public void setBuffer1(MyBuffer buffer) {
            this.buffer1 = buffer;
        }
        @Override
        public void setBuffer2(MyBuffer buffer) {
            this.buffer2 = buffer;
        }
        @Override
        public MyBuffer getBuffer1() {
            return this.buffer1;
        }
        @Override
        public MyBuffer getBuffer2() {
            return this.buffer2;
        }
        /*
         * 
         */
        @Override
        public void putByteAt(int index, byte value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putIntAt(int index, int value) {
            ByteArrayUtils.putIntAt(this.buffer1.array, index, value, this.buffer1.order);
        }
        @Override
        public void putLongAt(int index, long value) {
            ByteArrayUtils.putLongAt(this.buffer1.array, index, value, this.buffer1.order);
        }
        @Override
        public byte getByteAt(int index) {
            throw new UnsupportedOperationException();
        }
        @Override
        public int getIntAt(int index) {
            return ByteArrayUtils.getIntAt(this.buffer1.array, index, this.buffer1.order);
        }
        @Override
        public long getLongAt(int index) {
            return ByteArrayUtils.getLongAt(this.buffer1.array, index, this.buffer1.order);
        }
        @Override
        public void putByte(byte value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putInt(int value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putLong(long value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public byte getByte() {
            throw new UnsupportedOperationException();
        }
        @Override
        public int getInt() {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getLong() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putBitAtBit(long bitPosition, boolean value) {
            ByteArrayUtils.putBitAtBit(this.buffer1.array, bitPosition, value, this.buffer1.order);
        }
        @Override
        public void putBit(boolean value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean getBitAtBit(long bitPosition) {
            return ByteArrayUtils.getBitAtBit(this.buffer1.array, bitPosition, this.buffer1.order);
        }
        @Override
        public boolean getBit() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putIntSignedAtBit(long firstBitPos, int value, int bitSize) {
            ByteArrayUtils.putIntSignedAtBit(this.buffer1.array, firstBitPos, value, bitSize, this.buffer1.order);
        }
        @Override
        public void putLongSignedAtBit(long firstBitPos, long value, int bitSize) {
            ByteArrayUtils.putLongSignedAtBit(this.buffer1.array, firstBitPos, value, bitSize, this.buffer1.order);
        }
        @Override
        public void putIntUnsignedAtBit(long firstBitPos, int value, int bitSize) {
            ByteArrayUtils.putIntUnsignedAtBit(this.buffer1.array, firstBitPos, value, bitSize, this.buffer1.order);
        }
        @Override
        public void putLongUnsignedAtBit(long firstBitPos, long value, int bitSize) {
            ByteArrayUtils.putLongUnsignedAtBit(this.buffer1.array, firstBitPos, value, bitSize, this.buffer1.order);
        }
        @Override
        public int getIntSignedAtBit(long firstBitPos, int bitSize) {
            return ByteArrayUtils.getIntSignedAtBit(this.buffer1.array, firstBitPos, bitSize, this.buffer1.order);
        }
        @Override
        public long getLongSignedAtBit(long firstBitPos, int bitSize) {
            return ByteArrayUtils.getLongSignedAtBit(this.buffer1.array, firstBitPos, bitSize, this.buffer1.order);
        }
        @Override
        public int getIntUnsignedAtBit(long firstBitPos, int bitSize) {
            return ByteArrayUtils.getIntUnsignedAtBit(this.buffer1.array, firstBitPos, bitSize, this.buffer1.order);
        }
        @Override
        public long getLongUnsignedAtBit(long firstBitPos, int bitSize) {
            return ByteArrayUtils.getLongUnsignedAtBit(this.buffer1.array, firstBitPos, bitSize, this.buffer1.order);
        }
        @Override
        public void bufferCopy(int srcFirstByteIndex, int dstFirstByteIndex, int byteSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void bufferCopyBits(long srcFirstBitPos, long dstFirstBitPos, long bitSize) {
            ByteArrayUtils.arrayCopyBits(
                    this.buffer1.array,
                    srcFirstBitPos,
                    this.buffer2.array,
                    dstFirstBitPos,
                    bitSize,
                    this.buffer1.order);
        }
    }

    /**
     * For ByteBufferUtils operations.
     */
    private static abstract class MyAbstractBBUtilsBenchable implements MyInterfaceBenchable<ByteBuffer> {
        private final String string;
        private ByteBuffer buffer1;
        private ByteBuffer buffer2;
        public MyAbstractBBUtilsBenchable(String bbName) {
            this.string = "ByteBufferUtils("+bbName+")";
        }
        @Override
        public String toString() {
            return this.string;
        }
        @Override
        public void order(ByteBuffer buffer, ByteOrder order) {
            buffer.order(order);
        }
        @Override
        public void bitPosition(ByteBuffer buffer, long bitPosition) {
            throw new UnsupportedOperationException();
        }
        /*
         * 
         */
        @Override
        public void setBuffer1(ByteBuffer buffer) {
            this.buffer1 = buffer;
        }
        @Override
        public void setBuffer2(ByteBuffer buffer) {
            this.buffer2 = buffer;
        }
        @Override
        public ByteBuffer getBuffer1() {
            return this.buffer1;
        }
        @Override
        public ByteBuffer getBuffer2() {
            return this.buffer2;
        }
        /*
         * 
         */
        @Override
        public void putByteAt(int index, byte value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putIntAt(int index, int value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putLongAt(int index, long value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public byte getByteAt(int index) {
            throw new UnsupportedOperationException();
        }
        @Override
        public int getIntAt(int index) {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getLongAt(int index) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putByte(byte value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putInt(int value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putLong(long value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public byte getByte() {
            throw new UnsupportedOperationException();
        }
        @Override
        public int getInt() {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getLong() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putBitAtBit(long bitPosition, boolean value) {
            ByteBufferUtils.putBitAtBit(this.buffer1, bitPosition, value);
        }
        @Override
        public void putBit(boolean value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean getBitAtBit(long bitPosition) {
            return ByteBufferUtils.getBitAtBit(this.buffer1, bitPosition);
        }
        @Override
        public boolean getBit() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putIntSignedAtBit(long firstBitPos, int value, int bitSize) {
            ByteBufferUtils.putIntSignedAtBit(this.buffer1, firstBitPos, value, bitSize);
        }
        @Override
        public void putLongSignedAtBit(long firstBitPos, long value, int bitSize) {
            ByteBufferUtils.putLongSignedAtBit(this.buffer1, firstBitPos, value, bitSize);
        }
        @Override
        public void putIntUnsignedAtBit(long firstBitPos, int value, int bitSize) {
            ByteBufferUtils.putIntUnsignedAtBit(this.buffer1, firstBitPos, value, bitSize);
        }
        @Override
        public void putLongUnsignedAtBit(long firstBitPos, long value, int bitSize) {
            ByteBufferUtils.putLongUnsignedAtBit(this.buffer1, firstBitPos, value, bitSize);
        }
        @Override
        public int getIntSignedAtBit(long firstBitPos, int bitSize) {
            return ByteBufferUtils.getIntSignedAtBit(this.buffer1, firstBitPos, bitSize);
        }
        @Override
        public long getLongSignedAtBit(long firstBitPos, int bitSize) {
            return ByteBufferUtils.getLongSignedAtBit(this.buffer1, firstBitPos, bitSize);
        }
        @Override
        public int getIntUnsignedAtBit(long firstBitPos, int bitSize) {
            return ByteBufferUtils.getIntUnsignedAtBit(this.buffer1, firstBitPos, bitSize);
        }
        @Override
        public long getLongUnsignedAtBit(long firstBitPos, int bitSize) {
            return ByteBufferUtils.getLongUnsignedAtBit(this.buffer1, firstBitPos, bitSize);
        }
        @Override
        public void bufferCopy(int srcFirstByteIndex, int dstFirstByteIndex, int byteSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void bufferCopyBits(long srcFirstBitPos, long dstFirstBitPos, long bitSize) {
            ByteBufferUtils.bufferCopyBits(
                    this.buffer1,
                    srcFirstBitPos,
                    this.buffer2,
                    dstFirstBitPos,
                    bitSize);
        }
    }

    /**
     * For ByteBuffer operations.
     */
    private static abstract class MyAbstractBBBenchable implements MyInterfaceBenchable<ByteBuffer> {
        private final String string;
        private ByteBuffer buffer1;
        private ByteBuffer buffer2;
        public MyAbstractBBBenchable(final String string) {
            this.string = string;
        }
        @Override
        public String toString() {
            return this.string;
        }
        @Override
        public void order(ByteBuffer buffer, ByteOrder order) {
            buffer.order(order);
        }
        @Override
        public void bitPosition(ByteBuffer buffer, long bitPosition) {
            if ((bitPosition&7) == 0) {
                buffer.position((int)(bitPosition>>3));
            } else {
                throw new UnsupportedOperationException();
            }
        }
        /*
         * 
         */
        @Override
        public void setBuffer1(ByteBuffer buffer) {
            this.buffer1 = buffer;
        }
        @Override
        public void setBuffer2(ByteBuffer buffer) {
            this.buffer2 = buffer;
        }
        @Override
        public ByteBuffer getBuffer1() {
            return this.buffer1;
        }
        @Override
        public ByteBuffer getBuffer2() {
            return this.buffer2;
        }
        /*
         * 
         */
        @Override
        public void putByteAt(int index, byte value) {
            this.buffer1.put(index, value);
        }
        @Override
        public void putIntAt(int index, int value) {
            this.buffer1.putInt(index, value);
        }
        @Override
        public void putLongAt(int index, long value) {
            this.buffer1.putLong(index, value);
        }
        @Override
        public byte getByteAt(int index) {
            return this.buffer1.get(index);
        }
        @Override
        public int getIntAt(int index) {
            return this.buffer1.getInt(index);
        }
        @Override
        public long getLongAt(int index) {
            return this.buffer1.getLong(index);
        }
        @Override
        public void putByte(byte value) {
            this.buffer1.put(value);
        }
        @Override
        public void putInt(int value) {
            this.buffer1.putInt(value);
        }
        @Override
        public void putLong(long value) {
            this.buffer1.putLong(value);
        }
        @Override
        public byte getByte() {
            return this.buffer1.get();
        }
        @Override
        public int getInt() {
            return this.buffer1.getInt();
        }
        @Override
        public long getLong() {
            return this.buffer1.getLong();
        }
        @Override
        public void putBitAtBit(long bitPosition, boolean value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putBit(boolean value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean getBitAtBit(long bitPosition) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean getBit() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putIntSignedAtBit(long firstBitPos, int value, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putLongSignedAtBit(long firstBitPos, long value, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putIntUnsignedAtBit(long firstBitPos, int value, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void putLongUnsignedAtBit(long firstBitPos, long value, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public int getIntSignedAtBit(long firstBitPos, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getLongSignedAtBit(long firstBitPos, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public int getIntUnsignedAtBit(long firstBitPos, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getLongUnsignedAtBit(long firstBitPos, int bitSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void bufferCopy(int srcFirstByteIndex, int dstFirstByteIndex, int byteSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void bufferCopyBits(long srcFirstBitPos, long dstFirstBitPos, long bitSize) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * For DataBuffer operations.
     */
    private static abstract class MyAbstractDBBenchable implements MyInterfaceBenchable<DataBuffer> {
        private final String string;
        private DataBuffer buffer1;
        private DataBuffer buffer2;
        public MyAbstractDBBenchable(String dbName) {
            this.string = "DataBuffer("+dbName+")";
        }
        @Override
        public String toString() {
            return this.string;
        }
        @Override
        public void order(DataBuffer buffer, ByteOrder order) {
            buffer.orderIgnoreBitPosition(order);
        }
        @Override
        public void bitPosition(DataBuffer buffer, long bitPosition) {
            buffer.bitPosition(bitPosition);
        }
        /*
         * 
         */
        @Override
        public void setBuffer1(DataBuffer buffer) {
            this.buffer1 = buffer;
        }
        @Override
        public void setBuffer2(DataBuffer buffer) {
            this.buffer2 = buffer;
        }
        @Override
        public DataBuffer getBuffer1() {
            return this.buffer1;
        }
        @Override
        public DataBuffer getBuffer2() {
            return this.buffer2;
        }
        /*
         * 
         */
        @Override
        public void putByteAt(int index, byte value) {
            this.buffer1.putByteAt(index, value);
        }
        @Override
        public void putIntAt(int index, int value) {
            this.buffer1.putIntAt(index, value);
        }
        @Override
        public void putLongAt(int index, long value) {
            this.buffer1.putLongAt(index, value);
        }
        @Override
        public byte getByteAt(int index) {
            return this.buffer1.getByteAt(index);
        }
        @Override
        public int getIntAt(int index) {
            return this.buffer1.getIntAt(index);
        }
        @Override
        public long getLongAt(int index) {
            return this.buffer1.getLongAt(index);
        }
        @Override
        public void putByte(byte value) {
            this.buffer1.putByte(value);
        }
        @Override
        public void putInt(int value) {
            this.buffer1.putInt(value);
        }
        @Override
        public void putLong(long value) {
            this.buffer1.putLong(value);
        }
        @Override
        public byte getByte() {
            return this.buffer1.getByte();
        }
        @Override
        public int getInt() {
            return this.buffer1.getInt();
        }
        @Override
        public long getLong() {
            return this.buffer1.getLong();
        }
        @Override
        public void putBitAtBit(long bitPosition, boolean value) {
            this.buffer1.putBitAtBit(bitPosition,value);
        }
        @Override
        public void putBit(boolean value) {
            this.buffer1.putBit(value);
        }
        @Override
        public boolean getBitAtBit(long bitPosition) {
            return this.buffer1.getBitAtBit(bitPosition);
        }
        @Override
        public boolean getBit() {
            return this.buffer1.getBit();
        }
        @Override
        public void putIntSignedAtBit(long firstBitPos, int value, int bitSize) {
            this.buffer1.putIntSignedAtBit(firstBitPos, value, bitSize);
        }
        @Override
        public void putLongSignedAtBit(long firstBitPos, long value, int bitSize) {
            this.buffer1.putLongSignedAtBit(firstBitPos, value, bitSize);
        }
        @Override
        public void putIntUnsignedAtBit(long firstBitPos, int value, int bitSize) {
            this.buffer1.putIntUnsignedAtBit(firstBitPos, value, bitSize);
        }
        @Override
        public void putLongUnsignedAtBit(long firstBitPos, long value, int bitSize) {
            this.buffer1.putLongUnsignedAtBit(firstBitPos, value, bitSize);
        }
        @Override
        public int getIntSignedAtBit(long firstBitPos, int bitSize) {
            return this.buffer1.getIntSignedAtBit(firstBitPos, bitSize);
        }
        @Override
        public long getLongSignedAtBit(long firstBitPos, int bitSize) {
            return this.buffer1.getLongSignedAtBit(firstBitPos, bitSize);
        }
        @Override
        public int getIntUnsignedAtBit(long firstBitPos, int bitSize) {
            return this.buffer1.getIntUnsignedAtBit(firstBitPos, bitSize);
        }
        @Override
        public long getLongUnsignedAtBit(long firstBitPos, int bitSize) {
            return this.buffer1.getLongUnsignedAtBit(firstBitPos, bitSize);
        }
        @Override
        public void bufferCopy(int srcFirstByteIndex, int dstFirstByteIndex, int byteSize) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void bufferCopyBits(long srcFirstBitPos, long dstFirstBitPos, long bitSize) {
            DataBuffer.bufferCopyBits(this.buffer1, srcFirstBitPos, this.buffer2, dstFirstBitPos, bitSize);
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final ByteOrder[] ORDERS = new ByteOrder[]{ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN};
    private static final HashMap<ByteOrder, String> PADDING_BY_ORDER = new HashMap<ByteOrder, String>();
    static {
        PADDING_BY_ORDER.put(ByteOrder.BIG_ENDIAN, "   ");
        PADDING_BY_ORDER.put(ByteOrder.LITTLE_ENDIAN, "");
    }

    // all bits random uniform: use >> or >>> to have small or positive values
    private final long[] valuesLong = new long[NBR_OF_VALUES];
    private final int[] valuesInt = new int[NBR_OF_VALUES];
    private final short[] valuesShort = new short[NBR_OF_VALUES];
    private final byte[] valuesByte = new byte[NBR_OF_VALUES];
    private final boolean[] valuesBoolean = new boolean[NBR_OF_VALUES];

    private final int[] int1To63 = new int[NBR_OF_VALUES];
    private final int[] int1To64 = new int[NBR_OF_VALUES];
    private final int[] int1To32 = new int[NBR_OF_VALUES];
    private final int[] int1To16 = new int[NBR_OF_VALUES];
    private final int[] int1To8 = new int[NBR_OF_VALUES];
    private final int[] int1To7 = new int[NBR_OF_VALUES];
    private final long[] firstBitPosTab = new long[NBR_OF_VALUES];
    private final ByteOrder[] byteOrderTab = new ByteOrder[NBR_OF_VALUES];

    private long timerRef;

    private final Random random = new Random(123456789L);

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println(TestUtils.getJVMInfo());
        newRun(args);
    }

    public static void newRun(String[] args) {
        new BufferOpPerf().run(args);
    }

    public BufferOpPerf() {
        this.init();
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private void init() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            valuesLong[i] = random.nextLong();
            valuesInt[i] = random.nextInt();
            valuesShort[i] = (short)random.nextInt();
            valuesByte[i] = (byte)random.nextInt();
            valuesBoolean[i] = random.nextBoolean();
            int1To63[i] = 1+random.nextInt(63);
            int1To64[i] = 1+random.nextInt(64);
            int1To32[i] = 1+random.nextInt(32);
            int1To16[i] = 1+random.nextInt(16);
            int1To8[i] = 1+random.nextInt(8);
            int1To7[i] = 1+random.nextInt(7);
            firstBitPosTab[i] = 1+random.nextInt(16);
            byteOrderTab[i] = random.nextBoolean() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        }
    }

    private void run(String[] args) {
        // XXX
        System.out.println("--- "+BufferOpPerf.class.getSimpleName()+"... ---");
        System.out.println("number of calls = "+NBR_OF_CALLS);

        final ArrayList<MyInterfaceBenchable> benchables = new ArrayList<MyInterfaceBenchable>();
        benchables.add(new MyBABenchable());
        benchables.add(new MyAbstractBBBenchable("HeapByteBuffer") {
            public ByteBuffer newBuffer(int capacity) { return ByteBuffer.allocate(capacity); }
        });
        benchables.add(new MyAbstractBBBenchable("DirectByteBuffer") {
            public ByteBuffer newBuffer(int capacity) { return ByteBuffer.allocateDirect(capacity); }
        });
        benchables.add(SEPARATOR_BENCHABLE);
        benchables.add(new MyBAUtilsBenchable());
        benchables.add(new MyAbstractBBUtilsBenchable("HeapByteBuffer") {
            public ByteBuffer newBuffer(int capacity) { return ByteBuffer.allocate(capacity); }
        });
        // If read-only, ByteBufferUtils doesn't use backing array.
        benchables.add(new MyAbstractBBUtilsBenchable("HeapByteBufferR") {
            public ByteBuffer newBuffer(int capacity) { return ByteBuffer.allocate(capacity).asReadOnlyBuffer(); }
        });
        benchables.add(new MyAbstractBBUtilsBenchable("DirectByteBuffer") {
            public ByteBuffer newBuffer(int capacity) { return ByteBuffer.allocateDirect(capacity); }
        });
        benchables.add(new MyAbstractDBBenchable("byte[]") {
            public DataBuffer newBuffer(int capacity) { return DataBuffer.newInstance(new byte[capacity]); }
        });
        benchables.add(new MyAbstractDBBenchable("HeapByteBuffer") {
            public DataBuffer newBuffer(int capacity) { return DataBuffer.newInstance(ByteBuffer.allocate(capacity)); }
        });
        benchables.add(new MyAbstractDBBenchable("DirectByteBuffer") {
            public DataBuffer newBuffer(int capacity) { return DataBuffer.newInstance(ByteBuffer.allocateDirect(capacity)); }
        });
        
        /*
         * 
         */

        bench_putByteAt(benchables);
        bench_putIntAt(benchables);
        bench_putLongAt(benchables);
        
        bench_getByteAt(benchables);
        bench_getIntAt(benchables);
        bench_getLongAt(benchables);
        
        for (boolean byteAligned : new boolean[]{true,false}) {
            bench_putByte(benchables, byteAligned);
        }
        for (boolean byteAligned : new boolean[]{true,false}) {
            bench_putInt(benchables, byteAligned);
        }
        for (boolean byteAligned : new boolean[]{true,false}) {
            bench_putLong(benchables, byteAligned);
        }
        
        for (boolean byteAligned : new boolean[]{true,false}) {
            bench_getByte(benchables, byteAligned);
        }
        for (boolean byteAligned : new boolean[]{true,false}) {
            bench_getInt(benchables, byteAligned);
        }
        for (boolean byteAligned : new boolean[]{true,false}) {
            bench_getLong(benchables, byteAligned);
        }

        bench_putBitAtBit(benchables);
        bench_putBit(benchables);
        bench_getBitAtBit(benchables);
        bench_getBit(benchables);

        bench_putIntSignedAtBit(benchables);
        bench_putLongSignedAtBit(benchables);
        
        bench_putIntUnsignedAtBit(benchables);
        bench_putLongUnsignedAtBit(benchables);
        
        bench_getIntSignedAtBit(benchables);
        bench_getLongSignedAtBit(benchables);
        
        bench_getIntUnsignedAtBit(benchables);
        bench_getLongUnsignedAtBit(benchables);

        bench_bufferCopy(benchables);
        bench_bufferCopyBits_byteAligned(benchables);
        bench_bufferCopyBits(benchables);

        System.out.println("--- ..."+BufferOpPerf.class.getSimpleName()+" ---");
    }

    /*
     * 
     */
    
    private void startTimer() {
        timerRef = System.nanoTime();
    }

    private double getElapsedSeconds() {
        return TestUtils.nsToSRounded(System.nanoTime() - timerRef);
    }

    private static boolean separator(MyInterfaceBenchable benchable) {
        return (benchable == SEPARATOR_BENCHABLE);
    }

    private static boolean separation(MyInterfaceBenchable benchable) {
        if (separator(benchable)) {
            System.out.println("---");
            return true;
        } else {
            return false;
        }
    }
    
    private static String info_order(ByteOrder order) {
        final String pad = PADDING_BY_ORDER.get(order);
        return "("+order+")"+pad;
    }

    private static String info_byteA_order(boolean byteAligned, ByteOrder order) {
        final String pad = PADDING_BY_ORDER.get(order);
        return "("+(byteAligned ? "byte-aligned, " : "")+order+")"+pad;
    }
    
    private static String info_bitSize_order(int bitSize, ByteOrder order) {
        final String pad = PADDING_BY_ORDER.get(order);
        return "("+bitSize+" bits, "+order+")"+pad;
    }
    
    private static String info_byteA_byteS_order(boolean byteAligned, int byteSize, ByteOrder order) {
        final String pad = PADDING_BY_ORDER.get(order);
        return "("+(byteAligned ? "byte-aligned, " : "")+byteSize+" bytes, "+order+")"+pad;
    }

    /*
     * 
     */

    private void bench_putByteAt(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.putByteAt(0,(byte)0);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            int j=0;
            startTimer();
            for (int i=0;i<NBR_OF_CALLS;i++) {
                benchable.putByteAt(j&MASK_FOR_AT, valuesByte[j]);
                j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
            }
            System.out.println("Loop on "+benchable+".putByteAt(int,byte) took "+getElapsedSeconds()+" s");
        }
    }

    private void bench_putIntAt(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.putIntAt(0,0);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    benchable.putIntAt(j&MASK_FOR_AT, valuesInt[j]);
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                }
                System.out.println("Loop on "+benchable+".putIntAt(int,int) "+info_order(order)+" took "+getElapsedSeconds()+" s");
            }
        }
    }

    private void bench_putLongAt(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.putLongAt(0,0L);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    benchable.putLongAt(j&MASK_FOR_AT, valuesLong[j]);
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                }
                System.out.println("Loop on "+benchable+".putLongAt(int,long) "+info_order(order)+" took "+getElapsedSeconds()+" s");
            }
        }
    }

    private void bench_getByteAt(final List<MyInterfaceBenchable> benchables) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.getByteAt(0);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            int j=0;
            startTimer();
            for (int i=0;i<NBR_OF_CALLS;i++) {
                dummy += benchable.getByteAt(j&MASK_FOR_AT);
                j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
            }
            System.out.println("Loop on "+benchable+".getByteAt(int) took "+getElapsedSeconds()+" s");
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_getIntAt(final List<MyInterfaceBenchable> benchables) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.getIntAt(0);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    dummy += benchable.getIntAt(j&MASK_FOR_AT);
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                }
                System.out.println("Loop on "+benchable+".getIntAt(int) "+info_order(order)+" took "+getElapsedSeconds()+" s");
            }
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_getLongAt(final List<MyInterfaceBenchable> benchables) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.getLongAt(0);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    dummy += benchable.getLongAt(j&MASK_FOR_AT);
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                }
                System.out.println("Loop on "+benchable+".getLongAt(int) "+info_order(order)+" took "+getElapsedSeconds()+" s");
            }
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }
    
    /*
     * 
     */

    private void bench_putByte(final List<MyInterfaceBenchable> benchables, boolean byteAligned) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            final int bitSize = 8;
            final int maxBitPos = CAPACITY_FOR_PUT_GET_BENCH * 8 - bitSize;
            final int initialBitPos = byteAligned ? 0 : 1;
            int bitPos = initialBitPos;
            try {
                benchable.putByte((byte)0);
                benchable.bitPosition(benchable.getBuffer1(),bitPos);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    benchable.putByte(valuesByte[j]);
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    if ((bitPos+=bitSize) > maxBitPos) {
                        bitPos = initialBitPos;
                        benchable.bitPosition(benchable.getBuffer1(),bitPos);
                    }
                }
                System.out.println("Loop on "+benchable+".putByte(byte) "+info_byteA_order(byteAligned,order)+" took "+getElapsedSeconds()+" s");
            }
        }
    }

    private void bench_putInt(final List<MyInterfaceBenchable> benchables, boolean byteAligned) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            final int bitSize = 32;
            final int maxBitPos = CAPACITY_FOR_PUT_GET_BENCH * 8 - bitSize;
            final int initialBitPos = byteAligned ? 0 : 1;
            int bitPos = initialBitPos;
            try {
                benchable.putInt(0);
                benchable.bitPosition(benchable.getBuffer1(),bitPos);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    benchable.putInt(valuesInt[j]);
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    if ((bitPos+=bitSize) > maxBitPos) {
                        bitPos = initialBitPos;
                        benchable.bitPosition(benchable.getBuffer1(),bitPos);
                    }
                }
                System.out.println("Loop on "+benchable+".putInt(int) "+info_byteA_order(byteAligned,order)+" took "+getElapsedSeconds()+" s");
            }
        }
    }

    private void bench_putLong(final List<MyInterfaceBenchable> benchables, boolean byteAligned) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            final int bitSize = 64;
            final int maxBitPos = CAPACITY_FOR_PUT_GET_BENCH * 8 - bitSize;
            final int initialBitPos = byteAligned ? 0 : 1;
            int bitPos = initialBitPos;
            try {
                benchable.putLong(0L);
                benchable.bitPosition(benchable.getBuffer1(),bitPos);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    benchable.putLong(valuesLong[j]);
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    if ((bitPos+=bitSize) > maxBitPos) {
                        bitPos = initialBitPos;
                        benchable.bitPosition(benchable.getBuffer1(),bitPos);
                    }
                }
                System.out.println("Loop on "+benchable+".putLong(long) "+info_byteA_order(byteAligned,order)+" took "+getElapsedSeconds()+" s");
            }
        }
    }

    private void bench_getByte(final List<MyInterfaceBenchable> benchables, boolean byteAligned) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            final int bitSize = 8;
            final int maxBitPos = CAPACITY_FOR_PUT_GET_BENCH * 8 - bitSize;
            final int initialBitPos = byteAligned ? 0 : 1;
            int bitPos = initialBitPos;
            try {
                benchable.getByte();
                benchable.bitPosition(benchable.getBuffer1(),bitPos);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    dummy += benchable.getByte();
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    if ((bitPos+=bitSize) > maxBitPos) {
                        bitPos = initialBitPos;
                        benchable.bitPosition(benchable.getBuffer1(),bitPos);
                    }
                }
                System.out.println("Loop on "+benchable+".getByte() "+info_byteA_order(byteAligned,order)+" took "+getElapsedSeconds()+" s");
            }
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_getInt(final List<MyInterfaceBenchable> benchables, boolean byteAligned) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            final int bitSize = 32;
            final int maxBitPos = CAPACITY_FOR_PUT_GET_BENCH * 8 - bitSize;
            final int initialBitPos = byteAligned ? 0 : 1;
            int bitPos = initialBitPos;
            try {
                benchable.getInt();
                benchable.bitPosition(benchable.getBuffer1(),bitPos);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    dummy += benchable.getInt();
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    if ((bitPos+=bitSize) > maxBitPos) {
                        bitPos = initialBitPos;
                        benchable.bitPosition(benchable.getBuffer1(),bitPos);
                    }
                }
                System.out.println("Loop on "+benchable+".getInt() "+info_byteA_order(byteAligned,order)+" took "+getElapsedSeconds()+" s");
            }
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_getLong(final List<MyInterfaceBenchable> benchables, boolean byteAligned) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            final int bitSize = 64;
            final int maxBitPos = CAPACITY_FOR_PUT_GET_BENCH * 8 - bitSize;
            final int initialBitPos = byteAligned ? 0 : 1;
            int bitPos = initialBitPos;
            try {
                benchable.getLong();
                benchable.bitPosition(benchable.getBuffer1(),bitPos);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (ByteOrder order : ORDERS) {
                benchable.order(benchable.getBuffer1(), order);
                int j=0;
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    dummy += benchable.getLong();
                    j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    if ((bitPos+=bitSize) > maxBitPos) {
                        bitPos = initialBitPos;
                        benchable.bitPosition(benchable.getBuffer1(),bitPos);
                    }
                }
                System.out.println("Loop on "+benchable+".getLong() "+info_byteA_order(byteAligned,order)+" took "+getElapsedSeconds()+" s");
            }
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    /*
     * 
     */
    
    private void bench_putBitAtBit(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.putBitAtBit(0L, false);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            int j=0;
            startTimer();
            for (int i=0;i<NBR_OF_CALLS;i++) {
                benchable.putBitAtBit(j&MASK_FOR_AT_BIT, valuesBoolean[j]);
                j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
            }
            System.out.println("Loop on "+benchable+".putBitAtBit(long,boolean) took "+getElapsedSeconds()+" s");
        }
    }
    
    private void bench_putBit(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            final int maxBitPos = CAPACITY_FOR_PUT_GET_BENCH * 8 - 1;
            final int initialBitPos = 0;
            int bitPos = initialBitPos;
            try {
                benchable.putBit(false);
                benchable.bitPosition(benchable.getBuffer1(),bitPos);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            int j=0;
            startTimer();
            for (int i=0;i<NBR_OF_CALLS;i++) {
                benchable.putBit(valuesBoolean[j]);
                j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                if ((++bitPos) > maxBitPos) {
                    bitPos = initialBitPos;
                    benchable.bitPosition(benchable.getBuffer1(),bitPos);
                }
            }
            System.out.println("Loop on "+benchable+".putBit(boolean) took "+getElapsedSeconds()+" s");
        }
    }
    
    private void bench_getBitAtBit(final List<MyInterfaceBenchable> benchables) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.getBitAtBit(0L);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            int j=0;
            startTimer();
            for (int i=0;i<NBR_OF_CALLS;i++) {
                dummy += benchable.getBitAtBit(j&MASK_FOR_AT_BIT) ? 1 : 0;
                j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
            }
            System.out.println("Loop on "+benchable+".getBitAtBit(long) took "+getElapsedSeconds()+" s");
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }
    
    private void bench_getBit(final List<MyInterfaceBenchable> benchables) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            final int maxBitPos = CAPACITY_FOR_PUT_GET_BENCH * 8 - 1;
            final int initialBitPos = 0;
            int bitPos = initialBitPos;
            try {
                benchable.getBit();
                benchable.bitPosition(benchable.getBuffer1(),bitPos);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            int j=0;
            startTimer();
            for (int i=0;i<NBR_OF_CALLS;i++) {
                dummy += benchable.getBit() ? 1 : 0;
                j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                if ((++bitPos) > maxBitPos) {
                    bitPos = initialBitPos;
                    benchable.bitPosition(benchable.getBuffer1(),bitPos);
                }
            }
            System.out.println("Loop on "+benchable+".getBit(boolean) took "+getElapsedSeconds()+" s");
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    /*
     * 
     */

    private void bench_putIntSignedAtBit(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.putIntSignedAtBit(0L, 0, 1);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (int bitSize : new int[]{31,32}) {
                for (ByteOrder order : ORDERS) {
                    benchable.order(benchable.getBuffer1(), order);
                    int j=0;
                    startTimer();
                    for (int i=0;i<NBR_OF_CALLS;i++) {
                        benchable.putIntSignedAtBit(j&MASK_FOR_AT_BIT, valuesInt[j]>>(32-bitSize), bitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop on "+benchable+".putIntSignedAtBit(long,int,int) "+info_bitSize_order(bitSize, order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }
    }

    private void bench_putLongSignedAtBit(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.putLongSignedAtBit(0L, 0L, 1);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (int bitSize : new int[]{63,64}) {
                for (ByteOrder order : ORDERS) {
                    benchable.order(benchable.getBuffer1(), order);
                    int j=0;
                    startTimer();
                    for (int i=0;i<NBR_OF_CALLS;i++) {
                        benchable.putLongSignedAtBit(j&MASK_FOR_AT_BIT, valuesLong[j]>>(64-bitSize), bitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop on "+benchable+".putLongSignedAtBit(long,long,int) "+info_bitSize_order(bitSize, order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }
    }

    private void bench_getIntSignedAtBit(final List<MyInterfaceBenchable> benchables) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.getIntSignedAtBit(0L, 1);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (int bitSize : new int[]{31,32}) {
                for (ByteOrder order : ORDERS) {
                    benchable.order(benchable.getBuffer1(), order);
                    int j=0;
                    startTimer();
                    for (int i=0;i<NBR_OF_CALLS;i++) {
                        dummy += benchable.getIntSignedAtBit(j&MASK_FOR_AT_BIT, bitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop on "+benchable+".getIntSignedAtBit(long,int) "+info_bitSize_order(bitSize, order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_getLongSignedAtBit(final List<MyInterfaceBenchable> benchables) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.getLongSignedAtBit(0L, 1);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (int bitSize : new int[]{63,64}) {
                for (ByteOrder order : ORDERS) {
                    benchable.order(benchable.getBuffer1(), order);
                    int j=0;
                    startTimer();
                    for (int i=0;i<NBR_OF_CALLS;i++) {
                        dummy += benchable.getLongSignedAtBit(j&MASK_FOR_AT_BIT, bitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop on "+benchable+".getLongSignedAtBit(long,int) "+info_bitSize_order(bitSize, order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }
        
        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_putIntUnsignedAtBit(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.putIntUnsignedAtBit(0L, 0, 1);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (int bitSize : new int[]{24,31}) {
                for (ByteOrder order : ORDERS) {
                    benchable.order(benchable.getBuffer1(), order);
                    int j=0;
                    startTimer();
                    for (int i=0;i<NBR_OF_CALLS;i++) {
                        benchable.putIntUnsignedAtBit(j&MASK_FOR_AT_BIT, valuesInt[j]>>>(32-bitSize), bitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop on "+benchable+".putIntUnsignedAtBit(long,int,int) "+info_bitSize_order(bitSize, order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }
    }

    private void bench_putLongUnsignedAtBit(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.putLongUnsignedAtBit(0L, 0L, 1);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (int bitSize : new int[]{56,63}) {
                for (ByteOrder order : ORDERS) {
                    benchable.order(benchable.getBuffer1(), order);
                    int j=0;
                    startTimer();
                    for (int i=0;i<NBR_OF_CALLS;i++) {
                        benchable.putLongUnsignedAtBit(j&MASK_FOR_AT_BIT, valuesLong[j]>>>(64-bitSize), bitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop on "+benchable+".putLongUnsignedAtBit(long,int,int) "+info_bitSize_order(bitSize, order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }
    }

    private void bench_getIntUnsignedAtBit(final List<MyInterfaceBenchable> benchables) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.getIntUnsignedAtBit(0L, 1);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (int bitSize : new int[]{24,31}) {
                for (ByteOrder order : ORDERS) {
                    benchable.order(benchable.getBuffer1(), order);
                    int j=0;
                    startTimer();
                    for (int i=0;i<NBR_OF_CALLS;i++) {
                        dummy += benchable.getIntUnsignedAtBit(j&MASK_FOR_AT_BIT, bitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop on "+benchable+".getIntUnsignedAtBit(long,int) "+info_bitSize_order(bitSize, order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_getLongUnsignedAtBit(final List<MyInterfaceBenchable> benchables) {
        long dummy = 0;
        
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            benchable.setBuffer1(benchable.newBuffer(CAPACITY_FOR_PUT_GET_BENCH));
            try {
                benchable.getLongUnsignedAtBit(0L, 1);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            for (int bitSize : new int[]{56,63}) {
                for (ByteOrder order : ORDERS) {
                    benchable.order(benchable.getBuffer1(), order);
                    int j=0;
                    startTimer();
                    for (int i=0;i<NBR_OF_CALLS;i++) {
                        dummy += benchable.getLongUnsignedAtBit(j&MASK_FOR_AT_BIT, bitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop on "+benchable+".getLongUnsignedAtBit(long,int) "+info_bitSize_order(bitSize, order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }

        if (dummy == Long.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_bufferCopy(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            final int maxCopiedByteSize = 10000;
            final int arrayByteSize = maxCopiedByteSize + 8;
            final Object b1 = benchable.newBuffer(arrayByteSize);
            final Object b2 = benchable.newBuffer(arrayByteSize);
            benchable.setBuffer1(b1);
            benchable.setBuffer2(b2);
            try {
                benchable.bufferCopy(0, 0, 1);
            } catch (UnsupportedOperationException e) {
                continue;
            }

            final byte[] srcBA = new byte[arrayByteSize];
            final byte[] dstBA = new byte[arrayByteSize];

            long copiedBitSize;
            int divisor;
            int nbrOfRounds;

            for (int copiedByteSize : new int[]{1,4,8,9,100,1000,10000}) {
                for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
                    benchable.order(b1, order);
                    benchable.order(b2, order);

                    copiedBitSize = copiedByteSize * 8L;
                    divisor = Math.max(1,copiedByteSize/10);
                    nbrOfRounds = NBR_OF_CALLS/divisor;

                    int j=0;
                    startTimer();
                    for (int i=0;i<nbrOfRounds;i++) {
                        benchable.bufferCopy(0, 0, copiedByteSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop(/"+divisor+") on "+benchable+".bufferCopy(...) "+info_byteA_byteS_order(false,copiedByteSize,order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }
    }

    private void bench_bufferCopyBits_byteAligned(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            final int maxCopiedByteSize = 10000;
            final int arrayByteSize = maxCopiedByteSize + 8;
            final Object b1 = benchable.newBuffer(arrayByteSize);
            final Object b2 = benchable.newBuffer(arrayByteSize);
            benchable.setBuffer1(b1);
            benchable.setBuffer2(b2);
            try {
                benchable.bufferCopyBits(0L, 0L, 1L);
            } catch (UnsupportedOperationException e) {
                continue;
            }

            long copiedBitSize;
            int divisor;
            int nbrOfRounds;

            for (int copiedByteSize : new int[]{1,4,8,9,100,1000,10000}) {
                for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
                    benchable.order(b1, order);
                    benchable.order(b2, order);

                    copiedBitSize = copiedByteSize * 8L;
                    divisor = Math.max(1,copiedByteSize/10);
                    nbrOfRounds = NBR_OF_CALLS/divisor;

                    int j=0;
                    startTimer();
                    for (int i=0;i<nbrOfRounds;i++) {
                        benchable.bufferCopyBits(0L, 0L, copiedBitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop(/"+divisor+") on "+benchable+".bufferCopyBits(...) "+info_byteA_byteS_order(true,copiedByteSize,order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }
    }

    private void bench_bufferCopyBits(final List<MyInterfaceBenchable> benchables) {
        System.out.println("");
        for (MyInterfaceBenchable benchable : benchables) {
            if (separation(benchable)) { continue; }
            final int maxCopiedByteSize = 10000;
            final int arrayByteSize = maxCopiedByteSize + 8;
            final Object b1 = benchable.newBuffer(arrayByteSize);
            final Object b2 = benchable.newBuffer(arrayByteSize);
            benchable.setBuffer1(b1);
            benchable.setBuffer2(b2);
            try {
                benchable.bufferCopyBits(0L, 0L, 1L);
            } catch (UnsupportedOperationException e) {
                continue;
            }

            long copiedBitSize;
            int divisor;
            int nbrOfRounds;

            for (int copiedByteSize : new int[]{1,4,8,9,100,1000,10000}) {
                for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
                    benchable.order(b1, order);
                    benchable.order(b2, order);

                    copiedBitSize = copiedByteSize * 8L;
                    divisor = Math.max(1,copiedByteSize/10);
                    nbrOfRounds = NBR_OF_CALLS/divisor;

                    int j=0;
                    startTimer();
                    for (int i=0;i<nbrOfRounds;i++) {
                        long srcBitPos = int1To7[j];
                        long dstBitPos = int1To7[(NBR_OF_VALUES-1)-j];
                        benchable.bufferCopyBits(srcBitPos, dstBitPos, copiedBitSize);
                        j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
                    }
                    System.out.println("Loop(/"+divisor+") on "+benchable+".bufferCopyBits(...) "+info_byteA_byteS_order(false,copiedByteSize,order)+" took "+getElapsedSeconds()+" s");
                }
            }
        }
    }
}
