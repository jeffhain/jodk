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

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import net.jodk.lang.IntWrapper;
import net.jodk.lang.InterfaceBooleanCondition;
import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;
import net.jodk.threading.NonNullOrAtomicReference;
import net.jodk.threading.PostPaddedAtomicInteger;
import net.jodk.threading.PostPaddedAtomicLong;
import net.jodk.threading.PostPaddedAtomicReference;
import net.jodk.threading.locks.InterfaceCondilock;
import net.jodk.threading.locks.MonitorCondilock;

/**
 * For non-service multicast ring buffers, there is no coordination
 * between terminating workers, which consider themselves idle (when
 * their state is TERMINATE_WHEN_IDLE), only if detecting that the sequence
 * they have to read has not been (visibly) published yet.
 * This might cause a worker to either block, waiting for a terminated
 * ahead worker to make progress, or keep processing events, if not
 * processing them faster than publisher(s) publish them.
 * 
 * See AbstractRingBuffer Javadoc for more info.
 */
public class MulticastRingBuffer extends AbstractRingBuffer {

    /*
     * Condilocks usage:
     * - readWaitCondilock:
     *   - waited on by publishers, for tail workers to make progress.
     *   - waited on by workers, for ahead workers to make progress.
     * - writeWaitCondilock:
     *   - waited on by head workers, for publishers to make progress.
     */
    
    /*
     * It could be more efficient, in case of together high parallelism
     * and high contention, not to have publishing serialization
     * done by workers, but done by publishers (using pending
     * publications actually published by publishers that fill
     * the lowest publication hole), but it would increase the overhead
     * on publishers side (systematic CAS, and lazySet not possible),
     * divide usual max throughput by 1.5 or 2, and simply using
     * a single head or tail worker can make up for it.
     */
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean ASSERTIONS = false;
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------
    
    /**
     * True or false, depending on balance between CAS cost
     * and re-catch-up cost. Seems about equivalent.
     */
    private static final boolean MPSC_MONOTONIC_WITH_CAS = false;

    private static final boolean MPSC_CAS_ONLY_IF_GOOD_CHANCE = true;

    /**
     * If true, throughput should be a bit (or a lot, if false sharing)
     * lower, but reactivity to state change is better.
     * 
     * If false, makes it like original ring buffer design.
     * 
     * Set to true, for reactivity to state change.
     * 
     * NB: If false and ring buffer is a service, you can still
     * check ring buffer state when processing events,
     * and make processReadEvent() method return true
     * if wanting to stop processing events.
     */
    private static final boolean CHECK_STATE_IN_BATCH = true;

    /**
     * If true, throughput might be better.
     * 
     * If false, publishers and other workers can only see each worker advance
     * when it finds no more sequence to read, or has to wait for a ahead worker
     * to make progress, which typically increases latency, and might also reduce
     * parallelism (and throughput) in case of dependent workers, if each of (n-1)
     * workers waits for another to read the whole published sequences before
     * starting to read them.
     * 
     * Set to false, i.e. as done in original ring buffer design,
     * to avoid the downsides described above.
     */
    private static final boolean SET_VOLATILE_MPS_ONLY_BEFORE_WAITING = false;

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    /*
     * subscribers side
     */

    /**
     * To wait for a sequence to be written, when running.
     */
    private static class MyWriteWaitStopBC implements InterfaceBooleanCondition {
        final MulticastRingBuffer ringBuffer;
        final boolean singlePublisher;
        final PostPaddedAtomicLong sharedMaxPubSeqContiguous;
        final PostPaddedAtomicInteger state;
        int localState_WWSBC; // in
        long sequence_WWSBC; // in
        boolean trueBecauseOfState_WWSBC; // out
        /**
         * Undefined if returned true because of state.
         */
        long localMPSC_WWSBC = INITIAL_SEQUENCE-1;
        public MyWriteWaitStopBC(MyMulticastWorker worker) {
            this.ringBuffer = worker.ringBuffer;
            this.singlePublisher = worker.ringBuffer.singlePublisher;
            this.sharedMaxPubSeqContiguous = worker.ringBuffer.sharedMaxPubSeqContiguous;
            this.state = worker.state;
        }
        /**
         * If returns true because of state,
         * highestAvailableSequence_WWSBC is undefined.
         */
        @Override
        public boolean isTrue() {
            return isTrue_static(this);
        }
        public static boolean isBehindPublishers(MyWriteWaitStopBC bc, long sequence) {
            if (bc.singlePublisher) {
                return (sequence <= bc.localMPSC_WWSBC)
                        || (sequence <= (bc.localMPSC_WWSBC = bc.sharedMaxPubSeqContiguous.get()));
            } else {
                return isBehindMultiPublishers(bc, sequence);
            }
        }
        private static boolean isBehindMultiPublishers(MyWriteWaitStopBC bc, long sequence) {
            // Checking local first helps, when used after ahead workers
            // progression check, when we check for idleness, but it doesn't
            // help if no ahead worker, since we usually read up to
            // max published sequence (unless lower end sequence).
            if (sequence <= bc.localMPSC_WWSBC) {
                return true;
            }
            if (MPSC_MONOTONIC_WITH_CAS) {
                if (sequence <= (bc.localMPSC_WWSBC = bc.sharedMaxPubSeqContiguous.get())) {
                    return true;
                }
            } else {
                // Volatile value can go backward:
                // taking care that our local value doesn't.
                long recentMPSC = bc.sharedMaxPubSeqContiguous.get();
                if (sequence <= recentMPSC) {
                    bc.localMPSC_WWSBC = recentMPSC;
                    return true;
                }
            }
            if (sequence <= (bc.localMPSC_WWSBC = bc.ringBuffer.updateAndGetMaxPubSeqContiguous(bc.localMPSC_WWSBC))) {
                return true;
            }
            return false;
        }
        private static boolean isTrue_static(MyWriteWaitStopBC bc) {
            // Checking state before, so that when wait stops, user
            // does not have to check state again to know if it stopped
            // because of state or not (this helps because we only start
            // to eventually wait after a first check of max published sequence).
            if (bc.trueBecauseOfState_WWSBC = (bc.state.get() > bc.localState_WWSBC)) {
                return true;
            }
            return isBehindPublishers(bc, bc.sequence_WWSBC);
        }
    }

    /**
     * To wait for a sequence to be read, when running.
     */
    private static class MyReadWaitStopBC implements InterfaceBooleanCondition {
        final PostPaddedAtomicLong[] workersMPSCounters_SWC;
        final PostPaddedAtomicInteger state;
        int localState_RWSBC; // in
        long sequence_RWSBC; // in
        boolean trueBecauseOfState_RWSBC; // out
        /**
         * Only increases.
         */
        long lastMinWorkerMPS_SWC = INITIAL_SEQUENCE-1; // out
        /**
         * @param aheadWorkers Workers array. Must not be empty.
         */
        public MyReadWaitStopBC(
                final MyMulticastWorker worker,
                final InterfaceRingBufferWorker[] aheadWorkers) {
            this.state = worker.state;
            PostPaddedAtomicLong[] aheadCounters = new PostPaddedAtomicLong[aheadWorkers.length];
            for (int i=0;i<aheadWorkers.length;i++) {
                aheadCounters[i] = ((MyMulticastWorker)aheadWorkers[i]).getMPSCounter();
            }
            this.workersMPSCounters_SWC = aheadCounters;
        }
        @Override
        public boolean isTrue() {
            return isTrue_static(this);
        }
        /**
         * Supposes that workers MPS increase monotonically,
         * i.e. that no worker goes backward, which is ensured by
         * setStartSequence(...) method.
         * 
         * @return True is workers to check are all
         */
        public static boolean isBehindWorkersToCheck(MyReadWaitStopBC bc, long sequence) {
            return (sequence <= bc.lastMinWorkerMPS_SWC)
                    || (sequence <= (bc.lastMinWorkerMPS_SWC = computeMinMaxPassedSequenceNotNull(bc.workersMPSCounters_SWC, bc.lastMinWorkerMPS_SWC)));
        }
        private static boolean isTrue_static(MyReadWaitStopBC bc) {
            // Checking state before, so that when wait stops, user
            // does not have to check state again to know if it stopped
            // because of state or not (this helps because we only start
            // to eventually wait after a first check of workers progression).
            if (bc.trueBecauseOfState_RWSBC = (bc.state.get() > bc.localState_RWSBC)) {
                return true;
            }
            return isBehindWorkersToCheck(bc, bc.sequence_RWSBC);
        }
    }

