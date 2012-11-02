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
package net.jodk.io;

import java.io.File;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import net.jodk.io.mock.ByteBufferMockBuffer;
import net.jodk.io.mock.InterfaceMockBuffer;
import net.jodk.io.mock.MockFileChannel;
import net.jodk.io.mock.VirtualMockBuffer;
import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;
import net.jodk.lang.Unchecked;
import net.jodk.test.TestUtils;

import junit.framework.TestCase;

public class ByteCopyUtilsTest extends TestCase {

    /*
     * We test both static and instance methods.
     * 
     * Instance methods are the only way to test some parts of the code, since
     * static methods use a default static final instance, and its fixed
     * settings, namely as MBB helper, chunk sizes and temporary buffers
     * thread-safety.
     * 
     * Static methods tests never use map/unmap when we use MockFileChannel,
     * since the backing instance uses DefaultMBBHelper.
     * 
     * Splitting most tests between "badArgs" and "goodArgs" cases,
     * else nested loops are too large and each test takes ages.
     * 
     * Not always using multiple combinations of readAllCount and writeAllRead,
     * because these values only have an impact on growing and exception
     * throwing, not on actual copy treatments.
     */

    /*
     * For overlapping tests, using chunk sizes and sizes of all sorts,
     * and not bothering with static methods, which allows to use
     * instances with custom chunks sizes.
     * 
     * Small sizes allow to test that our copy loops are done
     * in the correct direction (forward or backward), without
     * having to use huge copy sizes (else only one chunk is used).
     * 
     * Chunks, as well as copies (if we don't use chunks), of various sizes,
     * allow to test that used JDK's copy treatments don't copy by chunk,
     * or in the appropriate direction, hoping that our used chunks sizes
     * and copy sizes are larger than JDK's chunks sizes (if any).
     * Need to use various sizes, and not only big sizes, in case
     * JDK uses different low-level copy treatments depending on size.
     * Of course, when testing FC to FC copies, tests with mid to large chunks
     * or copy sizes are therefore only relevant with actual (not mock) FileChannels.
     */

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean DEBUG = false;

    private static final int BB_OFFSET = 3;
    private static final int BB_LIMIT_TO_CAPACITY = 2;

    private static final int BB_SRC_LIMIT = 23;
    private static final int BB_DST_LIMIT = 17;

    private static final int MAX_NON_HUGE_COUNT = 10;

    private static final int MAX_CONTENT_SIZE = Math.max(MAX_NON_HUGE_COUNT, Math.max(BB_SRC_LIMIT, BB_DST_LIMIT));

    /**
     * Small enough not to have too long and device-burning io tests.
     */
    private static final int NBR_OF_RANDOM_COPIES_IO = 100;
    
    /**
     * Using very small, medium and very large copy sizes,
     * in case low-mid level copy treatments use different
     * sub-treatments depending on the size.
     * 
     * Hopefully large enough to be superior to eventual thresholds
     * that decide between different copy treatments.
     * Small enough not to have too long and device-burning (if io) tests.
     */
    private static final int MAX_COUNT_IO = (DEBUG ? 32 : 128 * 1024);

    /*
     * Small max chunk sizes, to allow for multiple
     * iterations for copies by chunks (when using
     * instance methods), without having to copy
     * huge numbers of bytes.
     */

    private static final int DEFAULT_TEST_MAX_WRITE_CHUNK_SIZE = 1;
    private static final int DEFAULT_TEST_MAX_TMP_CHUNK_SIZE = 1;
    /**
     * Always using MBBs if a helper that can handle, else never using MBBs.
     */
    private static final int DEFAULT_TEST_MBB_THRESHOLD = 0;
    private static final int DEFAULT_TEST_MAX_MBB_CHUNK_SIZE = 1;

    private static final int MAX_IO_CHUNK_SIZE_FOR_CHECK = 8 * 1024;

    /**
     * Huge size for channels, for use of huge positions.
     */
    private static final long HUGE_SIZE_OFFSET = (Long.MAX_VALUE>>1);

    /**
     * Containers size for concurrency test.
     * Each thread can play with first or last half of bytes.
     */
    private static final int CONCURRENCY_TEST_SIZE = 2*MAX_NON_HUGE_COUNT;

    private static final int CONCURRENCY_NBR_OF_CALLS_PER_THREAD = 1 * 1000;

    private static final long COUNTDOWN_LOG_DELAY_MS = 10L * 1000L;

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static abstract class MyAbstractContainer {
        @Override
        public String toString() {
            return this.getBackingContainer().toString();
        }
        public String toStringContent() {
            final int size = NumbersUtils.toInt(this.size());
            if (size > 10*MAX_COUNT_IO) {
                return "(too large)";
            }
            final ByteBuffer bb = ByteBuffer.allocateDirect(size);
            this.get(0, bb, size);
            
            final StringBuilder sb = new StringBuilder();
            sb.append("{");
            for (int i=0;i<size;i++) {
                if (i != 0) {
                    sb.append(",");
                }
                sb.append(bb.get(i));
            }
            sb.append("}");
            return sb.toString();
        }
        public abstract Object getBackingContainer();
        public abstract boolean getReadable();
        public abstract boolean getWritable();
        public abstract boolean getAppendMode();
        //
        public abstract long capacity();
        public abstract long position();
        public abstract long size();
        public abstract void position(long position);
        public abstract void size(long size);
        //
        public abstract void put(long position, byte b);
        public abstract byte get(long position);
        /**
         * Puts bytes in [src.position,src.limit[ from specified position.
         * Must not go past this limit.
         */
        public abstract void put(long position, ByteBuffer src);
        /**
         * Gets count bytes from specified position into dst at position 0.
         * dst limit is set to count, and its position to 0.
         */
        public abstract void get(long position, ByteBuffer dst, int count);
    }

    private static class MyBBContainer extends MyAbstractContainer {
        final ByteBuffer bb;
        public MyBBContainer(ByteBuffer bb) {
            this.bb = bb;
        }
        @Override
        public Object getBackingContainer() {
            return this.bb;
        }
        @Override
        public boolean getReadable() {
            return true;
        }
        @Override
        public boolean getWritable() {
            return !this.bb.isReadOnly();
        }
        @Override
        public boolean getAppendMode() {
            return false;
        }
        @Override
        public long capacity() {
            return this.bb.capacity();
        }
        @Override
        public long position() {
            return this.bb.position();
        }
        @Override
        public long size() {
            return this.bb.limit();
        }
        @Override
        public void position(long position) {
            this.bb.position(NumbersUtils.asInt(position));
        }
        @Override
        public void size(long size) {
            this.bb.limit(NumbersUtils.asInt(size));
        }
        @Override
        public void put(long position, byte b) {
            this.bb.put(NumbersUtils.asInt(position), b);
        }
        @Override
        public byte get(long position) {
            return this.bb.get(NumbersUtils.asInt(position));
        }
        @Override
        public void put(long position, ByteBuffer src) {
            // Duplicate in case of concurrent usage.
            final ByteBuffer bd = this.bb.duplicate();
            bd.position(NumbersUtils.asInt(position));
            bd.put(src);
        }
        @Override
        public void get(long position, ByteBuffer dst, int count) {
            // Duplicate in case of concurrent usage.
            final ByteBuffer bd = this.bb.duplicate();
            bd.limit(NumbersUtils.asInt(position + count));
            bd.position(NumbersUtils.asInt(position));
            dst.limit(count);
            dst.position(0);
            dst.put(bd);
            dst.flip();
        }
    }

    private static class MyFCContainer extends MyAbstractContainer {
        final MockFileChannel fc;
        public MyFCContainer(MockFileChannel fc) {
            this.fc = fc;
        }
        @Override
        public Object getBackingContainer() {
            return this.fc;
        }
        @Override
        public boolean getReadable() {
            return this.fc.getReadable();
        }
        @Override
        public boolean getWritable() {
            return this.fc.getWritable();
        }
        @Override
        public boolean getAppendMode() {
            return this.fc.getAppendMode();
        }
        @Override
        public long capacity() {
            return Long.MAX_VALUE;
        }
        @Override
        public long position() {
            return UncheckedIO.position(this.fc);
        }
        @Override
        public long size() {
            return UncheckedIO.size(this.fc);
        }
        @Override
        public void position(long position) {
            UncheckedIO.position(this.fc, position);
        }
        @Override
        public void size(long size) {
            this.getMB().limit(size);
        }
        @Override
        public void put(long position, byte b) {
            this.getMB().put(position, b);
        }
        @Override
        public byte get(long position) {
            return this.getMB().get(position);
        }
        @Override
        public void put(long position, ByteBuffer src) {
            // Duplicate in case of concurrent usage.
            final ByteBuffer bd = this.getOrCreateBackingBB(position).duplicate();
            final long offset = this.getBackingBBOffset();
            bd.position(NumbersUtils.asInt(position-offset));
            bd.put(src);
        }
        @Override
        public void get(long position, ByteBuffer dst, int count) {
            // Duplicate in case of concurrent usage.
            final ByteBuffer bd = this.getOrCreateBackingBB(position).duplicate();
            final long offset = this.getBackingBBOffset();
            bd.limit(NumbersUtils.asInt(position-offset + count));
            bd.position(NumbersUtils.asInt(position-offset));
            dst.limit(count);
            dst.position(0);
            dst.put(bd);
            dst.flip();
        }
        private InterfaceMockBuffer getMB() {
            return this.fc.getBackingMockBuffer();
        }
        private ByteBuffer getOrCreateBackingBB(long position) {
            final InterfaceMockBuffer mb = this.getMB();
            if (mb instanceof VirtualMockBuffer) {
                final VirtualMockBuffer vmb = (VirtualMockBuffer)mb;
                ByteBuffer bb = vmb.getLocalBufferElseNull();
                if (bb == null) {
                    // Creating local buffer.
                    mb.put(position, (byte)position);
                    bb = vmb.getLocalBufferElseNull();
                }
                return bb;
            } else {
                return ((ByteBufferMockBuffer)mb).getBackingByteBuffer();
            }
        }
        private long getBackingBBOffset() {
            final InterfaceMockBuffer mb = this.getMB();
            if (mb instanceof VirtualMockBuffer) {
                final VirtualMockBuffer vmb = (VirtualMockBuffer)mb;
                return vmb.getLocalBufferOffset();
            } else {
                return 0;
            }
        }
    }

