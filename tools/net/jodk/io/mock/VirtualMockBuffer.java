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
package net.jodk.io.mock;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;

/**
 * Mock buffer which content is defined by a specified
 * InterfaceMockContent implementation, which allows for
 * definition of huge contents (limit up to Long.MAX_VALUE)
 * possibly without using actual memory for it (for example
 * by defining "get(long position){return (byte)position;}").
 * 
 * Also, some bytes can be put through put(long,byte) method,
 * into a ByteBuffer which is positionned (in the virtual buffer)
 * on first put, as centered on it as possible, which allows for
 * a few contiguous puts (forward or backward).
 * This local ByteBuffer is initialized with content provided
 * by backing InterfaceMockContent implementation, and then
 * get(long) method always returns data from the ByteBuffer
 * if it is in its range.
 */
public class VirtualMockBuffer implements InterfaceMockBuffer {

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    /**
     * Not providing a getter of it, for it's only used where no byte
     * has been put, which could cause confusion.
     */
    private final InterfaceMockContent content;
    
    private long limit = Long.MAX_VALUE;
    
    private final int localBufferCapacity;
    
    /**
     * For small contiguous puts.
     * Lazily initialized.
     */
    private ByteBuffer localBuffer;
    
    /**
     * Offset of local buffer, in the whole content.
     */
    private long localBufferOffset = Long.MIN_VALUE;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates an instance with an initial limit of Long.MAX_VALUE,
     * which get(long position) method returns (byte)position,
     * unless where different bytes where put.
     * 
     * @param localBufferCapacity Capacity for the local buffer.
     * @throws IllegalArgumentException if localBufferCapacity is < 0.
     */
    public VirtualMockBuffer(int localBufferCapacity) {
        this(
                new PositionMockContent(),
                localBufferCapacity);
    }

