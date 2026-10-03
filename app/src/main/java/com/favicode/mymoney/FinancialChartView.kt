package com.favicode.mymoney

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Vista personalizada de gráfica financiera interactiva que muestra
 * la comparativa de Ingresos vs Gastos por periodo.
 */
class FinancialChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class BarData(
        val label: String,
        val income: Float,
        val expense: Float
    )

    private var chartData: List<BarData> = listOf(
        BarData("Ene", 4500f, 2100f),
        BarData("Feb", 5200f, 2800f),
        BarData("Mar", 4800f, 3100f),
        BarData("Abr", 6100f, 2400f),
        BarData("May", 5800f, 2900f),
        BarData("Jun", 6500f, 2250f)
    )

    private var selectedIndex: Int = 5 // Por defecto junio seleccionado

    // Visual Paints
    private val incomePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.income_green)
        style = Paint.Style.FILL
    }

    private val expensePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.expense_red)
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider_dark)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.card_dark_secondary)
        style = Paint.Style.FILL
    }

    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_teal)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    private val selectionHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.card_dark_secondary)
        style = Paint.Style.FILL
    }

    var onBarSelectedListener: ((BarData) -> Unit)? = null

    fun setChartData(data: List<BarData>) {
        this.chartData = data
        this.selectedIndex = if (data.isNotEmpty()) data.size - 1 else -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chartData.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()

        val paddingLeft = 40f
        val paddingRight = 40f
        val paddingTop = 100f
        val paddingBottom = 70f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        // Encontrar valor máximo para escala
        val maxVal = (chartData.flatMap { listOf(it.income, it.expense) }.maxOrNull() ?: 1000f) * 1.15f

        // Dibujar líneas de cuadrícula horizontal
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = paddingTop + (chartHeight / gridLines) * i
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
        }

        val count = chartData.size
        val groupWidth = chartWidth / count
        val barWidth = (groupWidth * 0.30f).coerceAtMost(36f)
        val barSpacing = 8f

        for (i in 0 until count) {
            val item = chartData[i]
            val groupX = paddingLeft + i * groupWidth + groupWidth / 2f

            // Destacar barra seleccionada
            if (i == selectedIndex) {
                val highlightRect = RectF(
                    paddingLeft + i * groupWidth + 8f,
                    paddingTop - 20f,
                    paddingLeft + (i + 1) * groupWidth - 8f,
                    height - paddingBottom + 10f
                )
                canvas.drawRoundRect(highlightRect, 16f, 16f, selectionHighlightPaint)
            }

            // Calcular alturas
            val incomeHeight = (item.income / maxVal) * chartHeight
            val expenseHeight = (item.expense / maxVal) * chartHeight

            val incomeLeft = groupX - barWidth - (barSpacing / 2f)
            val incomeRight = groupX - (barSpacing / 2f)
            val incomeTop = height - paddingBottom - incomeHeight
            val incomeBottom = height - paddingBottom

            val expenseLeft = groupX + (barSpacing / 2f)
            val expenseRight = groupX + barWidth + (barSpacing / 2f)
            val expenseTop = height - paddingBottom - expenseHeight
            val expenseBottom = height - paddingBottom

            // Dibujar barras con esquinas redondeadas superiores
            val incomeRect = RectF(incomeLeft, incomeTop, incomeRight, incomeBottom)
            canvas.drawRoundRect(incomeRect, 8f, 8f, incomePaint)

            val expenseRect = RectF(expenseLeft, expenseTop, expenseRight, expenseBottom)
            canvas.drawRoundRect(expenseRect, 8f, 8f, expensePaint)

            // Dibujar etiqueta de periodo (X-Axis)
            labelPaint.color = if (i == selectedIndex)
                ContextCompat.getColor(context, R.color.accent_teal)
            else
                ContextCompat.getColor(context, R.color.text_secondary)

            canvas.drawText(item.label, groupX, height - 20f, labelPaint)
        }

        // Dibujar tooltip de información seleccionada
        if (selectedIndex in chartData.indices) {
            val selected = chartData[selectedIndex]
            val groupX = paddingLeft + selectedIndex * groupWidth + groupWidth / 2f
            val tooltipText = "${selected.label}: Ing S/ ${selected.income.toInt()} | Gas S/ ${selected.expense.toInt()}"

            val tooltipWidth = tooltipTextPaint.measureText(tooltipText) + 40f
            val tooltipHeight = 60f

            // Posicionar tooltip asegurando que no salga del Canvas
            var tooltipX = groupX
            if (tooltipX - tooltipWidth / 2f < paddingLeft) {
                tooltipX = paddingLeft + tooltipWidth / 2f
            } else if (tooltipX + tooltipWidth / 2f > width - paddingRight) {
                tooltipX = width - paddingRight - tooltipWidth / 2f
            }

            val tooltipY = 45f
            val tooltipRect = RectF(
                tooltipX - tooltipWidth / 2f,
                tooltipY - tooltipHeight / 2f,
                tooltipX + tooltipWidth / 2f,
                tooltipY + tooltipHeight / 2f
            )

            canvas.drawRoundRect(tooltipRect, 16f, 16f, tooltipBgPaint)
            canvas.drawRoundRect(tooltipRect, 16f, 16f, tooltipBorderPaint)
            canvas.drawText(tooltipText, tooltipX, tooltipY + 10f, tooltipTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val width = width.toFloat()
            val paddingLeft = 40f
            val paddingRight = 40f
            val chartWidth = width - paddingLeft - paddingRight

            if (chartData.isNotEmpty()) {
                val groupWidth = chartWidth / chartData.size
                val touchX = event.x - paddingLeft
                val index = (touchX / groupWidth).toInt().coerceIn(0, chartData.size - 1)

                if (index != selectedIndex) {
                    selectedIndex = index
                    invalidate()
                    onBarSelectedListener?.invoke(chartData[index])
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
