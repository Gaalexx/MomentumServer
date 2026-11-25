package com.example

import com.example.Models.FriendRequestActionDTO
import com.example.Models.FriendRequestWithUserDetailsDTO
import com.example.Models.FriendshipListDTO
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

class FriendsRoutesTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    @Test
    fun `incoming request contains avatar url when sender has active avatar`() = testApplication {
        application { module(TestAppConfig.create()) }

        val client = jsonClient()
        val senderId = TestData.createUser("sender-${UUID.randomUUID()}@test.com")
        val receiverId = TestData.createUser("receiver-${UUID.randomUUID()}@test.com")
        TestData.createAvatar(senderId, isActive = true)
        TestData.createFriendRequest(senderId, receiverId)

        val receiverToken = TestData.generateToken(receiverId)

        val response = client.get("/api/momentum/friends/requests/incoming") {
            header(HttpHeaders.Authorization, "Bearer $receiverToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<List<FriendRequestWithUserDetailsDTO>>()
        assertEquals(1, body.size)
        assertEquals(senderId.toString(), body[0].fromUserId)
        assertNotNull(body[0].fromUserAvatarUrl)
        assertTrue(body[0].fromUserAvatarUrl!!.startsWith("http"))
    }

    @Test
    fun `friends list returns avatars for all friends`() = testApplication {
        application { module(TestAppConfig.create()) }

        val client = jsonClient()
        val userA = TestData.createUser("a-${UUID.randomUUID()}@test.com")
        val userB = TestData.createUser("b-${UUID.randomUUID()}@test.com")
        TestData.createAvatar(userA)
        TestData.createAvatar(userB)
        TestData.createFriendship(userA, userB)

        val tokenA = TestData.generateToken(userA)
        val response = client.get("/api/momentum/friends") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<FriendshipListDTO>()
        assertEquals(1, body.friends.size)
        assertNotNull(body.friends[0].userAvatarUrl)
    }
}