package com.example.crosschat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ImagePreviewActivity extends AppCompatActivity {

    public static final String EXTRA_BASE64 = "img_base64";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        ImageView ivPreview = findViewById(R.id.iv_preview);
        Button btnBack = findViewById(R.id.button2Fan);

        // 获取传递过来的base64
        String base64 = getIntent().getStringExtra(EXTRA_BASE64);
        if(base64 != null){
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            ivPreview.setImageBitmap(bitmap);
        }

        // 返回按钮，关闭页面
        btnBack.setOnClickListener(v -> finish());

    }
}
