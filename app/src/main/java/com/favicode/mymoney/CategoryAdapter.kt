package com.favicode.mymoney

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.favicode.mymoney.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onEditClick: (Category) -> Unit,
    private val onDeleteClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        val color = Color.parseColor(item.colorHex)

        b.tvCategoryName.text = item.name
        b.ivCategoryIcon.setImageResource(CategoryIcons.resId(item.iconKey))
        b.ivCategoryIcon.imageTintList = ColorStateList.valueOf(color)
        // Cuadrito de fondo con el mismo color, pero transparente
        b.ivCategoryIcon.backgroundTintList = ColorStateList.valueOf(withAlpha(color, 0x33))

        b.btnEditCategory.setOnClickListener { onEditClick(item) }
        b.btnDeleteCategory.setOnClickListener { onDeleteClick(item) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
            override fun areContentsTheSame(a: Category, b: Category) = a == b
        }
    }
}

/** Le pone transparencia (0-255) a un color. */
fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)