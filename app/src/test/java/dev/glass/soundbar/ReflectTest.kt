package dev.glass.soundbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.Executors

class ReflectTest {
    open class Parent {
        fun inherited(): String = "parent"
    }

    class Material : Parent() {
        var radius = 0
        fun select(value: Int): String = "int:$value"
        fun select(value: Float): String = "float:$value"
        fun select(value: String?): String = "string:$value"
        fun combine(flag: Boolean, timestamp: Long): String = "$flag:$timestamp"
        fun describe(value: CharSequence): String = value.toString()
    }

    class OtherMaterial {
        fun select(value: Int): String = "other:$value"
    }

    @Test fun cachedOverloadsResolveEachArgumentTypeAndValue() {
        val target = Material()
        repeat(20) { value ->
            assertEquals("int:$value", Reflect.call(target, "select", value))
            assertEquals("float:${value.toFloat()}", Reflect.call(target, "select", value.toFloat()))
            assertEquals("string:$value", Reflect.call(target, "select", value.toString()))
        }
    }

    @Test fun nullSelectsAReferenceParameter() {
        assertEquals("string:null", Reflect.call(Material(), "select", null))
        assertThrows(IllegalStateException::class.java) {
            Reflect.call(Material(), "setRadius", null)
        }
    }

    @Test fun inheritedMethodsAndAssignableArgumentsStillResolve() {
        val target = Material()
        assertEquals("parent", Reflect.call(target, "inherited"))
        assertEquals("text", Reflect.call(target, "describe", StringBuilder("text")))
        assertEquals("true:42", Reflect.call(target, "combine", true, 42L))
        Reflect.call(target, "setRadius", 120)
        assertEquals(120, Reflect.call(target, "getRadius"))
    }

    @Test fun cacheSeparatesClassesAndTargetInstances() {
        assertEquals("int:2", Reflect.call(Material(), "select", 2))
        assertEquals("other:2", Reflect.call(OtherMaterial(), "select", 2))
        val first = Material()
        val second = Material()
        Reflect.call(first, "setRadius", 120)
        Reflect.call(second, "setRadius", 80)
        assertEquals(120, Reflect.call(first, "getRadius"))
        assertEquals(80, Reflect.call(second, "getRadius"))
    }

    @Test fun concurrentCallersCanPopulateAndReadTheCache() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = (0..31).map { value ->
                executor.submit<String> { Reflect.call(Material(), "select", value) as String }
            }
            results.forEachIndexed { value, result -> assertEquals("int:$value", result.get()) }
        } finally {
            executor.shutdownNow()
        }
    }
}
