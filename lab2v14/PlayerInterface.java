/**
 * [uwara 2025-12-20] TAG game
 * PlayerInterface defines the contract for game players.
 * TagPlayer(or any player) must implement this interface to participate in the TAG game.
 */
public interface PlayerInterface extends java.io.Serializable {
    /**
     * Get the unique identifier of this player.
     */
    public String getId();

    /**
     * Attempt to tag this player. Called by the Bailiff when another player tries to tag.
     *
     * @return true if tag succeeded, false if player was already 'it' or tag failed
     */
    public boolean tag();

    /**
     * Check if this player is currently 'it'.
     *
     * @return true if player is 'it', false otherwise
     */
    public boolean getIsIt();
}
