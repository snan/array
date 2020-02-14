package array.builtins

import array.*

class IotaArray(private val size: Int, private val start: Int = 0) : APLArray() {
    override fun dimensions() = arrayOf(size)

    override fun valueAt(p: Int): APLValue {
        return APLLong((p + start).toLong())
    }
}

class IotaAPLFunction : NoAxisAPLFunction() {
    override fun eval1Arg(context: RuntimeContext, a: APLValue): APLValue {
        if (a is APLNumber) {
            return IotaArray(a.asDouble().toInt())
        } else {
            throw IllegalStateException("Needs to be rewritten once the new class hierarchy is in place")
        }
    }

    override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue): APLValue {
        TODO("not implemented")
    }
}

class ResizedArray(private val dimensions: Dimensions, private val value: APLValue) : APLArray() {
    override fun dimensions() = dimensions
    override fun valueAt(p: Int) = if (value is APLSingleValue) value else value.valueAt(p % value.size())
}

class RhoAPLFunction : NoAxisAPLFunction() {
    override fun eval1Arg(context: RuntimeContext, a: APLValue): APLValue {
        val argDimensions = a.dimensions()
        return APLArrayImpl(arrayOf(argDimensions.size)) { APLLong(argDimensions[it].toLong()) }
    }

    override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue): APLValue {
        if (a.dimensions().size > 1) {
            throw InvalidDimensionsException("Left side of rho must be scalar or a one-dimensional array")
        }

        val d1 = Array(a.size()) { a.valueAt(it).ensureNumber().asInt() }
        val d2 = b.dimensions()
        return if (Arrays.equals(d1, d2)) {
            // The array already has the correct dimensions, simply return the old one
            b
        } else {
            ResizedArray(d1, b)
        }
    }
}

class IdentityAPLFunction : NoAxisAPLFunction() {
    override fun eval1Arg(context: RuntimeContext, a: APLValue) = a
    override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue) = b
}

class HideAPLFunction : NoAxisAPLFunction() {
    override fun eval1Arg(context: RuntimeContext, a: APLValue) = a
    override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue) = a
}

class EncloseAPLFunction : NoAxisAPLFunction() {
    override fun eval1Arg(context: RuntimeContext, a: APLValue): APLValue {
        return EnclosedAPLValue(a)
    }

    override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue): APLValue {
        TODO("not implemented")
    }
}

class DiscloseAPLFunction : NoAxisAPLFunction() {
    override fun eval1Arg(context: RuntimeContext, a: APLValue): APLValue {
        val rank = a.rank()
        return when {
            a is APLSingleValue -> a
            rank == 0 -> a.valueAt(0)
            else -> a
        }
    }

    override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue): APLValue {
        TODO("not implemented")
    }
}

class Concatenated1DArrays(private val a: APLValue, private val b: APLValue) : APLArray() {
    init {
        assertx(a.rank() == 1 && b.rank() == 1)
    }

    private val aSize = a.dimensions()[0]
    private val bSize = b.dimensions()[0]
    private val dimensions = arrayOf(aSize + bSize)

    override fun dimensions() = dimensions

    override fun valueAt(p: Int): APLValue {
        return if (p >= aSize) b.valueAt(p - aSize) else a.valueAt(p)
    }
}

class ConcatenateAPLFunction : APLFunction {
    override fun eval1Arg(context: RuntimeContext, a: APLValue, axis: APLValue?): APLValue {
        return if (a is APLSingleValue) {
            a
        } else {
            ResizedArray(arrayOf(a.size()), a)
        }
    }

