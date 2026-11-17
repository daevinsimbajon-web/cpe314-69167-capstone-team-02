package com.triviagame.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triviagame.online.practice.PracticeEngine;
import com.triviagame.online.practice.PracticeQuestion;

import org.junit.jupiter.api.Test;

import java.util.List;

class PracticeEngineTest {
    private final PracticeQuestion first =
            new PracticeQuestion("The Moon is a star.", "TRUE_FALSE", List.of("True", "False"), 1);
    private final PracticeQuestion second =
            new PracticeQuestion(
                    "Which is a planet?", "MULTIPLE_CHOICE", List.of("Mars", "Sun"), 0);

    @Test
    void undoRestoresScoredQuestionsInReverseOrderAndCanResumeAfterCompletion() {
        var engine = new PracticeEngine(PracticeEngine.start(List.of(first, second)));
        engine.answer(1);
        assertThat(engine.state().score()).isEqualTo(5);
        assertThat(engine.state().remaining()).containsExactly(second);
        engine.answer(0);
        assertThat(engine.state().score()).isEqualTo(15);
        assertThat(engine.state().remaining()).isEmpty();
        engine = new PracticeEngine(engine.state());
        engine.undo();
        assertThat(engine.state().score()).isEqualTo(5);
        assertThat(engine.state().remaining()).containsExactly(second);
        engine.undo();
        assertThat(engine.state().score()).isZero();
        assertThat(engine.state().remaining()).containsExactly(first, second);
        engine.answer(0);
        assertThat(engine.state().score()).isZero();
        engine.undo();
        engine.answer(1);
        assertThat(engine.state().score()).isEqualTo(5);
    }

    @Test
    void invalidActionsNeverConsumeQuestionsOrAlterScore() {
        var engine = new PracticeEngine(PracticeEngine.start(List.of(first)));
        assertThatThrownBy(engine::undo).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.answer(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.answer(2)).isInstanceOf(IllegalArgumentException.class);
        assertThat(engine.state().remaining()).containsExactly(first);
        engine.answer(1);
        assertThatThrownBy(() -> engine.answer(1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(engine.state().score()).isEqualTo(5);
        assertThatThrownBy(() -> PracticeEngine.start(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
