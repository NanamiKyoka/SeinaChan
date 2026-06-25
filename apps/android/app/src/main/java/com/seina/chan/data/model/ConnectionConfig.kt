package com.seina.chan.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
data class ConnectionConfig(
    val ip: String,
    val port: String,
    val token: String = "",
    val username: String = "",
    val authMode: AuthMode = AuthMode.TOKEN
)

@Serializable
enum class AuthMode {
    TOKEN,
    OAUTH
}
