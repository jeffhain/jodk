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
 * Allows to simulate huge content, without using
 * corresponding memory or storage space.
 */
public interface InterfaceMockBuffer extends InterfaceMockContent {

    /**
     * Similar to ByteBuffer.limit() and to FileChannel.size().
     */
    public long limit();
    
    /**
     * Similar to ByteBuffer.limit(int).
     * 
     * @throws IndexOutOfBoundsException if the specified size
     *         is < 0, or too large for the implementation.
     */
    public void limit(long newLimit);
    
    /**
     * Similar to ByteBuffer.put(int,byte).
     * 
     * @throws IndexOutOfBoundsException if the specified position
     *         is < 0 or >= limit.
     */
    public void put(long position, byte b);
}
