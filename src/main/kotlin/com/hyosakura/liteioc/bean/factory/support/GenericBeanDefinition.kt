package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.util.ObjectUtil

/**
 * @author LovesAsuna
 **/
open class GenericBeanDefinition : AbstractBeanDefinition {

    private var parentName: String? = null

    constructor() : super()

    constructor(original: BeanDefinition) : super(original)

    override fun setParentName(parentName: String?) {
        this.parentName = parentName
    }

    override fun getParentName(): String? = parentName

    override fun cloneBeanDefinition(): AbstractBeanDefinition {
        return GenericBeanDefinition(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return if (other !is GenericBeanDefinition) {
            false
        } else ObjectUtil.nullSafeEquals(parentName, other.parentName) && super.equals(other)
    }

    override fun toString(): String {
        return if (parentName != null) {
            "Generic bean with parent '" + parentName + "': " + super.toString()
        } else "Generic bean: " + super.toString()
    }

}