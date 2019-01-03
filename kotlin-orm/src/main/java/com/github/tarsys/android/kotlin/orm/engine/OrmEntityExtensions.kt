package com.github.tarsys.android.kotlin.orm.engine

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.github.tarsys.android.kotlin.orm.annotations.TableField
import com.github.tarsys.android.kotlin.orm.dataobjects.DataTable
import com.github.tarsys.android.kotlin.orm.enums.DBDataType
import com.github.tarsys.android.kotlin.orm.interfaces.IOrmEntity
import com.github.tarsys.android.kotlin.orm.sqlite.SQLiteSupport
import com.github.tarsys.android.support.utilities.AndroidSupport
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.starProjectedType

//region Useful query properties

fun Boolean.toInt() = if (this) 1 else 0

val <T: IOrmEntity>T.hasFieldEntities: Boolean
    get() = !this.javaClass.kotlin.entityFields.isEmpty() || !this.javaClass.kotlin.entityListFields.isEmpty()

fun <T: IOrmEntity>T.entityReport(includeWithoutCascadeDelete: Boolean): ArrayList<IOrmEntity>{
    val returnValue: ArrayList<IOrmEntity> = arrayListOf()

    if (this.hasFieldEntities){
        val entityFields = (this.javaClass.kotlin.entityFields + this.javaClass.kotlin.entityListFields)
            .filter { x -> x.tableField!!.CascadeDelete ||(includeWithoutCascadeDelete && !x.tableField!!.CascadeDelete) }

        for(field in entityFields){
            try{
                var value = field.getter.call(this) as? IOrmEntity
                if (field.tableField!!.DataType == DBDataType.EntityDataType && value != null){
                    returnValue += value
                }else if (field.returnType.javaClass.isArray || field.returnType.javaClass == ArrayList::class.java){
                    returnValue.addAll(value as ArrayList<IOrmEntity>)
                }
            }catch (ex: Exception){
                returnValue.clear()
            }
        }
    }

    return returnValue
}

fun <T: IOrmEntity>T.primaryKeyFilter(forTableRelation: Boolean): Filter?
{
    try{
        val dbEntity = this.javaClass.kotlin.dbEntity
        if (dbEntity != null){
            var whereClause = AndroidSupport.EmptyString
            val whereData: ArrayList<String> = arrayListOf()
            val classFields = this.javaClass.kotlin.primaryKeyProperties
            for (field in classFields){
                whereClause += if (!whereClause.isEmpty()) " and " else AndroidSupport.EmptyString
                whereClause += (if (forTableRelation) "${dbEntity.TableName.toLowerCase()}_" else AndroidSupport.EmptyString) +
                        "${if(field.tableField!!.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else AndroidSupport.EmptyString}${field.tableField!!.FieldName}" +
                        "=?"
                val fieldValue = field.getter.call(this)

                when (field.tableField!!.DataType){
                    DBDataType.StringDataType, DBDataType.TextDataType, DBDataType.Serializable -> whereData.add(fieldValue?.toString() ?: field.tableField!!.DefaultValue)
                    DBDataType.RealDataType -> whereData.add((fieldValue as? Float ?:
                    field.tableField!!.DefaultValue?.toFloatOrNull() ?: 0f).toString()
                        .replace(",","."))
                    DBDataType.IntegerDataType, DBDataType.LongDataType -> whereData.add (fieldValue?.toString() ?: field.tableField!!.DefaultValue)
                    DBDataType.DateDataType -> whereData.add(((fieldValue as? Date)?.time ?: 0L).toString())
                    DBDataType.EnumDataType -> whereData.add((fieldValue as? Enum<*>)?.ordinal?.toString() ?: field.tableField!!.DefaultValue)
                    DBDataType.BooleanDataType -> whereData.add((fieldValue as? Boolean)?.toInt()?.toString() ?: field.tableField!!.DefaultValue)
                    DBDataType.EntityDataType -> {
                        val filter = (fieldValue as? IOrmEntity)?.primaryKeyFilter(true)
                        if (filter != null){
                            whereClause += " and ${filter.FilterString}"
                            whereData.addAll(filter.FilterData)
                        }
                    }
                    else -> whereData.add(fieldValue?.toString() ?: field.tableField!!.DefaultValue)
                }
            }

            if (!whereData.isEmpty()){
                val returnValue = Filter()
                returnValue.FilterString = whereClause
                returnValue.FilterData += whereData

                return returnValue
            }
        }
    }catch (ex: Exception){
        Log.e("primaryKeyFilter", ex.message)
        ex.printStackTrace()
    }

    return null
}

