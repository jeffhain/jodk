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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import net.jodk.test.TestUtils;

/**
 * Benches that make use of actual drive, since they involve FileChannel.
 * 
 * Using FileChannels from RandomAccessFiles.
 */
public class ByteCopyUtilsPerf {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean DEBUG = false;
    
    /**
     * Copy size, which is also used for both src and dst sizes.
     */
    private static final int COPY_SIZE = 200 * 1000 * 1000;

    /**
     * To allow for srcPos != dstPos.
     */
    private static final int CONTAINER_CAP = COPY_SIZE+1;
    
    private static final int SMALL_SUB_COPY_SIZE = 1024;
    
    /**
     * To tune default one.
     */
    private static final int MBB_THRESHOLD = ByteCopyUtils.DEFAULT_MBB_THRESHOLD;

    private static final int NBR_OF_RUNS = 8;
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final int BB_TYPE_DIRECT = 0;
    private static final int BB_TYPE_HEAP_ARRAY = 1;
    /**
     * read-only
     */
    private static final int BB_TYPE_HEAP_NOARRAY = 2;

    private static final int BB_TYPE_MIN = BB_TYPE_DIRECT;
    private static final int BB_TYPE_MAX_WRITABLE = BB_TYPE_HEAP_ARRAY;
    private static final int BB_TYPE_MAX = BB_TYPE_HEAP_NOARRAY;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println(TestUtils.getJVMInfo());
        newRun(args);
    }

    public static void newRun(String[] args) {
        new ByteCopyUtilsPerf().run(args);
    }

    public ByteCopyUtilsPerf() {
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private void run(String[] args) {
        System.out.println("--- "+ByteCopyUtilsPerf.class.getSimpleName()+"... ---");
        System.out.println("copy size = "+COPY_SIZE);
        System.out.println("If dst is a FileChannel, first copy grows it");
        System.out.println("up to copy size, so it might take longer.");

        bench_BB_BB();
        
        bench_BB_FC();
        
        bench_FC_BB();
        
        bench_FC_FC();

        System.out.println("--- ..."+ByteCopyUtilsPerf.class.getSimpleName()+" ---");
    }

    /**
     * @param utils Null for use of static methods.
     * @param byteShift < 0 for srcPos > dstPos (forward copy),
     *                  > 0 for srcPos < dstPos (backward copy),
     *                  0 for srcPos == dstPos.
     */
    private static void benchCopy(
            ByteCopyUtils utils,
            Object src,
            Object dst,
            int maxSubCopySize,
            int byteShift,
            String comment) {
        if ((byteShift < -1) || (byteShift > 1)) {
            throw new AssertionError();
        }
        final int n = COPY_SIZE;
        if ((maxSubCopySize < 1) || (maxSubCopySize > n)) {
            throw new AssertionError();
        }
        for (int k=0;k<NBR_OF_RUNS;k++) {
            long a = System.nanoTime();
            int nDone = 0;
            while (nDone < n) {
                final int nRemaining = (n-nDone);
                final int subCopySize = Math.min(nRemaining, maxSubCopySize);
                try {
                    final int srcPos = nDone + ((byteShift >= 0) ? 0 : 1);
                    final int dstPos = nDone + ((byteShift <= 0) ? 0 : 1);
                    // All/All-ness has currently no impact on actually
                    // used copy treatments, so no need to test other methods.
                    if (utils == null) {
                        ByteCopyUtils.readAllAtAndWriteAllAt(src, srcPos, dst, dstPos, subCopySize);
                    } else {
                        utils.readAllAtAndWriteAllAt_(src, srcPos, dst, dstPos, subCopySize);
                    }
                    nDone += subCopySize;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            long b = System.nanoTime();
            final String dressedComment = ((comment == null) ? "" : " ("+comment+")");
            final String scsInfo =
                ((maxSubCopySize == n) ? "" :
                    ((maxSubCopySize >= MBB_THRESHOLD) ? " (>= MBB threshold)" :
                        (maxSubCopySize == MBB_THRESHOLD-1) ? " (< MBB threshold)" :
                            " (small sub-copies)"));
            final String backwardInfo = ((byteShift > 0) ? " (backward)" : "");
            System.out.println("copy from "+info(src)+" to "+info(dst)+dressedComment+scsInfo+backwardInfo+" took "+TestUtils.nsToSRounded(b-a)+" s");
        }
    }
    
    private static String info(Object container) {
        if (container instanceof ByteBuffer) {
            return bbInfo((ByteBuffer)container);
        } else {
            return fcInfo((FileChannel)container);
        }
    }

    private static String bbInfo(ByteBuffer bb) {
        if (bb.isDirect()) {
            return "BB(direct)";
        } else {
            if (bb.hasArray()) {
                return "BB(heap,array)";
            } else {
                return "BB(heap,noarray)";
            }
        }
    }

    private static String fcInfo(FileChannel fc) {
        return "FC";
    }
    
    private static void settle() {
        System.gc();
    }
    
    /*
     * 
     */
    
    private static ByteBuffer newBB(int bbType) {
        if (bbType == BB_TYPE_HEAP_ARRAY) {
            return ByteBuffer.allocate(CONTAINER_CAP);
        } else if (bbType == BB_TYPE_DIRECT) {
            return ByteBuffer.allocateDirect(CONTAINER_CAP);
        } else if (bbType == BB_TYPE_HEAP_NOARRAY) {
            return ByteBuffer.allocate(CONTAINER_CAP).asReadOnlyBuffer();
        } else {
            throw new AssertionError(bbType);
        }
    }

    private static FCHolder newFCHolder(int initialSize) {
        File file = TestUtils.newTempFile();
        if (DEBUG) {
            System.out.println("using tmp file "+file.getAbsolutePath());
        }
        FCHolder fch = new FCHolder(file);
        fch.setSize(initialSize);
        return fch;
    }

    /*
     * 
     */
    
    private void bench_BB_BB() {
        System.out.println("");
        System.out.println("BB to BB");
        // no MBBs for these copies, so only one sub-copy size near MBB threshold
        for (int maxSubCopySize : new int[]{SMALL_SUB_COPY_SIZE,MBB_THRESHOLD,COPY_SIZE}) {
            System.out.println("");
            System.out.println("BB to BB - sub-copy size = "+maxSubCopySize);
            // no forward/backward copies (overlapping-proof without)
            for (int byteShift : new int[]{0}) {
                bench_BB_BB(maxSubCopySize, byteShift);
            }
        }
    }
    
    private void bench_BB_FC() {
        System.out.println("");
        System.out.println("BB to FC");
        // no MBBs for these copies, so only one sub-copy size near MBB threshold
        for (int maxSubCopySize : new int[]{SMALL_SUB_COPY_SIZE,MBB_THRESHOLD,COPY_SIZE}) {
            System.out.println("");
            System.out.println("BB to FC - sub-copy size = "+maxSubCopySize);
            // not overlapping-proof so no forward/backward copies
            for (int byteShift : new int[]{0}) {
                bench_BB_FC(maxSubCopySize, byteShift);
            }
        }
    }
    
    private void bench_FC_BB() {
        System.out.println("");
        System.out.println("FC to BB");
        for (int maxSubCopySize : new int[]{SMALL_SUB_COPY_SIZE,MBB_THRESHOLD-1,MBB_THRESHOLD,COPY_SIZE}) {
            System.out.println("");
            System.out.println("FC to BB - sub-copy size = "+maxSubCopySize);
            // not overlapping-proof so no forward/backward copies
            for (int byteShift : new int[]{0}) {
                bench_FC_BB(maxSubCopySize, byteShift);
            }
        }
    }
    
    private void bench_FC_FC() {
        System.out.println("");
        System.out.println("FC to FC");
        for (int maxSubCopySize : new int[]{SMALL_SUB_COPY_SIZE,MBB_THRESHOLD-1,MBB_THRESHOLD,COPY_SIZE}) {
            System.out.println("");
            System.out.println("FC to FC - sub-copy size = "+maxSubCopySize);
            for (int byteShift : new int[]{-1,1}) {
                bench_FC_FC(maxSubCopySize, byteShift);
            }
        }
    }

    /*
     * 
     */
    
    private void bench_BB_BB(int maxSubCopySize, int byteShift) {
        System.out.println("");
        for (int srcType=BB_TYPE_MIN;srcType<=BB_TYPE_MAX;srcType++) {
            for (int dstType=BB_TYPE_MIN;dstType<=BB_TYPE_MAX_WRITABLE;dstType++) {
                settle();
                benchCopy(null, newBB(srcType), newBB(dstType), maxSubCopySize, byteShift, null);
            }
        }
        settle();
    }

    private void bench_BB_FC(int maxSubCopySize, int byteShift) {
        System.out.println("");
        for (int srcType=BB_TYPE_MIN;srcType<=BB_TYPE_MAX;srcType++) {
            settle();
            FCHolder dstFCH = newFCHolder(0);
            try {
                benchCopy(null, newBB(srcType), dstFCH.getFC(), maxSubCopySize, byteShift, null);
            } finally {
                dstFCH.release();
            }
        }
        settle();
    }

    private void bench_FC_BB(int maxSubCopySize, int byteShift) {
        System.out.println("");
        for (boolean mbbIfPossible : new boolean[]{true,false}) {
            final ByteCopyUtils utils = (mbbIfPossible ? null : new ByteCopyUtils(null, false));
            final String comment = (mbbIfPossible ? "MBB if possible" : "no MBB");
            for (int dstType=BB_TYPE_MIN;dstType<=BB_TYPE_MAX_WRITABLE;dstType++) {
                settle();
                FCHolder srcFCH = newFCHolder(CONTAINER_CAP);
                try {
                    benchCopy(utils, srcFCH.getFC(), newBB(dstType), maxSubCopySize, byteShift, comment);
                } finally {
                    srcFCH.release();
                }
            }
        }
        settle();
    }

    private void bench_FC_FC(int maxSubCopySize, int byteShift) {
        System.out.println("");
        for (boolean mbbIfPossible : new boolean[]{true,false}) {
            final ByteCopyUtils utils = (mbbIfPossible ? null : new ByteCopyUtils(null, false));
            final String comment = (mbbIfPossible ? "MBB if possible" : "no MBB");
            settle();
            FCHolder srcFCH = newFCHolder(CONTAINER_CAP);
            try {
                FCHolder dstFCH = newFCHolder(0);
                try {
                    benchCopy(utils, srcFCH.getFC(), dstFCH.getFC(), maxSubCopySize, byteShift, comment);
                } finally {
                    dstFCH.release();
                }
            } finally {
                srcFCH.release();
            }
        }
        settle();
    }
}
