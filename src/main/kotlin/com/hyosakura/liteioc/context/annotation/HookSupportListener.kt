package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.aop.HookSupport

/**
 * @author LovesAsuna
 **/
interface HookSupportListener {

    fun activated(hook: HookSupport)

    fun adviceChanged(hook: HookSupport)

}