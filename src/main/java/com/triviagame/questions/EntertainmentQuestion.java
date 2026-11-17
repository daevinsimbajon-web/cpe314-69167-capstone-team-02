package com.triviagame.questions;

/** Entertainment questions use the base score of their answer format. */
public final class EntertainmentQuestion extends CategoryQuestion {
    public EntertainmentQuestion(Question question) { super(question); }
    @Override public int getPoints() { return question.getPoints(); }
}
