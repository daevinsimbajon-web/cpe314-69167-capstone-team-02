package com.triviagame.online.bank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/** JSON representation shared by saved quiz questions and room snapshots. */
@Component
public class QuestionChoices {
    private final ObjectMapper json;

    public QuestionChoices(ObjectMapper json) {
        this.json = json;
    }

    public String write(List<String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot serialize question choices", failure);
        }
    }

    public List<String> read(String value) {
        try {
            return json.readValue(value, new TypeReference<List<String>>() {});
        } catch (IOException failure) {
            throw new IllegalStateException("Saved question choices are invalid", failure);
        }
    }
}
