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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import net.jodk.lang.NumbersUtils;

/**
 * Class providing treatments to copy contiguous byte ranges between
 * ByteBuffers, between FileChannels, or between each other.
 * 
 * Various types of copies are provided, whether src and/or dst current
 * positions are used as copy positions, and whether the number of bytes to copy
 * must be limited by src and/or dst remaining amounts of bytes from copy
 * positions up to limit/size.
 * 
 * To reduce the overall amount of methods, which is already large, src and dst
 * arguments type is Object. ClassCastException is thrown at some point if your
 * object is neither a ByteBuffer nor a FileChannel.
 * 
 * Not providing support for raw byte arrays, because it would cause a
 * (supplementary) specific semantic case, since byte arrays don't
 * hold a position, and it would also multiply the number of internal
 * treatments. Instead, ByteBuffer.wrap(byte[]) can be used before,
 * if you don't mind the garbage.
 * 
 * If the specified number of bytes to copy is > 0, and position copy
 * for src is >= its size, and no exception is to be thrown, these treatments
 * return 0, and not -1 as some read methods do, to keep things simple, i.e.
 * for homogeneity with other nothing-read cases.
 * 
 * A ByteBuffer or FileChannel for which copy position is specified (not using
 * its own position), can be used concurrently for multiple non-overlaping
 * copies, as long as none of these copies causes it to grow, i.e. modifies its
 * limit or size (in particular when using mapped buffers which behavior is
 * inherently undefined in such cases).
 * 
 * Treatments might use thread-local temporary ByteBuffers, each being about 8ko,
 * which should allow for usage by many threads without too much memory overhead.
 * 
 * These treatment's behavior is undefined if src and dst share data such as
 * position or limit/size, or share content memory and src and dst copy ranges
 * overlap.
 * 
 * FileChannels don't provide access to their readability state,
 * and we want semantics to be similar for ByteBuffers and FileChannels,
 * so ByteBuffers read-only-ness check is delegated to ByteBuffer's method.
 * As a result, no ReadOnlyBufferException or NonWritableChannelException
 * is thrown unless an attempt to write data is actually made.
 * 
 * These methods are rather permissive about their arguments.
 * For example, if specified src position is past src size,
 * System.arraycopy(byte[],int,byte[],int,int)
 * and
 * ByteBufferUtils.bufferCopy(ByteBuffer,int,ByteBuffer,int,int)
 * throw, but
 * IOUtils.readAtAndWriteAllAt(Object,long,Object,long,long)
 * just returns 0.
 */
public class ByteCopyUtils {

    /*
     * TODO
     * - These treatments could be considerably simplified, and made faster and
     *   garbage-free, if the JDK would provide more appropriate low level
     *   primitives, in particular native copy treatments that don't make use of
     *   position.
     * - The API is very open about when an IOException can be thrown,
     *   not to constrain the implementation, but a better (lower-level)
     *   implementation could precise it some.
     * - When shared memory between src and dst can be figured out,
     *   could handle it by copying first-to-last or last-to-first.
     *   Not doing it for now, because it would complicate both
     *   semantics (when can overlapping be figured out?) and
     *   implementation.
     * - Could make instance methods public, along with some constructor,
     *   and allow for use of instance-local temporary ByteBuffers,
     *   which could help in case of high threads turnover that
     *   would make thread-local temporary ByteBuffers hurt.
     */
    
    /*
     * Taking care not to use FileChannel.read/write with large heap buffers,
     * for Sun's implementation uses an as big temporary direct buffer.
     */

    /*
     * Not allowing user to specify whether src or dst instances are supposed to
     * be used concurrently when a position is specified, in which case we
     * always support concurrency, for we prefer to end up with the eventually
     * associated garbage (.duplicate()) and additional copies, than having
     * to simplify the API if/when efficient concurrency-proof implementations
     * become available.
     * Also, if position is specified, and concurrency-proof-ness turned off,
     * to avoid a call to .duplicate(), treatments might temporarily change
     * limit and position of the specified ByteBuffer, which might clear
     * the mark if any.
     */
    
    /*
     * JDK/JODK byte copies cookbook:
     * (not considering scattering/gathering copies)
     * 
     * If position is "fix", it allows concurrent non-growing
     * usage of the ByteBuffer or FileChannel.
     * 
     *                                                     +---------+---------+---------------+---------------+--------
     *                                                     | src pos | dst pos | src too small | dst too small | comment
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     * JDK                                                 |         |         |               |               |
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     * dstB.put(srcB)                                      |   mod   |   mod   |     stops     |     throws    |
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     * srcC.read(dstB)                                     |   mod   |   mod   |     stops     |     stops     | returns -1 if src position >= src size
     * srcC.read(dstB,srcPos)                              |   fix   |   mod   |     stops     |     stops     | returns -1 if src copy position >= src size
     * srcC.transferTo(srcPos,count,dstC)                  |   fix   |   mod   |     stops     |     stops     |
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     * dstC.write(srcB)                                    |   mod   |   mod   |     stops     |     grows     |
     * dstC.write(srcB,dstPos)                             |   mod   |   fix   |     stops     |     grows     |
     * dstC.transferFrom(srcC,dstPos,count)                |   mod   |   fix   |     stops     |  stops/grows  | stops if dstPos > dst.size (TODO bug?)
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     * JODK                                                |         |         |               |               |
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     * readAndWrite(src,dst,count)                         |   mod   |   mod   |     stops     |     stops     |
     * readAndWriteAll(src,dst,count)                      |   mod   |   mod   |     stops     |  grows/throws | throws if can't grow
     * readAllAndWriteAll(src,dst,count)                   |   mod   |   mod   |     throws    |  grows/throws | throws if can't grow
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     * readAndWriteAt(src,dst,dstPos,count)                |   mod   |   fix   |     stops     |     stops     |
     * readAndWriteAllAt(src,dst,dstPos,count)             |   mod   |   fix   |     stops     |  grows/throws | throws if can't grow
     * readAllAndWriteAllAt(src,dst,dstPos,count)          |   mod   |   fix   |     throws    |  grows/throws | throws if can't grow
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     * readAtAndWrite(src,srcPos,dst,count)                |   fix   |   mod   |     stops     |     stops     |
     * readAtAndWriteAll(src,srcPos,dst,count)             |   fix   |   mod   |     stops     |  grows/throws | throws if can't grow
     * readAllAtAndWriteAll(src,srcPos,dst,count)          |   fix   |   mod   |     throws    |  grows/throws | throws if can't grow
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     * readAtAndWriteAt(src,srcPos,dst,dstPos,count)       |   fix   |   fix   |     stops     |     stops     |
     * readAtAndWriteAllAt(src,srcPos,dst,dstPos,count)    |   fix   |   fix   |     stops     |  grows/throws | throws if can't grow
     * readAllAtAndWriteAllAt(src,srcPos,dst,dstPos,count) |   fix   |   fix   |     throws    |  grows/throws | throws if can't grow
     * ----------------------------------------------------+---------+---------+---------------+---------------+--------
     */
    
