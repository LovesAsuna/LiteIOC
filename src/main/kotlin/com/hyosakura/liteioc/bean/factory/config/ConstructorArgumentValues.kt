package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.Mergeable
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ObjectUtil
import org.jetbrains.annotations.Nullable
import java.util.*

/**
 * @author LovesAsuna
 **/
class ConstructorArgumentValues {

    private val indexedArgumentValues = LinkedHashMap<Int, ValueHolder>()

    private val genericArgumentValues = ArrayList<ValueHolder>()

    constructor()

    constructor(original: ConstructorArgumentValues) {
        addArgumentValues(original)
    }

    fun addArgumentValues(other: ConstructorArgumentValues?) {
        if (other != null) {
            other.indexedArgumentValues.forEach { (index: Int, argValue: ValueHolder) ->
                addOrMergeIndexedArgumentValue(
                    index, argValue.copy()
                )
            }
            other.genericArgumentValues.stream()
                .filter { valueHolder: ValueHolder ->
                    !genericArgumentValues.contains(
                        valueHolder
                    )
                }
                .forEach { valueHolder: ValueHolder ->
                    addOrMergeGenericArgumentValue(
                        valueHolder.copy()
                    )
                }
        }
    }

    fun isEmpty(): Boolean {
        return indexedArgumentValues.isEmpty() && genericArgumentValues.isEmpty()
    }

    fun getArgumentCount(): Int {
        return indexedArgumentValues.size + genericArgumentValues.size
    }

    fun addIndexedArgumentValue(
        index: Int,
        newValue: ValueHolder
    ) {
        require(index >= 0) { "Index must not be negative" }
        addOrMergeIndexedArgumentValue(index, newValue)
    }

    private fun addOrMergeIndexedArgumentValue(
        key: Int,
        newValue: ValueHolder
    ) {
        val currentValue = indexedArgumentValues[key]
        if (currentValue != null && newValue.value is Mergeable) {
            val mergeable = newValue.value as Mergeable
            if (mergeable.isMergeEnabled()) {
                newValue.value = mergeable.merge(currentValue.value)
            }
        }
        indexedArgumentValues[key] = newValue
    }

    fun getIndexedArgumentValues(): Map<Int, ValueHolder> {
        return Collections.unmodifiableMap(indexedArgumentValues)
    }

    fun getGenericArgumentValues(): List<ValueHolder> {
        return Collections.unmodifiableList(genericArgumentValues)
    }

    fun getArgumentValue(
        index: Int,
        requiredType: Class<*>?,
        requiredName: String?,
        usedValueHolders: Set<ValueHolder>?
    ): ValueHolder? {
        require(index >= 0) { "Index must not be negative" }
        var valueHolder = getIndexedArgumentValue(index, requiredType, requiredName)
        if (valueHolder == null) {
            valueHolder = getGenericArgumentValue(requiredType, requiredName, usedValueHolders)
        }
        return valueHolder
    }

    fun getIndexedArgumentValue(
        index: Int,
        @Nullable requiredType: Class<*>?,
        @Nullable requiredName: String?
    ): ValueHolder? {
        require(index >= 0) { "Index must not be negative" }
        val valueHolder = indexedArgumentValues[index]
        return if (valueHolder != null &&
            (valueHolder.type == null || requiredType != null && ClassUtil.matchesTypeName(
                requiredType,
                valueHolder.type
            )) &&
            (valueHolder.name == null || requiredName != null && (requiredName.isEmpty() || requiredName == valueHolder.name))
        ) {
            valueHolder
        } else null
    }

    fun getGenericArgumentValue(
        requiredType: Class<*>?, requiredName: String?,
        usedValueHolders: Set<ValueHolder>?
    ): ValueHolder? {
        for (valueHolder in genericArgumentValues) {
            if (usedValueHolders != null && usedValueHolders.contains(valueHolder)) {
                continue
            }
            if (valueHolder.name != null && (requiredName == null || !requiredName.isEmpty() && requiredName != valueHolder.name)) {
                continue
            }
            if (valueHolder.type != null && (requiredType == null ||
                        !ClassUtil.matchesTypeName(requiredType, valueHolder.type))
            ) {
                continue
            }
            if (requiredType != null && valueHolder.type == null && valueHolder.name == null &&
                !ClassUtil.isAssignableValue(requiredType, valueHolder.type)
            ) {
                continue
            }
            return valueHolder
        }
        return null
    }

    fun addGenericArgumentValue(newValue: ValueHolder) {
        if (!genericArgumentValues.contains(newValue)) {
            addOrMergeGenericArgumentValue(newValue)
        }
    }

    private fun addOrMergeGenericArgumentValue(newValue: ValueHolder) {
        if (newValue.name != null) {
            val it = genericArgumentValues.iterator()
            while (it.hasNext()) {
                val currentValue = it.next()
                if (newValue.name == currentValue.name) {
                    if (newValue.value is Mergeable) {
                        val mergeable = newValue.value as Mergeable
                        if (mergeable.isMergeEnabled()) {
                            newValue.value = mergeable.merge(currentValue.value)
                        }
                    }
                    it.remove()
                }
            }
        }
        genericArgumentValues.add(newValue)
    }

    class ValueHolder {

        var value: Any? = null

        var type: String? = null

        var name: String? = null

        var source: Any? = null

        @get:Synchronized
        var isConverted = false
            private set

        @get:Synchronized
        @set:Synchronized
        var convertedValue: Any? = null
            set(value) {
                isConverted = value != null
                field = value
            }

        constructor(value: Any?) {
            this.value = value
        }

        constructor(value: Any?, type: String?) {
            this.value = value
            this.type = type
        }

        constructor(value: Any?, type: String?, name: String?) {
            this.value = value
            this.type = type
            this.name = name
        }

        private fun contentEquals(other: ValueHolder): Boolean {
            return this === other || ObjectUtil.nullSafeEquals(value, other.value) && ObjectUtil.nullSafeEquals(
                type,
                other.type
            )
        }

        private fun contentHashCode(): Int {
            return ObjectUtil.nullSafeHashCode(value) * 29 + ObjectUtil.nullSafeHashCode(type)
        }

        fun copy(): ValueHolder {
            val copy = ValueHolder(value, type, name)
            copy.source = source
            return copy
        }
    }
}