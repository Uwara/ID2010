/**
 * Simple wrapper to pair a message with its sender's RMI stub.
 */
public class MessageWithSender {
    public RemoteEventListener sender;
    public String text;

    public MessageWithSender(RemoteEventListener sender, String text) {
        this.sender = sender;
        this.text = text;
    }
}