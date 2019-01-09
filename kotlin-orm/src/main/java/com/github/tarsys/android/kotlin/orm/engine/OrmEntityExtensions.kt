package com.github.tarsys.android.kotlin.orm.engine

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.github.tarsys.android.kotlin.orm.annotations.TableField
import com.github.tarsys.android.kotlin.orm.dataobjects.DataTable
import com.github.tarsys.android.kotlin.orm.engine.utilities.toFilter
import com.github.tarsys.android.kotlin.orm.engine.utilities.toInt
import com.github.tarsys.android.kotlin.orm.enums.DBDataType
import com.github.tarsys.android.kotlin.orm.interfaces.IOrmEntity
import com.github.tarsys.android.kotlin.orm.sqlite.SQLiteSupport
import com.github.tarsys.android.support.utilities.AndroidSupport
import com.google.gson.GsonBuilder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType

//region Useful query methods

inline fun <reified T:IOrmEntity>KClass<T>.mapSoftEntity(cursorEntity: Cursor): T?{
    try{
        val returnValue: T? = T::class.primaryConstructor?.call()
        val fieldProperties = T::class.tableFieldProperties.filter { x -> x is KMutableProperty }
        val dbEntity = T::class.dbEntity

        for(field in fieldProperties){
            val tableField = field.tableField!!

            (field as KMutableProperty).setter.call(returnValue, when (tableField.DataType){
                DBDataType.StringDataType, DBDataType.TextDataType -> cursorEntity.getString(cursorEntity.getColumnIndex(field.fieldName))
                DBDataType.RealDataType -> cursorEntity.getFloat(cursorEntity.getColumnIndex(field.fieldName))
                DBDataType.IntegerDataType, DBDataType.LongDataType -> if (field.returnType == Int::class.starProjectedType) cursorEntity.getInt(cursorEntity.getColumnIndex(field.fieldName)) else cursorEntity.getLong(cursorEntity.getColumnIndex(field.fieldName))
                DBDataType.DateDataType -> {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = cursorEntity.getLong(cursorEntity.getColumnIndex(field.fieldName))
                    calendar.time
                }
                DBDataType.BooleanDataType -> cursorEntity.getInt(cursorEntity.getColumnIndex(field.fieldName)) == 1
                DBDataType.Serializable -> GsonBuilder().create().fromJson(cursorEntity.getString(cursorEntity.getColumnIndex(field.fieldName)), field.returnType::class.java)
                DBDataType.EnumDataType -> {
                    if (field.returnType::class.java.isEnum){
                        val ordinalValue = cursorEntity.getInt(cursorEntity.getColumnIndex(field.fieldName))
                        field.returnType::class.java.cast(ordinalValue)
                    }else{
                        null
                    }
                }
                DBDataType.EntityDataType -> {
                    val dbEntityField = field.dbEntityClass?.dbEntity

                    if (dbEntityField != null){
                        field.dbEntityClass?.createFilteringEntity(field.dbEntityClass!!, cursorEntity) as? IOrmEntity
                    }else{
                        null
                    }
                }
                DBDataType.EntityListDataType ->{
                    val fieldClass = field.dbEntityClass
                    val fieldDbEntity = fieldClass?.dbEntity

                    if (fieldDbEntity != null && (field.returnType.javaClass.isArray || field.returnType.isSubtypeOf(ArrayList::class.starProjectedType)) && tableField.EntityClass.isSubclassOf(IOrmEntity::class)){
                        arrayListOf<IOrmEntity>()
                    }else{
                        null
                    }
                }
                else -> null
            })
        }

        return returnValue
    }catch (ex: Exception){
        Log.e("mapSoftEntity", ex.message)
        ex.printStackTrace()
    }


    return null
}

inline fun <reified T:IOrmEntity> KClass<T>.filter(predicate: (T) -> Boolean): ArrayList<T> = this.filterMap(false, predicate)

inline fun <reified T:IOrmEntity> KClass<T>.fullFilter(predicate: (T) -> Boolean): ArrayList<T> = this.filterMap(true, predicate)

