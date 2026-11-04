package com.triviagame.questions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
        
        Collections.shuffle(Arrays.asList(this.choices));
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
    public List<String> getChoices() {
        return Collections.unmodifiableList(
            Arrays.asList(choices)
        );
    }

    @Override
    public boolean checkAnswer(String input) {
        if (input == null) {
            return false;
        }

        String in = input.trim();
 
        if (in.isEmpty()) {
            return false;
        }

        for (String choice : choices) {
            if (choice.equalsIgnoreCase(in)) {
                return choice.equalsIgnoreCase(answer);
            }
        }

        try {
            int selected = Integer.parseInt(in);

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
