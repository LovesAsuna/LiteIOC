package com.hyosakura.liteioc.core

import com.hyosakura.liteioc.util.ReflectionUtil
import java.util.*

object CollectionFactory {

    private val approximableCollectionTypes: MutableSet<Class<*>> = HashSet()

    private val approximableMapTypes: MutableSet<Class<*>> = HashSet()

    init {
        // Standard collection interfaces
        approximableCollectionTypes.add(Collection::class.java)
        approximableCollectionTypes.add(List::class.java)
        approximableCollectionTypes.add(Set::class.java)
        approximableCollectionTypes.add(SortedSet::class.java)
        approximableCollectionTypes.add(NavigableSet::class.java)
        approximableMapTypes.add(Map::class.java)
        approximableMapTypes.add(SortedMap::class.java)
        approximableMapTypes.add(NavigableMap::class.java)

        // Common concrete collection classes
        approximableCollectionTypes.add(ArrayList::class.java)
        approximableCollectionTypes.add(LinkedList::class.java)
        approximableCollectionTypes.add(HashSet::class.java)
        approximableCollectionTypes.add(LinkedHashSet::class.java)
        approximableCollectionTypes.add(TreeSet::class.java)
        approximableCollectionTypes.add(EnumSet::class.java)
        approximableMapTypes.add(HashMap::class.java)
        approximableMapTypes.add(LinkedHashMap::class.java)
        approximableMapTypes.add(TreeMap::class.java)
        approximableMapTypes.add(EnumMap::class.java)
    }

    fun isApproximableCollectionType(collectionType: Class<*>?): Boolean {
        return collectionType != null && approximableCollectionTypes.contains(
            collectionType
        )
    }

    fun <E> createCollection(collectionType: Class<*>, capacity: Int): Collection<E> {
        return createCollection(collectionType, null, capacity)
    }

    @Suppress("UNCHECKED_CAST")
    fun <E> createCollection(
        collectionType: Class<*>,
        elementType: Class<*>?,
        capacity: Int
    ): Collection<E> {
        return if (collectionType.isInterface) {
            if (MutableSet::class.java == collectionType || MutableCollection::class.java == collectionType) {
                LinkedHashSet(capacity)
            } else if (MutableList::class.java == collectionType) {
                ArrayList(capacity)
            } else if (SortedSet::class.java == collectionType || NavigableSet::class.java == collectionType) {
                TreeSet()
            } else {
                throw IllegalArgumentException("Unsupported Collection interface: " + collectionType.name)
            }
        } else if (EnumSet::class.java.isAssignableFrom(collectionType)) {
            EnumSet.noneOf(asEnumType(elementType!!)) as Collection<E>
        } else {
            require(MutableCollection::class.java.isAssignableFrom(collectionType)) { "Unsupported Collection type: " + collectionType.name }
            try {
                ReflectionUtil.accessibleConstructor(collectionType).newInstance() as Collection<E>
            } catch (ex: Throwable) {
                throw IllegalArgumentException(
                    "Could not instantiate Collection type: " + collectionType.name, ex
                )
            }
        }
    }

    fun isApproximableMapType(mapType: Class<*>?): Boolean {
        return mapType != null && approximableMapTypes.contains(mapType)
    }

    fun <K, V> createMap(mapType: Class<*>, capacity: Int): Map<K, V> {
        return createMap<K, V>(mapType, null, capacity)
    }

    @Suppress("UNCHECKED_CAST")
    fun <K, V> createMap(mapType: Class<*>, keyType: Class<*>?, capacity: Int): Map<K, V> {
        return if (mapType.isInterface) {
            if (MutableMap::class.java == mapType) {
                LinkedHashMap(capacity)
            } else if (SortedMap::class.java == mapType || NavigableMap::class.java == mapType) {
                TreeMap()
            } else {
                throw IllegalArgumentException("Unsupported Map interface: " + mapType.name)
            }
        } else if (EnumMap::class.java == mapType) {
            TODO()
        } else {
            require(MutableMap::class.java.isAssignableFrom(mapType)) { "Unsupported Map type: " + mapType.name }
            try {
                ReflectionUtil.accessibleConstructor(mapType).newInstance() as Map<K, V>
            } catch (ex: Throwable) {
                throw IllegalArgumentException("Could not instantiate Map type: " + mapType.name, ex)
            }
        }
    }

    private fun asEnumType(enumType: Class<*>): Class<out Enum<*>> {
        require(Enum::class.java.isAssignableFrom(enumType)) { "Supplied type is not an enum: " + enumType.name }
        return enumType.asSubclass(Enum::class.java)
    }

}