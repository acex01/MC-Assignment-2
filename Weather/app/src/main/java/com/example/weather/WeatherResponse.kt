package com.example.weather

data class WeatherResponse(
    val days: List<WeatherDay>

)


data class WeatherDay(
    val datetime: String,
    val tempmax: Double,
    val tempmin: Double,
    val description: String

)