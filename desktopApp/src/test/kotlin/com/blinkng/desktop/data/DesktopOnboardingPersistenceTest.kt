package com.blinkng.desktop.data

import com.blinkng.desktop.DesktopAppState
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Real client/state serialization with an in-process server; no live Auth account or SMS. */
class DesktopOnboardingPersistenceTest {
    private class Server : Interceptor {
        val profile = JSONObject()
            .put("id", "00000000-0000-4000-8000-000000000001")
            .put("username", "student_test").put("full_name", "Test Student")
            .put("onboarding_step", 1).put("onboarding_completed", false)
            .put("phone", "").put("phone_visibility", "PRIVATE")
        var birthday = "2001-02-03"
        var failBirthdayRead = false
        var failPhoneSave = false
        val writes = mutableListOf<Pair<String, JSONObject>>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val table = request.url.pathSegments.last()
            val body = request.body?.let { JSONObject(Buffer().also(it::writeTo).readUtf8()) }
            var status = 200
            var response = "[]"
            when {
                request.method == "GET" && table == "profiles" ->
                    response = JSONArray().put(profile).toString()
                request.method == "GET" && table == "profile_private_details" -> {
                    if (failBirthdayRead) status = 503
                    else response = JSONArray().put(JSONObject().put("birth_date", birthday)).toString()
                }
                body != null -> {
                    writes.add(table to body)
                    if (table == "profiles" && body.has("phone") && failPhoneSave) status = 503
                    else if (table == "profiles") body.keys().forEach { key -> profile.put(key, body.get(key)) }
                    else if (table == "profile_private_details") birthday = body.getString("birth_date")
                }
                else -> error("Unexpected request: ${request.method} ${request.url.encodedPath}")
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("test")
                .body(response.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun client(server: Server) = DesktopSupabaseClient(
        httpClient = OkHttpClient.Builder().addInterceptor(server).build(),
        initialSession = DesktopSession(
            accessToken = "test", refreshToken = "test",
            userId = server.profile.getString("id"), email = "test@example.invalid",
            expiresAtEpochSeconds = Long.MAX_VALUE,
        ),
    )

    @Test fun ownerBirthdayIsLoadedBeforeEditingAndPreservedAfterSavingUsername() = runBlocking {
        val server = Server()
        val client = client(server)
        assertEquals(server.birthday, client.fetchProfile().birthDate)
        val updated = client.saveOnboardingProfile(onboardingStep = 1)
        assertEquals(server.birthday, updated.birthDate)
        assertTrue(server.writes.none { it.first == "profile_private_details" })
    }

    @Test fun privateReadFailureDoesNotCacheAnEmptyBirthdayOrWriteAnything() = runBlocking {
        val server = Server()
        val client = client(server)
        server.failBirthdayRead = true
        assertTrue(runCatching { client.fetchProfile() }.isFailure)
        assertTrue(server.writes.isEmpty())
        server.failBirthdayRead = false
        assertEquals(server.birthday, client.fetchProfile().birthDate)
    }

    @Test fun invalidBirthdaysCannotClearOrReplaceAnExistingBirthday() = runBlocking {
        val server = Server()
        val client = client(server)
        for (value in listOf("", "2099-01-01", "2001-02-29")) {
            assertTrue(runCatching { client.savePrivateBirthDate(value) }.isFailure)
        }
        assertEquals("2001-02-03", server.birthday)
        assertTrue(server.writes.isEmpty())
    }

    @Test fun phoneIsNormalizedPrivateAndSurvivesLaterProfileSteps() = runBlocking {
        val server = Server()
        val client = client(server)
        client.saveMyProfilePhone("08012345678")
        val phoneWrite = server.writes.single().second
        assertEquals(setOf("phone", "phone_visibility"), phoneWrite.keySet())
        assertEquals("+2348012345678", phoneWrite.getString("phone"))
        assertEquals("PRIVATE", phoneWrite.getString("phone_visibility"))
        val updated = client.saveOnboardingProfile(interests = listOf("Technology"), onboardingStep = 3)
        assertEquals("+2348012345678", updated.phone)
        assertEquals("PRIVATE", updated.phoneVisibility)
        client.saveMyProfilePhone("")
        assertEquals("", client.fetchProfile().phone)
    }

    @Test fun failedPhoneSaveDoesNotAdvanceOnboardingAndRetryDoes() = runBlocking {
        val server = Server()
        val client = client(server)
        val state = DesktopAppState(client)
        state.refreshProfile()
        server.failPhoneSave = true
        assertTrue(runCatching {
            state.saveOnboardingBasics("", "", "", "Male", "2001-02-03", "08012345678", true)
        }.isFailure)
        assertEquals(1, server.profile.getInt("onboarding_step"))
        assertFalse(state.busy)
        server.failPhoneSave = false
        state.saveOnboardingBasics("", "", "", "Male", "2001-02-03", "08012345678", true)
        assertEquals(2, state.profile?.onboardingStep)
        assertEquals("NON_STUDENT", state.profile?.accountType)
        assertEquals("+2348012345678", state.profile?.phone)
        assertEquals("2001-02-03", state.profile?.birthDate)
    }
}
