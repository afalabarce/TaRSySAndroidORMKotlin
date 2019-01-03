package com.github.tarsys.android.kotlin.orm.annotations

import kotlin.annotation.Target
import kotlin.annotation.Retention

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class DBEntity(
    val TableName: String = "",
    val Description: String = "",
    val ResourceDescription: Int = 0,
    val ResourceDrawable: Int = 0
)