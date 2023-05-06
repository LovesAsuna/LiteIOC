package com.hyosakura.liteioc.bean.factory.config

object AutowiredPropertyMarker {

    override fun hashCode(): Int {
        return AutowiredPropertyMarker::class.java.hashCode()
    }

    override fun toString(): String {
        return "(autowired)"
    }

}