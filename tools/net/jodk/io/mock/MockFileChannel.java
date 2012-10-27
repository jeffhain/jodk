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
package net.jodk.io.mock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashSet;

import net.jodk.lang.InterfaceBooleanCondition;
import net.jodk.lang.LangUtils;
import net.jodk.lang.NumbersUtils;
import net.jodk.threading.locks.InterfaceCondilock;
import net.jodk.threading.locks.MonitorCondilock;

/**
 * Mock FileChannel, using a specified InterfaceMockBuffer implementation.
 * 
 * Behavior is undefined if multiple MockFileChannel share a same mock buffer.
 */
public class MockFileChannel extends FileChannel {

    /*
     * The order of arguments tests is inconsistent across methods,
     * but this is done to be as close to FilechannelImpl as possible
     * (but no code should rely on that order, since it's not in spec,
     * so we could still make order homogeneous, which would allow
     * to factor some code here).
     */
    
    /*
     * In InterfaceMockBuffer, could have bulk get/put methods using ByteBuffer
     * arguments, which would avoid some loops in this class, but that would
     * make InterfaceMockBuffer implementations heavier and more error-prone,
     * and since mocks are designed to be in-memory, looping doesn't entail the
     * huge cost of many IO operations.
     */
    
    /*
     * Reads and writes don't throw AsynchronousCloseException
     * or ClosedByInterruptException, which is acceptable since
     * they are not designed to be blocking.
     * State is undefined if target or source channel of transferXXX
     * methods throw these (or any other) exceptions.
     */
    
    /*
     * While checking against potentially conflicting locks,
     * we don't need to check that locks are still valid,
     * for released locks are removed from checked locks sets,
     * and lock(...) wait is stopped on channel closing.
     * (Also lock set is cleared on channel closing.)
     */
    
    /*
     * Channels in append mode have unintuitive behaviors,
     * due to the fact that their position is always their size.
     * For example, a readable channel can be in append mode,
     * but then channel.read(ByteBuffer) will never read anything,
     * since channel's number of remaining bytes is always zero.
     */
    
    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------
    
    /**
     * When using a temp ByteBuffer.
     * No need to be larger and more "optimal", since it's just a mock.
     * Could even use just 1.
     * Having it not too large also helps to speed up tests, when copying
     * more than a chunk with many configurations.
     */
    static final int TMP_CHUNK_SIZE = 1024;

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------
    
    private static class MyFileLock extends MockFileLock {
        public MyFileLock(
                FileChannel channel,
                long position,
                long size,
                boolean shared) {
            super(
                    channel,
                    position,
                    size,
                    shared);
        }
        @Override
        public void release() throws IOException {
            try {
                super.release();
            } finally {
                final MockFileChannel channel = (MockFileChannel)this.channel();
                synchronized (channel.mainMutex) {
                    channel.locks.remove(this);
                    // For threads waiting in a channel's lock method,
                    // for which these locks are alien locks.
                    channel.mainCondilock.signalAll();
                }
            }
        }
    }
    
