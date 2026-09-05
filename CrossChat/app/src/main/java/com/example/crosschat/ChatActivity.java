package com.example.crosschat;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatActivity extends AppCompatActivity {

    private  Button butFile;
    private Button butSend;//按钮发送
    private String username;//接受上一个的名称

    private EditText etInputMsg;//输入框

    private TextView tvMsgShow;//展示界面

    private  Socket socket;//定义全局变量只连接一次socket

    private  OutputStream outputStream;

    private boolean isConnect=false;

    private Uri selectedFileUri; // 保存选中文件Uri
    private String selectedFileName; // 保存文件名

    //回调文件接受器
    private final ActivityResultLauncher<Intent> fileChooseLauncher=
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult o) {
                            if(o.getResultCode()==RESULT_OK&&o.getData()!=null)//拿到数据不为空，并且客户选择了欧克
                            {
                                Intent dataIntent=o.getData();
                                Uri fileUri=dataIntent.getData();
                                if(fileUri!=null)
                                {
                                    selectedFileUri=fileUri;// 保存选中的文件

                                    selectedFileName=getFileNameByUri(fileUri);//获取文件名

                                    runOnUiThread(()->
                                    {
                                        Toast.makeText(ChatActivity.this,"已选中文件："+selectedFileName,Toast.LENGTH_SHORT).show();
                                    });

                                }


                            }
                        }
                    }

            );




    @Override
    protected void onCreate( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);//添加xml页面

      username = getIntent().getStringExtra("username");
        butSend = findViewById(R.id.btn_send);
        etInputMsg = findViewById(R.id.et_input_msg);
        tvMsgShow=findViewById(R.id.tv_msg_show);
        butFile=findViewById(R.id.btn_file);

        new Thread(this::initSocketConnect).start();//子线程

        butFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector();
            }
        });
        butSend.setOnClickListener(v->sendMessage());//lamble表达式



    }


    private  void initSocketConnect()
    {
        try {
            socket=new Socket("8.148.221.180",10000);
            outputStream = socket.getOutputStream();
            isConnect=true;
            sendRaw(username+"\n");

            InputStream is = socket.getInputStream();
            InputStreamReader isr = new InputStreamReader(is,StandardCharsets.UTF_8);
            BufferedReader bf = new BufferedReader(isr);

            String line;
            while ((line=bf.readLine())!=null)
            {
                String finalLine = line;
                System.out.println("服务器转发消息：" + finalLine);
                // 子线程不能更新UI，切主线程更新聊天框
                runOnUiThread(()->{
                    if(finalLine.startsWith("FILE_IMAGE|")){
                        // 分割协议：FILE_IMAGE|文件名|大小|base64
                        String[] parts = finalLine.split("\\|",4);
                        if(parts.length ==4){
                            String base64Data = parts[3];
                            tvMsgShow.append("【收到图片，自动打开预览】\n");

                            // 自动跳转预览页面
                            Intent intent = new Intent(ChatActivity.this, ImagePreviewActivity.class);
                            intent.putExtra(ImagePreviewActivity.EXTRA_BASE64, base64Data);
                            startActivity(intent);
                        }
                    }else{
                        tvMsgShow.append(finalLine+"\n");
                    }
                });


            }


        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(()->Toast.makeText(ChatActivity.this,"服务器连接失败",Toast.LENGTH_SHORT).show());

        }
        finally {
            // 连接断开
            System.out.println("🔴 Socket连接已结束，进入finally");
            isConnect = false;
            closeSocket();
        }

    }

//获取文件名的工具方法
    private String getFileNameByUri(Uri uri){
        String displayName = "";
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)){
            if(cursor != null && cursor.moveToFirst()){
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                displayName = cursor.getString(index);
            }
        }
        return displayName;
    }

    /**
     * 按钮点击调用：发送消息
     */
    private void sendMessage() {
        if (!isConnect) {
            Toast.makeText(this, "未连接服务器", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedFileUri != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    sendFile(selectedFileUri, selectedFileName);
                }
            }).start();
            selectedFileUri = null;
            selectedFileName = null;
            etInputMsg.setText("");
            return;

        }
        // 获取输入框内容
        String msg = etInputMsg.getText().toString().trim();
        if (msg.isEmpty()) {
            Toast.makeText(this, "不能发送空消息", Toast.LENGTH_SHORT).show();
            return;
        }

        // 887下线指令
        if ("887".equals(msg)) {
            new Thread(this::closeSocket).start();
            runOnUiThread(() -> Toast.makeText(ChatActivity.this, "已下线", Toast.LENGTH_SHORT).show());
            return;
        }

        // 发送消息，末尾加换行符，给readLine识别
        new Thread(() -> sendRaw(msg + "\n")).start();

        // 发送完清空输入框
        etInputMsg.setText("");
    }
    private void sendRaw(String content)
    {
        try {
            if(outputStream!=null)
            {
                outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

            }
        } catch (IOException e) {
            System.out.println("发送消息失败："+e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 文件发送逻辑，运行在子线程
     */
    private void sendFile(Uri fileUri,String fileName)
    {
        try {
            InputStream inputStream=getContentResolver().openInputStream(fileUri);

            if(isImageFile(fileName)){
                // ========== 图片：使用 FILE_BASE64 协议 ==========
                byte[] fileBytes = readAllBytesCompat(inputStream);
                inputStream.close();
                String base64Str = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP);
                String header = "FILE_BASE64|" + fileName + "|" + base64Str + "\n";
                sendRaw(header);

                runOnUiThread(()->{
                    tvMsgShow.append("【发送图片】"+fileName+" 发送完成\n");
                });
            }else{
                // ========== 普通文件：保留原有 FILE| 二进制分片 ==========
                byte[] buffer=new byte[4096];
                int len;
                long fileSize=inputStream.available();
                String fileHeader= "FILE|" + fileName + "|" + fileSize + "\n";
                sendRaw(fileHeader);
                while ((len=inputStream.read(buffer))!=-1) {
                    if (outputStream != null) {
                        outputStream.write(buffer, 0, len);
                        outputStream.flush();
                    }
                }
                inputStream.close();

                runOnUiThread(()->{
                    tvMsgShow.append("【发送文件】"+fileName+" 发送完成\n");
                });
            }

        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(()->{
                Toast.makeText(ChatActivity.this,"文件发送失败",Toast.LENGTH_SHORT).show();
            });
        }


    }
    private void closeSocket()
    {
        try {
            if(outputStream!=null)
            {
                outputStream.close();
            }
            if(socket!=null)
            {
                socket.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        isConnect=false;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeSocket();
    }



    private boolean isImageFile(String fileName){
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".bmp") || lower.endsWith(".webp")
                || lower.endsWith(".ico");
    }
    private void openFileSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // 选择所有类型文件：*/*
        // 只选图片：image/*
        // 多选文件：intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        fileChooseLauncher.launch(intent);

    }



    /**
     * 兼容minSdk24，替代 inputStream.readAllBytes()
     */
    private byte[] readAllBytesCompat(InputStream inputStream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        return bos.toByteArray();
    }
}
