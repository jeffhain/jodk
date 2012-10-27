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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import net.jodk.lang.RethrowException;

/**
 * Might allow for using map/unmap for common JDK's FileChannel implementations.
 */
class DefaultMBBHelper implements InterfaceMBBHelper {

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    /**
     * Name of the class for which we hack acces to unmapping treatments.
     */
    private static final String FILE_CHANNEL_CLASS_NAME = "sun.nio.ch.FileChannelImpl";
    
    private static final String DIRECT_BUFFER_CLASS_NAME = "sun.nio.ch.DirectBuffer";
    
    private static final String CLEANER_METHOD_NAME = "cleaner";
    private static final String CLEAN_METHOD_NAME = "clean";
    
    private static final Class<?> FILE_CHANNEL_CLASS;
    private static final Class<?> DIRECT_BUFFER_CLASS;
    
    /**
     * True if we found unmapping treatments, false otherwise.
     */
    private static final boolean CAN_UNMAP;
    
    static {
        boolean cleanMethodFound = false;

        Class<?> fileChannelClass = null;
        try {
            fileChannelClass = DefaultMBBHelper.class.getClassLoader().loadClass(FILE_CHANNEL_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            // quiet
        }
        FILE_CHANNEL_CLASS = fileChannelClass;

        Class<?> directBufferClass = null;
        try {
            directBufferClass = DefaultMBBHelper.class.getClassLoader().loadClass(DIRECT_BUFFER_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            // quiet
        }
        DIRECT_BUFFER_CLASS = directBufferClass;
        
        if (directBufferClass != null) {
            final Method cleanerMethod = getMethodElseNull(directBufferClass, CLEANER_METHOD_NAME);
            if (cleanerMethod != null) {
                final Method cleanMethod = getMethodElseNull(cleanerMethod.getReturnType(), CLEAN_METHOD_NAME);
                if (cleanMethod != null) {
                    cleanMethodFound = true;
                }
            }
        }
        CAN_UNMAP = cleanMethodFound;
    }

    /**
     * The instance.
     */
    public static final DefaultMBBHelper INSTANCE = new DefaultMBBHelper();

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    @Override
    public boolean canMapAndUnmap(FileChannel channel, MapMode mode) {
        return CAN_UNMAP && (channel.getClass() == FILE_CHANNEL_CLASS);
    }

    /**
     * Delegates to the specified channel's map method.
     */
    @Override
    public ByteBuffer map(
            FileChannel channel,
            MapMode mode,
            long position,
            long size) throws IOException {
        return channel.map(mode, position, size);
    }
    
    @Override
    public void unmap(FileChannel channel, ByteBuffer mbb) {
        // Using reflection, to allow for compilation
        // with JDK not providing this API.
        if (false) {
//            final sun.misc.Cleaner cleaner = ((sun.nio.ch.DirectBuffer)mbb).cleaner();
//            if (cleaner != null) {
//                cleaner.clean();
//            }
        } else {
            final Object cleaner = invokeDeclaredMethod(DIRECT_BUFFER_CLASS, mbb, CLEANER_METHOD_NAME);
            if (cleaner != null) {
                invokeDeclaredMethod(cleaner, CLEAN_METHOD_NAME);
            }
        }
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private DefaultMBBHelper() {
    }

    private static Method getMethodElseNull(Class<?> clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            // quiet
        } catch (SecurityException e) {
            // quiet
        }
        return null;
    }
    
    private static Object invokeDeclaredMethod(
            final Object instance,
            final String methodName) {
        return invokeDeclaredMethod(instance.getClass(), instance, methodName);
    }

    private static Object invokeDeclaredMethod(
            final Class<?> clazz,
            final Object instance,
            final String methodName) {
        try {
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(instance);
        } catch (NoSuchMethodException e) {
            throw new RethrowException("couldn't invoke method: "+methodName+"()",e);
        } catch (IllegalAccessException e) {
            throw new RethrowException("couldn't invoke method: "+methodName+"()",e);
        } catch (InvocationTargetException e) {
            throw new RethrowException("couldn't invoke method: "+methodName+"()",e);
        }
    }
}