val <T: IOrmEntity>T.foreignKeyContentValues: ContentValues?
get(){
    var returnValue: ContentValues? = null

    try{
        val dbEntity = this.javaClass.kotlin.dbEntity

        if (dbEntity != null){
            returnValue = ContentValues(0)
            val primaryKeyProperties = this.javaClass.kotlin.primaryKeyProperties

            for (field in primaryKeyProperties){
                val fieldName = "${dbEntity.TableName.toLowerCase()}_${field.foreignKeyFieldName(dbEntity)}"
                val fieldValue = field.getter.call(this)

                when(field.tableField!!.DataType){
                    DBDataType.StringDataType, DBDataType.TextDataType -> returnValue.put(fieldName, fieldValue as? String ?: field.tableField!!.DefaultValue)
                    DBDataType.IntegerDataType, DBDataType.LongDataType -> returnValue.put(fieldName, fieldValue?.toString() ?: field.tableField!!.DefaultValue)
                    DBDataType.RealDataType -> returnValue.put(fieldName, fieldValue?.toString()?.replace(",",".") ?: field.tableField!!.DefaultValue)
                    DBDataType.DateDataType -> returnValue.put(fieldName, (fieldValue as? Date)?.time?.toString() ?: field.tableField!!.DefaultValue ?: "0")
                }
            }
        }
    }catch (ex: java.lang.Exception){
        returnValue = null
        Log.e("foreignKeyContentValues", ex.message)
        ex.printStackTrace()
    }

    return returnValue
}

val ContentValues.toFilter: Filter?
    get(){
        try{
            if (this.size() > 0){
                val returnValue = Filter()
                for (entry in this.valueSet()){
                    returnValue.FilterString += if (returnValue.FilterString.trim().equals(AndroidSupport.EmptyString)) AndroidSupport.EmptyString else " and "
                    returnValue.FilterString += "${entry.key}=?"
                    returnValue.FilterData.add(entry.value.toString())
                }

                return returnValue
            }
        }catch (ex:java.lang.Exception){
            Log.e("ContentValues.toFilter", ex.message)
            ex.printStackTrace()
        }

        return null
    }

//endregion

//region Useful query methods

inline fun <reified T: IOrmEntity>Filter.execute(sqliteDb: SQLiteDatabase): ArrayList<T>{
    val returnValue: ArrayList<T> = arrayListOf()
    try{
        val dbEntity = T::class.dbEntity
        val returnType = T::class

        if (dbEntity != null) {
            val navQuery = sqliteDb.query(
                dbEntity.TableName,
                null,
                this.FilterString,
                this.FilterData.toTypedArray(),
                null,
                null,
                null
            )

            while (navQuery?.moveToNext() == true) {
                val obj =
                    (returnType.createFilteringEntity(returnType, navQuery) as T).findEntityByPrimaryKey(sqliteDb, true)
                if (obj != null)
                    returnValue += obj
            }
        }
    }catch (ex:Exception){
        Log.e("IOrmEntity.filter", ex.message)
        ex.printStackTrace()
    }

    return returnValue
}

inline fun <reified T:IOrmEntity>SQLiteDatabase.filter(whereFilter: String, whereValues: Array<String>, orderBy: String, recursiveLoad: Boolean): ArrayList<T>{
    val returnValue: ArrayList<T> = arrayListOf()
    val dbEntity = T::class.dbEntity
    try{
        if (dbEntity != null && this.isOpen){
            val query = this.query(dbEntity.TableName, null, whereFilter, whereValues, null,null, orderBy)

            while (query?.moveToNext() == true){
                val pkEntity = T::class.createFilteringEntity(T::class, query) as? T
                if (pkEntity != null){
                    val entity = pkEntity.findEntityByPrimaryKey(this, true)
                    if (entity != null) returnValue += entity
                }
            }
        }
    }catch (ex: Exception){
        returnValue.clear()
        Log.e("SQLiteDatabase.filter", ex.message)
        ex.printStackTrace()
    }


    return returnValue
}

fun SQLiteDatabase.rawQueryToDataTable(sqlQuery: String): DataTable? = this.rawQueryToDataSource(sqlQuery, false)

