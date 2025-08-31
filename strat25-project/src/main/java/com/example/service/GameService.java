package com.example.service;

import java.util.List;

import com.example.model.Game;
import com.example.repository.RepositoryService;
import com.example.time.GameRuntimeService;

public class GameService {

    private Game game;

    private RepositoryService<Game> gameRepository;
    private GameRuntimeService gameRuntimeService;

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
