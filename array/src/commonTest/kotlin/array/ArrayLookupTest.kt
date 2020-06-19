package array

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ArrayLookupTest : APLTest() {
    @Test
    fun testSimpleArrayLookup() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("2 ⌷ 1 2 3 4")
        assertSimpleNumber(3, result)
    }

    @Test
    fun testSimpleArrayLookupFromFunctionInvocation() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("2 ⌷ 10 + 10 11 12 13")
        assertSimpleNumber(22, result)
    }

    @Test
    fun testIllegalIndex() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("3 ⌷ 1 2 3").collapse()
        }
    }

    @Test
    fun illegalDimension() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("2 3 ⌷ 1 2 3 4").collapse()
        }
    }
}
