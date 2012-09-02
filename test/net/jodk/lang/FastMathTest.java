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
package net.jodk.lang;

import java.util.Random;

import net.jodk.lang.DoubleWrapper;
import net.jodk.lang.FastMath;
import net.jodk.lang.IntWrapper;


import junit.framework.TestCase;

public strictfp class FastMathTest extends TestCase {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final int NBR_OF_VALUES = 1000 * 1000;

    private final Random random = new Random(123456789L);

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final double DEFAULT_EPSILON = 1e-10;
    private static final double HUGE_EPSILON = 1e-7;

    private static final double A_LOT = 1e10;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public void test_cos_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.cos(value), FastMath.cos(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.cos(Double.NaN));
    }

    public void test_cosQuick_double() {
        final double bound = Integer.MAX_VALUE * (2*Math.PI/(1<<11));
        final double epsilon = 1.6e-3;
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleUniform(-bound,bound);
            double delta = minDelta(Math.cos(value), FastMath.cosQuick(value));
            assertTrue(delta < epsilon);
        }
    }

    public void test_sin_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.sin(value), FastMath.sin(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.sin(Double.NaN));
    }

    public void test_sinQuick_double() {
        final double bound = Integer.MAX_VALUE * (2*Math.PI/(1<<11));
        final double epsilon = 1.6e-3;
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleUniform(-bound,bound);
            double delta = minDelta(Math.sin(value), FastMath.sinQuick(value));
            assertTrue(delta < epsilon);
        }
    }

    public void test_sinAndCos_double_DoubleWrapper_DoubleWrapper() {
        DoubleWrapper tmpSin = new DoubleWrapper();
        DoubleWrapper tmpCos = new DoubleWrapper();
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            FastMath.sinAndCos(value, tmpSin, tmpCos);
            assertTrue(minDelta(Math.sin(value), tmpSin.value) < DEFAULT_EPSILON);
            assertTrue(minDelta(Math.cos(value), tmpCos.value) < DEFAULT_EPSILON);
        }
        FastMath.sinAndCos(Double.NaN, tmpSin, tmpCos);
        assertEquals(Double.NaN, tmpSin.value);
        assertEquals(Double.NaN, tmpCos.value);
    }

    public void test_tan_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.tan(value), FastMath.tan(value)) < 9e-9);
        }
        assertEquals(Double.NaN, FastMath.tan(Double.NaN));
    }

    public void test_acos_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleUniformMinusOneOne();
            assertTrue(minDelta(Math.acos(value), FastMath.acos(value)) < DEFAULT_EPSILON);
        }
        assertEquals(0.0, FastMath.acos(1.0));
        assertEquals(Math.PI, FastMath.acos(-1.0));
        assertEquals(Double.NaN, FastMath.acos(-1.1));
        assertEquals(Double.NaN, FastMath.acos(1.1));
        assertEquals(Double.NaN, FastMath.acos(Double.NaN));
    }

    public void test_acosInRange_double() {
        assertEquals(Math.PI, FastMath.acosInRange(-1.1));
        assertEquals(FastMath.acos(0.1), FastMath.acosInRange(0.1));
        assertEquals(0.0, FastMath.acosInRange(1.1));
        assertEquals(Double.NaN, FastMath.acosInRange(Double.NaN));
    }

    public void test_asin_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleUniformMinusOneOne();
            assertTrue(minDelta(Math.asin(value), FastMath.asin(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Math.PI/2, FastMath.asin(1.0));
        assertEquals(-Math.PI/2, FastMath.asin(-1.0));
        assertEquals(Double.NaN, FastMath.asin(-1.1));
        assertEquals(Double.NaN, FastMath.asin(1.1));
        assertEquals(Double.NaN, FastMath.asin(Double.NaN));
    }

    public void test_asinInRange_double() {
        assertEquals(-Math.PI/2, FastMath.asinInRange(-1.1));
        assertEquals(FastMath.asin(0.1), FastMath.asinInRange(0.1));
        assertEquals(Math.PI/2, FastMath.asinInRange(1.1));
        assertEquals(Double.NaN, FastMath.asinInRange(Double.NaN));
    }

    public void test_atan_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.atan(value), FastMath.atan(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Math.PI/4, FastMath.atan(1.0));
        assertEquals(-Math.PI/4, FastMath.atan(-1.0));
        assertEquals(Double.NaN,FastMath.atan(Double.NaN));
    }

    public void test_atan2_2double() {
        /* TODO JVM bug if too many loops (looks like an optimization problem):
#
# A fatal error has been detected by the Java Runtime Environment:
#
#  Internal Error (os_windows_x86.cpp:149), pid=4848, tid=4340
#  guarantee(result == EXCEPTION_CONTINUE_EXECUTION) failed: Unexpected result from topLevelExceptionFilter
#
# JRE version: 6.0_29-b08
# Java VM: Java HotSpot(TM) 64-Bit Server VM (20.4-b01 mixed mode windows-amd64 compressed oops)
# If you would like to submit a bug report, please visit:
#   http://java.sun.com/webapps/bugreport/crash.jsp
#
         */
        final boolean avoidBug = false;
        final int nbrOfValues = avoidBug ? 10 * 1000 : NBR_OF_VALUES;
        for (int i=0;i<nbrOfValues;i++) {
            double y = doubleAllMagnitudesNaNInf();
            double x = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.atan2(y,x), FastMath.atan2(y,x)) < DEFAULT_EPSILON);
        }
        double[] specialValuesTab = new double[] {
                Double.NEGATIVE_INFINITY,
                -2.0,
                -1.0,
                -0.0,
                0.0,
                1.0,
                2.0,
                Double.POSITIVE_INFINITY,
                Double.NaN
        };
        for (int i=0;i<specialValuesTab.length;i++) {
            double y = specialValuesTab[i];
            for (int j=0;j<specialValuesTab.length;j++) {
                double x = specialValuesTab[j];
                assertEquals(Math.atan2(y,x),FastMath.atan2(y,x));
            }
        }
    }

    public void test_cosh_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.cosh(value), FastMath.cosh(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN,FastMath.cosh(Double.NaN));
    }

    public void test_sinh_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.sinh(value), FastMath.sinh(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN,FastMath.sinh(Double.NaN));
    }

    public void test_sinhAndCosh_double_DoubleWrapper_DoubleWrapper() {
        DoubleWrapper tmpSinh = new DoubleWrapper();
        DoubleWrapper tmpCosh = new DoubleWrapper();
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            FastMath.sinhAndCosh(value, tmpSinh, tmpCosh);
            assertTrue(minDelta(Math.sinh(value), tmpSinh.value) < DEFAULT_EPSILON);
            assertTrue(minDelta(Math.cosh(value), tmpCosh.value) < DEFAULT_EPSILON);
        }
        FastMath.sinhAndCosh(Double.NaN, tmpSinh, tmpCosh);
        assertEquals(Double.NaN, tmpSinh.value);
        assertEquals(Double.NaN, tmpCosh.value);
    }

    public void test_tanh_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.tanh(value), FastMath.tanh(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN,FastMath.tanh(Double.NaN));
    }

    public void test_exp_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.exp(value), FastMath.exp(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.exp(Double.NaN));
    }

    public void test_expQuick_double() {
        final double epsilon = 3e-2;
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleUniform(-700.0, 700.0);
            double delta = minDelta(Math.exp(value), FastMath.expQuick(value));
            assertTrue(delta < epsilon);
        }
    }

    public void test_expm1_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.expm1(value), FastMath.expm1(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.expm1(Double.NaN));
    }

    public void test_log_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = Math.abs(doubleAllMagnitudesNaNInf());
            assertTrue(minDelta(Math.log(value), FastMath.log(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.log(Double.NaN));
    }

    public void test_logQuick_double() {
        final double epsilon = 2.8e-4;
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = Math.abs(doubleAllMagnitudes());
            double delta = minDelta(Math.log(value), FastMath.logQuick(value));
            assertTrue(delta < epsilon);
        }
    }

    public void test_log10_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = Math.abs(doubleAllMagnitudesNaNInf());
            assertTrue(minDelta(Math.log10(value), FastMath.log10(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.log10(Double.NaN));
    }

    public void test_log1p_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = Math.abs(doubleAllMagnitudesNaNInf())-1;
            assertTrue(minDelta(Math.log1p(value), FastMath.log1p(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.log1p(Double.NaN));
    }

    public void test_log2_int() {
        for (int value : new int[]{Integer.MIN_VALUE,0}) {
            try {
                FastMath.log2(value);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
        
        for (int p=0;p<=30;p++) {
            int pot = (1<<p);
            
            if (p != 0) {
                assertEquals(p-1, FastMath.log2(pot-1));
            }
            assertEquals(p, FastMath.log2(pot));
            assertEquals(p, FastMath.log2(pot+pot-1));
            if (p != 30) {
                assertEquals(p+1, FastMath.log2(pot+pot));
            }
        }
    }

    public void test_log2_long() {
        for (long value : new long[]{Long.MIN_VALUE,0}) {
            try {
                FastMath.log2(value);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }

        for (int p=0;p<=62;p++) {
            long pot = (1L<<p);
            
            if (p != 0) {
                assertEquals(p-1, FastMath.log2(pot-1));
            }
            assertEquals(p, FastMath.log2(pot));
            assertEquals(p, FastMath.log2(pot+pot-1));
            if (p != 62) {
                assertEquals(p+1, FastMath.log2(pot+pot));
            }
        }
    }
    
    public void test_pow_2double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double a = doubleAllMagnitudesNaNInf();
            double b = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.pow(a,b), FastMath.pow(a,b)) < DEFAULT_EPSILON);
        }
        // with mathematical integers as value
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double a = (double)(int)floatAllMagnitudesNaNInf();
            double b = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.pow(a,b), FastMath.pow(a,b)) < DEFAULT_EPSILON);
        }
        // with mathematical integers as power
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double a = doubleAllMagnitudesNaNInf();
            double b = (double)(int)floatAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.pow(a,b), FastMath.pow(a,b)) < DEFAULT_EPSILON);
        }
        assertEquals(1.0, FastMath.pow(0.0,0.0));
        assertEquals(0.0, FastMath.pow(0.0,2.0));
        assertEquals(0.0, FastMath.pow(-0.0,2.0));
        assertEquals(Double.POSITIVE_INFINITY, FastMath.pow(0.0,-2.0));
        assertEquals(0.0, FastMath.pow(0.0,3.0));
        assertEquals(-0.0, FastMath.pow(-0.0,3.0));
        assertEquals(Double.POSITIVE_INFINITY, FastMath.pow(0.0,-3.0));
        assertEquals(4.0, FastMath.pow(2.0,2.0), DEFAULT_EPSILON);
        assertEquals(8.0, FastMath.pow(2.0,3.0), DEFAULT_EPSILON);
        assertEquals(1.0/4.0, FastMath.pow(2.0,-2.0), DEFAULT_EPSILON);
        assertEquals(1.0/8.0, FastMath.pow(2.0,-3.0), DEFAULT_EPSILON);
        assertEquals(Double.POSITIVE_INFINITY, FastMath.pow(Double.NEGATIVE_INFINITY,2.0));
        assertEquals(Double.NEGATIVE_INFINITY, FastMath.pow(Double.NEGATIVE_INFINITY,3.0));
        assertEquals(0.0, FastMath.pow(Double.NEGATIVE_INFINITY,-2.0));
        assertEquals(-0.0, FastMath.pow(Double.NEGATIVE_INFINITY,-3.0));
        assertEquals(Double.POSITIVE_INFINITY, FastMath.pow(-2.0,(1L<<40))); // even power
        assertEquals(Double.NEGATIVE_INFINITY, FastMath.pow(-2.0,(1L<<40)+1)); // odd power
        assertEquals(Double.NaN, FastMath.pow(Double.NaN,1.0));
        assertEquals(Double.NaN, FastMath.pow(1.0,Double.NaN));
        assertEquals(Double.NaN, FastMath.pow(Double.NaN,-1.0));
        assertEquals(Double.NaN, FastMath.pow(-1.0,Double.NaN));
    }

    public void test_powQuick_2double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double a = Math.abs(doubleAllMagnitudes());
            double b = doubleAllMagnitudes();
            double ref = Math.pow(a, b);
            double absRef = Math.abs(ref);
            final double epsilon;
            if (absRef < 1e10) {
                epsilon = 3.5e-2;
            } else if (absRef < 1e50) {
                epsilon = 0.17;
            } else {
                --i; // hack
                continue;
            }
            double delta = minDelta(ref, FastMath.powQuick(a,b));
            assertTrue(delta < epsilon);
        }
    }
    
    public void test_powFast_double_int() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double a = doubleUniformMinusTenTen();
            int b = (int)Math.round(doubleUniformMinusTenTen());
            assertTrue(minDelta(Math.pow(a,b), FastMath.powFast(a,b)) < DEFAULT_EPSILON);
        }
        assertEquals(1.0, FastMath.powFast(1.0,Integer.MIN_VALUE));
        assertEquals(Double.POSITIVE_INFINITY, FastMath.powFast(Double.MIN_VALUE,Integer.MIN_VALUE));
        assertEquals(Double.NaN, FastMath.powFast(Double.NaN,1));
    }

    public void test_twoPow_int() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            int value = (int)floatAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.pow(2,value), FastMath.twoPow(value)) < DEFAULT_EPSILON);
        }
    }

    public void test_pow2_int() {
        assertEquals(2*2, FastMath.pow2(2));
    }

    public void test_pow2_long() {
        assertEquals(2L*2L, FastMath.pow2(2L));
    }

    public void test_pow2_float() {
        assertEquals(2.1f*2.1f, FastMath.pow2(2.1f));
    }

    public void test_pow2_double() {
        assertEquals(2.1*2.1, FastMath.pow2(2.1));
    }

    public void test_pow3_int() {
        assertEquals(2*2*2, FastMath.pow3(2));
    }

    public void test_pow3_long() {
        assertEquals(2L*2L*2L, FastMath.pow3(2L));
    }

    public void test_pow3_float() {
        assertEquals(2.1f*2.1f*2.1f, FastMath.pow3(2.1f));
    }

    public void test_pow3_double() {
        assertEquals(2.1*2.1*2.1, FastMath.pow3(2.1));
    }

    public void test_sqrt_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = Math.abs(doubleAllMagnitudesNaNInf());
            assertTrue(minDelta(Math.sqrt(value), FastMath.sqrt(value)) < DEFAULT_EPSILON);
        }
        assertEquals(-0.0, FastMath.sqrt(-0.0));
        assertEquals(0.0, FastMath.sqrt(0.0));
        assertEquals(Double.NaN, FastMath.sqrt(Double.NaN));
    }

    public void test_cbrt_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.cbrt(value), FastMath.cbrt(value)) < DEFAULT_EPSILON);
        }
        assertEquals(-0.0, FastMath.cbrt(-0.0));
        assertEquals(0.0, FastMath.cbrt(0.0));
        assertEquals(Double.NaN, FastMath.cbrt(Double.NaN));
    }

    public void test_remainder_2double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double a = doubleAllMagnitudesNaNInf();
            double b = doubleAllMagnitudesNaNInf();
            double ref = getExpectedResult_remainder_2double(a,b);
            double res = FastMath.remainder(a,b);
            assertTrue(minDelta(ref, res) < DEFAULT_EPSILON);
        }

        double[] specialValues = new double[] { Double.NaN, -0.0, 0.0, -1.0, 1.0, -2.0, 2.0, -3.0, 3.0, -5.0, 5.0, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        for (int i=0;i<specialValues.length;i++) {
            double a = specialValues[i];
            for (int j=0;j<specialValues.length;j++) {
                double b = specialValues[j];
                double ref = getExpectedResult_remainder_2double(a,b);
                double res = FastMath.remainder(a,b);
                assertEquals(ref, res);
            }
        }
    }
    
    public void test_normalizeMinusPiPi() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            double refNorm = Math.atan2(Math.sin(value),Math.cos(value));
            double fastNorm = FastMath.normalizeMinusPiPi(value);
            assertTrue(minDelta(refNorm,fastNorm) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.normalizeMinusPiPi(Double.NaN));
    }

    public void test_normalizeMinusPiPiFast() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            if (Math.abs(value) > A_LOT) {
                value = doubleUniformMinusALotALot();
            }
            double refNorm = Math.atan2(Math.sin(value),Math.cos(value));
            double fastNorm = FastMath.normalizeMinusPiPiFast(value);
            assertTrue(minDelta(refNorm,fastNorm) < HUGE_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.normalizeMinusPiPiFast(Double.NaN));
    }

    public void test_normalizeZeroTwoPi() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            double refNorm = Math.atan2(Math.sin(value),Math.cos(value));
            if (refNorm < 0.0) {
                refNorm += 2*Math.PI;
            }
            double fastNorm = FastMath.normalizeZeroTwoPi(value);
            assertTrue(minDelta(refNorm,fastNorm) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.normalizeZeroTwoPi(Double.NaN));
    }

    public void test_normalizeZeroTwoPiFast() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            if (Math.abs(value) > A_LOT) {
                value = doubleUniformMinusALotALot();
            }
            double refNorm = Math.atan2(Math.sin(value),Math.cos(value));
            if (refNorm < 0.0) {
                refNorm += 2*Math.PI;
            }
            double fastNorm = FastMath.normalizeZeroTwoPiFast(value);
            assertTrue(minDelta(refNorm,fastNorm) < HUGE_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.normalizeZeroTwoPiFast(Double.NaN));
    }

    public void test_normalizeMinusHalfPiHalfPi() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.atan(Math.tan(value)), FastMath.normalizeMinusHalfPiHalfPi(value)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.normalizeMinusHalfPiHalfPi(Double.NaN));
    }

    public void test_normalizeMinusHalfPiHalfPiFast() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            if (Math.abs(value) > A_LOT) {
                value = doubleUniformMinusALotALot();
            }
            assertTrue(minDelta(Math.atan(Math.tan(value)), FastMath.normalizeMinusHalfPiHalfPiFast(value)) < HUGE_EPSILON);
        }
        assertEquals(Double.NaN, FastMath.normalizeMinusHalfPiHalfPiFast(Double.NaN));
    }

    public void test_hypot_2double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double x = doubleAllMagnitudesNaNInf();
            double y = doubleAllMagnitudesNaNInf();
            assertTrue(minDelta(Math.hypot(x,y), FastMath.hypot(x,y)) < DEFAULT_EPSILON);
        }
        assertEquals(Double.POSITIVE_INFINITY, FastMath.hypot(Double.POSITIVE_INFINITY,Double.NaN));
        assertEquals(Double.POSITIVE_INFINITY, FastMath.hypot(Double.NaN,Double.POSITIVE_INFINITY));
        assertEquals(Double.NaN, FastMath.hypot(Double.NaN,1.0));
        assertEquals(Double.NaN, FastMath.hypot(1.0,Double.NaN));
    }

    public void test_ceil_float() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            float value = floatCloseToInteger();
            assertEquals((float)Math.ceil(value), FastMath.ceil(value));
        }
        assertEquals(-0.0f, FastMath.ceil(-0.0f));
        assertEquals(0.0f, FastMath.ceil(0.0f));
        assertEquals(-1.0f, FastMath.ceil(-1.0f));
        assertEquals(1.0f, FastMath.ceil(1.0f));
        assertEquals(-5.0f, FastMath.ceil(-5.0f));
        assertEquals(5.0f, FastMath.ceil(5.0f));
        assertEquals(Float.NaN, FastMath.ceil(Float.NaN));
    }

    public void test_ceil_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleCloseToInteger();
            assertEquals(Math.ceil(value), FastMath.ceil(value));
        }
        assertEquals(-0.0, FastMath.ceil(-0.0));
        assertEquals(0.0, FastMath.ceil(0.0));
        assertEquals(-1.0, FastMath.ceil(-1.0));
        assertEquals(1.0, FastMath.ceil(1.0));
        assertEquals(-5.0, FastMath.ceil(-5.0));
        assertEquals(5.0, FastMath.ceil(5.0));
        assertEquals(Double.NaN, FastMath.ceil(Double.NaN));
    }

    public void test_floor_float() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            float value = floatCloseToInteger();
            assertEquals((float)Math.floor(value), FastMath.floor(value));
        }
        assertEquals(-0.0f, FastMath.floor(-0.0f));
        assertEquals(0.0f, FastMath.floor(0.0f));
        assertEquals(-1.0f, FastMath.floor(-1.0f));
        assertEquals(1.0f, FastMath.floor(1.0f));
        assertEquals(-5.0f, FastMath.floor(-5.0f));
        assertEquals(5.0f, FastMath.floor(5.0f));
        assertEquals(Float.NaN, FastMath.floor(Float.NaN));
    }

    public void test_floor_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleCloseToInteger();
            assertEquals(Math.floor(value), FastMath.floor(value));
        }
        assertEquals(-0.0, FastMath.floor(-0.0));
        assertEquals(0.0, FastMath.floor(0.0));
        assertEquals(-1.0, FastMath.floor(-1.0));
        assertEquals(1.0, FastMath.floor(1.0));
        assertEquals(-5.0, FastMath.floor(-5.0));
        assertEquals(5.0, FastMath.floor(5.0));
        assertEquals(Double.NaN, FastMath.floor(Double.NaN));
    }

    public void test_round_float() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            float value = floatAllMagnitudesNaNInf();
            assertTrue(Math.round(value) == FastMath.round(value));
        }
        assertEquals(0, FastMath.round(Float.NaN));
    }

    public void test_round_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertTrue(Math.round(value) == FastMath.round(value));
        }
        assertEquals(0L, FastMath.round(Double.NaN));
    }

    public void test_getExponent_float() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            float value = floatAllMagnitudesNaNInf();
            assertEquals(Math.getExponent(value), FastMath.getExponent(value));
        }
    }

    public void test_getExponent_double() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            double value = doubleAllMagnitudesNaNInf();
            assertEquals(Math.getExponent(value), FastMath.getExponent(value));
        }
    }

    public void test_toDegrees_double() {
        assertEquals(Math.toDegrees(1.1), FastMath.toDegrees(1.1), DEFAULT_EPSILON);
    }

    public void test_toRadians_double() {
        assertEquals(Math.toRadians(1.1), FastMath.toRadians(1.1), DEFAULT_EPSILON);
    }

    public void test_toRadians_boolean_2int_double() {
        assertEquals(Math.toRadians(1+2*(1.0/60)+3*(1.0/3600)), FastMath.toRadians(true,1,2,3), DEFAULT_EPSILON);
        assertEquals(-Math.toRadians(1+2*(1.0/60)+3*(1.0/3600)), FastMath.toRadians(false,1,2,3), DEFAULT_EPSILON);
    }

    public void test_toDegrees_boolean_2int_double() {
        assertEquals((1+2*(1.0/60)+3*(1.0/3600)), FastMath.toDegrees(true,1,2,3), DEFAULT_EPSILON);
        assertEquals(-(1+2*(1.0/60)+3*(1.0/3600)), FastMath.toDegrees(false,1,2,3), DEFAULT_EPSILON);
    }

    public void test_toDMS_double_IntWrapper_IntWrapper_DoubleWrapper() {
        boolean sign;
        IntWrapper degrees = new IntWrapper();
        IntWrapper minutes = new IntWrapper();
        DoubleWrapper seconds = new DoubleWrapper();

        sign = FastMath.toDMS(Math.toRadians(1+2*(1.0/60)+3.1*(1.0/3600)), degrees, minutes, seconds);
        assertEquals(true, sign);
        assertEquals(1, degrees.value);
        assertEquals(2, minutes.value);
        assertEquals(3.1, seconds.value, DEFAULT_EPSILON);

        sign = FastMath.toDMS(Math.toRadians(-(1+2*(1.0/60)+3.1*(1.0/3600))), degrees, minutes, seconds);
        assertEquals(false, sign);
        assertEquals(1, degrees.value);
        assertEquals(2, minutes.value);
        assertEquals(3.1, seconds.value, DEFAULT_EPSILON);

        sign = FastMath.toDMS(-Math.PI, degrees, minutes, seconds);
        assertEquals(false, sign);
        assertEquals(180, degrees.value);
        assertEquals(0, minutes.value);
        assertEquals(0.0, seconds.value);

        sign = FastMath.toDMS(-Math.PI/2, degrees, minutes, seconds);
        assertEquals(false, sign);
        assertEquals(90, degrees.value);
        assertEquals(0, minutes.value);
        assertEquals(0.0, seconds.value);

        sign = FastMath.toDMS(0.0, degrees, minutes, seconds);
        assertEquals(0, degrees.value);
        assertEquals(0, minutes.value);
        assertEquals(0.0, seconds.value);

        sign = FastMath.toDMS(Math.PI/2, degrees, minutes, seconds);
        assertEquals(true, sign);
        assertEquals(90, degrees.value);
        assertEquals(0, minutes.value);
        assertEquals(0.0, seconds.value);

        sign = FastMath.toDMS(Math.PI, degrees, minutes, seconds);
        assertEquals(true, sign);
        assertEquals(180, degrees.value);
        assertEquals(0, minutes.value);
        assertEquals(0.0, seconds.value);
    }

    public void test_abs_int() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            int value = (int)doubleUniformMinusHundredHundred();
            assertTrue(Math.abs(value) == FastMath.abs(value));
        }
    }

    public void test_toIntExact_long() {
        /*
         * quick test (delegates)
         */

        assertEquals(Integer.MAX_VALUE, FastMath.toIntExact((long)Integer.MAX_VALUE));
        try {
            FastMath.toIntExact(((long)Integer.MAX_VALUE)+1L);
            assertTrue(false);
        } catch (ArithmeticException e) {
            // ok
        }
    }

    public void test_toInt_long() {
        /*
         * quick test (delegates)
         */

        assertEquals(Integer.MAX_VALUE, FastMath.toInt((long)Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, FastMath.toInt(Long.MAX_VALUE));
    }

    public void test_addExact_2int() {
        /*
         * quick test (delegates)
         */

        assertEquals(Integer.MAX_VALUE, FastMath.addExact(Integer.MAX_VALUE-1, 1));
        try {
            FastMath.addExact(Integer.MAX_VALUE, 1);
            assertTrue(false);
        } catch (ArithmeticException e) {
            // ok
        }
    }

    public void test_addExact_2long() {
        /*
         * quick test (delegates)
         */

        assertEquals(Long.MAX_VALUE, FastMath.addExact(Long.MAX_VALUE-1L, 1L));
        try {
            FastMath.addExact(Long.MAX_VALUE, 1L);
            assertTrue(false);
        } catch (ArithmeticException e) {
            // ok
        }
    }

    public void test_addBounded_2int() {
        /*
         * quick test (delegates)
         */

        assertEquals(Integer.MAX_VALUE, FastMath.addBounded(Integer.MAX_VALUE-1, 1));
        assertEquals(Integer.MAX_VALUE, FastMath.addBounded(Integer.MAX_VALUE, 1));
    }

    public void test_addBounded_2long() {
        /*
         * quick test (delegates)
         */

        assertEquals(Long.MAX_VALUE, FastMath.addBounded(Long.MAX_VALUE-1L, 1L));
        assertEquals(Long.MAX_VALUE, FastMath.addBounded(Long.MAX_VALUE, 1L));
    }

    public void test_subtractExact_2int() {
        /*
         * quick test (delegates)
         */

        assertEquals(Integer.MIN_VALUE, FastMath.subtractExact(Integer.MIN_VALUE+1, 1));
        try {
            FastMath.subtractExact(Integer.MIN_VALUE, 1);
            assertTrue(false);
        } catch (ArithmeticException e) {
            // ok
        }
    }

    public void test_subtractExact_2long() {
        /*
         * quick test (delegates)
         */

        assertEquals(Long.MIN_VALUE, FastMath.subtractExact(Long.MIN_VALUE+1L, 1L));
        try {
            FastMath.subtractExact(Long.MIN_VALUE, 1L);
            assertTrue(false);
        } catch (ArithmeticException e) {
            // ok
        }
    }

    public void test_subtractBounded_2int() {
        /*
         * quick test (delegates)
         */

        assertEquals(Integer.MIN_VALUE, FastMath.subtractBounded(Integer.MIN_VALUE+1, 1));
        assertEquals(Integer.MIN_VALUE, FastMath.subtractBounded(Integer.MIN_VALUE, 1));
    }

    public void test_subtractBounded_2long() {
        /*
         * quick test (delegates)
         */

        assertEquals(Long.MIN_VALUE, FastMath.subtractBounded(Long.MIN_VALUE+1L, 1L));
        assertEquals(Long.MIN_VALUE, FastMath.subtractBounded(Long.MIN_VALUE, 1L));
    }

    public void test_multiplyExact_2int() {
        /*
         * quick test (delegates)
         */

        assertEquals(Integer.MIN_VALUE, FastMath.multiplyExact(Integer.MIN_VALUE/2, 2));
        try {
            FastMath.multiplyExact(Integer.MIN_VALUE, 2);
            assertTrue(false);
        } catch (ArithmeticException e) {
            // ok
        }
    }

    public void test_multiplyExact_2long() {
        /*
         * quick test (delegates)
         */

        assertEquals(Long.MIN_VALUE, FastMath.multiplyExact(Long.MIN_VALUE/2L, 2L));
        try {
            FastMath.multiplyExact(Long.MIN_VALUE, 2L);
            assertTrue(false);
        } catch (ArithmeticException e) {
            // ok
        }
    }

    public void test_multiplyBounded_2int() {
        /*
         * quick test (delegates)
         */

        assertEquals(Integer.MIN_VALUE, FastMath.multiplyBounded(Integer.MIN_VALUE/2, 2));
        assertEquals(Integer.MIN_VALUE, FastMath.multiplyBounded(Integer.MIN_VALUE, 2));
    }

    public void test_multiplyBounded_2long() {
        /*
         * quick test (delegates)
         */

        assertEquals(Long.MIN_VALUE, FastMath.multiplyBounded(Long.MIN_VALUE/2L, 2L));
        assertEquals(Long.MIN_VALUE, FastMath.multiplyBounded(Long.MIN_VALUE, 2L));
    }

    public void test_toRange_3int() {
        assertEquals(0, FastMath.toRange(0, 2, -1));
        assertEquals(0, FastMath.toRange(0, 2, 0));
        assertEquals(1, FastMath.toRange(0, 2, 1));
        assertEquals(2, FastMath.toRange(0, 2, 2));
        assertEquals(2, FastMath.toRange(0, 2, 3));
    }

    public void test_toRange_3long() {
        assertEquals(0L, FastMath.toRange(0L, 2L, -1L));
        assertEquals(0L, FastMath.toRange(0L, 2L, 0L));
        assertEquals(1L, FastMath.toRange(0L, 2L, 1L));
        assertEquals(2L, FastMath.toRange(0L, 2L, 2L));
        assertEquals(2L, FastMath.toRange(0L, 2L, 3L));
    }

    public void test_toRange_3float() {
        assertEquals(0.0f, FastMath.toRange(0.0f, 2.0f, -1.0f));
        assertEquals(0.0f, FastMath.toRange(0.0f, 2.0f, 0.0f));
        assertEquals(1.0f, FastMath.toRange(0.0f, 2.0f, 1.0f));
        assertEquals(2.0f, FastMath.toRange(0.0f, 2.0f, 2.0f));
        assertEquals(2.0f, FastMath.toRange(0.0f, 2.0f, 3.0f));
    }

    public void test_toRange_3double() {
        assertEquals(0.0, FastMath.toRange(0.0, 2.0, -1.0));
        assertEquals(0.0, FastMath.toRange(0.0, 2.0, 0.0));
        assertEquals(1.0, FastMath.toRange(0.0, 2.0, 1.0));
        assertEquals(2.0, FastMath.toRange(0.0, 2.0, 2.0));
        assertEquals(2.0, FastMath.toRange(0.0, 2.0, 3.0));
    }

    public void test_isInClockwiseDomain_3double() {
        assertTrue(FastMath.isInClockwiseDomain(0.0, 2*Math.PI, 0.0));
        assertTrue(FastMath.isInClockwiseDomain(0.0, 2*Math.PI, -Math.PI));
        assertTrue(FastMath.isInClockwiseDomain(0.0, 2*Math.PI, Math.PI));
        assertTrue(FastMath.isInClockwiseDomain(0.0, 2*Math.PI, 2*Math.PI));
        assertTrue(FastMath.isInClockwiseDomain(-Math.PI, 2*Math.PI, -Math.PI));
        assertTrue(FastMath.isInClockwiseDomain(-Math.PI, 2*Math.PI, 0.0));
        assertTrue(FastMath.isInClockwiseDomain(-Math.PI, 2*Math.PI, Math.PI));

        // always in
        for (int i=-10;i<10;i++) {
            double startAngRad = Math.toRadians(55.0*i);
            double spanAngRad = Math.PI/2;
            double angRad = startAngRad + Math.PI/3;
            assertTrue(FastMath.isInClockwiseDomain(startAngRad, spanAngRad, angRad));
        }

        // never in
        for (int i=-10;i<10;i++) {
            double startAngRad = Math.toRadians(55.0*i);
            double spanAngRad = Math.PI/3;
            double angRad = startAngRad + Math.PI/2;
            assertFalse(FastMath.isInClockwiseDomain(startAngRad, spanAngRad, angRad));
        }

        // small angular values
        assertTrue(FastMath.isInClockwiseDomain(0.0, 2*Math.PI, -1e-10));
        assertFalse(FastMath.isInClockwiseDomain(0.0, 2*Math.PI, -1e-20));
        assertTrue(FastMath.isInClockwiseDomain(0.0, 2*FastMath.PI_SUP, -1e-20));
        assertTrue(FastMath.isInClockwiseDomain(1e-10, 2*Math.PI, -1e-20));
        assertFalse(FastMath.isInClockwiseDomain(1e-20, 2*Math.PI, -1e-20));
        assertTrue(FastMath.isInClockwiseDomain(1e-20, 2*FastMath.PI_SUP, -1e-20));

        // NaN
        assertFalse(FastMath.isInClockwiseDomain(Double.NaN, Math.PI, Math.PI/2));
        assertFalse(FastMath.isInClockwiseDomain(Double.NaN, 3*Math.PI, Math.PI/2));
        assertFalse(FastMath.isInClockwiseDomain(0.0, Math.PI, Double.NaN));
        assertFalse(FastMath.isInClockwiseDomain(0.0, 3*Math.PI, Double.NaN));
        assertFalse(FastMath.isInClockwiseDomain(0.0, Double.NaN, Math.PI/2));
    }

    public void test_isNaNOrInfinite_float() {
        assertTrue(FastMath.isNaNOrInfinite(Float.NaN));
        assertTrue(FastMath.isNaNOrInfinite(Float.NEGATIVE_INFINITY));
        assertTrue(FastMath.isNaNOrInfinite(Float.POSITIVE_INFINITY));
        
        assertFalse(FastMath.isNaNOrInfinite(0.0f));
        assertFalse(FastMath.isNaNOrInfinite(1.0f));
    }
    
    public void test_isNaNOrInfinite_double() {
        assertTrue(FastMath.isNaNOrInfinite(Double.NaN));
        assertTrue(FastMath.isNaNOrInfinite(Double.NEGATIVE_INFINITY));
        assertTrue(FastMath.isNaNOrInfinite(Double.POSITIVE_INFINITY));
        
        assertFalse(FastMath.isNaNOrInfinite(0.0));
        assertFalse(FastMath.isNaNOrInfinite(1.0));
    }
    
    /*
     * Not-redefined java.lang.Math public values and treatments.
     * ===> fast "tests" just for coverage.
     */

    public void test_E() {
        assertEquals(Math.E, FastMath.E);
    }

    public void test_PI() {
        assertEquals(Math.PI, FastMath.PI);
    }

    public void test_abs_double() {
        assertEquals(Math.abs(1.0), FastMath.abs(1.0));
    }

    public void test_abs_float() {
        assertEquals(Math.abs(1.0f), FastMath.abs(1.0f));
    }

    public void test_abs_long() {
        assertEquals(Math.abs(1L), FastMath.abs(1L));
    }

    public void test_copySign_2double() {
        assertEquals(Math.copySign(1.0,1.0), FastMath.copySign(1.0,1.0));
    }

    public void test_copySign_2float() {
        assertEquals(Math.copySign(1.0f,1.0f), FastMath.copySign(1.0f,1.0f));
    }

    public void test_IEEEremainder_2double() {
        assertEquals(Math.IEEEremainder(1.0,1.0), FastMath.IEEEremainder(1.0,1.0));
    }

    public void test_max_2double() {
        assertEquals(Math.max(1.0,1.0), FastMath.max(1.0,1.0));
    }

    public void test_max_2float() {
        assertEquals(Math.max(1.0f,1.0f), FastMath.max(1.0f,1.0f));
    }

    public void test_max_2int() {
        assertEquals(Math.max(1,1), FastMath.max(1,1));
    }

    public void test_max_2long() {
        assertEquals(Math.max(1L,1L), FastMath.max(1L,1L));
    }

    public void test_min_2double() {
        assertEquals(Math.min(1.0,1.0), FastMath.min(1.0,1.0));
    }

    public void test_min_2float() {
        assertEquals(Math.min(1.0f,1.0f), FastMath.min(1.0f,1.0f));
    }

    public void test_min_2int() {
        assertEquals(Math.min(1,1), FastMath.min(1,1));
    }

    public void test_min_2long() {
        assertEquals(Math.min(1L,1L), FastMath.min(1L,1L));
    }

    public void test_nextAfter_2double() {
        assertEquals(Math.nextAfter(1.0,1.0), FastMath.nextAfter(1.0,1.0));
    }

    public void test_nextAfter_2float() {
        assertEquals(Math.nextAfter(1.0f,1.0f), FastMath.nextAfter(1.0f,1.0f));
    }

    public void test_nextUp_double() {
        assertEquals(Math.nextUp(1.0), FastMath.nextUp(1.0));
    }

    public void test_nextUp_float() {
        assertEquals(Math.nextUp(1.0f), FastMath.nextUp(1.0f));
    }

    public void test_random() {
        assertTrue((FastMath.random() >= 0.0) && (FastMath.random() <= 1.0));
    }

    public void test_rint_double() {
        assertEquals(Math.rint(1.0), FastMath.rint(1.0));
    }

    public void test_scalb_double_int() {
        assertEquals(Math.scalb(1.0,1), FastMath.scalb(1.0,1));
    }

    public void test_scalb_float_int() {
        assertEquals(Math.scalb(1.0f,1), FastMath.scalb(1.0f,1));
    }

    public void test_signum_double() {
        assertEquals(Math.signum(1.0), FastMath.signum(1.0));
    }

    public void test_signum_float() {
        assertEquals(Math.signum(1.0f), FastMath.signum(1.0f));
    }

    public void test_ulp_double() {
        assertEquals(Math.ulp(1.0), FastMath.ulp(1.0));
    }

    public void test_ulp_float() {
        assertEquals(Math.ulp(1.0f), FastMath.ulp(1.0f));
    }

    //--------------------------------------------------------------------------
    // MISCELLANEOUS METHODS
    //--------------------------------------------------------------------------

    /*
     * uniform
     */

    private int intUniform() {
        return random.nextInt();
    }

    private long longUniform() {
        return random.nextLong();
    }

    private double doubleUniform(double min, double max) {
        final double width = max-min;
        if (Double.isNaN(width) || Double.isInfinite(width)) {
            throw new ArithmeticException();
        }
        return min + width * random.nextDouble();
    }

    private double doubleUniformMinusOneOne() {
        return doubleUniform(-1.0,1.0);
    }

    private double doubleUniformMinusTenTen() {
        return doubleUniform(-10.0,10.0);
    }

    private double doubleUniformMinusHundredHundred() {
        return doubleUniform(-100.0,100.0);
    }

    private double doubleUniformMinusALotALot() {
        return doubleUniform(-A_LOT,A_LOT);
    }

    /*
     * magnitudes
     */

    private float floatAllMagnitudes() {
        float tmp;
        do {
            tmp = Float.intBitsToFloat(intUniform());
        } while (Float.isNaN(tmp) || Float.isInfinite(tmp));
        return tmp;
    }

    private double doubleAllMagnitudes() {
        double tmp;
        do {
            tmp = Double.longBitsToDouble(longUniform());
        } while (Double.isNaN(tmp) || Double.isInfinite(tmp));
        return tmp;
    }

    private float floatAllMagnitudesNaNInf() {
        if (random.nextDouble() < 0.1) {
            // We want infinities and NaNs.
            return random.nextBoolean() ? Float.NaN : (random.nextBoolean() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY);
        } else {
            return Float.intBitsToFloat(intUniform());
        }
    }

    private double doubleAllMagnitudesNaNInf() {
        if (random.nextDouble() < 0.1) {
            // We want infinities and NaNs.
            return random.nextBoolean() ? Double.NaN : (random.nextBoolean() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        } else {
            return Double.longBitsToDouble(longUniform());
        }
    }

    private float floatCloseToInteger() {
        /*
         * (yes, direction is a double for nextAfter on floats)
         */
        // an integer
        float value = (float)Math.rint(floatAllMagnitudes());
        if (random.nextBoolean()) {
            // integer or just below one
            value = Math.nextAfter(value, Double.NEGATIVE_INFINITY);
        } else if (random.nextBoolean()) {
            // integer or just above one
            value = Math.nextAfter(value, Double.POSITIVE_INFINITY);
        }
        return value;
    }

    private double doubleCloseToInteger() {
        // an integer
        double value = Math.rint(doubleAllMagnitudes());
        if (random.nextBoolean()) {
            // integer or just below one
            value = Math.nextAfter(value, Double.NEGATIVE_INFINITY);
        } else if (random.nextBoolean()) {
            // integer or just above one
            value = Math.nextAfter(value, Double.POSITIVE_INFINITY);
        }
        return value;
    }

    /*
     * 
     */

    private static double absDelta(double a, double b) {
        if (a == b) {
            return 0.0;
        }
        if (Double.isNaN(a)) {
            return Double.isNaN(b) ? 0.0 : Double.POSITIVE_INFINITY;
        } else if (Double.isNaN(b)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(a-b);
    }

    private static double relDelta(double a, double b) {
        if (a == b) {
            return 0.0;
        }
        if (signsAreDifferent(a,b)) {
            return Double.POSITIVE_INFINITY;
        }
        if (Double.isNaN(a)) {
            return (Double.isNaN(b)) ? 0.0 : Double.POSITIVE_INFINITY;
        } else if (Double.isNaN(b)) {
            return Double.POSITIVE_INFINITY;
        }
        if (Math.abs(a) < 1.0) {
            // Division seems to behave weird for very low values.
            a = Math.scalb(a,100);
            b = Math.scalb(b,100);
        }
        return Math.abs(a-b) / Math.max(Math.abs(a), Math.abs(b));
    }

    private static double minDelta(double a, double b) {
        return Math.min(absDelta(a,b), relDelta(a,b));
    }

    /**
     * @return True if value signs are different. Returns false if either
     *         value is NaN.
     */
    private static boolean signsAreDifferent(double a, double b) {
        return ((a > 0) && (b < 0)) || ((a < 0) && (b > 0));
    }
    
    /*
     * 
     */
    
    private static double getExpectedResult_remainder_2double(double a, double b) {
        double expected = Math.IEEEremainder(a,b);

        /*
         * Treating special case, where we differ from IEEEremainder.
         */
        
        double div = a/b;
        // If div is equally close to surrounding integers,
        // this will be the even value.
        double divAnteComma = Math.rint(div);
        double divPostComma = div - divAnteComma;
        if (Math.abs(divPostComma) == 0.5) {
            double nIEEE = divAnteComma;
            // If div is equally close to surrounding integers,
            // this will be the value of lowest magnitude.
            double nFastMath = Math.rint(Math.nextAfter(div, 0.0));
            if (nIEEE != nFastMath) {
                assertEquals(Math.abs(nIEEE), Math.abs(nFastMath)+1.0);
                final double newExpected = expected - (nFastMath - nIEEE) * b;
                // n choice only valid if result remains in output range.
                final double absBound = Math.abs(b/2);
                if ((newExpected >= -absBound) && (newExpected <= absBound)) {
                    expected = newExpected;
                }
            }
        }

        return expected;
    }
}
