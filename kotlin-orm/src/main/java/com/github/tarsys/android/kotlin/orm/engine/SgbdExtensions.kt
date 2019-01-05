package com.github.tarsys.android.kotlin.orm.engine

import android.database.Cursor
import android.util.Log
import com.github.tarsys.android.kotlin.orm.annotations.DBEntity
import com.github.tarsys.android.kotlin.orm.annotations.Index
import com.github.tarsys.android.kotlin.orm.annotations.Indexes
import com.github.tarsys.android.kotlin.orm.annotations.TableField
import com.github.tarsys.android.kotlin.orm.enums.DBDataType
import com.github.tarsys.android.kotlin.orm.interfaces.IOrmEntity
import com.github.tarsys.android.kotlin.orm.sqlite.SQLiteSupport
import com.github.tarsys.android.support.utilities.AndroidSupport
import java.lang.Exception
import java.lang.reflect.ParameterizedType
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

//region sgbd field properties and methods

val KProperty<*>.dbIndexes: ArrayList<Index>
    get(){
        val returnValue: ArrayList<Index> = arrayListOf()
        val simpleIndex = this.findAnnotation<Index>()
        val collectionIndexes = this.findAnnotation<Indexes>()
        if (simpleIndex != null) returnValue += simpleIndex
        if (collectionIndexes != null) returnValue += collectionIndexes.value
        
        return returnValue
    }

val KProperty<*>.dbDataType: DBDataType
    get(){
        var returnValue: DBDataType = when (this.returnType){
            Boolean::class.createType() -> DBDataType.BooleanDataType
            Int::class.createType() -> DBDataType.IntegerDataType
            Long::class.createType() -> DBDataType.LongDataType
            Float::class.createType(), Double::class.createType() -> DBDataType.RealDataType
            Date::class.createType() -> DBDataType.DateDataType
            else -> DBDataType.StringDataType
        }

        if (this.returnType != Boolean::class.createType() &&
            this.returnType != Int::class.createType() &&
            this.returnType != Long::class.createType() &&
            this.returnType != Float::class.createType() &&
            this.returnType != Date::class.createType() &&
            this.returnType != String::class.createType()){

            if (this.returnType.javaClass.isEnum){
                returnValue = DBDataType.EnumDataType
            }else if (this.returnType.isSubtypeOf(IOrmEntity::class.starProjectedType)){
                returnValue = DBDataType.EntityDataType
            }else if (this.returnType.isSubtypeOf(ArrayList::class.starProjectedType)){
                returnValue = DBDataType.EntityListDataType
            }



        }

        return returnValue
    }

val KProperty<*>.dbDataTypeLength: Int
    get(){
        val tableField = this.findAnnotation<TableField>()

        return when (this.returnType){
            String::class.createType() -> if ((tableField?.DataTypeLength ?: 0) > 0) tableField!!.DataTypeLength else SGBDEngine.applicationInfo?.metaData?.getInt("DB_STRING_DEFAULT_LENGTH", 500) ?: 500
            else -> 0
        }
    }

val KProperty<*>.dbEntityClass: KClass<*>?
    get(){
        val ownedTableField: TableField? = this.findAnnotation()

        if (ownedTableField?.EntityClass != null) return ownedTableField.EntityClass

        if (ownedTableField?.DataType in arrayOf(DBDataType.EntityDataType, DBDataType.EntityListDataType)){
            if (ownedTableField!!.DataType == DBDataType.EntityDataType){
                if (this.returnType.findAnnotation<DBEntity>() != null)
                    return this.returnType::class
            }else{
                val returnClass = this.returnType::class.java

                if (returnClass == ArrayList::class.java && this.returnType.javaType is ParameterizedType){
                    val pType = this.returnType.javaType as ParameterizedType
                    val rType = pType.actualTypeArguments.firstOrNull()?.javaClass?.kotlin
                    if (rType?.annotations?.firstOrNull { x -> x == DBEntity::class } != null)
                        return rType
                }
            }
        }

        return null
    }

val KProperty<*>.tableField: TableField?
    get(){
        var returnValue: TableField? = null
        val tField = this.findAnnotation<TableField>()

        if (tField != null){
            returnValue = this.fnTableField(tField)
        }

        return returnValue
    }

