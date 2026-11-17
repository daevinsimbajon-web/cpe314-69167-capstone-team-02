package com.triviagame.questions;

/** Science questions earn a 50% subject bonus, rounded down. */
public final class ScienceQuestion extends CategoryQuestion {
    public ScienceQuestion(Question question) { super(question); }
    @Override public int getPoints() { return question.getPoints() + question.getPoints() / 2; }
}
