package com.favicode.mymoney

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mymoney.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_GOALS = "goals"
        const val COLUMN_ID = "id"
        const val COLUMN_AMOUNT = "amount"
        const val COLUMN_MONTH = "month_name"
        const val COLUMN_YEAR = "year"
        const val COLUMN_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createGoalsTable = """
            CREATE TABLE $TABLE_GOALS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_AMOUNT REAL NOT NULL,
                $COLUMN_MONTH TEXT NOT NULL,
                $COLUMN_YEAR INTEGER NOT NULL,
                $COLUMN_CREATED_AT TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createGoalsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GOALS")
        onCreate(db)
    }

    // Insertar o actualizar la meta para el mes/año
    fun saveOrUpdateGoal(amount: Double, monthName: String, year: Int, createdAt: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_AMOUNT, amount)
            put(COLUMN_MONTH, monthName)
            put(COLUMN_YEAR, year)
            put(COLUMN_CREATED_AT, createdAt)
        }

        // Si ya existe meta para este mes y año, la actualizamos; si no, la insertamos
        val rowsAffected = db.update(
            TABLE_GOALS,
            values,
            "$COLUMN_MONTH = ? AND $COLUMN_YEAR = ?",
            arrayOf(monthName, year.toString())
        )

        return if (rowsAffected == 0) {
            db.insert(TABLE_GOALS, null, values)
        } else {
            rowsAffected.toLong()
        }
    }

    // Obtener la meta del mes actual
    fun getGoalForMonth(monthName: String, year: Int): Goal? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_GOALS,
            null,
            "$COLUMN_MONTH = ? AND $COLUMN_YEAR = ?",
            arrayOf(monthName, year.toString()),
            null,
            null,
            null
        )

        var goal: Goal? = null
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT))
            val month = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MONTH))
            val yr = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_YEAR))
            val date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
            goal = Goal(id, amount, month, yr, date)
        }
        cursor.close()
        return goal
    }
}