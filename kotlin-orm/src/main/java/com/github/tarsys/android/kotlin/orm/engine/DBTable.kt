package com.github.tarsys.android.kotlin.orm.engine

import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import com.github.tarsys.android.kotlin.orm.annotations.DBEntity
import com.github.tarsys.android.kotlin.orm.annotations.Index
import com.github.tarsys.android.kotlin.orm.annotations.TableField
import com.github.tarsys.android.kotlin.orm.enums.DBDataType
import com.github.tarsys.android.kotlin.orm.sqlite.SQLiteSupport
import com.github.tarsys.android.support.utilities.AndroidSupport
import kotlin.reflect.KClass

class DBTable {
    var relatedClass: KClass<*>? = null
    var Table: DBEntity? = null
    val Fields: ArrayList<TableField> = arrayListOf()
    val Indexes: ArrayList<Index> = arrayListOf()

    private fun sqlCreateForeignKey(foreignKeyTable: DBTable): String{
        var returnValue: String = AndroidSupport.EmptyString

        if (this.Table != null){
            val foreignKeyTableName = "rel_${this.Table!!.TableName.toLowerCase()}_${foreignKeyTable.Table!!.TableName.toLowerCase()}"
            var foreignKeyFieldsDefinition: String = AndroidSupport.EmptyString
            val primaryKeyForeignKeyFields: ArrayList<String> = arrayListOf()

            this.Fields.filter { f -> f.PrimaryKey }
                .forEach { fPk ->
                    primaryKeyForeignKeyFields.add("${this.Table!!.TableName.toLowerCase()}_${fPk.FieldName}")
                    foreignKeyFieldsDefinition += if (foreignKeyFieldsDefinition.isEmpty()) AndroidSupport.EmptyString else ", "
                    foreignKeyFieldsDefinition += "${this.Table!!.TableName.toLowerCase()}_${if(fPk.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else AndroidSupport.EmptyString}${fPk.FieldName} ${fPk.DataType.SqlType(fPk.DataTypeLength)}"

                }

            foreignKeyTable.Fields.filter { f -> f.PrimaryKey }
                .forEach { fPk ->
                    primaryKeyForeignKeyFields.add("${foreignKeyTable.Table!!.TableName.toLowerCase()}_${fPk.FieldName}")
                    foreignKeyFieldsDefinition += if (foreignKeyFieldsDefinition.isEmpty()) AndroidSupport.EmptyString else ", "
                    foreignKeyFieldsDefinition += "${foreignKeyTable.Table!!.TableName.toLowerCase()}_${if(fPk.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else AndroidSupport.EmptyString}${fPk.FieldName} ${fPk.DataType.SqlType(fPk.DataTypeLength)}"

                }

            val pKeyFK = ", PRIMARY KEY(${TextUtils.join(", ", primaryKeyForeignKeyFields)})"
            returnValue = "create table if not exists $foreignKeyTableName($foreignKeyFieldsDefinition$pKeyFK)"
        }

        return returnValue
    }

