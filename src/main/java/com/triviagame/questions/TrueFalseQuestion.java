package com.triviagame;

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
    public int getPoints() {
        return 5;
    }
}