fun SQLiteDatabase.rawQueryToDataSource(sqlQuery: String, forceOnlyDate: Boolean): DataTable?{
    try{
        val cursor = this.rawQuery(sqlQuery, null)
        if (cursor != null){
            val returnValue = DataTable()
            cursor.moveToFirst()
            cursor.columnNames.forEach { column ->
                // TODO: Pending implementing datasource read...
            }
        }
    }catch (ex: Exception){
        Log.e("rawQueryToDataSource", ex.message)
        ex.printStackTrace()
    }

    return null
}

fun <T:IOrmEntity>SQLiteDatabase.filterObjectQuery(): ArrayList<T>{
    val returnValue: ArrayList<T> = arrayListOf()
    // TODO: Pending implementing object filter read...

    return returnValue
}

//endregion

//region CRUD methods

fun <T: IOrmEntity>T.findEntityByPrimaryKey(sqliteDb: SQLiteDatabase?, entity: T, recursiveLoad: Boolean): T? {
    try{
        val returnValue: T = entity

        val dbEntity = this::class.dbEntity

        if (dbEntity != null){
            val primaryKeyFilter = this.primaryKeyFilter(false)
            if (primaryKeyFilter != null && sqliteDb?.isOpen == true){
                val query = sqliteDb.query(dbEntity.TableName, null, primaryKeyFilter.FilterString, primaryKeyFilter.FilterData.toTypedArray(), null, null, null)

                if (query?.moveToFirst() == true){
                    val fieldProperties =  this::class.tableFieldProperties

                    for(field in fieldProperties){
                        val tableField = field.tableField

                        if (field is KMutableProperty){
                            try{
                                field.setter.call(returnValue, when(tableField!!.DataType){
                                    DBDataType.StringDataType, DBDataType.TextDataType -> query.getString(query.getColumnIndex(field.fieldName))
                                    DBDataType.RealDataType -> query.getFloat(query.getColumnIndex(field.fieldName))
                                    DBDataType.IntegerDataType, DBDataType.LongDataType -> query.getLong(query.getColumnIndex(field.fieldName))
                                    DBDataType.DateDataType -> {
                                        val calendar = Calendar.getInstance()
                                        calendar.timeInMillis = query.getLong(query.getColumnIndex(field.fieldName))
                                        calendar.time
                                    }
                                    DBDataType.BooleanDataType -> query.getInt(query.getColumnIndex(field.fieldName)) == 1
                                    DBDataType.Serializable -> GsonBuilder().create().fromJson(query.getString(query.getColumnIndex(field.fieldName)), field.returnType::class.java)
                                    DBDataType.EnumDataType -> {
                                        if (field.returnType::class.java.isEnum){
                                            val ordinalValue = query.getInt(query.getColumnIndex(field.fieldName))
                                            field.returnType::class.java.cast(ordinalValue)
                                        }else{
                                            null
                                        }
                                    }
                                    DBDataType.EntityDataType -> {
                                        val dbEntityField = field.returnType::class.dbEntity

                                        if (dbEntityField != null){
                                            val obj = field.returnType::class.createFilteringEntity(field.tableField!!.EntityClass, query) as? IOrmEntity
                                            obj?.findEntityByPrimaryKey(sqliteDb, obj, recursiveLoad)

                                        }else{
                                            null
                                        }
                                    }
                                    DBDataType.EntityListDataType -> {
                                        val fieldDbEntity = field.dbEntityClass?.dbEntity
                                        val fieldArray: ArrayList<IOrmEntity> = arrayListOf()
                                        val returnType = field.returnType

                                        if (fieldDbEntity != null && (returnType.javaClass.isArray || returnType == ArrayList::class.starProjectedType)){
                                            val relationTableName = "rel_${dbEntity.TableName.toLowerCase()}_${fieldDbEntity.TableName.toLowerCase()}"
                                            val foreignFilter = entity.primaryKeyFilter(true)

                                            if (foreignFilter != null){
                                                val navQuery = sqliteDb.query(relationTableName, null, foreignFilter.FilterString, foreignFilter.FilterData.toTypedArray(), null, null, null)

                                                while (navQuery?.moveToNext() == true){
                                                    val obj = field.tableField!!.EntityClass::class.createFilteringEntity(field.tableField!!.EntityClass, navQuery) as? IOrmEntity
                                                    if (obj != null) fieldArray.add(obj)
                                                }
                                            }
                                        }

                                        returnValue
                                    }
                                    else -> null
                                })
                            }catch (ex:Exception){
                                Log.e("findEntityByPrimaryKey", ex.message)
                                ex.printStackTrace()
                            }
                        }
                    }
                }
            }
        }

        return returnValue
    }catch (ex: Exception){
        Log.e("findEntityByPrimaryKey", ex.message)
        ex.printStackTrace()
    }

    return null
}

