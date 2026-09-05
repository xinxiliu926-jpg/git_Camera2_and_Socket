package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 注册界面：向 MySQL chat_db.user 表插入新用户。
 */
public class RegisterUI {

    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmField;
    private JTextField nicknameField;
    private JButton registerButton;

    public RegisterUI() {
        initializeUI();
    }

    private void initializeUI() {
        Font font = new Font("微软雅黑", Font.PLAIN, 14);

        frame = new JFrame("注册 - 聊天室");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(440, 400);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 18));
        mainPanel.setBorder(new EmptyBorder(25, 30, 25, 30));
        mainPanel.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel("注册新账号", SwingConstants.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 22));
        titleLabel.setForeground(new Color(70, 130, 180));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 6, 7, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        usernameField = createTextField();
        addRow(formPanel, gbc, 0, "用户名：", usernameField, font);

        passwordField = createPasswordField();
        addRow(formPanel, gbc, 1, "密　码：", passwordField, font);

        confirmField = createPasswordField();
        addRow(formPanel, gbc, 2, "确认密码：", confirmField, font);

        nicknameField = createTextField();
        addRow(formPanel, gbc, 3, "昵称（选填）：", nicknameField, font);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 12));
        bottomPanel.setOpaque(false);

        registerButton = new JButton("注 册");
        registerButton.setFont(new Font("微软雅黑", Font.BOLD, 15));
        registerButton.setBackground(new Color(70, 130, 180));
        registerButton.setForeground(Color.WHITE);
        registerButton.setFocusPainted(false);
        registerButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        registerButton.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
        registerButton.addActionListener(e -> doRegister());
        bottomPanel.add(registerButton, BorderLayout.NORTH);

        JLabel backLink = new JLabel("<html><u>已有账号？返回登录</u></html>", SwingConstants.CENTER);
        backLink.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        backLink.setForeground(new Color(70, 130, 180));
        backLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                goLogin();
            }
        });
        bottomPanel.add(backLink, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // 回车触发注册
        KeyAdapter enterRegister = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doRegister();
                }
            }
        };
        usernameField.addKeyListener(enterRegister);
        passwordField.addKeyListener(enterRegister);
        confirmField.addKeyListener(enterRegister);
        nicknameField.addKeyListener(enterRegister);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, JComponent field, Font font) {
        JLabel label = new JLabel(labelText);
        label.setFont(font);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(label, gbc);
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        panel.add(field, gbc);
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

    private void doRegister() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());
        String nickname = nicknameField.getText().trim();

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请输入用户名", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请输入密码", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (password.length() < 6) {
            JOptionPane.showMessageDialog(frame, "密码长度至少 6 位", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!password.equals(confirm)) {
            JOptionPane.showMessageDialog(frame, "两次输入的密码不一致", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        registerButton.setEnabled(false);
        new Thread(() -> {
            String error = ChatClient.register(username, password, nickname);
            SwingUtilities.invokeLater(() -> {
                registerButton.setEnabled(true);
                if (error == null) {
                    JOptionPane.showMessageDialog(frame, "注册成功，请登录", "成功", JOptionPane.INFORMATION_MESSAGE);
                    goLogin();
                } else {
                    JOptionPane.showMessageDialog(frame, "注册失败：" + error, "注册失败", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private void goLogin() {
        frame.dispose();
        new LoginUI();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(RegisterUI::new);
    }
}