    /**
     * Only possible states for non-service ring buffer workers:
     * - RUNNING
     * - TERMINATE_WHEN_IDLE
     * - TERMINATE_ASAP
     * 
     * For performance purpose (since a non-service worker
     * might be used for small parallelization sessions),
     * and because it doesn't hurt, worker's state
     * is not set in main lock (as ring buffer state is).
     * 
     * Comparable for tail workers sorting (might help
     * to scan them in creation order).
     */
    private static class MyMulticastWorker extends MyAbstractWorker implements Comparable<MyMulticastWorker> {
        final MulticastRingBuffer ringBuffer;
        final MyWriteWaitStopBC writeWaitStopBC;
        final MyReadWaitStopBC readWaitStopBC;
        /**
         * 0 for first created worker, etc.
         * 
         * For multicast ring buffers, workers are
         * created in main lock, before add to workers
         * list, so we can use its size as index provider.
         */
        final int workerIndex;
        //
        /**
         * We need this value in case of service, to keep track
         * of our progression among states, in case of exceptions
         * and re-runs, to make sure not to increment workers progression
         * counters multiple times for a same state transition.
         */
        int stateToBeDoneWith = STATE_RUNNING;
        @Override
        public int compareTo(MyMulticastWorker other) {
            return this.workerIndex - other.workerIndex;
        }
        public MyMulticastWorker(
                MulticastRingBuffer ringBuffer,
                PostPaddedAtomicInteger state,
                InterfaceRingBufferSubscriber subscriber,
                InterfaceRingBufferWorker... aheadWorkers) {
            super(
                    ringBuffer,
                    state,
                    subscriber);
            this.ringBuffer = ringBuffer;
            this.workerIndex = ringBuffer.workers.size();
            this.writeWaitStopBC = new MyWriteWaitStopBC(this);
            if (aheadWorkers.length != 0) {
                this.readWaitStopBC = new MyReadWaitStopBC(
                        this,
                        aheadWorkers);
            } else {
                this.readWaitStopBC = null;
            }
        }
        @Override
        public void reset() {
            if (this.service) {
                throw new UnsupportedOperationException();
            }
            // Setting non-volatile before volatile set.
            this.stateToBeDoneWith = STATE_RUNNING;
            super.reset();
        }
        /**
         * @param startSequence Must be > to max passed sequence.
         */
        @Override
        public void runWorkerFrom(long startSequence) throws InterruptedException {
            if (this.service) {
                throw new UnsupportedOperationException();
            }
            
            // Volatile read.
            final int recentState = this.state.get();
            
            final long mps = this.getMaxPassedSequenceLocal();
            final long newMPS = startSequence-1;
            if (newMPS != mps) {
                if (newMPS < mps) {
                    throw new IllegalArgumentException("worker can't go backward ("+startSequence+" <= "+mps+")");
                }
                this.setMaxPassedSequenceAndSignalAllInLock(newMPS);
            }
            
            this.runWorkerImplAfterStateAndMPSUpdates(recentState);
        }
        @Override
        protected void afterLastMaxPassedSequenceSetting() {
            this.signalAllInLockAfterMPSSetting();
        }
        @Override
        protected void innerRunWorker(int recentState) throws InterruptedException {
            if (this.service) {
                innerRunWorker_service(this, recentState);
            } else {
                innerRunWorker_nonService(this, recentState);
            }
        }
        /**
         * Specific small method for non-service case.
         */
        private static void innerRunWorker_nonService(MyMulticastWorker worker, int recentState) throws InterruptedException {
            if (recentState == STATE_RUNNING) {
                boolean subscriberStop = runStateSpecific(worker, STATE_RUNNING,Long.MAX_VALUE);
                if (subscriberStop) {
                    return;
                }
            }

            // If not service, no coordination between workers ends,
            // but might want to process events until there is no more to process,
            // so we can still run with TERMINATE_WHEN_IDLE state.
            if ((recentState = worker.state.get()) == STATE_TERMINATE_WHEN_IDLE) {
                runStateSpecific(worker, STATE_TERMINATE_WHEN_IDLE,Long.MAX_VALUE);
            }
            
            // If not service, no coordination between workers ends,
            // so never running with TERMINATE_ASAP state.
        }
        private static void innerRunWorker_service(MyMulticastWorker worker, int recentState) throws InterruptedException {
            // If state is TERMINATE_ASAP, we might have to process some sequences,
            // so we only return if state is TERMINATED.
            if (recentState == STATE_TERMINATED) {
                // Called again after termination, i.e. after termination
                // on exceptional completion (not supposed to be called
                // again after normal completion). Worker will now complete
                // normally, after which it should not be called anymore.
                return;
            }

            final MulticastRingBuffer ringBuffer = worker.ringBuffer;

            if (worker.stateToBeDoneWith == STATE_RUNNING) {
                if (recentState == STATE_RUNNING) {
                    boolean subscriberStop = runStateSpecific(worker, STATE_RUNNING,Long.MAX_VALUE);
                    if (subscriberStop) {
                        return;
                    }
                }

                worker.stateToBeDoneWith = STATE_TERMINATE_WHEN_IDLE;
                // Sequence setting must be done before counter increment.
                synchronized (ringBuffer.maxProcessedSequencesMutex) {
                    ringBuffer.maxProcessedSequence_RUNNING = Math.max(ringBuffer.maxProcessedSequence_RUNNING, worker.getMaxPassedSequenceLocal());
                }
                incrementAndNotifyAllInLockIfIs(ringBuffer.nbrOfWorkersDoneWith_RUNNING, ringBuffer.nbrOfWorkersAtStart);
            }

            if (worker.stateToBeDoneWith == STATE_TERMINATE_WHEN_IDLE) {
                boolean subscriberStop = false;
                boolean considerStateDone = true;
                if ((recentState = worker.state.get()) == STATE_TERMINATE_WHEN_IDLE) {
                    final long endSequence = getMaxSequenceToProcessAfterEventualOnBatchEnd(worker, ringBuffer.maxSequenceToProcess_TERMINATE_WHEN_IDLE);
                    subscriberStop = runStateSpecific(worker, STATE_TERMINATE_WHEN_IDLE,endSequence);
                    // If subscriber stop, considering done if reached end sequence.
                    considerStateDone = (!subscriberStop) || (worker.getMaxPassedSequenceLocal() == endSequence);
                }
                if (considerStateDone) {
                    worker.stateToBeDoneWith = STATE_TERMINATE_ASAP;
                    // Sequence setting must be done before counter increment.
                    synchronized (ringBuffer.maxProcessedSequencesMutex) {
                        ringBuffer.maxProcessedSequence_TERMINATE_WHEN_IDLE = Math.max(ringBuffer.maxProcessedSequence_TERMINATE_WHEN_IDLE, worker.getMaxPassedSequenceLocal());
                    }
                    incrementAndNotifyAllInLockIfIs(ringBuffer.nbrOfWorkersDoneWith_TERMINATE_WHEN_IDLE, ringBuffer.nbrOfWorkersAtStart);
                }
                if (subscriberStop) {
                    return;
                }
            }
            
            // If not service, no coordination between workers ends,
            // so never running with TERMINATE_ASAP state.
            if (worker.stateToBeDoneWith == STATE_TERMINATE_ASAP) {
                boolean subscriberStop = false;
                boolean considerStateDone = true;
                if ((recentState = worker.state.get()) == STATE_TERMINATE_ASAP) {
                    final long endSequence = getMaxSequenceToProcessAfterEventualOnBatchEnd(worker, ringBuffer.maxSequenceToProcess_TERMINATE_ASAP);
                    subscriberStop = runStateSpecific(worker, STATE_TERMINATE_ASAP,endSequence);
                    // If subscriber stop, considering done if reached end sequence.
                    considerStateDone = (!subscriberStop) || (worker.getMaxPassedSequenceLocal() == endSequence);
                }
                if (considerStateDone) {
                    worker.stateToBeDoneWith = Integer.MAX_VALUE;
                }
                if (subscriberStop) {
                    return;
                }
            }
        }
        /**
         * @return If this ring buffer is a service, the value of the specified
         *         end sequence once it is set (waiting uninterruptibly),
         *         Long.MAX_VALUE otherwise.
         */
        private static long getMaxSequenceToProcessAfterEventualOnBatchEnd(
                MyMulticastWorker worker,
                MyEndSequence endSequence) {
            final long result;
            if (worker.service) {
                if (!endSequence.isSet()) {
                    // Will likely wait.
                    worker.callOnBatchEndOrNotAndSetMPSOrNotAndSignalAllInLock(worker.getMaxPassedSequenceLocal());
                }
                result = endSequence.getBlockingUninterruptibly();
            } else {
                result = Long.MAX_VALUE;
            }
            return result;
        }
        /**
         * @param endSequence Max sequence to read (can return earlier in case of state change).
         * @return True if the subscriber asked to process no more event, false otherwise.
         */
        private static boolean runStateSpecific(
                final MyMulticastWorker worker,
                final int localState,
                final long endSequence) throws InterruptedException {
            final MulticastRingBuffer ringBuffer = worker.ringBuffer;
            
            final boolean service = worker.service;
            final boolean hasAheadWorkers = (worker.readWaitStopBC != null);

            final InterfaceCondilock writeWaitCondilock = ringBuffer.writeWaitCondilock;
            final InterfaceCondilock readWaitCondilock = ringBuffer.readWaitCondilock;
            final int indexMask = ringBuffer.indexMask;
            final InterfaceRingBufferSubscriber subscriber = worker.subscriber;
            final PostPaddedAtomicInteger state = worker.state;
            final MyWriteWaitStopBC writeWaitStopBC = worker.writeWaitStopBC;
            writeWaitStopBC.localState_WWSBC = localState;
            final MyReadWaitStopBC readWaitStopBC = worker.readWaitStopBC;
            if (readWaitStopBC != null) {
                readWaitStopBC.localState_RWSBC = writeWaitStopBC.localState_WWSBC;
            }
            long attemptSequence = worker.getMaxPassedSequenceLocal() + 1;
            
            /*
             * If service, reading up to the end sequence.
             */
            final boolean stopIfIdle = (!service) && (localState == STATE_TERMINATE_WHEN_IDLE);
            
            // Test here to avoid a test later.
            if (attemptSequence > endSequence) {
                return false;
            }
            MainLoop : while (true) {
                if(ASSERTIONS)assert(attemptSequence <= endSequence);
                // To set max passed sequence (volatile) only if actually changed.
                final long attemptSequenceBeforeTry = attemptSequence;
                try {
                    final long batchEndSequence;
                    if (hasAheadWorkers) {
                        /*
                         * Waiting for attempt sequence to be read by ahead workers.
                         */
                        if (!MyReadWaitStopBC.isBehindWorkersToCheck(readWaitStopBC, attemptSequence)) {
                            if (stopIfIdle && (!MyWriteWaitStopBC.isBehindPublishers(writeWaitStopBC, attemptSequence))) {
                                break;
                            }
                            worker.callOnBatchEndOrNotAndSetMPSOrNotAndSignalAllInLock(attemptSequence-1);
                            readWaitStopBC.sequence_RWSBC = attemptSequence;
                            readWaitCondilock.awaitUntilNanosTimeoutTimeWhileFalseInLock(readWaitStopBC,Long.MAX_VALUE);
                            if (readWaitStopBC.trueBecauseOfState_RWSBC) {
                                break;
                            }
                        }
                        batchEndSequence = Math.min(readWaitStopBC.lastMinWorkerMPS_SWC, endSequence);
                    } else {
                        if (!MyWriteWaitStopBC.isBehindPublishers(writeWaitStopBC, attemptSequence)) {
                            // No need to call onBatchEnd if needed before return,
                            // it's taken care of in callImpl(): calling it if needed
                            // ONLY before actual (or almost sure) waits, or in finally
                            // clause in callImpl().
                            if (stopIfIdle) {
                                break;
                            }
                            worker.callOnBatchEndOrNotAndSetMPSOrNotAndSignalAllInLock(attemptSequence-1);
                            writeWaitStopBC.sequence_WWSBC = attemptSequence;
                            writeWaitCondilock.awaitUntilNanosTimeoutTimeWhileFalseInLock(writeWaitStopBC,Long.MAX_VALUE);
                            if (writeWaitStopBC.trueBecauseOfState_WWSBC) {
                                break;
                            }
                        }
                        batchEndSequence = Math.min(writeWaitStopBC.localMPSC_WWSBC, endSequence);
                    }

                    /*
                     * reading a batch of sequences
                     */

                    do {
                        final int index = (int)attemptSequence & indexMask;

                        // attemptSequence incremented even if readEvent throws an exception
                        // (but onBatchEnd call not required in that case).
                        subscriber.readEvent(attemptSequence++, index);
                        worker.needToCallOnBatchEnd = true;

                        if (subscriber.processReadEvent()) {
                            return true;
                        }

                        if (CHECK_STATE_IN_BATCH && (state.get() != localState)) {
                            break MainLoop;
                        }
                    } while (attemptSequence <= batchEndSequence);

                    if ((!CHECK_STATE_IN_BATCH) && (state.get() != localState)) {
                        break MainLoop;
                    }

                    if (attemptSequence > endSequence) {
                        break;
                    }
                } finally {
                    if (attemptSequence != attemptSequenceBeforeTry) {
                        if(ASSERTIONS)assert(attemptSequence > attemptSequenceBeforeTry);
                        if (SET_VOLATILE_MPS_ONLY_BEFORE_WAITING) {
                            worker.setMaxPassedSequenceLocal(attemptSequence-1);
                        } else {
                            worker.setMaxPassedSequenceAndSignalAllInLock(attemptSequence-1);
                        }
                    }
                }
            }
            return false;
        }
        private void callOnBatchEndOrNotAndSetMPSOrNotAndSignalAllInLock(long maxPassedSequence) {
            try {
                this.callOnBatchEndIfNeeded();
            } finally {
                if (SET_VOLATILE_MPS_ONLY_BEFORE_WAITING) {
                    this.setMaxPassedSequenceAndSignalAllInLock(maxPassedSequence);
                }
            }
        }
        private void setMaxPassedSequenceAndSignalAllInLock(long maxPassedSequence) {
            this.setMaxPassedSequence(maxPassedSequence);
            this.signalAllInLockAfterMPSSetting();
        }
        private void signalAllInLockAfterMPSSetting() {
            // Signal for publishers waiting for a slot to write to,
            // and for subscribers waiting for an ahead worker to make progress.
            this.ringBuffer.readWaitCondilock.signalAllInLock();
        }
    }

