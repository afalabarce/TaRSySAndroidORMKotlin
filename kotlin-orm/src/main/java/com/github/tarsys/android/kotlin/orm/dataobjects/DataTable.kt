package com.github.tarsys.android.kotlin.orm.dataobjects

class DataTable: Iterable<DataRow> {
    var Columns: ArrayList<DataColumn> = arrayListOf()
    var Rows: ArrayList<DataRow> = arrayListOf()

    constructor()

    constructor(columns: ArrayList<DataColumn>){
        this.Columns.addAll(columns)
    }

    val empty: Boolean = this.Rows.size == 0
    fun clone(): DataTable = DataTable(this.Columns)
    fun importRow(row: DataRow): DataRow{
        val returnValue = DataRow(this)
        returnValue.putAll(row)

        return returnValue
    }
    fun copy(): DataTable{
        val returnValue = DataTable()
        returnValue.Columns.addAll(this.Columns)

        this.Rows.forEach { r -> returnValue.importRow(r) }

        return returnValue
    }

    override fun iterator(): Iterator<DataRow> {
        return object : Iterator<DataRow> {
            private val dataRows = this@DataTable.Rows

            private var currentIndex = 0

            override fun hasNext(): Boolean {
                return currentIndex < dataRows.size && dataRows.get(currentIndex) != null
            }

            override fun next(): DataRow {
                return dataRows.get(currentIndex++)
            }
        }
    }

}