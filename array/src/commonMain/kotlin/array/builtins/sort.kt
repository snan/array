package array.builtins

import array.*

suspend fun compareAPLArrays(a: APLValue, b: APLValue, pos: Position? = null): Int {
    val aDimensions = a.dimensions
    val bDimensions = b.dimensions

    // Lower rank arrays always compare less than higher rank
    aDimensions.size.compareTo(bDimensions.size).let { result ->
        if (result != 0) {
            return result
        }
    }

    // If both arrays are scalar, raise an error
    if (aDimensions.size == 0) {
        throw IllegalStateException("Attempt to compare scalars via array comparison")
    }

    // If both arrays are rank 1, compare lexicographically
    if (aDimensions.size == 1 && bDimensions.size == 1) {
        val aLength = aDimensions[0]
        val bLength = bDimensions[0]
        var i = 0
        while (i < aLength && i < bLength) {
            val aVal = a.valueAt(i)
            val bVal = b.valueAt(i)
            val result = aVal.compare(bVal, pos)
            if (result != 0) {
                return result
            }
            i++
        }
        return when {
            i < aLength -> 1
            i < bLength -> -1
            else -> 0
        }
    }

    // Both arrays are higher dimension. Do APL-style comparison by checking dimensions first.
    Arrays.compare(aDimensions.dimensions, bDimensions.dimensions).let { result ->
        if (result != 0) {
            return result
        }
    }

    // Both arrays have the same dimensions, iterate over all members
    for (i in 0 until a.size) {
        val aVal = a.valueAt(i)
        val bVal = b.valueAt(i)
        aVal.compare(bVal).let { result ->
            if (result != 0) {
                return result
            }
        }
    }
    return 0
}

abstract class GradeFunction(pos: Position) : NoAxisAPLFunction(pos) {
    override suspend fun eval1Arg(context: RuntimeContext, a: APLValue): APLValue {
        val aDimensions = a.dimensions

        // Scalars can't be sorted
        if (aDimensions.size == 0) {
            throw InvalidDimensionsException("Scalars cannot be sorted", pos)
        }

        // If the value has a single element along its first axis, return a simple zero
        if (aDimensions[0] in 0..1) {
            return ConstantArray(dimensionsOfSize(1), APLLONG_0)
        }

        val multipliers = aDimensions.multipliers()
        val firstAxisMultiplier = multipliers[0]

        val source = a.collapse()
        val list = ArrayList<Int>(aDimensions[0])
        repeat(aDimensions[0]) { i ->
            list.add(i)
        }
        suspendSort(list, { aIndex, bIndex ->
            var ap = aIndex * firstAxisMultiplier
            var bp = bIndex * firstAxisMultiplier
            var res = 0
            for (i in 0 until firstAxisMultiplier) {
                val objA = source.valueAt(ap)
                val objB = source.valueAt(bp)
                val result = objA.compare(objB, pos)
                if (result != 0) {
                    res = result
                    break
                }
                ap++
                bp++
            }
            applyReverse(res)
        })
        return APLArrayImpl(dimensionsOfSize(list.size), list.map { it.makeAPLNumber() }.toTypedArray())
    }

    abstract fun applyReverse(result: Int): Int
}

class GradeUpFunction : APLFunctionDescriptor {
    class GradeUpFunctionImpl(pos: Position) : GradeFunction(pos) {
        override fun applyReverse(result: Int) = result
    }

    override fun make(pos: Position) = GradeUpFunctionImpl(pos)
}

class GradeDownFunction : APLFunctionDescriptor {
    class GradeUpFunctionImpl(pos: Position) : GradeFunction(pos) {
        override fun applyReverse(result: Int) = -result
    }

    override fun make(pos: Position) = GradeUpFunctionImpl(pos)
}

suspend fun <T> suspendSort(list: MutableList<T>, comparator: suspend (a: T, b: T) -> Int) {
    quickSort(list, 0, list.size - 1, comparator)
}

private suspend fun <T> quickSort(array: MutableList<T>, left: Int, right: Int, comparator: suspend (a: T, b: T) -> Int) {
    val index = partition(array, left, right, comparator)
    if (left < index - 1) { // 2) Sorting left half
        quickSort(array, left, index - 1, comparator)
    }
    if (index < right) { // 3) Sorting right half
        quickSort(array, index, right, comparator)
    }
}

private suspend fun <T> partition(array: MutableList<T>, l: Int, r: Int, comparator: suspend (a: T, b: T) -> Int): Int {
    var left = l
    var right = r
    val pivot = array[(left + right) / 2]
    while (left <= right) {
        while (comparator(array[left], pivot) < 0) {
            left++
        }
        while (comparator(array[right], pivot) > 0) {
            right--
        }
        if (left <= right) {
            swapArray(array, left, right)
            left++
            right--
        }
    }
    return left
}

private fun <T> swapArray(a: MutableList<T>, b: Int, c: Int) {
    val temp = a[b]
    a[b] = a[c]
    a[c] = temp
}
