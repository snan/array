package array

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlowControlTest : APLTest() {
    @Test
    fun assertIf() = runBlockingCompat<Unit> {
        assertSimpleNumber(10, parseAPLExpression("if (1) { 10 }", true))
        parseAPLExpression("if (0) { 10 }", true).let { result ->
            assertDimension(dimensionsOfSize(0), result)
        }
    }

    @Test
    fun testIfElse() = runBlockingCompat<Unit> {
        assertSimpleNumber(10, parseAPLExpression("if (1) { 10 } else { 20 }", true))
        assertSimpleNumber(20, parseAPLExpression("if (0) { 10 } else { 20 }", true))
    }

    @Test
    fun testIfElseInExpressionLeftSide() = runBlockingCompat<Unit> {
        assertSimpleNumber(1010, parseAPLExpression("1000 + if (1) { 10 } else { 20 }", true))
        assertSimpleNumber(1020, parseAPLExpression("1000 + if (0) { 10 } else { 20 }", true))
    }

    @Test
    fun testIfElseInExpressionRightSide() = runBlockingCompat<Unit> {
        assertSimpleNumber(1004, parseAPLExpression("if (1) { 4 } else { 5 } + 1000", true))
        assertSimpleNumber(1005, parseAPLExpression("if (0) { 4 } else { 5 } + 1000", true))
    }

    @Test
    fun testIfElseInExpressionBothSides() = runBlockingCompat<Unit> {
        assertSimpleNumber(11004, parseAPLExpression("10000 + if (1) { 4 } else { 5 } + 1000", true))
        assertSimpleNumber(11005, parseAPLExpression("10000 + if (0) { 4 } else { 5 } + 1000", true))
    }

    @Test
    fun testSideEffectsInIf() = runBlockingCompat<Unit> {
        parseAPLExpressionWithOutput("print 10 ◊ if (1) { print 2 } ◊ print 3 ◊ 100", true).let { (result, s) ->
            assertSimpleNumber(100, result)
            assertEquals("1023", s)
        }
        parseAPLExpressionWithOutput("print 10 ◊ if (0) { print 2 } ◊ print 3 ◊ 100", true).let { (result, s) ->
            assertSimpleNumber(100, result)
            assertEquals("103", s)
        }
    }

    @Test
    fun testSideEffectsInIfElse() = runBlockingCompat<Unit> {
        parseAPLExpressionWithOutput("print 10 ◊ if (1) { print 2 } else { print 4 } ◊ print 3 ◊ 100", true).let { (result, s) ->
            assertSimpleNumber(100, result)
            assertEquals("1023", s)
        }
        parseAPLExpressionWithOutput("print 10 ◊ if (0) { print 2 } else { print 4 } ◊ print 3 ◊ 100", true).let { (result, s) ->
            assertSimpleNumber(100, result)
            assertEquals("1043", s)
        }
    }

    @Test
    fun testMultilineIf() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |if (1) {
            |    10
            |}
            """.trimMargin(), true)
        assertSimpleNumber(10, result)
    }

    @Test
    fun testMultilineIfWithElse() = runBlockingCompat<Unit> {
        val result0 = parseAPLExpression(
            """
            |if (1) {
            |    10
            |} else {
            |    20
            |}
            """.trimMargin(), true)
        assertSimpleNumber(10, result0)

        val result1 = parseAPLExpression(
            """
            |if (0) {
            |    10
            |} else {
            |    20
            |}
            """.trimMargin(), true)
        assertSimpleNumber(20, result1)
    }

    @Ignore
    @Test
    fun recursionTest() = runBlockingCompat<Unit> {
        val (result, out) = parseAPLExpressionWithOutput(
            """
            |∇ foo (x) { if (x>0) { print x ◊ foo x-1 } else { 123 } }
            |foo 10
            """.trimMargin(), true)
        assertSimpleNumber(123, result)
        assertEquals("10987654321", out)
    }

    @Test
    fun lambdaRecursionTest() = runBlockingCompat<Unit> {
        val (result, out) = parseAPLExpressionWithOutput(
            """
            |foo ← λ{ x←⍵ ◊ if(x>0) { print x ◊ ⍞foo x-1 } else { 123 } }
            |⍞foo 10
            """.trimMargin(), true)
        assertSimpleNumber(123, result)
        assertEquals("10987654321", out)
    }

    @Test
    fun scopeTest0() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |∇ foo (x) { λ{⍵+x} }
            |x ← foo 2
            |⍞x 100
            """.trimMargin())
        assertSimpleNumber(102, result)
    }

    @Test
    fun scopeTest1() = runBlockingCompat<Unit> {
        assertFailsWith<VariableNotAssigned> {
            parseAPLExpression(
                """
                |foo ← λ{ x + ⍵ }
                |bar ← λ{ x ← 10 ◊ (⍞foo 1) - ⍵ }
                |⍞bar 20 
                """.trimMargin()
            )
        }
    }

    @Ignore
    @Test
    fun nonLocalExitTest() = runBlockingCompat<Unit> {
        parseAPLExpression("catch ('a) { { ⍵+10 ◊ 3→'a ◊ 10 } 1 } {⍵+1}").let { result ->
            assertSimpleNumber(4, result)
        }
    }
}