    /*
     * publishers side
     */

    private static class MyTailWorkersSequencesRef extends NonNullOrAtomicReference<PostPaddedAtomicLong[]> {
        final PostPaddedAtomicLong sharedRecentMinWorkerMPS;
        public MyTailWorkersSequencesRef(MulticastRingBuffer ringBuffer) {
            super(ringBuffer.tailWorkersMPSRef);
            this.sharedRecentMinWorkerMPS = ringBuffer.sharedRecentMinWorkerMPS;
        }
        static final boolean isWrapPointBehindWorkers(MyTailWorkersSequencesRef ref, long wrapPoint) {
            final long recentMinWorkerMPS = ref.sharedRecentMinWorkerMPS.get();
            if (wrapPoint <= recentMinWorkerMPS) {
                return true;
            }
            final long newMinWorkerMPS = computeMinMaxPassedSequence(ref.get(), recentMinWorkerMPS);
            // Setting even if value didn't change, for lazySet doesn't cost much,
            // it can help if in the mean time another publisher did set it to a
            // lower value, it takes less code, and seems to only help.
            // Using CAS instead, to ensure that the value only increases,
            // and possibly with reading it just before to avoid a useless CAS,
            // seems to slow things down.
            ref.sharedRecentMinWorkerMPS.lazySet(newMinWorkerMPS);
            return (wrapPoint <= newMinWorkerMPS);
        }
    }

    private static class MyRBRoomWaitStopBC extends MyTailWorkersSequencesRef implements InterfaceBooleanCondition {
        long wrapPoint_LRC;
        public MyRBRoomWaitStopBC(MulticastRingBuffer ringBuffer) {
            super(ringBuffer);
        }
        final void setWrapPoint(long wrapPoint) {
            this.wrapPoint_LRC = wrapPoint;
        }
        final boolean isWrapPointBehindWorkers() {
            return isWrapPointBehindWorkers(this, this.wrapPoint_LRC);
        }
        boolean isTrueBecauseOfState() {
            return false;
        }
        @Override
        public boolean isTrue() {
            return isWrapPointBehindWorkers(this, this.wrapPoint_LRC);
        }
    }

    private static class MyRBSRoomWaitStopBC extends MyRBRoomWaitStopBC {
        final PostPaddedAtomicInteger ringBufferState;
        boolean trueBecauseOfState_RWSBC;
        public MyRBSRoomWaitStopBC(MulticastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.ringBufferState = ringBuffer.ringBufferState;
        }
        @Override
        public boolean isTrue() {
            return isTrue_static(this);
        }
        @Override
        public boolean isTrueBecauseOfState() {
            return this.trueBecauseOfState_RWSBC;
        }
        private static boolean isTrue_static(MyRBSRoomWaitStopBC bc) {
            bc.trueBecauseOfState_RWSBC = false;
            // Workers check first, for ring buffer state is usually not shutdown.
            return isWrapPointBehindWorkers(bc, bc.wrapPoint_LRC)
                    || (bc.trueBecauseOfState_RWSBC = isShutdown(bc.ringBufferState.get()));
        }
    }

