import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;


/**
 * This class implements the ChatClient application.
 */
public class ChatClient extends UnicastRemoteObject        // Since we accept remote calls
    implements RemoteEventListener        // So we can receive chat notifications
{
    /**
     * Information string for the user. Printed by the help command.
     */
    protected static final String versionString = "fki-8.0";

    /**
     * Holds the names of found ChatServers.
     */
    protected ArrayList<String> servers = new ArrayList<>();

    /**
     * Refers to the service object of the currently connected chat-service.
     */
    protected ChatServerInterface myServer = null;

    /**
     * The name the user has choosen to present itself as.
     */
    protected String myName = null;

    protected String clientSessionId;


    /**
     * This array holds the strings of the user command help text.
     */
    protected String[] cmdHelp = {"Commands (can be abbreviated):", ".list              List the currently known chat servers", ".name <name>       Set the username presented by the chat client", ".c                 Connect to the default server", ".connect <string>  Connect to a server with a matching string", ".disconnect        Break the connection to the server", ".quit              Exit the client", ".help              This text"};

    /* ***** Interface RemoteEventListener ***** */

    /**
     * Creates a new ChatClient instance.
     */
    public ChatClient() throws RemoteException {
        // Generate stable unique ID for this client session
        this.clientSessionId = "SESSION_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000000);
        System.out.println("[Client Session ID: " + clientSessionId + "]");

        scanForChatServers();
    }

    /* *** ChatClient *** */

    /**
     * The ChatServer we are registered with (connected to) calls this
     * method to notify us of a new chat message.
     *
     * @param rev The remote event that is the notification.
     */
    public void notify(RemoteEvent rev) throws RemoteException {
        if (rev instanceof ChatNotification) {
            ChatNotification chat = (ChatNotification) rev;

            // Echo cancellation: compare session IDs
            String senderSessionId = chat.getSenderSessionId();

            if (senderSessionId != null && senderSessionId.equals(this.clientSessionId)) {
                // This is MY message - suppress it
                return;
            }

            System.out.println(chat.getSequenceNumber() + " : " + chat.getText());
        }
    }

    @Override
    public String getClientSessionId() throws RemoteException {
        return this.clientSessionId;
    }

    /**
     * Scan the rmiregistry for services that name themselves ChatServer.
     * Save those names in the servers list.
     */
    protected void scanForChatServers() {
        try {

            // Get the default registry

            Registry registry = LocateRegistry.getRegistry(null);

            // Ask for all registered services

            String[] serviceNames = registry.list();

            // Save the names that starts with "ChatServer"

            servers.clear();

            for (String name : serviceNames) {
                if (name.startsWith("ChatServer")) servers.add(name);
            }

        } catch (Exception e) {
            System.out.printf("[Scanning for servers failed: %s]\n", e.toString());
            //e.printStackTrace();
        }
    }

    /**
     * Disconnects this chat client from the current chat server, if
     * any, by unregistering from the chat server.
     *
     * @param server The chat server to disconnect from.
     */
    protected void disconnect(ChatServerInterface server) {
        if (server != null) {
            try {
                String serverName = server.getName();
                server.unregister(this);
                System.out.println("[Disconnected from " + serverName + "]");
            } catch (RemoteException rex) {
            }
        }
    }

    /**
     * This method implements the '.disconnect' user command.
     */
    protected void userDisconnect() {
        if (myServer != null) {
            disconnect(myServer);
            myServer = null;
        } else {
            System.out.println("[Client is not currently connected]");
        }
    }

    /**
     * This method implements the '.connect' user command. If a
     * servername pattern is supplied, the known chat services are
     * scanned for names in which the pattern is a substring. If a null
     * or empty pattern is supplied, the connection attempt is directed
     * at the first known server (regardless of whether it is answering
     * or not). Any current service is disconnected.
     *
     * @param serviceName The substring to match against the server name.
     */
    protected void connectToChat(String serviceName) {
        if (servers.isEmpty()) scanForChatServers();

        if (servers.isEmpty()) {
            System.out.println("[There are no known servers]");
            return;
        }

        // Count nof matching service names

        int nofMatches = 0;
        String selectedServiceName = null;

        if (serviceName == null || serviceName.isEmpty()) {
            nofMatches = 1;
            selectedServiceName = servers.get(0);
        } else for (String name : servers)
            if (name.contains(serviceName)) {
                nofMatches++;
                selectedServiceName = name;
            }

        if (nofMatches == 0) {
            System.out.printf("[No servers found matching '%s']\n", serviceName);
            return;
        } else if (1 < nofMatches) {
            System.out.printf("['%s' matches more than one server]\n", serviceName);
            return;
        }

        // One name matched. If we are already connected somewhere,
        // disconnect that first.

        if (myServer != null) {
            disconnect(myServer);
            myServer = null;
        }

        try {
            // Get the default registry

            Registry registry = LocateRegistry.getRegistry(null);

            // Retrieve the service stub from the registry

            Remote service = registry.lookup(selectedServiceName);

            // Verify that it is indeed what we expect

            if (service instanceof ChatServerInterface) {
                myServer = (ChatServerInterface) service;
                myServer.register(this);
                System.out.printf("[Connected to %s]\n", selectedServiceName);
            }
        } catch (Exception e) {
            System.out.printf("[Unable to connect: %s]\n", e.toString());
            //e.printStackTrace();
        }

    } // method connectToChat

    /**
     * This method implements the '.name' user command. It sets the name
     * the user has choosen for herself on the chat. If the name is null
     * or the empty string, the &quot;user.name&quot; system property is
     * used as a substitute.
     *
     * @param newName The user's name.
     */
    /**
     * Sets the username for this chat client.
     * If a non-empty name is provided, uses it (after trimming whitespace).
     * If name is null or empty, generates a random 6-digit identifier.
     * This ensures every client has a unique name for echo cancellation.
     *
     * @param newName The desired username, or null to generate a random 6-digit ID
     */
    protected void setName(String newName) {
        // If a name is explicitly provided and not empty, use it
        if (newName != null && !newName.trim().isEmpty()) {
            myName = newName.trim();
        } else {
            // Generate a random 6-digit username as fallback
            int randomId = (int) (Math.random() * 1000000);
            myName = String.format("%06d", randomId);
        }
    }

    /**
     * Sends text to the currently connected chat server.
     * Prepends the username to the message.
     *
     * @param text The text to send.
     */
    protected void sendToChat(String text) {
        if (myServer != null) {
            try {
                myServer.say(this, myName + ": " + text);
            } catch (RemoteException rex) {
                System.out.println("[Sending to server failed]");
            }
        } else {
            System.out.println("[Cannot send chat text: not connected to a server]");
        }
    }

    /**
     * This method implements the '.list' user command.  All known chat
     * servers are listed and a call attempt is made with each.
     */
    protected void listServers() {
        scanForChatServers();

        if (servers.isEmpty()) {
            System.out.println("[There are no known servers at this time]");
            return;
        }

        try {
            Registry registry = LocateRegistry.getRegistry(null);

            for (String name : servers) {
                System.out.printf("[%s ", name);
                try {
                    Remote service = registry.lookup(name);
                    if (service instanceof ChatServerInterface) {
                        ChatServerInterface csi = (ChatServerInterface) service;
                        // How can we detect which one we are connected to?
                        try {
                            String s = csi.getName();
                            System.out.printf("%s OK]\n", s);
                        } catch (Exception e) {
                            System.out.printf(" - server not responding: %s]\n", e.toString());
                        }
                    }
                } catch (Exception e) {
                    System.out.printf(" - registry lookup failed: %s]\n", e.toString());
                }
            }
        } catch (Exception e) {
            System.out.printf("[Unable to list servers: %s]\n", e.toString());
        }
    }

    /**
     * Implements the '.help' user command.
     *
     * @param argv Reserved for future used (e.g. '.help connect').
     */
    protected void showHelp(String[] argv) {
        System.out.println("[" + versionString + "]");
        for (int i = 0; i < cmdHelp.length; i++) {
            System.out.println("[" + cmdHelp[i] + "]");
        }
    }

    // The main method.

    public static void main(String[] argv) throws RemoteException {

        // Added by uwara on 2025-12-10
        String userName = null;

        // If a username is provided as argument, use it
        if (argv.length > 0) {
            userName = argv[0];
        } else {
            // Generate random 6-digit username
            int randomId = (int) (Math.random() * 1000000);
            userName = String.format("%06d", randomId);
        }

        ChatClient chatClient = new ChatClient();
        // Set the username
        chatClient.setName(userName);

        System.out.println("[Client starting with username: " + chatClient.myName + "]");
        chatClient.readLoop();

        // For unknown reasons we need to force the exit.
        System.exit(0);
    }

    /**
     * Creates a new string which is the concatenation of the elements
     * in a string array, joined around a given delimiter string. This
     * is slightly different from String.join() in that this method can
     * start from any position in the array.
     *
     * @param sa         The string array to join together.
     * @param firstIndex The index of the first element in sa to consider.
     * @param delimiter  Delimiter string between elements in sa, or null.
     * @return The concatenated result or at least the empty string.
     */
    protected String stringJoin(String[] sa, int firstIndex, String delimiter) {
        StringBuilder sb = new StringBuilder();
        String delim = (delimiter == null) ? "" : delimiter;

        if (sa != null) {
            if (firstIndex < sa.length) {
                sb.append(sa[firstIndex]);
                for (int i = firstIndex + 1; i < sa.length; i++)
                    sb.append(delim).append(sa[i]);
            }
        }

        return sb.toString();
    }

    // Added by uwara

    /**
     * The user command interpreter. Commands are read from standard
     * input, parsed and dispatched to methods that either implement
     * user commands or sends the text to the ChatServer (when
     * connected).
     */
    protected void readLoop() {
        boolean halted = false;
        BufferedReader d = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("[Output from the client is in square brackets]");
        System.out.println("[Commands start with '.' (period). Try .help]");
        System.out.println("[When connected, type text and hit return to send]");

        setName(myName);        // set default name

        while (!halted) {
            System.out.print("Client> ");
            System.out.flush();
            String buf = null;

            try {
                buf = d.readLine();
            } catch (IOException iox) {
                iox.printStackTrace();
                System.out.println("\n[I/O error in command interface]");
                halted = true;
                continue;
            }

            if (buf == null) {    // EOF in command input.
                halted = true;
                continue;
            }

            // Trim away leading and trailing space from the raw input.

            String arg = buf.trim();

            // Check if the input starts with a period.

            if (arg.startsWith(".")) {

                // Skip the leading period and split the string into
                // fragments, separated by whitespace.

                String[] argv = arg.substring(1).split("\\s++");

                // Treat the first word as a command verb and make it
                // lowercase for easier matching.

                String verb = argv[0].toLowerCase();

                // Accept leading commend verb abbreviations.

                if ("quit".startsWith(verb)) {
                    halted = true;
                } else if ("connect".startsWith(verb)) {
                    connectToChat(stringJoin(argv, 1, " "));
                } else if ("disconnect".startsWith(verb)) {
                    userDisconnect();
                } else if ("list".startsWith(verb)) {
                    listServers();
                } else if ("name".startsWith(verb)) {
                    setName(stringJoin(argv, 1, " "));
                } else if ("help".startsWith(verb)) {
                    showHelp(argv);
                } else {
                    System.out.println("[" + verb + ": unknown command]");
                }
            } else if (0 < arg.length()) {
                sendToChat(arg);
            }

        } // while not halted

        System.out.println("[Quitting, please wait...]");

        disconnect(myServer);

        System.out.println("[Done]");

    }
}
