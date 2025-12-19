/**
 * uwara - added on 12/2025
 * PlayerInterface defines the contract for game players.
 * All players must implement this interface to participate in the TAG game.
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
