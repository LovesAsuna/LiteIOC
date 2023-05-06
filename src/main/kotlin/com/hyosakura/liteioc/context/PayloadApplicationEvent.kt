package com.hyosakura.liteioc.context

import com.hyosakura.liteioc.context.event.ApplicationEvent
import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.core.ResolvableTypeProvider

/**
 * @author LovesAsuna
 **/
class PayloadApplicationEvent<T> : ApplicationEvent, ResolvableTypeProvider {

    val payload: T

    val payloadType: ResolvableType?

    constructor(source: Any, payload: T, payloadType: ResolvableType?) : super(source) {
        this.payload = payload
        this.payloadType = payloadType
    }

    constructor(source: Any, payload: T) : this(source, payload, null)

    override fun getResolvableType(): ResolvableType {
        return ResolvableType.forClassWithGenerics(javaClass, this.payloadType)
    }

}