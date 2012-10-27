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

import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;

/**
 * A sort of bitwise ByteBuffer, useful to optimize memory usage.
 * 
 * Can be backed by a byte array, or a ByteBuffer.
 * A same ByteBuffer should not be used to back different DataBuffers,
 * and DataBuffer behavior is undefined if its backing ByteBuffer
 * happens to be modified by other treatments.
 * 
 * If backed by a ByteBuffer, the ByteBuffer position, limit and order
 * are not always kept consistent by these treatments
 * (and there could be multiple ways of keeping them consistent):
 * you must do it on your own if needed (such as when reading from
 * ByteBuffer after writing through DataBuffer, or when reading from
 * DataBuffer after writing through ByteBuffer).
 * Though, ByteBuffer's limit and order are set when DataBuffer
 * ones are set, for ByteBuffer's put/get methods to behave properly,
 * as well as ByteBuffer's position on DataBuffer.slice() operation.
 * 
 * Only defining bitwise size operations for ints or longs.
 * Methods for byte/short/char could be added later, but would cause
 * much similar code for not much performances improvement,
 * and the number of methods is quite large already.
 * 
 * For performance reasons, for put operations,
 * if the byte range is invalid, exception might
 * be thrown only after some data has been written.
 * 
 * Using a bytewise limit, not a bitwise one, for it:
 * - makes it easier to split a same array between multiple buffers
 *   (and is homogeneous with arrayOffset()),
 * - allows for faster bounds checks (int instead of long),
 * - allows to delegate bounds check to eventual backing buffer
 *   or array (should not hurt much to have some data written
 *   before an eventual exception is thrown),
 * - should not hurt much not to have a bitwise limit,
 *   especially because data streams provide bytes and not bits,
 *   so one have to deal with possible meaningless trailing bits
 *   anyway.
 * 
 * Unlike for ByteBuffer:
 * - hashCode(), equals(Object) and compareTo(DataBuffer)
 *   methods can make use of bits outside remaining bits, more specifically if,
 *   and only if, bit position is not a multiple of 8,
 * - order(ByteOrder) method does not accept null.
 */
public class DataBuffer implements Comparable<DataBuffer> {

    /*
     * Could remove helpers, and make all treatments
     * directly work on ByteBuffers, to optimize this case
     * (and therefore the direct case), but it would no longer
     * be possible to directly work on a byte array without
     * creating a wrapping ByteBuffer.
     * 
     * Could also remove setBackingBuffer(...) methods,
     * not to allow for backing tab's change, and also
     * make backing tab not accessible (no accessor,
     * and .duplicated() on setting), to make sure its
     * limit, order and position don't get changed improperly,
     * but again it would cause avoidable garbage.
     */
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    /**
     * For optimization, where it doesn't cause much additional code.
     * 
     * Maybe abusive to optimize bytewise cases, since for performances
     * bytewise data should be put directly in the backing ByteBuffer;
     * especially because it adds a ByteBuffer field; but that should
     * not hurt much.
     */
    private static final boolean SPECIFIC_OPTIM = true;
    
    private static final BaseBTHelper BA_HELPER = BaseBTHelper.INSTANCE;
    
    private static final ByteBufferBTHelper BB_HELPER = ByteBufferBTHelper.INSTANCE;
    
    private BaseBTHelper tabHelper;

    /**
     * Either byte array or ByteBuffer.
     */
    private Object tab;

    /**
     * If tab is a byte array, this value is baOffset,
     * else it is zero (offset taken care of in ByteBuffer treatments).
     * 
     * This offset must be added before use of helper methods, but AFTER bounds
     * check, which must be done before because limit must be checked not to
     * cause overflow if offset is added to it.
     */
    private int tabOffset;

    /**
     * null if does not exist or is not known.
     * 
     * If this DataBuffer is writable, and has a read-only backing ByteBuffer,
     * we don't have access to its array, so no risk to use it for write purpose
     * from DataBuffer treatments.
     */
    private byte[] ba;
    
    private int baOffset;

    /**
     * null if no backing ByteBuffer.
     */
    private ByteBuffer bb;

    /*
     * Invariant: bitMark <= bitPosition <= limit * 8 <= capacity * 8.
     */

    /**
     * Position of the first byte that should not be read or written.
     */
    private int limit;

    /**
     * Number of bytes this buffer can contain.
     */
    private int capacity;

    /**
     * Position of the next bit to be read or written.
     */
    private long bitPosition;

    /**
     * The value bitPosition is set at by reset() method.
     * -1 if not defined.
     */
    private long bitMark;

    private boolean bigEndian = true;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /*
     * allocate methods
     */

