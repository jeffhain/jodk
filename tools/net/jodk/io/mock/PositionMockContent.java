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

/**
 * Mock content that always returns (byte)position.
 * 
 * Has a limit of Long.MAX_VALUE.
 */
public class PositionMockContent implements InterfaceMockContent {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public PositionMockContent() {
    }
    
    @Override
    public String toString() {
        return "[(byte)position]";
    }
    
    @Override
    public byte get(long position) {
        if ((position < 0) || (position == Long.MAX_VALUE)) {
            throw new IllegalArgumentException();
        }
        return (byte)position;
    }
}
