package array

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CustomFunctionTest : APLTest() {
    @Test
    fun oneArgFunction() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("∇ foo (A) { A+1 } ◊ foo 10")
        assertSimpleNumber(11, result)
    }

    @Test
    fun twoArgFunction() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("∇ (B) foo (A) { A+B+1 } ◊ 10 foo 20")
        assertSimpleNumber(31, result)
    }

    @Test
    fun oneArgListFunction() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("∇ foo (A;B;C;D) { A+B+C+D+1 } ◊ foo (10;20;30;40)")
        assertSimpleNumber(101, result)
    }

    @Test
    fun twoArgListFunction() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("∇ (E;F) foo (A;B;C;D) { A+B+C+D+E+F+1 } ◊ (1000;2000) foo (10;20;30;40)")
        assertSimpleNumber(3101, result)
    }

    @Test
    fun multipleFunctions() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("∇ foo (A) { A+1 } ◊ ∇ bar (A) { A+10 } ◊ (foo 10) + (bar 100)")
        assertSimpleNumber(121, result)
    }

    @Test
    fun sideEffects() = runBlockingCompat<Unit> {
        val engine = Engine()
        val output = StringBuilderOutput()
        engine.standardOutput = output
        val result = engine.parseAndEval(StringSourceLocation("∇ foo (A) { print A ◊ A+10 } ◊ foo(1000) "), false)
        assertSimpleNumber(1010, result)
        assertEquals("1000", output.buf.toString())
    }

    @Test
    fun recursiveFunctionCall() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("∇ foo (A) { A+1 } ◊ foo foo 2")
        assertSimpleNumber(4, result)
    }

    @Test
    fun recursiveFunctionCallWithMultiArguments() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("∇ foo (A;B) { A+B+1 } ◊ foo (10 ; foo (1;2))")
        assertSimpleNumber(15, result)
    }

    @Test
    fun recursiveFunctionCallWithMultiArg2() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("∇ (A;B) foo (C;D) { A+B+C+D+1 } ◊ (8;11) foo (10 ; (100;200) foo (1;2))")
        assertSimpleNumber(334, result)
    }

    @Test
    fun multilineFunction() = runBlockingCompat<Unit> {
        val result = parseAPLExpression(
            """
            |∇ foo (x) {
            |  a ← 10
            |  b ← 2
            |  a+b+x
            |}
            |foo(100)
            """.trimMargin())
        assertSimpleNumber(112, result)
    }

    @Test
    fun tooFewArgumentsLeft() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("∇ (E;F) foo (A;B;C;D) { A+B+C+D+E+F+1 } ◊ (1) foo (2;3;4;5)")
        }
    }

    @Test
    fun tooManyArgumentsLeft() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("∇ (E;F) foo (A;B;C;D) { A+B+C+D+E+F+1 } ◊ (1;2;3) foo (2;3;4;5)")
        }
    }

    @Test
    fun tooFewArgumentsRight() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("∇ (E;F) foo (A;B;C;D) { A+B+C+D+E+F+1 } ◊ (1;2) foo (2;3;4)")
        }

    }

    @Test
    fun tooManyArgumentsRight() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("∇ (E;F) foo (A;B;C;D) { A+B+C+D+E+F+1 } ◊ (1;2) foo (2;3;4;5;6)")
        }
    }

    @Test
    fun illegalTypeInArgument() = runBlockingCompat<Unit> {
        assertFailsWith<ParseException> {
            parseAPLExpression("∇ (E;1) foo (A;B;C;D) { A+B+C+D+E+1 }")
        }
        assertFailsWith<ParseException> {
            parseAPLExpression("∇ (E;F) foo (A;B;C;D;1) { A+B+C+D+E+F+1 }")
        }
    }

    @Test
    fun duplicatedArgumentsTest() = runBlockingCompat<Unit> {
        assertFailsWith<ParseException> {
            parseAPLExpression("∇ (A;A) { A }")
        }
    }

    @Test
    fun duplicatedArgumentsTest2() = runBlockingCompat<Unit> {
        assertFailsWith<ParseException> {
            parseAPLExpression("∇ (A;B;C;D;E;F;G;H;J;D;K;L;M) { A }")
        }
    }
}
