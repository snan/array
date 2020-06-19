package array

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DiscloseTest : APLTest() {
    @Test
    fun discloseArrayTest() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("⊃1 2 3 4")
        assertFalse(result.isScalar())
        assertDimension(dimensionsOfSize(4), result)
        assertArrayContent(arrayOf(1, 2, 3, 4), result)
    }

    @Test
    fun discloseNumberTest() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("⊃6")
        assertSimpleNumber(6, result)
    }

    /////////////////////////////
    // Tests for pick
    /////////////////////////////

    @Test
    fun pickOneDimension() = runBlockingCompat<Unit> {
        assertSimpleNumber(102, parseAPLExpression("2⊃100+⍳100"))
    }

    @Test
    fun pickTwoDimensions() = runBlockingCompat<Unit> {
        assertSimpleNumber(167, parseAPLExpression("(⊂8 7)⊃10 20 ⍴ 100+⍳100"))
    }

    @Test
    fun pickFailOutOfRange() = runBlockingCompat<Unit> {
        assertFailsWith<APLIndexOutOfBoundsException> {
            parseAPLExpression("4 ⊃ 1 2 3").collapse()
        }
    }

    @Test
    fun pickFailInvalidDimensions() = runBlockingCompat<Unit> {
        assertFailsWith<InvalidDimensionsException> {
            parseAPLExpression("(⊂8 7) ⊃ 100+⍳100").collapse()
        }
        assertFailsWith<InvalidDimensionsException> {
            parseAPLExpression("7 ⊃ 3 10 ⍴ 100+⍳100").collapse()
        }
        assertFailsWith<InvalidDimensionsException> {
            parseAPLExpression("2⊃1").collapse()
        }
    }

    @Test
    fun pickInvalidSelectorDimension() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("0 (1 0) 0 ⊃ ((1 2) (3 4)) ((5 6) (7 8))").collapse()
        }
    }

    @Test
    fun pickInvalidSelectorType() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("0 @a 0 ⊃ ((1 2) (3 4)) ((5 6) (7 8))").collapse()
        }
    }

    @Test
    fun pickThreeDimensions() = runBlockingCompat<Unit> {
        assertSimpleNumber(3, parseAPLExpression("0 1 0 ⊃ ((1 2) (3 4)) ((5 6) (7 8))"))
    }

    @Test
    fun pickDepthTwo() = runBlockingCompat<Unit> {
        parseAPLExpression("2 1 ⊃ (10 11 12 13 14 15) (20 21 22 23 24 25) (30 31 32 33 34 35)").let { result ->
            assertSimpleNumber(31, result)
        }
    }

    @Test
    fun pickDepthTwoWithDifferentSizedArrays() = runBlockingCompat<Unit> {
        parseAPLExpression("2 6 ⊃ (10 11) (20 21 22 23 24 25 26 27 28 29) (30 31 32 33 34 35 36)").let { result ->
            assertSimpleNumber(36, result)
        }
    }

    @Test
    fun pickFailWhenInnerDimensionsMismatch() = runBlockingCompat<Unit> {
        assertFailsWith<APLIndexOutOfBoundsException> {
            parseAPLExpression("2 6 ⊃ (10 11 12 13 14 15 16 17 18 19) (20 21 22 23 24 25 26 27 28 29) (30 31 32 33) (1 2 3 4 5 6 7)").collapse()
        }
    }

    @Test
    fun pickWithExpressionAsSelector() = runBlockingCompat<Unit> {
        assertSimpleNumber(4, parseAPLExpression("0 (¯99+↑⍴⍳100) ({¯2+⍵} 3) ⊃ ((1 2) (3 4)) ((5 6) (7 8))"))
    }

    @Test
    fun pickScalar() = runBlockingCompat<Unit> {
        assertSimpleNumber(99, parseAPLExpression("⍬⊃99"))
    }
}
