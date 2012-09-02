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
package net.jodk.threading;

import java.util.concurrent.atomic.AtomicReference;

import junit.framework.TestCase;

public class NonNullElseAtomicReferenceTest extends TestCase {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_sequential() {
        final AtomicReference<Integer> atomic = new AtomicReference<Integer>();
        final NonNullElseAtomicReference<Integer> nnea = new NonNullElseAtomicReference<Integer>(atomic);

        /*
         * Null through atomic.
         */
        
        assertNull(nnea.get());
        
        /*
         * Non-null through atomic.
         * Updates non-volatile reference.
         */
        
        final Integer value = new Integer(1);
        atomic.set(value);
        assertSame(value, nnea.get());
        
        /*
         * Non volatile reference kept even after
         * any set of atomic reference.
         */
        
        atomic.set(null);
        assertSame(value, nnea.get());
        
        atomic.set(new Integer(2));
        assertSame(value, nnea.get());
        
        atomic.set(value);
        assertSame(value, nnea.get());
    }
}
