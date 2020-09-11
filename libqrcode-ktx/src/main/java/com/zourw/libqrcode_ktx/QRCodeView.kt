package com.zourw.libqrcode_ktx

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.controls.Audio
import com.otaliastudios.cameraview.controls.Engine
import com.otaliastudios.cameraview.frame.Frame
import com.otaliastudios.cameraview.gesture.Gesture
import com.otaliastudios.cameraview.gesture.GestureAction
import com.otaliastudios.cameraview.size.Size
import java.util.*
import kotlin.math.min

/**
 * Created by Zourw on 2020/4/2.
 */
private const val DEF_COLOR = 0xFF00FF00.toInt()

class QRCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val maskPaint = Paint().apply {
        color = 0xBF04080D.toInt()
    }

    private var cornerBreadth = dp2px(context, 3f)
    private var cornerLength = dp2px(context, 25f)

    private val cornerPath = Path()
    private val cornerPaint = Paint().apply {
        color = DEF_COLOR
        style = Paint.Style.STROKE
        strokeWidth = cornerBreadth
    }

    val scanFrame = RectF()

    private val gridPath = Path()
    private var gridColor = DEF_COLOR
    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val gridMaskPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private var gridGradient: LinearGradient? = null
    private val gridMaskGradients = mutableListOf<LinearGradient>()

    private val gridMatrix = Matrix()

    private var gridRowCount = 30
    private var gridColumnCount = 30

    private var sizeRatio = 0.7f
    private var offsetXRatio = 0.5f
    private var offsetYRatio = 0.5f

    val cameraView = CameraView(context).apply {
        set(Engine.CAMERA1)
        playSounds = false
        audio = Audio.OFF
        mapGesture(Gesture.PINCH, GestureAction.ZOOM)
        mapGesture(Gesture.TAP, GestureAction.AUTO_FOCUS)
        addFrameProcessor { processFrame(it) }
    }

    private val multiFormatReader = MultiFormatReader().apply {
        val formats: MutableList<BarcodeFormat> = ArrayList()
        formats.add(BarcodeFormat.AZTEC)
        formats.add(BarcodeFormat.DATA_MATRIX)
        formats.add(BarcodeFormat.MAXICODE)
        formats.add(BarcodeFormat.QR_CODE)

        val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
        hints[DecodeHintType.POSSIBLE_FORMATS] = formats
        hints[DecodeHintType.TRY_HARDER] = true
        hints[DecodeHintType.CHARACTER_SET] = "utf-8"
        setHints(hints)
    }

    var onScanResultCallback: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        addView(
            cameraView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        setWillNotDraw(true)
        attrs?.let { context.obtainStyledAttributes(it, R.styleable.QRCodeView) }?.run {
            maskPaint.color = getColor(R.styleable.QRCodeView_qr_maskColor, maskPaint.color)

            cornerBreadth = getDimension(R.styleable.QRCodeView_qr_cornerBreadth, cornerBreadth)
            cornerLength = getDimension(R.styleable.QRCodeView_qr_cornerLength, cornerLength)

            cornerPaint.apply {
                color = getColor(R.styleable.QRCodeView_qr_cornerColor, DEF_COLOR)
                strokeWidth = cornerBreadth
            }

            gridColor = getColor(R.styleable.QRCodeView_qr_gridColor, cornerPaint.color)

            gridRowCount = getInt(R.styleable.QRCodeView_qr_gridRowCount, gridRowCount)
            gridColumnCount = getInt(R.styleable.QRCodeView_qr_gridColumnCount, gridColumnCount)

            sizeRatio = getFloat(R.styleable.QRCodeView_qr_sizeRatio, sizeRatio)
            offsetXRatio = getFloat(R.styleable.QRCodeView_qr_offsetXRatio, offsetXRatio)
            offsetYRatio = getFloat(R.styleable.QRCodeView_qr_offsetYRatio, offsetYRatio)
            this
        }?.recycle()
    }

    override fun onDetachedFromWindow() {
        cameraView.clearFrameProcessors()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = min(measuredWidth, measuredHeight) * sizeRatio
        val centerX = measuredWidth * offsetXRatio
        val centerY = measuredHeight * offsetYRatio

        scanFrame.left = centerX - size / 2
        scanFrame.right = centerX + size / 2
        scanFrame.top = centerY - size / 2
        scanFrame.bottom = centerY + size / 2

        scanFrame.offset(
            when {
                scanFrame.left < 0 -> -scanFrame.left
                scanFrame.right > measuredWidth -> measuredWidth - scanFrame.right
                else -> 0f
            },
            when {
                scanFrame.top < 0 -> -scanFrame.top
                scanFrame.bottom > measuredHeight -> measuredHeight - scanFrame.bottom
                else -> 0f
            }
        )

        gridPath.reset()
        val rowGap: Float = scanFrame.width() * 1f / gridRowCount
        for (i in 0..gridRowCount) {
            gridPath.moveTo(scanFrame.left, scanFrame.top + rowGap * i)
            gridPath.lineTo(scanFrame.right, scanFrame.top + rowGap * i)
        }
        val colGap: Float = scanFrame.width() * 1f / gridColumnCount
        for (i in 0..gridColumnCount) {
            gridPath.moveTo(scanFrame.left + colGap * i, scanFrame.top)
            gridPath.lineTo(scanFrame.left + colGap * i, scanFrame.bottom)
        }

        setGridGradient()
        initGridAnimation()
    }

    private fun setGridGradient() {
        val gridBottom = scanFrame.bottom + 0.01f * scanFrame.height()

        gridGradient = LinearGradient(
            0f,
            scanFrame.top,
            0f,
            gridBottom,
            intArrayOf(Color.TRANSPARENT, gridColor.withAlpha(0.4f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.99f, 1f),
            Shader.TileMode.CLAMP
        )

        gridMaskGradients.apply {
            clear()
            add(
                LinearGradient(
                    0f,
                    scanFrame.top,
                    0f,
                    gridBottom,
                    intArrayOf(Color.TRANSPARENT, gridColor.withAlpha(0.1f), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.99f, 1f),
                    Shader.TileMode.CLAMP
                )
            )
            add(
                LinearGradient(
                    0f, scanFrame.top, 0f, gridBottom,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        gridColor.withAlpha(0.35f),
                        Color.TRANSPARENT
                    ), floatArrayOf(0f, 0.875f, 0.99f, 1f),
                    Shader.TileMode.CLAMP
                )
            )
            add(
                LinearGradient(
                    0f, scanFrame.top, 0f, gridBottom,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        gridColor.withAlpha(0.15f),
                        Color.TRANSPARENT
                    ), floatArrayOf(0f, 0.95f, 0.99f, 1f),
                    Shader.TileMode.CLAMP
                )
            )
        }
    }

    private fun initGridAnimation() {
        ValueAnimator
            .ofFloat(-scanFrame.height(), 0f)
            .apply {
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = -1
                duration = 1500
                addUpdateListener {
                    gridMatrix.setTranslate(0f, it.animatedValue as Float)
                    invalidate()
                }
            }
            .start()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        canvas.apply {
            drawRect(0f, 0f, scanFrame.left, measuredHeight.toFloat(), maskPaint)
            drawRect(
                scanFrame.right,
                0f,
                measuredWidth.toFloat(),
                measuredHeight.toFloat(),
                maskPaint
            )
            drawRect(scanFrame.left, 0f, scanFrame.right, scanFrame.top, maskPaint)
            drawRect(
                scanFrame.left,
                scanFrame.bottom,
                scanFrame.right,
                measuredHeight.toFloat(),
                maskPaint
            )
        }

        canvas.apply {
            gridGradient?.setLocalMatrix(gridMatrix)
            gridPaint.shader = gridGradient
            drawPath(gridPath, gridPaint)
        }

        canvas.apply {
            for (gradient in gridMaskGradients) {
                gradient.setLocalMatrix(gridMatrix)
                gridMaskPaint.shader = gradient
                drawRect(scanFrame, gridMaskPaint)
            }
        }

        canvas.apply {
            save()
            repeat(3) {
                save()
                translate(cornerBreadth / 2f, cornerBreadth / 2f)
                cornerPath.apply {
                    reset()
                    moveTo(scanFrame.left, scanFrame.top + cornerLength)
                    lineTo(scanFrame.left, scanFrame.top)
                    lineTo(scanFrame.left + cornerLength, scanFrame.top)
                }
                drawPath(cornerPath, cornerPaint)
                restore()
                rotate(90f, scanFrame.centerX(), scanFrame.centerY())
            }
            restore()
        }
    }

    private fun calScanAreaRect(previewWidth: Int, previewHeight: Int): RectF {
        val rect = RectF(scanFrame)
        val wRatio = 1.0f * previewWidth / measuredWidth
        val hRatio = 1.0f * previewHeight / measuredHeight

        val centerX = rect.centerX() * wRatio
        val centerY = rect.centerY() * hRatio
        val halfWidth = rect.width() / 2f * wRatio
        val halfHeight = rect.height() / 2f * hRatio

        rect.left = centerX - halfWidth
        rect.right = centerX + halfWidth
        rect.top = centerY - halfHeight
        rect.bottom = centerY + halfHeight

        return rect
    }

    private fun processFrame(frame: Frame) {
        var data: ByteArray? = frame.getData()
        val size: Size = frame.size
        if (data == null) {
            return
        }
        var width: Int = size.width
        var height: Int = size.height
        if (isPortrait(context)) {
            val tempData = ByteArray(data.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    tempData[x * height + height - y - 1] = data[x + y * width]
                }
            }
            data = tempData
            width = size.height
            height = size.width
        }
        var rawResult: Result? = null
        try {
            val scanAreaRect: RectF = calScanAreaRect(width, height)
            val source = PlanarYUVLuminanceSource(
                data,
                width,
                height,
                scanAreaRect.left.toInt(),
                scanAreaRect.top.toInt(),
                scanAreaRect.width().toInt(),
                scanAreaRect.height().toInt(),
                false
            )
            rawResult =
                multiFormatReader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source)))
            if (rawResult == null) {
                rawResult = multiFormatReader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                if (rawResult != null) {
                    print("GlobalHistogramBinarizer 没识别到，HybridBinarizer 能识别到")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            multiFormatReader.reset()
        }
        if (rawResult == null || rawResult.text == null) {
            return
        }
        mainHandler.post { onScanResultCallback?.invoke(rawResult.text) }
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        cameraView.setLifecycleOwner(owner)
    }
}