    private class MyLockWaitingBC implements InterfaceBooleanCondition {
        final long position;
        final long size;
        final boolean shared;
        public MyLockWaitingBC(
                long position,
                long size,
                boolean shared) {
            this.position = position;
            this.size = size;
            this.shared = shared;
        }
        @Override
        public String toString() {
            return "[position="+this.position
            +",size="+this.size
            +",shared="+this.shared+"]";
        }
        @Override
        public boolean isTrue() {
            // No need to test against waiters, since it is tested
            // before starting to wait.
            return (!isOpen()) || (!conflictsAlienLock(this.position, this.size, this.shared));
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------
    
    /**
     * Using limit to emulate truncation.
     */
    private final InterfaceMockBuffer buffer;
    
    /**
     * Only used if not in append mode.
     * 
     * Might be > buffer.limit().
     */
    private long position;
    
    private final boolean readable;
    private final boolean writable;
    
    /**
     * If true, data is not written from position but from end of file.
     */
    private final boolean appendMode;
    
    /**
     * Used for both locks set and condilock, and shared with alien channel,
     * else we could have deadlocks.
     * 
     * Main mutex is synchronized on in implCloseChannel()
     * method, which is itself called within synchronization
     * on FileChannel.closeLock.
     * As a result, to avoid deadlocks, FileChannel.close(),
     * or any treatment that might close it (like regular
     * FileChannel operations that get interrupted), must not
     * be called from within main mutex.
     */
    private final Object mainMutex;
    
    /**
     * Condilock locking on main mutex.
     */
    private final InterfaceCondilock mainCondilock;
    
    /**
     * Guarded by main mutex.
     * 
     * Boolean conditions for threads waiting in lock(...) method.
     */
    private final HashSet<MyLockWaitingBC> lockWaitings = new HashSet<MyLockWaitingBC>();
    
    /**
     * Guarded by main mutex.
     * 
     * Each MockFileChannel owns its own "imaginary" file,
     * so there is no possible interferences between MockFileChannel
     * instances, and we don't have to use a global static structure
     * to keep track of locks.
     */
    private final HashSet<FileLock> locks = new HashSet<FileLock>();
    
    /**
     * Channel containing alien locks, i.e. locks considered
     * from other applications.
     * 
     * Shares mainMutex with this instance.
     * 
     * Using this class for these locks allows not to bother
     * re-implementing specific lock methods for alien locks.
     */
    private final MockFileChannel alienChannel;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------
    
    /**
     * @param buffer Its limit must not be modified
     *        other than by the new instance.
     */
    public MockFileChannel(
            InterfaceMockBuffer buffer,
            boolean readable,
            boolean writable,
            boolean appendMode) {
        LangUtils.checkNonNull(buffer);
        this.buffer = buffer;
        this.readable = readable;
        this.writable = writable;
        this.appendMode = appendMode;
        this.mainMutex = new Object();
        this.mainCondilock = new MonitorCondilock(this.mainMutex);
        this.alienChannel = new MockFileChannel(
                appendMode,
                this); // alien's alien is this
        if (this.appendMode) {
            this.position = Long.MIN_VALUE;
        }
    }
    
    @Override
    public String toString() {
        return "[buffer="+this.buffer
        +",position="+(this.appendMode ? this.buffer.limit() : this.position)
        +",readable="+this.readable
        +",writable="+this.writable
        +",appendMode="+this.appendMode
        +"]";
    }
    
    /**
     * @return The backing mock buffer.
     */
    public InterfaceMockBuffer getBackingMockBuffer() {
        return this.buffer;
    }
    
    public boolean getReadable() {
        return this.readable;
    }

    public boolean getWritable() {
        return this.writable;
    }

    public boolean getAppendMode() {
        return this.appendMode;
    }

    /*
     * position/size
     */
    
    /**
     * If this channel is in append mode, returns size.
     * 
     * @throws ClosedChannelException if this channel is closed.
     */
    @Override
    public long position() throws IOException {
        this.checkOpen();
        return this.positionElseSize();
    }

    /**
     * Setting the position to a value that is greater than the file's current
     * size is legal but does not change the size of the file. A later attempt
     * to read bytes at such a position will immediately return an end-of-file
     * indication. A later attempt to write bytes at such a position will cause
     * the file to be grown to accommodate the new bytes; the values of any
     * bytes between the previous end-of-file and the newly-written bytes are
     * unspecified. 
     * 
     * If this channel is in append mode, doesn't set position,
     * which is always equal to size.
     * 
     * @throws ClosedChannelException if this channel is closed.
     * @throws IllegalArgumentException if the specified position is < 0.
     */
    @Override
    public FileChannel position(long newPosition) throws IOException {
        this.checkOpen();
        if (newPosition < 0) {
            throw new IllegalArgumentException("position ["+newPosition+"] must be >= 0");
        }
        this.setPositionIfExists(newPosition);
        return this;
    }

    /**
     * @throws ClosedChannelException if this channel is closed.
     */
    @Override
    public long size() throws IOException {
        this.checkOpen();
        return this.buffer.limit();
    }

    /**
     * @throws ClosedChannelException if this channel is closed.
     * @throws IllegalArgumentException if the specified size is < 0.
     * @throws NonWritableChannelException if this channel was not opened for writing.
     */
    @Override
    public FileChannel truncate(long size) throws IOException {
        this.checkOpen();
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        // TODO Could return this if >=,
        // but we want to be as close to
        // FileChannelImpl as possible.
        // This should also be traced with a JDK bug, see nio-dev mailing list, message
        // from Alan Bateman, 2012/10/02, subject "Re: Tr : FileChannel doc":
        // "I think we have to fix specific one as all checks should be done at the start, I'll create a bug for that.".
        // Note: there is also a bug in that the spec says that position is
        // always reworked if > specified size, but that early test prevents it.
        final long oldSize = this.buffer.limit();
        if (size > oldSize) {
            // No growth.
            return this;
        }
        if (!this.writable) {
            throw new NonWritableChannelException();
        }
        
        if (!this.appendMode) {
            if (this.position > size) {
                this.position = size;
            }
        }
        this.buffer.limit(size);
        return this;
    }
    
    /*
     * lock
     */

    /**
     * Behaves like tryLock(long,long,boolean) from a readable and writable
     * channel on the same file, open in another JVM.
     */
    public FileLock tryLockAlien(long position, long size, boolean shared) throws IOException {
        return this.alienChannel.tryLock(position, size, shared);
    }

    /**
     * Behaves like lock(long,long,boolean) from a readable and writable
     * channel on the same file, open in another JVM.
     */
    public FileLock lockAlien(long position, long size, boolean shared) throws IOException {
        return this.alienChannel.lock(position, size, shared);
    }

    /**
     * @throws ClosedChannelException if this channel is closed.
     * @throws NonReadableChannelException if shared is true and this channel was not opened for writing.
     * @throws NonWritableChannelException if shared is false and this channel was not opened for reading.
     * @throws IllegalArgumentException if the preconditions on the parameters do not hold.
     * @throws OverlappingFileLockException if, for this instance, a conflicting lock is already held,
     *         or already attempted to be held (by another thread blocked in a lock method).
     */
    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        this.checkChannelAndLockArgs(position, size, shared);
        synchronized (this.mainMutex) {
            if (this.conflictsLockOrWaiter(position, size, shared)) {
                throw new OverlappingFileLockException();
            } else {
                if (this.conflictsAlienLock(position, size, shared)) {
                    return null;
                } else {
                    return this.newLockInSet(position, size, shared);
                }
            }
        }
    }

