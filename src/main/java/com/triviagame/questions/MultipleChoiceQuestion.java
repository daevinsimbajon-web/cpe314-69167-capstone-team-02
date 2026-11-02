package com.triviagame.questions;

public class MultipleChoiceQuestion extends Question {

    private String[] choices;

    public MultipleChoiceQuestion(String text,
            String[] choices, String answer) {
        this(0, 0, text, choices, answer);
    }

    public MultipleChoiceQuestion(int id, int categoryId,
            String text, String[] choices, String answer) {

        super(id, categoryId, text, answer);
        this.choices = choices.clone();
        
        java.util.Collections.shuffle(
        	    java.util.Arrays.asList(this.choices)
        );
    }

    @Override
    public void display() {
        System.out.println(text);

        for (int i = 0; i < choices.length; i++) {
            System.out.println(
                (i + 1) + ". " + choices[i]
            );
        }
    }

    @Override
    public boolean checkAnswer(String input) {
        if (input == null) {
            return false;
        }

        try {
            int selected = Integer.parseInt(input.trim());

            if (selected < 1 || selected > choices.length) {
                return false;
            }

            return choices[selected - 1]
                    .equalsIgnoreCase(answer);

        } catch (NumberFormatException e) {
            return super.checkAnswer(input);
        }
    }

    @Override
    public int getPoints() {
        return 10;
    }
}
