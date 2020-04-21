package array

data class InstrTokenHolder(val instruction: Optional<Instruction>, val lastToken: Token, val pos: Position)

class APLParser(val tokeniser: TokenGenerator) {

    fun parseValueToplevel(endToken: Token): Instruction {
        val statementList = ArrayList<Instruction>()
        while (true) {
            val holder = parseList()
            holder.instruction.withValueIfExists { instr ->
                statementList.add(instr)
            }
            if (holder.lastToken == endToken) {
                assertx(!statementList.isEmpty())
                return when (statementList.size) {
                    0 -> throw ParseException("Empty statement list")
                    1 -> statementList[0]
                    else -> InstructionList(statementList)
                }
            } else if (holder.lastToken != StatementSeparator && holder.lastToken != Newline) {
                throw UnexpectedToken(holder.lastToken, holder.pos)
            }
        }
    }

    private fun parseList(): InstrTokenHolder {
        val statementList = ArrayList<Instruction>()
        while (true) {
            val holder = parseValue()
            if (holder.lastToken == ListSeparator) {
                statementList.add(holder.instruction.withValue({ it }, { LiteralAPLNullValue(holder.pos) }))
            } else {
                holder.instruction.withValueIfExists { instr ->
                    statementList.add(instr)
                }
                val list = when {
                    statementList.isEmpty() -> Optional.empty()
                    statementList.size == 1 -> Optional.make(statementList[0])
                    else -> Optional.make(ParsedAPLList(statementList))
                }
                return InstrTokenHolder(list, holder.lastToken, holder.pos)
            }
        }
    }

    private fun makeResultList(leftArgs: List<Instruction>): Optional<Instruction> {
        return when {
            leftArgs.isEmpty() -> Optional.empty()
            leftArgs.size == 1 -> Optional.make(LiteralScalarValue(leftArgs[0]))
            else -> Optional.make(Literal1DArray(leftArgs))
        }
    }

    private fun processFn(fn: APLFunctionDescriptor, pos: Position, leftArgs: List<Instruction>): InstrTokenHolder {
        val axis = parseAxis()
        val parsedFn = parseOperator(fn)
        val (rightValue, lastToken) = parseValue()
        return rightValue.withValue({ instr ->
            if (leftArgs.isEmpty()) {
                InstrTokenHolder(Optional.make(FunctionCall1Arg(parsedFn.make(pos), instr, axis, pos)), lastToken, pos)
            } else {
                makeResultList(leftArgs).withValue({ leftArgs ->
                    InstrTokenHolder(Optional.make(FunctionCall2Arg(parsedFn.make(pos), leftArgs, instr, axis, pos)), lastToken, pos)
                }, { throw ParseException("No left-side argument", pos) })
            }
        }, { throw ParseException("Missing right-side argument to function", pos) })
    }

    private fun processAssignment(pos: Position, leftArgs: List<Instruction>): InstrTokenHolder {
        // Ensure that the left argument to leftarrow is a single symbol
        unless(leftArgs.size == 1) {
            throw IncompatibleTypeParseException("Can only assign to a single variable", pos)
        }
        val dest = leftArgs[0]
        if (dest !is VariableRef) {
            throw IncompatibleTypeParseException("Attempt to assign to a type which is not a variable", pos)
        }
        val (rightValue, lastToken, assignmentPos) = parseValue()
        return rightValue.withValue({ instr ->
            InstrTokenHolder(Optional.make(AssignmentInstruction(dest.name, instr, pos)), lastToken, pos)
        }, { throw ParseException("No right-side value in assignment instruction", assignmentPos) })
    }

    private fun parseFnArgs(): List<Symbol> {
        val initial = tokeniser.nextToken()
        if (initial != OpenParen) {
            tokeniser.pushBackToken(initial)
            return emptyList()
        }

        val result = ArrayList<Symbol>()

        val (token, pos) = tokeniser.nextTokenWithPosition()
        when (token) {
            is CloseParen -> return result
            is Symbol -> result.add(token)
            else -> throw ParseException("Token is not a symbol: ${token}", pos)
        }
        while (true) {
            val (newToken, newPos) = tokeniser.nextTokenWithPosition()
            when {
                newToken == CloseParen -> return result
                newToken != ListSeparator -> throw ParseException("Expected separator or end of list, got ${newToken}", newPos)
                else -> {
                    val symbolToken = tokeniser.nextTokenWithType<Symbol>()
                    result.add(symbolToken)
                }
            }
        }
    }

