package com.example.service;

import com.example.model.*;
import com.example.net.*;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class GameService {

    private Game game;

    private final com.example.repository.RepositoryService<Game> gameRepository;
    private final com.example.time.GameRuntimeService gameRuntimeService;

    // --- NodeMode / Networking ---
    private volatile NodeMode nodeMode = NodeMode.HOST;
    private String hostAddress = "127.0.0.1";
    private final int INPUT_PORT = 53536;

    private transient NetInputServer netServer;  // nur auf HOST aktiv
    private transient NetInputClient netClient;  // nur auf SLAVE aktiv

    // optional: Discovery
    private transient DiscoveryResponder discoveryResponder;

    public GameService() {
        this.gameRepository = new com.example.repository.RepositoryService<>();
        this.gameRuntimeService = new com.example.time.GameRuntimeService(this);
    }

    // ----------------- Repository -----------------

    public void buildNewGame(String gameName) {
        game = GameFactoryService.newGame(gameName);
    }

    public void loadGame(String gameName) throws Exception {
        game = gameRepository.load(gameName);
    }

    public List<String> listSaves() { return gameRepository.listSaves(); }

    public List<String> listBackups(String baseName) { return gameRepository.listBackups(baseName); }

    public void saveGame() throws Exception { gameRepository.save(game); }

    public void backupGame() { gameRepository.backup(game); }

    // ----------------- GameRuntime -----------------

    public void startGame() { gameRuntimeService.start(); }

    public void pauseGame() { gameRuntimeService.pause(); }

    public void resumeGame() { gameRuntimeService.resume(); }

    public void stopGame() { gameRuntimeService.stop(); }

    // -------------- Logic thread helpers --------------

    public void runOnLogic(Runnable r) { gameRuntimeService.runOnLogic(r); }

    public <T> T callOnLogic(Callable<T> c) { return gameRuntimeService.callOnLogic(c); }

    // ----------------- Accessors -----------------

    public Game getGame() { return game; }

    public void printCurrentGame() {
        if (getGame() == null) { System.out.println("No game loaded."); return; }
        System.out.println("Current Game: " + getGame().getName());
        System.out.println("Families: " + getGame().getFamilies().get(0).getName() + ", "
                + getGame().getFamilies().get(1).getName() + ", "
                + getGame().getFamilies().get(2).getName());
        System.out.println("Categories: " + getGame().getCategories().get(0).getName() + ", "
                + getGame().getCategories().get(1).getName() + ", "
                + getGame().getCategories().get(2).getName());
    }

    // ----------------- NodeMode / Networking -----------------

    public NodeMode getNodeMode() { return nodeMode; }

    public void setNodeMode(NodeMode mode) {
        this.nodeMode = mode;
        if (mode == NodeMode.HOST) {
            ensureServerRunning();
            stopDiscovery(); // optional
            this.netClient = null;
        } else {
            stopServer();
            ensureClientReady();
            ensureDiscoveryRunning(); // optional
        }
    }

    public void setHostAddress(String host) {
        this.hostAddress = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
        if (nodeMode == NodeMode.SLAVE) {
            ensureClientReady();
        }
    }

    private void ensureServerRunning() {
        if (netServer == null) {
            netServer = new NetInputServer(INPUT_PORT, this);
            netServer.start();
        }
    }

    private void stopServer() {
        if (netServer != null) {
            try { netServer.close(); } catch (Exception ignored) {}
            netServer = null;
        }
    }

    private void ensureClientReady() {
        netClient = new NetInputClient(hostAddress, INPUT_PORT);
    }

    // --- optional: DiscoveryResponder ---
    private void ensureDiscoveryRunning() {
        if (discoveryResponder == null) {
            discoveryResponder = new DiscoveryResponder(this);
            discoveryResponder.start();
        }
    }
    public void stopDiscovery() {
        if (discoveryResponder != null) {
            try { discoveryResponder.close(); } catch (Exception ignored) {}
            discoveryResponder = null;
        }
    }

    /** Für Application.stop(): beendet Netzwerkteile robust. */
    public void shutdown() {
        stopDiscovery();
        stopServer();
        // Client ist kurzlebig (pro Send neu), daher nichts nötig.
    }

    // ----------------- NEU: Verbindungstest (nur TCP-Connect) -----------------
    /** Prüft, ob der Host-Port erreichbar ist (TCP Connect mit Timeout). */
    public boolean testConnectionToHost() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(hostAddress, INPUT_PORT), 1200);
            return true; // Connect erfolgreich
        } catch (Exception e) {
            return false; // Connect fehlgeschlagen
        }
    }

    // ----------------- Public API für Controller (Host/Slave-transparente Eingaben) -----------------

    public void requestTeamPrestigeDelta(int teamId, double delta) {
        if (nodeMode == NodeMode.SLAVE) {
            send(new InputMessage(InputType.TEAM_PRESTIGE_DELTA)
                    .put("teamId", teamId)
                    .put("delta", delta));
        } else {
            applyTeamPrestigeDelta(teamId, delta);
        }
    }

    public void requestInfluenceDelta(int teamId, String categoryName, double delta) {
        if (nodeMode == NodeMode.SLAVE) {
            send(new InputMessage(InputType.CATEGORY_INFLUENCE_DELTA)
                    .put("teamId", teamId)
                    .put("category", categoryName)
                    .put("delta", delta));
        } else {
            applyInfluenceDelta(teamId, categoryName, delta);
        }
    }

    public void requestMaterialAdd(int teamId, String buildCategoryName, String materialName, int amount) {
        if (nodeMode == NodeMode.SLAVE) {
            send(new InputMessage(InputType.MATERIAL_ADD)
                    .put("teamId", teamId)
                    .put("build", buildCategoryName)
                    .put("material", materialName)
                    .put("amount", amount));
        } else {
            applyMaterialAdd(teamId, buildCategoryName, materialName, amount);
        }
    }

    public void requestSetGameSpeed(double speed) {
        if (nodeMode == NodeMode.SLAVE) {
            send(new InputMessage(InputType.SET_SPEED).put("speed", speed));
        } else {
            runOnLogic(() -> { if (game != null) game.getGameTime().setGameSpeed(speed); });
        }
    }

    public void requestSetPrestigeMultiplier(double mult) {
        if (nodeMode == NodeMode.SLAVE) {
            send(new InputMessage(InputType.SET_PRESTIGE_MULTIPLIER).put("mult", mult));
        } else {
            runOnLogic(() -> { if (game != null) game.setPrestigeMultiplier(mult); });
        }
    }

    private void send(InputMessage msg) {
        try {
            if (netClient == null) ensureClientReady();
            netClient.send(msg);
        } catch (Exception e) {
            System.err.println("Send failed: " + e.getMessage());
        }
    }

    // ----------------- Vom Server aufgerufen: Nachricht anwenden -----------------

    public void applyInputMessage(InputMessage msg) {
        switch (msg.getType()) {
            case TEAM_PRESTIGE_DELTA -> {
                int teamId = msg.getInt("teamId", -1);
                double delta = msg.getDouble("delta", 0.0);
                applyTeamPrestigeDelta(teamId, delta);
            }
            case CATEGORY_INFLUENCE_DELTA -> {
                int teamId = msg.getInt("teamId", -1);
                String cat = msg.get("category");
                double delta = msg.getDouble("delta", 0.0);
                applyInfluenceDelta(teamId, cat, delta);
            }
            case MATERIAL_ADD -> {
                int teamId = msg.getInt("teamId", -1);
                String build = msg.get("build");
                String material = msg.get("material");
                int amount = msg.getInt("amount", 0);
                applyMaterialAdd(teamId, build, material, amount);
            }
            case SET_SPEED -> {
                double speed = msg.getDouble("speed", 1.0);
                runOnLogic(() -> { if (game != null) game.getGameTime().setGameSpeed(speed); });
            }
            case SET_PRESTIGE_MULTIPLIER -> {
                double mult = msg.getDouble("mult", 1.0);
                runOnLogic(() -> { if (game != null) game.setPrestigeMultiplier(mult); });
            }
        }
    }

    // ----------------- Reale Mutationen (Host, Logic-Thread) -----------------

    private void applyTeamPrestigeDelta(int teamId, double delta) {
        runOnLogic(() -> {
            Team t = findTeamById(teamId);
            if (t != null) t.addPrestige(delta);
        });
    }

    private void applyInfluenceDelta(int teamId, String categoryName, double delta) {
        runOnLogic(() -> {
            if (game == null || categoryName == null) return;
            CategoryInterface ci = findCategoryByName(categoryName);
            Team t = findTeamById(teamId);
            if (ci == null || t == null) return;
            Map<Integer, Double> map = ci.getInfluenceMap();
            map.putIfAbsent(t.getId(), 0.0);
            ci.addInfluence(t, delta);
        });
    }

    private void applyMaterialAdd(int teamId, String buildCategoryName, String materialName, int amount) {
        runOnLogic(() -> {
            if (game == null) return;
            BuildCategory bc = findBuildCategoryByName(buildCategoryName);
            Team t = findTeamById(teamId);
            if (bc == null || t == null) return;
            Material mat;
            try { mat = Material.valueOf(materialName); } catch (Exception e) { return; }
            int free = calcFree(bc, mat);
            if (amount <= 0 || free <= 0) return;
            final int payAmount = Math.min(amount, free);
            if (payAmount <= 0) return;
            bc.addMaterial(t, mat, payAmount);
        });
    }

    private int calcFree(BuildCategory bc, Material material) {
        Map<Material, Integer> need = bc.getNeededMaterials();
        Map<Material, Integer> pay = bc.getPayedMaterials();
        int n = need.getOrDefault(material, 0);
        int p = pay.getOrDefault(material, 0);
        return Math.max(n - p, 0);
    }

    // ----------------- Finder -----------------

    private Team findTeamById(int id) {
        if (game == null || game.getFamilies() == null) return null;
        for (Family f : game.getFamilies()) {
            if (f.getTeams() == null) continue;
            for (Team t : f.getTeams()) {
                if (t != null && t.getId() == id) return t;
            }
        }
        return null;
    }

    private CategoryInterface findCategoryByName(String name) {
        if (game == null || game.getCategories() == null || name == null) return null;
        for (CategoryInterface ci : game.getCategories()) {
            if (name.equals(ci.getName())) return ci;
        }
        return null;
    }

    private BuildCategory findBuildCategoryByName(String name) {
        if (game == null || game.getCategories() == null || name == null) return null;
        for (CategoryInterface ci : game.getCategories()) {
            if (ci instanceof BuildCategory bc && name.equals(bc.getName())) return bc;
        }
        return null;
    }
}
