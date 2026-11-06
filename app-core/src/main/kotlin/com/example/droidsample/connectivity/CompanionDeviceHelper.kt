package com.example.droidsample.connectivity

import android.annotation.SuppressLint
import android.app.Activity
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.ParcelUuid
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.regex.Pattern

data class CompanionAssociation(
    val id: Int,
    val deviceAddress: String,
    val displayName: String,
    val isSelfManaged: Boolean = false
)

class CompanionDeviceHelper(private val context: Context) {

    private val companionManager: CompanionDeviceManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(CompanionDeviceManager::class.java)
        } else null

    private val _associations = MutableStateFlow<List<CompanionAssociation>>(emptyList())
    val associations: StateFlow<List<CompanionAssociation>> = _associations.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshAssociations()
    }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && companionManager != null

    @SuppressLint("NewApi")
    fun refreshAssociations() {
        if (!isSupported()) {
            _statusMessage.value = "CompanionDeviceManager requires Android 8.0 (API 26)+"
            return
        }
        val cdm = companionManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val list = cdm.myAssociations.map { info ->
                    CompanionAssociation(
                        id = info.id,
                        deviceAddress = info.deviceMacAddress?.toString() ?: "Unknown",
                        displayName = info.displayName?.toString() ?: "Associated Companion",
                        isSelfManaged = info.isSelfManaged
                    )
                }
                _associations.value = list
            } else {
                @Suppress("DEPRECATION")
                val oldList = cdm.associations.mapIndexed { index, mac ->
                    CompanionAssociation(
                        id = index,
                        deviceAddress = mac,
                        displayName = "Companion Device $mac",
                        isSelfManaged = false
                    )
                }
                _associations.value = oldList
            }
        } catch (e: Exception) {
            _statusMessage.value = "Error reading associations: ${e.message}"
        }
    }

    @SuppressLint("NewApi")
    fun requestClassicBluetoothAssociation(
        activityLauncher: ActivityResultLauncher<IntentSenderRequest>,
        namePattern: String? = null,
        singleDevice: Boolean = true
    ) {
        if (!isSupported()) {
            _statusMessage.value = "CompanionDeviceManager not supported on this OS version."
            return
        }

        val filterBuilder = BluetoothDeviceFilter.Builder()
        if (!namePattern.isNullOrBlank()) {
            filterBuilder.setNamePattern(Pattern.compile(namePattern, Pattern.CASE_INSENSITIVE))
        }

        val request = AssociationRequest.Builder()
            .addDeviceFilter(filterBuilder.build())
            .setSingleDevice(singleDevice)
            .build()

        executeAssociationRequest(request, activityLauncher)
    }

    @SuppressLint("NewApi")
    fun requestBleAssociation(
        activityLauncher: ActivityResultLauncher<IntentSenderRequest>,
        namePattern: String? = null,
        serviceUuid: UUID? = null,
        singleDevice: Boolean = true
    ) {
        if (!isSupported()) {
            _statusMessage.value = "CompanionDeviceManager not supported on this OS version."
            return
        }

        val filterBuilder = BluetoothLeDeviceFilter.Builder()
        if (!namePattern.isNullOrBlank()) {
            filterBuilder.setNamePattern(Pattern.compile(namePattern, Pattern.CASE_INSENSITIVE))
        }
        if (serviceUuid != null) {
            val scanFilter = android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()
            filterBuilder.setScanFilter(scanFilter)
        }

        val request = AssociationRequest.Builder()
            .addDeviceFilter(filterBuilder.build())
            .setSingleDevice(singleDevice)
            .build()

        executeAssociationRequest(request, activityLauncher)
    }

    @SuppressLint("NewApi")
    private fun executeAssociationRequest(
        request: AssociationRequest,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val cdm = companionManager ?: return
        _statusMessage.value = "Requesting device association..."

        val callback = object : CompanionDeviceManager.Callback() {
            // Android 13+ (API 33)
            override fun onAssociationPending(intentSender: IntentSender) {
                _statusMessage.value = "Association dialog pending..."
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }

            // Android 8.0 - 12L (API 26 - 32)
            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    _statusMessage.value = "Companion device found, launching selector..."
                    launcher.launch(IntentSenderRequest.Builder(chooserLauncher).build())
                }
            }

            override fun onFailure(error: CharSequence?) {
                _statusMessage.value = "Association failed: $error"
            }
        }

        try {
            cdm.associate(request, callback, null)
        } catch (e: Exception) {
            _statusMessage.value = "Association invocation failed: ${e.message}"
        }
    }

    @SuppressLint("NewApi")
    fun disassociateDevice(association: CompanionAssociation): Boolean {
        if (!isSupported()) return false
        val cdm = companionManager ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.disassociate(association.id)
            } else {
                @Suppress("DEPRECATION")
                cdm.disassociate(association.deviceAddress)
            }
            refreshAssociations()
            _statusMessage.value = "Disassociated ${association.displayName}"
            true
        } catch (e: Exception) {
            _statusMessage.value = "Disassociate failed: ${e.message}"
            false
        }
    }
}
