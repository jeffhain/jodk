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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;

import net.jodk.test.TestUtils;

/**
 * Benches for some methods not benched in BufferOpPerf.
 */
public class DataBufferPerf {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final int BUFFER_CAPACITY = 1000;

    private static final int NBR_OF_CALLS = 100 * 1000;

    //--------------------------------------------------------------------------
    // PRIVATE CLASSES
    //--------------------------------------------------------------------------

    private static abstract class MyAbstractBufferHolder {
        final String description;
        public MyAbstractBufferHolder(final String description) {
            this.description = description;
        }
        public abstract Object getBuffer();
        public abstract int call_hashCode();
        public abstract boolean call_equals(Object other);
        public abstract int call_compareTo(Object other);
        public abstract void call_put(byte[] src);
        public abstract void call_get(byte[] dest);
        public abstract void call_put_bitwise(byte[] src);
        public abstract void call_get_bitwise(byte[] dest);
    }
    
    private static class MyByteBufferHolder extends MyAbstractBufferHolder {
        private final ByteBuffer buffer;
        public MyByteBufferHolder(
                ByteBuffer buffer,
                String description) {
            super(description);
            this.buffer = buffer;
        }
        @Override
        public Object getBuffer() {
            return this.buffer;
        }
        @Override
        public int call_hashCode() {
            return this.buffer.hashCode();
        }
        @Override
        public boolean call_equals(Object other) {
            return this.buffer.equals(other);
        }
        @Override
        public int call_compareTo(Object other) {
            return this.buffer.compareTo((ByteBuffer)other);
        }
        @Override
        public void call_put(byte[] src) {
            this.buffer.position(0);
            this.buffer.put(src);
        }
        @Override
        public void call_get(byte[] dest) {
            this.buffer.position(0);
            this.buffer.get(dest);
        }
        @Override
        public void call_put_bitwise(byte[] src) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void call_get_bitwise(byte[] dest) {
            throw new UnsupportedOperationException();
        }
    }
    
