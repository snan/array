package array

import kotlin.test.Test
import kotlin.test.assertFailsWith

class LogicTest : APLTest() {
    @Test
    fun andTest() = runBlockingCompat<Unit> {
        assertSimpleNumber(0, parseAPLExpression("0∧0"))
        assertSimpleNumber(0, parseAPLExpression("0∧1"))
        assertSimpleNumber(0, parseAPLExpression("1∧0"))
        assertSimpleNumber(1, parseAPLExpression("1∧1"))
    }

    @Test
    fun andTestWithArray() = runBlockingCompat<Unit> {
        parseAPLExpression("1 1 0 0 ∧ 0 1 1 0").let { result ->
            assertDimension(dimensionsOfSize(4), result)
            assertArrayContent(arrayOf(0, 1, 0, 0), result)
        }
    }

    @Test
    fun orTest() = runBlockingCompat<Unit> {
        assertSimpleNumber(0, parseAPLExpression("0∨0"))
        assertSimpleNumber(1, parseAPLExpression("0∨1"))
        assertSimpleNumber(1, parseAPLExpression("1∨0"))
        assertSimpleNumber(1, parseAPLExpression("1∨1"))
    }

    @Test
    fun orTestWithArray() = runBlockingCompat<Unit> {
        parseAPLExpression("1 1 0 0 ∨ 0 1 1 0").let { result ->
            assertDimension(dimensionsOfSize(4), result)
            assertArrayContent(arrayOf(1, 1, 1, 0), result)
        }
    }

    @Test
    fun testNotWorking() = runBlockingCompat<Unit> {
        parseAPLExpression("~0 1").let { result ->
            assertDimension(dimensionsOfSize(2), result)
            assertArrayContent(arrayOf(1, 0), result)
        }
    }

    @Test
    fun testNotFailing() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("~10")
        }
    }
}
