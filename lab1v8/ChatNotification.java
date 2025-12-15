public class ChatNotification extends RemoteEvent {

    protected String text;
    protected RemoteEventListener senderStub;
    protected String senderSessionId; // Added uwara 2025-12

    // Updated uwara 2024-06 to include sender information
    public ChatNotification(Object source, RemoteEventListener sender, String senderSessionId, String msg, int serial) {
        // Call the constructor of the superclass (RemoteEvent) explicitly
        // so that its fields can be initialized to what we want. Actually,
        // we are only putting the serial number in as the sequence nr, but
        // the other arguments could be there as well if we had use for them.
        super(source, 0, serial, null);
        this.senderStub = sender;
        this.senderSessionId = senderSessionId;
        this.text = msg;
    }

    public String getText() {
        return text;
    }

    public RemoteEventListener getSenderStub() {
        return senderStub;
    }

    // Added uwara 2025-12 to identify sender ID
    public String getSenderSessionId() {
        return senderSessionId;
    }
}
