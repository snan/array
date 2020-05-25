package array

class CustomSyntax(
    val triggerSymbol: Symbol,
    val environment: Environment,
    val rulesList: List<SyntaxRule>,
    val instr: Instruction,
    val pos: Position)

class SyntaxRuleVariableBinding(val name: EnvironmentBinding, val value: Instruction)

interface SyntaxRule {
    fun isValid(token: Token): Boolean
    fun processRule(parser: APLParser, syntaxRuleBindings: MutableList<SyntaxRuleVariableBinding>)
}

class ConstantSyntaxRule(val symbolName: Symbol) : SyntaxRule {
    override fun isValid(token: Token) = token === symbolName

    override fun processRule(parser: APLParser, syntaxRuleBindings: MutableList<SyntaxRuleVariableBinding>) {
        val (sym, pos) = parser.tokeniser.nextTokenAndPosWithType<Symbol>()
        if (sym !== symbolName) {
            throw SyntaxRuleMismatch(symbolName, sym, pos)
        }
    }
}

class ValueSyntaxRule(val variable: EnvironmentBinding) : SyntaxRule {
    override fun isValid(token: Token) = token is OpenParen

    override fun processRule(parser: APLParser, syntaxRuleBindings: MutableList<SyntaxRuleVariableBinding>) {
        parser.tokeniser.nextTokenWithType<OpenParen>()
        val instr = parser.parseValueToplevel(CloseParen)
        syntaxRuleBindings.add(SyntaxRuleVariableBinding(variable, instr))
    }
}

/*
      defsyntax foo (:function X) {
        ⍞X 1
      }

      foo { print ⍵ }
 */

class FunctionSyntaxRule(val variable: EnvironmentBinding) : SyntaxRule {
    override fun isValid(token: Token) = token is OpenFnDef

    override fun processRule(parser: APLParser, syntaxRuleBindings: MutableList<SyntaxRuleVariableBinding>) {
        val (token, pos) = parser.tokeniser.nextTokenWithPosition()
        if (token !is OpenFnDef) {
            throw UnexpectedToken(token, pos)
        }
        val fnDefinition = parser.parseFnDefinition()
        syntaxRuleBindings.add(
            SyntaxRuleVariableBinding(
                variable,
                APLParser.EvalLambdaFnx(fnDefinition.make(pos), pos)))
    }
}

class OptionalSyntaxRule(val initialRule: SyntaxRule, val rest: List<SyntaxRule>) : SyntaxRule {
    override fun isValid(token: Token) = initialRule.isValid(token)

    override fun processRule(parser: APLParser, syntaxRuleBindings: MutableList<SyntaxRuleVariableBinding>) {
        if (initialRule.isValid(parser.tokeniser.peekToken())) {
            initialRule.processRule(parser, syntaxRuleBindings)
            rest.forEach { rule ->
                rule.processRule(parser, syntaxRuleBindings)
            }
        }
    }
}

class CallWithVarInstruction(
    val instr: Instruction,
    val env: Environment,
    val bindings: List<Pair<EnvironmentBinding, Instruction>>,
    pos: Position
) : Instruction(pos) {
    override fun evalWithContext(context: RuntimeContext): APLValue {
        val newContext = context.link(env)
        bindings.forEach { (envBinding, instr) ->
            newContext.setVar(envBinding, instr.evalWithContext(context))
        }
        return instr.evalWithContext(newContext)
    }
}

private fun processPair(parser: APLParser, curr: MutableList<SyntaxRule>, token: Symbol, pos: Position) {
    val tokeniser = parser.tokeniser
    if (token.namespace !== tokeniser.engine.keywordNamespace) {
        throw ParseException("Tag is not a keyword: ${token.nameWithNamespace()}", pos)
    }
    when (token.symbolName) {
        "constant" -> curr.add(ConstantSyntaxRule(tokeniser.nextTokenWithType()))
        "value" -> curr.add(ValueSyntaxRule(parser.currentEnvironment().bindLocal(tokeniser.nextTokenWithType())))
        "function" -> curr.add(FunctionSyntaxRule(parser.currentEnvironment().bindLocal(tokeniser.nextTokenWithType())))
        "optional" -> curr.add(processOptional(parser))
        else -> throw ParseException("Unexpected tag: ${token.nameWithNamespace()}")
    }
}

private fun processPairs(parser: APLParser): ArrayList<SyntaxRule> {
    val rulesList = ArrayList<SyntaxRule>()
    parser.tokeniser.iterateUntilToken(CloseParen) { token, pos ->
        when (token) {
            is Symbol -> processPair(parser, rulesList, token, pos)
            else -> throw UnexpectedToken(token, pos)
        }
    }
    return rulesList
}

private fun processOptional(parser: APLParser): OptionalSyntaxRule {
    parser.tokeniser.nextTokenWithType<OpenParen>()
    val rulesList = processPairs(parser)
    if (rulesList.isEmpty()) {
        throw ParseException("Optional syntax rules must have at least one rule")
    }
    return OptionalSyntaxRule(rulesList[0], rulesList.drop(1))
}

fun processDefsyntax(parser: APLParser, pos: Position): Instruction {
    parser.withEnvironment {
        val tokeniser = parser.tokeniser
        val triggerSymbol = tokeniser.nextTokenWithType<Symbol>()
        tokeniser.nextTokenWithType<OpenParen>()

        val rulesList = processPairs(parser)

        tokeniser.nextTokenWithType<OpenFnDef>()
        val instr = parser.parseValueToplevel(CloseFnDef)
        tokeniser.engine.registerCustomSyntax(CustomSyntax(triggerSymbol, parser.currentEnvironment(), rulesList, instr, pos))
        return LiteralSymbol(triggerSymbol, pos)
    }
}

fun processCustomSyntax(parser: APLParser, customSyntax: CustomSyntax): Instruction {
    val bindings = ArrayList<SyntaxRuleVariableBinding>()
    customSyntax.rulesList.forEach { rule ->
        rule.processRule(parser, bindings)
    }
    val envBindings = bindings.map { b -> Pair(b.name, b.value) }
    return CallWithVarInstruction(customSyntax.instr, customSyntax.environment, envBindings, customSyntax.pos)
}