package com.zourw.qrcode

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zourw.libqrcode.EXTRA_HINT
import com.zourw.libqrcode.EXTRA_RESULT
import com.zourw.libqrcode.EXTRA_TITLE
import com.zourw.libqrcode.QRCodeActivity
import kotlinx.android.synthetic.main.activity_main.*

private const val REQUEST_CODE = 100

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnScan.setOnClickListener {
            Intent(this, QRCodeActivity::class.java)
                .putExtra(EXTRA_TITLE, "二维码扫描")
                .putExtra(EXTRA_HINT, "将二维码放入框内，即可自动扫描")
                .also {
                    startActivityForResult(it, REQUEST_CODE)
                }
        }

        btnClear.setOnClickListener {
            tvResult.text = ""
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> tvResult.append("${data?.getStringExtra(EXTRA_RESULT)}\n\n")
                Activity.RESULT_CANCELED -> tvResult.append("取消\n\n")
            }
        }
    }
}
