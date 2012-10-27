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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import net.jodk.io.mock.MockFileChannel;
import net.jodk.io.mock.VirtualMockBuffer;
import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;
import net.jodk.lang.Unchecked;

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
     * Static methods tests never use map/unmap, since the backing instance uses
     * DefaultMBBHelper, and our tests use MockFileChannel implementations.
     * 
     * Splitting tests between "badArgs" and "goodArgs" cases,
     * else nested loops are too large and each test takes ages.
     */

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean DEBUG = true;
    
    private static final int BB_OFFSET = 3;
    private static final int BB_LIMIT_TO_CAPACITY = 2;
    
    private static final int BB_SRC_LIMIT = 23;
    private static final int BB_DST_LIMIT = 17;
    
    private static final int MAX_NON_HUGE_COUNT = 10;

    private static final int MAX_CONTENT_SIZE = Math.max(MAX_NON_HUGE_COUNT, Math.max(BB_SRC_LIMIT, BB_DST_LIMIT));

    /*
     * Small max chunk sizes, to allow for multiple
     * iterations for copies by chunks (when using
     * instance methods), without having to copy
     * huge numbers of bytes.
     */
    
    private static final int TEST_MAX_CHUNK_SIZE = 1;
    private static final int TEST_MAX_BB_CHUNK_SIZE = 1;
    
    /**
     * Huge limit for channels, for use of huge positions.
     */
    private static final long HUGE_LIMIT_OFFSET = (Long.MAX_VALUE>>1);

    /**
     * Containers limit for concurrency test.
     * Each thread can play with first or last MAX_COPY_SIZE bytes.
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
            this.getVMB().limit(size);
        }
        @Override
        public void put(long position, byte b) {
            this.getVMB().put(position, b);
        }
        @Override
        public byte get(long position) {
            return this.getVMB().get(position);
        }
        private VirtualMockBuffer getVMB() {
            return (VirtualMockBuffer)this.fc.getBackingMockBuffer();
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    private static final List<Boolean> FALSE_TRUE = Collections.unmodifiableList(Arrays.asList(new Boolean[]{false,true}));
    
    private static final ByteCopyUtils A_THREAD_SAFE_BCU = new ByteCopyUtils(
            null, // MBB helper
            true, // threadSafe
            TEST_MAX_CHUNK_SIZE,
            TEST_MAX_BB_CHUNK_SIZE);
    
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
                    37);
            assertTrue(utils.toString().contains(
                    "[threadSafe="+threadSafe
                    +",maxChunkSize="+36
                    +",maxMBBChunkSize="+37
                    +",mbbHelper="+mbbHelper+"]"));
        }
    }
    
    /*
     * NPE
     */
    
    public void test_readXXXAndWriteXXX_NPE() {
        for (boolean staticCalls : FALSE_TRUE) {
            for (Object src : new Object[]{null,ByteBuffer.allocate(MAX_CONTENT_SIZE),newMockFileChannel(MAX_CONTENT_SIZE, MAX_CONTENT_SIZE)}) {
                for (Object dst : new Object[]{null,ByteBuffer.allocate(MAX_CONTENT_SIZE),newMockFileChannel(MAX_CONTENT_SIZE, MAX_CONTENT_SIZE)}) {
                    test_readXXXAndWriteXXX_NPE(staticCalls, src, dst);
                }
            }
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
        for (Object utilz : (staticCalls ? new Object[]{null} : variousBCU().toArray())) {
            final ByteCopyUtils utils = (ByteCopyUtils)utilz;
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

    /**
     * Huge positions/counts, and read-only/non-writable dst.
     */
    private static void test_readXXXAndWriteXXX_badArgs(boolean staticCalls, boolean srcIsBB, boolean dstIsBB) {
        for (Object utilz : (staticCalls ? new Object[]{null} : variousBCU().toArray())) {
            final ByteCopyUtils utils = (ByteCopyUtils)utilz;
            for (MyAbstractContainer srcCont : srcIsBB ? variousBBC_allCases(true) : variousFCC_allCases(true)) {
                for (MyAbstractContainer dstCont : dstIsBB ? variousBBC_allCases(false) : variousFCC_allCases(false)) {
                    for (long srcPos : new long[]{-1,1,Long.MAX_VALUE}) {
                        for (long dstPos : new long[]{-1,1,Long.MAX_VALUE}) {
                            for (long count : new long[]{-1,1,Long.MAX_VALUE}) {
                                for (boolean posInSrc : FALSE_TRUE) {
                                    for (boolean posInDst : FALSE_TRUE) {
                                        for (boolean readAllCount : FALSE_TRUE) {
                                            for (boolean writeAllRead : FALSE_TRUE) {
                                                if (readAllCount && (!writeAllRead)) {
                                                    // not applicable
                                                    continue;
                                                }
                                                resetContent(srcCont);
                                                resetContent(dstCont);
                                                final boolean checkAdjacentDstBytes = true;
                                                callAndCheck_readXXXAndWriteXXX_unlessCant(
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
                                                        checkAdjacentDstBytes);
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
        // If static calls, no need to use utils instances.
        for (Object utilz : (staticCalls ? new Object[]{null} : variousBCU().toArray())) {
            final ByteCopyUtils utils = (ByteCopyUtils)utilz;
            for (MyAbstractContainer srcCont : srcIsBB ? variousBBC_allCases(true) : variousFCC_allCases(true)) {
                if (!srcCont.getReadable()) {
                    continue;
                }
                for (MyAbstractContainer dstCont : dstIsBB ? variousBBC_allCases(false) : variousFCC_allCases(false)) {
                    if (!dstCont.getWritable()) {
                        continue;
                    }
                    /*
                     * Some copies near 0, some copies near
                     * size (a huge value for channels).
                     * Need to have a position equal to size,
                     * to allow for testing channels in append mode.
                     */
                    final long srcSize = srcCont.size();
                    final long dstSize = dstCont.size();
                    for (long srcPos : new long[]{
                            0,1,MAX_NON_HUGE_COUNT/2, // near 0
                            srcSize-MAX_NON_HUGE_COUNT/2,srcSize-1, // near size
                            srcSize,srcSize+1}) { // >= size
                        for (long dstPos : new long[]{
                                0,1,MAX_NON_HUGE_COUNT/2, // near 0
                                dstSize-MAX_NON_HUGE_COUNT/2,dstSize-1, // near size
                                dstSize, dstSize+1}) { // >= size
                            for (long count : new long[]{
                                    0,1, // small
                                    MAX_NON_HUGE_COUNT/2, // mid
                                    MAX_NON_HUGE_COUNT}) { // max
                                for (boolean posInSrc : FALSE_TRUE) {
                                    for (boolean posInDst : FALSE_TRUE) {
                                        for (boolean readAllCount : FALSE_TRUE) {
                                            for (boolean writeAllRead : FALSE_TRUE) {
                                                if (readAllCount && (!writeAllRead)) {
                                                    // not applicable
                                                    continue;
                                                }
                                                resetContent(srcCont);
                                                resetContent(dstCont);
                                                final boolean checkAdjacentDstBytes = true;
                                                callAndCheck_readXXXAndWriteXXX_unlessCant(
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
                                                        checkAdjacentDstBytes);
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
    
    /**
     * @param checkAdjacentDstBytes Should be false for concurrency tests,
     *        in which dst might be shared, for each thread to only check
     *        its part of it.
     * @return True if did throw, false otherwise.
     */
    private static boolean callAndCheck_readXXXAndWriteXXX_unlessCant(
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
            final boolean checkAdjacentDstBytes) {
        
        boolean didThrow = false;
        
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
            if ((srcPos < 0)
                    || ((srcCont instanceof MyBBContainer) && (srcPos > srcInitialSize))) {
                // can't do
                return didThrow;
            }
            if (srcCont.getAppendMode() && (srcPos != srcCont.position())) {
                // can't do
                return didThrow;
            }
            srcCont.position(srcPos);
        } else {
            srcCont.position(unusedSrcPos);
        }
        if (posInDst) {
            if ((dstPos < 0)
                    || ((dstCont instanceof MyBBContainer) && (dstPos > dstInitialSize))) {
                // can't do
                return didThrow;
            }
            if (dstCont.getAppendMode() && (dstPos != dstCont.position())) {
                // can't do
                return didThrow;
            }
            dstCont.position(dstPos);
        } else {
            dstCont.position(unusedDstPos);
        }
        
        final long srcRemFromPos = Math.max(0, srcInitialSize - srcPos);
        final long nReadable = Math.min(srcRemFromPos, count);

        final long notSmallThreshold = Long.MAX_VALUE/10;
        if ((count > notSmallThreshold) && (srcRemFromPos > notSmallThreshold)) {
            // Would overflow our buffers, and take much time if not.
            return didThrow;
        }
        
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
        
        /*
         * 
         */
        
        long nDone = Long.MIN_VALUE;
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
                    assertEquals(msg, unusedSrcPos, srcCont.position());
                }
            }
            if (posInDst) {
                assertEquals(msg, dstPos + nDone, dstCont.position());
            } else {
                if (dstCont.getAppendMode()) {
                    assertEquals(msg, dstCont.size(), dstCont.position());
                } else {
                    assertEquals(msg, unusedDstPos, dstCont.position());
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
            
            checkIsReset(msg, srcCont);

            /*
             * Checking dst bytes in range were copied.
             */

            for (long i=0;i<nDone;i++) {
                final byte srcByte = srcCont.get(srcPos + i);
                final byte dstByte = dstCont.get(dstPos + i);
                assertEquals(msg, srcByte, dstByte);
            }

            /*
             * Checking adjacent dst bytes didn't change.
             */

            if (checkAdjacentDstBytes) {
                final long preDstPos = dstPos-1;
                if ((preDstPos >= 0) && (preDstPos < dstCont.size())) {
                    assertEquals(msg, (byte)preDstPos, dstCont.get(preDstPos));
                }
                final long postDstPos = dstPos+nDone;
                if ((postDstPos >= 0) && (postDstPos < dstCont.size())) {
                    assertEquals(msg, (byte)postDstPos, dstCont.get(postDstPos));
                }
            }

        } catch (IllegalArgumentException e) {
            didThrow = true;
            if (!mustThrow_IllegalArgumentException) {
                throw new RuntimeException(msg,e);
            }

        } catch (BufferUnderflowException e) {
            didThrow = true;
            if (!mustThrow_BufferUnderflowException) {
                throw new RuntimeException(msg,e);
            }

        } catch (BufferOverflowException e) {
            didThrow = true;
            if (!mustThrow_BufferOverflowException) {
                throw new RuntimeException(msg,e);
            }

        } catch (NonReadableChannelException e) {
            didThrow = true;
            if (!mustThrow_NonReadableChannelException) {
                throw new RuntimeException(msg,e);
            }

        } catch (ReadOnlyBufferException e) {
            didThrow = true;
            if (!mustThrow_ReadOnlyBufferException) {
                throw new RuntimeException(msg,e);
            }
            
        } catch (NonWritableChannelException e) {
            didThrow = true;
            if (!mustThrow_NonWritableChannelException) {
                throw new RuntimeException(msg,e);
            }
            
        } catch (Exception e) {
            throw new RuntimeException(msg,e);
        }
        
        return didThrow;
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

                    final boolean checkAdjacentDstBytes = false;
                    final boolean didThrow = callAndCheck_readXXXAndWriteXXX_unlessCant(
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
                            checkAdjacentDstBytes);
                    assertFalse(msg, didThrow);
                }
            }
        }
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
    
    private static void resetContent(MyAbstractContainer container) {
        if (container instanceof MyBBContainer) {
            if (container.getWritable()) {
                fill((ByteBuffer)container.getBackingContainer());
            }
        } else {
            ((MyFCContainer)container).getVMB().deleteLocalBuffer();
        }
    }
    
    /**
     * Checks that no put has been made (unless eventually non-modifying ones).
     */
    private static void checkIsReset(String msg, MyAbstractContainer container) {
        if (container instanceof MyBBContainer) {
            final ByteBuffer bb = (ByteBuffer)container.getBackingContainer();
            final int limit = bb.limit();
            bb.limit(bb.capacity());
            for (int i=0;i<bb.capacity();i++) {
                assertEquals(msg, (byte)i, bb.get(i));
            }
            bb.limit(limit);
        } else {
            MyFCContainer cont = (MyFCContainer)container;
            assertNull(cont.getVMB().getLocalBufferElseNull());
        }
    }

    /*
     * 
     */
    
    private static ByteBuffer sliceAndFill(ByteBuffer buffer, int offset) {
        return fill(((ByteBuffer)buffer.position(offset)).slice());
    }
    
    /**
     * Using (byte)position as content.
     */
    private static ByteBuffer fill(ByteBuffer buffer) {
        final int limit = buffer.limit();
        buffer.limit(buffer.capacity());
        for (int i=0;i<buffer.capacity();i++) {
            buffer.put(i,(byte)i);
        }
        buffer.limit(limit);
        return buffer;
    }
    
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
        VirtualMockBuffer vmb = new VirtualMockBuffer(localBufferCapacity);
        vmb.limit(size);
        MockFileChannel fc = new MockFileChannel(
                vmb,
                readable,
                writable,
                appendMode);
        return fc;
    }

    /*
     * 
     */
    
    private static ArrayList<ByteCopyUtils> variousBCU() {
        ArrayList<ByteCopyUtils> result = new ArrayList<ByteCopyUtils>();
        result.addAll(variousBCU(false));
        result.addAll(variousBCU(true));
        return result;
    }

    private static ArrayList<ByteCopyUtils> variousBCU(boolean threadSafe) {
        ArrayList<ByteCopyUtils> result = new ArrayList<ByteCopyUtils>();
        for (InterfaceMBBHelper mbbHelper : new InterfaceMBBHelper[]{null,new MockMBBHelper()}) {
            result.add(
                    new ByteCopyUtils(
                            mbbHelper,
                            threadSafe,
                            TEST_MAX_CHUNK_SIZE,
                            TEST_MAX_BB_CHUNK_SIZE));
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
        final long hugeLimitOffset = HUGE_LIMIT_OFFSET;
        final int localBufferCapacity = MAX_CONTENT_SIZE;
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
        for (MockFileChannel fc : variousFC_concurrency(CONCURRENCY_TEST_SIZE,CONCURRENCY_TEST_SIZE)) {
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
            return new MyFCContainer(newMockFileChannel(CONCURRENCY_TEST_SIZE, CONCURRENCY_TEST_SIZE));
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
