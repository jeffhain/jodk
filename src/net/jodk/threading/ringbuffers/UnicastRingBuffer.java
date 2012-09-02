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

import java.util.concurrent.Executor;

import net.jodk.lang.IntWrapper;
import net.jodk.lang.InterfaceBooleanCondition;
import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;
import net.jodk.threading.LongCounter;
import net.jodk.threading.PostPaddedAtomicInteger;
import net.jodk.threading.PostPaddedAtomicLong;
import net.jodk.threading.LongCounter.LocalData;
import net.jodk.threading.locks.InterfaceCondilock;

/**
 * Unicast ring buffer, which allows for multiple configurations:
 * - single or multi publisher,
 * - single or multi subscriber,
 * - monotonic or non-monotonic (which might scale better) sequence claim.
 * 
 * Non-monotonic sequence claim is especially beneficial for tryClaimXXX
 * methods, since for these methods there are some operations done between
 * AtomicLong.get() and AtomicLong.compareAndSet(...), which makes retries
 * very likely in case of single (monotonic) atomic counter under heavy load.
 * 
 * See AbstractRingBuffer Javadoc for more info.
 */
public class UnicastRingBuffer extends AbstractRingBuffer {

    /*
     * Workers scan the ring buffer monotonically, unless in case
     * of logarithmic search of sequences front, but then this is
     * equivalent to monotonic scan as it only allows to catch-up
     * over parts of the ring buffer already monotonically scanned
     * by other workers.
     */
    
    /*
     * Entry (sequence,status) transitions, n being ring buffer capacity:
     * 
     * - event writing:
     *   - transition: (s,WRITABLE) ---(lazy if writeLazySet)---> (s,READABLE)
     *   - by: publishers
     *   - precondition: having acquired corresponding sequence from sequencer
     *   - signaled: writeWaitCondilock
     *   
     * - jump-up (skipping write/read for a sequence) (only in case of non-monotonic sequences claim):
     *   - transition: (s,WRITABLE) ---(lazy if writeLazySet and readLazySet)---> (s+n,WRITABLE)
     *   - by: publishers claiming a sequence batch,
     *         or subscribers encountering a writable entry from previous round
     *            while there are events to read past it.
     *   - precondition: having acquired corresponding sequence from sequencer
     *   - signaled: writeWaitCondilock and readWaitCondilock
     *   
     * - event reading begin (only in case of multi-subscriber):
     *   - transition: (s,READABLE) ---(CAS)---> (s,BEING_READ)
     *   - by: subscribers
     *   - precondition: none
     *   - signaled: none
     *   
     * - event reading end for multi-subscriber case:
     *   - transition: (s,BEING_READ) ---(lazy if readLazySet)---> (s+n,WRITABLE)
     *   - by: subscribers
     *   - precondition: being the subscriber who CASed the value
     *   - signaled: readWaitCondilock
     *   
     * - event reading end for single-subscriber case:
     *   - transition: (s,READABLE) ---(lazy if readLazySet)---> (s+n,WRITABLE)
     *   - by: subscribers
     *   - precondition: being the subscriber
     *   - signaled: readWaitCondilock
     * 
     * - writable event rejection:
     *   - transition: (s,WRITABLE) ---(lazy if writeLazySet)---> (s,REJECTED)
     *   - by: publisher
     *   - precondition: having acquired corresponding sequence from sequencer
     *   - signaled: writeWaitCondilock
     * 
     * - readable event rejection:
     *   - transition: (s,READABLE) ---(CAS)---> (s,REJECTED)
     *   - by: publishers or shut down thread
     *   - precondition: none
     *   - signaled: readWaitCondilock
     *   
     * Threads might be waiting for an entry, which sequence has been
     * acquired from sequencer by another thread, to be taken care of,
     * but not knowing whether it's going to be written or jumped-up:
     * in that case, the thread waits on write wait condilock, which will
     * be signaled in both cases.
     */

    /*
     * Condilocks usage:
     * - readWaitCondilock:
     *   - waited on by publishers (of course), for sequences to become writable.
     *   - waited on by workers, in case of multiple subscribers and encountering
     *     a sequence of previous round that is still being read, or still
     *     readable (if non-service and using runWorkerFrom(...) method).
     * - writeWaitCondilock:
     *   - waited on by workers (of course), for sequences to become readable.
     *   - waited on by publishers, when rejecting acquired sequences that could
     *     not be claimed due to shut down, or (but it is currently disabled),
     *     while trying to claim multiple sequences at once.
     */
    
    /*
     * Having a specific ring buffer class for each possible combination doesn't
     * seem to improve performances much (possibly because it mostly avoids a few
     * ifs on some final boolean variables), and can even degrade them as the JVM
     * has to handle much more bytecode.
     * Also, if it improves code readability for each particular case,
     * the much larger overall amount of code degrades maintainability.
     * As a result, we stick to this unique and versatile implementation.
     */

    /*
     * Possible optimization for service: in read wait boolean conditions,
     * when waiting for a readable sequence to change, it might be possible not
     * to check state, by considering that a readable sequence always becomes
     * either writable or rejected.
     * But we don't think it's worth it due to the already high complexity
     * of this class.
     */
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean ASSERTIONS = false;

    /**
     * Number of times a worker is late with linear scan,
     * that triggers switch to logarithmic scan.
     * 
     * Logarithmic scan is typically slower than linear scan
     * for small "distances", possibly because due to non-linear
     * memory usage, hence this threshold, but it helps a lot
     * for large ring buffers.
     */
    private static final int LOG_SCAN_THRESHOLD = 1024;

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    /*
     * subscribers side
     */

    private static class MyWorkerWaitStopBC implements InterfaceBooleanCondition {
        protected final MyUnicastWorker worker;
        protected PostPaddedAtomicLong entry_WWSBC;
        protected long seqStat_WWSBC;
        protected int localState_WWSBC;
        private boolean trueBecauseOfState_WWSBC;
        public MyWorkerWaitStopBC(final MyUnicastWorker worker) {
            this.worker = worker;
        }
        @Override
        public boolean isTrue() {
            return isTrue_static(this);
        }
        public boolean isTrueBecauseOfState() {
            return this.trueBecauseOfState_WWSBC;
        }
        public void waitForEntryChangeOrStopState(
                final PostPaddedAtomicLong entry,
                long seqStat,
                int localState,
                final InterfaceCondilock condilock) throws InterruptedException {
            this.entry_WWSBC = entry;
            this.seqStat_WWSBC = seqStat;
            this.localState_WWSBC = localState;
            condilock.awaitUntilNanosTimeoutTimeWhileFalseInLock(this, Long.MAX_VALUE);
        }
        protected static boolean isTrue_static(MyWorkerWaitStopBC bc) {
            // Evaluating state part first, for entry part is usually false anyway
            // when this method is used, and to avoid the need for an initial reset
            // of the set boolean.
            return (bc.trueBecauseOfState_WWSBC = (bc.worker.state.get() > bc.localState_WWSBC))
                    || (bc.entry_WWSBC.get() != bc.seqStat_WWSBC);
        }
    }

