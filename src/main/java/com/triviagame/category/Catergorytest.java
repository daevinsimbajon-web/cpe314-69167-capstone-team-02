package com.triviagame.category;
 
import static org.junit.jupiter.api.Assertions.*;
 
import com.triviagame.questions.Question;
import com.triviagame.questions.TrueFalseQuestion;
 
import java.util.List;
 
import org.junit.jupiter.api.Test;
 
public class CategoryTest {
 
    /*
     * Tree used for the hand-trace:
     *
     *   Trivia
     *   ├── Science        (questionScience)
     *   │   └── Physics    (questionPhysics)
     *   │       └── Astrophysics
     *   │           └── Black Holes (questionBlackHoles)
     *   └── History        (questionHistory)
     */
    private final Question questionScience =
        new TrueFalseQuestion("Science question", "True");
    private final Question questionPhysics =
        new TrueFalseQuestion("Physics question", "True");
    private final Question questionBlackHoles =
        new TrueFalseQuestion("Black hole question", "True");
    private final Question questionHistory =
        new TrueFalseQuestion("History question", "True");
 
    private Category root;
    private Category science;
    private Category blackHoles;
 
    private void buildTree() {
 
        root = new Category("Trivia");
        science = new Category("Science");
        Category physics = new Category("Physics");
        Category astrophysics = new Category("Astrophysics");
        blackHoles = new Category("Black Holes");
        Category history = new Category("History");
 
        root.addSubcategory(science);
        root.addSubcategory(history);
        science.addSubcategory(physics);
        physics.addSubcategory(astrophysics);
        astrophysics.addSubcategory(blackHoles);
 
        science.addQuestion(questionScience);
        physics.addQuestion(questionPhysics);
        blackHoles.addQuestion(questionBlackHoles);
        history.addQuestion(questionHistory);
    }
 
    @Test
    void testFindCategoryAtAnyDepthIgnoringCase() {
 
        buildTree();
 
        assertSame(blackHoles, root.findCategory("black holes"));
        assertSame(science, root.findCategory("SCIENCE"));
        assertSame(root, root.findCategory("trivia"));
    }
 
    // EDGE CASE: names that are not in the tree
    @Test
    void testFindCategoryReturnsNullWhenMissing() {
 
        buildTree();
 
        assertNull(root.findCategory("Chemistry"));
        assertNull(root.findCategory(""));
        assertNull(root.findCategory(null));
    }
 
    @Test
    void testGetAllQuestionsCollectsEveryLevel() {
 
        buildTree();
 
        List<Question> all = root.getAllQuestions();
 
        assertEquals(4, all.size());
 
        // Own questions first, then each child depth-first
        assertSame(questionScience, all.get(0));
        assertSame(questionPhysics, all.get(1));
        assertSame(questionBlackHoles, all.get(2));
        assertSame(questionHistory, all.get(3));
    }
 
    @Test
    void testGetAllQuestionsOfSubtreeOnly() {
 
        buildTree();
 
        List<Question> scienceQuestions = science.getAllQuestions();
 
        assertEquals(3, scienceQuestions.size());
        assertFalse(scienceQuestions.contains(questionHistory));
    }
 
    // EDGE CASE: category with no questions and no children
    @Test
    void testEmptyCategoryReturnsEmptyList() {
 
        Category empty = new Category("Empty");
 
        assertNotNull(empty.getAllQuestions());
        assertTrue(empty.getAllQuestions().isEmpty());
        assertSame(empty, empty.findCategory("empty"));
    }
}
