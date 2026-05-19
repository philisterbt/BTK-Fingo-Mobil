package com.example.fingo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fingo.network.ApiClient
import com.example.fingo.network.PortfolioResponse
import com.example.fingo.network.TokenManager
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class WalletActivity : AppCompatActivity() {

    private lateinit var tvBalance: TextView
    private lateinit var tvDailyGain: TextView
    private lateinit var containerPositions: LinearLayout

    // Dinamik Varlık Dağılımı Elemanları
    private lateinit var tvAssetCryptoPct: TextView
    private lateinit var pbAssetCrypto: ProgressBar
    private lateinit var tvAssetStockPct: TextView
    private lateinit var pbAssetStock: ProgressBar
    private lateinit var tvAssetCashPct: TextView
    private lateinit var pbAssetCash: ProgressBar

    // Sınıf seviyesinde Nakit bakiyesini sakla
    private var balanceTry: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_primary)
        window.decorView.systemUiVisibility = 0
        setContentView(R.layout.activity_wallet)

        tvBalance    = findViewById(R.id.tvBalance)
        tvDailyGain  = findViewById(R.id.tvDailyGain)
        containerPositions = findViewById(R.id.containerPositions)

        // Dağılım Bindings
        tvAssetCryptoPct = findViewById(R.id.tvAssetCryptoPct)
        pbAssetCrypto    = findViewById(R.id.pbAssetCrypto)
        tvAssetStockPct  = findViewById(R.id.tvAssetStockPct)
        pbAssetStock     = findViewById(R.id.pbAssetStock)
        tvAssetCashPct   = findViewById(R.id.tvAssetCashPct)
        pbAssetCash      = findViewById(R.id.pbAssetCash)

        setupNavigation()
        loadWalletData()
    }

    /**
     * Paralel olarak:
     * - GET /api/v1/users/me       → Bakiye
     * - GET /api/v1/trade/portfolio → Pozisyonlar
     * - GET /api/v1/crypto/tickers → Anlık fiyatlar (P&L hesabı için)
     */
    private fun loadWalletData() {
        val api = TokenManager.getApiService(this)
        if (api == null) {
            Toast.makeText(this, "Giriş yapmanız gerekiyor.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }

        lifecycleScope.launch {
            try {
                // Paralel istek
                val userDeferred      = async { api.getMe() }
                val portfolioDeferred = async { api.getPortfolio() }
                val tickersDeferred   = async { ApiClient.public.getTickers() }
                val stockTickersDeferred = async { ApiClient.public.getStockTickers() }

                val userResponse      = userDeferred.await()
                val portfolioResponse = portfolioDeferred.await()
                val tickersResponse   = tickersDeferred.await()
                val stockTickersResponse = stockTickersDeferred.await()

                // ─── Bakiye ─────────────────────────────────────────────────
                if (userResponse.isSuccessful) {
                    val user = userResponse.body()
                    balanceTry = user?.balance_try ?: 0.0
                    tvBalance.text = "₺${NumberFormat.getNumberInstance(Locale.US).format(balanceTry)}"
                } else if (userResponse.code() == 403) {
                    handleExpiredToken()
                    return@launch
                }

                // ─── Portföy + Anlık P&L ────────────────────────────────────
                if (portfolioResponse.isSuccessful) {
                    val portfolio = portfolioResponse.body() ?: emptyList()
                    val tickers   = if (tickersResponse.isSuccessful) tickersResponse.body() ?: emptyList() else emptyList()
                    val stockTickers = if (stockTickersResponse.isSuccessful) stockTickersResponse.body() ?: emptyList() else emptyList()

                    val combinedTickers = tickers + stockTickers
                    val priceMap  = combinedTickers.associate { it.symbol to it.lastPriceDouble() }

                    updatePositionsUI(portfolio, priceMap)
                } else {
                    Toast.makeText(this@WalletActivity, "Portföy yüklenemedi: ${portfolioResponse.code()}", Toast.LENGTH_LONG).show()
                    // Portföy yüklenemese de nakit dağılımını göster
                    updatePositionsUI(emptyList(), emptyMap())
                }

            } catch (e: Exception) {
                Toast.makeText(this@WalletActivity, "Bağlantı hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                updatePositionsUI(emptyList(), emptyMap())
            }
        }
    }

    /** Portföy kartlarını dinamik olarak doldur ve Varlık Dağılımını Hesapla */
    private fun updatePositionsUI(
        portfolio: List<PortfolioResponse>,
        priceMap: Map<String, Double>
    ) {
        containerPositions.removeAllViews()

        // USD - TRY Kur Değeri (USDT/TRY ticker'ından dinamik al, yoksa 33.0 varsay)
        val usdtTry = priceMap["USDTTRY"] ?: priceMap["USDT/TRY"] ?: 33.0

        var cryptoVal = 0.0
        var stockVal = 0.0
        val cashVal = balanceTry // Zaten TRY bazında

        if (portfolio.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Henüz açık pozisyon yok."
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            empty.textSize = 14f
            containerPositions.addView(empty)
            
            // Portföy boşsa %100 Nakit göster
            updateAssetDistributionUI(0.0, 0.0, cashVal)
            return
        }

        var totalPnl = 0.0

        portfolio.forEach { pos ->
            val isCrypto = pos.asset_type == null || pos.asset_type == "crypto" || pos.asset_type == ""
            val ticker     = if (isCrypto) "${pos.symbol}USDT" else pos.symbol
            val livePrice  = priceMap[ticker] ?: priceMap[pos.symbol] ?: pos.average_buy_price
            val currentVal = pos.amount * livePrice
            val costBasis  = pos.amount * pos.average_buy_price
            val pnl        = currentVal - costBasis
            totalPnl      += pnl

            // Dağılım hesaplamaları için TRY bazına çevir
            val isTryAsset = pos.symbol.endsWith(".IS")
            val currentValTry = if (isTryAsset) currentVal else (currentVal * usdtTry)

            if (isCrypto) {
                cryptoVal += currentValTry
            } else {
                stockVal += currentValTry
            }

            val pnlFormatted = if (isTryAsset) {
                if (pnl >= 0) "↑ +₺${String.format("%.2f", pnl)}" else "↓ -₺${String.format("%.2f", kotlin.math.abs(pnl))}"
            } else {
                if (pnl >= 0) "↑ +$${String.format("%.2f", pnl)}" else "↓ -$${String.format("%.2f", kotlin.math.abs(pnl))}"
            }
            val pnlColor = if (pnl >= 0) R.color.accent_green else R.color.error_red

            // Inflate item satırı (programmatically)
            val row = layoutInflater.inflate(R.layout.item_position, containerPositions, false)
            
            val tvIcon = row.findViewById<TextView>(R.id.tvPositionSymbol)
            tvIcon.text = if (isCrypto) "₿" else "📈"
            tvIcon.setTextColor(ContextCompat.getColor(this, if (isCrypto) R.color.accent_green else R.color.warning_orange))
            
            row.findViewById<TextView>(R.id.tvPositionName).text = if (isCrypto) getCryptoName(pos.symbol) else getStockName(pos.symbol)
            row.findViewById<TextView>(R.id.tvPositionAmount).text = "${pos.amount} Adet"
            
            row.findViewById<TextView>(R.id.tvPositionValue).text = if (isTryAsset) {
                "₺${String.format("%,.2f", currentVal)}"
            } else {
                "$${String.format("%,.2f", currentVal)}"
            }
            
            val tvPnl = row.findViewById<TextView>(R.id.tvPositionPnl)
            tvPnl.text = pnlFormatted
            tvPnl.setTextColor(ContextCompat.getColor(this, pnlColor))

            containerPositions.addView(row)
        }

        // Toplam günlük P&L
        val gainText  = if (totalPnl >= 0) "+ $${String.format("%.2f", totalPnl)} Today 🚀 Piyasayı Yendin!" else "- $${String.format("%.2f", kotlin.math.abs(totalPnl))} Today 📉"
        tvDailyGain.text = gainText
        tvDailyGain.setTextColor(
            ContextCompat.getColor(this, if (totalPnl >= 0) R.color.accent_green else R.color.error_red)
        )

        // Dinamik Varlık Dağılımını Güncelle
        updateAssetDistributionUI(cryptoVal, stockVal, cashVal)
    }

    private fun updateAssetDistributionUI(crypto: Double, stock: Double, cash: Double) {
        val total = crypto + stock + cash
        if (total > 0.0) {
            val cryptoPct = ((crypto / total) * 100).toInt()
            val stockPct = ((stock / total) * 100).toInt()
            val cashPct = 100 - cryptoPct - stockPct // Küsürat yuvarlamasından dolayı tam 100 olmasını garantile

            tvAssetCryptoPct.text = "$cryptoPct%"
            pbAssetCrypto.progress = cryptoPct

            tvAssetStockPct.text = "$stockPct%"
            pbAssetStock.progress = stockPct

            tvAssetCashPct.text = "$cashPct%"
            pbAssetCash.progress = cashPct
        } else {
            tvAssetCryptoPct.text = "0%"
            pbAssetCrypto.progress = 0
            tvAssetStockPct.text = "0%"
            pbAssetStock.progress = 0
            tvAssetCashPct.text = "100%"
            pbAssetCash.progress = 100
        }
    }

    private fun handleExpiredToken() {
        TokenManager.clear(this)
        Toast.makeText(this, "Oturum süresi doldu, tekrar giriş yapın.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setupNavigation() {
        val navPiyasa   = findViewById<LinearLayout>(R.id.navPiyasa)
        val navFinAgent = findViewById<LinearLayout>(R.id.navFinAgent)
        val navAkademi  = findViewById<LinearLayout>(R.id.navAkademi)

        navPiyasa.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        navFinAgent.setOnClickListener {
            startActivity(Intent(this, FinAgentActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        navAkademi.setOnClickListener {
            startActivity(Intent(this, AcademyActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun getCryptoName(sym: String): String {
        return when (sym) {
            "BTC" -> "Bitcoin"
            "ETH" -> "Ethereum"
            "SOL" -> "Solana"
            "BNB" -> "Binance Coin"
            "XRP" -> "Ripple"
            else -> sym
        }
    }

    private fun getStockName(sym: String): String {
        return when (sym) {
            "THYAO" -> "Türk Hava Yolları"
            "AAPL" -> "Apple Inc."
            "TSLA" -> "Tesla Inc."
            "MSFT" -> "Microsoft Corp."
            "EREGL" -> "Erdemir"
            "ASELS" -> "Aselsan"
            else -> sym
        }
    }
}
