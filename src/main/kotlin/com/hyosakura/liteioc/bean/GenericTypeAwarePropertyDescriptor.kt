package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.core.BridgeMethodResolver
import com.hyosakura.liteioc.core.GenericTypeResolver
import com.hyosakura.liteioc.core.MethodParameter
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ObjectUtil
import com.hyosakura.liteioc.util.StringUtil
import org.jetbrains.annotations.Nullable
import org.slf4j.LoggerFactory
import java.beans.IntrospectionException
import java.beans.PropertyDescriptor
import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
class GenericTypeAwarePropertyDescriptor : PropertyDescriptor {

    private val beanClass: Class<*>

    private val readMethod: Method?

    private val writeMethod: Method?

    @Volatile
    private var ambiguousWriteMethods: Set<Method>? = null

    private var writeMethodParameter: MethodParameter? = null

    private var propertyType: Class<*>? = null

    private var propertyEditorClass: Class<*>? = null

    @Throws(IntrospectionException::class)
    constructor(
        beanClass: Class<*>,
        propertyName: String?,
        readMethod: Method?,
        writeMethod: Method?,
        propertyEditorClass: Class<*>?
    ) : super(propertyName, null, null) {
        this.beanClass = beanClass
        val readMethodToUse = if (readMethod != null) BridgeMethodResolver.findBridgedMethod(readMethod) else null
        var writeMethodToUse = if (writeMethod != null) BridgeMethodResolver.findBridgedMethod(writeMethod) else null
        if (writeMethodToUse == null && readMethodToUse != null) {
            // Fallback: Original JavaBeans introspection might not have found matching setter
            // method due to lack of bridge method resolution, in case of the getter using a
            // covariant return type whereas the setter is defined for the concrete property type.
            val candidate = ClassUtil.getMethodIfAvailable(
                this.beanClass, "set" + StringUtil.capitalize(name), *emptyArray()
            )
            if (candidate != null && candidate.parameterCount == 1) {
                writeMethodToUse = candidate
            }
        }
        this.readMethod = readMethodToUse
        this.writeMethod = writeMethodToUse
        if (this.writeMethod != null) {
            if (this.readMethod == null) {
                // Write method not matched against read method: potentially ambiguous through
                // several overloaded variants, in which case an arbitrary winner has been chosen
                // by the JDK's JavaBeans Introspector...
                val ambiguousCandidates: MutableSet<Method> = HashSet()
                for (method in beanClass.methods) {
                    if (method.name == writeMethodToUse!!.name && method != writeMethodToUse && !method.isBridge && method.parameterCount == writeMethodToUse.parameterCount) {
                        ambiguousCandidates.add(method)
                    }
                }
                if (ambiguousCandidates.isNotEmpty()) {
                    ambiguousWriteMethods = ambiguousCandidates
                }
            }
            writeMethodParameter = MethodParameter(this.writeMethod, 0).withContainingClass(this.beanClass)
        }
        if (this.readMethod != null) {
            propertyType = GenericTypeResolver.resolveReturnType(this.readMethod, this.beanClass)
        } else if (writeMethodParameter != null) {
            propertyType = writeMethodParameter!!.getParameterType()
        }
        this.propertyEditorClass = propertyEditorClass
    }


    fun getBeanClass(): Class<*> {
        return beanClass
    }

    override fun getReadMethod(): Method? {
        return readMethod
    }

    override fun getWriteMethod(): Method? {
        return writeMethod
    }

    fun getWriteMethodForActualAccess(): Method {
        requireNotNull(writeMethod) { "No write method available" }
        val ambiguousCandidates = ambiguousWriteMethods
        if (ambiguousCandidates != null) {
            ambiguousWriteMethods = null
            LoggerFactory.getLogger(GenericTypeAwarePropertyDescriptor::class.java).debug(
                "Non-unique JavaBean property '" +
                        name + "' being accessed! Ambiguous write methods found next to actually used [" +
                        writeMethod + "]: " + ambiguousCandidates
            )
        }
        return writeMethod
    }

    fun getWriteMethodParameter(): MethodParameter {
        requireNotNull(writeMethodParameter) { "No write method available" }
        return writeMethodParameter!!
    }

    @Nullable
    override fun getPropertyType(): Class<*>? {
        return propertyType
    }

    @Nullable
    override fun getPropertyEditorClass(): Class<*>? {
        return propertyEditorClass
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is GenericTypeAwarePropertyDescriptor) {
            return false
        }
        return (getBeanClass() == other.getBeanClass() && PropertyDescriptorUtil.equals(this, other))
    }

    override fun hashCode(): Int {
        var hashCode = getBeanClass().hashCode()
        hashCode = 29 * hashCode + ObjectUtil.nullSafeHashCode(getReadMethod())
        hashCode = 29 * hashCode + ObjectUtil.nullSafeHashCode(getWriteMethod())
        return hashCode
    }

}