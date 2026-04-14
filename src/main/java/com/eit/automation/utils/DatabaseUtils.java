package com.eit.automation.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseUtils {
    // Your credentials
    private static final String URL = "jdbc:postgresql://194.233.75.197:5432/we1";
    private static final String USER = "postgres";
    private static final String PASS = "we12025";

    public static void executeCleanup(String sql) throws Exception {
        // --- SAFETY FIREWALL ---
        String normalizedSql = sql.trim().toLowerCase();

        if (normalizedSql.startsWith("delete") || normalizedSql.startsWith("update")) {
            if (!normalizedSql.contains("where")) {
                throw new Exception("🛑 ACCIDENTAL DELETION PREVENTED: SQL command missing 'WHERE' clause. Query: " + sql);
            }
        }

        // --- EXECUTION ---
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            System.out.println("🗄️ Executing DB Cleanup: " + sql);
            int affectedRows = stmt.executeUpdate(sql);
            System.out.println("✅ Rows affected: " + affectedRows);

        } catch (SQLException e) {
            throw new Exception("❌ Database Error: " + e.getMessage());
        }
    }
}