inline fun <reified T:IOrmEntity> KClass<T>.filterMap(fullMap: Boolean, predicate: (T) -> Boolean): ArrayList<T>{
    val returnValue: ArrayList<T> = arrayListOf()

    val sqliteDb = SGBDEngine.SQLiteDataBase(true)

    if (sqliteDb?.isOpen == true){
        val dbEntity = T::class.dbEntity

        if (dbEntity != null){
            val query = sqliteDb.query(dbEntity.TableName, null, null, null, null, null, null)

            while (query?.moveToNext() == true){
                val softEntity = T::class.mapSoftEntity(query)

                if (softEntity != null)
                    returnValue += softEntity
            }

            query.close()
            sqliteDb.close()
        }
    }

    return  ArrayList(returnValue.filter(predicate).map{ p -> (if (!fullMap) p else { p!!.read(); p}) } )
}

//endregion

//region CRUD methods

/**
 * Gets entity identified by its primary key
 * @return true if the entity has been loaded from database, false otherwise
 */
fun <T:IOrmEntity>T.read(): Boolean = this.read(true)

/**
 * Gets entity identified by its primary key
 * @param recursiveLoad true if the entity needs to be fully loaded, false otherwise
 * @return true if the entity has been loaded from database, false otherwise
 */
fun <T:IOrmEntity>T.read(recursiveLoad: Boolean): Boolean{
    val sqliteDb = SGBDEngine.SQLiteDataBase(true)

    if (sqliteDb != null){
        val returnValue = this.findEntityByPrimaryKey(sqliteDb, recursiveLoad)
        sqliteDb.close()

        return returnValue
    }

    return false
}

/**
 * delete entity identified by its primary key
 * @return true if the entity has been deleted from database, else otherwise
 */
fun <T: IOrmEntity>T.delete(): Boolean{
    var returnValue: Boolean = false
    val sqliteDb = SGBDEngine.SQLiteDataBase(false)

    if (sqliteDb?.isOpen == true){
        val dbEntity = this.javaClass.kotlin.dbEntity
        if (dbEntity != null){
            val pkFilter = this.primaryKeyFilter(false)
            if (pkFilter != null){

                sqliteDb.beginTransaction()

                try{
                    returnValue = this.delete(sqliteDb)

                    if (returnValue) sqliteDb.setTransactionSuccessful()

                }catch (ex: java.lang.Exception){

                }finally {
                    sqliteDb.endTransaction()
                }
            }
        }
    }

    return returnValue
}

/**
 * persist entity into database (needs the entity fully loaded)
 * @return true if the entity has been persisted into database, false otherwise
 */
fun <T: IOrmEntity>T.save(): Boolean = this.save(true)

//endregion

//region Useful query properties

private val <T: IOrmEntity>T.hasFieldEntities: Boolean
    get() = !this.javaClass.kotlin.entityFields.isEmpty() || !this.javaClass.kotlin.entityListFields.isEmpty()

private fun <T: IOrmEntity>T.entityReport(includeWithoutCascadeDelete: Boolean): ArrayList<IOrmEntity>{
    val returnValue: ArrayList<IOrmEntity> = arrayListOf()

    if (this.hasFieldEntities){
        val entityFields = (this.javaClass.kotlin.entityFields + this.javaClass.kotlin.entityListFields)
            .filter { x -> x.tableField!!.CascadeDelete ||(includeWithoutCascadeDelete && !x.tableField!!.CascadeDelete) }

        for(field in entityFields){
            try{
                var value = field.getter.call(this)
                if (field.tableField!!.DataType == DBDataType.EntityDataType && value is IOrmEntity){
                    returnValue += value
                }else if (field.returnType.javaClass.isArray || field.returnType.isSubtypeOf(ArrayList::class.starProjectedType)){
                    returnValue.addAll(value as ArrayList<IOrmEntity>)
                }
            }catch (ex: Exception){
                returnValue.clear()
            }
        }
    }

    return returnValue
}

