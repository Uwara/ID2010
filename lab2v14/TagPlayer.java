import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * TagPlayer is a game agent that participates in the TAG game.
 * Players can be in one of two states: 'it' (hunter) or 'not it' (evader).
 * The 'it' player hunts other players to tag them.
 * Non-'it' players try to evade being tagged by moving to different Bailiffs.
 */
public class TagPlayer implements PlayerInterface {
    private static final long serialVersionUID = 1L;

    // Player identification
    private String playerId;
    private boolean isIt;

    // Game parameters
    private long moveDelayMs = 5000; // 5 seconds base delay
    private long randomVariationMs = 150; // 50-250ms variation
    private boolean debug = false;
    private int jumpCount = 0;

    // RMI registry interaction (transient so not serialized)
    private transient ArrayList<String> goodBailiffs = new ArrayList<>();
    private transient ArrayList<String> badBailiffs = new ArrayList<>();
    private transient long retrySleep = 20000; // 20 seconds between retries

    public TagPlayer() {
        this.playerId = UUID.randomUUID().toString().substring(0, 8);
        this.isIt = false;
    }

    public TagPlayer(String id) {
        this.playerId = (id != null) ? id : UUID.randomUUID().toString().substring(0, 8);
        this.isIt = false;
    }

    // ================ PlayerInterface Implementation ================

    @Override
    public String getId() {
        return playerId;
    }

    @Override
    public synchronized boolean tag() {
        if (!isIt) {
            isIt = true;
            System.out.println("\n========================================");
            System.out.println("Player " + playerId + " is now IT player.");
            System.out.println("========================================\n");
            debugMsg("Got tagged! Now I'm IT");
            return true;
        }
        debugMsg("Already IT, cannot be tagged");
        return false;
    }

    @Override
    public boolean getIsIt() {
        return isIt;
    }

    // ================ Configuration Methods ================

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setMoveDelayMs(long ms) {
        this.moveDelayMs = Math.max(100, ms);
    }

    public void setRandomVariationMs(long ms) {
        this.randomVariationMs = Math.max(0, ms);
    }

    // ================ Debug and Utility Methods ================

    protected void debugMsg(String msg) {
        if (debug) {
            System.out.printf("%s(%d): %s%n", playerId, jumpCount, msg);
        }
    }

    protected void snooze(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms); // Standard modern java practice
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    protected long getDelay() {
        long variation = (long) (Math.random() * randomVariationMs);
        if (isIt) {
            // Give 'it' player shorter delay for hunting advantage
            return (moveDelayMs / 2) + variation;
        }
        return moveDelayMs + variation;
    }

    // ================ Bailiff Discovery ================

    protected void scanForBailiffs() {
        try {
            Registry registry = LocateRegistry.getRegistry(null);
            String[] serviceNames = registry.list();

            for (String name : serviceNames) {
                if (name.startsWith("Bailiff")) {
                    if (!badBailiffs.contains(name) && !goodBailiffs.contains(name)) {
                        goodBailiffs.add(name);
                        debugMsg("Found Bailiff: " + name);
                    }
                }
            }
        } catch (Exception e) {
            debugMsg("Scanning failed: " + e.getMessage());
        }
    }

    protected BailiffInterface pickBailiff() {
        if (goodBailiffs.isEmpty()) {
            return null;
        }

        String name = goodBailiffs.get((int) (Math.random() * goodBailiffs.size()));

        try {
            Registry registry = LocateRegistry.getRegistry(null);
            Remote service = registry.lookup(name);

            if (service instanceof BailiffInterface) {
                return (BailiffInterface) service;
            } else {
                goodBailiffs.remove(name);
                badBailiffs.add(name);
                return null;
            }
        } catch (Exception e) {
            goodBailiffs.remove(name);
            badBailiffs.add(name);
            return null;
        }
    }

    // ================ Game Logic ================

    protected void fleeBehavior(BailiffInterface currentBailiff) {
        try {
            String itPlayerId = currentBailiff.getItPlayerId();

            if (itPlayerId != null && !itPlayerId.equals(playerId)) {
                // The 'it' player is HERE! FLEE!
                debugMsg("IT player " + itPlayerId + " detected - ESCAPING!");
                System.out.println("\nPlayer " + itPlayerId + " is IT player. Player " + playerId + " is fleeing.\n");

                BailiffInterface safeBailiff = pickBailiff();
                if (safeBailiff != null) {
                    safeBailiff.migrate(this, "topLevel", new Object[]{});
                }
                return;
            }

            // If not threatened, occasionally move
            if (Math.random() > 0.7) {
                debugMsg("Moving to another Bailiff occasionally.");
                BailiffInterface nextBailiff = pickBailiff();
                if (nextBailiff != null) {
                    nextBailiff.migrate(this, "topLevel", new Object[]{});
                }
            }
        } catch (RemoteException | NoSuchMethodException e) {
            debugMsg("Error during fleeing: " + e.getMessage());
        }
    }

