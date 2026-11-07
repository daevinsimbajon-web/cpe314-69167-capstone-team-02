package com.triviagame.db;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
/** Installs offline questions without deleting or duplicating saved data. */
public final class SampleQuestionSeeder {
 private static final ObjectMapper JSON = new ObjectMapper();
 private SampleQuestionSeeder() { }
 private record Seed(String category, String text, String answer,
 List<String> wrongAnswers, String difficulty,
 String type) { }
 private static final List<Seed> SEEDS = buildSeeds();
 private static List<Seed> buildSeeds() {
 List<Seed> result = new ArrayList<>();
 // SCIENCE — 5 questions of each type at each difficulty
 addMultiple(result, "Science", "easy", List.of(
 "Which planet is known as the Red Planet?|Mars|Earth|Venus|Jupiter",
 "What is the chemical formula for water?|H2O|CO2|O2|NaCl",
 "What force pulls objects toward Earth?|Gravity|Friction|Magnetism|Pressure",
 "What do bees collect from flowers to make honey?|Nectar|Sand|Salt|Clay",
 "What is the largest living mammal?|Blue whale|Elephant|Giraffe|Hippopotamus"
 ));
 addMultiple(result, "Science", "medium", List.of(
 "Which organelle produces most ATP in animal cells?|Mitochondrion|Nucleus|Ribosome|Golgi apparatus",
 "Which element has atomic number 6?|Carbon|Oxygen|Nitrogen|Hydrogen",
 "Which blood vessels generally carry blood away from the heart?|Arteries|Veins|Capillaries|Venules",
 "Which SI unit measures electrical resistance?|Ohm|Volt|Ampere|Watt",
 "Which gas is most abundant in Earth's atmosphere?|Nitrogen|Oxygen|Carbon dioxide|Argon"
 ));
 addMultiple(result, "Science", "hard", List.of(
 "Which particle mediates the electromagnetic interaction?|Photon|Neutron|Proton|Electron",
 "What is the SI unit of magnetic flux?|Weber|Tesla|Henry|Farad",
 "Which thermodynamic law describes entropy increasing in an isolated system?|Second law|First law|Zeroth law|Third law",
 "Which atmospheric layer contains most of the ozone layer?|Stratosphere|Troposphere|Mesosphere|Thermosphere",
 "Which element has the chemical symbol W?|Tungsten|Tin|Titanium|Tantalum"
 ));
 addTrueFalse(result, "Science", "easy", List.of(
 "Earth revolves around the Sun.|true",
 "An adult human typically has 206 bones.|true",
 "Sound can travel through a perfect vacuum.|false",
 "The Sun is a star.|true",
 "Pure water boils at about 100 degrees Celsius at sea level.|true"
 ));
 addTrueFalse(result, "Science", "medium", List.of(
 "An electron has a positive electric charge.|false",
 "Most human DNA is double-stranded.|true",
 "A human heart normally has four chambers.|true",
 "Venus is the closest planet to the Sun.|false",
 "Photosynthesis in green plants releases oxygen.|true"
 ));
 addTrueFalse(result, "Science", "hard", List.of(
 "Isotopes of an element have different numbers of protons.|false",
 "One mole contains about 6.022 times 10 to the power 23 entities.|true",
 "Absolute zero is the same as zero degrees Celsius.|false",
 "A light-year is a unit of time.|false",
 "A neutron has no net electric charge.|true"
 ));
 // HISTORY — 5 questions of each type at each difficulty
 addMultiple(result, "History", "easy", List.of(
 "Who was the first President of the Philippines?|Emilio Aguinaldo|Manuel Quezon|Jose Rizal|Andres Bonifacio",
 "In what year did the EDSA People Power Revolution happen?|1986|1972|1990|2001",
 "Which ancient civilization built the pyramids at Giza?|Ancient Egyptians|Romans|Greeks|Vikings",
 "In what country is the Great Wall located?|China|India|Japan|Thailand",
 "Which wall famously fell in 1989?|Berlin Wall|Great Wall|Hadrian's Wall|Western Wall"
 ));
 addMultiple(result, "History", "medium", List.of(
 "In what year did the Philippines gain independence from the United States?|1946|1898|1935|1965",
 "Who was the main author of the US Declaration of Independence?|Thomas Jefferson|George Washington|Abraham Lincoln|Benjamin Franklin",
 "Who led the Soviet Union during most of World War II?|Joseph Stalin|Vladimir Lenin|Mikhail Gorbachev|Nikita Khrushchev",
 "Who was the first person to walk on the Moon?|Neil Armstrong|Buzz Aldrin|Yuri Gagarin|John Glenn",
 "Which treaty was signed with Germany after World War I?|Treaty of Versailles|Treaty of Paris 1783|Treaty of Utrecht|Treaty of Tordesillas"
 ));
 addMultiple(result, "History", "hard", List.of(
 "In what year was the Magna Carta sealed?|1215|1066|1492|1776",
 "Which 1648 peace settlement ended the Thirty Years' War?|Peace of Westphalia|Treaty of Versailles|Treaty of Utrecht|Congress of Vienna",
 "Which empire ruled Constantinople before its capture in 1453?|Byzantine Empire|Roman Republic|Mongol Empire|British Empire",
 "Who was the first president of the French Fifth Republic?|Charles de Gaulle|Napoleon Bonaparte|Louis XIV|Francois Mitterrand",
 "Which 1494 treaty divided overseas claims between Spain and Portugal?|Treaty of Tordesillas|Treaty of Paris|Treaty of Ghent|Treaty of Zaragoza"
 ));
 addTrueFalse(result, "History", "easy", List.of(
 "Jose Rizal wrote Noli Me Tangere.|true",
 "World War II ended in 1945.|true",
 "The Philippines was once a Spanish colony.|true",
 "Ferdinand Magellan reached the Philippines in 1521.|true",
 "The entire Great Wall of China was built in one year.|false"
 ));
 addTrueFalse(result, "History", "medium", List.of(
 "The Berlin Wall fell in 1989.|true",
 "The United States declared independence in 1776.|true",
 "The capital of the Roman Empire was always Constantinople.|false",
 "The Philippines became a founding member of the United Nations in 1945.|true",
 "The French Revolution began in 1789.|true"
 ));
 addTrueFalse(result, "History", "hard", List.of(
 "The Battle of Hastings took place in 1066.|true",
 "The Treaty of Tordesillas was signed in 1494.|true",
 "The Byzantine Empire ended permanently in 1204.|false",
 "The Thirty Years' War ended in 1648.|true",
 "The Meiji Restoration began in 1868.|true"
 ));
 // ENTERTAINMENT — 5 questions of each type at each difficulty
 addMultiple(result, "Entertainment", "easy", List.of(
 "Who played Harry Potter in the film series?|Daniel Radcliffe|Tom Holland|Rupert Grint|Robert Pattinson",
 "Which Marvel hero carries a vibranium shield?|Captain America|Iron Man|Thor|Hulk",
 "Which animated movie features Elsa and Anna?|Frozen|Moana|Tangled|Brave",
 "What color is SpongeBob SquarePants?|Yellow|Blue|Green|Purple",
 "Which character is a famous mascot of Disney?|Mickey Mouse|SpongeBob SquarePants|Bugs Bunny|Garfield"
 ));
 addMultiple(result, "Entertainment", "medium", List.of(
 "Who directed the film Titanic released in 1997?|James Cameron|Steven Spielberg|Christopher Nolan|Peter Jackson",
 "Which band recorded Bohemian Rhapsody?|Queen|The Beatles|Coldplay|U2",
 "Which film series features Captain Jack Sparrow?|Pirates of the Caribbean|Star Wars|The Matrix|Jurassic Park",
 "Which singer is widely called the Queen of Pop?|Madonna|Adele|Beyonce|Celine Dion",
 "Who played Hermione Granger in the Harry Potter films?|Emma Watson|Emma Stone|Natalie Portman|Anne Hathaway"
 ));
 addMultiple(result, "Entertainment", "hard", List.of(
 "Who composed the film score for Inception?|Hans Zimmer|John Williams|Alan Silvestri|Howard Shore",
 "Which 1972 film features Vito Corleone?|The Godfather|Goodfellas|Scarface|Casino",
 "Who directed The Lord of the Rings: The Fellowship of the Ring?|Peter Jackson|James Cameron|George Lucas|Ridley Scott",
 "Which Michael Jackson album contains Billie Jean?|Thriller|Bad|Dangerous|Off the Wall",
 "Which fictional city is Batman primarily associated with?|Gotham City|Metropolis|Central City|Star City"
 ));
 addTrueFalse(result, "Entertainment", "easy", List.of(
 "Mario is commonly shown wearing a red cap.|true",
 "Elsa is a character in the movie Frozen.|true",
 "Spider-Man is a character from DC Comics.|false",
 "Toy Story features toys that come to life.|true",
 "Pikachu is a Disney cartoon character.|false"
 ));
 addTrueFalse(result, "Entertainment", "medium", List.of(
 "The Beatles originated in Liverpool.|true",
 "Thriller is an album by Michael Jackson.|true",
 "Batman was created by Stan Lee.|false",
 "James Cameron directed the 2009 film Avatar.|true",
 "Hogwarts is a school in the Harry Potter series.|true"
 ));
 addTrueFalse(result, "Entertainment", "hard", List.of(
 "The original Star Wars film was released in 1977.|true",
 "Christopher Nolan directed Interstellar.|true",
 "Citizen Kane was first released in 1941.|true",
 "The Dark Knight was released in 2008.|true",
 "Casablanca was first released in 1952.|false"
 ));
 if (result.size() != 90) {
 throw new IllegalStateException("Expected 90 preloaded questions");
 }
 return List.copyOf(result);
 }
 private static void addMultiple(List<Seed> result, String category,
 String difficulty, List<String> lines) {
 for (String line : lines) {
 String[] fields = line.split("\\|", -1);
 if (fields.length != 5) {
 throw new IllegalArgumentException("Invalid MC question: " + line);
 }
 result.add(new Seed(category, fields[0], fields[1],
 List.of(fields[2], fields[3], fields[4]),
 difficulty, "MULTIPLE_CHOICE"));
 }
 }
 private static void addTrueFalse(List<Seed> result, String category,
 String difficulty, List<String> lines) {
 for (String line : lines) {
 String[] fields = line.split("\\|", -1);
 if (fields.length != 2) {
 throw new IllegalArgumentException("Invalid T/F question: " + line);
 }
 boolean answer;
 if (fields[1].equalsIgnoreCase("true")) {
 answer = true;
 } else if (fields[1].equalsIgnoreCase("false")) {
 answer = false;
 } else {
 throw new IllegalArgumentException("Invalid T/F answer: " + line);
 }
 result.add(new Seed(category, fields[0], answer ? "True" : "False",
 List.of(answer ? "False" : "True"),
 difficulty, "TRUE_FALSE"));
 }
 }
 public static int seed() throws SQLException {
 Database.initializeSchema();
 CategoryDao categoriesDao = new CategoryDao();
 QuestionDao questionsDao = new QuestionDao();
 Map<String, Integer> categoryIds = new HashMap<>();
 Map<Integer, Set<String>> existingTexts = new HashMap<>();
 for (CategoryDao.CategoryRecord c : categoriesDao.getAllCategories()) {
 if (c.parentId() == null) {
 categoryIds.putIfAbsent(normalize(c.name()), c.id());
 }
 }
 for (QuestionDao.QuestionRecord q : questionsDao.getAllQuestions()) {
 existingTexts.computeIfAbsent(q.categoryId(), id -> new HashSet<>())
 .add(normalize(q.text()));
 }
 int inserted = 0;
 for (Seed q : SEEDS) {
 String categoryKey = normalize(q.category());
 Integer categoryId = categoryIds.get(categoryKey);
 if (categoryId == null) {
 categoryId = categoriesDao.addCategory(q.category());
 categoryIds.put(categoryKey, categoryId);
 }
 Set<String> saved = existingTexts.computeIfAbsent(
 categoryId, id -> new HashSet<>());
 if (saved.contains(normalize(q.text()))) {
 continue;
 }
 try {
 questionsDao.addQuestion(q.text(), q.answer(),
 JSON.writeValueAsString(q.wrongAnswers()),
 q.difficulty(), categoryId, q.type());
 } catch (JsonProcessingException ex) {
 throw new SQLException("Could not save offline answer choices", ex);
 }
 saved.add(normalize(q.text()));
 inserted++;
 }
 return inserted;
 }
 private static String normalize(String text) {
 return text.strip().replaceAll("\\s+", " ")
 .toLowerCase(Locale.ROOT);
 }
 public static void main(String[] args) throws SQLException {
 System.out.println("Added " + seed() + " new offline questions.");
 System.out.println("Total saved questions: "
 + new QuestionDao().getAllQuestions().size());
 }
}