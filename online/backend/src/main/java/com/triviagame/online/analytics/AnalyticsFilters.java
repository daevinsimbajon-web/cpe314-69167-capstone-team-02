package com.triviagame.online.analytics;

/** Pure report filtering shared by export orchestration and tests. */
public final class AnalyticsFilters {
    private AnalyticsFilters() {}

    public static AnalyticsExport apply(
            AnalyticsExport original, String category, String mode, String ruleset) {
        var games =
                original.games().stream()
                        .filter(game -> ruleset.isEmpty() || ruleset.equals(game.scoringVersion()))
                        .map(game -> {
                            if (category.isEmpty() && mode.isEmpty()) {
                                return game;
                            }
                            var questions =
                                    game.questions().stream()
                                            .filter(q ->
                                                    (category.isEmpty()
                                                                    || category.equals(
                                                                            q.category()))
                                                            && (mode.isEmpty()
                                                                    || mode.equals(
                                                                            q.questionType())))
                                            .toList();
                            return new AnalyticsExport.Game(
                                    game.gameId(),
                                    game.playedAt(),
                                    questions.stream()
                                            .mapToInt(AnalyticsExport.Question::pointsEarned)
                                            .sum(),
                                    questions.stream()
                                            .mapToInt(AnalyticsExport.Question::maxPossiblePoints)
                                            .sum(),
                                    game.won(),
                                    game.scoringVersion(),
                                    questions.size(),
                                    game.questionsComplete(),
                                    questions);
                        })
                        .filter(game ->
                                category.isEmpty() && mode.isEmpty() || !game.questions().isEmpty())
                        .toList();
        return new AnalyticsExport(
                original.format(),
                original.schemaVersion(),
                original.exportedAt(),
                original.player(),
                games);
    }
}
