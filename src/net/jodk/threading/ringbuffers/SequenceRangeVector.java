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

import net.jodk.lang.NumbersUtils;

/**
 * Vector to contain sequences ranges
 * as {min1,max1,min2,max2,etc.}.
 */
public class SequenceRangeVector {
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private long[] values = new long[2];
    
    /**
     * Number of values in the vector,
     * i.e. twice the number of ranges.
     */
    private int size = 0;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public SequenceRangeVector() {
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        final int nbrOfRanges = this.getNbrOfRanges();
        sb.append("(");
        sb.append(nbrOfRanges);
        sb.append(" ranges)");
        RingBuffersUtils.appendStringSequencesRanges(sb, nbrOfRanges, this.values);
        return sb.toString();
    }
    
    /**
     * @return The number of ranges.
     */
    public int getNbrOfRanges() {
        return this.size/2;
    }
    
    /**
     * The specified range must not overlap any already added range,
     * else the state of this container, and the behavior of its
     * methods, become undefined.
     * 
     * If the specified range immediately follows the last added range,
     * both ranges are merged.
     * 
     * @param min Min bound (inclusive) of new range to add.
     * @param max Max bound (inclusive) of new range to add.
     */
    public void add(long min,long max) {
        if (min > max) {
            throw new IllegalArgumentException("min ["+min+"] must be <= max ["+max+"]");
        }
        final boolean mergeWithLast;
        if (this.size == 0) {
            mergeWithLast = false;
        } else {
            final long previousMax = this.values[this.size-1];
            mergeWithLast = (previousMax == min-1);
        }
        if (mergeWithLast) {
            this.values[this.size-1] = max;
        } else {
            if (this.size + 2 > this.values.length) {
                final long[] newValues = new long[NumbersUtils.timesBounded(2, this.values.length)];
                System.arraycopy(this.values, 0, newValues, 0, this.size);
                this.values = newValues;
            }
            this.values[this.size++] = min;
            this.values[this.size++] = max;
        }
    }
    
    /**
     * Makes sure the ranges are sorted by increasing values,
     * and merges contiguous ranges.
     */
    public void sortAndMerge() {
        if (this.size <= 2) {
            return;
        }
        Arrays.sort(this.values, 0, this.size);
        int oMaxIndex=1;
        int iMinIndex=2;
        int newSize = this.size;
        while (iMinIndex < this.size) {
            final long prevMax = this.values[oMaxIndex];
            final long nextMin = this.values[iMinIndex];
            if (prevMax == nextMin-1) {
                // Merging ranges.
                // ex.: {0,10,11,20}
                final long nextMax = this.values[iMinIndex+1];
                this.values[oMaxIndex] = nextMax;
                newSize -= 2;
            } else {
                this.values[oMaxIndex+1] = this.values[iMinIndex];
                this.values[oMaxIndex+2] = this.values[iMinIndex+1];
                oMaxIndex += 2;
            }
            iMinIndex += 2;
        }
        this.size = newSize;
    }
    
    /**
     * @return An array containing currently stored ranges,
     *         as {min1,max1,min2,max2,etc.}.
     */
    public long[] toArray() {
        final long[] result = new long[this.size];
        System.arraycopy(this.values, 0, result, 0, this.size);
        return result;
    }
    
    /**
     * Equivalent to calling sortAndMerge() and then toArray().
     * 
     * @return An array containing currently stored ranges,
     *         as {min1,max1,min2,max2,etc.}.
     */
    public long[] sortAndMergeAndToArray() {
        this.sortAndMerge();
        return this.toArray();
    }
}
