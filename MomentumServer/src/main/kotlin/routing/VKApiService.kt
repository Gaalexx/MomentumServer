package com.example.routing

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class VKUserInfoResponse(
    val user: VKUser
)

@Serializable
data class VKUser(
    @SerialName("user_id") val userId: String,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val email: String? = null,
    val phone: String? = null
)

object VKApiService {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getUserInfo(accessToken: String, clientId: String): VKUser? {
        return try {
            val response = httpClient.post("https://id.vk.com/oauth2/user_info") {
                setBody(FormDataContent(Parameters.build {
                    append("access_token", accessToken)
                    append("client_id", clientId)
                }))
            }
            if (response.status.isSuccess()) {
                response.body<VKUserInfoResponse>().user
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
