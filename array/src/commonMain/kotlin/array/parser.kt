package array

abstract class Token

object Whitespace : Token()
object EndOfFile : Token()
object OpenParen : Token()
object CloseParen : Token()
object OpenFnDef : Token()
object CloseFnDef : Token()
object OpenBracket : Token()
object CloseBracket : Token()
object StatementSeparator : Token()
object LeftArrow : Token()
object FnDefSym : Token()
object APLNullSym : Token()

class Symbol(val value: String) : Token(), Comparable<Symbol> {
    override fun toString() = "Symbol[name=${value}]"
    override fun compareTo(other: Symbol) = value.compareTo(other.value)
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?) = other != null && other is Symbol && value == other.value
}

class StringToken(val value: String) : Token()

class ParsedLong(val value: Long) : Token()

class TokenGenerator(val engine: Engine, contentArg: CharacterProvider) {
    private val content = PushBackCharacterProvider(contentArg)
    private val singleCharFunctions: Set<String>
    private val pushBackQueue = ArrayList<Token>()

    private val charToTokenMap = hashMapOf(
        "(" to OpenParen,
        ")" to CloseParen,
        "{" to OpenFnDef,
        "}" to CloseFnDef,
        "[" to OpenBracket,
        "]" to CloseBracket,
        "←" to LeftArrow,
        "◊" to StatementSeparator,
        "∇" to FnDefSym,
        "⍬" to APLNullSym
    )

    init {
        singleCharFunctions = hashSetOf(
            "!", "#", "%", "&", "*", "+", ",", "-", "/", "<", "=", ">", "?", "@", "^", "|",
            "~", "¨", "×", "÷", "↑", "→", "↓", "∊", "∘", "∧", "∨", "∩", "∪", "∼", "≠", "≡",
            "≢", "≤", "≥", "⊂", "⊃", "⊖", "⊢", "⊣", "⊤", "⊥", "⋆", "⌈", "⌊", "⌶", "⌷", "⌹",
            "⌺", "⌽", "⌿", "⍀", "⍉", "⍋", "⍎", "⍒", "⍕", "⍙", "⍞", "⍟", "⍠", "⍣", "⍤", "⍥",
            "⍨", "⍪", "⍫", "⍱", "⍲", "⍳", "⍴", "⍵", "⍶", "⍷", "⍸", "⍹", "⍺", "◊",
            "○", "$", "¥", "χ", "\\"
        )
    }

    inline fun <reified T : Token> nextTokenWithType(): T {
        val token = nextToken()
        if (token is T) {
            return token
        } else {
            throw UnexpectedToken(token)
        }
    }

    fun nextTokenOrSpace(): Token {
        if (!pushBackQueue.isEmpty()) {
            return pushBackQueue.removeAt(pushBackQueue.size - 1)
        }

        val ch = content.nextCodepoint() ?: return EndOfFile

        charToTokenMap[charToString(ch)]?.also { return it }

        return when {
            singleCharFunctions.contains(charToString(ch)) -> engine.internSymbol(charToString(ch))
            isNegationSign(ch) -> collectNegativeNumber()
            isDigit(ch) -> collectNumber(ch)
            isWhitespace(ch) -> Whitespace
            isLetter(ch) -> collectSymbol(ch)
            isQuoteChar(ch) -> collectString()
            isCommentChar(ch) -> skipUntilNewline()
            else -> throw UnexpectedSymbol(ch)
        }
    }

    fun pushBackToken(token: Token) {
        pushBackQueue.add(token)
    }

    private fun isNegationSign(ch: Int) = ch == '¯'.toInt()
    private fun isQuoteChar(ch: Int) = ch == '"'.toInt()
    private fun isCommentChar(ch: Int) = ch == '⍝'.toInt()

    private fun skipUntilNewline(): Whitespace {
        while(true) {
            val ch = content.nextCodepoint()
            if(ch == null || ch == '\n'.toInt()) {
                break
            }
        }
        return Whitespace
    }

    private fun collectNumber(firstChar: Int, isNegative: Boolean = false): ParsedLong {
        val buf = StringBuilder()
        buf.addCodepoint(firstChar)
        while (true) {
            val ch = content.nextCodepoint() ?: break
            if (isLetter(ch)) {
                throw IllegalNumberFormat("Illegal number format")
            }
            if (!isDigit(ch)) {
                content.pushBack(ch)
                break
            }
            buf.addCodepoint(ch)
        }
        return ParsedLong(buf.toString().toLong() * if (isNegative) -1 else 1)
    }

    private fun collectNegativeNumber(): ParsedLong {
        val ch = content.nextCodepoint()
        if (ch == null || !isDigit(ch)) {
            throw IllegalNumberFormat("Negation sign not followed by number")
        }
        return collectNumber(ch, true)
    }

