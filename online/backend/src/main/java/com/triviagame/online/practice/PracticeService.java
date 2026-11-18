package com.triviagame.online.practice;

import com.triviagame.online.bank.PersonalQuizzes;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Coordinates owned quiz snapshots and pure practice rules. */
@Service
public class PracticeService {
    private final PersonalQuizzes quizzes;
    private final PracticeRepository sessions;

    public PracticeService(PersonalQuizzes quizzes, PracticeRepository sessions) {
        this.quizzes = quizzes;
        this.sessions = sessions;
    }

    @Transactional
    public PracticeView start(UUID user, UUID quiz) {
        if (!sessions.lockOwner(user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        }
        var playable = quizzes.playable(user, quiz);
        List<PracticeQuestion> questions =
                playable.questions().stream()
                        .map(entry -> {
                            var question = entry.question();
                            var choices =
                                    question.questionType().equals("TRUE_FALSE")
                                            ? new java.util.ArrayList<>(List.of("True", "False"))
                                            : new java.util.ArrayList<>(
                                                    question.incorrectAnswers());
                            if (!question.questionType().equals("TRUE_FALSE")) {
                                choices.add(question.correctAnswer());
                                java.util.Collections.shuffle(choices);
                            }
                            return new PracticeQuestion(
                                    question.text(),
                                    question.questionType(),
                                    choices,
                                    choices.indexOf(question.correctAnswer()));
                        })
                        .toList();
        return view(sessions.create(user, quiz, PracticeEngine.start(questions)));
    }

    @Transactional(readOnly = true)
    public PracticeView get(UUID user, UUID id) {
        return view(sessions.load(user, id, false));
    }

    @Transactional
    public PracticeView act(UUID user, UUID id, int version, UUID action, Integer selectedIndex) {
        PracticeSession session = sessions.load(user, id, true);
        if (action.equals(session.lastAction())) {
            return view(session);
        }
        if (version != session.version()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Practice changed in another tab. Reload before answering.");
        }
        PracticeEngine engine = new PracticeEngine(session.state());
        if (selectedIndex == null) {
            engine.undo();
        } else {
            engine.answer(selectedIndex);
        }
        return view(sessions.save(session, engine.state(), action));
    }

    private PracticeView view(PracticeSession session) {
        PracticeState state = session.state();
        PracticeQuestion question = state.remaining().isEmpty() ? null : state.remaining().get(0);
        PracticeAnswer last = state.history().isEmpty() ? null : state.history().get(0);
        return new PracticeView(
                session.id(),
                session.version(),
                state.score(),
                state.history().size(),
                state.history().size() + state.remaining().size(),
                question == null ? null : question.text(),
                question == null ? List.of() : question.choices(),
                question == null ? null : question.type(),
                last != null,
                last == null ? null : last.question().choices().get(last.question().correctIndex()),
                last == null ? null : last.correct(),
                last == null ? null : last.points());
    }
}