    /**
     * Blocks while conflicting with alien locks.
     * 
     * @throws ClosedChannelException if this channel is closed.
     * @throws NonReadableChannelException if shared is true and this channel was not opened for writing.
     * @throws NonWritableChannelException if shared is false and this channel was not opened for reading.
     * @param IllegalArgumentException if the preconditions on the parameters do not hold.
     * @throws OverlappingFileLockException if, for this instance, a conflicting lock is already held,
     *         or already attempted to be held (by another thread blocked in a lock method).
     * @throws FileLockInterruptionException if the invoking thread is interrupted
     *         while blocked in this method (interruption status restored before throwing).
     * @throws AsynchronousCloseException if another thread closes this channel while
     *         the invoking thread is blocked in this method.
     */
    @Override
    public FileLock lock(
            final long position,
            final long size,
            final boolean shared) throws IOException {
        this.checkChannelAndLockArgs(position, size, shared);
        synchronized (this.mainMutex) {
            if (this.conflictsLockOrWaiter(position, size, shared)) {
                throw new OverlappingFileLockException();
            }
            final MyLockWaitingBC freeToLockOrClosedBC = new MyLockWaitingBC(position,size,shared);
            this.lockWaitings.add(freeToLockOrClosedBC);
            try {
                try {
                    this.mainCondilock.awaitNanosWhileFalseInLock(freeToLockOrClosedBC, Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    // FileLockInterruptionException Javadoc says that
                    // we need to restore interruption status before throwing.
                    Thread.currentThread().interrupt();
                    throw new FileLockInterruptionException();
                }
                if (!this.isOpen()) {
                    // We consider that the wait stop is due to closing.
                    throw new AsynchronousCloseException();
                }
            } finally {
                this.lockWaitings.remove(freeToLockOrClosedBC);
            }
            LangUtils.azzert(!this.conflictsAlienLock(position, size, shared));
            LangUtils.azzert(!this.conflictsLockOrWaiter(position, size, shared));
            return this.newLockInSet(position, size, shared);
        }
    }
    
    /*
     * read
     */

    /**
     * Doesn't allow for concurrent reads (reads and updates position).
     * 
     * @throws ClosedChannelException if this channel is closed.
     * @throws NonReadableChannelException if this channel was not opened for reading.
     * @throws NullPointerException is the specified ByteBuffer is null.
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        this.checkOpen();
        if (!this.readable) {
            throw new NonReadableChannelException();
        }
        LangUtils.checkNonNull(dst);
        
        final long srcInitialPos = this.position();
        final long srcInitialLim = this.buffer.limit();
        final long srcRemaining = Math.max(0, srcInitialLim - srcInitialPos);
        
        if (srcRemaining == 0) {
            return -1;
        }
        
        final int toRead = minPos(srcRemaining, dst.remaining());
        if (toRead > 0) {
            this.read_internal(
                    dst,
                    srcInitialPos,
                    toRead);
            
            this.addToPositionIfExists(toRead);
        }
        return toRead;
    }
    
    /**
     * Allows for concurrent reads into different ByteBuffers.
     * 
     * @throws NullPointerException is the specified ByteBuffer is null.
     * @throws IllegalArgumentException if the specified position is < 0.
     * @throws NonReadableChannelException if this channel was not opened for reading.
     * @throws ClosedChannelException if this channel is closed.
     */
    @Override
    public int read(ByteBuffer dst, long srcPos) throws IOException {
        LangUtils.checkNonNull(dst);
        if (srcPos < 0) {
            throw new IllegalArgumentException();
        }
        if (!this.readable) {
            throw new NonReadableChannelException();
        }
        this.checkOpen();
        
        final long srcInitialLim = this.buffer.limit();
        final long srcRemainingFromPos = Math.max(0, srcInitialLim - srcPos);
        
        if (srcRemainingFromPos == 0) {
            return -1;
        }
        
        final int toRead = minPos(srcRemainingFromPos, dst.remaining());
        if (toRead > 0) {
            this.read_internal(
                    dst,
                    srcPos,
                    toRead);
        }
        return toRead;
    }

    /**
     * Doesn't allow for concurrent reads (reads and updates position).
     * 
     * @throws NullPointerException if the specified ByteBuffer array is null.
     * @throws IndexOutOfBoundsException if the preconditions on the offset and length
     *         parameters do not hold.
     * @throws ClosedChannelException if this channel is closed.
     * @throws NonReadableChannelException if this channel was not opened for reading.
     * @throws NullPointerException if a ByteBuffer references to actually use is null.
     */
    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        // Implicit null check.
        LangUtils.checkBounds(dsts.length, offset, length);
        this.checkOpen();
        if (!this.readable) {
            throw new NonReadableChannelException();
        }
        
        long sum = 0;
        final int bound = offset + length;
        for (int i=offset;i<bound;i++) {
            ByteBuffer dst = dsts[i];
            final int n = this.read(dst);
            if (n < 0) {
                if (i == offset) {
                    // eos on first read
                    return -1;
                }
                break;
            }
            sum += n;
        }
        return sum;
    }
    
