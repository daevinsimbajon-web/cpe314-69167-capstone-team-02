package com.triviagame.db;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SampleQuestionSeeder {

    private SampleQuestionSeeder() {
    }

    private record SampleQuestion(
            String text,
            String correctAnswer,
            String incorrectAnswers,
            String difficulty
    ) {
    }

    private record SeedCategory(
            String name,
            List<SampleQuestion> questions
    ) {
    }

    private static final List<SeedCategory> SEED_DATA =
            List.of(

                // SCIENCE QUESTIONS
                new SeedCategory("Science", List.of(
                    new SampleQuestion(
                        "Which planet is known as the Red Planet?",
                        "Mars",
                        "[\"Earth\",\"Venus\",\"Jupiter\"]",
                        "easy"
                    ),
                    new SampleQuestion(
                        "What is the chemical formula for water?",
                        "H2O",
                        "[\"CO2\",\"O2\",\"NaCl\"]",
                        "easy"
                    ),
                    new SampleQuestion(
                        "What force pulls objects toward Earth?",
                        "Gravity",
                        "[\"Friction\",\"Magnetism\",\"Pressure\"]",
                        "easy"
                    ),
                    new SampleQuestion(
                        "What gas do plants absorb during photosynthesis?",
                        "Carbon dioxide",
                        "[\"Oxygen\",\"Nitrogen\",\"Hydrogen\"]",
                        "medium"
                    ),
                    new SampleQuestion(
                        "What is the largest living mammal?",
                        "Blue whale",
                        "[\"Elephant\",\"Giraffe\",\"Hippopotamus\"]",
                        "easy"
                    )
                )),

                // HISTORY QUESTIONS
                new SeedCategory("History", List.of(
                    new SampleQuestion(
                        "Who was the first President of the Philippines?",
                        "Emilio Aguinaldo",
                        "[\"Manuel Quezon\",\"Jose Rizal\",\"Andres Bonifacio\"]",
                        "easy"
                    ),
                    new SampleQuestion(
                        "In what year did the EDSA People Power Revolution happen?",
                        "1986",
                        "[\"1972\",\"1990\",\"2001\"]",
                        "easy"
                    ),
                    new SampleQuestion(
                        "Which ancient civilization built the pyramids at Giza?",
                        "Ancient Egyptians",
                        "[\"Romans\",\"Greeks\",\"Vikings\"]",
                        "easy"
                    ),
                    new SampleQuestion(
                        "Who was the first person to walk on the Moon?",
                        "Neil Armstrong",
                        "[\"Buzz Aldrin\",\"Yuri Gagarin\",\"John Glenn\"]",
                        "medium"
                    ),
                    new SampleQuestion(
                        "In what year was the Magna Carta sealed?",
                        "1215",
                        "[\"1066\",\"1492\",\"1776\"]",
                        "hard"
                    )
                )),

                // ENTERTAINMENT QUESTIONS
                new SeedCategory("Entertainment", List.of(
                    new SampleQuestion(
                        "Who played Harry Potter in the film series?",
                        "Daniel Radcliffe",
                        "[\"Tom Holland\",\"Rupert Grint\",\"Robert Pattinson\"]",
                        "easy"
                    ),
                    new SampleQuestion(
                        "Which Marvel hero carries a vibranium shield?",
                        "Captain America",
                        "[\"Iron Man\",\"Thor\",\"Hulk\"]",
                        "easy"
                    ),
                    new SampleQuestion(
                        "Which animated movie features Elsa and Anna?",
                        "Frozen",
                        "[\"Moana\",\"Tangled\",\"Brave\"]",
                        "easy"
                    ),
                    new SampleQuestion(
                        "Which video game character eats dots in a maze?",
                        "Pac-Man",
                        "[\"Sonic\",\"Mario\",\"Kirby\"]",
                        "medium"
                    ),
                    new SampleQuestion(
                        "What is the name of Harry Potter's school?",
                        "Hogwarts",
                        "[\"Nevermore\",\"Durmstrang\",\"Camp Half-Blood\"]",
                        "easy"
                    )
                ))
            );

	    public static int seed() throws SQLException {
	
	        Database.initializeSchema();
	
	        CategoryDao categoryDao = new CategoryDao();
	        QuestionDao questionDao = new QuestionDao();
	
	        int insertedCount = 0;
	
	        // INSERT MULTIPLE-CHOICE QUESTIONS
	        for (SeedCategory seedCategory : SEED_DATA) {
	
	            int categoryId = getOrCreateCategory(
	                    categoryDao,
	                    seedCategory.name()
	            );
	
	            // Prevent duplicate questions
	            Set<String> existingQuestions = new HashSet<>();
	
	            for (QuestionDao.QuestionRecord question :
	                    questionDao.getQuestionsByCategory(categoryId)) {
	
	                existingQuestions.add(
	                        normalize(question.text())
	                );
	            }
	
	            for (SampleQuestion question :
	                    seedCategory.questions()) {
	
	                String key = normalize(question.text());
	
	                if (existingQuestions.contains(key)) {
	                    continue;
	                }
	
	                questionDao.addQuestion(
	                        question.text(),
	                        question.correctAnswer(),
	                        question.incorrectAnswers(),
	                        question.difficulty(),
	                        categoryId,
	                        "MULTIPLE_CHOICE"
	                );
	
	                existingQuestions.add(key);
	                insertedCount++;
	            }
	        }
	
	        // INSERT TRUE/FALSE QUESTIONS
	
	        // Science category
	        int scienceId = getOrCreateCategory(
	                categoryDao, "Science"
	        );
	
	        String tfText1 = "The Sun is a star.";
	
	        boolean exists1 = questionDao
	                .getQuestionsByCategory(scienceId)
	                .stream()
	                .anyMatch(q -> q.text().equalsIgnoreCase(tfText1));
	
	        if (!exists1) {
	
	            questionDao.addQuestion(
	                    tfText1,
	                    "True",
	                    "[\"False\"]",
	                    "easy",
	                    scienceId,
	                    "TRUE_FALSE"
	            );
	
	            insertedCount++;
	        }
	
	        // History category
	        int historyId = getOrCreateCategory(
	                categoryDao, "History"
	        );
	
	        String tfText2 = "World War II ended in 1945.";
	
	        boolean exists2 = questionDao
	                .getQuestionsByCategory(historyId)
	                .stream()
	                .anyMatch(q -> q.text().equalsIgnoreCase(tfText2));
	
	        if (!exists2) {
	
	            questionDao.addQuestion(
	                    tfText2,
	                    "True",
	                    "[\"False\"]",
	                    "easy",
	                    historyId,
	                    "TRUE_FALSE"
	            );
	
	            insertedCount++;
	        }
	
	        // Return AFTER inserting all questions
	        return insertedCount;
	    }

    private static int getOrCreateCategory(
            CategoryDao categoryDao,
            String categoryName
    ) throws SQLException {

        for (CategoryDao.CategoryRecord category :
                categoryDao.getAllCategories()) {

            if (category.parentId() == null
                    && category.name()
                            .equalsIgnoreCase(categoryName)) {

                return category.id();
            }
        }

        return categoryDao.addCategory(categoryName);
    }

    private static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    // Allows running the seeder independently.
    public static void main(String[] args)
            throws SQLException {

        int insertedCount = seed();

        System.out.println(
                "New questions inserted: " + insertedCount
        );

        CategoryDao categoryDao = new CategoryDao();
        QuestionDao questionDao = new QuestionDao();

        for (CategoryDao.CategoryRecord category :
                categoryDao.getAllCategories()) {

            if (category.parentId() != null) {
                continue;
            }

            for (SeedCategory seedCategory : SEED_DATA) {

                if (seedCategory.name()
                        .equalsIgnoreCase(category.name())) {

                    int count = questionDao
                            .getQuestionsByCategory(category.id())
                            .size();

                    System.out.println(
                            category.name() + ": " +
                            count + " questions"
                    );
                }
            }
        }
    }
}