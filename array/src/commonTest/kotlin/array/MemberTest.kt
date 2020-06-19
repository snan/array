package array

import kotlin.test.Test

class MemberTest : APLTest() {
    @Test
    fun oneDimension() = runBlockingCompat<Unit> {
        parseAPLExpression("2 11 100 10 ∊ 1 2 3 4 10 11").let { result ->
            assertDimension(dimensionsOfSize(4), result)
            assertArrayContent(arrayOf(1, 1, 0, 1), result)
        }
    }

    @Test
    fun twoDimension() = runBlockingCompat<Unit> {
        parseAPLExpression("(2 2 ⍴ 10 20 30 40) ∊ 1 2 10 100 40 200 300 400 500").let { result ->
            assertDimension(dimensionsOfSize(2, 2), result)
            assertArrayContent(arrayOf(1, 0, 0, 1), result)
        }
    }

    @Test
    fun testScalarRight() = runBlockingCompat<Unit> {
        parseAPLExpression("1 2 3 ∊ 2").let { result ->
            assertDimension(dimensionsOfSize(3), result)
            assertArrayContent(arrayOf(0, 1, 0), result)
        }
    }

    @Test
    fun testScalarLeft() = runBlockingCompat<Unit> {
        parseAPLExpression("1 ∊ 1 2 3").let { result ->
            assertSimpleNumber(1, result)
        }
    }

    @Test
    fun findChars() = runBlockingCompat<Unit> {
        parseAPLExpression("\"foo\" ∊ \"bbxyzabcf\"").let { result ->
            assertDimension(dimensionsOfSize(3), result)
            assertArrayContent(arrayOf(1, 0, 0), result)
        }
    }
}