inline fun <reified T:IOrmEntity>T.read():T?{
    val sqliteDb = SGBDEngine.SQLiteDataBase(true)

    if (sqliteDb != null){
        val returnValue = this.findEntityByPrimaryKey(sqliteDb, true)
        sqliteDb.close()

        return returnValue
    }

    return null
}

inline fun <reified T:IOrmEntity>T.read(recursiveLoad: Boolean):T?{
    val sqliteDb = SGBDEngine.SQLiteDataBase(true)

    if (sqliteDb != null){
        val returnValue = this.findEntityByPrimaryKey(sqliteDb, recursiveLoad)
        sqliteDb.close()

        return returnValue
    }

    return null
}

inline fun <reified T: IOrmEntity>T.findEntityByPrimaryKey(sqliteDb: SQLiteDatabase?, recursiveLoad: Boolean): T? {
    try{
        val returnValue: T = T::class.java.newInstance()

        val dbEntity = this::class.dbEntity

        if (dbEntity != null){
            val primaryKeyFilter = this.primaryKeyFilter(false)
            if (primaryKeyFilter != null && sqliteDb?.isOpen == true){
                val query = sqliteDb.query(dbEntity.TableName, null, primaryKeyFilter.FilterString, primaryKeyFilter.FilterData.toTypedArray(), null, null, null)

                if (query?.moveToFirst() == true){
                    val fieldProperties =  this::class.tableFieldProperties

                    for(field in fieldProperties){
                        val tableField = field.tableField

                        if (field is KMutableProperty){
                            try{
                                field.setter.call(returnValue, when(tableField!!.DataType){
                                    DBDataType.StringDataType, DBDataType.TextDataType -> query.getString(query.getColumnIndex(field.fieldName))
                                    DBDataType.RealDataType -> query.getFloat(query.getColumnIndex(field.fieldName))
                                    DBDataType.IntegerDataType, DBDataType.LongDataType -> if (field.returnType == Int::class.starProjectedType) query.getInt(query.getColumnIndex(field.fieldName)) else query.getLong(query.getColumnIndex(field.fieldName))
                                    DBDataType.DateDataType -> {
                                        val calendar = Calendar.getInstance()
                                        calendar.timeInMillis = query.getLong(query.getColumnIndex(field.fieldName))
                                        calendar.time
                                    }
                                    DBDataType.BooleanDataType -> query.getInt(query.getColumnIndex(field.fieldName)) == 1
                                    DBDataType.Serializable -> GsonBuilder().create().fromJson(query.getString(query.getColumnIndex(field.fieldName)), field.returnType::class.java)
                                    DBDataType.EnumDataType -> {
                                        if (field.returnType::class.java.isEnum){
                                            val ordinalValue = query.getInt(query.getColumnIndex(field.fieldName))
                                            field.returnType::class.java.cast(ordinalValue)
                                        }else{
                                            null
                                        }
                                    }
                                    DBDataType.EntityDataType -> {
                                        val dbEntityField = field.returnType::class.dbEntity

                                        if (dbEntityField != null){
                                            val entity = field.returnType::class.createFilteringEntity(field.returnType::class, query) as? IOrmEntity

                                            entity?.findEntityByPrimaryKey(sqliteDb, entity, recursiveLoad)
                                        }else{
                                            null
                                        }
                                    }
                                    DBDataType.EntityListDataType ->{
                                        val fieldClass = field.returnType::class
                                        val fieldDbEntity = fieldClass.dbEntity

                                        if (fieldDbEntity != null && (fieldClass.java.isArray || fieldClass == ArrayList::class) && tableField.EntityClass.isSubclassOf(IOrmEntity::class)){
                                            val objectList: ArrayList<IOrmEntity> = arrayListOf()
                                            val relationTable = "rel_${dbEntity.TableName.toLowerCase()}_${fieldDbEntity.TableName.toLowerCase()}"
                                            val filter = this.primaryKeyFilter(true)
                                            if (filter != null){
                                                val curNav = sqliteDb.query(relationTable, null, filter.FilterString, filter.FilterData.toTypedArray(), null, null, null)

                                                while(curNav?.moveToNext() == true){
                                                    val relatedFk = tableField.EntityClass.createFilteringEntity(tableField.EntityClass, curNav) as? IOrmEntity
                                                    val obj = relatedFk?.findEntityByPrimaryKey(sqliteDb, relatedFk,recursiveLoad)

                                                    if (obj != null) objectList.add(obj)
                                                }

                                                curNav?.close()
                                            }

                                            objectList
                                        }else{
                                            null
                                        }
                                    }
                                    else -> null
                                })
                            }catch (ex:Exception){

                            }
                        }
                    }
                }
            }
        }

        return returnValue
    }catch (ex: Exception){
        Log.e("findEntityByPrimaryKey", ex.message)
        ex.printStackTrace()
    }

    return null
}

