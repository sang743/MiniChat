package com.app.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static DatabaseConnection instance; // Singleton instance
    private Connection connection; // Connection object

    // Singleton getInstance method
    public static DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    // Private constructor for Singleton pattern
    private DatabaseConnection() {}

    /**
     * Method to establish a connection to the database.
     * @throws SQLException if a database access error occurs
     */
    public void connectToDatabase() throws SQLException {
        String server = "localhost";
        String port = "3306";
        String database = "chat_application";
        String userName = "root";
        String password = ""; // Nếu có mật khẩu, thay đổi tại đây

        // Chuỗi kết nối đơn giản cho MySQL 5.0
        String url = String.format("jdbc:mysql://%s:%s/%s", server, port, database);

        try {
            // Đăng ký driver cũ (không bắt buộc với JDBC 4.0 trở lên)
            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection(url, userName, password);
            System.out.println("Database connection established successfully.");
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Failed to connect to the database: " + e.getMessage());
            throw new SQLException(e);
        }
    }


    /**
     * Getter for the database connection object.
     * @return the active database connection
     */
    public Connection getConnection() {
        return connection;
    }
}