    protected void huntBehavior(BailiffInterface currentBailiff) {
        try {
            String[] players = currentBailiff.getPlayerList();

            if (players.length > 1) {
                for (String victim : players) {
                    if (!victim.equals(playerId)) {
                        debugMsg("Trying to tag: " + victim);
                        boolean success = currentBailiff.tag(victim);
                        if (success) {
                            isIt = false;
                            System.out.println("\n========================================");
                            System.out.println("*** PLAYER " + playerId + " TAGGED " + victim + "! ***");
                            System.out.println("*** " + playerId + " IS NO LONGER 'IT'! ***");
                            System.out.println("========================================\n");
                            return;
                        }
                    }
                }
            } else {
                debugMsg("No players here,  another Bailiff");
            }
        } catch (RemoteException e) {
            debugMsg("Error during hunting: " + e.getMessage());
        }
    }

    // ================ Main Game Loop ================

    public void topLevel() throws RemoteException, NoSuchMethodException {
        jumpCount++;

        // [uwara] Initialize Bailiff lists if null
        if (goodBailiffs == null) {
            goodBailiffs = new ArrayList<>();
        }
        if (badBailiffs == null) {
            badBailiffs = new ArrayList<>();
        }

        debugMsg("Starting jump #" + jumpCount);

        for (; ; ) {
            // Discover Bailiffs
            debugMsg("Scanning for Bailiffs...");
            scanForBailiffs();

            // Keep trying until we have good Bailiffs
            while (goodBailiffs.isEmpty()) {
                scanForBailiffs();
                if (goodBailiffs.isEmpty()) {
                    debugMsg("No Bailiffs found, waiting...");
                    snooze(retrySleep);
                }
            }

            debugMsg("Found " + goodBailiffs.size() + " Bailiffs");

            // Try to get a Bailiff and migrate
            BailiffInterface bailiff = pickBailiff();

            if (bailiff != null) {
                try {
                    debugMsg("Attempting to migrate");

                    // Sleep before migrating
                    snooze(getDelay());

                    // Play the game
                    debugMsg("In Bailiff, state: IT=" + isIt);

                    if (isIt) {
                        huntBehavior(bailiff);
                    } else {
                        fleeBehavior(bailiff);
                    }

                    // After game logic, migrate to next Bailiff
                    BailiffInterface nextBailiff = pickBailiff();
                    if (nextBailiff != null) {
                        debugMsg("Migrating to next Bailiff");
                        nextBailiff.migrate(this, "topLevel", new Object[]{});
                        return; // This instance will end after migration
                    }

                } catch (RemoteException | NoSuchMethodException e) {
                    debugMsg("Migration failed: " + e.getMessage());
                    goodBailiffs.clear();
                    badBailiffs.clear();
                }
            }
        }
    }

    // ================ Main Method ================

    public static void main(String[] argv) throws java.io.IOException, NoSuchMethodException {
        String playerId = null;
        boolean isIt = false;
        boolean debug = false;

        int state = 0;

        for (String av : argv) {
            switch (state) {
                case 0:
                    if (av.equals("?") || av.equals("-h") || av.equals("-help")) {
                        showUsage();
                        return;
                    } else if (av.equals("-id")) {
                        state = 1;
                    } else if (av.equals("-isit")) {
                        state = 2;
                    } else if (av.equals("-debug")) {
                        debug = true;
                    } else {
                        System.err.println("Unknown argument: " + av);
                        return;
                    }
                    break;

                case 1:
                    playerId = av;
                    state = 0;
                    break;

                case 2:
                    isIt = av.equalsIgnoreCase("true");
                    state = 0;
                    break;
            }
        }

        final TagPlayer player = new TagPlayer(playerId);
        player.setDebug(debug);
        player.isIt = isIt;

        System.out.println("TAG PLAYER STARTED: id=" + player.playerId + " isIt=" + isIt);

        player.topLevel();
    }

    private static void showUsage() {
        String[] msg = {
            "Usage: {?,-h,-help}|[-id string][-isit true|false][-debug]",
            "? -h -help   Show this help message",
            "-id   string Set the player ID (default: auto-generated UUID)",
            "-isit bool   Start as IT player (true/false, default: false)",
            "-debug       Enable debug output"
        };
        for (String s : msg) {
            System.out.println(s);
        }
    }

    @Override
    public String toString() {
        return "TagPlayer [playerId=" + playerId + ", isIt=" + isIt + ", moveDelayMs=" + moveDelayMs
            + ", randomVariationMs=" + randomVariationMs + ", debug=" + debug + ", jumpCount=" + jumpCount + "]";
    }
}