    private static class MyDataBufferHolder extends MyAbstractBufferHolder {
        private final DataBuffer buffer;
        public MyDataBufferHolder(
                DataBuffer buffer,
                String description) {
            super(description);
            this.buffer = buffer;
        }
        @Override
        public Object getBuffer() {
            return this.buffer;
        }
        @Override
        public int call_hashCode() {
            return this.buffer.hashCode();
        }
        @Override
        public boolean call_equals(Object other) {
            return this.buffer.equals(other);
        }
        @Override
        public int call_compareTo(Object other) {
            return this.buffer.compareTo((DataBuffer)other);
        }
        @Override
        public void call_put(byte[] src) {
            this.buffer.bitPosition(0L);
            this.buffer.put(src);
        }
        @Override
        public void call_get(byte[] dest) {
            this.buffer.bitPosition(0L);
            this.buffer.get(dest);
        }
        @Override
        public void call_put_bitwise(byte[] src) {
            this.buffer.bitPosition(1L);
            this.buffer.put(src);
        }
        @Override
        public void call_get_bitwise(byte[] dest) {
            this.buffer.bitPosition(1L);
            this.buffer.get(dest);
        }
    }
    
    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private long timerRef;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println(TestUtils.getJVMInfo());
        newRun(args);
    }

    public static void newRun(String[] args) {
        new DataBufferPerf().run(args);
    }

    public DataBufferPerf() {
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private void run(String[] args) {
        // XXX
        System.out.println("--- "+DataBufferPerf.class.getSimpleName()+"... ---");
        System.out.println("number of calls = "+NBR_OF_CALLS);
        System.out.println("buffer capacity = "+BUFFER_CAPACITY);

        bench_hashCode();
        
        bench_equals_Object();
        
        bench_compareTo_DataBuffer();
        
        bench_put_byteArray();
        
        bench_get_byteArray();
        
        bench_put_byteArray_bitwise();
        
        bench_get_byteArray_bitwise();

        System.out.println("--- ..."+DataBufferPerf.class.getSimpleName()+" ---");
    }

    /*
     * 
     */

    private void startTimer() {
        timerRef = System.nanoTime();
    }

    private double getElapsedSeconds() {
        return TestUtils.nsToSRounded(System.nanoTime() - timerRef);
    }

    /*
     * 
     */

    private void bench_hashCode() {
        System.out.println("");
        int dummy = 0;
        for (MyAbstractBufferHolder dbh : variousBH()) {
            startTimer();
            for (int i=0;i<NBR_OF_CALLS;i++) {
                dummy += dbh.call_hashCode();
            }
            System.out.println("Loop on "+dbh.description+".hashCode() took "+getElapsedSeconds()+" s");
        }
        if (dummy == Integer.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_equals_Object() {
        System.out.println("");
        int dummy = 0;
        // Only using buffers of a same kind, else that makes a lot of cases.
        MyAbstractBufferHolder[] bhs1 = variousBH();
        MyAbstractBufferHolder[] bhs2 = variousBH();
        for (int k=0;k<bhs1.length;k++) {
            for (int h=0;h<2;h++) {
                final int k1 = k;
                final int k2 = k-h;
                final String orders = ((k1 == k2) ? "same" : "diff");
                
                MyAbstractBufferHolder bh1 = bhs1[k1];
                MyAbstractBufferHolder bh2 = bhs2[k2];
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    dummy += bh1.call_equals(bh2.getBuffer()) ? 1 : 0;
                }
                System.out.println("Loop on "+bh1.description+".equals(Object) ("+orders+" order) took "+getElapsedSeconds()+" s");
                
                final boolean kOdd = ((k&1) != 0);
                if (!kOdd) {
                    break;
                }
            }
        }
        if (dummy == Integer.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_compareTo_DataBuffer() {
        System.out.println("");
        int dummy = 0;
        // Only using buffers of a same kind, else that makes a lot of cases.
        MyAbstractBufferHolder[] bhs1 = variousBH();
        MyAbstractBufferHolder[] bhs2 = variousBH();
        for (int k=0;k<bhs1.length;k++) {
            for (int h=0;h<2;h++) {
                final int k1 = k;
                final int k2 = k-h;
                final String orders = ((k1 == k2) ? "same" : "diff");
                
                MyAbstractBufferHolder bh1 = bhs1[k1];
                MyAbstractBufferHolder bh2 = bhs2[k2];
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    dummy += bh1.call_compareTo(bh2.getBuffer());
                }
                System.out.println("Loop on "+bh1.description+".compareTo(buffer) ("+orders+" order) took "+getElapsedSeconds()+" s");
                
                final boolean kOdd = ((k&1) != 0);
                if (!kOdd) {
                    break;
                }
            }
        }
        if (dummy == Integer.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_put_byteArray() {
        System.out.println("");
        int dummy = 0;
        final int limit = BUFFER_CAPACITY;
        final byte[] src = new byte[limit];
        for (MyAbstractBufferHolder bh : variousBH(limit)) {
            try {
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    bh.call_put(src);
                }
                System.out.println("Loop on "+bh.description+".put(byte[]) took "+getElapsedSeconds()+" s");
                dummy += bh.call_hashCode();
            } catch (ReadOnlyBufferException e) {
                // quiet
            }
        }
        if (dummy == Integer.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    private void bench_get_byteArray() {
        System.out.println("");
        int dummy = 0;
        final int limit = BUFFER_CAPACITY;
        final byte[] dest = new byte[limit];
        for (MyAbstractBufferHolder bh : variousBH(limit)) {
            try {
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    bh.call_get(dest);
                }
                System.out.println("Loop on "+bh.description+".get(byte[]) took "+getElapsedSeconds()+" s");
                dummy += bh.call_hashCode();
            } catch (ReadOnlyBufferException e) {
                // quiet
            }
        }
        if (dummy == Integer.MIN_VALUE) {
            System.out.println("rare");
        }
    }
    
    private void bench_put_byteArray_bitwise() {
        System.out.println("");
        int dummy = 0;
        final int limit = BUFFER_CAPACITY;
        final byte[] src = new byte[limit];
        // +1 to have room for bitwise operations
        for (MyAbstractBufferHolder bh : variousBH(limit+1)) {
            try {
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    bh.call_put_bitwise(src);
                }
                System.out.println("Loop on "+bh.description+".put(byte[]) (bitwise) took "+getElapsedSeconds()+" s");
                dummy += bh.call_hashCode();
            } catch (ReadOnlyBufferException e) {
                // quiet
            } catch (UnsupportedOperationException e) {
                // quiet
            }
        }
        if (dummy == Integer.MIN_VALUE) {
            System.out.println("rare");
        }
    }
    
    private void bench_get_byteArray_bitwise() {
        System.out.println("");
        int dummy = 0;
        final int limit = BUFFER_CAPACITY;
        final byte[] dest = new byte[limit];
        // +1 to have room for bitwise operations
        for (MyAbstractBufferHolder bh : variousBH(limit+1)) {
            try {
                startTimer();
                for (int i=0;i<NBR_OF_CALLS;i++) {
                    bh.call_get_bitwise(dest);
                }
                System.out.println("Loop on "+bh.description+".get(byte[]) (bitwise) took "+getElapsedSeconds()+" s");
                dummy += bh.call_hashCode();
            } catch (ReadOnlyBufferException e) {
                // quiet
            } catch (UnsupportedOperationException e) {
                // quiet
            }
        }
        if (dummy == Integer.MIN_VALUE) {
            System.out.println("rare");
        }
    }

    /*
     * 
     */
    
    private MyAbstractBufferHolder[] variousBH() {
        return variousBH(BUFFER_CAPACITY);
    }

    /**
     * Benches suppose odd indexes correspond to buffer
     * of same kinds but opposite order than buffer of
     * previous even index.
     */
    private MyAbstractBufferHolder[] variousBH(int capacity) {
        ArrayList<MyAbstractBufferHolder> dbs = new ArrayList<MyAbstractBufferHolder>();
        
        /*
         * Contents are identical for equals and compareTo to scan all of it.
         */
        
        /*
         * ByteBuffers
         */

        dbs.add(new MyByteBufferHolder(
                ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN),
                "[bb,      ,        ,BIG_ENDIAN]   "));
        dbs.add(new MyByteBufferHolder(
                ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN),
                "[bb,      ,        ,LITTLE_ENDIAN]"));
        dbs.add(new MyByteBufferHolder(
                ByteBuffer.allocate(capacity).asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN),
                "[bb,      ,readOnly,BIG_ENDIAN]   "));
        dbs.add(new MyByteBufferHolder(
                ByteBuffer.allocate(capacity).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN),
                "[bb,      ,readOnly,LITTLE_ENDIAN]"));

        dbs.add(new MyByteBufferHolder(
                ByteBuffer.allocateDirect(capacity).order(ByteOrder.BIG_ENDIAN),
                "[bb,direct,        ,BIG_ENDIAN]   "));
        dbs.add(new MyByteBufferHolder(
                ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN),
                "[bb,direct,        ,LITTLE_ENDIAN]"));
        dbs.add(new MyByteBufferHolder(
                ByteBuffer.allocateDirect(capacity).asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN),
                "[bb,direct,readOnly,BIG_ENDIAN]   "));
        dbs.add(new MyByteBufferHolder(
                ByteBuffer.allocateDirect(capacity).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN),
                "[bb,direct,readOnly,LITTLE_ENDIAN]"));

        /*
         * DataBuffers
         */
        
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBA(capacity).order(ByteOrder.BIG_ENDIAN),
                "[db-ba,      ,        ,BIG_ENDIAN]   "));
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBA(capacity).order(ByteOrder.LITTLE_ENDIAN),
                "[db-ba,      ,        ,LITTLE_ENDIAN]"));
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBA(capacity).asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN),
                "[db-ba,      ,readOnly,BIG_ENDIAN]   "));
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBA(capacity).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN),
                "[db-ba,      ,readOnly,LITTLE_ENDIAN]"));
        
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBB(capacity).order(ByteOrder.BIG_ENDIAN),
                "[db-bb,      ,        ,BIG_ENDIAN]   "));
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBB(capacity).order(ByteOrder.LITTLE_ENDIAN),
                "[db-bb,      ,        ,LITTLE_ENDIAN]"));
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBB(capacity).asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN),
                "[db-bb,      ,readOnly,BIG_ENDIAN]   "));
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBB(capacity).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN),
                "[db-bb,      ,readOnly,LITTLE_ENDIAN]"));

        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBBDirect(capacity).order(ByteOrder.BIG_ENDIAN),
                "[db-bb,direct,        ,BIG_ENDIAN]   "));
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBBDirect(capacity).order(ByteOrder.LITTLE_ENDIAN),
                "[db-bb,direct,        ,LITTLE_ENDIAN]"));
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBBDirect(capacity).asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN),
                "[db-bb,direct,readOnly,BIG_ENDIAN]   "));
        dbs.add(new MyDataBufferHolder(
                DataBuffer.allocateBBDirect(capacity).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN),
                "[db-bb,direct,readOnly,LITTLE_ENDIAN]"));

        return dbs.toArray(new MyAbstractBufferHolder[dbs.size()]);
    }
}
