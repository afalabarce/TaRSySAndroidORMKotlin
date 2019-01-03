package com.github.tarsys.android.kotlin.orm.annotations

import com.github.tarsys.android.kotlin.orm.enums.DBDataType
import com.github.tarsys.android.kotlin.orm.interfaces.IOrmEntity
import kotlin.reflect.KClass
import kotlin.annotation.Retention

@Retention(AnnotationRetention.RUNTIME)
annotation class TableField(
    val FieldName: String = "",
    val Description: String = "",
    val ResourceDescription: Int = 0,
    val DataType: DBDataType = DBDataType.None,
    val DataTypeLength: Int = 0,
    val EntityClass: KClass<*> = String::class,
    val PrimaryKey: Boolean = false,
    val ForeignKeyName: String = "",
    val ForeignKeyTableName: String = "",
    val ForeignKeyFieldName: String = "",
    val NotNull: Boolean = false,
    val DefaultValue: String = "",
    val CascadeDelete: Boolean = false,
    val AutoIncrement: Boolean = false
)