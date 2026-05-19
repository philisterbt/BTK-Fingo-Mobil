package com.example.fingo

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fingo.network.ApiClient
import com.example.fingo.network.TokenManager
import com.example.fingo.network.TradeRequest
import com.example.fingo.network.CandleResponse
import com.example.fingo.network.UserResponse
import com.github.mikephil.charting.charts.CandleStickChart
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class TradeDetailActivity : AppCompatActivity() {

    private lateinit var tvDetailIcon: TextView
    private lateinit var tvDetailSymbol: TextView
    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailPrice: TextView
    private lateinit var tvDetailChange: TextView
    private lateinit var tvEstimatedTotal: TextView
    private lateinit var etAmount: EditText
    private lateinit var candleChart: CandleStickChart

    private var symbol: String = "BTCUSDT"
    private var assetType: String = "crypto"
    private var lastPrice: Double = 0.0
    private var changePercent: Double = 0.0
    
    // Aktif Grafik Zaman Dilimi
    private var currentInterval: String = "1h"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_primary)
        window.decorView.systemUiVisibility = 0
        setContentView(R.layout.activity_trade_detail)

        // Extract Extras
        symbol = intent.getStringExtra("symbol") ?: "BTCUSDT"
        assetType = intent.getStringExtra("asset_type") ?: "crypto"
        lastPrice = intent.getDoubleExtra("price", 0.0)
        changePercent = intent.getDoubleExtra("change", 0.0)

        // Varsayılan zaman dilimi başlangıcı
        currentInterval = if (assetType == "crypto") "1h" else "1d"

        // Init views
        tvDetailIcon = findViewById(R.id.tvDetailIcon)
        tvDetailSymbol = findViewById(R.id.tvDetailSymbol)
        tvDetailName = findViewById(R.id.tvDetailName)
        tvDetailPrice = findViewById(R.id.tvDetailPrice)
        tvDetailChange = findViewById(R.id.tvDetailChange)
        tvEstimatedTotal = findViewById(R.id.tvEstimatedTotal)
        etAmount = findViewById(R.id.etAmount)
        candleChart = findViewById(R.id.candleChart)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        setupUI()
        setupTextWatcher()
        setupChart()
        setupIntervalButtons()
        loadChartData()

        findViewById<View>(R.id.btnBuyDetail).setOnClickListener { performTrade(isBuy = true) }
        findViewById<View>(R.id.btnSellDetail).setOnClickListener { performTrade(isBuy = false) }
    }

    private fun setupUI() {
        val isCrypto = assetType == "crypto"
        val shortSymbol = if (isCrypto) symbol.removeSuffix("USDT") else symbol

        tvDetailSymbol.text = symbol
        tvDetailName.text = if (isCrypto) getCryptoName(shortSymbol) else getStockName(shortSymbol)
        tvDetailIcon.text = if (isCrypto) "₿" else "📈"

        val isTryAsset = symbol.endsWith(".IS")
        tvDetailPrice.text = if (isTryAsset) {
            "₺${NumberFormat.getNumberInstance(Locale.US).format(lastPrice)}"
        } else {
            "$${NumberFormat.getNumberInstance(Locale.US).format(lastPrice)}"
        }

        val sign = if (changePercent >= 0) "+" else ""
        tvDetailChange.text = "$sign${String.format("%.2f", changePercent)}%"
        tvDetailChange.setTextColor(
            ContextCompat.getColor(this, if (changePercent >= 0) R.color.accent_green else R.color.error_red)
        )
    }

    private fun setupIntervalButtons() {
        val btn15m = findViewById<TextView>(R.id.btnInterval15m)
        val btn1h = findViewById<TextView>(R.id.btnInterval1h)
        val btn4h = findViewById<TextView>(R.id.btnInterval4h)
        val btn1d = findViewById<TextView>(R.id.btnInterval1d)
        val btn1w = findViewById<TextView>(R.id.btnInterval1w)
        val tvChartTitle = findViewById<TextView>(R.id.tvChartTitle)

        updateIntervalButtonsUI(btn15m, btn1h, btn4h, btn1d, btn1w, tvChartTitle)

        val buttons = mapOf(
            btn15m to "15m",
            btn1h to "1h",
            btn4h to "4h",
            btn1d to "1d",
            btn1w to "1wk"
        )

        buttons.forEach { (btn, intervalVal) ->
            btn.setOnClickListener {
                currentInterval = intervalVal
                updateIntervalButtonsUI(btn15m, btn1h, btn4h, btn1d, btn1w, tvChartTitle)
                loadChartData()
            }
        }
    }

    private fun updateIntervalButtonsUI(
        btn15m: TextView, btn1h: TextView, btn4h: TextView, btn1d: TextView, btn1w: TextView,
        tvChartTitle: TextView
    ) {
        val list = listOf(btn15m, btn1h, btn4h, btn1d, btn1w)
        list.forEach { btn ->
            btn.background = ContextCompat.getDrawable(this, R.drawable.bg_chip)
            btn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        val activeBtn = when (currentInterval) {
            "15m" -> {
                tvChartTitle.text = "Fiyat Hareketi (15D)"
                btn15m
            }
            "1h" -> {
                tvChartTitle.text = "Fiyat Hareketi (1S)"
                btn1h
            }
            "4h" -> {
                tvChartTitle.text = "Fiyat Hareketi (4S)"
                btn4h
            }
            "1d" -> {
                tvChartTitle.text = "Fiyat Hareketi (1G)"
                btn1d
            }
            "1wk" -> {
                tvChartTitle.text = "Fiyat Hareketi (1H)"
                btn1w
            }
            else -> btn1h
        }

        activeBtn.background = ContextCompat.getDrawable(this, R.drawable.bg_chip_active)
        activeBtn.setTextColor(ContextCompat.getColor(this, R.color.bg_primary))
    }

    private fun setupTextWatcher() {
        etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                calculateEstimatedTotal()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun calculateEstimatedTotal() {
        val amountText = etAmount.text.toString()
        val amount = amountText.toDoubleOrNull() ?: 0.0
        val isTryAsset = symbol.endsWith(".IS")
        val totalUsdOrTry = amount * lastPrice

        if (amount <= 0) {
            tvEstimatedTotal.text = "₺0.00"
            return
        }

        if (isTryAsset) {
            tvEstimatedTotal.text = "₺${String.format("%,.2f", totalUsdOrTry)}"
        } else {
            // Assume 34.0 exchange rate roughly for display
            val totalTry = totalUsdOrTry * 34.0
            tvEstimatedTotal.text = "$${String.format("%,.2f", totalUsdOrTry)} (~ ₺${String.format("%,.2f", totalTry)})"
        }
    }

    private fun setupChart() {
        candleChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(true)
            setGridBackgroundColor(ContextCompat.getColor(this@TradeDetailActivity, R.color.bg_primary))
            
            // Interaction settings
            isDragEnabled = true
            isScaleXEnabled = true
            isScaleYEnabled = false
            setPinchZoom(true)
            isDoubleTapToZoomEnabled = true

            // X Axis (Time/Indices)
            xAxis.apply {
                isEnabled = true
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                textColor = ContextCompat.getColor(this@TradeDetailActivity, R.color.text_secondary)
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@TradeDetailActivity, R.color.border_default)
                setDrawAxisLine(true)
            }

            // Y Axis Left (Disabled)
            axisLeft.apply {
                isEnabled = false
            }

            // Y Axis Right (Value scale on the right - TradingView Standard!)
            axisRight.apply {
                isEnabled = true
                textColor = ContextCompat.getColor(this@TradeDetailActivity, R.color.text_secondary)
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@TradeDetailActivity, R.color.border_default)
                setDrawAxisLine(true)
            }

            setNoDataText("Grafik Yükleniyor...")
            setNoDataTextColor(ContextCompat.getColor(this@TradeDetailActivity, R.color.text_secondary))
        }
    }

    private fun loadChartData() {
        lifecycleScope.launch {
            try {
                val isCrypto = assetType == "crypto"
                
                // Map frontend values if needed (Binance needs 1w instead of 1wk)
                val queryInterval = if (isCrypto && currentInterval == "1wk") "1w" else currentInterval

                val response = if (isCrypto) {
                    ApiClient.public.getChart(symbol = symbol, interval = queryInterval, limit = 40)
                } else {
                    ApiClient.public.getStockChart(symbol = symbol, interval = queryInterval, limit = 40)
                }

                if (response.isSuccessful) {
                    val candles = response.body() ?: emptyList()
                    val entries = candles.mapIndexed { index, candle ->
                        com.github.mikephil.charting.data.CandleEntry(
                            index.toFloat(),
                            candle.high.toFloatOrNull() ?: 0f,
                            candle.low.toFloatOrNull() ?: 0f,
                            candle.open.toFloatOrNull() ?: 0f,
                            candle.close.toFloatOrNull() ?: 0f
                        )
                    }

                    val dataSet = com.github.mikephil.charting.data.CandleDataSet(entries, symbol)
                    dataSet.apply {
                        setDrawIcons(false)
                        axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
                        shadowColorSameAsCandle = true // Glow wick styling
                        shadowWidth = 1.2f
                        decreasingColor = ContextCompat.getColor(this@TradeDetailActivity, R.color.error_red)
                        decreasingPaintStyle = android.graphics.Paint.Style.FILL
                        increasingColor = ContextCompat.getColor(this@TradeDetailActivity, R.color.accent_green)
                        increasingPaintStyle = android.graphics.Paint.Style.FILL
                        neutralColor = ContextCompat.getColor(this@TradeDetailActivity, R.color.text_secondary)
                        setDrawValues(false)
                    }

                    candleChart.data = com.github.mikephil.charting.data.CandleData(dataSet)
                    candleChart.invalidate()
                }
            } catch (e: Exception) {
                candleChart.clear()
            }
        }
    }

    private fun performTrade(isBuy: Boolean) {
        val api = TokenManager.getApiService(this)
        if (api == null) {
            Toast.makeText(this, "Giriş yapmanız gerekiyor.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }

        val amountText = etAmount.text.toString()
        val amount = amountText.toDoubleOrNull() ?: 0.0

        if (amount <= 0) {
            Toast.makeText(this, "Geçerli bir miktar girin.", Toast.LENGTH_SHORT).show()
            return
        }

        val action = if (isBuy) "Al" else "Sat"
        val isCrypto = assetType == "crypto"
        val shortSymbol = if (isCrypto) symbol.removeSuffix("USDT") else symbol

        lifecycleScope.launch {
            try {
                val body = TradeRequest(symbol = shortSymbol, amount = amount, asset_type = assetType)
                val response = if (isBuy) api.buyCrypto(body) else api.sellCrypto(body)

                if (response.isSuccessful) {
                    Toast.makeText(this@TradeDetailActivity, "Sanal $action İşlemi Başarıyla Gerçekleşti!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    val msg = if (errorBody.contains("Bakiye yetersiz")) "Yetersiz TL bakiye!" else if (errorBody.contains("Yetersiz bakiye")) "Portföyünüzde yeterli adet yok!" else "Hata: ${response.code()}"
                    Toast.makeText(this@TradeDetailActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TradeDetailActivity, "Bağlantı hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
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