    /*
     * 
     */

    private static class MyPublishPortLocals {
        final MyRBRoomWaitStopBC claimWaitStopBC;
        public MyPublishPortLocals(MulticastRingBuffer ringBuffer) {
            if (ringBuffer.service) {
                this.claimWaitStopBC = new MyRBSRoomWaitStopBC(ringBuffer);
            } else {
                this.claimWaitStopBC = new MyRBRoomWaitStopBC(ringBuffer);
            }
        }
    }

    private static abstract class MyAbstractPublishPort extends MyTailWorkersSequencesRef implements InterfaceRingBufferPublishPort {
        final MulticastRingBuffer ringBuffer;
        final boolean service;
        final PostPaddedAtomicInteger ringBufferState;
        final PostPaddedAtomicLong sharedMaxPubSeqContiguous;
        final InterfaceCondilock readWaitCondilock;
        final InterfaceCondilock writeWaitCondilock;
        final int indexMask;
        public MyAbstractPublishPort(MulticastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.ringBuffer = ringBuffer;
            this.service = ringBuffer.service;
            this.ringBufferState = ringBuffer.ringBufferState;
            this.sharedMaxPubSeqContiguous = ringBuffer.sharedMaxPubSeqContiguous;
            this.readWaitCondilock = ringBuffer.readWaitCondilock;
            this.writeWaitCondilock = ringBuffer.writeWaitCondilock;
            this.indexMask = ringBuffer.indexMask;
        }
        @Override
        public final int sequenceToIndex(long sequence) {
            return (int)sequence & this.indexMask;
        }
        @Override
        public final long tryClaimSequence() {
            return tryClaimSequence_static(this);
        }
        @Override
        public final long tryClaimSequence(long timeoutNS) throws InterruptedException {
            return tryClaimSequence_static(this, timeoutNS);
        }
        @Override
        public final long claimSequence() {
            return claimSequence_static(this);
        }
        @Override
        public final long claimSequences(IntWrapper nbrOfSequences) {
            return claimSequences_static(this, nbrOfSequences);
        }
        @Override
        public final void publish(long sequence) {
            this.publishInternal(sequence, 1);
        }
        @Override
        public final void publish(long minSequence, int nbrOfSequences) {
            this.publishInternal(minSequence, nbrOfSequences);
        }
        protected abstract MyPublishPortLocals getLocals();
        protected abstract long sequencer_get();
        protected abstract boolean sequencer_isCurrentAndCompareAndIncrement(long sequence);
        protected abstract long sequencer_getAndIncrement();
        protected abstract long sequencer_getAndAdd(int delta);
        protected abstract void publishInternal(long minSequence, int nbrOfSequences);
        /**
         * On shutdown, higher sequences might have ended up as pending pubs,
         * and will never be actually published since our sequences won't.
         * That's why we need to reject them.
         * @return True if acquired sequences, false if they are lost.
         */
        protected abstract boolean canPublishAcquiredSequencesOnShutDown(long minAcquiredSequence, int nbrOfAcquiredSequences);
        /**
         * @return True if there is room for the specified sequence
         *         (ring buffer being shut down or not),
         *         false if ring buffer has been detected to be
         *         shut down (whith room or not).
         */
        private static boolean waitForRoomUnlessShutdown(
                MyAbstractPublishPort port,
                long minAcquiredSequence,
                long maxAcquiredSequence) {
            final long wrapPoint = maxAcquiredSequence - (port.indexMask+1);
            if (isWrapPointBehindWorkers(port, wrapPoint)) {
                return true;
            }
            // Only retrieving locals if needed.
            final MyRBRoomWaitStopBC bc = port.getLocals().claimWaitStopBC;
            bc.setWrapPoint(wrapPoint);
            // While loop in case of messy timing.
            port.readWaitCondilock.awaitWhileFalseInLockUninterruptibly(bc);
            return !bc.isTrueBecauseOfState();
        }
        private static long tryClaimSequence_static(MyAbstractPublishPort port) {
            final int jump = port.indexMask+1;
            while (true) {
                final long acquirableSequence = port.sequencer_get();
                final long wrapPoint = acquirableSequence - jump;
                if (!isWrapPointBehindWorkers(port, wrapPoint)) {
                    return SEQUENCE_NO_ROOM;
                }
                if (port.sequencer_isCurrentAndCompareAndIncrement(acquirableSequence)) {
                    // Got it.
                    return acquirableSequence;
                }
                // Someone else got it before us: will try to get another sequence.
                if(ASSERTIONS)assert(!(port instanceof MyPublishPort_SinglePub));
            }
        }
        private static long tryClaimSequence_static(MyAbstractPublishPort port, long timeoutNS) throws InterruptedException {
            // Fast claim if there is room (not bothering with timing
            // nor locals).
            final long firstClaimedSequence = tryClaimSequence_static(port);
            if ((firstClaimedSequence >= 0) || (timeoutNS <= 0)) {
                return firstClaimedSequence;
            }

            final int bufferCapacity = (port.indexMask+1);
            final long endTimeoutTimeNS = NumbersUtils.plusBounded(port.readWaitCondilock.timeoutTimeNS(),timeoutNS);

            final MyPublishPortLocals locals = port.getLocals();
            final MyRBRoomWaitStopBC bc = locals.claimWaitStopBC;
            while (true) {
                final long acquirableSequence = port.sequencer_get();
                final long wrapPoint = acquirableSequence - bufferCapacity;
                bc.setWrapPoint(wrapPoint);
                boolean haveRoom = bc.isWrapPointBehindWorkers();
                if (!haveRoom) {
                    // No room (and possibly shut down, but we didn't check yet).
                    final boolean bcTrue = port.readWaitCondilock.awaitUntilNanosTimeoutTimeWhileFalseInLock(bc, endTimeoutTimeNS);
                    if (bcTrue) {
                        if (bc.isTrueBecauseOfState()) {
                            return SEQUENCE_SHUT_DOWN;
                        } else {
                            // Got room: will try to get sequence.
                        }
                    } else {
                        return SEQUENCE_NO_ROOM;
                    }
                }
                if (port.sequencer_isCurrentAndCompareAndIncrement(acquirableSequence)) {
                    // Got it.
                    return acquirableSequence;
                }
                // Someone else got it before us: will try to get another sequence.
                if(ASSERTIONS)assert(!(port instanceof MyPublishPort_SinglePub));
            }
        }
        private static long claimSequence_static(MyAbstractPublishPort port) {
            final long acquiredSequence = port.sequencer_getAndIncrement();
            if (waitForRoomUnlessShutdown(
                    port,
                    acquiredSequence,
                    acquiredSequence)) {
                // Got it.
                return acquiredSequence;
            } else {
                // Shut down (sequence lost).
                if(ASSERTIONS)assert(port.service);
                if (port.canPublishAcquiredSequencesOnShutDown(acquiredSequence,1)) {
                    return acquiredSequence;
                } else {
                    return SEQUENCE_SHUT_DOWN;
                }
            }
        }
        private static long claimSequences_static(MyAbstractPublishPort port, IntWrapper nbrOfSequences) {
            final int desiredNbrOfSequences = nbrOfSequences.value;
            if (desiredNbrOfSequences <= 0) {
                throw new IllegalArgumentException("number of sequences ["+desiredNbrOfSequences+"] must be > 0");
            }

            // Making sure we don't wrap.
            final int actualNbrOfSequences = Math.min(desiredNbrOfSequences,port.indexMask+1);
            final long minAcquiredSequence = port.sequencer_getAndAdd(actualNbrOfSequences);
            final long maxAcquiredSequence = minAcquiredSequence + (actualNbrOfSequences-1);

            if (waitForRoomUnlessShutdown(
                    port,
                    minAcquiredSequence,
                    maxAcquiredSequence)) {
                // Got sequences.
                nbrOfSequences.value = actualNbrOfSequences;
                return minAcquiredSequence;
            } else {
                // Shut down (sequences lost).
                if(ASSERTIONS)assert(port.service);
                if (port.canPublishAcquiredSequencesOnShutDown(minAcquiredSequence,actualNbrOfSequences)) {
                    nbrOfSequences.value = actualNbrOfSequences;
                    return minAcquiredSequence;
                } else {
                    nbrOfSequences.value = 0;
                    return SEQUENCE_SHUT_DOWN;
                }
            }
        }
        protected static final void onPublish(
                MyAbstractPublishPort port,
                long minPublished,
                long maxPublished) {
            // If non-service, since we don't have to take care of rejections
            // on shutdown, instead of testing if ring buffer state is not RUNNING,
            // we can just test if min published sequence is initial sequence,
            // which removes a volatile read.
            if (port.service ? (port.ringBufferState.get() != STATE_RUNNING) : (minPublished == INITIAL_SEQUENCE)) {
                onEarlyOrLatePublish(port, minPublished, maxPublished);
            }
            port.writeWaitCondilock.signalAllInLock();
        }
        private static void onEarlyOrLatePublish(
                MyAbstractPublishPort port,
                long minSequence,
                long maxSequence) {
            if (port.ringBufferState.get() == STATE_PENDING) {
                port.ringBuffer.tryStart();
            }
            if (port.service && isShutdown(port.ringBufferState.get())) {
                // Late publish.
                tryToRejectSequences(port, minSequence, maxSequence);
            }
        }
        private static void tryToRejectSequences(
                MyAbstractPublishPort port,
                long minSequence,
                long maxSequence) {
            if(ASSERTIONS)assert(port.service);
            if(ASSERTIONS)assert(isShutdown(port.ringBufferState.get()));

            final long minToReject = port.ringBuffer.minSequenceToReject.getBlockingUninterruptibly();
            if (port.ringBuffer.singlePublisher) {
                if (maxSequence >= minToReject) {
                    // No need to CAS, since rejection in shutdownNow is not done concurrently.
                    final long minRejectedSequence = Math.max(minSequence, minToReject);
                    port.ringBuffer.getRejectedEventHandlerImpl().onRejectedEvents(
                            port.ringBuffer,
                            minRejectedSequence,
                            maxSequence);
                }
            } else {
                SequenceRangeVector ranges = null;
                for (long seq=Math.max(minToReject,minSequence);seq<=maxSequence;seq++) {
                    int index = (int)seq & port.indexMask;
                    PostPaddedAtomicLong entry = port.ringBuffer.entries[index];
                    long sequence = entry.get();
                    if ((sequence == seq)
                            && entry.compareAndSet(sequence, SEQUENCE_NOT_SET)) {
                        if (ranges == null) {
                            ranges = new SequenceRangeVector();
                        }
                        ranges.add(seq, seq);
                    }
                }
                if (ranges != null) {
                    port.ringBuffer.getRejectedEventHandlerImpl().onRejectedEvents(
                            port.ringBuffer,
                            ranges.sortAndMergeAndToArray());
                }
            }
        }
    }

