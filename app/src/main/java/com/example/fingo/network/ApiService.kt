package com.example.fingo.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ─── Auth ────────────────────────────────────────────────────────────────

    /** POST /api/v1/register — JSON body */
    @POST("register")
    suspend fun register(@Body body: RegisterRequest): Response<UserResponse>

    /**
     * POST /api/v1/login/access-token — FORM DATA (x-www-form-urlencoded)
     * ⚠️ JSON değil! @FormUrlEncoded + @Field kullanılıyor.
     */
    @FormUrlEncoded
    @POST("login/access-token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<TokenResponse>

    // ─── User ────────────────────────────────────────────────────────────────

    /** GET /api/v1/users/me — Kullanıcı bilgisi & bakiye (Token gerekli) */
    @GET("users/me")
    suspend fun getMe(): Response<UserResponse>

    /** GET /api/v1/users/me/history — İşlem geçmişi (Token gerekli) */
    @GET("users/me/history")
    suspend fun getHistory(
        @Query("limit") limit: Int = 50
    ): Response<List<TransactionResponse>>

    // ─── Crypto ──────────────────────────────────────────────────────────────

    /** GET /api/v1/crypto/tickers — Piyasa listesi (Auth yok) */
    @GET("crypto/tickers")
    suspend fun getTickers(
        @Query("symbol") symbol: String? = null
    ): Response<List<TickerResponse>>

    /** GET /api/v1/crypto/chart/{symbol} — Mum (Candlestick) verisi (Auth yok) */
    @GET("crypto/chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1h",
        @Query("limit") limit: Int = 100
    ): Response<List<CandleResponse>>

    // ─── Stocks ──────────────────────────────────────────────────────────────

    /** GET /api/v1/stocks/tickers — Hisse senedi piyasa listesi (Auth yok) */
    @GET("stocks/tickers")
    suspend fun getStockTickers(
        @Query("symbol") symbol: String? = null
    ): Response<List<TickerResponse>>

    /** GET /api/v1/stocks/chart/{symbol} — Hisse senedi mum (Candlestick) verisi (Auth yok) */
    @GET("stocks/chart/{symbol}")
    suspend fun getStockChart(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1h",
        @Query("limit") limit: Int = 100
    ): Response<List<CandleResponse>>

    // ─── Trade ───────────────────────────────────────────────────────────────

    /** POST /api/v1/trade/buy — Kripto al (Token gerekli) */
    @POST("trade/buy")
    suspend fun buyCrypto(@Body body: TradeRequest): Response<UserResponse>

    /** POST /api/v1/trade/sell — Kripto sat (Token gerekli) */
    @POST("trade/sell")
    suspend fun sellCrypto(@Body body: TradeRequest): Response<UserResponse>

    /** GET /api/v1/trade/portfolio — Portföy (Token gerekli) */
    @GET("trade/portfolio")
    suspend fun getPortfolio(): Response<List<PortfolioResponse>>

    // ─── AI ──────────────────────────────────────────────────────────────────

    /** GET /api/v1/ai/analyze-profile — Profil Analizi (Token gerekli) */
    @GET("ai/analyze-profile")
    suspend fun getAIAnalysis(): Response<AIAnalysisResponse>

    /** GET /api/v1/ai/news-feed — Portföye Özel AI Haber Özetleri (Token gerekli) */
    @GET("ai/news-feed")
    suspend fun getPersonalizedNews(): Response<List<NewsFeedItem>>

    /** POST /api/v1/ai/rebalance — Portföyü AI ile Yeniden Dengele (Token gerekli) */
    @POST("ai/rebalance")
    suspend fun performRebalance(): Response<RebalanceResponse>

    /** GET /api/v1/ai/explain-term — AI Finans Terimleri Sözlüğü (Token gerekmez/gerekli) */
    @GET("ai/explain-term")
    suspend fun explainTerm(@Query("term") term: String): Response<TermExplainResponse>

    /** GET /api/v1/ai/personalized-quiz — Fingo Döngüsü Kişiselleştirilmiş Quiz (Token gerekli) */
    @GET("ai/personalized-quiz")
    suspend fun getPersonalizedQuiz(): Response<PersonalizedQuizResponse>

    /** GET /api/v1/ai/daily-quests — Görev Merkezi (Token gerekli) */
    @GET("ai/daily-quests")
    suspend fun getDailyQuests(): Response<List<DailyQuestItem>>

    /** GET /api/v1/ai/chart-challenges — Geçmiş Grafik Arenası (Token gerekli) */
    @GET("ai/chart-challenges")
    suspend fun getChartChallenges(): Response<List<ChartChallengeItem>>

    /** POST /api/v1/ai/chart-challenges/submit — Tahmin Sonucunu Gönder (Token gerekli) */
    @POST("ai/chart-challenges/submit")
    suspend fun submitChartChallenge(@Body request: ChallengeSubmitRequest): Response<ChallengeSubmitResponse>
}
