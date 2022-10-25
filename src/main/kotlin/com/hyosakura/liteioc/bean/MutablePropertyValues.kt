package com.hyosakura.liteioc.bean

/**
 * 用于存储和管理多个PropertyValue对象
 *
 * @author LovesAsuna
 */
class MutablePropertyValues : Iterable<PropertyValue> {
    // 存储多个PropertyValue对象
    val propertyValueList: MutableList<PropertyValue>

    constructor() {
        propertyValueList = ArrayList()
    }

    constructor(propertyValueList: MutableList<PropertyValue>?) {
        if (propertyValueList == null) {
            this.propertyValueList = ArrayList()
        } else {
            this.propertyValueList = propertyValueList
        }
    }

    /**
     * 以数组形式返回PropertyValues
     *
     * @return PropertyValue数组
     */
    fun getPropertyValues(): Array<PropertyValue> = propertyValueList.toTypedArray()

    /**
     * 根据name属性值获取PropertyValue对象
     *
     * @param propertyName propertyName
     * @return PropertyValue
     */
    fun getPropertyValue(propertyName: String): PropertyValue? {
        for (propertyValue in propertyValueList) {
            if (propertyValue.name == propertyName) {
                return propertyValue
            }
        }
        return null
    }

    /**
     * 判断集合是否为空
     *
     * @return boolean
     */
    fun isEmpty(): Boolean = propertyValueList.isEmpty()

    /**
     * 往集合中添加PropertyValue对象
     *
     * @param value PropertyValue对象
     * @return MutablePropertyValues 本身
     */
    fun addPropertyValue(value: PropertyValue): MutablePropertyValues {
        // 判断是否重复
        for (i in propertyValueList.indices) {
            val currentValue: PropertyValue = propertyValueList[i]
            if (currentValue.name == value.name) {
                // 覆盖
                propertyValueList[i] = value
                // 链式编程
                return this
            }
        }
        propertyValueList.add(value)
        return this
    }

    /**
     * 判断是否有指定name的PropertyValue对象
     *
     * @param propertyName propertyName
     * @return 操作是否成功
     */
    operator fun contains(propertyName: String): Boolean {
        return getPropertyValue(propertyName) != null
    }

    override fun iterator(): Iterator<PropertyValue> {
        return propertyValueList.iterator()
    }
}