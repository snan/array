package array

// This is a test implementation of an immutable map. This is not meant to be a proper implementation
// but as a reference for testing once the real implementation is completed.

class ImmutableMap<K, V> private constructor(content: HashMap<K, V>) : Map<K, V> by content {
    constructor() : this(HashMap<K, V>())

    fun copyAndPut(key: K, value: V): ImmutableMap<K, V> {
        val new = copyMap()
        new[key] = value
        return ImmutableMap(new)
    }

    fun copyAndPutMultiple(vararg content: Pair<K, V>): ImmutableMap<K, V> {
        val new = copyMap()
        fillContent(new, content)
        return ImmutableMap(new)
    }

    fun copyWithout(key: K): ImmutableMap<K, V> {
        return if (containsKey(key)) {
            ImmutableMap(copyMap(key))
        } else {
            this
        }
    }

    private fun copyMap(ignoreKey: K? = null): HashMap<K, V> {
        val new = HashMap<K, V>()
        this.forEach { (key, value) ->
            if (ignoreKey == null || key != ignoreKey) {
                new[key] = value
            }
        }
        return new
    }

    companion object {
        fun <K, V> makeFromContent(content: List<Pair<K, V>>): ImmutableMap<K, V> {
            val map = HashMap<K, V>()
            content.forEach { (key, value) ->
                map[key] = value
            }
            return ImmutableMap(map)
        }

        private fun <K, V> fillContent(map: HashMap<K, V>, content: Array<out Pair<K, V>>) {
            content.forEach { (key, value) ->
                map[key] = value
            }
        }
    }
}