package array

import java.util.concurrent.atomic.AtomicReferenceArray

actual fun sleepMillis(time: Long) {
    Thread.sleep(time)
}

class JvmMPAtomicRefArray<T>(size: Int) : MPAtomicRefArray<T> {
    private val content = AtomicReferenceArray<T>(size)

    override operator fun get(index: Int): T? = content[index]

    override fun set(index: Int, value: T) {
        content.set(index, value)
    }

    override fun compareAndExchange(index: Int, expected: T?, newValue: T?): T? {
        return content.compareAndExchange(index, expected, newValue)
    }
}

actual fun <T> makeAtomicRefArray(size: Int): MPAtomicRefArray<T> {
    return JvmMPAtomicRefArray(size)
}

class JavaThreadWrapper(val thread: Thread) : ThreadWrapper {
    override fun join() {
        thread.join()
    }
}

actual fun runInThread(name: String, fn: () -> Unit): ThreadWrapper {
    val thread = object : Thread(name) {
        override fun run() {
            fn()
        }
    }
    thread.start()
    return JavaThreadWrapper(thread)
}

private class JVMLock : MPLock {
    private val lock = Object()

    override fun <T> withLockHeld(fn: () -> T): T {
        synchronized(lock) {
            return fn()
        }
    }
}

actual fun makeLock(): MPLock {
    return JVMLock()
}
