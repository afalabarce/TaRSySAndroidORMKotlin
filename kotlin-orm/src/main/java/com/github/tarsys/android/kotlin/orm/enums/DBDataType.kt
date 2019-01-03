package com.github.tarsys.android.kotlin.orm.enums

/**
 * Created by tarsys on 9/10/15.
 */
enum class DBDataType {
    None {
        override fun SqlType(fieldLength: Int): String {
            return "void"
        }
    },
    IntegerDataType {
        override fun SqlType(fieldLength: Int): String {
            return "integer"
        }
    },
    LongDataType {
        override fun SqlType(fieldLength: Int): String {
            return "integer"
        }
    },
    StringDataType {
        override fun SqlType(fieldLength: Int): String {
            return String.format("varchar(%d)", fieldLength)
        }
    },
    TextDataType {
        override fun SqlType(fieldLength: Int): String {
            return "text"
        }
    },
    RealDataType {
        override fun SqlType(fieldLength: Int): String {
            return "real"
        }
    },
    DateDataType {
        override fun SqlType(fieldLength: Int): String {
            return "integer"
        }
    },
    EntityDataType {
        override fun SqlType(fieldLength: Int): String {
            return "entity"
        }
    },
    EntityListDataType {
        override fun SqlType(fieldLength: Int): String {
            return "entitylist"
        }
    },
    EnumDataType {
        override fun SqlType(fieldLength: Int): String {
            return "integer"
        }
    },
    BooleanDataType {
        override fun SqlType(fieldLength: Int): String {
            return "integer"
        }
    },
    Serializable {
        override fun SqlType(fieldLength: Int): String {
            return "text"
        }
    };

    abstract fun SqlType(fieldLength: Int): String

    companion object {

        fun DataType(value: String): DBDataType? {
            var returnValue: DBDataType? = null
            if (value.toLowerCase().contains("integer")) returnValue = DBDataType.LongDataType
            if (value.toLowerCase().contains("varchar")) returnValue = DBDataType.StringDataType
            if (value.toLowerCase().contains("text")) returnValue = DBDataType.TextDataType
            if (value.toLowerCase().contains("real")) returnValue = DBDataType.RealDataType

            return returnValue

        }
    }
}

