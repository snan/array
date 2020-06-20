package array

import array.builtins.compareAPLArrays
import array.rendertext.encloseInBox
import array.rendertext.renderNullValue
import array.rendertext.renderStringValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

enum class APLValueType(val typeName: String) {
    INTEGER("integer"),
    FLOAT("float"),
    COMPLEX("complex"),
    CHAR("char"),
    ARRAY("array"),
    SYMBOL("symbol"),
    LAMBDA_FN("function"),
    LIST("list"),
    MAP("map"),
    INTERNAL("internal")
}

enum class FormatStyle {
    PLAIN,
    READABLE,
    PRETTY
}

class AxisLabel(val title: String)

class DimensionLabels(val labels: List<List<AxisLabel?>?>) {
    companion object {
        fun makeEmpty(dimensions: Dimensions): DimensionLabels {
            val result = ArrayList<List<AxisLabel?>?>(dimensions.size)
            repeat(dimensions.size) {
                result.add(null)
            }
            return DimensionLabels(result)
        }

        fun makeDerived(dimensions: Dimensions, oldLabels: DimensionLabels?, newLabels: List<List<AxisLabel?>?>): DimensionLabels {
            assertx(newLabels.size == dimensions.size)
            val oldLabelsList = oldLabels?.labels
            val result = ArrayList<List<AxisLabel?>?>(dimensions.size)
            repeat(dimensions.size) { i ->
                val newLabelsList = newLabels[i]
                val v = when {
                    newLabelsList != null -> {
                        assertx(newLabelsList.size == dimensions[i])
                        newLabelsList
                    }
                    oldLabelsList != null -> oldLabelsList[i]
                    else -> null
                }
                result.add(v)
            }
            return DimensionLabels(result)
        }
    }
}

interface APLValue {
    val aplValueType: APLValueType

    val dimensions: Dimensions
    val rank: Int
        get() = dimensions.size

    suspend fun valueAt(p: Int): APLValue
    suspend fun valueAtWithScalarCheck(p: Int): APLValue = valueAt(p)
    val size: Int
        get() = dimensions.contentSize()

    suspend fun formatted(style: FormatStyle = FormatStyle.PRETTY): String
    suspend fun collapseInt(): APLValue
    fun isScalar(): Boolean = rank == 0
    fun defaultValue(): APLValue = APLLONG_0
    suspend fun arrayify(): APLValue
    suspend fun unwrapDeferredValue(): APLValue = this
    suspend fun compareEquals(reference: APLValue): Boolean
    suspend fun compare(reference: APLValue, pos: Position? = null): Int =
        throw IncompatibleTypeException("Comparison not implemented for objects of type ${this.aplValueType.typeName}", pos)

    val labels: DimensionLabels? get() = null

    suspend fun collapse(): APLValue {
        val l = labels
        val v = collapseInt()
        return if (l == null) {
            v
        } else if (v === this) {
            this
        } else {
            LabelledArray(v, l)
        }
    }

    /**
     * Return a value which can be used as a hash key when storing references to this object in Kotlin maps.
     * The key must follow the standard equals/hashCode conventions with respect to the object which it
     * represents.
     *
     * In other words, if two instances of [APLValue] are to be considered equivalent, then the objects returned
     * by this method should be the same when compared using [equals] and return the same value from [hashCode].
     */
    suspend fun makeKey(): Any

    suspend fun singleValueOrError(): APLValue {
        return when {
            rank == 0 -> this
            size == 1 -> valueAt(0)
            else -> throw IllegalStateException("Expected a single element in array, found ${size} elements")
        }
    }

    suspend fun ensureNumber(pos: Position? = null): APLNumber {
        val v = unwrapDeferredValue()
        if (v == this) {
            throw IncompatibleTypeException("Value $this is not a numeric value (type=${aplValueType.typeName})", pos)
        } else {
            return v.ensureNumber(pos)
        }
    }

    suspend fun ensureSymbol(pos: Position? = null): APLSymbol {
        val v = unwrapDeferredValue()
        if (v == this) {
            throw IncompatibleTypeException("Value $this is not a symbol (type=${aplValueType.typeName})", pos)
        } else {
            return v.ensureSymbol(pos)
        }
    }

