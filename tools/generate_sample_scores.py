#!/usr/bin/env python3
"""Generates the demo score files for the Import Scores screen.

Questions must already exist in the database (the 15 seeded ones), so
this uses exactly their category and question texts.
Run:  python3 tools/generate_sample_scores.py
"""
import json, random

QUESTIONS = {
 "Science": ["Which planet is known as the Red Planet?",
  "What is the chemical formula for water?",
  "What force pulls objects toward Earth?",
  "What gas do plants absorb during photosynthesis?",
  "What is the largest living mammal?"],
 "History": ["Who was the first President of the Philippines?",
  "In what year did the EDSA People Power Revolution happen?",
  "Which ancient civilization built the pyramids at Giza?",
  "Who was the first person to walk on the Moon?",
  "In what year was the Magna Carta sealed?"],
 "Entertainment": ["Who played Harry Potter in the film series?",
  "Which Marvel hero carries a vibranium shield?",
  "Which animated movie features Elsa and Anna?",
  "Which video game character eats dots in a maze?",
  "What is the name of Harry Potter's school?"],
}

# Chance each player answers correctly, per category.
SKILL = {
 "Ana":    {"Science": 0.90, "History": 0.55, "Entertainment": 0.60},
 "Ben":    {"Science": 0.50, "History": 0.88, "Entertainment": 0.65},
 "Carla":  {"Science": 0.60, "History": 0.60, "Entertainment": 0.92},
 "Dino":   {"Science": 0.75, "History": 0.70, "Entertainment": 0.72},
 "Ella":   {"Science": 0.40, "History": 0.45, "Entertainment": 0.50},
 "Felix":  {"Science": 0.85, "History": 0.80, "Entertainment": 0.35},
 "Gia":    {"Science": 0.55, "History": 0.95, "Entertainment": 0.50},
 "Hugo":   {"Science": 0.65, "History": 0.50, "Entertainment": 0.85},
}

def make(path, n, skill, seed):
    rnd = random.Random(seed)
    players = list(skill)
    rows = []
    for _ in range(n):
        p = rnd.choice(players)
        c = rnd.choice(list(QUESTIONS))
        q = rnd.choice(QUESTIONS[c])
        ok = rnd.random() < skill[p][c]
        rows.append({"player": p, "category": c, "questionText": q,
                     "isCorrect": ok, "points": 10 if ok else 0})
    with open(path, "w", encoding="utf-8") as f:
        json.dump(rows, f, indent=1)
    print(path, len(rows), "entries")

make("sample-data/sample_scores_2000.json", 2000, SKILL, 314)

# Small second file for the "change the input, change the result" demo:
# Ana is now terrible at Science and Ella is now great at History.
changed = {p: dict(s) for p, s in SKILL.items()}
changed["Ana"]["Science"] = 0.0
changed["Ella"]["History"] = 1.0
only = {p: changed[p] for p in ("Ana", "Ella")}
make("sample-data/sample_scores_change_demo.json", 600, only, 7)