    /*
     * JDK byte copies Sun's implementation details:
     * ("concurrent" = allowing concurrent non-overlaping
     * usage of a same src or dst instance)
     * 
     * 
     * - System.arraycopy(srcBB.array(),srcOff+srcPos,dstBB.array(),dstOff+dstPos,n)
     *   (concurrent)
     * 
     * 
     * - ByteBuffer.put(srcBB)
     *   (not concurrent, native if dst is direct)
     * 
     * - ByteBuffer.put(srcBB.array(),srcOff+srcPos,n)
     *   (concurrent for src, native if dst is direct)
     * 
     * - ByteBuffer.get(dstBB.array(),dstOff+dstPos,n)
     *   (concurrent for dst, native if src is direct)
     * 
     * 
     * - FileChannel.read(dstBB)
     *   (not concurrent, native if dst is direct,
     *   2*native otherwise)
     *   (if dst is not direct, uses an AS BIG temp direct ByteBuffer)
     *   (takes position lock)
     * 
     * - FileChannel.read(dstBB,srcPos)
     *   (concurrent for src, native if dst is direct,
     *   2*native otherwise)
     *   (if dst is not direct, uses an AS BIG temp direct ByteBuffer)
     * 
     * - FileChannel.read(dstBB[],offset,length)
     *   (not concurrent, native if dst is direct)
     *   (if dst is not direct, uses an AS BIG temp direct ByteBuffer)
     *   (takes position lock)
     * 
     * 
     * - FileChannel.write(srcBB)
     *   (not concurrent, native if src is direct,
     *   2*native otherwise)
     *   (if src is not direct, uses an AS BIG temp direct ByteBuffer)
     *   (takes position lock)
     *   (can grow dst)
     * 
     * - FileChannel.write(srcBB,dstPos)
     *   (concurrent for dst, native if src is direct,
     *   2*native otherwise)
     *   (if src is not direct, uses an AS BIG temp direct ByteBuffer)
     *   (can grow dst)
     * 
     * - FileChannel.write(srcBB[],offset,length)
     *   (not concurrent, native if src is direct,
     *   2*native otherwise)
     *   (if src is not direct, uses an AS BIG temp direct ByteBuffer)
     *   (takes position lock)
     *   (can grow dst)
     * 
     * 
     * - FileChannel.transferTo(srcPos,n,dstWBC)
     *   (concurrent for src, might use
     *   kernel's copy,
     *   else map/write(mbb) (chunks <= 8Mo),
     *   else tmp/read(bb,int)/write(bb) (chunks <= 8Ko))
     *   (takes dst position lock (through use of write(BB)))
     *   (can grow dst)
     * 
     * - FileChannel.transferFrom(srcRBC,dstPos,n)
     *   (concurrent for dst, uses
     *   map/write(bb,int) (chunks <= 8Mo),
     *   else tmp/read(bb)/write(bb,int) (chunks <= 8Ko))
     *   (takes src position lock)
     *   (can grow dst, (TODO bug?) but doesn't if dstPos > dst.size())
     * 
     * 
     * - FileChannel.map(...) : returns a MappedByteBuffer (direct).
     *   (must call force() to flush writes, but only used for reads
     *   in sun.nio.ch.IOUtils)
     */
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final boolean ASSERTIONS = false;
    
    /**
     * Similar to java.nio.FileChannelImpl.TRANSFER_SIZE = 8 * 1024.
     * Not too large (i.e. not like java.nio.Bits.UNSAFE_COPY_THRESHOLD = 1024 * 1024),
     * to avoid too large memory overheap in case of usage of thread-safe
     * instance by many threads.
     */
    private static final int DEFAULT_MAX_CHUNK_SIZE = 8 * 1024;

    /**
     * Similar to java.nio.FileChannelImpl.MAPPED_TRANSFER_SIZE = 8 * 1024 * 1024.
     */
    private static final int DEFAULT_MAX_MBB_CHUNK_SIZE = 8 * 1024 * 1024;

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    /**
     * Position argument placeholder to indicate use of
     * ByteBuffer or FileChannel's position.
     */
    private static final int INST_POS = -1;
    
    /*
     * 
     */
    
    /**
     * An instance using thread-local temporary ByteBuffers.
     * Using MBB if can unmap.
     */
    private static final ByteCopyUtils DEFAULT_INSTANCE = new ByteCopyUtils(
            DefaultMBBHelper.INSTANCE,
            true); // threadSafe
    
    /*
     * 
     */
    
    private final int maxChunkSize;
    private final int maxMBBChunkSize;

    /**
     * Can be null.
     */
    private final InterfaceMBBHelper mbbHelper;
    
    /*
     * temps
     */
    
    private final ThreadLocal<ByteBuffer> tlTmpDirectBB;

    /**
     * Lazily initialized.
     * 
     * Can use multiple instances over time
     * (at least exponential growth up to max chunk size).
     */
    private ByteBuffer tmpDirectBB;
    
    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    /**
     * @return A String containing info about this instance.
     */
    @Override
    public String toString() {
        return super.toString()
        +"[threadSafe="+(this.tlTmpDirectBB != null)
        +",maxChunkSize="+this.maxChunkSize
        +",maxMBBChunkSize="+this.maxMBBChunkSize
        +",mbbHelper="+this.mbbHelper+"]";
    }
    
    /*
     * (instance position, instance position)
     */
    
    /**
     * Copies min(src remaining, dst remaining, maxCount) bytes.
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param maxCount Max number of bytes to copy.
     * @return The number of bytes copied, possibly 0.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if maxCount < 0.
     * @throws IOException if thrown by underlying treatments.
     */
    public static long readAndWrite(Object src, Object dst, long maxCount) throws IOException {
        return DEFAULT_INSTANCE.readAndWrite_(src, dst, maxCount);
    }
    
