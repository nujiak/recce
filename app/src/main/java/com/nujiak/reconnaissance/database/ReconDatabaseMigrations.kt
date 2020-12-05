package com.nujiak.reconnaissance.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE pins_table ADD COLUMN 'group' TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE chains_table (`name` TEXT NOT NULL,`data` TEXT NOT NULL, `color` INTEGER NOT NULL, `chainId` INTEGER NOT NULL, `group` TEXT NOT NULL DEFAULT '', PRIMARY KEY (`chainId`))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chains_table ADD COLUMN 'cyclical' INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE pins_table ADD COLUMN 'description' TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE chains_table ADD COLUMN 'description' TEXT NOT NULL DEFAULT ''")
    }
}