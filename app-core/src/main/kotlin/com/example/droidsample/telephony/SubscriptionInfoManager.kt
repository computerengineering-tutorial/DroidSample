package com.example.droidsample.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * Representation of a SIM subscription (physical SIM card or eSIM profile).
 */
data class SimSubscriptionData(
    val subscriptionId: Int,
    val simSlotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val countryIso: String,
    val mcc: String?,
    val mnc: String?,
    val isDataRoaming: Boolean,
    val isEmbedded: Boolean,
    val isDefaultVoice: Boolean,
    val isDefaultSms: Boolean,
    val isDefaultData: Boolean,
    val cardId: Int = -1,
    val phoneNumber: String? = null
) {
    val displaySlotNumber: String
        get() = if (simSlotIndex >= 0) "SIM ${simSlotIndex + 1}" else "Embedded (eSIM)"

    val plmn: String
        get() = if (!mcc.isNullOrEmpty() && !mnc.isNullOrEmpty()) "$mcc-$mnc" else "Unknown"
}

/**
 * Encapsulates the overall state of SIM subscriptions on the device.
 */
data class SubscriptionsState(
    val activeSubscriptions: List<SimSubscriptionData> = emptyList(),
    val activeSubscriptionCount: Int = 0,
    val isMultiSimSupported: Boolean = false,
    val defaultVoiceSubId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    val defaultSmsSubId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    val defaultDataSubId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    val hasPermission: Boolean = false
)

/**
 * Manages access to Android's [SubscriptionManager] and related telephony queries.
 * Provides real-time reactive updates for multi-SIM cards, carrier metadata,
 * default routing SIMs, and eSIM profiles.
 */
class SubscriptionInfoManager(private val context: Context) {

    private val subscriptionManager: SubscriptionManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    }

    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    /**
     * Checks if READ_PHONE_STATE permission is granted.
     */
    fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if READ_PHONE_NUMBERS permission is granted (used for phone number access on API 30+).
     */
    fun hasReadPhoneNumbersPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED || hasReadPhoneStatePermission()
    }

    /**
     * Checks whether the device hardware and telephony stack support Multi-SIM/Dual-SIM.
     */
    fun isMultiSimSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephonyManager?.isMultiSimSupported == TelephonyManager.MULTISIM_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            val phoneCount = telephonyManager?.phoneCount ?: 1
            phoneCount > 1
        }
    }

    /**
     * Retrieves an immediate snapshot of the current SIM subscription state.
     */
    @SuppressLint("MissingPermission")
    fun getSubscriptionsState(): SubscriptionsState {
        if (!hasReadPhoneStatePermission() || subscriptionManager == null) {
            return SubscriptionsState(hasPermission = hasReadPhoneStatePermission())
        }

        return try {
            val subList: List<SubscriptionInfo> = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            val defaultVoiceId = SubscriptionManager.getDefaultVoiceSubscriptionId()
            val defaultSmsId = SubscriptionManager.getDefaultSmsSubscriptionId()
            val defaultDataId = SubscriptionManager.getDefaultDataSubscriptionId()

            val mappedSubscriptions = subList.map { info ->
                mapSubscriptionInfo(info, defaultVoiceId, defaultSmsId, defaultDataId)
            }

            SubscriptionsState(
                activeSubscriptions = mappedSubscriptions,
                activeSubscriptionCount = mappedSubscriptions.size,
                isMultiSimSupported = isMultiSimSupported() || mappedSubscriptions.size > 1,
                defaultVoiceSubId = defaultVoiceId,
                defaultSmsSubId = defaultSmsId,
                defaultDataSubId = defaultDataId,
                hasPermission = true
            )
        } catch (se: SecurityException) {
            SubscriptionsState(hasPermission = false)
        } catch (e: Exception) {
            SubscriptionsState(hasPermission = true)
        }
    }

    /**
     * Produces a cold [Flow] that listens to subscription changes via [SubscriptionManager.OnSubscriptionsChangedListener]
     * and emits the updated [SubscriptionsState].
     */
    @SuppressLint("MissingPermission")
    fun subscriptionsFlow(): Flow<SubscriptionsState> = callbackFlow {
        // Emit initial snapshot
        trySend(getSubscriptionsState())

        val subManager = subscriptionManager
        if (subManager == null || !hasReadPhoneStatePermission()) {
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                trySend(getSubscriptionsState())
            }
        }

        try {
            subManager.addOnSubscriptionsChangedListener(context.mainExecutor, listener)
        } catch (se: SecurityException) {
            trySend(SubscriptionsState(hasPermission = false))
        } catch (e: Exception) {
            // Older API fallback or platform variance
        }

        awaitClose {
            try {
                subManager.removeOnSubscriptionsChangedListener(listener)
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun mapSubscriptionInfo(
        info: SubscriptionInfo,
        defaultVoiceId: Int,
        defaultSmsId: Int,
        defaultDataId: Int
    ): SimSubscriptionData {
        val subId = info.subscriptionId

        val mcc: String?
        val mnc: String?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mcc = info.mccString
            mnc = info.mncString
        } else {
            @Suppress("DEPRECATION")
            mcc = if (info.mcc > 0) info.mcc.toString() else null
            @Suppress("DEPRECATION")
            mnc = if (info.mnc > 0) info.mnc.toString() else null
        }

        val isEmbedded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.isEmbedded
        } else {
            false
        }

        val cardId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.cardId
        } else {
            -1
        }

        val phoneNumber: String? = resolvePhoneNumber(subId, info)

        return SimSubscriptionData(
            subscriptionId = subId,
            simSlotIndex = info.simSlotIndex,
            displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
            carrierName = info.carrierName?.toString() ?: "Unknown Carrier",
            countryIso = info.countryIso?.uppercase(Locale.ROOT) ?: "",
            mcc = mcc,
            mnc = mnc,
            isDataRoaming = info.dataRoaming == SubscriptionManager.DATA_ROAMING_ENABLE,
            isEmbedded = isEmbedded,
            isDefaultVoice = subId == defaultVoiceId,
            isDefaultSms = subId == defaultSmsId,
            isDefaultData = subId == defaultDataId,
            cardId = cardId,
            phoneNumber = phoneNumber
        )
    }

    @SuppressLint("MissingPermission")
    private fun resolvePhoneNumber(subId: Int, info: SubscriptionInfo): String? {
        if (!hasReadPhoneNumbersPermission()) return null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                subscriptionManager?.getPhoneNumber(subId)?.takeIf { it.isNotBlank() }
            } else {
                @Suppress("DEPRECATION")
                info.number?.takeIf { it.isNotBlank() }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