    fun sqlCreateTable(): ArrayList<String> {
        var returnValue: ArrayList<String> = arrayListOf()

        if (this.Table != null){
            var createTable = "create table if not exists ${this.Table!!.TableName.toLowerCase()}"
            var paramDefinition: String = AndroidSupport.EmptyString
            val primaryKeyFields: ArrayList<String> = arrayListOf()

            for(field in this.Fields){
                if (field.PrimaryKey){
                    if (field.DataType !in arrayOf( DBDataType.EntityDataType, DBDataType.EntityListDataType)){
                        primaryKeyFields += field.FieldName
                    }else if (field.DataType == DBDataType.EntityDataType && field.EntityClass?.dbEntity != null){
                        val entityFieldPKeys = field.EntityClass.primaryKeyFieldNames
                        primaryKeyFields += entityFieldPKeys.map { x -> "${field.EntityClass.tableName}_${x}" }
                    }
                }


                if (field.DataType !in arrayOf( DBDataType.EntityDataType, DBDataType.EntityListDataType)){
                    // Primitive fields
                    paramDefinition += if (paramDefinition.trim().isEmpty()) AndroidSupport.EmptyString else ", "
                    paramDefinition += "${if(field.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else AndroidSupport.EmptyString}${field.FieldName} " +
                                       "${field.DataType.SqlType(field.DataTypeLength)} " +
                                       "${if (field.NotNull) "not null" else AndroidSupport.EmptyString } " +
                                       "${if (field.NotNull && !field.DefaultValue.isNullOrEmpty()) "default ${field.DefaultValue}" else AndroidSupport.EmptyString}"
                }else{
                    if (field.EntityClass != null){
                        if (field.DataType == DBDataType.EntityDataType){
                            for(fkField in field.EntityClass.primaryKeyProperties){
                                paramDefinition += if (!paramDefinition.isEmpty()) ", " else AndroidSupport.EmptyString
                                paramDefinition += "${field.EntityClass.tableName}_${"${if (fkField.tableField!!.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else ""}${fkField.tableField!!.FieldName}" } ${fkField.tableField!!.DataType.SqlType(fkField.tableField!!.DataTypeLength)}"
                            }
                        }else{
                            returnValue.add(this.sqlCreateForeignKey(field.EntityClass.dbTable!!))
                        }
                    }
                }
            }

            val pKey = if (primaryKeyFields.size > 0) ", PRIMARY KEY(" + TextUtils.join(", ", primaryKeyFields) + ")" else ""
            createTable += " ($paramDefinition$pKey)"
            returnValue.add(createTable)
        }

        return returnValue
    }

    fun sqlCreationQuerys(sqliteDb: SQLiteDatabase): ArrayList<String>{
        val returnValue: ArrayList<String> = arrayListOf()
        if (this.Table != null){
            if (!SQLiteSupport.tableExistsInDataBase(sqliteDb, this.Table!!.TableName)){
                returnValue += this.sqlCreateTable()
            }else{
                for (field in this.Fields){
                    if (field.DataType !in arrayOf(DBDataType.EntityDataType, DBDataType.EntityListDataType)){
                        // add new primitive fields to table...
                        val fieldName = "${if (field.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else AndroidSupport.EmptyString }${field.FieldName}"

                        if (!SQLiteSupport.existsTableField(sqliteDb, this.Table!!.TableName, fieldName)){
                            returnValue += ("alter table ${this.Table!!.TableName.toLowerCase()} add column $fieldName " +
                                            "${field.DataType.SqlType(field.DataTypeLength)} ${if (field.NotNull) " not null" else AndroidSupport.EmptyString } " +
                                            "${if (field.NotNull && !field.DefaultValue.isNullOrEmpty()) "default ${field.DefaultValue}" else AndroidSupport.EmptyString}")
                        }
                    }else{
                        if (field.DataType == DBDataType.EntityDataType){
                            for(pField in field.EntityClass.primaryKeyProperties){
                                val fieldName = "${field.EntityClass.tableName}_${if (pField.dbDataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else AndroidSupport.EmptyString}${pField.tableField!!.FieldName}"
                                if (!SQLiteSupport.existsTableField(sqliteDb, this.Table!!.TableName, fieldName)){
                                    returnValue += ("alter table ${this.Table!!.TableName.toLowerCase()} add column $fieldName ${pField.tableField!!.DataType.SqlType(pField.tableField!!.DataTypeLength)}")
                                }
                            }

                        }else{
                            val foreignTable = "rel_${this.Table!!.TableName.toLowerCase()}_${field.EntityClass.tableName.toLowerCase()}"
                            if (!SQLiteSupport.tableExistsInDataBase(sqliteDb, foreignTable)){
                                returnValue += this.sqlCreateForeignKey(field.EntityClass.dbTable!!)
                            }
                        }
                    }
                }
            }

            // Finally add indexes...
            for (index in this.Indexes){
                returnValue += "drop index if exists ${index.IndexName}"
                returnValue += "create ${if (index.IsUniqueIndex) "unique" else AndroidSupport.EmptyString} index if not exists ${index.IndexName} on ${this.Table!!.TableName.toLowerCase()} (${index.IndexFields.joinToString(", ")})"
            }
        }

        return returnValue
    }
}