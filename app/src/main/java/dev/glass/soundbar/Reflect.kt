package dev.glass.soundbar

internal object Reflect {
    fun set(target: Any, name: String, value: Any?) {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try { type.getDeclaredField(name).apply { isAccessible = true }.set(target, value); return }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("${target.javaClass.name}.$name missing")
    }
    fun field(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("${target.javaClass.name}.$name missing")
    }
    fun call(target: Any, name: String, vararg args: Any?): Any? {
        val m = target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterCount == args.size && it.parameterTypes.zip(args).all { (t, a) ->
                a == null || t.isInstance(a) || (t.isPrimitive && when(t) {
                    Integer.TYPE -> a is Int; java.lang.Boolean.TYPE -> a is Boolean
                    java.lang.Float.TYPE -> a is Float; java.lang.Long.TYPE -> a is Long
                    else -> false
                })
            }
        } ?: error("${target.javaClass.name}.$name missing")
        return m.apply { isAccessible = true }.invoke(target, *args)
    }
}
