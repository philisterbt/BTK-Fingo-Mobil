package com.example.fingo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fingo.R
import com.example.fingo.network.TickerResponse
import java.text.NumberFormat
import java.util.Locale

class TickerAdapter(
    private val onItemClick: (TickerResponse) -> Unit
) : ListAdapter<TickerResponse, TickerAdapter.ViewHolder>(DIFF_CALLBACK) {

    // Lokal arama için tam liste tutulur (yapilanlar.md: lokal filtreleyin)
    private var fullList: List<TickerResponse> = emptyList()

    fun submitFullList(list: List<TickerResponse>) {
        fullList = list
        submitList(list)
    }

    fun filter(query: String) {
        if (query.isBlank()) {
            submitList(fullList)
        } else {
            val q = query.uppercase().trim()
            submitList(fullList.filter {
                it.symbol.contains(q) || it.symbol.removeSuffix("USDT").contains(q)
            })
        }
    }

    fun filterByCategory(category: String) {
        when (category) {
            "HEPSI" -> submitList(fullList)
            "KRIPTO" -> submitList(fullList.filter { it.symbol.endsWith("USDT") })
            "HISSELER" -> submitList(fullList.filter { !it.symbol.endsWith("USDT") })
            "POPULER" -> submitList(fullList.filter { it.symbol.startsWith("BTC") || it.symbol.startsWith("ETH") || it.symbol.startsWith("AAPL") || it.symbol.contains(".IS") })
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSymbolIcon: TextView = view.findViewById(R.id.tvSymbolIcon)
        val tvSymbol: TextView     = view.findViewById(R.id.tvSymbol)
        val tvPairName: TextView   = view.findViewById(R.id.tvPairName)
        val tvPrice: TextView      = view.findViewById(R.id.tvPrice)
        val tvChangePercent: TextView = view.findViewById(R.id.tvChangePercent)
        val tvVolume: TextView     = view.findViewById(R.id.tvVolume)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ticker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ticker = getItem(position)
        val ctx = holder.itemView.context

        // Symbol kısa adı (BTCUSDT → BTC)
        val shortSymbol = ticker.symbol.removeSuffix("USDT")
        holder.tvSymbol.text = shortSymbol
        holder.tvPairName.text = ticker.symbol

        // İkon (ilk 1-2 karakter)
        holder.tvSymbolIcon.text = shortSymbol.take(2)

        // Fiyat formatla
        val price = ticker.lastPriceDouble()
        holder.tvPrice.text = if (price > 1) {
            "$${NumberFormat.getNumberInstance(Locale.US).format(price)}"
        } else {
            "$$price"
        }

        // Değişim yüzdesi
        val change = ticker.changePercent()
        val changeText = if (change >= 0) "+${String.format("%.2f", change)}%" else "${String.format("%.2f", change)}%"
        holder.tvChangePercent.text = changeText

        val greenColor = ContextCompat.getColor(ctx, R.color.accent_green)
        val redColor   = ContextCompat.getColor(ctx, R.color.error_red)
        holder.tvChangePercent.setTextColor(if (change >= 0) greenColor else redColor)
        holder.tvSymbolIcon.setTextColor(if (change >= 0) greenColor else redColor)

        // Hacim
        val vol = ticker.volume.toDoubleOrNull() ?: 0.0
        holder.tvVolume.text = "Vol: ${String.format("%.0f", vol)}"

        holder.itemView.setOnClickListener { onItemClick(ticker) }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TickerResponse>() {
            override fun areItemsTheSame(old: TickerResponse, new: TickerResponse) =
                old.symbol == new.symbol
            override fun areContentsTheSame(old: TickerResponse, new: TickerResponse) =
                old.lastPrice == new.lastPrice && old.priceChangePercent == new.priceChangePercent
        }
    }
}