    private static class MyPublishPort_SinglePub extends MyAbstractPublishPort {
        final boolean writeLazySets;
        final MyPublishPortLocals locals;
        long nextSequence = INITIAL_SEQUENCE;
        public MyPublishPort_SinglePub(MulticastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.writeLazySets = ringBuffer.writeLazySets;
            this.locals = new MyPublishPortLocals(ringBuffer);
        }
        @Override
        protected final MyPublishPortLocals getLocals() {
            return this.locals;
        }
        @Override
        protected long sequencer_get() {
            return this.nextSequence;
        }
        @Override
        protected boolean sequencer_isCurrentAndCompareAndIncrement(long expected) {
            // Only used with current value as expected value.
            if(ASSERTIONS)assert(this.nextSequence == expected);
            this.nextSequence++;
            return true;
        }
        @Override
        protected final long sequencer_getAndIncrement() {
            return this.nextSequence++;
        }
        @Override
        protected final long sequencer_getAndAdd(int delta) {
            final long result = this.nextSequence;
            this.nextSequence += delta;
            return result;
        }
        @Override
        protected final void publishInternal(
                long minSequence,
                int nbrOfSequences) {
            if(ASSERTIONS)assert(minSequence > this.sharedMaxPubSeqContiguous.get());
            if(ASSERTIONS)assert(nbrOfSequences > 0);
            final long maxSequence = minSequence + nbrOfSequences - 1;
            if (this.writeLazySets) {
                this.sharedMaxPubSeqContiguous.lazySet(maxSequence);
            } else {
                this.sharedMaxPubSeqContiguous.set(maxSequence);
            }
            onPublish(this, minSequence, maxSequence);
        }
        @Override
        protected final boolean canPublishAcquiredSequencesOnShutDown(long minAcquiredSequence, int nbrOfAcquiredSequences) {
            return false;
        }
    }

    /*
     * 
     */

    private static abstract class MyAbstractPublishPort_MultiPub extends MyAbstractPublishPort {
        final boolean writeLazySets;
        final PostPaddedAtomicLong pubSeqM;
        final PostPaddedAtomicLong[] entries;
        final int indexMask;
        public MyAbstractPublishPort_MultiPub(MulticastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.writeLazySets = ringBuffer.writeLazySets;
            this.pubSeqM = ringBuffer.pubSeqM;
            this.entries = ringBuffer.entries;
            this.indexMask = ringBuffer.indexMask;
        }
        @Override
        protected long sequencer_get() {
            return this.pubSeqM.get();
        }
        @Override
        protected boolean sequencer_isCurrentAndCompareAndIncrement(long expected) {
            // Checking expected before CAS, for CAS is a heavy operation.
            return (this.pubSeqM.get() == expected)
                    && this.pubSeqM.compareAndSet(expected, expected+1);
        }
        @Override
        protected final long sequencer_getAndIncrement() {
            return this.pubSeqM.getAndIncrement();
        }
        @Override
        protected final long sequencer_getAndAdd(int delta) {
            return this.pubSeqM.getAndAdd(delta);
        }
        @Override
        protected final void publishInternal(
                long minSequence,
                int nbrOfSequences) {
            if(ASSERTIONS)assert(nbrOfSequences > 0);
            
            final long maxSequence = minSequence + nbrOfSequences - 1;
            for (long seq=minSequence;seq<maxSequence;seq++) {
                final int index = (int)seq & this.indexMask;
                PostPaddedAtomicLong entry = entries[index];
                // If write lazy set is false, we can still use lazy set
                // for sequences prior to last one: If last one has not
                // been done yet (otherwise they all become visible),
                // shut down thread might only reject some of them,
                // but then remaining sequences will be rejected
                // by the publisher.
                entry.lazySet(seq);
            }
            
            final int index = (int)maxSequence & this.indexMask;
            PostPaddedAtomicLong entry = entries[index];
            if (this.writeLazySets) {
                entry.lazySet(maxSequence);
            } else {
                entry.set(maxSequence);
            }
            
            onPublish(this, minSequence, maxSequence);
        }
        @Override
        protected final boolean canPublishAcquiredSequencesOnShutDown(long minAcquiredSequence, int nbrOfAcquiredSequences) {
            final long minToReject = this.ringBuffer.minSequenceToReject.getBlockingUninterruptibly();
            final long maxAcquiredSequence = minAcquiredSequence + nbrOfAcquiredSequences - 1;
            if(ASSERTIONS)assert((minToReject <= minAcquiredSequence) || (minToReject > maxAcquiredSequence));
            // If min to reject is past our range of sequences,
            // that means a higher sequence has been published,
            // which implies that there is room for publishing
            // our sequences.
            return (minToReject > minAcquiredSequence);
        }
    }

    private static class MyPublishPort_MultiPub_Main extends MyAbstractPublishPort_MultiPub {
        // Only one main publish port: no need ThreadLocal as main class member.
        final ThreadLocal<MyPublishPortLocals> threadLocalLocals;
        public MyPublishPort_MultiPub_Main(final MulticastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.threadLocalLocals = new ThreadLocal<MyPublishPortLocals>() {
                @Override
                public MyPublishPortLocals initialValue() {
                    return new MyPublishPortLocals(ringBuffer);
                }
            };
        }
        @Override
        protected MyPublishPortLocals getLocals() {
            return this.threadLocalLocals.get();
        }
    }

    private static class MyPublishPort_MultiPub_Local extends MyAbstractPublishPort_MultiPub {
        final MyPublishPortLocals locals;
        public MyPublishPort_MultiPub_Local(final MulticastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.locals = new MyPublishPortLocals(ringBuffer);
        }
        @Override
        protected MyPublishPortLocals getLocals() {
            return this.locals;
        }
    }

    /*
     * Sequences for workers coordination or rejections.
     */
    
