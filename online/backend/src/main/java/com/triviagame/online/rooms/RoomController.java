package com.triviagame.online.rooms;

import com.triviagame.online.rooms.dto.RoomAnswerRequest;
import com.triviagame.online.rooms.dto.RoomAttempt;
import com.triviagame.online.rooms.dto.RoomCreateRequest;
import com.triviagame.online.rooms.dto.RoomJoinRequest;
import com.triviagame.online.rooms.dto.RoomRetrySettings;
import com.triviagame.online.rooms.dto.RoomSummary;
import com.triviagame.online.rooms.dto.RoomView;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping
    List<RoomSummary> list(Principal user) {
        return rooms.list(id(user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoomView create(Principal user, @Valid @RequestBody RoomCreateRequest input) {
        return rooms.create(id(user), input, rooms.prepare(input));
    }

    @GetMapping("/{code}")
    RoomView get(Principal user, @PathVariable String code) {
        return rooms.get(code, id(user));
    }

    @PostMapping("/{code}/join")
    RoomView join(
            Principal user, @PathVariable String code, @Valid @RequestBody RoomJoinRequest input) {
        return rooms.join(code, id(user), input.password());
    }

    @PostMapping("/{code}/start")
    RoomView start(Principal user, @PathVariable String code) {
        return rooms.start(code, id(user));
    }

    @PatchMapping("/{code}/settings")
    RoomView settings(
            Principal user,
            @PathVariable String code,
            @Valid @RequestBody RoomRetrySettings input) {
        return rooms.settings(code, id(user), input);
    }

    @PostMapping("/{code}/next")
    RoomView next(Principal user, @PathVariable String code) {
        return rooms.next(code, id(user));
    }

    @PostMapping("/{code}/end")
    RoomView end(Principal user, @PathVariable String code) {
        return rooms.end(code, id(user));
    }

    @PostMapping("/{code}/answers")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RoomAttempt answer(
            Principal user,
            @PathVariable String code,
            @Valid @RequestBody RoomAnswerRequest input) {
        return rooms.answer(code, id(user), input);
    }

    @DeleteMapping("/{code}/answers/{number}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void undo(Principal user, @PathVariable String code, @PathVariable int number) {
        rooms.undo(code, id(user), number);
    }
    @PostMapping("/{code}/retry")
    RoomView retry(Principal user,@PathVariable String code,@Valid @RequestBody com.triviagame.online.rooms.dto.RoomRetryDecision input){return rooms.retry(code,id(user),input);}

    @PostMapping("/{code}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(Principal user, @PathVariable String code) {
        rooms.leave(code, id(user));
    }

    @DeleteMapping("/{code}/participants/{participant}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(Principal user, @PathVariable String code, @PathVariable UUID participant) {
        rooms.remove(code, id(user), participant);
    }

    private static UUID id(Principal user) {
        return UUID.fromString(user.getName());
    }
}
