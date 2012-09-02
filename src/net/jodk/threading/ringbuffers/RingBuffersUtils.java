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

public class RingBuffersUtils {
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @param sequencesRanges Sequences ranges, as {min1,max1,min2,max2,etc.}.
     * @return True if the specified sequences ranges are correctly defined,
     *         i.e. exclusive, non-contiguous, and increasing monotonically.
     * @throws IllegalArgumentException if the specified sequences ranges are not correctly defined.
     */
    public static boolean checkSequencesRanges(final long... sequencesRanges) {
        final int nbrOfRanges = computeNbrOfRanges(sequencesRanges);
        long previousMax = 0; // re-set before use
        for (int i=0;i<nbrOfRanges;i++) {
            final long min = sequencesRanges[2*i];
            final long max = sequencesRanges[2*i+1];
            final int rangeNum = (i+1);
            if (min > max) {
                throw new IllegalArgumentException("range invalid : min("+rangeNum+") ["+min+"] > max("+rangeNum+") ["+max+"]");
            }
            if (i != 0) {
                // Works even if previousMax is Long.MAX_VALUE
                // (min <= previousMax+1 wouldn't).
                if (min-1 <= previousMax) {
                    if (min-1 == previousMax) {
                        throw new IllegalArgumentException("ranges contiguous : min("+rangeNum+")-1 ["+(min-1)+"] == max("+(rangeNum-1)+") ["+previousMax+"]");
                    } else {
                        throw new IllegalArgumentException("ranges not increasing : min("+rangeNum+")-1 ["+(min-1)+"] < max("+(rangeNum-1)+") ["+previousMax+"]");
                    }
                }
            }
            previousMax = max;
        }
        return true;
    }

    /**
     * @param sequencesRanges Sequences ranges, as {min1,max1,min2,max2,etc.}.
     * @return The number of ranges.
     * @throws IllegalArgumentException if there is not an even number of min/max values specified.
     */
    public static int computeNbrOfRanges(final long... sequencesRanges) {
        final int nbrOfRanges = sequencesRanges.length/2;
        if (2*nbrOfRanges != sequencesRanges.length) {
            throw new IllegalArgumentException("sequencesRanges length ["+sequencesRanges.length+"] must be even");
        }
        return nbrOfRanges;
    }

    /**
     * @param sequencesRanges Sequences ranges, as {min1,max1,min2,max2,etc.}.
     * @return The number of sequences.
     * @throws IllegalArgumentException if the specified sequences ranges are not correctly defined.
     */
    public static long computeNbrOfSequences(final long... sequencesRanges) {
        checkSequencesRanges(sequencesRanges);
        final int nbrOfRanges = computeNbrOfRanges(sequencesRanges);
        long nbrOfSequences = 0;
        for (int i=0;i<nbrOfRanges;i++) {
            final long min = sequencesRanges[2*i];
            final long max = sequencesRanges[2*i+1];
            nbrOfSequences += (max-min+1);
        }
        return nbrOfSequences;
    }
    
    
    /**
     * @param sequencesRanges Sequences ranges, as {min1,max1,min2,max2,etc.}.
     * @return String representation of the specified sequences ranges.
     */
    public static String toStringSequencesRanges(final long... sequencesRanges) {
        StringBuilder sb = new StringBuilder();
        appendStringSequencesRanges(sb, Integer.MAX_VALUE, sequencesRanges);
        return sb.toString();
    }
    
    /**
     * This treatment does not check that the specified ranges are increasing,
     * to allow for toString of not (yet) sorted ranges.
     * 
     * @param sb Buffer where to add representation of the specified ranges.
     * @param maxNbrOfRanges Max number of ranges to consider. Must be >= 0.
     * @param sequencesRanges Sequences ranges, as {min1,max1,min2,max2,etc.}.
     */
    public static void appendStringSequencesRanges(
            StringBuilder sb,
            int maxNbrOfRanges,
            final long... sequencesRanges) {
        if (maxNbrOfRanges < 0) {
            throw new IllegalArgumentException("max number of ranges ["+maxNbrOfRanges+"] must be >= 0");
        }
        // Does the ranges check.
        final int nbrOfRanges = computeNbrOfRanges(sequencesRanges);
        final int nbrOfRangesToConsider = Math.min(nbrOfRanges, maxNbrOfRanges);
        sb.append("{");
        if (nbrOfRanges != 0) {
            boolean firstDone = false;
            for (int i=0;i<nbrOfRangesToConsider;i++) {
                if (firstDone) {
                    sb.append(",");
                }
                final long min = sequencesRanges[2*i];
                final long max = sequencesRanges[2*i+1];
                sb.append(min);
                if (max != min) {
                    sb.append("..");
                    sb.append(max);
                }
                firstDone = true;
            }
        }
        if (nbrOfRangesToConsider != nbrOfRanges) {
            if (nbrOfRangesToConsider != 0) {
                sb.append(",");
            }
            sb.append("...");
        }
        sb.append("}");
    }
}
