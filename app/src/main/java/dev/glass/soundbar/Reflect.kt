package dev.glass.soundbar

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object Reflect {
    private class CachedMethod(val method: Method) {
        val parameters: Array<Class<*>> = method.parameterTypes
        fun accepts(args: Array<out Any?>): Boolean {
            if (parameters.size != args.size) return false
            for (index in parameters.indices) {
                val type = parameters[index]
                val arg = args[index]
                if (arg == null) {
                    if (type.isPrimitive) return false
                } else if (!type.isInstance(arg) && !(type.isPrimitive && when (type) {
                    Integer.TYPE -> arg is Int
                    java.lang.Boolean.TYPE -> arg is Boolean
                    java.lang.Float.TYPE -> arg is Float
                    java.lang.Long.TYPE -> arg is Long
                    else -> false
                })) return false
            }
            return true
        }
    }

    // Material getters/setters run on every frame. Enumerate ROM methods and
    // copy parameter types once per actual Class (including its class loader).
    private val methods = ConcurrentHashMap<Class<*>, Map<String, List<CachedMethod>>>()

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

    fun fieldOrNull(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        return null
    }

    fun declaredFieldValues(target: Any): List<Pair<String, Any?>> {
        val result = ArrayList<Pair<String, Any?>>()
        var type: Class<*>? = target.javaClass
        while (type != null) {
            type.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    result += field.name to field.get(target)
                }
            }
            type = type.superclass
        }
        return result
    }
    fun call(target: Any, name: String, vararg args: Any?): Any? {
        val named = methods.computeIfAbsent(target.javaClass) { type ->
            type.methods.groupBy { it.name }.mapValues { (_, overloads) -> overloads.map(::CachedMethod) }
        }[name]
        val cached = named?.firstOrNull { it.accepts(args) }
            ?: error("${target.javaClass.name}.$name missing")
        if (!cached.method.isAccessible) cached.method.isAccessible = true
        return cached.method.invoke(target, *args)
    }
}
