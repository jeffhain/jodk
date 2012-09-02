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
package net.jodk.threading.ringbuffers;

import junit.framework.TestCase;

public class RingBuffersUtilsTest extends TestCase {

    public void test_toStringSequencesRanges_longArray() {
        
        /*
         * NPE if null
         */
        
        try {
            RingBuffersUtils.toStringSequencesRanges(null);
            assertTrue(false);
        } catch (NullPointerException e) {
        }
        
        /*
         * IAG if length is not even
         */
        
        try {
            RingBuffersUtils.toStringSequencesRanges(new long[]{36});
            assertTrue(false);
        } catch (IllegalArgumentException e) {
        }
        
        try {
            RingBuffersUtils.toStringSequencesRanges(new long[]{36,37,38});
            assertTrue(false);
        } catch (IllegalArgumentException e) {
        }
        
        /*
         * This method must not check ranges validity,
         * so we specify overlapping ranges on purpose.
         */
        
        assertEquals("{}",RingBuffersUtils.toStringSequencesRanges(new long[]{}));
        assertEquals("{12}",RingBuffersUtils.toStringSequencesRanges(new long[]{12,12}));
        assertEquals("{11..13}",RingBuffersUtils.toStringSequencesRanges(new long[]{11,13}));
        assertEquals("{37..49,12,11..13}",RingBuffersUtils.toStringSequencesRanges(new long[]{37,49,12,12,11,13}));
    }
    
    public void test_appendStringSequencesRanges_StringBuilder_int_longArray() {
        StringBuilder sb;
        
        sb = new StringBuilder();
        RingBuffersUtils.appendStringSequencesRanges(sb,3,new long[]{37,49,12,12,11,13});
        assertEquals("{37..49,12,11..13}",sb.toString());
        
        sb = new StringBuilder();
        RingBuffersUtils.appendStringSequencesRanges(sb,2,new long[]{37,49,12,12,11,13});
        assertEquals("{37..49,12,...}",sb.toString());
        
        sb = new StringBuilder();
        RingBuffersUtils.appendStringSequencesRanges(sb,1,new long[]{37,49,12,12,11,13});
        assertEquals("{37..49,...}",sb.toString());
        
        sb = new StringBuilder();
        RingBuffersUtils.appendStringSequencesRanges(sb,0,new long[]{37,49,12,12,11,13});
        assertEquals("{...}",sb.toString());
    }
}
