package com.trivia;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

public class GameEngineTest {

    @Test
    void testMultipleChoiceScoring() throws SQLException {

        Question question = new MultipleChoiceQuestion(
            "What is 2 + 2?",
            new String[]{"3", "4", "5"},
            "4"
        );

        GameEngine game = new GameEngine();
        game.loadQuestions(List.of(question));

        assertTrue(game.submitAnswer("4"));
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
}
