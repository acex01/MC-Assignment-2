package com.example.weather

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path


interface VisualCrossingService {

    @GET("timeline/{location}/{startDate}")
    suspend fun getHistoricalWeather(
        @Path("location") location: String,
        @Path("startDate") startDate: String,
        @Query("key") apiKey: String,
        @Query("include") include : String = "days",
        @Query("elements") elements : String = "datetime,tempmax,tempmin"
    ): Response<WeatherResponse>

    @GET("timeline/{location}/{startDate}/{endDate}")
    suspend fun getWeatherDataRange(
        @Path("location") location: String,
        @Path("startDate") startDate: String,
        @Path("endDate") endDate: String,
        @Query("key") apiKey: String,
        @Query("include") include: String = "days",
        @Query("elements") elements: String = "datetime,tempmax,tempmin"
    ): Response<WeatherResponse>
}