package de.openbahn.navigator.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import de.openbahn.model.TicketType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(
    entities = [
        TicketEntity::class,
        TrackedJourneyEntity::class,
        RecentLocationEntity::class,
        FavoriteLocationEntity::class,
        FavoriteRouteEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class OpenBahnDatabase : RoomDatabase() {
    abstract fun ticketDao(): TicketDao
    abstract fun trackedJourneyDao(): TrackedJourneyDao
    abstract fun recentLocationDao(): RecentLocationDao
    abstract fun favoriteLocationDao(): FavoriteLocationDao
    abstract fun favoriteRouteDao(): FavoriteRouteDao
}

class Converters {
    @TypeConverter
    fun fromTicketType(value: TicketType): String = value.name

    @TypeConverter
    fun toTicketType(value: String): TicketType = TicketType.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(value)
}