    /*
     * write
     */
    
    /**
     * Doesn't allow for concurrent writes (reads and updates position).
     * 
     * @throws ClosedChannelException if this channel is closed.
     * @throws NonWritableChannelException if this channel was not opened for writing.
     * @throws NullPointerException is the specified ByteBuffer is null.
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        this.checkOpen();
        if (!this.writable) {
            throw new NonWritableChannelException();
        }
        LangUtils.checkNonNull(src);
        
        final int srcInitialPos = src.position();
        final int srcInitialLim = src.limit();
        final int srcRemaining = srcInitialLim - srcInitialPos;

        final int toWrite = srcRemaining;
        if (toWrite > 0) {
            final long dstPos = this.positionElseSize();
            
            this.write_internal(
                    src,
                    dstPos,
                    toWrite);
            
            this.addToPositionIfExists(toWrite);
        }
        return toWrite;
    }

    /**
     * Allows for concurrent non-overlaping writes from different ByteBuffers,
     * unless write cause growth, in which case channel's size is modified,
     * or this channel is in append mode, in which case growth might happen,
     * and this channel's position might be updated.
     * 
     * If this channel is in append mode, resulting channel's position
     * is max(previous position,position of last written byte).
     * 
     * @throws NullPointerException is the specified ByteBuffer is null.
     * @throws IllegalArgumentException if the specified position is < 0.
     * @throws NonWritableChannelException if this channel was not opened for writing.
     * @throws ClosedChannelException if this channel is closed.
     */
    @Override
    public int write(ByteBuffer src, long dstPos) throws IOException {
        LangUtils.checkNonNull(src);
        if (dstPos < 0) {
            throw new IllegalArgumentException();
        }
        if (!this.writable) {
            throw new NonWritableChannelException();
        }
        this.checkOpen();

        final int srcInitialPos = src.position();
        final int srcInitialLim = src.limit();
        final int srcRemaining = srcInitialLim - srcInitialPos;
        
        if (srcRemaining == 0) {
            return 0;
        }
        
        final int toWrite = srcRemaining;
        if (toWrite > 0) {
            this.write_internal(
                    src,
                    dstPos,
                    toWrite);
        }
        return toWrite;
    }

