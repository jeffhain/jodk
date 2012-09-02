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
package net.jodk.threading.locks;

import junit.framework.TestCase;

public class LocksUtilsTest extends TestCase {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_checkNotLocked_InterfaceCheckerLock() {
        // already covered by ReentrantCheckerLock tests
    }

    public void test_checkNotHeldByAnotherThread_InterfaceCheckerLock() {
        // already covered by ReentrantCheckerLock tests
    }

    public void test_checkHeldByCurrentThread_InterfaceCheckerLock() {
        // already covered by ReentrantCheckerLock tests
    }
    
    public void test_checkNotHeldByCurrentThread_InterfaceCheckerLock() {
        // already covered by ReentrantCheckerLock tests
    }
}