    suspend fun ensureList(pos: Position? = null): APLList {
        val v = unwrapDeferredValue()
        if (v == this) {
            throw IncompatibleTypeException("Value $this is not a list (type=${aplValueType.typeName})", pos)
        } else {
            return v.ensureList(pos)
        }
    }

    suspend fun toIntArray(pos: Position): IntArray {
        return IntArray(size) { i ->
            valueAt(i).ensureNumber(pos).asInt()
        }
    }

    suspend fun asBoolean(): Boolean {
        val v = unwrapDeferredValue()
        return if (v == this) {
            true
        } else {
            v.asBoolean()
        }
    }
}

suspend inline fun APLValue.iterateMembers(fn: (APLValue) -> Unit) {
    if (rank == 0) {
        fn(this)
    } else {
        for (i in 0 until size) {
            fn(valueAt(i))
        }
    }
}

suspend inline fun APLValue.iterateMembersWithPosition(fn: (APLValue, Int) -> Unit) {
    if (rank == 0) {
        fn(this, 0)
    } else {
        for (i in 0 until size) {
            fn(valueAt(i), i)
        }
    }
}

suspend fun APLValue.membersSequence(): Flow<APLValue> {
    val v = unwrapDeferredValue()
    return if (v is APLSingleValue) {
        flow {
            emit(v)
        }
    } else {
        flow {
            repeat(v.size) { i ->
                emit(v.valueAt(i))
            }
        }
    }
}

abstract class APLSingleValue : APLValue {
    override val dimensions get() = emptyDimensions()
    override suspend fun valueAt(p: Int) = throw APLIndexOutOfBoundsException("Reading index ${p} from scalar")
    override suspend fun valueAtWithScalarCheck(p: Int) =
        if (p == 0) this else throw APLIndexOutOfBoundsException("Reading at non-zero index ${p} from scalar")

    override val size get() = 1
    override val rank get() = 0
    override suspend fun collapseInt() = this
    override suspend fun arrayify() = APLArrayImpl.make(dimensionsOfSize(1)) { this }
}

var minParallelSize = 16

abstract class APLArray : APLValue {
    override val aplValueType: APLValueType get() = APLValueType.ARRAY

    override suspend fun collapseInt(): APLValue {
        val v = unwrapDeferredValue()
        return when {
            v is APLSingleValue -> v
            v.rank == 0 -> EnclosedAPLValue(v.valueAt(0).collapseInt())
            else -> collapseParallel(v)
        }
    }

    private suspend fun collapseParallel(v: APLValue): APLValue {
        val length = v.dimensions.contentSize()
        if (length < minParallelSize) {
            return APLArrayImpl.make(v.dimensions) { i -> v.valueAt(i).collapseInt() }
        }
        val numBlocks = minParallelSize
        val baseBlockSize = length / numBlocks
        val excess = length - (baseBlockSize * numBlocks)
        assertx(excess in (0 until numBlocks))

        var currBlockIndex = 0
        var start = 0
        val deferredValues = Array(numBlocks) {
            val currBlockSize = baseBlockSize + (if (currBlockIndex < excess) 1 else 0)
            val deferred = CompletableDeferred<Array<APLValue>>()
            val copyStart = start
            GlobalScope.launch {
                val resultList = Array(currBlockSize) { i ->
                    v.valueAt(i + copyStart).collapseInt()
                }
                deferred.complete(resultList)
            }.start()
            start += currBlockSize
            currBlockIndex++
            deferred
        }
        assertx(start == length)

        var blockIndex = 0
        var posInBlock = 0
        var currArray: Array<APLValue>? = null
        return APLArrayImpl.make(v.dimensions) {
            if (posInBlock == 0) {
                currArray = deferredValues[blockIndex].await()
            }
            val result = currArray!![posInBlock++]
            if (posInBlock >= currArray!!.size) {
                blockIndex++
                posInBlock = 0
            }
            result
        }
    }

    override suspend fun formatted(style: FormatStyle) =
        when (style) {
            FormatStyle.PLAIN -> arrayAsString(this, FormatStyle.PLAIN)
            FormatStyle.PRETTY -> arrayAsString(this, FormatStyle.PRETTY)
            FormatStyle.READABLE -> arrayToAPLFormat(this)
        }