fun KProperty<*>.fnTableField(tableField: TableField): TableField?{
    var returnValue: TableField? = null
    val ownedTableField = this.findAnnotation<TableField>()

    if (ownedTableField != null){
        returnValue = TableField::class.constructors.first().call(
            if (ownedTableField.FieldName.toLowerCase().isNullOrEmpty()) this.name.toLowerCase() ?: tableField.FieldName.toLowerCase() else tableField.FieldName.toLowerCase(),
            tableField.Description,
            tableField.ResourceDescription,
            if (tableField.DataType != DBDataType.None) tableField.DataType else this.dbDataType,
            if (tableField.DataTypeLength == 0) this.dbDataTypeLength else tableField.DataTypeLength,
            if (tableField.EntityClass == null) this.dbEntityClass else tableField.EntityClass,
            tableField.PrimaryKey,
            tableField.ForeignKeyName,
            tableField.ForeignKeyTableName,
            tableField.ForeignKeyFieldName,
            tableField.NotNull,
            tableField.DefaultValue,
            tableField.CascadeDelete,
            tableField.AutoIncrement)
    }

    return returnValue
}

val KProperty<*>.fieldName: String
    get() = "${(if(this.tableField!!.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else AndroidSupport.EmptyString)}${this.tableField!!.FieldName}"
fun KProperty<*>.foreignKeyFieldName(dbEntity: DBEntity): String
{
    if (this.tableField != null){
        return "${dbEntity.TableName.toLowerCase()}_${this.fieldName}"
    }

    return AndroidSupport.EmptyString
}

//endregion

fun KClass<*>.createFilteringEntity(vClass: KClass<*>, dataCursor: Cursor): Any? {
    val dbEntity = this.dbEntity
    var returnValue: Any? = null

    try{
        if (dbEntity != null){
            val primaryKeyProperties = this.primaryKeyProperties

            if (!(primaryKeyProperties?.isEmpty())){
                returnValue = vClass.primaryConstructor?.call()

                for(pkField in primaryKeyProperties){
                    val fieldName = pkField.foreignKeyFieldName(dbEntity)
                    var fieldValue: Any? = when(pkField.tableField!!.DataType){
                        DBDataType.StringDataType, DBDataType.TextDataType, DBDataType.Serializable -> dataCursor.getString(dataCursor.getColumnIndex(fieldName))
                        DBDataType.RealDataType -> dataCursor.getFloat(dataCursor.getColumnIndex(fieldName))
                        DBDataType.IntegerDataType, DBDataType.LongDataType -> dataCursor.getLong(dataCursor.getColumnIndex(fieldName))
                        DBDataType.DateDataType -> {
                            val ticks = dataCursor.getLong(dataCursor.getColumnIndex(fieldName))
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = ticks
                            calendar.time
                        }
                        else -> null
                    }

                    if (fieldValue != null && pkField is KMutableProperty){

                        if (pkField.tableField!!.DataType in arrayOf(DBDataType.IntegerDataType, DBDataType.LongDataType)){
                            try{
                                pkField.setter.call(returnValue, fieldValue as? Long ?: 0)
                            }catch (ex: Exception){
                                if (fieldValue is Int)
                                    pkField.setter.call(returnValue, fieldValue as? Int ?: 0)
                                else
                                    pkField.setter.call(returnValue, (fieldValue as? Long ?: 0).toInt())
                            }
                        }else{
                            pkField.setter.call(returnValue, fieldValue)
                        }
                    }

                }
            }
        }
    }catch (ex: Exception){
        Log.e("createFilteringEntity", ex.message)
        ex.printStackTrace()
    }

    return returnValue
}

val KClass<*>.dbEntity: DBEntity?
    get() {
        var returnValue: DBEntity? = null
        val dbEntityTmp: DBEntity? = this.findAnnotation()

        if (dbEntityTmp != null) {
            returnValue = if (dbEntityTmp.TableName.isEmpty()) DBEntity::class.constructors.first()
                .call(this.simpleName?.toLowerCase() ?: AndroidSupport.EmptyString,
                    dbEntityTmp.Description,
                    dbEntityTmp.ResourceDescription,
                    dbEntityTmp.ResourceDrawable
                ) else {
                dbEntityTmp
            }
        }

        return returnValue
    }

