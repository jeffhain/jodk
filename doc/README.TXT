--------------------------------------------------------------------------------
-- OVERVIEW
--------------------------------------------------------------------------------

   Java Optimized Development Kit (JODK) is a performance-oriented Java
general-purpose library, which treatments are extensions, additions or
alternatives to those of JDK.

   Main content:
- FastMath (older versions available at http://sourceforge.net/projects/jafama).
- ThinTime, which provides nanosecond-precise time.
- DataBuffer, a bitwise version of ByteBuffer, and equivalent treatments for use
  on byte arrays or ByteBuffer instances.
- LongCounter, a non-monotonic sort of AtomicLong which might scale better for
  some usages.
- HeisenLogger, a light-side-effect logger for debugging concurrent treatments.
- ReentrantCheckerLock, which extends ReentrantLock and provides methods to
  check lock state.
- Condilocks, which interface extends Condition, and provides methods to wait
  for a boolean condition to be true, making it both simple and transparently
  configurable.
- Multicast and unicast ring buffers, which are a cross of LMAX ring buffers
  (http://code.google.com/p/disruptor) and JDK's ExecutorService, and share
  common interfaces.
- An ExecutorService implementation based on a ring buffer.

--------------------------------------------------------------------------------
-- PROPERTIES
--------------------------------------------------------------------------------

   Classes with properties (see their Javadoc for details):
   
- jodk.lang.FastMath

- jodk.lang.ThinTime

--------------------------------------------------------------------------------
-- VERSIONS
--------------------------------------------------------------------------------

- V1.0:
  - Code in src and samples depends on JDK6.
  - Code in test depends on JDK7 (for ForkJoinPool).
  - jodk.jar, corresponding to code in src, was generated with JDK6.
  - FastMath changes since jafama 1.2:
    - minor typos, refactorings and doc enhancements
    - corrected Javadoc (accuracy claims) for some xxxQuick methods
    - properties renamed from odk.xxx into jodk.xxx
    - for asinInRange and acosInRange, replaced < and > with <= and >=,
      for quick return in case input is a bound
    - redefined log10(double), not to use StrictMath.log10(double) by default,
      which can be quite slow
    - gave credit to Archimedes as well for so-called Newton's method :)
    - added isNaNOrInfinite(float)
    - to match upcoming JDK8's new Math methods, renamed:
      - toIntSafe into toIntExact (and asInt in NumbersUtils)
      - plusNoModulo into addBounded (and plusBounded in NumbersUtils)
      - plusNoModuloSafe into addExact (and plusExact in NumbersUtils)
      - minusNoModulo into subtractBounded (and minusBounded in NumbersUtils)
      - minusNoModuloSafe into subtractExact (and minusExact in NumbersUtils)
      - timesNoModulo into multiplyBounded (and timesBounded in NumbersUtils)
      - timesNoModuloSafe into multiplyExact (and timesExact in NumbersUtils)

--------------------------------------------------------------------------------

