package com.example.weather

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.weather.WeatherEntity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.Response

import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class WeatherViewModel(private val appContext: Context, private val database: AppDatabase) : ViewModel() {
    val weatherData = MutableStateFlow<WeatherDay?>(null)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val successMessage = MutableStateFlow<String?>(null)

    private val visualCrossingService: VisualCrossingService by lazy {
        Retrofit.Builder()
            .baseUrl("https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VisualCrossingService::class.java)
    }

    private fun isValidDate(dateStr: String): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateFormat.isLenient = false
        return try {
            dateFormat.parse(dateStr)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            return networkInfo.isConnected
        }
    }


    fun loadWeatherData(location: String, date: String) {
        isLoading.value = true
        errorMessage.value = null
        if (!isNetworkAvailable(appContext)) {
            errorMessage.value = "No Internet Connection"
            isLoading.value = false
            return
        }

        if (!isValidDate(date)) {
            errorMessage.value = "Invalid Date Format"
            isLoading.value = false
            return
        }

        val enteredDate = LocalDate.parse(date)
        val currentDate = LocalDate.now()

        if (!enteredDate.isAfter(currentDate)) {
            fetchWeatherData(location, date)
        } else {
            // Adjust the start date to the most recent occurrence of the same month and day
            val adjustedStartDate = if (enteredDate.year - currentDate.year > 1) {
                LocalDate.of(currentDate.year - 1, enteredDate.monthValue, enteredDate.dayOfMonth)
            } else {
                LocalDate.of(enteredDate.year - 1, enteredDate.monthValue, enteredDate.dayOfMonth)
            }
            fetchWeatherForFutureDate(location, adjustedStartDate, enteredDate)
        }
    }

    private fun fetchWeatherData(location: String, date: String) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            if (!isValidDate(date)) {
                errorMessage.value = "Invalid Date Format"
                isLoading.value = false
                return@launch
            }

            val enteredDate = LocalDate.parse(date)
            val currentDate = LocalDate.now()

            if (enteredDate.isAfter(currentDate)) {
                // Adjust the start date to the most recent occurrence of the same month and day
                val adjustedStartDate = if (enteredDate.year - currentDate.year > 1) {
                    LocalDate.of(currentDate.year - 1, enteredDate.monthValue, enteredDate.dayOfMonth)
                } else {
                    LocalDate.of(enteredDate.year - 1, enteredDate.monthValue, enteredDate.dayOfMonth)
                }
                fetchWeatherForFutureDate(location, adjustedStartDate, enteredDate)
            } else {
                // Handle past dates
                try {
                    val response: Response<WeatherResponse> = visualCrossingService.getHistoricalWeather(
                        location, date, "WTU4ET8PLN34TWR4CXAHWGV4Z"
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val weatherDay = response.body()?.days?.firstOrNull()
                        weatherData.value = weatherDay

                        weatherDay?.let {
                            val weatherEntity = WeatherEntity(
                                date = it.datetime,
                                maxTemp = it.tempmax,
                                minTemp = it.tempmin
                            )
                            withContext(Dispatchers.IO) {
                                database.weatherDao().insert(weatherEntity)
                            }
                        }
                    } else {
                        errorMessage.value = "Error: ${response.message()}"
                        weatherData.value = null
                    }
                } catch (e: HttpException) {
                    errorMessage.value = "HTTP Error: ${e.code()}"
                    weatherData.value = null
                } catch (e: Exception) {
                    errorMessage.value = "Exception: ${e.localizedMessage}"
                    weatherData.value = null
                } finally {
                    isLoading.value = false
                }
            }
        }
    }

    private fun fetchWeatherForFutureDate(location: String, startDate: LocalDate, futureDate: LocalDate) {
        viewModelScope.launch {
            var totalMaxTemp = 0.0
            var totalMinTemp = 0.0
            var count = 0

            for (years in 0 until 10) {
                val pastDate = startDate.minusYears(years.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)

                try {
                    val response: Response<WeatherResponse> = visualCrossingService.getHistoricalWeather(
                        location, pastDate, "WTU4ET8PLN34TWR4CXAHWGV4Z"
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val weatherDay = response.body()?.days?.firstOrNull()
                        weatherDay?.let {
                            totalMaxTemp += it.tempmax
                            totalMinTemp += it.tempmin
                            count++
                        }
                    }
                } catch (e: Exception) {

                }
            }

            if (count > 0) {
                val avgMaxTemp = totalMaxTemp / count
                val avgMinTemp = totalMinTemp / count

                withContext(Dispatchers.Main) {
                    val averageWeather = WeatherEntity(
                        date = futureDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        maxTemp = avgMaxTemp,
                        minTemp = avgMinTemp
                    )
                    database.weatherDao().insert(averageWeather)
                    weatherData.value = convertEntityToDay(averageWeather)
                }
            } else {
                withContext(Dispatchers.Main) {
                    errorMessage.value = "No data available for future prediction"
                    weatherData.value = null
                }
            }
            isLoading.value = false
        }
    }




    fun getWeatherDataRange(location: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = visualCrossingService.getWeatherDataRange(location, startDate, endDate, "WTU4ET8PLN34TWR4CXAHWGV4Z")
                if (response.isSuccessful && response.body() != null) {
                    val days = response.body()!!.days
                    val weatherList = days.map { day ->
                        WeatherEntity(
                            date = day.datetime,
                            maxTemp = day.tempmax,
                            minTemp = day.tempmin
                        )
                    }
                    database.weatherDao().insertAll(weatherList)
                } else {
                    errorMessage.value = "Error fetching data: ${response.message()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }

        }

    fun convertEntityToDay(entity: WeatherEntity): WeatherDay {
        return WeatherDay(
            datetime = entity.date,
            tempmax = entity.maxTemp,
            tempmin = entity.minTemp,
            description = "Description not available"
        )
    }

    fun queryWeatherDataFromDb(date: String) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                withContext(Dispatchers.IO) {
                    val weatherEntity = database.weatherDao().getWeatherDataRange(date)
                    if (weatherEntity != null) {
                        // Convert WeatherEntity to WeatherDay
                        val weatherDay = convertEntityToDay(weatherEntity)
                        withContext(Dispatchers.Main) {
                            weatherData.value = weatherDay
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            errorMessage.value = "No data available for this date"
                            weatherData.value = null
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage.value = "Error: ${e.localizedMessage}"
                    weatherData.value = null
                }
            } finally {
                isLoading.value = false
            }
        }
    }
}






//    fun preloadPastWeatherData(location: String) {
//        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
//        val pastYears = (currentYear - 9..currentYear)
//
//        pastYears.forEach { year ->
//            val date = "$year-03-25" // Assuming you want to fetch data for March 25 of each year
//            fetchWeatherFromAPIAndStore(location, date)
//        }
//    }




