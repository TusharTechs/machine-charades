package com.machinecharades.data

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase

/** The public SDK key for whichever store this build talks to. */
internal expect val storeApiKey: String

/** One thing the player can buy, flattened out of a RevenueCat offering. */
data class Plan(
    /** RevenueCat package identifier — what [Plus.buy] takes back. */
    val id: String,
    /** Localised price, already formatted by the store. Never build this yourself. */
    val price: String,
    /** True for the annual package, which the paywall leads with. */
    val isAnnual: Boolean,
)

/**
 * Machine Charades Plus.
 *
 * Everything the game knows about entitlements goes through here, so the rest
 * of the app never imports a RevenueCat type and the whole thing can be stubbed
 * in a build with no store key.
 *
 * Deliberately fails closed and quiet: an unconfigured or unreachable store
 * leaves the player on the free tier, which is a complete game. A daily word
 * game must never refuse to open a puzzle because billing had a bad day.
 */
object Plus {

    /** Entitlement identifier as configured in the RevenueCat dashboard. */
    const val ENTITLEMENT = "plus"

    /** False in a build with no key, so the paywall stays hidden rather than broken. */
    val isConfigured: Boolean get() = storeApiKey.isNotEmpty()

    private var started = false

    /** Safe to call more than once; the SDK itself is not. */
    fun start() {
        if (started || !isConfigured) return
        Purchases.configure(apiKey = storeApiKey)
        started = true
    }

    /** Whether this player currently has Plus. False on any failure. */
    suspend fun isActive(): Boolean {
        if (!isConfigured) return false
        return runCatching {
            Purchases.sharedInstance.awaitCustomerInfo()
                .entitlements[ENTITLEMENT]?.isActive == true
        }.getOrDefault(false)
    }

    /** What is on sale right now. Empty when the store is unreachable. */
    suspend fun plans(): List<Plan> {
        if (!isConfigured) return emptyList()
        return runCatching {
            val offering = Purchases.sharedInstance.awaitOfferings().current
                ?: return emptyList()
            offering.availablePackages.map { pkg ->
                Plan(
                    id = pkg.identifier,
                    price = pkg.storeProduct.price.formatted,
                    isAnnual = pkg.identifier.contains("annual", ignoreCase = true) ||
                        pkg.identifier.contains("year", ignoreCase = true),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Buys [planId] and reports whether the entitlement is live afterwards.
     *
     * A cancelled purchase is not an error — the player changed their mind, and
     * the paywall should simply stay open. Both cases return false, because the
     * only question the caller has is whether Plus is on.
     */
    suspend fun buy(planId: String): Boolean {
        if (!isConfigured) return false
        return runCatching {
            val offering = Purchases.sharedInstance.awaitOfferings().current
                ?: return false
            val pkg = offering.availablePackages.firstOrNull { it.identifier == planId }
                ?: return false
            Purchases.sharedInstance.awaitPurchase(packageToPurchase = pkg)
            isActive()
        }.getOrDefault(false)
    }

    /** Restores a previous purchase on a reinstall or a new device. */
    suspend fun restore(): Boolean {
        if (!isConfigured) return false
        return runCatching { isActive() }.getOrDefault(false)
    }
}
