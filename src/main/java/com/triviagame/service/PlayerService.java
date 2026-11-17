package com.triviagame.service;

import com.triviagame.db.PlayerDao;
import com.triviagame.db.ScoreDao;
import java.sql.SQLException;
import java.util.List;

/** Performs player management outside the JavaFX view. */
public final class PlayerService {
    private final PlayerDao players = new PlayerDao();
    private final ScoreDao scores = new ScoreDao();

    public List<PlayerDao.PlayerRecord> all() throws SQLException { return players.getAllPlayers(); }
    public void add(String name) throws SQLException { players.addPlayer(name); }
    public boolean deleteIfUnscored(int id) throws SQLException {
        if (scores.hasScoresByPlayer(id)) return false;
        players.deletePlayer(id);
        return true;
    }
}
