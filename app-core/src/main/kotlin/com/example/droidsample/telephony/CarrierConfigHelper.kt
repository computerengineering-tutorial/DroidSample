package com.example.droidsample.telephony

import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager

/**
 * Summary of carrier network flags and capabilities read from [CarrierConfigManager].
 */
data class CarrierConfigSummary(
    val subId: Int,
    val isVoLteAvailable: Boolean,
    val isVoWifiAvailable: Boolean,
    val isVtAvailable: Boolean,
    val isDataRoamingAllowed: Boolean,
    val is5gNrEnabled: Boolean,
    val maxMmsSizeBytes: Int,
    val carrierProperties: Map<String, String> = emptyMap()
)

/**
 * Helper to inspect Android's [CarrierConfigManager] configuration bundles
 * for carrier-specific telephony features (VoLTE, VoWiFi / Wi-Fi calling, 5G SA/NSA, MMS limits).
 */
@Suppress("DEPRECATION")
object CarrierConfigHelper {

    /**
     * Inspects carrier configuration bundle for the given [subId] or default subscription.
     */
    fun getCarrierConfig(context: Context, subId: Int? = null): CarrierConfigSummary {
        val ccm = context.getSystemService(Context.CARRIER_CONFIG_SERVICE) as? CarrierConfigManager
        val effectiveSubId = subId ?: SubscriptionManager.getDefaultSubscriptionId()

        if (ccm == null) {
            return CarrierConfigSummary(
                subId = effectiveSubId,
                isVoLteAvailable = false,
                isVoWifiAvailable = false,
                isVtAvailable = false,
                isDataRoamingAllowed = false,
                is5gNrEnabled = false,
                maxMmsSizeBytes = 0
            )
        }

        val bundle: PersistableBundle? = try {
            if (effectiveSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                ccm.getConfigForSubId(effectiveSubId)
            } else {
                ccm.config
            }
        } catch (_: Exception) {
            null
        }

        if (bundle == null) {
            return CarrierConfigSummary(
                subId = effectiveSubId,
                isVoLteAvailable = false,
                isVoWifiAvailable = false,
                isVtAvailable = false,
                isDataRoamingAllowed = false,
                is5gNrEnabled = false,
                maxMmsSizeBytes = 0
            )
        }

        val isVoLte = bundle.getBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, false)
        val isVoWifi = bundle.getBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, false)
        val isVt = bundle.getBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, false)
        val isRoaming = bundle.getBoolean(CarrierConfigManager.KEY_SHOW_ROAMING_INDICATOR_BOOL, false)

        val is5gEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val nrAvailabilities = bundle.getIntArray(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)
            nrAvailabilities != null && nrAvailabilities.isNotEmpty()
        } else {
            false
        }

        val maxMms = bundle.getInt(CarrierConfigManager.KEY_MMS_MAX_MESSAGE_SIZE_INT, 0)

        val properties = mutableMapOf<String, String>()
        for (key in bundle.keySet()) {
            val value = bundle.get(key)
            if (value != null) {
                properties[key] = value.toString()
            }
        }

        return CarrierConfigSummary(
            subId = effectiveSubId,
            isVoLteAvailable = isVoLte,
            isVoWifiAvailable = isVoWifi,
            isVtAvailable = isVt,
            isDataRoamingAllowed = isRoaming,
            is5gNrEnabled = is5gEnabled,
            maxMmsSizeBytes = maxMms,
            carrierProperties = properties
        )
    }
}