val KClass<*>.dbTable: DBTable?
    get() {
        var returnValue: DBTable? = null
        val dbEntity: DBEntity? = this.dbEntity

        if (dbEntity != null){
            try{
                returnValue = DBTable()
                val properties = this.memberProperties.filter { x -> x.findAnnotation<TableField>() != null }.toList()
                returnValue.relatedClass = this
                returnValue.Table = this.dbEntity
                returnValue.Fields.addAll(properties.map { x -> x.tableField }.filterNotNull())
                returnValue.Indexes += properties.flatMap { x -> x.dbIndexes }


            }catch (ex: Exception){
                returnValue = null
            }
        }

        return returnValue
    }

val KClass<*>.tableName: String
    get() {
        if (this.dbEntity != null)
            return if (!this.dbEntity!!.TableName.isNullOrEmpty()) this.dbEntity!!.TableName else this.simpleName?.toLowerCase() ?: AndroidSupport.EmptyString

        return AndroidSupport.EmptyString
    }

val KClass<*>.dbTableModel: ArrayList<DBTable>
    get(){
        val returnValue: ArrayList<DBTable> = arrayListOf()
        val thisModel = this.dbTable

        if (thisModel != null){
            returnValue += this.memberProperties.filter { x -> x.dbEntityClass != null }.flatMap { y -> y.dbEntityClass!!.dbTableModel }
            returnValue += thisModel
        }

        return returnValue
    }

val KClass<*>.primaryKeyFieldNames: ArrayList<String>
    get() {
        val returnValue: ArrayList<String> = arrayListOf()

        if (this.dbEntity != null)
            returnValue += this.memberProperties.filter { x -> x.tableField?.PrimaryKey ?: false }.map { y -> y.tableField!!.FieldName }

        return returnValue
    }

val KClass<*>.withForeignEntities: Boolean
    get() {
        if (this.dbEntity != null){
            return this.memberProperties.firstOrNull { x -> x.findAnnotation<TableField>()?.DataType in arrayOf(DBDataType.EntityDataType, DBDataType.EntityListDataType) } != null
        }

        return false
    }

val KClass<*>.primaryKeyProperties: ArrayList<KProperty<*>>
    get(){
        val returnValue: ArrayList<KProperty<*>> = arrayListOf()

        if (this.dbEntity != null){
            returnValue += this.memberProperties.filter { x -> x.tableField?.PrimaryKey ?: false }
        }

        return returnValue
    }

val KClass<*>.entityFields: ArrayList<KProperty<*>>
    get(){
        val returnValue: ArrayList<KProperty<*>> = arrayListOf()

        if (this.dbEntity != null){
            returnValue += this.memberProperties.filter { x -> x.tableField!= null && x.returnType::class.dbEntity != null }
        }

        return returnValue
    }

val KClass<*>.entityListFields: ArrayList<KProperty<*>>
    get(){
        val returnValue: ArrayList<KProperty<*>> = arrayListOf()

        if (this.dbEntity != null){
            returnValue += this.memberProperties.filter { x -> (x.tableField?.DataType ?: DBDataType.None) == DBDataType.EntityListDataType }
        }

        return returnValue
    }

val KClass<*>.tableFieldProperties: ArrayList<KProperty<*>>
    get(){
        val returnValue: ArrayList<KProperty<*>> = arrayListOf()

        if (this.dbEntity != null){
            returnValue += this.memberProperties.filter { x -> x.findAnnotation<TableField>()!= null }
        }

        return returnValue
    }

fun KClass<*>.entityListRelationTables(addWithoutCascadeDelete: Boolean): List<String> = this.entityListFields
                    .filter { x -> x.tableField!!.CascadeDelete || (!addWithoutCascadeDelete && !x.tableField!!.CascadeDelete) }
                    .map { f -> "rel_${this.dbEntity!!.TableName.toLowerCase()}_${f.dbEntityClass!!.tableName}" }

