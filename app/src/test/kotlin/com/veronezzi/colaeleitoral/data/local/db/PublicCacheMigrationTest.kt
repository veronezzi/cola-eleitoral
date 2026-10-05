package com.veronezzi.colaeleitoral.data.local.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * 1 → 2 (S2): the detail tables and their `detail:` keys go away, the cached lists stay (an app
 * update must not cost the offline lists), and municipalities get their search key.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PublicCacheMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun version1CacheKeepsItsListsAndLosesTheDetails() = runTest {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        createVersion1(file)

        val db = Room.databaseBuilder(context, PublicCacheDatabase::class.java, file.absolutePath)
            .addMigrations(PublicCacheDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(1, db.candidateDao().observe(ELECTION, "SP", 6).first().size)
            assertEquals(listOf("candidates:$ELECTION:SP:6"), db.fetchStateDao().getAll().map { it.fetchKey })
            val municipality = db.municipalityDao().observeByUf("SP").first().single()
            assertEquals("sao paulo", municipality.searchKey)
            val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertFalse(tables.toString(), "candidate_details" in tables || "running_mates" in tables)
            assertTrue("candidates" in tables)
        } finally {
            db.close()
            file.delete()
        }
    }

    /** A database as version 1 of the app left it, from the exported schema. */
    private fun createVersion1(file: File) {
        val schema = Json.parseToJsonElement(File(SCHEMA_1).readText()).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (entity in schema.getValue("entities").jsonArray) {
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            for (query in schema.getValue("setupQueries").jsonArray) db.execSQL(query.jsonPrimitive.content)
            val columns = "ueCode, officeCode, number, ballotName, partyAcronym, registrationStatus"
            val values = "'SP', 6, 5070, 'NOME', 'P', 'Deferido'"
            db.execSQL("INSERT INTO candidates (electionId, id, $columns) VALUES ($ELECTION, 1, $values)")
            db.execSQL(
                "INSERT INTO candidate_details (electionId, candidateId, officialPageUrl, $columns) " +
                    "VALUES ($ELECTION, 1, 'https://divulgacandcontas.tse.jus.br/divulga/', $values)",
            )
            db.execSQL(
                "INSERT INTO running_mates (electionId, candidateId, position, role, ballotName) VALUES ($ELECTION, 1, 0, 'Vice', 'VICE')",
            )
            db.execSQL("INSERT INTO municipalities VALUES ('70017', 'SP', 'SÃO PAULO', 2045202024)")
            db.execSQL("INSERT INTO fetch_state VALUES ('candidates:$ELECTION:SP:6', 1, 1, NULL, 'DIVULGA_CAND_CONTAS')")
            db.execSQL("INSERT INTO fetch_state VALUES ('detail:$ELECTION:1', 1, 1, NULL, 'DIVULGA_CAND_CONTAS')")
            db.version = 1
        }
    }

    private companion object {
        const val ELECTION = 20322002026L
        const val SCHEMA_1 = "schemas/com.veronezzi.colaeleitoral.data.local.db.PublicCacheDatabase/1.json"
    }
}
