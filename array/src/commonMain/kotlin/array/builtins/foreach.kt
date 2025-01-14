package array.builtins

import array.*

class ForEachResult1Arg(
    val context: RuntimeContext,
    val fn: APLFunction,
    val value: APLValue,
    val axis: APLValue?,
    val pos: Position
) : APLArray() {
    override val dimensions
        get() = value.dimensions
    override val rank get() = value.rank
    override fun valueAt(p: Int) = fn.eval1Arg(context, value.valueAt(p), axis)
    override val size get() = value.size
}

class ForEachResult2Arg(
    val context: RuntimeContext,
    val fn: APLFunction,
    val arg1: APLValue,
    val arg2: APLValue,
    val axis: APLValue?,
    val pos: Position
) : APLArray() {
    init {
        unless(arg1.dimensions.compareEquals(arg2.dimensions)) {
            throwAPLException(InvalidDimensionsException("Arguments to foreach does not have the same dimensions", pos))
        }
    }

    override val dimensions: Dimensions
        get() = arg1.dimensions
    override val rank get() = arg1.rank
    override fun valueAt(p: Int) = fn.eval2Arg(context, arg1.valueAt(p), arg2.valueAt(p), axis)
    override val size get() = arg1.size
}

class ForEachFunctionDescriptor(val fn: APLFunction) : APLFunctionDescriptor {
    override fun make(pos: Position): APLFunction {
        return object : APLFunction(pos), ParallelSupported {
            override fun eval1Arg(context: RuntimeContext, a: APLValue, axis: APLValue?): APLValue {
                return if (a.isScalar()) {
                    return EnclosedAPLValue.make(fn.eval1Arg(context, a.disclose(), null))
                } else {
                    ForEachResult1Arg(context, fn, a, axis, pos)
                }
            }

            override fun eval2Arg(
                context: RuntimeContext,
                a: APLValue,
                b: APLValue,
                axis: APLValue?
            ): APLValue {
                return compute2Arg(context, fn, a, b, axis, pos)
            }

            override fun computeParallelTasks1Arg(
                context: RuntimeContext, numTasks: Int, a: APLValue, axis: APLValue?
            ): ParallelTaskList {
                val res = eval1Arg(context, a, axis)
                return ParallelCompressTaskList.make(res, numTasks, pos)
            }

            override fun computeParallelTasks2Arg(
                context: RuntimeContext, numTasks: Int, a: APLValue, b: APLValue, axis: APLValue?
            ): ParallelTaskList {
                val res = eval2Arg(context, a, b, axis)
                return ParallelCompressTaskList.make(res, numTasks, pos)
            }

        }
    }

    companion object {
        fun compute2Arg(context: RuntimeContext, fn: APLFunction, a: APLValue, b: APLValue, axis: APLValue?, pos: Position): APLValue {
            if (a.isScalar() && b.isScalar()) {
                return EnclosedAPLValue.make(fn.eval2Arg(context, a.disclose(), b.disclose(), axis).unwrapDeferredValue())
            }
            val a1 = if (a.isScalar()) {
                ConstantArray(b.dimensions, a.valueAt(0))
            } else {
                a
            }
            val b1 = if (b.isScalar()) {
                ConstantArray(a.dimensions, b.valueAt(0))
            } else {
                b
            }
            return ForEachResult2Arg(context, fn, a1, b1, axis, pos)
        }
    }
}

class ForEachOp : APLOperatorOneArg {
    override fun combineFunction(fn: APLFunction, operatorAxis: Instruction?, pos: Position): APLFunctionDescriptor {
        if (operatorAxis != null) {
            throwAPLException(AxisNotSupported(pos))
        }
        return ForEachFunctionDescriptor(fn)
    }
}
