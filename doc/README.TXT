--------------------------------------------------------------------------------
-- OVERVIEW
--------------------------------------------------------------------------------

   Java Optimized Development Kit (JODK) is a performance-oriented Java
general-purpose library, which treatments are extensions, additions or
alternatives to those of JDK.

   Main content:
- FastMath (older versions available at http://sourceforge.net/projects/jafama).
- ThinTime, which provides up to nanosecond-precise (but not -accurate) time.
- DataBuffer, a bitwise version of ByteBuffer, and equivalent treatments for use
  on byte arrays or ByteBuffer instances.
- ByteCopyUtils, which provides methods to copy bytes between ByteBuffers,
  between FileChannels, and between each other.
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

- src, tools and samples: JDK 6

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

- 2012/11/03 modifications (non-tagged):
  - BUG FIX: ByteCopyUtils: The amount of bytes to actually copy was initially
    computed, based on src/dst size and capacity, and then the copy was
    performed. If not all of these bytes could be copied (reads or writes
    copying less than expected), and the user did allow for it (not readAll
    or writeAll), src or dst positions could be moved according to the amount
    of bytes actually copied. But that wasn't relevant in case of backward
    copies, since the copied bytes would typically be located elsewhere.
    Now, if the initially computed amount of bytes to copy can't be fully
    copied, an IOException is thrown (either "Unexpected underflow", or
    "Unexpected overflow").
    Note that this bug could only occur in exceptional cases, such as concurrent
    truncation of src, or no space left on device.
  - ByteCopyUtils: Backward copies being possibly slower than forward copies
    (for cache reasons if done by forward-copied-chunks, as well as because
    they might first grow dst and then copy into it), they are now only used
    if there actually is overlapping, i.e. if (srcPos < dstPos) and
    (srcPos + n > dstPos).
  - Little cleanups.

- 2012/11/02 modifications (non-tagged):
  - ByteCopyUtils upgrade:
    - Overlapping ranges are now handled for copies between ByteBuffers and
      between FileChannels, but still not for copies between (mapped)
      ByteBuffers and FileChannels since there is no way to find out if and how
      they overlap.
    - src and dst positions, and possibly limits, are now properly restored
      or updated in case of exceptions.
    - Added a max chunk size for writes into FileChannels, for writes of
      large ByteBuffers (event if direct) were found to be slow on WinXP/7.
    - Enlarged max temporary ByteBuffers size from 8*1024 to 32*1024 bytes.
    - Optimizations, including complete disabling of MBBs usage (code still
      there for experimentation purpose).
    - Removed use of FileChannel.transferTo/transferFrom, for both simplicity
      and performances.
  - Added a bulk put method to InterfaceMockBuffer (and implementations),
    to allow for MockFileChannel.write methods to handle overlapping
    (which actual FileChannels don't always do!).
  - VirtualMockBuffer's local buffer is now centered on first put position,
    to allow for both forward and backward puts without prior preparation.
  - Updated some Javadocs in ByteBufferUtils and DataBufferUtils, in accordance
    with ByteCopyUtils upgrades.

- 2012/10/28 modifications (non-tagged):
  - API change: LangUtils.checkBounds(int,int,int) and
    LangUtils.checkBounds(long,long,long) now can only throw
    IndexOutOfBoundsException, instead of IllegalArgumentException in some cases,
    which is more alike usual JDK behavior.
    These methods now also have less code, so might be faster.
    Did let this modification leak through multiple APIs, that now in some cases
    throw IOOBE instead of IAE.
  - API change: removed some usages of UnsupportedOperationException:
    - in HeisenLogger, replaced with IllegalStateException if too many logs,
    - in InterfaceRingBufferWorkerFactory and its implementations, replaced with
      IllegalStateException if no more worker can be created,
    - when encountering a value that "can't be" (i.e. never happens unless of a
      bug), after multiple "if" or in a switch/case, replaced with
      AssertionError.
  - API change: added tools source directory, and moved Unchecked, TestUtils and
    ProcessorsUser classes to it.
  - In tools, added UncheckedIO, as well as MockFileChannel and related mocking
    classes, useful to test treatments dealing with FileChannels.
  - Added ByteCopyUtils, which provides methods to copy bytes between
    ByteBuffers, between FileChannels, and between each other.
  - Updated some Javadocs in ByteBufferUtils and DataBufferUtils, in particular
    to make reference to ByteCopyUtils treatments which might be way faster.
  - Typos.
  - Renamed all "dest/destXXX" variables into "dst/dstXXX", for consistency
    across io package, and because it fits better with "src" (same length, no
    vowel). This did not change any class or method name.
  - In FastMath, removed some uselessly-indenting "} else {" before a return.
  - In NumbersUtils.toInt(long), simplified a test.
  - In MulticastRingBuffer, corrected messed-up comment about
    SET_VOLATILE_MPS_ONLY_BEFORE_WAITING.
  - In ring buffers Javadocs, replaced references to "setStartSequence" method
    (removed before open-sourcing), with references to "runWorkerFrom" method.
  - Made DataBufferTest faster, by only using messiest buffers instances.

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