    private static class MyEndSequence {
        final boolean canDecrease;
        final MonitorCondilock condilock = new MonitorCondilock(this);
        final InterfaceBooleanCondition setBC;
        volatile long value = SEQUENCE_NOT_SET;
        public MyEndSequence(boolean canDecrease) {
            this.canDecrease = canDecrease;
            this.setBC = new InterfaceBooleanCondition() {
                @Override
                public boolean isTrue() {
                    return value >= (INITIAL_SEQUENCE-1);
                }
            };
        }
        /**
         * @return True if value is set, in which case a subsequent
         *         call to getBlockingUninterruptibly() won't block.
         */
        public boolean isSet() {
            return this.setBC.isTrue();
        }
        /**
         * The whole method is synchronized, in case of concurrent
         * calls for decrementing sequence.
         */
        public synchronized void setAndSignalAllInLock(long value) {
            if (value < (INITIAL_SEQUENCE-1)) {
                throw new IllegalArgumentException();
            }
            final long previousValue = this.value;
            if (this.canDecrease) {
                if (previousValue >= (INITIAL_SEQUENCE-1)) {
                    if (value > previousValue) {
                        throw new IllegalArgumentException(value+" > "+previousValue);
                    }
                }
            } else {
                if (this.isSet()) {
                    throw new IllegalStateException("already set");
                }
            }
            this.value = value;
            this.condilock.signalAll();
        }
        /**
         * @return Value, blocking while it has not been set
         *         (to another value than Long.MIN_VALUE).
         */
        public long getBlockingUninterruptibly() {
            this.condilock.awaitWhileFalseInLockUninterruptibly(this.setBC);
            return this.value;
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    /**
     * For optimized iteration on workers max passed sequences.
     */
    private final PostPaddedAtomicReference<PostPaddedAtomicLong[]> tailWorkersMPSRef = new PostPaddedAtomicReference<PostPaddedAtomicLong[]>();

    private final PostPaddedAtomicLong pubSeqM;

    /**
     * If single publisher, updated by publisher,
     * else updated by workers.
     */
    private final PostPaddedAtomicLong sharedMaxPubSeqContiguous = new PostPaddedAtomicLong(INITIAL_SEQUENCE-1);

    /**
     * Used for publishers to make sure not to publish on a slot a subscriber might still have to read.
     * Using it also for single-publisher case, even though volatile is overkill,
     * else there is more code and if's or overriden methods, and that slows down
     * most of the cases.
     */
    private final PostPaddedAtomicLong sharedRecentMinWorkerMPS = new PostPaddedAtomicLong(INITIAL_SEQUENCE-1);

    /*
     * 
     */
    
    private final AtomicInteger nbrOfWorkersDoneWith_RUNNING;
    private final AtomicInteger nbrOfWorkersDoneWith_TERMINATE_WHEN_IDLE;

    private final Object maxProcessedSequencesMutex = new Object();
    private long maxProcessedSequence_RUNNING = Long.MIN_VALUE;
    private long maxProcessedSequence_TERMINATE_WHEN_IDLE = Long.MIN_VALUE;
    
    private final MyEndSequence maxSequenceToProcess_TERMINATE_WHEN_IDLE;
    private final MyEndSequence maxSequenceToProcess_TERMINATE_ASAP;
    private final MyEndSequence minSequenceToReject;

    /*
     * 
     */

    private final MyAbstractPublishPort mainPublishPort;

    /**
     * To optimize the case of batch publishing, we could avoid publishing each sequence,
     * and instead publishing the first and indicating the number of consecutive sequences
     * in a non-volatile field (written before volatile min sequence, and read after),
     * but that complicates rejections on shut down (due to publication of one sequence
     * that can erase publication info of many sequences), and might also reduce performances
     * of the common non-batch case. For this reason, each sequence is published individually.
     */
    private final PostPaddedAtomicLong[] entries;
    
    /**
     * Guarded by main lock.
     * Nullified on start, for GC.
     */
    private HashSet<MyMulticastWorker> tailWorkersSet = new HashSet<MyMulticastWorker>();

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @param bufferCapacity Buffer capacity. Must be a power of two.
     * @param singlePublisher True if single publisher, false otherwise.
     * @param singleSubscriber True if single subscriber, false otherwise.
     * @param readLazySets If true, lazySet is used to indicate that a sequence has been read,
     *        which implies that readWaitCondilock  must handle possible delays between the
     *        lazySet and its visibility of the new volatile value, which typically implies
     *        waking-up from wait from time to time to re-check the value.
     * @param writeLazySets Same as for readLazySets, but when indicating that a sequence
     *        has been written, and must be handled by writeWaitCondilock.
     * @param readWaitCondilock Condilock to wait for a sequence to be read.
     *        Mostly used by publishers.
     * @param writeWaitCondilock Condilock to wait for a sequence to be written.
     *        Mostly used by subscribers.
     */
    public MulticastRingBuffer(
            int bufferCapacity,
            boolean singlePublisher,
            boolean singleSubscriber,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock) {
        this(
                false, // service
                //
                bufferCapacity,
                singlePublisher,
                singleSubscriber,
                readLazySets,
                writeLazySets,
                readWaitCondilock,
                writeWaitCondilock,
                null,
                null);
    }

    @Override
    public long tryClaimSequence() {
        return MyAbstractPublishPort.tryClaimSequence_static(this.mainPublishPort);
    }

    @Override
    public long tryClaimSequence(long timeoutNS) throws InterruptedException {
        return MyAbstractPublishPort.tryClaimSequence_static(this.mainPublishPort, timeoutNS);
    }

    @Override
    public long claimSequence() {
        return MyAbstractPublishPort.claimSequence_static(this.mainPublishPort);
    }

    @Override
    public long claimSequences(IntWrapper nbrOfSequences) {
        return MyAbstractPublishPort.claimSequences_static(this.mainPublishPort, nbrOfSequences);
    }

    @Override
    public void publish(long sequence) {
        this.mainPublishPort.publishInternal(sequence, 1);
    }

    @Override
    public void publish(
            long minSequence,
            int nbrOfSequences) {
        this.mainPublishPort.publishInternal(minSequence, nbrOfSequences);
    }

    @Override
    public InterfaceRingBufferPublishPort newLocalPublishPort() {
        if (this.singlePublisher) {
            throw new UnsupportedOperationException();
        } else {
            return new MyPublishPort_MultiPub_Local(this);
        }
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------

    protected MulticastRingBuffer(
            boolean service,
            //
            int bufferCapacity,
            boolean singlePublisher,
            boolean singleSubscriber,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock,
            final Executor executor,
            final InterfaceRingBufferRejectedEventHandler rejectedEventHandler) {
        super(
                true, // multicast
                service,
                //
                bufferCapacity,
                singlePublisher,
                singleSubscriber,
                readLazySets,
                writeLazySets,
                readWaitCondilock,
                writeWaitCondilock,
                executor,
                rejectedEventHandler);

        if (this.service) {
            this.maxSequenceToProcess_TERMINATE_WHEN_IDLE = new MyEndSequence(true);
            this.maxSequenceToProcess_TERMINATE_ASAP = new MyEndSequence(false);
            this.minSequenceToReject = new MyEndSequence(false);
        } else {
            this.maxSequenceToProcess_TERMINATE_WHEN_IDLE = null;
            this.maxSequenceToProcess_TERMINATE_ASAP = null;
            this.minSequenceToReject = null;
        }

        if (this.service) {
            this.nbrOfWorkersDoneWith_RUNNING = new AtomicInteger();
            this.nbrOfWorkersDoneWith_TERMINATE_WHEN_IDLE = new AtomicInteger();
        } else {
            this.nbrOfWorkersDoneWith_RUNNING = null;
            this.nbrOfWorkersDoneWith_TERMINATE_WHEN_IDLE = null;
        }
        
        /*
         * Taking care to create publishers last,
         * for them might use other fields in their
         * construction.
         */

        if (singlePublisher) {
            this.entries = null;
            this.pubSeqM = null;
            this.mainPublishPort = new MyPublishPort_SinglePub(this);
        } else {
            this.entries = new PostPaddedAtomicLong[bufferCapacity];
            for (int i=0;i<this.entries.length;i++) {
                this.entries[i] = new PostPaddedAtomicLong(SEQUENCE_NOT_SET);
            }
            this.pubSeqM = new PostPaddedAtomicLong(INITIAL_SEQUENCE);
            this.mainPublishPort = new MyPublishPort_MultiPub_Main(this);
        }
    }

    @Override
    protected void signalSubscribers() {
        this.readWaitCondilock.signalAllInLock();
        this.writeWaitCondilock.signalAllInLock();
    }

    /*
     * Implementations of service-specific methods.
     */

    protected void shutdownImpl() {
        // Main lock ensures consistency between permission checks,
        // and actual shutdown (in particular: involved worker threads).
        final boolean stateChanged;
        boolean neverRunning = false;
        this.mainLock.lock();
        try {
            checkShutdownAccessIfNeeded(false);
            // Using CASes, which would update state properly (order matters) even if used concurrently.
            stateChanged = this.ringBufferState.compareAndSet(STATE_RUNNING, STATE_TERMINATE_WHEN_IDLE)
                    || (neverRunning = this.ringBufferState.compareAndSet(STATE_PENDING, STATE_TERMINATE_WHEN_IDLE));
            if (stateChanged) {
                // Signaling everyone, doesn't hurt.
                this.signalAllInLockWriteRead();
            }
        } finally {
            this.mainLock.unlock();
        }

        if (stateChanged) {
            if (neverRunning) {
                // Need to set it, for publishers might use it.
                this.minSequenceToReject.setAndSignalAllInLock(INITIAL_SEQUENCE);
            } else {
                waitForCounterAndMutexToBeUninterruptibly(nbrOfWorkersDoneWith_RUNNING, nbrOfWorkersAtStart);

                // We want to process sequences up to max currently published.
                final long maxToProcess = this.computeMaxPubSeqNonContiguous();
                
                this.maxSequenceToProcess_TERMINATE_WHEN_IDLE.setAndSignalAllInLock(maxToProcess);
                this.minSequenceToReject.setAndSignalAllInLock(maxToProcess+1);
            }
            terminateIfNeeded(neverRunning);
        }
    }

    /**
     * @param interruptIfPossible If true, attempts to interrupt running workers.
     */
    protected long[] shutdownNowImpl(boolean interruptIfPossible) {
        // Main lock ensures:
        // - consistency between permission checks, and actual shutdown
        //   (in particular: involved worker threads),
        // - ring buffer state not to be changed by another treatment,
        // - consistency between ring buffer state and end sequences values.
        final boolean stateChanged;
        final boolean wasShutdown;
        boolean neverRunning = false;
        mainLock.lock();
        try {
            // Always possible.
            final boolean interruption = interruptIfPossible;
            checkShutdownAccessIfNeeded(interruption);
            // Using CASes, which would update state properly (order matters) even if used concurrently.
            stateChanged =
                    (wasShutdown = this.ringBufferState.compareAndSet(STATE_TERMINATE_WHEN_IDLE, STATE_TERMINATE_ASAP))
                    || this.ringBufferState.compareAndSet(STATE_RUNNING, STATE_TERMINATE_ASAP)
                    || (neverRunning = this.ringBufferState.compareAndSet(STATE_PENDING, STATE_TERMINATE_ASAP));
            if (stateChanged) {
                if (!neverRunning) {
                    if (interruptIfPossible) {
                        for (MyAbstractWorker runnable : this.workers) {
                            runnable.interruptRunningThreadIfAny();
                        }
                    }
                }
                // Signaling everyone, doesn't hurt.
                this.signalAllInLockWriteRead();
            }
        } finally {
            mainLock.unlock();
        }

        final long[] sequencesRangesArray;
        if (stateChanged) {
            if (neverRunning) {
                // State check for rejection on publish is done after eventual state set to RUNNING,
                // so if we never went running, any event will necessarily have been rejected on publish.
                sequencesRangesArray = null;
                if (wasShutdown) {
                    // Nothing to do.
                } else {
                    // Need to set it, for publishers might use it.
                    this.minSequenceToReject.setAndSignalAllInLock(INITIAL_SEQUENCE);
                }
            } else {
                waitForCounterAndMutexToBeUninterruptibly(nbrOfWorkersDoneWith_TERMINATE_WHEN_IDLE, nbrOfWorkersAtStart);

                final long maxToProcess_T_A;
                synchronized (maxProcessedSequencesMutex) {
                    maxToProcess_T_A = this.maxProcessedSequence_TERMINATE_WHEN_IDLE;
                }
                this.maxSequenceToProcess_TERMINATE_ASAP.setAndSignalAllInLock(maxToProcess_T_A);

                if (wasShutdown) {
                    final long maxToProcess_T_W_I = this.maxSequenceToProcess_TERMINATE_WHEN_IDLE.getBlockingUninterruptibly();
                    if (maxToProcess_T_A < maxToProcess_T_W_I) {
                        // Aborting sequences that no worker in TERMINATE_WHEN_IDLE state
                        // did manage to process yet.
                        sequencesRangesArray = new long[] {
                                maxToProcess_T_A+1,
                                maxToProcess_T_W_I
                        };
                    } else {
                        if(ASSERTIONS)assert(maxToProcess_T_A == maxToProcess_T_W_I);
                        sequencesRangesArray = null;
                    }
                } else {
                    // Only created if necessary.
                    SequenceRangeVector sequencesRangesVector = null;
                    
                    final long minToReject;
                    if (this.singlePublisher) {
                        final long maxPubSeqCont = this.sharedMaxPubSeqContiguous.get();
                        if(ASSERTIONS)assert(maxPubSeqCont >= maxToProcess_T_A);
                        if (maxPubSeqCont > maxToProcess_T_A) {
                            if (sequencesRangesVector == null) {
                                sequencesRangesVector = new SequenceRangeVector();
                            }
                            sequencesRangesVector.add(maxToProcess_T_A+1,maxPubSeqCont);
                        }
                        minToReject = maxPubSeqCont+1;
                    } else {
                        minToReject = maxToProcess_T_A+1;
                    }

                    // Set after single-pub rejections to make sure the publisher
                    // doesn't reject concurrently (no CAS, since we can avoid them).
                    this.minSequenceToReject.setAndSignalAllInLock(minToReject);

                    if (!this.singlePublisher) {
                        /*
                         * Rejecting concurrently with publishers.
                         * Could avoid concurrency, by setting minSequenceToReject
                         * after these rejections, but publishers would have to check
                         * whether or not we already rejected their sequences, so for
                         * simplicity we go full-CAS.
                         */

                        for (PostPaddedAtomicLong entry : this.entries) {
                            long sequence = entry.get();
                            if ((sequence > maxToProcess_T_A)
                                    && entry.compareAndSet(sequence, SEQUENCE_NOT_SET)) {
                                if (sequencesRangesVector == null) {
                                    sequencesRangesVector = new SequenceRangeVector();
                                }
                                sequencesRangesVector.add(sequence,sequence);
                            }
                        }
                    }
                    
                    if (sequencesRangesVector != null) {
                        sequencesRangesArray = sequencesRangesVector.sortAndMergeAndToArray();
                    } else {
                        sequencesRangesArray = null;
                    }
                }
            }

            terminateIfNeeded(neverRunning);
        } else {
            sequencesRangesArray = null;
        }
        return sequencesRangesArray;
    }

    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    @Override
    void beforeRunning() {
        super.beforeRunning();
        // For GC.
        this.tailWorkersSet = null;
    }

    @Override
    MyAbstractWorker newWorkerRaw(
            InterfaceRingBufferSubscriber subscriber,
            InterfaceRingBufferWorker... aheadWorkers) {
        
        MyMulticastWorker worker;
        if (this.service) {
            worker = new MyMulticastWorker(
                    this,
                    this.ringBufferState,
                    subscriber,
                    aheadWorkers);
        } else {
            worker = new MyMulticastWorker(
                    this,
                    new PostPaddedAtomicInteger(STATE_RUNNING),
                    subscriber,
                    aheadWorkers);
        }

        for (InterfaceRingBufferWorker aw : aheadWorkers) {
            this.tailWorkersSet.remove(aw);
        }
        this.tailWorkersSet.add(worker);
        
        return worker;
    }

    /*
     * for debug
     */

    String toStringState() {
        final StringBuilder sb = new StringBuilder();
        long sequenceM2 = Long.MIN_VALUE;
        long sequenceM1 = Long.MIN_VALUE;
        boolean didLogM2 = false;
        boolean didLogM1 = false;
        if (entries != null) {
            for (int i=0;i<entries.length;i++) {
                PostPaddedAtomicLong e = entries[i];

                long sequence = e.get();
                boolean didLogCurrent = false;

                if (sequence != sequenceM1+1) {
                    // First entry, or status change, or sequence discontinuity.
                    if ((!didLogM1) && (sequenceM1 >= INITIAL_SEQUENCE)) {
                        if ((!didLogM2) && (sequenceM2 >= INITIAL_SEQUENCE)) {
                            sb.append("(...)");
                            sb.append(LangUtils.LINE_SEPARATOR);
                        }
                        sb.append("pubs[");
                        sb.append(i-1);
                        sb.append("] = ");
                        sb.append(sequenceM1);
                        sb.append(LangUtils.LINE_SEPARATOR);
                    }
                    sb.append("pubs[");
                    sb.append(i);
                    sb.append("] = ");
                    sb.append(sequence);
                    sb.append(LangUtils.LINE_SEPARATOR);
                    didLogCurrent = true;
                }
                sequenceM2 = sequenceM1;
                sequenceM1 = sequence;
                didLogM2 = didLogM1;
                didLogM1 = didLogCurrent;
            }
        }
        if ((!didLogM1) && (sequenceM1 >= INITIAL_SEQUENCE)) {
            if ((!didLogM2) && (sequenceM2 >= INITIAL_SEQUENCE)) {
                sb.append("(...)");
                sb.append(LangUtils.LINE_SEPARATOR);
            }
            sb.append("pubs[");
            sb.append(entries.length-1);
            sb.append("] = ");
            sb.append(sequenceM1);
            sb.append(LangUtils.LINE_SEPARATOR);
        }
        sb.append("sharedMaxPubSeqContiguous = ");
        sb.append(sharedMaxPubSeqContiguous.get());
        sb.append(LangUtils.LINE_SEPARATOR);
        return sb.toString();
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * Changes ring buffer state from pending to running if needed.
     * After call to this method, ring buffer state is at least RUNNING.
     */
    private void tryStart() {
        this.mainLock.lock();
        try {
            if (this.workers.size() == 0) {
                throw new IllegalStateException("starting before worker(s) creation");
            }
            if (this.ringBufferState.get() == STATE_PENDING) {
                PostPaddedAtomicLong[] tailWorkersMPSArray = computeTailWorkersMPS_mainLockLocked();
                tailWorkersMPSRef.set(tailWorkersMPSArray);
                if (this.service) {
                    setNbrOfStartedWorkersAndExecuteWorkers_mainLockLocked();
                }
                beforeRunning();
                // Set (almost) last.
                this.ringBufferState.set(STATE_RUNNING);
                // Need to signal read wait condilock, in case some publishers
                // started to wait for a slot that was already available,
                // which they could not see due to workers array being null.
                this.readWaitCondilock.signalAllInLock();
            }
        } finally {
            this.mainLock.unlock();
        }
    }

    /*
     * 
     */
    
    /**
     * Only for multi-publisher case.
     * @param localMPSC This value must be <= actual max contiguous published sequence
     *        (else might cause non-yet-published sequence to be considered published).
     */
    private long updateAndGetMaxPubSeqContiguous(final long localMPSC) {
        if(ASSERTIONS)assert(!singlePublisher);
        
        long recentMPSC;
        long newLocalMPSC;
        if (MPSC_MONOTONIC_WITH_CAS) {
            // Taking our local as recent, since
            // we just updated it in a previous test.
            recentMPSC = localMPSC;
            // Starting from our previous local.
            newLocalMPSC = localMPSC;
        } else {
            recentMPSC = this.sharedMaxPubSeqContiguous.get();
            // Need to take max, for volatile might go backward.
            newLocalMPSC = Math.max(localMPSC, recentMPSC);
        }
        
        long tmpSequence = newLocalMPSC+1;
        while (true) {
            final int index = ((int)tmpSequence & this.indexMask);
            final PostPaddedAtomicLong entry = this.entries[index];
            final long sequence = entry.get();
            if (sequence < tmpSequence) {
                // Not published (negative value), or published at a previous round.
                if (tmpSequence != newLocalMPSC+1) {
                    newLocalMPSC = tmpSequence-1;
                    if (MPSC_MONOTONIC_WITH_CAS) {
                        if (MPSC_CAS_ONLY_IF_GOOD_CHANCE) {
                            final long moreRecentMPSC = this.sharedMaxPubSeqContiguous.get();
                            if (moreRecentMPSC != recentMPSC) {
                                // Already changed by someone else: taking its value.
                                return moreRecentMPSC;
                            }
                        }
                        if (this.sharedMaxPubSeqContiguous.compareAndSet(recentMPSC,newLocalMPSC)) {
                            // Did advance it.
                            return newLocalMPSC;
                        } else {
                            // Returning a fresh value.
                            // We don't return newLocalMPSC, for it might be > to the
                            // value set by the successful CAS.
                            return this.sharedMaxPubSeqContiguous.get();
                        }
                    } else {
                        // Can make it go backward.
                        this.sharedMaxPubSeqContiguous.lazySet(newLocalMPSC);
                        return newLocalMPSC;
                    }
                } else {
                    return newLocalMPSC;
                }
            }
            // Can't be ahead.
            if(ASSERTIONS)assert(sequence == tmpSequence);
            ++tmpSequence;
        }
    }
    
    /**
     * @return Max published sequence (possibly past non-yet-published sequences),
     *         or INITIAL_SEQUENCE-1 if none has been published yet.
     */
    private long computeMaxPubSeqNonContiguous() {
        if (this.singlePublisher) {
            return this.sharedMaxPubSeqContiguous.get();
        } else {
            return this.computeMaxPubSeqNonContiguous_multiPublisher();
        }
    }

    private long computeMaxPubSeqNonContiguous_multiPublisher() {
        long maxPub = INITIAL_SEQUENCE-1;
        // Could be more subtle, but it's not worth it.
        for (PostPaddedAtomicLong entry : this.entries) {
            long sequence = entry.get();
            if (sequence > maxPub) {
                maxPub = sequence;
            }
        }
        return maxPub;
    }

    /*
     * 
     */

    /**
     * When workers MPS counters array is retrieved in claim methods, for room check,
     * it might still be null, but that means ring buffer state has not been set
     * to RUNNING yet, so that no event has been processed, and that only slots
     * from first buffer round are available, which is properly indicated by
     * this method when the specified array is null.
     * 
     * @param recentMinWorkerMPS When encountering a counter with a sequence equal
     *        to this value, returns it right away (no need to look further).
     * @return Minimum max passed sequence for the specified counters,
     *         or Long.MAX_VALUE if counters array is empty,
     *         or INITIAL_SEQUENCE-1 if counters array is null.
     */
    private static long computeMinMaxPassedSequence(
            final PostPaddedAtomicLong[] workersMPSCounters,
            long recentMinWorkerMPS) {
        if (workersMPSCounters == null) {
            return INITIAL_SEQUENCE-1;
        } else {
            return computeMinMaxPassedSequenceNotNull(
                    workersMPSCounters,
                    recentMinWorkerMPS);
        }
    }

    /**
     * @param workersMPSCounters Must not be null.
     * @param recentMinWorkerMPS A recent value returned by this method, INITIAL_SEQUENCE-1 for first call.
     * @return Minimum max passed sequence for the specified counters,
     *         or Long.MAX_VALUE if counters array is empty.
     */
    private static long computeMinMaxPassedSequenceNotNull(
            final PostPaddedAtomicLong[] workersMPSCounters,
            long recentMinWorkerMPS) {
        long minWorkerMPS = Long.MAX_VALUE;
        for (PostPaddedAtomicLong workerMPSCounter : workersMPSCounters) {
            final long workerMPS = workerMPSCounter.get();
            if (workerMPS < minWorkerMPS) {
                minWorkerMPS = workerMPS;
                // Considering no worker goes backward.
                // This is ensured by setStartSequence(...) method.
                if (workerMPS == recentMinWorkerMPS) {
                    // This worker is still at a computed min worker MPS,
                    // so we can't find another worker with a lower MPS.
                    break;
                }
            }
        }
        return minWorkerMPS;
    }

    /*
     * 
     */

    private static void incrementAndNotifyAllInLockIfIs(final AtomicInteger counterAndMutex, int value) {
        if (counterAndMutex.incrementAndGet() == value) {
            synchronized (counterAndMutex) {
                counterAndMutex.notifyAll();
            }
        }
    }

    private static void waitForCounterAndMutexToBeUninterruptibly(
            final AtomicInteger counterAndMutex,
            int value) {
        if (counterAndMutex.get() != value) {
            boolean interrupted = Thread.interrupted();
            synchronized (counterAndMutex) {
                while (counterAndMutex.get() != value) {
                    try {
                        counterAndMutex.wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                // Restoring interruption status.
                Thread.currentThread().interrupt();
            }
        }
    }

    /*
     * 
     */
    
    /**
     * @return Array of workers that process events last.
     */
    private PostPaddedAtomicLong[] computeTailWorkersMPS_mainLockLocked() {
        MyMulticastWorker[] tailWorkersArray = tailWorkersSet.toArray(new MyMulticastWorker[tailWorkersSet.size()]);
        Arrays.sort(tailWorkersArray);
        PostPaddedAtomicLong[] tailWorkersMPSArray = new PostPaddedAtomicLong[tailWorkersArray.length];
        for (int i=0;i<tailWorkersArray.length;i++) {
            tailWorkersMPSArray[i] = tailWorkersArray[i].getMPSCounter();
        }
        return tailWorkersMPSArray;
    }
}
