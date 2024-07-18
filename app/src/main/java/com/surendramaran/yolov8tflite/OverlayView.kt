package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.util.LinkedList
import kotlin.math.max
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import java.lang.Math.pow
import kotlin.math.pow
import kotlin.math.sqrt

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()

    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var errorTextPaint = Paint()
    private var dotPaint = Paint()
    private var grayPaint = Paint()
    private var polyPaint = Paint()
    private var linePaint = Paint()
    private var boardLinePaint = Paint()
    private var bitmapPaint = Paint()

    private var bounds = Rect()
    private var status : Int = 0
    private var error = String()
    private var error1 = String()
    private var error2 = String()

    private var boardRect = RectF()
    private var clipBoardRect = RectF()
    private var bitmap: Bitmap? = null

    public var dotVisible = false
    public var clipVisible = true
    public var dotArray = ArrayList<Point>()

    private val path: Path = Path()

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

    fun clipVisible() {
        clipVisible = !clipVisible
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
        errorTextPaint.textSize = 30f

        linePaint.color = Color.YELLOW//Color.rgb(103, 80, 164)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 5F
        linePaint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)

        boardLinePaint.color = Color.rgb(103, 80, 164)
        boardLinePaint.style = Paint.Style.STROKE
        boardLinePaint.strokeWidth = 5F

        dotPaint.color = Color.rgb(103, 80, 164)
        dotPaint.style = Paint.Style.FILL

        grayPaint.color = Color.argb(180, 255, 255, 255)
        grayPaint.style = Paint.Style.FILL

        polyPaint.color = Color.argb(128,255, 255, 255)
        polyPaint.style = Paint.Style.FILL_AND_STROKE
        polyPaint.strokeWidth = 5F

        bitmapPaint.color = Color.RED
        bitmapPaint.style = Paint.Style.FILL_AND_STROKE
        bitmapPaint.strokeWidth = 5F

    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(dotVisible == false) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                setError2("Pos: "+ event.x.toString() + ", " + event.y.toString())
                invalidate()
                handleTouch(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouch(event.x, event.y)
            }
        }
        return true
    }

    fun handleTouch(x: Float, y: Float) {
        var mousePos = Point(x.toInt(), y.toInt())
        var index = 0
        var dis = 999F
        for(i in 0 until 4) {
            if(getDistance(mousePos, dotArray[i]) < dis) {
                dis = getDistance(mousePos, dotArray[i])
                index = i
            }
        }

        dotArray[index] = mousePos
    }

    fun getDistance(pt1: Point, pt2: Point): Float {
        val x = pt2.x - pt1.x.toDouble()
        val y = pt2.y - pt1.y.toDouble()
        val distance = sqrt(pow(x, 2.0) + pow(y, 2.0))
        return distance.toFloat()
    }
    fun initDots() {
        val sx = boardRect.left.toInt()
        val sy = boardRect.top.toInt()
        val ex = boardRect.right.toInt()
        val ey = boardRect.bottom.toInt()
        dotArray.clear()
        dotArray.apply {
            add(Point(sx, sy))
            add(Point(ex, sy))
            add(Point(ex, ey))
            add(Point(sx, ey))
        }
    }
    fun showDots() {
        dotVisible = true
        invalidate()
    }

    fun hideDots() {
        dotVisible = false
        invalidate()
    }

    fun drawGrid(canvas: Canvas, t: Int) {
        val tRect = RectF(
            clipBoardRect.left / t,
            clipBoardRect.top / t,
            clipBoardRect.right / t,
            clipBoardRect.bottom / t
        )

        var dx = tRect.width() / 8F
        var dy = tRect.height() / 8F
        var sx = tRect.left
        var sy = tRect.top

        for(i in 1 until 8)
            canvas.drawLine(sx, sy + dy * i ,tRect.right, sy + dy * i , linePaint)
        for(i in 1 until 8)
            canvas.drawLine(sx + dx * i, sy ,sx + dx * i , tRect.bottom, linePaint)

        canvas.drawRect(tRect, linePaint)
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        val paint = Paint()

        if(dotArray.count() > 3)
            drawPolygon(canvas, dotArray)

        status = Constants.SHOW_ERROR

        if(dotVisible) {

            for(i in 0 until 4) {
                canvas.drawCircle(dotArray[i].x.toFloat(), dotArray[i].y.toFloat(), 20F, dotPaint)
            }

            clipOutsidePolygon(canvas, dotArray)

            var msg = ""
            for(i in 0 until 4) {
                msg += dotArray[i].toString() + " "
            }
        }

        if(clipVisible) {
            if(bitmap != null && dotVisible == false)
                synchronized(this) {
                    val displayMetrics = context.resources.displayMetrics
                    var screenDensity = displayMetrics.density
                    screenDensity = 1F

                    bitmap?.let {
                        //canvas.drawBitmap(it, 0F, 0F, null)
                        //canvas.drawBitmap(it, clipBoardRect.width().toFloat() / screenDensity,  clipBoardRect.height().toFloat() / screenDensity, null)
                        canvas.drawRect(0F, 0F, clipBoardRect.width().toFloat() / screenDensity + 10, clipBoardRect.height().toFloat() / screenDensity + 10, bitmapPaint)
                        canvas.drawBitmap(it, null, RectF(0F, 0F, width.toFloat() / screenDensity , height.toFloat() / screenDensity) , null)

                        //canvas.drawBitmap(it, null, RectF(0F, 0F, width.toFloat() , height.toFloat() ) , null)
                    }

                    drawGrid(canvas, screenDensity.toInt())
                }
        }

        //if(status != Constants.Success) {
        val left = 100 // left coordinate
        val top = 70 // top coordinate

        var errormsg = ""
        when {
            status == Constants.NO_PIECE  -> errormsg = "No Piece"
            status == Constants.NO_BOARD -> errormsg = "No ChessBaord"
            status == Constants.MULTI_BOARD -> errormsg = "Multiple Boards(" + error + ")"
            status == Constants.SHOW_ERROR ->  errormsg = error
            status == Constants.Success -> errormsg = error
        }
        canvas.drawText("e1: " + errormsg, left.toFloat(), top.toFloat(), errorTextPaint)
        canvas.drawText("e2: " + error1, left.toFloat(), top.toFloat() + 40F, errorTextPaint)
        canvas.drawText("e3: " + error2, left.toFloat(), top.toFloat()+ 80F, errorTextPaint)

        if (status != Constants.Success && status != Constants.SHOW_ERROR)
            return
        //}

        results.forEach {
            var t = 1
            if(clipVisible && bitmap != null && dotVisible == false) {
                val displayMetrics = context.resources.displayMetrics
                var screenDensity = displayMetrics.density
                screenDensity = 1F

                t = screenDensity.toInt()
            }
            val left = it.x1 * width / t
            val top = it.y1 * height / t
            val right = it.x2 * width / t
            val bottom = it.y2 * height / t

            boxPaint.color = colors[it.clsName] ?: Color.BLACK
            textBackgroundPaint.color = colors[it.clsName] ?: Color.BLACK

            canvas.drawRect(left, top, right, bottom, boxPaint)
            val drawableText = it.clsName

            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
//            canvas.drawRect(
//                left,
//                top,
//                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
//                top + textHeight + BOUNDING_RECT_TEXT_PADDING,
//                textBackgroundPaint
//            )
//            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
        }

    }

    private fun drawPolygon(canvas: Canvas, vertices: List<Point>) {
        path.reset()
        path.moveTo(vertices[0].x.toFloat(), vertices[0].y.toFloat())

        for (i in 1 until vertices.size) {
            path.lineTo(vertices[i].x.toFloat(), vertices[i].y.toFloat())
        }

        path.close()

        canvas.drawPath(path, boardLinePaint)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun clipOutsidePolygon(canvas: Canvas, vertices: List<Point>) {
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // Create a path for the polygon
        val polygonPath = Path().apply {
            moveTo(vertices[0].x.toFloat(), vertices[0].y.toFloat())
            for (i in 1 until vertices.size) {
                lineTo(vertices[i].x.toFloat(), vertices[i].y.toFloat())
            }
            close()
        }

        canvas.clipOutPath(polygonPath)

        canvas.drawRect(rect, grayPaint)

        canvas.clipRect(rect)
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

    fun setError1(msg: String) {
        error1 = msg
    }

    fun setError2(msg: String) {
        error2 = msg
    }
    fun getStatus(): Int {
        return status
    }

    fun setBitmap(map: Bitmap) {
        bitmap = map
    }
    fun getView(): List<Int> {
        val res = mutableListOf<Int>()
        res.add(width)
        res.add(height)
        return res
    }

    fun setBoard(rect: RectF) {
        boardRect = rect
    }

    fun setClipBoard(rect: RectF) {
        clipBoardRect = rect
    }
    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}