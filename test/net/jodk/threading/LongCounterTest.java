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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import net.jodk.lang.InterfaceFactory;
import net.jodk.lang.NumbersUtils;
import net.jodk.lang.Unchecked;
import net.jodk.threading.LongCounter;
import net.jodk.threading.LongCounter.InterfaceHoleListener;
import net.jodk.threading.LongCounter.LocalData;

import junit.framework.TestCase;

public class LongCounterTest extends TestCase {

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    /**
     * Data to use a counter from a same thread.
     */
    private static class MyThreadData implements InterfaceHoleListener {
        private final HashSet<Long> values = new HashSet<Long>();
        private final HashSet<Long> holes = new HashSet<Long>();
        @Override
        public void onHole(long hole) {
            this.holes.add(hole);
        }
        public int getNbrOfHoles() {
            return this.holes.size();
        }
    }

    private static class MyThreadDataFactory implements InterfaceFactory<MyThreadData> {
        private final ArrayList<MyThreadData> threadDataList = new ArrayList<MyThreadData>();
        @Override
        public MyThreadData newInstance() {
            final MyThreadData instance = new MyThreadData();
            threadDataList.add(instance);
            return instance;
        }
        public long getTotalNbrOfHoles() {
            long totalNbrOfHoles = 0;
            for (MyThreadData holeListener : threadDataList) {
                totalNbrOfHoles += holeListener.getNbrOfHoles();
            }
            return totalNbrOfHoles;
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    /**
     * Power of two.
     * Not necessarily the default of LongCounter constructors.
     */
    private static final int DEFAULT_NBR_OF_CELLS_FOR_TEST = 8;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_LongCounter_long_int() {
        // Negative initial value.
        for (int n=1;n<=8;n*=2) {
            try {
                new LongCounter(-1, 1);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
            try {
                new LongCounter(Long.MIN_VALUE, 1);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        // Testing handling of number of cells.
        for (int n=-100;n<100;n++) {
            boolean exception;
            try {
                new LongCounter(0, n);
                exception = false;
            } catch (IllegalArgumentException e) {
                exception = true;
            }
            assertEquals(!NumbersUtils.isPowerOfTwo(n), exception);
        }
        
        final LongCounter counter = new LongCounter(17L,2);
        assertEquals(17L,counter.get(0));
        assertEquals(18L,counter.get(0));
    }

    public void test_LongCounter_long() {
        final long initialValue = 17L;
        final LongCounter counter = new LongCounter(initialValue);
        
        final int expectedNbrOfCells = NumbersUtils.ceilingPowerOfTwo(
                Math.min(
                        1<<30,
                        NumbersUtils.timesBounded(
                                2,
                                Runtime.getRuntime().availableProcessors())));
        assertEquals(expectedNbrOfCells,counter.getNbrOfCells());
        
        assertEquals(initialValue,counter.get(0));
    }

    public void test_LongCounter() {
        final LongCounter counter = new LongCounter();
        
        final int expectedNbrOfCells = NumbersUtils.ceilingPowerOfTwo(
                Math.min(
                        1<<30,
                        NumbersUtils.timesBounded(
                                2,
                                Runtime.getRuntime().availableProcessors())));
        assertEquals(expectedNbrOfCells,counter.getNbrOfCells());
        
        assertEquals(0L,counter.get(0));
    }

    public void test_toString() {
        final long initialValue = 11;
        final LongCounter counter = new LongCounter(
                initialValue,
                4);
        
        assertEquals("[11,12,13,14]",counter.toString());
    }

    public void test_newLocalData() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(
                initialValue,
                DEFAULT_NBR_OF_CELLS_FOR_TEST);
        
        // Loop to check each new local data index starts at first cell.
        for (int k=0;k<2;k++) {
            final LongCounter.LocalData localData = counter.newLocalData();
            for (int i=0;i<DEFAULT_NBR_OF_CELLS_FOR_TEST;i++) {
                assertEquals(initialValue+i+(DEFAULT_NBR_OF_CELLS_FOR_TEST*k), counter.getAndIncrement(localData));
            }
        }
    }

    public void test_getNbrOfCells() {
        LongCounter counter;
        
        counter = new LongCounter(0, 1);
        assertEquals(1,counter.getNbrOfCells());
        
        counter = new LongCounter(1, 2);
        assertEquals(2,counter.getNbrOfCells());
        
        counter = new LongCounter(1, 4);
        assertEquals(4,counter.getNbrOfCells());
    }
    
    public void test_getCellIndex_long() {
        final long initialValue = 17L;
        final LongCounter counter = new LongCounter(initialValue,DEFAULT_NBR_OF_CELLS_FOR_TEST);
        
        final int n = counter.getNbrOfCells();
        
        assertEquals(0,counter.getCellIndex(initialValue-n));
        assertEquals(n-1,counter.getCellIndex(initialValue-1));
        assertEquals(0,counter.getCellIndex(initialValue));
        assertEquals(1,counter.getCellIndex(initialValue+1));
        assertEquals(0,counter.getCellIndex(initialValue+n));
    }
    
    public void test_getCellValue_long() {
        final long initialValue = 17L;
        final LongCounter counter = new LongCounter(initialValue,DEFAULT_NBR_OF_CELLS_FOR_TEST);
        
        final int n = counter.getNbrOfCells();
        
        assertEquals(initialValue,counter.getCellValue(initialValue-n));
        assertEquals(initialValue+n-1,counter.getCellValue(initialValue-1));
        assertEquals(initialValue,counter.getCellValue(initialValue));
        assertEquals(initialValue+1,counter.getCellValue(initialValue+1));
        assertEquals(initialValue,counter.getCellValue(initialValue+n));
    }

    public void test_isCurrent_long() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);
        
        for (int i=0;i<counter.getNbrOfCells();i++) {
            assertFalse(counter.isCurrent(initialValue+i-counter.getNbrOfCells()));
            assertTrue(counter.isCurrent(initialValue+i));
            assertFalse(counter.isCurrent(initialValue+i+counter.getNbrOfCells()));
        }
    }
    
    public void test_isAvailable_long() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);
        
