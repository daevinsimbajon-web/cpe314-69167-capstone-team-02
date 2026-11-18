package com.triviagame.online.bank;

import com.triviagame.api.BankQuestion;
import java.util.UUID;

/** A question to snapshot, with a source ID only for a saved quiz question. */
public record QuestionSourceEntry(UUID questionId, BankQuestion question) {}
