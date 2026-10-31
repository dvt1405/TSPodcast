package tss.t.shared.storage

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tss.t.shared.favourite.FavouriteItem
import tss.t.shared.favourite.FavouriteRepository
import tss.t.shared.favourite.FavouriteType
import tss.t.shared.fixtures.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M4-AC2: the exported shared schema (`shared/schemas/.../1.json`) has the same tables, columns
 * (name, affinity, NOT NULL) and primary keys as Android `PodcastDatabase` v6, minus `RadioChannel`;
 * `FavouriteDTO.type` holds the Android enum names.
 */
class DatabaseSchemaParityTest {

    private data class Column(val name: String, val affinity: String, val notNull: Boolean)
    private data class Table(val columns: Set<Column>, val primaryKey: List<String>)

    private fun tables(schema: String): Map<String, Table> {
        val database = Json.parseToJsonElement(schema).jsonObject.getValue("database").jsonObject
        return database.getValue("entities").jsonArray.associate { element ->
            val entity = element.jsonObject
            val columns = entity.getValue("fields").jsonArray.map { field ->
                val f = field.jsonObject
                Column(
                    name = f.text("columnName"),
                    affinity = f.text("affinity"),
                    notNull = f["notNull"]?.jsonPrimitive?.boolean ?: false,
                )
            }.toSet()
            val primaryKey = entity.getValue("primaryKey").jsonObject.getValue("columnNames").jsonArray
                .map { it.jsonPrimitive.content }
            entity.text("tableName") to Table(columns, primaryKey)
        }
    }

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private val android = tables(Fixtures.androidDatabaseSchemaV6)
    private val shared = tables(Fixtures.sharedDatabaseSchemaV1)

    @Test
    fun sharedSchemaIsVersionOne() {
        val version = Json.parseToJsonElement(Fixtures.sharedDatabaseSchemaV1).jsonObject
            .getValue("database").jsonObject.getValue("version").jsonPrimitive.int
        assertEquals(PodcastDatabase.VERSION, version)
        assertEquals(1, version)
    }

    @Test
    fun sharedHasTheFourAndroidTablesAndNoRadio() {
        assertEquals(setOf("Podcast", "Feed", "Episode", "FavouriteDTO"), shared.keys)
        assertFalse("RadioChannel" in shared)
        assertTrue("RadioChannel" in android, "the Android fixture is the real v6 schema")
    }

    @Test
    fun everyTableMatchesAndroidV6() {
        for (name in listOf("Podcast", "Feed", "Episode", "FavouriteDTO")) {
            val expected = android.getValue(name)
            val actual = shared.getValue(name)
            assertEquals(expected.columns, actual.columns, "columns of $name")
            assertEquals(expected.primaryKey, actual.primaryKey, "primary key of $name")
        }
    }

    @Test
    fun cachedAtIsNotNullOnTheApiTables() {
        for (name in listOf("Podcast", "Feed", "Episode")) {
            assertTrue(Column("cachedAt", "INTEGER", true) in shared.getValue(name).columns, name)
        }
        assertEquals(listOf("type", "id"), shared.getValue("FavouriteDTO").primaryKey)
    }

    @Test
    fun favouriteTypeIsStoredAsTheAndroidEnumName() = runTest {
        val test = TestDatabase()
        try {
            val repository = FavouriteRepository(test.db.favouriteDao())
            repository.setFavourite(FavouriteItem("7", FavouriteType.PodcastEpisode, "E", null, null), true)
            repository.setFavourite(FavouriteItem("42", FavouriteType.Podcast, "P", null, null), true)

            val types = test.texts("SELECT type FROM FavouriteDTO ORDER BY id").toSet()

            assertEquals(setOf("Podcast", "PodcastEpisode"), types)
        } finally {
            test.close()
        }
    }
}
