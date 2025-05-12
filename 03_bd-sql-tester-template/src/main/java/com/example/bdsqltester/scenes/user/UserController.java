package com.example.bdsqltester.scenes.user;

import com.example.bdsqltester.datasources.GradingDataSource;
import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.dtos.Assignment;
import com.example.bdsqltester.dtos.Grade;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UserController {

    @FXML
    private Label assignmentNameLabel;

    @FXML
    private TextArea assignmentInstructionsArea;

    @FXML
    private TextArea userAnswerArea;

    @FXML
    private ListView<Assignment> assignmentListView;

    @FXML
    private Label gradeLabel;

    @FXML
    private Label averageGradeLabel;

    private Long loggedInUserId;
    private Assignment currentAssignment;
    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();

    public void setLoggedInUserId(Long userId) {
        this.loggedInUserId = userId;
        refreshAssignmentList();
        calculateAndDisplayAverageGrade(); // Pastikan ini dipanggil setelah ID pengguna diatur
    }

    @FXML
    void initialize() {
        assignmentListView.setCellFactory(param -> new ListCell<Assignment>() {
            @Override
            protected void updateItem(Assignment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name);
                }
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                if (selected) {
                    currentAssignment = getItem();
                    if (currentAssignment != null) {
                        displayAssignmentDetails(currentAssignment);
                        loadUserGrade(currentAssignment.id);
                    } else {
                        clearAssignmentDetails();
                        gradeLabel.setText("Grade: -");
                    }
                }
            }
        });
    }

    void refreshAssignmentList() {
        assignments.clear();
        try (Connection c = MainDataSource.getConnection()) {
            Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM assignments");
            while (rs.next()) {
                assignments.add(new Assignment(rs));
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load assignments.", e.toString());
        }
        assignmentListView.setItems(assignments);
    }

    void displayAssignmentDetails(Assignment assignment) {
        assignmentNameLabel.setText(assignment.name);
        assignmentInstructionsArea.setText(assignment.instructions);
        userAnswerArea.clear();
    }

    void clearAssignmentDetails() {
        assignmentNameLabel.setText("");
        assignmentInstructionsArea.setText("");
        userAnswerArea.clear();
    }

    void loadUserGrade(Long assignmentId) {
        if (loggedInUserId != null && assignmentId != null) {
            try (Connection c = MainDataSource.getConnection()) {
                String query = "SELECT grade FROM grades WHERE user_id = ? AND assignment_id = ?";
                PreparedStatement stmt = c.prepareStatement(query);
                stmt.setLong(1, loggedInUserId);
                stmt.setLong(2, assignmentId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    gradeLabel.setText("Grade: " + rs.getDouble("grade"));
                } else {
                    gradeLabel.setText("Grade: -");
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to load your grade for this assignment.", e.toString());
                gradeLabel.setText("Grade: Error");
            }
        } else {
            gradeLabel.setText("Grade: -");
        }
    }

    @FXML
    void onTestButtonClick(ActionEvent event) {
        if (currentAssignment == null) {
            showAlert("Warning", "No Assignment Selected", "Please select an assignment to test your query.");
            return;
        }

        Stage stage = new Stage();
        stage.setTitle("Query Results");
        TableView<ArrayList<String>> tableView = new TableView<>();
        ObservableList<ArrayList<String>> data = FXCollections.observableArrayList();
        ArrayList<String> headers = new ArrayList<>();

        try (Connection conn = GradingDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(userAnswerArea.getText())) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                final int columnIndex = i - 1;
                String headerText = metaData.getColumnLabel(i);
                headers.add(headerText);
                TableColumn<ArrayList<String>, String> column = new TableColumn<>(headerText);
                column.setCellValueFactory(cellData -> {
                    ArrayList<String> rowData = cellData.getValue();
                    return (rowData != null && columnIndex < rowData.size()) ? new SimpleStringProperty(rowData.get(columnIndex)) : new SimpleStringProperty("");
                });
                column.setPrefWidth(120);
                tableView.getColumns().add(column);
            }

            while (rs.next()) {
                ArrayList<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    row.add(value != null ? value : "");
                }
                data.add(row);
            }

            if (headers.isEmpty() && data.isEmpty()) {
                showAlert("Query Results", null, "The query executed successfully but returned no data.");
                return;
            }

            tableView.setItems(data);
            StackPane root = new StackPane();
            root.getChildren().add(tableView);
            Scene scene = new Scene(root, 800, 600);
            stage.setScene(scene);
            stage.show();

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to execute your query.", e.getMessage());
        }
    }

    private final Map<Long, String> userAnswers = new HashMap<>(); // Map untuk menyimpan jawaban pengguna
    @FXML
    void onSubmitButtonClick(ActionEvent event) {
        if (currentAssignment == null) {
            showAlert("Warning", "No Assignment Selected", "Please select an assignment to submit your answer.");
            return;
        }

        String userAnswerQuery = userAnswerArea.getText();
        String correctAnswerQuery = currentAssignment.answerKey;
        int newGrade = calculateGrade(userAnswerQuery, correctAnswerQuery);

        saveUserGrade(newGrade); // Panggil saveUserGrade yang sudah ada
        userAnswers.remove(currentAssignment.getId()); // Hapus jawaban setelah submit
        calculateAndDisplayAverageGrade(); // Perbarui nilai rata-rata setelah submit
    }

    private void saveUserGrade(int newGrade) {
        if (loggedInUserId != null && currentAssignment != null) {
            try (Connection c = MainDataSource.getConnection()) {
                String selectQuery = "SELECT grade FROM grades WHERE user_id = ? AND assignment_id = ?";
                PreparedStatement selectStmt = c.prepareStatement(selectQuery);
                selectStmt.setLong(1, loggedInUserId);
                selectStmt.setLong(2, currentAssignment.id);
                ResultSet rs = selectStmt.executeQuery();

                int currentHighestGrade = -1;
                if (rs.next()) {
                    currentHighestGrade = rs.getInt("grade");
                }

                if (newGrade > currentHighestGrade) { // Hanya simpan jika nilai baru lebih tinggi
                    String upsertQuery;
                    if (currentHighestGrade == -1) {
                        upsertQuery = "INSERT INTO grades (user_id, assignment_id, grade) VALUES (?, ?, ?)";
                    } else {
                        upsertQuery = "UPDATE grades SET grade = ? WHERE user_id = ? AND assignment_id = ?";
                    }
                    PreparedStatement upsertStmt = c.prepareStatement(upsertQuery);
                    if (currentHighestGrade == -1) {
                        upsertStmt.setLong(1, loggedInUserId);
                        upsertStmt.setLong(2, currentAssignment.id);
                        upsertStmt.setInt(3, newGrade);

                    } else {
                        upsertStmt.setInt(1, newGrade);
                        upsertStmt.setLong(2, loggedInUserId);
                        upsertStmt.setLong(3, currentAssignment.id);
                    }
                    System.out.println(upsertStmt.toString());
                    int affectedRows = upsertStmt.executeUpdate();
                    if (affectedRows > 0) {
                        showAlert("Submission Successful", null, "Your grade has been saved: " + newGrade);
                        loadUserGrade(currentAssignment.id); // Update displayed grade
                    } else {
                        showAlert("Error", "Grade Not Saved", "Failed to save your grade.");
                    }
                } else if (currentHighestGrade != -1 && newGrade == currentHighestGrade) {
                    showAlert("Info", "Grade Not Updated", "Your grade is the same as the current highest grade (" + currentHighestGrade + ").");
                } else if (currentHighestGrade != -1 && newGrade < currentHighestGrade) {
                    showAlert("Info", "Grade Not Updated", "Your current highest grade (" + currentHighestGrade + ") is already higher than your new grade (" + newGrade + ").");
                } else if (currentHighestGrade == -1) {
                    // Jika belum ada nilai, simpan nilai baru (bisa 0, 50, atau 100)
                    String insertQuery = "INSERT INTO grades (user_id, assignment_id, grade) VALUES (?, ?, ?)";
                    PreparedStatement insertStmt = c.prepareStatement(insertQuery);
                    insertStmt.setLong(1, loggedInUserId);
                    insertStmt.setLong(2, currentAssignment.id);
                    insertStmt.setInt(3, newGrade);
                    int affectedRows = insertStmt.executeUpdate();
                    if (affectedRows > 0) {
                        showAlert("Submission Successful", null, "Your grade has been saved: " + newGrade);
                        loadUserGrade(currentAssignment.id); // Update displayed grade
                    } else {
                        showAlert("Error", "Grade Not Saved", "Failed to save your grade.");
                    }
                }

            } catch (SQLException e) {
                showAlert("Database Error", "Failed to save your grade.", e.getMessage());
            }
        } else {
            showAlert("Error", "User or Assignment Not Loaded", "Could not save grade due to missing user or assignment information.");
        }
    }

    private int calculateGrade(String userAnswerQuery, String correctAnswerQuery) {
        List<String> userResults = executeAndFetch(userAnswerQuery);
        List<String> correctResults = executeAndFetch(correctAnswerQuery);

        if (userResults.equals(correctResults) && !userResults.isEmpty()) {
            return 100;
        } else if (!userResults.isEmpty() && !correctResults.isEmpty() &&
                userResults.size() == correctResults.size()) {
            // Normalize and compare contents regardless of row and column order
            List<java.util.Set<String>> normalizedUserRows = userResults.stream()
                    .map(s -> java.util.Arrays.stream(s.trim().toLowerCase().split(",")) // Split into columns
                            .map(String::trim)
                            .collect(Collectors.toSet())) // Collect columns into a Set
                    .collect(Collectors.toList());

            List<java.util.Set<String>> normalizedCorrectRows = correctResults.stream()
                    .map(s -> java.util.Arrays.stream(s.trim().toLowerCase().split(",")) // Split into columns
                            .map(String::trim)
                            .collect(Collectors.toSet())) // Collect columns into a Set
                    .collect(Collectors.toList());

            if (normalizedUserRows.size() == normalizedCorrectRows.size() &&
                    normalizedUserRows.containsAll(normalizedCorrectRows) &&
                    normalizedCorrectRows.containsAll(normalizedUserRows)) {
                return 50;
            }
        }
        return 0;
    }

    private List<String> executeAndFetch(String sqlQuery) {
        List<String> results = new ArrayList<>();
        try (Connection conn = GradingDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlQuery)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                List<String> rowValues = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    rowValues.add(value != null ? value : "");
                }
                results.add(String.join(",", rowValues)); // Join row values for easier comparison
            }
        } catch (SQLException e) {
            // Log the error, but we'll compare based on potentially empty results
            e.printStackTrace();
        }
        return results;
    }

    @FXML
    void resetUserAssignments(ActionEvent event) {
        if (loggedInUserId != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Reset Assignments");
            alert.setHeaderText("Reset All Your Assignments?");
            alert.setContentText("This will delete all your grades for all assignments. Are you sure?");

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try (Connection c = MainDataSource.getConnection()) {
                        String deleteQuery = "DELETE FROM grades WHERE user_id = ?";
                        PreparedStatement deleteStmt = c.prepareStatement(deleteQuery);
                        deleteStmt.setLong(1, loggedInUserId);
                        int rowsDeleted = deleteStmt.executeUpdate();
                        showAlert("Assignments Reset", null, "Successfully deleted grades for " + rowsDeleted + " assignments.");
                        loadUserGrade(currentAssignment != null ? currentAssignment.id : null); // Clear displayed grade
                        calculateAndDisplayAverageGrade(); // Recalculate average grade
                    } catch (SQLException e) {
                        showAlert("Database Error", "Failed to reset assignments.", e.getMessage());
                    }
                }
            });
        } else {
            showAlert("Error", "User Not Logged In", "Cannot reset assignments as no user is logged in.");
        }
    }

    private void calculateAndDisplayAverageGrade() {
        if (loggedInUserId != null) {
            try (Connection c = MainDataSource.getConnection()) {
                String query = "SELECT AVG(grade) AS average_grade FROM grades WHERE user_id = ?";
                PreparedStatement stmt = c.prepareStatement(query);
                stmt.setLong(1, loggedInUserId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    double averageGrade = rs.getDouble("average_grade");
                    averageGradeLabel.setText("Rata-rata: " + String.format("%.2f", averageGrade));
                } else {
                    averageGradeLabel.setText("Rata-rata: -");
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Gagal menghitung nilai rata-rata.", e.getMessage());
                averageGradeLabel.setText("Rata-rata: Error");
            }
        } else {
            averageGradeLabel.setText("Rata-rata: -");
        }
    }

    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}