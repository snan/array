package array.repl

import array.Engine
import array.RuntimeContext
import array.makeKeyboardInput

fun runRepl() {
    val keyboardInput = makeKeyboardInput()
    val engine = Engine()
    val prompt = "> "
    while (true) {
        print(prompt)
        val line = keyboardInput.readString()
        if (line.trim() == "quit") {
            break
        }
        val parsed = engine.parseString(line)
        val context = RuntimeContext(engine, null)
        val result = parsed.evalWithContext(context)
        println(result.formatted())
    }
}
