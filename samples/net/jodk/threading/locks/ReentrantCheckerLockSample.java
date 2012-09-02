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

public class ReentrantCheckerLockSample {

    //--------------------------------------------------------------------------
    // PUBLIC TREATMENTS
    //--------------------------------------------------------------------------
    
    public static void main(String[] args) {
        System.out.println("--- "+CondilockSample.class.getSimpleName()+"... ---");

        ReentrantCheckerLock fastLock = new ReentrantCheckerLock("FAST LOCK");
        ReentrantCheckerLock slowLock = new ReentrantCheckerLock("SLOW LOCK",false,fastLock);

        /*
         * OK
         */
        
        slowLock.lock();
        try {
            
            // Doing some slow stuff.
            
            fastLock.lock();
            try {
                
                // Doing some fast stuff.
                
                // This is allowed because slowLock is already held.
                slowLock.lock();
                try {
                } finally {
                    slowLock.unlock();
                }
                
                // Doing some fast stuff.
                
            } finally {
                fastLock.unlock();
            }
            
            // Doing some slow stuff.
            
        } finally {
            slowLock.unlock();
        }
        
        /*
         * KO
         */
        
        fastLock.lock();
        try {
            try {
                slowLock.lock();
                System.out.println("we never pass here");
                try {
                } finally {
                    slowLock.unlock();
                }
            } catch (IllegalStateException e) {
                System.out.println("current thread attempted to acquire slowLock while holding fastLock");
            }
        } finally {
            fastLock.unlock();
        }
        
        System.out.println("--- ..."+CondilockSample.class.getSimpleName()+" ---");
    }
}
