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
package net.jodk.threading.ringbuffers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import junit.framework.TestCase;

import net.jodk.threading.ringbuffers.RingBufferWorkFlowBuilder.VirtualWorker;

public class RingBufferWorkFlowBuilderTest extends TestCase {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean DEBUG = false;
    
    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static class MySubscriber implements InterfaceRingBufferSubscriber {
        @Override
        public void readEvent(long sequence, int index) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean processReadEvent() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void onBatchEnd() {
            throw new UnsupportedOperationException();
        }
    }
    
    private static class MyWorker implements InterfaceRingBufferWorker {
        final int index;
        final InterfaceRingBufferSubscriber subscriber;
        final MyWorker[] aheadWorkers;
        public MyWorker(
                int index,
                InterfaceRingBufferSubscriber subscriber,
                MyWorker... aheadWorkers) {
            this.index = index;
            this.subscriber = subscriber;
            this.aheadWorkers = aheadWorkers;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.index);
            if (this.aheadWorkers.length != 0) {
                sb.append("(");
                for (int i=0;i<this.aheadWorkers.length;i++) {
                    if (i != 0) {
                        sb.append(",");
                    }
                    sb.append(this.aheadWorkers[i].index);
                }
                sb.append(")");
            }
            return sb.toString();
        }
        @Override
        public void reset() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void setStateTerminateWhenIdle() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void setStateTerminateASAP() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void runWorkerFrom(long startSequence) throws InterruptedException {
            throw new UnsupportedOperationException();
        }
        @Override
        public void runWorker() throws InterruptedException {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getMaxPassedSequence() {
            throw new UnsupportedOperationException();
        }
    }
    
