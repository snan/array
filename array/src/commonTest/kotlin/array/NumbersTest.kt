package array

import kotlin.test.Test
import kotlin.test.assertFailsWith

class NumbersTest : APLTest() {
    @Test
    fun testMathsOperations() = runBlockingCompat<Unit> {
        assertMathsOperation({ a, b -> a + b }, "+")
        assertMathsOperation({ a, b -> a - b }, "-")
        assertMathsOperation({ a, b -> a * b }, "×")
    }

    @Test
    fun testDivision() = runBlockingCompat<Unit> {
        assertSimpleNumber(0, parseAPLExpression("1÷0"))
        assertSimpleNumber(0, parseAPLExpression("100÷0"))
        assertSimpleNumber(0, parseAPLExpression("¯100÷0"))
        assertSimpleNumber(0, parseAPLExpression("12.2÷0"))
        assertSimpleNumber(0, parseAPLExpression("2÷0.0"))
        assertSimpleNumber(0, parseAPLExpression("2.0÷0.0"))
        assertSimpleNumber(2, parseAPLExpression("4÷2"))
        assertSimpleNumber(20, parseAPLExpression("40÷2"))
        assertDoubleWithRange(Pair(3.33332, 3.33334), parseAPLExpression("10÷3"))
    }

    @Test
    fun testAbs() = runBlockingCompat<Unit> {
        // Plain integers
        assertSimpleNumber(2, parseAPLExpression("|2"))
        assertSimpleNumber(10, parseAPLExpression("|¯10"))
        // Floating point
        assertDoubleWithRange(Pair(10.79999, 10.80001), parseAPLExpression("|10.8"))
        assertDoubleWithRange(Pair(4.89999, 4.90001), parseAPLExpression("|¯4.9"))
        // Complex numbers
        assertDoubleWithRange(Pair(9.219543, 9.219545), parseAPLExpression("|6J7"))
        assertDoubleWithRange(Pair(4.472134, 4.472136), parseAPLExpression("|¯4J2"))
        assertDoubleWithRange(Pair(4.472134, 4.472136), parseAPLExpression("|4J¯2"))
        assertDoubleWithRange(Pair(342.285, 342.287), parseAPLExpression("|¯194J¯282"))
    }

    @Test
    fun testMod() = runBlockingCompat<Unit> {
        assertSimpleNumber(1, parseAPLExpression("2|3"))
        assertDoubleWithRange(Pair(0.66669, 0.700001), parseAPLExpression("1|1.7"))
        assertSimpleNumber(-1, parseAPLExpression("¯2|11"))
        assertSimpleNumber(0, parseAPLExpression("3|3"))
        assertSimpleNumber(2, parseAPLExpression("100|2"))
        assertSimpleNumber(-5, parseAPLExpression("10000|¯20005"))
    }

    @Test
    fun testNegation() = runBlockingCompat<Unit> {
        assertSimpleNumber(0, parseAPLExpression("-0"))
        assertSimpleNumber(1, parseAPLExpression("-(1-2)"))
        assertSimpleNumber(-3, parseAPLExpression("-3"))
        assertSimpleNumber(-6, parseAPLExpression("-2+4"))
    }

    @Test
    fun testExponential() = runBlockingCompat<Unit> {
        assertSimpleNumber(1024, parseAPLExpression("2⋆10"))
        assertDoubleWithRange(Pair(0.0009, 0.0011), parseAPLExpression("10⋆¯3"))
        assertSimpleNumber(0, parseAPLExpression("0⋆10"))
        assertSimpleNumber(1, parseAPLExpression("10⋆0"))
    }

    @Test
    fun invalidExpressions() = runBlockingCompat<Unit> {
        assertFailsWith<ParseException> {
            parseAPLExpression("1+")
        }
        assertFailsWith<ParseException> {
            parseAPLExpression("1++")
        }
        assertFailsWith<ParseException> {
            parseAPLExpression("-")
        }
        assertFailsWith<ParseException> {
            parseAPLExpression("1 2 3 4+")
        }
    }

    @Test
    fun mathOperationsWithCharacters() = runBlockingCompat<Unit> {
        testFailedOpWithChar("+")
        testFailedOpWithChar("-")
        testFailedOpWithChar("×")
        testFailedOpWithChar("÷")
        testFailedOpWithChar("⋆")
        testFailedOpWithChar("|")
    }

    private suspend fun testFailedOpWithChar(name: String) {
        assertFailsWith<APLEvalException> { parseAPLExpression("1${name}@a").collapse() }
        assertFailsWith<APLEvalException> { parseAPLExpression("@a${name}1").collapse() }
        assertFailsWith<APLEvalException> { parseAPLExpression("@a${name}@b").collapse() }
    }

    @Test
    fun functionAliases() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("2*4")
        assertSimpleNumber(16, result)
    }

    private suspend fun assertMathsOperation(op: (Long, Long) -> Long, name: String) {
        val args: Array<Long> =
            arrayOf(0, 1, -1, 2, 3, 10, 100, 123456, -12345, Int.MAX_VALUE.toLong(), Int.MIN_VALUE.toLong(), Long.MAX_VALUE, Long.MIN_VALUE)
        args.forEach { left ->
            args.forEach { right ->
                val expr = "${formatLongAsAPL(left)}${name}${formatLongAsAPL(right)}"
                val result = parseAPLExpression(expr)
                val expect = op(left, right)
                assertSimpleNumber(expect, result, expr)
            }
        }
    }

    private fun formatLongAsAPL(value: Long): String {
        return if (value < 0) {
            // This is a hack to deal with Long.MIN_VALUE
            "¯${value.toString().substring(1)}"
        } else {
            value.toString()
        }
    }
}
