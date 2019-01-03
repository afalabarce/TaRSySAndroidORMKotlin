package com.github.tarsys.android.kotlin.orm.annotations

import com.github.tarsys.android.kotlin.orm.enums.OrderCriteria
import kotlin.annotation.Retention
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Indexes(val value: Array<Index>)
