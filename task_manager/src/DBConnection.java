import java.sql.*;

public class DBConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/task_manager";
    private static final String USER = "root";
    private static final String PASSWORD = "asdfghjkop@077";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found!");
            e.printStackTrace();
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void updateTaskStatuses() {
        try (Connection conn = getConnection()) {
            // Mark overdue tasks
            String overdueSql = """
                UPDATE tasks 
                SET status = 'Overdue' 
                WHERE due_date < CURDATE() 
                AND status = 'Pending'""";

            // Auto-complete tasks with past due dates
            String completeSql = """
                UPDATE tasks 
                SET status = 'Completed', is_auto_completed = TRUE 
                WHERE due_date < DATE_SUB(CURDATE(), INTERVAL 7 DAY) 
                AND status = 'Overdue'""";

            conn.createStatement().executeUpdate(overdueSql);
            conn.createStatement().executeUpdate(completeSql);

        } catch (SQLException e) {
            System.err.println("Error updating task statuses: " + e.getMessage());
        }
    }
}