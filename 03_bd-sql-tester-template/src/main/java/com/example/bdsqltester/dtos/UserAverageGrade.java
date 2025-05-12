package com.example.bdsqltester.dtos;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

public class UserAverageGrade {
    private final SimpleLongProperty userId;
    private final SimpleStringProperty username;
    private final SimpleDoubleProperty averageGrade;

    public UserAverageGrade(long userId, String username, double averageGrade) {
        this.userId = new SimpleLongProperty(userId);
        this.username = new SimpleStringProperty(username);
        this.averageGrade = new SimpleDoubleProperty(averageGrade);
    }

    public long getUserId() {
        return userId.get();
    }

    public SimpleLongProperty userIdProperty() {
        return userId;
    }

    public String getUsername() {
        return username.get();
    }

    public SimpleStringProperty usernameProperty() {
        return username;
    }

    public double getAverageGrade() {
        return averageGrade.get();
    }

    public SimpleDoubleProperty averageGradeProperty() {
        return averageGrade;
    }
}

