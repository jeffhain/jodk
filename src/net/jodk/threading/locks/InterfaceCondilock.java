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

import java.util.concurrent.locks.Condition;

import net.jodk.lang.InterfaceBooleanCondition;

/**
 * Interface for conditions that provide a way to run treatments in their lock.
 */
public interface InterfaceCondilock extends Condition, InterfaceLocker {
    
    /*
     * Reason for awaitXXXWhileFalseInLockXXX methods not to check interruption
     * status if boolean condition is first evaluated as true:
     * If the user does not want the cost of an interruption status check
     * when the boolean condition is true, he would need to check the
     * boolean condition himself first, which would always lead to a
     * double evaluation if it's false (with interruption check in between).
     * With current design, if the user wants interruption status check,
     * he can still do it himself before calling the method.
     */
    
    /**
     * @return Time, in nanoseconds, used for timeouts measurement.
     */
    public long timeoutTimeNS();

    /**
     * @return Time, in nanoseconds, used for deadlines measurement.
     */
    public long deadlineTimeNS();

    /*
     * convenience methods
     */

    /**
     * Is roughly equivalent to calling awaitNanos(long) with corresponding timeout,
     * but typically involves less calls to timing method, especially if end timeout
     * time is already known.
     * 
     * @param endTimeoutTimeNS Timeout time to wait for, in nanoseconds.
     *        This is not a timeout, but a time compared to timeoutTimeNS(),
     *        to compute the timeout to wait.
     * @throws InterruptedException if current thread is interrupted.
     */
    public void awaitUntilNanosTimeoutTime(long endTimeoutTimeNS) throws InterruptedException;

    /**
     * Method to wait until a deadline, which does not require to provide a Date object
     * (unlike awaitUntil(Date)).
     * 
     * @param deadlineNS Deadline to wait for, in nanoseconds.
     * @throws InterruptedException if current thread is interrupted.
     */
    public boolean awaitUntilNanos(long deadlineNS) throws InterruptedException;

    /**
     * Method to wait for a boolean condition to be true, uninterruptibly.
     * 
     * If interruption of current thread is detected while waiting,
     * interruption status is restored before returning.
     * 
     * @param booleanCondition Condition waited to be true (waiting while it is false).
     */
    public void awaitWhileFalseInLockUninterruptibly(final InterfaceBooleanCondition booleanCondition);

    /**
     * Method to wait for a boolean condition to be true,
     * or specified timeout to be elapsed.
     * 
     * If the specified condition is true when this method is called,
     * it returns true whatever the timeout.
     * Else, it is evaluated again in lock, before eventual wait(s).
     * 
     * @param booleanCondition Condition waited to be true (waiting while it is false).
     * @param timeoutNS Timeout, in nanoseconds, after which this method returns false.
     * @return True if the boolean condition was true before timeout elapsed, false otherwise.
     * @throws InterruptedException if current thread is interrupted, possibly unless the specified
     *         boolean condition is first evaluated as true (authorized for performances).
     */
    public boolean awaitNanosWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long timeoutNS) throws InterruptedException;

    /**
     * This method can be used instead of awaitNanosWhileFalseInLock(...),
     * and along with timeoutTimeNS(), to easily design multiple layers of waiting,
     * without having to compute remaining timeout in each of them.
     * This is useful when awaitUntilNanosWhileFalseInLock(...) can't be used,
     * for it is typically based on System.currentTimeMillis(), which is not
     * accurate, and might jump around.
     * 
     * Ex.: instead of:
     * public Train waitForTrain(long timeoutNS) {
     *    final long endTimeoutTimeNS = NumbersUtils.plusBounded(System.nanoTime(), timeoutNS);
     *    final TrainWaitStopBC booleanCondition = new TrainWaitStopBC();
     *    while (true) {
     *       Train train = getNextTrainToArrive();
     *       booleanCondition.configure(train);
     *       if (condilock.awaitNanosWhileFalseInLock(booleanCondition,timeoutNS)) {
     *          if (booleanCondition.trainArrived()) {
     *             // Got the train.
     *             return train;
     *          } else {
     *             // Wait stopped not because the train arrived,
     *             // but because another train has been added,
     *             // which might arrive earlier.
     *             // Need to compute remaining timeout.
     *             timeoutNS = endTimeoutTimeNS - System.nanoTime();
     *          }
     *       } else {
     *          // Timeout elapsed.
     *          return null;
     *       }
     *    }
     * }
     * one can do:
     * public Train waitForTrain(final long timeoutNS) {
     *    final long endTimeoutTimeNS = NumbersUtils.plusBounded(condilock.timeoutTimeNS(), timeoutNS);
     *    final TrainWaitStopBC booleanCondition = new TrainWaitStopBC();
     *    while (true) {
     *       Train train = getNextTrainToArrive();
     *       booleanCondition.configure(train);
     *       if (condilock.awaitUntilNanosTimeoutTimeWhileFalseInLock(booleanCondition,endTimeoutTimeNS)) {
     *          if (booleanCondition.trainArrived()) {
     *             return train;
     *          }
     *       } else {
     *          return null;
     *       }
     *    }
     * }
     * Note: instead of piling-up wait layers, one could also design
     * a complex boolean condition, but it could be slow and slow down
     * things when being called in lock.
     * 
     * If the specified condition is true when this method is called,
     * it returns true whatever the end timeout time.
     * Else, it is evaluated again in lock, before eventual wait(s).
     * 
     * @param booleanCondition Condition waited to be true (waiting while it is false).
     * @param endTimeoutTimeNS Timeout time, in nanoseconds, after which this method returns false.
     *        This is not a timeout, but a time compared to timeoutTimeNS(),
     *        to decide whether one can still wait or not.
     * @return True if the boolean condition was true before end timeout time occurred, false otherwise.
     * @throws InterruptedException if current thread is interrupted, possibly unless the specified
     *         boolean condition is first evaluated as true (authorized for performances).
     */
    public boolean awaitUntilNanosTimeoutTimeWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long endTimeoutTimeNS) throws InterruptedException;

    /**
     * Method to wait for a boolean condition to be true,
     * or specified deadline to occur.
     * 
     * If the specified condition is true when this method is called,
     * it returns true whatever the deadline.
     * Else, it is evaluated again in lock, before eventual wait(s).
     * 
     * @param booleanCondition Condition waited to be true (waiting while it is false).
     * @param deadlineNS Deadline, in nanoseconds, after which this method returns false.
     * @return True if the boolean condition was true before deadline occurred, false otherwise.
     * @throws InterruptedException if current thread is interrupted, possibly unless the specified
     *         boolean condition is first evaluated as true (authorized for performances).
     */
    public boolean awaitUntilNanosWhileFalseInLock(
            final InterfaceBooleanCondition booleanCondition,
            long deadlineNS) throws InterruptedException;

    /**
     * Calls signal() in lock.
     */
    public void signalInLock();
    
    /**
     * Calls signalAll() in lock.
     */
    public void signalAllInLock();
}
