package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 登录界面：连接 MySQL 的 chat_db.user 表校验账号密码。
 * 登录成功后，用昵称进入聊天室 ClientUI。
 */
public class LoginUI {

    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;

    public LoginUI() {
        initializeUI();
    }

    private void initializeUI() {
        Font font = new Font("微软雅黑", Font.PLAIN, 14);

        frame = new JFrame("登录 - 聊天室");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 320);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 18));
        mainPanel.setBorder(new EmptyBorder(25, 30, 25, 30));
        mainPanel.setBackground(Color.WHITE);

        // 标题
        JLabel titleLabel = new JLabel("欢迎登录聊天室", SwingConstants.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 22));
        titleLabel.setForeground(new Color(70, 130, 180));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // 表单区
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 6, 8, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel usernameLabel = new JLabel("用户名：");
        usernameLabel.setFont(font);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(usernameLabel, gbc);

        usernameField = createTextField();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1;
        formPanel.add(usernameField, gbc);

        JLabel passwordLabel = new JLabel("密　码：");
        passwordLabel.setFont(font);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(passwordLabel, gbc);

        passwordField = createPasswordField();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1;
        formPanel.add(passwordField, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // 底部按钮
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 12));
        bottomPanel.setOpaque(false);

        loginButton = new JButton("登 录");
        loginButton.setFont(new Font("微软雅黑", Font.BOLD, 15));
        loginButton.setBackground(new Color(70, 130, 180));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        loginButton.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
        loginButton.addActionListener(e -> doLogin());
        bottomPanel.add(loginButton, BorderLayout.NORTH);

        JLabel registerLink = new JLabel("<html><u>没有账号？立即注册</u></html>", SwingConstants.CENTER);
        registerLink.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        registerLink.setForeground(new Color(70, 130, 180));
        registerLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        registerLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                goRegister();
            }
        });
        bottomPanel.add(registerLink, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // 回车触发登录
        KeyAdapter enterLogin = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doLogin();
                }
            }
        };
        usernameField.addKeyListener(enterLogin);
        passwordField.addKeyListener(enterLogin);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    private JTextField createTextField() {
        JTextField field = new JTextField();
        field.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        field.setPreferredSize(new Dimension(200, 32));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        return field;
    }

    private JPasswordField createPasswordField() {
        JPasswordField field = new JPasswordField();
        field.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        field.setPreferredSize(new Dimension(200, 32));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        return field;
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请输入用户名和密码", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loginButton.setEnabled(false);
        new Thread(() -> {
            StringBuilder failReason = new StringBuilder();
            ChatClient.Session session = ChatClient.login(username, password, failReason);
            SwingUtilities.invokeLater(() -> {
                loginButton.setEnabled(true);
                if (session != null) {
                    JOptionPane.showMessageDialog(frame, "登录成功，欢迎 " + session.nickname + "！", "成功", JOptionPane.INFORMATION_MESSAGE);
                    frame.dispose();
                    // 复用已登录的 Socket 进入聊天室
                    new ClientUI(session.socket, session.reader, session.writer, session.nickname);
                } else {
                    JOptionPane.showMessageDialog(frame, "登录失败：" + failReason, "登录失败", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private void goRegister() {
        frame.dispose();
        new RegisterUI();
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
