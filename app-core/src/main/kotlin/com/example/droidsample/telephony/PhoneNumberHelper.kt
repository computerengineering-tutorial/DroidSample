package com.example.droidsample.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Encapsulates parsed and formatted representations of a telephone number.
 */
data class FormattedNumberResult(
    val rawInput: String,
    val e164: String?,
    val international: String,
    val national: String,
    val normalized: String,
    val isValidGlobal: Boolean,
    val countryIso: String,
    val countryCallingCode: String?
)

/**
 * Utility class wrapping Android's [PhoneNumberUtils], country code detection,
 * E.164 normalization, international/national formatting, and device phone number querying.
 */
object PhoneNumberHelper {

    /**
     * Common ISO 3166-1 alpha-2 country codes mapped to international dialing calling codes.
     */
    private val COUNTRY_CALLING_CODES = mapOf(
        "US" to "+1",
        "CA" to "+1",
        "GB" to "+44",
        "IN" to "+91",
        "DE" to "+49",
        "FR" to "+33",
        "JP" to "+81",
        "CN" to "+86",
        "AU" to "+61",
        "BR" to "+55",
        "IT" to "+39",
        "ES" to "+34",
        "MX" to "+52",
        "KR" to "+82",
        "RU" to "+7",
        "ZA" to "+27",
        "SG" to "+65",
        "AE" to "+971",
        "SA" to "+966",
        "NL" to "+31",
        "SE" to "+46",
        "NO" to "+47",
        "CH" to "+41",
        "NZ" to "+64",
        "IE" to "+353",
        "IL" to "+972",
        "TR" to "+90",
        "PL" to "+48",
        "ID" to "+62",
        "PH" to "+63",
        "VN" to "+84",
        "TH" to "+66",
        "MY" to "+60",
        "EG" to "+20",
        "NG" to "+234",
        "AR" to "+54",
        "CO" to "+57",
        "CL" to "+56"
    )

    /**
     * Resolves the international calling prefix for a given 2-letter ISO country code.
     */
    fun getCountryCallingCode(countryIso: String): String? {
        return COUNTRY_CALLING_CODES[countryIso.trim().uppercase(Locale.ROOT)]
    }

    /**
     * Detects the device's likely default country ISO code (2 letters, uppercase)
     * using active SIM, cellular network, and system locale fallback.
     */
    fun detectDeviceCountryIso(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simIso = tm?.simCountryIso?.takeIf { it.isNotBlank() }
        if (!simIso.isNullOrBlank()) {
            return simIso.uppercase(Locale.ROOT)
        }

        val networkIso = tm?.networkCountryIso?.takeIf { it.isNotBlank() }
        if (!networkIso.isNullOrBlank()) {
            return networkIso.uppercase(Locale.ROOT)
        }

        val localeIso = Locale.getDefault().country.takeIf { it.isNotBlank() }
        return localeIso?.uppercase(Locale.ROOT) ?: "US"
    }

    /**
     * Formats a phone number into strict ITU-T E.164 standard (e.g. +14155552671).
     */
    fun formatE164(rawNumber: String, defaultCountryIso: String): String? {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) return null

        val formatted = PhoneNumberUtils.formatNumberToE164(trimmed, defaultCountryIso.uppercase(Locale.ROOT))
        if (formatted != null) return formatted

        // Fallback heuristic if formatNumberToE164 returns null but number already has +
        val digits = normalizeNumber(trimmed)
        if (trimmed.startsWith("+") && digits.length >= 7) {
            return "+$digits"
        }

        val callingCode = getCountryCallingCode(defaultCountryIso)
        if (callingCode != null && digits.length >= 7) {
            val prefixDigits = callingCode.removePrefix("+")
            return if (digits.startsWith(prefixDigits)) {
                "+$digits"
            } else {
                "$callingCode$digits"
            }
        }

        return null
    }

    /**
     * Formats a telephone number for international representation with spacing and dashes.
     */
    fun formatInternational(rawNumber: String, defaultCountryIso: String): String {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) return ""

        val formatted = PhoneNumberUtils.formatNumber(trimmed, defaultCountryIso.uppercase(Locale.ROOT))
        return formatted ?: trimmed
    }

    /**
     * Formats a phone number using national format conventions.
     */
    fun formatNational(rawNumber: String, defaultCountryIso: String): String {
        val intl = formatInternational(rawNumber, defaultCountryIso)
        val callingCode = getCountryCallingCode(defaultCountryIso)
        return if (callingCode != null && intl.startsWith(callingCode)) {
            intl.removePrefix(callingCode).trimStart('-', ' ')
        } else {
            intl
        }
    }

    /**
     * Strips all non-digit formatting characters except leading '+'.
     */
    fun normalizeNumber(rawNumber: String): String {
        return PhoneNumberUtils.normalizeNumber(rawNumber)
    }

    /**
     * Strips all separators (spaces, dashes, parentheses).
     */
    fun stripSeparators(rawNumber: String): String {
        return PhoneNumberUtils.stripSeparators(rawNumber)
    }

    /**
     * Validates whether a number conforms to a global telephone number structure.
     */
    fun isGlobalPhoneNumber(rawNumber: String): Boolean {
        return PhoneNumberUtils.isGlobalPhoneNumber(rawNumber)
    }

    /**
     * Compares two telephone numbers for equality.
     * [useStrict] performs strict digit-by-digit comparison.
     */
    fun compareNumbers(number1: String, number2: String, useStrict: Boolean = false): Boolean {
        return if (useStrict) {
            normalizeNumber(number1) == normalizeNumber(number2)
        } else {
            @Suppress("DEPRECATION")
            PhoneNumberUtils.compare(number1, number2)
        }
    }

    /**
     * Computes all formatting variations for an input phone number.
     */
    fun formatComprehensive(rawNumber: String, defaultCountryIso: String): FormattedNumberResult {
        val iso = defaultCountryIso.uppercase(Locale.ROOT)
        val callingCode = getCountryCallingCode(iso)

        if (rawNumber.isEmpty()) {
            return FormattedNumberResult(
                rawInput = "",
                e164 = null,
                international = "",
                national = "",
                normalized = "",
                isValidGlobal = false,
                countryIso = iso,
                countryCallingCode = callingCode
            )
        }

        val e164 = formatE164(rawNumber, iso)
        val international = formatInternational(rawNumber, iso)
        val national = formatNational(rawNumber, iso)
        val normalized = normalizeNumber(rawNumber)
        val isValid = isGlobalPhoneNumber(rawNumber)

        return FormattedNumberResult(
            rawInput = rawNumber,
            e164 = e164,
            international = international,
            national = national,
            normalized = normalized,
            isValidGlobal = isValid,
            countryIso = iso,
            countryCallingCode = callingCode
        )
    }

    /**
     * Queries the line 1 telephone number of the device or a given subscription slot.
     * Note: Subject to carrier restriction and READ_PHONE_NUMBERS / READ_PHONE_STATE permission.
     */
    @SuppressLint("MissingPermission")
    fun getLine1Number(context: Context, subId: Int? = null): String? {
        val hasStatePerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasNumbersPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasStatePerm && !hasNumbersPerm) return null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && subId != null && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                sm?.getPhoneNumber(subId)?.takeIf { it.isNotBlank() }
            } else {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                @Suppress("DEPRECATION")
                tm?.line1Number?.takeIf { it.isNotBlank() }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
