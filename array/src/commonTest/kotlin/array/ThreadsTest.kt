package array

import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadsTest {
    @Test
    fun testThreadAtomicRefArray() {
        data class NumberWrapper(val n: Long)

        val array = makeAtomicRefArray<NumberWrapper>(1)
        array.set(0, NumberWrapper(0))

        fun incrementValue() {
            while (true) {
                val v = array[0]!!
                if (array.compareAndExchange(0, v, NumberWrapper(v.n + 1)) == v) {
                    break
                }
            }
        }

        val iterations = 10000
        val nThreads = 100
        val l = Array(nThreads) {
            runInThread("foo") {
                repeat(iterations) {
                    incrementValue()
                }
            }
        }
        l.forEach { thread ->
            thread.join()
        }
        assertEquals(NumberWrapper(nThreads.toLong() * iterations.toLong()), array[0])
    }

    @Test
    fun testLocks() {
        var currentVal = 0
        val lock = makeLock()

        fun incrementValue() {
            lock.withLockHeld {
                currentVal++
            }
        }

        val iterations = 10000
        val nThreads = 100
        val l = Array(nThreads) {
            runInThread("foo") {
                repeat(iterations) {
                    incrementValue()
                }
            }
        }
        l.forEach { thread ->
            thread.join()
        }
        assertEquals(nThreads * iterations, currentVal)
    }
}
