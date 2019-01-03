package com.github.tarsys.android.kotlin.orm.dataobjects

import android.os.Parcel
import android.os.Parcelable
import com.github.tarsys.android.support.utilities.AndroidSupport
import java.io.Serializable
import java.lang.Exception
import java.util.*

class DataRow() : LinkedHashMap<String, Any?>(), Serializable, Parcelable {
    var DataTable: DataTable = DataTable(arrayListOf())

    constructor(parcel: Parcel) : this() {

    }

    constructor(dataTable: DataTable) : this() {
        this.DataTable = dataTable
    }

    fun asBoolean(columnName: String): Boolean = try{ this[columnName] as Boolean }catch (ex: Exception){ false }
    fun asInt(columnName: String): Int = try{ this[columnName] as Int }catch (ex: Exception){ 0 }
    fun asFloat(columnName: String): Float = try{ this[columnName] as Float }catch (ex: Exception){ 0f }
    fun isNull(columnName: String): Boolean = this[columnName] == null
    fun asDouble(columnName: String): Double = try{ this[columnName] as Double }catch (ex: Exception){ 0.0 }
    fun asDate(columnName: String): Date? = try{ this[columnName] as Date }catch (ex: Exception){ null }
    fun asString(columnName: String): String = try{ this[columnName] as String }catch (ex: Exception){ AndroidSupport.EmptyString }
    override fun writeToParcel(parcel: Parcel, flags: Int) {

    }

    override fun describeContents(): Int {
        return this.size
    }

    companion object CREATOR : Parcelable.Creator<DataRow> {
        override fun createFromParcel(parcel: Parcel): DataRow {
            return DataRow(parcel)
        }

        override fun newArray(size: Int): Array<DataRow?> {
            return arrayOfNulls(size)
        }
    }


}