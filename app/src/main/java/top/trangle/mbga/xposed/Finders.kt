package top.trangle.mbga.xposed

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

val IntType: Class<Int> = Int::class.javaPrimitiveType!!
val LongType: Class<Long> = Long::class.javaPrimitiveType!!
val BooleanType: Class<Boolean> = Boolean::class.javaPrimitiveType!!
val UnitType: Class<Void> = Void.TYPE
val ListClass: Class<*> = List::class.java
val StringClass: Class<*> = String::class.java

fun String.toClass(): Class<*> = Class.forName(this, false, HookEnv.classLoader)

fun String.toClassOrNull(): Class<*>? = runCatching { toClass() }.getOrNull()

/** DSL for the `modifiers { isStatic }` style conditions. */
class ModifierRule(private val modifiers: Int) {
    private var required: Int = 0

    val isStatic: Boolean
        get() {
            required = required or Modifier.STATIC
            return Modifier.isStatic(modifiers)
        }

    val isFinal: Boolean
        get() {
            required = required or Modifier.FINAL
            return Modifier.isFinal(modifiers)
        }

    val isPublic: Boolean
        get() {
            required = required or Modifier.PUBLIC
            return Modifier.isPublic(modifiers)
        }

    val isPrivate: Boolean
        get() {
            required = required or Modifier.PRIVATE
            return Modifier.isPrivate(modifiers)
        }

    val isAbstract: Boolean
        get() {
            required = required or Modifier.ABSTRACT
            return Modifier.isAbstract(modifiers)
        }

    internal fun accept(): Boolean = (modifiers and required) == required
}

abstract class MemberRule {
    var name: String? = null

    private var modifiersBlock: (ModifierRule.() -> Unit)? = null

    fun modifiers(block: ModifierRule.() -> Unit) {
        modifiersBlock = block
    }

    internal fun acceptModifiers(modifiers: Int): Boolean {
        val block = modifiersBlock ?: return true
        val rule = ModifierRule(modifiers)
        rule.block()
        return rule.accept()
    }

    internal fun describe(): String =
        buildString {
            append(name?.let { "name=$it" } ?: "<any name>")
        }
}

class MethodRule : MemberRule() {
    var returnType: Class<*>? = null

    private var expectedParams: Array<Class<*>>? = null
    private var expectedParamCount: Int? = null
    private var paramCondition: ((Array<Class<*>>) -> Boolean)? = null

    fun param(vararg types: Class<*>) {
        expectedParams = arrayOf(*types)
    }

    fun paramCount(count: Int) {
        expectedParamCount = count
    }

    fun emptyParam() {
        expectedParams = emptyArray()
    }

    fun param(condition: (Array<Class<*>>) -> Boolean) {
        paramCondition = condition
    }

    internal fun matches(method: Method): Boolean {
        val expectedName = name
        if (expectedName != null && method.name != expectedName) return false
        val expectedReturnType = returnType
        if (expectedReturnType != null && method.returnType != expectedReturnType) return false
        val params = method.parameterTypes
        val types = expectedParams
        if (types != null && !types.contentEquals(params)) return false
        val count = expectedParamCount
        if (count != null && params.size != count) return false
        val condition = paramCondition
        if (condition != null && !condition(params)) return false
        return acceptModifiers(method.modifiers)
    }
}

class FieldRule : MemberRule() {
    var type: Class<*>? = null

    internal fun matches(field: Field): Boolean {
        val expectedName = name
        if (expectedName != null && field.name != expectedName) return false
        val expectedType = type
        if (expectedType != null && field.type != expectedType) return false
        return acceptModifiers(field.modifiers)
    }
}

class ConstructorRule : MemberRule() {
    private var expectedParams: Array<Class<*>>? = null
    private var expectedParamCount: Int? = null
    private var paramCondition: ((Array<Class<*>>) -> Boolean)? = null

    fun param(vararg types: Class<*>) {
        expectedParams = arrayOf(*types)
    }

    fun paramCount(count: Int) {
        expectedParamCount = count
    }

    fun emptyParam() {
        expectedParams = emptyArray()
    }

    fun param(condition: (Array<Class<*>>) -> Boolean) {
        paramCondition = condition
    }

