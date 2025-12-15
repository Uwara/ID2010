// ChatServerInterface.java
// 2024-01-24/fki Version 8: no package, no Jini, no rmid
// 2018-08-22/fki Refactored for lab version 7
// 14-oct-2004/FK New package.
// 25-mar-2004/FK New package.
// 18-mar-2004/FK First version

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This interface is implemented by the ChatServer, and is used by ChatClient
 * to place requests. It must therefore be known to both implementations.
 */
public interface ChatServerInterface extends Remote {
    /**
     * Used by ChatClient instances to inject a text message to be
     * distributed to registered RemoteEventListeners.
     * The sender's RMI identity is captured by the server for echo cancellation.
     *
     * @param sender The RemoteEventListener stub of the client sending the message.
     * @param msg    The message text.
     */
    public void say(RemoteEventListener sender, String msg) throws RemoteException;

    /**
     * Returns the server's user-friendly name.
     *
     * @return The server's user-friendly name.
     */
    public String getName() throws RemoteException;

    /**
     * Used by ChatClient instances to register themselves as receivers of
     * remote notifications.
     *
     * @param rel An object that implements RemoteEventListener interface.
     */
    public void register(RemoteEventListener rel) throws RemoteException;

    /**
     * Used by ChatClient instances to unregister themselves as receivers of
     * remote notifications.
     *
     * @param rel An object that implements RemoteEventListener interface.
     *            This should be the same object as was originally used to register.
     */
    public void unregister(RemoteEventListener rel) throws RemoteException;
}