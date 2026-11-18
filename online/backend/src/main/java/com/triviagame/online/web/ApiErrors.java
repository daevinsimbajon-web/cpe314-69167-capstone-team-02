package com.triviagame.online.web;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(com.triviagame.online.rooms.RoomRemovedException.class)
    ResponseEntity<?> removed(com.triviagame.online.rooms.RoomRemovedException ex){
        return ResponseEntity.status(403).body(Map.of("code","ROOM_REMOVED","message",ex.getMessage()));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", ex.getReason() == null ? "Request rejected" : ex.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Check the highlighted fields", "errors",
                ex.getBindingResult().getFieldErrors().stream().map(e -> Map.of("field",e.getField(),
                        "message",e.getDefaultMessage() == null ? "Invalid value" : e.getDefaultMessage())).toList()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<?> invalid(Exception ex) { return ResponseEntity.badRequest().body(Map.of("message","Invalid request data")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate(DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(Map.of("message","A record already exists or conflicts with saved data"));
    }
}
