# TAG Game Implementation - Lab 2 v14.1

## Overview

This is an implementation of the Game of TAGGING. Players migrate between Bailiffs (execution servers) and attempt to tag each other. The game requires interaction between the Bailiff(s) and TagPlayer (game logic).

---

## What Bailiff Must Do

The Bailiff is the **game engine provider**. It is responsible for:

### 1. **Track Active Players**
- Maintain a thread-safe HashMap of all players currently in this Bailiff
- When a player arrives via `migrate()`, register them in the HashMap
- When a player finishes (exits `topLevel()`), unregister them
- Use `Collections.synchronizedMap()` to handle concurrent access safely

```java
protected Map<String, PlayerInterface> players = 
    Collections.synchronizedMap(new HashMap<>());
```

### 2. **Thread Pool**
- **Use ExecutorService instead of creating unbounded threads**
- Initialize a fixed thread pool in the constructor to prevent thread exhaustion
- Without this, the system will crash with `OutOfMemoryError: unable to create native thread`
- Default pool size: 20 threads as of now

```java
private final ExecutorService executorService;
private static final int THREAD_POOL_SIZE = 20;

// In constructor - initialize with named threads
this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE, namedThreadFactory);
```

### 3. **Modify `migrate()` Method**
- When an object arrives, check if it implements `PlayerInterface`
- If yes, add it to the players HashMap before starting execution
- **Use executor service instead of creating new Thread** 

```java
public void migrate(Object obj, String cb, Object[] args) {
    if (obj instanceof PlayerInterface player) {
        players.put(player.getId(), player);
        log.info("Player " + player.getId() + " arrived");
    }
    
    Agitator agitator = new Agitator(obj, cb, args);
    agitator.initialize();
    executorService.execute(agitator);  // Use pool, NOT agitator.start()
}
```

### 4. **Convert Agitator to Runnable**
- Change from extending `Thread` to implementing `Runnable`
- Set context classloader in `run()` method for pooled threads
- Add `finally` block to clean up when player leaves

```java
private class Agitator implements Runnable {
    public void run() {
        try {
            Thread.currentThread().setContextClassLoader(myObj.getClass().getClassLoader());
            myMethod.invoke(myObj, myArgs);
        } catch (Throwable t) {
            log.severe(t.getMessage());
        } finally {
            if (myObj instanceof PlayerInterface player) {
                players.remove(player.getId());
                log.fine("Player " + player.getId() + " departed");
            }
        }
    }
}
```

### 5. **Provide `getPlayerList()` Method**
- Returns an array of all player IDs currently in this Bailiff
- Used by players to find potential victims or see who's around
- Must be thread-safe

```java
public String[] getPlayerList() throws RemoteException {
    synchronized(players) {
        return players.keySet().toArray(new String[0]);
    }
}
```

### 6. **Provide `getItPlayerId()` Method**
- Returns the ID of the player who is currently "it"
- Returns null if no one is "it" in this Bailiff
- Used by fleeing players to detect danger

```java
public String getItPlayerId() throws RemoteException {
    synchronized(players) {
        for (PlayerInterface p : players.values()) {
            if (p.getIsIt()) return p.getId();
        }
    }
    return null;
}
```

### 7. **Implement `tag(String playerId)` Method**
- **Critical for preventing tag loss!**
- Acts as a mediator between two players
- Looks up the player by ID in the local HashMap
- Calls the player's `tag()` method directly (not remotely!)
- Returns true if tag succeeded, false otherwise
- **Why mediation is essential**: When players are in the same Bailiff, the Bailiff calls the tag method on the actual object, not a serialized copy. This prevents tag loss due to migration timing issues.

```java
public boolean tag(String playerId) throws RemoteException {
    PlayerInterface p = players.get(playerId);
    if (p != null) {
        return p.tag();  // Local method call on actual object
    }
    return false;
}
```

### 8. **Graceful Shutdown**
- Add shutdown method to properly terminate the executor
- Add shutdown hook in main() for cleanup on exit
- Ensures running player threads complete before termination

```java
public void shutdown() {
    unbind();
    executorService.shutdown();
    executorService.awaitTermination(60, TimeUnit.SECONDS);
}

// In main()
Runtime.getRuntime().addShutdownHook(new Thread(bailiff::shutdown));
```

---

## What TagPlayer Must Do

TagPlayer is the **game logic provider**. It is responsible for:

### 1. **Maintain Game State**
- **Unique ID**: `playerId` (UUID or commandline-assigned)
- **IT Status**: `isIt` boolean flag (starts as false unless specified)
- **Game Parameters**: Move delays, random variations
- **Bailiff Lists**: Track working and failed Bailiffs

```java
private String playerId;
private boolean isIt = false;
private long moveDelayMs = 4000;
private long randomVariationMs = 200;
private List<String> goodBailiffs;
private List<String> badBailiffs;
```

### 2. **Implement PlayerInterface**
- `getId()`: Return player's unique identifier
- `tag()`: Handle being tagged (synchronized to prevent race conditions)
  - If not 'it', become 'it' and return true
  - If already 'it', return false (cannot tag yourself)
