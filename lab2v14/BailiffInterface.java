// BailiffInterface.java
// 2024-01-25/fki Reviewed for v14
// 2018-08-16/fki Refactored for v13.

import java.rmi.RemoteException;

/**
 * This interface is for the Bailiff's clients. The clients are mobile
 * code which move into the Bailiff's JVM for execution.
 */
public interface BailiffInterface extends java.rmi.Remote {


    /**
     * The entry point for mobile code.
     * The client sends and object (itself perhaps), a string
     * naming the callback method and an array of arguments which must
     * map against the parameters of the callback method.
     */
    public void migrate(Object obj, String cb, Object[] args) throws java.rmi.RemoteException, java.lang.NoSuchMethodException;

    /**
     * Get a list of all player IDs currently in this Bailiff
     * [uwara 2025-12-20] TAG game
     */
    public String[] getPlayerList() throws java.rmi.RemoteException;

    /**
     * Get the ID of the player who is "it" on this Bailiff.
     * Returns null if no player is "it" here.
     * [uwara 2025-12-20]
     */
    public String getItPlayerId() throws RemoteException;

    /**
     * [uwara 2025-12-20] TAG game
     * Attempt to tag a player by their ID. Uses the Bailiff as a mediator
     * to ensure the tag happens on the actual player object.
     */
    public boolean tag(String playerId) throws java.rmi.RemoteException;

}
