package com.example.droidsample.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager

/**
 * Access severity level for telephony identifiers.
 */
enum class RestrictionSeverity(val label: String) {
    SYSTEM_RESTRICTED("Restricted (System / Device Owner Only)"),
    CARRIER_PRIVILEGE_REQUIRED("Carrier Privileges Required"),
    ACCESSIBLE("Accessible"),
    UNSUPPORTED("Hardware / API Unsupported")
}

/**
 * Audit result of attempting to access a specific hardware or SIM identifier.
 */
data class IdentifierAuditResult(
    val identifierName: String,
    val accessible: Boolean,
    val valueOrMasked: String?,
    val restrictionReason: String,
    val requiredPermission: String,
    val apiLevelIntroducedRestriction: Int,
    val severity: RestrictionSeverity
) {
    val statusChipText: String
        get() = if (accessible) "Accessible" else "Protected (${apiLevelIntroducedRestriction}+)"
}

/**
 * Comprehensive telephony privacy and identifier security report.
 */
data class TelephonySecurityReport(
    val results: List<IdentifierAuditResult>,
    val hasCarrierPrivileges: Boolean,
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val isEuiccSupported: Boolean,
    val overallSummary: String
)

/**
 * Probes and audits restricted telephony identifiers (IMEI, MEID, IMSI, ICCID, eSIM EID).
 * Safely documents platform privacy barriers introduced in Android 10 (API 29)
 * and verifies carrier privileges / system permission boundaries.
 */
