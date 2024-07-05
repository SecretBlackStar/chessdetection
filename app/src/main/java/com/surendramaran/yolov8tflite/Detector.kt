package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import com.surendramaran.yolov8tflite.Constants.BOARD_PATH
import com.surendramaran.yolov8tflite.Constants.MULTI_BOARD
import com.surendramaran.yolov8tflite.Constants.NO_BOARD
import com.surendramaran.yolov8tflite.Constants.NO_PIECE
import com.surendramaran.yolov8tflite.Constants.SHOW_ERROR
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
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

    fun detect(frame: Bitmap, binding: ActivityMainBinding) {
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

        var board_bestBoxes = bestBox(output.floatArray, 2)
        if(board_bestBoxes == null) {
            binding.overlay.setStatus(NO_BOARD)
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

        //=============Piece Detect================
        frame
        resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)

        tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)
        processedImage = imageProcessor.process(tensorImage)
        imageBuffer = processedImage.buffer

        output = TensorBuffer.createFixedSize(intArrayOf(1 , numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter?.run(imageBuffer, output.buffer)

        val bestBoxes = bestBox(output.floatArray)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime


        if (bestBoxes == null) {
            binding.overlay.setStatus(NO_PIECE)
            detectorListener.onEmptyDetect()
            return
        }

        detectorListener.onDetect(bestBoxes + board_bestBoxes!!, inferenceTime)
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
    private fun bestBox(array: FloatArray, type: Int = 1) : List<BoundingBox>? {

        val boundingBoxes = mutableListOf<BoundingBox>()

        val tmp_numElements = if( type == 1 ) numElements else board_numElements
        val tmp_numChannel = if( type == 1 ) numChannel else board_numChannel

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

                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cx = cx, cy = cy, w = w, h = h,
                        cnf = maxConf, cls = maxIdx, clsName = clsName
                    )
                )
            }
        }

        if (boundingBoxes.isEmpty()) return null

        return applyNMS(boundingBoxes)
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