# TAG Game Implementation - Lab 2 v14

## Overview

This is an implementation of the Game of TAGGING. Players migrate between Bailiffs (execution servers) and attempt to tag each other. The game requires interaction between the Bailiff (infrastructure) and TagPlayer (game logic).

---

## What Bailiff Must Do

The Bailiff is the **game infrastructure provider**. It is responsible for:

### 1. **Track Active Players**
- Maintain a thread-safe HashMap of all players currently in this Bailiff
- When a player arrives via `migrate()`, register them in the HashMap
- When a player finishes (exits `topLevel()`), unregister them
- Use `Collections.synchronizedMap()` to handle concurrent access safely

```java
protected Map<String, PlayerInterface> players = 
    Collections.synchronizedMap(new HashMap<String, PlayerInterface>());
```

### 2. **Modify `migrate()` Method**
- When an object arrives, check if it implements `PlayerInterface`
- If yes, add it to the players HashMap before starting execution
- Log the arrival for debugging

```java
public void migrate(Object obj, String cb, Object[] args) {
    // Register player if it's a PlayerInterface
    if (obj instanceof PlayerInterface) {
        PlayerInterface p = (PlayerInterface) obj;
        players.put(p.getId(), p);
        System.out.println("Player " + p.getId() + " arrived");
    }
    
    Agitator agt = new Agitator(obj, cb, args);
    agt.initialize();
    agt.start();
}
```

### 3. **Cleanup in Agitator.run()**
- Add a `finally` block to the Agitator's run method
- When the player's thread finishes, remove from HashMap
- Log the departure

```java
public void run() {
    try {
        myMethod.invoke(myObj, myArgs);
    } catch (Throwable t) {
        log.severe(t.getMessage());
    } finally {
        // Clean up when player leaves
        if (myObj instanceof PlayerInterface) {
            PlayerInterface p = (PlayerInterface) myObj;
            players.remove(p.getId());
        }
    }
}
```

### 4. **Provide `getPlayerList()` Method**
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

### 5. **Implement `tag(String playerId)` Method**
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

---

## What TagPlayer Must Do

TagPlayer is the **game logic provider**. It is responsible for:

### 1. **Maintain Game State**
- **Unique ID**: `playerId` (UUID or commandline-assigned)
- **IT Status**: `isIt` boolean flag (starts as false unless specified)
- **Game Parameters**: Move delays, random variations

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
- Query the RMI registry for services starting with "Bailiff"
- Maintain lists of good (working) and bad (failed) Bailiff names
- Retry discovery if no Bailiffs found

### 4. **Implement Hunt Behavior (if 'it')**
- Get player list from current Bailiff
- Select a victim (any player except yourself)
- Attempt to tag the victim via `bailiff.tag(victimId)`
- If successful, set `isIt = false`
- If no victims, migrate to another Bailiff with more players

**Goal**: Find and tag other players

### 5. **Implement Flee Behavior (if not 'it')**
- Query Bailiffs to locate where the 'it' player is
- If 'it' player is in your Bailiff, migrate away immediately
- Otherwise, stay put or migrate randomly to spread out
- Watch for incoming threats

**Goal**: Avoid being tagged

### 6. **Add Delays and Random Variation**
- Base delay: 3-5 seconds between actions
- Random variation: 50-250ms to avoid lock-step behavior
- 'IT' player gets shorter delay (hunting advantage)

```java
protected long getDelay() {
    long variation = (long)(Math.random() * randomVariationMs);
    if (isIt) {
        return (moveDelayMs / 2) + variation;  // Shorter for hunters
    }
    return moveDelayMs + variation;
}
```

### 7. **Main Game Loop in topLevel()**
- Infinite loop that:
  1. Scans for Bailiffs
  2. Picks a random Bailiff
  3. Migrates there via `bailiff.migrate(this, "topLevel", {})`
  4. Executes game logic (hunt or flee)
  5. Sleeps appropriately
  6. Migrates to next Bailiff
  7. Repeats

