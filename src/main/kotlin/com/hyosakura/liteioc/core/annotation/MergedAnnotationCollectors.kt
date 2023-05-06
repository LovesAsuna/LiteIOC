package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.core.annotation.MergedAnnotation.Adapt
import com.hyosakura.liteioc.util.LinkedMultiValueMap
import com.hyosakura.liteioc.util.MultiValueMap
import java.util.function.Function
import java.util.function.IntFunction
import java.util.stream.Collector

object MergedAnnotationCollectors {

    private val NO_CHARACTERISTICS = arrayOf<Collector.Characteristics>()

    private val IDENTITY_FINISH_CHARACTERISTICS = arrayOf(Collector.Characteristics.IDENTITY_FINISH)

    fun <A : Annotation> toAnnotationSet(): Collector<MergedAnnotation<A>, *, MutableSet<A>> {
        return Collector.of(
            { LinkedHashSet() },
            { set: MutableSet<A>, annotation: MergedAnnotation<A> ->
                set.add(
                    annotation.synthesize()
                )
            },
            MergedAnnotationCollectors::combiner
        )
    }

    fun <A : Annotation> toAnnotationArray(): Collector<MergedAnnotation<A>, *, Array<Annotation>> {
        return toAnnotationArray { n -> arrayOfNulls(n) }
    }

    inline fun <reified R : Annotation, A : R> toAnnotationArray(
        generator: IntFunction<Array<R?>>
    ): Collector<MergedAnnotation<A>, *, Array<R>> {
        return Collector.of({ mutableListOf() },
            { list: MutableList<Any>, annotation: MergedAnnotation<A> ->
                list.add(
                    annotation.synthesize()
                )
            },
            MergedAnnotationCollectors::combiner,
            { list: MutableList<Any> ->
                generator.apply(list.size).filterNotNull().toTypedArray()
            })
    }

    fun <A : Annotation> toMultiValueMap(
        vararg adaptations: Adapt
    ): Collector<MergedAnnotation<A>, *, MultiValueMap<String, Any>?> {
        return toMultiValueMap(Function.identity(), *adaptations)
    }

    fun <A : Annotation> toMultiValueMap(
        finisher: Function<MultiValueMap<String, Any>, MultiValueMap<String, Any>?>,
        vararg adaptations: Adapt
    ): Collector<MergedAnnotation<A>, *, MultiValueMap<String, Any>?> {
        val characteristics = if (
            isSameInstance(
                finisher,
                Function.identity<Any>()
            )
        ) IDENTITY_FINISH_CHARACTERISTICS else NO_CHARACTERISTICS
        return Collector.of(
            { LinkedMultiValueMap() },
            { map: MultiValueMap<String, Any>, annotation: MergedAnnotation<A> ->
                annotation.asMap(*adaptations).forEach(map::add)
            },
            MergedAnnotationCollectors::combiner, finisher, *characteristics
        )
    }

    private fun isSameInstance(instance: Any, candidate: Any): Boolean {
        return instance === candidate
    }

    fun <E, C : MutableCollection<E>> combiner(collection: C, additions: C): C {
        collection.addAll(additions)
        return collection
    }

    private fun <K, V> combiner(map: MultiValueMap<K, V>, additions: MultiValueMap<K, V>): MultiValueMap<K, V> {
        map.addAll(additions)
        return map
    }

}