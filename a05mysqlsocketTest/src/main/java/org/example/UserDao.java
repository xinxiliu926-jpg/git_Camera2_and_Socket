package org.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDao {
    // 注册：插入 username, password, nickname（nickname 可选，不传则用 username）
    public boolean register(String username, String password, String nickname) {
        // 先检查用户名是否已存在（数据库 username 未加唯一约束时也能拦截重复）
        if (existsByUsername(username)) {
            System.out.println("用户名已存在: " + username);
            return false;
        }
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = username; // 默认昵称等于用户名
        }
        String sql = "INSERT INTO user (username, password, nickname) VALUES (?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, nickname);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            // 唯一键冲突（username 重复）错误码 1062
            if (e.getErrorCode() == 1062) {
                System.out.println("用户名已存在: " + username);
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    // 判断用户名是否已存在（用于注册前查重）
    public boolean existsByUsername(String username) {
        String sql = "SELECT id FROM user WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 登录：根据用户名查询密码
    public boolean login(String username, String password) {
        String sql = "SELECT password FROM user WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String dbPwd = rs.getString("password");
                return dbPwd.equals(password); // 后续改哈希比较
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 可选：根据用户名获取用户信息（后续展示用）
    public String getNickname(String username) {
        String sql = "SELECT nickname FROM user WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("nickname");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return username; // 默认返回用户名
    }
}
