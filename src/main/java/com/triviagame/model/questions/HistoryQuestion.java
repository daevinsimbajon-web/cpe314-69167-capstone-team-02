package com.triviagame.model.questions;

/** History questions earn a one-point subject bonus. */
public final class HistoryQuestion extends CategoryQuestion {
  public HistoryQuestion(Question question) {
    super(question);
  }

  @Override
  public int getPoints() {
    return question.getPoints() + 1;
  }
}
