package com.example.my_camera2_finalpractice;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import android.graphics.RectF;

public class MY_Camera2 extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private int sensorOrientation;//传感器参数
    private int mJpegOrientation;         // 本次拍照计算出的JPEG方向

    private Button btn_take_photo, btn_switch_camera, btn_view_image;
    private TextureView mTextureView;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;

    private Surface MypreviewSurface;
    private String mBackCameraId;//后置
    private String mFrontCameraId;//前置
    private String mCameraId;//当前的

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;

    private boolean isSwitching = false;//防止连续点击
    private Size mPreviewSize;
    private int previewWidth, previewHeight;   // 预览尺寸，和拍照尺寸分开
    private int photoWidth, photoHeight;
    private CaptureRequest.Builder MypreviewRequestBuilder;
    private final List<String> photoPathList = new ArrayList<>();


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_camera2);
        btn_switch_camera = findViewById(R.id.btn_switch_camera);
        btn_take_photo = findViewById(R.id.btn_take_photo);
        btn_view_image = findViewById(R.id.btn_view_image);

        mTextureView = findViewById(R.id.textureView);

        mCameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        // 提前找出前后摄像头ID
        findFrontBackCameraId();

        btn_take_photo.setOnClickListener(v -> takePicture());
        btn_view_image.setOnClickListener(v -> openPhotoViewer());
        btn_switch_camera.setOnClickListener(v -> switchCamera());

        // 动态申请相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }


    }


    /**
     * 跳转查看照片页
     */
    private void openPhotoViewer() {
        if (photoPathList.isEmpty()) {
            Toast.makeText(this, "还没有拍过照片", Toast.LENGTH_SHORT).show();
            return;   // 没有照片就直接返回，不再跳转
        }
        Intent intent = new Intent(MY_Camera2.this, ImagePreview_2_Activity.class);
        intent.putStringArrayListExtra("photo_paths", new ArrayList<>(photoPathList));
        startActivity(intent);
    }

    /**
     * 找出前后摄像头ID
     * 后置优先，没有后置就用前置，都没有用列表第一个
     */
    private void findFrontBackCameraId() {

        try {
            String[] strings = mCameraManager.getCameraIdList();
            for (String id : strings) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null) continue;

                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    mBackCameraId = id;
                } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    mFrontCameraId = id;
                }
            }

            // 默认ID的选择放到循环外面
            if (mBackCameraId != null) {
                mCameraId = mBackCameraId;
            } else if (mFrontCameraId != null) {
                mCameraId = mFrontCameraId;
            } else if (strings.length > 0) {
                mCameraId = strings[0];
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * 当前是否为前置摄像头
     */
    private boolean isFrontCamera() {
        return mFrontCameraId != null && mFrontCameraId.equals(mCameraId);
    }

    /**
     * 切换前后摄像头
     */
    private void switchCamera() {
        // 只有一个摄像头，无法切换
        if (mBackCameraId == null || mFrontCameraId == null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MY_Camera2.this, "当前设备只有一个摄像头，无法切换", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        if (isSwitching) return;
        isSwitching = true;

        closeCamera();   // 先彻底释放当前相机

        // 前后ID互换
        mCameraId = isFrontCamera() ? mBackCameraId : mFrontCameraId;
        openCamera();    // 重新打开新相机，成功后 onOpened 里会重置 isSwitching
    }

    private void openCamera() {
        // 后台线程没就绪、或相机已打开，直接返回，防止重复打开
        if (mCameraHandler == null || mCameraDevice != null) return;
        //判断权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        setupCameraOutputs();//获取相机特性和设备
        try {
            mCameraManager.openCamera(mCameraId, stateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            isSwitching = false;
        }


    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
            isSwitching = false;

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
            isSwitching = false;

        }

        @Override
        public void onOpened(@NonNull CameraDevice camera) {

            mCameraDevice = camera;
            isSwitching = false;   // 切换完成

            //创建会话
            creatPrevwSession();


        }
    };

    private void creatPrevwSession() {

        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture == null) return;
        surfaceTexture.setDefaultBufferSize(previewWidth, previewHeight);
        MypreviewSurface = new Surface(surfaceTexture);
        List<Surface> surfaceList = new ArrayList<>();
        surfaceList.add(MypreviewSurface);
        surfaceList.add(mImageReader.getSurface());

        try {
            mCameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MY_Camera2.this, "会话配置失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {

                    mCaptureSession = session;
                    try {
                        MypreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        MypreviewRequestBuilder.addTarget(MypreviewSurface);
                        mCaptureSession.setRepeatingRequest(MypreviewRequestBuilder.build(), null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // 新增：预览配置好后，按预览比例修正显示。修正预览问题
        runOnUiThread(() ->
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight()));


    }

    private void takePicture() {
        if (mCameraDevice == null || mCaptureSession == null) return;

        try {
            CaptureRequest.Builder captureRequest =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequest.addTarget(mImageReader.getSurface());

            // 计算JPEG方向并写入EXIF，图库查看方向才正确
            mJpegOrientation = calculateJpegRotation();
            captureRequest.set(CaptureRequest.JPEG_ORIENTATION, mJpegOrientation);

            mCaptureSession.capture(captureRequest.build(), null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int calculateJpegRotation() {

        int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
        int rotation;
        switch (deviceRotation) {
            case Surface.ROTATION_0:
                rotation = 0;
                break;
            case Surface.ROTATION_90:
                rotation = 90;
                break;
            case Surface.ROTATION_180:
                rotation = 180;
                break;
            case Surface.ROTATION_270:
                rotation = 270;
                break;
            default:
                rotation = 0;
                break;
        }

        int result;
        if (isFrontCamera()) {
            // 前置摄像头：镜像补偿
            result = (sensorOrientation + rotation) % 360;
            result = (360 - result) % 360;
        } else {
            result = (sensorOrientation - rotation + 360) % 360;
        }
        return result;
    }

    /**
     * 配置当前摄像头的输出参数（使用当前 CameraId，不再固定取下标0）
     */
    private void setupCameraOutputs() {

        CameraCharacteristics characteristics = null;
        Integer orientation = null;
        try {
            characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        sensorOrientation = orientation != null ? orientation : 0;

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        // 改点A：拍照尺寸和预览尺寸分开算
        // 1) 拍照(JPEG)尺寸：取最大
        Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
        if (jpegSizes == null || jpegSizes.length == 0) {
            jpegSizes = map.getOutputSizes(SurfaceTexture.class);
        }
        Size largestJpeg = jpegSizes[0];
        for (Size s : jpegSizes) {
            if (s.getWidth() * s.getHeight() > largestJpeg.getWidth() * largestJpeg.getHeight()) {
                largestJpeg = s;
            }
        }
        photoWidth = largestJpeg.getWidth();
        photoHeight = largestJpeg.getHeight();

        // 2) 预览尺寸：从 SurfaceTexture 支持的尺寸里挑（关键！）
        Size preview = choosePreviewSize(map);
        previewWidth = preview.getWidth();
        previewHeight = preview.getHeight();

        // 3) ImageReader 继续用 JPEG 尺寸（拍照不受影响）
        mImageReader = ImageReader.newInstance(photoWidth, photoHeight, ImageFormat.JPEG, 3);
        mImageReader.setOnImageAvailableListener(imageAvailableListener, mCameraHandler);


    }

    private Size choosePreviewSize(StreamConfigurationMap map) {
        Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
        if (sizes == null || sizes.length == 0) {
            return new Size(1920, 1080); // 兜底
        }

        int viewW = mTextureView.getWidth();
        int viewH = mTextureView.getHeight();
        float targetRatio = (viewW > 0 && viewH > 0) ? (float) viewW / viewH : 4f / 3f;

        Size best = null;
        float bestDiff = Float.MAX_VALUE;
        for (Size s : sizes) {
            float ratio = (float) s.getWidth() / s.getHeight();
            float diff = Math.abs(ratio - targetRatio);
            if (best == null) {
                best = s;
                bestDiff = diff;
                continue;
            }
            if (diff < bestDiff - 0.05f
                    || (Math.abs(diff - bestDiff) <= 0.05f
                    && s.getWidth() * s.getHeight() > best.getWidth() * best.getHeight())) {
                best = s;
                bestDiff = diff;
            }
        }
        Log.d("Camera2", "预览尺寸: " + best.getWidth() + " x " + best.getHeight());
        return best;
    }

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image == null) return;

            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            image.close();

            saveImage(bytes);
        }
    };

    private void saveImage(byte[] bytes) {

        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return;

            Matrix matrix = new Matrix();
            matrix.postRotate(mJpegOrientation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();

            File dir = new File(getExternalFilesDir(null), "camera2");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, System.currentTimeMillis() + ".jpg");

            FileOutputStream fos = new FileOutputStream(file);
            rotated.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();

            photoPathList.add(file.getAbsolutePath());
            runOnUiThread(() ->
                    Toast.makeText(this, "保存成功：" + file.getAbsolutePath(),
                            Toast.LENGTH_SHORT).show());

            rotated.recycle();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (MypreviewSurface != null) {
            MypreviewSurface.release();
            MypreviewSurface = null;
        }
        MypreviewRequestBuilder = null;
    }


    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            openCamera();

        } else {
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

            configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                if (mTextureView.isAvailable()) {
                    openCamera();//防止一直打不开相机，重新跑一边Resume;
                } else {
                    mTextureView.setSurfaceTextureListener(surfaceTextureListener);
                }
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MY_Camera2.this, "未授予相机权限，无法使用相机", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void startBackgroundThread() {
        mCameraThread = new HandlerThread("相机线程");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());


    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {

        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            try {
                mCameraThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mCameraThread = null;
            mCameraHandler = null;
        }
    }


    //预览画面变形问题-ai
    private void configureTransform(int viewWidth, int viewHeight) {
        if (mTextureView == null || previewWidth == 0 || previewHeight == 0) return;

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            // 竖屏：先交换宽高，再旋转 90°/270°
            RectF bufferRect = new RectF(0, 0, previewHeight, previewWidth);
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
             //Math.max = 裁剪铺满(全屏)；想留黑边就改成 Math.min
            float scale = Math.min(

                    (float) viewHeight / previewHeight,
                    (float) viewWidth / previewWidth);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }
}

