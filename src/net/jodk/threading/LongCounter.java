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
package net.jodk.threading;

import net.jodk.lang.NumbersUtils;

/**
 *   A counter that should scale better than AtomicLong.getAndIncrement(),
 * but which two main methods don't always provide monotonic results when
 * being used concurrently:
 * - getAndIncrement() is not monotonic across threads nor even for
 *   a same thread.
 * - getAndIncrementMonotonic(hole_listener) is not monotonic across
 *   threads, but is for a same thread.
 * 
 *   AtomicLong.getAndIncrement() provides a unique value for each call
 * (as long as wrapping doesn't occur), but it also ensures that this value,
 * at the time it is computed (when the CAS succeeds), is superior to any
 * previously returned value, among all threads.
 *   Though, in case of concurrency, and depending on usage, this last property
 * can be superfluous, because once a thread retrieved a value, it might pause
 * and actually use it AFTER another thread would use it's subsequently retrieved
 * and superior value.
 *   This class is designed to take advantage of not ensuring this last property.
 */
public class LongCounter {

    /*
     * Not providing incrementAndGetXXX methods, because
     * that would make holes handling more complicated
     * (if relying on getAndIncrementXXX methods, would
     * need to notify hole+1 instead).
     */

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    /**
     * 0 for no padding cell, 1 for padding cell.
     */
    private static final int PRE_PADDING_CELL = 1;

    /**
     * More than twice the number of possibly concurrent threads,
     * else it might be too tight.
     */
    private static final int DEFAULT_NBR_OF_CELLS = NumbersUtils.ceilingPowerOfTwo(
            Math.min(
                    (1<<30),
                    NumbersUtils.timesBounded(
                            2,
                            Runtime.getRuntime().availableProcessors())));

    //--------------------------------------------------------------------------
    // PUBLIC CLASSES
    //--------------------------------------------------------------------------

    public interface InterfaceHoleListener {
        /**
         * The counter must not be modified in this method.
         * @param hole Counter value that will never be returned.
         */
        public void onHole(long hole);
    }

