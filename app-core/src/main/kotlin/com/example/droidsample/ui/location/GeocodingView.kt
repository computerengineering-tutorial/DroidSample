package com.example.droidsample.ui.location

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.location.AddressInfo
import com.example.droidsample.location.GeocodingHelper
import com.example.droidsample.location.NativeLocationManager
import kotlinx.coroutines.launch

@Composable
fun GeocodingView(
    geocodingHelper: GeocodingHelper,
    locationManager: NativeLocationManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLocation by locationManager.currentLocation.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Forward, 1 = Reverse
    var searchQuery by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<AddressInfo>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isAvailable = remember { geocodingHelper.isGeocoderAvailable() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Geocoder service status header
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (isAvailable) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isAvailable) "Geocoder Backend Service Online" else "Geocoder Service Unavailable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Supports forward search & Android 13+ asynchronous geocoding",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Sub Tabs: Forward vs Reverse
        item {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        results = emptyList()
                        errorMessage = null
                    },
                    text = { Text("Forward (Search)") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        results = emptyList()
                        errorMessage = null
                    },
                    text = { Text("Reverse (Coordinates)") }
                )
            }
        }

        // Input Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (selectedTab == 0) {
                        // Forward Geocoding input
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search address, place, or landmark") },
                            placeholder = { Text("e.g. Empire State Building or Golden Gate") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val result = geocodingHelper.getFromLocationName(searchQuery)
                                        isLoading = false
                                        result.fold(
                                            onSuccess = { results = it },
                                            onFailure = { errorMessage = it.localizedMessage ?: "Forward geocoding failed" }
                                        )
                                    }
                                }
                            },
                            enabled = searchQuery.isNotBlank() && !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Geocode Address")
                        }
                    } else {
                        // Reverse Geocoding input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = latInput,
                                onValueChange = { latInput = it },
                                label = { Text("Latitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lonInput,
                                onValueChange = { lonInput = it },
                                label = { Text("Longitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    currentLocation?.let {
                                        latInput = it.latitude.toString()
                                        lonInput = it.longitude.toString()
                                    } ?: Toast.makeText(context, "No location fix available", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Current Fix")
                            }

                            Button(
                                onClick = {
                                    val lat = latInput.toDoubleOrNull()
                                    val lon = lonInput.toDoubleOrNull()
                                    if (lat != null && lon != null) {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            val result = geocodingHelper.getFromLocation(lat, lon)
                                            isLoading = false
                                            result.fold(
                                                onSuccess = { results = it },
                                                onFailure = { errorMessage = it.localizedMessage ?: "Reverse geocoding failed" }
                                            )
                                        }
                                    } else {
                                        Toast.makeText(context, "Enter valid numbers", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = latInput.isNotBlank() && lonInput.isNotBlank() && !isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Reverse Lookup")
                            }
                        }
                    }
                }
            }
        }

        // Error Banner
        item {
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Results Section Header
        item {
            Text(
                text = "Results (${results.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (results.isEmpty() && !isLoading && errorMessage == null) {
            item {
                Text(
                    text = "No addresses found. Enter a search query or coordinates above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(results) { address ->
                AddressResultCard(address = address)
            }
        }
    }
}

@Composable
private fun AddressResultCard(address: AddressInfo) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = address.formattedAddress,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.6f", address.latitude)}, ${String.format("%.6f", address.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        val text = "${address.formattedAddress}\n${address.latitude}, ${address.longitude}"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Address", text))
                        Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Breakdown
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                address.thoroughfare?.let { AddressDetailRow("Street", it) }
                address.locality?.let { AddressDetailRow("City", it) }
                address.adminArea?.let { AddressDetailRow("State", it) }
                address.postalCode?.let { AddressDetailRow("Postal Code", it) }
                address.countryName?.let { AddressDetailRow("Country", it) }
            }
        }
    }
}

@Composable
private fun AddressDetailRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