    private fun collectSymbol(firstChar: Int): Symbol {
        val buf = StringBuilder()
        buf.addCodepoint(firstChar)
        while (true) {
            val ch = content.nextCodepoint() ?: break
            if (!isLetter(ch) || isDigit(ch)) {
                content.pushBack(ch)
                break
            }
            buf.addCodepoint(ch)
        }
        return engine.internSymbol(buf.toString())
    }

    private fun collectString(): Token {
        val buf = StringBuilder()
        while (true) {
            val ch = content.nextCodepoint() ?: throw ParseException("End of input in the middle of string")
            if (ch == '"'.toInt()) {
                break
            } else if (ch == '\\'.toInt()) {
                val next = content.nextCodepoint() ?: throw ParseException("End of input in the middle of string")
                buf.addCodepoint(next)
            } else {
                buf.addCodepoint(ch)
            }
        }
        return StringToken(buf.toString())
    }

    fun nextToken(): Token {
        while (true) {
            val token = nextTokenOrSpace()
            if (token != Whitespace) {
                return token
            }
        }
    }
}

interface Instruction {
    fun evalWithContext(context: RuntimeContext): APLValue
}

class InstructionList(val instructions: List<Instruction>) : Instruction {
    override fun evalWithContext(context: RuntimeContext): APLValue {
        var result: APLValue? = null
        for (instr in instructions) {
            result = instr.evalWithContext(context)
        }
        if (result == null) {
            throw IllegalStateException("Empty instruction list")
        }
        return result
    }
}

class FunctionCall1Arg(val fn: APLFunction, val rightArgs: Instruction, val axis: Instruction?) : Instruction {
    override fun evalWithContext(context: RuntimeContext) =
        fn.eval1Arg(context, rightArgs.evalWithContext(context), axis?.evalWithContext(context))

    override fun toString() = "FunctionCall1Arg(fn=${fn}, rightArgs=${rightArgs})"
}

class FunctionCall2Arg(val fn: APLFunction, val leftArgs: Instruction, val rightArgs: Instruction, val axis: Instruction?) : Instruction {
    override fun evalWithContext(context: RuntimeContext): APLValue {
        val leftValue = rightArgs.evalWithContext(context)
        val rightValue = leftArgs.evalWithContext(context)
        val axisValue = axis?.evalWithContext(context)
        return fn.eval2Arg(context, rightValue, leftValue, axisValue)
    }

    override fun toString() = "FunctionCall2Arg(fn=${fn}, leftArgs=${leftArgs}, rightArgs=${rightArgs})"
}

class VariableRef(val name: Symbol) : Instruction {
    override fun evalWithContext(context: RuntimeContext): APLValue {
        return context.lookupVar(name) ?: throw VariableNotAssigned(name)
    }

    override fun toString() = "Var(${name})"
}

class Literal1DArray(val values: List<Instruction>) : Instruction {
    override fun evalWithContext(context: RuntimeContext): APLValue {
        val size = values.size
        val result = Array<APLValue?>(size) { null }
        for (i in (size - 1) downTo 0) {
            result[i] = values[i].evalWithContext(context)
        }
        return APLArrayImpl(oneDimensionalDimensions(size)) { result[it]!! }
    }

    override fun toString() = "Literal1DArray(${values})"
}

class LiteralScalarValue(val value: Instruction) : Instruction {
    override fun evalWithContext(context: RuntimeContext) = value.evalWithContext(context)
    override fun toString() = "LiteralScalarValue(${value})"
}

class LiteralNumber(val value: Long) : Instruction {
    override fun evalWithContext(context: RuntimeContext) = APLLong(value)
    override fun toString() = "LiteralNumber(value=$value)"
}

class LiteralSymbol(name: Symbol) : Instruction {
    private val value = APLSymbol(name)
    override fun evalWithContext(context: RuntimeContext): APLValue = value
}

class LiteralAPLNullValue : Instruction {
    override fun evalWithContext(context: RuntimeContext) = APLNullValue()
}

class LiteralStringValue(val s: String) : Instruction {
    override fun evalWithContext(context: RuntimeContext) = makeAPLString(s)
}

class AssignmentInstruction(val name: Symbol, val instr: Instruction) : Instruction {
    override fun evalWithContext(context: RuntimeContext): APLValue {
        val res = instr.evalWithContext(context)
        context.setVar(name, res)
        return res
    }
}

class UserFunction(
    private val arg: Symbol,
    private val instr: Instruction,
    val source: String? = null
) : APLFunction {
    override fun eval1Arg(context: RuntimeContext, a: APLValue, axis: APLValue?): APLValue {
        val inner = context.link()
        inner.setVar(arg, a)
        return instr.evalWithContext(inner)
    }

    override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue, axis: APLValue?): APLValue {
        TODO("not implemented")
    }
}

fun parseValueToplevel(engine: Engine, tokeniser: TokenGenerator, endToken: Token): Instruction {
    val statementList = ArrayList<Instruction>()

    while (true) {
        val (instr, lastToken) = parseValue(engine, tokeniser)
        statementList.add(instr)
        if (lastToken == endToken) {
            assertx(!statementList.isEmpty())
            return if (statementList.size == 1) instr else InstructionList(statementList)
        } else if (lastToken != StatementSeparator) {
            throw UnexpectedToken(lastToken)
        }
    }
}