    /**
     * Doesn't allow for concurrent writes (reads and updates position).
     * 
     * @throws NullPointerException if the specified ByteBuffer array is null.
     * @throws IndexOutOfBoundsException if the preconditions on the offset and length
     *         parameters do not hold.
     * @throws ClosedChannelException if this channel is closed.
     * @throws NonWritableChannelException if this channel was not opened for writing.
     * @throws NullPointerException if a ByteBuffer references to actually use is null.
     */
    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        // Implicit null check.
        LangUtils.checkBounds(srcs.length, offset, length);
        this.checkOpen();
        if (!this.writable) {
            throw new NonWritableChannelException();
        }
        
        long sum = 0;
        final int bound = offset + length;
        for (int i=offset;i<bound;i++) {
            ByteBuffer src = srcs[i];
            sum += this.write(src);
        }
        return sum;
    }

    /*
     * transfer
     */
    
    /**
     * Allows for concurrent transfers into different WritableByteChannels.
     * 
     * The target channel will grow as needed, even if it is this channel.
     * 
     * @throwd ClosedChannelException if this channel is closed.
     * @throws NullPointerException is the target channel is null.
     * @throwd ClosedChannelException if the target channel is closed.
     * @throws NonReadableChannelException if this channel was not opened for reading.
     * @throws NonWritableChannelException if the target channel was not opened for writing
     *         and is a MockFileChannel (else can't check).
     * @throws IllegalArgumentException if the preconditions on the parameters do not hold.
     */
    @Override
    public long transferTo(long srcPos, long count, WritableByteChannel dst) throws IOException {
        this.checkOpen();
        // Implicit null check.
        if (!dst.isOpen()) {
            throw new ClosedChannelException();
        }
        if (!this.readable) {
            throw new NonReadableChannelException();
        }
        if (dst instanceof MockFileChannel) {
            if (!((MockFileChannel)dst).writable) {
                throw new NonWritableChannelException();
            }
        }
        if ((srcPos < 0) || (count < 0)) {
            throw new IllegalArgumentException();
        }
        
        final long srcInitialLim = this.buffer.limit();
        final long srcRemainingFromPos = Math.max(0, srcInitialLim - srcPos);
        
        if (srcRemainingFromPos == 0) {
            return 0;
        }

        // Might transfer less if dst gets filled.
        final long toTransferSrcCount = minPos(srcRemainingFromPos,count);
        if (toTransferSrcCount == 0) {
            return 0;
        }
        
        /*
         * If dst is this, and transfer moves data up,
         * we take care not to erase data to copy with copied data,
         * by copying in reverse order.
         * 
         * Also copying from first to last if relevant,
         * which allows not to create garbage when
         * transfering to a MockFileChannel.
         */
        
        if (dst == this) {
            final MockFileChannel dstImpl = (MockFileChannel)dst;
            final long dstPos;
            if (dstImpl.appendMode) {
                dstPos = dstImpl.position();
                final long neededLimit = NumbersUtils.plusExact(dstPos, toTransferSrcCount);
                dstImpl.buffer.limit(neededLimit);
            } else {
                dstPos = dstImpl.position;
            }
            final long dstRem = Math.max(0, dstImpl.buffer.limit() - dstPos);
            if (dstRem == 0) {
                return 0;
            }
            final long toTransfer = minPos(toTransferSrcCount, dstRem);
            final long srcToDstPosShift = dstPos - srcPos;
            if (srcToDstPosShift > 0) {
                /*
                 * copying from last to first
                 */
                for (long i=toTransfer;--i>=0;) {
                    final byte b = this.buffer.get(srcPos + i);
                    dstImpl.buffer.put(dstPos + i, b);
                }
            } else {
                /*
                 * copying from first to last
                 */
                for (long i=0;i<toTransfer;i++) {
                    final byte b = this.buffer.get(srcPos + i);
                    dstImpl.buffer.put(dstPos + i, b);
                }
            }
            if (!dstImpl.getAppendMode()) {
                dstImpl.position += toTransfer;
            }
            return toTransfer;
        }
        
        /*
         * copying from first to last
         */
        
        final int tmpCapacity = minPos(toTransferSrcCount, TMP_CHUNK_SIZE);
        // temp garbage
        final ByteBuffer tmpBB = ByteBuffer.allocate(tmpCapacity);

        long tmpRemaining = toTransferSrcCount;

        long tmpSrcPos = srcPos;
        while (tmpRemaining > 0) {
            tmpBB.clear();
            if (tmpRemaining < tmpCapacity) {
                tmpBB.limit(NumbersUtils.asInt(tmpRemaining));
            }
            final int tmpRead = this.read(tmpBB,tmpSrcPos);
            if (tmpRead <= 0) {
                // To avoid infinite loop if src depletes
                // early for some reason, and we can never
                // read the desired number of bytes.
                break;
            }
            tmpBB.flip();
            // Might be < tmpRead.
            final int tmpWritten = dst.write(tmpBB);
            tmpRemaining -= tmpWritten;
            if (tmpWritten < tmpRead) {
                // Breaking here to avoid infinite loop,
                // if dst channel gets filled.
                // More read than written, but it's ok
                // since src position has not been updated
                // by the read.
                break;
            }
            tmpSrcPos += tmpWritten;
        }
        final long transfered = toTransferSrcCount - tmpRemaining;
        return transfered;
    }
    