    data class DefinedUserFunction(val fn: UserFunction, val name: Symbol, val pos: Position)

    private fun processFunctionDefinition(pos: Position, leftArgs: List<Instruction>): Instruction {
        if (leftArgs.isNotEmpty()) {
            throw ParseException("Function definition with non-null left argument", pos)
        }
        val definedUserFunction = parseUserDefinedFn(pos)
        registerDefinedUserFunction(definedUserFunction)
        return LiteralSymbol(definedUserFunction.name, definedUserFunction.pos)
    }

    fun registerDefinedUserFunction(definedUserFunction: DefinedUserFunction) {
        tokeniser.engine.registerFunction(definedUserFunction.name, definedUserFunction.fn)
    }

    fun parseUserDefinedFn(pos: Position): DefinedUserFunction {
        val leftFnArgs = parseFnArgs()
        val name = tokeniser.nextTokenWithType<Symbol>()
        val rightFnArgs = parseFnArgs()

        // Ensure that no arguments are duplicated
        val argNames = HashSet<Symbol>()
        fun checkArgs(list: List<Symbol>) {
            list.forEach { element ->
                if (argNames.contains(element)) {
                    throw ParseException("Symbol ${element.symbolName} in multiple positions", pos)
                }
                argNames.add(element)
            }
        }
        checkArgs(leftFnArgs)
        checkArgs(rightFnArgs)

        // Read the opening brace
        tokeniser.nextTokenWithType<OpenFnDef>()
        // Parse like a normal function definition
        val instr = parseValueToplevel(CloseFnDef)

        return DefinedUserFunction(UserFunction(leftFnArgs, rightFnArgs, instr), name, pos)
    }

    private fun parseValue(): InstrTokenHolder {
        val engine = tokeniser.engine
        val leftArgs = ArrayList<Instruction>()

        fun addLeftArg(instr: Instruction) {
            val (token, pos) = tokeniser.nextTokenWithPosition()
            val instrWithIndex = if (token == OpenBracket) {
                val indexInstr = parseValueToplevel(CloseBracket)
                ArrayIndex(instr, indexInstr, pos)
            } else {
                tokeniser.pushBackToken(token)
                instr
            }
            leftArgs.add(instrWithIndex)
        }

        while (true) {
            val (token, pos) = tokeniser.nextTokenWithPosition()
            if (listOf(CloseParen, EndOfFile, StatementSeparator, CloseFnDef, CloseBracket, ListSeparator, Newline).contains(token)) {
                return InstrTokenHolder(makeResultList(leftArgs), token, pos)
            }

            when (token) {
                is Symbol -> {
                    val fn = engine.getFunction(token)
                    if (fn != null) {
                        return processFn(fn, pos, leftArgs)
                    } else {
                        addLeftArg(VariableRef(token, pos))
                    }
                }
                is OpenParen -> addLeftArg(parseValueToplevel(CloseParen))
                is OpenFnDef -> return processFn(parseFnDefinition(pos), pos, leftArgs)
                is ParsedLong -> leftArgs.add(LiteralInteger(token.value, pos))
                is ParsedDouble -> leftArgs.add(LiteralDouble(token.value, pos))
                is ParsedComplex -> leftArgs.add(LiteralComplex(token.value, pos))
                is ParsedCharacter -> leftArgs.add(LiteralCharacter(token.value, pos))
                is LeftArrow -> return processAssignment(pos, leftArgs)
                is FnDefSym -> leftArgs.add(processFunctionDefinition(pos, leftArgs))
                is APLNullSym -> leftArgs.add(LiteralAPLNullValue(pos))
                is StringToken -> leftArgs.add(LiteralStringValue(token.value, pos))
                is QuotePrefix -> leftArgs.add(LiteralSymbol(tokeniser.nextTokenWithType(), pos))
                is LambdaToken -> leftArgs.add(processLambda(pos))
                is ApplyToken -> return processFn(parseApplyDefinition(), pos, leftArgs)
                is IfToken -> addLeftArg(parseIfStatement(pos))
                else -> throw UnexpectedToken(token, pos)
            }
        }
    }

