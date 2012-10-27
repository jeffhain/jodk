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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
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
    private static final int COPY_SIZE = 100 * 1000 * 1000;

    private static final int NBR_OF_RUNS = 4;
    
    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    private static class MyFCHolder {
        final RandomAccessFile rac;
        public MyFCHolder(File file) {
            try {
                this.rac = new RandomAccessFile(file, "rw");
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        public FileChannel getFC() {
            return this.rac.getChannel();
        }
        public void close() {
            try {
                this.rac.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

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
     * Uses specified positions (0,0).
     * 
     * @param utils Null for use of static methods.
     */
    private static void benchCopy(
            ByteCopyUtils utils,
            Object src,
            Object dst,
            String comment) {
        benchCopy(utils, src, dst, false, false, comment);
    }

    /**
     * @param utils Null for use of static methods.
     */
    private static void benchCopy(
            ByteCopyUtils utils,
            Object src,
            Object dst,
            boolean useSrcPos,
            boolean useDstPos,
            String comment) {
        for (int k=0;k<NBR_OF_RUNS;k++) {
            if (useSrcPos) {
                resetPos(src);
            }
            if (useDstPos) {
                resetPos(dst);
            }

            long a = System.nanoTime();
            try {
                if (useSrcPos) {
                    if (useDstPos) {
                        if (utils == null) {
                            ByteCopyUtils.readAllAndWriteAll(src, dst, COPY_SIZE);
                        } else {
                            utils.readAllAndWriteAll_(src, dst, COPY_SIZE);
                        }
                    } else {
                        if (utils == null) {
                            ByteCopyUtils.readAllAndWriteAllAt(src, dst, 0, COPY_SIZE);
                        } else {
                            utils.readAllAndWriteAllAt_(src, dst, 0, COPY_SIZE);
                        }
                    }
                } else {
                    if (useDstPos) {
                        if (utils == null) {
                            ByteCopyUtils.readAllAtAndWriteAll(src, 0, dst, COPY_SIZE);
                        } else {
                            utils.readAllAtAndWriteAll_(src, 0, dst, COPY_SIZE);
                        }
                    } else {
                        if (utils == null) {
                            ByteCopyUtils.readAllAtAndWriteAllAt(src, 0, dst, 0, COPY_SIZE);
                        } else {
                            utils.readAllAtAndWriteAllAt_(src, 0, dst, 0, COPY_SIZE);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            long b = System.nanoTime();
            final String dressedComment = ((comment == null) ? "" : " ("+comment+")");
            System.out.println("copy from "+info(src)+" to "+info(dst)+dressedComment+" took "+TestUtils.nsToSRounded(b-a)+" s");
        }
    }
    
    private static void resetPos(Object container) {
        if (container instanceof ByteBuffer) {
            ((ByteBuffer)container).position(0);
        } else {
            UncheckedIO.position((FileChannel)container, 0);
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
    
    private static String addComment(String previous, String added) {
        if ((added == null) || (added.length() == 0)) {
            return ""+previous;
        } else {
            if ((previous == null) || (previous.length() == 0)) {
                return ""+added;
            } else {
                return previous+", "+added;
            }
        }
    }
    
    private static void settle() {
        System.gc();
    }
    
    /*
     * 
     */
    
    private static ByteBuffer newBB(int bbType) {
        if (bbType == BB_TYPE_HEAP_ARRAY) {
            return ByteBuffer.allocate(COPY_SIZE);
        } else if (bbType == BB_TYPE_DIRECT) {
            return ByteBuffer.allocateDirect(COPY_SIZE);
        } else if (bbType == BB_TYPE_HEAP_NOARRAY) {
            return ByteBuffer.allocate(COPY_SIZE).asReadOnlyBuffer();
        } else {
            throw new AssertionError(bbType);
        }
    }

    private static MyFCHolder newFCHolder(int initialSize) {
        File file = TestUtils.newTempFile();
        if (DEBUG) {
            System.out.println("using tmp file "+file.getAbsolutePath());
        }
        MyFCHolder fch = new MyFCHolder(file);
        if (initialSize > 0) {
            ByteBuffer src = ByteBuffer.allocate(initialSize);
            try {
                ByteCopyUtils.readAllAndWriteAllAt(src, fch.getFC(), 0, initialSize);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return fch;
    }
    
    /*
     * 
     */
    
    private void bench_BB_BB() {
        System.out.println("");
        for (int srcType=BB_TYPE_MIN;srcType<=BB_TYPE_MAX;srcType++) {
            for (int dstType=BB_TYPE_MIN;dstType<=BB_TYPE_MAX_WRITABLE;dstType++) {
                settle();
                benchCopy(null, newBB(srcType), newBB(dstType), null);
            }
        }
        settle();
    }

    private void bench_BB_FC() {
        System.out.println("");
        for (int srcType=BB_TYPE_MIN;srcType<=BB_TYPE_MAX;srcType++) {
            settle();
            MyFCHolder dstFCH = newFCHolder(0);
            try {
                benchCopy(null, newBB(srcType), dstFCH.getFC(), null);
            } finally {
                dstFCH.close();
            }
        }
        settle();
    }

    private void bench_FC_BB() {
        System.out.println("");
        for (boolean mbbIfPossible : new boolean[]{true,false}) {
            final ByteCopyUtils utils = (mbbIfPossible ? null : new ByteCopyUtils(null, false));
            final String comment = (mbbIfPossible ? "MBB if possible" : "no MBB");
            for (int dstType=BB_TYPE_MIN;dstType<=BB_TYPE_MAX_WRITABLE;dstType++) {
                settle();
                MyFCHolder srcFCH = newFCHolder(COPY_SIZE);
                try {
                    benchCopy(utils, srcFCH.getFC(), newBB(dstType), comment);
                } finally {
                    srcFCH.close();
                }
            }
        }
        settle();
    }

    private void bench_FC_FC() {
        System.out.println("");
        for (boolean mbbIfPossible : new boolean[]{true,false}) {
            final ByteCopyUtils utils = (mbbIfPossible ? null : new ByteCopyUtils(null, false));
            final String comment1 = (mbbIfPossible ? "MBB if possible" : "no MBB");
            for (boolean posInSrc : new boolean[]{false,true}) {
                final String comment2 = addComment(comment1, (posInSrc ? "pos in src" : ""));
                for (boolean posInDst : new boolean[]{false,true}) {
                    if (posInSrc && posInDst) {
                        // Same as if just posInDst, and not wanting
                        // to burn the drive for nothing.
                        continue;
                    }
                    final String comment3 = addComment(comment2, (posInDst ? "pos in dst" : ""));
                    
                    settle();
                    MyFCHolder srcFCH = newFCHolder(COPY_SIZE);
                    try {
                        MyFCHolder dstFCH = newFCHolder(0);
                        try {
                            benchCopy(utils, srcFCH.getFC(), dstFCH.getFC(), comment3);
                        } finally {
                            dstFCH.close();
                        }
                    } finally {
                        srcFCH.close();
                    }
                }
            }
        }
        settle();
    }
}