    /**
     * Copies min(src remaining, maxCount) bytes.
     * 
     * If dst is a FileChannel, its size will be grown as needed, and if
     * it is a Bytebuffer, its limit will be grown as needed and as capacity allows
     * (you can use maxCount = min(maxCount,dst.remaining()) to avoid that last growth).
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param maxCount Max number of bytes to copy.
     * @return The number of bytes copied, possibly 0.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if maxCount < 0.
     * @throws BufferOverflowException if could not write all read bytes into dst, possibly after some copying.
     * @throws IOException if thrown by underlying treatments.
     */
    public static long readAndWriteAll(Object src, Object dst, long maxCount) throws IOException {
        return DEFAULT_INSTANCE.readAndWriteAll_(src, dst, maxCount);
    }
    
    /**
     * Copies count bytes.
     * 
     * If dst is a FileChannel, its size will be grown as needed, and if
     * it is a Bytebuffer, its limit will be grown as needed and as capacity allows
     * (you can use count = min(count,dst.remaining()) to avoid that last growth).
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param count Number of bytes to copy.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if count < 0.
     * @throws BufferUnderflowException if could not read the specified number of bytes from src, possibly after some copying.
     * @throws BufferOverflowException if could not write all read bytes into dst, possibly after some copying.
     * @throws IOException if thrown by underlying treatments.
     */
    public static void readAllAndWriteAll(Object src, Object dst, long count) throws IOException {
        DEFAULT_INSTANCE.readAllAndWriteAll_(src, dst, count);
    }
    
    /*
     * (instance position, specified position)
     */
    
    /**
     * Copies min(src remaining, dst remaining from dstPos, maxCount) bytes.
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param dstPos Position to copy to in dst.
     * @param maxCount Max number of bytes to copy.
     * @return The number of bytes copied, possibly 0.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if maxCount < 0.
     * @throws IOException if thrown by underlying treatments.
     */
    public static long readAndWriteAt(Object src, Object dst, long dstPos, long maxCount) throws IOException {
        return DEFAULT_INSTANCE.readAndWriteAt_(src, dst, dstPos, maxCount);
    }
    
    /**
     * Copies min(src remaining, maxCount) bytes.
     * 
     * If dst is a FileChannel, its size will be grown as needed, and if
     * it is a Bytebuffer, its limit will be grown as needed and as capacity allows
     * (you can use maxCount = min(maxCount,max(0,dst.limit()-dstPos)) to avoid that last growth).
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param dstPos Position to copy to in dst.
     * @param maxCount Max number of bytes to copy.
     * @return The number of bytes copied, possibly 0.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if maxCount < 0.
     * @throws BufferOverflowException if could not write all read bytes into dst, possibly after some copying.
     * @throws IOException if thrown by underlying treatments.
     */
    public static long readAndWriteAllAt(Object src, Object dst, long dstPos, long maxCount) throws IOException {
        return DEFAULT_INSTANCE.readAndWriteAllAt_(src, dst, dstPos, maxCount);
    }
    
    /**
     * Copies count bytes.
     * 
     * If dst is a FileChannel, its size will be grown as needed, and if
     * it is a Bytebuffer, its limit will be grown as needed and as capacity allows
     * (you can use count = min(count,max(0,dst.limit()-dstPos)) to avoid that last growth).
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param dstPos Position to copy to in dst.
     * @param count Number of bytes to copy.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if count < 0.
     * @throws BufferUnderflowException if could not read the specified number of bytes from src, possibly after some copying.
     * @throws BufferOverflowException if could not write all read bytes into dst, possibly after some copying.
     * @throws IOException if thrown by underlying treatments.
     */
    public static void readAllAndWriteAllAt(Object src, Object dst, long dstPos, long count) throws IOException {
        DEFAULT_INSTANCE.readAllAndWriteAllAt_(src, dst, dstPos, count);
    }
    
    /*
     * (specified position, instance position)
     */
    
    /**
     * Copies min(src remaining from srcPos, dst remaining, maxCount) bytes.
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param srcPos Position to copy from in src.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param maxCount Max number of bytes to copy.
     * @return The number of bytes copied, possibly 0.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if maxCount < 0.
     * @throws IOException if thrown by underlying treatments.
     */
    public static long readAtAndWrite(Object src, long srcPos, Object dst, long maxCount) throws IOException {
        return DEFAULT_INSTANCE.readAtAndWrite_(src, srcPos, dst, maxCount);
    }
    
    /**
     * Copies min(src remaining from srcPos, maxCount) bytes.
     * 
     * If dst is a FileChannel, its size will be grown as needed, and if
     * it is a Bytebuffer, its limit will be grown as needed and as capacity allows
     * (you can use maxCount = min(maxCount,dst.remaining()) to avoid that last growth).
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param srcPos Position to copy from in src.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param maxCount Max number of bytes to copy.
     * @return The number of bytes copied, possibly 0.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if maxCount < 0.
     * @throws BufferOverflowException if could not write all read bytes into dst, possibly after some copying.
     * @throws IOException if thrown by underlying treatments.
     */
    public static long readAtAndWriteAll(Object src, long srcPos, Object dst, long maxCount) throws IOException {
        return DEFAULT_INSTANCE.readAtAndWriteAll_(src, srcPos, dst, maxCount);
    }

    /**
     * Copies count bytes.
     * 
     * If dst is a FileChannel, its size will be grown as needed, and if
     * it is a Bytebuffer, its limit will be grown as needed and as capacity allows
     * (you can use count = min(count,dst.remaining()) to avoid that last growth).
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param srcPos Position to copy from in src.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param count Number of bytes to copy.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if count < 0.
     * @throws BufferUnderflowException if could not read the specified number of bytes from src, possibly after some copying.
     * @throws BufferOverflowException if could not write all read bytes into dst, possibly after some copying.
     * @throws IOException if thrown by underlying treatments.
     */
    public static void readAllAtAndWriteAll(Object src, long srcPos, Object dst, long count) throws IOException {
        DEFAULT_INSTANCE.readAllAtAndWriteAll_(src, srcPos, dst, count);
    }
    
    /*
     * (specified position, specified position)
     */
    
    /**
     * Copies min(src remaining from srcPos, dst remaining from dstPos, maxCount) bytes.
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param srcPos Position to copy from in src.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param dstPos Position to copy to in dst.
     * @param maxCount Max number of bytes to copy.
     * @return The number of bytes copied, possibly 0.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if maxCount < 0.
     * @throws IOException if thrown by underlying treatments.
     */
    public static long readAtAndWriteAt(Object src, long srcPos, Object dst, long dstPos, long maxCount) throws IOException {
        return DEFAULT_INSTANCE.readAtAndWriteAt_(src, srcPos, dst, dstPos, maxCount);
    }

