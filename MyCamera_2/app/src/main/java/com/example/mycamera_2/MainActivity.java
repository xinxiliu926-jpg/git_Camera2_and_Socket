package com.example.mycamera_2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private TextureView mTextureView;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private int sensorOrientation; // 相机传感器物理旋转角度，拍照后图片需要按这个角度旋转，否则图片颠倒
    private CameraCaptureSession mCameraCaptureSession;
    private String mCameraId;
    private CaptureRequest.Builder MypreviewRequestBuilder; // 【预览请求构建器】构建持续预览的请求
    private Surface MypreviewSurface; // 预览输出Surface，由TextureView的SurfaceTexture包装而来
    private HandlerThread mCameraHandlerThread;
    private Handler mHandler;
    private ImageReader mImageReader;
    private int photoWidth;                     // 拍照图片宽度（取摄像头支持最大JPEG尺寸）
    private int photoHeight;                    // 拍照图片高度

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        mTextureView = findViewById(R.id.mTextureView_2);

        mCameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);//获取服务
        // ========== 动态申请相机权限 ==========
        // 检查是否已经拥有相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // 没有权限，发起权限申请，系统弹出授权弹窗；授权结果回调 onRequestPermissionsResult
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
        // 如果已经有权限，什么都不做；等待onResume生命周期打开相机


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;


        });
    }

    /**
     * 权限申请回调：用户在系统弹窗点允许/拒绝后，系统回调此方法
     *
     * @param requestCode  请求码，用来区分是哪一次权限申请
     * @param permissions  请求的权限数组
     * @param grantResults 用户授权结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户同意权限：onResume会自动执行打开相机逻辑，这里不需要手动调用
            } else {
                Toast.makeText(this, "未授予相机权限，无法使用相机", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            // Surface还没创建，设置监听；等Surface创建完成回调 onSurfaceTextureAvailable，再打开相机
            mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }


    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

            openCamera();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();

    }


    private void startBackgroundThread() {
        mCameraHandlerThread = new HandlerThread("相机后台线程");
        mCameraHandlerThread.start();
        mHandler = new Handler(mCameraHandlerThread.getLooper());//获取looper循环


    }

    /**
     * 安全停止后台线程；页面销毁的时候调用，防止内存泄漏
     */
    private void stopBackgroundThread() {
        if (mCameraHandlerThread != null) {
            mCameraHandlerThread.quitSafely();
            try {
                mCameraHandlerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mCameraHandlerThread = null;
            mHandler = null;
        }
    }

    private void openCamera() {

        // 二次校验权限，防止没有权限调用打开相机
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        setupCameraOutputs();

        try {
            mCameraManager.openCamera(mCameraId, stateCallback, mHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            creatPrevwSession();//赋值相机设备并且开始配置会话


        }
    };

    private void closeCamera() {

        // 补全释放逻辑
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        // 你现在不需要拍照，可以注释/删掉mImageReader
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }// 关闭相机会话、CameraDevice、ImageReader


    /**
     * setupCameraOutputs：配置相机输出参数
     * 作用：获取摄像头ID、传感器角度、找到最大JPEG拍照尺寸、创建ImageReader对象
     */
    private void setupCameraOutputs() {
        try {
            mCameraId = mCameraManager.getCameraIdList()[0];
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);

            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // getOutputSizes(SurfaceTexture.class) → 获取支持预览的尺寸列表
            Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);

            // 简单取第一个预览尺寸，你后面可以替换成选最优预览尺寸算法
            Size previewSize = previewSizes[0];
            photoHeight = previewSize.getHeight();
            photoWidth = previewSize.getWidth();
            mImageReader = ImageReader.newInstance(photoWidth, photoHeight, ImageFormat.JPEG, 3);
            mImageReader.setOnImageAvailableListener(imageAvailableListener, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            buffer.get(new byte[buffer.remaining()]);
            image.close();


        }
    };

    private void creatPrevwSession() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(photoWidth, photoHeight);
        MypreviewSurface = new Surface(surfaceTexture);
        List<Surface> surfaceList = new ArrayList<>();
        surfaceList.add(MypreviewSurface);
        try {
            mCameraDevice.createCaptureSession(surfaceList, callback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraCaptureSession.StateCallback callback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "配置失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {

            try {
                mCameraCaptureSession = session;
                MypreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                MypreviewRequestBuilder.addTarget(MypreviewSurface);
                mCameraCaptureSession.setRepeatingRequest(MypreviewRequestBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }


        }
    };
}