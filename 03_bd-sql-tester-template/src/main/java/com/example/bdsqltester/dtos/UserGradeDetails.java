package com.example.bdsqltester.dtos;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

public class UserGradeDetails {
    private final SimpleStringProperty assignmentName;
    private final SimpleDoubleProperty grade;

    public UserGradeDetails(String assignmentName, double grade) {
        this.assignmentName = new SimpleStringProperty(assignmentName);
        this.grade = new SimpleDoubleProperty(grade);
    }

    public String getAssignmentName() {
        return assignmentName.get();
    }

    public SimpleStringProperty assignmentNameProperty() {
        return assignmentName;
    }

    public double getGrade() {
        return grade.get();
    }

    public SimpleDoubleProperty gradeProperty() {
        return grade;
    }
}

