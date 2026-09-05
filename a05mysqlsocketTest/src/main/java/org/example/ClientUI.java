package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ClientUI {
    private JFrame frame;
    private JTextArea messageArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton fileButton;
    private JLabel statusLabel;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private String userName;
    private JLabel imageLabel;

    public ClientUI() {
        this(JOptionPane.showInputDialog(null, "请输入你的聊天室昵称：", "登录", JOptionPane.PLAIN_MESSAGE));
    }

    // 旧流程：直接按昵称连接（发送昵称作为身份，不校验账号）
    public ClientUI(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "匿名用户";
        }
        this.userName = nickname;

        initializeUI();
        connectToServer();
    }

    // 新流程：登录成功后复用已连接的 Socket 进入聊天室
    public ClientUI(Socket socket, BufferedReader reader, PrintWriter writer, String nickname) {
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
        this.userName = (nickname == null || nickname.trim().isEmpty()) ? "匿名用户" : nickname;

        initializeUI();
        statusLabel.setText("● 已连接");
        statusLabel.setForeground(new Color(0, 150, 0));
        appendMessage("系统", "已成功连接到聊天室！");
        new Thread(this::receiveMessages).start();
    }

    private void initializeUI() {
        Font font = new Font("微软雅黑", Font.PLAIN, 14);

        frame = new JFrame("聊天室 - " + userName);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 240));

        // 状态栏
        statusLabel = new JLabel("● 连接中...", SwingConstants.CENTER);
        statusLabel.setFont(font);
        statusLabel.setForeground(new Color(0, 150, 0));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(230, 230, 230));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        mainPanel.add(statusLabel, BorderLayout.NORTH);

        // 中间区域 - 消息和图片预览
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        centerPanel.setBackground(new Color(240, 240, 240));

        // 消息显示区域
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        messageArea.setBackground(Color.WHITE);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        centerPanel.add(scrollPane);

        // 图片预览区域
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBackground(Color.WHITE);
        previewPanel.setBorder(BorderFactory.createTitledBorder("图片预览"));

        imageLabel = new JLabel("暂无图片", SwingConstants.CENTER);
        imageLabel.setFont(new Font("微软雅黑", Font.PLAIN, 16));
        imageLabel.setForeground(Color.GRAY);
        imageLabel.setPreferredSize(new Dimension(300, 300));
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);

        JScrollPane imageScrollPane = new JScrollPane(imageLabel);
        imageScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        imageScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        previewPanel.add(imageScrollPane, BorderLayout.CENTER);

        centerPanel.add(previewPanel);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // 底部输入区域
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBackground(new Color(240, 240, 240));

        inputField = new JTextField();
        inputField.setFont(font);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        bottomPanel.add(inputField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(new Color(240, 240, 240));

        fileButton = new JButton("📎 文件");
        fileButton.setFont(font);
        fileButton.setBackground(new Color(240, 240, 240));
        fileButton.setFocusPainted(false);
        fileButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        fileButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        buttonPanel.add(fileButton);

        sendButton = new JButton("发送");
        sendButton.setFont(font);
        sendButton.setBackground(new Color(70, 130, 180));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 130, 180)),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)
        ));
        buttonPanel.add(sendButton);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);

        // 事件监听
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        fileButton.addActionListener(e -> selectFile());
    }

    private void connectToServer() {
        try {
            socket = new Socket(ChatClient.HOST, ChatClient.PORT);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            writer.println(userName);

            statusLabel.setText("● 已连接");
            statusLabel.setForeground(new Color(0, 150, 0));
            appendMessage("系统", "已成功连接到聊天室！");

            new Thread(this::receiveMessages).start();

        } catch (IOException e) {
            statusLabel.setText("● 连接失败");
            statusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(frame,
                    "无法连接到服务器:\n" + e.getMessage(),
                    "连接错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void receiveMessages() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> handleReceivedMessage(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("● 已断开");
                statusLabel.setForeground(Color.RED);
                appendMessage("系统", "与服务器断开连接");
            });
        }
    }

    private void handleReceivedMessage(String msg) {
        if (msg.startsWith("FILE_INFO|")) {
            handleFileInfo(msg);
        } else if (msg.startsWith("FILE_IMAGE|")) {
            handleImageFile(msg);
        } else if (msg.contains("【") && msg.contains("】")) {
            appendMessage("系统", msg);
        } else {
            int colonIndex = msg.indexOf(":");
            if (colonIndex > 0) {
                String sender = msg.substring(0, colonIndex);
                String content = msg.substring(colonIndex + 1).trim();
                appendMessage(sender, content);
            } else {
                appendMessage("未知", msg);
            }
        }
    }

    private void handleFileInfo(String msg) {
        try {
            String[] parts = msg.split("\\|");
            if (parts.length >= 4) {
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                boolean isImage = Boolean.parseBoolean(parts[3]);
                String filePath = parts.length > 4 ? parts[4] : "";

                appendMessage("系统", "收到文件: " + fileName + " (大小: " +
                        formatFileSize(fileSize) + ")" + (isImage ? " [图片]" : ""));

                if (!isImage) {
                    appendMessage("系统", "文件已保存到服务器: " + filePath);
                }
            }
        } catch (Exception e) {
            System.err.println("解析文件信息失败: " + e.getMessage());
        }
    }

    private void handleImageFile(String msg) {
        try {
            String[] parts = msg.split("\\|", 4);
            if (parts.length >= 4) {
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                String base64Data = parts[3];

                appendMessage("系统", "收到图片: " + fileName + " (大小: " +
                        formatFileSize(fileSize) + ")");

                displayImage(base64Data, fileName);
            }
        } catch (Exception e) {
            System.err.println("解析图片数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void displayImage(String base64Data, String fileName) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            ImageIcon imageIcon = new ImageIcon(imageBytes);

            int maxWidth = 280;
            int maxHeight = 280;
            Image image = imageIcon.getImage();
            int width = imageIcon.getIconWidth();
            int height = imageIcon.getIconHeight();

            double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
            if (scale < 1) {
                width = (int) (width * scale);
                height = (int) (height * scale);
                image = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                imageIcon = new ImageIcon(image);
            }

            imageLabel.setText("");
            imageLabel.setIcon(imageIcon);
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);
            imageLabel.setToolTipText(fileName + " (点击查看大图)");

            // 点击查看大图
            imageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    showLargeImage(imageBytes, fileName);
                }
            });

        } catch (Exception e) {
            System.err.println("显示图片失败: " + e.getMessage());
            e.printStackTrace();
            appendMessage("系统", "图片显示失败: " + e.getMessage());
        }
    }

    private void showLargeImage(byte[] imageBytes, String fileName) {
        JDialog dialog = new JDialog(frame, "图片查看 - " + fileName, true);
        dialog.setSize(600, 600);
        dialog.setLocationRelativeTo(frame);

        try {
            ImageIcon originalIcon = new ImageIcon(imageBytes);
            JLabel label = new JLabel(originalIcon);

            JScrollPane scrollPane = new JScrollPane(label);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

            dialog.add(scrollPane);
            dialog.setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(dialog, "无法显示大图: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        if (writer != null) {
            writer.println(message);
            inputField.setText("");
            inputField.requestFocus();
        } else {
            JOptionPane.showMessageDialog(frame, "未连接到服务器", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择文件");

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            new Thread(() -> {
                try {
                    if (socket == null || socket.isClosed()) {
                        SwingUtilities.invokeLater(() -> appendMessage("系统", "未连接服务器，无法发送文件"));
                        return;
                    }

                    long fileSize = selectedFile.length();
                    String fileName = selectedFile.getName();

                    // 发送文件头 - 使用 writer 发送
                    writer.println("FILE|" + fileName + "|" + fileSize);
                    writer.flush();

                    // 获取输出流发送文件数据
                    OutputStream outputStream = socket.getOutputStream();
                    BufferedOutputStream bos = new BufferedOutputStream(outputStream);
                    FileInputStream fis = new FileInputStream(selectedFile);
                    BufferedInputStream bis = new BufferedInputStream(fis);

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long sent = 0;

                    while ((bytesRead = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        sent += bytesRead;
                        bos.flush(); // 重要：每写一块就刷新一次
                    }

                    bos.flush(); // 最后再刷新一次

                    // 不要关闭流！不要关闭socket！
                    // 只关闭文件流
                    bis.close();
                    fis.close();
                    // 注意：不要关闭 bos 和 outputStream，否则会关闭socket连接

                    SwingUtilities.invokeLater(() ->
                            appendMessage("系统", "文件发送完成: " + fileName)
                    );

                } catch (IOException e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() ->
                            appendMessage("系统", "文件发送失败: " + e.getMessage())
                    );
                }
            }).start();
        }
    }

    private void appendMessage(String sender, String content) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        StringBuilder sb = new StringBuilder();

        if ("系统".equals(sender)) {
            sb.append("           ");
            sb.append("[");
            sb.append(time);
            sb.append("] ");
            sb.append(content);
            sb.append("\n\n");
        } else {
            sb.append("[");
            sb.append(time);
            sb.append("] ");
            sb.append(sender);
            sb.append(": ");
            sb.append(content);
            sb.append("\n");
        }

        messageArea.append(sb.toString());
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(LoginUI::new);
    }
}