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
package net.jodk.lang;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Provides versions of some JDK methods throwing checked exceptions,
 * that throw a wrapping RethrowException instead.
 */
public class Unchecked {

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Calls Thread.sleep(long).
     */
    public static void sleepMS(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RethrowException(e);
        }
    }

    /**
     * Calls Object.wait().
     */
    public static void wait(Object object) {
        try {
            object.wait();
        } catch (InterruptedException e) {
            throw new RethrowException(e);
        }
    }

    /**
     * Calls Object.wait(long).
     */
    public static void waitMS(Object object, long ms) {
        try {
            object.wait(ms);
        } catch (InterruptedException e) {
            throw new RethrowException(e);
        }
    }

    /**
     * Calls ExecutorService.awaitTermination(Long.MAX_VALUE,TimeUnit.NANOSECONDS).
     */
    public static void awaitTermination(ExecutorService executor) {
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw new RethrowException(e);
        }
    }

    /**
     * Calls ExecutorService.shutdown()
     * and then ExecutorService.awaitTermination(Long.MAX_VALUE,TimeUnit.NANOSECONDS).
     */
    public static void shutdownAndAwaitTermination(ExecutorService executor) {
        executor.shutdown();
        awaitTermination(executor);
    }
}
