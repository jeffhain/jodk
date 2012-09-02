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
package net.jodk.lang;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.jodk.threading.AtomicUtils;

/**
 * This class makes up in some ways to the absence of a
 * System.currentTimeNanos() method. If such a method
 * would get available, most of this class would become
 * pointless (unless the new method would be slower).
 * 
 * Methods of this class are thread-safe and non-blocking.
 * 
 * The advantage of these treatments over System.currentTimeMillis(),
 * resides not in accuracy, which is about the same, but in that
 * from a call to the next, the returned value will change more often.
 * That's why this class could not be called "AccurateTime".
 * 
 * The average current time computed is biased compared
 * to the time returned by System.currentTimeMillis(),
 * being usually a bit ahead of it, by an amount
 * depending on tolerance configuration.
 * 
 * An offset can be defined, in milliseconds, relatively
 * to the value returned by System.currentTimeMillis().
 * 
 * Unless system time backward jumps, returned time
 * is never smaller than a previously returned time
 * (for a same method at least, since they do not
 * all have the same precision).
 * As a consequence, returned time gets stalled
 * once Long.MAX_VALUE nanoseconds time is reached,
 * unless actual system time does a backward jump.
 * 
 * Properties:
 * 
 * - jodk.thintime.ftr (double, default is 2):
 *   The default ratio to multiply by the smallest date increment between
 *   System.currentTimeMillis jumps, which defines the maximum advance of thin
 *   time ahead of current system time.
 *   
 * - jodk.thintime.imsctmj (long, in milliseconds, default is 100):
 *   The default initially considered smallest date increment between
 *   System.currentTimeMillis jumps. The considered smallest increment is
 *   computed dynamically and typically gets lower than initial value.
 *   
 * - jodk.thintime.stzero (long, in milliseconds, default is 0):
 *   The default offset of thin time from system time.
 */
public class ThinTime {

    /*
     * Note on using a time in nanoseconds, with a custom offset from system time.
     * 
     * One year = 3600*24*365 seconds
     * = 31_536_000 seconds
     * = 31_536_000_000 milliseconds
     * = 31_536_000_000_000_000 nanoseconds
     * and
     * (2^63)/31_536_000_000_000_000 = 292.47120867753601623541349568747
     * so with time in nanoseconds, starting from zero, a long allows to go up to 292 years,
     * and starting from Long.MIN_VALUE, up to 584 years, which should be enough for most needs.
     * 
     * Meanwhile, this class allows to define an offset relatively to system time, in milliseconds.
     * It can therefore be used over a 584 years period of time located anywhere in the
     * next 292 millions years, which should also be enough for most needs.
     */

    /*
     * System.currentTimeMillis() behavior illustration:
     * time
     *  ^
     *  |           time = actual current time
     *  |          /
     *  |         /
     * 9|        +-- time = System.currentTimeMillis()
     * 8|       /|
     * 7|      +-+ (case 1: reaches actual current time)
     * 6|     /| |
     * 5|    / +-+ (case 2: does not reach actual current time)
     * 4|   /  |
     * 3|  +---+
     * 2| /|
     * 1|+-+
     * 0+-----------------------------------> actual current time
     *  0123456789
     * 
     * As shown, we consider the actual current time to always be
     * superior or equal to the value returned by System.currentTimeMillis().
     * This might not be the case, but at worse it just introduces a
     * small and constant bias in our measurement of time, which is no biggie
     * since we just pretend to be "thin", and about as accurate (not more)
     * than System.currentTimeMillis().
     * 
     * Let minSCTMJump be the min time jump when System.currentTimeMillis()
     * returned value changes.
     * minSCTMJump is estimated at first with a default value, then dynamically.
     */

    /*
     * We don't use padded volatile values:
     * - to have less dependencies,
     * - because the values should not be updated that often,
     * - because we hope that our volatile values are not located
     *   nearby an intensively updated volatile value.
     * TODO Due to this last and half-bad reason, once @Contented
     * annotation is available, we should use it here.
     */
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    /**
     * Max number of times sctm and snt are retrieved again,
     * in case recently retrieved ones were not suited for
     * time reference update.
     */
    private static final int MAX_NBR_OF_TIME_REF_UPDATE_RE_ATTEMPTS = 3;
    
    private static final double DEFAULT_FUTURE_TOLERANCE_RATIO = LangUtils.getDoubleProperty("jodk.thintime.ftr", 2.0);
    
