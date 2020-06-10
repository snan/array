package array

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import platform.posix.nanosleep
import platform.posix.timespec
import kotlin.coroutines.EmptyCoroutineContext

actual fun sleepMillis(time: Long) {
    memScoped {
        val tms = alloc<timespec>()
        tms.tv_sec = time / 1000
        tms.tv_nsec = time.rem(1000) * 1000L * 1000L
        nanosleep(tms.ptr, null)
    }
}

class LinuxMPAtomicRefArray<T>(size: Int) : MPAtomicRefArray<T> {
    private val content = ArrayList<AtomicRef<T?>>(size)

    init {
        repeat(size) { content.add(atomic(null)) }
    }

    override operator fun get(index: Int): T? = content[index].value

    override fun compareAndExchange(index: Int, expected: T?, newValue: T?): T? {
        if (content[index].compareAndSet(expected, newValue)) {
            return newValue
        }
        return content[index].value
    }
}

actual fun <T> makeAtomicRefArray(size: Int): MPAtomicRefArray<T> {
    return LinuxMPAtomicRefArray(size)
}

actual fun <T> runBlockingCompat(fn: suspend CoroutineScope.() -> T): T {
    return runBlocking(EmptyCoroutineContext, fn)
}