private fun <T: IOrmEntity>T.primaryKeyFilter(forTableRelation: Boolean): Filter?
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

private val <T: IOrmEntity>T.foreignKeyContentValues: ContentValues?
    get(){
        var returnValue: ContentValues? = null

        try{
            val dbEntity = this.javaClass.kotlin.dbEntity

            if (dbEntity != null){
                returnValue = ContentValues(0)
                val primaryKeyProperties = this.javaClass.kotlin.primaryKeyProperties

                for (field in primaryKeyProperties){
                    val fieldName = field.foreignKeyFieldName(dbEntity)
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

//endregion


//region undocumented private support crud methods

private fun <T: IOrmEntity>T.findEntityByPrimaryKey(sqliteDb: SQLiteDatabase?, recursiveLoad: Boolean): Boolean {
    try{
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
                                field.setter.call(this, when(tableField!!.DataType){
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
                                        val dbEntityField = field.dbEntityClass?.dbEntity

                                        if (dbEntityField != null){
                                            val entity = field.dbEntityClass?.createFilteringEntity(field.dbEntityClass!!, query) as? IOrmEntity
                                            if (entity?.findEntityByPrimaryKey(sqliteDb, recursiveLoad) == true)
                                                entity
                                            else
                                                null
                                        }else{
                                            null
                                        }
                                    }
                                    DBDataType.EntityListDataType ->{
                                        val fieldClass = field.dbEntityClass
                                        val fieldDbEntity = fieldClass?.dbEntity

                                        if (fieldDbEntity != null && (field.returnType.javaClass.isArray || field.returnType.isSubtypeOf(ArrayList::class.starProjectedType)) && tableField.EntityClass.isSubclassOf(IOrmEntity::class)){
                                            val objectList: ArrayList<IOrmEntity> = arrayListOf()
                                            val relationTable = "rel_${dbEntity.TableName.toLowerCase()}_${fieldDbEntity.TableName.toLowerCase()}"
                                            val filter = this.primaryKeyFilter(true)
                                            if (filter != null){
                                                val curNav = sqliteDb.query(relationTable, null, filter.FilterString, filter.FilterData.toTypedArray(), null, null, null)

                                                while(curNav?.moveToNext() == true){
                                                    val relatedFk = tableField.EntityClass.createFilteringEntity(tableField.EntityClass, curNav) as? IOrmEntity
                                                    val obj = relatedFk?.findEntityByPrimaryKey(sqliteDb,recursiveLoad)

                                                    if (obj == true) objectList.add(relatedFk)
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

                    return true
                }else{
                    this.javaClass.kotlin.primaryKeyProperties.forEach { pkf ->
                        val pkTableField = pkf.tableField!!

                        if (pkf is KMutableProperty){
                            pkf.setter.call(this, when (pkTableField.DataType){
                                DBDataType.IntegerDataType, DBDataType.LongDataType -> 0
                                DBDataType.DateDataType -> Calendar.getInstance().time
                                DBDataType.StringDataType -> AndroidSupport.EmptyString
                                DBDataType.RealDataType -> 0f
                                DBDataType.BooleanDataType -> false
                                else -> null
                            })
                        }
                    }
                }
            }
        }
    }catch (ex: Exception){
        Log.e("findEntityByPrimaryKey", ex.message)
        ex.printStackTrace()
    }

    return false
}

private fun <T: IOrmEntity>T.delete(sqliteDb: SQLiteDatabase): Boolean{
    try{
        val dbEntity = this.javaClass.kotlin.dbEntity
        if (dbEntity != null) {
            val pkFilter = this.primaryKeyFilter(false)
            if (pkFilter != null) {
                val relatedEntities = this.entityReport(false)
                val results: ArrayList<Boolean> = arrayListOf()

                // first we need to delete relation tables
                val relationTables = this.javaClass.kotlin.entityListRelationTables(false)
                val pkFilterRT = this.primaryKeyFilter(true)
                if (pkFilterRT != null){
                    relationTables.forEach { x -> results += sqliteDb.delete(x, pkFilterRT.FilterString, pkFilterRT.FilterData.toTypedArray()) > 0 }
                    if (results.firstOrNull { x -> !x } != true ) {
                        //next entity data
                        relatedEntities.forEach { x -> results += x.delete(sqliteDb) }
                    }
                }
                if (results.firstOrNull { x -> !x } != true ){
                    // and finally, this entity...
                    return sqliteDb.delete(dbEntity.TableName, pkFilter.FilterString, pkFilter.FilterData.toTypedArray()) > 0
                }
            }
        }
    }catch (ex:Exception){
        Log.e("IOrmEntity.delete", ex.message)
        ex.printStackTrace()
    }

    return false
}

private fun <T: IOrmEntity>T.save(forceEntitySave: Boolean): Boolean{
    val sqliteDb = SGBDEngine.SQLiteDataBase(false)

    if (sqliteDb?.isOpen == true){
        sqliteDb.beginTransaction()
        val returnValue = this.save(sqliteDb, forceEntitySave)

        if (returnValue) sqliteDb.setTransactionSuccessful()

        sqliteDb.endTransaction()
        sqliteDb.close()

        return returnValue
    }

    return false
}

private fun <T: IOrmEntity>T.save(sqliteDb: SQLiteDatabase, forceEntitySave: Boolean): Boolean{
    try{
        val dbEntity = this.javaClass.kotlin.dbEntity

        if (sqliteDb.isOpen && dbEntity != null){
            val pkFilter = this.primaryKeyFilter(false)
            if (pkFilter != null){
                val contentValues = ContentValues()
                val entityFields: ArrayList<IOrmEntity> = arrayListOf()
                val entityListFields: ArrayList<IOrmEntity> = arrayListOf()
                val entityListTableFields: ArrayList<TableField> = arrayListOf()

                var error = false
                val autoIncrementField: KProperty<*>? = this.javaClass.kotlin.tableFieldProperties.firstOrNull { x -> x.tableField!!.AutoIncrement }

                val fields = this.javaClass.kotlin.tableFieldProperties.filter { x -> !x.tableField!!.AutoIncrement }

                //region Data load for persistence...

                fields.forEach { field ->
                    try{
                        val fieldValue = field.getter.call(this)

                        when (field.dbDataType){
                            DBDataType.StringDataType, DBDataType.TextDataType -> contentValues.put(field.fieldName, fieldValue as? String ?: field.tableField!!.DefaultValue)
                            DBDataType.Serializable -> contentValues.put(field.fieldName, GsonBuilder().create().toJson(fieldValue))
                            DBDataType.RealDataType -> contentValues.put(field.fieldName, fieldValue as? Float ?: field.tableField!!.DefaultValue.toFloat() ?: 0f)
                            DBDataType.IntegerDataType, DBDataType.LongDataType -> contentValues.put(field.fieldName, fieldValue as? Long ?: (fieldValue as? Int)?.toLong() ?: try{ field.tableField!!.DefaultValue?.toLong()}catch (ex:Exception){ 0L } ?: 0)
                            DBDataType.BooleanDataType -> contentValues.put(field.fieldName, if (fieldValue as? Boolean ?: field.tableField!!.DefaultValue == "1") 1 else 0)
                            DBDataType.DateDataType -> contentValues.put(field.fieldName, (fieldValue as? Date)?.time ?: 0 )
                            DBDataType.EntityDataType -> {
                                entityFields += fieldValue as IOrmEntity
                                contentValues.putAll((fieldValue as? IOrmEntity)?.foreignKeyContentValues)
                            }
                            DBDataType.EntityListDataType -> {
                                try {
                                    entityListFields += fieldValue as ArrayList<IOrmEntity>
                                    entityListTableFields += field.tableField!!
                                }catch (ex: Exception){
                                    Log.e("IOrmEntity.save::EList", ex.message)
                                    ex.printStackTrace()
                                }
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

                        queryEntity?.close()
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
                                sqliteDb.insert(relatedTable, null, cValues)
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
