
package com.triviagame.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CategoryDaoTest {

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Database.initializeSchema();
    }

    @Test
    void testInsertAndSelectCategory()
            throws SQLException {

        CategoryDao dao = new CategoryDao();

        String name =
                "Science_" + UUID.randomUUID();

        int categoryId = dao.addCategory(name);

        try {
            assertTrue(categoryId > 0);

            CategoryDao.CategoryRecord category =
                    dao.getCategoryById(categoryId)
                            .orElseThrow();

            assertEquals(categoryId, category.id());
            assertEquals(name, category.name());
            assertNull(category.parentId());

            assertTrue(
                    dao.getAllCategories()
                            .stream()
                            .anyMatch(c ->
                                    c.id() == categoryId
                            )
            );

        } finally {
            dao.deleteCategory(categoryId);
        }

        assertTrue(
                dao.getCategoryById(categoryId).isEmpty()
        );
    }

    @Test
    void testSubcategories() throws SQLException {

        CategoryDao dao = new CategoryDao();

        int parentId = dao.addCategory(
                "Science_" + UUID.randomUUID()
        );

        int childId = 0;

        try {
            childId = dao.addCategory(
                    "Physics_" + UUID.randomUUID(),
                    parentId
            );

            List<CategoryDao.CategoryRecord> children =
                    dao.getSubcategories(parentId);

            int expectedChildId = childId;

            assertTrue(
                    children.stream()
                            .anyMatch(c ->
                                    c.id() == expectedChildId
                                    && c.parentId() != null
                                    && c.parentId() == parentId
                            )
            );

        } finally {

            if (childId > 0) {
                dao.deleteCategory(childId);
            }

            dao.deleteCategory(parentId);
        }
    }

    @Test
    void testInvalidCategoryName() {

        CategoryDao dao = new CategoryDao();

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.addCategory("")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.addCategory("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.addCategory(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.addCategory("Science", -1)
        );
    }

    @Test
    void testNonexistentCategory()
            throws SQLException {

        CategoryDao dao = new CategoryDao();

        assertTrue(
                dao.getCategoryById(
                        Integer.MAX_VALUE
                ).isEmpty()
        );
    }
}
