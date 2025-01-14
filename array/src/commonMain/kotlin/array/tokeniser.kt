package array

import array.complex.Complex

abstract class Token {
    open fun formatted(): String {
        return this::class.simpleName ?: toString()
    }
}

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
object FnDefArrow : Token()
object APLNullSym : Token()
object QuotePrefix : Token()
object LambdaToken : Token()
object ApplyToken : Token()
object ListSeparator : Token()
object Newline : Token()
object NamespaceToken : Token()
object ImportToken : Token()
object DefsyntaxSubToken : Token()
object DefsyntaxToken : Token()
object IncludeToken : Token()
object DeclareToken : Token()

class Namespace(val name: String) {
    private val symbols = HashMap<String, NamespaceEntry>()
    private val imports = ArrayList<Namespace>()

    override fun toString() = "Namespace[name=${name}]"

    fun findSymbol(name: String, includePrivate: Boolean = false): Symbol? {
        val e = symbols[name]
        return when {
            e == null -> null
            includePrivate -> e.symbol
            e.exported -> e.symbol
            else -> null
        }
    }

    fun internSymbol(name: String): Symbol {
        val e = symbols[name]
        return if (e == null) {
            Symbol(name, this).also { sym -> symbols[name] = NamespaceEntry(sym, false) }
        } else {
            e.symbol
        }
    }

    fun addImport(namespace: Namespace) {
        if (namespace !== this) {
            imports.add(namespace)
        }
    }

    fun internAndExport(name: String): Symbol {
        val e = symbols[name]
        val e2 = if (e == null) {
            NamespaceEntry(Symbol(name, this), true).also { symbols[name] = it }
        } else {
            e.exported = true
            e
        }
        return e2.symbol
    }

    fun imports(): List<Namespace> = imports

    /**
     * If [symbol] is interned in this namespace, mark it as exported. Otherwise throw
     * an exception.
     */
    fun exportIfInterned(symbol: Symbol) {
        val v = symbols[symbol.symbolName]
        if (v == null || v.symbol !== symbol) {
            throw IllegalArgumentException("Symbol is not interned in namespace")
        }
        v.exported = true
    }

    private class NamespaceEntry(val symbol: Symbol, var exported: Boolean)
}

class Symbol(val symbolName: String, val namespace: Namespace) : Token(), Comparable<Symbol> {
    override fun toString() = "Symbol[name=${nameWithNamespace()}]"
    override fun compareTo(other: Symbol) = symbolName.compareTo(other.symbolName)
    override fun hashCode() = symbolName.hashCode()
    override fun equals(other: Any?) = other != null && other is Symbol && symbolName == other.symbolName && namespace === other.namespace
    override fun formatted() = nameWithNamespace()

    fun nameWithNamespace() = "${namespace.name}:${symbolName}"
}

class StringToken(val value: String) : Token()
class ParsedLong(val value: Long) : Token()
class ParsedDouble(val value: Double) : Token()
class ParsedComplex(val value: Complex) : Token()
class ParsedCharacter(val value: Int) : Token()

interface SourceLocation {
    fun sourceText(): String
    fun open(): CharacterProvider
}

class StringSourceLocation(private val sourceText: String) : SourceLocation {
    override fun sourceText() = sourceText
    override fun open() = StringCharacterProvider(sourceText)
}

class FileSourceLocation(private val file: String) : SourceLocation {
    override fun sourceText(): String {
        TODO("not implemented")
    }

    override fun open() = openCharFile(file)
}

data class Position(val source: SourceLocation, val line: Int, val col: Int, val name: String? = null) {
    fun withName(s: String) = copy(name = s)
}

