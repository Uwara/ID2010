/**
 * Uwara on 2025-12
 * Simple wrapper to pair a message with its sender's RMI stub.
 */
public class MessageWithSender {
    public RemoteEventListener sender;
    public String senderId;
    public String text;
    
    public MessageWithSender(RemoteEventListener sender, String senderId, String text) {
        this.sender = sender;
        this.senderId = senderId;
        this.text = text;
    }
}