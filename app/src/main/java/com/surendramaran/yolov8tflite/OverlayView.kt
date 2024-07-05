package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.util.LinkedList
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var errorTextPaint = Paint()
    private var bounds = Rect()
    private var status : Int = 0
    private var error = String()

    private var colors = mapOf(
        Pair("Black-Bishop", Color.parseColor("#EB3324")),
        Pair("White-Bishop", Color.parseColor("#EB3324")),
        Pair("Black-King", Color.parseColor("#0023F5")),
        Pair("White-King", Color.parseColor("#0023F5")),
        Pair("Black-Knight", Color.parseColor("#EA3FF7")),
        Pair("White-Knight", Color.parseColor("#EA3FF7")),
        Pair("Black-Queen", Color.parseColor("#75F94D")),
        Pair("White-Queen", Color.parseColor("#75F94D")),
        Pair("Black-Rook", Color.parseColor("#F9E11B")),
        Pair("White-Rook", Color.parseColor("#F9E11B")),
        Pair("Black-Pawn", Color.parseColor("#0DF8F9")),
        Pair("White-Pawn", Color.parseColor("#0DF8F9")),
        Pair("chessboard", Color.parseColor("#FF0000"))
    )

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 30F

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 30F

        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 4F
        boxPaint.style = Paint.Style.STROKE

        errorTextPaint.color = Color.RED
        errorTextPaint.style = Paint.Style.FILL
        errorTextPaint.textSize = 40f

    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if(status != Constants.Success) {
            val left = 100 // left coordinate
            val top = 100 // top coordinate
            val right = 200 // right coordinate (width)
            val bottom = 200 // bottom coordinate (height)

            var errormsg = ""
            when {
                status == Constants.NO_PIECE  -> errormsg = "No Piece"
                status == Constants.NO_BOARD -> errormsg = "No ChessBaord"
                status == Constants.MULTI_BOARD -> errormsg = "Multiple Boards(" + error + ")"
                status == Constants.SHOW_ERROR ->  errormsg = error
            }
            canvas.drawText(errormsg, left.toFloat(), top.toFloat(), errorTextPaint)

            if (status != Constants.MULTI_BOARD)
                return
        }

        results.forEach {
            val left = it.x1 * width
            val top = it.y1 * height
            val right = it.x2 * width
            val bottom = it.y2 * height

            boxPaint.color = colors[it.clsName] ?: Color.BLACK
            textBackgroundPaint.color = colors[it.clsName] ?: Color.BLACK

            canvas.drawRect(left, top, right, bottom, boxPaint)
            val drawableText = it.clsName

            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                left,
                top,
                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)

        }
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    fun setStatus(value: Int) {
        status = value
    }

    fun setError(msg: String) {
        error = msg
    }
    fun getStatus(): Int {
        return status
    }


    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}