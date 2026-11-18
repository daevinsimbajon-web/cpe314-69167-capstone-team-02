package com.triviagame.model.questions;

import java.util.List;
import java.util.Objects;

/** Adds a category scoring rule while retaining the question's answer format. */
public abstract class CategoryQuestion extends Question {
  protected final Question question;

  protected CategoryQuestion(Question question) {
    super(
        question.getId(),
        question.getCategoryId(),
        question.getText(),
        question.getCorrectAnswer());
    this.question = Objects.requireNonNull(question, "question");
  }

  @Override
  public boolean checkAnswer(String input) {
    return question.checkAnswer(input);
  }

  @Override
  public void display() {
    question.display();
  }

  @Override
  public List<String> getChoices() {
    return question.getChoices();
  }
}
