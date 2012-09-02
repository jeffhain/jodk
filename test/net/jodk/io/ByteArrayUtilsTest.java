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
import java.util.ArrayList;

import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;

public class ByteArrayUtilsTest extends AbstractBufferOpTezt {
    
    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MyTab implements InterfaceTab {
        final byte[] buffer;
        ByteOrder order = ByteOrder.BIG_ENDIAN;
        public MyTab(byte[] buffer) {
            this.buffer = buffer;
        }
        @Override
        public boolean isReadOnly() {
            return false;
        }
        @Override
        public boolean isDirect() {
            return false;
        }
        @Override
        public boolean hasArray() {
            return true;
        }
        @Override
        public boolean hasArrayWritableOrNot() {
            return true;
        }
        @Override
        public InterfaceTab asReadOnly() {
            return null;
        }
        @Override
        public int limit() {
            return this.buffer.length;
        }
        @Override
        public void order(ByteOrder order) {
            this.order = order;
        }
        @Override
        public void orderAndSetInBufferIfAny(ByteOrder order) {
            this.order = order;
        }
        @Override
        public ByteOrder order() {
            return this.order;
        }
        @Override
        public void put(int index, byte value) {
            this.buffer[index] = value;
        }
        @Override
        public byte get(int index) {
            return this.buffer[index];
        }
    }

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public void test_putShortAt_byteArray_int_short_ByteOrder_to_putLongAt_byteArray_int_long_ByteOrder() {
        for (final int bitZise : new int[]{16,32,64}) {
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
                    LangUtils.azzert((firstBitPos&7) == 0);
                    LangUtils.azzert(bitSize == bitZise);
                    
                    final int index = (int)(firstBitPos/8L);
                    if (bitZise == 16) {
                        LangUtils.azzert(value == (short)value);
                        ByteArrayUtils.putShortAt(tab.buffer, index, (short)value, tab.order);
                    } else if (bitZise == 32) {
                        LangUtils.azzert(value == (int)value);
                        ByteArrayUtils.putIntAt(tab.buffer, index, (int)value, tab.order);
                    } else {
                        ByteArrayUtils.putLongAt(tab.buffer, index, value, tab.order);
                    }
                }
            });
        }
    }
    
    /*
     * 
     */

    public void test_getShortAt_byteArray_int_ByteOrder_to_getLongAt_byteArray_int_ByteOrder() {
        for (final int bitZise : new int[]{16,32,64}) {
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
                    LangUtils.azzert((firstBitPos&7) == 0);
                    LangUtils.azzert(bitSize == bitZise);

                    final int index = (int)(firstBitPos/8L);
                    final long result;
                    if (bitZise == 16) {
                        result = ByteArrayUtils.getShortAt(tab.buffer, index, tab.order);
                    } else if (bitZise == 32) {
                        result = ByteArrayUtils.getIntAt(tab.buffer, index, tab.order);
                    } else {
                        result = ByteArrayUtils.getLongAt(tab.buffer, index, tab.order);
                    }
                    return result;
                }
            });
        }
     }

     /*
      * 
      */
     
    public void test_padByteAtBit_byteArray_long_ByteOrder() {
        test_padByteAtBitOperation(new InterfacePadByteAtBitOperation<MyTab>() {
            @Override
            public int padByteAtBit(MyTab tab, long bitPos) {
                return ByteArrayUtils.padByteAtBit(tab.buffer, bitPos, tab.order);
            }
        });
    }
    
    /*
     * 
     */

    public void test_putBitAt_byteArray_long_boolean_ByteOrder() {
        test_putBitAtBitOperation(new InterfacePutBitAtBitOperation<MyTab>() {
            @Override
            public void putBitAtBit(MyTab tab, long bitPos, boolean value) {
                LangUtils.checkNonNull(tab.order);
                ByteArrayUtils.putBitAtBit(tab.buffer, bitPos, value, tab.order);
            }
        });
    }

    public void test_getBitAt_byteArray_long_ByteOrder() {
        test_getBitAtBitOperation(new InterfaceGetBitAtBitOperation<MyTab>() {
            @Override
            public boolean getBitAtBit(MyTab tab, long bitPos) {
                LangUtils.checkNonNull(tab.order);
                return ByteArrayUtils.getBitAtBit(tab.buffer, bitPos, tab.order);
            }
        });
    }

    /*
     * 
     */
    
    public void test_putIntSignedAt_byteArray_long_int_int_ByteOrder() {
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
                ByteArrayUtils.putIntSignedAtBit(tab.buffer, firstBitPos, NumbersUtils.asInt(value), bitSize, tab.order);
            }
        });
    }

    public void test_putLongSignedAt_byteArray_long_long_int_ByteOrder() {
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
                ByteArrayUtils.putLongSignedAtBit(tab.buffer, firstBitPos, value, bitSize, tab.order);
            }
        });
    }
    
    public void test_putIntUnsignedAt_byteArray_long_int_int_ByteOrder() {
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
                ByteArrayUtils.putIntUnsignedAtBit(tab.buffer, firstBitPos, NumbersUtils.asInt(value), bitSize, tab.order);
            }
        });
    }

    public void test_putLongUnsignedAt_byteArray_long_long_int_ByteOrder() {
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
                ByteArrayUtils.putLongUnsignedAtBit(tab.buffer, firstBitPos, value, bitSize, tab.order);
            }
        });
    }

    /*
     * 
     */

    public void test_getIntSignedAt_byteArray_long_int_ByteOrder() {
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
                return ByteArrayUtils.getIntSignedAtBit(tab.buffer, firstBitPos, bitSize, tab.order);
            }
        });
    }

    public void test_getLongSignedAt_byteArray_long_int_ByteOrder() {
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
                return ByteArrayUtils.getLongSignedAtBit(tab.buffer, firstBitPos, bitSize, tab.order);
            }
        });
    }

    public void test_getIntUnsignedAt_byteArray_long_int_ByteOrder() {
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
                return ByteArrayUtils.getIntUnsignedAtBit(tab.buffer, firstBitPos, bitSize, tab.order);
            }
        });
    }

    public void test_getLongUnsignedAt_byteArray_long_int_ByteOrder() {
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
                return ByteArrayUtils.getLongUnsignedAtBit(tab.buffer, firstBitPos, bitSize, tab.order);
            }
        });
    }
    
    /*
     * 
     */
    
    public void test_arrayCopyBits_byteArray_long_byteArray_long_long_ByteOrder() {
        test_copyBitsOperation(new InterfaceCopyBitsOperation<MyTab>() {
            @Override
            public void copyBits(MyTab src, long srcFirstBitPos, MyTab dest, long destFirstBitPos, long bitSize) {
                if ((src.order == null) || (dest.order == null)) {
                    throw new NullPointerException();
                }
                if (src.order != dest.order) {
                    throw new IllegalArgumentException();
                }
                ByteArrayUtils.arrayCopyBits(src.buffer, srcFirstBitPos, dest.buffer, destFirstBitPos, bitSize, src.order);
            }
        });
    }
    
    /*
     * 
     */
    
    public void test_toString_byteArray_int_int_int() {
        test_toStringOperation(new InterfaceToStringOperation<MyTab>() {
            @Override
            public String toString(MyTab tab, int firstByteIndex, int byteSize, int radix) {
                return ByteArrayUtils.toString(tab.buffer, firstByteIndex, byteSize, radix);
            }
        });
    }
    
    public void test_toStringBits_byteArray_long_long_boolean() {
        test_toStringBitsOperation(new InterfaceToStringBitsOperation<MyTab>() {
            @Override
            public String toStringBits(MyTab tab, long firstBitPos, long bitSize, boolean bigEndian) {
                return ByteArrayUtils.toStringBits(tab.buffer, firstBitPos, bitSize, bigEndian);
            }
        });
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    @Override
    protected InterfaceTab[] newTabs(int limit, InterfaceTab... with) {
        final ArrayList<InterfaceTab> tabs = new ArrayList<InterfaceTab>();
        tabs.add(new MyTab(fillRandom(new byte[limit])));
        
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
        return new MyTab(bat.buffer);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private byte[] fillRandom(byte[] buffer) {
        for (int i=0;i<buffer.length;i++) {
            buffer[i] = (byte)random.nextInt();
        }
        return buffer;
    }
}
