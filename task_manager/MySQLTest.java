import java.sql.*;

public class MySQLTest {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/JDBC_Project";
        String user = "root";
        String password = "asdfghjkop@077";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to MySQL!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
