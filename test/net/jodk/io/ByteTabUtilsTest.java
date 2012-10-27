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

import junit.framework.TestCase;

public class ByteTabUtilsTest extends TestCase {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public void test_computeByteIndex_long() {
        for (long bitPos : new long[]{-1,ByteTabUtils.MAX_BIT_POS+9}) {
            try {
                ByteTabUtils.computeByteIndex(bitPos);
                assertTrue(false);
            } catch (IndexOutOfBoundsException e) {
                // ok
            }
        }

        assertEquals(0,ByteTabUtils.computeByteIndex(0));
        assertEquals(0,ByteTabUtils.computeByteIndex(7));
        assertEquals(1,ByteTabUtils.computeByteIndex(8));
        assertEquals(1,ByteTabUtils.computeByteIndex(15));
        assertEquals(2,ByteTabUtils.computeByteIndex(16));
        assertEquals(2,ByteTabUtils.computeByteIndex(23));
        assertEquals(3,ByteTabUtils.computeByteIndex(24));
        assertEquals(Integer.MAX_VALUE-1,ByteTabUtils.computeByteIndex(ByteTabUtils.MAX_BIT_POS));
        assertEquals(Integer.MAX_VALUE,ByteTabUtils.computeByteIndex(ByteTabUtils.MAX_BIT_POS+1));
        assertEquals(Integer.MAX_VALUE,ByteTabUtils.computeByteIndex(ByteTabUtils.MAX_BIT_POS+8));
    }

    public void test_computeByteSize_long() {
        for (long bitSize : new long[]{-1,ByteTabUtils.MAX_BIT_SIZE+1}) {
            try {
                ByteTabUtils.computeByteSize(bitSize);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }

        assertEquals(0,ByteTabUtils.computeByteSize(0));
        assertEquals(1,ByteTabUtils.computeByteSize(1));
        assertEquals(1,ByteTabUtils.computeByteSize(8));
        assertEquals(2,ByteTabUtils.computeByteSize(9));
        assertEquals(2,ByteTabUtils.computeByteSize(16));
        assertEquals(3,ByteTabUtils.computeByteSize(17));
        assertEquals(3,ByteTabUtils.computeByteSize(24));
        assertEquals(Integer.MAX_VALUE-1,ByteTabUtils.computeByteSize(ByteTabUtils.MAX_BIT_SIZE-8));
        assertEquals(Integer.MAX_VALUE,ByteTabUtils.computeByteSize(ByteTabUtils.MAX_BIT_SIZE-7));
        assertEquals(Integer.MAX_VALUE,ByteTabUtils.computeByteSize(ByteTabUtils.MAX_BIT_SIZE));
    }

    public void test_computeByteSize_2long() {
        // Quick test (uses LangUtils).
        try {
            ByteTabUtils.computeByteSize(0L,-1L);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            ByteTabUtils.computeByteSize(-1L,1L);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            ByteTabUtils.computeByteSize(0L,ByteTabUtils.MAX_BIT_SIZE+1);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        try {
            ByteTabUtils.computeByteSize(ByteTabUtils.MAX_BIT_POS+2,0L);
            assertTrue(false);
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
        
        /*
         * 
         */

        for (long bitSize=0;bitSize<100;bitSize++) {
            final long byteSizeAligned = ByteTabUtils.computeByteSize(bitSize);
            
            assertEquals(byteSizeAligned, ByteTabUtils.computeByteSize(32L,bitSize));
            
            final int bitSurplus = (int)(bitSize&7);
            for (int n=1;n<bitSurplus;n++) {
                assertEquals(byteSizeAligned+1, ByteTabUtils.computeByteSize(32L-n,bitSize));
            }
        }
        
        assertEquals(Integer.MAX_VALUE, ByteTabUtils.computeByteSize(0L,ByteTabUtils.MAX_BIT_SIZE));
        assertEquals(Integer.MAX_VALUE, ByteTabUtils.computeByteSize(7L,ByteTabUtils.MAX_BIT_SIZE-7L));
        assertEquals(Integer.MAX_VALUE-1, ByteTabUtils.computeByteSize(8L,ByteTabUtils.MAX_BIT_SIZE-8L));
    }
}
