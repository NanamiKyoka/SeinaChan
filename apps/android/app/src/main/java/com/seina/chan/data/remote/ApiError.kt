package com.seina.chan.data.remote

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkError(cause: Throwable) : ApiError("网络错误: ${cause.message}", cause)
    class ServerError(code: Int, body: String?) : ApiError("服务端错误 $code: ${body?.take(100)}")
    class AuthError : ApiError("认证失败")
    class NotFound(message: String) : ApiError(message)
    class Timeout(cause: Throwable) : ApiError("请求超时", cause)
    class Unknown(message: String, cause: Throwable? = null) : ApiError(message, cause)
}