    private static final long DEFAULT_INITIAL_MIN_SCTM_JUMP_MS = LangUtils.getLongProperty("jodk.thintime.imsctmj", 100L);
    
    private static final long DEFAULT_SYSTEM_TIME_ZERO_MS = LangUtils.getLongProperty("jodk.thintime.stzero", 0L);

    //--------------------------------------------------------------------------
    // PUBLIC CLASSES
    //--------------------------------------------------------------------------
    
    public static class TimeRef {
        /**
         * Reference value returned by (System.currentTimeMillis() - systemTimeZeroMS).
         */
        private final long refSCTM;
        /**
         * Reference value returned by System.nanoTime().
         */
        private final long refSNT;
        public TimeRef(
                long refSCTM,
                long refSNT) {
            this.refSCTM = refSCTM;
            this.refSNT = refSNT;
        }
        /**
         * @return Reference (System.currentTimeMillis() - systemTimeZeroMS).
         */
        public long getRefSCTM() {
            return this.refSCTM;
        }
        /**
         * @return Reference System.nanoTime().
         */
        public long getRefSNT() {
            return this.refSNT;
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final ThinTime DEFAULT_INSTANCE = new ThinTime();
    
    /**
     * This ratio (>= 0.0) defines the maximum time delta into the future
     * (the maximum time delta into the past is 0),
     * from the time returned by System.currentTimeMillis(), to
     * the "thin current time" (the time returned by methods of this class).
     * 
     * This delta is computed as "ratio * minSCTMJump", where minSCTMJump
     * is the min time jump when System.currentTimeMillis() value changes,
     * i.e. System.currentTimeMillis()'s best precision (and hopefully accuracy).
     * 
     * You want this ratio to be >= 1.0, or 0.0, considering
     * what happens for values < 1.0:
     * - If this ratio is 0.0, thin current time will always be equal
     *   to the time returned by System.currentTimeMillis().
     * - If this ratio is in ]0.0,1.0[, as thin current time will not be
     *   allowed to differ from System.currentTimeMillis() for more than
     *   System.currentTimeMillis()'s best accuracy, there will be a lot of
     *   recomputations of thin current time line (time references),
     *   which will also jump a lot relatively to the actual current time:
     *   this might be CPU heavy, and leads to a less thin/continuous
     *   time line.
     * 
     * For ratios >= 1.0:
     * - Since System.currentTimeMillis() can have jumps larger than its
     *   min jumps, the bad effects described for ratios in ]0.0,1.0[ also
     *   occur for ratios >= 1.0 but too small regarding System.currentTimeMillis()
     *   accuracy variations.
     * - Ratios too large regarding System.currentTimeMillis() will by definition
     *   allow for a "thin current time line" uselessly ahead of the actual current time,
     *   i.e. for a (still) thin/continuous but less accurate time.
     * 
     * The best ratio is the smallest one that does not lead to (a lot of) jumps
     * (relatively to the actual current time).
     * Ideally, when System.nanoTime() does not jump, with a good ratio,
     * the "thin current time line" will converge to being just-above actual
     * current time line.
     */
    private final double futureToleranceRatio;    

    private final long systemTimeZeroMS;
    
    private final AtomicReference<TimeRef> timeRef = new AtomicReference<TimeRef>();

    /**
     * NB: Not having it and future tolerance in a same volatile-referenced object
     * (to reduce number of accesses to a volatile object),
     * for minSCTMJumpMS is only accessed when sctm changes, i.e. not so often.
     * 
     * In milliseconds.
     * Can only get smaller.
     */
    private final AtomicLong minSCTMJumpMS = new AtomicLong(Long.MAX_VALUE);

    /**
     * In nanoseconds.
     * Can only get smaller.
     */
    private final AtomicLong futureToleranceNS = new AtomicLong(Long.MAX_VALUE);

    /**
     * Last retrieved (System.currentTimeMillis() - systemTimeZeroMS).
     */
    private final AtomicLong lastGetSCTM = new AtomicLong(Long.MIN_VALUE);
    
    /**
     * Last returned current time, in nanoseconds.
     */
    private final AtomicLong lastReturnedCTN = new AtomicLong(Long.MIN_VALUE);

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * Unless default parameters are redefined by properties,
     * creates an instance with a future tolerance ratio of 2,
     * a default initial min sctm jump of 100ms,
     * and a zero offset from system time.
     * You most likely would like to use the default instance,
     * which is of this kind, instead of another one (the most
     * an instance is used, the most accurate it is), unless
     * for some reasons you don't want, for example, too many
     * threads to use your instance.
     */
    public ThinTime() {
        this(
                DEFAULT_FUTURE_TOLERANCE_RATIO,
                DEFAULT_INITIAL_MIN_SCTM_JUMP_MS,
                DEFAULT_SYSTEM_TIME_ZERO_MS);
    }

    /**
     * @param futureToleranceRatio Tolerance ratio for the returned time to be ahead
     *        of the time returned by System.currentTimeMillis(). This value is multiplied
     *        with the min time jump (precision) of System.currentTimeMillis().
     *        Ex. : if system time precision is 10ms, and this ratio is 2, returned time
     *        will be allowed to be up to 20ms ahead of system time.
     *        This value must be 0.0, or a value >= 1.0.
     * @param initialMinSCTMJumpMS Initial value (in milliseconds) for min date jump between
     *        two consecutive calls to System.currentTimeMillis() returning different values.
     *        This initial value must be higher or equal to the actual min jump,
     *        or we would not end up with a large enough future tolerance and
     *        would be updating reference very frequently; but it must not be too large,
     *        or first returned times could be about as inaccurate than this value.
     * @param systemTimeZeroMS Offset from system time: returned time ~= system time - offset.
     */
    public ThinTime(
            double futureToleranceRatio,
            long initialMinSCTMJumpMS,
            long systemTimeZeroMS) {
        if (!((futureToleranceRatio >= 1.0) || (futureToleranceRatio == 0.0))) { // takes care of NaN
            throw new IllegalArgumentException("future tolerance ratio ["+futureToleranceRatio+"] must be >= 1.0, or equal to zero");
        }
        if (initialMinSCTMJumpMS <= 0) {
            throw new IllegalArgumentException("initial min sctm jump ["+initialMinSCTMJumpMS+"] must be > 0");
        }
        this.futureToleranceRatio = futureToleranceRatio;
        this.systemTimeZeroMS = systemTimeZeroMS;

        setMinSCTMJump(initialMinSCTMJumpMS);

        long sctm1 = getRawSystemCurrentTimeMillisFromTimeZero();
        long snt = getSNT();
        long sctm2 = getRawSystemCurrentTimeMillisFromTimeZero();
        this.updateTimeRef(sctm1, snt, sctm2);
    }

    /**
     * @return The default instance.
     */
    public static ThinTime getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * In case anyone would like to make use of it.
     * @return The current time reference.
     */
    public TimeRef getTimeRef() {
        return this.timeRef.get();
    }
    
    /**
     * @return The future tolerance ratio.
     */
    public double getFutureToleranceRatio() {
        return this.futureToleranceRatio;
    }

    /**
     * @return The System.currentTimeMillis() for which this instance, should return time 0.
     */
    public long getSystemTimeZeroMS() {
        return this.systemTimeZeroMS;
    }

    /**
     * @return Min recorded time jump, in milliseconds, between two consecutive calls of
     *         System.currentTimeMillis(). This value is > 0 (does not count backward time jumps).
     */
    public long getMinSCTMJumpMS() {
        return this.minSCTMJumpMS.get();
    }

    /**
     * @return The tolerance, in nanoseconds, for returned time to be ahead of
     *         (System.currentTimeMillis() - systemTimeZeroMS).
     */
    public long getFutureToleranceNS() {
        return this.futureToleranceNS.get();
    }

    /**
     * @return Last retrieved (System.currentTimeMillis() - systemTimeZeroMS).
     */
    public long getLastGetSCTM() {
        return this.lastGetSCTM.get();
    }

    /**
     * @return Last returned current time, in nanoseconds.
     */
    public long getLastReturnedCTN() {
        return this.lastReturnedCTN.get();
    }

    /*
     * static time methods (use a default instance of ThinTime)
     */
    
    public static double currentTimeSeconds() {
        return DEFAULT_INSTANCE.currentTimeSeconds_();
    }

    public static long currentTimeMillis() {
        return DEFAULT_INSTANCE.currentTimeMillis_();
    }

    public static long currentTimeMicros() {
        return DEFAULT_INSTANCE.currentTimeMicros_();
    }

    public static long currentTimeNanos() {
        return DEFAULT_INSTANCE.currentTimeNanos_();
    }

    /*
     * instance time methods
     */

    public double currentTimeSeconds_() {
        return currentTimeNanos_() * 1e-9;
    }

    public long currentTimeMillis_() {
        return currentTimeNanos_()/1000000L;
    }

    public long currentTimeMicros_() {
        return currentTimeNanos_()/1000L;
    }

    public long currentTimeNanos_() {
        long ctn;
        if (this.futureToleranceRatio == 0.0) {
            // Fast handling for this special case:
            // no need to use all our machinery.
            ctn = getRawSystemCurrentTimeMillisFromTimeZero() * 1000000L;
        } else {

            /*
             * Algorithm in short (prefixing volatile variables with an underscore):
             * 1) get sctm1 (and do A = {
             *                           update _lastGetSCTM if needed,
             *                           update _lastReturnedCTN if system time did a backward jump,
             *                           update _minSCTMJump and _futureToleranceNS if needed})
             * 2) get snt
             * 3) get sctm2 (and do A)
             * 4) retrieve latest _timeReference
             * 5) compute ctn (using retrieved _timeReference, and snt)
             * 6) if computed ctn is outside tolerance range (computed from sctm1, sctm2, and _futureToleranceNS),
             *    compute and set new _timeReference, and set ctn with reference sctm (*1000000 for ctn is in nanoseconds).
             * 7) if ctn is inferior to _lastReturnedCTN, set it with _lastReturnedCTN value,
             *    else update _lastReturnedCTN value with ctn.
             * 
             * Here is the update policy (i.e. using CAS and stuffs or not) for the volatile variables:
             * - _timeReference:
             *   NOT a problem if set concurrently in anarchy by multiple threads,
             *   since it always goes towards a "good" reference.
             *   ===> Not using CAS for it.
             * - _minSCTMJump and _futureToleranceNS:
             *   NOT a problem if set concurrently in anarchy by multiple threads,
             *   as long as they are set together (they are linked), since they can
             *   only get smaller, and finally don't change anymore.
             *   Though, it's nice to have them get small ASAP, and doesn't hurt to take care
             *   of it since they are only set a finite (and small) number of time.
             *   Also if using "ensureMinAndGet" to set each of them, no need for synchronization
             *   or lock to set them together.
             *   ===> Using CAS and stuffs for these.
             * - _lastGetSCTM and _lastReturnedCTN:
             *   IMPORTANT not to have them set concurrently in anarchy by multiple threads,
             *   since we want to keep track of System.currentTimeMillis() backward jumps,
             *   and don't want to return a time inferior to previously returned time
             *   (unless system time backward jumps).
             *   ===> Using CAS and stuffs for these.
             */
            
            /*
             * Since System.nanoTime() can have jumps, we make sure
             * we won't return an absurd value, by using a security
             * window computed from times returned by System.currentTimeMillis().
             */
            long sctm1 = getSystemCurrentTimeMillisFromTimeZero();
            /*
             * If our thread has a rest here, for a duration d,
             * we will be tolerant to System.nanoTime() jumps in
             * past of -d, when testing (ctn < sctn1).
             * This is no biggie, since the computed current time will
             * remain superior to the time this method was called at.
             */
            long snt = getSNT();
            /*
             * If our thread has a rest here, for a duration d,
             * we will be tolerant to System.nanoTime() jumps in
             * future of toleranceToSCTN + d, when testing (ctn > sctn2 + futureTolerance).
             * This is no biggie, since the computed current time will
             * remain inferior to the time this method returns + toleranceToSCTN.
             */
            long sctm2 = getSystemCurrentTimeMillisFromTimeZero();

            long sctn1 = sctm1 * 1000000L;
            long sctn2 = sctm2 * 1000000L;

            TimeRef timeRef = this.timeRef.get();
            
            ctn = timeRef.refSCTM * 1000000L + (snt - timeRef.refSNT);

            /*
             * If system time jumped backward between calls to System.currentTimeMillis(),
             * no biggie, we will just most likely update references according to new
             * system time.
             */

            if ((ctn < sctn1) || (ctn > sctn2 + this.futureToleranceNS.get())) {
                // ctn too low or too high: need to update reference
                // and recompute ctn.
                
                /*
                 * ctn too low:
                 * 
                 * Three non-exclusive possibilities:
                 * - Our line was below the line "time=actual current time".
                 *   System.nanoTime() might also have jumped some into the
                 *   future, but not enough to make our line reach the line
                 *   "time=actual current time",
                 * - System.nanoTime() did jump in the past (or went past
                 *   Long.MAX_VALUE),
                 * - futureToleranceRatio is small.
                 */
                
                /*
                 * ctn too high:
                 * 
                 * We authorize our computed current time to be a bit ahead
                 * system current time millis, since it changes more often,
                 * hence the tolerance, but too much is too much.
                 * 
                 * Two non-exclusive possibilities:
                 * - System.nanoTime() did jump in the future,
                 * - futureToleranceRatio is small.
                 */
                
                timeRef = updateTimeRef(sctm1,snt,sctm2);
                ctn = timeRef.refSCTM * 1000000L;
            }

            // Making sure we don't return a ctn inferior to a previously returned one,
            // (unless system time went backward, which is handled elsewhere),
            // and updating lastReturnedCTN if needed.
            ctn = AtomicUtils.ensureMaxAndGet(this.lastReturnedCTN, ctn);
        }

        return ctn;
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    /*
     * Overridable for use of custom time sources (for tests or else).
     */
    
    protected long getSCTM() {
        return System.currentTimeMillis();
    }
    
    protected long getSNT() {
        return System.nanoTime();
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    /**
     * This method updates minSCTMJump and related values as needed,
     * and keeps track of system time backward jumps.
     */
    private long getSystemCurrentTimeMillisFromTimeZero() {
        long sctm;
        long previousSCTM;
        // Most of the time, this loop should only
        // do one round, and it could do two if done near
        // the time the value returned by System.currentTimeMillis()
        // changes.
        do {
            previousSCTM = this.lastGetSCTM.get();
            sctm = getRawSystemCurrentTimeMillisFromTimeZero();
            // CAS fails when last get SCTM was changed
            // by another thread since we read it last.
        } while (!this.lastGetSCTM.compareAndSet(previousSCTM, sctm));
        // Here, we are sure sctm has been retrieved while
        // lastGetSCTM value was previousSCTM.
        if (sctm != previousSCTM) {
            // "rare" case: sctm changed.
            if (sctm < previousSCTM) {
                // System time went backward: need to reset lastReturnedCTN,
                // or our clock will be stalled until system time reaches
                // lastGetSCTM again.
                this.lastReturnedCTN.set(Long.MIN_VALUE);
            } else {
                long dtMS = sctm - previousSCTM;
                if (dtMS < 0) {
                    // Jump was so large we had overflow:
                    // obviously, not a candidate value
                    // to update min possible jump!
                } else {
                    // If time jump was smaller than smallest
                    // registered, we update it.
                    if (dtMS < this.minSCTMJumpMS.get()) {
                        setMinSCTMJump(dtMS);
                    }
                }
            }
        }
        return sctm;
    }

    /**
     * Method to be used with caution, since it does not update
     * anything of our multiple time related variables...
     */
    private long getRawSystemCurrentTimeMillisFromTimeZero() {
        return getSCTM() - this.systemTimeZeroMS;
    }

    /**
     * {sctm1, snt, sctm2} : recent values of System.currentTimeMillis(),
     * System.nanoTime(), and System.currentTimeMillis(), retrieved in
     * that order.
     * @return The new time reference.
     */
    private TimeRef updateTimeRef(
            long sctm1,
            long snt,
            long sctm2) {
        /*
         * Using counter to prevent "infinite" loop,
         * in case System.currentTimeMillis() is really thin,
         * and our thread really lazy...
         * At worse, currentTimeNanos() will be less "thin"
         * and closer to System.currentTimeMillis().
         */
        int counter = MAX_NBR_OF_TIME_REF_UPDATE_RE_ATTEMPTS;
        while ((sctm1 != sctm2) && (counter-- != 0)) {
            sctm1 = sctm2;
            snt = getSNT();
            sctm2 = getSystemCurrentTimeMillisFromTimeZero();
        }
        /*
         * In case sctm1 != sctm2, and even if sctm1 > sctm2 (backward
         * system time jump), we can use either value for SCTM reference:
         * our "thin current time line", defined by our references,
         * might be well below (with sctm1) or well above (with sctm2)
         * actual current time line, but in both cases, SCTM reference
         * will be a system time computed while currentTimeNanos() was
         * being called, so a valid time to return, and for next calls,
         * as for each call, time window validity will be checked again.
         */
        TimeRef newTimeRef = new TimeRef(sctm1,snt);
        
        this.timeRef.set(newTimeRef);
        
        return newTimeRef;
    }
    
    private void setMinSCTMJump(long newValue) {
        AtomicUtils.ensureMinAndGet(this.minSCTMJumpMS, newValue);
        long newFutureToleranceNS = (long)Math.ceil(newValue * (this.futureToleranceRatio * 1000000.0));
        AtomicUtils.ensureMinAndGet(this.futureToleranceNS, newFutureToleranceNS);
    }
}
