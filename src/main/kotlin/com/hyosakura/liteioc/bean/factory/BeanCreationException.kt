package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.bean.FatalBeanException
import java.io.PrintStream
import java.io.PrintWriter

/**
 * @author LovesAsuna
 **/
open class BeanCreationException : FatalBeanException {

    private var beanName: String? = null

    private var resourceDescription: String? = null

    private var relatedCauses: MutableList<Throwable>? = null

    constructor(msg: String) : super(msg)

    constructor(msg: String, cause: Throwable) : super(msg, cause)

    constructor(beanName: String?, msg: String) : super("Error creating bean with name '$beanName': $msg") {
        this.beanName = beanName
    }

    constructor(beanName: String, msg: String, cause: Throwable) : this(beanName, msg) {
        initCause(cause)
    }

    fun addRelatedCause(ex: Throwable) {
        if (relatedCauses == null) {
            relatedCauses = ArrayList()
        }
        relatedCauses!!.add(ex)
    }

    fun getRelatedCauses(): Array<Throwable>? {
        return if (relatedCauses == null) {
            null
        } else relatedCauses!!.toTypedArray()
    }

    override fun toString(): String {
        val sb = StringBuilder(super.toString())
        if (relatedCauses != null) {
            for (relatedCause in relatedCauses!!) {
                sb.append("\nRelated cause: ")
                sb.append(relatedCause)
            }
        }
        return sb.toString()
    }

    fun printStackTrace(ps: PrintStream) {
        synchronized(ps) {
            super.printStackTrace(ps)
            if (relatedCauses != null) {
                for (relatedCause in relatedCauses!!) {
                    ps.println("Related cause:")
                    relatedCause.printStackTrace(ps)
                }
            }
        }
    }

    fun printStackTrace(pw: PrintWriter) {
        synchronized(pw) {
            super.printStackTrace(pw)
            if (relatedCauses != null) {
                for (relatedCause in relatedCauses!!) {
                    pw.println("Related cause:")
                    relatedCause.printStackTrace(pw)
                }
            }
        }
    }

    fun getBeanName() = beanName

}