    override suspend fun arrayify() = if (rank == 0) APLArrayImpl.make(dimensionsOfSize(1)) { valueAt(0) } else this

    override suspend fun compareEquals(reference: APLValue): Boolean {
        val u = this.unwrapDeferredValue()
        if (u is APLSingleValue) {
            return u.compareEquals(reference)
        } else {
            val uRef = reference.unwrapDeferredValue()
            if (!u.dimensions.compareEquals(uRef.dimensions)) {
                return false
            }
            for (i in 0 until u.size) {
                val o1 = u.valueAt(i)
                val o2 = uRef.valueAt(i)
                if (!o1.compareEquals(o2)) {
                    return false
                }
            }
            return true
        }
    }

    override suspend fun compare(reference: APLValue, pos: Position?): Int {
        return when {
            isScalar() && reference.isScalar() -> {
                return if (reference is APLSingleValue) {
                    -1
                } else {
                    valueAt(0).compare(reference.valueAt(0), pos)
                }
            }
            // Until we have a proper ordering of all types, we have to prevent comparing scalars to anything which is not a scalar
            isScalar() && !reference.isScalar() -> {
                throw IncompatibleTypeException("Comparison is not supported using these types", pos)
            }
            !isScalar() && reference.isScalar() -> {
                throw IncompatibleTypeException("Comparison is not supported using these types", pos)
            }
            else -> compareAPLArrays(this, reference)
        }
    }

    override suspend fun makeKey(): Any {
        var curr = 0
        dimensions.dimensions.forEach { dim ->
            curr = (curr * 63) xor dim
        }
        iterateMembers { v ->
            curr = (curr * 63) xor v.makeKey().hashCode()
        }

        return object {
            override fun equals(other: Any?): Boolean {
                TODO("Broken due to suspend")
                //return other is APLArray && compare(other) == 0
            }

            override fun hashCode() = curr
        }
    }
}

class LabelledArray(val value: APLValue, override val labels: DimensionLabels) : APLArray() {
    override val dimensions = value.dimensions
    override suspend fun valueAt(p: Int) = value.valueAt(p)

    override suspend fun collapseInt(): APLValue {
        return value.collapseInt()
    }

    companion object {
        fun make(value: APLValue, extraLabels: List<List<AxisLabel?>?>): LabelledArray {
            return LabelledArray(value, DimensionLabels.makeDerived(value.dimensions, value.labels, extraLabels))
        }
    }
}

class APLMap(val content: ImmutableMap<Any, APLValue>) : APLSingleValue() {
    override val aplValueType get() = APLValueType.MAP
    override val dimensions = emptyDimensions()

    override suspend fun formatted(style: FormatStyle): String {
        return "map[size=${content.size}]"
    }

    override suspend fun compareEquals(reference: APLValue): Boolean {
        if (reference !is APLMap) {
            return false
        }
        if (content.size != reference.content.size) {
            return false
        }
        content.forEach { (key, value) ->
            val v = reference.content[key] ?: return false
            if (!value.compareEquals(v)) {
                return false
            }
        }
        return true
    }

    override suspend fun makeKey(): Any {
        return content
    }

    suspend fun lookupValue(key: APLValue): APLValue {
        return content[key.makeKey()] ?: APLNullValue()
    }

    suspend fun updateValue(key: APLValue, value: APLValue): APLMap {
        return APLMap(content.copyAndPut(key.makeKey(), value))
    }

    companion object {
        fun makeEmptyMap() = APLMap(ImmutableMap())
    }
}

class APLList(val elements: List<APLValue>) : APLValue {
    override val aplValueType: APLValueType get() = APLValueType.LIST

    override val dimensions get() = emptyDimensions()

    override suspend fun valueAt(p: Int): APLValue {
        TODO("not implemented")
    }

    override suspend fun formatted(style: FormatStyle): String {
        val buf = StringBuilder()
        var first = true
        for (v in elements) {
            if (first) {
                first = false
            } else {
                buf.append("\n; value\n")
            }
            buf.append(v.formatted())
        }
        return buf.toString()
    }

    override suspend fun collapseInt() = this

