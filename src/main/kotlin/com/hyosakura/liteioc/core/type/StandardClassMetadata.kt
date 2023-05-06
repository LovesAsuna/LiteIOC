package com.hyosakura.liteioc.core.type

import com.hyosakura.liteioc.util.StringUtil
import java.lang.reflect.Modifier

/**
 * @author LovesAsuna
 **/
open class StandardClassMetadata : ClassMetadata {

    private val introspectedClass: Class<*>

    constructor(introspectedClass: Class<*>) {
        this.introspectedClass = introspectedClass
    }

    fun getIntrospectedClass(): Class<*> = introspectedClass

    override fun getClassName(): String = introspectedClass.name

    override fun isInterface(): Boolean = introspectedClass.isInterface

    override fun isAnnotation(): Boolean = introspectedClass.isAnnotation

    override fun isAbstract(): Boolean = Modifier.isAbstract(introspectedClass.modifiers)

    override fun isFinal(): Boolean = Modifier.isFinal(introspectedClass.modifiers)

    override fun isIndependent(): Boolean {
        return !hasEnclosingClass() || introspectedClass.declaringClass != null && Modifier.isStatic(
            introspectedClass.modifiers
        )
    }

    override fun getEnclosingClassName(): String? {
        val enclosingClass = introspectedClass.enclosingClass
        return enclosingClass?.name
    }

    override fun getSuperClassName(): String? {
        val superClass = introspectedClass.superclass
        return superClass?.name
    }

    override fun getInterfaceNames(): Array<String> {
        val ifcs = introspectedClass.interfaces

        val ifcNames = Array(ifcs.size) { i ->
            ifcs[i].name
        }
        return ifcNames
    }

    override fun getMemberClassNames(): Array<String> {
        val memberClassNames = LinkedHashSet<String>(4)
        for (nestedClass in introspectedClass.declaredClasses) {
            memberClassNames.add(nestedClass.name)
        }
        return StringUtil.toStringArray(memberClassNames)
    }

    override fun equals(obj: Any?): Boolean {
        return this === obj || obj is StandardClassMetadata && getIntrospectedClass() == obj.getIntrospectedClass()
    }

    override fun hashCode(): Int {
        return getIntrospectedClass().hashCode()
    }

    override fun toString(): String {
        return getClassName()
    }

}