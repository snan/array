package array

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AssignmentTest : APLTest() {
    @Test
    fun simpleAssignment() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("a←3")
        assertSimpleNumber(3, result)
    }

    @Test
    fun simpleAssignmentAndReadValue() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("a←3 ◊ a+1")
        assertSimpleNumber(4, result)
    }

    @Test
    fun testScope() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("a←4 ◊ { local(a) a←3 ◊ ⍵+a } 2 ◊ a+5")
        assertSimpleNumber(9, result)
    }

    @Test
    fun undefinedVariable() = runBlockingCompat<Unit> {
        assertFailsWith<VariableNotAssigned> {
            parseAPLExpression("a+1")
        }
    }

    @Test
    fun multipleVariableAssignment() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("a←1+b←2 ◊ c←10 ◊ a b c")
        assertDimension(dimensionsOfSize(3), result)
        assertArrayContent(arrayOf(3, 2, 10), result)
    }

    @Test
    fun redefineVariable() = runBlockingCompat<Unit> {
        val result = parseAPLExpression("a←1 ◊ b←a ◊ a←2 ◊ a+b")
        assertSimpleNumber(3, result)
    }

    @Test
    fun invalidVariable() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("foo")
        }
    }

    @Test
    fun invalidVariableInExpression() = runBlockingCompat<Unit> {
        assertFailsWith<APLEvalException> {
            parseAPLExpression("1+foo")
        }
    }

    @Test
    fun assignmentToNonVariable() = runBlockingCompat<Unit> {
        assertFailsWith<ParseException> {
            parseAPLExpression("10←20")
        }
    }

    @Test
    fun assignmentToList() = runBlockingCompat<Unit> {
        assertFailsWith<ParseException> {
            parseAPLExpression("foo bar←10")
        }
    }
}