```java
public void topLevel() {
    for (;;) {
        // Discover Bailiffs
        scanForBailiffs();
        
        // Get current Bailiff
        BailiffInterface bailiff = pickBailiff();
        
        // Execute game logic
        if (isIt) {
            huntBehavior(bailiff);
        } else {
            fleeBehavior(bailiff);
        }
        
        // Sleep
        snooze(getDelay());
        
        // Migrate to next Bailiff
        BailiffInterface nextBailiff = pickBailiff();
        nextBailiff.migrate(this, "topLevel", {});
        return;  // This instance terminates
    }
}
```

### 8. **Provide main() Method**
- Parse commandline arguments: `-id`, `-isit`, `-debug`
- Create TagPlayer instance
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
Terminal 1: rmiregistry
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
│ TagPlayer A (launcher JVM)                                  │
│  - Discovers 3 Bailiffs from registry                       │
│  - Picks Bailiff1                                           │
│  - Calls: bailiff1.migrate(this, "topLevel", {})           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Bailiff1 JVM                                                │
│  - Receives TagPlayer A                                     │
│  - Registers: players.put("A", playerA)                    │
│  - Starts Agitator thread → calls playerA.topLevel()       │
│                                                              │
│  Meanwhile, TagPlayer B and C also arrive...               │
│  - players = {"A", "B", "C"}                              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ TagPlayer A running in Bailiff1                            │
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
│    → Returns (thread ends in Bailiff1)                     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Bailiff1 Cleanup                                            │
│  - Agitator.run() finally block executes                   │
│  - players.remove("A")                                      │
│  - players = {"B", "C"}                                    │
│                                                              │
│ Meanwhile in Bailiff2...                                   │
│  - A arrives and is registered                             │
│  - A continues game loop there                             │
└─────────────────────────────────────────────────────────────┘
```

### **Key Moment: Why Bailiff Mediation is Critical**

**Without Bailiff Mediation (WRONG):**
```
PlayerA (in Bailiff1)  →  Gets reference to PlayerC
                        →  Calls remote playerC.tag()
                        →  Gets copy of PlayerC (not original!)
                        →  Copy becomes IT, original stays untagged
                        →  TAG IS LOST when original dies! ❌
```

**With Bailiff Mediation (CORRECT):**
```
PlayerA (in Bailiff1)  →  Calls bailiff.tag("C")
                        →  Bailiff looks up PlayerC locally
                        →  Calls ACTUAL playerC.tag()
                        →  Original PlayerC becomes IT
                        →  TAG IS PRESERVED! ✅
```

---

## Key Implementation Details

### **PlayerInterface Requirements**
```java
public interface PlayerInterface extends Serializable {
    public String getId();
    public boolean tag();
    public boolean getIsIt();
}
```

### **BailiffInterface Extensions**
```java
public String[] getPlayerList() throws RemoteException;
public boolean tag(String playerId) throws RemoteException;
```

### **Critical: Thread Safety**
- Use `Collections.synchronizedMap()` for players HashMap
- Use `synchronized` blocks when iterating over player lists
- Use `synchronized` on TagPlayer's `tag()` method
- Handle concurrent arrivals/departures/tagging

### **Migration Pattern**
When a TagPlayer wants to move:
```java
// Get next Bailiff
BailiffInterface nextBailiff = pickBailiff();

// Migrate
nextBailiff.migrate(this, "topLevel", new Object[]{});

// This returns immediately
// The copy now runs in nextBailiff
// Original thread dies here
return;
```

---

## Testing

### **Compile**
```bash
cd lab2v14
javac *.java
```

### **Run (4 separate terminals)**

**Terminal 1 - RMI Registry:**
```bash
rmiregistry
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

---

## Design Decisions

1. **Mediated Communication**: All player-to-player communication goes through the Bailiff to prevent tag loss
2. **Local Registration**: Bailiff only knows about players currently in its JVM, not remote players
3. **Optimistic Discovery**: Players maintain lists of good/bad Bailiffs and try them repeatedly
4. **Stochastic Behavior**: Random delays and choices create interesting emergent behavior
5. **No Centralized Logic**: Game rules are enforced locally in players, not in Bailiff

---

## References

- README.txt - Original assignment specification
- PlayerInterface.java - Player contract
- BailiffInterface.java - Bailiff contract extensions
- Bailiff.java - Infrastructure implementation
- TagPlayer.java - Game agent implementation
