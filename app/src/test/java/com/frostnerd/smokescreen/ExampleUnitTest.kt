package com.frostnerd.smokescreen

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.frostnerd.smokescreen.database.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
    private val TEST_DB = "direct-migration-test"
    private val migrations = mutableMapOf<Migration,(SupportSQLiteDatabase) -> Unit>().apply {
        put(MIGRATION_3_4) {

        }
        put(MIGRATION_4_5){

        }
        put(MIGRATION_5_6){

        }
        put(MIGRATION_6_7){

        }
        put(MIGRATION_7_8){

        }
        put(MIGRATION_8_9){

        }
    }.toMap()

    @Rule
    @JvmField
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /*@Test
    @Throws(IOException::class)
    fun testIncrementalMigrations_withoutData() {
        var db = helper.createDatabase(TEST_DB, 3)
        db.close()
        for(migration in migrations) {
            db = helper.runMigrationsAndValidate(TEST_DB, migration.key.endVersion, true, migration.key)
            db.close()
        }
    }*/
}
