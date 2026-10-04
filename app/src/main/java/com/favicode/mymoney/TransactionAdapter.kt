package com.favicode.mymoney

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.favicode.mymoney.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val categoryOf: (Transaction) -> Category?,
    private val onItemClick: (Transaction) -> Unit,
    private val onDeleteClick: (Transaction) -> Unit

) : ListAdapter<Transaction, TransactionAdapter.ViewHolder>(DIFF) {

    private val dateFormat = SimpleDateFormat("dd MMM", Locale("es", "PE"))

    inner class ViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        val context = b.root.context

        b.tvCategory.text = item.category
        b.tvDescription.text = item.description.ifBlank { context.getString(R.string.budget_no_description) }
        b.tvDate.text = dateFormat.format(Date(item.timestamp))

        // Ícono y color de la categoría. Si ya no existe (se eliminó), usa uno neutro.
        val category = categoryOf(item)
        val color = Color.parseColor(category?.colorHex ?: "#78909C")
        b.ivIcon.setImageResource(CategoryIcons.resId(category?.iconKey ?: "other"))
        b.ivIcon.imageTintList = ColorStateList.valueOf(color)
        b.ivIcon.backgroundTintList = ColorStateList.valueOf(withAlpha(color, 0x33))

        if (item.type == Transaction.Type.INCOME) {
            b.tvAmount.text = String.format(Locale.US, "+ S/ %,.2f", item.amount)
            b.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_green))
        } else {
            b.tvAmount.text = String.format(Locale.US, "- S/ %,.2f", item.amount)
            b.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_red))
        }

        b.btnDelete.setOnClickListener { onDeleteClick(item) }
        b.root.setOnClickListener { onItemClick(item) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Transaction>() {
            override fun areItemsTheSame(a: Transaction, b: Transaction) = a.id == b.id
            override fun areContentsTheSame(a: Transaction, b: Transaction) = a == b
        }
    }
}