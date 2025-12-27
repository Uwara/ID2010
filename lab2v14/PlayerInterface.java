/**
 * [uwara 2025-12-20] TAG game
 * TagPlayer implements this interface to participate in the TAG game.
 */
public interface PlayerInterface extends java.io.Serializable {
    public String getId();

    public boolean tag();

    public boolean getIsIt();
}
