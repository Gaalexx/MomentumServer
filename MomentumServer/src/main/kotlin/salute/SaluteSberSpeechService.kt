package com.example.salute

import com.example.Models.SaluteSberRecognitionResultDTO
import com.example.database.MediaModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

object SaluteSberSpeechService {
    private const val TOKEN_REFRESH_SKEW_MS = 60_000L

    private val logger = LoggerFactory.getLogger(SaluteSberSpeechService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }
    private val tokenMutex = Mutex()

    @Volatile
    private var cachedToken: AccessToken? = null

    suspend fun recognizeByPresignedUrl(presignedUrl: String, media: MediaModel): RecognitionResult {
        val config = SaluteSberConfig.load()
        val sourceContentType = config.audioContentTypeOverride
            ?: SberAudioContentTypes.resolve(media.mimeType, config.pcmSampleRate)
        val requiresTranscoding = SberAudioContentTypes.requiresPcmTranscoding(sourceContentType)
        validateMediaLimits(media, config, requiresTranscoding)

        val sourceBytes = downloadMedia(
            presignedUrl = presignedUrl,
            maxBytes = if (requiresTranscoding) config.maxSourceMediaBytes else config.maxAudioBytes
        )
        val preparedAudio = prepareAudioForRecognition(sourceBytes, sourceContentType, media, config)

        logger.info(
            "SaluteSpeech media prepared: mediaId={}, mimeType={}, sourceContentType={}, contentType={}, audioBytes={}, sourceBytes={}",
            media.id,
            media.mimeType,
            sourceContentType,
            preparedAudio.contentType,
            preparedAudio.bytes.size,
            sourceBytes.size
        )

        val accessToken = getAccessToken(config)
        val requestId = UUID.randomUUID().toString()
        logger.info(
            "SaluteSpeech recognition request started: mediaId={}, requestId={}, recognizeUrl={}, contentType={}, audioBytes={}",
            media.id,
            requestId,
            config.recognizeUrl,
            preparedAudio.contentType,
            preparedAudio.bytes.size
        )
        val response = httpClient.post(config.recognizeUrl) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.ContentType, preparedAudio.contentType)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header("X-Request-ID", requestId)
            setBody(preparedAudio.bytes)
        }

        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            logger.warn(
                "SaluteSpeech recognition request failed: mediaId={}, requestId={}, status={}, body={}",
                media.id,
                requestId,
                response.status.value,
                responseBody.take(1000)
            )
            throw SaluteSberRemoteException(
                status = response.status,
                message = "SaluteSpeech recognition failed",
                details = responseBody
            )
        }

        val results = parseRecognitionResults(responseBody)
        val text = results
            .mapNotNull { (it.normalizedText ?: it.text)?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
        val normalizedText = results
            .mapNotNull { it.normalizedText?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
            .takeIf(String::isNotEmpty)
        if (text.isBlank()) {
            logger.warn(
                "SaluteSpeech recognition returned empty text: mediaId={}, requestId={}, status={}, body={}",
                media.id,
                requestId,
                response.status.value,
                responseBody.take(1000)
            )
        }
        logger.info(
            "SaluteSpeech recognition request completed: mediaId={}, requestId={}, status={}, textChars={}, results={}",
            media.id,
            requestId,
            response.status.value,
            text.length,
            results.size
        )

        return RecognitionResult(
            text = text,
            normalizedText = normalizedText,
            results = results
        )
    }

    private suspend fun getAccessToken(config: SaluteSberConfig): String {
        val now = System.currentTimeMillis()
        val token = cachedToken
        if (token != null && token.expiresAtMs - TOKEN_REFRESH_SKEW_MS > now) {
            return token.value
        }

        return tokenMutex.withLock {
            val lockedToken = cachedToken
            val lockedNow = System.currentTimeMillis()
            if (lockedToken != null && lockedToken.expiresAtMs - TOKEN_REFRESH_SKEW_MS > lockedNow) {
                lockedToken.value
            } else {
                val newToken = requestAccessToken(config)
                cachedToken = newToken
                newToken.value
            }
        }
    }

    private suspend fun requestAccessToken(config: SaluteSberConfig): AccessToken {
        val response = httpClient.submitForm(
            url = config.oauthUrl,
            formParameters = Parameters.build {
                append("scope", config.scope)
            }
        ) {
            header(HttpHeaders.Authorization, "Basic ${config.authorizationKey}")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header("RqUID", UUID.randomUUID().toString())
        }

        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            logger.warn(
                "SaluteSpeech token request failed: status={}, body={}",
                response.status.value,
                responseBody.take(1000)
            )
            throw SaluteSberRemoteException(
                status = response.status,
                message = "SaluteSpeech token request failed",
                details = responseBody
            )
        }

        val tokenResponse = json.decodeFromString<SaluteSberTokenResponse>(responseBody)
        return AccessToken(
            value = tokenResponse.accessToken,
            expiresAtMs = tokenResponse.expiresAt
        )
    }

    private suspend fun downloadMedia(presignedUrl: String, maxBytes: Long): ByteArray {
        val response = httpClient.get(presignedUrl)
        if (!response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            logger.warn(
                "S3 presigned media download failed: status={}, body={}",
                response.status.value,
                responseBody.take(1000)
            )
            throw SaluteSberRemoteException(
                status = response.status,
                message = "Failed to download media from S3 presigned URL",
                details = responseBody
            )
        }

        val bytes = response.body<ByteArray>()
        if (bytes.size > maxBytes) {
            throw SaluteSberAudioLimitException("Media file is larger than $maxBytes bytes")
        }

        return bytes
    }

    private fun prepareAudioForRecognition(
        sourceBytes: ByteArray,
        contentType: String,
        media: MediaModel,
        config: SaluteSberConfig
    ): PreparedAudio {
        if (!SberAudioContentTypes.requiresPcmTranscoding(contentType)) {
            return PreparedAudio(sourceBytes, contentType)
        }

        val convertedBytes = transcodeToPcmWav(sourceBytes, media, config)
        if (convertedBytes.size > config.maxAudioBytes) {
            throw SaluteSberAudioLimitException(
                "Transcoded audio file is larger than ${config.maxAudioBytes} bytes"
            )
        }

        return PreparedAudio(convertedBytes, "audio/x-pcm;bit=16;rate=${config.pcmSampleRate}")
    }

    private fun transcodeToPcmWav(sourceBytes: ByteArray, media: MediaModel, config: SaluteSberConfig): ByteArray {
        val input = Files.createTempFile("momentum-salute-input-", ".media")
        val output = Files.createTempFile("momentum-salute-output-", ".wav")
        val log = Files.createTempFile("momentum-salute-ffmpeg-", ".log")

        try {
            Files.write(input, sourceBytes)
            val process = ProcessBuilder(
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                input.toString(),
                "-map",
                "0:a:0",
                "-vn",
                "-ac",
                "1",
                "-ar",
                config.pcmSampleRate.toString(),
                "-c:a",
                "pcm_s16le",
                output.toString()
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(log.toFile())
                .start()

            val completed = process.waitFor(config.mediaConversionTimeoutSeconds, TimeUnit.SECONDS)
            val stderr = Files.readString(log).trim()
            if (!completed) {
                process.destroyForcibly()
                throw SaluteSberAudioConversionException(
                    "ffmpeg timed out after ${config.mediaConversionTimeoutSeconds} seconds while converting media ${media.id}"
                )
            }

            if (process.exitValue() != 0) {
                throw SaluteSberAudioConversionException(
                    "ffmpeg failed while converting media ${media.id}: ${stderr.take(1000)}"
                )
            }

            val convertedBytes = Files.readAllBytes(output)
            logger.info(
                "Audio transcoded for SaluteSpeech: mediaId={}, sourceBytes={}, convertedBytes={}, contentType={}",
                media.id,
                sourceBytes.size,
                convertedBytes.size,
                "audio/x-pcm;bit=16;rate=${config.pcmSampleRate}"
            )
            return convertedBytes
        } finally {
            Files.deleteIfExists(input)
            Files.deleteIfExists(output)
            Files.deleteIfExists(log)
        }
    }

    private fun validateMediaLimits(media: MediaModel, config: SaluteSberConfig, requiresTranscoding: Boolean) {
        val maxSourceBytes = if (requiresTranscoding) config.maxSourceMediaBytes else config.maxAudioBytes
        if (media.sizeBytes > maxSourceBytes) {
            throw SaluteSberAudioLimitException("Media file is larger than $maxSourceBytes bytes")
        }

        val duration = media.duration
        if (duration != null && duration > config.maxAudioDurationMs) {
            throw SaluteSberAudioLimitException("Audio file is longer than ${config.maxAudioDurationMs} ms")
        }
    }

    private fun parseRecognitionResults(responseBody: String): List<SaluteSberRecognitionResultDTO> {
        return when (val root = json.parseToJsonElement(responseBody)) {
            is JsonObject -> parseRecognitionRoot(root)
            is JsonArray -> root.flatMap(::parseRecognitionElement)
            else -> emptyList()
        }
    }

    private fun parseRecognitionRoot(root: JsonObject): List<SaluteSberRecognitionResultDTO> {
        return when (val result = root["result"]) {
            is JsonArray -> result.flatMap(::parseRecognitionElement)
            is JsonObject -> parseRecognitionElement(result)
            is JsonPrimitive -> parseRecognitionElement(result)
            else -> parseRecognitionElement(root)
        }
    }

    private fun parseRecognitionElement(element: JsonElement): List<SaluteSberRecognitionResultDTO> {
        return when (element) {
            is JsonPrimitive -> element.contentOrNull
                ?.let { listOf(SaluteSberRecognitionResultDTO(text = it)) }
                ?: emptyList()

            is JsonArray -> element.flatMap(::parseRecognitionElement)

            is JsonObject -> {
                val nestedResults = element["results"]
                if (nestedResults is JsonArray) {
                    nestedResults.flatMap(::parseRecognitionElement)
                } else {
                    listOfNotNull(
                        json.decodeFromJsonElement<SaluteSberRecognitionResultDTO>(element)
                            .takeIf { it.text != null || it.normalizedText != null }
                    )
                }
            }
        }
    }
}

