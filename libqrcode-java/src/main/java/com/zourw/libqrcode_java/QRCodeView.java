package com.zourw.libqrcode_java;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Engine;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.otaliastudios.cameraview.gesture.Gesture;
import com.otaliastudios.cameraview.gesture.GestureAction;
import com.otaliastudios.cameraview.size.Size;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.zourw.libqrcode_java.Utils.dp2px;
import static com.zourw.libqrcode_java.Utils.isPortrait;
import static com.zourw.libqrcode_java.Utils.withAlpha;

/**
 * Created by Zourw on 2020/4/7.
 */
public class QRCodeView extends FrameLayout {
    private static int DEF_COLOR = 0xFF00FF00;

    private Paint maskPaint = new Paint() {{
        setColor(0xBF04080D);
    }};

    private float cornerBreadth = dp2px(getContext(), 3f);
    private float cornerLength = dp2px(getContext(), 25f);

    private Path cornerPath = new Path();
    private Paint cornerPaint = new Paint() {{
        setColor(DEF_COLOR);
        setStyle(Paint.Style.STROKE);
        setStrokeWidth(cornerBreadth);
    }};

    private RectF scanFrame = new RectF();

    private Path gridPath = new Path();
    private int gridColor = DEF_COLOR;
    private Paint gridPaint = new Paint() {{
        setStyle(Paint.Style.STROKE);
        setStrokeWidth(2f);
    }};
    private Paint gridMaskPaint = new Paint() {{
        setStyle(Paint.Style.FILL);
    }};

    private LinearGradient gridGradient;
    private LinearGradient[] gridMaskGradients = new LinearGradient[3];

    private Matrix gridMatrix = new Matrix();

    private int gridRowCount = 30;
    private int gridColumnCount = 30;

    private float sizeRatio = 0.7f;
    private float offsetXRatio = 0.5f;
    private float offsetYRatio = 0.5f;

    private CameraView cameraView = new CameraView(getContext()) {{
        set(Engine.CAMERA1);
        setPlaySounds(false);
        mapGesture(Gesture.PINCH, GestureAction.ZOOM);
        mapGesture(Gesture.TAP, GestureAction.AUTO_FOCUS);
        addFrameProcessor(new FrameProcessor() {
            @Override
            public void process(@NonNull Frame frame) {
                processFrame(frame);
            }
        });
    }};

    private MultiFormatReader multiFormatReader = new MultiFormatReader();

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private OnScanResultCallback onScanResultCallback;

    public QRCodeView(@NonNull Context context) {
        this(context, null);
    }

    public QRCodeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public QRCodeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        addView(cameraView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setWillNotDraw(true);

        if (attrs != null) {
            final TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.QRCodeView);
            maskPaint.setColor(array.getColor(R.styleable.QRCodeView_qr_maskColor, maskPaint.getColor()));

            cornerBreadth = array.getDimension(R.styleable.QRCodeView_qr_cornerBreadth, cornerBreadth);
            cornerLength = array.getDimension(R.styleable.QRCodeView_qr_cornerLength, cornerLength);

            cornerPaint.setColor(array.getColor(R.styleable.QRCodeView_qr_cornerColor, DEF_COLOR));
            cornerPaint.setStrokeWidth(cornerBreadth);

            gridColor = array.getColor(R.styleable.QRCodeView_qr_gridColor, cornerPaint.getColor());

            gridRowCount = array.getInt(R.styleable.QRCodeView_qr_gridRowCount, gridRowCount);
            gridColumnCount = array.getInt(R.styleable.QRCodeView_qr_gridColumnCount, gridColumnCount);

            sizeRatio = array.getFloat(R.styleable.QRCodeView_qr_sizeRatio, sizeRatio);
            offsetXRatio = array.getFloat(R.styleable.QRCodeView_qr_offsetXRatio, offsetXRatio);
            offsetYRatio = array.getFloat(R.styleable.QRCodeView_qr_offsetYRatio, offsetYRatio);

            array.recycle();
        }

