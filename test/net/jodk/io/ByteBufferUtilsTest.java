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

import net.jodk.lang.LangUtils;

public class ByteBufferUtilsTest extends AbstractBufferOpTezt {

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MyTab implements InterfaceTab {
        final ByteBuffer buffer;
        /**
         * Stored aside from buffer, for set and null check
         * to be done on actual operation.
         */
        ByteOrder order;
        public MyTab(ByteBuffer buffer) {
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
            return this.buffer.hasArray();
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
            this.buffer.put(index, value);
        }
        @Override
        public byte get(int index) {
            return this.buffer.get(index);
        }
        protected void myBufferOrder(ByteOrder order) {
            LangUtils.checkNonNull(order);
            this.buffer.order(order);
        }
    }
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public void test_padByteAtBit_ByteBuffer_long() {
        test_padByteAtBitOperation(new InterfacePadByteAtBitOperation<MyTab>() {
            @Override
            public int padByteAtBit(MyTab tab, long bitPos) {
                if (tab.isReadOnly()) {
                    // Padding method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                }
                
                return ByteBufferUtils.padByteAtBit(tab.buffer, bitPos);
            }
        });
    }
    
    /*
     * 
     */

    public void test_putBitAt_ByteBuffer_long_boolean() {
        test_putBitAtBitOperation(new InterfacePutBitAtBitOperation<MyTab>() {
            @Override
            public void putBitAtBit(MyTab tab, long bitPos, boolean value) {
                if (tab.isReadOnly()) {
                    // Put method will throw ReadOnlyBufferException, whatever the arguments.
                } else {
                    tab.myBufferOrder(tab.order);
                }
                
                ByteBufferUtils.putBitAtBit(tab.buffer, bitPos, value);
            }
        });
    }

    public void test_getBitAt_ByteBuffer_long() {
        test_getBitAtBitOperation(new InterfaceGetBitAtBitOperation<MyTab>() {
            @Override
            public boolean getBitAtBit(MyTab tab, long bitPos) {
                tab.myBufferOrder(tab.order);
                return ByteBufferUtils.getBitAtBit(tab.buffer, bitPos);
            }
        });
    }

    /*
     * 
     */
    
    public void test_putIntSignedAt_ByteBuffer_long_int_int() {
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
                
                ByteBufferUtils.putIntSignedAtBit(tab.buffer, firstBitPos, (int)value, bitSize);
            }
        });
    }

    public void test_putLongSignedAt_ByteBuffer_long_long_int() {
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
                
                ByteBufferUtils.putLongSignedAtBit(tab.buffer, firstBitPos, value, bitSize);
            }
        });
    }
    
    public void test_putIntUnsignedAt_ByteBuffer_long_int_int() {
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
                
                ByteBufferUtils.putIntUnsignedAtBit(tab.buffer, firstBitPos, (int)value, bitSize);
            }
        });
    }

    public void test_putLongUnsignedAt_ByteBuffer_long_long_int() {
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
                
                ByteBufferUtils.putLongUnsignedAtBit(tab.buffer, firstBitPos, value, bitSize);
            }
        });
    }

    /*
     * 
     */
    
    public void test_getIntSignedAt_ByteBuffer_long_int() {
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
                return ByteBufferUtils.getIntSignedAtBit(tab.buffer, firstBitPos, bitSize);
            }
        });
    }

    public void test_getLongSignedAt_ByteBuffer_long_int() {
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
                return ByteBufferUtils.getLongSignedAtBit(tab.buffer, firstBitPos, bitSize);
            }
        });
    }

    public void test_getIntUnsignedAt_ByteBuffer_long_int() {
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
                return ByteBufferUtils.getIntUnsignedAtBit(tab.buffer, firstBitPos, bitSize);
            }
        });
    }

    public void test_getLongUnsignedAt_ByteBuffer_long_int() {
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
                return ByteBufferUtils.getLongUnsignedAtBit(tab.buffer, firstBitPos, bitSize);
            }
        });
    }
    
    /*
     * 
     */

    public void test_bufferCopy_ByteBuffer_int_ByteBuffer_int_int() {
        test_copyBytesOperation(new InterfaceCopyBytesOperation<MyTab>() {
            @Override
            public void copyBytes(MyTab src, int srcFirstByteIndex, MyTab dest, int destFirstByteIndex, int byteSize) {
                src.myBufferOrder(src.order);
                dest.myBufferOrder(dest.order);
                ByteBufferUtils.bufferCopy(src.buffer, srcFirstByteIndex, dest.buffer, destFirstByteIndex, byteSize);
            }
        });
    }

    public void test_bufferCopyBits_ByteBuffer_long_ByteBuffer_long_long() {
        test_copyBitsOperation(new InterfaceCopyBitsOperation<MyTab>() {
            @Override
            public void copyBits(MyTab src, long srcFirstBitPos, MyTab dest, long destFirstBitPos, long bitSize) {
                src.myBufferOrder(src.order);
                dest.myBufferOrder(dest.order);
                ByteBufferUtils.bufferCopyBits(src.buffer, srcFirstBitPos, dest.buffer, destFirstBitPos, bitSize);
            }
        });
    }
    
    /*
     * 
     */
    
    public void test_toString_ByteBuffer_int_int_int() {
        test_toStringOperation(new InterfaceToStringOperation<MyTab>() {
            @Override
            public String toString(MyTab tab, int firstByteIndex, int byteSize, int radix) {
                return ByteBufferUtils.toString(tab.buffer, firstByteIndex, byteSize, radix);
            }
        });
    }
    
    public void test_toStringBits_ByteBuffer_long_long_boolean() {
        test_toStringBitsOperation(new InterfaceToStringBitsOperation<MyTab>() {
            @Override
            public String toStringBits(MyTab tab, long firstBitPos, long bitSize, boolean bigEndian) {
                return ByteBufferUtils.toStringBits(tab.buffer, firstBitPos, bitSize, bigEndian);
            }
        });
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    @Override
    protected InterfaceTab[] newTabs(int limit, InterfaceTab... with) {
        // To make sure offset is used.
        final int offset = 3;
        // To make sure limit is used, and not capacity.
        final int surcapacity = 5;
        
        final ArrayList<ByteBuffer> bbs = new ArrayList<ByteBuffer>();
        final int capacity = limit + offset + surcapacity;
        bbs.add(fillRandom(ByteBuffer.allocate(capacity)));
        bbs.add(fillRandom(ByteBuffer.allocateDirect(capacity)));
        bbs.add(fillRandom(ByteBuffer.allocate(capacity)).asReadOnlyBuffer());
        bbs.add(fillRandom(ByteBuffer.allocateDirect(capacity)).asReadOnlyBuffer());
        
        for (int i=0;i<bbs.size();i++) {
            ByteBuffer bb = bbs.get(i);
            bb.position(offset);
            bb = bb.slice();
            bbs.set(i, bb);
        }

        final ArrayList<InterfaceTab> tabs = new ArrayList<InterfaceTab>();
        for (ByteBuffer bb : bbs) {
            bb.limit(limit);
            tabs.add(new MyTab(bb));
        }
        
        if (with != null) {
            for (InterfaceTab tab : with) {
                tabs.add(tab);
            }
        }
        return tabs.toArray(new InterfaceTab[tabs.size()]);
    }

    @Override
    protected InterfaceTab newSharingTab(InterfaceTab tab, int targetRelativeOffset) {
        final MyTab bat = (MyTab)tab;
        final ByteOrder order = bat.order;
        
        ByteBuffer buffer = bat.buffer.duplicate().order(order);
        final int relativeOffset = Math.min(targetRelativeOffset, buffer.limit());
        if (relativeOffset != 0) {
            buffer.position(relativeOffset);
            buffer = buffer.slice().order(order);
        }
        
        return new MyTab(buffer);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private ByteBuffer fillRandom(ByteBuffer buffer) {
        for (int i=0;i<buffer.limit();i++) {
            buffer.put(i,(byte)random.nextInt());
        }
        return buffer;
    }
}
