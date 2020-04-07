package com.zourw.libqrcode_java;

import android.content.Intent;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.otaliastudios.cameraview.controls.Flash;

import static com.zourw.libqrcode_java.Utils.dp2px;
import static com.zourw.libqrcode_java.Utils.makeStatusBarTransparent;

/**
 * Created by Zourw on 2020/4/7.
 */
public class QRCodeActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_HINT = "extra_hint";
    public static final String EXTRA_RESULT = "extra_result";

    private QRCodeView qrCodeView;
    private ImageView btnBack;
    private TextView tvTitle;
    private TextView tvHint;
    private FrameLayout swFlash;
    private CheckBox cbFlash;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        makeStatusBarTransparent(this);
        setContentView(R.layout.activity_qrcode_java);

        initView();
        setupListener();

        final String title = getIntent().getStringExtra(EXTRA_TITLE);
        final String hint = getIntent().getStringExtra(EXTRA_HINT);

        tvTitle.setText(TextUtils.isEmpty(title) ? "二维码扫描" : title);
        tvHint.setText(TextUtils.isEmpty(hint) ? "将二维码放入框内，即可自动扫描" : hint);

        qrCodeView.setLifecycleOwner(this);
    }

    private void initView() {
        qrCodeView = findViewById(R.id.qrCodeView);
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        tvHint = findViewById(R.id.tvHint);
        swFlash = findViewById(R.id.swFlash);
        cbFlash = findViewById(R.id.cbFlash);
    }

    private void setupListener() {
        qrCodeView.setOnScanResultCallback(new QRCodeView.OnScanResultCallback() {
            @Override
            public void onScanResult(String result) {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT, result));
                finish();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        swFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cbFlash.toggle();
                qrCodeView.getCameraView().setFlash(cbFlash.isChecked() ? Flash.TORCH : Flash.OFF);
            }
        });

        qrCodeView.post(new Runnable() {
            @Override
            public void run() {
                final RectF scanFrame = qrCodeView.getScanFrame();
                tvHint.setTranslationY(scanFrame.top - dp2px(QRCodeActivity.this, 18f) - tvHint.getMeasuredHeight());
                swFlash.setTranslationY(scanFrame.bottom + dp2px(QRCodeActivity.this, 80f));
            }
        });
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}