    /**
     * For real FileChannels.
     */
    private static class MyFCHContainer extends MyAbstractContainer {
        final FCHolder fch;
        final boolean readable;
        final boolean writable;
        final boolean appendMode;
        public MyFCHContainer(
                FCHolder fch,
                boolean appendMode) {
            this.fch = fch;
            this.readable = fch.getMode().contains("r");
            this.writable = fch.getMode().contains("w");
            this.appendMode = appendMode;
        }
        @Override
        public Object getBackingContainer() {
            return this.fch.getFC();
        }
        @Override
        public boolean getReadable() {
            return this.readable;
        }
        @Override
        public boolean getWritable() {
            return this.writable;
        }
        @Override
        public boolean getAppendMode() {
            return this.appendMode;
        }
        @Override
        public long capacity() {
            return Long.MAX_VALUE;
        }
        @Override
        public long position() {
            return UncheckedIO.position(this.fch.getFC());
        }
        @Override
        public long size() {
            return UncheckedIO.size(this.fch.getFC());
        }
        @Override
        public void position(long position) {
            UncheckedIO.position(this.fch.getFC(), position);
        }
        @Override
        public void size(long size) {
            this.fch.setSize(NumbersUtils.asInt(size));
        }
        @Override
        public void put(long position, byte b) {
            final ByteBuffer bb = ByteBuffer.allocateDirect(1);
            bb.put(0, b);
            this.put(position, bb);
        }
        @Override
        public byte get(long position) {
            final ByteBuffer bb = ByteBuffer.allocateDirect(1);
            this.get(position, bb, 1);
            return bb.get(0);
        }
        @Override
        public void put(long position, ByteBuffer src) {
            try {
                this.fch.getFC().write(src, position);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public void get(long position, ByteBuffer dst, int count) {
            try {
                dst.limit(count);
                dst.position(0);
                final int nWritten = this.fch.getFC().read(dst, position);
                if (nWritten != count) {
                    throw new AssertionError();
                }
                dst.flip();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final List<Boolean> FALSE_TRUE = Collections.unmodifiableList(Arrays.asList(new Boolean[]{false,true}));

    private static final ByteCopyUtils A_THREAD_SAFE_BCU = new ByteCopyUtils(
            null, // MBB helper
            true, // threadSafe
            DEFAULT_TEST_MAX_WRITE_CHUNK_SIZE,
            DEFAULT_TEST_MAX_TMP_CHUNK_SIZE,
            DEFAULT_TEST_MBB_THRESHOLD,
            DEFAULT_TEST_MAX_MBB_CHUNK_SIZE);

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_toString() {
        for (boolean threadSafe : FALSE_TRUE) {
            DefaultMBBHelper mbbHelper = DefaultMBBHelper.INSTANCE;
            ByteCopyUtils utils = new ByteCopyUtils(
                    mbbHelper,
                    threadSafe,
                    36,
                    37,
                    1001,
                    38);
            assertTrue(utils.toString().contains(
                    "[threadSafe="+threadSafe
                    +",maxWriteChunkSize="+36
                    +",maxTmpChunkSize="+37
                    +",mbbThreshold="+1001
                    +",maxMBBChunkSize="+38
                    +",mbbHelper="+mbbHelper+"]"));
        }
    }

    /*
     * NPE
     */

    public void test_readXXXAndWriteXXX_NPE() {
        for (boolean staticCalls : FALSE_TRUE) {
            for (Object src : new Object[]{null,ByteBuffer.allocate(MAX_CONTENT_SIZE),newMockFileChannel(MAX_CONTENT_SIZE, 2*MAX_CONTENT_SIZE)}) {
                for (Object dst : new Object[]{null,ByteBuffer.allocate(MAX_CONTENT_SIZE),newMockFileChannel(MAX_CONTENT_SIZE, 2*MAX_CONTENT_SIZE)}) {
                    test_readXXXAndWriteXXX_NPE(staticCalls, src, dst);
                }
            }
        }
    }

    /*
     * 
     */
    
    /**
     * Tests post-copy treatments, that are used if dst
     * gets full early.
     */
    public void test_readXXXAndWriteXXX_postTreatments() {
        
        /*
         * Copying a direct ByteBuffer into a FileChannel.
         * src content will be written byte-by-byte into
         * dst, and we return 0 after a few writes,
         * as could happen if device got full.
         */
        
        final ByteCopyUtils utils = new ByteCopyUtils(
                null, // mbbHelper
                false, // threadSafe
                DEFAULT_TEST_MAX_WRITE_CHUNK_SIZE,
                DEFAULT_TEST_MAX_TMP_CHUNK_SIZE,
                DEFAULT_TEST_MBB_THRESHOLD,
                DEFAULT_TEST_MAX_MBB_CHUNK_SIZE);
        
        final int count = 10;
        
        final ByteBuffer src = ByteBuffer.allocateDirect(count);
        
        final boolean readable = true;
        final boolean writable = true;
        final boolean appendMode = false;
        final MockFileChannel dst = new MockFileChannel(
                new ByteBufferMockBuffer(10),
                readable,
                writable,
                appendMode) {
            int counter = 0;
            @Override
            public int write(ByteBuffer src, long dstPos) throws IOException {
                if (++this.counter == count/2) {
                    return 0;
                }
                return super.write(src, dstPos);
            }
        };
        
        try {
            utils.readAtAndWriteAllAt_(src, 0, dst, 0, count);
            assertTrue(false);
        } catch (BufferOverflowException e) {
            // ok
        } catch (IOException e) {
            assertTrue(false);
        }
    }

    /*
     * badArgs STATIC
     */

    public void test_readXXXAndWriteXXX_badArgs_static_BB_BB() {
        test_readXXXAndWriteXXX_badArgs(true, true, true);
    }

    public void test_readXXXAndWriteXXX_badArgs_static_BB_FC() {
        test_readXXXAndWriteXXX_badArgs(true, true, false);
    }

    public void test_readXXXAndWriteXXX_badArgs_static_FC_BB() {
        test_readXXXAndWriteXXX_badArgs(true, false, true);
    }

    public void test_readXXXAndWriteXXX_badArgs_static_FC_FC() {
        test_readXXXAndWriteXXX_badArgs(true, false, false);
    }

    /*
     * badArgs INSTANCE
     */

    public void test_readXXXAndWriteXXX_badArgs_instance_BB_BB() {
        test_readXXXAndWriteXXX_badArgs(false, true, true);
    }

    public void test_readXXXAndWriteXXX_badArgs_instance_BB_FC() {
        test_readXXXAndWriteXXX_badArgs(false, true, false);
    }

    public void test_readXXXAndWriteXXX_badArgs_instance_FC_BB() {
        test_readXXXAndWriteXXX_badArgs(false, false, true);
    }

    public void test_readXXXAndWriteXXX_badArgs_instance_FC_FC() {
        test_readXXXAndWriteXXX_badArgs(false, false, false);
    }

    /*
     * goodArgs STATIC
     */

    public void test_readXXXAndWriteXXX_goodArgs_static_BB_BB() {
        test_readXXXAndWriteXXX_goodArgs(true, true, true);
    }

    public void test_readXXXAndWriteXXX_goodArgs_static_BB_FC() {
        test_readXXXAndWriteXXX_goodArgs(true, true, false);
    }

    public void test_readXXXAndWriteXXX_goodArgs_static_FC_BB() {
        test_readXXXAndWriteXXX_goodArgs(true, false, true);
    }

    public void test_readXXXAndWriteXXX_goodArgs_static_FC_FC() {
        test_readXXXAndWriteXXX_goodArgs(true, false, false);
    }

    /*
     * goodArgs INSTANCE
     */

    public void test_readXXXAndWriteXXX_goodArgs_instance_BB_BB() {
        test_readXXXAndWriteXXX_goodArgs(false, true, true);
    }

    public void test_readXXXAndWriteXXX_goodArgs_instance_BB_FC() {
        test_readXXXAndWriteXXX_goodArgs(false, true, false);
    }

    public void test_readXXXAndWriteXXX_goodArgs_instance_FC_BB() {
        test_readXXXAndWriteXXX_goodArgs(false, false, true);
    }

    public void test_readXXXAndWriteXXX_goodArgs_instance_FC_FC() {
        test_readXXXAndWriteXXX_goodArgs(false, false, false);
    }

    /*
     * overlapping INSTANCE
     */

    public void test_readXXXAndWriteXXX_overlapping_instance_BB_BB() {
        tezt_readXXXAndWriteXXX_overlapping_instance_BB_BB_impl();
    }

    public void test_readXXXAndWriteXXX_overlapping_instance_FC_FC() {
        tezt_readXXXAndWriteXXX_overlapping_instance_FC_FC_mock_impl();
    }

    public void test_readXXXAndWriteXXX_overlapping_instance_FC_FC_io() {
        tezt_readXXXAndWriteXXX_overlapping_instance_FC_FC_io_impl();
    }
    
    /*
     * Non-overlapping IO tests, to make sure IO treatments
     * actually work, even though they are already well tested
     * with MockFileChannel.
     */
    
    public void test_readXXXAndWriteXXX_basic_BB_FC_io() {
        tezt_readXXXAndWriteXXX_basic_BB_FC_io_impl();
    }

    public void test_readXXXAndWriteXXX_basic_FC_BB_io() {
        tezt_readXXXAndWriteXXX_basic_FC_BB_io_impl();
    }

    public void test_readXXXAndWriteXXX_basic_FC_FC_io() {
        tezt_readXXXAndWriteXXX_basic_FC_FC_io_impl();
    }

    /*
     * concurrency STATIC
     */

    public void test_readXXXAndWriteXXX_concurrency_static_BB_BB() {
        test_readXXXAndWriteXXX_concurrency(true, true, true, true);
    }

    public void test_readXXXAndWriteXXX_concurrency_static_BB_FC() {
        test_readXXXAndWriteXXX_concurrency(true, true, true, false);
    }

    public void test_readXXXAndWriteXXX_concurrency_static_FC_BB() {
        test_readXXXAndWriteXXX_concurrency(true, true, false, true);
    }

    public void test_readXXXAndWriteXXX_concurrency_static_FC_FC() {
        test_readXXXAndWriteXXX_concurrency(true, true, false, false);
    }

    /*
     * concurrency INSTANCE
     */

    public void test_readXXXAndWriteXXX_concurrency_threadSafeInstance_BB_BB() {
        test_readXXXAndWriteXXX_concurrency(true, false, true, true);
    }
    public void test_readXXXAndWriteXXX_concurrency_nonThreadSafeInstance_BB_BB() {
        test_readXXXAndWriteXXX_concurrency(false, false, true, true);
    }

    public void test_readXXXAndWriteXXX_concurrency_threadSafeInstance_BB_FC() {
        test_readXXXAndWriteXXX_concurrency(true, false, true, false);
    }
    public void test_readXXXAndWriteXXX_concurrency_nonThreadSafeInstance_BB_FC() {
        test_readXXXAndWriteXXX_concurrency(false, false, true, false);
    }

    public void test_readXXXAndWriteXXX_concurrency_threadSafeInstance_FC_BB() {
        test_readXXXAndWriteXXX_concurrency(true, false, false, true);
    }
    public void test_readXXXAndWriteXXX_concurrency_nonThreadSafeInstance_FC_BB() {
        test_readXXXAndWriteXXX_concurrency(false, false, false, true);
    }

    public void test_readXXXAndWriteXXX_concurrency_threadSafeInstance_FC_FC() {
        test_readXXXAndWriteXXX_concurrency(true, false, false, false);
    }
    public void test_readXXXAndWriteXXX_concurrency_nonThreadSafeInstance_FC_FC() {
        test_readXXXAndWriteXXX_concurrency(false, false, false, false);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private static void test_readXXXAndWriteXXX_NPE(boolean staticCalls, Object src, Object dst) {
        for (ByteCopyUtils utils : variousBCU(staticCalls)) {
            final boolean mustThrow_NullPointerException = (src == null) || (dst == null);
            try {
                call_readXXXAndWriteXXX(
                        utils,
                        src,
                        dst,
                        0, // srcPos
                        0, // dstPos
                        false, // posInSrc
                        false, // posInDst
                        1, // count
                        false, // readAllCount
                        false);
                assertFalse(mustThrow_NullPointerException);
            } catch (NullPointerException e) {
                assertTrue(mustThrow_NullPointerException);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /*
     * 
     */
    
    /**
     * Huge positions/counts, and read-only/non-writable dst.
     */
    private static void test_readXXXAndWriteXXX_badArgs(boolean staticCalls, boolean srcIsBB, boolean dstIsBB) {
        for (ByteCopyUtils utils : variousBCU(staticCalls)) {
            for (MyAbstractContainer srcCont : srcIsBB ? variousBBC_allCases(true) : variousFCC_allCases(true)) {
                final long srcSize = srcCont.size();
                for (MyAbstractContainer dstCont : dstIsBB ? variousBBC_allCases(false) : variousFCC_allCases(false)) {
                    final long dstSize = dstCont.size();
                    for (long srcPos : new long[]{-1,1,Long.MAX_VALUE}) {
                        for (boolean posInSrc : FALSE_TRUE) {
                            if (posInSrc && (!canDoPosInContainer(srcCont, srcPos, srcSize))) {
                                continue;
                            }
                            for (long dstPos : new long[]{-1,1,Long.MAX_VALUE}) {
                                for (boolean posInDst : FALSE_TRUE) {
                                    if (posInDst && (!canDoPosInContainer(dstCont, dstPos, dstSize))) {
                                        continue;
                                    }
                                    for (long count : new long[]{-1,0,1,Long.MAX_VALUE}) {
                                        for (boolean readAllCount : FALSE_TRUE) {
                                            for (boolean writeAllRead : FALSE_TRUE) {
                                                if (readAllCount && (!writeAllRead)) {
                                                    // not applicable
                                                    continue;
                                                }
                                                // Should not change in these tests.
                                                assertEquals(srcSize,srcCont.size());
                                                resetSizeAndFillIfWritable(dstCont,dstSize);
                                                final boolean cancelIfHugeCopy = true;
                                                final boolean checkAdjacentDstBytes = true;
                                                final boolean mightOverlap = false;
                                                callAndCheck_readXXXAndWriteXXX(
                                                        utils,
                                                        srcCont,
                                                        dstCont,
                                                        srcPos,
                                                        dstPos,
                                                        posInSrc,
                                                        posInDst,
                                                        count,
                                                        readAllCount,
                                                        writeAllRead,
                                                        cancelIfHugeCopy,
                                                        checkAdjacentDstBytes,
                                                        mightOverlap);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * 
     */

    private static void test_readXXXAndWriteXXX_goodArgs(boolean staticCalls, boolean srcIsBB, boolean dstIsBB) {
        for (ByteCopyUtils utils : variousBCU(staticCalls)) {
            for (MyAbstractContainer srcCont : srcIsBB ? variousBBC_allCases(true) : variousFCC_allCases(true)) {
                if (!srcCont.getReadable()) {
                    continue;
                }
                final long srcSize = srcCont.size();
                for (MyAbstractContainer dstCont : dstIsBB ? variousBBC_allCases(false) : variousFCC_allCases(false)) {
                    if (!dstCont.getWritable()) {
                        continue;
                    }
                    final long dstSize = dstCont.size();
                    /*
                     * Some copies near 0, some copies near
                     * size (a huge value for channels).
                     * Need to have a position equal to size,
                     * to allow for testing channels in append mode.
                     */
                    for (long srcPos : new long[]{
                            0,1,MAX_NON_HUGE_COUNT/2, // near 0
                            srcSize-MAX_NON_HUGE_COUNT/2,srcSize-1, // near size
                            srcSize,srcSize+1}) { // >= size
                        for (boolean posInSrc : FALSE_TRUE) {
                            if (posInSrc && (!canDoPosInContainer(srcCont, srcPos, srcSize))) {
                                continue;
                            }
                            for (long dstPos : new long[]{
                                    0,1,MAX_NON_HUGE_COUNT/2, // near 0
                                    dstSize-MAX_NON_HUGE_COUNT/2,dstSize-1, // near size
                                    dstSize, dstSize+1}) { // >= size
                                for (boolean posInDst : FALSE_TRUE) {
                                    if (posInDst && (!canDoPosInContainer(dstCont, dstPos, dstSize))) {
                                        continue;
                                    }
                                    for (long count : new long[]{
                                            0,1, // small
                                            MAX_NON_HUGE_COUNT/2, // mid
                                            MAX_NON_HUGE_COUNT}) { // max
                                        for (boolean readAllCount : FALSE_TRUE) {
                                            for (boolean writeAllRead : FALSE_TRUE) {
                                                if (readAllCount && (!writeAllRead)) {
                                                    // not applicable
                                                    continue;
                                                }
                                                // Should not change in these tests.
                                                assertEquals(srcSize,srcCont.size());
                                                resetSizeAndFillIfWritable(dstCont,dstSize);
                                                dstCont.size(dstSize);
                                                final boolean cancelIfHugeCopy = false;
                                                final boolean checkAdjacentDstBytes = true;
                                                final boolean mightOverlap = false;
                                                callAndCheck_readXXXAndWriteXXX(
                                                        utils,
                                                        srcCont,
                                                        dstCont,
                                                        srcPos,
                                                        dstPos,
                                                        posInSrc,
                                                        posInDst,
                                                        count,
                                                        readAllCount,
                                                        writeAllRead,
                                                        cancelIfHugeCopy,
                                                        checkAdjacentDstBytes,
                                                        mightOverlap);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * 
     */

    private static void tezt_readXXXAndWriteXXX_overlapping_instance_BB_BB_impl() {
        final boolean staticCalls = false;
        for (int count=2;count<=MAX_COUNT_IO;count*=2) {
            final int size = 2*count;
            final int size_0_5 = size/2;
            final int size_0_25 = size/4;
            final int size_0_75 = size_0_5 + size_0_25;
            if (DEBUG) {
                System.out.println("count = "+count);
            }
            // Since copy size might be large, we don't want to have multiple
            // ByteBuffers out there in memory, so we take care of it,
            // by not iterating naively on a list of ByteBuffers.
            // The good news is that we just need one direct or one
            // heap ByteBuffer, so a simple false/true loop makes it.
            for (boolean direct : FALSE_TRUE) {
                System.gc(); // making room
                final ByteBuffer src;
                if (direct) {
                    src = fill(ByteBuffer.allocateDirect(size));
                } else {
                    src = fill(ByteBuffer.allocate(size));
                }
                for (ByteCopyUtils utils : variousBCU_BB_overlapping(staticCalls)) {
                    if (DEBUG) {
                        System.out.println("utils = "+utils);
                    }
                    for (boolean sameInstance : FALSE_TRUE) {
                        final ByteBuffer dst;
                        if (sameInstance) {
                            dst = src;
                        } else {
                            /*
                             * Example with size=8
                             * src: [0,1,2,3,4,5,6,7]
                             * dst:     [2,3,4,5]
                             * srcPos={0=need to loop backward (bytes moving forward),
                             *         size/2=need to loop forward (bytes moving backward)}
                             * dstPos=0
                             * count=cap/2
                             */
                            src.limit(size_0_75);
                            src.position(size_0_25);
                            dst = src.slice();
                        }
                        for (boolean needBackwardLoop : FALSE_TRUE) {
                            for (boolean posInSrc : FALSE_TRUE) {
                                for (boolean posInDst : FALSE_TRUE) {
                                    final int srcPos = (needBackwardLoop ? 0 : size_0_5);
                                    // If (src == dst), and posInSrc and posInDst, need (srcPoc == dstPos).
                                    final int dstPos = (sameInstance ? ((posInSrc && posInDst) ? srcPos : size_0_25) : 0);
                                    final boolean readAllCount = true;
                                    final boolean writeAllRead = true;
                                    for (boolean srcReadOnly : FALSE_TRUE) {
                                        if (sameInstance && srcReadOnly) {
                                            // Can't do.
                                            continue;
                                        }
                                        src.limit(src.capacity());
                                        dst.limit(dst.capacity());
                                        fillIfWritable(src);
                                        final boolean cancelIfHugeCopy = false;
                                        final boolean checkAdjacentDstBytes = true;
                                        final boolean mightOverlap = true;
                                        callAndCheck_readXXXAndWriteXXX(
                                                utils,
                                                new MyBBContainer(srcReadOnly ? src.asReadOnlyBuffer() : src),
                                                new MyBBContainer(sameInstance ? src : dst),
                                                srcPos,
                                                dstPos,
                                                posInSrc,
                                                posInDst,
                                                count,
                                                readAllCount,
                                                writeAllRead,
                                                cancelIfHugeCopy,
                                                checkAdjacentDstBytes,
                                                mightOverlap);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void tezt_readXXXAndWriteXXX_overlapping_instance_FC_FC_mock_impl() {
        final boolean staticCalls = false;
        /*
         * No need huge copy sizes (no underlying actual IO treatments to test).
         * 
         * Need to use MockFileChannels that are:
         * - identical,
         * - or backed by different ByteBufferMockBuffers
         *   which own ByteBuffers share memory,
         * We don't use different MockFileChannels backed by a same mock buffer,
         * since the mock buffer holds channel's size.
         * 
         * Also, we only need overlapping to be handled for writes
         * (which MockFileChannel does), since implementation uses
         * a temporary ByteBuffer when using reads.
         */
        final int maxCount = Math.min(1024,MAX_COUNT_IO);
        for (int count=2;count<=maxCount;count*=2) {
            final int size = 2*count;
            final int size_0_5 = size/2;
            final int size_0_25 = size/4;
            // Only used if no static calls.
            // Big chunks, but < count.
            final int maxChunkSize = Math.max(1, count/2);
            if (DEBUG) {
                System.out.println("count = "+count);
                System.out.println("maxChunkSize = "+maxChunkSize);
            }
            /*
             * Using heap ByteBuffers, for then ByteBuffer.put(ByteBuffer) method
             * delegates to System.arraycopy(...), to make sure backing copies
             * are overlapping-proof (even though mock buffer's bulk put method
             * is supposed to be overlapping-proof anyway).
             */
            System.gc(); // making room
            final ByteBuffer srcBB = ByteBuffer.allocate(size);
            final MockFileChannel src = newMockFileChannel(size, srcBB);
            for (boolean sameInstance : FALSE_TRUE) {
                final ByteBuffer dstBB;
                final MockFileChannel dst;
                if (sameInstance) {
                    dstBB = srcBB;
                    dst = src;
                } else {
                    dstBB = srcBB.duplicate();
                    dst = newMockFileChannel(size, dstBB);
                }
                final boolean io = false;
                for (ByteCopyUtils utils : variousBCU_FC_overlapping(staticCalls, maxChunkSize, io)) {
                    if (DEBUG) {
                        System.out.println("utils = "+utils);
                    }
                    for (boolean needBackwardLoop : FALSE_TRUE) {
                        for (boolean posInSrc : FALSE_TRUE) {
                            for (boolean posInDst : FALSE_TRUE) {
                                final int srcPos = (needBackwardLoop ? 0 : size_0_5);
                                // If (src == dst), and posInSrc and posInDst, need (srcPoc == dstPos).
                                final int dstPos = (sameInstance && (posInSrc && posInDst) ? srcPos : size_0_25);
                                final boolean readAllCount = true;
                                final boolean writeAllRead = true;

                                srcBB.limit(size);
                                if (!sameInstance) {
                                    dstBB.limit(size);
                                }
                                fillIfWritable(src);
                                final boolean cancelIfHugeCopy = false;
                                final boolean checkAdjacentDstBytes = true;
                                final boolean mightOverlap = true;
                                callAndCheck_readXXXAndWriteXXX(
                                        utils,
                                        new MyFCContainer(src),
                                        new MyFCContainer(sameInstance ? src : dst),
                                        srcPos,
                                        dstPos,
                                        posInSrc,
                                        posInDst,
                                        count,
                                        readAllCount,
                                        writeAllRead,
                                        cancelIfHugeCopy,
                                        checkAdjacentDstBytes,
                                        mightOverlap);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Uses actual FileChannels, to test that actual channel IO treatments
     * are overlapping-proof.
     * 
     * Since most of our code is covered by overlapping tests using MockFileChannel,
     * and we don't want to burn the storage device too much with unit tests,
     * here we just test that FileChannel.write(...) is overlapping-proof,
     * by using ByteCopyUtils instances configured with DefaultMBBHelper
     * and various chunk sizes.
     */
    private static void tezt_readXXXAndWriteXXX_overlapping_instance_FC_FC_io_impl() {
        // Using random arguments, in case treatments actually mess-up
        // with unthought combinations of counts, chunk sizes and byte shifts.
        final Random random = new Random(123456789L);
        // Doing all with a single file.
        final File file = TestUtils.newTempFile();
        final boolean appendMode = false;
        // Used if not using dst as src.
        final FCHolder otherSrcFCH = new FCHolder(file, "r");
        final FCHolder dstFCH = new FCHolder(file, "rw");
        
        final int maxSize = MAX_COUNT_IO;
        
        try {
            final int nbrOfCopies = NBR_OF_RANDOM_COPIES_IO;
            for (int k=0;k<nbrOfCopies;k++) {
                final int size = 1 + random.nextInt(maxSize);
                final int srcPos = random.nextInt(size);
                final int dstPos = random.nextInt(size);
                final int maxRem = Math.min(size-srcPos, size-dstPos);
                final int count = 1 + random.nextInt(maxRem);
                // Sometimes < count, sometimes not.
                final int maxChunkSize = 1 + random.nextInt(2*count);
                if (DEBUG) {
                    System.out.println("count = "+count);
                    System.out.println("maxChunkSize = "+maxChunkSize);
                }
                
                System.gc(); // making room
                final ByteCopyUtils utils = new ByteCopyUtils(
                        DefaultMBBHelper.INSTANCE,
                        false, // threadSafe
                        maxChunkSize,
                        maxChunkSize,
                        DEFAULT_TEST_MBB_THRESHOLD,
                        maxChunkSize);
                if (DEBUG) {
                    System.out.println("utils = "+utils);
                }
                for (boolean sameInstance : FALSE_TRUE) {
                    if (DEBUG) {
                        System.out.println("sameInstance = "+sameInstance);
                    }
                    final FCHolder srcFCH;
                    if (sameInstance) {
                        srcFCH = dstFCH;
                    } else {
                        srcFCH = otherSrcFCH;
                    }

                    final boolean posInSrc = false;
                    final boolean posInDst = false;
                    final boolean readAllCount = true;
                    final boolean writeAllRead = true;

                    dstFCH.setSizeAndFill(size);
                    if (!sameInstance) {
                        // size is in file
                        assertEquals(size, UncheckedIO.size(srcFCH.getFC()));
                    }
                    final boolean cancelIfHugeCopy = false;
                    final boolean checkAdjacentDstBytes = true;
                    final boolean mightOverlap = true;
                    callAndCheck_readXXXAndWriteXXX(
                            utils,
                            new MyFCHContainer(srcFCH, appendMode),
                            new MyFCHContainer(dstFCH, appendMode),
                            srcPos,
                            dstPos,
                            posInSrc,
                            posInDst,
                            count,
                            readAllCount,
                            writeAllRead,
                            cancelIfHugeCopy,
                            checkAdjacentDstBytes,
                            mightOverlap);
                }
            }
        } finally {
            otherSrcFCH.release();
            dstFCH.release();
        }
    }
    
    /*
     * 
     */

    private void tezt_readXXXAndWriteXXX_basic_BB_FC_io_impl() {
        final Random random = new Random(123456789L);
        final File dstFile = TestUtils.newTempFile();
        final boolean appendMode = false;
        final FCHolder dstFCH = new FCHolder(dstFile, "rw");
        
        final int maxSize = MAX_COUNT_IO;
        
        try {
            final int nbrOfCopies = NBR_OF_RANDOM_COPIES_IO;
            for (int k=0;k<nbrOfCopies;k++) {
                final boolean posInSrc = random.nextBoolean();
                final boolean posInDst = random.nextBoolean();
                final boolean readAllCount = random.nextBoolean();
                final boolean writeAllRead = readAllCount || random.nextBoolean();
                
                final int srcSize = 1 + random.nextInt(maxSize);
                final int dstSize = 1 + random.nextInt(maxSize);
                final int srcPos = random.nextInt(srcSize);
                final int dstPos = random.nextInt(posInDst ? dstSize : maxSize);
                final int count = 1 + random.nextInt(readAllCount ? srcSize-srcPos : maxSize);
                // Sometimes < count, sometimes not.
                final int maxChunkSize = 1 + random.nextInt(2*count);
                
                final boolean srcDirect = random.nextBoolean();
                final ByteBuffer src = (srcDirect ? ByteBuffer.allocateDirect(2*MAX_COUNT_IO) : ByteBuffer.allocate(2*MAX_COUNT_IO));
                final boolean srcReadOnly = random.nextBoolean();

                System.gc(); // making room
                for (ByteCopyUtils utils : variousBCU_io(maxChunkSize)) {
                    fill(src);
                    dstFCH.setSizeAndFill(dstSize);
                    final boolean cancelIfHugeCopy = false;
                    final boolean checkAdjacentDstBytes = true;
                    final boolean mightOverlap = false;
                    callAndCheck_readXXXAndWriteXXX(
                            utils,
                            new MyBBContainer(srcReadOnly ? src.asReadOnlyBuffer() : src),
                            new MyFCHContainer(dstFCH, appendMode),
                            srcPos,
                            dstPos,
                            posInSrc,
                            posInDst,
                            count,
                            readAllCount,
                            writeAllRead,
                            cancelIfHugeCopy,
                            checkAdjacentDstBytes,
                            mightOverlap);
                }
            }
        } finally {
            dstFCH.release();
        }
    }

    private void tezt_readXXXAndWriteXXX_basic_FC_BB_io_impl() {
        final Random random = new Random(123456789L);
        final File srcFile = TestUtils.newTempFile();
        final boolean appendMode = false;
        final FCHolder srcFCH = new FCHolder(srcFile, "rw");
        
        final int maxSize = MAX_COUNT_IO;
        
        try {
            final int nbrOfCopies = NBR_OF_RANDOM_COPIES_IO;
            for (int k=0;k<nbrOfCopies;k++) {
                final boolean posInSrc = random.nextBoolean();
                final boolean posInDst = random.nextBoolean();
                final boolean readAllCount = random.nextBoolean();
                final boolean writeAllRead = readAllCount || random.nextBoolean();
                
                final int srcSize = 1 + random.nextInt(maxSize);
                final int dstSize = 1 + random.nextInt(maxSize);
                final int srcPos = random.nextInt(srcSize);
                final int dstPos = random.nextInt(posInDst ? dstSize : maxSize);
                final int count = 1 + random.nextInt(readAllCount ? srcSize-srcPos : maxSize);
                // Sometimes < count, sometimes not.
                final int maxChunkSize = 1 + random.nextInt(2*count);

                final boolean dstDirect = random.nextBoolean();
                final ByteBuffer dst = (dstDirect ? ByteBuffer.allocateDirect(2*MAX_COUNT_IO) : ByteBuffer.allocate(2*MAX_COUNT_IO));
                
                System.gc(); // making room
                for (ByteCopyUtils utils : variousBCU_io(maxChunkSize)) {
                    srcFCH.setSizeAndFill(srcSize);
                    fill(dst);
                    final boolean cancelIfHugeCopy = false;
                    final boolean checkAdjacentDstBytes = true;
                    final boolean mightOverlap = false;
                    callAndCheck_readXXXAndWriteXXX(
                            utils,
                            new MyFCHContainer(srcFCH, appendMode),
                            new MyBBContainer(dst),
                            srcPos,
                            dstPos,
                            posInSrc,
                            posInDst,
                            count,
                            readAllCount,
                            writeAllRead,
                            cancelIfHugeCopy,
                            checkAdjacentDstBytes,
                            mightOverlap);
                }
            }
        } finally {
            srcFCH.release();
        }
    }

    private void tezt_readXXXAndWriteXXX_basic_FC_FC_io_impl() {
        final Random random = new Random(123456789L);
        final File srcFile = TestUtils.newTempFile();
        final File dstFile = TestUtils.newTempFile();
        final boolean appendMode = false;
        final FCHolder srcFCH = new FCHolder(srcFile, "rw");
        final FCHolder dstFCH = new FCHolder(dstFile, "rw");
        
        final int maxSize = MAX_COUNT_IO;
        
        try {
            final int nbrOfCopies = NBR_OF_RANDOM_COPIES_IO;
            for (int k=0;k<nbrOfCopies;k++) {
                final boolean posInSrc = random.nextBoolean();
                final boolean posInDst = random.nextBoolean();
                final boolean readAllCount = random.nextBoolean();
                final boolean writeAllRead = readAllCount || random.nextBoolean();
                
                final int srcSize = 1 + random.nextInt(maxSize);
                final int dstSize = 1 + random.nextInt(maxSize);
                final int srcPos = random.nextInt(srcSize);
                final int dstPos = random.nextInt(posInDst ? dstSize : maxSize);
                final int count = 1 + random.nextInt(readAllCount ? srcSize-srcPos : maxSize);
                // Sometimes < count, sometimes not.
                final int maxChunkSize = 1 + random.nextInt(2*count);
                
                System.gc(); // making room
                for (ByteCopyUtils utils : variousBCU_io(maxChunkSize)) {
                    srcFCH.setSizeAndFill(srcSize);
                    dstFCH.setSizeAndFill(dstSize);
                    final boolean cancelIfHugeCopy = false;
                    final boolean checkAdjacentDstBytes = true;
                    final boolean mightOverlap = false;
                    callAndCheck_readXXXAndWriteXXX(
                            utils,
                            new MyFCHContainer(srcFCH, appendMode),
                            new MyFCHContainer(dstFCH, appendMode),
                            srcPos,
                            dstPos,
                            posInSrc,
                            posInDst,
                            count,
                            readAllCount,
                            writeAllRead,
                            cancelIfHugeCopy,
                            checkAdjacentDstBytes,
                            mightOverlap);
                }
            }
        } finally {
            try {
                srcFCH.release();
            } finally {
                dstFCH.release();
            }
        }
    }

    /*
     * 
     */

    private static void test_readXXXAndWriteXXX_concurrency(
            final boolean threadSafeUtils,
            final boolean staticCalls,
            final boolean srcIsBB,
            final boolean dstIsBB) {
        if (staticCalls && (!threadSafeUtils)) {
            throw new AssertionError();
        }
        final ExecutorService executor = Executors.newCachedThreadPool();
        for (boolean posInSrc : FALSE_TRUE) {
            for (boolean posInDst : FALSE_TRUE) {
                if (posInSrc && posInDst) {
                    // irrelevant
                    continue;
                }

                if (staticCalls) {
                    test_readXXXAndWriteXXX_conc_posInXXX(
                            executor,
                            srcIsBB,
                            dstIsBB,
                            null,
                            null,
                            posInSrc,
                            posInDst);
                } else {
                    for (ByteCopyUtils utils : variousBCU(threadSafeUtils)) {
                        test_readXXXAndWriteXXX_conc_posInXXX(
                                executor,
                                srcIsBB,
                                dstIsBB,
                                utils,
                                (threadSafeUtils ? utils : A_THREAD_SAFE_BCU),
                                posInSrc,
                                posInDst);
                    }
                }
            }
        }
        Unchecked.shutdownAndAwaitTermination(executor);
    }


    /**
     * @param thread1Utils Can be null, in which case static treatments are used.
     * @param thread2Utils Can be null, in which case static treatments are used.
     */
    private static void test_readXXXAndWriteXXX_conc_posInXXX(
            final Executor executor,
            final boolean srcIsBB,
            final boolean dstIsBB,
            final ByteCopyUtils thread1Utils,
            final ByteCopyUtils thread2Utils,
            final boolean posInSrc,
            final boolean posInDst) {
        if (posInSrc && posInDst) {
            // Can't use src or dst concurrently.
            throw new AssertionError();
        }

        for (MyAbstractContainer srcCont : srcIsBB ? variousBBC_concurrency() : variousFCC_concurrency()) {
            if (!srcCont.getReadable()) {
                continue;
            }
            final MyAbstractContainer srcCont1 = srcCont;
            final MyAbstractContainer srcCont2 = (posInSrc ? dummyContainer_concurrency(srcIsBB) : srcCont);
            for (MyAbstractContainer dstCont : dstIsBB ? variousBBC_concurrency() : variousFCC_concurrency()) {
                if (!dstCont.getWritable()) {
                    continue;
                }
                final MyAbstractContainer dstCont1 = dstCont;
                final MyAbstractContainer dstCont2 = (posInDst ? dummyContainer_concurrency(dstIsBB) : dstCont);

                /*
                 * 
                 */

                final int nbrOfCallsPerThread = CONCURRENCY_NBR_OF_CALLS_PER_THREAD;
                final AtomicInteger countdown = new AtomicInteger(2);

                final String context;
                if (DEBUG) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("thread1Utils = "+thread1Utils+"\n");
                    sb.append("thread2Utils = "+thread2Utils+"\n");
                    sb.append("src1 = "+srcCont1+"\n");
                    sb.append("src2 = "+srcCont2+"\n");
                    sb.append("dst1 = "+dstCont1+"\n");
                    sb.append("dst2 = "+dstCont2+"\n");
                    context = sb.toString();
                } else {
                    context = null;
                }

                /*
                 * src's and dst's initially all contain (byte)position content.
                 * Each thread resets its dst's content, in its own range,
                 * after checking is has correctly been copied.
                 * 
                 * For dst's MockFileChannel's VirtualMockBuffer implementation,
                 * we need to initially create the local buffer over the whole
                 * copy range, because its creation (and deletion) is not thread-safe.
                 * Note: could as well use a MockFileChannel backed by a ByteBufferMockBuffer,
                 * but that wouldn't allow copies in ranges of huge positions, if we ever do it.
                 */

                if (dstCont1 instanceof MyFCContainer) {
                    MockFileChannel fc = ((MyFCContainer)dstCont1).fc;
                    VirtualMockBuffer mb = (VirtualMockBuffer)fc.getBackingMockBuffer();
                    mb.put(0, (byte)0);
                }
                if (dstCont2 != dstCont1) {
                    if (dstCont2 instanceof MyFCContainer) {
                        MockFileChannel fc = ((MyFCContainer)dstCont2).fc;
                        VirtualMockBuffer mb = (VirtualMockBuffer)fc.getBackingMockBuffer();
                        mb.put(0, (byte)0);
                    }
                }

                /*
                 * thread 1
                 */

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        copyMany(
                                context,
                                thread1Utils,
                                true, // isFirstThread
                                srcCont1,
                                dstCont1,
                                posInSrc,
                                posInDst,
                                nbrOfCallsPerThread);
                        if (countdown.decrementAndGet() == 0) {
                            synchronized (countdown) {
                                countdown.notifyAll();
                            }
                        }
                    }
                });

                /*
                 * thread 2
                 */

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        copyMany(
                                context,
                                thread2Utils,
                                false, // isFirstThread
                                srcCont2,
                                dstCont2,
                                posInSrc,
                                posInDst,
                                nbrOfCallsPerThread);
                        if (countdown.decrementAndGet() == 0) {
                            synchronized (countdown) {
                                countdown.notifyAll();
                            }
                        }
                    }
                });

                /*
                 * 
                 */

                synchronized (countdown) {
                    while (true) {
                        Unchecked.waitMS(countdown, COUNTDOWN_LOG_DELAY_MS);
                        if (countdown.get() == 0) {
                            break;
                        }
                        System.out.println("waiting... countdown = "+countdown);
                    }
                }
            }
        }
    }

    /**
     * @param nbrOfCalls Number of copies for each possible
     *        set of parameters for generic copy method,
     *        i.e. about for each possible specific copy method.
     */
    private static void copyMany(
            final String context,
            final ByteCopyUtils utils,
            final boolean isFirstThread,
            final MyAbstractContainer srcCont,
            final MyAbstractContainer dstCont,
            final boolean posInSrc,
            final boolean posInDst,
            final int nbrOfCalls) {
        final long posOffset = (isFirstThread ? 0 : MAX_NON_HUGE_COUNT);
        final long srcPos = posOffset;
        final long dstPos = posOffset+1;
        final long count = MAX_NON_HUGE_COUNT-1;
        for (boolean readAllCount : FALSE_TRUE) {
            for (boolean writeAllRead : FALSE_TRUE) {
                final String msg;
                if (DEBUG) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("---\n");
                    sb.append(context+"\n");
                    sb.append("utils = "+utils+"\n");
                    sb.append("src = "+srcCont+"\n");
                    sb.append("dst = "+dstCont+"\n");
                    sb.append("srcPos = "+srcPos+"\n");
                    sb.append("dstPos = "+dstPos+"\n");
                    sb.append("posInSrc = "+posInSrc+"\n");
                    sb.append("posInDst = "+posInDst+"\n");
                    sb.append("readAllCount = "+readAllCount+"\n");
                    sb.append("writeAllRead = "+writeAllRead+"\n");
                    msg = sb.toString();
                } else {
                    msg = "";
                }
                for (int k=0;k<nbrOfCalls;k++) {
                    /*
                     * reseting dst in the range our thread copies into
                     */
                    for (int i=0;i<count;i++) {
                        final long pos = dstPos + i;
                        dstCont.put(pos, (byte)pos);
                    }

                    final boolean cancelIfHugeCopy = false;
                    final boolean checkAdjacentDstBytes = false;
                    final boolean mightOverlap = false;
                    final boolean didThrow = callAndCheck_readXXXAndWriteXXX(
                            utils,
                            srcCont,
                            dstCont,
                            srcPos,
                            dstPos,
                            posInSrc,
                            posInDst,
                            count,
                            readAllCount,
                            writeAllRead,
                            cancelIfHugeCopy,
                            checkAdjacentDstBytes,
                            mightOverlap);
                    assertFalse(msg, didThrow);
                }
            }
        }
    }
    
    /*
     * 
     */

    /**
     * @param cancelIfHugeCopy If true, and expected copy is huge with
     *        no exception expected, just cancels the copy and returns false.
     *        Useful not to start taking crazy time and memory.
     * @param checkAdjacentDstBytes Should be false for concurrency tests,
     *        in which dst might be shared, for each thread to only check
     *        its part of it.
     * @return True if did throw, false otherwise.
     */
    private static boolean callAndCheck_readXXXAndWriteXXX(
            final ByteCopyUtils utils,
            final MyAbstractContainer srcCont,
            final MyAbstractContainer dstCont,
            final long srcPos,
            final long dstPos,
            final boolean posInSrc,
            final boolean posInDst,
            final long count,
            final boolean readAllCount,
            final boolean writeAllRead,
            final boolean cancelIfHugeCopy,
            final boolean checkAdjacentDstBytes,
            final boolean mightOverlap) {

        final boolean sameInstance = (srcCont.getBackingContainer() == dstCont.getBackingContainer());

        /*
         * 
         */

        final long srcInitialSize = srcCont.size();
        final long dstInitialSize = dstCont.size();

        // For concurrency test, and for each container
        // that is shared between threads, unused position
        // must be the same for both threads, since each of them
        // sets and checks it.
        // Since it's computed from size, it is the case.
        final long unusedSrcPos = srcCont.size()/2;
        final long unusedDstPos = dstCont.size()/2;
        
        if (posInSrc) {
            if (!canDoPosInContainer(srcCont, srcPos, srcInitialSize)) {
                throw new AssertionError();
            }
            srcCont.position(srcPos);
        } else {
            if (sameInstance && posInDst) {
                // used for dst
            } else {
                srcCont.position(unusedSrcPos);
            }
        }
        if (posInDst) {
            if (!canDoPosInContainer(dstCont, dstPos, dstInitialSize)) {
                throw new AssertionError();
            }
            dstCont.position(dstPos);
        } else {
            if (sameInstance && posInSrc) {
                // not erasing it
            } else {
                dstCont.position(unusedDstPos);
            }
        }

        final long srcRemFromPos = Math.max(0, srcInitialSize - srcPos);
        final long nReadable = Math.min(srcRemFromPos, count);

        // If no throw, i.e. if count = 0,
        // or count > 0 and src/dst big enough,
        // src readable and dst writable.
        final long expectedNDone;
        if (readAllCount) {
            expectedNDone = count;
        } else if (writeAllRead) {
            expectedNDone = nReadable;
        } else {
            expectedNDone = Math.min(nReadable, Math.max(0, dstInitialSize - dstPos));
        }

        final boolean readWriteCalls = (expectedNDone > 0) && (nReadable > 0);

        // Order matters, i.e. "must throw" if
        // and only if no throw expected already.
        final boolean mustThrow_IllegalArgumentException = (srcPos < 0) || (dstPos < 0) || (count < 0);
        // handles arithmetic overflow
        final boolean mustThrow_BufferUnderflowException = (expectedNDone > 0) && (readAllCount && (srcPos > srcInitialSize - count));
        // handles arithmetic overflow
        final boolean mustThrow_BufferOverflowException = (expectedNDone > 0) && (writeAllRead && (dstPos > dstCont.capacity() - nReadable));
        final boolean mustThrow_NonReadableChannelException = readWriteCalls && (srcCont instanceof MyFCContainer) && (!srcCont.getReadable());
        final boolean mustThrow_ReadOnlyBufferException = readWriteCalls && (dstCont instanceof MyBBContainer) && (!dstCont.getWritable());
        final boolean mustThrow_NonWritableChannelException = readWriteCalls && (dstCont instanceof MyFCContainer) && (!dstCont.getWritable());
        final boolean mustThrow = (mustThrow_IllegalArgumentException
                || mustThrow_BufferUnderflowException
                || mustThrow_BufferOverflowException
                || mustThrow_NonReadableChannelException
                || mustThrow_ReadOnlyBufferException
                || mustThrow_NonWritableChannelException);

        if (cancelIfHugeCopy) {
            final long threshold = Long.MAX_VALUE/10;
            if ((expectedNDone > threshold) && (!mustThrow)) {
                return false;
            }
        }
        
        /*
         * 
         */

        final String msg;
        if (DEBUG) {
            final StringBuilder sb = new StringBuilder();
            sb.append("---\n");
            sb.append("utils = "+utils+"\n");
            sb.append("src = "+srcCont+"\n");
            sb.append("dst = "+dstCont+"\n");
            sb.append("srcPos = "+srcPos+"\n");
            sb.append("dstPos = "+dstPos+"\n");
            sb.append("posInSrc = "+posInSrc+"\n");
            sb.append("posInDst = "+posInDst+"\n");
            sb.append("count = "+count+"\n");
            sb.append("readAllCount = "+readAllCount+"\n");
            sb.append("writeAllRead = "+writeAllRead+"\n");
            sb.append("checkAdjacentDstBytes = "+checkAdjacentDstBytes+"\n");
            sb.append("overlapping = "+mightOverlap+"\n");
            sb.append("sameInstance = "+sameInstance+"\n");
            sb.append("readWriteCalls = "+readWriteCalls+"\n");
            sb.append("expectedNDone = "+expectedNDone+"\n");
            sb.append("mustThrow = "+mustThrow+"\n");
            sb.append("mustThrow_IllegalArgumentException = "+mustThrow_IllegalArgumentException+"\n");
            sb.append("mustThrow_BufferUnderflowException = "+mustThrow_BufferUnderflowException+"\n");
            sb.append("mustThrow_BufferOverflowException = "+mustThrow_BufferOverflowException+"\n");
            sb.append("mustThrow_NonReadableChannelException = "+mustThrow_NonReadableChannelException+"\n");
            sb.append("mustThrow_ReadOnlyBufferException = "+mustThrow_ReadOnlyBufferException+"\n");
            sb.append("mustThrow_NonWritableChannelException = "+mustThrow_NonWritableChannelException+"\n");
            msg = sb.toString();
        } else {
            msg = "";
        }
        
        if (DEBUG) {
            System.out.println("bef : src = "+srcCont.toStringContent());
            System.out.println("bef : dst = "+dstCont.toStringContent());
        }
        
        /*
         * 
         */

        final long srcPreCopyPos = srcCont.position();
        final long dstPreCopyPos = dstCont.position();
        final long srcPreCopySize = srcCont.size();
        final long dstPreCopySize = dstCont.size();

        long nDone = Long.MIN_VALUE;
        Exception thrown = null;
        try {
            nDone = call_readXXXAndWriteXXX(
                    utils,
                    srcCont.getBackingContainer(),
                    dstCont.getBackingContainer(),
                    srcPos,
                    dstPos,
                    posInSrc,
                    posInDst,
                    count,
                    readAllCount,
                    writeAllRead);

            if (DEBUG) {
                System.out.println("aft : src = "+srcCont.toStringContent());
                System.out.println("aft : dst = "+dstCont.toStringContent());
            }

            assertFalse(msg, mustThrow);

            /*
             * checking scalars
             */

            assertEquals(msg, expectedNDone, nDone);

            if (posInSrc) {
                assertEquals(msg, srcPos + nDone, srcCont.position());
            } else {
                if (srcCont.getAppendMode()) {
                    assertEquals(msg, srcCont.size(), srcCont.position());
                } else {
                    if (sameInstance && posInDst) {
                        assertEquals(msg, dstCont.position(), srcCont.position());
                    } else {
                        assertEquals(msg, unusedSrcPos, srcCont.position());
                    }
                }
            }
            if (posInDst) {
                assertEquals(msg, dstPos + nDone, dstCont.position());
            } else {
                if (dstCont.getAppendMode()) {
                    assertEquals(msg, dstCont.size(), dstCont.position());
                } else {
                    if (sameInstance && posInSrc) {
                        assertEquals(msg, srcCont.position(), dstCont.position());
                    } else {
                        assertEquals(msg, unusedDstPos, dstCont.position());
                    }
                }
            }

            assertEquals(msg, srcInitialSize, srcCont.size());
            if (writeAllRead && (nDone > 0)) {
                assertEquals(msg, Math.max(dstInitialSize, dstPos + nDone), dstCont.size());
            } else {
                assertEquals(msg, dstInitialSize, dstCont.size());
            }

            /*
             * Checking src didn't change.
             * Checking whole range (even part of other thread)
             * should not hurt.
             * If src is shared, it also allows to make sure that
             * other thread does not temporarily modifies its content
             * or size.
             */

            if (mightOverlap && (nDone != 0) && (!(sameInstance && (srcPos == dstPos)))) {
                // src has typically been modified
            } else {
                checkIsReset(msg, srcCont);
            }

            /*
             * Checking dst bytes in range were copied.
             */

            checkContent(msg, dstCont, dstPos, nDone, (byte)(dstPos-srcPos));

            /*
             * Checking adjacent dst bytes didn't change.
             * We must take care not to check bytes that were created
             * but not copied, due to dstPos > dstInitialSize, and
             * which value is undefined.
             */

            if (checkAdjacentDstBytes) {
                final long preDstPos = dstPos-1;
                if ((preDstPos >= 0) && (preDstPos < dstInitialSize)) {
                    assertEquals(msg, (byte)preDstPos, dstCont.get(preDstPos));
                }
                final long postDstPos = dstPos+nDone;
                if ((postDstPos >= 0) && (postDstPos < dstCont.size())) {
                    assertEquals(msg, (byte)postDstPos, dstCont.get(postDstPos));
                }
            }
        } catch (IllegalArgumentException e) {
            thrown = e;
            if (!mustThrow_IllegalArgumentException) {
                throw new RuntimeException(msg,e);
            }

        } catch (BufferUnderflowException e) {
            thrown = e;
            if (!mustThrow_BufferUnderflowException) {
                throw new RuntimeException(msg,e);
            }

        } catch (BufferOverflowException e) {
            thrown = e;
            if (!mustThrow_BufferOverflowException) {
                throw new RuntimeException(msg,e);
            }

        } catch (NonReadableChannelException e) {
            thrown = e;
            if (!mustThrow_NonReadableChannelException) {
                throw new RuntimeException(msg,e);
            }

        } catch (ReadOnlyBufferException e) {
            thrown = e;
            if (!mustThrow_ReadOnlyBufferException) {
                throw new RuntimeException(msg,e);
            }

        } catch (NonWritableChannelException e) {
            thrown = e;
            if (!mustThrow_NonWritableChannelException) {
                throw new RuntimeException(msg,e);
            }

        } catch (Exception e) {
            // Can happen in case of full device ("no space left on device").
            throw new RuntimeException(msg,e);
        }
        
        if (thrown != null) {
            final boolean underOver =
                (thrown instanceof BufferUnderflowException)
                ||  (thrown instanceof BufferOverflowException);
            
            // src pos
            if (posInSrc && underOver) {
                // src pos might have changed (if thrown from post treatments)
            } else {
                assertEquals(msg, srcPreCopyPos, srcCont.position());
            }
            
            // dst pos
            if (posInDst && underOver) {
                // dst pos might have changed (if thrown from post treatments)
            } else {
                assertEquals(msg, dstPreCopyPos, dstCont.position());
            }
            
            // src size
            if ((srcCont.getBackingContainer() == dstCont.getBackingContainer())
                    && writeAllRead) {
                // src size might have changed.
            } else {
                assertEquals(msg, srcPreCopySize, srcCont.size());
            }
            
            // dst size
            if (writeAllRead) {
                // dst size might have changed
            } else {
                assertEquals(msg, dstPreCopySize, dstCont.size());
            }
        }

        return (thrown != null);
    }
    
    /*
     * 
     */
    
    private static boolean canDoPosInContainer(
            MyAbstractContainer container,
            long pos,
            long containerInitialSize) {
        if ((pos < 0)
                || ((container instanceof MyBBContainer) && (pos > containerInitialSize))) {
            return false;
        }
        if (container.getAppendMode() && (pos != container.position())) {
            return false;
        }
        return true;
    }
    
    /*
     * 
     */

    private static long call_readXXXAndWriteXXX(
            final ByteCopyUtils utils,
            final Object src,
            final Object dst,
            final long srcPos,
            final long dstPos,
            final boolean posInSrc,
            final boolean posInDst,
            final long count,
            final boolean readAllCount,
            final boolean writeAllRead) throws IOException {
        final boolean staticCalls = (utils == null);
        if (posInSrc) {
            if (posInDst) {
                if (readAllCount) {
                    if (staticCalls) {
                        ByteCopyUtils.readAllAndWriteAll(src, dst, count);
                    } else {
                        utils.readAllAndWriteAll_(src, dst, count);
                    }
                    return count;
                } else {
                    if (writeAllRead) {
                        if (staticCalls) {
                            return ByteCopyUtils.readAndWriteAll(src, dst, count);
                        } else {
                            return utils.readAndWriteAll_(src, dst, count);
                        }
                    } else {
                        if (staticCalls) {
                            return ByteCopyUtils.readAndWrite(src, dst, count);
                        } else {
                            return utils.readAndWrite_(src, dst, count);
                        }
                    }
                }
            } else {
                if (readAllCount) {
                    if (staticCalls) {
                        ByteCopyUtils.readAllAndWriteAllAt(src, dst, dstPos, count);
                    } else {
                        utils.readAllAndWriteAllAt_(src, dst, dstPos, count);
                    }
                    return count;
                } else {
                    if (writeAllRead) {
                        if (staticCalls) {
                            return ByteCopyUtils.readAndWriteAllAt(src, dst, dstPos, count);
                        } else {
                            return utils.readAndWriteAllAt_(src, dst, dstPos, count);
                        }
                    } else {
                        if (staticCalls) {
                            return ByteCopyUtils.readAndWriteAt(src, dst, dstPos, count);
                        } else {
                            return utils.readAndWriteAt_(src, dst, dstPos, count);
                        }
                    }
                }
            }
        } else {
            if (posInDst) {
                if (readAllCount) {
                    if (staticCalls) {
                        ByteCopyUtils.readAllAtAndWriteAll(src, srcPos, dst, count);
                    } else {
                        utils.readAllAtAndWriteAll_(src, srcPos, dst, count);
                    }
                    return count;
                } else {
                    if (writeAllRead) {
                        if (staticCalls) {
                            return ByteCopyUtils.readAtAndWriteAll(src, srcPos, dst, count);
                        } else {
                            return utils.readAtAndWriteAll_(src, srcPos, dst, count);
                        }
                    } else {
                        if (staticCalls) {
                            return ByteCopyUtils.readAtAndWrite(src, srcPos, dst, count);
                        } else {
                            return utils.readAtAndWrite_(src, srcPos, dst, count);
                        }
                    }
                }
            } else {
                if (readAllCount) {
                    if (staticCalls) {
                        ByteCopyUtils.readAllAtAndWriteAllAt(src, srcPos, dst, dstPos, count);
                    } else {
                        utils.readAllAtAndWriteAllAt_(src, srcPos, dst, dstPos, count);
                    }
                    return count;
                } else {
                    if (writeAllRead) {
                        if (staticCalls) {
                            return ByteCopyUtils.readAtAndWriteAllAt(src, srcPos, dst, dstPos, count);
                        } else {
                            return utils.readAtAndWriteAllAt_(src, srcPos, dst, dstPos, count);
                        }
                    } else {
                        if (staticCalls) {
                            return ByteCopyUtils.readAtAndWriteAt(src, srcPos, dst, dstPos, count);
                        } else {
                            return utils.readAtAndWriteAt_(src, srcPos, dst, dstPos, count);
                        }
                    }
                }
            }
        }
    }

    /*
     * 
     */

    private static void resetSizeAndFillIfWritable(
            MyAbstractContainer container,
            long size) {
        container.size(size);
        fillIfWritable(container);
    }
    
    private static void fillIfWritable(MyAbstractContainer container) {
        if (container instanceof MyBBContainer) {
            fillIfWritable((ByteBuffer)container.getBackingContainer());
        } else if (container instanceof MyFCContainer) {
            fillIfWritable((MyFCContainer)container);
        } else {
            // Supposing writable.
            setSizeAndFill((MyFCHContainer)container);
        }
    }

    private static void fillIfWritable(ByteBuffer bb) {
        if (!bb.isReadOnly()) {
            fill(bb);
        }
    }

    private static void fillIfWritable(MockFileChannel fc) {
        if (fc.getWritable()) {
            fill(fc);
        }
    }

    private static void fillIfWritable(MyFCContainer container) {
        fillIfWritable(container.fc);
    }

    private static void setSizeAndFill(MyFCHContainer container) {
        container.fch.setSizeAndFill(NumbersUtils.asInt(UncheckedIO.size(container.fch.getFC())));
    }

    private static ByteBuffer sliceAndFill(ByteBuffer bb, int offset) {
        return fill(((ByteBuffer)bb.position(offset)).slice());
    }

    /**
     * Using (byte)position as content.
     */
    private static ByteBuffer fill(ByteBuffer bb) {
        final int limit = bb.limit();
        bb.limit(bb.capacity());
        for (int i=0;i<bb.capacity();i++) {
            bb.put(i,(byte)i);
        }
        bb.limit(limit);
        return bb;
    }

    /**
     * Using (byte)position as content.
     */
    private static MockFileChannel fill(MockFileChannel fc) {
        if (fc.getBackingMockBuffer() instanceof VirtualMockBuffer) {
            VirtualMockBuffer mb = (VirtualMockBuffer)fc.getBackingMockBuffer();
            mb.deleteLocalBuffer();
        } else {
            ByteBufferMockBuffer mb = (ByteBufferMockBuffer)fc.getBackingMockBuffer();
            fill(mb.getBackingByteBuffer());
        }
        return fc;
    }

    /**
     * Checks that no put has been made (unless eventually non-modifying ones).
     * Only checks in [0,size[.
     */
    private static void checkIsReset(String msg, MyAbstractContainer container) {
        if (container instanceof MyFCContainer) {
            MyFCContainer cont = (MyFCContainer)container;
            final InterfaceMockBuffer mb = cont.getMB();
            if (mb instanceof VirtualMockBuffer) {
                if (((VirtualMockBuffer)mb).getLocalBufferElseNull() == null) {
                    // Quick case.
                    return;
                }
            }
        }
        checkContent(msg, container, 0, container.size(), 0);
    }
    
    /**
     * @param valueShift 0 to check that container is reset over the specified range,
     *        i.e. that it contains (byte)position, and (int/byte)(dstPos-srcPos)
     *        to check that it contains content corresponding to a copy from srcPos
     *        to dstPos, from a container that was reset.
     */
    private static void checkContent(
            String msg,
            MyAbstractContainer container,
            long position,
            long count,
            int valueShift) {
        if (container instanceof MyFCHContainer) {
            // IO: faster chunk by chunk.
            final int maxChunkSize = NumbersUtils.asInt(Math.min(count,MAX_IO_CHUNK_SIZE_FOR_CHECK));
            final ByteBuffer bb = ByteBuffer.allocateDirect(maxChunkSize);
            long nDone = 0;
            long tmpPos = position;
            while (nDone < count) {
                final long nRemaining = (count-nDone);
                final int tmpN = NumbersUtils.asInt(Math.min(nRemaining, maxChunkSize));
                container.get(tmpPos, bb, tmpN);
                for (int i=0;i<tmpN;i++) {
                    final long dstPos = tmpPos + i;
                    final byte expected = (byte)(dstPos - valueShift);
                    final byte actual = bb.get(i);
                    assertEquals(msg, expected, actual);
                }
                tmpPos += tmpN;
                nDone += tmpN;
            }
        } else {
            // No IO: faster byte by byte.
            for (long i=0;i<count;i++) {
                final long dstPos = position + i;
                final byte expected = (byte)(dstPos - valueShift);
                final byte actual = container.get(dstPos);
                assertEquals(msg, expected, actual);
            }
        }
    }

    /*
     * 
     */

    /**
     * Readable, writable, and not in append mode.
     */
    private static MockFileChannel newMockFileChannel(
            long size,
            int localBufferCapacity) {
        return newMockFileChannel(
                true,
                true,
                false,
                size,
                localBufferCapacity);
    }

    private static MockFileChannel newMockFileChannel(
            boolean readable,
            boolean writable,
            boolean appendMode,
            long size,
            int localBufferCapacity) {
        VirtualMockBuffer mb = new VirtualMockBuffer(localBufferCapacity);
        mb.limit(size);
        MockFileChannel fc = new MockFileChannel(
                mb,
                readable,
                writable,
                appendMode);
        return fc;
    }
    
    /*
     * MockFileChannels backed by a ByteBufferMockBuffer.
     */

    /**
     * Readable, writable, and not in append mode.
     */
    private static MockFileChannel newMockFileChannel(
            int size,
            ByteBuffer bb) {
        return newMockFileChannel(
                true,
                true,
                false,
                size,
                bb);
    }

    private static MockFileChannel newMockFileChannel(
            boolean readable,
            boolean writable,
            boolean appendMode,
            int size,
            ByteBuffer bb) {
        ByteBufferMockBuffer mb = new ByteBufferMockBuffer(bb);
        mb.limit(size);
        MockFileChannel fc = new MockFileChannel(
                mb,
                readable,
                writable,
                appendMode);
        return fc;
    }

    /*
     * 
     */

    /**
     * @param staticCalls If true, result contains a single null element.
     */
    private static ArrayList<ByteCopyUtils> variousBCU(boolean staticCalls) {
        ArrayList<ByteCopyUtils> result = new ArrayList<ByteCopyUtils>();
        if (staticCalls) {
            result.add(null);
        } else {
            result.addAll(variousBCUInstances(false));
            result.addAll(variousBCUInstances(true));
        }
        return result;
    }

    private static ArrayList<ByteCopyUtils> variousBCUInstances(boolean threadSafe) {
        ArrayList<ByteCopyUtils> result = new ArrayList<ByteCopyUtils>();
        for (InterfaceMBBHelper mbbHelper : new InterfaceMBBHelper[]{null,MockMBBHelper.INSTANCE}) {
            result.add(
                    new ByteCopyUtils(
                            mbbHelper,
                            threadSafe,
                            DEFAULT_TEST_MAX_WRITE_CHUNK_SIZE,
                            DEFAULT_TEST_MAX_TMP_CHUNK_SIZE,
                            DEFAULT_TEST_MBB_THRESHOLD,
                            DEFAULT_TEST_MAX_MBB_CHUNK_SIZE));
        }
        return result;
    }

    /**
     * @param staticCalls If true, result contains a single null element.
     */
    private static ArrayList<ByteCopyUtils> variousBCU_BB_overlapping(boolean staticCalls) {
        ArrayList<ByteCopyUtils> result = new ArrayList<ByteCopyUtils>();
        if (staticCalls) {
            result.add(null);
        } else {
            result.addAll(variousBCUInstances_BB_overlapping());
        }
        return result;
    }

    /**
     * Chunk size specified, to make large copies faster, and to use low level treatments
     * with various chunk sizes, in case that would make them behave differently.
     * 
     * Always thread-safe, considering instance-local temporary buffers BCUs
     * are tested by other and faster tests already.
     */
    private static ArrayList<ByteCopyUtils> variousBCUInstances_BB_overlapping() {
        ArrayList<ByteCopyUtils> result = new ArrayList<ByteCopyUtils>();
        // No need for helper since no MBB is involved for BB to BB copies.
        // Chunk size has no effect either.
        result.add(
                new ByteCopyUtils(
                        null, // mbbHelper
                        true, // threadSafe
                        DEFAULT_TEST_MAX_WRITE_CHUNK_SIZE,
                        DEFAULT_TEST_MAX_TMP_CHUNK_SIZE,
                        DEFAULT_TEST_MBB_THRESHOLD,
                        DEFAULT_TEST_MAX_MBB_CHUNK_SIZE));
        return result;
    }

    /**
     * @param staticCalls If true, result contains a single null element.
     * @param chunkSize Only used if no static calls.
     * @param io True if using actual FileChannels, false if using MockFileChannels.
     */
    private static ArrayList<ByteCopyUtils> variousBCU_FC_overlapping(
            boolean staticCalls,
            int chunkSize,
            boolean io) {
        ArrayList<ByteCopyUtils> result = new ArrayList<ByteCopyUtils>();
        if (staticCalls) {
            result.add(null);
        } else {
            result.addAll(variousBCUInstances_FC_overlapping(chunkSize,io));
        }
        return result;
    }

    /**
     * Chunk size specified, to make large copies faster, and to use low level treatments
     * (when using actual FileChannels) with various chunk sizes, in case that would make
     * them behave differently.
     * 
     * Never thread-safe, for temporary buffers to be GCed after use.
     * are tested by other and faster tests already.
     * 
     * @param io True if using actual FileChannels, false if using MockFileChannels.
     */
    private static ArrayList<ByteCopyUtils> variousBCUInstances_FC_overlapping(
            int chunkSize,
            boolean io) {
        ArrayList<ByteCopyUtils> result = new ArrayList<ByteCopyUtils>();
        final InterfaceMBBHelper usedHelper = (io ? DefaultMBBHelper.INSTANCE : MockMBBHelper.INSTANCE);
        for (InterfaceMBBHelper mbbHelper : new InterfaceMBBHelper[]{null,usedHelper}) {
            result.add(
                    new ByteCopyUtils(
                            mbbHelper,
                            true, // threadSafe
                            chunkSize,
                            chunkSize,
                            DEFAULT_TEST_MBB_THRESHOLD,
                            chunkSize));
        }
        return result;
    }
    
    /**
     * @param maxChunkSize Only used for instance (use of non-static methods).
     */
    private static ArrayList<ByteCopyUtils> variousBCU_io(int maxChunkSize) {
        ArrayList<ByteCopyUtils> result = new ArrayList<ByteCopyUtils>();
        result.add(null);
        for (InterfaceMBBHelper mbbHelper : new InterfaceMBBHelper[]{null,DefaultMBBHelper.INSTANCE}) {
            result.add(
                    new ByteCopyUtils(
                            mbbHelper,
                            false, // threadSafe
                            maxChunkSize,
                            maxChunkSize,
                            DEFAULT_TEST_MBB_THRESHOLD,
                            maxChunkSize));
        }
        return result;
    }

    /*
     * containers of all sorts
     */

    private static ArrayList<MyAbstractContainer> variousBBC_allCases(boolean isSrc) {
        final int limit = isSrc ? BB_SRC_LIMIT : BB_DST_LIMIT;
        ArrayList<MyAbstractContainer> result = new ArrayList<MyAbstractContainer>();
        for (ByteBuffer bb : variousBB_allCases(limit)) {
            result.add(new MyBBContainer(bb));
        }
        return result;
    }

    private static ArrayList<MyAbstractContainer> variousFCC_allCases(boolean isSrc) {
        final int limit = isSrc ? BB_SRC_LIMIT : BB_DST_LIMIT;
        final long hugeLimitOffset = HUGE_SIZE_OFFSET;
        final int localBufferCapacity = 2*MAX_CONTENT_SIZE;
        ArrayList<MyAbstractContainer> result = new ArrayList<MyAbstractContainer>();
        // small size
        for (MockFileChannel fc : variousFC_allCases(limit,localBufferCapacity)) {
            result.add(new MyFCContainer(fc));
        }
        // huge size
        for (MockFileChannel fc : variousFC_allCases(limit + hugeLimitOffset,localBufferCapacity)) {
            result.add(new MyFCContainer(fc));
        }
        return result;
    }

    /**
     * @return Various ByteBuffers with non-zero offset and
     *         limit-to-capacity, various order, random content,
     *         position at 0, and no mark.
     */
    private static ArrayList<ByteBuffer> variousBB_allCases(int limit) {
        ArrayList<ByteBuffer> bbs = new ArrayList<ByteBuffer>();

        // To make sure limit is used, and not capacity.
        final int limitToCapacity = BB_LIMIT_TO_CAPACITY;
        // To make sure offset is used.
        final int offset = BB_OFFSET;

        final int capacity = limit + limitToCapacity;
        final int arrayCapacity = offset + capacity;
        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN}) {
            ArrayList<ByteBuffer> localBBs = new ArrayList<ByteBuffer>();

            localBBs.add(sliceAndFill(ByteBuffer.allocate(arrayCapacity),offset));
            localBBs.add(sliceAndFill(ByteBuffer.allocate(arrayCapacity),offset).asReadOnlyBuffer());
            localBBs.add(sliceAndFill(ByteBuffer.allocateDirect(arrayCapacity),offset));
            localBBs.add(sliceAndFill(ByteBuffer.allocateDirect(arrayCapacity),offset).asReadOnlyBuffer());

            for (ByteBuffer bb : localBBs) {
                bb.limit(limit);
            }

            for (ByteBuffer bb : localBBs) {
                bb.order(order);
            }

            bbs.addAll(localBBs);
        }

        for (ByteBuffer bb : bbs) {
            LangUtils.azzert(bb.limit() == limit);
        }

        return bbs;
    }

    /**
     * @return Various MickFileChannels with various readable/writable/appendMode
     *         settings, (byte)position content, position at 0 if not in append
     *         mode.
     */
    private static ArrayList<MockFileChannel> variousFC_allCases(
            long size,
            int localBufferCapacity) {
        ArrayList<MockFileChannel> fcs = new ArrayList<MockFileChannel>();
        for (boolean readable : FALSE_TRUE) {
            for (boolean writable : FALSE_TRUE) {
                for (boolean appendMode : FALSE_TRUE) {
                    fcs.add(
                            newMockFileChannel(
                                    readable,
                                    writable,
                                    appendMode,
                                    size,
                                    localBufferCapacity));
                }
            }
        }
        return fcs;
    }

    /*
     * containers for concurrency tests
     */

    private static ArrayList<MyAbstractContainer> variousBBC_concurrency() {
        ArrayList<MyAbstractContainer> result = new ArrayList<MyAbstractContainer>();
        for (ByteBuffer bb : variousBB_concurrency(CONCURRENCY_TEST_SIZE)) {
            result.add(new MyBBContainer(bb));
        }
        return result;
    }

    private static ArrayList<MyAbstractContainer> variousFCC_concurrency() {
        ArrayList<MyAbstractContainer> result = new ArrayList<MyAbstractContainer>();
        // Need local buffer of whole size, because it is used by both threads.
        for (MockFileChannel fc : variousFC_concurrency(CONCURRENCY_TEST_SIZE, 2*CONCURRENCY_TEST_SIZE)) {
            result.add(new MyFCContainer(fc));
        }
        return result;
    }

    /**
     * @return A dummy src or dst container for concurrency tests, when a same
     *         src or dst instance can't be shared across thread due to its position
     *         being used.
     */
    private static MyAbstractContainer dummyContainer_concurrency(boolean isBB) {
        if (isBB) {
            return new MyBBContainer(fill(ByteBuffer.allocateDirect(CONCURRENCY_TEST_SIZE)));
        } else {
            return new MyFCContainer(newMockFileChannel(CONCURRENCY_TEST_SIZE, 2*CONCURRENCY_TEST_SIZE));
        }
    }

    private static ArrayList<ByteBuffer> variousBB_concurrency(int limit) {
        ArrayList<ByteBuffer> bbs = new ArrayList<ByteBuffer>();
        // heap, array accessible
        bbs.add(fill(ByteBuffer.allocate(limit)));
        // heap, array not accessible
        bbs.add(fill(ByteBuffer.allocate(limit)).asReadOnlyBuffer());
        // direct
        bbs.add(fill(ByteBuffer.allocateDirect(limit)));
        return bbs;
    }

    /**
     * Always readable and writable, never in append mode.
     */
    private static ArrayList<MockFileChannel> variousFC_concurrency(
            long size,
            int localBufferCapacity) {
        ArrayList<MockFileChannel> fcs = new ArrayList<MockFileChannel>();
        fcs.add(newMockFileChannel(size, localBufferCapacity));
        return fcs;
    }
}
