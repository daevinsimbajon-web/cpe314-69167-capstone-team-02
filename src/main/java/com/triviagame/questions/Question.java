package com.triviagame;

public abstract class Question {

    protected int id;
    protected int categoryId;
    protected String text;
    protected String answer;

    public Question(String text, String answer) {
        this(0, 0, text, answer);
    }

    public Question(int id, int categoryId,
            String text, String answer) {
        this.id = id;
        this.categoryId = categoryId;
        this.text = text;
        this.answer = answer;
    }

    public int getId() {
        return id;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public String getText() {
        return text;
    }

    public boolean checkAnswer(String input) {
        return input != null
            && answer.equalsIgnoreCase(input.trim());
    }

    public abstract int getPoints();

    public abstract void display();
}