fun <T: IOrmEntity>T.delete(): Boolean{
    val sqliteDb = SGBDEngine.SQLiteDataBase(false)

    if (sqliteDb?.isOpen == true){
        val dbEntity = this.javaClass.kotlin.dbEntity

        if (dbEntity != null){
            val pkFilter = this.primaryKeyFilter(false)
            if (pkFilter != null){
                val relatedEntities = this.entityReport(false)
                val results: ArrayList<Boolean> = arrayListOf()

                try{
                    // first we need to delete relation tables
                    val relationTables = this.javaClass.kotlin.entityListRelationTables(false)
                    val pkFilterRT = this.primaryKeyFilter(true)
                    if (pkFilterRT != null){
                        relationTables.forEach { x -> results += sqliteDb.delete(x, pkFilterRT.FilterString, pkFilterRT.FilterData.toTypedArray()) > 0 }
                        if (results.firstOrNull { x -> !x } != true ) {
                            //next entity data
                            relatedEntities.forEach { x -> results += x.delete() }
                        }
                    }
                    if (results.firstOrNull { x -> !x } != true ){
                        // and finally, this entity...
                        var returnValue = sqliteDb.delete(dbEntity.TableName, pkFilter.FilterString, pkFilter.FilterData.toTypedArray()) > 0

                        return returnValue
                    }
                }catch (ex: java.lang.Exception){

                }

            }
        }
    }

    return false
}

fun <T: IOrmEntity>T.save(): Boolean = this.save(true)

fun <T: IOrmEntity>T.save(forceEntitySave: Boolean): Boolean{
    val sqliteDb = SGBDEngine.SQLiteDataBase(false)

    if (sqliteDb?.isOpen == true){
        val returnValue = this.save(sqliteDb, forceEntitySave)
        sqliteDb.close()

        return returnValue
    }

    return false
}