class TokenGenerator(val engine: Engine, contentArg: SourceLocation) {
    private val content = PushBackCharacterProvider(contentArg)
    private val singleCharFunctions: MutableSet<String>
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
        "⋄" to StatementSeparator,
        "∇" to FnDefSym,
        "⇐" to FnDefArrow,
        "⍬" to APLNullSym,
        "λ" to LambdaToken,
        "⍞" to ApplyToken,
        ";" to ListSeparator)

    private val stringToKeywordMap = hashMapOf(
        "namespace" to NamespaceToken,
        "import" to ImportToken,
        "defsyntaxsub" to DefsyntaxSubToken,
        "defsyntax" to DefsyntaxToken,
        "use" to IncludeToken,
        "declare" to DeclareToken)

    init {
        singleCharFunctions = hashSetOf(
            "!", "#", "%", "&", "*", "+", ",", "-", "/", "<", "=", ">", "?", "^", "|",
            "~", "¨", "×", "÷", "↑", "→", "↓", "∊", "∘", "∧", "∨", "∩", "∪", "∼", "≠", "≡",
            "≢", "≤", "≥", "⊂", "⊃", "⊖", "⊢", "⊣", "⊤", "⊥", "⋆", "⌈", "⌊", "⌶", "⌷", "⌹",
            "⌺", "⌽", "⌿", "⍀", "⍉", "⍋", "⍎", "⍒", "⍕", "⍙", "⍞", "⍟", "⍠", "⍣", "⍤", "⍥",
            "⍨", "⍪", "⍫", "⍱", "⍲", "⍳", "⍴", "⍵", "⍶", "⍷", "⍸", "⍹", "⍺", "◊",
            "○", "$", "¥", "χ", "\\", ".", "∵", "⍓")
    }

    fun registerSingleCharFunction(name: String) {
        assertx(name.asCodepointList().size == 1)
        singleCharFunctions.add(name)
    }

    fun peekToken(): Token {
        return nextToken().also { token ->
            pushBackToken(token)
        }
    }

    inline fun <reified T : Token> nextTokenWithType(): T {
        val (token, pos) = nextTokenWithPosition()
        if (token is T) {
            return token
        } else {
            throw UnexpectedToken(token, pos)
        }
    }

    inline fun <reified T : Token> nextTokenAndPosWithType(): Pair<T, Position> {
        val (token, pos) = nextTokenWithPosition()
        if (token is T) {
            return Pair(token, pos)
        } else {
            throw UnexpectedToken(token, pos)
        }
    }

    fun close() {
        content.close()
    }

    fun nextTokenOrSpace(): Pair<Token, Position> {
        val posBeforeParse = content.pos()
        fun mkpos(token: Token) = Pair(token, posBeforeParse)

        if (!pushBackQueue.isEmpty()) {
            return mkpos(pushBackQueue.removeAt(pushBackQueue.size - 1))
        }

        val ch = content.nextCodepoint()
        if (ch == null) {
            close()
            return mkpos(EndOfFile)
        }

        charToTokenMap[charToString(ch)]?.also { return mkpos(it) }

        return mkpos(
            when {
                singleCharFunctions.contains(charToString(ch)) -> {
                    val name = charToString(ch)
                    findSymbolInImports(name) ?: engine.internSymbol(name, engine.currentNamespace)
                }
                isNegationSign(ch) || isDigit(ch) -> {
                    content.pushBack()
                    collectNumber()
                }
                isNewline(ch) -> Newline
                isWhitespace(ch) -> Whitespace
                isCharQuote(ch) -> collectChar()
                isSymbolStartChar(ch) -> collectSymbolOrKeyword(ch, posBeforeParse)
                isQuoteChar(ch) -> collectString()
                isCommentChar(ch) -> skipUntilNewline()
                isQuotePrefixChar(ch) -> QuotePrefix
                isBackquote(ch) -> skipNextNewline()
                else -> throw UnexpectedSymbol(ch, posBeforeParse)
            }
        )
    }

    fun pushBackToken(token: Token) {
        pushBackQueue.add(token)
    }

    private fun isNegationSign(ch: Int) = ch == '¯'.code
    private fun isQuoteChar(ch: Int) = ch == '"'.code
    private fun isCommentChar(ch: Int) = ch == '⍝'.code
    private fun isSymbolStartChar(ch: Int) = isLetter(ch) || ch == '_'.code || ch == ':'.code
    private fun isSymbolContinuation(ch: Int) = isSymbolStartChar(ch) || isDigit(ch)
    private fun isNumericConstituent(ch: Int) =
        isDigit(ch) || isNegationSign(ch) || ch == '.'.code || ch == 'j'.code || ch == 'J'.code

    private fun isCharQuote(ch: Int) = ch == '@'.code
    private fun isQuotePrefixChar(ch: Int) = ch == '\''.code
    private fun isNewline(ch: Int) = ch == '\n'.code
    private fun isBackquote(ch: Int) = ch == '`'.code

    private fun skipUntilNewline(): Whitespace {
        while (true) {
            val ch = content.nextCodepoint()
            if (ch == null || ch == '\n'.code) {
                break
            }
        }
        return Whitespace
    }

    private fun skipNextNewline(): Whitespace {
        while (true) {
            val (ch, pos) = content.nextCodepointWithPos()
            when {
                ch == null -> throw ParseException("End of file after continuation character", pos)
                isNewline(ch) -> return Whitespace
                !isWhitespace(ch) -> throw ParseException("Non-whitespace characters after continuation character", pos)
            }
        }
    }

    private fun collectChar(): ParsedCharacter {
        val (ch, pos) = content.nextCodepointWithPos()
        if (ch == null) {
            throw ParseException("Incomplete character in input", pos)
        }
        return ParsedCharacter(ch)
    }

    private fun collectNumber(): Token {
        val buf = StringBuilder()
        var foundComplex = false
        val posStart = content.pos()
        loop@ while (true) {
            val posBeforeParse = content.pos()
            val ch = content.nextCodepoint() ?: break
            when {
                ch == 'j'.code || ch == 'J'.code -> {
                    if (foundComplex) {
                        throw IllegalNumberFormat("Garbage after number", posBeforeParse)
                    }
                    foundComplex = true
                }
                isLetter(ch) -> throw IllegalNumberFormat("Garbage after number", posBeforeParse)
                !isNumericConstituent(ch) -> {
                    content.pushBack()
                    break@loop
                }
            }
            buf.addCodepoint(ch)
        }

        val s = buf.toString()
        for (parser in NUMBER_PARSERS) {
            val result = parser.process(s)
            if (result != null) {
                return result
            }
        }
        throw IllegalNumberFormat("Content cannot be parsed as a number", posStart)
    }

    private fun collectSymbolOrKeyword(firstChar: Int, posBeforeParse: Position): Token {
        val buf = StringBuilder()
        buf.addCodepoint(firstChar)
        var foundColon = false
        while (true) {
            val ch = content.nextCodepoint() ?: break
            if (ch == ':'.code) {
                if (foundColon) {
                    throw ParseException("Multiple : characters in symbol")
                }
                foundColon = true
            } else if (!isSymbolContinuation(ch)) {
                content.pushBack()
                break
            }
            buf.addCodepoint(ch)
        }
        val name = buf.toString()
        val keywordResult = "^:([^:]+)$".toRegex().matchEntire(name)
        if (keywordResult != null) {
            return engine.keywordNamespace.internSymbol(keywordResult.groups.get(1)!!.value)
        } else {
            val result =
                "^(?:([^:]+):)?([^:]+)$".toRegex().matchEntire(name) ?: throw ParseException("Malformed symbol: '${name}'", posBeforeParse)
            val symbolString = result.groups.get(2)!!.value
            val nsName = result.groups.get(1)

            val namespace = if (nsName == null) {
                val keyword = stringToKeywordMap[name]
                if (keyword != null) {
                    return keyword
                }
                val sym = findSymbolInImports(symbolString)
                if (sym != null) {
                    return sym
                }
                null
            } else {
                engine.makeNamespace(nsName.value)
            }

            return engine.internSymbol(symbolString, namespace)
        }
    }

    private fun findSymbolInImports(name: String): Symbol? {
        engine.currentNamespace.findSymbol(name, true)?.also { sym -> return sym }
        engine.currentNamespace.imports().forEach { namespace ->
            namespace.findSymbol(name, false)?.also { sym -> return sym }
        }
        return null
    }

    private fun collectString(): Token {
        val buf = StringBuilder()
        while (true) {
            val ch = content.nextCodepoint() ?: throw ParseException("End of input in the middle of string", content.pos())
            if (ch == '"'.code) {
                break
            } else if (ch == '\\'.code) {
                val next = content.nextCodepoint() ?: throw ParseException("End of input in the middle of string", content.pos())
                buf.addCodepoint(next)
            } else {
                buf.addCodepoint(ch)
            }
        }
        return StringToken(buf.toString())
    }

    fun nextToken(): Token {
        return nextTokenWithPosition().first
    }

    fun nextTokenWithPosition(): Pair<Token, Position> {
        while (true) {
            val tokenAndPos = nextTokenOrSpace()
            if (tokenAndPos.first != Whitespace) {
                return tokenAndPos
            }
        }
    }

    inline fun iterateUntilToken(endToken: Token, fn: (Token, Position) -> Unit) {
        while (true) {
            val (token, pos) = nextTokenWithPosition()
            if (token == endToken) {
                break
            }
            fn(token, pos)
        }
    }

    private class NumberParser(val pattern: Regex, val fn: (MatchResult) -> Token) {
        fun process(s: String): Token? {
            val result = pattern.matchEntire(s)
            return if (result == null) {
                null
            } else {
                fn(result)
            }
        }
    }

    companion object {
        private fun withNeg(isNegative: Boolean, s: String) = if (isNegative) "-$s" else s

        private val NUMBER_PARSERS = listOf(
            NumberParser("^(¯?)([0-9]+\\.[0-9]*)\$".toRegex()) { result ->
                val groups = result.groups
                val sign = groups.get(1) ?: throw IllegalNumberFormat("Illegal format of sign")
                val s = groups.get(2) ?: throw IllegalNumberFormat("Illegal format of number part")
                ParsedDouble(withNeg(sign.value != "", s.value).toDouble())
            },
            NumberParser("^(¯?)([0-9]+)$".toRegex()) { result ->
                val groups = result.groups
                val sign = groups.get(1) ?: throw IllegalNumberFormat("Illegal format of sign")
                val s = groups.get(2) ?: throw IllegalNumberFormat("Illegal format of number part")
                ParsedLong(withNeg(sign.value != "", s.value).toLong())
            },
            NumberParser("^(¯?)([0-9]+(?:\\.[0-9]*)?)[jJ](¯?)([0-9]+(?:\\.[0-9]*)?)$".toRegex()) { result ->
                val groups = result.groups
                val realSign = groups.get(1) ?: throw IllegalNumberFormat("Illegal format of sign in real part")
                val realS = groups.get(2) ?: throw IllegalNumberFormat("Illegal format of number in real part")
                val complexSign = groups.get(3) ?: throw IllegalNumberFormat("Illegal format of sign in complex")
                val complexS = groups.get(4) ?: throw IllegalNumberFormat("Illegal format of number in complex part")
                ParsedComplex(
                    Complex(
                        withNeg(realSign.value != "", realS.value).toDouble(),
                        withNeg(complexSign.value != "", complexS.value).toDouble()))
            }
        )
    }
}