    override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue, axis: APLValue?): APLValue {
        // This is pretty much a step-by-step reimplementation of the catenate function in the ISO spec.

        // For now, let's just raise an error if either argument are scalar
        if (a.rank() == 0 || b.rank() == 0) {
            throw InvalidDimensionsException("scalar argument to catenate")
        }

        val axisInt = if (axis == null) throw RuntimeException("Need the correct axis") else axis.ensureNumber().asInt()

        val a1 = a
        val b1 = b

        val a2 = if (b1.rank() - a1.rank() == 1) {
            // Reshape a1, inserting a new dimension at the position of the axis
            ResizedArray(copyArrayAndInsert(a1.dimensions(), axisInt, 1), a1)
        } else {
            a1
        }

        val b2 = if (a1.rank() - b1.rank() == 1) {
            ResizedArray(copyArrayAndInsert(b1.dimensions(), axisInt, 1), b1)
        } else {
            b1
        }

        val da = a2.dimensions()
        val db = b2.dimensions()

        if (da.size != db.size) {
            throw InvalidDimensionsException("different ranks: ${da.size} compared to ${db.size}")
        }

        for (i in 0 until da.size) {
            if (i != axisInt && da[i] != db[i]) {
                throw InvalidDimensionsException("dimensions at axis ${axisInt} does not match: ${da} compared to ${db}")
            }
        }

        if (a2.size() == 0 || b2.size() == 0) {
            // Catenating an empty array, this needs an implementation
            throw RuntimeException("a2.size = ${a2.size()}, b2.size = ${b2.size()}")
        }

        if (da.size == 1 && db.size == 1) {
            return Concatenated1DArrays(a2, b2)
        }

        return ConcatenatedMultiDimensionalArrays(a2, b2, axisInt)
    }

    // This is an inner class since it's highly dependent on invariants that are established in the parent class
    class ConcatenatedMultiDimensionalArrays(val a: APLValue, val b: APLValue, val axis: Int) : APLArray() {
        private val dimensions: Dimensions
        private val bottomMultiplier: Int
        private val topMultiplier: Int
        private val axisA: Int

        init {
            val ad = a.dimensions()
            val bd = b.dimensions()

            axisA = ad[axis]

            dimensions = Array(ad.size) { i -> if (i == axis) ad[i] + bd[i] else ad[i] }

            var currB = 1
            for(i in dimensions.size - 1 until axis) {
                currB *= dimensions[i]
            }
            bottomMultiplier = currB

            val axisMultiplier = bottomMultiplier * dimensions[axis]
            topMultiplier = size() / axisMultiplier
        }

        override fun dimensions() = dimensions

        /*
        a * b * c * d * e
          axisMod = c
          divisor = d*e

          multiplier = p / (d*e)
          posAlongExtended = multiplier % c

          (3 4 ⍴ ⍳100) ,[0] 100+⍳4
          ┏→━━━━━━━━━━━━━━┓
          ↓  0   1   2   3┃
          ┃  4   5   6   7┃
          ┃  8   9  10  11┃
          ┃100 101 102 103┃
          ┗━━━━━━━━━━━━━━━┛
          (3 4 ⍴ ⍳100) ,[1] 100+⍳3
          ┏→━━━━━━━━━━━━┓
          ↓0 1  2  3 100┃
          ┃4 5  6  7 101┃
          ┃8 9 10 11 102┃
          ┗━━━━━━━━━━━━━┛
         */
        override fun valueAt(p: Int): APLValue {
            val axisCoord = (p % topMultiplier) / bottomMultiplier
            if(axisCoord < axisA) {
                return APLLong(1111)
            }
            else {
                return APLLong(2222)
            }
        }
    }
}

class AccessFromIndexAPLFunction : NoAxisAPLFunction() {
    override fun eval1Arg(context: RuntimeContext, a: APLValue): APLValue {
        TODO("not implemented")
    }

    override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue): APLValue {
        val ad = a.dimensions()
        if (ad.size != 1) {
            throw InvalidDimensionsException("position argument is not rank 1")
        }
        val bd = b.dimensions()
        if (ad[0] != bd.size) {
            throw InvalidDimensionsException("number of values in position argument must match the number of dimensions")
        }
        val posList = Array(ad[0]) { i -> a.valueAt(i).ensureNumber().asInt() }
        val pos = indexFromDimensions(bd, posList)
        return b.valueAt(pos)
    }
}
