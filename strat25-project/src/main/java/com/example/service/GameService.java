package com.example.service;

import com.example.model.Game;
import com.example.repository.RepositoryService;
import com.example.time.GameRuntimeService;

import java.util.List;
import java.util.concurrent.Callable;

public class GameService {

    private Game game;

    private final RepositoryService<Game> gameRepository;
    private final GameRuntimeService gameRuntimeService;

    public GameService() {
        this.gameRepository = new RepositoryService<>();
        this.gameRuntimeService = new GameRuntimeService(this);
    }

    // ----------------- Repository -----------------

    public void buildNewGame(String gameName) {
        game = GameFactoryService.newGame(gameName);
    }

    public void loadGame(String gameName) throws Exception {
        game = gameRepository.load(gameName);
    }

    public List<String> listSaves() {
        return gameRepository.listSaves();
    }

    public List<String> listBackups(String baseName) {
        return gameRepository.listBackups(baseName);
    }

    public void saveGame() throws Exception {
        gameRepository.save(game);
    }

    public void backupGame() {
        gameRepository.backup(game);
    }

    // ----------------- GameRuntime -----------------

    public void startGame() {
        gameRuntimeService.start();
    }

    public void pauseGame() {
        gameRuntimeService.pause();
    }

    public void resumeGame() {
        gameRuntimeService.resume();
    }

    public void stopGame() {
        gameRuntimeService.stop();
    }

    // -------------- Logic thread helpers (für Controller & andere Services) --

    /** Post a mutation to the single logic thread (fire-and-forget). */
    public void runOnLogic(Runnable r) {
        gameRuntimeService.runOnLogic(r);
    }

    /** Compute a value on the logic thread (blocking). Use sparingly in UI. */
    public <T> T callOnLogic(Callable<T> c) {
        return gameRuntimeService.callOnLogic(c);
    }

    // ----------------- Accessors -----------------

    public Game getGame() {
        return game;
    }

    public void printCurrentGame() {
        System.out.println("Current Game: " + getGame().getName());
        System.out.println("Families: " + getGame().getFamilies().get(0).getName() + ", "
                + getGame().getFamilies().get(1).getName() + ", "
                + getGame().getFamilies().get(2).getName());
        System.out.println("Categories: " + getGame().getCategories().get(0).getName() + ", "
                + getGame().getCategories().get(1).getName() + ", "
                + getGame().getCategories().get(2).getName());
    }
}