    override suspend fun arrayify(): APLValue {
        TODO("not implemented")
    }

    override suspend fun ensureList(pos: Position?) = this

    override suspend fun compareEquals(reference: APLValue): Boolean {
        if (reference !is APLList) {
            return false
        }
        if (elements.size != reference.elements.size) {
            return false
        }
        elements.indices.forEach { i ->
            if (!listElement(i).compareEquals(reference.listElement(i))) {
                return false
            }
        }
        return true
    }

    override suspend fun makeKey(): Any {
        TODO("Disabled due to suspend")
        //return ComparableList<Any>().apply { addAll(elements.map(APLValue::makeKey)) }
    }

    fun listSize() = elements.size
    fun listElement(index: Int) = elements[index]
}

class ComparableList<T> : MutableList<T> by ArrayList<T>() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComparableList<*>) return false
        if (size != other.size) return false
        for (i in 0 until size) {
            if (this[i] != other[i]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var curr = 0
        for (i in 0 until size) {
            curr = (curr * 63) xor this[i].hashCode()
        }
        return curr
    }
}

private suspend fun arrayToAPLFormat(value: APLArray): String {
    val v = value.collapse()
    return if (isStringValue(v)) {
        renderStringValue(v, FormatStyle.READABLE)
    } else {
        arrayToAPLFormatStandard(v as APLArray)
    }
}

private suspend fun arrayToAPLFormatStandard(value: APLArray): String {
    val buf = StringBuilder()
    val dimensions = value.dimensions
    if (dimensions.size == 0) {
        buf.append("⊂")
        buf.append(value.valueAt(0).formatted(FormatStyle.READABLE))
    } else {
        for (i in dimensions.indices) {
            if (i > 0) {
                buf.append(" ")
            }
            buf.append(dimensions[i])
        }
        buf.append("⍴")
        if (value.size == 0) {
            buf.append("1")
        } else {
            for (i in 0 until value.size) {
                val a = value.valueAt(i)
                if (i > 0) {
                    buf.append(" ")
                }
                buf.append(a.formatted(FormatStyle.READABLE))
            }
        }
    }
    return buf.toString()
}

fun isNullValue(value: APLValue): Boolean {
    val dimensions = value.dimensions
    return dimensions.size == 1 && dimensions[0] == 0
}

suspend fun isStringValue(value: APLValue): Boolean {
    val dimensions = value.dimensions
    if (dimensions.size == 1) {
        for (i in 0 until value.size) {
            val v = value.valueAt(i)
            if (v !is APLChar) {
                return false
            }
        }
        return true
    } else {
        return false
    }
}

suspend fun arrayAsStringValue(array: APLValue, pos: Position? = null): String {
    val dimensions = array.dimensions
    if (dimensions.size != 1) {
        throw IncompatibleTypeException("Argument is not a string", pos)
    }

    val buf = StringBuilder()
    for (i in 0 until array.size) {
        val v = array.valueAt(i)
        if (v !is APLChar) {
            throw IncompatibleTypeException("Argument is not a string", pos)
        }
        buf.append(v.asString())
    }

    return buf.toString()
}

suspend fun arrayAsString(array: APLValue, style: FormatStyle): String {
    val v = array.collapse() // This is to prevent multiple evaluations during printing
    return when {
        isNullValue(v) -> renderNullValue()
        isStringValue(v) -> renderStringValue(v, style)
        else -> encloseInBox(v)
    }
}

class ConstantArray(
    override val dimensions: Dimensions,
    private val value: APLValue
) : APLArray() {

    override suspend fun valueAt(p: Int) = value
}

class APLArrayImpl(
    override val dimensions: Dimensions,
    private val values: Array<APLValue>
) : APLArray() {

    override suspend fun valueAt(p: Int) = values[p]
    override fun toString() = Arrays.toString(values)

    companion object {
        inline fun make(dimensions: Dimensions, fn: (index: Int) -> APLValue): APLArrayImpl {
            val content = Array(dimensions.contentSize()) { index -> fn(index) }
            return APLArrayImpl(dimensions, content)
        }
    }
}

class EnclosedAPLValue(val value: APLValue) : APLArray() {
    override val dimensions: Dimensions
        get() = emptyDimensions()

