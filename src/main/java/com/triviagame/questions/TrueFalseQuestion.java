package com.triviagame;

import java.util.List;

public class TrueFalseQuestion extends Question {

    public TrueFalseQuestion(String text, String answer) {
        this(0, 0, text, answer);
    }

    public TrueFalseQuestion(int id, int categoryId,
            String text, String answer) {

        super(id, categoryId, text, answer);
    }

    @Override
    public void display() {
        System.out.println(text);
        System.out.println("(True / False)");
    }

    @Override
    public List<String> getChoices() {
        return List.of("True", "False");
    }

    @Override
    public int getPoints() {
        return 5;
    }
}
