package com.triviagame.online.practice;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/** Thin authenticated HTTP adapter for private quiz practice. */
@RestController
@RequestMapping("/api")
public class PracticeController {
    private final PracticeService practice;

    public PracticeController(PracticeService practice) {
        this.practice = practice;
    }

    @PostMapping("/quizzes/{quiz}/practice")
    public PracticeView start(Principal user, @PathVariable UUID quiz) {
        return practice.start(UUID.fromString(user.getName()), quiz);
    }

    @GetMapping("/practice/{id}")
    public PracticeView get(Principal user, @PathVariable UUID id) {
        return practice.get(UUID.fromString(user.getName()), id);
    }

    @PostMapping("/practice/{id}/answers")
    public PracticeView answer(
            Principal user,
            @PathVariable UUID id,
            @Valid @RequestBody PracticeAnswerRequest input) {
        return practice.act(
                UUID.fromString(user.getName()),
                id,
                input.version(),
                input.actionId(),
                input.selectedIndex());
    }

    @PostMapping("/practice/{id}/undo")
    public PracticeView undo(
            Principal user, @PathVariable UUID id, @Valid @RequestBody PracticeUndoRequest input) {
        return practice.act(
                UUID.fromString(user.getName()), id, input.version(), input.actionId(), null);
    }
}
