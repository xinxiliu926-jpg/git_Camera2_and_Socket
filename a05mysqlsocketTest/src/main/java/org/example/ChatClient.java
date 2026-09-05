package org.example;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 客户端连接工具：负责连接服务器并完成登录/注册握手。
 * 登录成功后复用同一个 Socket/流进入聊天室（不能再直连 MySQL）。
 */
public class ChatClient {

    // 云服务器地址和聊天端口（改成你自己的服务器 IP）
    public static final String HOST = "8.148.221.180";
    public static final int PORT = 10000;

    /**
     * 登录会话：保存已连接的 Socket 和流，直接交给 ClientUI 使用。
     */
    public static class Session {
        public Socket socket;
        public BufferedReader reader;
        public PrintWriter writer;
        public String nickname;
    }

    /**
     * 登录：成功返回 Session（复用连接），失败返回 null，失败原因写入 failReason。
     */
    public static Session login(String username, String password, StringBuilder failReason) {
        try {
            Socket socket = new Socket(HOST, PORT);
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            writer.println("LOGIN|" + username + "|" + password);

            String line = reader.readLine();
            if (line == null) {
                failReason.append("服务器无响应");
                close(socket);
                return null;
            }

            if (line.startsWith("LOGIN_OK|")) {
                Session session = new Session();
                session.socket = socket;
                session.reader = reader;
                session.writer = writer;
                session.nickname = line.substring("LOGIN_OK|".length());
                return session;
            }

            failReason.append(line.startsWith("LOGIN_FAIL|") ? line.substring("LOGIN_FAIL|".length()) : line);
            close(socket);
            return null;
        } catch (IOException e) {
            failReason.append("无法连接服务器: ").append(e.getMessage());
            return null;
        }
    }

    /**
     * 注册：成功返回 null，失败返回错误信息。
     */
    public static String register(String username, String password, String nickname) {
        try (Socket socket = new Socket(HOST, PORT);
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            if (nickname == null) {
                nickname = "";
            }
            writer.println("REGISTER|" + username + "|" + password + "|" + nickname);

            String line = reader.readLine();
            if (line == null) {
                return "服务器无响应";
            }
            if (line.startsWith("REGISTER_OK")) {
                return null; // null 表示成功
            }
            return line.startsWith("REGISTER_FAIL|") ? line.substring("REGISTER_FAIL|".length()) : line;
        } catch (IOException e) {
            return "无法连接服务器: " + e.getMessage();
        }
    }

    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
