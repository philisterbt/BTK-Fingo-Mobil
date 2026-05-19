package com.example.fingo

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fingo.network.ApiService
import com.example.fingo.network.ChartChallengeItem
import com.example.fingo.network.ChallengeSubmitRequest
import com.example.fingo.network.ChallengeSubmitResponse
import com.example.fingo.network.TermExplainResponse
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class AcademyActivity : AppCompatActivity() {

    // AI Finans Sözlüğü
    private lateinit var etSearchTerm: EditText
    private lateinit var btnSearchTerm: ImageView
    private lateinit var layoutExplanationResult: LinearLayout
    private lateinit var tvExplainedTerm: TextView
    private lateinit var tvTermExplanation: TextView

    // Grafik Simülatör Elemanları
    private lateinit var tvChallengeSymbolBadge: TextView
    private lateinit var tvChallengeTitle: TextView
    private lateinit var tvChallengeDescription: TextView
    private lateinit var chartView: LineChartView
    private lateinit var btnPredictUp: LinearLayout
    private lateinit var btnPredictDown: LinearLayout
    private lateinit var layoutChallengeResult: LinearLayout
    private lateinit var tvChallengeResultTitle: TextView
    private lateinit var tvChallengeResultText: TextView
    private lateinit var btnNextChallenge: MaterialButton

    // Simülatör Durumu
    private var challengeList: List<ChartChallengeItem> = emptyList()
    private var currentChallengeIndex = 0
    private var isChallengeAnswered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_primary)
        window.decorView.systemUiVisibility = 0
        setContentView(R.layout.activity_academy)

        bindViews()
        setupNavigation()
        setupButtons()
        
        // Verileri Yükle
        loadAcademyData()
    }

    private fun bindViews() {
        // AI Sözlük
        etSearchTerm = findViewById(R.id.etSearchTerm)
        btnSearchTerm = findViewById(R.id.btnSearchTerm)
        layoutExplanationResult = findViewById(R.id.layoutExplanationResult)
        tvExplainedTerm = findViewById(R.id.tvExplainedTerm)
        tvTermExplanation = findViewById(R.id.tvTermExplanation)

        // Grafik Simülatörü
        tvChallengeSymbolBadge = findViewById(R.id.tvChallengeSymbolBadge)
        tvChallengeTitle = findViewById(R.id.tvChallengeTitle)
        tvChallengeDescription = findViewById(R.id.tvChallengeDescription)
        chartView = findViewById(R.id.chartView)
        btnPredictUp = findViewById(R.id.btnPredictUp)
        btnPredictDown = findViewById(R.id.btnPredictDown)
        layoutChallengeResult = findViewById(R.id.layoutChallengeResult)
        tvChallengeResultTitle = findViewById(R.id.tvChallengeResultTitle)
        tvChallengeResultText = findViewById(R.id.tvChallengeResultText)
        btnNextChallenge = findViewById(R.id.btnNextChallenge)
    }

    private fun setupNavigation() {
        val navPiyasa  = findViewById<LinearLayout>(R.id.navPiyasa)
        val navCuzdan  = findViewById<LinearLayout>(R.id.navCuzdan)
        val navFinAgent = findViewById<LinearLayout>(R.id.navFinAgent)

        navPiyasa.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        navCuzdan.setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        navFinAgent.setOnClickListener {
            startActivity(Intent(this, FinAgentActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun setupButtons() {
        // AI Sözlük Araması
        btnSearchTerm.setOnClickListener { performTermSearch() }
        etSearchTerm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performTermSearch()
                true
            } else {
                false
            }
        }


        // Tahmin Butonları
        btnPredictUp.setOnClickListener { onPredictionSubmitted("UP") }
        btnPredictDown.setOnClickListener { onPredictionSubmitted("DOWN") }

        // Sonraki Soru Butonu
        btnNextChallenge.setOnClickListener {
            if (!isChallengeAnswered) {
                Toast.makeText(this, "Lütfen sonraki grafiğe geçmeden önce tahmininizi yapın!", Toast.LENGTH_SHORT).show()
            } else {
                showNextChallenge()
            }
        }

        // Başucu Kaynakları Click Listeners
        findViewById<LinearLayout>(R.id.btnResourceSPK)?.setOnClickListener {
            openUrl("https://finansalokuryazarlik.gov.tr/")
        }
        findViewById<LinearLayout>(R.id.btnResourceBIST)?.setOnClickListener {
            openUrl("https://www.borsaistanbul.com/tr/sayfa/31/yatirimci-rehberi")
        }
        findViewById<LinearLayout>(R.id.btnResourceKhan)?.setOnClickListener {
            openUrl("https://tr.khanacademy.org/economics-finance-domain/core-finance")
        }
        findViewById<LinearLayout>(R.id.btnResourceBook)?.setOnClickListener {
            openUrl("https://www.goodreads.com/book/show/1052.The_Richest_Man_in_Babylon")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tarayıcı açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAcademyData() {
        val api = com.example.fingo.network.TokenManager.getApiService(this) ?: return

        lifecycleScope.launch {
            try {
                // Grafik Arenası Verilerini Yükle
                val challengesRes = api.getChartChallenges()
                if (challengesRes.isSuccessful && challengesRes.body() != null) {
                    challengeList = challengesRes.body()!!
                    if (challengeList.isNotEmpty()) {
                        currentChallengeIndex = 0
                        bindChallenge(challengeList[0])
                    } else {
                        loadMockChallenges()
                    }
                } else {
                    loadMockChallenges()
                }
            } catch (e: Exception) {
                loadMockChallenges()
            }
        }
    }

    private fun bindChallenge(challenge: ChartChallengeItem) {
        isChallengeAnswered = false
        tvChallengeSymbolBadge.text = challenge.symbol
        tvChallengeTitle.text = challenge.title
        tvChallengeDescription.text = challenge.description
        
        // Grafiği Çiz
        chartView.setData(challenge.visible_prices)

        // Tahmin butonlarını sıfırla
        resetPredictButtons()

        // Sonuç panelini gizle
        layoutChallengeResult.visibility = View.GONE
    }

    private fun onPredictionSubmitted(prediction: String) {
        if (isChallengeAnswered) return
        isChallengeAnswered = true

        // Butonları inaktif yap
        btnPredictUp.isClickable = false
        btnPredictDown.isClickable = false

        val challenge = challengeList.getOrNull(currentChallengeIndex) ?: return
        val api = com.example.fingo.network.TokenManager.getApiService(this)

        if (api == null) {
            simulateChallengeSubmit(challenge, prediction)
            return
        }

        // Tıklanan butonu seçili yap
        highlightSelectedPrediction(prediction)

        lifecycleScope.launch {
            try {
                val req = ChallengeSubmitRequest(challenge_id = challenge.id, user_prediction = prediction)
                val response = api.submitChartChallenge(req)
                if (response.isSuccessful && response.body() != null) {
                    val res = response.body()!!
                    renderChallengeResult(res, prediction)
                } else {
                    simulateChallengeSubmit(challenge, prediction)
                }
            } catch (e: Exception) {
                simulateChallengeSubmit(challenge, prediction)
            }
        }
    }

    private fun renderChallengeResult(res: ChallengeSubmitResponse, prediction: String) {
        // Gelecek Fiyatları Ekrana Çizdir
        chartView.revealFuture(res.future_prices, res.is_correct)

        // Sonuç Panelini Doldur
        layoutChallengeResult.visibility = View.VISIBLE
        if (res.is_correct) {
            tvChallengeResultTitle.text = "🎉 TEBRİKLER! DOĞRU TAHMİN\n(${res.explanation_title})"
            tvChallengeResultTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            Toast.makeText(this, "🎉 Harika! Formasyonu doğru tahmin ettin! +25 XP", Toast.LENGTH_SHORT).show()
        } else {
            tvChallengeResultTitle.text = "❌ MAALESEF YANLIŞ TAHMİN\n(${res.explanation_title})"
            tvChallengeResultTitle.setTextColor(ContextCompat.getColor(this, R.color.error_red))
            Toast.makeText(this, "❌ Yanlış Tahmin! Grafikten ders çıkarıp öğrenelim.", Toast.LENGTH_SHORT).show()
        }
        tvChallengeResultText.text = res.explanation_text
    }

    private fun simulateChallengeSubmit(challenge: ChartChallengeItem, prediction: String) {
        highlightSelectedPrediction(prediction)
        
        val isCorrect = prediction.uppercase() == challenge.correct_direction.uppercase()
        val mockRes = ChallengeSubmitResponse(
            is_correct = isCorrect,
            explanation_title = challenge.explanation_title,
            explanation_text = challenge.explanation_text,
            future_prices = challenge.future_prices
        )
        renderChallengeResult(mockRes, prediction)
    }

    private fun showNextChallenge() {
        if (challengeList.isEmpty()) return
        currentChallengeIndex = (currentChallengeIndex + 1) % challengeList.size
        bindChallenge(challengeList[currentChallengeIndex])
    }

    private fun highlightSelectedPrediction(prediction: String) {
        if (prediction == "UP") {
            btnPredictUp.background = ContextCompat.getDrawable(this, R.drawable.bg_option_selected)
            btnPredictDown.alpha = 0.4f
        } else {
            btnPredictDown.background = ContextCompat.getDrawable(this, R.drawable.bg_option_selected)
            btnPredictUp.alpha = 0.4f
        }
    }

    private fun resetPredictButtons() {
        val defBg = ContextCompat.getDrawable(this, R.drawable.bg_option)
        btnPredictUp.background = defBg
        btnPredictDown.background = defBg
        btnPredictUp.alpha = 1.0f
        btnPredictDown.alpha = 1.0f
        btnPredictUp.isClickable = true
        btnPredictDown.isClickable = true
    }

    private fun loadMockChallenges() {
        challengeList = listOf(
            ChartChallengeItem(
                id = "challenge_btc_double_top",
                symbol = "BTC",
                title = "Bitcoin 60,000$ Psikolojik Direnci",
                description = "Bitcoin 60,000$ psikolojik sınırını ikinci kez test ediyor. RSI göstergesi aşırı alım bölgesinde yorulma sinyalleri veriyor. Bir sonraki hamlede fiyat ne yöne gidecek?",
                visible_prices = listOf(57500.0, 58200.0, 59100.0, 57800.0, 59500.0, 60100.0, 59800.0, 60300.0, 59400.0, 59900.0),
                future_prices = listOf(58100.0, 56200.0, 54000.0, 51500.0, 48200.0),
                correct_direction = "DOWN",
                explanation_title = "Çift Tepe (Double Top) Formasyonu",
                explanation_text = "Fiyat 60.000$ psikolojik direncini iki kez aşamadı ve güçlü bir 'Çift Tepe' formasyonu oluşturdu. RSI'daki negatif uyumsuzlukla birleşince güçlü bir satış baskısı geldi ve fiyat 48.000$'a kadar düştü. Burada satıp nakitte kalmak en doğru stratejiydi."
            ),
            ChartChallengeItem(
                id = "challenge_eth_cup_handle",
                symbol = "ETH",
                title = "Ethereum Çanak Kulp Formasyonu",
                description = "Ethereum büyük bir düşüşün ardından toparlanarak kulp aşamasına geçti. İşlem hacmi kırılımdan hemen önce daralıyor. Fiyat ne yöne patlayacak?",
                visible_prices = listOf(2200.0, 2400.0, 2600.0, 2500.0, 2300.0, 2400.0, 2480.0, 2430.0, 2460.0, 2490.0),
                future_prices = listOf(2650.0, 2800.0, 3100.0, 3350.0, 3600.0),
                correct_direction = "UP",
                explanation_title = "Çanak Kulp (Cup & Handle) Formasyonu",
                explanation_text = "Çanak kulp, yükseliş trendinin devam edeceğine işaret eden çok güçlü bir boğa formasyonudur. Kulp direncini (2500$) aşan Ethereum, arkasına aldığı hacimle 3600$'a kadar ralli yaptı."
            )
        )
        if (challengeList.isNotEmpty()) {
            currentChallengeIndex = 0
            bindChallenge(challengeList[0])
        }
    }

    private fun performTermSearch() {
        val term = etSearchTerm.text.toString().trim()
        if (term.isEmpty()) return

        val api = com.example.fingo.network.TokenManager.getApiService(this)

        // UI Arama yükleniyor durumu
        layoutExplanationResult.visibility = View.VISIBLE
        tvExplainedTerm.text = "🔍 ${term.uppercase()} kelimesi aranıyor..."
        tvTermExplanation.text = "AI benzetmesi oluşturuluyor..."

        if (api == null) {
            runLocalTermSearch(term)
            return
        }

        lifecycleScope.launch {
            try {
                val response = api.explainTerm(term)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    tvExplainedTerm.text = "💡 ${data.term.uppercase()}:"
                    tvTermExplanation.text = data.explanation
                } else {
                    runLocalTermSearch(term)
                }
            } catch (e: Exception) {
                runLocalTermSearch(term)
            }
        }
    }

    private fun runLocalTermSearch(term: String) {
        val termClean = term.lowercase()
        val explanation = when {
            "arbitraj" in termClean -> "Düşün ki A bakkalında çikolata 5 TL, karşıdaki B bakkalında ise 8 TL. Sen A bakkalından çikolatayı alıp hemen B bakkalının önünde 7 TL'ye satarak zahmetsizce aradaki farktan para kazanıyorsun. İşte finans dünyasında buna arbitraj denir!"
            "hedging" in termClean || "riskten korunma" in termClean -> "Dışarı çıkarken havanın yağmurlu olabileceğini düşünüp yanına şemsiye almak gibidir. Yatırım yaparken de ters giden bir duruma karşı kendini güvenceye almak için zıt yönlü bir koruma işlemi yaparsın."
            "kaldıraç" in termClean || "leverage" in termClean -> "10 TL paran varken, bir arkadaşından 90 TL borç alıp 100 TL'lik bir oyuncak almaktır. Eğer oyuncak değerlenirse çok kazanırsın, ama değeri ufacık düşerse kendi paranı tamamen kaybedersin!"
            "volatilite" in termClean || "oynaklık" in termClean -> "Lunaparktaki hız treni gibidir. Fiyatların bir saniyede yukarı fırlayıp, diğer saniyede hızla aşağı düşmesi durumudur. Kripto paralar yüksek volatiliteye sahiptir."
            else -> "Bu terim yatırımların değer kazanması veya korunması için kullanılan önemli bir kavramdır. Tıpkı bir çiftçinin ekinlerini korumak için çit örmesi veya doğru zamanda doğru tohumu ekmesi gibi finansal dengeyi sağlamayı amaçlar."
        }
        tvExplainedTerm.text = "💡 ${term.uppercase()} (Simüle):"
        tvTermExplanation.text = explanation
    }
}
