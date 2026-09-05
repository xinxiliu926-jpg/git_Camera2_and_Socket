package com.example.crosschat;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


import com.example.crosschat.CloseUtils;
import com.example.crosschat.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class AudioActivity extends AppCompatActivity {
    private static final String TAG = "AudioRecordActivity";
    private static final int AUDIO_RATE = 44100;
    private static final String PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/VideoDemo";
    private AudioThread mAudioThread;
    private AudioTrackThread mAudioTrackThread;

    // ============ 新增动态权限相关 ============
    private final String[] REQUEST_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private final int PERMISSION_CODE = 1001;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);

        // 启动检测权限
        if (!hasPermission()) {
            ActivityCompat.requestPermissions(this, REQUEST_PERMISSIONS, PERMISSION_CODE);
        }

        Button button = findViewById(R.id.record);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 按下二次校验权限
                if (!hasPermission()){
                    Toast.makeText(AudioActivity.this,"请授予麦克风和存储权限",Toast.LENGTH_SHORT).show();
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        //开始录制
                        startRecord();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (mAudioThread != null) {
                            mAudioThread.done();
                        }
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    // 判断权限
    private boolean hasPermission() {
        for (String p : REQUEST_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // 权限申请回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            boolean allOk = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allOk = false;
                    break;
                }
            }
            if (!allOk) {
                Toast.makeText(this, "权限拒绝，无法录音", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 开始录制
     */
    private void startRecord() {
        //如果存在，先停止线程
        if (mAudioThread != null) {
            mAudioThread.done();
            mAudioThread = null;
        }
        //开启线程录制
        mAudioThread = new AudioThread();
        mAudioThread.start();
    }

    public void playwav(View view) {

        File file = new File(PATH, "test.wav");
        if (file.exists()) {

            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            Uri uri;
            //Android 7.0 以上，需要使用 FileProvider
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(this, "com.zhengsr.videodemo.fileprovider", file);
            } else {
                uri = Uri.fromFile(file.getAbsoluteFile());
            }
            intent.setDataAndType(uri, "audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(intent);
        } else {
            Toast.makeText(this, "请先录制", Toast.LENGTH_SHORT).show();
        }
    }

    public void playpcm(View view) {
        if (mAudioTrackThread != null) {
            mAudioTrackThread.down();
            mAudioTrackThread = null;
        }
        //播放pcm文件
        mAudioTrackThread = new AudioTrackThread();
        mAudioTrackThread.start();
    }

    public void playpcm2(View view) {
        try {
            File file = new File(PATH, "test.pcm");
            InputStream is = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int len;
            byte[] buffer = new byte[1024];
            while ((len = is.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            byte[] bytes = baos.toByteArray();

            int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(AUDIO_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(channelConfig)
                    .build();
            AudioTrack audioTrack = new AudioTrack(
                    attributes,
                    format,
                    bytes.length,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );
            audioTrack.write(bytes, 0, bytes.length);
            audioTrack.play();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "zsr playpcm2: " + e);
        }
    }

    /**
     * 音频录制线程
     */
    class AudioThread extends Thread {
        private AudioRecord record;
        private int minBufferSize;
        private boolean isDone = false;

        public AudioThread() {
            minBufferSize = AudioRecord.getMinBufferSize(AUDIO_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
//            record = new AudioRecord(
//                    MediaRecorder.AudioSource.MIC,
//                    AUDIO_RATE,
//                    AudioFormat.CHANNEL_IN_STEREO,
//                    AudioFormat.ENCODING_PCM_16BIT,
//                    minBufferSize
//            );
//        }
        }
        @Override
        public void run() {
            super.run();
            FileOutputStream fos = null;
            FileOutputStream wavFos = null;
            RandomAccessFile wavRaf = null;
            try {
                File dir = new File(PATH);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File pcmFile = getFile(PATH, "test.pcm");
                File wavFile = getFile(PATH, "test.wav");
                fos = new FileOutputStream(pcmFile);
                wavFos = new FileOutputStream(wavFile);

                byte[] headers = generateWavFileHeader(0, AUDIO_RATE, record.getChannelCount());
                wavFos.write(headers, 0, headers.length);

                record.startRecording();
                byte[] buffer = new byte[minBufferSize];
                while (!isDone) {
                    int read = record.read(buffer, 0, buffer.length);
                    if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                        fos.write(buffer, 0, read);
                        wavFos.write(buffer, 0, read);
                    }
                }
                record.stop();
                record.release();

                fos.flush();
                wavFos.flush();

                wavRaf = new RandomAccessFile(wavFile, "rw");
                byte[] header = generateWavFileHeader(pcmFile.length(), AUDIO_RATE, record.getChannelCount());
                wavRaf.seek(0);
                wavRaf.write(header);

            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "zsr run: " + e.getMessage());
            } finally {
                CloseUtils.close(fos, wavFos,wavRaf);
            }
        }

        public void done() {
            interrupt();
            isDone = true;
        }
    }

    class AudioTrackThread extends Thread {
        AudioTrack audioTrack;
        private final int bufferSize;
        private boolean isDone;

        public AudioTrackThread() {
            int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(AUDIO_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(channelConfig)
                    .build();
            bufferSize = AudioTrack.getMinBufferSize(AUDIO_RATE, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
            audioTrack = new AudioTrack(
                    attributes,
                    format,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );
            audioTrack.play();
        }

        @Override
        public void run() {
            super.run();
            File file = new File(PATH, "test.pcm");
            if (file.exists()) {
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(file);
                    byte[] buffer = new byte[bufferSize];
                    int len;
                    while (!isDone && (len = fis.read(buffer)) > 0) {
                        audioTrack.write(buffer, 0, len);
                    }
                    audioTrack.stop();
                    audioTrack.release();
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(TAG, "zsr run: " + e);
                } finally {
                    CloseUtils.close(fis);
                }
            }
        }

        void down() {
            isDone = true;
        }
    }

    private File getFile(String path, String name) {
        File file = new File(path, name);
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
            return file;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] generateWavFileHeader(long pcmAudioByteCount, long longSampleRate, int channels) {
        long totalDataLen = pcmAudioByteCount + 36;
        long byteRate = longSampleRate * 2 * channels;
        byte[] header = new byte[44];
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';

        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);

        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';

        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';

        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;

        header[20] = 1;
        header[21] = 0;

        header[22] = (byte) channels;
        header[23] = 0;

        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);

        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);

        header[32] = (byte) (2 * channels);
        header[33] = 0;

        header[34] = 16;
        header[35] = 0;

        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';

        header[40] = (byte) (pcmAudioByteCount & 0xff);
        header[41] = (byte) ((pcmAudioByteCount >> 8) & 0xff);
        header[42] = (byte) ((pcmAudioByteCount >> 16) & 0xff);
        header[43] = (byte) ((pcmAudioByteCount >> 24) & 0xff);
        return header;
    }
}