    private fun parseIfStatement(pos: Position): Instruction {
        tokeniser.nextTokenWithType<OpenParen>()
        val condition = parseValueToplevel(CloseParen)
        tokeniser.nextTokenWithType<OpenFnDef>()
        val thenStatement = parseValueToplevel(CloseFnDef)

        val token = tokeniser.nextToken()
        val elseStatement = if (token is ElseToken) {
            tokeniser.nextTokenWithType<OpenFnDef>()
            parseValueToplevel(CloseFnDef)
        } else {
            tokeniser.pushBackToken(token)
            LiteralAPLNullValue(pos)
        }
        return IfInstruction(condition, thenStatement, elseStatement, pos)
    }

    private fun parseApplyDefinition(): APLFunctionDescriptor {
        val (token, firstPos) = tokeniser.nextTokenWithPosition()
        val ref = when (token) {
            is Symbol -> VariableRef(token, firstPos)
            is OpenParen -> parseValueToplevel(CloseParen)
            else -> throw UnexpectedToken(token, firstPos)
        }
        return DynamicFunctionDescriptor(ref)
    }

    class EvalLambdaFnx(val fn: APLFunction, pos: Position) : Instruction(pos) {
        override fun evalWithContext(context: RuntimeContext): APLValue {
            return LambdaValue(fn)
        }
    }

    private fun processLambda(pos: Position): EvalLambdaFnx {
        val (token, pos2) = tokeniser.nextTokenWithPosition()
        return when (token) {
            is OpenFnDef -> {
                val fnDefinition = parseFnDefinition(pos)
                EvalLambdaFnx(fnDefinition.make(pos), pos)
            }
            else -> throw UnexpectedToken(token, pos2)
        }
    }

    private fun parseFnDefinition(
        pos: Position,
        leftArgName: Symbol? = null,
        rightArgName: Symbol? = null
    ): DeclaredFunction {
        val engine = tokeniser.engine
        val instruction = parseValueToplevel(CloseFnDef)
        return DeclaredFunction(instruction, leftArgName ?: engine.internSymbol("⍺"), rightArgName ?: engine.internSymbol("⍵"), pos)
    }

    private fun parseTwoArgOperatorArgument(): APLFunctionDescriptor {
        val (token, pos) = tokeniser.nextTokenWithPosition()
        return when (token) {
            is Symbol -> {
                val fn = tokeniser.engine.getFunction(token) ?: throw ParseException("Symbol is not a function", pos)
                parseOperator(fn)
            }
            is OpenFnDef -> {
                parseFnDefinition(pos)
            }
            else -> throw ParseException("Expected function, got: ${token}", pos)
        }
    }

    private fun parseOperator(fn: APLFunctionDescriptor): APLFunctionDescriptor {
        var currentFn = fn
        var token: Token
        loop@ while (true) {
            val readToken = tokeniser.nextToken()
            token = readToken
            if (token is Symbol) {
                val op = tokeniser.engine.getOperator(token) ?: break
                val axis = parseAxis()
                when (op) {
                    is APLOperatorOneArg -> currentFn = op.combineFunction(currentFn, axis)
                    is APLOperatorTwoArg -> currentFn = op.combineFunction(fn, parseTwoArgOperatorArgument(), axis)
                    else -> throw IllegalStateException("Operators must be either one or two arg")
                }

            } else {
                break
            }
        }
        tokeniser.pushBackToken(token)
        return currentFn
    }

    private fun parseAxis(): Instruction? {
        val token = tokeniser.nextToken()
        if (token != OpenBracket) {
            tokeniser.pushBackToken(token)
            return null
        }

        return parseValueToplevel(CloseBracket)
    }
}