class RestrictedTelephonyAuditor(private val context: Context) {

    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    private val euiccManager: EuiccManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(Context.EUICC_SERVICE) as? EuiccManager
        } else {
            null
        }
    }

    /**
     * Conducts a safe, non-crashing probe of all restricted identifiers and returns the report.
     */
    fun auditRestrictedIdentifiers(): TelephonySecurityReport {
        val tm = telephonyManager
        val carrierPrivileges = tm?.hasCarrierPrivileges() ?: false
        val isEuiccEnabled = euiccManager?.isEnabled ?: false

        val results = mutableListOf<IdentifierAuditResult>()

        // 1. IMEI (International Mobile Equipment Identity)
        results.add(auditImei(tm))

        // 2. MEID (Mobile Equipment Identifier)
        results.add(auditMeid(tm))

        // 3. IMSI (International Mobile Subscriber Identity)
        results.add(auditImsi(tm))

        // 4. ICCID (Integrated Circuit Card ID / SIM Serial Number)
        results.add(auditIccid(tm))

        // 5. eSIM EID (Embedded Identity Document)
        results.add(auditEsimEid())

        val accessibleCount = results.count { it.accessible }
        val summary = if (accessibleCount == 0) {
            "All non-resettable hardware identifiers are securely restricted by Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) privacy policies."
        } else {
            "$accessibleCount of ${results.size} identifiers are accessible under current permissions."
        }

        return TelephonySecurityReport(
            results = results,
            hasCarrierPrivileges = carrierPrivileges,
            apiLevel = Build.VERSION.SDK_INT,
            isEuiccSupported = isEuiccEnabled,
            overallSummary = summary
        )
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun auditImei(tm: TelephonyManager?): IdentifierAuditResult {
        if (tm == null) {
            return IdentifierAuditResult(
                identifierName = "IMEI (Device Identifier)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "Telephony service unavailable.",
                requiredPermission = "READ_PRIVILEGED_PHONE_STATE",
                apiLevelIntroducedRestriction = 29,
                severity = RestrictionSeverity.UNSUPPORTED
            )
        }

        return runCatching {
            val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.imei
            } else {
                @Suppress("DEPRECATION")
                tm.deviceId
            }
            if (imei != null) {
                IdentifierAuditResult(
                    identifierName = "IMEI (Device Identifier)",
                    accessible = true,
                    valueOrMasked = maskIdentifier(imei),
                    restrictionReason = "Accessible (Device owner, carrier app, or pre-Android 10)",
                    requiredPermission = "READ_PHONE_STATE",
                    apiLevelIntroducedRestriction = 29,
                    severity = RestrictionSeverity.ACCESSIBLE
                )
            } else {
                IdentifierAuditResult(
                    identifierName = "IMEI (Device Identifier)",
                    accessible = false,
                    valueOrMasked = null,
                    restrictionReason = "IMEI returned null from radio daemon.",
                    requiredPermission = "READ_PRIVILEGED_PHONE_STATE",
                    apiLevelIntroducedRestriction = 29,
                    severity = RestrictionSeverity.SYSTEM_RESTRICTED
                )
            }
        }.getOrElse { error ->
            IdentifierAuditResult(
                identifierName = "IMEI (Device Identifier)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "Blocked by Android 10+ privacy policy: Non-resettable hardware IDs require READ_PRIVILEGED_PHONE_STATE (${error.javaClass.simpleName}).",
                requiredPermission = "READ_PRIVILEGED_PHONE_STATE / Carrier Privileges",
                apiLevelIntroducedRestriction = 29,
                severity = RestrictionSeverity.SYSTEM_RESTRICTED
            )
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun auditMeid(tm: TelephonyManager?): IdentifierAuditResult {
        if (tm == null) {
            return IdentifierAuditResult(
                identifierName = "MEID (CDMA Equipment ID)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "Telephony service unavailable.",
                requiredPermission = "READ_PRIVILEGED_PHONE_STATE",
                apiLevelIntroducedRestriction = 29,
                severity = RestrictionSeverity.UNSUPPORTED
            )
        }

        return runCatching {
            val meid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                tm.meid
            } else {
                null
            }
            if (meid != null) {
                IdentifierAuditResult(
                    identifierName = "MEID (CDMA Equipment ID)",
                    accessible = true,
                    valueOrMasked = maskIdentifier(meid),
                    restrictionReason = "Accessible.",
                    requiredPermission = "READ_PRIVILEGED_PHONE_STATE",
                    apiLevelIntroducedRestriction = 29,
                    severity = RestrictionSeverity.ACCESSIBLE
                )
            } else {
                IdentifierAuditResult(
                    identifierName = "MEID (CDMA Equipment ID)",
                    accessible = false,
                    valueOrMasked = null,
                    restrictionReason = "MEID not supported by LTE/NR GSM modem or returned null.",
                    requiredPermission = "READ_PRIVILEGED_PHONE_STATE",
                    apiLevelIntroducedRestriction = 29,
                    severity = RestrictionSeverity.UNSUPPORTED
                )
            }
        }.getOrElse { error ->
            IdentifierAuditResult(
                identifierName = "MEID (CDMA Equipment ID)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "Blocked by Android 10+ privacy policy: ${error.message}",
                requiredPermission = "READ_PRIVILEGED_PHONE_STATE / Carrier Privileges",
                apiLevelIntroducedRestriction = 29,
                severity = RestrictionSeverity.SYSTEM_RESTRICTED
            )
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun auditImsi(tm: TelephonyManager?): IdentifierAuditResult {
        if (tm == null) {
            return IdentifierAuditResult(
                identifierName = "IMSI (Subscriber ID)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "Telephony service unavailable.",
                requiredPermission = "READ_PRIVILEGED_PHONE_STATE",
                apiLevelIntroducedRestriction = 29,
                severity = RestrictionSeverity.UNSUPPORTED
            )
        }

        return runCatching {
            val imsi = tm.subscriberId
            if (imsi != null) {
                IdentifierAuditResult(
                    identifierName = "IMSI (Subscriber ID)",
                    accessible = true,
                    valueOrMasked = maskIdentifier(imsi),
                    restrictionReason = "Accessible under current privileges.",
                    requiredPermission = "READ_PHONE_STATE",
                    apiLevelIntroducedRestriction = 29,
                    severity = RestrictionSeverity.ACCESSIBLE
                )
            } else {
                IdentifierAuditResult(
                    identifierName = "IMSI (Subscriber ID)",
                    accessible = false,
                    valueOrMasked = null,
                    restrictionReason = "Subscriber ID returned null.",
                    requiredPermission = "READ_PRIVILEGED_PHONE_STATE",
                    apiLevelIntroducedRestriction = 29,
                    severity = RestrictionSeverity.SYSTEM_RESTRICTED
                )
            }
        }.getOrElse { error ->
            IdentifierAuditResult(
                identifierName = "IMSI (Subscriber ID)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "Restricted in Android 10+: subscriber ID requires carrier privileges or system caller (${error.javaClass.simpleName}).",
                requiredPermission = "Carrier Privileges / READ_PRIVILEGED_PHONE_STATE",
                apiLevelIntroducedRestriction = 29,
                severity = RestrictionSeverity.CARRIER_PRIVILEGE_REQUIRED
            )
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun auditIccid(tm: TelephonyManager?): IdentifierAuditResult {
        return runCatching {
            val serial = tm?.simSerialNumber
            if (serial != null) {
                IdentifierAuditResult(
                    identifierName = "ICCID (SIM Card Serial Number)",
                    accessible = true,
                    valueOrMasked = maskIdentifier(serial),
                    restrictionReason = "Accessible.",
                    requiredPermission = "READ_PHONE_STATE",
                    apiLevelIntroducedRestriction = 29,
                    severity = RestrictionSeverity.ACCESSIBLE
                )
            } else {
                // Check SubscriptionInfo iccId
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subList: List<SubscriptionInfo>? = sm?.activeSubscriptionInfoList
                val iccId = subList?.firstOrNull()?.iccId
                if (iccId != null) {
                    IdentifierAuditResult(
                        identifierName = "ICCID (SIM Card Serial Number)",
                        accessible = true,
                        valueOrMasked = maskIdentifier(iccId),
                        restrictionReason = "Accessible via SubscriptionInfo.",
                        requiredPermission = "READ_PHONE_STATE",
                        apiLevelIntroducedRestriction = 29,
                        severity = RestrictionSeverity.ACCESSIBLE
                    )
                } else {
                    IdentifierAuditResult(
                        identifierName = "ICCID (SIM Card Serial Number)",
                        accessible = false,
                        valueOrMasked = null,
                        restrictionReason = "ICCID returned null or not available.",
                        requiredPermission = "READ_PRIVILEGED_PHONE_STATE",
                        apiLevelIntroducedRestriction = 29,
                        severity = RestrictionSeverity.SYSTEM_RESTRICTED
                    )
                }
            }
        }.getOrElse { error ->
            IdentifierAuditResult(
                identifierName = "ICCID (SIM Card Serial Number)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "Restricted on Android 10+: SIM serial number access restricted to carrier apps or system profile (${error.javaClass.simpleName}).",
                requiredPermission = "Carrier Privileges / READ_PRIVILEGED_PHONE_STATE",
                apiLevelIntroducedRestriction = 29,
                severity = RestrictionSeverity.SYSTEM_RESTRICTED
            )
        }
    }

    private fun auditEsimEid(): IdentifierAuditResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return IdentifierAuditResult(
                identifierName = "eSIM EID (eUICC Identifier)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "eSIM EuiccManager API introduced in Android 9 (API 28).",
                requiredPermission = "EuiccManager",
                apiLevelIntroducedRestriction = 28,
                severity = RestrictionSeverity.UNSUPPORTED
            )
        }

        val em = euiccManager
        if (em == null || !em.isEnabled) {
            return IdentifierAuditResult(
                identifierName = "eSIM EID (eUICC Identifier)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "Device does not contain an enabled embedded UICC (eSIM) chip.",
                requiredPermission = "None",
                apiLevelIntroducedRestriction = 28,
                severity = RestrictionSeverity.UNSUPPORTED
            )
        }

        return runCatching {
            val eid = em.eid
            if (eid != null) {
                IdentifierAuditResult(
                    identifierName = "eSIM EID (eUICC Identifier)",
                    accessible = true,
                    valueOrMasked = maskIdentifier(eid),
                    restrictionReason = "Accessible.",
                    requiredPermission = "Carrier Privileges / System",
                    apiLevelIntroducedRestriction = 28,
                    severity = RestrictionSeverity.ACCESSIBLE
                )
            } else {
                IdentifierAuditResult(
                    identifierName = "eSIM EID (eUICC Identifier)",
                    accessible = false,
                    valueOrMasked = null,
                    restrictionReason = "eSIM present, but EID query requires carrier privileges.",
                    requiredPermission = "Carrier Privileges",
                    apiLevelIntroducedRestriction = 28,
                    severity = RestrictionSeverity.CARRIER_PRIVILEGE_REQUIRED
                )
            }
        }.getOrElse { error ->
            IdentifierAuditResult(
                identifierName = "eSIM EID (eUICC Identifier)",
                accessible = false,
                valueOrMasked = null,
                restrictionReason = "SecurityException accessing eSIM EID: ${error.message}",
                requiredPermission = "Carrier Privileges",
                apiLevelIntroducedRestriction = 28,
                severity = RestrictionSeverity.CARRIER_PRIVILEGE_REQUIRED
            )
        }
    }

    private fun maskIdentifier(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length <= 4) return "****"
        val prefix = trimmed.take(2)
        val suffix = trimmed.takeLast(4)
        val masked = "*".repeat(trimmed.length - 6)
        return "$prefix$masked$suffix"
    }
}
