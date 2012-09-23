--------------------------------------------------------------------------------
-- OVERVIEW
--------------------------------------------------------------------------------

   Java Optimized Development Kit (JODK) is a performance-oriented Java
general-purpose library, which treatments are extensions, additions or
alternatives to those of JDK.

   Main content:
- FastMath (older versions available at http://sourceforge.net/projects/jafama).
- ThinTime, which provides nanosecond-precise (but not -accurate) time.
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
- ExecutorService implementations based on ring buffer services.

--------------------------------------------------------------------------------
-- DEPENDENCIES
--------------------------------------------------------------------------------

- src and samples: JDK 6

- test: JDK 7 (for ForkJoinPool) and JUnit (1.0 works)

--------------------------------------------------------------------------------
-- PROPERTIES
--------------------------------------------------------------------------------

   Classes with properties (see their Javadoc for details):
   
- jodk.lang.FastMath

- jodk.lang.ThinTime

--------------------------------------------------------------------------------
-- VERSIONS
--------------------------------------------------------------------------------

- V1.2:
  - BUG FIX: RingBufferExecutorService.shutdownNow() wasn't properly returning
    Runnables rejected on shut down, when they were not contiguous in the array
    (nothing subtle, just sloppy sequential code).
  - Typos.
  - Added AtomicUtils.getAndEnsureMin(...) and AtomicUtils.getAndEnsureMax(...).
  - Added bytewise buffer copy methods: ByteBufferUtils.bufferCopy(...) and
    DataBuffer.bufferCopy(...).
  - In ByteBufferUtils, moved some ByteBuffer.isReadOnly() calls that could be
    avoided for non-read-only case.
  - In ByteTabUtils, replaced "(((((int)firstBitPos)&7)|(bitSize&7)) == 0)"
    with "(((((int)firstBitPos)|bitSize)&7) == 0)".

- V1.1:
  - Reduced the visibility of many fields, methods and inner classes
    (mainly from protected to package-private).
  - Condilocks implementations rework:
    - Removed many constructors, for simplicity, and to reduce side effects
      in case of constructors changes.
    - Removed non-timed spins, for simplicity, and because their duration is
      not taken into account for some timeouts computations, which could yield
      surprising results for the user.
    - Added AbstractCondilock.getNbrOfInitialBusySpins(), to configure initial
      number of busy spins, aside from the number of busy spins after a yield.
    - Added AbstractCondilock.getNbrOfBusySpinsAfterEachYield(), to configure
      a fixed number of busy spins after each yield, which allows not to force
      yields timing in case user doesn't use a dynamically changing number of
      busy spins.
    - Added AbstractCondilock.useMaxBlockingWaitChunks(), to allow not
      to use timing methods in blocking condilocks, when waiting for a
      boolean condition to be true and timeout can be approximated as infinite.
    - Made remaining timeout computation more accurate for spinning wait
      (no longer considering that max spinning wait time exactly elapsed).
    - Multiple treatments now don't use timing if they can avoid to,
      which reduces the overhead in some cases where timeouts/deadlines are
      approximated as infinite, and blocking waits are not cut in chunks.
    - In LockCondilock, renamed "afterLockForAwaitOnBooleanCondition()"
      into "afterLockWaitingForBooleanCondition()",
      and "beforeUnlockForAwaitOnBooleanCondition()"
      into "beforeUnlockWaitingForBooleanCondition()",
      for homogeneity with MonitorCondilock.
    - In smart condilocks, added a non-volatile long field to avoid
      two volatile reads per locking wait.
    - Made many condilocks internal methods static.
  - Ring buffer based executor services upgrade:
    - RingBufferExecutorService now doesn't depend on any unicast and multicast
      implementation, just on ring buffer services interface.
    - Added URBExecutorService, which extends RingBufferExecutorService
      and uses a UnicastRingBufferService.
    - Added MRBExecutorService, which extends RingBufferExecutorService
      and uses a MulticastRingBufferService (with single subscriber).
    - Added tryExecute(Runnable) and tryExecute(Runnable,long) methods.
    - Added interruptWorkers() method, since our backing ring buffer
      services provide it.
    - execute(Runnable) now throws NullPointerException if the specified
      Runnable is null, as done in ThreadPoolExecutor, and to conform already
      existing assertions supposing that executed runnables are never null.
  - Renamed NullOrAtomicReference into NullElseAtomicReference,
    and NonNullOrAtomicReference into NonNullElseAtomicReference.
  - Added HeisenLogger.getInitialDateNS(), to create homogeneous timestamps.
  - In LongCounter, added a final field to avoid computation
    of bits shift in getNbrOfIncrementations() method.
  - In BaseBTHelper, for put methods, throwing AssertionError
    if LangUtils.checkBounds(...) doesn't throw in catch,
    for homogeneity with get methods.
  - Typos.
  - Corrected AbstractRingBuffer Javadoc: if multicast, worker's max
    passed sequence is set with lazySet only if readLazySets is true.
  - In RingBufferExecutorServicesPerf, indicated that when using
    ForkJoinPool called from itself, done publisher threads will
    contribute as workers, which biases the bench.
  - Some DataBuffer tests were failing on some architectures (Bug ID: 4660849).
    To make up for that, we don't test cases where float/double is a NaN.
  - Small tests modifications, including rethrows instead
    of e.printStackTrace() in case of trouble, GC-proof sleeps,
    and smaller default ring buffers benches (no local port,
    no 100-batch publishing).
  - Corrected bench for FastMath.atan2(...), which was not using the
    expected number of rounds.
  - Added benches for condilocks, for some FastMath methods,
    and tests for ring buffer based executor services,
    NonNullElseAtomicReference and NullElseAtomicReference.

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

