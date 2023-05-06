package com.hyosakura.liteioc.bean

/**
 * 用于存储和管理多个PropertyValue对象
 *
 * @author LovesAsuna
 */
class MutablePropertyValues : PropertyValues {
    // 存储多个PropertyValue对象
    private lateinit var propertyValueList: MutableList<PropertyValue>

    private var processedProperties: MutableSet<String>? = null

    @Volatile
    private var converted = false

    constructor() {
        propertyValueList = ArrayList(0)
    }

    constructor(original: PropertyValues?) {
        if (original != null) {
            val pvs = original.getPropertyValues()
            this.propertyValueList = ArrayList(pvs.size)
            for (pv in pvs) {
                this.propertyValueList.add(PropertyValue(pv))
            }
        } else {
            this.propertyValueList = ArrayList(0)
        }
    }

    constructor(propertyValueList: MutableList<PropertyValue>?) {
        this.propertyValueList = propertyValueList ?: ArrayList()
    }

    /**
     * 以数组形式返回PropertyValues
     *
     * @return PropertyValue数组
     */
    override fun getPropertyValues(): Array<PropertyValue> = propertyValueList.toTypedArray()

    /**
     * 根据name属性值获取PropertyValue对象
     *
     * @param propertyName propertyName
     * @return PropertyValue
     */
    override fun getPropertyValue(propertyName: String?): PropertyValue? {
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
    override fun isEmpty(): Boolean = propertyValueList.isEmpty()

    fun add(propertyName: String, propertyValue: Any?): MutablePropertyValues {
        addPropertyValue(PropertyValue(propertyName, propertyValue))
        return this
    }

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
    override fun contains(propertyName: String?): Boolean {
        return getPropertyValue(propertyName) != null
    }

    fun setConverted() {
        this.converted = true
    }

    fun isConverted(): Boolean = this.converted

    fun getPropertyValueList(): List<PropertyValue> = this.propertyValueList

    fun registerProcessedProperty(propertyName: String) {
        if (this.processedProperties == null) {
            this.processedProperties = HashSet(4)
        }
        this.processedProperties!!.add(propertyName)
    }

    fun clearProcessedProperty(propertyName: String) {
        if (processedProperties != null) {
            processedProperties!!.remove(propertyName)
        }
    }

}