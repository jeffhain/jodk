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

/**
 * Non-volatile reference to an instance obtained
 * through a volatile reference, which is read
 * each time the calling thread sees the non-volatile
 * reference as null.
 * 
 * Useful if the volatile reference is only set once
 * to a non-null value, for it allows to use a
 * non-volatile non-null reference in the long run.
 * 
 * Beware of data races, since this allows to obtain
 * a reference to an object before eventually needed
 * memory synchronization.
 */
public class NonNullElseAtomicReference<T> {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private final AtomicReference<T> vRef;
    
    /**
     * Initialized to null.
     */
    private T ref = null;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * The non-null reference is not retrieved in this
     * constructor, but in call(s) to get() method.
     * 
     * @param vRef Atomic reference providing the non-null reference
     *        that is returned by get() method.
     */
    public NonNullElseAtomicReference(final AtomicReference<T> vRef) {
        this.vRef = vRef;
    }
    
    /**
     * @return Non-null reference obtained from non-volatile reference,
     *         else reference obtained from volatile reference (possibly null).
     */
    public T get() {
        T ref = this.ref;
        if (ref == null) {
            ref = this.vRef.get();
            if (ref != null) {
                this.ref = ref;
            }
        }
        return ref;
    }
}