fun <T: IOrmEntity>T.save(sqliteDb: SQLiteDatabase, forceEntitySave: Boolean): Boolean{
    try{
        val dbEntity = this.javaClass.kotlin.dbEntity

        if (sqliteDb.isOpen && dbEntity != null){
            val pkFilter = this.primaryKeyFilter(false)
            if (pkFilter != null){
                val contentValues = ContentValues()
                val entityFields: ArrayList<IOrmEntity> = arrayListOf()
                val entityListFields: ArrayList<IOrmEntity> = arrayListOf()
                val entityListTableFields: ArrayList<TableField> = arrayListOf()

                var error: Boolean = false
                var autoIncrementField: KProperty<*>? = this.javaClass.kotlin.tableFieldProperties.firstOrNull { x -> x.tableField!!.AutoIncrement }

                val fields = this.javaClass.kotlin.tableFieldProperties.filter { x -> !x.tableField!!.AutoIncrement }

                //region Data load for persistence...

                fields.forEach { field ->
                    try{
                        val fieldValue = field.getter.call(this)

                        when (field.dbDataType){
                            DBDataType.StringDataType, DBDataType.TextDataType -> contentValues.put(field.fieldName, fieldValue as? String ?: field.tableField!!.DefaultValue)
                            DBDataType.Serializable -> contentValues.put(field.fieldName, GsonBuilder().create().toJson(fieldValue))
                            DBDataType.RealDataType -> contentValues.put(field.fieldName, fieldValue as? Float ?: field.tableField!!.DefaultValue?.toFloat() ?: 0f)
                            DBDataType.IntegerDataType, DBDataType.LongDataType -> contentValues.put(field.fieldName, fieldValue as? Long ?: (fieldValue as? Int)?.toLong() ?: try{ field.tableField!!.DefaultValue?.toLong()}catch (ex:Exception){ 0L } ?: 0)
                            DBDataType.BooleanDataType -> contentValues.put(field.fieldName, if (fieldValue as? Boolean ?: field.tableField!!.DefaultValue == "1") 1 else 0)
                            DBDataType.DateDataType -> contentValues.put(field.fieldName, (field as? Date)?.time ?: 0 )
                            DBDataType.EntityDataType -> {
                                entityFields += fieldValue as IOrmEntity
                                contentValues.putAll((fieldValue as? IOrmEntity)?.foreignKeyContentValues)
                            }
                            DBDataType.EntityListDataType -> {
                                entityListFields += fieldValue as ArrayList<IOrmEntity>
                                entityListTableFields += field.tableField!!
                            }
                            DBDataType.EnumDataType -> {
                                if (field.returnType::class.java.isEnum){
                                    val ordinalValue = (fieldValue as? Enum<*>)?.ordinal ?: -1
                                    contentValues.put(field.fieldName, ordinalValue)
                                }else{
                                    null
                                }
                            }
                            else -> {

                            }
                        }
                    }catch (ex:Exception){
                        Log.e("IOrmEntity.save->map", ex.message)
                        ex.printStackTrace()
                        error = true
                        return@forEach
                    }
                }

                //endregion

                if (error) return false

                // if we are here... we don't have any errors, and... we can persist entity
                if (forceEntitySave){
                    entityFields.forEach { x ->
                        if (!x.save(sqliteDb, true)){
                            error = true
                            return@forEach
                        }
                    }
                }

                if (error) return false

                try{
                    sqliteDb.delete(dbEntity.TableName, pkFilter.FilterString, pkFilter.FilterData.toTypedArray())
                }catch (ex: java.lang.Exception){
                    Log.e("IOrmEntity.save", "Innexistent Entity: ${ex.message}")
                    ex.printStackTrace()
                }

                if (autoIncrementField != null){
                    val id: Int = autoIncrementField.getter.call(this) as? Int ?: 0
                    if (id > 0) contentValues.put(autoIncrementField.fieldName, id)
                }

                val identity: Long = sqliteDb.insert(dbEntity.TableName, null, contentValues)

                if (autoIncrementField is KMutableProperty) autoIncrementField.setter.call(this, identity)

                if (identity < 0) return false

                val filterRelations = this.primaryKeyFilter(true)

                if (filterRelations != null){
                    entityListTableFields.forEach { x ->
                        val entityTableName = x.EntityClass.dbEntity!!.TableName
                        val relatedTable = "rel_${dbEntity.TableName.toLowerCase()}_$entityTableName"

                        val queryEntity = sqliteDb.query(relatedTable, null, filterRelations.FilterString, filterRelations.FilterData.toTypedArray(), null,null, null)
                        while (queryEntity?.moveToNext() == true){
                            var fgFilter = AndroidSupport.EmptyString
                            val fgValues: ArrayList<String> = arrayListOf()

                            queryEntity.columnNames.filter { xc -> xc.startsWith(entityTableName) }.forEach { column ->
                                fgFilter += "${if(fgFilter.isEmpty()) AndroidSupport.EmptyString else " and "}${column.replace("${entityTableName.toLowerCase()}_", AndroidSupport.EmptyString)} = ?"
                                fgValues += queryEntity.getInt(queryEntity.getColumnIndex(column)).toString()
                            }

                            if (!fgFilter.isEmpty() && !fgValues.isEmpty()) sqliteDb.delete(entityTableName, fgFilter, fgValues.toTypedArray())
                        }

                        sqliteDb.delete(relatedTable, filterRelations.FilterString, filterRelations.FilterData.toTypedArray())
                    }

                    entityListFields.forEach { eField ->
                        val relatedTable = "rel_${dbEntity.TableName.toLowerCase()}_${eField.javaClass.kotlin.tableName}"
                        val returnValue = eField.save(sqliteDb, true)
                        if (returnValue){
                            val cValues = ContentValues()
                            cValues.putAll(this.foreignKeyContentValues)
                            cValues.putAll(eField.foreignKeyContentValues)
                            val deleteFilter = cValues.toFilter

                            if (deleteFilter != null){
                                try{
                                    sqliteDb.delete(relatedTable, deleteFilter.FilterString, deleteFilter.FilterData.toTypedArray())
                                }catch (ex: Exception){
                                    Log.e("IOrmEntity.save:X", ex.message)
                                    ex.printStackTrace()
                                }
                            }

                            try{
                                val entityId = sqliteDb.insert(relatedTable, null, cValues)

                            }catch (ex:Exception){
                                Log.e("IOrmEntity.save:Y", ex.message)
                                ex.printStackTrace()
                            }
                        }
                    }

                    return true
                }
            }
        }
    }catch (ex: Exception){
        Log.e("IOrmEntity.save", ex.message)
        ex.printStackTrace()
    }

    return false
}

//endregion