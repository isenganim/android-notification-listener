package com.saquone.notificationlistener

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Tiga tab bawah + satu layar pengaturan endpoint. */
@Serializable data object Status : NavKey

@Serializable data object Apps : NavKey

@Serializable data object Logs : NavKey

@Serializable data object Setup : NavKey

@Serializable data class OemGuide(val url: String) : NavKey
