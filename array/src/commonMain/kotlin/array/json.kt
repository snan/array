package array.json

import array.APLValue
import array.ByteProvider

expect suspend fun parseJsonToAPL(input: ByteProvider): APLValue
