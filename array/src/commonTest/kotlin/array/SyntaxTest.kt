package array

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SyntaxText : APLTest() {
    @Test
    fun simpleCustomSyntax() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |defsyntax foo (:value x) { x + 10 }
            |200+foo (1)
        """.trimMargin())
        assertSimpleNumber(211, result)
    }

    @Test
    fun constants() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |defsyntax foo (:constant x) { 10 }
            |foo x
            """.trimMargin())
        assertSimpleNumber(10, result)
    }

    @Test
    fun nonMatchedConstants() = runBlockingCompat<Unit> {
        assertFailsWith<ParseException> {
            parseAPLExpression(
                """
                |defsyntax foo (:constant x) { 10 }
                |foo xyz
                """.trimMargin())
        }
    }

    @Test
    fun valueArg() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |defsyntax foo (:value x) { x + 1 }
            |foo (100)
            """.trimIndent())
        assertSimpleNumber(101, result)
    }

    @Test
    fun doubleValueArg() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |defsyntax foo (:value x :value y) { x + y }
            |foo (200) (10)
            """.trimIndent())
        assertSimpleNumber(210, result)
    }

    @Test
    fun ifTestWithOptionalElse() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |defsyntax xif (:value cond :function thenStatement :optional (:constant xelse :function elseStatement)) {
            |  ⍞((cond ≡ 1) ⌷ (⍞((isLocallyBound 'elseStatement) ⌷ λ{λ{⍬}} λ{elseStatement}) ⍬) thenStatement) cond
            |}
            |(xif (1) { 10 }) (xif (0) { 10 })
            """.trimMargin())
        assertDimension(dimensionsOfSize(2), result)
        assertSimpleNumber(10, result.valueAt(0))
        val secondRes = result.valueAt(1)
        assertDimension(dimensionsOfSize(0), secondRes)
    }

    @Test
    fun ifTestWithElse() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |defsyntax xif (:value cond :function thenStatement :optional (:constant xelse :function elseStatement)) {
            |  ⍞((cond ≡ 1) ⌷ (⍞((isLocallyBound 'elseStatement) ⌷ λ{λ{⍬}} λ{elseStatement}) ⍬) thenStatement) cond
            |}
            |(xif (1) { 10 } xelse { 20 }) (xif (0) { 11 } xelse { 22 })
            """.trimMargin())
        assertDimension(dimensionsOfSize(2), result)
        assertArrayContent(arrayOf(10, 22), result)
    }

    @Test
    fun ifTest() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |defsyntax xif (:value cond :function thenStatement :constant xelse :function elseStatement) {
            |  ⍞((cond ≡ 1) ⌷ elseStatement thenStatement) cond
            |}
            |(xif (1) { 10 } xelse { 11 }) (xif (0) { 100 } xelse { 101 })  
            """.trimMargin())
        assertDimension(dimensionsOfSize(2), result)
        assertArrayContent(arrayOf(10, 101), result)
    }

    @Test
    fun xifWithSideEffect() = runBlockingCompat<Unit> {
        val (result, output) = parseAPLExpressionWithOutput(
            """
            |defsyntax xif (:value cond :function thenStatement :constant xelse :function elseStatement) {
            |  ⍞((cond ≡ 1) ⌷ elseStatement thenStatement) cond
            |}
            |xif (1) { print "aa" ◊ 10 } xelse { print "bb" ◊ 11 }  
            """.trimMargin())
        assertSimpleNumber(10, result)
        assertEquals("aa", output)
    }
}
