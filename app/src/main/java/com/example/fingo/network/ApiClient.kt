package com.example.fingo.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://f-ngo-btk-backend.onrender.com/api/v1/"

    // ─── Public (auth gerektirmeyen) istemci ─────────────────────────────────

    val public: ApiService by lazy { buildService(token = null) }

    // ─── Auth gerektiren istemci — token dinamik olarak alınır ───────────────

    fun authenticated(token: String): ApiService = buildService(token)

    // ─── İnşa fonksiyonu ─────────────────────────────────────────────────────

    private fun buildService(token: String?): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // Render cold-start için 60s
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .apply {
                if (token != null) {
                    addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                        )
                    }
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
