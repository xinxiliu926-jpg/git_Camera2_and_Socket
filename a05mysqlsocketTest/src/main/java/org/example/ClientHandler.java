package org.example;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private static final String UPLOAD_DIR = "/opt/chat_uploads";

    static {
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String username = null;
        try {
            InputStream is = socket.getInputStream();
            InputStreamReader ios = new InputStreamReader(is, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(ios);

            String firstLine = br.readLine();
            if (firstLine == null) {
                return;
            }

            // ===== 注册请求：只做注册，不进聊天室 =====
            if (firstLine.startsWith("REGISTER|")) {
                handleRegister(firstLine);
                return;
            }

            // ===== 登录请求：校验通过后才进聊天室 =====
            if (firstLine.startsWith("LOGIN|")) {
                String[] parts = firstLine.split("\\|", 3); // LOGIN|用户名|密码
                if (parts.length < 3) {
                    sendLine("LOGIN_FAIL|登录格式错误");
                    return;
                }
                UserDao dao = new UserDao();
                if (dao.login(parts[1], parts[2])) {
                    username = parts[1]; // 用唯一用户名作为聊天身份
                    sendLine("LOGIN_OK|" + dao.getNickname(parts[1]));
                } else {
                    sendLine("LOGIN_FAIL|用户名或密码错误");
                    return;
                }
            } else {
                // 兼容旧客户端（如 Android）：直接发昵称/用户名，不做校验
                username = firstLine;
            }

            if (username == null || username.trim().length() == 0) {
                return;
            }
            ClientUser user;
            try {
                user = new ClientUser(socket, username);
            } catch (IOException e) {
                System.out.println("初始化客户端输出流失败，断开连接");
                return;
            }

            synchronized (Server.LOCK) {
                Server.ONLINE_SOCKET_LIST.add(user);
                Server.User_Map.put(username, user);
                System.out.println("【" + username + "】" + "进入聊天室,人数为" + Server.ONLINE_SOCKET_LIST.size());
            }

            String line;
            while ((line = br.readLine()) != null) {
                if ("887".equals(line)) {
                    System.out.println(username + "发送下线指令887");
                    break;
                }

                // 新增：接收安卓Base64图片消息
                if (line.startsWith("FILE_BASE64|")) {
                    handleBase64Image(line, user, username);
                    continue;}

                if (line.startsWith("FILE|")) {
                    // 传递 username 和 user 对象
                    handleFileTransfer(line, user, username);
                    continue;
                }

                if (line.startsWith("@")) {
                    handlePrivateMsg(user, line);
                } else {
                    String Megs = username + ":" + line;
                    System.out.println("广播：" + Megs);
                    sendAllClient(Megs, username);
                }
            }
            System.out.println("客户端正常断开");

        } catch (IOException e) {
            System.out.println("客户端异常断开" + e.getMessage());
        } finally {
            if (username != null) {
                synchronized (Server.LOCK) {
                    Server.ONLINE_SOCKET_LIST.removeIf(u -> u.getSocket() == socket);
                    Server.User_Map.remove(username);
                    System.out.println("【" + username + "】离开聊天室，剩余在线：" + Server.ONLINE_SOCKET_LIST.size());
                }
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理注册请求：REGISTER|用户名|密码|昵称
     */
    private void handleRegister(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 3) {
            sendLine("REGISTER_FAIL|注册格式错误");
            return;
        }
        String nickname = parts.length > 3 ? parts[3] : "";
        UserDao dao = new UserDao();
        if (dao.register(parts[1], parts[2], nickname)) {
            sendLine("REGISTER_OK");
        } else {
            sendLine("REGISTER_FAIL|注册失败，用户名可能已存在");
        }
    }

    /**
     * 向当前连接写一行（UTF-8 + 换行）
     */
    private void sendLine(String line) {
        try {
            OutputStream os = socket.getOutputStream();
            os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            System.out.println("发送失败: " + e.getMessage());
        }
    }

    private void handleBase64Image(String line, ClientUser user, String username) {
        try {
            // 只分割前两个 | ，base64内部会包含 + / = 不能全部分割
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3) {
                System.out.println("Base64图片头格式错误：" + line);
                return;
            }
            String fileName = parts[1];
            String base64Str = parts[2];

            // base64解码成图片字节
            byte[] imgBytes = Base64.getDecoder().decode(base64Str);

            // 保存图片到服务器上传目录
            String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
            String filePath = UPLOAD_DIR + File.separator + uniqueFileName;
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(imgBytes);
            }

            long fileSize = imgBytes.length;
            FileInfo fileInfo = new FileInfo(fileName, fileSize, filePath, true);
            fileInfo.setBase64Data(base64Str);

            // 广播 FILE_IMAGE 给所有客户端，安卓收到自动跳转预览页
            broadcastFileToAll(fileInfo, username);

        } catch (Exception e) {
            e.printStackTrace();
            sendPrivateMsg(user, "【系统】图片接收失败");
        }

    }

    private void handleFileTransfer(String headerLine, ClientUser sender, String senderName) {
        try {
            String[] parts = headerLine.split("\\|");
            if (parts.length != 3) {
                System.out.println("文件头格式错误: " + headerLine);
                return;
            }

            String fileName = parts[1];
            long fileSize = Long.parseLong(parts[2]);

            System.out.println("接收文件: " + fileName + " (大小: " + fileSize + " bytes) - 来自: " + senderName);

            String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
            String filePath = UPLOAD_DIR + File.separator + uniqueFileName;

            // 接收文件数据
            InputStream rawIn = socket.getInputStream();
            FileOutputStream fos = new FileOutputStream(filePath);
            BufferedOutputStream bos = new BufferedOutputStream(fos);

            byte[] buffer = new byte[8192];
            long received = 0;
            int bytesRead;

            while (received < fileSize && (bytesRead = rawIn.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                received += bytesRead;
            }

            bos.flush();
            bos.close();

            System.out.println("文件接收完成: " + filePath + "，实际接收: " + received + " bytes");

            boolean isImage = isImageFile(fileName);

            FileInfo fileInfo = new FileInfo(fileName, fileSize, filePath, isImage);

            if (isImage) {
                String base64Data = encodeFileToBase64(filePath);
                fileInfo.setBase64Data(base64Data);
                System.out.println("图片已转换为Base64，数据长度: " + base64Data.length());
            }

            // 广播文件给所有客户端（包括发送者）
            broadcastFileToAll(fileInfo, senderName);

            // 不关闭任何流，保持连接

        } catch (Exception e) {
            System.err.println("文件接收失败: " + e.getMessage());
            e.printStackTrace();
            try {
                String errorMsg = "【系统】文件上传失败: " + e.getMessage();
                sendPrivateMsg(sender, errorMsg);
            } catch (Exception ex) {
                System.err.println("无法通知发送者上传失败: " + ex.getMessage());
            }
        }
    }


    /**
     * 广播文件给所有客户端（包括发送者）
     */
    private void broadcastFileToAll(FileInfo fileInfo, String senderName) {
        String broadcastMsg;

        if (fileInfo.isImage() && fileInfo.getBase64Data() != null) {
            broadcastMsg = "FILE_IMAGE|" + fileInfo.getFileName() + "|" +
                    fileInfo.getFileSize() + "|" + fileInfo.getBase64Data();
        } else {
            broadcastMsg = "FILE_INFO|" + fileInfo.getFileName() + "|" +
                    fileInfo.getFileSize() + "|" + fileInfo.isImage() + "|" +
                    fileInfo.getFilePath();
        }

        String textMsg = "【系统】" + senderName + " 发送了文件: " + fileInfo.getFileName() +
                " (大小: " + formatFileSize(fileInfo.getFileSize()) + ")" +
                (fileInfo.isImage() ? " [图片]" : "");

        synchronized (Server.LOCK) {
            for (ClientUser user : Server.ONLINE_SOCKET_LIST) {
                try {
                    OutputStream os = user.getOut();
                    // 发送文件数据
                    os.write((broadcastMsg + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    // 发送文字提示
                    os.write((textMsg + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException e) {
                    System.out.println("广播发送失败:" + user.getUsername());
                }
            }
        }
    }


    private boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                lowerName.endsWith(".bmp") || lowerName.endsWith(".webp") ||
                lowerName.endsWith(".ico");
    }

    private String encodeFileToBase64(String filePath) throws IOException {
        File file = new File(filePath);
        byte[] bytes = new byte[(int) file.length()];

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(bytes);
        }

        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 广播文件给所有其他客户端（不包括发送者）
     */
    private void broadcastFileToOthers(FileInfo fileInfo, String senderName) {
        String broadcastMsg;

        if (fileInfo.isImage() && fileInfo.getBase64Data() != null) {
            broadcastMsg = "FILE_IMAGE|" + fileInfo.getFileName() + "|" +
                    fileInfo.getFileSize() + "|" + fileInfo.getBase64Data();
        } else {
            broadcastMsg = "FILE_INFO|" + fileInfo.getFileName() + "|" +
                    fileInfo.getFileSize() + "|" + fileInfo.isImage() + "|" +
                    fileInfo.getFilePath();
        }

        // 先发送文件消息给其他客户端
        String textMsg = "【系统】" + senderName + " 发送了文件: " + fileInfo.getFileName() +
                " (大小: " + formatFileSize(fileInfo.getFileSize()) + ")" +
                (fileInfo.isImage() ? " [图片]" : "");

        synchronized (Server.LOCK) {
            for (ClientUser user : Server.ONLINE_SOCKET_LIST) {
                // 排除发送者自己
                if (user.getUsername().equals(senderName)) {
                    continue;
                }
                try {
                    OutputStream os = user.getOut();
                    // 发送文件数据
                    os.write((broadcastMsg + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    // 发送文字提示
                    os.write((textMsg + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException e) {
                    System.out.println("广播发送失败:" + user.getUsername());
                }
            }
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }

    private void sendAllClient(String msg, String senderName) {
        synchronized (Server.LOCK) {
            for (ClientUser user : Server.ONLINE_SOCKET_LIST) {
                // 不再排除发送者，让所有人都能看到
                try {
                    OutputStream os = user.getOut();
                    os.write((msg + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException e) {
                    System.out.println("广播发送失败:" + user.getUsername());
                }
            }
        }
    }

    private void handlePrivateMsg(ClientUser clientUser, String msg) {
        String sub = msg.substring(1);
        int index = sub.indexOf(" ");
        if (index <= 0) {
            sendPrivateMsg(clientUser, "格式不对,用户重新输入");
            return;
        }
        String targetName = sub.substring(0, index);
        String content = sub.substring(index + 1);
        synchronized (Server.LOCK) {
            ClientUser user = Server.User_Map.get(targetName);
            if (user == null) {
                sendPrivateMsg(clientUser, "系统提示：用户【" + targetName + "】不在线，私聊失败");
                return;
            }
            String toTarget = "【私聊】" + clientUser.getUsername() + "对你说：" + content;
            sendPrivateMsg(user, toTarget);
            String toSender = "【私聊对你发送给" + targetName + "】：" + content;
            sendPrivateMsg(clientUser, toSender);
        }
    }

    private void sendPrivateMsg(ClientUser clientUser, String msg) {
        try {
            OutputStream stream = clientUser.getOut();
            stream.write((msg + "\n").getBytes(StandardCharsets.UTF_8));
            stream.flush();
        } catch (IOException e) {
            System.out.println("私聊发送失败:" + clientUser.getUsername());
        }
    }

    private static class FileInfo {
        private String fileName;
        private long fileSize;
        private String filePath;
        private boolean isImage;
        private String base64Data;

        public FileInfo(String fileName, long fileSize, String filePath, boolean isImage) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.filePath = filePath;
            this.isImage = isImage;
        }

        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public String getFilePath() { return filePath; }
        public boolean isImage() { return isImage; }
        public String getBase64Data() { return base64Data; }
        public void setBase64Data(String base64Data) { this.base64Data = base64Data; }
    }
}