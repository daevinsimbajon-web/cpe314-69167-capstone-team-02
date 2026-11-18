package com.triviagame.model.questions;

import com.triviagame.dao.QuestionDao.QuestionRecord;
import com.triviagame.gameplay.GameEngine;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CategoryQuestionTest {
    @Test
    void unrecognizedCategoryKeepsTheBaseQuestionAndItsScoring() {
        var record = new QuestionRecord(1, "Sky?", "True", "[]", "easy", 2, "TRUE_FALSE");
        Question original = QuestionMapper.toQuestion(record);
        Question mapped = QuestionMapper.toQuestion(record, List.of("Space", "General Knowledge"));

        assertInstanceOf(TrueFalseQuestion.class, mapped);
        assertEquals(original.getPoints(), mapped.getPoints());
        assertFalse(mapped.checkAnswer("False"));
    }

    @Test void categoryRulesArePolymorphicAndKeepBothAnswerFormats() throws Exception {
        var tf=new QuestionRecord(1,"Sky?","True","[]","easy",2,"TRUE_FALSE");
        var mc=new QuestionRecord(2,"Star?","Sun","[\"Moon\",\"Mars\"]","easy",3,"MULTIPLE_CHOICE");
        Question science=QuestionMapper.toQuestion(tf,List.of("Physics","Science"));
        Question history=QuestionMapper.toQuestion(tf,List.of("History"));
        Question entertainment=QuestionMapper.toQuestion(mc,List.of("Entertainment: Film"));
        assertInstanceOf(ScienceQuestion.class,science);
        assertInstanceOf(HistoryQuestion.class,history);
        assertInstanceOf(EntertainmentQuestion.class,entertainment);
        assertEquals(7,science.getPoints());assertEquals(6,history.getPoints());
        assertEquals(10,entertainment.getPoints());
        assertTrue(science.checkAnswer("True"));assertTrue(entertainment.getChoices().contains("Sun"));
        GameEngine engine=new GameEngine();engine.loadQuestions(List.of(science,entertainment));
        assertTrue(engine.submitAnswer("True"));assertTrue(engine.submitAnswer("Sun"));
        assertEquals(17,engine.getScore());assertTrue(engine.undo());assertEquals(7,engine.getScore());
    }
}