    /**
     * Allows for concurrent non-overlaping transfers from different ReadableByteChannel.
     * 
     * The target channel (this one) will grow as needed, unless the specified
     * position is strictly superior to its current size, in which case no
     * byte is transfered.
     * 
     * @throws ClosedChannelException if this channel is closed.
     * @throws NullPointerException if the source channel is null.
     * @throwd ClosedChannelException if the source channel is closed.
     * @throws NonWritableChannelException if this channel was not opened for writing.
     * @throws IllegalArgumentException if the preconditions on the parameters do not hold.
     * @throws NonReadableChannelException if the source channel was not opened for reading
     *         and is a MockFileChannel (else can't check).
     */
    @Override
    public long transferFrom(ReadableByteChannel src, long dstPos, long count) throws IOException {
        this.checkOpen();
        // Implicit null check.
        if (!src.isOpen()) {
            throw new ClosedChannelException();
        }
        if (!this.writable) {
            throw new NonWritableChannelException();
        }
        if ((dstPos < 0) || (count < 0)) {
            throw new IllegalArgumentException();
        }
        if (src instanceof MockFileChannel) {
            if (!((MockFileChannel)src).readable) {
                throw new NonReadableChannelException();
            }
        }
        
        if (count == 0) {
            return 0;
        }

        final long dstInitialLim = this.buffer.limit();
        if (dstInitialLim < dstPos) {
            // Not growing to allow transfer (behavior tested with
            // src = RandomAccessFile.getChannel(), and
            // dst = FileOutputStream.getChannel()).
            return 0;
        }
        
        /*
         * If src is this, and transfer moves data down,
         * we take care not to erase data to copy with copied data,
         * by copying in reverse order.
         * 
         * Also copying from first to last if relevant,
         * which allows not to create garbage when
         * transfering from a MockFileChannel.
         */
        
        if (src == this) {
            final MockFileChannel srcImpl = (MockFileChannel)src;
            // If src is in append mode, won't read anything from it.
            final long srcPos = srcImpl.position();
            final long srcToDstPosShift = dstPos - srcPos;
            final long srcRem = Math.max(0, srcImpl.buffer.limit() - srcPos);
            if (srcRem == 0) {
                return 0;
            }
            final long toTransfer = minPos(srcRem, count);
            final long neededDstLim = NumbersUtils.plusExact(dstPos, toTransfer);
            if (neededDstLim > dstInitialLim) {
                this.buffer.limit(neededDstLim);
            }
            if (srcToDstPosShift > 0) {
                /*
                 * copying from last to first
                 */
                for (long i=toTransfer;--i>=0;) {
                    final byte b = srcImpl.buffer.get(srcPos + i);
                    this.buffer.put(dstPos + i, b);
                }
            } else {
                /*
                 * copying from first to last
                 */
                for (long i=0;i<toTransfer;i++) {
                    final byte b = srcImpl.buffer.get(srcPos + i);
                    this.buffer.put(dstPos + i, b);
                }
            }
            if (!srcImpl.appendMode) {
                srcImpl.position += toTransfer;
            }
            return toTransfer;
        }
        
        /*
         * copying from first to last
         */

        // Might transfer less if src depletes.
        long tmpRemaining = count;
        long tmpDstPos = dstPos;
        final int tmpCapacity = minPos(tmpRemaining, TMP_CHUNK_SIZE);
        // temp garbage
        final ByteBuffer tmpBB = ByteBuffer.allocate(tmpCapacity);
        while (tmpRemaining > 0) {
            tmpBB.clear();
            if (tmpRemaining < tmpCapacity) {
                tmpBB.limit(NumbersUtils.asInt(tmpRemaining));
            }
            final int tmpRead = src.read(tmpBB);
            if (tmpRead <= 0) {
                // To avoid infinite loop if src depletes
                // early for some reason, and we can never
                // read the desired number of bytes.
                break;
            }
            tmpBB.flip();
            // Might be < tmpRead.
            final int tmpWritten = this.write(tmpBB,tmpDstPos);
            tmpRemaining -= tmpWritten;
            if (tmpWritten < tmpRead) {
                if (false) {
                    // Breaking here to avoid infinite loop,
                    // if dst channel gets filled.
                    // More read than written, in which case
                    // non-written bytes are "lost" (for this
                    // treatment at least).
                    break;
                } else {
                    // Some read bytes were lost: not a good state.
                    throw new AssertionError();
                }
            }
            tmpDstPos += tmpWritten;
        }
        final long transfered = count - tmpRemaining;
        return transfered;
    }

