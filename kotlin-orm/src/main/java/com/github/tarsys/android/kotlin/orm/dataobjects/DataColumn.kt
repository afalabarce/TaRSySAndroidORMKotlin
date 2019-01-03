package com.github.tarsys.android.kotlin.orm.dataobjects

import com.github.tarsys.android.support.utilities.AndroidSupport
import java.io.Serializable
import kotlin.reflect.KClass

class DataColumn: Serializable {
    var ColumnName: String = AndroidSupport.EmptyString
    var ColumnTitle: String = AndroidSupport.EmptyString
    var ColumnDataType: KClass<*> = String::class
    var Visible: Boolean = false
}