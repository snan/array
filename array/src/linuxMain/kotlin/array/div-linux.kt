package array

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.native.concurrent.FreezableAtomicReference
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen

actual fun sleepMillis(time: Long) {
    memScoped {
        val tms = alloc<timespec>()
        tms.tv_sec = time / 1000
        tms.tv_nsec = time.rem(1000) * 1000L * 1000L
        nanosleep(tms.ptr, null)
    }
}

class LinuxMPAtomicRefArray<T>(size: Int) : MPAtomicRefArray<T> {
    private val content = ArrayList<FreezableAtomicReference<T?>>(size)

    init {
        repeat(size) { content.add(FreezableAtomicReference(null)) }
    }

    override operator fun get(index: Int): T? = content[index].value

    override fun set(index: Int, value: T) {
        content[index].value = value
    }

    override fun compareAndExchange(index: Int, expected: T?, newValue: T?): T? {
        val reference = content[index]
        if (reference.isFrozen) {
            newValue.freeze()
        }
        return reference.compareAndSwap(expected, newValue)
    }
}

actual fun <T> makeAtomicRefArray(size: Int): MPAtomicRefArray<T> {
    return LinuxMPAtomicRefArray(size)
}

private class ThreadArgs(val fn: () -> Unit)

private class LinuxThreadWrapper(val tid: pthread_t) : ThreadWrapper {
    override fun join() {
        memScoped {
            val res = alloc<CPointerVar<*>>()
            pthread_join(tid, res.ptr)
        }
    }
}

private fun threadHandler(arg: CPointer<*>): CPointer<*>? {
    val ref = arg.asStableRef<ThreadArgs>()
    val threadArgs = ref.get()
    ref.dispose()
    threadArgs.fn()
    return null
}

@OptIn(ExperimentalUnsignedTypes::class)
actual fun runInThread(name: String, fn: () -> Unit): ThreadWrapper {
    memScoped {
        val args = ThreadArgs(fn)
        args.freeze()
        val ref = StableRef.create(args)
        val tid = alloc<pthread_tVar>()
        val result = pthread_create(tid.ptr, null, staticCFunction(::threadHandler).reinterpret(), ref.asCPointer())
        if (result != 0) {
            throw RuntimeException("Error creating thread: ${strerror(errno)}")
        }
        return LinuxThreadWrapper(tid.value)
    }
}

private class LinuxLock : MPLock {
    private val lock: pthread_mutex_t

    init {
        val v = nativeHeap.alloc<pthread_mutex_t>()
        pthread_mutex_init(v.ptr, null)
        lock = v
    }

    override fun <T> withLockHeld(fn: () -> T): T {
        pthread_mutex_lock(lock.ptr)
        try {
            return fn()
        } finally {
            pthread_mutex_unlock(lock.ptr)
        }
    }
}

actual fun makeLock(): MPLock {
    return LinuxLock()
}
