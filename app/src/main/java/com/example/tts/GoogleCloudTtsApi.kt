package com.example.tts

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class CloudTtsRequest(
    val input: CloudTtsInput,
    val voice: CloudTtsVoice,
    val audioConfig: CloudTtsAudioConfig
)

@JsonClass(generateAdapter = true)
data class CloudTtsInput(
    val ssml: String? = null,
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class CloudTtsVoice(
    val languageCode: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class CloudTtsAudioConfig(
    val audioEncoding: String = "LINEAR16",
    val sampleRateHertz: Int = 24000,
    val speakingRate: Double = 0.95,
    val pitch: Double? = null
)

@JsonClass(generateAdapter = true)
data class CloudTtsResponse(
    val audioContent: String? = null
)

interface GoogleCloudTtsApi {
    @POST("v1/text:synthesize")
    suspend fun synthesize(
        @Query("key") apiKey: String,
        @Body body: CloudTtsRequest
    ): Response<CloudTtsResponse>

    companion object {
        private const val BASE_URL = "https://texttospeech.googleapis.com/"

        fun create(): GoogleCloudTtsApi {
            val okHttp = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GoogleCloudTtsApi::class.java)
        }
    }
}
