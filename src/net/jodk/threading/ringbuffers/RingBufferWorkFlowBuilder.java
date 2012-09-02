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
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;

/**
 * Thread-safe (not supposed to be used concurrently,
 * but allows to throw appropriate exceptions if badly used,
 * instead of going into a weird state).
 * 
 * Utility class for creating workers dependencies:
 * - automatically removes superfluous dependencies
 *   between workers,
 * - automatically sorts ahead workers by creation order,
 *   to make things more deterministic, and in case it
 *   could help with performances,
 * - provides methods ensuring a single head worker,
 *   or a single tail worker, which can increase
 *   throughput (especially in case of high
 *   parallelism and contention).
 */
public class RingBufferWorkFlowBuilder {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean ASSERTIONS = false;

    //--------------------------------------------------------------------------
    // PUBLIC CLASSES
    //--------------------------------------------------------------------------
    
    /**
     * Virtual worker, used as place holder for actual worker,
     * while the work flow is being build.
     * The actual worker can be retrieved from it,
     * once the work flow has been applied on a ring buffer.
     */
    public class VirtualWorker implements Comparable<VirtualWorker> {
        private final int index;
        private final InterfaceRingBufferSubscriber subscriber;
        private VirtualWorker[] aheadVWorkers;
        private volatile InterfaceRingBufferWorker worker;
        private VirtualWorker(
                InterfaceRingBufferSubscriber subscriber,
                VirtualWorker... aheadWorkers) {
            this.index = vworkers.size();
            this.subscriber = subscriber;
            this.aheadVWorkers = aheadWorkers;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.index);
            if (this.aheadVWorkers.length != 0) {
                sb.append("(");
                for (int i=0;i<this.aheadVWorkers.length;i++) {
                    if (i != 0) {
                        sb.append(",");
                    }
                    sb.append(this.aheadVWorkers[i].index);
                }
                sb.append(")");
            }
            return sb.toString();
        }
        @Override
        public int compareTo(VirtualWorker other) {
            return this.index - other.index;
        }
        /**
         * @return The actual worker.
         * @throws IllegalStateException if the work flow has not yet been applied.
         */
        public InterfaceRingBufferWorker getWorker() {
            InterfaceRingBufferWorker worker = this.worker;
            if (worker == null) {
                throw new IllegalStateException();
            }
            return worker;
        }
        /**
         * @return Ahead workers.
         * @throws IllegalStateException if the work flow has not yet been applied.
         */
        public InterfaceRingBufferWorker[] getAheadWorkers() {
            // Reading worker for the check, and also
            // for the required volatile read.
            InterfaceRingBufferWorker worker = this.worker;
            if (worker == null) {
                throw new IllegalStateException();
            }
            
            return this.getAheadWorkers_raw();
        }
        private InterfaceRingBufferWorker[] getAheadWorkers_raw() {
            VirtualWorker[] aheadWorkers = this.aheadVWorkers;
            if (aheadWorkers == null) {
                return EMPTY_WORKERS_ARRAY;
            }
            InterfaceRingBufferWorker[] result = new InterfaceRingBufferWorker[aheadWorkers.length];
            for (int i=0;i<aheadWorkers.length;i++) {
                result[i] = aheadWorkers[i].getWorker();
            }
            return result;
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private static final InterfaceRingBufferWorker[] EMPTY_WORKERS_ARRAY = new InterfaceRingBufferWorker[0];
    
    private boolean applied = false;
    
    /**
     * Used as mutex.
     */
    private final ArrayList<VirtualWorker> vworkers = new ArrayList<VirtualWorker>();
    
    private final IdentityHashMap<InterfaceRingBufferSubscriber,VirtualWorker> vworkerBySubscriber =
            new IdentityHashMap<InterfaceRingBufferSubscriber,VirtualWorker>();

    private final HashSet<VirtualWorker> vworkersSet = new HashSet<VirtualWorker>();

    private final ArrayList<VirtualWorker> headVWorkersSet = new ArrayList<VirtualWorker>();
    private final HashSet<VirtualWorker> tailVWorkersSet = new HashSet<VirtualWorker>();

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Creates an empty work flow builder.
     */
    public RingBufferWorkFlowBuilder() {
    }
    
    /**
     * Convenience methods if wanting to only deal with subscribers.
     * 
     * @param subscriber A subscriber.
     * @param aheadSubscribers Subscribers which must process events
     *        before the specified subscriber.
     * @return The specified subscriber.
     * @throws IllegalStateException if the work flow has already been applied.
     * @throws IllegalArgumentException if a worker has already been
     *         created by this builder for the specified subscriber, or
     *         if an ahead subscriber is unknown for this builder.
     * @throws UnsupportedOperationException if this builder can't handle more
     *         subscribers.
     */
    public InterfaceRingBufferSubscriber addSubscriber(
            final InterfaceRingBufferSubscriber subscriber,
            final InterfaceRingBufferSubscriber... aheadSubscribers) {
        if (aheadSubscribers.length == 0) {
            this.newVWorker(subscriber);
        } else {
            if (subscriber == null) {
                throw new NullPointerException("subscriber is null");
            }
            for (InterfaceRingBufferSubscriber as : aheadSubscribers) {
                if (as == null) {
                    throw new NullPointerException("null ahead subscriber");
                }
            }
            synchronized (this.vworkers) {
                VirtualWorker[] aheadVWorkers = new VirtualWorker[aheadSubscribers.length];
                for (int i=0;i<aheadSubscribers.length;i++) {
                    InterfaceRingBufferSubscriber aheadSubscriber = aheadSubscribers[i];
                    if (aheadSubscriber == null) {
                        throw new NullPointerException("null ahead subscriber");
                    }
                    VirtualWorker aheadVWorker = this.vworkerBySubscriber.get(aheadSubscriber);
                    if (aheadVWorker == null) {
                        throw new IllegalArgumentException("subscriber "+aheadVWorker+" does not belong to this work flow");
                    }
                    aheadVWorkers[i] = aheadVWorker;
                }
                this.newVWorker(subscriber, aheadVWorkers);
            }
        }
        return subscriber;
    }
    
    /**
     * Specifies the creation of a worker for the specified subscriber,
     * and with workers of the specified virtual workers as ahead workers.
     * 
     * @param subscriber A subscriber.
     * @param aheadVWorkers Virtual workers which workers must process events
     *        before the specified subscriber.
     * @return Virtual worker created for the specified subscriber.
     * @throws IllegalStateException if the work flow has already been applied.
     * @throws IllegalArgumentException if a worker has already been
     *         created by this builder for the specified subscriber, or
     *         if an ahead worker is unknown for this builder.
     * @throws UnsupportedOperationException if this builder can't handle more
     *         subscribers.
     */
    public VirtualWorker newVWorker(
            final InterfaceRingBufferSubscriber subscriber,
            final VirtualWorker... aheadVWorkers) {
        if (subscriber == null) {
            throw new NullPointerException("subscriber is null");
        }
        for (VirtualWorker vw : aheadVWorkers) {
            if (vw == null) {
                throw new NullPointerException("null virtual worker");
            }
        }
        VirtualWorker vworker;
        synchronized (this.vworkers) {
            if (this.applied) {
                throw new IllegalStateException();
            }
            if (this.vworkerBySubscriber.containsKey(subscriber)) {
                throw new IllegalArgumentException("virtual worker already created for subscriber "+subscriber);
            }
            for (VirtualWorker vw : aheadVWorkers) {
                if (!this.vworkersSet.contains(vw)) {
                    throw new IllegalArgumentException("virtual worker "+vw+" does not belong to this work flow");
                }
            }
            if (this.vworkers.size() == Integer.MAX_VALUE) {
                throw new UnsupportedOperationException("full");
            }
            
            VirtualWorker[] reducedAheadVWorkers = reducedSortedAheadVWorkers(subscriber,aheadVWorkers);

            vworker = new VirtualWorker(subscriber, reducedAheadVWorkers);

            if (reducedAheadVWorkers.length == 0) {
                this.headVWorkersSet.add(vworker);
            }

            for (VirtualWorker avw : reducedAheadVWorkers) {
                this.tailVWorkersSet.remove(avw);
            }
            this.tailVWorkersSet.add(vworker);

            this.vworkers.add(vworker);
            this.vworkersSet.add(vworker);
            this.vworkerBySubscriber.put(subscriber, vworker);
        }
        return vworker;
    }

    /**
     * Ensures that there is a single head worker,
     * by making other head workers depending on first
     * created worker.
     * Also removes eventual now useless dependencies.
     * 
     * Having a single head worker can yield better performances,
     * because only the head worker has to check max published sequence,
     * which lowers the contention on this possibly often-updated value.
     * 
     * @throws IllegalStateException if the work flow has already been applied.
     */
    public void ensureSingleHeadWorker() {
        synchronized (this.vworkers) {
            if (this.applied) {
                throw new IllegalStateException();
            }
            if (headVWorkersSet.size() <= 1) {
                return;
            }
            
            // Making sure all head workers except first one,
            // depend on first one, so that only it will check
            // max published sequence (less contention
            // on it helps since it is typically
            // incremented often).
            VirtualWorker theHeadVWorker = this.vworkers.get(0);
            VirtualWorker[] theHeadVWorkerInArray = new VirtualWorker[]{theHeadVWorker};
            for (VirtualWorker headVWorker : headVWorkersSet) {
                if (headVWorker == theHeadVWorker) {
                    continue;
                }
                headVWorker.aheadVWorkers = theHeadVWorkerInArray;
            }
            // Reducing ahead workers for all workers, in case some workers
            // were directly depending on first worker, and no longer need to.
            for (VirtualWorker vworker : this.vworkers) {
                vworker.aheadVWorkers = reducedSortedAheadVWorkers(
                        vworker.subscriber,
                        vworker.aheadVWorkers);
            }
            // Head workers set is now a singleton.
            headVWorkersSet.clear();
            headVWorkersSet.add(theHeadVWorker);
            // In case it was also a tail worker.
            tailVWorkersSet.remove(theHeadVWorker);
        }
    }
    
    /**
     * Ensuring that there is a single tail worker, by making
     * last created worker depend on other tail workers.
     * Also removes eventual now useless dependencies.
     * 
     * Having a single tail worker can yield better performances,
     * because publishers only have to check one worker.
     * 
     * @throws IllegalStateException if the work flow has already been applied.
     */
    public void ensureSingleTailWorker() {
        synchronized (this.vworkers) {
            if (this.applied) {
                throw new IllegalStateException();
            }
            if (tailVWorkersSet.size() <= 1) {
                return;
            }
            
            // Making sure last worker is the only tail worker,
            // by making it depend on specified tail workers.
            VirtualWorker theTailVWorker = vworkers.get(vworkers.size()-1);
            // Computing new ahead workers for the tail worker,
            // i.e. adding other tail workers to it (we know they
            // are not in it already since they are tail workers,
            // so no need to use a set).
            ArrayList<VirtualWorker> newAheadVWorkersList = new ArrayList<VirtualWorker>();
            for (VirtualWorker avw : theTailVWorker.aheadVWorkers) {
                newAheadVWorkersList.add(avw);
            }
            for (VirtualWorker tailVWorker : tailVWorkersSet) {
                if (tailVWorker == theTailVWorker) {
                    continue;
                }
                newAheadVWorkersList.add(tailVWorker);
            }
            VirtualWorker[] newAheadVWorkers = newAheadVWorkersList.toArray(new VirtualWorker[newAheadVWorkersList.size()]);
            // Reducing ahead workers for the tail worker, in case
            // it no longer needs to depend on workers it was depending on.
            newAheadVWorkers = reducedSortedAheadVWorkers(
                    theTailVWorker.subscriber,
                    newAheadVWorkers);
            theTailVWorker.aheadVWorkers = newAheadVWorkers;
            // In case it was also a head worker.
            headVWorkersSet.remove(theTailVWorker);
            // Tail workers set is now a singleton.
            tailVWorkersSet.clear();
            tailVWorkersSet.add(theTailVWorker);
        }
    }

    /**
     * Applies the work flow to the specified worker builder.
     * Can only be applied once.
     * 
     * @throws IllegalStateException if the work flow is empty,
     *         or has already been applied (without exception).
     */
    public void apply(InterfaceRingBufferWorkerFactory workerFactory) {
        synchronized (this.vworkers) {
            if (this.applied || (this.vworkers.size() == 0)) {
                throw new IllegalStateException();
            }
            for (VirtualWorker vworker : this.vworkers) {
                InterfaceRingBufferWorker worker = workerFactory.newWorker(
                        vworker.subscriber,
                        vworker.getAheadWorkers_raw());
                if (worker == null) {
                    throw new NullPointerException();
                }
                vworker.worker = worker;
            }
            this.applied = true;
        }
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * This method is recursive.
     */
    private static boolean isVW1AheadOfVW2(
            VirtualWorker vw1,
            VirtualWorker vw2) {
        if (vw1.index >= vw2.index) {
            // Can't be, since vw2 is the same
            // or has been created before.
            return false;
        }
        for (VirtualWorker avw : vw2.aheadVWorkers) {
            if (vw1 == avw) {
                return true;
            }
            if (isVW1AheadOfVW2(vw1,avw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param subscriber Subscriber for which no worker has already
     *        been created by this helper.
     * @param aheadVWorkers Virtual workers already created by this helper.
     * @param Reduced (without transitively-ahead workers) and sorted ahead workers.
     */
    private VirtualWorker[] reducedSortedAheadVWorkers(
            InterfaceRingBufferSubscriber subscriber,
            VirtualWorker[] aheadVWorkers) {
        if (aheadVWorkers.length == 0) {
            return aheadVWorkers;
        }
        
        Arrays.sort(aheadVWorkers);
        
        /*
         * Eliminating duplications (we allow stuttering users).
         */
        
        ArrayList<VirtualWorker> aheadVWorkersSortedUnique = new ArrayList<VirtualWorker>();
        {
            VirtualWorker previous = aheadVWorkers[0];
            aheadVWorkersSortedUnique.add(previous);
            for (int i=1;i<aheadVWorkers.length;i++) {
                VirtualWorker current = aheadVWorkers[i];
                if (current != previous) {
                    aheadVWorkersSortedUnique.add(current);
                }
                previous = current;
            }
        }
        
        // This list will contain workers in sorted order:
        // this might help (ahead workers already been scanned
        // in the same order (which is also creation order)).
        ArrayList<VirtualWorker> reducedAheadVWorkers = new ArrayList<VirtualWorker>();
        for (int i=0;i<aheadVWorkersSortedUnique.size();i++) {
            VirtualWorker vw1 = aheadVWorkersSortedUnique.get(i);
            boolean foundInAheadOfAhead = false;
            // Can start at i+1 since workers are sorted.
            for (int j=i+1;j<aheadVWorkersSortedUnique.size();j++) {
                VirtualWorker vw2 = aheadVWorkersSortedUnique.get(j);
                if (isVW1AheadOfVW2(vw1, vw2)) {
                    foundInAheadOfAhead = true;
                    break;
                }
            }
            if (!foundInAheadOfAhead) {
                reducedAheadVWorkers.add(vw1);
            }
        }

        /*
         * 
         */
        
        // Can't be empty, since a worker can't depend on itself.
        if(ASSERTIONS)assert(reducedAheadVWorkers.size() != 0);
        
        return reducedAheadVWorkers.toArray(new VirtualWorker[reducedAheadVWorkers.size()]);
    }
}
