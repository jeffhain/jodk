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
 * Mock buffer based on a ByteBuffer, which implies that
 * its limit can't be greater than Integer.MAX_VALUE.
 * 
 * The backing ByteBuffer holds both the content and the limit
 * of the mock buffer.
 * 
 * @Deprecated using mock buffer that always returns "(byte)position/put" instead
 */
public class ByteBufferMockBuffer implements InterfaceMockBuffer {

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private final ByteBuffer bb;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Creates an instance backed by a heap ByteBuffer
     * of specified capacity.
     */
    public ByteBufferMockBuffer(int capacity) {
        this(ByteBuffer.allocate(capacity));
    }

    /**
     * @param bb Backing ByteBuffer.
     * @throws NullPointerException if the specified ByteBuffer is null.
     */
    public ByteBufferMockBuffer(ByteBuffer bb) {
        LangUtils.checkNonNull(bb);
        this.bb = bb;
    }
    
    @Override
    public String toString() {
        return String.valueOf(this.bb);
    }

    /**
     * @return The backing ByteBuffer.
     */
    public ByteBuffer getBackingByteBuffer() {
        return this.bb;
    }
    
    /**
     * @return Limit, which is in int range.
     */
    @Override
    public long limit() {
        return this.bb.limit();
    }

    /**
     * @throws IllegalArgumentException if the specified limit is higher than
     *         backing ByteBuffer's capacity.
     */
    @Override
    public void limit(long newLimit) {
        if ((newLimit < 0) || (newLimit > this.bb.capacity())) {
            throw new IllegalArgumentException();
        }
        this.bb.limit(NumbersUtils.asInt(newLimit));
    }

    @Override
    public byte get(long position) {
        this.checkPosition(position);
        return this.bb.get(NumbersUtils.asInt(position));
    }

    @Override
    public void put(long position, byte b) {
        this.checkPosition(position);
        this.bb.put(NumbersUtils.asInt(position), b);
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private void checkPosition(long position) {
        final int limit = this.bb.limit();
        if ((position < 0) || (position >= limit)) {
            throw new IndexOutOfBoundsException("position ["+position+"] must be in [0,"+(limit-1)+"] range");
        }
    }
}
