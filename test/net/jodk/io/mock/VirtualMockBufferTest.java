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

import junit.framework.TestCase;

public class VirtualMockBufferTest extends TestCase {

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    private static class MyMockContent implements InterfaceMockContent {
        final long a;
        final long b;
        public MyMockContent(
                long a,
                long b) {
            this.a = a;
            this.b = b;
        }
        @Override
        public String toString() {
            return "[a="+this.a+",b="+this.b+"]";
        }
        /**
         * @return a * position + b
         */
        @Override
        public byte get(long position) {
            return (byte)(this.a * position + this.b);
        }
    }
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_VirtualMockBuffer_int() {
        for (int lbc : new int[]{Integer.MIN_VALUE,-1}) {
            try {
                new VirtualMockBuffer(lbc);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        
        VirtualMockBuffer mock = new VirtualMockBuffer(10);
        assertEquals(10, mock.getLocalBufferCapacity());
        assertEquals(Long.MAX_VALUE, mock.limit());
        
        for (long pos=0;pos<100;pos++) {
            final byte expected = (byte)pos;
            assertEquals(expected, mock.get(pos));
        }
    }

    public void test_VirtualMockBuffer_InterfaceMockContent_long() {
        try {
            new VirtualMockBuffer(null,0);
            assertTrue(false);
        } catch (NullPointerException e) {
            // ok
        }

        for (int lbc : new int[]{Integer.MIN_VALUE,-1}) {
            try {
                new VirtualMockBuffer(new MyMockContent(0,0),lbc);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }

        final long a = 1;
        final long b = 0;
        MyMockContent content = new MyMockContent(a,b);
        VirtualMockBuffer mock = new VirtualMockBuffer(content,0);
        assertEquals(0, mock.getLocalBufferCapacity());
        assertEquals(Long.MAX_VALUE, mock.limit());
    }
    
    public void test_toString() {
        final long a = 1;
        final long b = 0;
        final long limit = Long.MAX_VALUE - 100;
        {
            PositionMockContent expectedContent = new PositionMockContent();
            VirtualMockBuffer mock = new VirtualMockBuffer(0);
            mock.limit(limit);
            assertEquals("[content="+expectedContent.toString()+",limit="+limit+"]", mock.toString());
        }
        {
            MyMockContent content = new MyMockContent(a,b);
            VirtualMockBuffer mock = new VirtualMockBuffer(content,0);
            mock.limit(limit);
            assertEquals("[content="+content.toString()+",limit="+limit+"]", mock.toString());
        }
        {
            MyMockContent content = new MyMockContent(a,b);
            VirtualMockBuffer mock = new VirtualMockBuffer(content,10);
            mock.limit(limit);
            final long lbOffset = Long.MAX_VALUE/17;
            mock.put(lbOffset, (byte)0);
            assertEquals(
                    "[content="+content.toString()+",limit="+limit
                    +",localBufferOffset="+lbOffset
                    +",localBuffer="+mock.getLocalBufferElseNull()
                    +"]", mock.toString());
        }
    }
    
    public void test_getLocalBufferCapacity() {
        // already covered
    }
    
    public void test_limit() {
        // already covered
    }

    public void test_limit_long() {
        final long a = 1;
        final long b = 0;
        MyMockContent content = new MyMockContent(a,b);
        VirtualMockBuffer mock = new VirtualMockBuffer(content,0);
        
        for (long lim : new long[]{Long.MIN_VALUE,-1}) {
            try {
                mock.limit(lim);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        
        for (long lim : new long[]{0,Long.MAX_VALUE}) {
            mock.limit(lim);
            assertEquals(lim, mock.limit());
        }
    }
    
    public void test_get_long() {
        final long a = 3;
        final long b = 5;
        final int limit = 10;
        MyMockContent content = new MyMockContent(a,b);
        VirtualMockBuffer mock = new VirtualMockBuffer(content,0);
        mock.limit(limit);

        for (long pos : new long[]{Long.MIN_VALUE,-1,limit,Long.MAX_VALUE}) {
            try {
                mock.get(pos);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
        }
        
        for (int i=0;i<limit;i++) {
            assertEquals((byte)(a * i + b), mock.get(i));
        }
    }
    
    public void test_put_long_byte_and_getLocalBufferElseNull_and_getLocalBufferOffset_and_deleteLocalBuffer() {
        for (long firstPutPos : new long[]{1,Long.MAX_VALUE-3}) {
            final long a = 3;
            final long b = 5;
            final int lbc = 10;
            MyMockContent content = new MyMockContent(a,b);
            VirtualMockBuffer mock = new VirtualMockBuffer(content,lbc);

            ByteBuffer localBuffer = mock.getLocalBufferElseNull();
            assertNull(localBuffer);
            try {
                mock.getLocalBufferOffset();
                assertTrue(false);
            } catch (IllegalStateException e) {
                // ok
            }
            
            /*
             * first put
             */
            
            mock.put(firstPutPos, (byte)36);

            final long minLocalPos;
            if (firstPutPos == 1) {
                // Can put in [1..lbc].
                minLocalPos = 1;
            } else {
                // Can put in [Long.MAX_VALUE-lbc..Long.MAX_VALUE-1].
                minLocalPos = Long.MAX_VALUE-lbc;
            }
            final long maxLocalPos = minLocalPos + lbc - 1;

            localBuffer = mock.getLocalBufferElseNull();
            assertNotNull(localBuffer);
            assertEquals(lbc, localBuffer.limit());
            assertEquals(lbc, localBuffer.capacity());
            assertEquals(minLocalPos, mock.getLocalBufferOffset());
            for (int i=0;i<lbc;i++) {
                final long pos = minLocalPos+i;
                if (pos == firstPutPos) {
                    assertEquals(36, localBuffer.get(i));
                } else {
                    assertEquals(content.get(pos), localBuffer.get(i));
                }
            }
            
            /*
             * second put
             */
            
            final long secondPutPos = minLocalPos + 3;
            mock.put(secondPutPos, (byte)37);
            
            localBuffer = mock.getLocalBufferElseNull();
            assertNotNull(localBuffer);
            assertEquals(lbc, localBuffer.limit());
            assertEquals(lbc, localBuffer.capacity());
            assertEquals(minLocalPos, mock.getLocalBufferOffset());
            for (int i=0;i<lbc;i++) {
                final long pos = minLocalPos+i;
                if (pos == firstPutPos) {
                    assertEquals(36, localBuffer.get(i));
                } else if (pos == secondPutPos) {
                    assertEquals(37, localBuffer.get(i));
                } else {
                    assertEquals(content.get(pos), localBuffer.get(i));
                }
            }
            
            /*
             * Local bufer's content in [minPutPos,maxPutPos],
             * content.get(position) elsewhere.
             */
            
            for (int i=0;i<lbc;i++) {
                localBuffer.put(i, (byte)(-(minLocalPos+i)));
            }
            
            for (int i=-1;i<=lbc;i++) {
                final long pos = minLocalPos + i;
                if (pos == Long.MAX_VALUE) {
                    // nothing past local buffer
                    continue;
                }
                final byte expectedB;
                if ((pos >= minLocalPos) && (pos <= maxLocalPos)) {
                    expectedB = (byte)(-pos);
                } else {
                    expectedB = content.get(pos);
                }
                assertEquals(expectedB, mock.get(pos));
            }
            
            /*
             * exception if trying to put outside local buffer range (but in [0,limit[ range)
             */
            
            for (long pos : new long[]{minLocalPos-1,maxLocalPos+1}) {
                if (pos == Long.MAX_VALUE) {
                    // nothing past local buffer
                    continue;
                }
                try {
                    mock.put(pos, (byte)38);
                    assertTrue(false);
                } catch (IllegalArgumentException e) {
                    // ok
                }
            }

            /*
             * exception if trying to put outside local buffer range or limit range
             */
            
            final long limit = maxLocalPos-lbc/2;
            mock.limit(limit);
            for (long pos : new long[]{Long.MIN_VALUE,-1,minLocalPos-1,limit,Long.MAX_VALUE}) {
                try {
                    mock.put(pos, (byte)38);
                    assertTrue(false);
                } catch (IndexOutOfBoundsException e) {
                    assertTrue(pos < 0 || pos >= mock.limit());
                } catch (IllegalArgumentException e) {
                    assertTrue(pos < mock.limit());
                }
            }
            
            /*
             * putting in all local buffer range
             */
            
            mock.limit(Long.MAX_VALUE);
            for (long pos=minLocalPos;pos<=maxLocalPos;pos++) {
                mock.put(pos, (byte)38);
                assertEquals((byte)38, mock.get(pos));
            }

            /*
             * deleting local buffer: then we can create another one
             */
            
            assertNotNull(mock.getLocalBufferElseNull());
            mock.deleteLocalBuffer();
            assertNull(mock.getLocalBufferElseNull());
            
            final long newLocalBufferOffset = 1000;
            for (int i=0;i<lbc;i++) {
                mock.put(newLocalBufferOffset + i, (byte)i);
            }
            for (long pos : new long[]{newLocalBufferOffset-1,newLocalBufferOffset+lbc}) {
                try {
                    mock.put(pos, (byte)0);
                    assertTrue(false);
                } catch (IllegalArgumentException e) {
                    // ok
                }
            }
        }
    }
}
