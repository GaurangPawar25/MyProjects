import java.sql.*;
import java.util.Scanner;

public class TaskManager {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            printMenu();
            int choice = getIntInput("Choose an option: ");

            switch (choice) {
                case 1 -> addTask();
                case 2 -> viewPendingTasks();
                case 3 -> completeTask();
                case 4 -> viewCompletedTasks();
                case 5 -> {
                    System.out.println("Exiting...");
                    System.exit(0);
                }
                default -> System.out.println("Invalid choice!");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n==== Task Manager ====");
        System.out.println("1. Add New Task");
        System.out.println("2. View Pending Tasks");
        System.out.println("3. Mark Task as Completed");
        System.out.println("4. View Completed Tasks");
        System.out.println("5. Exit");
    }

    private static int getIntInput(String prompt) {
        System.out.print(prompt);
        while (!scanner.hasNextInt()) {
            System.out.println("Please enter a number!");
            scanner.next();
        }
        int input = scanner.nextInt();
        scanner.nextLine(); // Consume newline
        return input;
    }

    private static void addTask() {
        System.out.println("\n--- Add New Task ---");
        String title = getInput("Title: ");
        String description = getInput("Description: ");
        String priority = getPriority();
        String dueDate = getInput("Due Date (YYYY-MM-DD): ");

        try (Connection conn = DBConnection.getConnection()) {
            String sql = "INSERT INTO tasks (title, description, priority, due_date) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, title);
            stmt.setString(2, description);
            stmt.setString(3, priority);
            stmt.setString(4, dueDate);
            stmt.executeUpdate();
            System.out.println("Task added successfully!");
        } catch (SQLException e) {
            System.err.println("Error adding task: " + e.getMessage());
        }
    }
    private static void viewPendingTasks() {
        checkAutoStatusUpdates(); // First update statuses

        try (Connection conn = DBConnection.getConnection()) {
            String sql = """
            SELECT *,
                   DATEDIFF(due_date, CURDATE()) AS days_remaining,
                   is_auto_completed
            FROM tasks
            WHERE status IN ('Pending', 'Overdue')
            ORDER BY
                CASE priority
                    WHEN 'High' THEN 1
                    WHEN 'Medium' THEN 2
                    WHEN 'Low' THEN 3
                END,
                due_date ASC""";

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            System.out.println("\n=== TASK LIST ===");
            System.out.println("ID\tStatus\t\tDue Date\tDays Left\tPriority\tTitle");
            System.out.println("--------------------------------------------------------------------");

            while (rs.next()) {
                int daysLeft = rs.getInt("days_remaining");
                String status = formatStatus(
                        rs.getString("status"),
                        rs.getBoolean("is_auto_completed")
                );

                System.out.printf("%d\t%-12s\t%s\t%5d\t\t%-8s\t%s%n",
                        rs.getInt("task_id"),
                        status,
                        rs.getDate("due_date"),
                        daysLeft,
                        rs.getString("priority"),
                        rs.getString("title")
                );
            }

            if (!rs.isBeforeFirst()) {
                System.out.println("No pending tasks found!");
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }
    private static void completeTask() {
        viewPendingTasks();
        int taskId = getIntInput("\nEnter Task ID to complete: ");

        try (Connection conn = DBConnection.getConnection()) {
            // First verify task exists and isn't already completed
            String checkSql = "SELECT status FROM tasks WHERE task_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, taskId);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                System.out.println("No task found with ID: " + taskId);
                return;
            }

            String currentStatus = rs.getString("status");
            if ("Completed".equals(currentStatus)) {
                System.out.println("Task is already completed!");
                return;
            }

            // Archive and mark as completed
            conn.setAutoCommit(false);
            try {
                // Archive
                String archiveSql = """
                INSERT INTO archived_tasks
                (task_id, title, description, priority, due_date)
                SELECT task_id, title, description, priority, due_date
                FROM tasks
                WHERE task_id = ?""";

                PreparedStatement archiveStmt = conn.prepareStatement(archiveSql);
                archiveStmt.setInt(1, taskId);
                archiveStmt.executeUpdate();

                // Update status
                String updateSql = "UPDATE tasks SET status = 'Completed' WHERE task_id = ?";
                PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                updateStmt.setInt(1, taskId);
                updateStmt.executeUpdate();

                conn.commit();
                System.out.println("Task #" + taskId + " marked as completed!");

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("Error completing task: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
        }
    }
    private static void viewCompletedTasks() {
        try (Connection conn = DBConnection.getConnection()) {
            String sql = """
            SELECT *,
                   is_auto_completed,
                   DATEDIFF(completed_at, due_date) AS completion_delay
            FROM archived_tasks
            ORDER BY completed_at DESC
            LIMIT 20""";

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            System.out.println("\n=== RECENTLY COMPLETED TASKS ===");
            System.out.println("Completed At\t\tDelay\tAuto?\tTitle");
            System.out.println("------------------------------------------------");

            boolean hasTasks = false;
            while (rs.next()) {
                hasTasks = true;
                System.out.printf("%s\t%+3d days\t%s\t%s%n",
                        rs.getTimestamp("completed_at").toString().substring(0, 16),
                        rs.getInt("completion_delay"),
                        rs.getBoolean("is_auto_completed") ? "Yes" : "No",
                        rs.getString("title")
                );
            }

            if (!hasTasks) {
                System.out.println("No completed tasks yet!");
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }

    private static String getInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    private static String getPriority() {
        while (true) {
            String input = getInput("Priority (High/Medium/Low): ").trim();
            if (input.equalsIgnoreCase("high") || input.equalsIgnoreCase("medium") || input.equalsIgnoreCase("low")) {
                return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
            }
            System.out.println("Invalid priority! Please enter High, Medium, or Low");
        }
    }
    private static void checkAutoStatusUpdates() {
        DBConnection.updateTaskStatuses();
        System.out.println("Task statuses have been updated based on due dates.");
    }

    private static String formatStatus(String status, boolean isAutoCompleted) {
        return isAutoCompleted ? status + " (Auto)" : status;
    }
}