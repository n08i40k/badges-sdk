// TODO: fix lint errors
package ru.n08i40k.badges.util

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import sun.misc.Unsafe

internal class ClonableFields(
    internal val fields: Array<Field>,
    internal val kinds: ByteArray,
    internal val offsets: LongArray?,
)

private const val KIND_OBJECT: Byte = 0
private const val KIND_BOOLEAN: Byte = 1
private const val KIND_BYTE: Byte = 2
private const val KIND_CHAR: Byte = 3
private const val KIND_SHORT: Byte = 4
private const val KIND_INT: Byte = 5
private const val KIND_LONG: Byte = 6
private const val KIND_FLOAT: Byte = 7
private const val KIND_DOUBLE: Byte = 8

private val unsafe: Unsafe? =
    try {
        val field = Unsafe::class.java.declaredFields
            .first { Modifier.isStatic(it.modifiers) && it.type == Unsafe::class.java }
        field.isAccessible = true
        field.get(null) as Unsafe
    } catch (e: Throwable) {
        Logger.warn("sun.misc.Unsafe is not available, falling back to reflection: ${e.message}")
        null
    }

private fun fieldKind(type: Class<*>): Byte =
    when (type) {
        Boolean::class.javaPrimitiveType -> KIND_BOOLEAN
        Byte::class.javaPrimitiveType -> KIND_BYTE
        Char::class.javaPrimitiveType -> KIND_CHAR
        Short::class.javaPrimitiveType -> KIND_SHORT
        Int::class.javaPrimitiveType -> KIND_INT
        Long::class.javaPrimitiveType -> KIND_LONG
        Float::class.javaPrimitiveType -> KIND_FLOAT
        Double::class.javaPrimitiveType -> KIND_DOUBLE
        else -> KIND_OBJECT
    }

internal fun getAccessibleFields(klass: Class<*>): ClonableFields {
    val fields = arrayListOf<Field>()

    var c: Class<*>? = klass

    while (c != null && c != Any::class.java) {
        for (f in c.declaredFields) {
            if (Modifier.isStatic(f.modifiers)) continue

            f.isAccessible = true
            fields.add(f)
        }

        c = c.superclass
    }

    val offsets = unsafe?.let { unsafe ->
        try {
            LongArray(fields.size) { unsafe.objectFieldOffset(fields[it]) }
        } catch (e: Throwable) {
            Logger.warn("Unable to resolve field offsets of $klass, falling back to reflection: ${e.message}")
            null
        }
    }

    return ClonableFields(
        fields.toTypedArray(),
        ByteArray(fields.size) { fieldKind(fields[it].type) },
        offsets,
    )
}

internal fun cloneFields(
    src: Any,
    dest: Any,
    // can be got by calling getAccessibleFields
    fields: ClonableFields
) {
    val offsets = fields.offsets
    val unsafe = unsafe

    if (offsets == null || unsafe == null) {
        for (field in fields.fields) {
            field.set(dest, field.get(src))
        }
        return
    }

    val kinds = fields.kinds

    for (i in offsets.indices) {
        val offset = offsets[i]

        when (kinds[i]) {
            KIND_BOOLEAN -> unsafe.putBoolean(dest, offset, unsafe.getBoolean(src, offset))
            KIND_BYTE -> unsafe.putByte(dest, offset, unsafe.getByte(src, offset))
            KIND_CHAR -> unsafe.putChar(dest, offset, unsafe.getChar(src, offset))
            KIND_SHORT -> unsafe.putShort(dest, offset, unsafe.getShort(src, offset))
            KIND_INT -> unsafe.putInt(dest, offset, unsafe.getInt(src, offset))
            KIND_LONG -> unsafe.putLong(dest, offset, unsafe.getLong(src, offset))
            KIND_FLOAT -> unsafe.putFloat(dest, offset, unsafe.getFloat(src, offset))
            KIND_DOUBLE -> unsafe.putDouble(dest, offset, unsafe.getDouble(src, offset))
            else -> unsafe.putObject(dest, offset, unsafe.getObject(src, offset))
        }
    }
}

// invokeExact требует точного совпадения статических типов в месте вызова,
// поэтому handle заранее приводится к типам, которые там доступны
internal fun MethodHandle.retype(returnType: Class<*>, vararg parameterTypes: Class<*>): MethodHandle =
    asType(MethodType.methodType(returnType, parameterTypes))

internal fun getFieldGetter(klass: Class<*>, name: String): MethodHandle {
    val field = klass.getDeclaredField(name)
    field.isAccessible = true

    return MethodHandles.lookup()
        .unreflectGetter(field)
}

internal fun getFieldSetter(klass: Class<*>, name: String): MethodHandle {
    val field = klass.getDeclaredField(name)
    field.isAccessible = true

    return MethodHandles.lookup()
        .unreflectSetter(field)
}

internal fun getFieldGetterIfExists(klass: Class<*>, name: String): MethodHandle? {
    try {
        val field = klass.getDeclaredField(name)
        field.isAccessible = true

        return MethodHandles.lookup()
            .unreflectGetter(field)
    } catch (e: Throwable) {
        Logger.warn("Field $klass.$name is not available: ${e.message}")
        return null
    }
}

internal fun getFieldSetterIfExists(klass: Class<*>, name: String): MethodHandle? {
    try {
        val field = klass.getDeclaredField(name)
        field.isAccessible = true

        return MethodHandles.lookup()
            .unreflectSetter(field)
    } catch (e: Throwable) {
        Logger.warn("Field $klass.$name is not available: ${e.message}")
        return null
    }
}

internal inline fun <reified T> Method.invokeAndCast(obj: Any?, vararg params: Any?) =
    this.invoke(obj, *params) as? T
