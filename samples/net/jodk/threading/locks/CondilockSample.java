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

import java.util.concurrent.atomic.AtomicBoolean;

import net.jodk.lang.InterfaceBooleanCondition;

public class CondilockSample {
    
    //--------------------------------------------------------------------------
    // PUBLIC TREATMENTS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("--- "+CondilockSample.class.getSimpleName()+"... ---");

        final InterfaceCondilock condilock = new MonitorCondilock();
        
        final AtomicBoolean dinnerIsReady = new AtomicBoolean();
        final InterfaceBooleanCondition bc = new InterfaceBooleanCondition() {
            @Override
            public boolean isTrue() {
                return dinnerIsReady.get();
            }
        };
        
        final long cookingDurationMS = 1000L;
        final Thread cook = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(cookingDurationMS);
                } catch (InterruptedException e) {
                    throw new RuntimeException("cooking interrupted");
                }
                dinnerIsReady.set(true);
                condilock.signalAllInLock();
            }
        });
        cook.start();
        
        condilock.awaitWhileFalseInLockUninterruptibly(bc);
        System.out.println("dinner is ready = "+dinnerIsReady.get());
        
        System.out.println("--- ..."+CondilockSample.class.getSimpleName()+" ---");
    }
}
