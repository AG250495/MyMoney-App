package com.favicode.mymoney

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView

/**
 * Adaptador del desplegable de categorías: cada opción muestra su ícono y color.
 * Internamente trabaja con los nombres (String), para que el campo muestre solo el nombre al elegir.
 */
class CategoryDropdownAdapter(
    context: Context,
    private val categories: List<Category>
) : ArrayAdapter<String>(
    context,
    R.layout.item_dropdown_category,
    R.id.tvDropdownName,
    categories.map { it.name }
) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val category = categories.firstOrNull { it.name == getItem(position) }

        val icon = view.findViewById<ImageView>(R.id.ivDropdownIcon)
        val color = Color.parseColor(category?.colorHex ?: "#78909C")
        icon.setImageResource(CategoryIcons.resId(category?.iconKey ?: "other"))
        icon.imageTintList = ColorStateList.valueOf(color)
        icon.backgroundTintList = ColorStateList.valueOf(withAlpha(color, 0x33))
        return view
    }
}