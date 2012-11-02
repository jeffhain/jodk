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

import junit.framework.TestCase;

public class ByteBufferMockBufferTest extends TestCase {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_ByteBufferMockBuffer_int() {
        final int capacity = 10;
        ByteBufferMockBuffer mock = new ByteBufferMockBuffer(capacity);
        assertSame(capacity, mock.getBackingByteBuffer().capacity());
        assertFalse(mock.getBackingByteBuffer().isDirect());
    }
    
    public void test_ByteBufferMockBuffer_ByteBuffer() {
        try {
            new ByteBufferMockBuffer(null);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        
        final int capacity = 10;
        ByteBuffer bb = ByteBuffer.allocate(capacity);
        ByteBufferMockBuffer mock = new ByteBufferMockBuffer(bb);
        assertSame(bb, mock.getBackingByteBuffer());
        assertEquals(capacity, mock.limit());
    }
    
    public void test_toString() {
        final int capacity = 10;
        ByteBuffer bb = ByteBuffer.allocate(capacity);
        ByteBufferMockBuffer mock = new ByteBufferMockBuffer(bb);
        
        assertEquals(bb.toString(),mock.toString());
    }

    public void test_getBackingByteBuffer() {
        // already covered
    }

    public void test_limit() {
        final int capacity = 10;
        ByteBuffer bb = ByteBuffer.allocate(capacity);
        ByteBufferMockBuffer mock = new ByteBufferMockBuffer(bb);
        
        for (int lim=-capacity;lim<=capacity;lim++) {
            try {
                bb.limit(lim);
            } catch (Exception e) {
                // quiet
            }
            assertEquals(bb.limit(), mock.limit());
        }
    }

    public void test_limit_long() {
        final int capacity = 10;
        ByteBuffer bb = ByteBuffer.allocate(capacity);
        ByteBufferMockBuffer mock = new ByteBufferMockBuffer(bb);
        
        for (long lim : new long[]{Long.MIN_VALUE,-1,capacity+1,Long.MAX_VALUE}) {
            try {
                mock.limit(lim);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        
        for (long lim : new long[]{0,capacity/2,capacity}) {
            mock.limit(lim);
            assertEquals(lim, mock.limit());
            assertEquals(lim, mock.getBackingByteBuffer().limit());
        }
    }
    
    public void test_get_long() {
        final int capacity = 10;
        final int limit = capacity-2;
        ByteBuffer bb = ByteBuffer.allocate(capacity);
        bb.limit(limit);
        ByteBufferMockBuffer mock = new ByteBufferMockBuffer(bb);

        for (long pos : new long[]{Long.MIN_VALUE,-1,limit,Long.MAX_VALUE}) {
            try {
                mock.get(pos);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
        }
        
        for (int i=0;i<limit;i++) {
            bb.put(i, (byte)i);
        }
        for (int i=0;i<limit;i++) {
            assertEquals((byte)i, mock.get(i));
        }
    }
    
    public void test_put_long_byte() {
        final int capacity = 10;
        final int limit = capacity-2;
        ByteBuffer bb = ByteBuffer.allocate(capacity);
        bb.limit(limit);
        ByteBufferMockBuffer mock = new ByteBufferMockBuffer(bb);

        for (long pos : new long[]{Long.MIN_VALUE,-1,limit,Long.MAX_VALUE}) {
            try {
                mock.put(pos,(byte)0);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
        }
        
        for (int i=0;i<limit;i++) {
            mock.put(i, (byte)i);
        }
        for (int i=0;i<limit;i++) {
            assertEquals((byte)i, bb.get(i));
        }
    }

    public void test_put_long_ByteBuffer_regular() {
        final int capacity = 23;
        final int limit = capacity-2;
        ByteBuffer bb = ByteBuffer.allocate(capacity);
        bb.limit(limit);
        ByteBufferMockBuffer mock = new ByteBufferMockBuffer(bb);

        try {
            mock.put(0,(ByteBuffer)null);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }
        
        ByteBuffer src = ByteBuffer.allocate(capacity);

        for (long pos : new long[]{Long.MIN_VALUE,-1,limit,Long.MAX_VALUE}) {
            try {
                mock.put(pos,src);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
        }
        // Not changed.
        assertEquals(0, src.position());
        assertEquals(capacity, src.limit());
        
        // Filling src.
        for (int i=0;i<capacity;i++) {
            src.put(i, (byte)(i+1));
        }
        
        // Too large : no put.
        try {
            mock.put(0,src);
            assertTrue(false);
        } catch (BufferOverflowException e) {
            // ok
        }
        for (int i=0;i<limit;i++) {
            assertEquals(0, bb.get(i));
        }
        
        // Put.
        final int pos = 3;
        final int srcLim = bb.limit()-7;
        src.limit(srcLim);
        mock.put(pos,src);
        assertEquals(srcLim, src.limit());
        assertEquals(srcLim, src.position());
        for (int i=0;i<pos;i++) {
            assertEquals(0, bb.get(i));
        }
        for (int i=0;i<srcLim;i++) {
            assertEquals((i+1), bb.get(pos+i));
        }
        for (int i=srcLim;i<bb.limit()-pos;i++) {
            assertEquals(0, bb.get(pos+i));
        }
    }

    public void test_put_long_ByteBuffer_overlapping() {
        final int capacity = 23;
        final int limit = capacity-2;
        ByteBuffer bb = ByteBuffer.allocate(capacity);
        bb.limit(limit);
        ByteBufferMockBuffer mock = new ByteBufferMockBuffer(bb);

        for (boolean needBackwardCopy : new boolean[]{false,true}) {
            // Reset with (byte)position.
            mock.limit(capacity);
            for (int i=0;i<capacity;i++) {
                mock.put(i, (byte)i);
            }
            final int srcPos = 5;
            final int dstPos = (needBackwardCopy ? srcPos+1 : srcPos-1);
            final int count = 7;
            
            ByteBuffer srcShared = bb.duplicate();
            srcShared.limit(srcPos + count);
            srcShared.position(srcPos);
            mock.put(dstPos, srcShared);
            
            // Checks.
            for (int i=0;i<count;i++) {
                final byte expected = (byte)(srcPos + i);
                final byte actual = mock.get(dstPos + i);
                assertEquals(expected, actual);
            }
        }
    }
}
