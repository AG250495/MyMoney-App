package com.favicode.mymoney

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos SQLite de presupuesto, movimientos y categorías.
 *
 * Es independiente de [DatabaseHelper] (que maneja las metas) para que
 * los cambios de versión de una no afecten a la otra.
 * Todas las tablas llevan user_id, así cada usuario ve solo sus datos.
 */
class BudgetDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mymoney_budget.db"
        private const val DATABASE_VERSION = 1

        const val T_TRANSACTIONS = "transactions"
        const val T_CATEGORIES = "categories"
        const val T_BUDGETS = "budgets"
        const val T_USER_STATE = "user_state"

        @Volatile
        private var instance: BudgetDatabase? = null

        fun getInstance(context: Context): BudgetDatabase =
            instance ?: synchronized(this) {
                instance ?: BudgetDatabase(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_TRANSACTIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tx_user_time ON $T_TRANSACTIONS(user_id, timestamp)")

        db.execSQL(
            """
            CREATE TABLE $T_CATEGORIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                icon_key TEXT NOT NULL,
                color_hex TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_cat_user_type ON $T_CATEGORIES(user_id, type)")

        // month va de 0 (enero) a 11 (diciembre), igual que Calendar
        db.execSQL(
            """
            CREATE TABLE $T_BUDGETS (
                user_id TEXT NOT NULL,
                year INTEGER NOT NULL,
                month INTEGER NOT NULL,
                amount REAL NOT NULL,
                PRIMARY KEY (user_id, year, month)
            )
            """.trimIndent()
        )

        // Marcas por usuario: si ya se migró lo viejo y si ya se crearon las categorías por defecto
        db.execSQL(
            """
            CREATE TABLE $T_USER_STATE (
                user_id TEXT PRIMARY KEY,
                migrated INTEGER NOT NULL DEFAULT 0,
                categories_seeded INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión 1: nada que migrar todavía.
        // Cuando subas DATABASE_VERSION, agrega aquí ALTER TABLE por versión
        // (NO borres tablas: son los datos reales del usuario).
    }
}