data class RecognitionResult(
    val text: String,
    val normalizedText: String?,
    val results: List<SaluteSberRecognitionResultDTO>
)

private data class PreparedAudio(
    val bytes: ByteArray,
    val contentType: String
)

data class SaluteSberConfig(
    val authorizationKey: String,
    val scope: String,
    val oauthUrl: String,
    val recognizeUrl: String,
    val audioContentTypeOverride: String?,
    val pcmSampleRate: Int,
    val maxAudioBytes: Long,
    val maxAudioDurationMs: Long,
    val maxSourceMediaBytes: Long,
    val mediaConversionTimeoutSeconds: Long
) {
    companion object {
        fun load(): SaluteSberConfig {
            return SaluteSberConfig(
                authorizationKey = loadAuthorizationKey(),
                scope = env("SALUTE_SBER_SCOPE") ?: "SALUTE_SPEECH_PERS",
                oauthUrl = env("SALUTE_SBER_OAUTH_URL")
                    ?: "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                recognizeUrl = env("SALUTE_SBER_RECOGNIZE_URL")
                    ?: "https://smartspeech.sber.ru/rest/v1/speech:recognize",
                audioContentTypeOverride = env("SALUTE_SBER_AUDIO_CONTENT_TYPE"),
                pcmSampleRate = env("SALUTE_SBER_PCM_SAMPLE_RATE")?.toIntOrNull() ?: 16_000,
                maxAudioBytes = env("SALUTE_SBER_MAX_AUDIO_BYTES")?.toLongOrNull() ?: 2L * 1024L * 1024L,
                maxAudioDurationMs = env("SALUTE_SBER_MAX_AUDIO_DURATION_MS")?.toLongOrNull() ?: 60_000L,
                maxSourceMediaBytes = env("SALUTE_SBER_MAX_SOURCE_MEDIA_BYTES")?.toLongOrNull()
                    ?: 50L * 1024L * 1024L,
                mediaConversionTimeoutSeconds = env("SALUTE_SBER_MEDIA_CONVERSION_TIMEOUT_SECONDS")?.toLongOrNull()
                    ?: 60L
            )
        }

        private fun loadAuthorizationKey(): String {
            env("SALUTE_SBER_AUTH_KEY")?.let { key ->
                return key.removePrefix("Basic").trim()
            }

            val clientId = env("SALUTE_SBER_CLIENT_ID")
            val clientSecret = env("SALUTE_SBER_CLIENT_SECRET")
            if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
                throw SaluteSberConfigurationException(
                    "Set SALUTE_SBER_AUTH_KEY or both SALUTE_SBER_CLIENT_ID and SALUTE_SBER_CLIENT_SECRET"
                )
            }

            val credentials = "$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8)
            return Base64.getEncoder().encodeToString(credentials)
        }

        private fun env(name: String): String? {
            return System.getenv(name)?.takeIf { it.isNotBlank() }
        }
    }
}

