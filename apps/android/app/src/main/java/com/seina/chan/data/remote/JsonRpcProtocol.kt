package com.seina.chan.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class JsonRpcRequest(
    val id: Int,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val id: Int,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)

class JsonRpcException(
    val code: Int,
    override val message: String
) : Exception("JSON-RPC error $code: $message")

@Serializable
data class JsonRpcEvent(
    val event: String,
    val data: JsonObject
)
