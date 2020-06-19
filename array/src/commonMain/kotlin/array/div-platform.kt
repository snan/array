package array

expect fun sleepMillis(time: Long)

interface MPAtomicRefArray<T> {
    operator fun get(index: Int): T?

    fun set(index: Int, value: T)

    fun compareAndExchange(index: Int, expected: T?, newValue: T?): T?

    @Suppress("IfThenToElvis")
    suspend fun checkOrUpdate(index: Int, fn: suspend () -> T): T {
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

expect fun <T> runBlockingCompat(fn: suspend () -> T): T