object SberAudioContentTypes {
    private val transcodableAliases = setOf(
        "mp4",
        "m4a",
        "mov",
        "webm",
        "mkv",
        "3gp",
        "3gpp"
    )

    private val transcodableApplicationTypes = setOf(
        "application/mp4",
        "application/octet-stream"
    )

    fun resolve(mimeType: String, pcmSampleRate: Int): String {
        val normalizedMime = mimeType.lowercase().trim()
        val baseMime = normalizedMime.substringBefore(";").trim()

        return when {
            baseMime == "audio/mpeg" || baseMime == "audio/mp3" -> "audio/mpeg"
            baseMime == "audio/flac" || baseMime == "audio/x-flac" -> "audio/flac"
            baseMime == "audio/ogg" || normalizedMime.contains("codecs=opus") -> "audio/ogg;codecs=opus"
            baseMime == "audio/x-pcm" -> mimeType
            baseMime == "audio/pcm" -> "audio/x-pcm;bit=16;rate=$pcmSampleRate"
            baseMime == "audio/wav" || baseMime == "audio/wave" || baseMime == "audio/x-wav" ->
                "audio/x-pcm;bit=16;rate=$pcmSampleRate"
            baseMime.startsWith("audio/pcma") ->
                if (normalizedMime.contains("rate=")) mimeType else "audio/pcma;rate=$pcmSampleRate"
            baseMime.startsWith("audio/pcmu") ->
                if (normalizedMime.contains("rate=")) mimeType else "audio/pcmu;rate=$pcmSampleRate"
            baseMime.startsWith("audio/g729") -> mimeType
            isFfmpegConvertible(baseMime) -> mimeType
            else -> throw SaluteSberUnsupportedAudioException("Unsupported audio mime type for SaluteSpeech: $mimeType")
        }
    }

