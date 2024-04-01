package com.example.weather

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    var date by remember { mutableStateOf(TextFieldValue("")) }
    var location by remember { mutableStateOf(TextFieldValue("")) }
    val weatherData = viewModel.weatherData.collectAsState().value
    val isLoading = viewModel.isLoading.collectAsState().value
    val errorMessage = viewModel.errorMessage.collectAsState().value
    val successMessage = viewModel.successMessage.collectAsState().value

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Weather Forecast",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = date.text,
                onValueChange = { date = TextFieldValue(it) },
                label = { Text("Enter Date (YYYY-MM-DD)") },
                isError = errorMessage?.startsWith("Invalid Date") == true,
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.calendar), contentDescription = "Date") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = location.text,
                onValueChange = { location = TextFieldValue(it) },
                label = { Text("Enter Location") },
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.location), contentDescription = "Location") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.loadWeatherData(location.text, date.text) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get Weather")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (date.text.isNotEmpty()) {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        val endDate = LocalDate.parse(date.text, formatter)
                        val startDate = endDate.minusYears(1).format(formatter)
                        viewModel.getWeatherDataRange(location.text, startDate, endDate.format(formatter))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load Weather Data Offline (1 Year)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.queryWeatherDataFromDb(date.text) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get Weather (Offline)")
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                CircularProgressIndicator()
            }

            if (weatherData != null) {
                Spacer(modifier = Modifier.height(16.dp))
                val tempMaxCelsius = ((weatherData.tempmax - 32) * 5 / 9).toFloat()
                val tempMinCelsius = ((weatherData.tempmin - 32) * 5 / 9).toFloat()
                Text("Max Temp: $tempMaxCelsius°C")
                Text("Min Temp: $tempMinCelsius°C")
            }

            if (errorMessage != null && errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
            }

            if (successMessage != null && successMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Success: $successMessage", color = Color.Green)
            }

        }
    }
}