    /**
     * Boolean condition specific for waiting on writable entries.
     * Adds a check to take care of events that would occur ahead,
     * due to non-monotonicity.
     */
    private static class MyWorkerWaitStopBC_NM extends MyWorkerWaitStopBC {
        private final LongCounter pubSeqNM;
        public MyWorkerWaitStopBC_NM(MyUnicastWorker worker) {
            super(worker);
            this.pubSeqNM =  worker.ringBuffer.pubSeqNM;
        }
        @Override
        public boolean isTrue() {
            return isTrue2_static(this);
        }
        private static boolean isTrue2_static(MyWorkerWaitStopBC_NM bc) {
            return isTrue_static(bc)
                    || bc.pubSeqNM.isLowerThanMaxGot(sequence(bc.seqStat_WWSBC));
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
     */
    private static class MyUnicastWorker extends MyAbstractWorker {
        final UnicastRingBuffer ringBuffer;
        final boolean serviceOrSingleSubscriber;
        final MyWorkerWaitStopBC waitStopBC;
        final MyWorkerWaitStopBC_NM waitStopBCAheadNM;
        protected MyUnicastWorker(
                UnicastRingBuffer ringBuffer,
                PostPaddedAtomicInteger state,
                InterfaceRingBufferSubscriber subscriber) {
            super(
                    ringBuffer,
                    state,
                    subscriber);
            this.ringBuffer = ringBuffer;
            this.serviceOrSingleSubscriber = this.service || ringBuffer.singleSubscriber;
            this.waitStopBC = new MyWorkerWaitStopBC(this);
            this.waitStopBCAheadNM = (ringBuffer.pubSeqNM == null) ? null : new MyWorkerWaitStopBC_NM(this);
        }
        @Override
        public void runWorkerFrom(long startSequence) throws InterruptedException {
            // Disabled if single subscriber, else he could
            // wait forever for a skipped sequence to be read.
            if (this.serviceOrSingleSubscriber) {
                throw new UnsupportedOperationException();
            }
            
            // Volatile read.
            final int recentState = this.state.get();

            final long mps = this.getMaxPassedSequenceLocal();
            final long newMPS = startSequence-1;
            if (newMPS != mps) {
                // Allowing to go backward, but not too much.
                if (startSequence < INITIAL_SEQUENCE) {
                    throw new IllegalArgumentException("startSequence ["+startSequence+"] must be >= "+INITIAL_SEQUENCE);
                }
                this.setMaxPassedSequence(newMPS);
            }
            
            this.runWorkerImplAfterStateAndMPSUpdates(recentState);
        }
        @Override
        protected void afterLastMaxPassedSequenceSetting() {
            // Nothing to do: for unicast ring buffer,
            // we signal proper condilocks aside from
            // max passed sequence setting, which is
            // rarely done.
        }
        @Override
        protected void innerRunWorker(int recentState) throws InterruptedException {
            innerRunWorker_static(this, recentState);
        }
        private static boolean isSequenceLate(int bufferCapacity, PostPaddedAtomicLong entry, long attemptSequence) {
            long seqStat = entry.get();
            long sequence = sequence(seqStat);
            int status = status(seqStat);
            long virtualSequence = (status == ES_BEING_READ) ? sequence+bufferCapacity : sequence;
            return (virtualSequence > attemptSequence);
        }
        /**
         * Logarithmic search of lowest non-late attempt sequence,
         * when a worker is late one round or more.
         * 
         * Causes non-linear memory access, so can make things
         * actually slower, but it does not hurt much for small ring buffers,
         * and can help nicely for huge ones (100k elements or more).
         * 
         * Designed to be called only if the worker was late on its
         * attempt sequence, and computed the specified next attempt
         * sequence as "sequence - jump + 1".
         * 
         * Useless if single subscriber (but then worker never late,
         * so not called).
         */
        private static long logarithmicScan(
                UnicastRingBuffer ringBuffer,
                long attemptSequenceFrom) {
            // Possibly not late (if not late, it s result).
            // but rare: if late (one round or more),
            // front can be anywhere in the ring buffer.
            // ===> should maybe start with width = half the ring buffer ???
            long lowSeq = attemptSequenceFrom;

            // Not starting with a width of 1,
            // to avoid a check later in the algorithm.
            long width = 2;

            long highSeq = attemptSequenceFrom + (width - 1);

            final PostPaddedAtomicLong[] entries = ringBuffer.entries;
            final int indexMask = ringBuffer.indexMask;
            final int bufferCapacity = indexMask+1;

            PostPaddedAtomicLong highEntry = entries[(int)highSeq & indexMask];
            
            LoopCheckHigh : while (true) {
                if(ASSERTIONS)assert(width >= 2);
                if (isSequenceLate(bufferCapacity, highEntry, highSeq)) {
                    // High is late: moving up past high, doubling width.
                    lowSeq = highSeq + 1;
                    width <<= 1;
                    highSeq = lowSeq + (width - 1);
                    highEntry = entries[(int)highSeq & indexMask];
                    // Will check whether new high is again late.
                } else {
                    if (false) {
                        // Would only need this part of code if initial width was 1.
                        if (width == 1) {
                            // High is not late, and width is 1: we're done.
                            return highSeq;
                        }
                    }

                    LoopCheckMid : while (true) {
                        if(ASSERTIONS)assert(width >= 2);
                        // If width is even, takes "lower" mid
                        // (and if lowSeq+1 == highSeq, midSeq is lowSeq).
                        long midSeq = ((lowSeq+highSeq)>>>1);
                        PostPaddedAtomicLong midEntry = entries[(int)midSeq & indexMask];
                        
                        if (isSequenceLate(bufferCapacity, midEntry, midSeq)) {
                            if (width <= 3) {
                                // low (= mid) is late, but we just checked
                                // (above, or below), that current high was not late,
                                // which means it's the lowest non-late sequence.
                                if(ASSERTIONS)assert(midSeq+1 == highSeq);
                                return highSeq;
                            }
                            if(ASSERTIONS)assert(width >= 4);
                            // Moving up.
                            lowSeq = midSeq + 1;
                            width = highSeq - lowSeq + 1;
                            // Will check whether high is still not late.
                            continue LoopCheckHigh;
                        } else {
                            if (width == 2) {
                                if(ASSERTIONS)assert(lowSeq == midSeq);
                                return midSeq;
                            }
                            // Moving down.
                            highSeq = midSeq;
                            highEntry = entries[(int)highSeq & indexMask];
                            width = highSeq - lowSeq + 1;
                            // Will check whether new mid is late.
                        }
                    }
                }
            }
        }
        private static void innerRunWorker_static(MyUnicastWorker worker, int recentState) throws InterruptedException {
            final UnicastRingBuffer ringBuffer = worker.ringBuffer;
            final boolean singleSubscriber = ringBuffer.singleSubscriber;
            final boolean service = ringBuffer.service;
            final boolean singleSubscriberAndNonService = singleSubscriber && (!service);
            final InterfaceCondilock readWaitCondilock = ringBuffer.readWaitCondilock;
            final InterfaceCondilock writeWaitCondilock = ringBuffer.writeWaitCondilock;
            final PostPaddedAtomicLong pubSeqM = ringBuffer.pubSeqM;
            final LongCounter pubSeqNM = ringBuffer.pubSeqNM;
            final PostPaddedAtomicLong[] entries = ringBuffer.entries;
            final int indexMask = ringBuffer.indexMask;
            final InterfaceRingBufferSubscriber subscriber = worker.subscriber;
            final int jump = entries.length;
            
            final MyWorkerWaitStopBC waitStopBC = worker.waitStopBC;
            final MyWorkerWaitStopBC_NM waitStopBCAheadNM = worker.waitStopBCAheadNM;

            if (recentState >= STATE_TERMINATE_ASAP) {
                return;
            }

            long attemptSequence = worker.getMaxPassedSequenceLocal() + 1;
            
            int lateCounter = 0;
            
            try {
                LoopNewAttempt : while (true) {
                    final int index = (int)attemptSequence & indexMask;
                    final PostPaddedAtomicLong entry = entries[index];
                    
                    while (true) {
                        final long seqStat = entry.get();
                        final long sequence = sequence(seqStat);
                        final int status = status(seqStat);

                        // Sequence that we would skip.
                        final long seqForLateness = (status == ES_BEING_READ) ? sequence+jump : sequence;
                        if (seqForLateness > attemptSequence) {
                            // Subscriber late one round or more: catching-up
                            // by jumping at start of current round, supposing
                            // current round ends up at this sequence (i.e. that
                            // this sequence is currently the highest in the ring buffer).
                            attemptSequence = seqForLateness - jump + 1;
                            if (++lateCounter == LOG_SCAN_THRESHOLD) {
                                // We have been late many consecutive times with linear scan:
                                // switching to logarithmic scan.
                                attemptSequence = logarithmicScan(ringBuffer, attemptSequence);
                                // Allowing for some linear scan after a logarithmic scan,
                                // since if we are late again it's hopefully not be by much.
                                lateCounter = 0;
                            }
                            continue LoopNewAttempt;
                        }
                        lateCounter = 0;

                        if(ASSERTIONS)assert(sequence <= attemptSequence);

                        // Might not need to be accurately computed all the time
                        // (a not-hurting default value might help performances).
                        // Can be true if actually false, unless it would prevent
                        // termination, but must not be false if actually true.
                        final boolean possiblyMoreToRead;
                        final InterfaceCondilock waitCondilock;
                        final MyWorkerWaitStopBC waitBC;

                        // If there is only one subscriber, READABLE case is the
                        // most common, so it's better to test it at first.
                        // If there are multiple subscribers, BEING_READ should
                        // be common too, so we test it second.
                        if (status == ES_READABLE) {
                            if (singleSubscriberAndNonService) {
                                if(ASSERTIONS)assert(sequence == attemptSequence);
                                // Got it.
                                // No need to CAS.
                                // Entry keeps READABLE status.
                                break;
                            }
                            if (sequence < attemptSequence) {
                                if(ASSERTIONS)assert((!service) && (!singleSubscriber));
                                // Can happen if a user did set this worker's start sequence
                                // past a sequence yet to be published or processed:
                                // the worker went over (processing them or not) events
                                // of superior sequences, and then ended-up on this one.
                                // In that case, to make sure events are always processed
                                // in monotonic order by a same worker (for a same run
                                // of the worker), we'll wait for another worker to
                                // handle this event.
                                // If there is no other worker, unless interrupted of some sort,
                                // we will wait forever.
                                possiblyMoreToRead = true;
                                waitCondilock = readWaitCondilock;
                                waitBC = waitStopBC;
                            } else {
                                if(ASSERTIONS)assert(sequence == attemptSequence);
                                // Trying to get it.
                                if (entry.compareAndSet(seqStat, seqStatWithNewStatus(seqStat, ES_BEING_READ))) {
                                    // CAS OK: got it.
                                    break;
                                } else {
                                    if (service && (pubSeqNM != null)) {
                                        // The successful CAS might have rejected this event, which we need to check for,
                                        // for we might have to CAS-up sequencer before skipping it.
                                        continue;
                                    } else {
                                        // Since previous seqStat read, anything might have happened,
                                        // but what we know is sequence and/or status did change,
                                        // so we can try another one.
                                        ++attemptSequence;
                                        continue LoopNewAttempt;
                                    }
                                }
                            }
                        } else if (status == ES_WRITABLE) {
                            if (sequence < attemptSequence) {
                                // Typically, a user did set this worker's start sequence
                                // to a sequence of a further round.
                                // Will will wait for publishers to publish (if they do!),
                                // and for other subscribers (if any!) to process sequences,
                                // until this one gets written and we can read it.
                                
                                // Computing whether there is possibly more to read,
                                // so that if state becomes TERMINATE_WHEN_IDLE, we
                                // properly return, and don't wait forever.
                                if (pubSeqNM != null) {
                                    /*
                                     * possiblyMoreToRead
                                     * <=> (maxGot >= attemptSequence)
                                     * <=> (maxGot > (attemptSequence-1))
                                     */
                                    possiblyMoreToRead = pubSeqNM.isLowerThanMaxGot(attemptSequence-1);
                                } else if (pubSeqM != null) {
                                    /*
                                     * possiblyMoreToRead
                                     * <=> (maxGot >= attemptSequence)
                                     * <=> (pubSeqM-1 >= attemptSequence)
                                     * <=> (pubSeqM > attemptSequence)
                                     */
                                    possiblyMoreToRead = (pubSeqM.get() > attemptSequence);
                                } else {
                                    possiblyMoreToRead = false;
                                }
                                waitCondilock = writeWaitCondilock;
                                waitBC = waitStopBC;
                            } else {
                                if(ASSERTIONS)assert(sequence == attemptSequence);
                                // If non-monotonic claim, need to jump-up writable entries,
                                // even if skipping them while terminating, else they might
                                // be written, and a non-terminating and late worker might
                                // skip them while catching-up.
                                if (pubSeqNM != null) {
                                    final long cellSeq = pubSeqNM.getCellValue(attemptSequence);
                                    if (cellSeq == attemptSequence) {
                                        if(ASSERTIONS)assert(attemptSequence == sequence);
                                        /*
                                         * possiblyMoreToRead
                                         * <=> (maxGot >= attemptSequence)
                                         * <=> (maxGot > (attemptSequence-1))
                                         */
                                        // In O(nbrOfAtomicCounters), but shouldn't hurt much
                                        // since we checked currentness before.
                                        possiblyMoreToRead = pubSeqNM.isLowerThanMaxGot(attemptSequence-1);
                                        if (possiblyMoreToRead) {
                                            if (ringBuffer.tryToJumpUp(entry, sequence)) {
                                                // Jumped-it-up.
                                                ++attemptSequence;
                                                continue LoopNewAttempt;
                                            } else {
                                                // Sequence has already been acquired.
                                                // In both cases, we will wait for entry to be taken care of,
                                                // on write wait condilock, which is signaled in both cases
                                                // (entry written, or jumped-up).
                                                // If we are terminating, we wait, in case entry ends up being
                                                // readable, in which case we will try to read it if state is
                                                // TERMINATE_WHEN_IDLE.
                                                waitCondilock = writeWaitCondilock;
                                                waitBC = waitStopBC;
                                            }
                                        } else {
                                            // Sequence was superior or equal to max acquired:
                                            // will wait for it to be written.
                                            waitCondilock = writeWaitCondilock;
                                            waitBC = waitStopBCAheadNM;
                                        }
                                    } else { // attemptSequence not current sequencer's value
                                        // Sequence not current: has already been acquired for
                                        // write or jump-up.
                                        if(ASSERTIONS)assert(cellSeq > attemptSequence);
                                        if(ASSERTIONS)assert(sequence < pubSeqNM.getMinCellValue());
                                        possiblyMoreToRead = true;
                                        // We will wait for entry to be taken care of, on write wait condilock,
                                        // which is signaled in case of write or jump-up.
                                        waitCondilock = writeWaitCondilock;
                                        waitBC = waitStopBC;
                                    }
                                } else { // monotonic (single or multi publisher)
                                    if (pubSeqM != null) {
                                        // Need to compute it, to avoid waiting forever if idle.
                                        /*
                                         * possiblyMoreToRead
                                         * <=> (maxGot >= attemptSequence)
                                         * <=> (pubSeqM-1 >= attemptSequence)
                                         * <=> (pubSeqM > attemptSequence)
                                         */
                                        possiblyMoreToRead = (pubSeqM.get() > attemptSequence);
                                    } else {
                                        // Single publisher case: always idle if encounter a writable
                                        // sequence, since they are published monotonically.
                                        possiblyMoreToRead = false;
                                    }
                                    // Will wait for entry to be written (unless termination).
                                    waitCondilock = writeWaitCondilock;
                                    waitBC = waitStopBC;
                                }
                            }
                        } else if (status == ES_BEING_READ) {
                            // Worker might be almost wrapping, if sequence is still being read,
                            // in which case we need to wait for sequence to be read,
                            // to make sure not to wrap.
                            // sequence can't be attemptSequence, due to the above lateness check.
                            if(ASSERTIONS)assert(sequence < attemptSequence);
                            // Might be false, but we don't bother to compute it yet
                            // (i.e. while encountering BEING_READ sequences).
                            possiblyMoreToRead = true;
                            // Waiting even in case of termination, which
                            // should not hurt (small wait).
                            waitCondilock = readWaitCondilock;
                            waitBC = waitStopBC;
                        } else { // REJECTED
                            if(ASSERTIONS)assert(status == ES_REJECTED);
                            // Even if encountering a rejected sequence,
                            // we might still want to read more, if not idle.
                            if (pubSeqNM != null) {
                                // Since event has been rejected, corresponding sequence has been claimed.
                                if(ASSERTIONS)assert(pubSeqNM.getCellValue(sequence) > sequence);
                                /*
                                 * possiblyMoreToRead
                                 * <=> (maxGot > attemptSequence)
                                 */
                                possiblyMoreToRead = pubSeqNM.isLowerThanMaxGot(attemptSequence);
                                if (possiblyMoreToRead) {
                                    if (sequence < attemptSequence) {
                                        // Both ring buffer capacity and number of cells being multiples of two,
                                        // they are multiple of each other, so the cell that holds a sequence
                                        // also can hold all the sequences for the corresponding entry.
                                        final long cellSeq = pubSeqNM.getCellValue(sequence);
                                        if(ASSERTIONS)assert(cellSeq >= attemptSequence);
                                        if (cellSeq == attemptSequence) {
                                            // Trying to CAS-up sequencer, to make sure workers's attempt sequences
                                            // are always behind sequencer's cells, which is an invariant that can
                                            // be supposed in some places.
                                            boolean unused = pubSeqNM.isCurrentAndCompareAndIncrement(cellSeq);
                                        }
                                    }

                                    ++attemptSequence;
                                    continue LoopNewAttempt;
                                } else {
                                    return;
                                }
                            } else { // monotonic (single or multi publisher)
                                if (pubSeqM != null) {
                                    // Since event has been rejected, corresponding sequence has been claimed.
                                    if(ASSERTIONS)assert(pubSeqM.get() > sequence);
                                    /*
                                     * possiblyMoreToRead
                                     * <=> (maxGot > attemptSequence)
                                     * <=> (pubSeqM-1 > attemptSequence)
                                     */
                                    final long maxGot = pubSeqM.get()-1;
                                    possiblyMoreToRead = (maxGot > attemptSequence);
                                    if (possiblyMoreToRead) {
                                        ++attemptSequence;
                                        continue LoopNewAttempt;
                                    } else {
                                        return;
                                    }
                                } else {
                                    return;
                                }
                            }
                        }

                        if(ASSERTIONS)assert(sequence <= attemptSequence);
                        if(ASSERTIONS)assert(!(
                                // If this is true, then we should be reading it, not waiting.
                                (status == ES_READABLE)
                                && (sequence == attemptSequence)
                                && (recentState < STATE_TERMINATE_WHEN_IDLE)
                                ));

                        if (recentState >= STATE_TERMINATE_WHEN_IDLE) {
                            if (recentState > STATE_TERMINATE_WHEN_IDLE) {
                                return;
                            }
                            if (!possiblyMoreToRead) {
                                return;
                            }
                        }
                        worker.callOnBatchEndIfNeeded();
                        waitBC.waitForEntryChangeOrStopState(
                                entry,
                                seqStat,
                                recentState,
                                waitCondilock);
                        if (waitBC.isTrueBecauseOfState()) {
                            recentState = worker.state.get();
                            if (recentState >= STATE_TERMINATE_ASAP) {
                                // Re-checking entry, in case it is now readable.
                                // If it is not, we will find out we are idle,
                                // and won't wait for another sequence.
                                continue;
                            }
                        }
                    }

                    // Got it.
                    if(ASSERTIONS)assert(status(entry.get()) == (singleSubscriberAndNonService ? ES_READABLE : ES_BEING_READ));
                    if(ASSERTIONS)assert(sequence(entry.get()) == attemptSequence);

                    // attemptSequence incremented even if readEvent throws an exception.
                    try {
                        subscriber.readEvent(attemptSequence, index);
                        worker.needToCallOnBatchEnd = true;
                    } finally {
                        ringBuffer.setWritableAndSignalAll_afterRead(
                                entry,
                                (attemptSequence++) + jump);
                    }

                    if (subscriber.processReadEvent()) {
                        return;
                    }

                    recentState = worker.state.get();
                    if (recentState >= STATE_TERMINATE_ASAP) {
                        break;
                    }
                } // LoopNewAttempt
            } finally {
                worker.setMaxPassedSequenceLocal(attemptSequence-1);
            }
        }
    }

    /*
     * publishers side
     */

    private static class MyRBClaimWaitStopBC implements InterfaceBooleanCondition {
        private PostPaddedAtomicLong entry;
        private long seqStat;
        @Override
        public boolean isTrue() {
            return isTrue_static(this);
        }
        public boolean isTrueBecauseOfState() {
            return false;
        }
        public boolean waitForEntryChangeUntilTimeoutTime(
                final PostPaddedAtomicLong entry,
                long seqStat,
                long endTimeoutTimeNS,
                final InterfaceCondilock condilock) throws InterruptedException {
            this.entry = entry;
            this.seqStat = seqStat;
            return condilock.awaitUntilNanosTimeoutTimeWhileFalseInLock(this, endTimeoutTimeNS);
        }
        public void waitForEntryChangeUninterruptibly(
                final PostPaddedAtomicLong entry,
                long seqStat,
                final InterfaceCondilock condilock) {
            this.entry = entry;
            this.seqStat = seqStat;
            condilock.awaitWhileFalseInLockUninterruptibly(this);
        }
        protected static boolean isTrue_static(MyRBClaimWaitStopBC bc) {
            return (bc.entry.get() != bc.seqStat);
        }
    }

    private static class MyRBSClaimWaitStopBC extends MyRBClaimWaitStopBC {
        final PostPaddedAtomicInteger ringBufferState;
        final int waitStopMinState;
        private boolean trueBecauseOfState = false;
        public MyRBSClaimWaitStopBC(
                UnicastRingBuffer ringBuffer,
                int waitStopMinState) {
            this.ringBufferState = ringBuffer.ringBufferState;
            this.waitStopMinState = waitStopMinState;
        }
        @Override
        public boolean isTrue() {
            return isTrue2_static(this);
        }
        /**
         * Undefined if last call to isTrue() did return false.
         */
        @Override
        public boolean isTrueBecauseOfState() {
            return this.trueBecauseOfState;
        }
        private static boolean isTrue2_static(MyRBSClaimWaitStopBC bc) {
            return isTrue_static(bc)
                    || (bc.trueBecauseOfState = (bc.ringBufferState.get() >= bc.waitStopMinState));
        }
    }

    private static class MyPublishPortLocals {
        final MyRBClaimWaitStopBC claimWaitStopBC;
        final MyRBSClaimWaitStopBC onAcquiredSequenceLossWaitStopBC;
        /**
         * Only for non-monotonic.
         */
        final LocalData sequencerLocals;
        /**
         * Only for non-monotonic tryClaimSequence.
         */
        int nbrOfFailedCAS;
        public MyPublishPortLocals(final MyAbstractPublishPort port) {
            this.claimWaitStopBC = port.service ? new MyRBSClaimWaitStopBC(port.ringBuffer,STATE_TERMINATE_WHEN_IDLE) : new MyRBClaimWaitStopBC();
            this.onAcquiredSequenceLossWaitStopBC = port.service ? new MyRBSClaimWaitStopBC(port.ringBuffer,STATE_TERMINATE_ASAP) : null;
            if (port.ringBuffer.pubSeqNM != null) {
                this.sequencerLocals = port.ringBuffer.pubSeqNM.newLocalData();
            } else {
                this.sequencerLocals = null;
            }
        }
    }

    private static abstract class MyAbstractPublishPort implements InterfaceRingBufferPublishPort {
        final UnicastRingBuffer ringBuffer;
        final boolean service;
        final boolean writeLazySets;
        final PostPaddedAtomicLong[] entries;
        final int indexMask;
        final PostPaddedAtomicInteger ringBufferState;
        final InterfaceCondilock readWaitCondilock;
        final InterfaceCondilock writeWaitCondilock;
        public MyAbstractPublishPort(UnicastRingBuffer ringBuffer) {
            this.ringBuffer = ringBuffer;
            this.service = ringBuffer.service;
            this.writeLazySets = ringBuffer.writeLazySets;
            this.entries = ringBuffer.entries;
            this.indexMask = ringBuffer.indexMask;
            this.ringBufferState = ringBuffer.ringBufferState;
            this.readWaitCondilock = ringBuffer.readWaitCondilock;
            this.writeWaitCondilock = ringBuffer.writeWaitCondilock;
        }
        @Override
        public final int sequenceToIndex(long sequence) {
            return (int)sequence & this.indexMask;
        }
        @Override
        public final void publish(long sequence) {
            publish_static(this, sequence);
        }
        @Override
        public final void publish(
                long minSequence,
                int nbrOfSequences) {
            publish_static(this, minSequence, nbrOfSequences);
        }
        protected abstract MyPublishPortLocals getLocals();
        protected final boolean isServiceAndShutdown() {
            return this.service && isShutdown(this.ringBufferState.get());
        }
        /**
         * A sequence is lost if it has been acquired but can't be claimed
         * due to no room and ring buffer being shut down.
         * 
         * The point of this treatment is to reject writable sequences that were acquired,
         * (instead of publishing them, on which we gave up), for workers to skip them
         * instead of waiting for them to become readable.
         * Rejection is done without calling rejection handler, since these
         * sequences won't have been published.
         */
        protected final void onAcquiredSequencesLoss(
                long minAcquiredSequence,
                int nbrOfAcquiredSequences) {
            if(ASSERTIONS)assert(isServiceAndShutdown());

            final int indexMask = this.indexMask;
            final PostPaddedAtomicLong[] entries = this.entries;

            final MyRBClaimWaitStopBC bc = this.getLocals().onAcquiredSequenceLossWaitStopBC;

            /*
             * Important to retrieve state before retrieving
             * and treating each entry seqState accordingly,
             * else could use a state posterior to an entry setting,
             * and reject and entry published prior to a shutdown(),
             * i.e. that must be processed.
             * 
             * We loop on acquired sequences from first to last, and,
             * while detected state is only TERMINATE_WHEN_IDLE:
             * - when encountering a writable entry with a sequence that
             *   we acquired, we reject the sequence, so that workers
             *   can skip them, but without notifying rejection handler
             *   since the sequence has not been published,
             * - else, entry's sequence is necessarily strictly inferior:
             *   if it's rejected we go to next sequence, else we wait
             *   for it to be written and read, or rejected (by shutdownNow
             *   method).
             */
            
            if (this.ringBufferState.get() >= STATE_TERMINATE_ASAP) {
                return;
            }

            for (int i=0;i<nbrOfAcquiredSequences;i++) {
                final long acquiredSequence = minAcquiredSequence + i;
                final int index = (int)acquiredSequence & indexMask;
                final PostPaddedAtomicLong entry = entries[index];
                while (true) {
                    final long seqStat = entry.get();
                    final int status = status(seqStat);
                    final long sequence = sequence(seqStat);
                    if (sequence == acquiredSequence) {
                        if(ASSERTIONS)assert(status == ES_WRITABLE);
                        // Got it: rejecting it (right away, without intermediary readable state).
                        this.ringBuffer.rejectWritableEvent(seqStat, entry);
                        // Not adding it to rejected sequences, since it has not been claimed.
                        // Going to next entry, if any.
                        break;
                    } else {
                        if(ASSERTIONS)assert(sequence < acquiredSequence);
                        if (status == ES_WRITABLE) {
                            // Waiting for entry to become readable, or to be jumped-up,
                            // or to be rejected.
                            bc.waitForEntryChangeUninterruptibly(
                                    entry,
                                    seqStat,
                                    this.writeWaitCondilock);
                            if (bc.isTrueBecauseOfState()) {
                                if(ASSERTIONS)assert(this.ringBufferState.get() >= STATE_TERMINATE_ASAP);
                                return;
                            }
                            // Will check entry again.
                        } else if ((status == ES_READABLE) || (status == ES_BEING_READ)) {
                            bc.waitForEntryChangeUninterruptibly(
                                    entry,
                                    seqStat,
                                    this.readWaitCondilock);
                            if (bc.isTrueBecauseOfState()) {
                                if(ASSERTIONS)assert(this.ringBufferState.get() >= STATE_TERMINATE_ASAP);
                                return;
                            }
                            // Will check entry again.
                        } else { // rejected
                            if(ASSERTIONS)assert(status == ES_REJECTED);
                            // No worker will wait on this entry:
                            // going to next entry, if any.
                            break;
                        }
                    }
                }
            }
        }
        private static void publish_static(MyAbstractPublishPort port, long sequence) {
            final int index = (int)sequence & port.indexMask;
            final PostPaddedAtomicLong entry = port.entries[index];
            final long previousSeqStat = entry.get();
            final long newSeqStat = seqStatWithNewStatus(previousSeqStat, ES_READABLE);
            if (port.writeLazySets) {
                entry.lazySet(newSeqStat);
            } else {
                entry.set(newSeqStat);
            }
            onPublish(port, sequence, sequence);
        }
        private static void publish_static(
                MyAbstractPublishPort port,
                long minSequence,
                int nbrOfSequences) {
            final long maxSequence = minSequence + nbrOfSequences - 1;
            final int indexMask = port.indexMask;
            final PostPaddedAtomicLong[] entries = port.entries;
            for (long seq=minSequence;seq<=maxSequence;seq++) {
                final int index = (int)seq & indexMask;
                final PostPaddedAtomicLong entry = entries[index];
                final long previousSeqStat = entry.get();
                final long newSeqStat = seqStatWithNewStatus(previousSeqStat, ES_READABLE);
                // If not last one, always using lazy set.
                if (port.writeLazySets || (seq != maxSequence)) {
                    entry.lazySet(newSeqStat);
                } else {
                    entry.set(newSeqStat);
                }
            }
            onPublish(port, minSequence, maxSequence);
        }
        private static void onPublish(
                MyAbstractPublishPort port,
                long minPublished,
                long maxPublished) {
            if (port.service && (port.ringBufferState.get() != STATE_RUNNING)) {
                onEarlyOrLatePublish(port, minPublished, maxPublished);
            }
            /*
             * Signaling all waiters, even though only one worker
             * might actually find something to process, to make
             * sure all workers always end up at highest published
             * sequence. Otherwise, when an event is published,
             * woken-up workers might have to catch-up all ring buffer's
             * length to find the event to process, which could cause
             * high latency.
             */
            port.writeWaitCondilock.signalAllInLock();
        }
        /**
         * Doesn't need to be called for non-service ring buffers
         * (unless we want their state to be set to RUNNING if they
         * start to be used).
         */
        private static void onEarlyOrLatePublish(
                MyAbstractPublishPort port,
                long minSequence,
                long maxSequence) {
            if (port.ringBufferState.get() == STATE_PENDING) {
                port.ringBuffer.tryStart();
            }
            if (port.service && isShutdown(port.ringBufferState.get())) {
                // Late publish.
                port.tryToRejectSequences(minSequence, maxSequence);
            }
        }
        private void tryToRejectSequences(
                long minSequence,
                long maxSequence) {
            if(ASSERTIONS)assert(this.service);
            if(ASSERTIONS)assert(isShutdown(this.ringBufferState.get()));
            SequenceRangeVector sequencesRanges = null;
            for (long seq=minSequence;seq<=maxSequence;seq++) {
                final int index = (int)seq & this.indexMask;
                final PostPaddedAtomicLong entry = this.entries[index];
                // Quick volatile read for check before eventual CAS.
                final long seqStat = entry.get();
                if(ASSERTIONS)assert(sequence(seqStat) >= seq);
                final long rejectedOrNot = this.ringBuffer.tryToRejectReadableEvent(seqStat, seqStat(seq,ES_READABLE), entry);
                if (rejectedOrNot != Long.MIN_VALUE) {
                    // Did reject.
                    if (sequencesRanges == null) {
                        sequencesRanges = new SequenceRangeVector();
                    }
                    sequencesRanges.add(seq, seq);
                }
            }
            if (sequencesRanges != null) {
                this.ringBuffer.getRejectedEventHandlerImpl().onRejectedEvents(
                        this.ringBuffer,
                        sequencesRanges.sortAndMergeAndToArray());
            }
        }
    }

    /*
     * monotonic publishing
     */

    private static abstract class MyAbstractPublishPort_M extends MyAbstractPublishPort {
        public MyAbstractPublishPort_M(UnicastRingBuffer ringBuffer) {
            super(ringBuffer);
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
        protected abstract long sequencer_get();
        /**
         * Checking expected before CAS, for CAS is a heavy operation.
         */
        protected abstract boolean sequencer_isCurrentAndCompareAndIncrement(long sequence);
        protected abstract long sequencer_getAndIncrement();
        protected abstract long sequencer_getAndAdd(int delta);
        private static long tryClaimSequence_static(MyAbstractPublishPort_M port) {
            final int indexMask = port.indexMask;
            final PostPaddedAtomicLong[] entries = port.entries;
            while (true) {
                final long acquirableSequence = port.sequencer_get();
                final int index = (int)acquirableSequence & indexMask;
                final PostPaddedAtomicLong entry = entries[index];
                final long seqStat = entry.get();
                if ((seqStat == seqStat(acquirableSequence,ES_WRITABLE))
                        && port.sequencer_isCurrentAndCompareAndIncrement(acquirableSequence)) {
                    // Got it.
                    return acquirableSequence;
                }
                if (sequence(seqStat) < acquirableSequence) {
                    // Wrapping: giving up.
                    return SEQUENCE_NO_ROOM;
                }
                // Someone else got it before us: will try to get another sequence.
                if(ASSERTIONS)assert(!(port instanceof MyPublishPort_M_SinglePub));
            }
        }
        private static long tryClaimSequence_static(MyAbstractPublishPort_M port, long timeoutNS) throws InterruptedException {
            // Fast claim if there is room (not bothering with timing).
            final long firstClaimedSequence = tryClaimSequence_static(port);
            if ((firstClaimedSequence >= 0) || (timeoutNS <= 0)) {
                return firstClaimedSequence;
            }

            final long endTimeoutTimeNS = NumbersUtils.plusBounded(port.readWaitCondilock.timeoutTimeNS(),timeoutNS);

            // Null initially, to avoid eventual useless thread-local usage.
            MyRBClaimWaitStopBC bc = null;
            final int indexMask = port.indexMask;
            final PostPaddedAtomicLong[] entries = port.entries;
            while (true) {
                final long acquirableSequence = port.sequencer_get();
                final int index = (int)acquirableSequence & indexMask;
                final PostPaddedAtomicLong entry = entries[index];
                final long expectedSeqStat = seqStat(acquirableSequence,ES_WRITABLE);
                while (true) {
                    final long seqStat = entry.get();
                    if ((seqStat == expectedSeqStat)
                            && port.sequencer_isCurrentAndCompareAndIncrement(acquirableSequence)) {
                        // Got it.
                        return acquirableSequence;
                    }
                    if (sequence(seqStat) < acquirableSequence) {
                        // Wrapping.
                        // Waiting for entry to reach expected sequence and status.
                        if (bc == null) {
                            bc = port.getLocals().claimWaitStopBC;
                        }
                        final boolean bcTrue = bc.waitForEntryChangeUntilTimeoutTime(
                                entry,
                                seqStat,
                                endTimeoutTimeNS,
                                port.readWaitCondilock);
                        if (bcTrue) {
                            if (bc.isTrueBecauseOfState()) {
                                return SEQUENCE_SHUT_DOWN;
                            } else {
                                // Will check entry again.
                                continue;
                            }
                        } else {
                            return SEQUENCE_NO_ROOM;
                        }
                    }
                    // Someone else got it before us: will try to get another sequence.
                    if(ASSERTIONS)assert(!(port instanceof MyPublishPort_M_SinglePub));
                    break;
                }
            }
        }
        private static long claimSequence_static(MyAbstractPublishPort_M port) {
            final long acquiredSequence = port.sequencer_getAndIncrement();
            final int index = (int)acquiredSequence & port.indexMask;
            final PostPaddedAtomicLong entry = port.entries[index];
            final long expectedSeqStat = seqStat(acquiredSequence,ES_WRITABLE);
            // Null initially, to avoid eventual useless thread-local usage.
            MyRBClaimWaitStopBC bc = null;
            while (true) {
                final long seqStat = entry.get();
                // Never late.
                if(ASSERTIONS)assert(acquiredSequence >= sequence(seqStat));
                // Entry writable, and we are not ahead of it.
                if (seqStat == expectedSeqStat) {
                    // Got it.
                    return acquiredSequence;
                }
                // Waiting for entry to reach expected sequence and status.
                if (bc == null) {
                    bc = port.getLocals().claimWaitStopBC;
                }
                bc.waitForEntryChangeUninterruptibly(
                        entry,
                        seqStat,
                        port.readWaitCondilock);
                if (bc.isTrueBecauseOfState()) {
                    port.onAcquiredSequencesLoss(acquiredSequence, 1);
                    return SEQUENCE_SHUT_DOWN;
                } else {
                    // Will check entry again.
                }
            }
        }
        private static long claimSequences_static(MyAbstractPublishPort_M port, IntWrapper nbrOfSequences) {
            final int desiredNbrOfSequences = nbrOfSequences.value;
            if (desiredNbrOfSequences <= 0) {
                throw new IllegalArgumentException("number of sequences ["+desiredNbrOfSequences+"] must be > 0");
            }

            final PostPaddedAtomicLong[] entries = port.entries;
            final int indexMask = port.indexMask;

            final int nbrOfAcquiredSequences = Math.min(desiredNbrOfSequences, entries.length);

            final long minAcquiredSequence = port.sequencer_getAndAdd(nbrOfAcquiredSequences);
            final long maxAcquiredSequence = minAcquiredSequence + (nbrOfAcquiredSequences - 1);
            // Null initially, to avoid eventual useless thread-local usage.
            MyRBClaimWaitStopBC bc = null;
            
            // Might only need to check first and last sequences.
            // Even if single publisher and single subscriber, if we are a service,
            // we need to check all sequences, because some might have been rejected
            // on a previous publication or are being rejected on shutdownNow.
            final int seqJump;
            if (port.ringBuffer.singlePublisher
                    && port.ringBuffer.singleSubscriber
                    && (!port.service)) {
                seqJump = Math.max(1, nbrOfAcquiredSequences-1);
            } else {
                seqJump = 1;
            }
            for (long seq=minAcquiredSequence;seq<=maxAcquiredSequence;seq+=seqJump) {
                final int index = (int)seq & indexMask;
                final PostPaddedAtomicLong entry = entries[index];
                final long expectedSeqStat = seqStat(seq,ES_WRITABLE);
                while (true) {
                    final long seqStat = entry.get();
                    // Never late.
                    if(ASSERTIONS)assert(seq >= sequence(seqStat));
                    // Entry writable, and we are not ahead of it.
                    if (seqStat == expectedSeqStat) {
                        // Got it.
                        // Will try to get another sequence,
                        // unless we reached max already.
                        break;
                    }
                    // Waiting for entry to reach expected sequence and status.
                    if (bc == null) {
                        bc = port.getLocals().claimWaitStopBC;
                    }
                    bc.waitForEntryChangeUninterruptibly(
                            entry,
                            seqStat,
                            port.readWaitCondilock);
                    if (bc.isTrueBecauseOfState()) {
                        port.onAcquiredSequencesLoss(minAcquiredSequence, nbrOfAcquiredSequences);
                        nbrOfSequences.value = 0;
                        return SEQUENCE_SHUT_DOWN;
                    } else {
                        // Will check entry again.
                    }
                }
            }
            nbrOfSequences.value = nbrOfAcquiredSequences;
            return minAcquiredSequence;
        }
    }

    private static class MyPublishPort_M_SinglePub extends MyAbstractPublishPort_M {
        final MyPublishPortLocals locals = new MyPublishPortLocals(this);
        private long nextSequence = INITIAL_SEQUENCE;
        public MyPublishPort_M_SinglePub(UnicastRingBuffer ringBuffer) {
            super(ringBuffer);
        }
        @Override
        protected MyPublishPortLocals getLocals() {
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
        protected long sequencer_getAndIncrement() {
            return this.nextSequence++;
        }
        @Override
        protected long sequencer_getAndAdd(int delta) {
            final long result = this.nextSequence;
            this.nextSequence += delta;
            return result;
        }
    }

    private static class MyPublishPort_M_MultiPub_Main extends MyAbstractPublishPort_M {
        // Only one main publish port: no need ThreadLocal as main class member.
        final ThreadLocal<MyPublishPortLocals> threadLocalLocals;
        final PostPaddedAtomicLong pubSeqM;
        public MyPublishPort_M_MultiPub_Main(UnicastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.pubSeqM = ringBuffer.pubSeqM;
            this.threadLocalLocals = new ThreadLocal<MyPublishPortLocals>() {
                @Override
                public MyPublishPortLocals initialValue() {
                    return new MyPublishPortLocals(MyPublishPort_M_MultiPub_Main.this);
                }
            };
        }
        @Override
        protected MyPublishPortLocals getLocals() {
            return this.threadLocalLocals.get();
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
        protected long sequencer_getAndIncrement() {
            return this.pubSeqM.getAndIncrement();
        }
        @Override
        protected long sequencer_getAndAdd(int delta) {
            return this.pubSeqM.getAndAdd(delta);
        }
    }

    private static class MyPublishPort_M_MultiPub_Local extends MyAbstractPublishPort_M {
        final PostPaddedAtomicLong pubSeqM;
        final MyPublishPortLocals locals;
        public MyPublishPort_M_MultiPub_Local(UnicastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.pubSeqM = ringBuffer.pubSeqM;
            this.locals = new MyPublishPortLocals(this);
        }
        @Override
        protected MyPublishPortLocals getLocals() {
            return this.locals;
        }
        @Override
        protected long sequencer_get() {
            return this.pubSeqM.get();
        }
        @Override
        protected boolean sequencer_isCurrentAndCompareAndIncrement(long expected) {
            return (this.pubSeqM.get() == expected)
                    && this.pubSeqM.compareAndSet(expected, expected+1);
        }
        @Override
        protected long sequencer_getAndIncrement() {
            return this.pubSeqM.getAndIncrement();
        }
        @Override
        protected long sequencer_getAndAdd(int delta) {
            return this.pubSeqM.getAndAdd(delta);
        }
    }

    /*
     * non monotonic publishing
     */

    private static abstract class MyAbstractPublishPort_NM extends MyAbstractPublishPort {
        final LongCounter pubSeqNM;
        public MyAbstractPublishPort_NM(UnicastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.pubSeqNM = ringBuffer.pubSeqNM;
        }
        @Override
        public final long tryClaimSequence() {
            return tryClaimSequence_locals_static(this, this.getLocals());
        }
        @Override
        public final long tryClaimSequence(long timeoutNS) throws InterruptedException {
            final MyPublishPortLocals locals = this.getLocals();

            // Fast claim if there is room (not bothering with timing).
            final long firstClaimedSequence = tryClaimSequence_locals_static(this, locals);
            if ((firstClaimedSequence >= 0) || (timeoutNS <= 0)) {
                return firstClaimedSequence;
            }

            final long endTimeoutTimeNS = NumbersUtils.plusBounded(this.readWaitCondilock.timeoutTimeNS(),timeoutNS);
            return tryClaimSequenceUntilTimeoutTime_locals_static(this, locals, endTimeoutTimeNS);
        }
        @Override
        public final long claimSequence() {
            /*
             * We don't claim a sequence from sequencer, and then wait for its
             * entry to reach it and be writable (as done for monotonic publishing),
             * for it could make publishers try to claim wrapping sequences,
             * while non-wrapping sequences could be available, which can
             * hurt performances a lot.
             */
            return claimSequence_locals_static(this, this.getLocals());
        }
        @Override
        public final long claimSequences(IntWrapper nbrOfSequences) {
            return claimSequences_static(this, nbrOfSequences);
        }
        private static long claimSequences_static(MyAbstractPublishPort_NM port, IntWrapper nbrOfSequences) {
            int desiredNbrOfSequences = nbrOfSequences.value;
            if (desiredNbrOfSequences <= 0) {
                throw new IllegalArgumentException("number of sequences ["+desiredNbrOfSequences+"] must be > 0");
            }

            final LongCounter pubSeqNM = port.pubSeqNM;
            final PostPaddedAtomicLong[] entries = port.entries;
            final int indexMask = port.indexMask;

            final int maxNbrOfSequences = Math.min(desiredNbrOfSequences, entries.length);

            final MyPublishPortLocals locals = port.getLocals();

            final long minClaimedSequence = claimSequence_locals_static(port, locals);
            if (minClaimedSequence < 0) {
                nbrOfSequences.value = 0;
                return minClaimedSequence;
            }
            int actualNbrOfSequences = 1;

            final MyRBClaimWaitStopBC bc = locals.claimWaitStopBC;

            // Loop to claim consecutive sequences.
            LoopNewSequence : while (actualNbrOfSequences < maxNbrOfSequences) {
                final long acquirableSequence = minClaimedSequence + actualNbrOfSequences;
                final int index = (int)acquirableSequence & indexMask;
                final PostPaddedAtomicLong entry = entries[index];
                // Loop for a same entry.
                while (true) {
                    final long seqStat = entry.get();
                    final long sequence = sequence(seqStat);
                    InterfaceCondilock waitCondilock;
                    if (sequence == acquirableSequence) {
                        if (status(seqStat) == ES_WRITABLE) {
                            if (pubSeqNM.isCurrentAndCompareAndIncrement(sequence)) {
                                // Got it.
                                ++actualNbrOfSequences;
                                // Will try to get more, if we don't have enough.
                                continue LoopNewSequence;
                            }
                        }
                        // Someone else already acquired the sequence.
                        // Giving-up.
                        break LoopNewSequence;
                    } else if (sequence > acquirableSequence) {
                        // Someone else already acquired the sequence.
                        // Giving-up.
                        break LoopNewSequence;
                    } else {
                        // Wrapping.
                        final int status = status(seqStat);
                        if (status == ES_WRITABLE) {
                            if (port.ringBuffer.tryToJumpUp(entry, sequence)) {
                                // Will check entry again.
                                continue;
                            } else {
                                // We either can give up here, or keep trying
                                // after waiting for entry to be taken care of
                                // by whoever acquired the sequence.
                                // Neither seem to help throughput, but we prefer
                                // to break to make this claim faster/lighter.
                                if (true) {
                                    break LoopNewSequence;
                                } else {
                                    waitCondilock = port.writeWaitCondilock;
                                }
                            }
                        } else {
                            // Will wait (if not shut down).
                            waitCondilock = port.readWaitCondilock;
                        }
                    }
                    // If entry is readable or being read, will wait for it to be read.
                    // If entry is rejected, that means ring buffer is a service and
                    // that its state is shutdown, so the wait will return immediately.
                    bc.waitForEntryChangeUninterruptibly(
                            entry,
                            seqStat,
                            waitCondilock);
                    if (bc.isTrueBecauseOfState()) {
                        if (false) {
                            // Could also return what we managed to get up to now,
                            // like this, but we prefer fail-fast, and it is
                            // homogeneous with monotonic case.
                            break LoopNewSequence;
                        }
                        nbrOfSequences.value = 0;
                        port.onAcquiredSequencesLoss(minClaimedSequence, actualNbrOfSequences);
                        return SEQUENCE_SHUT_DOWN;
                    } else {
                        // Will check entry again.
                    }
                }
            }

            nbrOfSequences.value = actualNbrOfSequences;
            return minClaimedSequence;
        }
        private static long tryClaimSequence_locals_static(
                MyAbstractPublishPort_NM port,
                MyPublishPortLocals locals) {
            final LocalData localData = locals.sequencerLocals;
            final LongCounter pubSeqNM = port.pubSeqNM;
            final PostPaddedAtomicLong[] entries = port.entries;
            final int indexMask = port.indexMask;
            locals.nbrOfFailedCAS = 0;
            while (true) {
                // Taking care of using incrementing number of failed CAS,
                // to help get away from contention if this method is used
                // in a busy loop.
                final long acquirableSequence = pubSeqNM.get(localData, locals.nbrOfFailedCAS);
                final int index = (int)acquirableSequence & indexMask;
                final PostPaddedAtomicLong entry = entries[index];
                final long seqStat = entry.get();
                final long sequence = sequence(seqStat);
                if (sequence == acquirableSequence) {
                    if (status(seqStat) == ES_WRITABLE) {
                        if (pubSeqNM.isCurrentAndCompareAndIncrement(sequence)) {
                            // Got it.
                            return sequence;
                        } else {
                            ++locals.nbrOfFailedCAS;
                        }
                    }
                    // Sequence already claimed, by a publisher to write
                    // the entry, or by a subscriber to jump-it-up.
                    // Will try to claim another sequence.
                } else if (sequence > acquirableSequence) {
                    // Sequence already claimed, by a publisher to write
                    // the entry (which has since been read, since its
                    // sequence changed), or by a subscriber to jump-it-up.
                    // Will try to claim another sequence.
                } else {
                    // Wrapping.
                    // Our algorithms ensure that acquirable sequence can't
                    // be ahead more than one round - except if shut down.
                    if(ASSERTIONS)assert((sequence == acquirableSequence-entries.length) || (status(seqStat) == ES_REJECTED));
                    return SEQUENCE_NO_ROOM;
                }
            }
        }
        private static long tryClaimSequenceUntilTimeoutTime_locals_static(
                MyAbstractPublishPort_NM port,
                MyPublishPortLocals locals,
                long endTimeoutTimeNS) throws InterruptedException {
            final LocalData localData = locals.sequencerLocals;
            final MyRBClaimWaitStopBC bc = locals.claimWaitStopBC;
            final LongCounter pubSeqNM = port.pubSeqNM;
            final PostPaddedAtomicLong[] entries = port.entries;
            final int indexMask = port.indexMask;
            int nbrOfFailedCAS = 0;
            while (true) {
                final long acquirableSequence = pubSeqNM.get(localData, nbrOfFailedCAS);
                final int index = (int)acquirableSequence & indexMask;
                final PostPaddedAtomicLong entry = entries[index];
                while (true) {
                    final long seqStat = entry.get();
                    final long sequence = sequence(seqStat);
                    if (sequence == acquirableSequence) {
                        if (status(seqStat) == ES_WRITABLE) {
                            if (pubSeqNM.isCurrentAndCompareAndIncrement(sequence)) {
                                // Got it.
                                return acquirableSequence;
                            } else {
                                ++nbrOfFailedCAS;
                            }
                        }
                        // Sequence already claimed, by a publisher to write
                        // the entry, or by a subscriber to jump-it-up.
                        // Will try to claim another sequence.
                        break;
                    } else if (sequence > acquirableSequence) {
                        // Sequence already claimed, by a publisher to write
                        // the entry (which has since been read, since it's
                        // sequence changed), or by a subscriber to jump-it-up.
                        // Will try to claim another sequence.
                        break;
                    } else {
                        // Wrapping.
                        // Our algorithms ensure that acquirable sequence can't
                        // be ahead more than one round - except if shut down.
                        if(ASSERTIONS)assert((sequence == acquirableSequence-entries.length) || (status(seqStat) == ES_REJECTED));
                        final boolean bcTrue = bc.waitForEntryChangeUntilTimeoutTime(
                                entry,
                                seqStat,
                                endTimeoutTimeNS,
                                port.readWaitCondilock);
                        if (bcTrue) {
                            if (bc.isTrueBecauseOfState()) {
                                return SEQUENCE_SHUT_DOWN;
                            } else {
                                // Will check entry again.
                            }
                        } else {
                            return SEQUENCE_NO_ROOM;
                        }
                    }
                }
            }
        }
        private static long claimSequence_locals_static(
                MyAbstractPublishPort_NM port,
                MyPublishPortLocals locals) {
            final LocalData localData = locals.sequencerLocals;
            final MyRBClaimWaitStopBC bc = locals.claimWaitStopBC;
            final LongCounter pubSeqNM = port.pubSeqNM;
            final PostPaddedAtomicLong[] entries = port.entries;
            final int indexMask = port.indexMask;
            int nbrOfFailedCAS = 0;
            while (true) {
                final long acquirableSequence = pubSeqNM.get(localData, nbrOfFailedCAS);
                final int index = (int)acquirableSequence & indexMask;
                final PostPaddedAtomicLong entry = entries[index];
                while (true) {
                    final long seqStat = entry.get();
                    final long sequence = sequence(seqStat);
                    if (sequence == acquirableSequence) {
                        if (status(seqStat) == ES_WRITABLE) {
                            if (pubSeqNM.isCurrentAndCompareAndIncrement(sequence)) {
                                // Got it.
                                return acquirableSequence;
                            } else {
                                ++nbrOfFailedCAS;
                            }
                        }
                        // Sequence already claimed, by a publisher to write
                        // the entry, or by a subscriber to jump-it-up.
                        // Will try to claim another sequence.
                        break;
                    } else if (sequence > acquirableSequence) {
                        // Sequence already claimed, by a publisher to write
                        // the entry (which has since been read, since it's
                        // sequence changed), or by a subscriber to jump-it-up.
                        // Will try to claim another sequence.
                        break;
                    } else {
                        // Wrapping.
                        // Our algorithms ensure that acquirable sequence can't
                        // be ahead more than one round - except if shut down.
                        if(ASSERTIONS)assert((sequence == acquirableSequence-entries.length) || (status(seqStat) == ES_REJECTED));
                        bc.waitForEntryChangeUninterruptibly(
                                entry,
                                seqStat,
                                port.readWaitCondilock);
                        if(ASSERTIONS)assert(bc.isTrue());
                        if (bc.isTrueBecauseOfState()) {
                            return SEQUENCE_SHUT_DOWN;
                        } else {
                            // Will check entry again.
                        }
                    }
                }
            }
        }
    }

    private static class MyPublishPort_NM_Main extends MyAbstractPublishPort_NM {
        // Only one main publish port: no need ThreadLocal as main class member.
        final ThreadLocal<MyPublishPortLocals> threadLocalLocals;
        public MyPublishPort_NM_Main(UnicastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.threadLocalLocals = new ThreadLocal<MyPublishPortLocals>() {
                @Override
                public MyPublishPortLocals initialValue() {
                    return new MyPublishPortLocals(MyPublishPort_NM_Main.this);
                }
            };
        }
        @Override
        protected MyPublishPortLocals getLocals() {
            return this.threadLocalLocals.get();
        }
    }

    private static class MyPublishPort_NM_Local extends MyAbstractPublishPort_NM {
        final MyPublishPortLocals locals;
        public MyPublishPort_NM_Local(UnicastRingBuffer ringBuffer) {
            super(ringBuffer);
            this.locals = new MyPublishPortLocals(this);
        }
        @Override
        protected MyPublishPortLocals getLocals() {
            return this.locals;
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    /*
     * Entry status.
     * 
     * We don't need a "being written" status, for writable entries
     * are only being modified (written, jumped-up, or rejected) by
     * whoever acquired the corresponding sequence from the sequencer.
     * 
     * Storing status in right bits, for homogeneity with usages
     * where values (sequences here) can be negative.
     */

    private static final int ES_WRITABLE = 0;
    private static final int ES_READABLE = 1;
    private static final int ES_BEING_READ = 2;
    private static final int ES_REJECTED = 3;
    /**
     * Must be in [1,32].
     */
    private static final int ENTRY_STATUS_BIT_SIZE = 2;
    private static final int ENTRY_STATUS_MASK = NumbersUtils.intMaskLSBits1(ENTRY_STATUS_BIT_SIZE);
    private static final long ENTRY_NO_STATUS_MASK = NumbersUtils.longMaskLSBits0(ENTRY_STATUS_BIT_SIZE);
    private static long sequence(long seqStat) {
        return seqStat >> ENTRY_STATUS_BIT_SIZE;
    }
    private static int status(long seqStat) {
        return ((int)seqStat) & ENTRY_STATUS_MASK;
    }
    private static long seqStat(long sequence, int status) {
        if(ASSERTIONS)assert((sequence >= minSequence()) && (sequence <= maxSequence()));
        if(ASSERTIONS)assert((status >= 0) && (status <= ENTRY_STATUS_MASK));
        return (sequence << ENTRY_STATUS_BIT_SIZE) + status;
    }
    private static long seqStatWithNewStatus(long seqStat, int newStatus) {
        if(ASSERTIONS)assert((newStatus >= 0) && (newStatus <= ENTRY_STATUS_MASK));
        return (seqStat & ENTRY_NO_STATUS_MASK) + newStatus;
    }
    private static long minSequence() {
        return Long.MIN_VALUE>>ENTRY_STATUS_BIT_SIZE;
    }
    private static long maxSequence() {
        return Long.MAX_VALUE>>ENTRY_STATUS_BIT_SIZE;
    }

    /*
     * 
     */

    /**
     * The atomic long holds status and sequence,
     * which is last written or read one (depending on status) in this entry.
     */
    private final PostPaddedAtomicLong[] entries;

    private final PostPaddedAtomicLong pubSeqM;
    private final LongCounter pubSeqNM;
    private final MyAbstractPublishPort mainPublishPort;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @param bufferCapacity Buffer capacity. Must be a power of two.
     * @param pubSeqNbrOfAtomicCounters Number of atomic counters for publication sequencer,
     *        which is used to claim sequences before publication.
     *        Use 0 for single-publisher, 1 for multi-publisher with monotonic claim,
     *        or a power of two >= 2 for non-monotonic claim.
     *        For non-monotonic claim, can help to have this value larger
     *        (like twice) than actual parallelism.
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
    public UnicastRingBuffer(
            int bufferCapacity,
            int pubSeqNbrOfAtomicCounters,
            boolean singleSubscriber,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock) {
        this(
                bufferCapacity,
                pubSeqNbrOfAtomicCounters,
                singleSubscriber,
                false,
                readLazySets,
                writeLazySets,
                readWaitCondilock,
                writeWaitCondilock,
                null,
                null);
    }

    @Override
    public long tryClaimSequence() {
        return this.mainPublishPort.tryClaimSequence();
    }

    @Override
    public long tryClaimSequence(long timeoutNS) throws InterruptedException {
        return this.mainPublishPort.tryClaimSequence(timeoutNS);
    }

    @Override
    public long claimSequence() {
        return this.mainPublishPort.claimSequence();
    }

    @Override
    public long claimSequences(IntWrapper nbrOfSequences) {
        return this.mainPublishPort.claimSequences(nbrOfSequences);
    }

    @Override
    public void publish(long sequence) {
        MyAbstractPublishPort.publish_static(this.mainPublishPort, sequence);
    }

    @Override
    public void publish(
            long minSequence,
            int nbrOfSequences) {
        MyAbstractPublishPort.publish_static(this.mainPublishPort, minSequence, nbrOfSequences);
    }

    @Override
    public InterfaceRingBufferPublishPort newLocalPublishPort() {
        if (this.singlePublisher) {
            throw new UnsupportedOperationException();
        } else {
            if (this.pubSeqNM != null) {
                return new MyPublishPort_NM_Local(this);
            } else {
                return new MyPublishPort_M_MultiPub_Local(this);
            }
        }
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------

    /**
     * @param executor If null, creates a non-service ring buffer.
     * @param rejectedEventHandler Null if and only if executor is null.
     */
    protected UnicastRingBuffer(
            int bufferCapacity,
            int pubSeqNbrOfAtomicCounters,
            boolean singleSubscriber,
            boolean service,
            boolean readLazySets,
            boolean writeLazySets,
            final InterfaceCondilock readWaitCondilock,
            final InterfaceCondilock writeWaitCondilock,
            final Executor executor,
            final InterfaceRingBufferRejectedEventHandler rejectedEventHandler) {
        super(
                false, // multicast
                service,
                //
                bufferCapacity,
                (pubSeqNbrOfAtomicCounters == 0), // singlePublisher
                singleSubscriber,
                readLazySets,
                writeLazySets,
                readWaitCondilock,
                writeWaitCondilock,
                executor,
                rejectedEventHandler);
        if ((pubSeqNbrOfAtomicCounters != 0)
                && (!NumbersUtils.isPowerOfTwo(pubSeqNbrOfAtomicCounters))) {
            throw new IllegalArgumentException("number of atomic counters ["+pubSeqNbrOfAtomicCounters+"] must be 0 or a power of two");
        }

        // Must not have more atomic counters than entries,
        // else might block.
        if (pubSeqNbrOfAtomicCounters > bufferCapacity) {
            throw new IllegalArgumentException("number of atomic counters ["+pubSeqNbrOfAtomicCounters+"] must be <= buffer capacity ["+bufferCapacity+"]");
        }

        // Need to create this array before publish ports.
        this.entries = new PostPaddedAtomicLong[this.getBufferCapacity()];

        /*
         * Taking care to create ports last, in case they make
         * use of class fields.
         */

        if (this.singlePublisher) {
            this.pubSeqM = null;
            this.pubSeqNM = null;
            this.mainPublishPort = new MyPublishPort_M_SinglePub(this);
        } else {
            if (pubSeqNbrOfAtomicCounters == 1) {
                this.pubSeqM = new PostPaddedAtomicLong(INITIAL_SEQUENCE);
                this.pubSeqNM = null;
                this.mainPublishPort = new MyPublishPort_M_MultiPub_Main(this);
            } else {
                this.pubSeqM = null;
                this.pubSeqNM = new LongCounter(
                        INITIAL_SEQUENCE,
                        pubSeqNbrOfAtomicCounters);
                this.mainPublishPort = new MyPublishPort_NM_Main(this);
            }
        }

        for (int i=0;i<this.entries.length;i++) {
            PostPaddedAtomicLong entry = new PostPaddedAtomicLong(seqStat(i,ES_WRITABLE));
            this.entries[i] = entry;
        }
    }

    /*
     * 
     */

    @Override
    protected void signalSubscribers() {
        this.readWaitCondilock.signalAllInLock();
        this.writeWaitCondilock.signalAllInLock();
    }

    /*
     * Implementations of service-specific methods.
     */

    protected void shutdownImpl() {
        // Using main lock to ensure consistency between permission
        // checks and actual shutdown, in particular so that we checked
        // permissions for all eventually subsequently interrupted threads.
        final boolean stateChanged;
        boolean neverRunning = false;
        mainLock.lock();
        try {
            checkShutdownAccessIfNeeded(false);
            // Using CASes, which would update state properly (order matters) even if used concurrently.
            stateChanged =
                    this.ringBufferState.compareAndSet(STATE_RUNNING, STATE_TERMINATE_WHEN_IDLE)
                    || (neverRunning = this.ringBufferState.compareAndSet(STATE_PENDING, STATE_TERMINATE_WHEN_IDLE));
            if (stateChanged) {
                // Signaling everyone, doesn't hurt.
                this.signalAllInLockWriteRead();
            }
        } finally {
            mainLock.unlock();
        }

        if (stateChanged) {
            terminateIfNeeded(neverRunning);
        }
    }

    /**
     * @param interruptIfPossible If true, attempts to interrupt running workers.
     */
    protected long[] shutdownNowImpl(boolean interruptIfPossible) {
        // Using main mutex to ensure consistency between permission
        // checks and actual shutdown, in particular so that we checked
        // permissions for all eventually subsequently interrupted threads.
        final boolean stateChanged;
        final boolean wasShutdownUnused;
        boolean neverRunning = false;
        mainLock.lock();
        try {
            // Always possible.
            final boolean interruption = interruptIfPossible;
            checkShutdownAccessIfNeeded(interruption);
            // Using CASes, which would update state properly (order matters) even if used concurrently.
            stateChanged =
                    (wasShutdownUnused = this.ringBufferState.compareAndSet(STATE_TERMINATE_WHEN_IDLE, STATE_TERMINATE_ASAP))
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

        final long[] rejectedSequencesRanges;
        if (stateChanged) {
            if (neverRunning) {
                // State check for rejection on publish is done after eventual state set to RUNNING,
                // so if we never went running, any event will necessarily have been rejected on publish.
                rejectedSequencesRanges = null;
            } else {
                rejectedSequencesRanges = this.computeRejectionsOnShutdown();
            }
            terminateIfNeeded(neverRunning);
        } else {
            rejectedSequencesRanges = null;
        }

        return rejectedSequencesRanges;
    }

    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE METHODS
    //--------------------------------------------------------------------------

    @Override
    MyAbstractWorker newWorkerRaw(
            final InterfaceRingBufferSubscriber subscriber,
            final InterfaceRingBufferWorker... aheadWorkers) {
        if(ASSERTIONS)assert(aheadWorkers.length == 0);
        MyUnicastWorker worker;
        if (this.service) {
            worker = new MyUnicastWorker(
                    this,
                    this.ringBufferState,
                    subscriber);
        } else {
            worker = new MyUnicastWorker(
                    this,
                    new PostPaddedAtomicInteger(STATE_RUNNING),
                    subscriber);
        }
        return worker;
    }

    /*
     * for debug
     */

    String toStringState() {
        final StringBuilder sb = new StringBuilder();
        long sequenceM2 = Long.MIN_VALUE;
        long sequenceM1 = Long.MIN_VALUE;
        int statusM1 = Integer.MIN_VALUE;
        boolean didLogM2 = false;
        boolean didLogM1 = false;
        for (int i=0;i<entries.length;i++) {
            PostPaddedAtomicLong e = entries[i];
            long ss = e.get();

            long sequence = sequence(ss);
            int status = status(ss);
            boolean didLogCurrent = false;

            if ((status != statusM1) || (sequence != sequenceM1+1)) {
                // First entry, or status change, or sequence discontinuity.
                if ((!didLogM1) && (sequenceM1 >= INITIAL_SEQUENCE)) {
                    if ((!didLogM2) && (sequenceM2 >= INITIAL_SEQUENCE)) {
                        sb.append("(...)");
                        sb.append(LangUtils.LINE_SEPARATOR);
                    }
                    sb.append("entries[");
                    sb.append(i-1);
                    sb.append("] = (");
                    sb.append(sequenceM1);
                    sb.append(",");
                    sb.append(statusM1);
                    sb.append(")");
                    sb.append(LangUtils.LINE_SEPARATOR);
                }
                sb.append("entries[");
                sb.append(i);
                sb.append("] = (");
                sb.append(sequence);
                sb.append(",");
                sb.append(status);
                sb.append(")");
                sb.append(LangUtils.LINE_SEPARATOR);
                didLogCurrent = true;
            }
            sequenceM2 = sequenceM1;
            sequenceM1 = sequence;
            statusM1 = status;
            didLogM2 = didLogM1;
            didLogM1 = didLogCurrent;
        }
        if ((!didLogM1) && (sequenceM1 >= INITIAL_SEQUENCE)) {
            if ((!didLogM2) && (sequenceM2 >= INITIAL_SEQUENCE)) {
                sb.append("(...)");
                sb.append(LangUtils.LINE_SEPARATOR);
            }
            sb.append("entries[");
            sb.append(entries.length-1);
            sb.append("] = (");
            sb.append(sequenceM1);
            sb.append(",");
            sb.append(statusM1);
            sb.append(")");
            sb.append(LangUtils.LINE_SEPARATOR);
        }
        if (pubSeqNM != null) {
            sb.append("pubSeq = ");
            sb.append(pubSeqNM.toString());
            sb.append(LangUtils.LINE_SEPARATOR);
        }
        if (pubSeqM != null) {
            sb.append("pubSeq = ");
            sb.append(pubSeqM.toString());
            sb.append(LangUtils.LINE_SEPARATOR);
        }
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
        if(ASSERTIONS)assert(this.service);
        this.mainLock.lock();
        try {
            if (this.service) {
                if (this.workers.size() == 0) {
                    throw new IllegalStateException("starting before worker(s) creation");
                }
            } else {
                // Allowing set to RUNNING state before worker(s) creation.
            }
            if (this.ringBufferState.get() == STATE_PENDING) {
                if (this.service) {
                    setNbrOfStartedWorkersAndExecuteWorkers_mainLockLocked();
                }
                beforeRunning();
                // No need to set this for non-services ring buffers,
                // since we only check it when publishing initial sequence,
                // but it shouldn't hurt to set it.
                this.ringBufferState.set(STATE_RUNNING);
            }
        } finally {
            this.mainLock.unlock();
        }
    }
    
    /*
     * 
     */
    
    private void setWritableAndSignalAll_afterRead(
            PostPaddedAtomicLong entry,
            long newSequence) {
        if (this.readLazySets) {
            entry.lazySet(seqStat(newSequence, ES_WRITABLE));
        } else {
            entry.set(seqStat(newSequence, ES_WRITABLE));
        }
        this.readWaitCondilock.signalAllInLock();
    }

    private void setWritableAndSignalAll_forJumpUp(
            PostPaddedAtomicLong entry,
            long newSequence) {
        // Taking care of both laziness settings and signaling both condilocks,
        // to make jump-up semantically equivalent to a write and then a read.
        if (this.writeLazySets && this.readLazySets) {
            entry.lazySet(seqStat(newSequence, ES_WRITABLE));
        } else {
            entry.set(seqStat(newSequence, ES_WRITABLE));
        }
        this.signalAllInLockWriteRead();
    }

    /**
     * Only relevant in case of non-monotonic claiming.
     * 
     * @return True if did jump-up entry, false otherwise.
     */
    private boolean tryToJumpUp(
            PostPaddedAtomicLong entry,
            long sequenceFrom) {
        final boolean acquired = this.pubSeqNM.isCurrentAndCompareAndIncrement(sequenceFrom);
        if (acquired) {
            // Jumping-it-up.
            final long sequenceTo = sequenceFrom+this.entries.length;
            this.setWritableAndSignalAll_forJumpUp(
                    entry,
                    sequenceTo);
        }
        return acquired;
    }

    private long[] computeRejectionsOnShutdown() {
        /*
         * Here, we might reject a sequence from the middle of a batch publishing,
         * and not finish rejections for the batch if it wraps past buffer's end.
         * That's why we need interface for rejection on publish to allow rejection
         * of multiple sequence ranges (reverse-looping, or looping multiple times,
         * is not a solution), so that the publisher thread can reject remaining
         * sequences. As a result, this also allows to make use of this interface
         * for rejections on shutdown, which commonly correspond to multiple
         * sequences ranges in case of concurrent usage.
         */
        SequenceRangeVector sequencesRangesVector = null;
        for (int i=0;i<this.entries.length;i++) {
            final PostPaddedAtomicLong entry = this.entries[i];
            final long rejectedSequence = tryToRejectIfReadable(entry);
            if (rejectedSequence != Long.MIN_VALUE) {
                if (sequencesRangesVector == null) {
                    sequencesRangesVector = new SequenceRangeVector();
                }
                sequencesRangesVector.add(rejectedSequence, rejectedSequence);
            }
        }
        final long[] sequencesRangesArray;
        if (sequencesRangesVector != null) {
            if(ASSERTIONS)assert(sequencesRangesVector.getNbrOfRanges() != 0);
            sequencesRangesArray = sequencesRangesVector.sortAndMergeAndToArray();
        } else {
            sequencesRangesArray = null;
        }
        return sequencesRangesArray;
    }

    /**
     * @return Rejected sequence, else Long.MIN_VALUE.
     */
    private long tryToRejectIfReadable(PostPaddedAtomicLong entry) {
        final long seqStat = entry.get();
        return tryToRejectReadableEvent(seqStat, seqStatWithNewStatus(seqStat,ES_READABLE), entry);
    }

    /**
     * seqStat provided (fresh value), not to retrieve it uselessly.
     * 
     * @return Rejected sequence, else Long.MIN_VALUE.
     */
    private long tryToRejectReadableEvent(
            long seqStat,
            long seqStatToReject,
            PostPaddedAtomicLong entry) {
        if (seqStat == seqStatToReject) {
            return tryToRejectReadableEvent(seqStatToReject, entry);
        } else {
            return Long.MIN_VALUE;
        }
    }

    /**
     * seqStat provided (fresh value), not to retrieve it uselessly.
     * 
     * @return Rejected sequence, else Long.MIN_VALUE.
     */
    private long tryToRejectReadableEvent(
            long seqStatToReject,
            PostPaddedAtomicLong entry) {
        if(ASSERTIONS)assert(status(seqStatToReject) == ES_READABLE);
        if (entry.compareAndSet(seqStatToReject, seqStatWithNewStatus(seqStatToReject,ES_REJECTED))) {
            this.readWaitCondilock.signalAllInLock();
            return sequence(seqStatToReject);
        } else {
            return Long.MIN_VALUE;
        }
    }

    /**
     * @param seqStat Entry's seqStat.
     */
    private void rejectWritableEvent(
            long seqStat,
            PostPaddedAtomicLong entry) {
        // Can only reject safely without CAS if entry is writable (and if we did acquire its sequence).
        if(ASSERTIONS)assert((entry.get() == seqStat) && (status(seqStat) == ES_WRITABLE));
        final long newSeqStat = seqStatWithNewStatus(seqStat, ES_REJECTED);
        if (this.writeLazySets) {
            entry.lazySet(newSeqStat);
        } else {
            entry.set(newSeqStat);
        }
        this.writeWaitCondilock.signalAllInLock();
    }
}
