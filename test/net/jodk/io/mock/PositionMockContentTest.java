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

import junit.framework.TestCase;

public class PositionMockContentTest extends TestCase {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_PositionMockContent() {
        // already covered
    }

    public void test_toString() {
        PositionMockContent mock = new PositionMockContent();
        assertEquals("[(byte)position]", mock.toString());
    }

    public void test_get_long() {
        PositionMockContent mock = new PositionMockContent();
        
        for (long pos : new long[]{Long.MIN_VALUE,-1,Long.MAX_VALUE}) {
            try {
                mock.get(pos);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        
        for (long pos : new long[]{0,1,2,1001,1002,Long.MAX_VALUE-1}) {
            assertEquals((byte)pos, mock.get(pos));
        }
    }
}
