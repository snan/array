package array

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ArrayLookupBracketsTest : APLTest() {
    @Test
    fun lookupSingleElement() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("(10 20 30 40)[2]")
        assertSimpleNumber(30, result)
    }

    @Test
    fun lookupList() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("(10 20 30 40)[0 2]")
        assertArrayContent(arrayOf(10, 30), result)
    }

    @Test
    fun lookup2DimensionalArrayValue() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("(3 4 ⍴ 10+⍳12)[1;2]")
        assertSimpleNumber(16, result)
    }

    @Test
    fun lookupWithFunction() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("(10000+⍳100)[90+⍳4]")
        assertArrayContent(arrayOf(10090, 10091, 10092, 10093), result)
    }

    @Test
    fun lookupWithRightSide() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("3 4 (3 4 5)[0] 10 11 12")
        assertArrayContent(arrayOf(3, 4, 3, 10, 11, 12), result)
    }

    @Test
    fun selectAxis() = runBlockingCompat<Unit> {
        parseAPLExpression("(2 3 ⍴ ⍳6)[1;⍳3]").let { result ->
            assertDimension(dimensionsOfSize(3), result)
            assertArrayContent(arrayOf(3, 4, 5), result)
        }
    }


    @Test
    fun lookupWithInvalidArgument() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("(1 2 3 4)[\"foo\"]").collapse()
        }
    }

    @Test
    fun lookupWithInvalidDimension() = runBlockingCompat<Unit> {
        assertFailsWith<InvalidDimensionsException> {
            parseAPLExpression("(1 2 3 4)[0;1]").collapse()
        }
        assertFailsWith<InvalidDimensionsException> {
            parseAPLExpression("(2 3 ⍴ 1 2 3 4 5 6)[0]").collapse()
        }
    }

    @Test
    fun indexLookupParseError() = runBlockingCompat<Unit> {
        assertFailsWith<ParseException> {
            parseAPLExpression("(2 3 4)[0")
        }
        assertFailsWith<ParseException> {
            parseAPLExpression("(2 3 4)]")
        }
    }

    @Test
    fun lookupIllegalIndex() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("(1 2 3 4)[5]").collapse()
        }
    }
}