    internal fun matches(constructor: Constructor<*>): Boolean {
        val params = constructor.parameterTypes
        val types = expectedParams
        if (types != null && !types.contentEquals(params)) return false
        val count = expectedParamCount
        if (count != null && params.size != count) return false
        val condition = paramCondition
        if (condition != null && !condition(params)) return false
        return acceptModifiers(constructor.modifiers)
    }
}

class MethodFinder internal constructor(
    private val clazz: Class<*>,
    internal val rule: MethodRule,
) {
    fun find(): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredMethods.firstOrNull { rule.matches(it) }?.let { return it }
            current = current.superclass
        }
        // default methods of interfaces
        return clazz.interfaces.asSequence().flatMap { it.methods.asSequence() }
            .firstOrNull { rule.matches(it) }
    }

    fun get(target: Any? = null): MethodCall {
        val method = find()
            ?: throw NoSuchMethodException("${clazz.name}: ${rule.describe()}")
        return MethodCall(method, target)
    }

    fun hook(
        priority: Int = HookPriority.DEFAULT,
        spec: HookSpec.() -> Unit,
    ) {
        val method = find()
        if (method == null) {
            YLog.warn("method not found, skip hook: ${clazz.name}, ${rule.describe()}")
            return
        }
        hookExecutable(method, priority, spec)
    }
}

class MethodCall internal constructor(
    private val method: Method,
    private val target: Any?,
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> call(vararg args: Any?): T {
        method.isAccessible = true
        return method.invoke(target, *args) as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> invoke(vararg args: Any?): T = call(*args)
}

class FieldFinder internal constructor(
    private val clazz: Class<*>,
    private val rule: FieldRule,
) {
    fun find(): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredFields.firstOrNull { rule.matches(it) }?.let { return it }
            current = current.superclass
        }
        return null
    }

    fun get(target: Any? = null): FieldAccess {
        val field = find() ?: throw NoSuchFieldException("${clazz.name}: ${rule.describe()}")
        return FieldAccess(field, target)
    }
}

class FieldAccess internal constructor(
    private val field: Field,
    private val target: Any?,
) {
    private fun read(): Any? {
        field.isAccessible = true
        return field.get(target)
    }

    fun any(): Any? = read()

    fun string(): String = read()?.toString() ?: ""

    fun int(): Int = (read() as? Number)?.toInt() ?: -1

    fun long(): Long = (read() as? Number)?.toLong() ?: -1L

    fun boolean(): Boolean = read() as? Boolean ?: false

    fun set(value: Any?) {
        field.isAccessible = true
        field.set(target, value)
    }

    fun setNull() = set(null)
}

class ConstructorFinder internal constructor(
    private val clazz: Class<*>,
    private val rule: ConstructorRule,
) {
    fun find(): Constructor<*>? = clazz.declaredConstructors.firstOrNull { rule.matches(it) }

    fun give(): ConstructorInstance? = find()?.let { ConstructorInstance(it) }

    fun get(): ConstructorInstance =
        give() ?: throw NoSuchMethodException("${clazz.name}: constructor ${rule.describe()}")

    fun hook(
        priority: Int = HookPriority.DEFAULT,
        spec: HookSpec.() -> Unit,
    ) {
        val instance = give()
        if (instance == null) {
            YLog.warn("constructor not found, skip hook: ${clazz.name}")
            return
        }
        instance.hook(priority, spec)
    }
}

class ConstructorInstance internal constructor(private val constructor: Constructor<*>) {
    val parameters: Array<Class<*>>
        get() = constructor.parameterTypes

    @Suppress("UNCHECKED_CAST")
    fun <T> newInstance(vararg args: Any?): T {
        constructor.isAccessible = true
        return constructor.newInstance(*args) as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> call(vararg args: Any?): T = newInstance(*args)

    fun hook(
        priority: Int = HookPriority.DEFAULT,
        spec: HookSpec.() -> Unit,
    ) = hookExecutable(constructor, priority, spec)
}

fun Class<*>.method(init: MethodRule.() -> Unit): MethodFinder =
    MethodFinder(this, MethodRule().apply(init))

fun Class<*>.hasMethod(init: MethodRule.() -> Unit): Boolean =
    MethodFinder(this, MethodRule().apply(init)).find() != null

fun Class<*>.field(init: FieldRule.() -> Unit): FieldFinder =
    FieldFinder(this, FieldRule().apply(init))

fun Class<*>.constructor(init: ConstructorRule.() -> Unit = {}): ConstructorFinder =
    ConstructorFinder(this, ConstructorRule().apply(init))
