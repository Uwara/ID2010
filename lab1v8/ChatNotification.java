public class ChatNotification extends RemoteEvent {

    protected String text;
    protected RemoteEventListener senderStub;

    // Updated uwara 2024-06 to include sender information
    public ChatNotification(Object source, RemoteEventListener sender, String msg, int serial) {
        // Call the constructor of the superclass (RemoteEvent) explicitly
        // so that its fields can be initialized to what we want. Actually,
        // we are only putting the serial number in as the sequence nr, but
        // the other arguments could be there as well if we had use for them.
        super(source,        // Source
            0,            // ID
            serial,        // sequence number
            null);        // handback
        this.senderStub = sender;
        this.text = msg;
    }

    public String getText() {
        return text;
    }

    // Added uwara 2024-06 to identify sender
    public RemoteEventListener getSender() {
        return senderStub;
    }
}