    override suspend fun valueAt(p: Int): APLValue {
        if (p != 0) {
            throw APLIndexOutOfBoundsException("Attempt to read from a non-zero index ")
        }
        return value
    }
}

class APLChar(val value: Int) : APLSingleValue() {
    override val aplValueType: APLValueType get() = APLValueType.CHAR
    fun asString() = charToString(value)
    override suspend fun formatted(style: FormatStyle) = when (style) {
        FormatStyle.PLAIN -> charToString(value)
        FormatStyle.PRETTY -> "@${charToString(value)}"
        FormatStyle.READABLE -> "@${charToString(value)}"
    }

    override suspend fun compareEquals(reference: APLValue) = reference is APLChar && value == reference.value

    override suspend fun compare(reference: APLValue, pos: Position?): Int {
        if (reference is APLChar) {
            return value.compareTo(reference.value)
        } else {
            throw IncompatibleTypeException("Chars must be compared to chars", pos)
        }
    }

    override fun toString() = "APLChar['${asString()}' 0x${value.toString(16)}]"

    override suspend fun makeKey() = value
}

fun makeAPLString(s: String) = APLString(s)

class APLString(val content: IntArray) : APLArray() {
    constructor(string: String) : this(string.asCodepointList().toIntArray())

    override val dimensions = dimensionsOfSize(content.size)
    override suspend fun valueAt(p: Int) = APLChar(content[p])

    override suspend fun collapseInt() = this
}

private val NULL_DIMENSIONS = dimensionsOfSize(0)

class APLNullValue : APLArray() {
    override val dimensions get() = NULL_DIMENSIONS
    override suspend fun valueAt(p: Int) = throw APLIndexOutOfBoundsException("Attempt to read a value from the null value")
}

abstract class DeferredResultArray : APLArray() {
    override suspend fun unwrapDeferredValue(): APLValue {
        return if (dimensions.isEmpty()) valueAt(0).unwrapDeferredValue() else this
    }
}

class APLSymbol(val value: Symbol) : APLSingleValue() {
    override val aplValueType: APLValueType get() = APLValueType.SYMBOL
    override suspend fun formatted(style: FormatStyle) =
        when (style) {
            FormatStyle.PLAIN -> "${value.namespace.name}:${value.symbolName}"
            FormatStyle.PRETTY -> "${value.namespace.name}:${value.symbolName}"
            FormatStyle.READABLE -> "'${value.namespace.name}:${value.symbolName}"
        }

    override suspend fun compareEquals(reference: APLValue) = reference is APLSymbol && value == reference.value

    override suspend fun compare(reference: APLValue, pos: Position?): Int {
        if (reference is APLSymbol) {
            return value.compareTo(reference.value)
        } else {
            throw IncompatibleTypeException("Symbols can't be compared to values with type: ${reference.aplValueType.typeName}", pos)
        }
    }

    override suspend fun ensureSymbol(pos: Position?) = this

    override suspend fun makeKey() = value
}

/**
 * This class represents a closure. It wraps a function and a context to use when calling the closure.
 *
 * @param fn the function that is wrapped by the closure
 * @param previousContext the context to use when calling the function
 */
class LambdaValue(private val fn: APLFunction, private val previousContext: RuntimeContext) : APLSingleValue() {
    override val aplValueType: APLValueType get() = APLValueType.LAMBDA_FN
    override suspend fun formatted(style: FormatStyle) =
        when (style) {
            FormatStyle.PLAIN -> "function"
            FormatStyle.READABLE -> throw IllegalArgumentException("Functions can't be printed in readable form")
            FormatStyle.PRETTY -> "function"
        }

    override suspend fun compareEquals(reference: APLValue) = this === reference

    override suspend fun makeKey() = fn

    fun makeClosure(): APLFunction {
        return object : APLFunction(fn.pos) {
            override suspend fun eval1Arg(context: RuntimeContext, a: APLValue, axis: APLValue?): APLValue {
                return fn.eval1Arg(previousContext, a, axis)
            }

            override suspend fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue, axis: APLValue?): APLValue {
                return fn.eval2Arg(previousContext, a, b, axis)
            }

            override fun identityValue() = fn.identityValue()
        }
    }
}
