package com.seina.chan.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ConnectionProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val ip: String,
    val port: String,
    val token: String = "",
    val username: String = "",
    val authMode: AuthMode = AuthMode.TOKEN
)