    /**
     * Copies min(src remaining from srcPos, maxCount) bytes.
     * 
     * If dst is a FileChannel, its size will be grown as needed, and if
     * it is a Bytebuffer, its limit will be grown as needed and as capacity allows
     * (you can use maxCount = min(maxCount,max(0,dst.limit()-dstPos)) to avoid that last growth).
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param srcPos Position to copy from in src.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param dstPos Position to copy to in dst.
     * @param maxCount Max number of bytes to copy.
     * @return The number of bytes copied, possibly 0.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if maxCount < 0.
     * @throws BufferOverflowException if could not write all read bytes into dst, possibly after some copying.
     * @throws IOException if thrown by underlying treatments.
     */
    public static long readAtAndWriteAllAt(Object src, long srcPos, Object dst, long dstPos, long maxCount) throws IOException {
        return DEFAULT_INSTANCE.readAtAndWriteAllAt_(src, srcPos, dst, dstPos, maxCount);
    }

    /**
     * Copies count bytes.
     * 
     * If dst is a FileChannel, its size will be grown as needed, and if
     * it is a Bytebuffer, its limit will be grown as needed and as capacity allows
     * (you can use count = min(count,max(0,dst.limit()-dstPos)) to avoid that last growth).
     * 
     * @param src Source ByteBuffer or FileChannel.
     * @param srcPos Position to copy from in src.
     * @param dst Destination ByteBuffer or FileChannel.
     * @param dstPos Position to copy to in dst.
     * @param count Number of bytes to copy.
     * @throws NullPointerException if either src or dst is null.
     * @throws IllegalArgumentException if count < 0.
     * @throws BufferUnderflowException if could not read the specified number of bytes from src, possibly after some copying.
     * @throws BufferOverflowException if could not write all read bytes into dst, possibly after some copying.
     * @throws IOException if thrown by underlying treatments.
     */
    public static void readAllAtAndWriteAllAt(Object src, long srcPos, Object dst, long dstPos, long count) throws IOException {
        DEFAULT_INSTANCE.readAllAtAndWriteAllAt_(src, srcPos, dst, dstPos, count);
    }
    
    //--------------------------------------------------------------------------
    // PACKAGE-PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * Uses default chunks sizes.
     * 
     * @param mbbProvider Can be null.
     * @param threadSafe If true, using thread-local temporary ByteBuffers,
     *        else an instance-local one.
     */
    ByteCopyUtils(
            final InterfaceMBBHelper mbbProvider,
            final boolean threadSafe) {
        this(
                mbbProvider,
                threadSafe,
                DEFAULT_MAX_CHUNK_SIZE,
                DEFAULT_MAX_MBB_CHUNK_SIZE);
    }

