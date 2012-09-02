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
import net.jodk.test.TestUtils;

/**
 * Class to test some treatments of FastMath class:
 * speed and accuracy, relative to java.lang.Math treatments.
 */
public strictfp class FastMathPerf {

    //--------------------------------------------------------------------------
    // CONFIGURATION
    //--------------------------------------------------------------------------

    private static final int NBR_OF_CALLS = 10 * 1000 * 1000;
    
    private static final int NBR_OF_VALUES = 100 * 1000;
    
    private boolean settingMode = false;

    //--------------------------------------------------------------------------
    // MEMBERS
    //--------------------------------------------------------------------------

    private static final double A_LITTLE_LOT = 1e6;
    private static final double A_BIG_LOT = 1e12;
    // For float values near limit where they can't have digits after comma.
    private static final double FLOAT_COMMA_LIMIT = 2e7; // > 2*Math.pow(2,23);
    // For double values near limit where they can't have digits after comma.
    private static final double DOUBLE_COMMA_LIMIT = 1e16; // > 2*Math.pow(2,52);
    private static final double NEXT_D_BEFORE_ONE = Double.longBitsToDouble(0x3FEFFFFFFFFFFFFFL); // 0.9999999999999999
    private static final double NEXT_D_AFTER_ONE = Double.longBitsToDouble(0x3FF0000000000001L); // 1.0000000000000002
    private static final double NEXT_D_BEFORE_TEN = Double.longBitsToDouble(0x4023FFFFFFFFFFFFL); // 9.999999999999998
    private static final double NEXT_D_AFTER_TEN = Double.longBitsToDouble(0x4024000000000001L); // 10.000000000000002
    private static final float NEXT_F_BEFORE_ONE = Float.intBitsToFloat(0x3F7FFFFF); // 0.99999994
    private static final float NEXT_F_AFTER_ONE = Float.intBitsToFloat(0x3F800001); // 1.0000001
    private static final float NEXT_F_BEFORE_TEN = Float.intBitsToFloat(0x411FFFFF); // 9.999999
    private static final float NEXT_F_AFTER_TEN = Float.intBitsToFloat(0x41200001); // 10.000001
    private final double[] anglesMinusPiPi = new double[NBR_OF_VALUES];
    private final double[] anglesZeroTwoPi = new double[NBR_OF_VALUES];
    private final double[] anglesMinusHalfPiHalfPi = new double[NBR_OF_VALUES];
    private final double[] anglesMinusTwoPiTwoPi = new double[NBR_OF_VALUES];
    private final double[] anglesNearPiModTwoPi = new double[NBR_OF_VALUES];
    private final double[] anglesNearTwoPiModTwoPi = new double[NBR_OF_VALUES];
    private final double[] anglesNearHalfPiModPi = new double[NBR_OF_VALUES];
    private final double[] valuesMinusALittleLotALittleLot = new double[NBR_OF_VALUES];
    private final double[] valuesMinusABigLotABigLot = new double[NBR_OF_VALUES];
    private final double[] valuesFloatCommaLimit = new double[NBR_OF_VALUES];
    private final double[] valuesDoubleCommaLimit = new double[NBR_OF_VALUES];
    private final double[] valuesForAsinAcos = new double[NBR_OF_VALUES];
    private final double[] valuesForAtan = new double[NBR_OF_VALUES];
    private final double[] valuesZeroTen = new double[NBR_OF_VALUES];
    private final double[] valuesMinusOneOne = new double[NBR_OF_VALUES];
    private final double[] valuesMinusTenTen = new double[NBR_OF_VALUES];
    private final double[] valuesMinusHundredHundred = new double[NBR_OF_VALUES];
    private final int[] valuesIntZeroTenTen = new int[NBR_OF_VALUES];
    private final int[] valuesIntMinusTenTen = new int[NBR_OF_VALUES];
    private final int[] valuesIntMinusHundredHundred = new int[NBR_OF_VALUES];
    private final double[] valuesDoubleAllMagnitudes = new double[NBR_OF_VALUES];
    private final float[] valuesFloatAllMagnitudes = new float[NBR_OF_VALUES];
    private final int[] valuesIntAllMagnitudes = new int[NBR_OF_VALUES];
    private final long[] valuesLongAllMagnitudes = new long[NBR_OF_VALUES];
    private final int[] valuesIntPositive = new int[NBR_OF_VALUES];
    private final long[] valuesLongPositive = new long[NBR_OF_VALUES];
    private final double[] values1ForExp = new double[NBR_OF_VALUES];
    private final double[] values2ForExp = new double[NBR_OF_VALUES];
    private final double[] values1ForLog = new double[NBR_OF_VALUES];
    private final double[] values2ForLog = new double[NBR_OF_VALUES];
    private final double[] valuesForLog1p = new double[NBR_OF_VALUES];
    private final double[] valuesXForHypot = new double[NBR_OF_VALUES];
    private final double[] valuesYForHypot = new double[NBR_OF_VALUES];
    private final double[] valuesA1ForPow = new double[NBR_OF_VALUES];
    private final double[] valuesA2ForPow = new double[NBR_OF_VALUES];
    private final float[] valuesFloatNearIntegers = new float[NBR_OF_VALUES];
    private final double[] valuesDoubleNearIntegers = new double[NBR_OF_VALUES];

    private final Random random = new Random(123456789L);

    private long timerRef;

    //--------------------------------------------------------------------------
    // PUBLIC METHODS
    //--------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println(TestUtils.getJVMInfo());
        newRun(args);
    }

    public static void newRun(String[] args) {
        new FastMathPerf().run(args);
    }

    public FastMathPerf() {
        this.init();
    }

    //--------------------------------------------------------------------------
    // PRIVATE METHODS
    //--------------------------------------------------------------------------

    private void init() {
        for (int i=0;i<NBR_OF_VALUES;i++) {
            anglesMinusPiPi[i] = doubleUniformMinusOneOne() * Math.PI;
            anglesZeroTwoPi[i] = doubleUniform(0.0,2*Math.PI);
            anglesMinusHalfPiHalfPi[i] = doubleUniformMinusOneOne() * (Math.PI/2);
            anglesMinusTwoPiTwoPi[i] = doubleUniformMinusOneOne() * (2*Math.PI);
            int valueZeroFour = intUniform(0,4);
            int valueMinusTenTen = intUniform(-10,10);
            double direction = random.nextBoolean() ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            anglesNearPiModTwoPi[i] = Math.PI + (2*Math.PI) * valueMinusTenTen;
            anglesNearTwoPiModTwoPi[i] = (2*Math.PI) + (2*Math.PI) * valueMinusTenTen;
            anglesNearHalfPiModPi[i] = (Math.PI/2) + (Math.PI) * valueMinusTenTen;
            for (int j=valueZeroFour;j>=0;j--) {
                anglesNearPiModTwoPi[i] = Math.nextAfter(anglesNearPiModTwoPi[i], direction);
                anglesNearTwoPiModTwoPi[i] = Math.nextAfter(anglesNearTwoPiModTwoPi[i], direction);
                anglesNearHalfPiModPi[i] = Math.nextAfter(anglesNearHalfPiModPi[i], direction);
            }
            valuesMinusALittleLotALittleLot[i] = doubleUniformMinusOneOne() * A_LITTLE_LOT;
            valuesMinusABigLotABigLot[i] = doubleUniformMinusOneOne() * A_BIG_LOT;
            valuesFloatCommaLimit[i] = doubleUniformMinusOneOne() * FLOAT_COMMA_LIMIT;
            valuesDoubleCommaLimit[i] = doubleUniformMinusOneOne() * DOUBLE_COMMA_LIMIT;
            valuesForAsinAcos[i] = Math.cos(doubleUniform(0.0,2*Math.PI));
            valuesForAtan[i] = Math.tan(doubleUniform(-Math.PI/2,Math.PI/2));
            valuesZeroTen[i] = doubleUniform(0.0,10.0);
            valuesMinusOneOne[i] = doubleUniformMinusOneOne();
            valuesMinusTenTen[i] = doubleUniformMinusOneOne() * 10.0;
            valuesMinusHundredHundred[i] = doubleUniform(-100.0,100.0);
            valuesIntZeroTenTen[i] = intUniform(0,10);
            valuesIntMinusTenTen[i] = intUniform(-10,10);
            valuesIntMinusHundredHundred[i] = intUniform(-100,100);
            valuesDoubleAllMagnitudes[i] = doubleAllMagnitudes();
            valuesFloatAllMagnitudes[i] = floatAllMagnitudes();
            valuesIntAllMagnitudes[i] = intAllMagnitudes();
            valuesLongAllMagnitudes[i] = longAllMagnitudes();
            int tmpInt;
            while ((tmpInt = intUniform() & Integer.MAX_VALUE) == 0) {
            }
            valuesIntPositive[i] = tmpInt;
            long tmpLong;
            while ((tmpLong = longUniform() & Long.MAX_VALUE) == 0) {
            }
            valuesLongPositive[i] = tmpLong;
            values1ForExp[i] = doubleUniform(-700.0,700.0);
            values2ForExp[i] = doubleUniform(-750.0,720.0);
            values1ForLog[i] = doubleUniform(0.1,10.0); // values around 1, such as log(x) goes around -1 and 0
            values2ForLog[i] = Double.MIN_VALUE + Math.abs(doubleAllMagnitudes());
            valuesForLog1p[i] = doubleUniform(0.1,10.0)-1.0;  // values around 0, such as log1p(x) goes around -1 and 0
            valuesXForHypot[i] = doubleUniformMinusOneOne() * Math.pow(10, doubleUniformMinusOneOne() * 290);
            valuesYForHypot[i] = valuesXForHypot[i] * (doubleUniformMinusOneOne() * Math.pow(10, doubleUniformMinusOneOne() * 18));
            valuesA1ForPow[i] = Math.pow(2.0, doubleUniformMinusOneOne() * 10.0) * (random.nextBoolean() ? 1.0 : -1.0);
            valuesA2ForPow[i] = Math.pow(2.0, doubleUniformMinusOneOne() * 100.0) * (random.nextBoolean() ? 1.0 : -1.0);
            valuesFloatNearIntegers[i] = Math.nextAfter((float)Math.round(1e10f * Math.pow(doubleUniformMinusOneOne(),3)), random.nextBoolean() ? Float.MAX_VALUE : Float.MIN_VALUE);
            valuesDoubleNearIntegers[i] = Math.nextAfter((double)Math.round(1e18 * Math.pow(doubleUniformMinusOneOne(),3)), random.nextBoolean() ? Double.MAX_VALUE : Double.MIN_VALUE);
        }
    }

    private static void settle() {
        TestUtils.settleAndNewLine();
    }

    private void run(String[] args) {
        // XXX
        System.out.println("--- "+FastMathPerf.class.getSimpleName()+"... ---");
        System.out.println("number of calls = "+NBR_OF_CALLS);
        System.out.println("number of random values = "+NBR_OF_VALUES);
        printLoopOverhead();

        if (false) {
            settingMode = true;
        }

        settle();
        testClassLoad();

        // trigonometry

        settle();
        test_cos_double();
        settle();
        test_cosQuick_double();
        settle();
        test_sin_double();
        settle();
        test_sinQuick_double();
        settle();
        test_sinAndCos_double_2DoubleWrapper();
        settle();
        test_tan_double();
        settle();
        test_acos_double();
        settle();
        test_asin_double();
        settle();
        test_atan_double();
        settle();
        test_atan2_2double();
        settle();
        test_isInClockwiseDomain_3double();

        // hyperbolic trigonometry

        settle();
        test_cosh_double();
        settle();
        test_sinh_double();
        settle();
        test_sinhAndCosh_double_2DoubleWrapper();
        settle();
        test_tanh_double();

        // exponentials

        settle();
        test_exp_double();
        settle();
        test_expQuick_double();
        settle();
        test_expm1_double();

        // logarithms

        settle();
        test_log_double();
        settle();
        test_logQuick_double();
        settle();
        test_log10_double();
        settle();
        test_log1p_double();
        settle();
        test_log2_int();
        settle();
        test_log2_long();
        
        // powers

        settle();
        test_pow_2double();
        settle();
        test_powQuick_2double();
        settle();
        test_powFast_double_int();
        settle();
        test_twoPow_int();

        // roots

        settle();
        test_sqrt_double();
        settle();
        test_cbrt_double();

        // reduction

        settle();
        test_remainder_2double();

        settle();
        test_normalizeMinusPiPi();
        settle();
        test_normalizeMinusPiPiFast();
        settle();
        test_normalizeZeroTwoPi();
        settle();
        test_normalizeZeroTwoPiFast();
        settle();
        test_normalizeMinusHalfPiHalfPi();
        settle();
        test_normalizeMinusHalfPiHalfPiFast();

        // basics

        settle();
        test_abs_int();
        settle();
        test_ceil_float();
        settle();
        test_ceil_double();
        settle();
        test_floor_float();
        settle();
        test_floor_double();
        settle();
        test_round_float();
        settle();
        test_round_double();

        // others

        settle();
        test_hypot_2double();

        settle();
        test_addExact_2int();
        settle();
        test_addExact_2long();
        settle();
        test_addBounded_2int();
        settle();
        test_addBounded_2long();
        settle();
        test_subtractExact_2int();
        settle();
        test_subtractExact_2long();
        settle();
        test_subtractBounded_2int();
        settle();
        test_subtractBounded_2long();
        settle();
        test_multiplyExact_2int();
        settle();
        test_multiplyExact_2long();
        settle();
        test_multiplyBounded_2int();
        settle();
        test_multiplyBounded_2long();

        System.out.println("--- ..."+FastMathPerf.class.getSimpleName()+" ---");
    }

    /*
     * 
     */

    private void testClassLoad() {
        int dummy = 0;

        System.out.println("--- testing FastMath class load (if not loaded already) ---");

        // Making sure Math and StrictMath are loaded already.
        if (Math.sin(0.0) != StrictMath.sin(0.0)) {
            System.out.println("can't happen");
        }
        
        startTimer();
        dummy += FastMath.abs(0);
        System.out.println("FastMath class load took "+getElapsedSeconds()+" s");
        
        useDummy(dummy);
    }

    private void test_cos_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing cos(double) ---");

        // [0,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cos(anglesZeroTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cos(double), values in [0,2*PI], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cos(anglesZeroTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cos(double), values in [0,2*PI], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cos(anglesZeroTwoPi[i]);
            double fastResult = FastMath.cos(anglesZeroTwoPi[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cos(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cos(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cos(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cos(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cos(valuesMinusTenTen[i]);
            double fastResult = FastMath.cos(valuesMinusTenTen[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-100,100]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cos(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cos(double), values in [-100,100], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cos(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cos(double), values in [-100,100], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cos(valuesMinusHundredHundred[i]);
            double fastResult = FastMath.cos(valuesMinusHundredHundred[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cos(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cos(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cos(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cos(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cos(valuesMinusALittleLotALittleLot[i]);
            double fastResult = FastMath.cos(valuesMinusALittleLotALittleLot[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cos(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cos(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cos(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cos(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cos(valuesMinusABigLotABigLot[i]);
            double fastResult = FastMath.cos(valuesMinusABigLotABigLot[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS/10;i++) {
            dummy += Math.cos(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop(/10) on     Math.cos(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS/10;i++) {
            dummy += FastMath.cos(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop(/10) on FastMath.cos(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cos(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.cos(valuesDoubleAllMagnitudes[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -Math.PI, -Math.PI/2, 0.0, Math.PI/2, Math.PI, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.cos(specialValues[i]);
            double fastResult = FastMath.cos(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.cos("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.cos("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_cosQuick_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing cosQuick(double) ---");

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cos(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on          Math.cos(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cosQuick(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cosQuick(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cos(valuesMinusALittleLotALittleLot[i]);
            double fastResult = FastMath.cosQuick(valuesMinusALittleLotALittleLot[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);
        
        useDummy(dummy);
    }

    private void test_sin_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing sin(double) ---");

        // [0,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sin(anglesZeroTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sin(double), values in [0,2*PI], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sin(anglesZeroTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sin(double), values in [0,2*PI], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sin(anglesZeroTwoPi[i]);
            double fastResult = FastMath.sin(anglesZeroTwoPi[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sin(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sin(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sin(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sin(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sin(valuesMinusTenTen[i]);
            double fastResult = FastMath.sin(valuesMinusTenTen[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-100,100]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sin(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sin(double), values in [-100,100], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sin(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sin(double), values in [-100,100], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sin(valuesMinusHundredHundred[i]);
            double fastResult = FastMath.sin(valuesMinusHundredHundred[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sin(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sin(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sin(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sin(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sin(valuesMinusALittleLotALittleLot[i]);
            double fastResult = FastMath.sin(valuesMinusALittleLotALittleLot[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sin(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sin(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sin(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sin(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sin(valuesMinusABigLotABigLot[i]);
            double fastResult = FastMath.sin(valuesMinusABigLotABigLot[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS/10;i++) {
            dummy += Math.sin(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop(/10) on     Math.sin(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS/10;i++) {
            dummy += FastMath.sin(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop(/10) on FastMath.sin(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sin(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.sin(valuesDoubleAllMagnitudes[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -Math.PI, -Math.PI/2, 0.0, Math.PI/2, Math.PI, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.sin(specialValues[i]);
            double fastResult = FastMath.sin(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.sin("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.sin("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_sinQuick_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing sinQuick(double) ---");

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sin(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on          Math.sin(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sinQuick(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sinQuick(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sin(valuesMinusALittleLotALittleLot[i]);
            double fastResult = FastMath.sinQuick(valuesMinusALittleLotALittleLot[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);
        
        useDummy(dummy);
    }

    private void test_sinAndCos_double_2DoubleWrapper() {
        double maxSinDelta;
        double maxCosDelta;
        int i;
        int j;
        DoubleWrapper sine = new DoubleWrapper();
        DoubleWrapper cosine = new DoubleWrapper();
        double dummy = 0.0;

        System.out.println("--- testing sinAndCos(double,DoubleWrapper,DoubleWrapper) ---");

        // [0,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinAndCos(anglesZeroTwoPi[j],sine,cosine);
            dummy += sine.value + cosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinAndCos(double,DoubleWrapper,DoubleWrapper), values in [0,2*PI], took "+getElapsedSeconds()+" s");

        maxSinDelta = 0.0;
        maxCosDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSin = Math.sin(anglesZeroTwoPi[i]);
            double refCos = Math.cos(anglesZeroTwoPi[i]);
            FastMath.sinAndCos(anglesZeroTwoPi[i],sine,cosine);
            double deltaSin = absDelta(sine.value,refSin);
            double deltaCos = absDelta(cosine.value,refCos);
            if (deltaSin > maxSinDelta) {
                maxSinDelta = deltaSin;
            }
            if (deltaCos > maxCosDelta) {
                maxCosDelta = deltaCos;
            }
        }
        System.out.println("max sin delta: "+maxSinDelta);
        System.out.println("max cos delta: "+maxCosDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinAndCos(valuesMinusTenTen[j],sine,cosine);
            dummy += sine.value + cosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinAndCos(double,DoubleWrapper,DoubleWrapper), values in [-10,10], took "+getElapsedSeconds()+" s");

        maxSinDelta = 0.0;
        maxCosDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSin = Math.sin(valuesMinusTenTen[i]);
            double refCos = Math.cos(valuesMinusTenTen[i]);
            FastMath.sinAndCos(valuesMinusTenTen[i],sine,cosine);
            double deltaSin = absDelta(sine.value,refSin);
            double deltaCos = absDelta(cosine.value,refCos);
            if (deltaSin > maxSinDelta) {
                maxSinDelta = deltaSin;
            }
            if (deltaCos > maxCosDelta) {
                maxCosDelta = deltaCos;
            }
        }
        System.out.println("max sin delta: "+maxSinDelta);
        System.out.println("max cos delta: "+maxCosDelta);

        // [-100,100]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinAndCos(valuesMinusHundredHundred[j],sine,cosine);
            dummy += sine.value + cosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinAndCos(double,DoubleWrapper,DoubleWrapper), values in [-100,100], took "+getElapsedSeconds()+" s");

        maxSinDelta = 0.0;
        maxCosDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSin = Math.sin(valuesMinusHundredHundred[i]);
            double refCos = Math.cos(valuesMinusHundredHundred[i]);
            FastMath.sinAndCos(valuesMinusHundredHundred[i],sine,cosine);
            double deltaSin = absDelta(sine.value,refSin);
            double deltaCos = absDelta(cosine.value,refCos);
            if (deltaSin > maxSinDelta) {
                maxSinDelta = deltaSin;
            }
            if (deltaCos > maxCosDelta) {
                maxCosDelta = deltaCos;
            }
        }
        System.out.println("max sin delta: "+maxSinDelta);
        System.out.println("max cos delta: "+maxCosDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinAndCos(valuesMinusALittleLotALittleLot[j],sine,cosine);
            dummy += sine.value + cosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinAndCos(double,DoubleWrapper,DoubleWrapper), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        maxSinDelta = 0.0;
        maxCosDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSin = Math.sin(valuesMinusALittleLotALittleLot[i]);
            double refCos = Math.cos(valuesMinusALittleLotALittleLot[i]);
            FastMath.sinAndCos(valuesMinusALittleLotALittleLot[i],sine,cosine);
            double deltaSin = absDelta(sine.value,refSin);
            double deltaCos = absDelta(cosine.value,refCos);
            if (deltaSin > maxSinDelta) {
                maxSinDelta = deltaSin;
            }
            if (deltaCos > maxCosDelta) {
                maxCosDelta = deltaCos;
            }
        }
        System.out.println("max sin delta: "+maxSinDelta);
        System.out.println("max cos delta: "+maxCosDelta);

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinAndCos(valuesMinusABigLotABigLot[j],sine,cosine);
            dummy += sine.value + cosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinAndCos(double,DoubleWrapper,DoubleWrapper), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        maxSinDelta = 0.0;
        maxCosDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSin = Math.sin(valuesMinusABigLotABigLot[i]);
            double refCos = Math.cos(valuesMinusABigLotABigLot[i]);
            FastMath.sinAndCos(valuesMinusABigLotABigLot[i],sine,cosine);
            double deltaSin = absDelta(sine.value,refSin);
            double deltaCos = absDelta(cosine.value,refCos);
            if (deltaSin > maxSinDelta) {
                maxSinDelta = deltaSin;
            }
            if (deltaCos > maxCosDelta) {
                maxCosDelta = deltaCos;
            }
        }
        System.out.println("max sin delta: "+maxSinDelta);
        System.out.println("max cos delta: "+maxCosDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinAndCos(valuesDoubleAllMagnitudes[j],sine,cosine);
            dummy += sine.value + cosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinAndCos(double,DoubleWrapper,DoubleWrapper), values of all magnitudes, took "+getElapsedSeconds()+" s");

        maxSinDelta = 0.0;
        maxCosDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSin = Math.sin(valuesDoubleAllMagnitudes[i]);
            double refCos = Math.cos(valuesDoubleAllMagnitudes[i]);
            FastMath.sinAndCos(valuesDoubleAllMagnitudes[i],sine,cosine);
            double deltaSin = absDelta(sine.value,refSin);
            double deltaCos = absDelta(cosine.value,refCos);
            if (deltaSin > maxSinDelta) {
                maxSinDelta = deltaSin;
            }
            if (deltaCos > maxCosDelta) {
                maxCosDelta = deltaCos;
            }
        }
        System.out.println("max sin delta: "+maxSinDelta);
        System.out.println("max cos delta: "+maxCosDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -Math.PI, -Math.PI/2, 0.0, Math.PI/2, Math.PI, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refSin = Math.sin(specialValues[i]);
            double refCos = Math.cos(specialValues[i]);
            FastMath.sinAndCos(specialValues[i],sine,cosine);
            double fastSin = sine.value;
            double fastCos = cosine.value;
            if (!Double.toString(refSin).equals(Double.toString(fastSin))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("                 Math.sin("+specialValues[i]+")="+refSin);
                System.out.println("FastMath.sinAndCos("+specialValues[i]+",,) sine="+fastSin);
            }
            if (!Double.toString(refCos).equals(Double.toString(fastCos))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("                   Math.cos("+specialValues[i]+")="+refCos);
                System.out.println("FastMath.sinAndCos("+specialValues[i]+",,) cosine="+fastCos);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_tan_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing tan(double) ---");

        // [-PI/2,PI/2]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tan(anglesMinusHalfPiHalfPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tan(double), values in [-PI/2,PI/2], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tan(anglesMinusHalfPiHalfPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tan(double), values in [-PI/2,PI/2], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tan(anglesMinusHalfPiHalfPi[i]);
            double fastResult = FastMath.tan(anglesMinusHalfPiHalfPi[i]);
            double delta = minDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (delta=min(abs delta, relative delta)): "+maxDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tan(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tan(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tan(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tan(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tan(valuesMinusTenTen[i]);
            double fastResult = FastMath.tan(valuesMinusTenTen[i]);
            double delta = minDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (delta=min(abs delta, relative delta)): "+maxDelta);

        // [-100,100]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tan(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tan(double), values in [-100,100], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tan(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tan(double), values in [-100,100], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tan(valuesMinusHundredHundred[i]);
            double fastResult = FastMath.tan(valuesMinusHundredHundred[i]);
            double delta = minDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (delta=min(abs delta, relative delta)): "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tan(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tan(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tan(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tan(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tan(valuesMinusALittleLotALittleLot[i]);
            double fastResult = FastMath.tan(valuesMinusALittleLotALittleLot[i]);
            double delta = minDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (delta=min(abs delta, relative delta)): "+maxDelta);

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tan(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tan(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tan(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tan(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tan(valuesMinusABigLotABigLot[i]);
            double fastResult = FastMath.tan(valuesMinusABigLotABigLot[i]);
            double delta = minDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (delta=min(abs delta, relative delta)): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS/10;i++) {
            dummy += Math.tan(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop(/10) on     Math.tan(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS/10;i++) {
            dummy += FastMath.tan(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop(/10) on FastMath.tan(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tan(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.tan(valuesDoubleAllMagnitudes[i]);
            double delta = minDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (delta=min(abs delta, relative delta)): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -Math.PI/2, 0.0, Math.PI/2, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.tan(specialValues[i]);
            double fastResult = FastMath.tan(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.tan("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.tan("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_acos_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing acos(double) ---");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.acos(valuesForAsinAcos[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.acos(double) took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.acos(valuesForAsinAcos[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.acos(double) took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.acos(valuesForAsinAcos[i]);
            double fastResult = FastMath.acos(valuesForAsinAcos[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                if (settingMode) {
                    System.out.println("acos: new delta="+delta+" for angle(deg)="+Math.toDegrees(Math.acos(valuesForAsinAcos[i])));
                }
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -1.0, 0.0, 1.0, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.acos(specialValues[i]);
            double fastResult = FastMath.acos(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.acos("+specialValues[i]+")(deg)="+Math.toDegrees(refResult));
                System.out.println("FastMath.acos("+specialValues[i]+")(deg)="+Math.toDegrees(fastResult));
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_asin_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing asin(double) ---");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.asin(valuesForAsinAcos[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.asin(double) took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.asin(valuesForAsinAcos[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.asin(double) took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.asin(valuesForAsinAcos[i]);
            double fastResult = FastMath.asin(valuesForAsinAcos[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                if (settingMode) {
                    System.out.println("asin: new delta="+delta+" for angle(deg)="+Math.toDegrees(Math.asin(valuesForAsinAcos[i])));
                }
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -2.0, -1.0, 0.0, 1.0, 2.0, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.asin(specialValues[i]);
            double fastResult = FastMath.asin(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.asin("+specialValues[i]+")(deg)="+Math.toDegrees(refResult));
                System.out.println("FastMath.asin("+specialValues[i]+")(deg)="+Math.toDegrees(fastResult));
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_atan_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing atan(double) ---");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.atan(valuesForAtan[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.atan(double) took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.atan(valuesForAtan[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.atan(double) took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.atan(valuesForAtan[i]);
            double fastResult = FastMath.atan(valuesForAtan[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                if (settingMode) {
                    System.out.println("atan: new delta="+delta+" for angle(deg)="+Math.toDegrees(Math.atan(valuesForAtan[i]))+", value="+valuesForAtan[i]);
                }
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -1.0, 0.0, 1.0, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.atan(specialValues[i]);
            double fastResult = FastMath.atan(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.atan("+specialValues[i]+")(deg)="+Math.toDegrees(refResult));
                System.out.println("FastMath.atan("+specialValues[i]+")(deg)="+Math.toDegrees(fastResult));
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_atan2_2double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;
        
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
        final int nbrOfCalls = avoidBug ? 10 * 1000/2 : NBR_OF_VALUES;

        System.out.println("--- testing atan2(double,double) ---");

        if (avoidBug) {
            System.out.println("(loops of only "+nbrOfCalls+" calls to avoid a JVM bug)");
        }
        
        j=0;
        startTimer();
        for (i=0;i<nbrOfCalls;i++) {
            dummy += Math.atan2(valuesMinusTenTen[j],valuesMinusTenTen[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.atan2(double,double) took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<nbrOfCalls;i++) {
            dummy += FastMath.atan2(valuesMinusTenTen[j],valuesMinusTenTen[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.atan2(double,double) took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<Math.min(nbrOfCalls, NBR_OF_VALUES);i++) {
            double refResult = Math.atan2(valuesMinusTenTen[i],valuesMinusTenTen[(NBR_OF_VALUES-1)-i]);
            double fastResult = FastMath.atan2(valuesMinusTenTen[i],valuesMinusTenTen[(NBR_OF_VALUES-1)-i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                if (settingMode) {
                    System.out.println("atan2: new delta="+delta+" for y="+valuesMinusTenTen[i]+" and x="+valuesMinusTenTen[(NBR_OF_VALUES-1)-i]);
                }
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -1.0, 0.0, 1.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            for (j=0;j<specialValues.length;j++) {
                double refResult = Math.atan2(specialValues[i],specialValues[j]);
                double fastResult = FastMath.atan2(specialValues[i],specialValues[j]);
                if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                    if (!foundDifferences) {
                        System.out.println("");
                        foundDifferences = true;
                    }
                    System.out.println("    Math.atan2("+specialValues[i]+","+specialValues[j]+")(deg)="+Math.toDegrees(refResult));
                    System.out.println("FastMath.atan2("+specialValues[i]+","+specialValues[j]+")(deg)="+Math.toDegrees(fastResult));
                }
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_cosh_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing cosh(double) ---");

        // [-1,1]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cosh(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cosh(double), values in [-1,1], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cosh(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cosh(double), values in [-1,1], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cosh(valuesMinusOneOne[i]);
            double fastResult = FastMath.cosh(valuesMinusOneOne[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cosh(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cosh(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cosh(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cosh(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cosh(valuesMinusTenTen[i]);
            double fastResult = FastMath.cosh(valuesMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-700,700]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cosh(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cosh(double), values in [-700,700], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cosh(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cosh(double), values in [-700,700], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cosh(values1ForExp[i]);
            double fastResult = FastMath.cosh(values1ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-750,720]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cosh(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cosh(double), values in [-750,720], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cosh(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cosh(double), values in [-750,720], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cosh(values2ForExp[i]);
            double fastResult = FastMath.cosh(values2ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cosh(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cosh(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cosh(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cosh(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cosh(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.cosh(valuesDoubleAllMagnitudes[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, 0.0, 1e-10, 1e-5, 0.1, 0.5, 1.0, 1.5, -740.0, -745.13, -745.14, 705.0, 709.782, 709.783, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.cosh(specialValues[i]);
            double fastResult = FastMath.cosh(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.cosh("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.cosh("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_sinh_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing sinh(double) ---");

        // [-1,1]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sinh(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sinh(double), values in [-1,1], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sinh(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sinh(double), values in [-1,1], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sinh(valuesMinusOneOne[i]);
            double fastResult = FastMath.sinh(valuesMinusOneOne[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sinh(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sinh(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sinh(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sinh(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sinh(valuesMinusTenTen[i]);
            double fastResult = FastMath.sinh(valuesMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-700,700]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sinh(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sinh(double), values in [-700,700], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sinh(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sinh(double), values in [-700,700], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sinh(values1ForExp[i]);
            double fastResult = FastMath.sinh(values1ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-750,720]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sinh(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sinh(double), values in [-750,720], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sinh(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sinh(double), values in [-750,720], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sinh(values2ForExp[i]);
            double fastResult = FastMath.sinh(values2ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sinh(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sinh(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sinh(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sinh(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sinh(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.sinh(valuesDoubleAllMagnitudes[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, 0.0, 1e-10, 1e-5, 0.1, 0.5, 1.0, 1.5, -740.0, -745.13, -745.14, 705.0, 709.782, 709.783, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.sinh(specialValues[i]);
            double fastResult = FastMath.sinh(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.sinh("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.sinh("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_sinhAndCosh_double_2DoubleWrapper() {
        double maxSinhDelta;
        double maxCoshDelta;
        int i;
        int j;
        DoubleWrapper hsine = new DoubleWrapper();
        DoubleWrapper hcosine = new DoubleWrapper();
        double dummy = 0.0;

        System.out.println("--- testing sinhAndCosh(double,DoubleWrapper,DoubleWrapper) ---");

        // [-1,1]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinhAndCosh(valuesMinusOneOne[j],hsine,hcosine);
            dummy += hsine.value + hcosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinhAndCosh(double,DoubleWrapper,DoubleWrapper), values in [-1,1], took "+getElapsedSeconds()+" s");

        maxSinhDelta = 0.0;
        maxCoshDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSinh = Math.sinh(valuesMinusOneOne[i]);
            double refCosh = Math.cosh(valuesMinusOneOne[i]);
            FastMath.sinhAndCosh(valuesMinusOneOne[i],hsine,hcosine);
            double deltaSinh = relDelta(hsine.value,refSinh);
            double deltaCosh = relDelta(hcosine.value,refCosh);
            if (deltaSinh > maxSinhDelta) {
                maxSinhDelta = deltaSinh;
            }
            if (deltaCosh > maxCoshDelta) {
                maxCoshDelta = deltaCosh;
            }
        }
        System.out.println("max sinh delta (relative): "+maxSinhDelta);
        System.out.println("max cosh delta (relative): "+maxCoshDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinhAndCosh(valuesMinusTenTen[j],hsine,hcosine);
            dummy += hsine.value + hcosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinhAndCosh(double,DoubleWrapper,DoubleWrapper), values in [-10,10], took "+getElapsedSeconds()+" s");

        maxSinhDelta = 0.0;
        maxCoshDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSinh = Math.sinh(valuesMinusTenTen[i]);
            double refCosh = Math.cosh(valuesMinusTenTen[i]);
            FastMath.sinhAndCosh(valuesMinusTenTen[i],hsine,hcosine);
            double deltaSinh = relDelta(hsine.value,refSinh);
            double deltaCosh = relDelta(hcosine.value,refCosh);
            if (deltaSinh > maxSinhDelta) {
                maxSinhDelta = deltaSinh;
            }
            if (deltaCosh > maxCoshDelta) {
                maxCoshDelta = deltaCosh;
            }
        }
        System.out.println("max sinh delta (relative): "+maxSinhDelta);
        System.out.println("max cosh delta (relative): "+maxCoshDelta);

        // [-700,700]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinhAndCosh(values1ForExp[j],hsine,hcosine);
            dummy += hsine.value + hcosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinhAndCosh(double,DoubleWrapper,DoubleWrapper), values in [-700,700], took "+getElapsedSeconds()+" s");

        maxSinhDelta = 0.0;
        maxCoshDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSinh = Math.sinh(values1ForExp[i]);
            double refCosh = Math.cosh(values1ForExp[i]);
            FastMath.sinhAndCosh(values1ForExp[i],hsine,hcosine);
            double deltaSinh = relDelta(hsine.value,refSinh);
            double deltaCosh = relDelta(hcosine.value,refCosh);
            if (deltaSinh > maxSinhDelta) {
                maxSinhDelta = deltaSinh;
            }
            if (deltaCosh > maxCoshDelta) {
                maxCoshDelta = deltaCosh;
            }
        }
        System.out.println("max sinh delta (relative): "+maxSinhDelta);
        System.out.println("max cosh delta (relative): "+maxCoshDelta);

        // [-750,720]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinhAndCosh(values2ForExp[j],hsine,hcosine);
            dummy += hsine.value + hcosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinhAndCosh(double,DoubleWrapper,DoubleWrapper), values in [-750,720], took "+getElapsedSeconds()+" s");

        maxSinhDelta = 0.0;
        maxCoshDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSinh = Math.sinh(values2ForExp[i]);
            double refCosh = Math.cosh(values2ForExp[i]);
            FastMath.sinhAndCosh(values2ForExp[i],hsine,hcosine);
            double deltaSinh = relDelta(hsine.value,refSinh);
            double deltaCosh = relDelta(hcosine.value,refCosh);
            if (deltaSinh > maxSinhDelta) {
                maxSinhDelta = deltaSinh;
            }
            if (deltaCosh > maxCoshDelta) {
                maxCoshDelta = deltaCosh;
            }
        }
        System.out.println("max sinh delta (relative): "+maxSinhDelta);
        System.out.println("max cosh delta (relative): "+maxCoshDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            FastMath.sinhAndCosh(valuesDoubleAllMagnitudes[j],hsine,hcosine);
            dummy += hsine.value + hcosine.value;
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.sinhAndCosh(double,DoubleWrapper,DoubleWrapper), values of all magnitudes, took "+getElapsedSeconds()+" s");

        maxSinhDelta = 0.0;
        maxCoshDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refSinh = Math.sinh(valuesDoubleAllMagnitudes[i]);
            double refCosh = Math.cosh(valuesDoubleAllMagnitudes[i]);
            FastMath.sinhAndCosh(valuesDoubleAllMagnitudes[i],hsine,hcosine);
            double deltaSinh = relDelta(hsine.value,refSinh);
            double deltaCosh = relDelta(hcosine.value,refCosh);
            if (deltaSinh > maxSinhDelta) {
                maxSinhDelta = deltaSinh;
            }
            if (deltaCosh > maxCoshDelta) {
                maxCoshDelta = deltaCosh;
            }
        }
        System.out.println("max sinh delta (relative): "+maxSinhDelta);
        System.out.println("max cosh delta (relative): "+maxCoshDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, 0.0, 1e-10, 1e-5, 0.1, 0.5, 1.0, 1.5, -740.0, -745.13, -745.14, 705.0, 709.782, 709.783, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refSinh = Math.sinh(specialValues[i]);
            double refCosh = Math.cosh(specialValues[i]);
            FastMath.sinhAndCosh(specialValues[i],hsine,hcosine);
            double fastSinh = hsine.value;
            double fastCosh = hcosine.value;
            if (!Double.toString(refSinh).equals(Double.toString(fastSinh))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("                   Math.sinh("+specialValues[i]+")="+refSinh);
                System.out.println("FastMath.sinhAndCosh("+specialValues[i]+",,) hsine="+fastSinh);
            }
            if (!Double.toString(refCosh).equals(Double.toString(fastCosh))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("                     Math.cosh("+specialValues[i]+")="+refCosh);
                System.out.println("FastMath.sinhAndCosh("+specialValues[i]+",,) hcosine="+fastCosh);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_tanh_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing tanh(double) ---");

        // [-1,1]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tanh(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tanh(double), values in [-1,1], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tanh(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tanh(double), values in [-1,1], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tanh(valuesMinusOneOne[i]);
            double fastResult = FastMath.tanh(valuesMinusOneOne[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tanh(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tanh(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tanh(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tanh(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tanh(valuesMinusTenTen[i]);
            double fastResult = FastMath.tanh(valuesMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-700,700]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tanh(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tanh(double), values in [-700,700], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tanh(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tanh(double), values in [-700,700], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tanh(values1ForExp[i]);
            double fastResult = FastMath.tanh(values1ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-750,720]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tanh(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tanh(double), values in [-750,720], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tanh(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tanh(double), values in [-750,720], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tanh(values2ForExp[i]);
            double fastResult = FastMath.tanh(values2ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.tanh(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.tanh(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.tanh(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.tanh(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.tanh(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.tanh(valuesDoubleAllMagnitudes[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, 0.0, 1e-10, 1e-5, 0.1, 0.5, 1.0, 1.5, -740.0, -745.13, -745.14, 705.0, 709.782, 709.783, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.tanh(specialValues[i]);
            double fastResult = FastMath.tanh(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.tanh("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.tanh("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_exp_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing exp(double) ---");

        // [-1,1]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.exp(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.exp(double), values in [-1,1], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.exp(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.exp(double), values in [-1,1], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.exp(valuesMinusOneOne[i]);
            double fastResult = FastMath.exp(valuesMinusOneOne[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.exp(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.exp(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.exp(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.exp(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.exp(valuesMinusTenTen[i]);
            double fastResult = FastMath.exp(valuesMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-700,700]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.exp(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.exp(double), values in [-700,700], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.exp(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.exp(double), values in [-700,700], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.exp(values1ForExp[i]);
            double fastResult = FastMath.exp(values1ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-750,720]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.exp(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.exp(double), values in [-750,720], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.exp(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.exp(double), values in [-750,720], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.exp(values2ForExp[i]);
            double fastResult = FastMath.exp(values2ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.exp(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.exp(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.exp(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.exp(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.exp(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.exp(valuesDoubleAllMagnitudes[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, 0.0, 1e-5, 0.1, 0.5, 1.0, 1.5, -740.0, -745.13, -745.14, 705.0, 709.782, 709.783, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.exp(specialValues[i]);
            double fastResult = FastMath.exp(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.exp("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.exp("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_expQuick_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing expQuick(double) ---");

        // [-1,1]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.exp(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on          Math.exp(double), values in [-1,1], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.expQuick(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.expQuick(double), values in [-1,1], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.exp(valuesMinusOneOne[i]);
            double fastResult = FastMath.expQuick(valuesMinusOneOne[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.exp(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on          Math.exp(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.expQuick(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.expQuick(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.exp(valuesMinusTenTen[i]);
            double fastResult = FastMath.expQuick(valuesMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-700,700]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.exp(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on          Math.exp(double), values in [-700,700], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.expQuick(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.expQuick(double), values in [-700,700], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.exp(values1ForExp[i]);
            double fastResult = FastMath.expQuick(values1ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);
        
        useDummy(dummy);
    }

    private void test_expm1_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing expm1(double) ---");

        // [-1,1]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.expm1(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.expm1(double), values in [-1,1], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.expm1(valuesMinusOneOne[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.expm1(double), values in [-1,1], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.expm1(valuesMinusOneOne[i]);
            double fastResult = FastMath.expm1(valuesMinusOneOne[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.expm1(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.expm1(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.expm1(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.expm1(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.expm1(valuesMinusTenTen[i]);
            double fastResult = FastMath.expm1(valuesMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-700,700]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.expm1(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.expm1(double), values in [-700,700], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.expm1(values1ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.expm1(double), values in [-700,700], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.expm1(values1ForExp[i]);
            double fastResult = FastMath.expm1(values1ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-750,720]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.expm1(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.expm1(double), values in [-750,720], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.expm1(values2ForExp[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.expm1(double), values in [-750,720], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.expm1(values2ForExp[i]);
            double fastResult = FastMath.expm1(values2ForExp[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.expm1(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.expm1(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.expm1(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.expm1(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.expm1(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.expm1(valuesDoubleAllMagnitudes[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, 0.0, 1e-10, 1e-5, 0.1, 0.5, 1.0, 1.5, -740.0, -745.13, -745.14, 705.0, 709.782, 709.783, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.expm1(specialValues[i]);
            double fastResult = FastMath.expm1(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.expm1("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.expm1("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_log_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing log(double) ---");

        // [0.1,10.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.log(values1ForLog[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.log(double), values in [0.1,10.0], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.log(values1ForLog[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.log(double), values in [0.1,10.0], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.log(values1ForLog[i]);
            double fastResult = FastMath.log(values1ForLog[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.log(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.log(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.log(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.log(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.log(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.log(valuesDoubleAllMagnitudes[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -1.0, 0.0, 1.0, Math.E, Double.MIN_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.log(specialValues[i]);
            double fastResult = FastMath.log(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.log("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.log("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_logQuick_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing logQuick(double) ---");

        // [0.1,10.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.log(values1ForLog[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on          Math.log(double), values in [0.1,10.0], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.logQuick(values1ForLog[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.logQuick(double), values in [0.1,10.0], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.log(values1ForLog[i]);
            double fastResult = FastMath.logQuick(values1ForLog[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes (positive)

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.log(values2ForLog[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on          Math.log(double), values of all magnitudes (positive), took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.logQuick(values2ForLog[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.logQuick(double), values of all magnitudes (positive), took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.log(values2ForLog[i]);
            double fastResult = FastMath.logQuick(values2ForLog[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);
        
        useDummy(dummy);
    }

    private void test_log10_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing log10(double) ---");

        // [0.1,10.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.log10(values1ForLog[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.log10(double), values in [0.1,10.0], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.log10(values1ForLog[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.log10(double), values in [0.1,10.0], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.log10(values1ForLog[i]);
            double fastResult = FastMath.log10(values1ForLog[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.log10(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.log10(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.log10(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.log10(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.log10(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.log10(valuesDoubleAllMagnitudes[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -1.0, 0.0, 1.0, Math.E, Double.MIN_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.log10(specialValues[i]);
            double fastResult = FastMath.log10(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.log10("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.log10("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_log1p_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing log1p(double) ---");

        // [-0.9,9.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.log1p(valuesForLog1p[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.log1p(double), values in [-0.9,9.0], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.log1p(valuesForLog1p[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.log1p(double), values in [-0.9,9.0], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.log1p(valuesForLog1p[i]);
            double fastResult = FastMath.log1p(valuesForLog1p[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.log1p(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.log1p(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.log1p(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.log1p(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.log1p(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.log1p(valuesDoubleAllMagnitudes[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Math.E-1, Double.NaN, -2.0, -1.0, -1e-10, -1e-100, 0.0, 1e-100, 1e-10, 1.0, Double.MIN_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.log1p(specialValues[i]);
            double fastResult = FastMath.log1p(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.log1p("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.log1p("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_log2_int() {
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing log2(int) ---");

        // [0,Integer.MAX_VALUE]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.log2(valuesIntPositive[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.log2(int), values in [1,Integer.MAX_VALUE], took "+getElapsedSeconds()+" s... ");

        // special values

        int[] specialValues = new int[] { 1, 2, 3, 4, 7, 8, 15, 16, 31, 32, (1<<20)-1, (1<<20) };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            int refResult = (int)(Math.log((double)specialValues[i])/Math.log(2.0));
            int fastResult = FastMath.log2(specialValues[i]);
            if (!Integer.toString(refResult).equals(Integer.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("Math.log("+(double)specialValues[i]+")/Math.log(2.0)="+refResult);
                System.out.println("         FastMath.log2("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_log2_long() {
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing log2(long) ---");

        // [0,Long.MAX_VALUE]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.log2(valuesLongPositive[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.log2(long), values in [1,Long.MAX_VALUE], took "+getElapsedSeconds()+" s... ");

        // special values

        long[] specialValues = new long[] { 1, 2, 3, 4, 7, 8, 15, 16, 31, 32, (1L<<20)-1, (1L<<20) };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            int refResult = (int)(Math.log((double)specialValues[i])/Math.log(2.0));
            int fastResult = FastMath.log2(specialValues[i]);
            if (!Integer.toString(refResult).equals(Integer.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("Math.log("+(double)specialValues[i]+")/Math.log(2.0)="+refResult);
                System.out.println("         FastMath.log2("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_pow_2double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing pow(double,double) ---");

        // [-10,10],[0,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesMinusTenTen[j],valuesZeroTen[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.pow(double,double), values in {[-10,10],[0,10]}, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.pow(valuesMinusTenTen[j],valuesZeroTen[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.pow(double,double), values in {[-10,10],[0,10]}, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesMinusTenTen[i],valuesZeroTen[(NBR_OF_VALUES-1)-i]);
            double fastResult = FastMath.pow(valuesMinusTenTen[i],valuesZeroTen[(NBR_OF_VALUES-1)-i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-10,10],[-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesMinusTenTen[j],valuesMinusTenTen[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.pow(double,double), values in {[-10,10],[-10,10]}, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.pow(valuesMinusTenTen[j],valuesMinusTenTen[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.pow(double,double), values in {[-10,10],[-10,10]}, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesMinusTenTen[i],valuesMinusTenTen[(NBR_OF_VALUES-1)-i]);
            double fastResult = FastMath.pow(valuesMinusTenTen[i],valuesMinusTenTen[(NBR_OF_VALUES-1)-i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // {[-2^10,-2^-10],[2^-10,2^10]},[-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesA1ForPow[j],valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.pow(double,double), values in {[-2^10,2^10],[-10,10]}, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.pow(valuesA1ForPow[j],valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.pow(double,double), values in {[-2^10,2^10],[-10,10]}, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesA1ForPow[i],valuesMinusTenTen[i]);
            double fastResult = FastMath.pow(valuesA1ForPow[i],valuesMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-10,10],{[-2^10,-2^-10],[2^-10,2^10]}

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesMinusTenTen[j],valuesA1ForPow[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.pow(double,double), values in {[-10,10],[-2^10,2^10]}, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.pow(valuesMinusTenTen[j],valuesA1ForPow[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.pow(double,double), values in {[-10,10],[-2^10,2^10]}, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesMinusTenTen[i],valuesA1ForPow[i]);
            double fastResult = FastMath.pow(valuesMinusTenTen[i],valuesA1ForPow[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesDoubleAllMagnitudes[j],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.pow(double,double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.pow(valuesDoubleAllMagnitudes[j],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.pow(double,double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesDoubleAllMagnitudes[i],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-i]);
            double fastResult = FastMath.pow(valuesDoubleAllMagnitudes[i],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -3.0, -2.0, -1.1, -1.0, -0.9, -0.0, 0.0, 0.9, 1.0, 1.1, 2.0, 3.0, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            for (j=0;j<specialValues.length;j++) {
                double refResult = Math.pow(specialValues[i],specialValues[j]);
                double fastResult = FastMath.pow(specialValues[i],specialValues[j]);
                if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                    if (!foundDifferences) {
                        System.out.println("");
                        foundDifferences = true;
                    }
                    System.out.println("    Math.pow("+specialValues[i]+","+specialValues[j]+")="+refResult);
                    System.out.println("FastMath.pow("+specialValues[i]+","+specialValues[j]+")="+fastResult);
                }
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_powQuick_2double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing powQuick(double,double) ---");

        // [0.1,10],[-100,100]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(values1ForLog[j],valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on          Math.pow(double,double), values in {[0.1,10],[-100,100]}, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.powQuick(values1ForLog[j],valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.powQuick(double,double), values in {[0.1,10],[-100,100]}, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(values1ForLog[i],valuesMinusHundredHundred[i]);
            double fastResult = FastMath.powQuick(values1ForLog[i],valuesMinusHundredHundred[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // ]0,+infinity[,all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(values2ForLog[j],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on          Math.pow(double,double), values in {]0,+infinity[,all magnitudes}, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.powQuick(values2ForLog[j],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.powQuick(double,double), values in {]0,+infinity[,all magnitudes}, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(values2ForLog[i],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-i]);
            double fastResult = FastMath.powQuick(values2ForLog[i],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { 0.9, 1.0, 1.1, Double.MIN_VALUE, Double.MAX_VALUE };
        double[] specialPowers = new double[] { -1.0, 0.0, 1.0, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            for (j=0;j<specialPowers.length;j++) {
                double refResult = Math.pow(specialValues[i],specialPowers[j]);
                double fastResult = FastMath.powQuick(specialValues[i],specialPowers[j]);
                if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                    if (!foundDifferences) {
                        System.out.println("");
                        foundDifferences = true;
                    }
                    System.out.println("         Math.pow("+specialValues[i]+","+specialPowers[j]+")="+refResult);
                    System.out.println("FastMath.powQuick("+specialValues[i]+","+specialPowers[j]+")="+fastResult);
                }
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_powFast_double_int() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing powFast(double,int) ---");

        // [-10,10],[0,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesMinusTenTen[j],(double)valuesIntZeroTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on      Math.pow(double,double), values in ([-10,10],[0,10]), took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.powFast(valuesMinusTenTen[j],valuesIntZeroTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.powFast(double,int), values in ([-10,10],[0,10]), took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesMinusTenTen[i],(double)valuesIntZeroTenTen[i]);
            double fastResult = FastMath.powFast(valuesMinusTenTen[i],valuesIntZeroTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-10,10],[-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesMinusTenTen[j],(double)valuesIntMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on      Math.pow(double,double), values in ([-10,10],[-10,10]), took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.powFast(valuesMinusTenTen[j],valuesIntMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.powFast(double,int), values in ([-10,10],[-10,10]), took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesMinusTenTen[i],(double)valuesIntMinusTenTen[i]);
            double fastResult = FastMath.powFast(valuesMinusTenTen[i],valuesIntMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // {[-2^10,-2^-10],[2^-10,2^10]},[-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesA1ForPow[j],(double)valuesIntMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on      Math.pow(double,double), values in ({[-2^10,-2^-10],[2^-10,2^10]},[-10,10]), took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.powFast(valuesA1ForPow[j],valuesIntMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.powFast(double,int), values in ({[-2^10,-2^-10],[2^-10,2^10]},[-10,10]), took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesA1ForPow[i],(double)valuesIntMinusTenTen[i]);
            double fastResult = FastMath.powFast(valuesA1ForPow[i],valuesIntMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // {[-2^100,-2^-100],[2^-100,2^100]},[-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesA2ForPow[j],(double)valuesIntMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on      Math.pow(double,double), values in ({[-2^100,-2^-100],[2^-100,2^100]},[-10,10]), took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.powFast(valuesA2ForPow[j],valuesIntMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.powFast(double,int), values in ({[-2^100,-2^-100],[2^-100,2^100]},[-10,10]), took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesA2ForPow[i],(double)valuesIntMinusTenTen[i]);
            double fastResult = FastMath.powFast(valuesA2ForPow[i],valuesIntMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // {[-2^10,-2^-10],[2^-10,2^10]},[-100,100]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(valuesA1ForPow[j],(double)valuesIntMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on      Math.pow(double,double), values in ({[-2^10,-2^-10],[2^-10,2^10]},[-100,100]), took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.powFast(valuesA1ForPow[j],valuesIntMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.powFast(double,int), values in ({[-2^10,-2^-10],[2^-10,2^10]},[-100,100]), took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.pow(valuesA1ForPow[i],(double)valuesIntMinusHundredHundred[i]);
            double fastResult = FastMath.powFast(valuesA1ForPow[i],valuesIntMinusHundredHundred[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -1.0, 0.0, 1.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        int[] specialValuesInt = new int[] { -1, 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            for (j=0;j<specialValuesInt.length;j++) {
                double refResult = Math.pow(specialValues[i],(double)specialValuesInt[j]);
                double fastResult = FastMath.powFast(specialValues[i],specialValuesInt[j]);
                if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                    if (!foundDifferences) {
                        System.out.println("");
                        foundDifferences = true;
                    }
                    System.out.println("        Math.pow("+specialValues[i]+","+specialValuesInt[j]+")="+refResult);
                    System.out.println("FastMath.powFast("+specialValues[i]+","+specialValuesInt[j]+")="+fastResult);
                }
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_twoPow_int() {
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing twoPow(int) ---");

        // [-1074,1023]

        j=-1074;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(2.0,(double)j);
            j = (j<1023) ? j+1 : -1074;
        }
        System.out.println("Loop on Math.pow(2.0,double), values in [-1074,1023], took "+getElapsedSeconds()+" s");

        j=-1074;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.twoPow(j);
            j = (j<1023) ? j+1 : -1074;
        }
        System.out.println("Loop on FastMath.twoPow(int), values in [-1074,1023], took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.pow(2.0,(double)valuesIntAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on Math.pow(2.0,double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.twoPow(valuesIntAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.twoPow(int), values of all magnitudes, took "+getElapsedSeconds()+" s");

        // special values

        int[] specialValues = new int[] { -1075, -1074, -1023, -1022, -1, 0, 1, 1023, 1024, Integer.MIN_VALUE, Integer.MAX_VALUE };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.pow(2.0,(double)specialValues[i]);
            double fastResult = FastMath.twoPow(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("Math.pow(2.0,(double)"+specialValues[i]+")="+refResult);
                System.out.println("     FastMath.twoPow("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_sqrt_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing sqrt(double) ---");

        // [0,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sqrt(Math.abs(valuesMinusTenTen[j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sqrt(double), values in [0,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sqrt(Math.abs(valuesMinusTenTen[j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sqrt(double), values in [0,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sqrt(Math.abs(valuesMinusTenTen[i]));
            double fastResult = FastMath.sqrt(Math.abs(valuesMinusTenTen[i]));
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [0,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sqrt(Math.abs(valuesMinusABigLotABigLot[j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sqrt(double), values in [0,"+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sqrt(Math.abs(valuesMinusABigLotABigLot[j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sqrt(double), values in [0,"+A_BIG_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sqrt(Math.abs(valuesMinusABigLotABigLot[i]));
            double fastResult = FastMath.sqrt(Math.abs(valuesMinusABigLotABigLot[i]));
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.sqrt(Math.abs(valuesDoubleAllMagnitudes[j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.sqrt(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.sqrt(Math.abs(valuesDoubleAllMagnitudes[j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.sqrt(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.sqrt(Math.abs(valuesDoubleAllMagnitudes[i]));
            double fastResult = FastMath.sqrt(Math.abs(valuesDoubleAllMagnitudes[i]));
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -16.0, -1.0, -0.0, 0.0, 1.0, 2.0, 16.0, Double.MIN_VALUE, Double.MAX_VALUE, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.sqrt(specialValues[i]);
            double fastResult = FastMath.sqrt(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.sqrt("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.sqrt("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_cbrt_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing cbrt(double) ---");

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cbrt(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cbrt(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cbrt(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cbrt(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cbrt(valuesMinusTenTen[i]);
            double fastResult = FastMath.cbrt(valuesMinusTenTen[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cbrt(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cbrt(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cbrt(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cbrt(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cbrt(valuesMinusABigLotABigLot[i]);
            double fastResult = FastMath.cbrt(valuesMinusABigLotABigLot[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.cbrt(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.cbrt(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.cbrt(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.cbrt(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.cbrt(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.cbrt(valuesDoubleAllMagnitudes[i]);
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -27.0, -1.0, -0.0, 0.0, 1.0, 2.0, 27.0, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.cbrt(specialValues[i]);
            double fastResult = FastMath.cbrt(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.cbrt("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.cbrt("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_remainder_2double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing remainder(double,double) ---");

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.IEEEremainder(valuesMinusTenTen[j],valuesMinusTenTen[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on Math.IEEEremainder(double,double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.remainder(valuesMinusTenTen[j],valuesMinusTenTen[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.remainder(double,double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.IEEEremainder(valuesMinusTenTen[i],valuesMinusTenTen[(NBR_OF_VALUES-1)-i]);
            double fastResult = FastMath.remainder(valuesMinusTenTen[i],valuesMinusTenTen[(NBR_OF_VALUES-1)-i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.IEEEremainder(valuesMinusALittleLotALittleLot[j],valuesMinusALittleLotALittleLot[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on Math.IEEEremainder(double,double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.remainder(valuesMinusALittleLotALittleLot[j],valuesMinusALittleLotALittleLot[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.remainder(double,double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.IEEEremainder(valuesMinusALittleLotALittleLot[i],valuesMinusALittleLotALittleLot[(NBR_OF_VALUES-1)-i]);
            double fastResult = FastMath.remainder(valuesMinusALittleLotALittleLot[i],valuesMinusALittleLotALittleLot[(NBR_OF_VALUES-1)-i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.IEEEremainder(valuesMinusABigLotABigLot[j],valuesMinusABigLotABigLot[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on Math.IEEEremainder(double,double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.remainder(valuesMinusABigLotABigLot[j],valuesMinusABigLotABigLot[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.remainder(double,double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.IEEEremainder(valuesMinusABigLotABigLot[i],valuesMinusABigLotABigLot[(NBR_OF_VALUES-1)-i]);
            double fastResult = FastMath.remainder(valuesMinusABigLotABigLot[i],valuesMinusABigLotABigLot[(NBR_OF_VALUES-1)-i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS/100;i++) {
            dummy += Math.IEEEremainder(valuesDoubleAllMagnitudes[j],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop(/100) on Math.IEEEremainder(double,double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS/100;i++) {
            dummy += FastMath.remainder(valuesDoubleAllMagnitudes[j],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop(/100) on FastMath.remainder(double,double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.IEEEremainder(valuesDoubleAllMagnitudes[i],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-i]);
            double fastResult = FastMath.remainder(valuesDoubleAllMagnitudes[i],valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -0.0, 0.0, -1.0, 1.0, -2.0, 2.0, -3.0, 3.0, -5.0, 5.0, -Double.MIN_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            for (j=0;j<specialValues.length;j++) {
                double refResult = Math.IEEEremainder(specialValues[i],specialValues[j]);
                double fastResult = FastMath.remainder(specialValues[i],specialValues[j]);
                if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                    if (!foundDifferences) {
                        System.out.println("");
                        foundDifferences = true;
                    }
                    System.out.println("Math.IEEEremainder("+specialValues[i]+","+specialValues[j]+")="+refResult);
                    System.out.println("FastMath.remainder("+specialValues[i]+","+specialValues[j]+")="+fastResult);
                }
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_normalizeMinusPiPi() {
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing normalizeMinusPiPi(double) ---");

        // [-PI,PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPi(anglesMinusPiPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusPiPi(double), values in [-PI,PI], took "+getElapsedSeconds()+" s");

        // [-2*PI,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPi(anglesMinusTwoPiTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusPiPi(double), values in [-2*PI,2*PI], took "+getElapsedSeconds()+" s");

        // [-100.0,100.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPi(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusPiPi(double), values in [-100.0,100.0], took "+getElapsedSeconds()+" s");

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPi(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusPiPi(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPi(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusPiPi(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        // special values

        System.out.print("Testing special values... ");

        double[][] specialValues = new double[][] {
                // value, expected result
                {-Math.PI,-Math.PI},
                {Math.nextAfter(-Math.PI,Double.POSITIVE_INFINITY),Math.nextAfter(-Math.PI,Double.POSITIVE_INFINITY)},
                {0.1*Math.PI,0.1*Math.PI},
                {Math.PI,Math.PI},
                {Math.nextAfter(Math.PI,Double.NEGATIVE_INFINITY),Math.nextAfter(Math.PI,Double.NEGATIVE_INFINITY)}
        };
        boolean errorFound = false;
        for (i=0;i<specialValues.length;i++) {
            double value = specialValues[i][0];
            double expectedResult = specialValues[i][1];
            double result = FastMath.normalizeMinusPiPi(value);
            if (result != value) {
                if (!errorFound) {
                    System.out.println("");
                    errorFound = true;
                }
                System.out.println("Error: FastMath.normalizeMinusPiPi("+value+") returns "+result+" instead of "+expectedResult);
            }
        }
        // [-PI,PI] (must not change)
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesMinusPiPi[i];
            double result = FastMath.normalizeMinusPiPi(value);
            if (result != value) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeMinusPiPi("+value+") returns "+result+" instead of "+value);
                break;
            }
        }
        // around +-PI modulo 2*PI (must not be outside [-PI,PI])
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesNearPiModTwoPi[i];
            double result = FastMath.normalizeMinusPiPi(value);
            if ((result < -Math.PI) || (result > Math.PI)) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeMinusPiPi("+value+") returns "+result+", which is outside [-PI,PI]");
                break;
            }
        }
        if (!errorFound) {
            System.out.println("ok.");
        }
        
        useDummy(dummy);
    }

    private void test_normalizeMinusPiPiFast() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing normalizeMinusPiPiFast(double) ---");

        // [-PI,PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPiFast(anglesMinusPiPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeMinusPiPiFast(double), values in [-PI,PI], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeMinusPiPi(anglesMinusPiPi[i]);
            double fastResult = FastMath.normalizeMinusPiPiFast(anglesMinusPiPi[i]);
            double delta = absDeltaMod(fastResult,refResult,2*Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-2*PI,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPiFast(anglesMinusTwoPiTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeMinusPiPiFast(double), values in [-2*PI,2*PI], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeMinusPiPi(anglesMinusTwoPiTwoPi[i]);
            double fastResult = FastMath.normalizeMinusPiPiFast(anglesMinusTwoPiTwoPi[i]);
            double delta = absDeltaMod(fastResult,refResult,2*Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-100.0,100.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPiFast(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeMinusPiPiFast(double), values in [-100.0,100.0], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeMinusPiPi(valuesMinusHundredHundred[i]);
            double fastResult = FastMath.normalizeMinusPiPiFast(valuesMinusHundredHundred[i]);
            double delta = absDeltaMod(fastResult,refResult,2*Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPiFast(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeMinusPiPiFast(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeMinusPiPi(valuesMinusABigLotABigLot[i]);
            double fastResult = FastMath.normalizeMinusPiPiFast(valuesMinusABigLotABigLot[i]);
            double delta = absDeltaMod(fastResult,refResult,2*Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusPiPiFast(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusPiPiFast(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        // special values

        System.out.print("Testing special values... ");

        double[][] specialValues = new double[][] {
                // value, expected result
                {-Math.PI,-Math.PI},
                {Math.nextAfter(-Math.PI,Double.POSITIVE_INFINITY),Math.nextAfter(-Math.PI,Double.POSITIVE_INFINITY)},
                {0.1*Math.PI,0.1*Math.PI},
                {Math.PI,Math.PI},
                {Math.nextAfter(Math.PI,Double.NEGATIVE_INFINITY),Math.nextAfter(Math.PI,Double.NEGATIVE_INFINITY)}
        };
        boolean errorFound = false;
        for (i=0;i<specialValues.length;i++) {
            double value = specialValues[i][0];
            double expectedResult = specialValues[i][1];
            double result = FastMath.normalizeMinusPiPiFast(value);
            if (result != value) {
                if (!errorFound) {
                    System.out.println("");
                    errorFound = true;
                }
                System.out.println("Error: FastMath.normalizeMinusPiPiFast("+value+") returns "+result+" instead of "+expectedResult);
            }
        }
        // [-PI,PI] (must not change)
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesMinusPiPi[i];
            double result = FastMath.normalizeMinusPiPiFast(value);
            if (result != value) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeMinusPiPiFast("+value+") returns "+result+" instead of "+value);
                break;
            }
        }
        // around +-PI modulo 2*PI (must not be outside [-PI,PI])
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesNearPiModTwoPi[i];
            double result = FastMath.normalizeMinusPiPiFast(value);
            if ((result < -Math.PI) || (result > Math.PI)) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeMinusPiPiFast("+value+") returns "+result+", which is outside [-PI,PI]");
                break;
            }
        }
        if (!errorFound) {
            System.out.println("ok.");
        }
        
        useDummy(dummy);
    }

    private void test_normalizeZeroTwoPi() {
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing normalizeZeroTwoPi(double) ---");

        // [0,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPi(anglesZeroTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeZeroTwoPi(double), values in [0,2*PI], took "+getElapsedSeconds()+" s");

        // [-2*PI,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPi(anglesMinusTwoPiTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeZeroTwoPi(double), values in [-2*PI,2*PI], took "+getElapsedSeconds()+" s");

        // [-100.0,100.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPi(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeZeroTwoPi(double), values in [-100.0,100.0], took "+getElapsedSeconds()+" s");

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPi(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeZeroTwoPi(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPi(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeZeroTwoPi(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        // special values

        System.out.print("Testing special values... ");

        double[][] specialValues = new double[][] {
                // value, expected result
                {0.0,0.0},
                {Math.nextAfter(0.0,Double.POSITIVE_INFINITY),Math.nextAfter(0.0,Double.POSITIVE_INFINITY)},
                {1.1*Math.PI,1.1*Math.PI},
                {2*Math.PI,2*Math.PI},
                {Math.nextAfter(2*Math.PI,Double.NEGATIVE_INFINITY),Math.nextAfter(2*Math.PI,Double.NEGATIVE_INFINITY)}
        };
        boolean errorFound = false;
        for (i=0;i<specialValues.length;i++) {
            double value = specialValues[i][0];
            double expectedResult = specialValues[i][1];
            double result = FastMath.normalizeZeroTwoPi(value);
            if (result != value) {
                if (!errorFound) {
                    System.out.println("");
                    errorFound = true;
                }
                System.out.println("Error: FastMath.normalizeZeroTwoPi("+value+") returns "+result+" instead of "+expectedResult);
            }
        }
        // [0,2*PI] (must not change)
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesZeroTwoPi[i];
            double result = FastMath.normalizeZeroTwoPi(value);
            if (result != value) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeZeroTwoPi("+value+") returns "+result+" instead of "+value);
                break;
            }
        }
        // around +-2*PI modulo 2*PI (must not be outside [0,2*PI])
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesNearTwoPiModTwoPi[i];
            double result = FastMath.normalizeZeroTwoPi(value);
            if ((result < 0.0) || (result > 2*Math.PI)) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeZeroTwoPi("+value+") returns "+result+", which is outside [0,2*PI]");
                break;
            }
        }
        if (!errorFound) {
            System.out.println("ok.");
        }
        
        useDummy(dummy);
    }

    private void test_normalizeZeroTwoPiFast() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing normalizeZeroTwoPiFast(double) ---");

        // [0,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPiFast(anglesZeroTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeZeroTwoPiFast(double), values in [0,2*PI], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeZeroTwoPi(anglesZeroTwoPi[i]);
            double fastResult = FastMath.normalizeZeroTwoPiFast(anglesZeroTwoPi[i]);
            double delta = absDeltaMod(fastResult,refResult,2*Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-2*PI,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPiFast(anglesMinusTwoPiTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeZeroTwoPiFast(double), values in [-2*PI,2*PI], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeZeroTwoPi(anglesMinusTwoPiTwoPi[i]);
            double fastResult = FastMath.normalizeZeroTwoPiFast(anglesMinusTwoPiTwoPi[i]);
            double delta = absDeltaMod(fastResult,refResult,2*Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-100.0,100.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPiFast(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeZeroTwoPiFast(double), values in [-100.0,100.0], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeZeroTwoPi(valuesMinusHundredHundred[i]);
            double fastResult = FastMath.normalizeZeroTwoPiFast(valuesMinusHundredHundred[i]);
            double delta = absDeltaMod(fastResult,refResult,2*Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPiFast(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeZeroTwoPiFast(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeZeroTwoPi(valuesMinusABigLotABigLot[i]);
            double fastResult = FastMath.normalizeZeroTwoPiFast(valuesMinusABigLotABigLot[i]);
            double delta = absDeltaMod(fastResult,refResult,2*Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeZeroTwoPiFast(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeZeroTwoPiFast(double), values of all magnitudes, took "+getElapsedSeconds()+" s");


        System.out.print("Testing special values... ");

        double[][] specialValues = new double[][] {
                // value, expected result
                {0.0,0.0},
                {Math.nextAfter(0.0,Double.POSITIVE_INFINITY),Math.nextAfter(0.0,Double.POSITIVE_INFINITY)},
                {1.1*Math.PI,1.1*Math.PI},
                {2*Math.PI,2*Math.PI},
                {Math.nextAfter(2*Math.PI,Double.NEGATIVE_INFINITY),Math.nextAfter(2*Math.PI,Double.NEGATIVE_INFINITY)}
        };
        boolean errorFound = false;
        for (i=0;i<specialValues.length;i++) {
            double value = specialValues[i][0];
            double expectedResult = specialValues[i][1];
            double result = FastMath.normalizeZeroTwoPiFast(value);
            if (result != value) {
                if (!errorFound) {
                    System.out.println("");
                    errorFound = true;
                }
                System.out.println("Error: FastMath.normalizeZeroTwoPiFast("+value+") returns "+result+" instead of "+expectedResult);
            }
        }
        // [0,2*PI] (must not change)
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesZeroTwoPi[i];
            double result = FastMath.normalizeZeroTwoPiFast(value);
            if (result != value) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeZeroTwoPiFast("+value+") returns "+result+" instead of "+value);
                break;
            }
        }
        // around +-2*PI modulo 2*PI (must not be outside [0,2*PI])
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesNearTwoPiModTwoPi[i];
            double result = FastMath.normalizeZeroTwoPiFast(value);
            if ((result < 0.0) || (result > 2*Math.PI)) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeZeroTwoPiFast("+value+") returns "+result+", which is outside [0,2*PI]");
                break;
            }
        }
        if (!errorFound) {
            System.out.println("ok.");
        }
        
        useDummy(dummy);
    }

    private void test_normalizeMinusHalfPiHalfPi() {
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing normalizeMinusHalfPiHalfPi(double) ---");

        // [-PI/2,PI/2]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPi(anglesMinusHalfPiHalfPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusHalfPiHalfPi(double), values in [-PI/2,PI/2], took "+getElapsedSeconds()+" s");

        // [-2*PI,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPi(anglesMinusTwoPiTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusHalfPiHalfPi(double), values in [-2*PI,2*PI], took "+getElapsedSeconds()+" s");

        // [-100.0,100.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPi(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusHalfPiHalfPi(double), values in [-100.0,100.0], took "+getElapsedSeconds()+" s");

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPi(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusHalfPiHalfPi(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPi(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusHalfPiHalfPi(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        // special values

        System.out.print("Testing special values... ");

        double[][] specialValues = new double[][] {
                // value, expected result
                {-Math.PI/2,-Math.PI/2},
                {Math.nextAfter(-Math.PI/2,Double.POSITIVE_INFINITY),Math.nextAfter(-Math.PI/2,Double.POSITIVE_INFINITY)},
                {0.1*Math.PI,0.1*Math.PI},
                {Math.PI/2,Math.PI/2},
                {Math.nextAfter(Math.PI/2,Double.NEGATIVE_INFINITY),Math.nextAfter(Math.PI/2,Double.NEGATIVE_INFINITY)}
        };
        boolean errorFound = false;
        for (i=0;i<specialValues.length;i++) {
            double value = specialValues[i][0];
            double expectedResult = specialValues[i][1];
            double result = FastMath.normalizeMinusHalfPiHalfPi(value);
            if (result != value) {
                if (!errorFound) {
                    System.out.println("");
                    errorFound = true;
                }
                System.out.println("Error: FastMath.normalizeMinusHalfPiHalfPi("+value+") returns "+result+" instead of "+expectedResult);
            }
        }
        // [-PI/2,PI/2] (must not change)
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesMinusHalfPiHalfPi[i];
            double result = FastMath.normalizeMinusHalfPiHalfPi(value);
            if (result != value) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeMinusHalfPiHalfPi("+value+") returns "+result+" instead of "+value);
                break;
            }
        }
        // around +-PI/2 modulo PI (must not be outside [-PI/2,PI/2])
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesNearHalfPiModPi[i];
            double result = FastMath.normalizeMinusHalfPiHalfPi(value);
            if ((result < -Math.PI/2) || (result > Math.PI/2)) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeMinusHalfPiHalfPi("+value+") returns "+result+", which is outside [-PI/2,PI/2]");
                break;
            }
        }
        if (!errorFound) {
            System.out.println("ok.");
        }
        
        useDummy(dummy);
    }

    private void test_normalizeMinusHalfPiHalfPiFast() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing normalizeMinusHalfPiHalfPiFast(double) ---");

        // [-PI/2,PI/2]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPiFast(anglesMinusHalfPiHalfPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeMinusHalfPiHalfPiFast(double), values in [-PI/2,PI/2], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeMinusHalfPiHalfPi(anglesMinusHalfPiHalfPi[i]);
            double fastResult = FastMath.normalizeMinusHalfPiHalfPiFast(anglesMinusHalfPiHalfPi[i]);
            double delta = absDeltaMod(fastResult,refResult,Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-2*PI,2*PI]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPiFast(anglesMinusTwoPiTwoPi[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeMinusHalfPiHalfPiFast(double), values in [-2*PI,2*PI], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeMinusHalfPiHalfPi(anglesMinusTwoPiTwoPi[i]);
            double fastResult = FastMath.normalizeMinusHalfPiHalfPiFast(anglesMinusTwoPiTwoPi[i]);
            double delta = absDeltaMod(fastResult,refResult,Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-100.0,100.0]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPiFast(valuesMinusHundredHundred[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeMinusHalfPiHalfPiFast(double), values in [-100.0,100.0], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeMinusHalfPiHalfPi(valuesMinusHundredHundred[i]);
            double fastResult = FastMath.normalizeMinusHalfPiHalfPiFast(valuesMinusHundredHundred[i]);
            double delta = absDeltaMod(fastResult,refResult,Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_BIG_LOT,A_BIG_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPiFast(valuesMinusABigLotABigLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.normalizeMinusHalfPiHalfPiFast(double), values in ["+(-A_BIG_LOT)+","+A_BIG_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = FastMath.normalizeMinusHalfPiHalfPi(valuesMinusABigLotABigLot[i]);
            double fastResult = FastMath.normalizeMinusHalfPiHalfPiFast(valuesMinusABigLotABigLot[i]);
            double delta = absDeltaMod(fastResult,refResult,Math.PI);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.normalizeMinusHalfPiHalfPiFast(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.normalizeMinusHalfPiHalfPiFast(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        // special values

        System.out.print("Testing special values... ");

        double[][] specialValues = new double[][] {
                // value, expected result
                {-Math.PI/2,-Math.PI/2},
                {Math.nextAfter(-Math.PI/2,Double.POSITIVE_INFINITY),Math.nextAfter(-Math.PI/2,Double.POSITIVE_INFINITY)},
                {0.1*Math.PI,0.1*Math.PI},
                {Math.PI/2,Math.PI/2},
                {Math.nextAfter(Math.PI/2,Double.NEGATIVE_INFINITY),Math.nextAfter(Math.PI/2,Double.NEGATIVE_INFINITY)}
        };
        boolean errorFound = false;
        for (i=0;i<specialValues.length;i++) {
            double value = specialValues[i][0];
            double expectedResult = specialValues[i][1];
            double result = FastMath.normalizeMinusHalfPiHalfPiFast(value);
            if (result != value) {
                if (!errorFound) {
                    System.out.println("");
                    errorFound = true;
                }
                System.out.println("Error: FastMath.normalizeMinusHalfPiHalfPiFast("+value+") returns "+result+" instead of "+expectedResult);
            }
        }
        // [-PI/2,PI/2] (must not change)
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesMinusHalfPiHalfPi[i];
            double result = FastMath.normalizeMinusHalfPiHalfPiFast(value);
            if (result != value) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeMinusHalfPiHalfPiFast("+value+") returns "+result+" instead of "+value);
                break;
            }
        }
        // around +-PI/2 modulo PI (must not be outside [-PI/2,PI/2])
        for (i=0;i<NBR_OF_VALUES;i++) {
            double value = anglesNearHalfPiModPi[i];
            double result = FastMath.normalizeMinusHalfPiHalfPiFast(value);
            if ((result < -Math.PI/2) || (result > Math.PI/2)) {
                errorFound = true;
                System.out.println("");
                System.out.println("Error: FastMath.normalizeMinusHalfPiHalfPiFast("+value+") returns "+result+", which is outside [-PI/2,PI/2]");
                break;
            }
        }
        if (!errorFound) {
            System.out.println("ok.");
        }
        
        useDummy(dummy);
    }

    private void test_abs_int() {
        int i;
        int j;
        int dummy = 0;

        System.out.println("--- testing abs(int) ---");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.abs(valuesIntAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.abs(int), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.abs(valuesIntAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.abs(int), values of all magnitudes, took "+getElapsedSeconds()+" s");

        // special values

        int[] specialValues = new int[] { -1, 0, 1, Integer.MIN_VALUE, Integer.MIN_VALUE+1, Integer.MAX_VALUE, Integer.MAX_VALUE-1 };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            int refResult = Math.abs(specialValues[i]);
            int fastResult = FastMath.abs(specialValues[i]);
            if (!Integer.toString(refResult).equals(Integer.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.abs("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.abs("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }
    
    private void test_ceil_float() {
        float maxDelta;
        int i;
        int j;
        float dummy = 0.0f;

        System.out.println("--- testing ceil(float) ---");

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.ceil((double)(float)valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.ceil(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil((float)valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(float), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0f;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.ceil((double)(float)valuesMinusTenTen[i]);
            float fastResult = FastMath.ceil((float)valuesMinusTenTen[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.ceil((double)(float)valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.ceil(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil((float)valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(float), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0f;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.ceil((double)(float)valuesMinusALittleLotALittleLot[i]);
            float fastResult = FastMath.ceil((float)valuesMinusALittleLotALittleLot[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-FLOAT_COMMA_LIMIT,FLOAT_COMMA_LIMIT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.ceil((double)(float)valuesFloatCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.ceil(double), values in ["+(-FLOAT_COMMA_LIMIT)+","+FLOAT_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil((float)valuesFloatCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(float), values in ["+(-FLOAT_COMMA_LIMIT)+","+FLOAT_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.ceil((double)(float)valuesFloatCommaLimit[i]);
            float fastResult = FastMath.ceil((float)valuesFloatCommaLimit[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // near integers

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.ceil((double)valuesFloatNearIntegers[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.ceil(double), values near integers, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil(valuesFloatNearIntegers[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(float), values near integers, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.ceil((double)valuesFloatNearIntegers[i]);
            float fastResult = FastMath.ceil(valuesFloatNearIntegers[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.ceil((double)valuesFloatAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.ceil(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil(valuesFloatAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(float), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.ceil((double)valuesFloatAllMagnitudes[i]);
            float fastResult = FastMath.ceil(valuesFloatAllMagnitudes[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        float[] specialValues = new float[] {
                Float.NaN,
                -10.0f, -NEXT_F_BEFORE_TEN, -NEXT_F_AFTER_TEN,
                -1.0f, -NEXT_F_BEFORE_ONE, -NEXT_F_AFTER_ONE,
                -0.5f, -0.0f, 0.0f, 0.5f,
                1.0f, NEXT_F_BEFORE_ONE, NEXT_F_AFTER_ONE,
                10.0f, NEXT_F_BEFORE_TEN, NEXT_F_AFTER_TEN,
                Integer.MIN_VALUE, -Integer.MAX_VALUE, Integer.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            float refResult = (float)Math.ceil((double)specialValues[i]);
            float fastResult = FastMath.ceil(specialValues[i]);
            if (!Float.toString(refResult).equals(Float.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.ceil("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.ceil("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_ceil_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing ceil(double) ---");

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.ceil(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.ceil(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.ceil(valuesMinusTenTen[i]);
            double fastResult = FastMath.ceil(valuesMinusTenTen[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.ceil(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.ceil(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.ceil(valuesMinusALittleLotALittleLot[i]);
            double fastResult = FastMath.ceil(valuesMinusALittleLotALittleLot[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-DOUBLE_COMMA_LIMIT,DOUBLE_COMMA_LIMIT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.ceil(valuesDoubleCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.ceil(double), values in ["+(-DOUBLE_COMMA_LIMIT)+","+DOUBLE_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil(valuesDoubleCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(double), values in ["+(-DOUBLE_COMMA_LIMIT)+","+DOUBLE_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.ceil(valuesDoubleCommaLimit[i]);
            double fastResult = FastMath.ceil(valuesDoubleCommaLimit[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // near integers

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.ceil(valuesDoubleNearIntegers[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.ceil(double), values near integers, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil(valuesDoubleNearIntegers[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(double), values near integers, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.ceil(valuesDoubleNearIntegers[i]);
            double fastResult = FastMath.ceil(valuesDoubleNearIntegers[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.ceil(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.ceil(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.ceil(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.ceil(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.ceil(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.ceil(valuesDoubleAllMagnitudes[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] {
                Double.NaN,
                -10.0, -NEXT_D_BEFORE_TEN, -NEXT_D_AFTER_TEN,
                -1.0, -NEXT_D_BEFORE_ONE, -NEXT_D_AFTER_ONE,
                -0.5, -0.0, 0.0, 0.5,
                1.0, NEXT_D_BEFORE_ONE, NEXT_D_AFTER_ONE,
                10.0, NEXT_D_BEFORE_TEN, NEXT_D_AFTER_TEN,
                Integer.MIN_VALUE, -Integer.MAX_VALUE, Integer.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.ceil(specialValues[i]);
            double fastResult = FastMath.ceil(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.ceil("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.ceil("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_floor_float() {
        float maxDelta;
        int i;
        int j;
        float dummy = 0.0f;

        System.out.println("--- testing floor(float) ---");

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.floor((double)(float)valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.floor(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor((float)valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(float), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0f;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.floor((double)(float)valuesMinusTenTen[i]);
            float fastResult = FastMath.floor((float)valuesMinusTenTen[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.floor((double)(float)valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.floor(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor((float)valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(float), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0f;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.floor((double)(float)valuesMinusALittleLotALittleLot[i]);
            float fastResult = FastMath.floor((float)valuesMinusALittleLotALittleLot[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-FLOAT_COMMA_LIMIT,FLOAT_COMMA_LIMIT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.floor((double)(float)valuesFloatCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.floor(double), values in ["+(-FLOAT_COMMA_LIMIT)+","+FLOAT_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor((float)valuesFloatCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(float), values in ["+(-FLOAT_COMMA_LIMIT)+","+FLOAT_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.floor((double)(float)valuesFloatCommaLimit[i]);
            float fastResult = FastMath.floor((float)valuesFloatCommaLimit[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // near integers

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.floor((double)valuesFloatNearIntegers[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.floor(double), values near integers, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor(valuesFloatNearIntegers[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(float), values near integers, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.floor((double)valuesFloatNearIntegers[i]);
            float fastResult = FastMath.floor(valuesFloatNearIntegers[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += (float)Math.floor((double)valuesFloatAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    Math.floor(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor(valuesFloatAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(float), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            float refResult = (float)Math.floor((double)valuesFloatAllMagnitudes[i]);
            float fastResult = FastMath.floor(valuesFloatAllMagnitudes[i]);
            float delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        float[] specialValues = new float[] {
                Float.NaN,
                -10.0f, -NEXT_F_BEFORE_TEN, -NEXT_F_AFTER_TEN,
                -1.0f, -NEXT_F_BEFORE_ONE, -NEXT_F_AFTER_ONE,
                -0.5f, -0.0f, 0.0f, 0.5f,
                1.0f, NEXT_F_BEFORE_ONE, NEXT_F_AFTER_ONE,
                10.0f, NEXT_F_BEFORE_TEN, NEXT_F_AFTER_TEN,
                Integer.MIN_VALUE, -Integer.MAX_VALUE, Integer.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            float refResult = (float)Math.floor((double)specialValues[i]);
            float fastResult = FastMath.floor(specialValues[i]);
            if (!Float.toString(refResult).equals(Float.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.floor("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.floor("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_floor_double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing floor(double) ---");

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.floor(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.floor(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.floor(valuesMinusTenTen[i]);
            double fastResult = FastMath.floor(valuesMinusTenTen[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.floor(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.floor(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.floor(valuesMinusALittleLotALittleLot[i]);
            double fastResult = FastMath.floor(valuesMinusALittleLotALittleLot[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-DOUBLE_COMMA_LIMIT,DOUBLE_COMMA_LIMIT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.floor(valuesDoubleCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.floor(double), values in ["+(-DOUBLE_COMMA_LIMIT)+","+DOUBLE_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor(valuesDoubleCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(double), values in ["+(-DOUBLE_COMMA_LIMIT)+","+DOUBLE_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.floor(valuesDoubleCommaLimit[i]);
            double fastResult = FastMath.floor(valuesDoubleCommaLimit[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // near integers

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.floor(valuesDoubleNearIntegers[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.floor(double), values near integers, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor(valuesDoubleNearIntegers[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(double), values near integers, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.floor(valuesDoubleNearIntegers[i]);
            double fastResult = FastMath.floor(valuesDoubleNearIntegers[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.floor(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.floor(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.floor(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.floor(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.floor(valuesDoubleAllMagnitudes[i]);
            double fastResult = FastMath.floor(valuesDoubleAllMagnitudes[i]);
            double delta = absDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] {
                Double.NaN,
                -10.0, -NEXT_D_BEFORE_TEN, -NEXT_D_AFTER_TEN,
                -1.0, -NEXT_D_BEFORE_ONE, -NEXT_D_AFTER_ONE,
                -0.5, -0.0, 0.0, 0.5,
                1.0, NEXT_D_BEFORE_ONE, NEXT_D_AFTER_ONE,
                10.0, NEXT_D_BEFORE_TEN, NEXT_D_AFTER_TEN,
                Integer.MIN_VALUE, -Integer.MAX_VALUE, Integer.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            double refResult = Math.floor(specialValues[i]);
            double fastResult = FastMath.floor(specialValues[i]);
            if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.floor("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.floor("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_round_float() {
        int maxDelta;
        int i;
        int j;
        int dummy = 0;

        System.out.println("--- testing round(float) ---");

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.round((float)valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.round(float), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.round((float)valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.round(float), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            int delta = Math.abs(FastMath.round((float)valuesMinusTenTen[i])-Math.round((float)valuesMinusTenTen[i]));
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.round((float)valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.round(float), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.round((float)valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.round(float), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            int delta = Math.abs(FastMath.round((float)valuesMinusALittleLotALittleLot[i])-Math.round((float)valuesMinusALittleLotALittleLot[i]));
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-FLOAT_COMMA_LIMIT,FLOAT_COMMA_LIMIT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.round((float)valuesFloatCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.round(float), values in ["+(-FLOAT_COMMA_LIMIT)+","+FLOAT_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.round((float)valuesFloatCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.round(float), values in ["+(-FLOAT_COMMA_LIMIT)+","+FLOAT_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            int delta = Math.abs(FastMath.round((float)valuesFloatCommaLimit[i])-Math.round((float)valuesFloatCommaLimit[i]));
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.round(valuesFloatAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.round(float), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.round(valuesFloatAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.round(float), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            int delta = Math.abs(FastMath.round(valuesFloatAllMagnitudes[i])-Math.round(valuesFloatAllMagnitudes[i]));
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        float[] specialValues = new float[] {
                Float.NaN,
                -2.5f, -1.5f, -0.5f, -0.0f, 0.0f, 0.5f, 1.5f, 2.5f,
                Integer.MIN_VALUE, -Integer.MAX_VALUE, Integer.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            int refResult = Math.round(specialValues[i]);
            int fastResult = FastMath.round(specialValues[i]);
            if (!Integer.toString(refResult).equals(Integer.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.round("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.round("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_round_double() {
        long maxDelta;
        int i;
        int j;
        long dummy = 0;

        System.out.println("--- testing round(double) ---");

        // [-10,10]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.round(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.round(double), values in [-10,10], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.round(valuesMinusTenTen[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.round(double), values in [-10,10], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            long delta = Math.abs(FastMath.round(valuesMinusTenTen[i])-Math.round(valuesMinusTenTen[i]));
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-A_LITTLE_LOT,A_LITTLE_LOT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.round(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.round(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.round(valuesMinusALittleLotALittleLot[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.round(double), values in ["+(-A_LITTLE_LOT)+","+A_LITTLE_LOT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            long delta = Math.abs(FastMath.round(valuesMinusALittleLotALittleLot[i])-Math.round(valuesMinusALittleLotALittleLot[i]));
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // [-DOUBLE_COMMA_LIMIT,DOUBLE_COMMA_LIMIT]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.round(valuesDoubleCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.round(double), values in ["+(-DOUBLE_COMMA_LIMIT)+","+DOUBLE_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.round(valuesDoubleCommaLimit[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.round(double), values in ["+(-DOUBLE_COMMA_LIMIT)+","+DOUBLE_COMMA_LIMIT+"], took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            long delta = Math.abs(FastMath.round(valuesDoubleCommaLimit[i])-Math.round(valuesDoubleCommaLimit[i]));
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.round(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.round(double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.round(valuesDoubleAllMagnitudes[j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.round(double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            long delta = Math.abs(FastMath.round(valuesDoubleAllMagnitudes[i])-Math.round(valuesDoubleAllMagnitudes[i]));
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta: "+maxDelta);

        // special values

        double[] specialValues = new double[] {
                Double.NaN,
                -2.5, -1.5, -0.5, -0.0, 0.0, 0.5, 1.5, 2.5,
                Integer.MIN_VALUE, -Integer.MAX_VALUE, Integer.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            long refResult = Math.round(specialValues[i]);
            long fastResult = FastMath.round(specialValues[i]);
            if (!Long.toString(refResult).equals(Long.toString(fastResult))) {
                if (!foundDifferences) {
                    System.out.println("");
                    foundDifferences = true;
                }
                System.out.println("    Math.round("+specialValues[i]+")="+refResult);
                System.out.println("FastMath.round("+specialValues[i]+")="+fastResult);
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_hypot_2double() {
        double maxDelta;
        int i;
        int j;
        double dummy = 0.0;

        System.out.println("--- testing hypot(double,double) ---");

        // [-1e308,1e308]

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.hypot(Math.abs(valuesXForHypot[j]),Math.abs(valuesYForHypot[j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.hypot(double,double), values in [-1e308,1e308] (magnitudes varying together), took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.hypot(Math.abs(valuesXForHypot[j]),Math.abs(valuesYForHypot[j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.hypot(double,double), values in [-1e308,1e308] (magnitudes varying together), took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.hypot(Math.abs(valuesXForHypot[i]),Math.abs(valuesYForHypot[i]));
            double fastResult = FastMath.hypot(Math.abs(valuesXForHypot[i]),Math.abs(valuesYForHypot[i]));
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += Math.hypot(Math.abs(valuesDoubleAllMagnitudes[j]),Math.abs(valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     Math.hypot(double,double), values of all magnitudes, took "+getElapsedSeconds()+" s");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.hypot(Math.abs(valuesDoubleAllMagnitudes[j]),Math.abs(valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-j]));
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.print("Loop on FastMath.hypot(double,double), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        maxDelta = 0.0;
        for (i=0;i<NBR_OF_VALUES;i++) {
            double refResult = Math.hypot(Math.abs(valuesDoubleAllMagnitudes[i]),Math.abs(valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-i]));
            double fastResult = FastMath.hypot(Math.abs(valuesDoubleAllMagnitudes[i]),Math.abs(valuesDoubleAllMagnitudes[(NBR_OF_VALUES-1)-i]));
            double delta = relDelta(fastResult,refResult);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        System.out.println("max delta (relative): "+maxDelta);

        // special values

        double[] specialValues = new double[] { Double.NaN, -1.0, -0.0, 0.0, 1.0, 2.0, Double.MIN_VALUE, Double.MAX_VALUE, Double.POSITIVE_INFINITY };
        boolean foundDifferences = false;
        System.out.print("Result differences for special values:... ");
        for (i=0;i<specialValues.length;i++) {
            for (j=0;j<specialValues.length;j++) {
                double refResult = Math.hypot(specialValues[i],specialValues[j]);
                double fastResult = FastMath.hypot(specialValues[i],specialValues[j]);
                if (!Double.toString(refResult).equals(Double.toString(fastResult))) {
                    if (!foundDifferences) {
                        System.out.println("");
                        foundDifferences = true;
                    }
                    System.out.println("    Math.hypot("+specialValues[i]+","+specialValues[j]+")="+refResult);
                    System.out.println("FastMath.hypot("+specialValues[i]+","+specialValues[j]+")="+fastResult);
                }
            }
        }
        if (!foundDifferences) {
            System.out.println("none.");
        }
        
        useDummy(dummy);
    }

    private void test_addExact_2int() {
        int i;
        int j;
        int dummy = 0;

        System.out.println("--- testing addExact(int,int) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.addExact(valuesIntAllMagnitudes[j]>>1,valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]>>1);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     addExact(int,int), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            try {
                dummy += FastMath.addExact(valuesIntAllMagnitudes[j],valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            } catch (ArithmeticException e) {
                // quiet
            }
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on addExact(int,int), values of all magnitudes, took "+getElapsedSeconds()+" s... ");
        
        useDummy(dummy);
    }

    private void test_addExact_2long() {
        int i;
        int j;
        long dummy = 0;

        System.out.println("--- testing addExact(long,long) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.addExact(valuesLongAllMagnitudes[j]>>1,valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]>>1);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     addExact(long,long), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            try {
                dummy += FastMath.addExact(valuesLongAllMagnitudes[j],valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            } catch (ArithmeticException e) {
                // quiet
            }
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on addExact(long,long), values of all magnitudes, took "+getElapsedSeconds()+" s");
        
        useDummy(dummy);
    }

    private void test_addBounded_2int() {
        int i;
        int j;
        int dummy = 0;

        System.out.println("--- testing addBounded(int,int) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.addBounded(valuesIntAllMagnitudes[j]>>1,valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]>>1);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     addBounded(int,int), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.addBounded(valuesIntAllMagnitudes[j],valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on addBounded(int,int), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        useDummy(dummy);
    }

    private void test_addBounded_2long() {
        int i;
        int j;
        long dummy = 0;

        System.out.println("--- testing addBounded(long,long) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.addBounded(valuesLongAllMagnitudes[j]>>1,valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]>>1);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     addBounded(long,long), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.addBounded(valuesLongAllMagnitudes[j],valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on addBounded(long,long), values of all magnitudes, took "+getElapsedSeconds()+" s");
        
        useDummy(dummy);
    }

    private void test_subtractExact_2int() {
        int i;
        int j;
        int dummy = 0;

        System.out.println("--- testing subtractExact(int,int) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.subtractExact(valuesIntAllMagnitudes[j]>>1,valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]>>1);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     subtractExact(int,int), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            try {
                dummy += FastMath.subtractExact(valuesIntAllMagnitudes[j],valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            } catch (ArithmeticException e) {
                // quiet
            }
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on subtractExact(int,int), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        useDummy(dummy);
    }

    private void test_subtractExact_2long() {
        int i;
        int j;
        long dummy = 0;

        System.out.println("--- testing subtractExact(long,long) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.subtractExact(valuesLongAllMagnitudes[j]>>1,valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]>>1);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     subtractExact(long,long), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            try {
                dummy += FastMath.subtractExact(valuesLongAllMagnitudes[j],valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            } catch (ArithmeticException e) {
                // quiet
            }
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on subtractExact(long,long), values of all magnitudes, took "+getElapsedSeconds()+" s");
        
        useDummy(dummy);
    }

    private void test_subtractBounded_2int() {
        int i;
        int j;
        int dummy = 0;

        System.out.println("--- testing subtractBounded(int,int) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.subtractBounded(valuesIntAllMagnitudes[j]>>1,valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]>>1);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     subtractBounded(int,int), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.subtractBounded(valuesIntAllMagnitudes[j],valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on subtractBounded(int,int), values of all magnitudes, took "+getElapsedSeconds()+" s... ");
        
        useDummy(dummy);
    }

    private void test_subtractBounded_2long() {
        int i;
        int j;
        long dummy = 0;

        System.out.println("--- testing subtractBounded(long,long) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.subtractBounded(valuesLongAllMagnitudes[j]>>1,valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]>>1);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     subtractBounded(long,long), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.subtractBounded(valuesLongAllMagnitudes[j],valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on subtractBounded(long,long), values of all magnitudes, took "+getElapsedSeconds()+" s");
        
        useDummy(dummy);
    }

    private void test_multiplyExact_2int() {
        int i;
        int j;
        int dummy = 0;

        System.out.println("--- testing multiplyExact(int,int) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.multiplyExact(valuesIntAllMagnitudes[j]>>16,valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]>>16);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     multiplyExact(int,int), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            try {
                dummy += FastMath.multiplyExact(valuesIntAllMagnitudes[j],valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            } catch (ArithmeticException e) {
                // quiet
            }
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on multiplyExact(int,int), values of all magnitudes, took "+getElapsedSeconds()+" s... ");
        
        useDummy(dummy);
    }

    private void test_multiplyExact_2long() {
        int i;
        int j;
        long dummy = 0;

        System.out.println("--- testing multiplyExact(long,long) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.multiplyExact(valuesLongAllMagnitudes[j]>>32,valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]>>32);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     multiplyExact(long,long), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            try {
                dummy += FastMath.multiplyExact(valuesLongAllMagnitudes[j],valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            } catch (ArithmeticException e) {
                // quiet
            }
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on multiplyExact(long,long), values of all magnitudes, took "+getElapsedSeconds()+" s");
        
        useDummy(dummy);
    }

    private void test_multiplyBounded_2int() {
        int i;
        int j;
        int dummy = 0;

        System.out.println("--- testing multiplyBounded(int,int) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.multiplyBounded(valuesIntAllMagnitudes[j]>>16,valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]>>16);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     multiplyBounded(int,int), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.multiplyBounded(valuesIntAllMagnitudes[j],valuesIntAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on multiplyBounded(int,int), values of all magnitudes, took "+getElapsedSeconds()+" s... ");

        useDummy(dummy);
    }

    private void test_multiplyBounded_2long() {
        int i;
        int j;
        long dummy = 0;

        System.out.println("--- testing multiplyBounded(long,long) ---");

        // safe range

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.multiplyBounded(valuesLongAllMagnitudes[j]>>32,valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]>>32);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on     multiplyBounded(long,long), values in safe range, took "+getElapsedSeconds()+" s");

        // all magnitudes

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy += FastMath.multiplyBounded(valuesLongAllMagnitudes[j],valuesLongAllMagnitudes[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on multiplyBounded(long,long), values of all magnitudes, took "+getElapsedSeconds()+" s");
        
        useDummy(dummy);
    }

    private void test_isInClockwiseDomain_3double() {
        int i;
        int j;
        boolean dummy = false;

        System.out.println("--- testing isInClockwiseDomain(double,double,double) ---");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy &= FastMath.isInClockwiseDomain(
                    anglesMinusPiPi[j],
                    anglesZeroTwoPi[j],
                    anglesMinusPiPi[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on FastMath.isInClockwiseDomain(double,double,double), values in normalized ranges, took "+getElapsedSeconds()+" s... ");

        j=0;
        startTimer();
        for (i=0;i<NBR_OF_CALLS;i++) {
            dummy &= FastMath.isInClockwiseDomain(
                    valuesMinusHundredHundred[j],
                    anglesZeroTwoPi[j],
                    valuesMinusHundredHundred[(NBR_OF_VALUES-1)-j]);
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop on    FastMath.isInClockwiseDomain(double,double,double), values in [-100.0,100.0], took "+getElapsedSeconds()+" s... ");
        
        useDummy(dummy);
    }

    //--------------------------------------------------------------------------
    // MISCELLANEOUS METHODS
    //--------------------------------------------------------------------------

    private static void dummyLog() {
        System.out.println("anti optimization log - discard it");
    }
    
    private static void useDummy(int dummy) {
        if (dummy == Integer.MIN_VALUE+1) {
            dummyLog();
        }
    }

    private static void useDummy(long dummy) {
        if (dummy == Long.MIN_VALUE+1) {
            dummyLog();
        }
    }

    private static void useDummy(float dummy) {
        if (dummy == Math.PI+Math.E) {
            dummyLog();
        }
    }

    private static void useDummy(double dummy) {
        if (dummy == Math.PI+Math.E) {
            dummyLog();
        }
    }

    private static void useDummy(boolean dummy) {
        // Either case can't be optimized away
        // (uses Math.sqrt, which is a native function).
        if (dummy) {
            useDummy(Math.sqrt(Math.PI));
        } else {
            useDummy(Math.sqrt(Math.E));
        }
    }
    
    private void startTimer() {
        timerRef = System.nanoTime();
    }

    private double getElapsedSeconds() {
        return TestUtils.nsToSRounded(System.nanoTime() - timerRef);
    }

    /*
     * uniform
     */
    
    private int intUniform() {
        return random.nextInt();
    }

    private int intUniform(int min, int max) {
        final int width = max-min+1;
        if (width <= 0) {
            throw new ArithmeticException();
        }
        return min + (width-1) * random.nextInt(width);
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
    
    /*
     * magnitudes
     */

    private int intAllMagnitudes() {
        return random.nextInt()>>(random.nextInt(32));
    }

    private long longAllMagnitudes() {
        return random.nextLong()>>(random.nextInt(64));
    }

    /**
     * all magnitudes in ]-infinity,+infinity[
     */
    private float floatAllMagnitudes() {
        float tmp;
        do {
            tmp = Float.intBitsToFloat(intUniform());
        } while (Float.isNaN(tmp) || Float.isInfinite(tmp));
        return tmp;
    }

    /**
     * all magnitudes in ]-infinity,+infinity[
     */
    private double doubleAllMagnitudes() {
        double tmp;
        do {
            tmp = Double.longBitsToDouble(longUniform());
        } while (Double.isNaN(tmp) || Double.isInfinite(tmp));
        return tmp;
    }
    
    /*
     * 
     */

    private void printLoopOverhead() {
        double dummy = 0.0;
        int j=0;
        startTimer();
        for (int i=0;i<NBR_OF_CALLS;i++) {
            dummy += valuesMinusTenTen[j];
            j = (j<NBR_OF_VALUES-1) ? j+1 : 0;
        }
        System.out.println("Loop overhead: "+getElapsedSeconds()+" s");
        
        useDummy(dummy);
    }

    private static float absDelta(float a, float b) {
        if (a == b) {
            return 0.0f;
        }
        if (Float.isNaN(a)) {
            return (Float.isNaN(b)) ? 0.0f : Float.POSITIVE_INFINITY;
        } else if (Float.isNaN(b)) {
            return Float.POSITIVE_INFINITY;
        }
        return Math.abs(a-b);
    }

    private static double absDelta(double a, double b) {
        if (a == b) {
            return 0.0;
        }
        if (Double.isNaN(a)) {
            return (Double.isNaN(b)) ? 0.0 : Double.POSITIVE_INFINITY;
        } else if (Double.isNaN(b)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(a-b);
    }

    private static double absDeltaMod(double a, double b, double modulo) {
        double result = absDelta(a,b);
        if (result > modulo*0.5) {
            result -= modulo;
        }
        return result;
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
}
