package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBUtil {
    // 请修改为你的实际 MySQL 地址、端口、数据库名、账号、密码
    private static final String URL =  "jdbc:mysql://127.0.0.1:3306/chat_DB?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String USER = "chat_db";
    // TODO: 改成你在云服务器上为 chat 账号设置的密码
    private static final String PASSWORD = "Chat123456";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}