    /**
     * This class is public to allow for reduced use of ThreadLocal:
     * - by using a non-concurrently-used instance-local LocalData,
     * - or by wrapping it and other data inside a same thread-local object,
     * - or by retrieving a thread-local instance once for multiple
     *   methods calls.
     * 
     * Non-thread-safe data for use with some getXXX methods,
     * as thread-local or at least non-concurrently-used.
     * 
     * Using increment methods with a same instance of this class,
     * from a same thread, ensure that this thread, in the long run,
     * cycles through all internal cells.
     */
    public static class LocalData {
        private int previousIndex = -1;
        private long maxPreviousValue;
        private LocalData(long initialValue) {
            this.maxPreviousValue = initialValue;
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private final long initialValue;

    /**
     * Length power of two, for modulo by mask.
     */
    private final PostPaddedAtomicLong[] cells;

    /**
     * Not counting eventual padding cell.
     */
    private final int nbrOfCells;
    private final int indexMask;

    /**
     * Each thread local instance is also specific to this counter,
     * to make sure that each thread cyclically uses all indexes
     * of a same counter equally. Else, it could make use of only
     * one cell of each counter, and make it increase way ahead
     * others, without chance to get aware of holes.
     */
    private final ThreadLocal<LocalData> defaultThreadLocalData;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @param initialValue Initial value. Must be >= 0.
     * @param nbrOfCells Number of atomic counters (cells) that can be
     *        incremented in parallel. Must be a power of two.
     *        Can help to have this value larger (like twice)
     *        than actual parallelism.
     */
    public LongCounter(
            final long initialValue,
            final int nbrOfCells) {
        if (initialValue < 0) {
            throw new IllegalArgumentException("initial value ["+initialValue+"] must be >= 0");
        }
        // Allowing to have only 1 cell, even though it's not appropriate to use
        // this class in that case (a simple padded atomic long would be more efficient).
        if (!NumbersUtils.isPowerOfTwo(nbrOfCells)) {
            throw new IllegalArgumentException("number of cells ["+nbrOfCells+"] must be a power of two");
        }
        this.initialValue = initialValue;

        this.cells = new PostPaddedAtomicLong[nbrOfCells+PRE_PADDING_CELL];
        for (int i=0;i<this.cells.length;i++) {
            this.cells[i] = new PostPaddedAtomicLong(initialValue + i - PRE_PADDING_CELL);
        }

        this.nbrOfCells = nbrOfCells;
        this.indexMask = nbrOfCells-1;
        this.defaultThreadLocalData = new ThreadLocal<LocalData>() {
            @Override
            public LocalData initialValue() {
                return new LocalData(initialValue);
            }
        };
    }

    /**
     * Creates a counter with a default number of cells.
     * 
     * @param initialValue Initial value.
     */
    public LongCounter(long initialValue) {
        this(
                initialValue,
                DEFAULT_NBR_OF_CELLS);
    }

    /**
     * Creates a counter with a default number of cells
     * and initial value 0.
     */
    public LongCounter() {
        this(0L);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean skipFirstCell = (PRE_PADDING_CELL != 0);
        boolean skipComma = true;
        for (PostPaddedAtomicLong cell : this.cells) {
            if (skipFirstCell) {
                skipFirstCell = false;
                continue;
            }
            if (skipComma) {
                skipComma = false;
            } else {
                sb.append(",");
            }
            sb.append(cell.get());
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * @return A new LocalData instance.
     */
    public LocalData newLocalData() {
        return new LocalData(this.initialValue);
    }

    /*
     * misc
     */

    /**
     * @return The number of cells, which is a power of two.
     */
    public int getNbrOfCells() {
        return this.nbrOfCells;
    }

    /**
     * @param value A value.
     * @return Index of the cell that could contain the specified value.
     */
    public int getCellIndex(long value) {
        return (int)(value-this.initialValue) & this.indexMask;
    }

    /**
     * @param value A value.
     * @return Value for cell corresponding to the specified value.
     */
    public long getCellValue(long value) {
        final int index = this.getCellIndex(value);
        final PostPaddedAtomicLong cell = this.cells[index+PRE_PADDING_CELL];
        return cell.get();
    }

    /**
     * Equivalent to (value == getCellValue(value)).
     * 
     * A value being current does not mean that a subsequent call to get()
     * will return it. get() returns a current value, but always the
     * one of thread's current cell.
     * 
     * @param value A value.
     * @return True if the specified value is the current value of a cell,
     *         false otherwise.
     */
    public boolean isCurrent(long value) {
        final int index = this.getCellIndex(value);
        final PostPaddedAtomicLong cell = this.cells[index+PRE_PADDING_CELL];
        return value == cell.get();
    }

    /**
     * Equivalent to (value >= getCellValue(value)).
     * 
     * @param value A value.
     * @return True if the specified value can be returned
     *         or notified as hole, false otherwise.
     */
    public boolean isAvailable(long value) {
        final int index = this.getCellIndex(value);
        final PostPaddedAtomicLong cell = this.cells[index+PRE_PADDING_CELL];
        return value >= cell.get();
    }

    /**
     * @return The number of times this counter has been incremented,
     *         i.e. the number of times an increment method
     *         has been called, or a hole occurred.
     */
    public long getNbrOfIncrementations() {
        final int bitsShift = NumbersUtils.bitSizeForUnsignedValue(this.indexMask);
        long nbr = 0;
        final PostPaddedAtomicLong[] cells = this.cells;
        for (int i=0;i<this.nbrOfCells;i++) {
            final PostPaddedAtomicLong cell = cells[i+PRE_PADDING_CELL];
            final long cellValue = cell.get();
            final long initialCellValue = this.initialValue + i;
            nbr += (cellValue - initialCellValue)>>bitsShift;
        }
        return nbr;
    }

    /**
     * If used concurrently with increment methods,
     * result might miss to take related modifications into account.
     * 
     * @return The lowest value that can be returned by this counter
     *         by the next call to a getAndIncrementXXX method.
     */
    public long getMinCellValue() {
        long min = Long.MAX_VALUE;
        final PostPaddedAtomicLong[] cells = this.cells;
        for (int i=PRE_PADDING_CELL;i<cells.length;i++) {
            long value = cells[i].get();
            if (value < min) {
                min = value;
            }
        }
        return min;
    }

    /**
     * If used concurrently with increment methods,
     * result might miss to take related modifications into account.
     * 
     * @return The highest value that can be returned by this counter
     *         by the next call to a getAndIncrementXXX method.
     */
    public long getMaxCellValue() {
        long max = Long.MIN_VALUE;
        final PostPaddedAtomicLong[] cells = this.cells;
        for (int i=PRE_PADDING_CELL;i<cells.length;i++) {
            long value = cells[i].get();
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    /**
     * If used concurrently with increment methods,
     * result might miss to take related modifications into account.
     * 
     * @return Max value returned by a getAndIncrementXXX method,
     *         or a value < initial value if no value has been returned yet.
     */
    public long getMaxGot() {
        return this.getMaxCellValue() - this.nbrOfCells;
    }

    /**
     * If used concurrently with increment methods,
     * result might miss to take related modifications into account.
     * 
     * Semantically equivalent to "value < getMaxCellValue()",
     * but should be half faster on average, not necessarily
     * iterating on all cells.
     * 
     * @return True if the specified value is strictly lower than max call value,
     *         false otherwise.
     */
    public boolean isLowerThanMaxCellValue(long value) {
        final PostPaddedAtomicLong[] cells = this.cells;
        for (int i=PRE_PADDING_CELL;i<cells.length;i++) {
            if (value < cells[i].get()) {
                return true;
            }
        }
        return false;
    }

    /**
     * If used concurrently with increment methods,
     * result might miss to take related modifications into account.
     * 
     * Semantically equivalent to "value < getMaxGot()",
     * but should be half faster on average, not necessarily
     * iterating on all cells.
     * 
     * If this counter has not been incremented yet, returns
     * true only if the specified value is strictly inferior
     * to initial value minus one, because initial value minus
     * one is considered having being returned (being max cell value
     * minus the number of cells).
     * 
     * @return True if the specified value is strictly lower than max value
     *         returned by a getAndIncrementXXX method, false otherwise.
     */
    public boolean isLowerThanMaxGot(long value) {
        final long cellValueAfterValueGot = value + this.nbrOfCells;
        return this.isLowerThanMaxCellValue(cellValueAfterValueGot);
    }

    /**
     * Could also be called incrementIfCurrent.
     * 
     * @param value Value to increment.
     * @return True if the specified value was current and was replaced
     *         with next value of its cell, false otherwise.
     */
    public boolean compareAndIncrement(long value) {
        final int index = this.getCellIndex(value);
        final PostPaddedAtomicLong cell = this.cells[index+PRE_PADDING_CELL];
        return cell.compareAndSet(value, value + this.nbrOfCells);
    }

    /**
     * Checks that value is current before attempting CAS.
     * 
     * Could also be called isCurrentAndIncrementIfCurrent.
     * 
     * @param value Value to increment.
     * @return True if the specified value was current and was replaced
     *         with next value of its cell, false otherwise.
     */
    public boolean isCurrentAndCompareAndIncrement(long value) {
        final int index = this.getCellIndex(value);
        final PostPaddedAtomicLong cell = this.cells[index+PRE_PADDING_CELL];
        if (cell.get() == value) {
            return cell.compareAndSet(value, value + this.nbrOfCells);
        }
        return false;
    }

    /**
     * Could also be called weakIncrementIfCurrent.
     * 
     * Can fail spuriously, and has no ordering guarantees.
     * 
     * @param value Value to increment.
     * @return True if the specified value was current and was replaced
     *         with next value of its cell, false otherwise.
     */
    public boolean weakCompareAndIncrement(long value) {
        final int index = this.getCellIndex(value);
        final PostPaddedAtomicLong cell = this.cells[index+PRE_PADDING_CELL];
        return cell.weakCompareAndSet(value, value + this.nbrOfCells);
    }

    /**
     * Checks that value is current before attempting CAS.
     * 
     * Could also be called isCurrentAndWeakIncrementIfCurrent.
     * 
     * Can fail spuriously, and has no ordering guarantees.
     * 
     * @param value Value to increment.
     * @return True if the specified value was current and was replaced
     *         with next value of its cell, false otherwise.
     */
    public boolean isCurrentAndWeakCompareAndIncrement(long value) {
        final int index = this.getCellIndex(value);
        final PostPaddedAtomicLong cell = this.cells[index+PRE_PADDING_CELL];
        if (cell.get() == value) {
            return cell.weakCompareAndSet(value, value + this.nbrOfCells);
        }
        return false;
    }

    /*
     * methods using LocalData
     */

    /**
     * Equivalent to get(LocalData,int)
     * using default thread-local data.
     * 
     * @param nbrOfCellsToSkip Number of cells to skip,
     *        when changing cell index.
     * @return The value of the cell at previous index of
     *         default thread-local LocalData.
     */
    public long get(int nbrOfCellsToSkip) {
        return this.get(
                this.defaultThreadLocalData.get(),
                nbrOfCellsToSkip);
    }

    /**
     * This method changes the specified LocalData
     * cell index each time it is called.
     * 
     * This method can typically be used along with
     * compareAndIncrement(long), as follows:
     * 
     * int nbrOfFailedCAS = 0;
     * while (doGetValues) {
     *    // peeking some value, changing cell each time
     *    // (nbrOfFailedCAS+1 == nbrOfCells)
     *    long value = counter.get(localData,nbrOfFailedCAS);
     *    if (valueIsDesirable) {
     *       if (counter.compareAndIncrement(value)) {
     *          // got it
     *          nbrOfFailedCAS = 0;
     *          // do something with value
     *       } else {
     *          ++nbrOfFailedCAS;
     *       }
     *    }
     * }
     * 
     * In the example above, using the number of
     * failed CAS for nbrOfCellsToSkip parameter,
     * which might help to get away from contention.
     * If you have no clue you can also use 0 for
     * this parameter.
     * 
     * @param localData LocalData to use.
     * @param nbrOfCellsToSkip Number of cells to skip,
     *        when changing cell index.
     * @return The value of the cell at previous index of
     *         the specified LocalData.
     */
    public long get(
            LocalData localData,
            int nbrOfCellsToSkip) {
        final int index = this.nextIndex(localData.previousIndex+nbrOfCellsToSkip);
        localData.previousIndex = index;
        return this.cells[index+PRE_PADDING_CELL].get();
    }

    /**
     * Equivalent to getAndIncrement(LocalData)
     * using default thread-local data.
     * 
     * Not monotonic across threads nor even for a same thread.
     */
    public long getAndIncrement() {
        return this.getAndIncrement(this.defaultThreadLocalData.get());
    }

    /**
     * Not monotonic across threads nor even for a same thread.
     * 
     * @param localData LocalData to use.
     */
    public long getAndIncrement(LocalData localData) {
        final int jump = this.nbrOfCells;
        final int indexMask = this.indexMask;
        int index = this.nextIndex(localData.previousIndex);
        long value;
        final PostPaddedAtomicLong[] cells = this.cells;
        int nbrOfFailedCAS = 0;
        PostPaddedAtomicLong cell;
        while (true) {
            cell = cells[index+PRE_PADDING_CELL];
            value = cell.get();
            if (cell.compareAndSet(value, value + jump)) {
                break;
            } else {
                // Will try another cell.
                // The more we failed to CAS, the further
                // we pick next cell, to get away from contention.
                index = (index+(++nbrOfFailedCAS)) & indexMask;
            }
        }
        localData.previousIndex = index;
        // Setting max previous value if this one is higher, to ensure
        // cross monotonicity if thread-or-instance local's user
        // switches to using monotonic method.
        if (value > localData.maxPreviousValue) {
            localData.maxPreviousValue = value;
        }
        return value;
    }

    /**
     * Equivalent to weakGetAndIncrement(LocalData)
     * using default thread-local data.
     * 
     * Has memory semantics of a volatile read
     * (uses volatile read and weak CAS).
     * 
     * Not monotonic across threads nor even for a same thread.
     */
    public long weakGetAndIncrement() {
        return this.weakGetAndIncrement(this.defaultThreadLocalData.get());
    }

    /**
     * Not monotonic across threads nor even for a same thread.
     * 
     * Has memory semantics of a volatile read
     * (uses volatile read and weak CAS).
     * 
     * @param localData LocalData to use.
     */
    public long weakGetAndIncrement(LocalData localData) {
        final int jump = this.nbrOfCells;
        final int indexMask = this.indexMask;
        int index = this.nextIndex(localData.previousIndex);
        long value;
        final PostPaddedAtomicLong[] cells = this.cells;
        int nbrOfFailedCAS = 0;
        PostPaddedAtomicLong cell;
        while (true) {
            cell = cells[index+PRE_PADDING_CELL];
            value = cell.get();
            // Using weak CAS: if it fails spuriously,
            // the cell can still be incremented later.
            if (cell.weakCompareAndSet(value, value + jump)) {
                break;
            } else {
                index = (index+(++nbrOfFailedCAS)) & indexMask;
            }
        }
        localData.previousIndex = index;
        if (value > localData.maxPreviousValue) {
            localData.maxPreviousValue = value;
        }
        return value;
    }

    /**
     * Equivalent to getAndIncrementMonotonic(LocalData,InterfaceHoleListener)
     * using default thread-local data.
     * 
     * Monotonic only for a same thread.
     * 
     * @param holeListener Listener for values that were discarded to avoid non-monotonicity,
     *        and that will never have been returned by a getXXX method.
     */
    public long getAndIncrementMonotonic(final InterfaceHoleListener holeListener) {
        return this.getAndIncrementMonotonic(
                this.defaultThreadLocalData.get(),
                holeListener);
    }

    /**
     * Monotonic only for a same instance of LocalData.
     * 
     * @param localData LocalData to use.
     * @param holeListener Listener for values that were discarded to avoid non-monotonicity,
     *        and that will never have been returned by a getXXX method.
     */
    public long getAndIncrementMonotonic(
            LocalData localData,
            final InterfaceHoleListener holeListener) {
        final int jump = this.nbrOfCells;
        final int indexMask = this.indexMask;
        final long previousValue = localData.maxPreviousValue;
        int index = this.nextIndex(localData.previousIndex);
        int nbrOfFailedCAS = 0;
        long value;
        final PostPaddedAtomicLong[] cells = this.cells;
        PostPaddedAtomicLong cell = cells[index+PRE_PADDING_CELL];
        while (true) {
            value = cell.get();
            if (cell.compareAndSet(value, value + jump)) {
                if (value < previousValue) {
                    // Just considering this value is lost
                    // (it costs too much to look for a value
                    // superior to previously returned one,
                    // before doing CAS on it).
                    if (holeListener != null) {
                        holeListener.onHole(value);
                    }
                    // Will use the same cell next time:
                    // it might still be in cache, and that way
                    // we make it catch up, lowering risks
                    // of having some counters way behind others.
                } else {
                    localData.previousIndex = index;
                    localData.maxPreviousValue = value;
                    return value;
                }
            } else {
                // Will try another cell.
                // The more we failed to CAS, the further
                // we pick next cell, to get away from contention.
                index = (index+(++nbrOfFailedCAS)) & indexMask;
                cell = cells[index+PRE_PADDING_CELL];
            }
        }
    }

    /**
     * Equivalent to weakGetAndIncrementMonotonic(LocalData,InterfaceHoleListener)
     * using default thread-local data.
     * 
     * Has memory semantics of a volatile read
     * (uses volatile read and weak CAS).
     * 
     * Monotonic only for a same thread.
     * 
     * @param holeListener Listener for values that were discarded to avoid non-monotonicity,
     *        and that will never have been returned by a getXXX method.
     */
    public long weakGetAndIncrementMonotonic(final InterfaceHoleListener holeListener) {
        return this.getAndIncrementMonotonic(
                this.defaultThreadLocalData.get(),
                holeListener);
    }

    /**
     * Monotonic only for a same instance of LocalData.
     * 
     * Has memory semantics of a volatile read
     * (uses volatile read and weak CAS).
     * 
     * @param localData LocalData to use.
     * @param holeListener Listener for values that were discarded to avoid non-monotonicity,
     *        and that will never have been returned by a getXXX method.
     */
    public long weakGetAndIncrementMonotonic(
            LocalData localData,
            final InterfaceHoleListener holeListener) {
        final int jump = this.nbrOfCells;
        final int indexMask = this.indexMask;
        final long previousValue = localData.maxPreviousValue;
        int index = this.nextIndex(localData.previousIndex);
        int nbrOfFailedCAS = 0;
        long value;
        final PostPaddedAtomicLong[] cells = this.cells;
        PostPaddedAtomicLong cell = cells[index+PRE_PADDING_CELL];
        while (true) {
            value = cell.get();
            // Using weak CAS: if it fails spuriously,
            // the cell can still be incremented later.
            if (cell.weakCompareAndSet(value, value + jump)) {
                if (value < previousValue) {
                    if (holeListener != null) {
                        holeListener.onHole(value);
                    }
                } else {
                    localData.previousIndex = index;
                    localData.maxPreviousValue = value;
                    return value;
                }
            } else {
                index = (index+(++nbrOfFailedCAS)) & indexMask;
                cell = cells[index+PRE_PADDING_CELL];
            }
        }
    }

    /*
     * removal
     */

    /**
     * Removes available values <= the specified value.
     * 
     * @param max A value.
     * @param holeListener Listener to which eaten values
     *        (holes) must be notified to.
     */
    public void removeUpTo(
            long max,
            final InterfaceHoleListener holeListener) {
        long value;
        final long jump = this.nbrOfCells;
        final PostPaddedAtomicLong[] cells = this.cells;
        for (int i=PRE_PADDING_CELL;i<cells.length;i++) {
            final PostPaddedAtomicLong cell = cells[i];
            while ((value = cell.get()) <= max) {
                if (cell.compareAndSet(value, value + jump)) {
                    if (holeListener != null) {
                        holeListener.onHole(value);
                    }
                }
            }
        }
    }

    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * For test.
     * Makes sure cell values are not monotonically
     * incrementing, to simulate a possible result
     * of concurrent usage.
     */
    void messUpCells() {
        final PostPaddedAtomicLong[] cells = this.cells;
        for (int i=PRE_PADDING_CELL;i<cells.length;i++) {
            // Changes each round.
            final int value_m1_1 = (((i-PRE_PADDING_CELL)&1)<<1)-1;
            final int jumpFactor = value_m1_1 * (i-PRE_PADDING_CELL);
            this.cells[i].set(i-PRE_PADDING_CELL + jumpFactor * this.nbrOfCells);
        }
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private int nextIndex(int index) {
        return (index+1) & this.indexMask;
    }
}
