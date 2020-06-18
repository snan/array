package array

expect fun sleepMillis(time: Long)

interface MPAtomicRefArray<T> {
    operator fun get(index: Int): T?

    fun set(index: Int, value: T)

    fun compareAndExchange(index: Int, expected: T?, newValue: T?): T?

    @Suppress("IfThenToElvis")
    fun checkOrUpdate(index: Int, fn: () -> T): T {
        val old = get(index)
        if (old != null) {
            return old
        }
        val update = fn()
        val v = compareAndExchange(index, null, update)
        return if (v == null) update else v
    }
}

expect fun <T> makeAtomicRefArray(size: Int): MPAtomicRefArray<T>

interface ThreadWrapper {
    fun join()
}

expect fun runInThread(name: String, fn: () -> Unit): ThreadWrapper

interface MPLock {
    fun <T> withLockHeld(fn: ()-> T): T
}

expect fun makeLock(): MPLock
