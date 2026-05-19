package com.example.fingo.network

// ─── Auth ────────────────────────────────────────────────────────────────────

data class RegisterRequest(
    val email: String,
    val full_name: String,
    val password: String
)

data class TokenResponse(
    val access_token: String,
    val token_type: String
)

data class UserResponse(
    val id: Int,
    val email: String,
    val full_name: String,
    val is_active: Boolean,
    val balance_try: Double
)

// ─── Trade ───────────────────────────────────────────────────────────────────

data class TradeRequest(
    val symbol: String,
    val amount: Double,
    val asset_type: String = "crypto" // "crypto" veya "stock"
)

data class PortfolioResponse(
    val id: Int? = null,
    val symbol: String,
    val asset_type: String? = "crypto",
    val amount: Double,
    val average_buy_price: Double
)

data class TransactionResponse(
    val id: Int,
    val symbol: String,
    val transaction_type: String, // "buy" veya "sell"
    val amount: Double,
    val price_try: Double,
    val total_try: Double,
    val created_at: String
)

data class AIAnalysisResponse(
    val profile_title: String,
    val profile_description: String,
    val patience_score: Int,
    val risk_score: Int,
    val panic_sell_probability: Int,
    val ai_advice: String
)

data class NewsFeedItem(
    val symbol: String,
    val title: String,
    val summary: String,
    val sentiment: String,
    val source: String
)

data class RebalanceResponse(
    val message: String,
    val new_balance_try: Double,
    val sold_assets: List<String>,
    val bought_assets: List<String>
)

// ─── Crypto ──────────────────────────────────────────────────────────────────

data class TickerResponse(
    val symbol: String,
    val lastPrice: String,
    val priceChangePercent: String,
    val highPrice: String,
    val lowPrice: String,
    val volume: String,
    val quoteVolume: String
) {
    /** Fiyatı Double'a güvenli çevirir */
    fun lastPriceDouble(): Double = lastPrice.toDoubleOrNull() ?: 0.0
    /** Yüzde değişimi Double'a çevirir */
    fun changePercent(): Double = priceChangePercent.toDoubleOrNull() ?: 0.0
}

data class CandleResponse(
    val open_time: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
    val close_time: Long
)

data class TermExplainResponse(
    val term: String,
    val explanation: String
)

data class PersonalizedQuizResponse(
    val question_id: Int,
    val question_text: String,
    val option_a: String,
    val option_b: String,
    val option_c: String,
    val correct_option: String,
    val explanation: String
)

data class DailyQuestItem(
    val id: String,
    val title: String,
    val description: String,
    val xp_reward: Int,
    val is_completed: Boolean
)

data class ChartChallengeItem(
    val id: String,
    val symbol: String,
    val title: String,
    val description: String,
    val visible_prices: List<Double>,
    val future_prices: List<Double>,
    val correct_direction: String,
    val explanation_title: String,
    val explanation_text: String
)

data class ChallengeSubmitRequest(
    val challenge_id: String,
    val user_prediction: String // "UP" veya "DOWN"
)

data class ChallengeSubmitResponse(
    val is_correct: Boolean,
    val explanation_title: String,
    val explanation_text: String,
    val future_prices: List<Double>
)

// ─── UI Helper ───────────────────────────────────────────────────────────────

/** Genel API hata sarmalayıcısı */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int = -1) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}
