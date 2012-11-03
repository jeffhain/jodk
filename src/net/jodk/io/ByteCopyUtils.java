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
 * ByteBuffers, between FileChannels, and between each other.
 * 
 * Various types of copies are provided, whether src and/or dst current
 * positions are used as copy positions, and whether the number of bytes to copy
 * must be limited by src and/or dst remaining amounts of bytes from copy
 * positions up to limit/size.
 * 
 * If src (or dst) copy position is specified (not using instance's one),
 * it allows for usage of the same instance of src (or dst) for non-overlapping
 * copies by multiple threads, but then no single thread is allowed to modify src
 * (or dst) position or limit/size concurrently, since these values will typically
 * be read by the (other) copying threads (for example, on ByteBuffer.duplicate()).
 * 
 * Unlike ByteBuffer.put(ByteBuffer), these treatments allow src and dst
 * to be a same instance, even if src and dst copy positions are identical,
 * in which case no actual copy is made. This allows to pass over the particular
 * (and most likely rare) case of obviously useless copies without an annoying
 * and risky (surprising) exception being thrown.
 * 
 * Copies of overlapping ranges, i.e. when src and dst copy ranges overlap in
 * memory, are handled (to avoid the risk of erasing byte to copy with copied
 * bytes):
 * - for BB to BB copies, by counting on ByteBuffer's treatments to handle it,
 *   which they do by delegating to System.arraycopy(...) for heap ByteBuffers,
 *   and to unsafe.copyMemory(long,long,long) (or hopefully equivalent treatments
 *   in non-Sun/Oracle JDKs) for direct ByteBuffers (TODO Unsafe not specified
 *   but has been observed to behave that way),
 * - for FC to FC copies, by copying forward or backward depending on src and dst
 *   copy positions.
 * Copies of overlapping ranges are not handled for copies between (mapped)
 * ByteBuffers and FileChannels, for there is no way to find out if and how they
 * overlap.
 * Note: The need for a backward copy corresponds to srcPos < dstPos, i.e. to
 * bytes "moving forward"; to avoid confusion we only use backward/forward
 * refering to the way the looping is done, not refering to bytes shift.
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
 * for homogeneity with other nothing-copied cases.
 * 
 * Treatments might use thread-local temporary ByteBuffers, which should be
 * small enough to allow for usage by many threads without too much memory
 * overhead.
 * 
 * FileChannels don't provide access to their readability state,
 * and we want semantics to be similar for ByteBuffers and FileChannels,
 * so ByteBuffers read-only-ness check is delegated to ByteBuffer's method.
 * As a result, no ReadOnlyBufferException or NonWritableChannelException
 * is thrown unless an attempt to write data is actually made.
 * 
 * If src or dst is a ByteBuffer or a FileChannel not in append mode, and that
 * its initial position is used as copy position, if an exception is thrown
 * during the copy, its position is reset (if it changed) to its initial
 * position, and not moved according to the number of bytes copied, for the copy
 * might have been done backward to avoid erasing bytes to copy with copied
 * bytes, and we don't want the user to believe that some bytes were copied
 * where they were not.
 * On the other hand, in case of exception, src or dst limit or size might have
 * been changed and is not reset to its initial value, both to fail fast, and
 * to allow user to look at post-exception content state.
 * 
 * These methods are rather permissive about their arguments.
 * For example, if specified src position is past src size,
 * System.arraycopy(byte[],int,byte[],int,int)
 * throws, but
 * IOUtils.readAtAndWriteAllAt(Object,long,Object,long,long)
 * just returns 0.
 */
public class ByteCopyUtils {

    /*
     * TODO:
     * 
     * - These treatments could be considerably simplified, and made faster and
     *   garbage-free, if the JDK would provide more appropriate low level
     *   primitives, in particular native copy treatments that don't make use of
     *   position.
     *   
     * - The API is very open about when an IOException can be thrown,
     *   not to constrain the implementation, but a better (lower-level)
     *   implementation could precise it some.
     *   
     * - Could make instance methods public, along with some constructor,
     *   and allow for use of instance-local temporary ByteBuffers,
     *   which could help in case of high threads turnover that
     *   would make thread-local temporary ByteBuffers hurt.
     */
    
    /*
     * Not allowing user to specify whether src or dst instances are supposed to
     * be used concurrently when a position is specified, in which case we
     * always support concurrency, for we prefer to end up with the eventually
     * associated garbage (.duplicate()) and additional copies, than having
     * to simplify the API if/when efficient concurrency-proof implementations
     * become available.
     * 
     * Also, if position is specified with concurrency-proof-ness turned off,
     * to avoid a call to .duplicate(), treatments could temporarily change
     * limit and position of the specified ByteBuffer, which could clear
     * the mark if any.
     * 
     * Always considering possible concurrent usage of ByteBuffers when position
     * is specified also causes to never use specified instance's position or
     * limit as temporary position or limit, and thus not having to restore them
     * after use, since then we work on a .duplicate().
     */
    
    /*
     * Taking care not to use FileChannel.read/write with large heap buffers,
     * for Sun's implementation uses an as big temporary direct buffer,
     * and even if it was small, it would generate useless garbage
     * since we already have our own temporary ByteBuffers.
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
     * dstC.transferFrom(srcC,dstPos,count)                |   mod   |   fix   |     stops     |  stops/grows  | stops if dstPos > dst.size
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
     * - System.arraycopy(srcBB.array(),srcOff+srcPos,dstBB.array(),dstOff+dstPos,count)
     *   (concurrent)
     * 
     * 
     * - ByteBuffer.put(srcBB)
     *   (not concurrent, native if dst is direct)
     * 
     * - ByteBuffer.put(srcBB.array(),srcOff+srcPos,count)
     *   (concurrent for src, native if dst is direct)
     * 
     * - ByteBuffer.get(dstBB.array(),dstOff+dstPos,count)
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
     * - FileChannel.transferTo(srcPos,count,dstWBC)
     *   (concurrent for src, might use
     *   kernel's copy,
     *   else map/write(mbb) (chunks <= 8Mio),
     *   else tmp/read(bb,int)/write(bb) (chunks <= 8Kio))
     *   (takes dst position lock (through use of write(BB)))
     *   (can grow dst)
     * 
     * - FileChannel.transferFrom(srcRBC,dstPos,count)
     *   (concurrent for dst, uses
     *   map/write(mbb,int) (chunks <= 8Mio),
     *   else tmp/read(bb)/write(bb,int) (chunks <= 8Kio))
     *   (takes src position lock)
     *   (can grow dst, but doesn't if dstPos > dst.size())
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
     * TODO For some reason, intensive benches sometimes hang up to nearly a
     * second in treatments involving MBBs.
     * Since this problem doesn't arise with temporary direct ByteBuffer
     * approaches, and since MBBs don't provide much better performances,
     * we just completely disable MBBs usage.
     */
    private static final boolean ALLOW_MBB = false;
    
    /**
     * TODO It appears that, depending on OS/architecture, and possibly only for
     * copy sizes below a certain threshold (256 bytes or so), FileChannel.write
     * methods don't use overlapping-proof treatments, but either forward or
     * backward loops on bytes.
     * This can causes FC to FC copies between FileChannels corresponding
     * to a same file not to behave properly when using MBB (i.e. no temporary
     * ByteBuffer).
     * As a result, for FC to FC copies, MBBs are disabled.
     */
    private static final boolean ALLOW_MBB_FOR_FORWARD_FC_FC_COPIES = false;
    private static final boolean ALLOW_MBB_FOR_BACKWARD_FC_FC_COPIES = false;
    
    /**
     * TODO Taking care not to use FileChannel.write with a too large (500Kio or so)
     * direct buffer, for it can be slow for some reason (observed on WinXP/7,
     * not observed on Linux): cutting the write in chunks if it is too large.
     * (For the same reason, not using FileChannel.transferTo/transferFrom.
     * FileChannel.read doesn't have that problem.)
     */
    private static final boolean WRITE_BY_CHUNK = true;
    
    /**
     * Experimentally, 256Kio was found to be fast, and 512Kio quite slow,
     * so we use 32Kio, which is far enough from 256Kio to make "sure"
     * we are not slow, and large enough to make sure we are still fast.
     */
    private static final int DEFAULT_MAX_WRITE_CHUNK_SIZE = 32 * 1024;

    /**
     * Similar to java.nio.FileChannelImpl.TRANSFER_SIZE = 8 * 1024.
     * 
     * Not too large to avoid too large memory overheap in case of
     * usage of thread-safe instance by many threads.
     * 
     * Using same size than max write chunk size (being larger
     * could help for reads (not by much since it's large already),
     * but it would use more memory, so should be avoided).
     */
    private static final int DEFAULT_MAX_TMP_CHUNK_SIZE = 32 * 1024;
    
    static final int DEFAULT_MBB_THRESHOLD = 1024 * 1024;

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
    
    /**
     * Max copy size when using FileChannel.write(ByteBuffer,long)
     * with a (direct) ByteBuffer (mapped or not).
     * Needs to be small enough not to cause the slowness to show up,
     * and large enough to reduce overhead.
     */
    private final int maxWriteChunkSize;
    
    /**
     * Max size for temporary ByteBuffers.
     */
    private final int maxTmpChunkSize;
    
    /**
     * Copy size from which we try to use MBB,
     * i.e. from which using MBB should be faster.
     */
    private final int mbbThreshold;
    
    /**
     * Max size for mapped ByteBuffer.
     */
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
        +",maxWriteChunkSize="+this.maxWriteChunkSize
        +",maxTmpChunkSize="+this.maxTmpChunkSize
        +",mbbThreshold="+this.mbbThreshold
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
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws BufferOverflowException if dst capacity (Long.MAX_VALUE if FileChannel)
     *         does not allow for all bytes to copy to be written.
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws BufferUnderflowException if src initial size does not allow to
     *         read count bytes.
     * @throws BufferOverflowException if dst capacity (Long.MAX_VALUE if FileChannel)
     *         does not allow for all bytes to copy to be written.
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws BufferOverflowException if dst capacity (Long.MAX_VALUE if FileChannel)
     *         does not allow for all bytes to copy to be written.
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws BufferUnderflowException if src initial size does not allow to
     *         read count bytes.
     * @throws BufferOverflowException if dst capacity (Long.MAX_VALUE if FileChannel)
     *         does not allow for all bytes to copy to be written.
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws BufferOverflowException if dst capacity (Long.MAX_VALUE if FileChannel)
     *         does not allow for all bytes to copy to be written.
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws BufferUnderflowException if src initial size does not allow to
     *         read count bytes.
     * @throws BufferOverflowException if dst capacity (Long.MAX_VALUE if FileChannel)
     *         does not allow for all bytes to copy to be written.
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws BufferOverflowException if dst capacity (Long.MAX_VALUE if FileChannel)
     *         does not allow for all bytes to copy to be written.
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @throws BufferUnderflowException if src initial size does not allow to
     *         read count bytes.
     * @throws BufferOverflowException if dst capacity (Long.MAX_VALUE if FileChannel)
     *         does not allow for all bytes to copy to be written.
     * @throws IOException if thrown by underlying treatments, or if src is a
     *         FileChannel and depletes earlier than indicated by its initial
     *         size, or if dst is a FileChannel and all read bytes could not be
     *         written in it.
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
     * @param mbbHelper Can be null.
     * @param threadSafe If true, using thread-local temporary ByteBuffers,
     *        else an instance-local one.
     */
    ByteCopyUtils(
            final InterfaceMBBHelper mbbHelper,
            final boolean threadSafe) {
        this(
                mbbHelper,
                threadSafe,
                DEFAULT_MAX_WRITE_CHUNK_SIZE,
                DEFAULT_MAX_TMP_CHUNK_SIZE,
                DEFAULT_MBB_THRESHOLD,
                DEFAULT_MAX_MBB_CHUNK_SIZE);
    }

    /**
     * @param mbbHelper Can be null.
     * @param threadSafe If true, using thread-local temporary ByteBuffers,
     *        else an instance-local one.
     * @param maxWriteChunkSize Must be > 0.
     * @param maxTmpChunkSize Must be > 0.
     * @param mbbThreshold Must be >= 0.
     * @param maxMBBChunkSize Must be > 0.
     */
    ByteCopyUtils(
            final InterfaceMBBHelper mbbHelper,
            final boolean threadSafe,
            final int maxWriteChunkSize,
            final int maxTmpChunkSize,
            final int mbbThreshold,
            final int maxMBBChunkSize) {
        if (maxWriteChunkSize <= 0) {
            throw new IllegalArgumentException();
        }
        if (maxTmpChunkSize <= 0) {
            throw new IllegalArgumentException();
        }
        if (mbbThreshold < 0) {
            throw new IllegalArgumentException();
        }
        if (maxMBBChunkSize <= 0) {
            throw new IllegalArgumentException();
        }
        this.maxWriteChunkSize = maxWriteChunkSize;
        this.maxTmpChunkSize = maxTmpChunkSize;
        this.mbbThreshold = mbbThreshold;
        this.maxMBBChunkSize = maxMBBChunkSize;
        this.mbbHelper = mbbHelper;
        if (threadSafe) {
            this.tlTmpDirectBB = new ThreadLocal<ByteBuffer>() {
                @Override
                public ByteBuffer initialValue() {
                    return ByteBuffer.allocateDirect(maxTmpChunkSize);
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
                false); // threadSafe
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
        if(ASSERTIONS)assert(!(readAllCount && (!writeAllRead)));
        if(ASSERTIONS)assert(src != null);
        if(ASSERTIONS)assert(dst != null);
        if(ASSERTIONS)assert(count >= 0);
        
        final boolean posInSrc = (srcPosElseNeg < 0);
        final boolean posInDst = (dstPosElseNeg < 0);
        
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
                        // If dst is same instance than src,
                        // this grow needs not to impact considered
                        // src remaining bytes, which it doesn't
                        // since this has already been computed.
                        if (neededLimit > dstBB.capacity()) {
                            // Can't grow that much.
                            throw new BufferOverflowException();
                        } else {
                            // Growing.
                            dstBB.limit(asInt(neededLimit));
                        }
                    }
                } else {
                    // We assume that device has enough capacity,
                    // and will consider any limitation to be unexpected.
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
         * copy
         */
        
        if ((src == dst)
                && (srcPos == dstPos)) {
            // Virtual copy.
            // Note: if (src == dst), then, if both posInSrc and posInDst
            // are true, we have (srcPos == dstPos), so we end up here.
            // Can't have to grow (can't copy more than what's left).
            if (posInSrc || posInDst) {
                position(src, srcPos+n);
            }
        } else {
            if (src instanceof ByteBuffer) {
                if (dst instanceof ByteBuffer) {
                    this.readXXXAndWriteXXX_BB_BB(
                            asBB(src),
                            asInt(srcPos),
                            asBB(dst),
                            asInt(dstPos),
                            asInt(n),
                            posInSrc,
                            posInDst);
                } else {
                    this.readXXXAndWriteXXX_BB_FC(
                            asBB(src),
                            asInt(srcPos),
                            asFC(dst),
                            dstPos,
                            asInt(n),
                            posInSrc,
                            posInDst);
                }
            } else {
                if (dst instanceof ByteBuffer) {
                    this.readXXXAndWriteXXX_FC_BB(
                            asFC(src),
                            srcPos,
                            asBB(dst),
                            asInt(dstPos),
                            asInt(n),
                            posInSrc,
                            posInDst);
                } else {
                    this.readXXXAndWriteXXX_FC_FC(
                            asFC(src),
                            srcPos,
                            asFC(dst),
                            dstPos,
                            n,
                            posInSrc,
                            posInDst);
                }
            }
        }

        return n;
    }
    
    /*
     * 
     */
    
    private void readXXXAndWriteXXX_BB_BB(
            final ByteBuffer src,
            final int srcPos,
            final ByteBuffer dst,
            final int dstPos,
            final int n,
            final boolean posInSrc,
            final boolean posInDst) {

        final boolean sameInstance = (src == dst);

        boolean allCopied = false;
        
        try {
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
                    // but has no accessible array; so there
                    // is no risk of overlapping.
                    if(ASSERTIONS)assert(dst.isDirect());
                    final ByteBuffer dstModifiable;
                    if (posInDst) {
                        dstModifiable = dst;
                    } else {
                        dstModifiable = dst.duplicate();
                    }
                    dstModifiable.position(dstPos);
                    dstModifiable.put(src.array(), src.arrayOffset()+srcPos, n);
                }
            } else {
                if (dst.hasArray()) {
                    // If src is a read-only version of dst content,
                    // there is a risk of overlapping, but then
                    // HeapByteBuffer.get should resolve to
                    // System.arraycopy, so it's handled.
                    final ByteBuffer srcModifiable;
                    if (posInSrc) {
                        srcModifiable = src;
                    } else {
                        srcModifiable = src.duplicate();
                    }
                    srcModifiable.position(srcPos);
                    srcModifiable.get(dst.array(), dst.arrayOffset()+dstPos, n);
                } else {
                    // dst has no accessible array, so it is either
                    // a read-only heap ByteBuffer, or a direct ByteBuffer.
                    // To take care of overlapping, we can suppose that dst
                    // is direct, since if it is a read-only heap ByteBuffer
                    // it will just throw.
                    final ByteBuffer srcModifiable;
                    if (posInSrc) {
                        srcModifiable = src;
                    } else {
                        srcModifiable = src.duplicate();
                    }
                    final ByteBuffer dstModifiable;
                    if (posInDst) {
                        dstModifiable = dst;
                    } else {
                        dstModifiable = dst.duplicate();
                    }
                    // If posInSrc and posInDst are both true,
                    // and src == dst, then we have
                    // srcModifiable == dstModifiable,
                    // and put should throw, but that
                    // won't happend since in that case we
                    // don't even get to call the current method.
                    
                    final int srcInitialLim = srcModifiable.limit();
                    try {
                        srcModifiable.limit(srcPos + n);
                        srcModifiable.position(srcPos);
                        dstModifiable.position(dstPos);
                        if (false) {
                            // TODO On Sun/Oracle JVM at least, put between two
                            // direct ByteBuffers resolves to Unsafe.copyMemory(long,long,long),
                            // which seems to behave like System.arraycopy(...), i.e. seems
                            // to handle the case of overlapping bytes.
                            // But ByteBuffer.put(ByteBuffer) Javadoc says that it behaves
                            // (if no exception) like "while (src.hasRemaining()) dst.put(src.get());",
                            // which is a forward copy and is not suited if memory is shared
                            // and the copy moves bytes forward.
                            // ===> We count on all JDKs to handle overlapping for this copy, and on
                            // Oracle not changing direct ByteBuffer's put to align it with its spec.
                            
                            // TODO Code to complete and use if we don't count on
                            // ByteBuffer.put(ByteBuffer) to handle overlapping for
                            // direct ByteBuffers.
                            if (src.isDirect()) {
                                // We suppose dst direct, so there is a risk of overlapping.
                                if (false) {
                                    if (src == dst) {
                                        // Overlapping for sure, but we know how.
                                    } else {
                                        final boolean srcMBB = (src instanceof MappedByteBuffer);
                                        final boolean dstMBB = (src instanceof MappedByteBuffer);
                                        if (srcMBB != dstMBB) {
                                            // No risk of overlapping.
                                        } else {
                                            // Risk of overlapping, and we don't know how.
                                            // Would need to use an as big temporary ByteBuffer
                                            // (ouch!).
                                        }
                                    }
                                }
                            } else {
                                // We suppose dst direct, so there is no risk of overlapping.
                            }
                        }
                        dstModifiable.put(srcModifiable);
                    } finally {
                        // In case it is src.
                        setLimIfNeeded(srcModifiable, srcInitialLim);
                    }
                }
            }
            
            allCopied = true;
        } finally {
            // Need to set src or dst position as long as no .duplicate() has been done,
            // even if an exception has been thrown, for it might have been
            // used as a temporary value.
            final int nOr0 = (allCopied ? n : 0);
            if (sameInstance) {
                // If both posInSrc and posInDst are true,
                // any of the first two cases works,
                // since then srcPos equals dstPos.
                if (posInSrc) {
                    setPosIfNeeded(src, srcPos + nOr0);
                } else if (posInDst) {
                    setPosIfNeeded(src, dstPos + nOr0);
                } else {
                    // Did not modify the ByteBuffer (concurrency = .duplicate()).
                }
            } else {
                try {
                    if (posInSrc) {
                        setPosIfNeeded(src, srcPos + nOr0);
                    } else {
                        // Did not modify src (concurrency = .duplicate()).
                    }
                } finally {
                    if (posInDst) {
                        setPosIfNeeded(dst, dstPos + nOr0);
                    } else {
                        // Did not modify dst (concurrency = .duplicate()).
                    }
                }
            }
        }
    }

    /**
     * @throws IOException if thrown by underlying treatments, or if could not
     *         write all "n" bytes into dst.
     */
    private void readXXXAndWriteXXX_BB_FC(
            final ByteBuffer src,
            final int srcPos,
            final FileChannel dst,
            final long dstPos,
            final int n,
            final boolean posInSrc,
            final boolean posInDst) throws IOException {
        
        boolean allCopied = false;
        
        try {
            if (src.isDirect()) {
                // TODO There is a risk of overlapping if src
                // is a ByteBuffer mapped on FileChannel's file,
                // but we can't figure it out, so in that case
                // the copy might be messed-up.
                final ByteBuffer srcModifiable;
                if (posInSrc) {
                    srcModifiable = src;
                } else {
                    srcModifiable = src.duplicate();
                }
                final int srcInitialLim = srcModifiable.limit();
                try {
                    srcModifiable.limit(srcPos + n);
                    srcModifiable.position(srcPos);

                    writeByChunks(srcModifiable, dst, dstPos);
                } finally {
                    // In case it is src.
                    setLimIfNeeded(srcModifiable, srcInitialLim);
                }
            } else {
                // src is a heap ByteBuffer, so there is no risk
                // of overlapping.
                final ByteBuffer srcModifiable;
                if (posInSrc) {
                    srcModifiable = src;
                } else {
                    srcModifiable = src.duplicate();
                }

                final int srcInitialLim = srcModifiable.limit();
                try {
                    final ByteBuffer tmpBB = this.getTmpDirectBB(n);
                    int nDone = 0;
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
                        writeByChunks(tmpBB, dst, dstPos + nDone);

                        nDone += tmpN;
                    }
                } finally {
                    // In case it is src.
                    setLimIfNeeded(srcModifiable, srcInitialLim);
                }
            }
            
            allCopied = true;
        } finally {
            final int nOr0 = (allCopied ? n : 0);
            try {
                // Need to set src position as long as no .duplicate() has been done,
                // even if an exception has been thrown, for it might have been
                // used as a temporary value.
                if (posInSrc) {
                    setPosIfNeeded(src, srcPos + nOr0);
                } else {
                    // Did not modify src (concurrency = .duplicate()).
                }
            } finally {
                // No need to set FileChannel position if not all has been copied,
                // since its position is never changed during copy, and then we
                // also take care not to set it, because it causes an IO.
                if (posInDst && (nOr0 != 0)) {
                    dst.position(dstPos + nOr0);
                }
            }
        }
    }

    /**
     * @throws IOException if thrown by underlying treatments, or if could not
     *         read all "n" bytes from src.
     */
    private void readXXXAndWriteXXX_FC_BB(
            final FileChannel src,
            final long srcPos,
            final ByteBuffer dst,
            final int dstPos,
            final int n,
            final boolean posInSrc,
            final boolean posInDst) throws IOException {

        boolean allCopied = false;
        
        try {
            if (dst.isDirect()) {
                // TODO There is a risk of overlapping if dst
                // is a ByteBuffer mapped on FileChannel's file,
                // but we can't figure it out, so in that case
                // the copy might be messed-up, unless read
                // implementation takes care of it.
                final ByteBuffer dstModifiable;
                if (posInDst) {
                    dstModifiable = dst;
                } else {
                    dstModifiable = dst.duplicate();
                }
                
                final int dstInitialLim = dstModifiable.limit();
                try {
                    dstModifiable.limit(dstPos + n);
                    dstModifiable.position(dstPos);
                    
                    // Uses a single native copy.
                    final int nReadOrM1_unused = src.read(dstModifiable, srcPos);
                    // We can avoid to rely on read's result.
                    if (dstModifiable.remaining() != 0) {
                        throwUnexpectedUnderflow();
                    }
                } finally {
                    // In case it is dst.
                    setLimIfNeeded(dstModifiable, dstInitialLim);
                }
            } else {
                if (ALLOW_MBB
                        && (n >= this.mbbThreshold)
                        && (this.mbbHelper != null)
                        && (this.mbbHelper.canMapAndUnmap(src, MapMode.READ_ONLY))) {
                    int nDone = 0;
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
                } else {
                    final ByteBuffer tmpBB = this.getTmpDirectBB(n);
                    int nDone = 0;
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
                        if (tmpBB.remaining() != 0) {
                            throwUnexpectedUnderflow();
                        }
                        
                        // tmp to dst
                        tmpBB.flip();
                        tmpBB.get(dst.array(), dst.arrayOffset()+dstPos + nDone, tmpN);
                        
                        nDone += tmpN;
                    }
                }
            }
            
            allCopied = true;
        } finally {
            final int nOr0 = (allCopied ? n : 0);
            try {
                // No need to set FileChannel position if not all has been copied,
                // since its position is never changed during copy, and then we
                // also take care not to set it, because it causes an IO.
                if (posInSrc && (nOr0 != 0)) {
                    src.position(srcPos + nOr0);
                }
            } finally {
                // Need to set dst position as long as no .duplicate() has been done,
                // even if an exception has been thrown, for it might have been
                // used as a temporary value.
                if (posInDst) {
                    setPosIfNeeded(dst, dstPos + nOr0);
                } else {
                    // Did not modify dst (concurrency = .duplicate()).
                }
            }
        }
    }
    
    /**
     * @throws IOException if thrown by underlying treatments, or if could not
     *         read all "n" bytes from src, or if could not write all read bytes
     *         into dst.
     */
    private void readXXXAndWriteXXX_FC_FC(
            final FileChannel src,
            final long srcPos,
            final FileChannel dst,
            final long dstPos,
            final long n,
            final boolean posInSrc,
            final boolean posInDst) throws IOException {

        // Taking care of copy direction, in case src and dst
        // correspond to a same file.
        // We also only go backward if copy ranges actually
        // overlap, because going backward can decrease
        // performances if files are different, since it might
        // first grow dst file to its target size, and then fill
        // up lower bytes.
        final boolean copyBackward = (srcPos < dstPos) && (srcPos + n > dstPos);

        boolean allCopied = false;
        
        try {
            if (ALLOW_MBB
                    && (n >= this.mbbThreshold)
                    && (((!copyBackward) && ALLOW_MBB_FOR_FORWARD_FC_FC_COPIES)
                            || (copyBackward && ALLOW_MBB_FOR_BACKWARD_FC_FC_COPIES))
                    && (this.mbbHelper != null)
                    && (this.mbbHelper.canMapAndUnmap(src, MapMode.READ_ONLY))) {
                long nDone = 0;
                while (nDone < n) {
                    final long nRemaining = (n-nDone);
                    final int tmpN = minP(nRemaining, this.maxMBBChunkSize);
                    final long tmpOffset = (copyBackward ? (nRemaining-tmpN) : nDone);
                    final ByteBuffer srcMBB = this.mbbHelper.map(src, MapMode.READ_ONLY, srcPos + tmpOffset, tmpN);
                    try {
                        writeByChunks(srcMBB, dst, dstPos + tmpOffset);
                        nDone += tmpN;
                    } finally {
                        this.mbbHelper.unmap(src, srcMBB);
                    }
                }
            } else {
                final ByteBuffer tmpBB = this.getTmpDirectBB(n);
                long nDone = 0;
                while (nDone < n) {
                    final long nRemaining = (n-nDone);

                    tmpBB.clear();
                    if (nRemaining < tmpBB.remaining()) {
                        tmpBB.limit(asInt(nRemaining));
                    }
                    final int tmpN = tmpBB.remaining();

                    final long tmpOffset = (copyBackward ? (nRemaining-tmpN) : nDone);
                    
                    // src to tmp
                    final int tmpNReadOrM1_unused = src.read(tmpBB, srcPos + tmpOffset);
                    // We can avoid to rely on read's result.
                    if (tmpBB.remaining() != 0) {
                        throwUnexpectedUnderflow();
                    }

                    // tmp to dst
                    tmpBB.flip();
                    writeByChunks(tmpBB, dst, dstPos + tmpOffset);
                    nDone += tmpN;
                }
            }
            
            allCopied = true;
        } finally {
            final long nOr0 = (allCopied ? n : 0);
            // No need to set FileChannels position if not all has been copied,
            // since their position is never changed during copy, and then we
            // also take care not to set it, because it causes an IO.
            if (nOr0 != 0) {
                try {
                    if (posInSrc) {
                        src.position(srcPos + nOr0);
                    }
                } finally {
                    if (posInDst) {
                        dst.position(dstPos + nOr0);
                    }
                }
            }
        }
    }
    
    /*
     * 
     */
    
    /**
     * @param srcDirModBB Must be direct and modifiable (position and limit)
     *        (i.e. not concurrently used).
     * @throws IOException if thrown by underlying treatments, or if could not
     *         write all ByteBuffer's content into the specified FileChannel.
     */
    private void writeByChunks(
            final ByteBuffer srcDirModBB,
            final FileChannel dst,
            final long dstPos) throws IOException {
        if(ASSERTIONS)assert(srcDirModBB.isDirect());
        
        final int n = srcDirModBB.remaining();
        
        if (WRITE_BY_CHUNK) {
            final int srcInitialPos = srcDirModBB.position();
            int nDone = 0;
            while (nDone < n) {
                final int nRemaining = (n-nDone);

                final int tmpN = Math.min(nRemaining, this.maxWriteChunkSize);

                // src chunk to dst
                srcDirModBB.limit(srcInitialPos + nDone + tmpN);
                srcDirModBB.position(srcInitialPos + nDone);
                final int tmpNWritten_unused = dst.write(srcDirModBB, dstPos + nDone);
                // For homogeneity with reads, relying on ByteBuffer's remaining.
                final int tmpNWritten = (tmpN - srcDirModBB.remaining());
                if (tmpNWritten != tmpN) {
                    throwUnexpectedOverflow();
                }
                nDone += tmpN;
            }
        } else {
            // Uses a single native copy.
            int nWritten = dst.write(srcDirModBB, dstPos);
            if (nWritten != n) {
                throwUnexpectedOverflow();
            }
        }
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
    
    private static void throwUnexpectedUnderflow() throws IOException {
        throw new IOException("Unexpected underflow");
    }

    private static void throwUnexpectedOverflow() throws IOException {
        throw new IOException("Unexpected overflow");
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
            final int minimalCap = minP(n, this.maxTmpChunkSize);
            ByteBuffer bb = this.tmpDirectBB;
            if (bb == null) {
                // Not creating bigger than needed.
                bb = ByteBuffer.allocateDirect(minimalCap);
                this.tmpDirectBB = bb;
            } else {
                if (bb.capacity() < minimalCap) {
                    // Too small: at least doubling capacity, up to max capacity.
                    final int newCap = Math.min(this.maxTmpChunkSize, Math.max(minimalCap, 2*bb.capacity()));
                    bb = ByteBuffer.allocateDirect(newCap);
                    this.tmpDirectBB = bb;
                }
            }
            return bb;
        }
    }

    /**
     * Setting position only if needed allows not to uselessly discard mark,
     * and to avoid useless/dangerous(concurrency) writes.
     */
    private static void setPosIfNeeded(ByteBuffer bb, int pos) {
        if (bb.position() != pos) {
            bb.position(pos);
        }
    }

    /**
     * To avoid useless/dangerous(concurrency) writes.
     */
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
    
    private static long position(Object container) throws IOException {
        if (container instanceof ByteBuffer) {
            return ((ByteBuffer)container).position();
        } else {
            return ((FileChannel)container).position();
        }
    }

    private static void position(Object container, long position) throws IOException {
        if (container instanceof ByteBuffer) {
            ((ByteBuffer)container).position(asInt(position));
        } else {
            ((FileChannel)container).position(position);
        }
    }

    private static long size(Object container) throws IOException {
        if (container instanceof ByteBuffer) {
            return ((ByteBuffer)container).limit();
        } else {
            return ((FileChannel)container).size();
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
