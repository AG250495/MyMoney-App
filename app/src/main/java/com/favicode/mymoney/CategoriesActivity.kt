package com.favicode.mymoney

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.favicode.mymoney.databinding.ActivityCategoriesBinding
import com.favicode.mymoney.databinding.DialogCategoryBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class CategoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var repository: BudgetRepository
    private lateinit var adapter: CategoryAdapter

    private var currentType = Transaction.Type.EXPENSE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "invitado"
        repository = BudgetRepository(this, userId)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = CategoryAdapter(
            onEditClick = { showCategoryDialog(it) },
            onDeleteClick = { confirmDelete(it) }
        )
        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = adapter

        binding.btnCatExpense.setOnClickListener { selectType(Transaction.Type.EXPENSE) }
        binding.btnCatIncome.setOnClickListener { selectType(Transaction.Type.INCOME) }
        binding.btnAddCategory.setOnClickListener { showCategoryDialog(null) }

        selectType(Transaction.Type.EXPENSE)
    }

    // ---------- Lista ----------

    private fun selectType(type: Transaction.Type) {
        currentType = type
        styleTypeButton(binding.btnCatExpense, type == Transaction.Type.EXPENSE, R.color.expense_red, R.color.text_primary)
        styleTypeButton(binding.btnCatIncome, type == Transaction.Type.INCOME, R.color.income_green, R.color.bg_dark)
        loadCategories()
    }

    private fun styleTypeButton(button: MaterialButton, active: Boolean, activeColor: Int, activeTextColor: Int) {
        if (active) {
            button.setBackgroundColor(ContextCompat.getColor(this, activeColor))
            button.setTextColor(ContextCompat.getColor(this, activeTextColor))
        } else {
            button.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
            button.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun loadCategories() {
        val list = repository.getCategories(currentType)
        adapter.submitList(list)
        binding.tvEmptyCategories.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------- Eliminar ----------

    private fun confirmDelete(category: Category) {
        AlertDialog.Builder(this)
            .setTitle(R.string.budget_delete_title)
            .setMessage(getString(R.string.categories_delete_message, category.name))
            .setPositiveButton(R.string.budget_delete_confirm) { _, _ ->
                repository.deleteCategory(category.id)
                loadCategories()
            }
            .setNegativeButton(R.string.budget_cancel, null)
            .show()
    }

    // ---------- Crear / Editar ----------

    /** Si [existing] es null crea una categoría nueva; si no, edita esa. */
    private fun showCategoryDialog(existing: Category?) {
        val d = DialogCategoryBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(d.root).create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog)

        var selectedIcon = existing?.iconKey ?: CategoryIcons.icons.keys.first()
        var selectedColor = existing?.colorHex ?: CategoryIcons.palette.first()

        d.tvDialogTitle.setText(if (existing == null) R.string.categories_new else R.string.categories_edit)
        d.etCatName.setText(existing?.name ?: "")

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        fun renderIcons() {
            d.gridIcons.removeAllViews()
            val color = Color.parseColor(selectedColor)
            CategoryIcons.icons.forEach { (key, resId) ->
                val cell = ImageView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = dp(46); height = dp(46)
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    }
                    setPadding(dp(11), dp(11), dp(11), dp(11))
                    setImageResource(resId)
                    if (key == selectedIcon) {
                        setBackgroundResource(R.drawable.bg_icon_square)
                        backgroundTintList = ColorStateList.valueOf(withAlpha(color, 0x40))
                        imageTintList = ColorStateList.valueOf(color)
                    } else {
                        imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_muted))
                    }
                    setOnClickListener {
                        selectedIcon = key
                        renderIcons()
                    }
                }
                d.gridIcons.addView(cell)
            }
        }

        fun renderColors() {
            d.gridColors.removeAllViews()
            CategoryIcons.palette.forEach { hex ->
                val dot = ImageView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = dp(36); height = dp(36)
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                    setBackgroundResource(R.drawable.bg_color_dot)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(hex))
                    if (hex == selectedColor) {
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        setImageResource(R.drawable.ic_check)
                        imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.bg_dark))
                    }
                    setOnClickListener {
                        selectedColor = hex
                        renderColors()
                        renderIcons() // el ícono elegido toma el nuevo color
                    }
                }
                d.gridColors.addView(dot)
            }
        }

        renderIcons()
        renderColors()

        d.btnCatCancel.setOnClickListener { dialog.dismiss() }
        d.btnCatSave.setOnClickListener {
            val name = d.etCatName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                d.tilCatName.error = getString(R.string.categories_error_name)
                return@setOnClickListener
            }

            val saved = if (existing == null) {
                repository.addCategory(name, currentType, selectedIcon, selectedColor)
            } else {
                repository.updateCategory(existing.id, name, selectedIcon, selectedColor)
            }

            if (!saved) {
                d.tilCatName.error = getString(R.string.categories_error_duplicate)
                return@setOnClickListener
            }
            dialog.dismiss()
            loadCategories()
        }

        dialog.show()
    }
}