- `getIsIt()`: Return current IT status

```java
@Override
public synchronized boolean tag() {
    if (!isIt) {
        isIt = true;
        System.out.println("Got tagged! Now I'm IT");
        return true;
    }
    return false;  // Already IT
}
```

### 3. **Discover Bailiffs**
- `scanForBailiffs()`: Query the RMI registry for services starting with "Bailiff"
- Test each discovered Bailiff to see if it's reachable
- Maintain lists of good (working) and bad (failed) Bailiff names
- Retry discovery if no Bailiffs found

### 4. **Select Bailiffs**
- `pickBailiff()`: Randomly select from the list of working Bailiffs
- If no good Bailiffs available, scan again
- Handle failures by moving Bailiffs from good to bad list

### 5. **Implement Hunt Behavior (if 'it')**
- `huntBehavior(bailiff)`: Execute when you are the hunter
- Get player list from current Bailiff via `bailiff.getPlayerList()`
- Select a victim (any player except yourself)
- Attempt to tag the victim via `bailiff.tag(victimId)`
- If successful, set `isIt = false` (you're no longer it)
- If no victims, consider migrating to another Bailiff

**Goal**: Find and tag other players

### 6. **Implement Flee Behavior (if not 'it')**
- `fleeBehavior(bailiff)`: Execute when you are not the hunter
- Query current Bailiff via `bailiff.getItPlayerId()` to locate the 'it' player
- If 'it' player is in your Bailiff, prepare to migrate away immediately
- Otherwise, stay put or migrate randomly to spread out
- Watch for incoming threats

**Goal**: Avoid being tagged

### 7. **Add Delays and Random Variation**
- `getDelay()`: Calculate sleep time with random jitter
- Base delay: 3-5 seconds between actions
- Random variation: 50-250ms to avoid lock-step behavior
- 'IT' player gets shorter delay (hunting advantage)
- `snooze(ms)`: Sleep for specified duration

```java
protected long getDelay() {
    long variation = (long)(Math.random() * randomVariationMs);
    if (isIt) {
        return (moveDelayMs / 2) + variation;  // Shorter for hunters
    }
    return moveDelayMs + variation;
}
```

### 8. **Main Game Loop in topLevel()**
- Infinite loop that executes the game logic
- Flow: discover → execute behavior → sleep → migrate → repeat

```java
public void topLevel() {
    for (;;) {
        scanForBailiffs();              // 1. Find available Bailiffs
        BailiffInterface bailiff = pickBailiff();  // 2. Pick one
        
        if (isIt) {
            huntBehavior(bailiff);      // 3a. Hunt if you're it
        } else {
            fleeBehavior(bailiff);      // 3b. Flee if you're not
        }
        
        snooze(getDelay());             // 4. Sleep with variation
        
        BailiffInterface nextBailiff = pickBailiff();  // 5. Pick next destination
        nextBailiff.migrate(this, "topLevel", new Object[]{});  // 6. Migrate
        return;  // This instance terminates
    }
}
```

### 9. **Provide main() Method**
- Parse commandline arguments: `-id`, `-isit`, `-debug`
- Create TagPlayer instance
- Set initial state
- Call `topLevel()` to start the game

```java
public static void main(String[] argv) {
    String playerId = null;
    boolean isIt = false;
    
    // Parse arguments...
    
    TagPlayer player = new TagPlayer(playerId);
    player.isIt = isIt;
    player.topLevel();
}
```

---

## Architecture Flow

### **Setup**
```
Terminal 1: rmiregistry (with thread limits)
Terminal 2: java Bailiff -id b1
Terminal 3: java Bailiff -id b2
Terminal 4: java Bailiff -id b3
Terminal 5: java TagPlayer -id player1 -isit true
Terminal 6: java TagPlayer -id player2
Terminal 7: java TagPlayer -id player3
```

### **Game Execution Flow**

```
┌─────────────────────────────────────────────────────────────┐
│ TagPlayer A (launcher)                                      │
│  - Discovers 3 Bailiffs from registry                       │
│  - Picks Bailiff1                                           │
│  - Calls: bailiff1.migrate(this, "topLevel", {})           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Bailiff1                                                    │
│  - Receives TagPlayer A                                     │
│  - Registers: players.put("A", playerA)                    │
│  - Submits to thread pool → executor.execute(agitator)     │
│  - Pool thread calls playerA.topLevel()                    │
│                                                              │
│  Meanwhile, TagPlayer B and C also arrive...               │
│  - players = {"A", "B", "C"}                              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ TagPlayer A running in Bailiff1 (on pooled thread)        │
│  - isIt = true (hunter)                                     │
│  - Calls: bailiff1.getPlayerList()                         │
│    → Returns ["A", "B", "C"]                               │
│                                                              │
│  - Picks victim: "C"                                        │
│  - Calls: bailiff1.tag("C")                                │
│    → Bailiff looks up: players.get("C") = playerC          │
│    → Calls: playerC.tag()  [LOCAL CALL!]                  │
│    → playerC becomes isIt = true                           │
│    → Returns: true                                          │
│                                                              │
│  - A Updates: isIt = false (no longer hunter)              │
│  - Sleeps 3-5 seconds                                       │
│  - Picks next Bailiff                                       │
│  - Calls: bailiff2.migrate(this, "topLevel", {})           │
│    → Returns (thread goes back to pool)                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Bailiff1 Cleanup                                            │
│  - Agitator.run() finally block executes                   │
│  - players.remove("A")                                      │
│  - Thread returns to pool for reuse                        │
│  - players = {"B", "C"}                                    │
│                                                              │
│ Meanwhile in Bailiff2...                                   │
│  - A arrives and is registered                             │
│  - A continues game loop there                             │
└─────────────────────────────────────────────────────────────┘
```

### ** Bailiff as Game Engine**

**With Bailiff:**
```
PlayerA (in Bailiff1)   →  Calls bailiff.tag("C")
                        →  Bailiff looks up PlayerC locally
                        →  Calls ACTUAL playerC.tag()
                        →  Original PlayerC becomes IT
                        →  TAG IS PRESERVED! ✅
```

---

## Key Implementation Details

### **PlayerInterface (New Code)**
```java
public interface PlayerInterface extends Serializable {
    public String getId();
    public boolean tag();
    public boolean getIsIt();
}
```

### **BailiffInterface New methods added**
```java
public String[] getPlayerList() throws RemoteException;
public String getItPlayerId() throws RemoteException;
public boolean tag(String playerId) throws RemoteException;
```

### **Thread Safety**
- Use `Collections.synchronizedMap()` for players HashMap
- Use `synchronized` blocks when iterating over player lists
- Use `synchronized` on TagPlayer's `tag()` method
- Handle concurrent arrivals/departures/tagging

### **Migration Pattern**
When a TagPlayer wants to move:
```java
BailiffInterface nextBailiff = pickBailiff();
nextBailiff.migrate(this, "topLevel", new Object[]{});
return;  // Copy runs in nextBailiff, original thread returns to pool
```

---

## Testing

### **Compile**
```bash
cd lab2v14
javac *.java
```

### **Run (separate terminals)**

**Terminal 1 - RMI Registry with Thread Limits:**
```bash
java -Dsun.rmi.transport.tcp.maxConnectionThreads=50 \
     sun.rmi.registry.RegistryImpl 1099 &
```

**Terminals 2-4 - Start 3 Bailiffs:**
```bash
java Bailiff -id b1 -log 3
java Bailiff -id b2 -log 3
java Bailiff -id b3 -log 3
```

**Terminals 5-7 - Start 3 Players:**
```bash
java TagPlayer -id player1 -isit true -debug
java TagPlayer -id player2 -debug
java TagPlayer -id player3 -debug
```

### **Expected Output**
You should see:
- Players arriving at Bailiffs
- Players tagging each other
- Roles switching between 'it' and 'not it'
- Players migrating between Bailiffs
- Game continuing indefinitely
- Thread count staying bounded (not growing to thousands)

---

## Performance Tuning

### **Thread Pool Size**
Adjust `THREAD_POOL_SIZE` in Bailiff.java based on expected load:
- **Light load (1-10 players)**: 10-20 threads

For highly dynamic loads, use `Executors.newCachedThreadPool()` instead.

### **RMI Registry Limits**
Start registry with thread limits to prevent exhaustion:
```bash
-Dsun.rmi.transport.tcp.maxConnectionThreads=50
-Dsun.rmi.transport.tcp.handshakeTimeout=10000
-Dsun.rmi.transport.connectionTimeout=10000
```

---

## Troubleshooting

**OutOfMemoryError: unable to create native thread**
- ✅ Use ExecutorService in Bailiff
- ✅ Limit RMI registry threads
- ✅ Check OS limits: `ulimit -u`

**Tags not working**
- Always use `bailiff.tag(victimId)`, not direct player stub calls
- Ensures you tag the actual object, not a serialized copy

**Players not migrating**
- Enable debug: `java Bailiff -id b1 -log 7`
- Check Bailiff discovery with `-debug` flag

---

## Design

1. **Thread**: Fixed pool prevents unbounded thread
2. **Communication**: All player-to-player communication goes through the Bailiff to prevent tag loss
3. **Local**: Bailiff only knows about players currently in its process, not remote players
4. **Discovery**: Players maintain lists of good/bad Bailiffs and try them repeatedly
5. **Behavior**: Random delays and choices create interesting game behavior
6. **No Centralized Logic**: Game rules are enforced locally in players, not in Bailiff

---

## Version History

- **v14.1 (2025-12-21)**: UWARA, game logic and  introduction of ExecutorService
- **v14.0 (2024-01-25)**: Refactored for rmiregistry (no Jini)
- **v13.0 (2018-08-16)**: Original version

---

## References

- PlayerInterface.java - Player contract
- BailiffInterface.java - Bailiff contract extensions
- Bailiff.java - Infrastructure implementation with ExecutorService
- TagPlayer.java - Game agent implementation