package com.example.crosschat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class ImagePreview_2_Activity extends AppCompatActivity {

    private ImageView  ivPreview;           // 预览画布
    private Button but_1Next;//下一张
    private Button but_2Prew;
    private TextView tvIndex;              // 显示 "第几张 / 共几张"
    private int currentIndex = 0;          // 当前显示的是第几张（从 0 开始数）
    private  ArrayList<String>  photoPaths;  // 从上一个页面传来的照片路径集合

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_image_preview_2);
        but_1Next=findViewById(R.id.btn_next);
        but_2Prew=findViewById(R.id.btn_prev);
        tvIndex=findViewById(R.id.tv_index);
        ivPreview=findViewById(R.id.iv_preview);
        photoPaths=getIntent().getStringArrayListExtra("photo_paths");
        if(photoPaths==null||photoPaths.isEmpty())
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ImagePreview_2_Activity.this,"没有照片存在",Toast.LENGTH_SHORT).show();

                    finish();
                    return;
                }
            });
        }

        but_2Prew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPhoto(currentIndex-1);
            }
        });
        but_1Next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPhoto(currentIndex+1);
            }
        });
        showPhoto(0); // 先显示第一张



    }

    /**
     * 显示第 index 张照片
     */
    private void showPhoto(int index) {


        // 防止越界（小于 0 就停在 0，超过最后一张就停在最后一张）

        if (index < 0) {
            index = 0;
        }
        if (index >= photoPaths.size()) {
            index = photoPaths.size() - 1;
            currentIndex = index;
        }
        // 按路径加载图片（带采样压缩，防止大图 OOM）

        Bitmap bitmap=loadScaledBitmap(photoPaths.get(currentIndex));
        ivPreview.setImageBitmap(bitmap);
        // 更新指示文字：第 2 / 5 张
        tvIndex.setText((currentIndex + 1) + " / " + photoPaths.size());

        // 到边界时禁用对应按钮
        but_2Prew.setEnabled(currentIndex>0);
        but_1Next.setEnabled(currentIndex<photoPaths.size()-1);





    }

    /**
     * 根据文件路径加载图片，并用 inSampleSize 缩放，避免内存溢出。
     * 原理：先只读尺寸，算出一个缩小倍数，再真正解码。
     */
    private Bitmap loadScaledBitmap(String path)
    {
        // 第一次：只读图片尺寸，不加载到内存

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int outWidth = bounds.outWidth;

        // 目标最大宽度（超出就按 2 的倍数缩小）
        int targetWidth = 1080;
        int sampleSize = 1;
        while (outWidth / sampleSize > targetWidth) {
            sampleSize *= 2;
        }

        // 第二次：真正解码，按 sampleSize 缩小
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(path, decodeOptions);
    }
}
