package com.triviagame.gameplay;

import com.triviagame.category.Category;
import com.triviagame.questions.MultipleChoiceQuestion;
import com.triviagame.questions.Question;
import com.triviagame.questions.TrueFalseQuestion;

import com.triviagame.category.Category;
import com.triviagame.questions.MultipleChoiceQuestion;
import com.triviagame.questions.Question;
import com.triviagame.questions.TrueFalseQuestion;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

public class GameEngineTest {

    @Test
    void testMultipleChoiceScoring() throws SQLException {

        Question question = new MultipleChoiceQuestion(
            "What is 2 + 2?",
            new String[]{"three", "four", "five"},
            "four"
        );

        GameEngine game = new GameEngine();
        game.loadQuestions(List.of(question));

        assertTrue(game.submitAnswer("four"));
        assertEquals(10, game.getScore());
    }

    @Test
    void testTrueFalseScoring() throws SQLException {

        Question question = new TrueFalseQuestion(
            "Java supports OOP.", "True"
        );

        GameEngine game = new GameEngine();
        game.loadQuestions(List.of(question));

        assertTrue(game.submitAnswer("True"));
        assertEquals(5, game.getScore());
    }

    @Test
    void testPolymorphism() {

        Question first = new MultipleChoiceQuestion(
            "What is 1 + 1?",
            new String[]{"1", "2", "3"},
            "2"
        );

        Question second = new TrueFalseQuestion(
            "Earth is round.", "True"
        );

        assertEquals(10, first.getPoints());
        assertEquals(5, second.getPoints());
    }

    @Test
    void testUndo() throws SQLException {

        Question question = new TrueFalseQuestion(
            "Java is a language.", "True"
        );

        GameEngine game = new GameEngine();

        game.loadQuestions(List.of(question));

        game.submitAnswer("True");

        assertEquals(5, game.getScore());

        assertTrue(game.undo());

        assertEquals(0, game.getScore());
        assertTrue(game.hasNextQuestion());
        assertSame(question, game.nextQuestion());
    }

    @Test
    void testRecursiveCategory() {

        Category root = new Category("Trivia");
        Category science = new Category("Science");
        Category biology = new Category("Biology");

        root.addSubcategory(science);
        science.addSubcategory(biology);

        biology.addQuestion(new TrueFalseQuestion(
            "Humans need oxygen.", "True"
        ));

        assertEquals(1, root.getAllQuestions().size());

        assertNotNull(root.findCategory("Biology"));
        assertNull(root.findCategory("Unknown"));
    }

    @Test
    void testQueueOrder() throws SQLException {

        Question first = new TrueFalseQuestion(
            "First question", "True"
        );

        Question second = new TrueFalseQuestion(
            "Second question", "False"
        );

        GameEngine game = new GameEngine();

        game.loadQuestions(List.of(first, second));

        assertSame(first, game.nextQuestion());

        game.submitAnswer("True");

        assertSame(second, game.nextQuestion());
    }

    // EDGE CASE: no questions loaded at all
    @Test
    void testSubmitOnEmptyQueueReturnsFalse() throws SQLException {
 
        GameEngine game = new GameEngine();
 
        assertFalse(game.submitAnswer("True"));
        assertEquals(0, game.getScore());
        assertFalse(game.hasNextQuestion());
    }
 
    @Test
    void testNextQuestionOnEmptyQueueReturnsNull() {
 
        GameEngine game = new GameEngine();
 
        assertNull(game.nextQuestion());
    }
 
    @Test
    void testUndoWithNothingToUndoReturnsFalse() throws SQLException {
 
        GameEngine game = new GameEngine();
 
        assertFalse(game.undo());
 
        // Also false after loading but before answering
        game.loadQuestions(List.of(
            new TrueFalseQuestion("Q", "True")
        ));
 
        assertFalse(game.undo());
        assertEquals(1, game.getRemaining());
    }
 
    @Test
    void testWrongAnswerScoresZeroAndAdvances() throws SQLException {
 
        Question first = new TrueFalseQuestion("First", "True");
        Question second = new TrueFalseQuestion("Second", "True");
 
        GameEngine game = new GameEngine();
        game.loadQuestions(List.of(first, second));
 
        assertFalse(game.submitAnswer("False"));
 
        assertEquals(0, game.getScore());
        assertSame(second, game.nextQuestion());
        assertEquals(1, game.getRemaining());
    }
 
    // EDGE CASE: invalid answer text still counts as a wrong answer
    @Test
    void testBlankAndNullAnswersAreWrong() throws SQLException {
 
        GameEngine game = new GameEngine();
        game.loadQuestions(List.of(
            new TrueFalseQuestion("First", "True"),
            new TrueFalseQuestion("Second", "True")
        ));
 
        assertFalse(game.submitAnswer(null));
        assertFalse(game.submitAnswer("   "));
        assertEquals(0, game.getScore());
        assertFalse(game.hasNextQuestion());
    }
 
