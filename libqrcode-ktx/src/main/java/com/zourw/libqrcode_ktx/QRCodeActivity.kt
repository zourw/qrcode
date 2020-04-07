package com.zourw.libqrcode_ktx

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.otaliastudios.cameraview.controls.Flash
import kotlinx.android.synthetic.main.activity_qrcode_ktx.*

const val EXTRA_TITLE = "extra_title"
const val EXTRA_HINT = "extra_hint"
const val EXTRA_RESULT = "extra_result"

class QRCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeStatusBarTransparent(this)
        setContentView(R.layout.activity_qrcode_ktx)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "二维码扫描"
        val hint = intent.getStringExtra(EXTRA_HINT) ?: "将二维码放入框内，即可自动扫描"

        tvTitle.text = title
        tvHint.text = hint

        qrCodeView.setLifecycleOwner(this)
        qrCodeView.onScanResultCallback = {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT, it))
            finish()
        }

        btnBack.setOnClickListener {
            onBackPressed()
        }

        swFlash.setOnClickListener {
            cbFlash.toggle()
            qrCodeView.cameraView.flash = if (cbFlash.isChecked) Flash.TORCH else Flash.OFF
        }

        qrCodeView.post {
            val scanFrame = qrCodeView.scanFrame
            tvHint.translationY = scanFrame.top - dp2px(this, 18f) - tvHint.measuredHeight
            swFlash.translationY = scanFrame.bottom + dp2px(this, 80f)
        }
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }
}