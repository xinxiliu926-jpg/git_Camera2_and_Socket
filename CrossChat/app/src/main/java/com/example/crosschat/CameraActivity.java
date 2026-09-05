package com.example.crosschat;

import android.Manifest;

import android.content.pm.PackageManager;
import android.os.Bundle;

import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class CameraActivity extends AppCompatActivity {
    // 照片保存目录常量
    public  String PHOTO_SAVE_PATH ;
    private Button but_Task;//定义全局按钮变量;
    private PreviewView previewView;//定义预览画布；
    private ImageCapture imageCapture;//CameraX 拍照核心对象，负责执行拍照、保存图片。
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    //异步获取相机生命周期管理器，用来绑定预览、拍照用例到 Activity 生命周期。
    private static final int REQUEST_CAMERA_PERM = 1001;
     //REQUEST_CAMERA_PERM =1001：相机权限申请请求码，用于权限回调时区分本次权限请求。


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        but_Task = findViewById(R.id.btn_take_photo);
        previewView = findViewById(R.id.previewView);//添加按钮以及界面预览

        PHOTO_SAVE_PATH = getExternalFilesDir(null).getAbsolutePath() + "/camera_photo/";

        //判断是否有相机权限
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)== PackageManager.PERMISSION_GRANTED)
        {
            stratCamera();//权限允许，直接运行相机
        }
        else {
            //第一个参数传入当前 Activity 作为上下文；
            // 第二个是权限数组，声明本次需要相机权限；
            // 第三个是自定义请求码，用于在权限回调函数中区分不同权限申请，处理用户同意或拒绝后的业务逻辑。
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA_PERM);
        }
        but_Task.setOnClickListener(v->{takePhoto();});//按钮点击监听事件






    }
    private void stratCamera()//定义启动相机函数
    {
        cameraProviderFuture=ProcessCameraProvider.getInstance(this);
        //这行代码异步获取相机管理对象，因为打开相机是耗时操作，
        // 不能直接同步获取，通过 Future 回调拿到实例后再绑定相机预览与拍照。
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    //将相机的生命周期和activity的生命周期绑定，camerax 会自己释放，不用担心了
                    ProcessCameraProvider provider = cameraProviderFuture.get();
                    Preview preview = new Preview.Builder().build();
                    //预览的 capture，它里面支持角度换算,旋转90度那个;
                    //创建图片的 capture
                    imageCapture= new ImageCapture.Builder().
                             setFlashMode(ImageCapture.FLASH_MODE_AUTO).build();
                    //选择后置摄像头
                    CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                    //预览之前先解绑
                    provider.unbindAll();
                    //将数据绑定到相机的生命周期中预览的功能
                    Camera camer= provider.bindToLifecycle(CameraActivity.this,cameraSelector,preview,imageCapture);
                    //CameraxActivity.this：生命周期宿主,使用当前的界面；
                    //cameraSelector：指定使用后置 / 前置摄像头；
                    //preview：预览用例；
                    //ImageCapture：拍照用例。
                    //返回值：Camera对象，可获取相机信息。绑定前建议执行unbindAll()防止多实例冲突。
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());//绑定预览的界面是画布previewView
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        },ContextCompat.getMainExecutor(this));
        //返回当前可以绑定生命周期的 ProcessCameraProvider，ProcessCameraProvider 它会和宿主绑定生命周期，这样就不用担心打开相机和关闭的问题了。
        //
        //接着，向 cameraProviderFuture 注册一个监听，第一个参数是一个 runnable，第二个参数是线程池，即runnable 运行在哪个线程中：




    }
    private void takePhoto()
    {
        if(imageCapture!=null) {
            File dir = new File(PHOTO_SAVE_PATH);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(PHOTO_SAVE_PATH, "testx.jpg");
            if (file.exists()) {
                file.delete();
            }
            ImageCapture.OutputFileOptions fileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
            //创建包文件的数据，比如创建文件


        imageCapture.takePicture(fileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(ImageCapture.@NonNull OutputFileResults outputFileResults) {
                Toast.makeText(CameraActivity.this, "保存成功:", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(CameraActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
            }
        });

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERM) {
            if (
                    grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) stratCamera();
            else {
                Toast.makeText(this, "未授予相机权限", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
