package com.triviagame.online.practice;

/** A scored answer retained on the practice undo stack. */
public record PracticeAnswer(
        PracticeQuestion question, int selectedIndex, boolean correct, int points) {}
