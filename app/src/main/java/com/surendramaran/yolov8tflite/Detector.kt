package com.surendramaran.yolov8tflite

//import org.opencv.highgui.HighGui
//import org.opencv.core.Point2f
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.surendramaran.yolov8tflite.Constants.BOARD_PATH
import com.surendramaran.yolov8tflite.Constants.MULTI_BOARD
import com.surendramaran.yolov8tflite.Constants.NO_BOARD
import com.surendramaran.yolov8tflite.Constants.NO_PIECE
import com.surendramaran.yolov8tflite.Constants.SHOW_ERROR
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point


class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {

    private var interpreter: Interpreter? = null
    private var board_interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private var board_tensorWidth = 0
    private var board_tensorHeight = 0
    private var board_numChannel = 0
    private var board_numElements = 0

    private var boardRect : RectF = RectF(0F, 0F, 0F, 0F)
    private var clipBoardRect : RectF = RectF(0F, 0F, 0F, 0F)

    private var character = mapOf(
        Pair("Black-Bishop", 'b'),
        Pair("White-Bishop", 'B'),
        Pair("Black-King", 'k'),
        Pair("White-King", 'K'),
        Pair("Black-Knight", 'n'),
        Pair("White-Knight", 'N'),
        Pair("Black-Queen", 'q'),
        Pair("White-Queen", 'Q'),
        Pair("Black-Rook", 'r'),
        Pair("White-Rook", 'R'),
        Pair("Black-Pawn", 'p'),
        Pair("White-Pawn", 'P')
    )

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    fun board_setup() {
        val model = FileUtil.loadMappedFile(context, BOARD_PATH)
        val options = Interpreter.Options()
        options.numThreads = 4
        board_interpreter = Interpreter(model, options)

        val inputShape = board_interpreter?.getInputTensor(0)?.shape() ?: return
        val outputShape = board_interpreter?.getOutputTensor(0)?.shape() ?: return

        board_tensorWidth = inputShape[1]
        board_tensorHeight = inputShape[2]
        board_numChannel = outputShape[1]
        board_numElements = outputShape[2]
    }
    fun setup() {
        board_setup()

        System.loadLibrary("opencv_java4")
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options()
        options.numThreads = 4
        interpreter = Interpreter(model, options)

        val inputShape = interpreter?.getInputTensor(0)?.shape() ?: return
        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return

        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]
        numChannel = outputShape[1]
        numElements = outputShape[2]

        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String? = reader.readLine()
            while (line != null && line != "") {
                labels.add(line)
                line = reader.readLine()
            }

            reader.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    fun org.opencv.core.Point.distanceFrom(srcPoint: org.opencv.core.Point):
            Double {
        val w1 = this.x - srcPoint.x
        val h1 = this.y - srcPoint.y
        val distance = w1.pow(2) + h1.pow(2)
        return sqrt(distance)
    }

//    fun Bitmap.perspectiveTransform(srcPoints: List<org.opencv.core.Point>) :
//            Bitmap{
//        val dstWidth = max(
//            srcPoints[0].distanceFrom(srcPoints[1]),
//            srcPoints[2].distanceFrom(srcPoints[3])
//        )
//        val dstHeight = max(
//            srcPoints[0].distanceFrom(srcPoints[2]),
//            srcPoints[1].distanceFrom(srcPoints[3])
//        )
//
//        val dstPoints: List<org.opencv.core.Point> = listOf(
//            org.opencv.core.Point(0.0, 0.0),
//            org.opencv.core.Point(dstWidth, 0.0),
//            org.opencv.core.Point(0.0, dstHeight),
//            org.opencv.core.Point(dstWidth, dstHeight)
//        )
//        return try {
//            val srcMat = Converters.vector_Point2d_to_Mat(srcPoints)
//            val dstMat = Converters.vector_Point2d_to_Mat(dstPoints)
//            val perspectiveTransformation =
//                Imgproc.getPerspectiveTransform(srcMat, dstMat)
//            val inputMat = Mat(this.height, this.width, CvType.CV_8UC1)
//            Utils.bitmapToMat(this, inputMat)
//            val outPutMat = Mat(dstHeight.toInt(), dstWidth.toInt(), CvType.CV_8UC1)
//            Imgproc.warpPerspective(
//                inputMat,
//                outPutMat,
//                perspectiveTransformation,
//                Size(dstWidth, dstHeight)
//            )
//            val outPut = Bitmap.createBitmap(
//                dstWidth.toInt(),
//                dstHeight.toInt(), Bitmap.Config.RGB_565
//            )
//            //Imgproc.cvtColor(outPutMat , outPutMat , Imgproc.COLOR_GRAY2BGR)
//            Utils.matToBitmap(outPutMat , outPut)
//            outPut
//        }
//        catch ( e : Exception){
//            e.printStackTrace()
//            this
//        }
//    }