    private static class MyWorkerFactory implements InterfaceRingBufferWorkerFactory {
        private int nextIndex = 0;
        final ArrayList<MyWorker> workers = new ArrayList<MyWorker>();
        @Override
        public InterfaceRingBufferWorker newWorker(
                InterfaceRingBufferSubscriber subscriber,
                InterfaceRingBufferWorker... aheadWorkers) {
            MyWorker[] aheadWorkersImpl = new MyWorker[aheadWorkers.length];
            for (int i=0;i<aheadWorkers.length;i++) {
                aheadWorkersImpl[i] = (MyWorker)aheadWorkers[i];
            }
            MyWorker worker = new MyWorker(
                    this.nextIndex++,
                    subscriber,
                    aheadWorkersImpl);
            this.workers.add(worker);
            return worker;
        }
    }
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    public void test_addSubscriber_InterfaceRingBufferSubscriber_arrayOfInterfaceRingBufferSubscriber() {
        /*
         * null subscriber or ahead subscriber
         */
        
        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            
            try {
                builder.addSubscriber(null);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
            
            try {
                builder.addSubscriber(new MySubscriber(), (MySubscriber)null);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
            
            try {
                builder.addSubscriber(new MySubscriber(), new MySubscriber[1]);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
        }
        
        /*
         * subscriber already known
         */
        
        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            MySubscriber subscriber = new MySubscriber();
            builder.addSubscriber(subscriber);
            try {
                builder.addSubscriber(subscriber);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        
        /*
         * ahead subscriber unknown
         */
        
        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            try {
                builder.addSubscriber(new MySubscriber(), new MySubscriber());
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        
        /*
         * work flow already applied
         */

        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            builder.addSubscriber(new MySubscriber());
            builder.apply(new MyWorkerFactory());
            try {
                builder.addSubscriber(new MySubscriber());
                assertTrue(false);
            } catch (IllegalStateException e) {
                // ok
            }
        }

        /*
         * simple case
         */
        
        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            MySubscriber subscriber1 = new MySubscriber();
            MySubscriber subscriber2 = new MySubscriber();
            assertSame(subscriber1, builder.addSubscriber(subscriber1));
            assertSame(subscriber2, builder.addSubscriber(subscriber2, subscriber1));
            
            MyWorkerFactory workerFactory = new MyWorkerFactory();
            
            builder.apply(workerFactory);
            
            MyWorker worker1 = workerFactory.workers.get(0);
            MyWorker worker2 = workerFactory.workers.get(1);
            
            assertSame(worker1.subscriber, subscriber1);
            assertSame(worker2.subscriber, subscriber2);
            assertSame(0, worker1.aheadWorkers.length);
            assertSame(1, worker2.aheadWorkers.length);
            assertSame(worker1, worker2.aheadWorkers[0]);
        }
    }
    
    public void test_newVWorker_InterfaceRingBufferSubscriber_arrayOfVirtualWorker() {
        /*
         * null subscriber or ahead vworker
         */
        
        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            
            try {
                builder.newVWorker(null);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
            
            try {
                builder.newVWorker(new MySubscriber(), (VirtualWorker[])null);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
            
            try {
                builder.newVWorker(new MySubscriber(), new VirtualWorker[1]);
                assertTrue(false);
            } catch (NullPointerException e) {
                // ok
            }
        }
        
        /*
         * subscriber already known
         */
        
        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            MySubscriber subscriber = new MySubscriber();
            builder.newVWorker(subscriber);
            try {
                builder.newVWorker(subscriber);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        
        /*
         * ahead vworker unknown
         */
        
        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            RingBufferWorkFlowBuilder builder2 = new RingBufferWorkFlowBuilder();
            VirtualWorker vworker2 = builder2.newVWorker(new MySubscriber());
            try {
                builder.newVWorker(new MySubscriber(), vworker2);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        
        /*
         * work flow already applied
         */

        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            builder.newVWorker(new MySubscriber());
            builder.apply(new MyWorkerFactory());
            try {
                builder.newVWorker(new MySubscriber());
                assertTrue(false);
            } catch (IllegalStateException e) {
                // ok
            }
        }

        /*
         * simple case
         */
        
        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            MySubscriber subscriber1 = new MySubscriber();
            MySubscriber subscriber2 = new MySubscriber();
            VirtualWorker vworker1 = builder.newVWorker(subscriber1);
            VirtualWorker vworker2 = builder.newVWorker(subscriber2, vworker1);
            
            MyWorkerFactory workerFactory = new MyWorkerFactory();
            
            builder.apply(workerFactory);
            
            MyWorker worker1 = workerFactory.workers.get(0);
            MyWorker worker2 = workerFactory.workers.get(1);

            assertSame(worker1.subscriber, subscriber1);
            assertSame(worker2.subscriber, subscriber2);
            assertSame(0, worker1.aheadWorkers.length);
            assertSame(1, worker2.aheadWorkers.length);
            assertSame(worker1, worker2.aheadWorkers[0]);

            assertSame(worker1, vworker1.getWorker());
            assertSame(worker2, vworker2.getWorker());

            assertSame(0, vworker1.getAheadWorkers().length);
            assertSame(1, vworker2.getAheadWorkers().length);
            assertSame(worker1, vworker2.getAheadWorkers()[0]);
        }
    }

    /**
     * Tests
     * - apply(InterfaceRingBuffer)
     * - ensureSingleHeadWorker()
     * - ensureSingleTailWorker()
     */
    public void test_workersReductionsAndOrdering() {
        
        /*
         * work flow already applied
         */

        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            builder.newVWorker(new MySubscriber());
            builder.apply(new MyWorkerFactory());
            try {
                builder.apply(new MyWorkerFactory());
                assertTrue(false);
            } catch (IllegalStateException e) {
                // ok
            }
        }

        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            builder.newVWorker(new MySubscriber());
            builder.apply(new MyWorkerFactory());
            try {
                builder.ensureSingleHeadWorker();
                assertTrue(false);
            } catch (IllegalStateException e) {
                // ok
            }
        }
        
        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            builder.newVWorker(new MySubscriber());
            builder.apply(new MyWorkerFactory());
            try {
                builder.ensureSingleTailWorker();
                assertTrue(false);
            } catch (IllegalStateException e) {
                // ok
            }
        }
        
        /*
         * virtual worker's getWorker() method,
         * before and after apply
         */

        {
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            MyWorkerFactory workerFactory = new MyWorkerFactory();
            
            MySubscriber s1 = new MySubscriber();
            VirtualWorker vw1 = builder.newVWorker(s1);
            try {
                vw1.getWorker();
                assertTrue(false);
            } catch (IllegalStateException e) {
                // ok
            }
            builder.apply(workerFactory);
            vw1.getWorker();
        }
        
        /*
         * reduction and ordering
         */
        
        final int nbrOfCases = 10 * 1000;
        
        boolean foundReduction = false;
        
        for (int k=0;k<nbrOfCases;k++) {
            final Random random = new Random(k);
            RingBufferWorkFlowBuilder builder = new RingBufferWorkFlowBuilder();
            MyWorkerFactory workerFactory = new MyWorkerFactory();
            
            final int nbrOfSubscribers = 1 + random.nextInt(10);
            final boolean ensureSingleHeadWorker = random.nextBoolean();
            final boolean ensureSingleTailWorker = random.nextBoolean();
            final boolean ensureSingleHeadBeforeSingleTail = random.nextBoolean();
            
            if (DEBUG) {
                System.out.println("---");
                System.out.println("nbrOfSubscribers = "+nbrOfSubscribers);
                System.out.println("ensureSingleHeadWorker = "+ensureSingleHeadWorker);
                System.out.println("ensureSingleTailWorker = "+ensureSingleTailWorker);
                if (ensureSingleHeadWorker && ensureSingleTailWorker) {
                    System.out.println("ensureSingleHeadBeforeSingleTail = "+ensureSingleHeadBeforeSingleTail);
                }
            }
            
            HashSet<VirtualWorker> headVWorkersSet = new HashSet<VirtualWorker>();
            HashSet<VirtualWorker> tailVWorkersSet = new HashSet<VirtualWorker>();

            ArrayList<MySubscriber> subscribersList = new ArrayList<MySubscriber>();
            ArrayList<VirtualWorker> vworkersList = new ArrayList<VirtualWorker>();
            ArrayList<ArrayList<VirtualWorker>> aheadVWorkersListByIndex = new ArrayList<ArrayList<VirtualWorker>>();
            for (int i=0;i<nbrOfSubscribers;i++) {
                MySubscriber subscriber = new MySubscriber();
                ArrayList<VirtualWorker> aheadVWorkersList = new ArrayList<VirtualWorker>();
                // Eventually ahead workers (not necessarily sorted by creation).
                if ((vworkersList.size() != 0) && random.nextBoolean()) {
                    ArrayList<Integer> aheadVWorkersIndexes = randomIndexes(random, vworkersList.size());
                    for (int avwi : aheadVWorkersIndexes) {
                        aheadVWorkersList.add(vworkersList.get(avwi));
                    }
                }
                if (DEBUG) {
                    if (aheadVWorkersList.size() != 0) {
                        System.out.println("vworker "+i+"("+aheadVWorkersList+")");
                    } else {
                        System.out.println("vworker "+i);
                    }
                }

                VirtualWorker[] aheadVWorkers = aheadVWorkersList.toArray(new VirtualWorker[aheadVWorkersList.size()]);
                VirtualWorker vworker = builder.newVWorker(subscriber,aheadVWorkers);
                subscribersList.add(subscriber);
                vworkersList.add(vworker);
                aheadVWorkersListByIndex.add(aheadVWorkersList);
                
                if (aheadVWorkers.length == 0) {
                    headVWorkersSet.add(vworker);
                }
                
                for (VirtualWorker aw : aheadVWorkers) {
                    tailVWorkersSet.remove(aw);
                }
                tailVWorkersSet.add(vworker);
            }
            
            if (DEBUG) {
                System.out.println("headVWorkersSet = "+headVWorkersSet);
                System.out.println("tailVWorkersSet = "+tailVWorkersSet);
            }

            /*
             * calls
             */
            
            if (ensureSingleHeadBeforeSingleTail) {
                if (ensureSingleHeadWorker) {
                    builder.ensureSingleHeadWorker();
                }
                if (ensureSingleTailWorker) {
                    builder.ensureSingleTailWorker();
                }
            } else {
                if (ensureSingleTailWorker) {
                    builder.ensureSingleTailWorker();
                }
                if (ensureSingleHeadWorker) {
                    builder.ensureSingleHeadWorker();
                }
            }
            builder.apply(workerFactory);
            
            /*
             * computing head/tail workers sets
             */
            
            HashSet<MyWorker> headWorkersSet = new HashSet<MyWorker>();
            HashSet<MyWorker> tailWorkersSet = new HashSet<MyWorker>();
            // Initializing tail workers set to all workers,
            // and will remove those that are ahead workers of others.
            for (int i=0;i<subscribersList.size();i++) {
                MyWorker worker = (MyWorker)vworkersList.get(i).getWorker();
                tailWorkersSet.add(worker);
            }
            // Finishing to compute sets.
            for (int i=0;i<subscribersList.size();i++) {
                MyWorker worker = (MyWorker)vworkersList.get(i).getWorker();
                if (worker.aheadWorkers.length == 0) {
                    headWorkersSet.add(worker);
                }
                for (MyWorker aw : worker.aheadWorkers) {
                    tailWorkersSet.remove(aw);
                }
            }
            
            if (DEBUG) {
                System.out.println("headWorkersSet = "+headWorkersSet);
                System.out.println("tailWorkersSet = "+tailWorkersSet);
            }

            /*
             * checks
             */

            if (ensureSingleHeadWorker) {
                assertEquals(1, headWorkersSet.size());
            }
            
            if (ensureSingleTailWorker) {
                assertEquals(1, tailWorkersSet.size());
            }
            
            for (int i=0;i<subscribersList.size();i++) {
                MySubscriber subscriber = subscribersList.get(i);
                VirtualWorker vworker = vworkersList.get(i);
                MyWorker worker = (MyWorker)vworker.getWorker();
                
                if (DEBUG) {
                    ArrayList<MyWorker> aheadWorkersList = new ArrayList<MyWorker>();
                    for (MyWorker aw : worker.aheadWorkers) {
                        aheadWorkersList.add(aw);
                    }
                    if (aheadWorkersList.size() != 0) {
                        System.out.println("worker "+i+"("+aheadWorkersList+")");
                    } else {
                        System.out.println("worker "+i);
                    }
                }

                assertSame(subscriber, worker.subscriber);
                
                ArrayList<VirtualWorker> aheadVWorkersList = aheadVWorkersListByIndex.get(i);
                boolean specialCase = false;
                if (ensureSingleHeadWorker
                        && headVWorkersSet.contains(vworker)
                        && (i != 0)) {
                    specialCase = true;
                    // Was a head worker and must no longer be.
                    if (ensureSingleTailWorker
                            && (i == (subscribersList.size()-1))) {
                        // Ensured single tail worker, and it was for this worker,
                        // so it might now have multiple ahead workers.
                    } else {
                        assertEquals(1, worker.aheadWorkers.length);
                    }
                    MyWorker theHeadWorker = (MyWorker)headWorkersSet.toArray()[0];
                    assertTrue(isW1AheadOfW2(theHeadWorker, worker));
                }
                if (ensureSingleTailWorker
                        && tailVWorkersSet.contains(vworker)
                        && (i == (subscribersList.size()-1))) {
                    specialCase = true;
                    // Was a tail worker and must still be.
                    assertTrue(tailWorkersSet.contains(worker));
                    // All previous tail workers that no longer
                    // are, must be ahead of the remaining tail worker.
                    for (VirtualWorker tvw : tailVWorkersSet) {
                        if (tvw == vworker) {
                            continue;
                        }
                        assertTrue(isW1AheadOfW2((MyWorker)tvw.getWorker(), worker));
                    }
                }
                if (!specialCase) {
                    // Can have removed useless dependencies, but not added.
                    assertTrue(worker.aheadWorkers.length <= aheadVWorkersList.size());
                }
                if (worker.aheadWorkers.length < aheadVWorkersList.size()) {
                    foundReduction = true;
                }
                // Sorted.
                assertSorted(worker.aheadWorkers);
                // Checking that each ahead worker is not already
                // ahead of another ahead worker.
                for (MyWorker aw1 : worker.aheadWorkers) {
                    for (MyWorker aw2 : worker.aheadWorkers) {
                        assertFalse(isW1AheadOfW2(aw1, aw2));
                    }
                }
                // Checking that no dependency has been lost.
                for (VirtualWorker avw : aheadVWorkersList) {
                    MyWorker aw1 = (MyWorker)avw.getWorker();
                    boolean foundItAhead = false;
                    for (MyWorker aw2 : worker.aheadWorkers) {
                        if ((aw1 == aw2) || isW1AheadOfW2(aw1, aw2)) {
                            foundItAhead = true;
                            break;
                        }
                    }
                    assertTrue(foundItAhead);
                }
            }
        }
        
        assertTrue(foundReduction);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    private static void assertSorted(MyWorker... workers) {
        for (int i=1;i<workers.length;i++) {
            assertTrue(workers[i-1].index < workers[i].index);
        }
    }
    
    private static boolean isW1AheadOfW2(
            MyWorker w1,
            MyWorker w2) {
        for (MyWorker aw : w2.aheadWorkers) {
            if (w1 == aw) {
                return true;
            }
            if (isW1AheadOfW2(w1,aw)) {
                return true;
            }
        }
        return false;
    }

    private static ArrayList<Integer> randomIndexes(
            Random random,
            int size) {
        if (size == 0) {
            throw new IllegalArgumentException();
        }
        
        ArrayList<Integer> indexes = new ArrayList<Integer>();
        for (int i=0;i<size;i++) {
            indexes.add(i);
        }
        
        int nbrOfIndexes = 1 + random.nextInt(size);
        int nbrOfIndexesToRemove = size - nbrOfIndexes;
        for (int i=0;i<nbrOfIndexesToRemove;i++) {
            int index = random.nextInt(indexes.size());
            if (index == indexes.size()-1) {
                // Remove last.
                indexes.remove(index);
            } else {
                // Erasing index to remove with previous last index,
                // which is in O(1).
                int last = indexes.remove(indexes.size()-1);
                indexes.set(index, last);
            }
        }
        
        return indexes;
    }
}