    /*
     * 
     */

    /**
     * Does nothing functional.
     * 
     * @throws ClosedChannelException if this channel is closed.
     */
    @Override
    public void force(boolean metaData) throws IOException {
        this.checkOpen();
        // Memory intrinsically coherent with itself: no need to force.
    }

    /**
     * @throws UnsupportedOperationException always.
     */
    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) {
        // Can't (easily/portably) mock that.
        throw new UnsupportedOperationException();
    }

    //--------------------------------------------------------------------------
    // PROTECTED METHODS
    //--------------------------------------------------------------------------
    
    @Override
    protected void implCloseChannel() {
        synchronized (this.mainMutex) {
            // Once channel is closed, all (non-alien) locks are invalid, so it
            // doesn't hurt to delete these references, which are only useful to
            // detect conflicts with non-released locks.            
            // Also, it allows for GC of non-released but now invalid locks.
            this.locks.clear();
            // To wake-up threads blocked in a lock method.
            this.mainCondilock.signalAll();
        }
    }
    
    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Constructor for alien channel, used for alien locks:
     * - shares main mutex and main condilock,
     * - has no buffer,
     * - is readable and writable (to allow for
     *   shared or exclusive alien locks),
     * - has same append mode, but shouldn't be used anyway,
     * - is always open (always possible to create alien locks).
     */
    private MockFileChannel(
            boolean appendMode,
            MockFileChannel alienChannel) {
        this.buffer = null;
        this.readable = true;
        this.writable = true;
        this.appendMode = appendMode;
        this.alienChannel = alienChannel;
        this.mainMutex = alienChannel.mainMutex;
        this.mainCondilock = alienChannel.mainCondilock;
    }
    
    /*
     * min without having to bother with int/long ranges,
     * working if result is supposed >= 0.
     */
    
    private static long minPos(long a, long b) {
        return Math.min(a, b);
    }

    /**
     * @return a if < b, else max(0,b).
     */
    private static int minPos(int a, long b) {
        return (a < b) ? a : (int)Math.max(0,b);
    }

    /**
     * @return b if < a, else max(0,a).
     */
    private static int minPos(long a, int b) {
        return minPos(b,a);
    }
    
    /*
     * 
     */

    private long positionElseSize() {
        if (this.appendMode) {
            // no position
            return this.buffer.limit();
        } else {
            return this.position;
        }
    }
    
    private void setPositionIfExists(long position) {
        if (this.appendMode) {
            // no position
        } else {
            this.position = position;
        }
    }
    
    private void addToPositionIfExists(long toAdd) {
        if (this.appendMode) {
            // no position
        } else {
            this.position += toAdd;
        }
    }
    
    /*
     * 
     */
    
    private void read_internal(
            ByteBuffer dst,
            long srcPos,
            int toRead) {
        for (int i=0;i<toRead;i++) {
            dst.put(this.buffer.get(srcPos + i));
        }
    }
    
    private void write_internal(
            ByteBuffer src,
            long dstPos,
            int toWrite) {
        // Growing as needed (which throws if too large for buffer implementation).
        final long neededLimit = NumbersUtils.plusExact(dstPos, toWrite);
        if (this.buffer.limit() < neededLimit) {
            this.buffer.limit(neededLimit);
        }
        
        for (int i=0;i<toWrite;i++) {
            this.buffer.put(dstPos + i, src.get());
        }
    }
    
    /*
     * 
     */
    
    private boolean checkOpen() throws ClosedChannelException {
        if (!this.isOpen()) {
            throw new ClosedChannelException();
        }
        return true;
    }
    
    /**
     * Can factor this because check order is
     * the same for all lock/tryLock methods.
     */
    private void checkChannelAndLockArgs(long position, long size, boolean shared) throws IOException {
        this.checkOpen();
        if (shared && (!this.readable)) {
            throw new NonReadableChannelException();
        }
        if ((!shared) && (!this.writable)) {
            throw new NonWritableChannelException();
        }
        try {
            LangUtils.checkBounds(Long.MAX_VALUE, position, size);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static boolean overlap(
            long position1,
            long size1,
            long position2,
            long size2) {
        if (position1 + size1 <= position2) {
            // 1 is below 2
            return false;
        }
        if (position2 + size2 <= position1) {
            // 2 is below 1
            return false;
        }
        return true;
    }
    
    private static boolean conflict(
            long position1,
            long size1,
            boolean shared1,
            long position2,
            long size2,
            boolean shared2) {
        if (shared1 && shared2) {
            // Both shared: can overlap.
        } else {
            if (overlap(position1, size1, position2, size2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean conflict(
            FileLock lock,
            long position,
            long size,
            boolean shared) {
        return lock.isValid() && conflict(
                lock.position(),
                lock.size(),
                lock.isShared(),
                position,
                size,
                shared);
    }
    
    /**
     * @return True if conflicts with an alien lock, false otherwise.
     */
    private boolean conflictsAlienLock(long position, long size, boolean shared) {
        synchronized (this.mainMutex) {
            for (FileLock lock : this.alienChannel.locks) {
                if (conflict(lock, position, size, shared)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return True if conflicts with a lock, or with a thread already
     *         waiting (or elected to wait) for a conflicting lock, false otherwise.
     */
    private boolean conflictsLockOrWaiter(long position, long size, boolean shared) {
        synchronized (this.mainMutex) {
            for (MyLockWaitingBC lockWait : this.lockWaitings) {
                if (conflict(
                        lockWait.position,
                        lockWait.size,
                        lockWait.shared,
                        position,
                        size,
                        shared)) {
                    return true;
                }
            }
            for (FileLock lock : this.locks) {
                if (conflict(lock, position, size, shared)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Must be called in main mutex.
     * 
     * @return A new lock, added in the set.
     */
    private FileLock newLockInSet(long position, long size, boolean shared) {
        LangUtils.azzert(Thread.holdsLock(this.mainMutex));
        final FileLock lock = new MyFileLock(this, position, size, shared);
        this.locks.add(lock);
        return lock;
    }
}