    fun detect(frame: Bitmap, binding: ActivityMainBinding, view : List<Int>) {
        interpreter ?: return
        board_interpreter ?: return
        if (tensorWidth == 0) return
        if (tensorHeight == 0) return
        if (numChannel == 0) return
        if (numElements == 0) return

        if (board_tensorWidth == 0) return
        if (board_tensorHeight == 0) return
        if (board_numChannel == 0) return
        if (board_numElements == 0) return

        var inferenceTime = SystemClock.uptimeMillis()

        // ============Board Detect================
        var resizedBitmap = Bitmap.createScaledBitmap(frame, board_tensorWidth, board_tensorHeight, false)

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)
        var processedImage = imageProcessor.process(tensorImage)
        var imageBuffer = processedImage.buffer

        var output = TensorBuffer.createFixedSize(intArrayOf(1 , board_numChannel, board_numElements), OUTPUT_IMAGE_TYPE)
        board_interpreter?.run(imageBuffer, output.buffer)

        var board_bestBoxes = bestBox(output.floatArray, 2, view, binding)
        if(board_bestBoxes == null) {
            detectorListener.onEmptyDetect()
            return
        }

        var cnt = board_bestBoxes.count()
        if(cnt >= 2) {
            binding.overlay.setStatus(MULTI_BOARD)
            binding.overlay.setError(cnt.toString())
            detectorListener.onEmptyDetect()
            return
        }

        var box = board_bestBoxes.first()
        boardRect.left = box.x1 * view.first()
        boardRect.top = box.y1 * view.last()
        boardRect.right = box.x2 * view.first()
        boardRect.bottom = box.y2 * view.last()

        binding.overlay.setBoard(boardRect)

        val srcPoints = listOf(
            org.opencv.core.Point(100.0, 0.0),  // Top-left
            org.opencv.core.Point(boardRect.width().toDouble() - 100.0, 0.0),  // Top-right
            org.opencv.core.Point(0.0, boardRect.height().toDouble()),  // Bottom-left
            org.opencv.core.Point(boardRect.width().toDouble(), boardRect.height().toDouble())   // Bottom-right
        )

        //var res = frame.perspectiveTransform(srcPoints)
        //binding.overlay.setBitmap(res)

        //=============Perspective Transform=============
        //204 412 895 1005

        val displayMetrics = context.resources.displayMetrics
        val screenDensity = displayMetrics.density
        binding.overlay.setError(screenDensity.toString())
        var  croppedBitmap = frame

