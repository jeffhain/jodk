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
 * Initially non-null non-volatile reference to an instance,
 * which is read each time the calling thread sees the non-volatile
 * reference as non-null.
 * 
 * Useful if the volatile reference is only set once
 * to null value, for it allows to use a non-volatile
 * null reference in the long run.
 */
public class NullElseAtomicReference<T> {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private final AtomicReference<T> vRef;
    
    /**
     * Initialized to non-null.
     * "this" works even if the volatile reference refers to "this",
     * since we don't use volatile if "ref == this" but if "ref != null".
     */
    private Object ref = this;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * The initially non-null reference can be set in the specified
     * atomic reference, after construction, but it must be done
     * before first call to get() method.
     * 
     * @param vRef Atomic reference providing the non-null reference
     *        that is returned by get() method, until it ends up returning
     *        a null reference and all threads see non-volatile reference
     *        as null.
     */
    public NullElseAtomicReference(final AtomicReference<T> vRef) {
        this.vRef = vRef;
    }
    
    /**
     * @return Null obtained from non-volatile reference,
     *         else reference obtained from volatile reference (possibly non-null).
     */
    public T get() {
        final Object ref = this.ref;
        if (ref == null) {
            return null;
        } else {
            final T typedRef = this.vRef.get();
            if (typedRef == null) {
                this.ref = null;
                return null;
            } else {
                return typedRef;
            }
        }
    }
}
