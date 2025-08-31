package com.example.repository;

import org.junit.jupiter.api.Test;

import com.example.service.GameService;

public class JsonDirectoryRepositoryTest {
    

    @Test
    void createAndSaveGame() throws Exception{
        GameService gameService = new GameService();

        gameService.buildNewGame("Test Game");
        gameService.saveGame();

        gameService.loadGame("Test Game");

        gameService.printCurrentGame();


    }

}
