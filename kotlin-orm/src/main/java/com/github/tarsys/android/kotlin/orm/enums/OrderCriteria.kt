package com.github.tarsys.android.kotlin.orm.enums

enum class OrderCriteria {
    Asc {
        override fun toString(): String {
            return "ASC"
        }
    },
    Desc {
        override fun toString(): String {
            return "DESC"
        }
    }
}