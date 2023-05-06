package com.hyosakura.liteioc.bean.factory.support

/**
 * @author LovesAsuna
 **/
class ImplicitlyAppearedSingletonException : IllegalStateException {
    constructor() : super(
        "About-to-be-created singleton instance implicitly appeared through the " +
                "creation of the factory bean that its bean definition points to"
    )

}