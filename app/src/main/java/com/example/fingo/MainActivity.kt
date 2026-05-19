package com.example.fingo

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fingo.adapter.TickerAdapter
import com.example.fingo.network.ApiClient
import com.example.fingo.network.TokenManager
import com.example.fingo.network.TradeRequest
import com.example.fingo.network.TickerResponse
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var rvTickers: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var etSearch: EditText
    private lateinit var tvBtcPrice: TextView
    private lateinit var btnSanalAl: MaterialButton
    private lateinit var btnSanalSat: MaterialButton
    private lateinit var tickerAdapter: TickerAdapter
    private lateinit var btcChart: com.github.mikephil.charting.charts.CandleStickChart
    
    private lateinit var tvFeaturedSymbol: TextView
    private lateinit var tvFeaturedName: TextView
    private lateinit var tvFeaturedIcon: TextView
    
    private var selectedTicker:TickerResponse? = null

    // Seçili miktar yüzdesi (25/50/75/100)
    private var selectedPercent = 1.0  // 100% varsayılan

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_primary)
        window.decorView.systemUiVisibility = 0
        setContentView(R.layout.activity_main)

        initViews()
        setupNavigation(activeNav = "piyasa")
        setupRecyclerView()
        setupSearch()
        setupChipFilters()
        setupPercentButtons()
        setupTradeButtons()
        setupChart()
        loadTickers()
        loadChartData("BTCUSDT")
        startAutoRefresh()
    }

    private fun initViews() {
        rvTickers   = findViewById(R.id.rvTickers)
        progressBar = findViewById(R.id.progressBar)
        etSearch    = findViewById(R.id.etSearch)
        tvBtcPrice  = findViewById(R.id.tvBtcPrice)
        btnSanalAl  = findViewById(R.id.btnSanalAl)
        btnSanalSat = findViewById(R.id.btnSanalSat)
        btcChart    = findViewById(R.id.btcChart)
        
        tvFeaturedSymbol = findViewById(R.id.tvFeaturedSymbol)
        tvFeaturedName   = findViewById(R.id.tvFeaturedName)
        tvFeaturedIcon   = findViewById(R.id.tvFeaturedIcon)
    }

    private fun setupRecyclerView() {
        tickerAdapter = TickerAdapter { ticker ->
            selectAsset(ticker)
        }
        rvTickers.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = tickerAdapter
        }
    }

    private fun setupChart() {
        btcChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            xAxis.isEnabled = false
            axisLeft.textColor = ContextCompat.getColor(context, R.color.text_secondary)
            axisLeft.setDrawGridLines(true)
            axisLeft.gridColor = ContextCompat.getColor(context, R.color.bg_card_elevated)
            axisRight.isEnabled = false
            isDoubleTapToZoomEnabled = false
            setNoDataText("Grafik Yükleniyor...")
            setNoDataTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
    }

    private fun setupSearch() {
        // yapilanlar.md: "Lokal filtreleyin, her harf için yeni istek atmayın."
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tickerAdapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun selectAsset(ticker: TickerResponse) {
        selectedTicker = ticker
        
        val isCrypto = ticker.symbol.endsWith("USDT")
        val shortSymbol = if (isCrypto) ticker.symbol.removeSuffix("USDT") else ticker.symbol
        
        tvFeaturedSymbol.text = ticker.symbol
        tvFeaturedName.text = if (isCrypto) getCryptoName(shortSymbol) else getStockName(shortSymbol)
        tvFeaturedIcon.text = if (isCrypto) "₿" else "📈"
        
        val price = ticker.lastPriceDouble()
        tvBtcPrice.text = if (price > 1) {
            "$${NumberFormat.getNumberInstance(Locale.US).format(price)}"
        } else {
            "$$price"
        }
        
        val change = ticker.changePercent()
        tvBtcPrice.setTextColor(
            ContextCompat.getColor(this, if (change >= 0) R.color.accent_green else R.color.error_red)
        )
        
        // Yeniden grafiği yükle
        loadChartData(ticker.symbol)
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

    /**
     * GET /api/v1/crypto/tickers — Auth gerekmez
     */
    private fun loadTickers() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val cryptoResponse = ApiClient.public.getTickers()
                val stockResponse = ApiClient.public.getStockTickers()
                val combined = mutableListOf<TickerResponse>()
                if (cryptoResponse.isSuccessful) cryptoResponse.body()?.let { combined.addAll(it) }
                if (stockResponse.isSuccessful) stockResponse.body()?.let { combined.addAll(it) }
                val response = if (cryptoResponse.isSuccessful || stockResponse.isSuccessful) {
                    retrofit2.Response.success(combined.toList())
                } else {
                    retrofit2.Response.error<List<TickerResponse>>(500, okhttp3.ResponseBody.create(null, ""))
                }
                if (response.isSuccessful) {
                    val tickers = response.body() ?: emptyList()
                    tickerAdapter.submitFullList(tickers)

                    // BTC fiyatını featured kart'a yaz
                    val btc = tickers.firstOrNull { it.symbol == "BTCUSDT" }
                    if (selectedTicker == null && btc != null) {
                        selectAsset(btc)
                    } else if (selectedTicker != null) {
                        // Seçili olan varlığın anlık fiyatını güncelle
                        tickers.firstOrNull { it.symbol == selectedTicker?.symbol }?.let {
                            selectAsset(it)
                        }
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Piyasa verisi alınamadı: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Bağlantı hatası. Tekrar denenecek...", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadChartData(symbol: String = "BTCUSDT") {
        lifecycleScope.launch {
            try {
                val isCrypto = symbol.endsWith("USDT")
                val response = if (isCrypto) {
                    ApiClient.public.getChart(symbol = symbol, interval = "1d", limit = 40)
                } else {
                    ApiClient.public.getStockChart(symbol = symbol, interval = "1d", limit = 40)
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
                        axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
                        shadowColor = ContextCompat.getColor(this@MainActivity, R.color.text_secondary)
                        shadowWidth = 1.0f
                        decreasingColor = ContextCompat.getColor(this@MainActivity, R.color.error_red)
                        decreasingPaintStyle = android.graphics.Paint.Style.FILL
                        increasingColor = ContextCompat.getColor(this@MainActivity, R.color.accent_green)
                        increasingPaintStyle = android.graphics.Paint.Style.FILL
                        neutralColor = ContextCompat.getColor(this@MainActivity, R.color.text_secondary)
                        setDrawValues(false)
                    }

                    btcChart.data = com.github.mikephil.charting.data.CandleData(dataSet)
                    btcChart.invalidate() // Grafiği çizdir
                }
            } catch (e: Exception) {
                btcChart.clear()
            }
        }
    }

    /** 30 saniyede bir tickers'ı yenile */
    private fun startAutoRefresh() {
        lifecycleScope.launch {
            while (true) {
                delay(30_000)
                loadTickers()
            }
        }
    }

    private fun setupChipFilters() {
        val chips = listOf(R.id.chipHepsi, R.id.chipKripto, R.id.chipHisseler, R.id.chipPopuler)
        chips.forEach { chipId ->
            findViewById<TextView>(chipId)?.setOnClickListener { view ->
                chips.forEach { id ->
                    val chip = findViewById<TextView>(id)
                    chip?.background = ContextCompat.getDrawable(this, R.drawable.bg_chip)
                    chip?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
                (view as? TextView)?.background = ContextCompat.getDrawable(this, R.drawable.bg_chip_active)
                (view as? TextView)?.setTextColor(ContextCompat.getColor(this, R.color.bg_primary))

                // Kripto filtrele (USDT çifti = kripto)
                when (chipId) {
                    R.id.chipHepsi    -> tickerAdapter.filterByCategory("HEPSI")
                    R.id.chipKripto   -> tickerAdapter.filterByCategory("KRIPTO")
                    R.id.chipHisseler -> tickerAdapter.filterByCategory("HISSELER")
                    R.id.chipPopuler  -> tickerAdapter.filterByCategory("POPULER")
                }
            }
        }
    }

    private fun setupPercentButtons() {
        val percentMap = mapOf(R.id.btn25 to 0.25, R.id.btn50 to 0.50, R.id.btn75 to 0.75, R.id.btn100 to 1.0)
        percentMap.forEach { (id, pct) ->
            findViewById<TextView>(id)?.setOnClickListener {
                selectedPercent = pct
                Toast.makeText(this, "%${(pct * 100).toInt()} seçildi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTradeButtons() {
        btnSanalAl.setOnClickListener {
            openTradeDetail()
        }
        btnSanalSat.setOnClickListener {
            openTradeDetail()
        }
    }

    private fun openTradeDetail() {
        val ticker = selectedTicker ?: return
        val isCrypto = ticker.symbol.endsWith("USDT")
        val assetType = if (isCrypto) "crypto" else "stock"

        val intent = Intent(this, TradeDetailActivity::class.java).apply {
            putExtra("symbol", ticker.symbol)
            putExtra("asset_type", assetType)
            putExtra("price", ticker.lastPriceDouble())
            putExtra("change", ticker.changePercent())
        }
        startActivity(intent)
    }

    private fun setupNavigation(activeNav: String) {
        val navCuzdan   = findViewById<LinearLayout>(R.id.navCuzdan)
        val navFinAgent = findViewById<LinearLayout>(R.id.navFinAgent)
        val navAkademi  = findViewById<LinearLayout>(R.id.navAkademi)

        navCuzdan.setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
            overridePendingTransition(0, 0)
        }
        navFinAgent.setOnClickListener {
            startActivity(Intent(this, FinAgentActivity::class.java))
            overridePendingTransition(0, 0)
        }
        navAkademi.setOnClickListener {
            startActivity(Intent(this, AcademyActivity::class.java))
            overridePendingTransition(0, 0)
        }
    }
}