    fun requiresPcmTranscoding(contentType: String): Boolean {
        val normalizedContentType = contentType.lowercase().trim()
        val baseContentType = normalizedContentType.substringBefore(";").trim()
        return when {
            baseContentType == "audio/ogg" && normalizedContentType.contains("codecs=opus") -> true
            isFfmpegConvertible(baseContentType) -> true
            else -> false
        }
    }

    private fun isFfmpegConvertible(contentType: String): Boolean {
        return contentType.startsWith("video/") ||
            contentType in transcodableApplicationTypes ||
            contentType in transcodableAliases ||
            (
                contentType.startsWith("audio/") &&
                    contentType !in setOf(
                        "audio/mpeg",
                        "audio/mp3",
                        "audio/flac",
                        "audio/x-flac",
                        "audio/x-pcm",
                        "audio/pcm",
                        "audio/wav",
                        "audio/wave",
                        "audio/x-wav"
                    ) &&
                    !contentType.startsWith("audio/pcma") &&
                    !contentType.startsWith("audio/pcmu") &&
                    !contentType.startsWith("audio/g729")
                )
    }
}

class SaluteSberConfigurationException(message: String) : RuntimeException(message)

class SaluteSberUnsupportedAudioException(message: String) : RuntimeException(message)

class SaluteSberAudioLimitException(message: String) : RuntimeException(message)

class SaluteSberAudioConversionException(message: String) : RuntimeException(message)

class SaluteSberRemoteException(
    val status: HttpStatusCode,
    message: String,
    val details: String? = null
) : RuntimeException(message)

private data class AccessToken(
    val value: String,
    val expiresAtMs: Long
)

@Serializable
private data class SaluteSberTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_at")
    val expiresAt: Long
)
