package com.tuanha.deeplink

import androidx.annotation.Keep
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow


private const val TABLE_NAME = "ipas"

@Dao
interface IpaDao {

    @Query("SELECT * FROM $TABLE_NAME WHERE languageCode COLLATE NOCASE IN (:languageCode)")
    fun getRoomListAsync(languageCode: String): Flow<List<RoomIpa>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(rooms: List<RoomIpa>)


    @Query("SELECT COUNT(*) FROM $TABLE_NAME WHERE languageCode = :languageCode")
    fun getCount(languageCode: String): Int

    @Query("SELECT COUNT(*) FROM $TABLE_NAME WHERE languageCode = :languageCode")
    fun getCountAsync(languageCode: String): Flow<Int>
}

@Keep
@Entity(
    tableName = TABLE_NAME,
    primaryKeys = ["ipa"]
)
open class RoomIpa(
    val ipa: String,

    val examples: String = "",
    val languageCode: String = "",

    val type: String = "",
    val voice: String = "",
) {

}

@Database(entities = [RoomIpa::class], version = 1, exportSchema = false)
abstract class IpaRoomDatabase : RoomDatabase() {

    abstract fun providerIpaDao(): IpaDao
}
