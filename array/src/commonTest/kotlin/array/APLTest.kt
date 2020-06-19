package array

import array.complex.Complex
import kotlin.test.assertEquals
import kotlin.test.assertTrue

open class APLTest {
    suspend fun parseAPLExpression(expr: String, withStandardLib: Boolean = false): APLValue {
        return parseAPLExpression2(expr, withStandardLib).first
    }

    suspend fun parseAPLExpression2(expr: String, withStandardLib: Boolean = false): Pair<APLValue, Engine> {
        val engine = Engine()
        engine.addLibrarySearchPath("standard-lib")
        if (withStandardLib) {
            engine.parseAndEval(StringSourceLocation("use(\"standard-lib.kap\")"), true)
        }
        val result = engine.parseAndEval(StringSourceLocation(expr), false)
        return Pair(result.collapse(), engine)
    }

    suspend fun parseAPLExpressionWithOutput(expr: String, withStandardLib: Boolean = false): Pair<APLValue, String> {
        val engine = Engine()
        engine.addLibrarySearchPath("standard-lib")
        if (withStandardLib) {
            engine.parseAndEval(StringSourceLocation("use(\"standard-lib.kap\")"), true)
        }
        val output = StringBuilderOutput()
        engine.standardOutput = output
        val result = engine.parseAndEval(StringSourceLocation(expr), false)
        return Pair(result, output.buf.toString())
    }

    suspend fun assertArrayContent(expectedValue: Array<Int>, value: APLValue) {
        assertEquals(expectedValue.size, value.size, "Array dimensions mismatch")
        for (i in expectedValue.indices) {
            assertSimpleNumber(expectedValue[i].toLong(), value.valueAt(i), "index: ${i}")
        }
    }

    fun assertDimension(expectDimensions: Dimensions, result: APLValue) {
        val dimensions = result.dimensions
        assertTrue(result.dimensions.compareEquals(expectDimensions), "expected dimension: $expectDimensions, actual $dimensions")
    }

    suspend fun assertPairs(v: APLValue, vararg values: Array<Int>) {
        for (i in values.indices) {
            val cell = v.valueAt(i)
            val expectedValue = values[i]
            for (eIndex in expectedValue.indices) {
                assertSimpleNumber(expectedValue[eIndex].toLong(), cell.valueAt(eIndex))
            }
        }
    }

    suspend fun assertSimpleNumber(expected: Long, value: APLValue, expr: String? = null) {
        val v = value.unwrapDeferredValue()
        val prefix = "Expected value: ${expected}, actual: ${value}"
        val exprMessage = if (expr == null) prefix else "${prefix}, expr: ${expr}"
        assertTrue(v.isScalar(), exprMessage)
        assertTrue(v is APLNumber, exprMessage)
        assertEquals(expected, value.ensureNumber().asLong(), exprMessage)
    }

    suspend fun assertDoubleWithRange(expected: Pair<Double, Double>, value: APLValue) {
        assertTrue(value.isScalar())
        val v = value.unwrapDeferredValue()
        assertTrue(v is APLNumber)
        val num = value.ensureNumber().asDouble()
        assertTrue(expected.first <= num)
        assertTrue(expected.second >= num)
    }

    suspend fun assertSimpleDouble(expected: Double, value: APLValue) {
        assertTrue(value.isScalar())
        val v = value.unwrapDeferredValue()
        assertTrue(v is APLNumber)
        assertEquals(expected, v.ensureNumber().asDouble())
    }

    suspend fun assertComplexWithRange(real: Pair<Double, Double>, imaginary: Pair<Double, Double>, result: APLValue) {
        assertTrue(result.isScalar())
        val complex = result.ensureNumber().asComplex()
        val message = "expected: ${real} ${imaginary}, actual: ${complex}"
        assertTrue(real.first <= complex.real && real.second >= complex.real, message)
        assertTrue(imaginary.first <= complex.imaginary && imaginary.second >= complex.imaginary, message)
    }

    suspend fun assertSimpleComplex(expected: Complex, result: APLValue) {
        assertTrue(result.isScalar())
        val v = result.unwrapDeferredValue()
        assertTrue(v is APLNumber)
        assertEquals(expected, v.ensureNumber().asComplex())
    }

    suspend fun assertString(expected: String, value: APLValue) {
        assertEquals(1, value.dimensions.size)
        assertEquals(expected, arrayAsStringValue(value))
    }

    fun assertAPLNull(value: APLValue) {
        assertDimension(dimensionsOfSize(0), value)
        assertEquals(0, value.dimensions[0])
    }

    suspend fun assertAPLValue(expected: Any, result: APLValue) {
        when (expected) {
            is Int -> assertSimpleNumber(expected.toLong(), result)
            is Long -> assertSimpleNumber(expected, result)
            is Double -> assertSimpleDouble(expected, result)
            is Complex -> assertSimpleComplex(expected, result)
            is String -> assertString(expected, result)
            else -> throw IllegalArgumentException("No support for comparing values of type: ${expected::class.qualifiedName}")
        }
    }
}
