package com.example.crosschat;


import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.lights.LightsManager;
import android.media.Image;
import android.media.ImageReader;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
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

public class Camera_2Activity extends AppCompatActivity {
    private Button but_1;
    private Button but_2;
    private TextureView mTtextureView;
    // 相机权限请求码，用于权限回调的时候区分请求
    private static final int REQUEST_CAMERA_PERMISSION = 1001;

    private CameraManager MycameraManager; // 系统相机服务，用来枚举、打开相机设备
    private String CameraId;// 当前使用摄像头ID (0一般后置，1一般前置)
    private CameraDevice MycameraDevice; // 打开后的相机设备对象，相机打开成功后才不为null

    private int sensorOrientation; // 相机传感器物理旋转角度，拍照后图片需要按这个角度旋转，否则图片颠倒

    private CameraCaptureSession MycameraCaptureSession;
    ;// 捕获会话！！Camera2最重要对象，管理预览流、拍照请求

    private CaptureRequest.Builder MypreviewRequestBuilder; // 【预览请求构建器】构建持续预览的请求

    private ImageReader MyimageReader;  // 接收拍照输出JPEG图像；拍照的数据从这里拿

    // ========== 后台线程：相机操作不跑主线程 ==========
    private HandlerThread MyCameraThread;

    private Handler MyCameraHandler;//线程对应的handler;把回调分发到cameraThread线程执行
    // ========== UI相关 ==========
    private Surface MypreviewSurface; // 预览输出Surface，由TextureView的SurfaceTexture包装而来
    private int photoWidth;                     // 拍照图片宽度（取摄像头支持最大JPEG尺寸）
    private int photoHeight;                    // 拍照图片高度

    private final List<String> photoPathList = new ArrayList<>(); // 保存拍过的所有照片路径

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);
        but_1 = findViewById(R.id.btn_take_photo);
        but_2=findViewById(R.id.btn_view_photo);
        mTtextureView = findViewById(R.id.textureView_preview);


        MycameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);


        but_1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //先空着
                takePicture();
            }
        });

        but_2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPhotoViewer();
            }
        });

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

    }

    // 跳转到查看照片页，把照片路径集合传过去
    private void openPhotoViewer() {


        if(photoPathList.isEmpty())
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Camera_2Activity.this,"还没有拍过照片",Toast.LENGTH_SHORT).show();
                }
            });
        }

        Intent intent = new Intent(Camera_2Activity.this, ImagePreview_2_Activity.class);

        intent.putStringArrayListExtra("photo_paths",new ArrayList<>(photoPathList));
        startActivity(intent);
    }



    /**
     * onResume：页面变为可见的时候调用
     * Camera2规范：页面可见才打开相机；页面退后台必须关闭相机释放硬件
     */
    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
        // 第一步：启动相机后台线程！！
        // 为什么要单独线程：相机回调、ImageReader拿到图片回调会做耗时操作，不能阻塞UI主线程，否则ANR卡顿
