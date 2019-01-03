package com.github.tarsys.android.kotlin.orm.sqlite

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.github.tarsys.android.kotlin.orm.enums.DBDataType

class SQLiteSupport {
    companion object {
        const val SQLite3_EXTAG = "SQLite3Support"
        const val PREFIX_DATE_FIELD = "dt_"

        @SuppressLint("LongLogTag")
        fun dataBaseConnection(pathBD: String, readOnly: Boolean): SQLiteDatabase? {
            var returnValue: SQLiteDatabase?

            try {
                returnValue = SQLiteDatabase.openDatabase(
                    pathBD,
                    null,
                    if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE
                )
                if (!(returnValue != null && returnValue.isOpen)) {
                    Log.e(SQLiteSupport.SQLite3_EXTAG, "The Database " + pathBD + "is not openned.")
                    returnValue = null
                }
            } catch (ex: Exception) {
                Log.e(SQLiteSupport.SQLite3_EXTAG + "::dataBaseConnection", ex.message)
                returnValue = null
            }

            return returnValue
        }

        @SuppressLint("LongLogTag")
        fun existsTableField(database: SQLiteDatabase, tableName: String, fieldName: String): Boolean {
            var returnValue = false

            try {
                val ti = database.rawQuery("PRAGMA table_info($tableName)", null)
                if (ti.moveToFirst()) {
                    do {
                        val nBdField = ti.getString(1)
                        if (nBdField.equals(fieldName, ignoreCase = true)) {
                            returnValue = true
                            break
                        }
                    } while (ti.moveToNext())
                    ti.close()
                }
            } catch (ex: Exception) {
                Log.e(SQLiteSupport.SQLite3_EXTAG + "::existsTableField", ex.message)
            }

            return returnValue
        }

        @SuppressLint("LongLogTag")
        fun fieldDataTypeAsString(database: SQLiteDatabase, tableName: String, fieldName: String): String {
            var returnValue = ""

            try {
                val ti = database.rawQuery("PRAGMA table_info($tableName)", null)
                if (ti.moveToFirst()) {
                    do {
                        val nDbField = ti.getString(1)
                        if (nDbField.equals(fieldName, ignoreCase = true)) {
                            returnValue = ti.getString(2)
                            break
                        }
                    } while (ti.moveToNext())
                    ti.close()
                }
            } catch (ex: Exception) {
                Log.e(SQLiteSupport.SQLite3_EXTAG + "::fieldDataTypeAsString", ex.message)
            }

            return returnValue
        }

        @SuppressLint("LongLogTag")
        fun fieldDataType(database: SQLiteDatabase, tableName: String, fieldName: String): DBDataType {
            var returnValue = DBDataType.StringDataType

            try {
                val ti = database.rawQuery("PRAGMA table_info($tableName)", null)
                if (ti.moveToFirst()) {
                    do {
                        val nDbField = ti.getString(1)
                        if (nDbField.equals(fieldName, ignoreCase = true)) {
                            returnValue = DBDataType.DataType(nDbField)!!
                            if (returnValue == DBDataType.IntegerDataType && fieldName.startsWith(SQLiteSupport.PREFIX_DATE_FIELD))
                                returnValue = DBDataType.DateDataType
                            break
                        }
                    } while (ti.moveToNext())
                    ti.close()
                }
            } catch (ex: Exception) {
                Log.e(SQLiteSupport.SQLite3_EXTAG + "::fieldDataType", ex.message)
            }

            return returnValue
        }

        @SuppressLint("LongLogTag")
        fun fieldDataTypeLength(database: SQLiteDatabase, tableName: String, fieldName: String): Long {
            var returnValue = 0L

            try {
                if (SQLiteSupport.fieldDataType(database, tableName, fieldName) == DBDataType.StringDataType) {

                    val lCampo = SQLiteSupport.fieldDataTypeAsString(database, tableName, fieldName).replace("varchar", "")
                        .replace("(", "").replace(")", "").trim { it <= ' ' }
                    returnValue = java.lang.Long.parseLong(lCampo)
                }
            } catch (ex: Exception) {
                returnValue = 0L
                Log.e(SQLiteSupport.SQLite3_EXTAG + "::fieldDataTypeLength", ex.message)
            }

            return returnValue
        }

        @SuppressLint("LongLogTag")
        fun tableExistsInDataBase(database: SQLiteDatabase, tableName: String): Boolean {
            var returnvalue = false

            try {
                val ti = database.rawQuery("PRAGMA table_info($tableName)", null)
                returnvalue = ti.moveToFirst()
                if (returnvalue) ti.close()
            } catch (ex: Exception) {
                Log.e(SQLiteSupport.SQLite3_EXTAG + "::tableExistsInDataBase", ex.message)
            }

            return returnvalue
        }
    }
}