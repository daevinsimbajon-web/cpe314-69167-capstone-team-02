package com.triviagame.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

public class CategoryDao {

    // Represents a category record from SQLite
    public record CategoryRecord(
            int id,
            String name,
            Integer parentId
    ) {
    }

    // INSERT: Create a main category
    public int addCategory(String name)
            throws SQLException {

        return addCategory(name, null);
    }

    // INSERT: Create a category or subcategory
    public int addCategory(
            String name,
            Integer parentId
    ) throws SQLException {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Category name cannot be empty"
            );
        }

        if (parentId != null && parentId <= 0) {
            throw new IllegalArgumentException(
                    "Parent ID must be positive"
            );
        }

        String sql = """
            INSERT INTO categories (name, parent_id)
            VALUES (?, ?)
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, name.trim());

            if (parentId == null) {
                statement.setNull(2, Types.INTEGER);
            } else {
                statement.setInt(2, parentId);
            }

            int rows = statement.executeUpdate();

            if (rows != 1) {
                throw new SQLException(
                        "Failed to insert category"
                );
            }

            try (PreparedStatement idStatement =
                     connection.prepareStatement(
                             "SELECT last_insert_rowid() AS id"
                     );
                 ResultSet result =
                     idStatement.executeQuery()) {

                if (result.next()) {
                    return result.getInt("id");
                }

                throw new SQLException(
                        "Category ID could not be retrieved"
                );
            }
        }
    }

    // SELECT: Find category by ID
    public Optional<CategoryRecord> getCategoryById(
            int id
    ) throws SQLException {

        String sql = """
            SELECT id, name, parent_id
            FROM categories
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet result =
                     statement.executeQuery()) {

                if (result.next()) {
                    return Optional.of(
                            mapCategory(result)
                    );
                }
            }
        }

        return Optional.empty();
    }

    // SELECT: Get all categories
    public List<CategoryRecord> getAllCategories()
            throws SQLException {

        List<CategoryRecord> categories =
                new ArrayList<>();

        String sql = """
            SELECT id, name, parent_id
            FROM categories
            ORDER BY name ASC, id ASC
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result =
                     statement.executeQuery()) {

            while (result.next()) {
                categories.add(mapCategory(result));
            }
        }

        return categories;
    }

    // SELECT: Get subcategories under a parent
    public List<CategoryRecord> getSubcategories(
            int parentId
    ) throws SQLException {

        List<CategoryRecord> categories =
                new ArrayList<>();

        String sql = """
            SELECT id, name, parent_id
            FROM categories
            WHERE parent_id = ?
            ORDER BY name ASC, id ASC
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, parentId);

            try (ResultSet result =
                     statement.executeQuery()) {

                while (result.next()) {
                    categories.add(
                            mapCategory(result)
                    );
                }
            }
        }

        return categories;
    }

    // DELETE: Remove category by ID
    public boolean deleteCategory(int id)
            throws SQLException {

        String sql = """
            DELETE FROM categories
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            return statement.executeUpdate() > 0;
        }
    }

    // Convert SQL result to Java category record
    private CategoryRecord mapCategory(
            ResultSet result
    ) throws SQLException {

        int id = result.getInt("id");

        String name = result.getString("name");

        int parentValue = result.getInt("parent_id");

        Integer parentId = result.wasNull()
                ? null
                : parentValue;

        return new CategoryRecord(
                id,
                name,
                parentId
        );
    }
    public List<QuestionDao.QuestionRecord>
    		getQuestionsRecursively(int categoryId)
    		throws SQLException {

    	List<QuestionDao.QuestionRecord> questions =
    			new ArrayList<>();

    	collectQuestions(
    		categoryId, questions, new HashSet<>()
    	);

    	return questions;
    }

    private void collectQuestions(
    	int categoryId,
    	List<QuestionDao.QuestionRecord> questions,
    	Set<Integer> visited
    ) throws SQLException {

    	// Prevent repeatedly visiting the same category.
		if (!visited.add(categoryId)) {
		    return;
		}
		
		QuestionDao questionDao = new QuestionDao();
		
		// Get questions directly under this category.
		questions.addAll(
		    questionDao.getQuestionsByCategory(categoryId)
		);
	
		// Recursively search every child category.
		for (CategoryRecord child :
		        getSubcategories(categoryId)) {
		
		    collectQuestions(
		        child.id(), questions, visited
		    );
		}
    }
}