fun parseValue(engine: Engine, tokeniser: TokenGenerator): Pair<Instruction, Token> {
    val leftArgs = ArrayList<Instruction>()

    fun makeResultList(): Instruction {
        if (leftArgs.isEmpty()) {
            throw IllegalStateException("Argument list should not be empty")
        }
        return if (leftArgs.size == 1) {
            LiteralScalarValue(leftArgs[0])
        } else {
            Literal1DArray(leftArgs)
        }
    }

    fun processFn(fn: APLFunction): Pair<Instruction, Token> {
        val axis = parseAxis(engine, tokeniser)
        val parsedFn = parseOperator(fn, engine, tokeniser)
        val (rightValue, lastToken) = parseValue(engine, tokeniser)
        return if (leftArgs.isEmpty()) {
            Pair(FunctionCall1Arg(parsedFn, rightValue, axis), lastToken)
        } else {
            Pair(FunctionCall2Arg(parsedFn, makeResultList(), rightValue, axis), lastToken)
        }
    }

    fun processAssignment(engine: Engine, tokeniser: TokenGenerator): Pair<Instruction, Token> {
        // Ensure that the left argument to leftarrow is a single symbol
        unless(leftArgs.size == 1) {
            throw IncompatibleTypeException("Can only assign to a single variable")
        }
        val dest = leftArgs[0]
        if (dest !is VariableRef) {
            throw IncompatibleTypeException("Attempt to assign to a type which is not a variable")
        }
        val (rightValue, lastToken) = parseValue(engine, tokeniser)
        return Pair(AssignmentInstruction(dest.name, rightValue), lastToken)
    }

    fun processFunctionDefinition(engine: Engine, tokeniser: TokenGenerator): Instruction {
        if (!leftArgs.isEmpty()) {
            throw ParseException("Function definition with non-null left argument")
        }

        val name = tokeniser.nextTokenWithType<Symbol>()
        val arg = tokeniser.nextTokenWithType<Symbol>()
        // Read the opening brace
        tokeniser.nextTokenWithType<OpenFnDef>()
        // Parse like a normal function definition
        val instr = parseValueToplevel(engine, tokeniser, CloseFnDef)

        val obj = UserFunction(arg, instr)

        engine.registerFunction(name, obj)
        return LiteralSymbol(name)
    }

    while (true) {
        val token = tokeniser.nextToken()
        if (token == CloseParen || token == EndOfFile || token == StatementSeparator || token == CloseFnDef || token == CloseBracket) {
            return Pair(makeResultList(), token)
        }

        when (token) {
            is Symbol -> {
                val fn = engine.getFunction(token)
                if (fn != null) {
                    return processFn(fn)
                } else {
                    leftArgs.add(VariableRef(token))
                }
            }
            is OpenFnDef -> return processFn(parseFnDefinition(engine, tokeniser))
            is OpenParen -> leftArgs.add(parseValueToplevel(engine, tokeniser, CloseParen))
            is ParsedLong -> leftArgs.add(LiteralNumber(token.value))
            is LeftArrow -> return processAssignment(engine, tokeniser)
            is FnDefSym -> leftArgs.add(processFunctionDefinition(engine, tokeniser))
            is APLNullSym -> leftArgs.add(LiteralAPLNullValue())
            is StringToken -> leftArgs.add(LiteralStringValue(token.value))
            else -> throw UnexpectedToken(token)
        }
    }
}

fun parseFnDefinition(
    engine: Engine,
    tokeniser: TokenGenerator,
    leftArgName: Symbol? = null,
    rightArgName: Symbol? = null
): DeclaredFunction {
    val instruction = parseValueToplevel(engine, tokeniser, CloseFnDef)
    return DeclaredFunction(instruction, leftArgName ?: engine.internSymbol("⍺"), rightArgName ?: engine.internSymbol("⍵"))
}

fun parseOperator(fn: APLFunction, engine: Engine, tokeniser: TokenGenerator): APLFunction {
    var currentFn = fn
    var token: Token
    while (true) {
        token = tokeniser.nextToken()
        if (token is Symbol) {
            val op = engine.getOperator(token) ?: break
            val axis = parseAxis(engine, tokeniser)
            currentFn = op.combineFunction(currentFn, axis)
        } else {
            break
        }
    }
    if (token != EndOfFile) {
        tokeniser.pushBackToken(token)
    }
    return currentFn
}

fun parseAxis(engine: Engine, tokeniser: TokenGenerator): Instruction? {
    val token = tokeniser.nextToken()
    if (token != OpenBracket) {
        tokeniser.pushBackToken(token)
        return null
    }

    return parseValueToplevel(engine, tokeniser, CloseBracket)
}