    /**
     * @param mbbProvider Can be null.
     * @param threadSafe If true, using thread-local temporary ByteBuffers,
     *        else an instance-local one.
     */
    ByteCopyUtils(
            final InterfaceMBBHelper mbbProvider,
            final boolean threadSafe,
            final int maxChunkSize,
            final int maxMBBChunkSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException();
        }
        if (maxMBBChunkSize <= 0) {
            throw new IllegalArgumentException();
        }
        this.maxChunkSize = maxChunkSize;
        this.maxMBBChunkSize = maxMBBChunkSize;
        this.mbbHelper = mbbProvider;
        if (threadSafe) {
            this.tlTmpDirectBB = new ThreadLocal<ByteBuffer>() {
                @Override
                public ByteBuffer initialValue() {
                    return ByteBuffer.allocateDirect(maxChunkSize);
                }
            };
        } else {
            this.tlTmpDirectBB = null;
        }
    }

    /*
     * 
     */
    
    long readAndWrite_(Object src, Object dst, long maxCount) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(maxCount);
        return readXXXAndWriteXXX(src, INST_POS, dst, INST_POS, maxCount, false, false);
    }
    
    long readAndWriteAll_(Object src, Object dst, long maxCount) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(maxCount);
        return readXXXAndWriteXXX(src, INST_POS, dst, INST_POS, maxCount, false, true);
    }
    
    void readAllAndWriteAll_(Object src, Object dst, long count) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(count);
        final long n = readXXXAndWriteXXX(src, INST_POS, dst, INST_POS, count, true, true);
        if(ASSERTIONS)assert(n == count);
    }
    
    /*
     * 
     */
    
    long readAndWriteAt_(Object src, Object dst, long dstPos, long maxCount) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(dstPos);
        checkNonNegative(maxCount);
        return readXXXAndWriteXXX(src, INST_POS, dst, dstPos, maxCount, false, false);
    }
    
    long readAndWriteAllAt_(Object src, Object dst, long dstPos, long maxCount) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(dstPos);
        checkNonNegative(maxCount);
        return readXXXAndWriteXXX(src, INST_POS, dst, dstPos, maxCount, false, true);
    }
    
    void readAllAndWriteAllAt_(Object src, Object dst, long dstPos, long count) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(dstPos);
        checkNonNegative(count);
        final long n = readXXXAndWriteXXX(src, INST_POS, dst, dstPos, count, true, true);
        if(ASSERTIONS)assert(n == count);
    }
    
    /*
     * 
     */
    
    long readAtAndWrite_(Object src, long srcPos, Object dst, long maxCount) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(srcPos);
        checkNonNegative(maxCount);
        return readXXXAndWriteXXX(src, srcPos, dst, INST_POS, maxCount, false, false);
    }
    
    long readAtAndWriteAll_(Object src, long srcPos, Object dst, long maxCount) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(srcPos);
        checkNonNegative(maxCount);
        return readXXXAndWriteXXX(src, srcPos, dst, INST_POS, maxCount, false, true);
    }

    void readAllAtAndWriteAll_(Object src, long srcPos, Object dst, long count) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(srcPos);
        checkNonNegative(count);
        final long n = readXXXAndWriteXXX(src, srcPos, dst, INST_POS, count, true, true);
        if(ASSERTIONS)assert(n == count);
    }
    
    /*
     * 
     */
    
    long readAtAndWriteAt_(Object src, long srcPos, Object dst, long dstPos, long maxCount) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(srcPos);
        checkNonNegative(dstPos);
        checkNonNegative(maxCount);
        return readXXXAndWriteXXX(src, srcPos, dst, dstPos, maxCount, false, false);
    }
    
    long readAtAndWriteAllAt_(Object src, long srcPos, Object dst, long dstPos, long maxCount) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(srcPos);
        checkNonNegative(dstPos);
        checkNonNegative(maxCount);
        return readXXXAndWriteXXX(src, srcPos, dst, dstPos, maxCount, false, true);
    }

    void readAllAtAndWriteAllAt_(Object src, long srcPos, Object dst, long dstPos, long count) throws IOException {
        checkNonNull(src);
        checkNonNull(dst);
        checkNonNegative(srcPos);
        checkNonNegative(dstPos);
        checkNonNegative(count);
        final long n = readXXXAndWriteXXX(src, srcPos, dst, dstPos, count, true, true);
        if(ASSERTIONS)assert(n == count);
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    /**
     * TODO Constructor to make public if making instance methods public.
     * 
     * Creates an instance that is NOT thread-safe,
     * for it makes use of an instance-local temporary
     * ByteBuffer.
     */
    private ByteCopyUtils() {
        this(
                DefaultMBBHelper.INSTANCE,
                false, // threadSafe
                DEFAULT_MAX_CHUNK_SIZE,
                DEFAULT_MAX_MBB_CHUNK_SIZE);
    }

    /*
     * 
     */

    /**
     * Does pre-and-post treatments, and delegates actual copy
     * to appropriate readXXXAndWriteXXX_XXX_XXX method.
     * 
     * @param count Count or max count.
     */
    private long readXXXAndWriteXXX(
            final Object src,
            final long srcPosElseNeg,
            final Object dst,
            final long dstPosElseNeg,
            final long count,
            final boolean readAllCount,
            final boolean writeAllRead) throws IOException {
        
        final boolean posInSrc = (srcPosElseNeg < 0);
        final boolean posInDst = (dstPosElseNeg < 0);
        if(ASSERTIONS)assert(!(readAllCount && (!writeAllRead)));
        if(ASSERTIONS)assert(src != null);
        if(ASSERTIONS)assert(dst != null);
        if(ASSERTIONS)assert(count >= 0);
        
        /*
         * pre treatments
         */
        
        if (count == 0) {
            return 0;
        }
        
        final long srcPos;
        if (posInSrc) {
            srcPos = position(src);
        } else {
            srcPos = srcPosElseNeg;
        }
        final long srcInitialSize = size(src);
        final long srcInitialRemFromPos = Math.max(0, srcInitialSize - srcPos);
        if (srcInitialRemFromPos < count) {
            if (readAllCount) {
                throw new BufferUnderflowException();
            } else {
                if (srcInitialRemFromPos == 0) {
                    return 0;
                }
            }
        }
        
        final long toReadSrcCount = Math.min(srcInitialRemFromPos, count);
        if(ASSERTIONS)assert(toReadSrcCount > 0);
        
        final long dstPos;
        if (posInDst) {
            dstPos = position(dst);
        } else {
            dstPos = dstPosElseNeg;
        }
        // Size might be grown before copy.
        final long dstInitialSize = size(dst);
        final long dstInitialRemFromPos = Math.max(0, dstInitialSize - dstPos);
        final long toWriteDst;
        if (dstInitialRemFromPos < toReadSrcCount) {
            if (writeAllRead) {
                final long neededLimit = dstPos + toReadSrcCount;
                if (neededLimit < 0) {
                    // Couldn't grow that much (at least
                    // we couldn't keep track of such a
                    // file size).
                    throw new BufferOverflowException();
                }
                if (dst instanceof ByteBuffer) {
                    final ByteBuffer dstBB = asBB(dst);
                    if (dstInitialSize < neededLimit) {
                        // Need to grow.
                        if (neededLimit > dstBB.capacity()) {
                            // Can't grow that much.
                            throw new BufferOverflowException();
                        } else {
                            // Growing.
                            dstBB.limit(asInt(neededLimit));
                        }
                    }
                }
                // did/will grow (else throw)
                toWriteDst = toReadSrcCount;
            } else {
                toWriteDst = dstInitialRemFromPos;
            }
        } else {
            toWriteDst = toReadSrcCount;
        }
        
        final long n = Math.min(toReadSrcCount,toWriteDst);
        if (n == 0) {
            return 0;
        }
        
        /*
         * actual copy
         */
        
        final long nDone;
        if (src instanceof ByteBuffer) {
            if (dst instanceof ByteBuffer) {
                nDone = this.readXXXAndWriteXXX_BB_BB(
                        asBB(src),
                        asInt(srcPos),
                        asBB(dst),
                        asInt(dstPos),
                        asInt(n),
                        readAllCount,
                        writeAllRead,
                        posInSrc,
                        posInDst);
            } else {
                nDone = this.readXXXAndWriteXXX_BB_FC(
                        asBB(src),
                        asInt(srcPos),
                        asFC(dst),
                        dstPos,
                        asInt(n),
                        readAllCount,
                        writeAllRead,
                        posInSrc,
                        posInDst);
            }
        } else {
            if (dst instanceof ByteBuffer) {
                nDone = this.readXXXAndWriteXXX_FC_BB(
                        asFC(src),
                        srcPos,
                        asBB(dst),
                        asInt(dstPos),
                        asInt(n),
                        readAllCount,
                        writeAllRead,
                        posInSrc,
                        posInDst);
            } else {
                nDone = this.readXXXAndWriteXXX_FC_FC(
                        asFC(src),
                        srcPos,
                        asFC(dst),
                        dstPos,
                        n,
                        readAllCount,
                        writeAllRead,
                        posInSrc,
                        posInDst,
                        dstInitialSize);
            }
        }
        
        /*
         * post treatments
         */
        
        if (nDone != n) {
            if(ASSERTIONS)assert(nDone < n);
            final boolean couldHaveReadMore = (srcPos + nDone < srcInitialSize);
            // Note: if both readAllCount and writeAllRead are true,
            // an exception is necessarily thrown, as one could expect.
            if (readAllCount) {
                if (!couldHaveReadMore) {
                    throw new BufferUnderflowException();
                }
            }
            if (writeAllRead) {
                if (couldHaveReadMore) {
                    throw new BufferOverflowException();
                }
            }
        }

        return n;
    }
    
    /*
     * 
     */

    /**
     * @return The number of bytes copied, possibly 0.
     */
    private int readXXXAndWriteXXX_BB_BB(
            final ByteBuffer src,
            final int srcPos,
            final ByteBuffer dst,
            final int dstPos,
            final int n,
            final boolean readAllCount,
            final boolean writeAllRead,
            final boolean posInSrc,
            final boolean posInDst) {

        final boolean concurrentSrc = !posInSrc;
        final boolean concurrentDst = !posInDst;

        final int srcInitialPos;
        if (posInSrc) {
            srcInitialPos = srcPos;
        } else {
            srcInitialPos = src.position();
        }

        final int dstInitialPos;
        if (posInDst) {
            dstInitialPos = dstPos;
        } else {
            dstInitialPos = dst.position();
        }

        if (src.hasArray()) {
            if (dst.hasArray()) {
                System.arraycopy(
                        src.array(),
                        src.arrayOffset()+srcPos,
                        dst.array(),
                        dst.arrayOffset()+dstPos,
                        n);
            } else {
                // dst must be direct, since it is writable
                // but has no accessible array
                if(ASSERTIONS)assert(dst.isDirect());
                final ByteBuffer dstModifiable;
                if (concurrentDst) {
                    dstModifiable = dst.duplicate();
                } else {
                    dstModifiable = dst;
                }
                dstModifiable.position(dstPos);
                dstModifiable.put(src.array(), src.arrayOffset()+srcPos, n);
            }
        } else {
            if (dst.hasArray()) {
                final ByteBuffer srcModifiable;
                if (concurrentSrc) {
                    srcModifiable = src.duplicate();
                } else {
                    srcModifiable = src;
                }
                srcModifiable.position(srcPos);
                srcModifiable.get(dst.array(), dst.arrayOffset()+dstPos, n);
            } else {
                // dst must be direct, since it is writable
                // but has no accessible array
                if(ASSERTIONS)assert(dst.isDirect());
                final ByteBuffer srcModifiable;
                if (concurrentSrc) {
                    srcModifiable = src.duplicate();
                } else {
                    srcModifiable = src;
                }
                final ByteBuffer dstModifiable;
                if (concurrentDst) {
                    dstModifiable = dst.duplicate();
                } else {
                    dstModifiable = dst;
                }
                
                final int srcInitialLim = srcModifiable.limit();
                srcModifiable.limit(srcPos + n);
                srcModifiable.position(srcPos);
                
                setPosIfNeeded(dstModifiable, dstPos);
                
                dstModifiable.put(srcModifiable);
                
                setLimIfNeeded(src, srcInitialLim);
            }
        }
        if (posInSrc) {
            setPosIfNeeded(src, srcPos+n);
        } else {
            setPosIfNeeded(src, srcInitialPos);
        }
        if (posInDst) {
            setPosIfNeeded(dst, dstPos+n);
        } else {
            setPosIfNeeded(dst, dstInitialPos);
        }

        return n;
    }

    private int readXXXAndWriteXXX_BB_FC(
            final ByteBuffer src,
            final int srcPos,
            final FileChannel dst,
            final long dstPos,
            final int n,
            final boolean readAllCount,
            final boolean writeAllRead,
            final boolean posInSrc,
            final boolean posInDst) throws IOException {
        
        final boolean concurrentSrc = !posInSrc;

        final int srcInitialPos;
        if (posInSrc) {
            srcInitialPos = srcPos;
        } else {
            srcInitialPos = src.position();
        }

        int nDone = 0;
        
        /*
         * TODO For some reason, using FileChannel.write(ByteBuffer,long)
         * with a huge ByteBuffer can be very slow, as perf tests logs show
         * (100Mo copy using a SSD):
         * copy from BB(direct) to FC took 0.973 s
         * copy from BB(direct) to FC took 0.999 s
         * copy from BB(direct) to FC took 0.999 s
         * copy from BB(direct) to FC took 0.992 s
         * copy from BB(heap,array) to FC took 0.184 s
         * copy from BB(heap,array) to FC took 0.075 s
         * copy from BB(heap,array) to FC took 0.073 s
         * copy from BB(heap,array) to FC took 0.073 s
         * copy from BB(heap,noarray) to FC took 0.105 s
         * copy from BB(heap,noarray) to FC took 0.072 s
         * copy from BB(heap,noarray) to FC took 0.072 s
         * copy from BB(heap,noarray) to FC took 0.072 s
         * ===> So we disable it.
         */
        final boolean useWriteBigDirect = false;
        if (useWriteBigDirect && src.isDirect()) {
            final ByteBuffer srcModifiable;
            if (concurrentSrc) {
                srcModifiable = src.duplicate();
            } else {
                srcModifiable = src;
            }
            
            final int srcInitialLim = srcModifiable.limit();
            srcModifiable.limit(srcPos + n);
            srcModifiable.position(srcPos);
            
            // Uses a single native copy.
            nDone = dst.write(srcModifiable, dstPos);
            
            setLimIfNeeded(src, srcInitialLim);
            
            if (posInSrc) {
                if(ASSERTIONS)assert(srcModifiable == src);
                // has been updated
            } else {
                if (srcModifiable == src) {
                    // undoing position update
                    src.position(srcInitialPos);
                }
            }
        } else {
            // TODO Can't use a mapped buffer as destination buffer,
            // for it would require to either:
            // - know whether the channel is readable, which is
            //   required for mapping,
            // - or have a WRITE_ONLY mapping mode, useful for
            //   non-readable channel,
            // none of which is currently portably possible.
            final boolean useDstMBB = false;
            if (useDstMBB
                    && (this.mbbHelper != null)
                    && (this.mbbHelper.canMapAndUnmap(dst, MapMode.READ_WRITE))
                    && src.hasArray()) {
                while (nDone < n) {
                    final int nRemaining = (n-nDone);
                    final int tmpN = Math.min(nRemaining, this.maxMBBChunkSize);
                    final ByteBuffer dstMBB = this.mbbHelper.map(dst, MapMode.READ_WRITE, dstPos + nDone, tmpN);
                    try {
                        // src to mbb
                        // Uses a single native copy.
                        dstMBB.put(src.array(), src.arrayOffset()+srcPos + nDone, tmpN);
                        // Might not be an actual MappedByteBuffer,
                        // for tests or else.
                        if (dstMBB instanceof MappedByteBuffer) {
                            ((MappedByteBuffer)dstMBB).force();
                        }
                    } finally {
                        this.mbbHelper.unmap(dst, dstMBB);
                    }
                    nDone += tmpN;
                }
                if(ASSERTIONS)assert(nDone == n);
            } else {
                final ByteBuffer srcModifiable;
                if (concurrentSrc) {
                    srcModifiable = src.duplicate();
                } else {
                    srcModifiable = src;
                }

                final int srcInitialLim = srcModifiable.limit();
                
                final ByteBuffer tmpBB = this.getTmpDirectBB(n);
                while (nDone < n) {
                    final int nRemaining = (n-nDone);
                    
                    tmpBB.clear();
                    if (nRemaining < tmpBB.capacity()) {
                        tmpBB.limit(asInt(nRemaining));
                    }
                    final int tmpN = tmpBB.remaining();
                    
                    // src to tmp
                    srcModifiable.limit(srcPos + nDone + tmpN);
                    srcModifiable.position(srcPos + nDone);
                    tmpBB.put(srcModifiable);
                    
                    // tmp to dst
                    tmpBB.flip();
                    final int tmpNWritten = dst.write(tmpBB, dstPos + nDone);
                    nDone += tmpNWritten;
                    if (tmpNWritten != tmpN) {
                        // Giving up.
                        break;
                    }
                }
                setLimIfNeeded(src, srcInitialLim);
            }
            if (posInSrc) {
                src.position(srcPos + nDone);
            }
        }
        if (posInDst) {
            dst.position(dstPos + nDone);
        }

        return nDone;
    }

    /**
     * @return The number of bytes copied, possibly 0.
     */
    private int readXXXAndWriteXXX_FC_BB(
            final FileChannel src,
            final long srcPos,
            final ByteBuffer dst,
            final int dstPos,
            final int n,
            final boolean readAllCount,
            final boolean writeAllRead,
            final boolean posInSrc,
            final boolean posInDst) throws IOException {
        final boolean concurrentDst = !posInDst;

        final int dstInitialPos;
        if (posInDst) {
            dstInitialPos = dstPos;
        } else {
            dstInitialPos = dst.position();
        }

        int nDone = 0;
        
        if (dst.isDirect()) {
            final ByteBuffer dstModifiable;
            if (concurrentDst) {
                dstModifiable = dst.duplicate();
            } else {
                dstModifiable = dst;
            }
            
            final int dstInitialLim = dstModifiable.limit();
            dstModifiable.limit(dstPos + n);
            dstModifiable.position(dstPos);
            
            // Uses a single native copy.
            final int nReadOrM1_unused = src.read(dstModifiable, srcPos);
            // We can avoid to rely on read's result.
            nDone = n - dstModifiable.remaining();
            
            setLimIfNeeded(dst, dstInitialLim);
            
            if (posInDst) {
                if(ASSERTIONS)assert(dstModifiable == dst);
                // has been updated
            } else {
                if (dstModifiable == dst) {
                    // undoing position update
                    dst.position(dstInitialPos);
                }
            }
        } else {
            if ((this.mbbHelper != null)
                    && (this.mbbHelper.canMapAndUnmap(src, MapMode.READ_ONLY))) {
                while (nDone < n) {
                    final int nRemaining = (n-nDone);
                    final int tmpN = Math.min(nRemaining, this.maxMBBChunkSize);
                    final ByteBuffer srcMBB = this.mbbHelper.map(src, MapMode.READ_ONLY, srcPos + nDone, tmpN);
                    try {
                        // mbb to dst
                        // Uses a single native copy.
                        srcMBB.get(dst.array(), dst.arrayOffset()+dstPos + nDone, tmpN);
                    } finally {
                        this.mbbHelper.unmap(src, srcMBB);
                    }

                    nDone += tmpN;
                }
                if(ASSERTIONS)assert(nDone == n);
            } else {
                final ByteBuffer tmpBB = this.getTmpDirectBB(n);
                while (nDone < n) {
                    final int nRemaining = (n-nDone);
                    
                    tmpBB.clear();
                    if (nRemaining < tmpBB.capacity()) {
                        tmpBB.limit(asInt(nRemaining));
                    }
                    final int tmpN = tmpBB.remaining();
                    
                    // src to tmp
                    final int tmpNReadOrM1_unused = src.read(tmpBB, srcPos + nDone);
                    // We can avoid to rely on read's result.
                    final int tmpNDone = tmpN - tmpBB.remaining();
                    
                    // tmp to dst
                    tmpBB.flip();
                    tmpBB.get(dst.array(), dst.arrayOffset()+dstPos + nDone, tmpNDone);
                    
                    nDone += tmpNDone;
                    if (tmpNDone != tmpN) {
                        // Giving up.
                        break;
                    }
                }
            }
            if (posInDst) {
                dst.position(dstPos + nDone);
            }
        }
        if (posInSrc) {
            src.position(srcPos + nDone);
        }
        
        return nDone;
    }
    
    /**
     * @return The number of bytes copied, possibly 0.
     */
    private long readXXXAndWriteXXX_FC_FC(
            final FileChannel src,
            final long srcPos,
            final FileChannel dst,
            final long dstPos,
            final long n,
            final boolean readAllCount,
            final boolean writeAllRead,
            final boolean posInSrc,
            final boolean posInDst,
            final long dstInitialSize) throws IOException {
        
        final boolean concurrentSrc = !posInSrc;
        final boolean concurrentDst = !posInDst;
        
        final long srcInitialPos;
        if (posInSrc) {
            srcInitialPos = srcPos;
        } else {
            srcInitialPos = src.position();
        }

        final long dstInitialPos;
        if (posInDst) {
            dstInitialPos = dstPos;
        } else {
            dstInitialPos = dst.position();
        }

        long nDone = 0;
        
        if (!concurrentDst) {
            /*
             * dst not used concurrently, so we try to use src.transferTo(...),
             * hoping that it resolves to kernel's copy, and if it does not,
             * we hope that channel's implementation is still fast, using
             * mapped buffers and then unmaping them.
             */
            if(ASSERTIONS)assert(!posInSrc);
            
            final boolean useTransfertTo;
            if (dstPos == dstInitialPos) {
                useTransfertTo = true;
            } else {
                dst.position(dstPos);
                if (dst.position() != dstPos) {
                    // Most likely dst is in append mode,
                    // so we can't use src.transferTo(...)
                    // with the specified dstPos.
                    useTransfertTo = false;
                } else {
                    useTransfertTo = true;
                }
            }
            if (useTransfertTo) {
                // Uses kernel's copy,
                // else map/write(mbb),
                // else tmp/read(bb,int)/write(bb).
                nDone = src.transferTo(srcPos, n, dst);
                
                if (posInSrc) {
                    src.position(srcPos + nDone);
                }
                if (posInDst) {
                    // has been updated
                } else {
                    dst.position(dstInitialPos);
                }
                return nDone;
            }
        }

        if ((this.mbbHelper != null)
                && (this.mbbHelper.canMapAndUnmap(src, MapMode.READ_ONLY))) {
            while (nDone < n) {
                final long nRemaining = (n-nDone);
                final long tmpN = Math.min(nRemaining, this.maxMBBChunkSize);
                final ByteBuffer srcMBB = this.mbbHelper.map(src, MapMode.READ_ONLY, srcPos + nDone, tmpN);
                try {
                    final long tmpNWritten = dst.write(srcMBB, dstPos + nDone);
                    nDone += tmpNWritten;
                    if (tmpNWritten != tmpN) {
                        // Giving up.
                        break;
                    }
                } finally {
                    this.mbbHelper.unmap(src, srcMBB);
                }
            }
            if (posInSrc) {
                src.position(srcPos + nDone);
            }
            if (posInDst) {
                dst.position(dstPos + nDone);
            }
        } else {
            // TODO Taking care not to use transferFrom
            // if dstPos > dst.size(), because in that case
            // it doesn't transfer anything. If that is
            // considered a bug and gets changed, and not
            // caring about backward compatibility, could
            // remove this trick (and dstInitialSize arg).
            final boolean useTransferFrom;
            if (concurrentSrc || (dstPos > dstInitialSize)) {
                useTransferFrom = false;
            } else {
                if (srcPos == srcInitialPos) {
                    useTransferFrom = true;
                } else {
                    src.position(srcPos);
                    if (src.position() != srcPos) {
                        // Most likely src is in append mode,
                        // so we can't use dst.transferFrom(...)
                        // with the specified srcPos.
                        useTransferFrom = false;
                    } else {
                        useTransferFrom = true;
                    }
                }
            }
            if (useTransferFrom) {
                /*
                 * We hope that channel's implementation
                 * is fast, using mapped buffers and
                 * then unmaping them.
                 */
                if(ASSERTIONS)assert(!posInDst);
                
                // Uses map/write(mbb,int),
                // else tmp/read(bb)/write(bb,int).
                nDone = dst.transferFrom(src, dstPos, n);

                if (posInSrc) {
                    // has been updated
                } else {
                    src.position(srcInitialPos);
                }
                if (posInDst) {
                    dst.position(dstPos + nDone);
                }
            } else {
                /*
                 * Can't use transfer methods, which modify
                 * either src or dst position.
                 */
                if(ASSERTIONS)assert(!posInSrc);
                if(ASSERTIONS)assert(!posInDst);
                final ByteBuffer tmpBB = this.getTmpDirectBB(n);
                while (nDone < n) {
                    final long nRemaining = (n-nDone);

                    tmpBB.clear();
                    if (nRemaining < tmpBB.remaining()) {
                        tmpBB.limit(asInt(nRemaining));
                    }
                    final int tmpN = tmpBB.remaining();

                    // src to tmp
                    final int tmpNReadOrM1_unused = src.read(tmpBB, srcPos + nDone);
                    // We can avoid to rely on read's result.
                    final int tmpNRead = tmpN - tmpBB.remaining();

                    // tmp to dst
                    tmpBB.flip();
                    final int tmpNWritten = dst.write(tmpBB, dstPos + nDone);

                    nDone += tmpNWritten;
                    if (tmpNWritten != tmpNRead) {
                        // Giving up.
                        break;
                    }
                }
            }
        }

        return nDone;
    }
    
    /*
     * 
     */

    private static void checkNonNull(Object ref) {
        if (ref == null) {
            throw new NullPointerException();
        }
    }

    private static boolean checkNonNegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException();
        }
        return true;
    }

    /*
     * 
     */
    
    /**
     * @param n Total number of bytes to copy.
     * @return A temporary direct ByteBuffer, with capacity <= min(n,maxChunkSize)
     */
    private ByteBuffer getTmpDirectBB(long n) {
        if (this.tlTmpDirectBB != null) {
            // Using max capacity right away for thread-local temporary ByteBuffers.
            return this.tlTmpDirectBB.get();
        } else {
            final int minimalCap = minP(n, this.maxChunkSize);
            ByteBuffer bb = this.tmpDirectBB;
            if (bb == null) {
                // Not creating bigger than needed.
                bb = ByteBuffer.allocateDirect(minimalCap);
                this.tmpDirectBB = bb;
            } else {
                if (bb.capacity() < minimalCap) {
                    // Too small: at least doubling capacity, up to max capacity.
                    final int newCap = Math.min(this.maxChunkSize, Math.max(minimalCap, 2*bb.capacity()));
                    bb = ByteBuffer.allocateDirect(newCap);
                    this.tmpDirectBB = bb;
                }
            }
            return bb;
        }
    }

    /**
     * Setting position only if needed allows not to uselessly discard mark.
     */
    private static void setPosIfNeeded(ByteBuffer bb, int pos) {
        if (bb.position() != pos) {
            bb.position(pos);
        }
    }

    private static void setLimIfNeeded(ByteBuffer bb, int lim) {
        if (bb.limit() != lim) {
            bb.limit(lim);
        }
    }

    private static int asInt(long value) {
        return NumbersUtils.asInt(value);
    }
    
    private static ByteBuffer asBB(Object ref) {
        return (ByteBuffer)ref;
    }

    private static FileChannel asFC(Object ref) {
        return (FileChannel)ref;
    }
    
    private static long position(Object ref) throws IOException {
        if (ref instanceof ByteBuffer) {
            return ((ByteBuffer)ref).position();
        } else {
            return ((FileChannel)ref).position();
        }
    }

    private static long size(Object ref) throws IOException {
        if (ref instanceof ByteBuffer) {
            return ((ByteBuffer)ref).limit();
        } else {
            return ((FileChannel)ref).size();
        }
    }

    /*
     * min without having to bother with int/long ranges,
     * working if result is supposed >= 0.
     */
    
    /**
     * @return a if < b, else max(0,b).
     */
    private static int minP(int a, long b) {
        return (a < b) ? a : (int)Math.max(0,b);
    }

    /**
     * @return b if < a, else max(0,a).
     */
    private static int minP(long a, int b) {
        return minP(b,a);
    }
}