        setupReader();
    }

    private void setupReader() {
        final List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.AZTEC);
        formats.add(BarcodeFormat.DATA_MATRIX);
        formats.add(BarcodeFormat.MAXICODE);
        formats.add(BarcodeFormat.QR_CODE);

        final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "utf-8");

        multiFormatReader.setHints(hints);
    }

    @Override
    protected void onDetachedFromWindow() {
        cameraView.clearFrameProcessors();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int measuredWidth = getMeasuredWidth();
        final int measuredHeight = getMeasuredHeight();

        float size = Math.min(measuredWidth, measuredHeight) * sizeRatio;
        float centerX = measuredWidth * offsetXRatio;
        float centerY = measuredHeight * offsetYRatio;

        scanFrame.left = centerX - size / 2;
        scanFrame.right = centerX + size / 2;
        scanFrame.top = centerY - size / 2;
        scanFrame.bottom = centerY + size / 2;

        scanFrame.offset(
                scanFrame.left < 0 ? -scanFrame.left : scanFrame.right > measuredWidth ? measuredWidth - scanFrame.right : 0,
                scanFrame.top < 0 ? -scanFrame.top : scanFrame.bottom > measuredHeight ? measuredHeight - scanFrame.bottom : 0
        );

        gridPath.reset();
        final float rowGap = scanFrame.width() * 1f / gridRowCount;
        for (int i = 0; i < gridRowCount; i++) {
            gridPath.moveTo(scanFrame.left, scanFrame.top + rowGap * i);
            gridPath.lineTo(scanFrame.right, scanFrame.top + rowGap * i);
        }
        final float colGap = scanFrame.width() * 1f / gridColumnCount;
        for (int i = 0; i < gridColumnCount; i++) {
            gridPath.moveTo(scanFrame.left + colGap * i, scanFrame.top);
            gridPath.lineTo(scanFrame.left + colGap * i, scanFrame.bottom);
        }

        setGridGradient();
        initGridAnimation();
    }

    private void setGridGradient() {
        final float gridBottom = scanFrame.bottom + 0.01f * scanFrame.height();
        gridGradient = new LinearGradient(
                0, scanFrame.top, 0, gridBottom,
                new int[]{Color.TRANSPARENT, withAlpha(gridColor, 0.4f), Color.TRANSPARENT},
                new float[]{0, 0.99f, 1f},
                LinearGradient.TileMode.CLAMP
        );

        gridMaskGradients[0] = new LinearGradient(
                0, scanFrame.top, 0, gridBottom,
                new int[]{Color.TRANSPARENT, withAlpha(gridColor, 0.1f), Color.TRANSPARENT},
                new float[]{0, 0.99f, 1f},
                LinearGradient.TileMode.CLAMP
        );
        gridMaskGradients[1] = new LinearGradient(
                0, scanFrame.top, 0, gridBottom,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(gridColor, 0.35f), Color.TRANSPARENT},
                new float[]{0, 0.875f, 0.99f, 1f},
                LinearGradient.TileMode.CLAMP
        );
        gridMaskGradients[2] = new LinearGradient(
                0, scanFrame.top, 0, gridBottom,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(gridColor, 0.15f), Color.TRANSPARENT},
                new float[]{0, 0.95f, 0.99f, 1f},
                LinearGradient.TileMode.CLAMP
        );
    }

    private void initGridAnimation() {
        final ValueAnimator animator = ValueAnimator.ofFloat(-scanFrame.height(), 0);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setRepeatCount(-1);
        animator.setDuration(1500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                final float value = (float) animator.getAnimatedValue();
                gridMatrix.setTranslate(0, value);
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        canvas.drawRect(0, 0, scanFrame.left, getMeasuredHeight(), maskPaint);
        canvas.drawRect(scanFrame.right, 0, getMeasuredWidth(), getMeasuredHeight(), maskPaint);
        canvas.drawRect(scanFrame.left, 0, scanFrame.right, scanFrame.top, maskPaint);
        canvas.drawRect(scanFrame.left, scanFrame.bottom, scanFrame.right, getMeasuredHeight(), maskPaint);

        gridGradient.setLocalMatrix(gridMatrix);
        gridPaint.setShader(gridGradient);
        canvas.drawPath(gridPath, gridPaint);

        for (LinearGradient gradient : gridMaskGradients) {
            gradient.setLocalMatrix(gridMatrix);
            gridMaskPaint.setShader(gradient);
            canvas.drawRect(scanFrame, gridMaskPaint);
        }

        canvas.save();
        for (int i = 0; i < 4; i++) {
            canvas.save();
            canvas.translate(cornerBreadth / 2f, cornerBreadth / 2f);
            cornerPath.reset();
            cornerPath.moveTo(scanFrame.left, scanFrame.top + cornerLength);
            cornerPath.lineTo(scanFrame.left, scanFrame.top);
            cornerPath.lineTo(scanFrame.left + cornerLength, scanFrame.top);
            canvas.drawPath(cornerPath, cornerPaint);
            canvas.restore();
            canvas.rotate(90, scanFrame.centerX(), scanFrame.centerY());
        }
        canvas.restore();
    }

    private RectF calScanAreaRect(int previewWidth, int previewHeight) {
        final RectF rect = new RectF(scanFrame);
        final float wRatio = 1.0f * previewWidth / getMeasuredWidth();
        final float hRatio = 1.0f * previewHeight / getMeasuredHeight();

        final float centerX = rect.centerX() * wRatio;
        final float centerY = rect.centerY() * hRatio;
        final float halfWidth = rect.width() / 2f * Math.min(wRatio, hRatio);
        final float halfHeight = rect.height() / 2f * Math.min(wRatio, hRatio);

        rect.left = centerX - halfWidth;
        rect.right = centerX + halfWidth;
        rect.top = centerY - halfHeight;
        rect.bottom = centerY + halfHeight;

        return rect;
    }

    private void processFrame(Frame frame) {
        byte[] data = frame.getData();
        final Size size = frame.getSize();

        int width = size.getWidth();
        int height = size.getHeight();
        if (isPortrait(getContext())) {
            final byte[] tempData = new byte[data.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    tempData[x * height + height - y - 1] = data[x + y * width];
                }
            }
            data = tempData;
            width = size.getHeight();
            height = size.getWidth();
        }

        Result rawResult = null;
        try {
            final RectF scanAreaRect = calScanAreaRect(width, height);
            final PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    data, width, height,
                    (int) scanAreaRect.left, (int) scanAreaRect.top, (int) scanAreaRect.width(), (int) scanAreaRect.height(),
                    false);

            rawResult = multiFormatReader.decodeWithState(new BinaryBitmap(new GlobalHistogramBinarizer(source)));
            if (rawResult == null) {
                rawResult = multiFormatReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
                if (rawResult != null) {
                    Log.d("processFrame", "GlobalHistogramBinarizer 没识别到，HybridBinarizer 能识别到");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            multiFormatReader.reset();
        }

        if (rawResult == null || rawResult.getText() == null) {
            return;
        }

        final String text = rawResult.getText();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (onScanResultCallback != null) {
                    onScanResultCallback.onScanResult(text);
                }
            }
        });
    }

    public void setLifecycleOwner(LifecycleOwner owner) {
        cameraView.setLifecycleOwner(owner);
    }

    public interface OnScanResultCallback{
        void onScanResult(String result);
    }

    public void setOnScanResultCallback(OnScanResultCallback onScanResultCallback) {
        this.onScanResultCallback = onScanResultCallback;
    }

    public RectF getScanFrame() {
        return scanFrame;
    }

    public CameraView getCameraView() {
        return cameraView;
    }
}
