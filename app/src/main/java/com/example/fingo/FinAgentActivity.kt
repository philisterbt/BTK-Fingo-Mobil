package com.example.fingo

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fingo.network.ApiClient
import com.example.fingo.network.AIAnalysisResponse
import com.example.fingo.network.NewsFeedItem
import com.example.fingo.network.RebalanceResponse
import com.example.fingo.network.ApiService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinAgentActivity : AppCompatActivity() {

    private lateinit var tvAgentStatus: TextView
    private lateinit var tvProfileEmoji: TextView
    private lateinit var tvProfileTitle: TextView
    private lateinit var tvPatienceScore: TextView
    private lateinit var pbPatience: ProgressBar
    private lateinit var tvRiskScore: TextView
    private lateinit var pbRisk: ProgressBar
    private lateinit var tvPanicScore: TextView
    private lateinit var pbPanic: ProgressBar
    private lateinit var tvAiAdvice: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var btnStartAnalysis: MaterialButton
    
    // Yeni eklenen AI Rebalancing ve News Feed View elemanları
    private lateinit var btnRebalance: MaterialButton
    private lateinit var cardNewsFeed: LinearLayout
    private lateinit var containerNewsFeed: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_primary)
        window.decorView.systemUiVisibility = 0
        setContentView(R.layout.activity_finagent)

        // Bind Views
        tvAgentStatus = findViewById(R.id.tvAgentStatus)
        tvProfileEmoji = findViewById(R.id.tvProfileEmoji)
        tvProfileTitle = findViewById(R.id.tvProfileTitle)
        tvPatienceScore = findViewById(R.id.tvPatienceScore)
        pbPatience = findViewById(R.id.pbPatience)
        tvRiskScore = findViewById(R.id.tvRiskScore)
        pbRisk = findViewById(R.id.pbRisk)
        tvPanicScore = findViewById(R.id.tvPanicScore)
        pbPanic = findViewById(R.id.pbPanic)
        tvAiAdvice = findViewById(R.id.tvAiAdvice)
        tvLastUpdated = findViewById(R.id.tvLastUpdated)
        btnStartAnalysis = findViewById(R.id.btnStartAnalysis)
        
        btnRebalance = findViewById(R.id.btnRebalance)
        cardNewsFeed = findViewById(R.id.cardNewsFeed)
        containerNewsFeed = findViewById(R.id.containerNewsFeed)

        setupNavigation()
        setupButtons()
    }

    private fun setupNavigation() {
        val navPiyasa  = findViewById<LinearLayout>(R.id.navPiyasa)
        val navCuzdan  = findViewById<LinearLayout>(R.id.navCuzdan)
        val navAkademi = findViewById<LinearLayout>(R.id.navAkademi)

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
        navAkademi.setOnClickListener {
            startActivity(Intent(this, AcademyActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun setupButtons() {
        btnStartAnalysis.setOnClickListener {
            startAiAnalysis()
        }
        
        btnRebalance.setOnClickListener {
            showRebalanceConfirmationDialog()
        }
    }

    private fun startAiAnalysis() {
        val api = com.example.fingo.network.TokenManager.getApiService(this)

        if (api == null) {
            Toast.makeText(this, "Lütfen önce giriş yapın!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                // UI Yükleniyor Durumu
                btnStartAnalysis.isEnabled = false
                btnStartAnalysis.text = "Analiz Ediliyor..."
                tvAgentStatus.text = "İşlem Geçmişi İnceleniyor..."

                // 1.5 Saniye gerçekçi analiz hissi için bekleme ekleyelim
                delay(1500)

                val response = api.getAIAnalysis()

                if (response.isSuccessful && response.body() != null) {
                    bindAnalysisData(response.body()!!)
                    fetchPersonalizedNews(api)
                } else {
                    runLocalFallbackAnalysis()
                }
            } catch (e: Exception) {
                runLocalFallbackAnalysis()
            } finally {
                btnStartAnalysis.isEnabled = true
                btnStartAnalysis.text = "✨  AI ile Analizi Yeniden Başlat"
            }
        }
    }

    private fun fetchPersonalizedNews(api: ApiService) {
        lifecycleScope.launch {
            try {
                val response = api.getPersonalizedNews()
                if (response.isSuccessful && response.body() != null) {
                    displayNewsFeed(response.body()!!)
                } else {
                    displayMockNewsFeed()
                }
            } catch (e: Exception) {
                displayMockNewsFeed()
            }
        }
    }

    private fun displayNewsFeed(newsList: List<NewsFeedItem>) {
        containerNewsFeed.removeAllViews()
        cardNewsFeed.visibility = View.VISIBLE

        val positiveColor = ContextCompat.getColor(this, R.color.accent_green)
        val positiveBg = Color.parseColor("#1500E676")
        
        val negativeColor = ContextCompat.getColor(this, R.color.error_red)
        val negativeBg = Color.parseColor("#15FF5252")
        
        val neutralColor = ContextCompat.getColor(this, R.color.text_secondary)
        val neutralBg = Color.parseColor("#158B949E")

        for (item in newsList) {
            val itemView = layoutInflater.inflate(R.layout.item_ai_news, containerNewsFeed, false)
            
            val tvSymbol = itemView.findViewById<TextView>(R.id.tvNewsSymbol)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvNewsTitle)
            val tvSummary = itemView.findViewById<TextView>(R.id.tvNewsSummary)
            val tvSentiment = itemView.findViewById<TextView>(R.id.tvNewsSentiment)
            val tvSource = itemView.findViewById<TextView>(R.id.tvNewsSource)

            tvSymbol.text = item.symbol
            tvTitle.text = item.title
            tvSummary.text = item.summary
            tvSource.text = "✍️ ${item.source}"

            // Sentiment styling
            when (item.sentiment.lowercase()) {
                "positive" -> {
                    tvSentiment.text = "POZİTİF"
                    tvSentiment.setTextColor(positiveColor)
                    tvSentiment.backgroundTintList = ColorStateList.valueOf(positiveBg)
                }
                "negative" -> {
                    tvSentiment.text = "NEGATİF"
                    tvSentiment.setTextColor(negativeColor)
                    tvSentiment.backgroundTintList = ColorStateList.valueOf(negativeBg)
                }
                else -> {
                    tvSentiment.text = "NÖTR"
                    tvSentiment.setTextColor(neutralColor)
                    tvSentiment.backgroundTintList = ColorStateList.valueOf(neutralBg)
                }
            }

            containerNewsFeed.addView(itemView)
        }
    }

    private fun displayMockNewsFeed() {
        val mockNews = listOf(
            NewsFeedItem(
                symbol = "BTC",
                title = "Kripto Paralarda Volatilite Dengeleniyor",
                summary = "Fed'in faiz açıklamaları sonrasında Bitcoin ve altcoin piyasalarında hacimli alımlar gözleniyor. AI, portföyündeki kripto riskini orta vadede korumanı öneriyor.",
                sentiment = "positive",
                source = "Fingo AI Analist"
            ),
            NewsFeedItem(
                symbol = "EREGL.IS",
                title = "Ereğli Demir Çelik Tesislerinde Kapasite Artışı",
                summary = "Çelik fiyatlarındaki küresel toparlanma ve tesis modernizasyon yatırımları, şirketin uzun vadeli temettü potansiyelini güçlendiriyor.",
                sentiment = "positive",
                source = "BIST AI Digest"
            )
        )
        displayNewsFeed(mockNews)
    }

    private fun showRebalanceConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚖️ Portföy Rebalance Edilsin mi?")
            .setMessage("AI Tavsiyelerine uyularak portföyünüzdeki yüksek riskli kripto varlıkların %20'si satılacak ve elde edilen sanal bakiye ile defansif Ereğli Demir Çelik (EREGL.IS) hissesi alınacaktır. Devam etmek istiyor musunuz?")
            .setPositiveButton("Evet, Dengele") { dialog, _ ->
                dialog.dismiss()
                executePortfolioRebalance()
            }
            .setNegativeButton("İptal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun executePortfolioRebalance() {
        val api = com.example.fingo.network.TokenManager.getApiService(this)

        if (api == null) {
            Toast.makeText(this, "Lütfen önce giriş yapın!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                btnRebalance.isEnabled = false
                btnRebalance.text = "Portföy Dengeleniyor..."

                val response = api.performRebalance()

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    
                    // Başarılı rebalance diyaloğu göster
                    AlertDialog.Builder(this@FinAgentActivity)
                        .setTitle("🎉 Portföy Dengelendi!")
                        .setMessage("${data.message}\n\nSatılan Varlıklar:\n${data.sold_assets.joinToString("\n")}\n\nAlınan Varlıklar:\n${data.bought_assets.joinToString("\n")}")
                        .setPositiveButton("Harika") { dialog, _ ->
                            dialog.dismiss()
                            // Rebalance sonrası AI analizini otomatik olarak tetikle ki barlar yeşile dönsün!
                            startAiAnalysis()
                        }
                        .show()
                } else {
                    runLocalFallbackRebalance()
                }
            } catch (e: Exception) {
                runLocalFallbackRebalance()
            } finally {
                btnRebalance.isEnabled = true
                btnRebalance.text = "⚖️  Portföyü AI ile Yeniden Dengele"
            }
        }
    }

    private fun runLocalFallbackRebalance() {
        AlertDialog.Builder(this)
            .setTitle("🎉 Portföy Dengelendi! (Simüle)")
            .setMessage("Portföy başarıyla rebalance edildi. Riskli varlıklar azaltılarak defansif varlıklar eklendi.\n\nSatılan Varlıklar:\n- BTC (0.01 adet)\n\nAlınan Varlıklar:\n- EREGL.IS (240.25 adet)")
            .setPositiveButton("Harika") { dialog, _ ->
                dialog.dismiss()
                // Simülasyonda da kullanıcının risk iştahını düşürüp sabır puanını artıralım ki farkı görsün!
                lifecycleScope.launch {
                    btnStartAnalysis.isEnabled = false
                    btnStartAnalysis.text = "Analiz Ediliyor..."
                    tvAgentStatus.text = "İşlem Geçmişi İnceleniyor..."
                    delay(1200)
                    val mockBalancedData = AIAnalysisResponse(
                        profile_title = "Dengeli ve Kararlı Stratejist",
                        profile_description = "Yatırımcı Psikolojisi Profili",
                        patience_score = 75,
                        risk_score = 42,
                        panic_sell_probability = 25,
                        ai_advice = "Portföyün başarıyla dengelendi! Kripto varlık ağırlığını düşürüp temettü hissesi ekleyerek riskini dağıttın. Sabır puanın arttı, uzun vadede kazanmaya devam et."
                    )
                    bindAnalysisData(mockBalancedData)
                    btnStartAnalysis.isEnabled = true
                    btnStartAnalysis.text = "✨  AI ile Analizi Yeniden Başlat"
                }
            }
            .show()
    }

    private fun bindAnalysisData(data: AIAnalysisResponse) {
        tvAgentStatus.text = "Analiz Tamamlandı"
        tvProfileTitle.text = data.profile_title
        tvAiAdvice.text = "\"${data.ai_advice}\""

        // Profil başlığına göre dino/emoji seçimi
        if (data.risk_score >= 70) {
            tvProfileEmoji.text = "🦖"
            // Risk yüksekse rebalance butonu görünür olsun!
            btnRebalance.visibility = View.VISIBLE
        } else {
            tvProfileEmoji.text = "🐢"
            btnRebalance.visibility = View.GONE
        }

        // Skor metinlerini güncelle
        tvPatienceScore.text = "${data.patience_score}/100"
        tvRiskScore.text = "${data.risk_score}/100"
        tvPanicScore.text = "${data.panic_sell_probability}%"

        // Progress barları animasyonlu doldur
        animateProgressBar(pbPatience, data.patience_score)
        animateProgressBar(pbRisk, data.risk_score)
        animateProgressBar(pbPanic, data.panic_sell_probability)

        // Güncellenme zamanı
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        tvLastUpdated.text = "Son analiz: Bugün saat $currentTime itibarıyla güncellendi."
    }

    private fun runLocalFallbackAnalysis() {
        val randomPatience = (30..80).random()
        val randomRisk = (40..90).random()
        val randomPanic = (20..75).random()

        val mockData = AIAnalysisResponse(
            profile_title = if (randomRisk >= 70) "Sen bir 'FOMO Canavarı' Eğilimindesin!" else "Dengeli ve Kararlı Stratejist",
            profile_description = "Yatırımcı Psikolojisi Profili",
            patience_score = randomPatience,
            risk_score = randomRisk,
            panic_sell_probability = randomPanic,
            ai_advice = if (randomRisk >= 70) {
                "Son işlemlerinde yüksek volatiliteye sahip varlıklara (BTC, SOL) yoğunlaştığın görülüyor. Portföy risk profilini dengelemek için en az %20 oranında Altın Fonu veya BIST30 temettü hissesi eklemeni öneririz."
            } else {
                "Harika bir disipline sahipsin! Uzun vadeli hedeflerine sadık kalıyorsun. Portföyündeki hisse senedi ve kripto dengesini korumaya devam et."
            }
        )
        bindAnalysisData(mockData)
        displayMockNewsFeed()
        Toast.makeText(this, "AI Analiz Motoru Bağlantısı Simüle Edildi!", Toast.LENGTH_SHORT).show()
    }

    private fun animateProgressBar(progressBar: ProgressBar, targetProgress: Int) {
        val animator = ObjectAnimator.ofInt(progressBar, "progress", 0, targetProgress)
        animator.duration = 1000
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }
}
