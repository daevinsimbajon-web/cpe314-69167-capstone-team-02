"""Export desktop questions through SQLite's read-only mode. Never changes its schema or data."""
import argparse
from contextlib import closing
import json
from pathlib import Path
import sqlite3


def export_questions(database: Path, output: Path) -> int:
    database, output = database.resolve(strict=True), output.resolve()
    if database == output or output.suffix.lower() != '.json':
        raise ValueError('Use a separate .json output file.')
    with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as connection:
        connection.row_factory = sqlite3.Row
        columns = {row['name'] for row in connection.execute('PRAGMA table_info(questions)')}
        question_type = 'q.question_type' if 'question_type' in columns else "'MULTIPLE_CHOICE'"
        external_id = 'q.external_id' if 'external_id' in columns else 'NULL'
        rows = connection.execute(f'''
            SELECT c.name AS category,q.text,q.correct_answer,q.incorrect_answers,q.difficulty,
                   {question_type} AS question_type,{external_id} AS external_id
            FROM questions q JOIN categories c ON c.id=q.category_id ORDER BY q.id
        ''').fetchall()
        questions = [dict(category=row['category'], text=row['text'], correctAnswer=row['correct_answer'],
                          incorrectAnswers=json.loads(row['incorrect_answers']), difficulty=row['difficulty'],
                          questionType=row['question_type'], externalId=row['external_id']) for row in rows]
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation prevents overwriting an existing export or any other file.
    with output.open('x', encoding='utf-8') as file:
        json.dump({'questions': questions}, file, ensure_ascii=False, indent=2)
    return len(questions)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    arguments = parser.parse_args()
    print(f'Exported {export_questions(arguments.database, arguments.output)} questions.')
