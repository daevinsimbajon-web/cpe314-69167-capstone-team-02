package com.triviagame.questions;
 
import static org.junit.jupiter.api.Assertions.*;
 
import java.util.HashSet;
import java.util.List;
import java.util.Set;
 
import org.junit.jupiter.api.Test;
 
public class MultipleChoiceQuestionTest {
 
    private MultipleChoiceQuestion planetQuestion() {
        return new MultipleChoiceQuestion(
            "Which planet is known as the Red Planet?",
            new String[]{"Mars", "Earth", "Venus", "Jupiter"},
            "Mars"
        );
    }
 
    // BUG FIX: numeric answers used to be read as a position
    @Test
    void testNumericAnswerIsComparedAsText() {
 
        Question question = new MultipleChoiceQuestion(
            "In what year did the EDSA People Power Revolution happen?",
            new String[]{"1972", "1986", "1990", "2001"},
            "1986"
        );
 
        assertTrue(question.checkAnswer("1986"));
        assertFalse(question.checkAnswer("1972"));
    }
 
    @Test
    void testPositionNumberSelectsDisplayedChoice() {
 
        MultipleChoiceQuestion question = planetQuestion();
        List<String> shown = question.getChoices();
 
        int correctPosition = shown.indexOf("Mars") + 1;
        int wrongPosition = shown.indexOf("Earth") + 1;
 
        assertTrue(
            question.checkAnswer(String.valueOf(correctPosition))
        );
        assertFalse(
            question.checkAnswer(String.valueOf(wrongPosition))
        );
    }
 
    // EDGE CASE: positions outside the valid range
    @Test
    void testOutOfRangePositionsAreWrong() {
 
        MultipleChoiceQuestion question = planetQuestion();
 
        assertFalse(question.checkAnswer("0"));
        assertFalse(question.checkAnswer("5"));
        assertFalse(question.checkAnswer("-1"));
        assertFalse(question.checkAnswer("999"));
    }
 
    // EDGE CASE: missing or empty input
    @Test
    void testNullAndBlankInputAreWrong() {
 
        MultipleChoiceQuestion question = planetQuestion();
 
        assertFalse(question.checkAnswer(null));
        assertFalse(question.checkAnswer(""));
        assertFalse(question.checkAnswer("   "));
    }
 
    @Test
    void testTextAnswerIgnoresCaseAndSpaces() {
 
        MultipleChoiceQuestion question = planetQuestion();
 
        assertTrue(question.checkAnswer("mars"));
        assertTrue(question.checkAnswer("  MARS  "));
        assertFalse(question.checkAnswer("Earth"));
        assertFalse(question.checkAnswer("Saturn"));
    }
 
    @Test
    void testGetChoicesContainsEveryChoice() {
 
        MultipleChoiceQuestion question = planetQuestion();
 
        List<String> shown = question.getChoices();
 
        assertEquals(4, shown.size());
        assertEquals(
            Set.of("Mars", "Earth", "Venus", "Jupiter"),
            new HashSet<>(shown)
        );
    }
 
    @Test
    void testGetChoicesKeepsTheSameOrderEveryCall() {
 
        MultipleChoiceQuestion question = planetQuestion();
 
        assertEquals(question.getChoices(), question.getChoices());
    }
 
    @Test
    void testGetChoicesCannotBeModified() {
 
        MultipleChoiceQuestion question = planetQuestion();
 
        assertThrows(
            UnsupportedOperationException.class,
            () -> question.getChoices().add("Pluto")
        );
    }
 
    // The constructor copies the array it is given
    @Test
    void testChoicesAreCopiedDefensively() {
 
        String[] original = {"Mars", "Earth", "Venus", "Jupiter"};
 
        MultipleChoiceQuestion question = new MultipleChoiceQuestion(
            "Red Planet?", original, "Mars"
        );
 
        original[0] = "HACKED";
        original[1] = "HACKED";
 
        assertFalse(question.getChoices().contains("HACKED"));
    }
 
    @Test
    void testPointsAndIds() {
 
        MultipleChoiceQuestion question = new MultipleChoiceQuestion(
            7, 3, "Red Planet?",
            new String[]{"Mars", "Earth"}, "Mars"
        );
 
        assertEquals(10, question.getPoints());
        assertEquals(7, question.getId());
        assertEquals(3, question.getCategoryId());
        assertEquals("Red Planet?", question.getText());
        assertEquals("Mars", question.getCorrectAnswer());
    }
}