    /**
     * The returned DataBuffer is big endian by default, and not read-only.
     * 
     * @param capacity Byte capacity (>= 0).
     * @return A DataBuffer backed by a byte array.
     * @throws IllegalArgumentException if capacity < 0.
     */
    public static DataBuffer allocateBA(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("byte capacity ["+capacity+"] must be >= 0");
        }
        return new DataBuffer(new byte[capacity]);
    }

    /**
     * The returned DataBuffer is big endian by default, and not read-only.
     * 
     * @param capacity Byte capacity (>= 0).
     * @return A DataBuffer backed by a heap ByteBuffer.
     * @throws IllegalArgumentException if capacity < 0.
     */
    public static DataBuffer allocateBB(int capacity) {
        return new DataBuffer(ByteBuffer.allocate(capacity));
    }

    /**
     * The returned DataBuffer is big endian by default, and not read-only.
     * 
     * @param capacity Byte capacity (>= 0).
     * @return A DataBuffer backed by a direct ByteBuffer.
     * @throws IllegalArgumentException if capacity < 0.
     */
    public static DataBuffer allocateBBDirect(int capacity) {
        return new DataBuffer(ByteBuffer.allocateDirect(capacity));
    }

    /*
     * newInstance methods
     */

    /**
     * Equivalent to newInstance((byte[])buffer,0,buffer.length) and wrap(byte[]).
     */
    public static DataBuffer newInstance(byte[] buffer) {
        return new DataBuffer(buffer);
    }

    /**
     * Not to be confused with wrap(byte[],int,int):
     * newInstance int arguments are (array offset,capacity), while
     * wrap int arguments are (position,length=limit-position).
     * 
     * The returned DataBuffer is big endian by default, and not read-only.
     * 
     * @param buffer Byte array to work on.
     * @param offset Byte offset for this DataBuffer in the specified byte array.
     * @param capacity The capacity to use.
     * @return A DataBuffer backed by the specified byte array.
     * @throws NullPointerException if the specified byte array is null.
     * @throws IndexOutOfBoundsException if offset < 0, or capacity < 0,
     *         or offset+capacity overflows, or offset+capacity > buffer.length.
     */
    public static DataBuffer newInstance(byte[] buffer, int offset, int capacity) {
        LangUtils.checkBounds(buffer.length, offset, capacity);
        return new DataBuffer(
                buffer, // ba
                offset, // baOffset
                null, // bb
                -1, // bitMark
                0, // bitPosition
                capacity, // limit
                capacity, // capacity
                true); // bigEndian
    }

    /**
     * Not to be confused with wrap(ByteBuffer):
     * newInstance resets ByteBuffer's position to zero and limit to capacity, while
     * wrap preserves position and limit and sets them into the returned DataBuffer.
     * 
     * The returned DataBuffer has the same order than the specified ByteBuffer,
     * is read-only if and only if the specified ByteBuffer is read-only,
     * and the limit of both the specified ByteBuffer and this DataBuffer
     * is set to the capacity.
     * 
     * @param buffer ByteBuffer to work on.
     * @return A DataBuffer backed by the specified ByteBuffer.
     * @throws NullPointerException if the specified ByteBuffer is null.
     */
    public static DataBuffer newInstance(ByteBuffer buffer) {
        return wrap(buffer).bitPosition(0).limit(buffer.capacity());
    }

    /*
     * wrap methods
     */

    /**
     * Equivalent to wrap((byte[])buffer,0,buffer.length) and newInstance(byte[]).
     */
    public static DataBuffer wrap(byte[] buffer) {
        return new DataBuffer(buffer);
    }

    /**
     * Not to be confused with newInstance(byte[],int,int):
     * newInstance int arguments are (array offset,capacity), while
     * wrap int arguments are (position,length=limit-position).
     * 
     * Compared to ByteBuffer.wrap(byte[],int,int), the second argument has been
     * renamed from offset to position, to avoid confusion with the notion of
     * "array offset" for the backing array (if any).
     * 
     * The returned DataBuffer is big endian by default, and not read-only.
     * 
     * @param buffer Byte array to work on.
     * @param position Byte position for the new DataBuffer.
     * @param length Number of bytes from new DataBuffer's position to its limit.
     * @return A DataBuffer backed by the specified byte array, with an array offset of zero,
     *         and a capacity of buffer.length.
     * @throws NullPointerException if the specified byte array is null.
     * @throws IndexOutOfBoundsException if position < 0, or length < 0,
     *         or position+length overflows, or position+length > buffer.length.
     */
    public static DataBuffer wrap(byte[] buffer, int position, int length) {
        LangUtils.checkBounds(buffer.length, position, length);
        return new DataBuffer(
                buffer, // ba
                0, // baOffset
                null, // bb
                -1, // bitMark
                position * 8L, // bitPosition
                position+length, // limit
                buffer.length, // capacity
                true); // bigEndian
    }

    /**
     * Not to be confused with newInstance(ByteBuffer):
     * newInstance resets ByteBuffer's position to zero and limit to capacity, while
     * wrap preserves position and limit and sets them into the returned DataBuffer.
     * 
     * The returned DataBuffer has the same order than the specified ByteBuffer,
     * is read-only if and only if the specified ByteBuffer is read-only,
     * and has also the same position and limit.
     * 
     * @param buffer ByteBuffer to work on.
     * @return A DataBuffer backed by the specified ByteBuffer.
     * @throws NullPointerException if the specified ByteBuffer is null.
     */
    public static DataBuffer wrap(ByteBuffer buffer) {
        if (buffer.isReadOnly()) {
            return new DataBufferR(
                    (buffer.hasArray() ? buffer.array() : null), // ba
                    (buffer.hasArray() ? buffer.arrayOffset() : 0), // baOffset
                    buffer, // bb
                    -1, // bitMark
                    buffer.position() * 8L, // bitPosition
                    buffer.limit(), // limit
                    buffer.capacity(), // capacity
                    buffer.order() == ByteOrder.BIG_ENDIAN);
        } else {
            return new DataBuffer(
                    (buffer.hasArray() ? buffer.array() : null), // ba
                    (buffer.hasArray() ? buffer.arrayOffset() : 0), // baOffset
                    buffer, // bb
                    -1, // bitMark
                    buffer.position() * 8L, // bitPosition
                    buffer.limit(), // limit
                    buffer.capacity(), // capacity
                    buffer.order() == ByteOrder.BIG_ENDIAN);
        }
    }

    /*
     * other DataBuffer-generating methods
     */

    /**
     * Creates a new DataBuffer whose content is a shared subsequence of
     * this DataBuffer's content.
     * 
     * Unlike for ByteBuffer.slice(), the buffer generated by this method is not
     * big endian by default, but has the same order than the initial buffer.
     * This is useful because DataBuffer is bitwise, and depending on order
     * bit position designates a different bit.
     * 
     * The content of the new DataBuffer will start at positionSup(),
     * its bitPosition will be zero, its capacity and its limit will be
     * the number of bytes remaining in this buffer (i.e. up to limit),
     * and its mark will be undefined.
     * 
     * The new DataBuffer will be direct, respectively read-only,
     * if and only if this DataBuffer is direct, respectively read-only.
     * 
     * If this DataBuffer is backed by a ByteBuffer, this methods
     * sets its position to positionSup, for its slice() method
     * to behave properly.
     * 
     * @return A new, sliced DataBuffer.
     */
    public DataBuffer slice() {
        final long bitMark = -1;
        final long bitPosition = 0;
        final int posSup = this.positionSup();
        final int limit = this.limit - posSup;
        final int capacity = limit;
        final Object tabSliced = this.tabSlice();
        return new DataBuffer(
                this.ba, // ba
                ((this.ba != null) ? this.baOffset + posSup : 0), // baOffset
                (this.hasByteBuffer() ? (ByteBuffer)tabSliced : null), // bb
                bitMark,
                bitPosition,
                limit,
                capacity,
                this.bigEndian);
    }

    /**
     * Creates a new DataBuffer that shares this DataBuffer's content.
     * 
     * Unlike for ByteBuffer.duplicate(), the buffer generated by this method is not
     * big endian by default, but has the same order than the initial buffer.
     * This is useful because DataBuffer is bitwise, and depending on order
     * bit position designates a different bit.
     * 
     * The content of the new DataBuffer will be that of this DataBuffer,
     * its bitPosition, capacity, limit and mark will be identical to those
     * of this DataBuffer.
     * 
     * The new DataBuffer will be direct, respectively read-only,
     * if and only if this DataBuffer is direct, respectively read-only.
     * 
     * @return A new, identical DataBuffer.
     */
    public DataBuffer duplicate() {
        final Object tabDuplicated = this.tabDuplicate();
        return new DataBuffer(
                this.ba, // ba
                this.baOffset, // baOffset
                (this.hasByteBuffer() ? (ByteBuffer)tabDuplicated : null), // bb
                this.bitMark,
                this.bitPosition,
                this.limit,
                this.capacity,
                this.bigEndian);
    }

    /**
     * Creates a new, read-only DataBuffer that shares this DataBuffer's content.
     * 
     * Unlike for ByteBuffer.asReadOnlyBuffer(), the buffer generated by this method is not
     * big endian by default, but has the same order than the initial buffer.
     * This is useful because DataBuffer is bitwise, and depending on order
     * bit position designates a different bit.
     * 
     * The content of the new DataBuffer will be that of this DataBuffer,
     * its bitPosition, capacity, limit and mark will be identical to those
     * of this DataBuffer.
     * 
     * The new DataBuffer will be direct if and only if this DataBuffer is direct.
     * 
     * If this DataBuffer is read-only, this method is equivalent
     * to duplicate() method.
     * 
     * @return A new, read-only DataBuffer.
     */
    public DataBuffer asReadOnlyBuffer() {
        final Object tabAsReadOnlyBuffer = this.tabAsReadOnlyBuffer();
        return new DataBufferR(
                this.ba, // ba
                this.baOffset, // baOffset
                (this.hasByteBuffer() ? (ByteBuffer)tabAsReadOnlyBuffer : null), // bb
                this.bitMark,
                this.bitPosition,
                this.limit,
                this.capacity,
                this.bigEndian);
    }

    /*
     * backing buffer setting
     */

    /**
     * Equivalent to setBackingBuffer(buffer,0,buffer.length).
     */
    public void setBackingBuffer(byte[] buffer) {
        this.setBackingBufferImpl(buffer);
    }

    /**
     * Clears mark if any.
     * Lets order unchanged.
     * 
     * Sets bit position to zero, and limit to capacity.
     * 
     * @param buffer Byte array to work on.
     * @param offset Byte offset for this DataBuffer in the specified byte array.
     * @param capacity The capacity to use.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws NullPointerException if the specified byte array is null.
     * @throws IllegalArgumentException if capacity < 0.
     * @throws IndexOutOfBoundsException if offset < 0, or if offset+capacity
     *         overflows, or if offset+capacity > buffer.length.
     */
    public void setBackingBuffer(byte[] buffer, int offset, int capacity) {
        this.setBackingBufferImpl(buffer, offset, capacity);
    }

    /**
     * Aligns capacity, limit and order with the specified ByteBuffer.
     * 
     * Clears mark of this DataBuffer if any.
     * 
     * Sets bit position to zero, and the limit of both the specified
     * ByteBuffer and this DataBuffer is set to capacity.
     * 
     * If the specified ByteBuffer is read-only, this DataBuffer
     * remains non-read-only, but the specified ByteBuffer will
     * of course throw ReadOnlyBufferException if one tries to
     * write data in it, or call methods such as array().
     * This is done to to allow for bitwise read of a read-only
     * ByteBuffer through wrapping, which unlike newInstance(ByteBuffer)
     * does not create a DataBuffer object.
     * 
     * @param buffer ByteBuffer to work on.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws NullPointerException if the specified ByteBuffer is null.
     */
    public void setBackingBuffer(ByteBuffer buffer) {
        this.setBackingBufferImpl(buffer);
    }

    /*
     * 
     */

    /**
     * Returns the current hash code of this DataBuffer.
     *
     * This method behaves like ByteBuffer.hashCode()
     * if this DataBuffer is only used bytewise, i.e.
     * if its bit position is a multiple of 8.
     *
     * The hash code of a DataBuffer depends only upon its remaining bytes,
     * which includes its whole first byte even if not all of its bits
     * are remaining, for hashCode not to depend on DataBuffer's order
     * (which influences the order in which bits are taken into account).
     *
     * Because DataBuffer hash codes are content-dependent, it is inadvisable
     * to use DataBuffers as keys in hash maps or similar data structures unless
     * it is known that their contents will not change.
     *
     * @return The current hash code of this DataBuffer.
     */
    @Override
    public int hashCode() {
        int h = 1;
        final int posInf = this.positionInf();
        int i = this.limit - this.positionInf();
        if (this.ba != null) {
            // Using helper is about as fast for byte arrays,
            // but much slower if tab is a ByteBuffer.
            final byte[] array = this.ba;
            final int op = this.baOffset + posInf;
            while (--i >= 0) {
                h = 31 * h + array[op+i];
            }
        } else {
            final int op = this.tabOffset + posInf;
            if (this.bigEndian) {
                while (i > 7) {
                    i -= 8;
                    h = hashCode_bigEndian(h, tabHelper.get64Bits_bigEndian(tab, op+i));
                }
            } else {
                while (i > 7) {
                    i -= 8;
                    h = hashCode_littleEndian(h, tabHelper.get64Bits_littleEndian(tab, op+i));
                }
            }
            while (--i >= 0) {
                h = 31 * h + this.tabHelper.get8Bits(this.tab, op+i);
            }
        }
        return h;
    }

    /**
     * Tells whether or not this DataBuffer is equal to another object.
     *
     * Two DataBuffer are equal if, and only if, they have the same number
     * of remaining bits, and their remaining bytes are identical, including
     * their whole first byte even if not all of its bits are remaining, for
     * equality not to depend on DataBuffer's order (which influences the order
     * in which bits are taken into account).
     *
     * A DataBuffer is not equal to any other type of object.
     *
     * @param other An object.
     * @return True if this DataBuffer is equal to the specified object,
     *         false otherwise.
     * @throws NullPointerException if the specified object is null.
     */
    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof DataBuffer)) {
            return false;
        }
        final DataBuffer ozer = (DataBuffer)other;
        if (this.bitsRemaining() != ozer.bitsRemaining()) {
            return false;
        }
        return (this.compareTo(ozer) == 0);
    }

    /**
     * Compares this DataBuffer to another.
     * 
     * This method behaves like ByteBuffer.compareTo(ByteBuffer)
     * (except it might return different negative or positive values)
     * if this DataBuffer is only used bytewise, i.e.
     * if its bit position is a multiple of 8.
     *
     * Two DataBuffer are compared by comparing, lexicographically,
     * their bytes from positionInf() up to first reached limit.
     * The first byte is compared fully, even if some of its bits
     * are not remaining, for comparison not to depend on DataBuffer's
     * order (which influences the order in which bits are taken into account).
     * 
     * A DataBuffer is not comparable to any other type of object.
     *
     * @return A negative integer, zero, or a positive integer as
     *         this DataBuffer is less than, equal to, or greater
     *         than the specified DataBuffer.
     * @throws NullPointerException if the specified DataBuffer is null.
     */
    @Override
    public int compareTo(DataBuffer other) {
        if (other == this) {
            return 0;
        }

        DataBuffer ozer = other;

        final boolean thisHasArrayWritableOrNot = (this.ba != null);
        final boolean ozerHasArrayWritableOrNot = (ozer.ba != null);

        final int p1 = this.positionInf();
        final int p2 = ozer.positionInf();
        final int cmpByteSize = Math.min(this.limit-p1,ozer.limit-p2);
        int i = 0;

        /*
         * 
         */

        final BaseBTHelper helper1;
        final Object tab1;
        final int op1;

        final BaseBTHelper helper2;
        final Object tab2;
        final int op2;

        if (thisHasArrayWritableOrNot) {
            helper1 = BA_HELPER;
            tab1 = this.ba;
            op1 = this.baOffset + p1;
        } else {
            helper1 = this.tabHelper;
            tab1 = this.tab;
            op1 = this.tabOffset + p1;
        }

        if (ozerHasArrayWritableOrNot) {
            helper2 = BA_HELPER;
            tab2 = ozer.ba;
            op2 = ozer.baOffset + p2;
        } else {
            helper2 = ozer.tabHelper;
            tab2 = ozer.tab;
            op2 = ozer.tabOffset + p2;
        }

        /*
         * First, if not having access to both byte arrays,
         * quick scan of identical content using comparison on longs.
         * 
         * Note: here, like in other places, we suppose that ByteBuffer's helper
         * methods implying a specific order in their signature, actually use ByteBuffer's order,
         * which is supposed to be equal to DataBuffer's order.
         * 
         * This treatment could be simplified some, for we could consider that if
         * no array is accessible, that means we have a ByteBuffer, for which
         * big endian or little endian helper methods actually use ByteBuffer's
         * (i.e. the DataBuffer's) order, but that would not work for some tests
         * that could use hacky helpers.
         */

        boolean compareChunks = true;
        boolean compareDiffOrder = false;
        boolean compareSameOrderBigEndian = false;
        if (thisHasArrayWritableOrNot) {
            if (ozerHasArrayWritableOrNot) {
                compareChunks = false;
                // Orders not used.
            } else {
                // Using other's byte order, whether or not it has a ByteBuffer.
                compareSameOrderBigEndian = ozer.bigEndian;
            }
        } else {
            if (ozerHasArrayWritableOrNot) {
                // Using this's order.
                compareSameOrderBigEndian = this.bigEndian;
            } else if (this.bigEndian == ozer.bigEndian) {
                // Using buffer's order.
                compareSameOrderBigEndian = this.bigEndian;
            } else {
                compareDiffOrder = true;
            }
        }

        if (compareChunks) {
            if (compareDiffOrder) {
                if (this.bigEndian) {
                    while (i < cmpByteSize-7) {
                        if (!equalsOppositeOrder(helper1.get64Bits_bigEndian(tab1,op1+i), helper2.get64Bits_littleEndian(tab2,op2+i))) {
                            // Will terminate with loop comparing bytes.
                            break;
                        }
                        i += 8;
                    }
                } else {
                    while (i < cmpByteSize-7) {
                        if (!equalsOppositeOrder(helper1.get64Bits_littleEndian(tab1,op1+i), helper2.get64Bits_bigEndian(tab2,op2+i))) {
                            // Will terminate with loop comparing bytes.
                            break;
                        }
                        i += 8;
                    }
                }
            } else {
                if ((tab1 == tab2) && (op1 == op2)) {
                    // No need to iterate (supposing helpers are equivalent).
                } else {
                    if (compareSameOrderBigEndian) {
                        while (i < cmpByteSize-7) {
                            if (helper1.get64Bits_bigEndian(tab1,op1+i) != helper2.get64Bits_bigEndian(tab2,op2+i)) {
                                // Will terminate with loop comparing bytes.
                                break;
                            }
                            i += 8;
                        }
                    } else {
                        while (i < cmpByteSize-7) {
                            if (helper1.get64Bits_littleEndian(tab1,op1+i) != helper2.get64Bits_littleEndian(tab2,op2+i)) {
                                // Will terminate with loop comparing bytes.
                                break;
                            }
                            i += 8;
                        }
                    }
                }
            }
        }

        /*
         * 
         */

        if ((tab1 == tab2) && (op1 == op2)) {
            // No need to iterate (supposing helpers are equivalent).
        } else {
            // No need to use byte arrays directly if available,
            // since if any is available, we use helper for byte arrays
            // on it, which is about as fast as direct use of the byte array
            // (most likely due to an "easy" optimization).
            while (i < cmpByteSize) {
                final byte v1 = helper1.get8Bits(tab1,op1+i);
                final byte v2 = helper2.get8Bits(tab2,op2+i);
                if (v1 != v2) {
                    return (v1 < v2) ? -1 : 1;
                }
                ++i;
            }
        }
        final long brem1 = this.bitsRemaining();
        final long brem2 = other.bitsRemaining();
        return (brem1 < brem2) ? -1 : ((brem1 == brem2) ? 0 : 1);
    }

    /*
     * 
     */

    /**
     * Note that if setBackingBuffer(ByteBuffer) method is used with
     * a read-only ByteBuffer, this DataBuffer is still considered writable
     * by this method, even though its backing ByteBuffer is not (and
     * we count on it to throw ReadOnlyBufferException if ever one
     * tries to write in it).
     * 
     * @return True if this DataBuffer is read-only, false otherwise.
     */
    public boolean isReadOnly() {
        return false;
    }

    /**
     * @return True if this DataBuffer is direct.
     */
    public boolean isDirect() {
        ByteBuffer bb = this.bb;
        return (bb != null) && bb.isDirect();
    }

    /*
     * 
     */

    /**
     * @return True if this buffer is backed by an accessible byte array,
     *         either directly or through a ByteBuffer, and is not read-only,
     *         false otherwise.
     */
    public boolean hasArray() {
        // No need to test if backing ByteBuffer is read-only,
        // for if this DataBuffer is writable, we don't have access
        // to its array (if any), and this method is overriden
        // for read-only DataBuffers.
        return (this.ba != null);
    }

    /**
     * @return The array that backs this DataBuffer.
     * @throws UnsupportedOperationException if this DataBuffer is
     *         not backed by an accessible array.
     * @throws ReadOnlyBufferException if this DataBuffer is
     *         backed by an accessible array but is read-only.
     */
    public byte[] array() {
        byte[] ba = this.ba;
        if (ba == null) {
            throw new UnsupportedOperationException();
        }
        return ba;
    }

    /**
     * @return Index of the first byte of this DataBuffer in its backing array.
     * @throws UnsupportedOperationException if this DataBuffer is
     *         not backed by an accessible array.
     * @throws ReadOnlyBufferException if this DataBuffer is
     *         backed by an accessible array but is read-only.
     */
    public int arrayOffset() {
        if (this.ba == null) {
            throw new UnsupportedOperationException();
        }
        return this.baOffset;
    }

    /**
     * @return True if this buffer is backed by a ByteBuffer,
     *         false otherwise.
     */
    public boolean hasByteBuffer() {
        return (this.bb != null);
    }

    /**
     * @return The ByteBuffer that backs this buffer, if any.
     * @throws UnsupportedOperationException if this buffer is
     *         not backed by a ByteBuffer.
     */
    public ByteBuffer byteBuffer() {
        if (SPECIFIC_OPTIM) {
            ByteBuffer bb = this.bb;
            if (bb == null) {
                throw new UnsupportedOperationException();
            }
            return bb;
        } else {
            try {
                return (ByteBuffer)this.tab;
            } catch (ClassCastException e) {
                throw new UnsupportedOperationException();
            }
        }
    }

    /*
     * 
     */

    /**
     * Sets this buffer's mark at its position.
     * 
     * @return This DataBuffer.
     */
    public DataBuffer mark() {
        this.bitMark = this.bitPosition;
        return this;
    }

    /**
     * Resets this buffer's bitPosition to the previously-marked bitPosition.
     *
     * @return This DataBuffer.
     * @throws InvalidMarkException if the mark has not been set.
     */
    public DataBuffer reset() {
        final long m = this.bitMark;
        if (m < 0) {
            throw new InvalidMarkException();
        }
        this.bitPosition = m;
        return this;
    }

    /**
     * Sets limit to capacity,
     * bitPosition to zero,
     * and clears the mark if any.
     * 
     * @return This DataBuffer.
     */
    public DataBuffer clear() {
        this.limit(this.capacity);
        this.bitPosition = 0;
        this.bitMark = -1;
        return this;
    }

    /**
     * Sets limit to bytePositionSup(),
     * then bitPosition to zero,
     * and clears the mark if any.
     * 
     * @return This DataBuffer.
     */
    public DataBuffer flip() {
        this.limit(this.positionSup());
        this.bitPosition = 0;
        this.bitMark = -1;
        return this;
    }

    /**
     * Sets bitPosition to zero,
     * and clears the mark if any.
     * 
     * @return This DataBuffer.
     */
    public DataBuffer rewind() {
        this.bitPosition = 0;
        this.bitMark = -1;
        return this;
    }

    /**
     * Copies the bits in [bitPosition,bitLimit-1]
     * into [0,bitLimit-1-bitPosition] range,
     * sets bitPosition to bitLimit-bitPosition,
     * sets limit to capacity,
     * and clears the mark if any.
     * 
     * Unlike for ByteBuffer.compact(), the effect of this method can be
     * order-dependent, more specifically it is order-dependent if bit position
     * is not a multiple of 8, since depending on order bit position designates
     * a different bit.
     * 
     * This method behaves like ByteBuffer.compact()
     * if this DataBuffer is only used bytewise, i.e.
     * if its bit position is a multiple of 8.
     * 
     * Useful in case this DataBuffer's content has been only partially written
     * to some output, and we want to keep data that remains to be written before
     * adding more into this DataBuffer.
     * 
     * Can be way slower than ByteBuffer.compact(),
     * since here the compacting operation is bitwise.
     * 
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     */
    public DataBuffer compact() {
        final long bitSize = this.bitLimit()-this.bitPosition;
        if (bitSize != 0) {
            bufferCopyBits(this, this.bitPosition, this, 0, bitSize);
        }
        this.limit(this.capacity);
        this.bitPosition(bitSize);
        this.bitMark = -1;
        return this;
    }

    /*
     * 
     */

    /**
     * @return Order in which bytes are put in the buffer,
     *         which also determines order in which bits are put.
     */
    public ByteOrder order() {
        return this.bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
    }

    /**
     * If this buffer is backed by a ByteBuffer, sets its
     * order to the same value, for ByteBuffer's put/get methods
     * to use the same order.
     * Note that order of backing's ByteBuffer (if any) is not ensured
     * to be identical to this buffer's order on each put/get operation,
     * so you should not make order of backing's ByteBuffer (if any) different
     * from this order while using it through this buffer.
     * 
     * This method throws an exception if bit position is not a multiple of 8,
     * to prevent irrelevant order change while writing or reading data.
     * You can use orderIgnoreBitPosition(ByteOrder) if this exception bothers you.
     * 
     * @param order Order in which bytes are put in the buffer,
     *        which also determines order in which bits are put.
     * @return This DataBuffer.
     * @throws IllegalStateException if changing order while bitPosition is
     *         not a multiple of 8, i.e. if endianness would change inside of a byte.
     */
    public DataBuffer order(ByteOrder order) {
        if (order != this.order()) {
            LangUtils.checkNonNull(order);
            if ((this.bitPosition&7) != 0) {
                throw new IllegalStateException("changing endianness inside of a byte");
            }
            this.bigEndian = (order == ByteOrder.BIG_ENDIAN);
            // Setting order in case the tab holds it,
            // to allow for use of tab's class methods
            // on int/long/etc.
            if (SPECIFIC_OPTIM) {
                if (this.bb != null) {
                    this.bb.order(order);
                }
            } else {
                this.tabHelper.order(this.tab, order);
            }
        }
        return this;
    }

    /**
     * Equivalent to order(ByteOrder), except that is does not
     * throw IllegalStateException if bit position is not a
     * multiple of 8.
     * This method is useful when using put/get "at" methods,
     * i.e. when not making use of bit position.
     * 
     * @param order Order in which bytes are put in the buffer,
     *        which also determines order in which bits are put.
     * @return This DataBuffer.
     */
    public DataBuffer orderIgnoreBitPosition(ByteOrder order) {
        if (order != this.order()) {
            LangUtils.checkNonNull(order);
            this.bigEndian = (order == ByteOrder.BIG_ENDIAN);
            if (SPECIFIC_OPTIM) {
                if (this.bb != null) {
                    this.bb.order(order);
                }
            } else {
                this.tabHelper.order(this.tab, order);
            }
        }
        return this;
    }

    /*
     * 
     */

    /**
     * @return bitLimit - bitPosition.
     */
    public long bitsRemaining() {
        return this.bitLimit() - this.bitPosition;
    }

    /**
     * @return True if bitPosition < bitLimit,
     *         false otherwise.
     */
    public boolean hasBitsRemaining() {
        return this.bitPosition < this.bitLimit();
    }

    /**
     * @return Max value for limit.
     */
    public int capacity() {
        return this.capacity;
    }

    /**
     * @return Bit capacity, which is a multiple of 8.
     */
    public long bitCapacity() {
        return (((long)this.capacity)<<3);
    }

    /**
     * @return Position of the first byte that should not be read or written.
     */
    public int limit() {
        return this.limit;
    }

    /**
     * @return Bit limit, which is a multiple of 8.
     */
    public long bitLimit() {
        return (((long)this.limit)<<3);
    }

    /**
     * If bit position is strictly superior to the specified byte limit * 8,
     * sets it to the specified byte limit * 8.
     * 
     * If the mark exists and is strictly superior
     * to the specified byte limit * 8, clears it.
     * 
     * If this buffer is backed by a ByteBuffer, sets its
     * byte limit accordingly, for ByteBuffer's put/get methods
     * to work properly on [0,limit[ byte range.
     * Note that limit of backing's ByteBuffer (if any) is not ensured
     * to be identical to this buffer's limit on each put/get operation,
     * so you should not make limit of backing's ByteBuffer (if any) different
     * from this limit while using it through this buffer.
     * 
     * @param newLimit A byte limit in [0,capacity].
     * @return This DataBuffer.
     * @throws IllegalArgumentException if the specified byte limit is not in [0,capacity].
     */
    public DataBuffer limit(int newLimit) {
        if (!NumbersUtils.isInRange(0, this.capacity, newLimit)) {
            throw new IllegalArgumentException("byte limit ["+newLimit+"] must be in [0,"+this.capacity+"]");
        }
        this.limit = newLimit;
        if (SPECIFIC_OPTIM) {
            if (this.bb != null) {
                this.bb.limit(newLimit);
            }
        } else {
            this.tabHelper.limit(this.tab, newLimit);
        }
        final long bitLimit = this.bitLimit();
        if (this.bitPosition > bitLimit) {
            this.bitPosition = bitLimit;
        }
        if (this.bitMark > bitLimit) {
            this.bitMark = -1;
        }
        return this;
    }

    /**
     * @return Position of the next bit to be read or written.
     */
    public long bitPosition() {
        return this.bitPosition;
    }

    /**
     * @return Index of the byte for bit at bitPosition position,
     *         i.e. the byte that contains next bit to read or write,
     *         i.e. bitPosition/8.
     */
    public int positionInf() {
        return (int)(this.bitPosition>>3);
    }

    /**
     * @return Index of the first byte which all bits are >= bitPosition,
     *         i.e. the first byte which complete write would not erase
     *         already written bits, i.e. (bitPosition+7)/8.
     */
    public int positionSup() {
        return (int)((this.bitPosition+7)>>3);
    }

    /**
     * If the mark exists and is strictly superior
     * to the specified bit position, clears it.
     * 
     * @param newBitPosition A bit position in [0,bitLimit].
     * @return This DataBuffer.
     * @throws IllegalArgumentException if the specified bit position is not in [0,bitLimit].
     */
    public DataBuffer bitPosition(long newBitPosition) {
        if (!NumbersUtils.isInRange(0, this.bitLimit(), newBitPosition)) {
            throw new IllegalArgumentException("bit position ["+newBitPosition+"] must be in [0,"+this.bitLimit()+"]");
        }
        this.bitPosition = newBitPosition;
        if (this.bitMark > newBitPosition) {
            this.bitMark = -1;
        }
        return this;
    }

    /*
     * bulk put
     */

    /**
     * Equivalent to put(src,0,src.length).
     */
    public DataBuffer put(byte[] src) {
        return this.put(src, 0, src.length);
    }

    /**
     * Puts the bytes int [offset,offset+length[ range in the specified byte array,
     * into this DataBuffer, moving bit position accordingly.
     * 
     * If this DataBuffer is backed by a ByteBuffer, and that current
     * bit position is a multiple of 8, ByteBuffer.put(byte[],int,int)
     * is used (which is fast in case of direct ByteBuffer). In that case,
     * the ByteBuffer position is set for put method to behave properly.
     * 
     * Unlike for ByteBuffer.put(byte[],int,int), this method is not necessarily
     * equivalent to a loop putting bytes one by one by incrementing indexes,
     * which might lead to different behavior if the specified byte array
     * backs this DataBuffer.
     * 
     * @param src Byte array containing bytes to put.
     * @param offset Index, in the specified byte array, of the first byte to put.
     * @param length Number of bytes to put.
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws NullPointerException if the specified byte array is null.
     * @throws IndexOutOfBoundsException if offset < 0, or length < 0, or
     *         offset+length overflows, or offset+length > src.length.
     * @throws BufferOverflowException if there are not enough remaining bits
     *         for this operation.
     */
    public DataBuffer put(byte[] src, int offset, int length) {
        LangUtils.checkBounds(src.length, offset, length);
        if (length == 0) {
            return this;
        }
        final long bitSize = length * 8L;
        if (bitSize > this.bitsRemaining()) {
            throw new BufferOverflowException();
        }

        final boolean byteAligned = ((this.bitPosition&7) == 0);

        // Only using ByteBuffer.put(byte[],int,int) for byte-aligned copy
        // if the ByteBuffer is direct, else our implementation should be faster.
        final boolean useBBPut = (byteAligned && this.isDirect());
        if (useBBPut) {
            final ByteBuffer bb = this.byteBuffer();
            // Position used by put.
            bb.position(this.positionInf());
            // Fast if BB is direct.
            bb.put(src, offset, length);
        } else {
            final long srcFirstBitPos = offset * 8L;
            final BaseBTHelper dstTabHelper;
            final Object dstTab;
            final int dstTabOffset;
            if (this.ba != null) {
                dstTabHelper = BA_HELPER;
                dstTab = this.ba;
                dstTabOffset = this.baOffset;
            } else {
                dstTabHelper = this.tabHelper;
                dstTab = this.tab;
                dstTabOffset = this.tabOffset;
            }
            if (byteAligned) {
                ByteTabUtils.tabCopyBits_noCheck_bitShiftMultipleOf8(
                        BA_HELPER,
                        src,
                        srcFirstBitPos,
                        dstTabHelper,
                        dstTab,
                        (((long)dstTabOffset)<<3)+this.bitPosition,
                        bitSize,
                        this.bigEndian);
            } else {
                ByteTabUtils.tabCopyBits_noCheck_general(
                        BA_HELPER,
                        src,
                        srcFirstBitPos,
                        dstTabHelper,
                        dstTab,
                        (((long)dstTabOffset)<<3)+this.bitPosition,
                        bitSize,
                        this.bigEndian);
            }
        }

        this.bitPosition += bitSize;
        return this;
    }

    /*
     * bulk get
     */

    /**
     * Equivalent to get(dst,0,dst.length).
     */
    public DataBuffer get(byte[] dst) {
        return this.get(dst, 0, dst.length);
    }

    /**
     * Puts the bytes int [offset,offset+length[ range in the specified byte array,
     * into this DataBuffer, moving bit position accordingly.
     * 
     * If this DataBuffer is backed by a ByteBuffer, and that current
     * bit position is a multiple of 8, ByteBuffer.put(byte[],int,int)
     * is used (which is fast in case of direct ByteBuffer). In that case,
     * the ByteBuffer position is set for put method to behave properly.
     * 
     * Unlike for ByteBuffer.put(byte[],int,int), this method is not necessarily
     * equivalent to a loop putting bytes one by one by incrementing indexes,
     * which might lead to different behavior if the specified byte array
     * backs this DataBuffer.
     * 
     * @param dst Byte array where to put retrieved bytes.
     * @param offset Index, in the specified byte array, where to put the first retrieved byte.
     * @param length Number of bytes to get.
     * @return This DataBuffer.
     * @throws NullPointerException if the specified byte array is null.
     * @throws IndexOutOfBoundsException if offset < 0, or length < 0,
     *         or offset+length overflows, or offset+length > dst.length.
     * @throws BufferUnderflowException if there are not enough remaining bits
     *         for this operation.
     */
    public DataBuffer get(byte[] dst, int offset, int length) {
        LangUtils.checkBounds(dst.length, offset, length);
        if (length == 0) {
            return this;
        }
        final long bitSize = length * 8L;
        if (bitSize > this.bitsRemaining()) {
            throw new BufferUnderflowException();
        }

        final boolean byteAligned = ((this.bitPosition&7) == 0);

        // Only using ByteBuffer.get(byte[],int,int) for byte-aligned copy
        // if the ByteBuffer is direct, else our implementation should be faster.
        final boolean useBBGet = (byteAligned && this.hasByteBuffer() && this.isDirect());
        if (useBBGet) {
            final ByteBuffer bb = this.byteBuffer();
            // Position used by get.
            bb.position(this.positionInf());
            // Fast if BB is direct.
            bb.get(dst, offset, length);
        } else {
            final long dstFirstBitPos = offset * 8L;
            final BaseBTHelper srcTabHelper;
            final Object srcTab;
            final int srcTabOffset;
            if (this.ba != null) {
                srcTabHelper = BA_HELPER;
                srcTab = this.ba;
                srcTabOffset = this.baOffset;
            } else {
                srcTabHelper = this.tabHelper;
                srcTab = this.tab;
                srcTabOffset = this.tabOffset;
            }
            if (byteAligned) {
                ByteTabUtils.tabCopyBits_noCheck_bitShiftMultipleOf8(
                        srcTabHelper,
                        srcTab,
                        (((long)srcTabOffset)<<3)+this.bitPosition,
                        BA_HELPER,
                        dst,
                        dstFirstBitPos,
                        bitSize,
                        this.bigEndian);
            } else {
                ByteTabUtils.tabCopyBits_noCheck_general(
                        srcTabHelper,
                        srcTab,
                        (((long)srcTabOffset)<<3)+this.bitPosition,
                        BA_HELPER,
                        dst,
                        dstFirstBitPos,
                        bitSize,
                        this.bigEndian);
            }
        }

        this.bitPosition += bitSize;
        return this;
    }

    /*
     * put at bytewise
     */

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IndexOutOfBoundsException if specified byte is out of range.
     */
    public DataBuffer putByteAt(int index, byte value) {
        if (SPECIFIC_OPTIM) {
            if (this.ba != null) {
                LangUtils.checkBounds(this.limit, index, 1);
                this.ba[this.baOffset+index] = value;
            } else {
                this.bb.put(index, value);
            }
        } else {
            this.tabHelper.checkLimitByteIndexByteSizeIfNeeded(this.limit, index, 1);
            this.tabHelper.put8Bits(this.bb, index, value);
        }
        return this;
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public DataBuffer putShortAt(int index, short value) {
        if (SPECIFIC_OPTIM) {
            if (this.ba != null) {
                LangUtils.checkBounds(this.limit, index, 2);
                BA_HELPER.put16Bits(this.ba, this.baOffset+index, value, this.bigEndian);
            } else {
                this.bb.putShort(index, value);
            }
        } else {
            this.tabHelper.checkLimitByteIndexByteSizeIfNeeded(this.limit, index, 2);
            this.tabHelper.put16Bits(this.tab, index, value, this.bigEndian);
        }
        return this;
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public DataBuffer putCharAt(int index, char value) {
        this.putShortAt(index, (short)value);
        return this;
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public DataBuffer putIntAt(int index, int value) {
        if (SPECIFIC_OPTIM) {
            if (this.ba != null) {
                LangUtils.checkBounds(this.limit, index, 4);
                BA_HELPER.put32Bits(this.ba, this.baOffset+index, value, this.bigEndian);
            } else {
                this.bb.putInt(index, value);
            }
        } else {
            this.tabHelper.checkLimitByteIndexByteSizeIfNeeded(this.limit, index, 4);
            this.tabHelper.put32Bits(this.tab, index, value, this.bigEndian);
        }
        return this;
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public DataBuffer putLongAt(int index, long value) {
        if (SPECIFIC_OPTIM) {
            if (this.ba != null) {
                LangUtils.checkBounds(this.limit, index, 8);
                BA_HELPER.put64Bits(this.ba, this.baOffset+index, value, this.bigEndian);
            } else {
                this.bb.putLong(index, value);
            }
        } else {
            this.tabHelper.checkLimitByteIndexByteSizeIfNeeded(this.limit, index, 8);
            this.tabHelper.put64Bits(this.tab, index, value, this.bigEndian);
        }
        return this;
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public DataBuffer putFloatAt(int index, float value) {
        return this.putIntAt(index, Float.floatToRawIntBits(value));
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public DataBuffer putDoubleAt(int index, double value) {
        return this.putLongAt(index, Double.doubleToRawLongBits(value));
    }

    /*
     * get at bytewise
     */

    /**
     * @throws IndexOutOfBoundsException if specified byte is out of range.
     */
    public byte getByteAt(int index) {
        if (SPECIFIC_OPTIM) {
            if (this.ba != null) {
                LangUtils.checkBounds(this.limit, index, 1);
                return this.ba[this.baOffset+index];
            } else {
                return this.bb.get(index);
            }
        } else {
            this.tabHelper.checkLimitByteIndexByteSizeIfNeeded(this.limit, index, 1);
            return this.tabHelper.get8Bits(this.tab, index);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public short getShortAt(int index) {
        if (SPECIFIC_OPTIM) {
            if (this.ba != null) {
                LangUtils.checkBounds(this.limit, index, 2);
                return BA_HELPER.get16Bits(this.ba, this.baOffset+index, this.bigEndian);
            } else {
                return this.bb.getShort(index);
            }
        } else {
            this.tabHelper.checkLimitByteIndexByteSizeIfNeeded(this.limit, index, 2);
            return this.tabHelper.get16Bits(this.tab, index, this.bigEndian);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public char getCharAt(int index) {
        return (char)this.getShortAt(index);
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public int getIntAt(int index) {
        if (SPECIFIC_OPTIM) {
            if (this.ba != null) {
                LangUtils.checkBounds(this.limit, index, 4);
                return BA_HELPER.get32Bits(this.ba, this.baOffset+index, this.bigEndian);
            } else {
                return this.bb.getInt(index);
            }
        } else {
            this.tabHelper.checkLimitByteIndexByteSizeIfNeeded(this.limit, index, 4);
            return this.tabHelper.get32Bits(this.tab, index, this.bigEndian);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public long getLongAt(int index) {
        if (SPECIFIC_OPTIM) {
            if (this.ba != null) {
                LangUtils.checkBounds(this.limit, index, 8);
                return BA_HELPER.get64Bits(this.ba, this.baOffset+index, this.bigEndian);
            } else {
                return this.bb.getLong(index);
            }
        } else {
            this.tabHelper.checkLimitByteIndexByteSizeIfNeeded(this.limit, index, 8);
            return this.tabHelper.get64Bits(this.tab, index, this.bigEndian);
        }
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public float getFloatAt(int index) {
        return Float.intBitsToFloat(this.getIntAt(index));
    }

    /**
     * @throws IndexOutOfBoundsException if specified bytes are out of range.
     */
    public double getDoubleAt(int index) {
        return Double.longBitsToDouble(this.getLongAt(index));
    }

    /*
     * put for whole primitive types
     */

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putByte(byte value) {
        try {
            ByteTabUtils.putIntAtBit_bitSizeChecked(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, value, 8, this.bigEndian);
            this.bitPosition += 8;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferOverflowException();
        }
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putShort(short value) {
        try {
            ByteTabUtils.putIntAtBit_bitSizeChecked(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, value, 16, this.bigEndian);
            this.bitPosition += 16;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferOverflowException();
        }
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putChar(char value) {
        return this.putShort((short)value);
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putInt(int value) {
        try {
            ByteTabUtils.putIntAtBit_bitSizeChecked(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, value, 32, this.bigEndian);
            this.bitPosition += 32;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferOverflowException();
        }
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putLong(long value) {
        try {
            ByteTabUtils.putLongAtBit_bitSizeChecked(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, value, 64, this.bigEndian);
            this.bitPosition += 64;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferOverflowException();
        }
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putFloat(float value) {
        return this.putInt(Float.floatToRawIntBits(value));
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putDouble(double value) {
        return this.putLong(Double.doubleToRawLongBits(value));
    }

    /*
     * get for whole primitive types
     */

    /**
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public byte getByte() {
        try {
            final byte result = (byte)ByteTabUtils.getIntSignedAtBit_bitSizeChecked(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, 8, this.bigEndian);
            this.bitPosition += 8;
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public short getShort() {
        try {
            final short result = (short)ByteTabUtils.getIntSignedAtBit_bitSizeChecked(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, 16, this.bigEndian);
            this.bitPosition += 16;
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public char getChar() {
        return (char)this.getShort();
    }

    /**
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public int getInt() {
        try {
            final int result = ByteTabUtils.getIntSignedAtBit_bitSizeChecked(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, 32, this.bigEndian);
            this.bitPosition += 32;
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public long getLong() {
        try {
            final long result = ByteTabUtils.getLongSignedAtBit_bitSizeChecked(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, 64, this.bigEndian);
            this.bitPosition += 64;
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public float getFloat() {
        return Float.intBitsToFloat(this.getInt());
    }

    /**
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public double getDouble() {
        return Double.longBitsToDouble(this.getLong());
    }

    /*
     * byte padding
     */

    /**
     * Sets bits in [bitPos,last_bit_of_bitPosMinus1_byte], if any, to zero.
     * 
     * @param bitPos Position of first bit to set to zero.
     * @return Number of bits padded, i.e. a value in [0,7].
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IndexOutOfBoundsException if the specified bit position is out of range.
     */
    public int padByteAtBit(long bitPos) {
        return ByteTabUtils.padByteAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, bitPos, this.bigEndian);
    }

    /**
     * Sets bits in [bitPosition,last_bit_of_bitPositionMinus1_byte], if any, to zero.
     * 
     * This method does not move bit position at end of padding,
     * for one might want to overwrite padding with some more data.
     * If you want bit position to be moved past padding, use
     * putBytePadding() instead.
     * 
     * @return Number of bits padded, i.e. a value in [0,7].
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     */
    public int padLastByte() {
        /*
         * Not using ByteTabUtils.padByteAtBit(...),
         * to avoid the unnecessary bounds checks
         * and always-false bytewise-case test.
         */
        final int bitPosInByte = ((int)this.bitPosition)&7;
        if (bitPosInByte != 0) {
            final int bitSize = 8-bitPosInByte;
            ByteTabUtils.putIntAtBit_noCheck(this.tabHelper,this. tab, (((long)this.tabOffset)<<3)+this.bitPosition, 0, bitSize, this.bigEndian);
            return bitSize;
        } else {
            return 0;
        }
    }

    /**
     * Sets bits in [bitPos,last_bit_of_bitPosMinus1_byte], if any, to zero,
     * and adds the number of padded bits to bit position.
     * As a result, after call to this method, bit position is a multiple of 8.
     * 
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     */
    public DataBuffer putBytePadding() {
        final int bitPosInByte = ((int)this.bitPosition)&7;
        if (bitPosInByte != 0) {
            final int bitSize = 8-bitPosInByte;
            ByteTabUtils.putIntAtBit_noCheck(this.tabHelper,this. tab, (((long)this.tabOffset)<<3)+this.bitPosition, 0, bitSize, this.bigEndian);
            this.bitPosition += bitSize;
        }
        return this;
    }

    /*
     * bit
     */

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IndexOutOfBoundsException if the specified bit is out of range.
     */
    public DataBuffer putBitAtBit(long bitPos, boolean value) {
        ByteTabUtils.putBitAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, bitPos, value, this.bigEndian);
        return this;
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putBit(boolean value) {
        try {
            ByteTabUtils.putBitAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, value, this.bigEndian);
            this.bitPosition++;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferOverflowException();
        }
    }

    /**
     * @throws IndexOutOfBoundsException if the specified bit is out of range.
     */
    public boolean getBitAtBit(long bitPos) {
        return ByteTabUtils.getBitAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, bitPos, this.bigEndian);
    }

    /**
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public boolean getBit() {
        try {
            final boolean result = ByteTabUtils.getBitAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, this.bigEndian);
            this.bitPosition++;
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferUnderflowException();
        }
    }

    /*
     * put at bitwise
     */

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public DataBuffer putIntSignedAtBit(long firstBitPos, int value, int bitSize) {
        ByteTabUtils.putIntSignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, firstBitPos, value, bitSize, this.bigEndian);
        return this;
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public DataBuffer putLongSignedAtBit(long firstBitPos, long value, int bitSize) {
        ByteTabUtils.putLongSignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, firstBitPos, value, bitSize, this.bigEndian);
        return this;
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public DataBuffer putIntUnsignedAtBit(long firstBitPos, int value, int bitSize) {
        ByteTabUtils.putIntUnsignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, firstBitPos, value, bitSize, this.bigEndian);
        return this;
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public DataBuffer putLongUnsignedAtBit(long firstBitPos, long value, int bitSize) {
        ByteTabUtils.putLongUnsignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, firstBitPos, value, bitSize, this.bigEndian);
        return this;
    }

    /*
     * put bitwise
     */

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putIntSigned(int value, int bitSize) {
        try {
            ByteTabUtils.putIntSignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, value, bitSize, this.bigEndian);
            this.bitPosition += bitSize;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferOverflowException();
        }
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as a signed integer over the specified number of bits.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putLongSigned(long value, int bitSize) {
        try {
            ByteTabUtils.putLongSignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, value, bitSize, this.bigEndian);
            this.bitPosition += bitSize;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferOverflowException();
        }
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putIntUnsigned(int value, int bitSize) {
        try {
            ByteTabUtils.putIntUnsignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, value, bitSize, this.bigEndian);
            this.bitPosition += bitSize;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferOverflowException();
        }
    }

    /**
     * @return This DataBuffer.
     * @throws ReadOnlyBufferException if this DataBuffer is read-only.
     * @throws IllegalArgumentException if the specified value does not fit
     *         as an unsigned integer over the specified number of bits.
     * @throws BufferOverflowException if this put would move position after this DataBuffer's limit.
     */
    public DataBuffer putLongUnsigned(long value, int bitSize) {
        try {
            ByteTabUtils.putLongUnsignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, value, bitSize, this.bigEndian);
            this.bitPosition += bitSize;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferOverflowException();
        }
    }

    /*
     * get at bitwise
     */

    /**
     * @throws IllegalArgumentException if a signed int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,32].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public int getIntSignedAtBit(long firstBitPos, int bitSize) {
        return ByteTabUtils.getIntSignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, firstBitPos, bitSize, this.bigEndian);
    }

    /**
     * @throws IllegalArgumentException if a signed long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,64].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public long getLongSignedAtBit(long firstBitPos, int bitSize) {
        return ByteTabUtils.getLongSignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, firstBitPos, bitSize, this.bigEndian);
    }

    /**
     * @throws IllegalArgumentException if an unsigned int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,31].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public int getIntUnsignedAtBit(long firstBitPos, int bitSize) {
        return ByteTabUtils.getIntUnsignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, firstBitPos, bitSize, this.bigEndian);
    }

    /**
     * @throws IllegalArgumentException if an unsigned long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,63].
     * @throws IndexOutOfBoundsException if specified bits are out of range.
     */
    public long getLongUnsignedAtBit(long firstBitPos, int bitSize) {
        return ByteTabUtils.getLongUnsignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, firstBitPos, bitSize, this.bigEndian);
    }

    /*
     * get bitwise
     */

    /**
     * @throws IllegalArgumentException if a signed int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,32].
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public int getIntSigned(int bitSize) {
        try {
            final int result = ByteTabUtils.getIntSignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, bitSize, this.bigEndian);
            this.bitPosition += bitSize;
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * @throws IllegalArgumentException if a signed long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,64].
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public long getLongSigned(int bitSize) {
        try {
            final long result = ByteTabUtils.getLongSignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, bitSize, this.bigEndian);
            this.bitPosition += bitSize;
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * @throws IllegalArgumentException if an unsigned int value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,31].
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public int getIntUnsigned(int bitSize) {
        try {
            final int result = ByteTabUtils.getIntUnsignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, bitSize, this.bigEndian);
            this.bitPosition += bitSize;
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * @throws IllegalArgumentException if an unsigned long value can't be read over the
     *         specified number of bits, i.e. if it is not in [1,63].
     * @throws BufferUnderflowException if this get would move position after this DataBuffer's limit.
     */
    public long getLongUnsigned(int bitSize) {
        try {
            final long result = ByteTabUtils.getLongUnsignedAtBit(this.tabHelper, this.tab, this.tabOffset, this.limit, this.bitPosition, bitSize, this.bigEndian);
            this.bitPosition += bitSize;
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw new BufferUnderflowException();
        }
    }

    /*
     * buffer copy
     */

    /**
     * Equivalent to calling bufferCopyBits(...) version with "* 8L"
     * applied on indexes and size arguments.
     * 
     * If your DataBuffers are backed by ByteBuffers, you might want to use
     * some ByteCopyUtils methods on these ByteBuffers instead, see
     * ByteBufferUtils.bufferCopy(...) Javadoc.
     * 
     * @param src The source buffer.
     * @param srcFirstByteIndex Starting byte index in the source buffer.
     * @param dst The destination buffer.
     * @param dstFirstByteIndex Starting byte index in the destination buffer.
     * @param byteSize The number of bytes to copy.
     * @throws NullPointerException if either src or dst is null.
     * @throws ReadOnlyBufferException if dst is read-only and bitSize > 0.
     * @throws IllegalArgumentException if src and dst don't have the same byte order
     *         or if bitSize < 0.
     * @throws IndexOutOfBoundsException if the specified bits are out of range.
     */
    public static void bufferCopy(
            DataBuffer src,
            int srcFirstByteIndex,
            DataBuffer dst,
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
     *         or if bitSize < 0.
     * @throws IndexOutOfBoundsException if the specified bits are out of range.
     */
    public static void bufferCopyBits(
            DataBuffer src,
            long srcFirstBitPos,
            DataBuffer dst,
            long dstFirstBitPos,
            long bitSize) {
        // Implicit null checks.
        final boolean srcBigEndian = src.bigEndian;
        final boolean dstBigEndian = dst.bigEndian;

        if (dst.isReadOnly() && (bitSize > 0)) {
            throw new ReadOnlyBufferException();
        }

        if (srcBigEndian != dstBigEndian) {
            throw new IllegalArgumentException("src order ["+src.order()+"] != dst order ["+dst.order()+"]");
        }

        final BaseBTHelper srcTabHelper;
        final Object srcTab;
        final int srcTabOffset;
        if (src.ba != null) {
            srcTabHelper = BA_HELPER;
            srcTab = src.ba;
            srcTabOffset = src.baOffset;
        } else {
            srcTabHelper = src.tabHelper;
            srcTab = src.tab;
            srcTabOffset = src.tabOffset;
        }

        final BaseBTHelper dstTabHelper;
        final Object dstTab;
        final int dstTabOffset;
        if (dst.ba != null) {
            dstTabHelper = BA_HELPER;
            dstTab = dst.ba;
            dstTabOffset = dst.baOffset;
        } else {
            dstTabHelper = dst.tabHelper;
            dstTab = dst.tab;
            dstTabOffset = dst.tabOffset;
        }

        ByteTabUtils.tabCopyBits(
                srcTabHelper,
                srcTab,
                srcTabOffset,
                src.bitLimit(),
                srcFirstBitPos,
                dstTabHelper,
                dstTab,
                dstTabOffset,
                dst.bitLimit(),
                dstFirstBitPos,
                bitSize,
                srcBigEndian);
    }

    /*
     * toString
     */

    /**
     * @return A string summarizing the state of this buffer.
     */
    @Override
    public String toString() {
        /*
         * Similar to ByteBuffer's toString.
         */
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName());
        sb.append("[bit pos=");
        sb.append(this.bitPosition);
        sb.append(" bit lim=");
        sb.append(this.bitLimit());
        sb.append(" bit cap=");
        sb.append(this.bitCapacity());
        sb.append("]");
        return sb.toString();
    }

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
    public String toString(int firstByteIndex, int byteSize, int radix) {
        return ByteTabUtils.toString(this.tabHelper, this.tab, this.tabOffset, this.limit, firstByteIndex, byteSize, radix);
    }

    /**
     * toString with digits in binary format only, to display bits, but with custom endianness.
     * 
     * @param firstBitPos Position of the first bit to put in resulting string.
     * @param bitSize Number of bits to put in resulting string.
     * @param bigEndian Whether the bits should be displayed in big endian or
     *                  little endian order (regardless of this buffer's order).
     * @return A string containing the specified data range in the specified format.
     */
    public String toStringBits(long firstBitPos, long bitSize, boolean bigEndian) {
        return ByteTabUtils.toStringBits(this.tabHelper, this.tab, this.tabOffset, this.bitLimit(), firstBitPos, bitSize, bigEndian);
    }

    /**
     * @return A string containing buffer's content, from 0 to limit, as binary bytes.
     */
    public String toStringBinaryBytes() {
        return this.toString(0, this.limit(), 2);
    }

    /**
     * @return A string containing buffer's content, from 0 to limit, as octal bytes.
     */
    public String toStringOctalBytes() {
        return this.toString(0, this.limit(), 8);
    }

    /**
     * @return A string containing buffer's content, from 0 to limit, as decimal bytes.
     */
    public String toStringDecimalBytes() {
        return this.toString(0, this.limit(), 10);
    }

    /**
     * @return A string containing buffer's content, from 0 to limit, as hexadecimal bytes.
     */
    public String toStringHexadecimalBytes() {
        return this.toString(0, this.limit(), 16);
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------

    /**
     * @param ba null if not known.
     * @param bb null if no backing ByteBuffer.
     */
    protected DataBuffer(
            byte[] ba,
            int baOffset,
            ByteBuffer bb,
            long bitMark,
            long bitPosition,
            int limit,
            int capacity,
            boolean bigEndian) {
        this.setHelperAndTabs(ba, baOffset, bb);
        this.bitMark = bitMark;
        this.bitPosition = bitPosition;
        this.limit = limit;
        this.capacity = capacity;
        this.bigEndian = bigEndian;
    }

    protected DataBuffer(byte[] buffer) {
        this(
                buffer, // ba
                0, // baOffset
                null, // bb
                -1, // bitMark
                0, // bitPosition
                buffer.length, // limit
                buffer.length, // capacity
                true); // bigEndian
    }

    protected DataBuffer(ByteBuffer buffer) {
        this(
                (buffer.hasArray() ? buffer.array() : null), // ba
                (buffer.hasArray() ? buffer.arrayOffset() : 0), // baOffset
                buffer, // bb
                -1, // bitMark
                buffer.position() * 8L, // bitPosition
                buffer.limit(), // limit
                buffer.capacity(), // capacity
                buffer.order() == ByteOrder.BIG_ENDIAN); // bigEndian
    }

    /*
     * 
     */
    
    protected void setHelperAndTabs(
            byte[] ba,
            int baOffset,
            ByteBuffer bb) {
        if (ba != null) {
            this.tabHelper = BA_HELPER;
            this.tab = ba;
            this.tabOffset = baOffset;
        } else {
            this.tabHelper = BB_HELPER;
            this.tab = bb;
            this.tabOffset = 0;
        }
        this.ba = ba;
        this.baOffset = baOffset;
        this.bb = bb;
    }

    /*
     * To take care not to give our backing ByteBuffer
     * to our children DataBuffers.
     */

    protected Object tabSlice() {
        final Object tab;
        if (this.hasByteBuffer()) {
            this.byteBuffer().position(this.positionSup());
            // Setting order because slice() doesn't preserve it.
            tab = this.byteBuffer().slice().order(this.byteBuffer().order());
        } else {
            tab = this.tab;
        }
        return tab;
    }

    protected Object tabDuplicate() {
        final Object tab;
        if (this.hasByteBuffer()) {
            // Setting order because duplicate() doesn't preserve it.
            tab = this.byteBuffer().duplicate().order(this.byteBuffer().order());
        } else {
            tab = this.tab;
        }
        return tab;
    }

    protected Object tabAsReadOnlyBuffer() {
        final Object tab;
        if (this.hasByteBuffer()) {
            // Setting order because asReadOnlyBuffer() doesn't preserve it.
            tab = this.byteBuffer().asReadOnlyBuffer().order(this.byteBuffer().order());
        } else {
            tab = this.tab;
        }
        return tab;
    }

    /*
     * For use by DataBufferR, to keep our fields private.
     */

    protected DataBuffer slice_forDataBufferR() {
        final long bitMark = -1;
        final long bitPosition = 0;
        final int posSup = this.positionSup();
        final int limit = this.limit - posSup;
        final int capacity = limit;
        final Object tabSliced = this.tabSlice(); // tab already read-only
        return new DataBufferR(
                this.ba, // ba
                this.baOffset + posSup, // baOffset
                (this.hasByteBuffer() ? (ByteBuffer)tabSliced : null), // bb
                bitMark,
                bitPosition,
                limit,
                capacity,
                this.bigEndian);
    }

    protected DataBuffer duplicate_forDataBufferR() {
        final Object tabDuplicated = this.tabDuplicate();
        return new DataBufferR(
                this.ba, // ba
                this.baOffset, // baOffset
                (this.hasByteBuffer() ? (ByteBuffer)tabDuplicated : null), // bb
                this.bitMark,
                this.bitPosition,
                this.limit,
                this.capacity,
                this.bigEndian);
    }

    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    /**
     * For tests.
     * 
     * @return True if this buffer is backed by a known byte array,
     *         possibly through a read-only ByteBuffer, false otherwise.
     */
    boolean hasArrayWritableOrNot() {
        return (this.ba != null);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private void setBackingBufferImpl(byte[] buffer) {
        this.setBackingBufferImpl_noCheck(buffer, 0, buffer.length);
    }

    private void setBackingBufferImpl(byte[] buffer, int offset, int capacity) {
        LangUtils.checkBounds(buffer.length, offset, capacity);
        this.setBackingBufferImpl_noCheck(buffer, offset, capacity);
    }

    private void setBackingBufferImpl_noCheck(byte[] buffer, int offset, int capacity) {
        this.setHelperAndTabs(
                buffer, // ba
                offset, // baOffset
                null); // bb
        this.capacity = capacity;
        this.clear();
    }

    private void setBackingBufferImpl(ByteBuffer buffer) {
        // Retrieved early for null check.
        final int capacity = buffer.capacity();
        this.setHelperAndTabs(
                (buffer.hasArray() ? buffer.array() : null), // ba
                (buffer.hasArray() ? buffer.arrayOffset() : 0), // baOffset
                buffer); // bb
        this.capacity = capacity;
        this.clear();
        this.order(buffer.order());
    }

    /*
     * 
     */

    private static int hashCode_bigEndian(int h, long bits) {
        final int hi = (int)(bits>>32);
        final int lo = (int)bits;
        h = 31 * h + (byte)lo;
        h = 31 * h + (byte)((lo>>8)&0xFF);
        h = 31 * h + (byte)((lo>>16)&0xFF);
        h = 31 * h + (byte)((lo>>24)&0xFF);
        h = 31 * h + (byte)hi;
        h = 31 * h + (byte)((hi>>8)&0xFF);
        h = 31 * h + (byte)((hi>>16)&0xFF);
        h = 31 * h + (byte)((hi>>24)&0xFF);
        return h;
    }

    private static int hashCode_littleEndian(int h, long bits) {
        final int hi = (int)(bits>>32);
        final int lo = (int)bits;
        h = 31 * h + (byte)((hi>>24)&0xFF);
        h = 31 * h + (byte)((hi>>16)&0xFF);
        h = 31 * h + (byte)((hi>>8)&0xFF);
        h = 31 * h + (byte)hi;
        h = 31 * h + (byte)((lo>>24)&0xFF);
        h = 31 * h + (byte)((lo>>16)&0xFF);
        h = 31 * h + (byte)((lo>>8)&0xFF);
        h = 31 * h + (byte)lo;
        return h;
    }

    /**
     * @return True if the specified bytes are identical
     *         if the order or either of them is reversed,
     *         false otherwise.
     */
    private static boolean equalsOppositeOrder(int i1, int i2) {
        int i1lohi = ((i1>>8)&0xFF);
        int i1hilo = ((i1>>16)&0xFF);
        return i2 == ((i1<<24) | (i1lohi<<16) | (i1hilo<<8) | (i1>>>24));
    }

    /**
     * @return True if the specified bytes are identical
     *         if the order or either of them is reversed,
     *         false otherwise.
     */
    private static boolean equalsOppositeOrder(long l1, long l2) {
        return equalsOppositeOrder((int)l1, (int)(l2>>32))
                && equalsOppositeOrder((int)l2, (int)(l1>>32));
    }
}