    @Test
    void testLoadQuestionsTwiceResetsEverything() throws SQLException {
 
        GameEngine game = new GameEngine();
 
        game.loadQuestions(List.of(
            new TrueFalseQuestion("Old 1", "True"),
            new TrueFalseQuestion("Old 2", "True")
        ));
 
        game.submitAnswer("True");
        assertEquals(5, game.getScore());
 
        Question fresh = new TrueFalseQuestion("New", "True");
        game.loadQuestions(List.of(fresh));
 
        assertEquals(0, game.getScore());
        assertEquals(1, game.getTotal());
        assertEquals(1, game.getRemaining());
        assertSame(fresh, game.nextQuestion());
 
        // History was cleared too
        assertFalse(game.undo());
    }
 
    @Test
    void testUndoAfterSeveralAnswersRestoresScoreAndQueue()
            throws SQLException {
 
        Question first = new TrueFalseQuestion("First", "True");
        Question second = new TrueFalseQuestion("Second", "True");
        Question third = new TrueFalseQuestion("Third", "True");
 
        GameEngine game = new GameEngine();
        game.loadQuestions(List.of(first, second, third));
 
        game.submitAnswer("True");
        game.submitAnswer("True");
        assertEquals(10, game.getScore());
        assertEquals(1, game.getRemaining());
 
        assertTrue(game.undo());
        assertEquals(5, game.getScore());
        assertEquals(2, game.getRemaining());
        assertSame(second, game.nextQuestion());
 
        assertTrue(game.undo());
        assertEquals(0, game.getScore());
        assertEquals(3, game.getRemaining());
        assertSame(first, game.nextQuestion());
 
        // Nothing left to undo
        assertFalse(game.undo());
    }
 
    @Test
    void testUndoAfterWrongAnswerPutsQuestionBack() throws SQLException {
 
        Question question = new TrueFalseQuestion("Only", "True");
 
        GameEngine game = new GameEngine();
        game.loadQuestions(List.of(question));
 
        game.submitAnswer("False");
        assertFalse(game.hasNextQuestion());
 
        assertTrue(game.undo());
        assertEquals(0, game.getScore());
        assertSame(question, game.nextQuestion());
    }
 
    @Test
    void testTotalAndRemainingTrackProgress() throws SQLException {
 
        GameEngine game = new GameEngine();
 
        // Nothing loaded yet
        assertEquals(0, game.getTotal());
        assertEquals(0, game.getRemaining());
 
        game.loadQuestions(List.of(
            new TrueFalseQuestion("1", "True"),
            new TrueFalseQuestion("2", "True"),
            new TrueFalseQuestion("3", "True")
        ));
 
        assertEquals(3, game.getTotal());
        assertEquals(3, game.getRemaining());
 
        game.submitAnswer("True");
        assertEquals(3, game.getTotal());
        assertEquals(2, game.getRemaining());
 
        game.undo();
        assertEquals(3, game.getRemaining());
 
        game.submitAnswer("True");
        game.submitAnswer("True");
        game.submitAnswer("True");
        assertEquals(3, game.getTotal());
        assertEquals(0, game.getRemaining());
        assertFalse(game.hasNextQuestion());
    }
 
    // EDGE CASE: loading an empty list
    @Test
    void testLoadEmptyList() {
 
        GameEngine game = new GameEngine();
        game.loadQuestions(List.of());
 
        assertFalse(game.hasNextQuestion());
        assertEquals(0, game.getTotal());
        assertEquals(0, game.getRemaining());
        assertNull(game.nextQuestion());
    }
 
    // EDGE CASE: invalid input to loadQuestions
    @Test
    void testLoadNullListThrows() {
 
        GameEngine game = new GameEngine();
 
        assertThrows(
            IllegalArgumentException.class,
            () -> game.loadQuestions(null)
        );
    }
 
    @Test
    void testScoreAddsUpAcrossQuestionTypes() throws SQLException {
 
        GameEngine game = new GameEngine();
        game.loadQuestions(List.of(
            new MultipleChoiceQuestion(
                "Capital of France?",
                new String[]{"Paris", "Rome", "Madrid"},
                "Paris"
            ),
            new TrueFalseQuestion("Water is wet.", "True")
        ));
 
        assertTrue(game.submitAnswer("Paris"));
        assertTrue(game.submitAnswer("true"));
 
        assertEquals(15, game.getScore());
    }
 
    // EDGE CASE: a database game needs questions that have database IDs.
    // The error is thrown before anything is saved, so the game state
    // must stay exactly as it was.
    @Test
    void testDatabaseGameRejectsQuestionWithoutId() {
 
        GameEngine game = new GameEngine(1, 1);
        game.loadQuestions(List.of(
            new TrueFalseQuestion("No database id", "True")
        ));
 
        assertThrows(
            IllegalStateException.class,
            () -> game.submitAnswer("True")
        );
 
        assertEquals(0, game.getScore());
        assertEquals(1, game.getRemaining());
    }
}