        assertTrue(counter.isAvailable(initialValue));
        for (int i=1;i<1000;i++) {
            assertFalse(counter.isAvailable(initialValue-i));
            assertTrue(counter.isAvailable(initialValue+i));
        }
        
        for (int i=0;i<1000;i++) {
            long value = counter.getAndIncrement();
            assertFalse(counter.isAvailable(value));
            assertTrue(counter.isAvailable(value+1));
        }
    }

    public void test_getNbrOfIncrementations() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);
        
        for (int i=0;i<1000;i++) {
            assertEquals(i,counter.getNbrOfIncrementations());
            counter.getAndIncrement();
        }
    }
    
    public void test_getMinCellValue() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);
        
        assertEquals(initialValue,counter.getMinCellValue());
        
        final int nbrOfIncrementations = 1000;
        for (int i=0;i<nbrOfIncrementations;i++) {
            counter.getAndIncrement();
        }
        assertEquals(initialValue+nbrOfIncrementations,counter.getMinCellValue());
    }

    public void test_getMaxCellValue() {
        final long initialValue = 17;
        // Power of two.
        final int nbrOfCells = (1<<3);
        final LongCounter counter = new LongCounter(
                initialValue,
                nbrOfCells);
        
        assertEquals(initialValue+(nbrOfCells-1),counter.getMaxCellValue());
        
        final int nbrOfIncrementations = 1000;
        for (int i=0;i<nbrOfIncrementations;i++) {
            counter.getAndIncrement();
        }
        assertEquals(initialValue+(nbrOfCells-1)+nbrOfIncrementations,counter.getMaxCellValue());
    }
    
    public void test_getMaxGot() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);
        
        assertEquals(initialValue-1, counter.getMaxGot());
        
        for (int i=0;i<1000;i++) {
            long value = counter.getAndIncrement();
            assertEquals(value, counter.getMaxGot());
        }
    }
    
    public void test_isLowerThanMaxCellValue_long() {
        final long initialValue = 17;
        // Power of two.
        final int nbrOfCells = (1<<3);
        final LongCounter counter = new LongCounter(
                initialValue,
                nbrOfCells);
        
        assertTrue(counter.isLowerThanMaxCellValue(initialValue+(nbrOfCells-2)));
        assertFalse(counter.isLowerThanMaxCellValue(initialValue+(nbrOfCells-1)));
        assertFalse(counter.isLowerThanMaxCellValue(initialValue+nbrOfCells));
        
        for (int i=0;i<1000;i++) {
            counter.getAndIncrement();
            long maxCellValue = counter.getMaxCellValue();
            assertTrue(counter.isLowerThanMaxCellValue(maxCellValue-1));
            assertFalse(counter.isLowerThanMaxCellValue(maxCellValue));
        }
    }
    
    public void test_isLowerThanMaxGot_long() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);
        
        assertTrue(counter.isLowerThanMaxGot(initialValue-2));
        assertFalse(counter.isLowerThanMaxGot(initialValue-1));
        assertFalse(counter.isLowerThanMaxGot(initialValue));
        
        for (int i=0;i<1000;i++) {
            long value = counter.getAndIncrement();
            assertTrue(counter.isLowerThanMaxGot(value-1));
            assertFalse(counter.isLowerThanMaxGot(value));
        }
    }
    
    public void test_compareAndIncrement_long() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);

        assertFalse(counter.compareAndIncrement(initialValue-counter.getNbrOfCells()));
        assertFalse(counter.compareAndIncrement(initialValue+counter.getNbrOfCells()));
        assertTrue(counter.compareAndIncrement(initialValue));
        assertFalse(counter.isCurrent(initialValue));
    }
    
    public void test_isCurrentAndCompareAndIncrement_long() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);

        assertFalse(counter.isCurrentAndCompareAndIncrement(initialValue-counter.getNbrOfCells()));
        assertFalse(counter.isCurrentAndCompareAndIncrement(initialValue+counter.getNbrOfCells()));
        assertTrue(counter.isCurrentAndCompareAndIncrement(initialValue));
        assertFalse(counter.isCurrent(initialValue));
    }
    
    public void test_weakCompareAndIncrement_long() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);

        assertFalse(counter.weakCompareAndIncrement(initialValue-counter.getNbrOfCells()));
        assertFalse(counter.weakCompareAndIncrement(initialValue+counter.getNbrOfCells()));
        assertTrue(counter.weakCompareAndIncrement(initialValue));
        assertFalse(counter.isCurrent(initialValue));
    }
    
    public void test_isCurrentAndWeakCompareAndIncrement_long() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);

        assertFalse(counter.isCurrentAndWeakCompareAndIncrement(initialValue-counter.getNbrOfCells()));
        assertFalse(counter.isCurrentAndWeakCompareAndIncrement(initialValue+counter.getNbrOfCells()));
        assertTrue(counter.isCurrentAndWeakCompareAndIncrement(initialValue));
        assertFalse(counter.isCurrent(initialValue));
    }

    public void test_get_int() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(
                initialValue,
                DEFAULT_NBR_OF_CELLS_FOR_TEST);
        
        for (int k=0;k<2;k++) {
            for (int i=0;i<DEFAULT_NBR_OF_CELLS_FOR_TEST;i++) {
                assertEquals(initialValue+i,counter.get(0));
            }
        }
    }

    public void test_get_LocalData_int() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(
                initialValue,
                DEFAULT_NBR_OF_CELLS_FOR_TEST);
        final LocalData localData = counter.newLocalData();
        
        for (int k=0;k<2;k++) {
            for (int i=0;i<DEFAULT_NBR_OF_CELLS_FOR_TEST;i++) {
                assertEquals(initialValue+i,counter.get(localData,0));
            }
        }
    }
    
    public void test_getAndIncrement() {
        final LongCounter counter = new LongCounter();
        for (int i=0;i<1000;i++) {
            assertEquals(i,counter.getAndIncrement());
        }
    }
    
    public void test_getAndIncrement_LocalData() {
        final LongCounter counter = new LongCounter();
        final LocalData localData = counter.newLocalData();
        for (int i=0;i<1000;i++) {
            assertEquals(i,counter.getAndIncrement(localData));
        }
    }
    
    public void test_weakGetAndIncrement() {
        final LongCounter counter = new LongCounter();
        for (int i=0;i<1000;i++) {
            assertEquals(i,counter.weakGetAndIncrement());
        }
    }
    
    public void test_weakGetAndIncrement_LocalData() {
        final LongCounter counter = new LongCounter();
        final LocalData localData = counter.newLocalData();
        for (int i=0;i<1000;i++) {
            assertEquals(i,counter.weakGetAndIncrement(localData));
        }
    }

    public void test_getAndIncrementMonotonic_InterfaceHoleListener() {
        final LongCounter counter = new LongCounter();
        final MyThreadData holeListener = new MyThreadData();
        for (int i=0;i<1000;i++) {
            assertEquals(i,counter.getAndIncrementMonotonic(holeListener));
        }
        assertEquals(0L,holeListener.getNbrOfHoles());
    }

    public void test_getAndIncrementMonotonic_InterfaceHoleListener_stabilizationWithSingleThreadUsage() {
        // Powers of two.
        for (int nbrOfCells=4;nbrOfCells<=128;nbrOfCells*=2) {
            final LongCounter counter = new LongCounter(0,nbrOfCells);
            
            // Simulating a potential effect of concurrent usage.
            counter.messUpCells();
            
            long previous;
            long value;
            
            // Checking cells are messed-up.
            boolean foundValueNotPreviousPlusOne = false;
            previous = counter.getAndIncrementMonotonic(null);
            for (int i=0;i<nbrOfCells;i++) {
                value = counter.getAndIncrementMonotonic(null);
                if (value != previous+1) {
                    foundValueNotPreviousPlusOne = true;
                    break;
                }
                previous = value;
            }
            assertTrue(foundValueNotPreviousPlusOne);
            
            // Full traversal (stabilizes values).
            for (int i=0;i<nbrOfCells;i++) {
                counter.getAndIncrementMonotonic(null);
            }
            
            // Full traversal: checking cells are no longer messed-up.
            previous = counter.getAndIncrementMonotonic(null);
            for (int i=0;i<nbrOfCells;i++) {
                value = counter.getAndIncrementMonotonic(null);
                assertEquals(previous+1, value);
                previous = value;
            }
        }
    }
    
    public void test_getAndIncrementMonotonic_LocalData_InterfaceHoleListener() {
        final LongCounter counter = new LongCounter();
        final LocalData localData = counter.newLocalData();
        final MyThreadData holeListener = new MyThreadData();
        for (int i=0;i<1000;i++) {
            assertEquals(i,counter.getAndIncrementMonotonic(localData,holeListener));
        }
        assertEquals(0L,holeListener.getNbrOfHoles());
    }

    public void test_weakGetAndIncrementMonotonic_InterfaceHoleListener() {
        final LongCounter counter = new LongCounter();
        final MyThreadData holeListener = new MyThreadData();
        for (int i=0;i<1000;i++) {
            assertEquals(i,counter.weakGetAndIncrementMonotonic(holeListener));
        }
        assertEquals(0L,holeListener.getNbrOfHoles());
    }

    public void test_weakGetAndIncrementMonotonic_LocalData_InterfaceHoleListener() {
        final LongCounter counter = new LongCounter();
        final LocalData localData = counter.newLocalData();
        final MyThreadData holeListener = new MyThreadData();
        for (int i=0;i<1000;i++) {
            assertEquals(i,counter.weakGetAndIncrementMonotonic(localData,holeListener));
        }
        assertEquals(0L,holeListener.getNbrOfHoles());
    }
    
    public void test_removeUpTo_long_InterfaceHoleListener() {
        final long initialValue = 17;
        final LongCounter counter = new LongCounter(initialValue);

        final HashSet<Long> holes = new HashSet<Long>();
        final InterfaceHoleListener holeListener = new InterfaceHoleListener() {
            @Override
            public void onHole(long hole) {
                holes.add(hole);
            }
        };
        
        counter.removeUpTo(initialValue-1, holeListener);
        assertTrue(counter.isAvailable(initialValue));
        assertEquals(0, holes.size());
        
        for (int i=0;i<1000;i++) {
            final long value = initialValue+i;
            counter.removeUpTo(value, holeListener);
            assertFalse(counter.isAvailable(value));
            assertTrue(counter.isAvailable(value+1));
            assertEquals(i+1, holes.size());
            assertTrue(holes.contains(value));
        }
    }

    /*
     * stress tests
     */
    
    public void test_getAndIncrement_stress() {
        final int nbrOfThreads = DEFAULT_NBR_OF_CELLS_FOR_TEST;
        final long nbrOfCalls = 100L * 1000L * nbrOfThreads;
        final int nbrOfCells = DEFAULT_NBR_OF_CELLS_FOR_TEST;
        final LongCounter counter = new LongCounter(0L,nbrOfCells);
        final MyThreadDataFactory threadDataFactory = new MyThreadDataFactory();
        runConcurrentCalls(new InterfaceFactory<Runnable>() {
            public Runnable newInstance() { return new Runnable() {
                private final MyThreadData threadData = threadDataFactory.newInstance();
                public void run() {
                    final long value = counter.getAndIncrement();
                    this.threadData.values.add(value);
                }
            };}
        }, nbrOfCalls, nbrOfThreads);

        // Checking values uniqueness among all threads,
        // and that there was no hole.
        final HashSet<Long> values = new HashSet<Long>();
        final long[] maxByCellIndex = new long[nbrOfCells];
        for (MyThreadData threadData : threadDataFactory.threadDataList) {
            for (Long value : threadData.values) {
                // Unique value.
                assertTrue(values.add(value));
                // No value < initial value.
                assertTrue(value.longValue() >= 0);
                // 
                final int cellIndex = (int)value.longValue() & (nbrOfCells-1);
                maxByCellIndex[cellIndex] = Math.max(maxByCellIndex[cellIndex], value.longValue());
            }
        }
        // Checking no hole, for each cell.
        for (int i=0;i<maxByCellIndex.length;i++) {
            final long max = maxByCellIndex[i];
            for (long value=max;value>=0;value-=nbrOfCells) {
                assertTrue(values.contains(value));
            }
        }
    }

    public void test_getAndIncrementMonotinic_InterfaceHoleListener_stress() {
        final int nbrOfThreads = DEFAULT_NBR_OF_CELLS_FOR_TEST;
        final long nbrOfCalls = 100L * 1000L * nbrOfThreads;
        final int nbrOfCells = DEFAULT_NBR_OF_CELLS_FOR_TEST;
        final LongCounter counter = new LongCounter(0L,nbrOfCells);
        final MyThreadDataFactory threadDataFactory = new MyThreadDataFactory();
        final AtomicReference<String> error = new AtomicReference<String>();
        runConcurrentCalls(new InterfaceFactory<Runnable>() {
            public Runnable newInstance() { return new Runnable() {
                private final MyThreadData threadData = threadDataFactory.newInstance();
                private long previous = Long.MIN_VALUE;
                public void run() {
                    final long value = counter.getAndIncrementMonotonic(this.threadData);
                    this.threadData.values.add(value);
                    // monotonicity test
                    final boolean ok = (value > this.previous);
                    if (!ok) {
                        error.compareAndSet(null, "previous = "+this.previous+", value = "+value);
                        // To stop the thread.
                        assertTrue(false);
                    }
                    this.previous = value;
                }
            };}
        }, nbrOfCalls, nbrOfThreads);
        assertEquals(null,error.get());

        // Should have some holes.
        assertTrue(threadDataFactory.getTotalNbrOfHoles() != 0);
        
        // Checking values and holes are exclusive.
        final HashSet<Long> valuesOrHoles = new HashSet<Long>();
        for (MyThreadData threadData : threadDataFactory.threadDataList) {
            for (Long value : threadData.values) {
                assertTrue(valuesOrHoles.add(value));
            }
            for (Long hole : threadData.holes) {
                assertTrue(valuesOrHoles.add(hole));
            }
        }
    }
    
    /**
     * Checking cross-monotonicity (monotonicity between method types),
     * i.e. that for a same thread,
     * getAndIncrementMonotonic(InterfaceHoleListener)
     * does not return values inferior to value previously returned by
     * getAndIncrement().
     */
    public void test_getAndIncrementXXX_crossMonotonicity() {
        final int nbrOfThreads = DEFAULT_NBR_OF_CELLS_FOR_TEST;
        final long nbrOfCalls = 100L * 1000L * nbrOfThreads;
        final int nbrOfCells = DEFAULT_NBR_OF_CELLS_FOR_TEST;
        final LongCounter counter = new LongCounter(0L,nbrOfCells);
        final AtomicReference<String> error = new AtomicReference<String>();
        runConcurrentCalls(new InterfaceFactory<Runnable>() {
            public Runnable newInstance() { return new Runnable() {
                public void run() {
                    final long previous = counter.getAndIncrement();
                    final long value = counter.getAndIncrementMonotonic(null);
                    // cross-monotonicity test
                    final boolean ok = (value > previous);
                    if (!ok) {
                        error.compareAndSet(null, "previous = "+previous+", value = "+value);
                        // To stop the thread.
                        assertTrue(false);
                    }
                }
            };}
        }, nbrOfCalls, nbrOfThreads);
        assertEquals(null,error.get());
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    /**
     * @param runnableFactory Factory to create one runnable per thread.
     */
    private static void runConcurrentCalls(
            final InterfaceFactory<Runnable> runnableFactory,
            final long nbrOfCalls,
            int nbrOfThreads) {
        final ExecutorService executor = Executors.newFixedThreadPool(nbrOfThreads);

        // Neglecting the rest.
        final long nbrOfCallsPerThread = nbrOfCalls / nbrOfThreads;

        for (int i=0;i<nbrOfThreads;i++) {
            executor.execute(new Runnable() {
                private final Runnable runnable = runnableFactory.newInstance();
                @Override
                public void run() {
                    for (int j=0;j<nbrOfCallsPerThread;j++) {
                        this.runnable.run();
                    }
                }
            });
        }
        Unchecked.shutdownAndAwaitTermination(executor);
    }
}
