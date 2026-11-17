package com.triviagame.questions;
 
import static org.junit.jupiter.api.Assertions.*;
 
import java.util.List;
 
import org.junit.jupiter.api.Test;
 
public class TrueFalseQuestionTest {
 
    @Test
    void testChoicesAreTrueAndFalse() {
 
        Question question = new TrueFalseQuestion(
            "The Sun is a star.", "True"
        );
 
        assertEquals(List.of("True", "False"), question.getChoices());
    }
 
    @Test
    void testChoicesCannotBeModified() {
 
        Question question = new TrueFalseQuestion(
            "The Sun is a star.", "True"
        );
 
        assertThrows(
            UnsupportedOperationException.class,
            () -> question.getChoices().add("Maybe")
        );
    }
 
    @Test
    void testCorrectAnswerIgnoresCaseAndSpaces() {
 
        Question question = new TrueFalseQuestion(
            "The Sun is a star.", "True"
        );
 
        assertTrue(question.checkAnswer("True"));
        assertTrue(question.checkAnswer("true"));
        assertTrue(question.checkAnswer("  TRUE "));
        assertFalse(question.checkAnswer("False"));
    }
 
    @Test
    void testFalseAnswerQuestion() {
 
        Question question = new TrueFalseQuestion(
            "The Moon is bigger than Earth.", "False"
        );
 
        assertTrue(question.checkAnswer("false"));
        assertFalse(question.checkAnswer("true"));
    }
 
    // EDGE CASE: missing, empty, or unexpected input
    @Test
    void testInvalidInputIsWrong() {
 
        Question question = new TrueFalseQuestion(
            "The Sun is a star.", "True"
        );
 
        assertFalse(question.checkAnswer(null));
        assertFalse(question.checkAnswer(""));
        assertFalse(question.checkAnswer("   "));
        assertFalse(question.checkAnswer("maybe"));
        assertFalse(question.checkAnswer("1"));
    }
 
    // EDGE CASE: question built without a correct answer
    @Test
    void testNullCorrectAnswerNeverMatches() {
 
        Question question = new TrueFalseQuestion(
            "Broken question", null
        );
 
        assertFalse(question.checkAnswer("True"));
    }
 
    @Test
    void testPointsAndIds() {
 
        Question question = new TrueFalseQuestion(
            4, 2, "The Sun is a star.", "True"
        );
 
        assertEquals(5, question.getPoints());
        assertEquals(4, question.getId());
        assertEquals(2, question.getCategoryId());
        assertEquals("True", question.getCorrectAnswer());
    }
}
