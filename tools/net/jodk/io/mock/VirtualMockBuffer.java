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
 * on first put, which allows for a few contiguous puts.
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
        if (this.ensureLocalBufferUpTo(position)) {
            final int index = NumbersUtils.asInt(position - this.localBufferOffset);
            this.localBuffer.put(index, b);
        } else {
            final long minPos = this.localBufferOffset;
            final long maxPos = (this.localBufferOffset+this.localBufferCapacity-1);
            throw new IllegalArgumentException("position ["+position+"] not in local buffer range ["+minPos+","+maxPos+"]");
        }
    }
    
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
    
    private boolean ensureLocalBufferUpTo(long position) {
        if (this.localBufferCapacity == 0) {
            return false;
        }
        
        if (this.localBuffer == null) {
            this.localBuffer = ByteBuffer.allocate(this.localBufferCapacity);
            this.localBufferOffset = Math.min(position,Long.MAX_VALUE-this.localBufferCapacity);
            for (int i=0;i<this.localBufferCapacity;i++) {
                this.localBuffer.put(i, this.content.get(this.localBufferOffset + i));
            }
            return true;
        } else {
            final long indexLong = position - this.localBufferOffset;
            if (indexLong < 0) {
                // Can't move local buffer backward.
                return false;
            } else if (indexLong >= this.localBufferCapacity) {
                return false;
            } else {
                return true;
            }
        }
    }
    
    private void checkPosition(long position) {
        if ((position < 0) || (position >= this.limit)) {
            throw new IndexOutOfBoundsException("position ["+position+"] must be in [0,"+(this.limit-1)+"] range");
        }
    }
}
