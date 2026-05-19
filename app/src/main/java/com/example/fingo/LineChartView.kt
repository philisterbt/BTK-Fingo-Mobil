package com.example.fingo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LineChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Çizim Paint Tanımlamaları
    private val wickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A3746") // Koyu ızgara rengi
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8") // Slate gray etiket rengi
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private var prices: List<Double> = emptyList()
    private var futurePrices: List<Double> = emptyList()
    private var showFuture = false

    fun setData(visible: List<Double>) {
        this.prices = visible
        this.showFuture = false
        invalidate()
    }

    fun revealFuture(future: List<Double>, isCorrect: Boolean) {
        this.futurePrices = future
        this.showFuture = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (prices.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()

        // Kenar Boşlukları (Y-Ekseni Etiketleri için Sol Tarafta Boşluk)
        val leftMargin = 120f
        val rightMargin = 20f
        val topMargin = 40f
        val bottomMargin = 40f

        val chartWidth = width - leftMargin - rightMargin
        val chartHeight = height - topMargin - bottomMargin

        // Tüm fiyat serisini birleştir (Min-Max bulmak için)
        val allPrices = if (showFuture) prices + futurePrices else prices
        
        // Mum Mum Fiyat Modelini Çıkar (Deterministic High, Low, Open, Close)
        val candles = calculateCandles(allPrices)
        
        val minPrice = candles.minOfOrNull { it.low } ?: 0.0
        val maxPrice = candles.maxOfOrNull { it.high } ?: 1.0
        val priceRange = if (maxPrice == minPrice) 1.0 else maxPrice - minPrice

        // 1. Izgara Çizgilerini ve Fiyat Etiketlerini Çiz
        val gridLinesCount = 5
        for (i in 0 until gridLinesCount) {
            val ratio = i.toFloat() / (gridLinesCount - 1)
            val y = topMargin + (1f - ratio) * chartHeight
            val priceVal = minPrice + ratio * priceRange

            // Grid Çizgisi
            canvas.drawLine(leftMargin, y, width - rightMargin, y, gridPaint)

            // Fiyat Etiketi (Sol hizalı)
            val label = String.format("%,.0f", priceVal)
            canvas.drawText(label, 15f, y + 10f, textPaint)
        }

        // 2. Mumları Çiz
        val totalCount = candles.size
        val candleSpacing = chartWidth / totalCount
        val bodyWidth = candleSpacing * 0.65f

        for (i in candles.indices) {
            val candle = candles[i]
            
            // Gelecek mumu mu yoksa geçmiş mumu mu?
            val isFutureCandle = i >= prices.size
            
            // X koordinatını bul
            val centerX = leftMargin + i * candleSpacing + (candleSpacing / 2f)

            // Y koordinatlarını bul (Ekrana eşle)
            val openY  = topMargin + (1f - ((candle.open - minPrice) / priceRange)).toFloat() * chartHeight
            val closeY = topMargin + (1f - ((candle.close - minPrice) / priceRange)).toFloat() * chartHeight
            val highY  = topMargin + (1f - ((candle.high - minPrice) / priceRange)).toFloat() * chartHeight
            val lowY   = topMargin + (1f - ((candle.low - minPrice) / priceRange)).toFloat() * chartHeight

            // Yön ve Renk Seçimi
            val isGreen = candle.close >= candle.open
            val colorStr = if (isGreen) "#00E676" else "#FF5252" // Canlı Yeşil veya Kırmızı
            val color = Color.parseColor(colorStr)

            // Gelecek mumları için hafif saydamlık veya kesikli çizgiler
            if (isFutureCandle) {
                wickPaint.color = color
                wickPaint.alpha = 180
                bodyPaint.color = color
                bodyPaint.alpha = 140
            } else {
                wickPaint.color = color
                wickPaint.alpha = 255
                bodyPaint.color = color
                bodyPaint.alpha = 255
            }

            // A. Fitili Çiz (Wick)
            canvas.drawLine(centerX, highY, centerX, lowY, wickPaint)

            // B. Gövdeyi Çiz (Body)
            val topBody = min(openY, closeY)
            val bottomBody = max(openY, closeY)
            
            // Eğer açılış ve kapanış birebir aynıysa en az 4px yükseklik ver ki görünsün
            val rectBottom = if (bottomBody - topBody < 4f) topBody + 4f else bottomBody

            canvas.drawRect(
                centerX - (bodyWidth / 2f),
                topBody,
                centerX + (bodyWidth / 2f),
                rectBottom,
                bodyPaint
            )
        }
    }

    /**
     * Tekil fiyat serisinden gerçekçi mum grafiği (open, close, high, low) türetir.
     * Bu sayede backend verilerini değiştirmeden tam uyumlu çalışır.
     */
    private fun calculateCandles(rawPrices: List<Double>): List<CandleModel> {
        val result = ArrayList<CandleModel>()
        for (i in rawPrices.indices) {
            val close = rawPrices[i]
            val open = if (i == 0) {
                close * 0.997 // İlk mum için hafif aşağıda başla
            } else {
                rawPrices[i - 1] // Bir önceki kapanış
            }

            val diff = abs(close - open)
            val factor = if (diff == 0.0) close * 0.0015 else diff * 0.45

            // Yüksek ve Alçak fiyatları gerçekçi ve tutarlı dalgalandır
            val high = max(open, close) + factor
            val low = min(open, close) - factor

            result.add(CandleModel(open, close, high, low))
        }
        return result
    }

    private data class CandleModel(
        val open: Double,
        val close: Double,
        val high: Double,
        val low: Double
    )
}