        if(binding.overlay.dotVisible == false && binding.overlay.dotArray.count() > 3) {
            val displayMetrics = context.resources.displayMetrics
            val screenDensity = displayMetrics.density

            var x = boardRect.left.toDouble() / screenDensity
            var y = boardRect.top.toDouble() / screenDensity
            var w = boardRect.width().toDouble() / screenDensity
            var h = boardRect.height().toDouble() /screenDensity

            var dotArray = binding.overlay.dotArray
            val ptsArray = dotArray.map { pt -> org.opencv.core.Point(pt.x.toDouble() / screenDensity, pt.y.toDouble() / screenDensity)}.toTypedArray()
            ptsArray[1].x += w / 20
//            ptsArray[2].x += 20.0
//            ptsArray[2].y + 20.0
//            ptsArray[3].x -= 20.0
//            ptsArray[3].y + 20.0
            val pts1 = MatOfPoint2f(*ptsArray)

            val pts2 = MatOfPoint2f(
                Point(x, y),
                Point(x + w , y),
                Point(x + w , y + h),
                Point(x, y + h)
            )

            val M = Imgproc.getPerspectiveTransform(pts1, pts2)
            val dst = Mat()

            val srcMat = Mat()
            Utils.bitmapToMat(frame, srcMat)

            val fw = frame.width * screenDensity.toDouble()
            val fh = frame.height * screenDensity.toDouble()
            Imgproc.warpPerspective(srcMat, dst, M, Size(fw, fh))

            val dx = w / 10
            val dy = h / 10
            val scaledX = x + dx //* screenDensity
            val scaledY = y + dy//* screenDensity
            val scaledWidth = w * screenDensity - dx
            val scaledHeight = h * screenDensity - dy

            val roi = Rect(scaledX.toInt(), scaledY.toInt(), scaledWidth.toInt(), scaledHeight.toInt())

            val croppedMat = Mat(dst, roi)

            binding.overlay.setStatus(SHOW_ERROR)
            binding.overlay.setError(croppedMat.width().toString() + " " + croppedMat.height().toString())
            binding.overlay.setError1(roi.toString())

            croppedBitmap = Bitmap.createBitmap(croppedMat.width(), croppedMat.height(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(croppedMat, croppedBitmap)

            //val croppedBitmap = Bitmap.createBitmap(dst.width(), dst.height(), Bitmap.Config.ARGB_8888)
            //Utils.matToBitmap(dst, croppedBitmap)

            binding.overlay.setBitmap(croppedBitmap)
            //Utils.matToBitmap(dst, croppedBitmap)
            //var  outputBitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)

            //===========Clip Board Detect===============
            resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, board_tensorWidth, board_tensorHeight, false)

            tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(resizedBitmap)
            processedImage = imageProcessor.process(tensorImage)
            imageBuffer = processedImage.buffer

            output = TensorBuffer.createFixedSize(intArrayOf(1 , board_numChannel, board_numElements), OUTPUT_IMAGE_TYPE)
            board_interpreter?.run(imageBuffer, output.buffer)

            var board_bestBoxes = bestBox(output.floatArray, 2, view, binding)

            var box = board_bestBoxes!!.first()
            clipBoardRect.left = box.x1 * view.first()
            clipBoardRect.top = box.y1 * view.last()
            clipBoardRect.right = box.x2 * view.first()
            clipBoardRect.bottom = box.y2 * view.last()

            binding.overlay.setClipBoard(clipBoardRect)
        }

        //=============Piece Detect================

        resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, tensorWidth, tensorHeight, false)

        tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)
        processedImage = imageProcessor.process(tensorImage)
        imageBuffer = processedImage.buffer

        output = TensorBuffer.createFixedSize(intArrayOf(1 , numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter?.run(imageBuffer, output.buffer)

        val bestBoxes = bestBox(output.floatArray, 1, view, binding)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime


        if (bestBoxes == null) {
            binding.overlay.setStatus(NO_PIECE)
            detectorListener.onEmptyDetect()
            return
        }

        //detectorListener.onDetect(bestBoxes + board_bestBoxes!!, inferenceTime)
        detectorListener.onDetect(bestBoxes, inferenceTime)
    }


    private fun detectCount(array: FloatArray): Int {
        var cnt = 0
        for (c in 0 until board_numElements) {
            var maxConf = -1.0f
            var j = 4
            var arrayIdx = c + board_numElements * j
            while (j < board_numChannel){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                }
                j++
                arrayIdx += board_numElements
            }

            if (maxConf > BOARD_CONFIDENCE_THRESHOLD) {
                cnt ++
            }
        }
        return cnt
    }
    private fun bestBox(array: FloatArray, type: Int = 1, view : List<Int>, binding: ActivityMainBinding) : List<BoundingBox>? {

        val boundingBoxes = mutableListOf<BoundingBox>()

        val tmp_numElements = if( type == 1 ) numElements else board_numElements
        val tmp_numChannel = if( type == 1 ) numChannel else board_numChannel

        var vw = view.first()
        var vh = view.last()

        for (c in 0 until tmp_numElements) {
            var maxConf = -1.0f
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + tmp_numElements * j
            while (j < tmp_numChannel){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += tmp_numElements
            }

            var tmp = 0.0F
            if ( type == 1) tmp = CONFIDENCE_THRESHOLD else tmp = BOARD_CONFIDENCE_THRESHOLD

            if (maxConf > tmp) {
                var clsName = "chessboard"
                if(type == 1)
                    clsName = labels[maxIdx]
                val cx = array[c] // 0
                val cy = array[c + tmp_numElements] // 1
                val w = array[c + tmp_numElements * 2]
                val h = array[c + tmp_numElements * 3]
                val x1 = cx - (w/2F)
                var y1 = cy - (h/2F)
                val x2 = cx + (w/2F)
                val y2 = cy + (h/2F)
                if (x1 < 0F || x1 > 1F) continue
                if (y1 < 0F || y1 > 1F) continue
                if (x2 < 0F || x2 > 1F) continue
                if (y2 < 0F || y2 > 1F) continue

                if(type == 2) {
                    y1 -= (y2 - y1) / 20.0F
                }

                var tmpRect : RectF = RectF(x1 * vw, y1 * vh ,x2 * vw, y2 * vh)

                var bRect : RectF = if(binding.overlay.dotArray.count() > 3 && binding.overlay.dotVisible == false)  clipBoardRect else boardRect

                binding.overlay.setError1(boardRect.toString())
                binding.overlay.setError2(bRect.toString())

                if ( calculateOverlappingPercentage(tmpRect, bRect) > 60F || type == 2 )
                {
                    boundingBoxes.add(
                        BoundingBox(
                            x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                            cx = cx, cy = cy, w = w, h = h,
                            cnf = maxConf, cls = maxIdx, clsName = clsName
                        )
                    )
                }
            }
        }

        if (boundingBoxes.isEmpty()) return null

        var results = applyNMS(boundingBoxes)

        if(type == 2)
            return results

        var bRect : RectF = if(binding.overlay.dotArray.count() > 3 && binding.overlay.dotVisible == false)  clipBoardRect else boardRect

        val dx = bRect.width()  / 8F
        val dy = bRect.height() / 8F
        val sx = bRect.left
        val sy = bRect.top


        val matrix = Array(8) { CharArray(8) {'.'}}

        results.forEach {
            var pieceRect : RectF = RectF(it.x1*vw, it.y1*vh, it.x2*vw, it.y2*vh)
            var indexx = 0
            var indexy = 0
            var t = -1F
            var f = 0
            for(j in 8 downTo 0) {
                for (i in 0 until 8) {
                    var tx = sx + dx * i
                    var ty = sy + dy * j
                    var tmpRect: RectF = RectF(tx, ty, tx + dx, ty + dy)

                    if ((i == 0 || i == 7) && calculateOverlappingPercentage(pieceRect, tmpRect) > 20F) {
                        //indexx = j
                        //indexy = 7 - i
                        indexx = i
                        indexy = j
                        f = 1
                        break
                    }

                    if (calculateOverlappingPercentage(pieceRect, tmpRect) > t) {
                        t = calculateOverlappingPercentage(pieceRect, tmpRect)
                        //indexx = j
                        //indexy = 7 - i
                        indexx = i
                        indexy = j
                    }
                }

                if (f == 1) {
                    break
                }
            }

            matrix[indexy][indexx] = character[it.clsName]!!
        }

        val matrixAsString = matrix.joinToString("/") { row -> row.joinToString("") }

        binding.overlay.setError(matrixAsString)

        return results
    }

    private fun applyNMS(boxes: List<BoundingBox>) : MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while(sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    fun calculateIntersectionArea(rectA: RectF, rectB: RectF): Float {
        val overlapLeft = maxOf(rectA.left, rectB.left)
        val overlapTop = maxOf(rectA.top, rectB.top)
        val overlapRight = minOf(rectA.right, rectB.right)
        val overlapBottom = minOf(rectA.bottom, rectB.bottom)

        if (overlapLeft < overlapRight && overlapTop < overlapBottom) {
            val overlapWidth = overlapRight - overlapLeft
            val overlapHeight = overlapBottom - overlapTop
            return overlapWidth * overlapHeight
        }
        return 0f
    }

    fun calculateOverlappingPercentage(rectA: RectF, rectB: RectF): Float {
        val areaA = (rectA.right - rectA.left) * (rectA.bottom - rectA.top)
        val areaB = (rectB.right - rectB.left) * (rectB.bottom - rectB.top)

        val intersectionArea = calculateIntersectionArea(rectA, rectB)

        return (intersectionArea / minOf(areaA, areaB)) * 100
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.3F
        private const val BOARD_CONFIDENCE_THRESHOLD = 0.5F
        private const val IOU_THRESHOLD = 0.5F
    }
}