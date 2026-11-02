package com.triviagame.category;

import com.triviagame.questions.Question;
import java.util.ArrayList;
import java.util.List;

public class Category {

    private String name;
    private List<Question> questions = new ArrayList<>();
    private List<Category> subcategories = new ArrayList<>();

    public Category(String name) {
        this.name = name;
    }

    public void addQuestion(Question question) {
        questions.add(question);
    }

    public void addSubcategory(Category category) {
        subcategories.add(category);
    }

    public String getName() {
        return name;
    }

    // Recursive search for a category by name
    public Category findCategory(String target) {
        if (name.equalsIgnoreCase(target)) {
            return this;
        }

        for (Category child : subcategories) {
            Category result = child.findCategory(target);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    // Collect questions from every nested level
    public List<Question> getAllQuestions() {
        List<Question> result = new ArrayList<>(questions);

        for (Category child : subcategories) {
            result.addAll(child.getAllQuestions());
        }

        return result;
    }
}
