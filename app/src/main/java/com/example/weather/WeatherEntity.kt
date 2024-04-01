package com.example.weather

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather")
data class WeatherEntity(
    @PrimaryKey @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "maxTemp") val maxTemp: Double,
    @ColumnInfo(name = "minTemp") val minTemp: Double
)