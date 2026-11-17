package com.triviagame.online.practice;

import com.triviagame.questions.MultipleChoiceQuestion;
import com.triviagame.questions.Question;
import com.triviagame.questions.TrueFalseQuestion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Serves questions FIFO and reverses scored answers LIFO without database or UI dependencies. */
public final class PracticeEngine {
    private final Deque<PracticeQuestion> questions;
    private final Deque<PracticeAnswer> history;
    private int score;

    public PracticeEngine(PracticeState state) {
        questions = new ArrayDeque<>(state.remaining());
        history = new ArrayDeque<>(state.history());
        score = state.score();
    }

    public static PracticeState start(List<PracticeQuestion> questions) {
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Add questions before practicing.");
        }
        return new PracticeState(questions, List.of(), 0);
    }

    /** Scores one answer, consumes the queue head, and pushes an undo entry. */
    public void answer(int selectedIndex) {
        PracticeQuestion current = questions.peekFirst();
        if (current == null) {
            throw new IllegalArgumentException("This practice is complete.");
        }
        if (selectedIndex < 0 || selectedIndex >= current.choices().size()) {
            throw new IllegalArgumentException("Choose an available answer.");
        }
        String correctAnswer = current.choices().get(current.correctIndex());
        Question model =
                current.type().equals("TRUE_FALSE")
                        ? new TrueFalseQuestion(current.text(), correctAnswer)
                        : new MultipleChoiceQuestion(
                                current.text(),
                                current.choices().toArray(String[]::new),
                                correctAnswer);
        boolean correct = model.checkAnswer(current.choices().get(selectedIndex));
        int points = correct ? model.getPoints() : 0;
        questions.removeFirst();
        history.push(new PracticeAnswer(current, selectedIndex, correct, points));
        score += points;
    }

    /** Reverses the last scored answer and restores its question at the queue head. */
    public void undo() {
        if (history.isEmpty()) {
            throw new IllegalArgumentException("There is no scored answer to undo.");
        }
        PracticeAnswer previous = history.pop();
        score -= previous.points();
        questions.addFirst(previous.question());
    }

    public PracticeState state() {
        return new PracticeState(new ArrayList<>(questions), new ArrayList<>(history), score);
    }
}
