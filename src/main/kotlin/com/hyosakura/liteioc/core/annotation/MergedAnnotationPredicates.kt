package com.hyosakura.liteioc.core.annotation

import java.util.function.Function
import java.util.function.Predicate

/**
 * @author LovesAsuna
 **/
object MergedAnnotationPredicates {

    fun <A : Annotation, K> unique(
        keyExtractor: Function<in MergedAnnotation<A>, K>
    ): Predicate<MergedAnnotation<A>> {
        return UniquePredicate(
            keyExtractor
        )
    }

    private class UniquePredicate<A : Annotation, K>(val keyExtractor: Function<in MergedAnnotation<A>, K>) :
        Predicate<MergedAnnotation<A>> {

        private val seen: MutableSet<K> = HashSet()

        override fun test(annotation: MergedAnnotation<A>): Boolean {
            val key = keyExtractor.apply(annotation)
            return seen.add(key)
        }

    }

}