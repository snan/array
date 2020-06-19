package array.options

import array.runBlockingCompat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArgParserTest {
    @Test
    fun simpleTest() = runBlockingCompat<Unit> {
        val parser = ArgParser(Option("foo", true), Option("bar", false))
        val result = parser.parse(arrayOf("--foo=a", "--bar"))
        assertEquals(2, result.size)
        assertEquals("a", result["foo"])
        assertEquals(null, result["bar"])
    }

    @Test
    fun noArgOptionWithArgShouldFail() = runBlockingCompat<Unit> {
        assertFailsWith<InvalidOption> {
            ArgParser(Option("foo", false)).parse(arrayOf("--foo=a"))
        }
    }

    @Test
    fun argOptionWithoutArgShouldFail() = runBlockingCompat<Unit> {
        assertFailsWith<InvalidOption> {
            ArgParser(Option("foo", true)).parse(arrayOf("--foo"))
        }
    }

    @Test
    fun noArguments() = runBlockingCompat<Unit> {
        val result = ArgParser(Option("foo", true), Option("bar", false)).parse(emptyArray())
        assertEquals(0, result.size)
    }

    @Test
    fun invalidOptionFormat0() = runBlockingCompat<Unit> {
        assertFailsWith<InvalidOption> {
            ArgParser(Option("foo", true), Option("bar", false)).parse(arrayOf("-foo=a"))
        }
    }

    @Test
    fun invalidOptionFormat1() = runBlockingCompat<Unit> {
        assertFailsWith<InvalidOption> {
            ArgParser(Option("foo", true), Option("bar", false)).parse(arrayOf("-bar"))
        }
    }

    @Test
    fun invalidOptionFormat2() = runBlockingCompat<Unit> {
        assertFailsWith<InvalidOption> {
            ArgParser(Option("foo", true), Option("bar", false)).parse(arrayOf(" --foo=a"))
        }
    }
}