    /**
     * Creates an instance with an initial limit of Long.MAX_VALUE,
     * which get(long position) method delegates to the specified content,
     * unless where different bytes where put.
     * 
     * @param content The backing InterfaceMockContent.
     * @param localBufferCapacity Capacity for the local buffer.
     * @throws NullPointerException if the specified content is null.
     * @throws IllegalArgumentException if localBufferCapacity is < 0.
     */
    public VirtualMockBuffer(
            InterfaceMockContent content,
            int localBufferCapacity) {
        LangUtils.checkNonNull(content);
        if (localBufferCapacity < 0) {
            throw new IllegalArgumentException();
        }
        this.content = content;
        this.localBufferCapacity = localBufferCapacity;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[content=");
        sb.append(this.content);
        sb.append(",limit=");
        sb.append(this.limit);
        if (this.localBuffer != null) {
            sb.append(",localBufferOffset=");
            sb.append(this.localBufferOffset);
            sb.append(",localBuffer=");
            sb.append(this.localBuffer);
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * @return Capacity of the local buffer,
     *         whether or not there is currently one.
     */
    public int getLocalBufferCapacity() {
        return this.localBufferCapacity;
    }

    @Override
    public long limit() {
        return this.limit;
    }

    @Override
    public void limit(long newLimit) {
        if (newLimit < 0) {
            throw new IllegalArgumentException();
        }
        this.limit = newLimit;
    }

    @Override
    public byte get(long position) {
        this.checkPosition(position);
        if ((this.localBuffer != null)
                && (position >= this.localBufferOffset)
                && (position < this.localBufferOffset + this.localBuffer.limit())) {
            final int localBufferIndex = NumbersUtils.asInt(position - this.localBufferOffset);
            return this.localBuffer.get(localBufferIndex);
        }
        // Not in local buffer range (if any).
        return this.content.get(position);
    }
    
    /**
     * @throws IndexOutOfBoundsException if position is < 0 or >= limit.
     * @throws IllegalArgumentException if position is outside local buffer range.
     */
    @Override
    public void put(long position, byte b) {
        this.checkPosition(position);
        if (this.tryEnsureLocalBufferOn(position)) {
            final int index = NumbersUtils.asInt(position - this.localBufferOffset);
            this.localBuffer.put(index, b);
        } else {
            this.throwIAE_notInLocalBufferRange(position);
        }
    }

    /**
     * If local buffer does not exist, it is created centered
     * on the middle of the destination range.
     * 
     * @throws NullPointerException if the specified buffer is null.
     * @throws IndexOutOfBoundsException if the specified position
     *         is < 0 or >= limit.
     * @throws BufferOverflowException if there is insufficient space
     *         in this buffer for the remaining bytes in the specified buffer.
     * @throws IllegalArgumentException if destination range is outside local buffer range.
     */
    @Override
    public void put(long position, ByteBuffer src) {
        LangUtils.checkNonNull(src);
        this.checkPosition(position);
        
        final int remaining = src.remaining();
        if (remaining == 0) {
            return;
        }
        final long dstMidPos = position + remaining/2;
        
        final boolean clearIfThrow = (this.localBuffer == null);
        if (this.tryEnsureLocalBufferOn(dstMidPos)) {
            // Checking that whole destination range is in local buffer range.
            if (!this.isInLocalBufferRange(position)) {
                if (clearIfThrow) {
                    this.deleteLocalBuffer();
                }
                this.throwIAE_notInLocalBufferRange(position);
            }
            final long dstMax = position+remaining;
            if (!this.isInLocalBufferRange(dstMax)) {
                if (clearIfThrow) {
                    this.deleteLocalBuffer();
                }
                this.throwIAE_notInLocalBufferRange(dstMax);
            }
            if (dstMax > this.limit) {
                if (clearIfThrow) {
                    this.deleteLocalBuffer();
                }
                throw new BufferOverflowException();
            }
            
            // Allows not to modify local buffer, which allows concurrent usage,
            // and also allows to make sure we don't put a same ByteBuffer
            // into itself, which is not allowed by ByteBuffer.put(ByteBuffer).
            final ByteBuffer bd = this.localBuffer.duplicate();
            bd.position(NumbersUtils.asInt(position - this.localBufferOffset));
            bd.put(src);
        } else {
            this.throwIAE_notInLocalBufferRange(dstMidPos);
        }
    }

    /*
     * 
     */
    
    /**
     * @return The local buffer, or null if there is none.
     */
    public ByteBuffer getLocalBufferElseNull() {
        return this.localBuffer;
    }

    /**
     * @return Local buffer offset.
     * @throws IllegalStateException if there is no local buffer.
     */
    public long getLocalBufferOffset() {
        if (this.localBuffer == null) {
            throw new IllegalStateException();
        }
        return this.localBufferOffset;
    }

    /**
     * Deletes local buffer, allowing for creation of a new one.
     */
    public void deleteLocalBuffer() {
        if (this.localBuffer != null) {
            this.localBuffer = null;
            this.localBufferOffset = Long.MIN_VALUE;
        }
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private boolean tryEnsureLocalBufferOn(long position) {
        if (this.localBufferCapacity == 0) {
            return false;
        }
        
        if (this.localBuffer == null) {
            this.localBuffer = ByteBuffer.allocate(this.localBufferCapacity);
            this.localBufferOffset =
                Math.max(0,
                        Math.min(
                                position-this.localBufferCapacity/2,
                                Long.MAX_VALUE-this.localBufferCapacity));
            for (int i=0;i<this.localBufferCapacity;i++) {
                this.localBuffer.put(i, this.content.get(this.localBufferOffset + i));
            }
            return true;
        } else {
            return this.isInLocalBufferRange(position);
        }
    }
    
    private boolean isInLocalBufferRange(long position) {
        if (this.localBuffer == null) {
            throw new AssertionError();
        }
        return (position >= this.localBufferOffset) && (position < this.localBufferOffset+this.localBufferCapacity);
    }
    
    private void throwIAE_notInLocalBufferRange(long position) {
        final long minPos = this.localBufferOffset;
        final long maxPos = (this.localBufferOffset+this.localBufferCapacity-1);
        throw new IllegalArgumentException("position ["+position+"] not in local buffer range ["+minPos+","+maxPos+"]");
    }
    
    private void checkPosition(long position) {
        if ((position < 0) || (position >= this.limit)) {
            throw new IndexOutOfBoundsException("position ["+position+"] must be in [0,"+(this.limit-1)+"] range");
        }
    }
}