// 判断TextureView的SurfaceTexture是否已经创建完成
        if (mTtextureView.isAvailable()) {
            // Surface已经就绪，可以直接打开相机
            openCamera();
        } else {
            // Surface还没创建，设置监听；等Surface创建完成回调 onSurfaceTextureAvailable，再打开相机
            mTtextureView.setSurfaceTextureListener(surfaceTextureListener);
        }

    }


    /**
     * TextureView Surface生命周期监听
     * SurfaceTexture：TextureView的底层图像缓冲区，相机预览画面输出到这个对象上渲染显示
     */


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
            // TextureView大小发生变化，本demo暂不处理
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
// 每一帧画面更新回调，这里不需要处理
        }
    };

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

    /**
     * onPause：页面切后台、被覆盖的时候触发
     * 非常关键！！ 必须关闭相机、停止后台线程；否则相机硬件被占用，其他APP无法使用相机，内存泄漏
     */
    @Override
    protected void onPause() {
        closeCamera();// 关闭相机会话、CameraDevice、ImageReader
        stopBackgroundThread();// 退出相机后台线程

        super.onPause();
    }

    /**
     * 启动相机后台线程 + Handler
     * HandlerThread = Thread + Looper；可以循环处理任务
     * camerahandler 把任务投递到这个后台线程执行
     */
    private void startBackgroundThread() {
        MyCameraThread = new HandlerThread("Camera2-Background");
        MyCameraThread.start();
        MyCameraHandler = new Handler(MyCameraThread.getLooper());


    }

    /**
     * 安全停止后台线程；页面销毁的时候调用，防止内存泄漏
     */
    private void stopBackgroundThread() {
        if (MyCameraThread != null) {
            MyCameraThread.quitSafely();
            try {
                MyCameraThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            MyCameraThread = null;
            MyCameraHandler = null;
        }
    }

    /**
     * openCamera：打开相机设备
     * 流程：1.setupCameraOutputs读取摄像头参数，创建ImageReader  2.cameraManager.openCamera打开硬件相机
     */
    private void openCamera() {
        // 二次校验权限，防止没有权限调用打开相机
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        setupCameraOutputs();
        try {
            MycameraManager.openCamera(CameraId, cameraStateCallback, MyCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * setupCameraOutputs：配置相机输出参数
     * 作用：获取摄像头ID、传感器角度、找到最大JPEG拍照尺寸、创建ImageReader对象
     */
    private void setupCameraOutputs() {

        try {
            // getCameraIdList 获取本机全部摄像头ID；下标0一般后置摄像头
            CameraId = MycameraManager.getCameraIdList()[0];
            // 获取该摄像头全部硬件特性
            CameraCharacteristics characteristics = MycameraManager.getCameraCharacteristics(CameraId);
            // 获取传感器物理旋转角度；拍照图片原始数据是横屏，保存图片时需要旋转修正方向
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            // StreamConfigurationMap：保存摄像头支持的全部输出尺寸（预览尺寸、拍照尺寸）
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // 遍历JPEG输出尺寸，找到分辨率最大的尺寸，拍照用最大分辨率

            Size largestSize = null;
            for (Size size : map.getOutputSizes(ImageFormat.JPEG)) {
                if (largestSize == null ||
                        size.getWidth() * size.getHeight() > largestSize.getWidth() * largestSize.getHeight()) {
                    largestSize = size;
                }
            }
            photoHeight = largestSize.getHeight();
            photoWidth = largestSize.getWidth();
            // 创建ImageReader：专门接收拍照输出JPEG图像数据
            // 参数：宽、高、图像格式、最多缓存几张图片
            MyimageReader = ImageReader.newInstance(photoWidth, photoHeight, ImageFormat.JPEG, 3);
            // 设置图像可用回调：拍照完成，底层拿到图片，回调 onImageAvailable
            // 回调运行在 camerahandler 指定的后台线程

            MyimageReader.setOnImageAvailableListener(onImageAvailableListener, MyCameraHandler);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }


    /**
     * imageAvailableListener：ImageReader回调，拍照完成触发！
     * 拍照硬件处理完成，JPEG图像数据就绪，回调到此方法；运行在后台相机线程
     */
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {

            // acquireNextImage() 获取一张图像数据；注意用完必须close！否则缓冲区占满，再也收不到照片

            Image image = reader.acquireNextImage();
            // JPEG格式图像，只有planes[0]存有有效数据

            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            // 把ByteBuffer转为byte[]字节数组，就是JPEG原始文件流

            byte[] bytes = new byte[byteBuffer.remaining()];

            byteBuffer.get(bytes);

            image.close();// ⚠️极其重要，释放Image缓冲区，不写这里拍照只会执行一次然后卡死
            savaImage(bytes); // 保存图片字节数组到本地文件

        }
    };


    /**
     * cameraStateCallback：CameraDevice打开状态回调
     * openCamera()之后，硬件相机打开成功/断开/出错，都会走到这里；运行在 camerahandler后台线程
     */
    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {

        /**
         * ✅相机打开成功！！
         * 只有走到onOpened，cameraDevice对象才有效；下一步：创建CameraCaptureSession会话
         */
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

            camera.close();
            MycameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

            camera.close();  // 必须关闭释放
            MycameraDevice = null;
        }

        @Override
        public void onOpened(@NonNull CameraDevice camera) {

            MycameraDevice = camera;
            //创建会话
            creatPrevwSession();// 创建预览会话，这一步才真正出画面
        }
    };

    /**
     * creatPrevwSession 创建【CameraCaptureSession捕获会话】
     * 重点规则：创建会话的时候，必须一次性把所有将来要用到的Surface全部传进去！
     * 本项目2个Surface：
     * 1. previewSurface：预览输出到TextureView
     * 2. imageReader.getSurface()：拍照输出给ImageReader
     * 会话创建成功之后，才能发送预览请求、拍照请求
     */
    private void creatPrevwSession() {
        // 获取TextureView的SurfaceTexture
        SurfaceTexture surfaceTexture = mTtextureView.getSurfaceTexture();
        // 设置缓冲区大小；这里简单使用拍照尺寸；后续优化替换成预览最优尺寸，解决拉伸
        surfaceTexture.setDefaultBufferSize(photoWidth, photoHeight);
        // SurfaceTexture包装成Surface；Camera2输出目标是Surface对象
        MypreviewSurface = new Surface(surfaceTexture);
        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(MypreviewSurface);//预览的surface
        outputSurfaces.add(MyimageReader.getSurface());//拍照的surface
        try {
            // 创建会话
            // 参数1：全部输出surface集合；参数2：会话状态回调；参数3：回调执行线程handler
            MycameraDevice.createCaptureSession(outputSurfaces, sessionStateCallback, MyCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }


    /**
     * sessionStateCallback：会话创建状态回调
     */
    private final CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {

        /**
         * 会话配置失败
         */
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Camera_2Activity.this, "配置失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * ✅会话配置成功！！到此，预览硬件通道全部就绪，可以发送预览请求
         */
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            try {
                MycameraCaptureSession = session;
                // TEMPLATE_PREVIEW：预览模板，构建预览请求
                MypreviewRequestBuilder = MycameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                // 指定输出目标：画面输出到预览surface

                MypreviewRequestBuilder.addTarget(MypreviewSurface);
                // setRepeatingRequest：【重复请求】不断循环执行，源源不断输出预览画面
                MycameraCaptureSession.setRepeatingRequest(MypreviewRequestBuilder.build(), null, MyCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }


        }
    };


    /**
     * takePicture() 点击拍照按钮触发
     * 拍照逻辑：构建单次拍照请求，调用 captureSession.capture()提交单次请求
     * setRepeatingRequest是循环预览；capture()是执行一次（拍照）
     */
    private void takePicture() {
        // 防御判断：相机设备、会话为空，代表相机没就绪，直接返回

        if (MycameraDevice == null || MycameraCaptureSession == null) {
            return;

        }

        try {
            // TEMPLATE_STILL_CAPTURE：静态拍照模板

            CaptureRequest.Builder captureRequest = MycameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 拍照输出目标：imageReader的Surface，图片数据会流入ImageReader

            captureRequest.addTarget(MyimageReader.getSurface());
            // 提交单次拍照请求！！ 缺少这行，不会真正拍照！

            MycameraCaptureSession.capture(captureRequest.build(), null, MyCameraHandler);
            // 提交之后，相机硬件拍摄完成，会回调 imageAvailableListener，拿到图片字节
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * savaImage：字节数组保存为本地图片文件
     *
     * @param bytes jpeg原始字节
     */
    private void savaImage(byte[] bytes) {
        try {
            // 先解码成 Bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                return;
            }

            // 按传感器角度旋转，照片方向才正常
            Matrix matrix = new Matrix();
            matrix.postRotate(sensorOrientation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            // 回收原始bitmap，释放内存，防止内存泄露
            bitmap.recycle();

            // 保存目录：app 私有目录下的 camera2 文件夹
            File dir = new File(getExternalFilesDir(null), "camera2");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // 文件名用时间戳，避免覆盖
            File file = new File(dir, System.currentTimeMillis() + ".jpg");

            FileOutputStream fos = new FileOutputStream(file);
            rotated.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();


            photoPathList.add(file.getAbsolutePath());
            // 切主线程提示
            runOnUiThread(() ->
                    Toast.makeText(this, "保存成功：" + file.getAbsolutePath(),
                            Toast.LENGTH_SHORT).show());

            rotated.recycle();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * closeCamera：释放相机所有资源
     * 关闭顺序：会话 → CameraDevice → ImageReader
     */

    private void closeCamera() {
        if (MycameraCaptureSession != null) {
            MycameraCaptureSession.close();
            MycameraCaptureSession = null;
        }
        if (MycameraDevice != null) {
            MycameraDevice.close();
            MycameraDevice = null;
        }
        if (MyimageReader != null) {
            MyimageReader.close();
            MyimageReader = null;
        }
    }
}
