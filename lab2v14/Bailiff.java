// Bailiff.java
// 2024-01-25/fki Refactored for v14 - No Jini, just rmiregistry
// 2018-08-16/fki Refactored for v13

import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Bailiff extends UnicastRemoteObject implements BailiffInterface {
    private static final long serialVersionUID = 1L;

    protected boolean debug = false;
    protected Logger log;
    protected String id = "";
    protected String info = "";
    protected Map<String, String> propertyMap;
    protected String myHostName = "";
    protected InetAddress myInetAddress;
    protected String serviceName = null;

    // [uwara 2025-12-20] TAG game: track players on this Bailiff
    protected Map<String, PlayerInterface> players = Collections.synchronizedMap(new HashMap<>());

    protected void debugMsg(String s) {
        if (debug) System.out.println(s);
    }

    private class Agitator extends Thread {
        protected Object myObj;
        protected String myCb;
        protected Object[] myArgs;
        protected java.lang.reflect.Method myMethod;
        protected Class[] myParms;

        public Agitator(Object obj, String cb, Object[] args) {
            myObj = obj;
            myCb = cb;
            myArgs = args;
            if (0 < args.length) {
                myParms = new Class[args.length];
                for (int i = 0; i < args.length; i++) myParms[i] = args[i].getClass();
            } else {
                myParms = null;
            }
        }

        public void initialize() throws java.lang.NoSuchMethodException {
            myMethod = myObj.getClass().getMethod(myCb, myParms);
            setContextClassLoader(myObj.getClass().getClassLoader());
        }

        @Override
        public void run() {
            try {
                myMethod.invoke(myObj, myArgs);
            } catch (Throwable t) {
                log.severe("Exception in " + myObj.getClass().getName() + ": " + t.toString());
                t.printStackTrace();
            } finally {
                // [uwara 2025-12-20] TAG game: unregister player when done
                if (myObj instanceof PlayerInterface player) {
                    players.remove(player.getId());
                    log.fine("Player " + player.getId() + " departed");
                }
            }
        }
    }

    public void migrate(Object obj, String cb, Object[] args)
        throws RemoteException, NoSuchMethodException {

        // [uwara 2025-12-20] TAG game: register player on arrival
        if (obj instanceof PlayerInterface player) {
            players.put(player.getId(), player);
            log.info("Player %s arrived".formatted(player.getId()));
        }

        log.fine(() -> "migrate obj=%s cb=%s args=%s".formatted(Objects.toString(obj), cb, Arrays.toString(args)));

        Agitator agitator = new Agitator(obj, cb, args);
        agitator.initialize();
        agitator.start();
    }

    // [uwara 2025-12-20] TAG game: get list of players on this Bailiff
    @Override
    public String[] getPlayerList() throws RemoteException {
        synchronized (players) {
            return players.keySet().toArray(new String[0]);
        }
    }

    // [uwara 2025-12-20] TAG game: get ID of player who is "it"
    @Override
    public String getItPlayerId() throws RemoteException {
        synchronized (players) {
            for (PlayerInterface p : players.values()) {
                if (p.getIsIt()) return p.getId();
            }
        }
        return null;
    }

    // [uwara 2025-12-20] TAG game: mediate tagging between players
    @Override
    public boolean tag(String playerId) throws RemoteException {
        PlayerInterface p = players.get(playerId);
        if (p != null) return p.tag();
        return false;
    }

    public Bailiff(String id, String info, Logger log)
        throws java.io.IOException, java.rmi.RemoteException {

        if (log != null) this.log = log;
        else throw new IllegalArgumentException("Logger is null");

        this.id = (id != null) ? id : this.id;
        this.info = (info != null) ? info : this.info;

        myInetAddress = java.net.InetAddress.getLocalHost();
        myHostName = myInetAddress.getHostName().toLowerCase();

        propertyMap = Collections.synchronizedMap(new HashMap<String, String>());
        propertyMap.put("id", id);
        propertyMap.put("info", info);
        propertyMap.put("hostname", myHostName);
        propertyMap.put("hostaddress", myInetAddress.getHostAddress());

        log.info(String.format("STARTING id=%s info=%s host=%s debug=%b",
            id, info, myHostName, debug));

        serviceName = getClass().getName() + "." + id + "." +
            Integer.toString((int) (Math.random() * (float) 0x7FFF_FFFF));

        Naming.rebind("///" + serviceName, this);
        log.info(String.format("Registered as %s", serviceName));
    }

    protected void unbind() {
        try {
            Naming.unbind("///" + serviceName);
        } catch (Exception e) {
            System.out.printf("When unbinding from rmiregistry: %s%n", e.toString());
        }
    }

    public String toString() {
        return String.format("Bailiff %s (%s) on host %s [%s]",
            id, info, myHostName, myInetAddress.getHostAddress());
    }

    private static void showUsage() {
        String[] msg = {
            "Usage: {'?',-h,-help}|[-id string][-info string][-log n]",
            "? -h help     This message",
            "-id   string  Sets the identification string of this Bailiff",
            "-info string  Sets the information message of this Bailiff",
            "-log  n       Sets the logging level, higher is more:",
            "  -log 0        Level.OFF",
            "  -log 3        Level.INFO",
            "  -log 7        Level.ALL"
        };
        for (String s : msg) System.out.println(s);
    }

    private static Level setLoglevelFromCmdLine(String s) {
        switch (Integer.parseInt(s)) {
            case 0:
                return Level.OFF;
            case 1:
                return Level.SEVERE;
            case 2:
                return Level.WARNING;
            case 3:
                return Level.INFO;
            case 4:
                return Level.CONFIG;
            case 5:
                return Level.FINE;
            case 6:
                return Level.FINER;
            case 7:
                return Level.FINEST;
            default:
                return Level.ALL;
        }
    }

    public static void main(String[] argv) throws  Exception {

        String id = null;
        String info = null;
        Level logLevel = Level.ALL;
        int state = 0;

        for (String av : argv) {
            switch (state) {
                case 0:
                    if (av.equals("?") || av.equals("-h") || av.equals("-help")) {
                        showUsage();
                        return;
                    } else if (av.equals("-id")) {
                        state = 1;
                    } else if (av.equals("-info")) {
                        state = 2;
                    } else if (av.equals("-log")) {
                        state = 3;
                    } else {
                        System.err.println("Unknown commandline argument: " + av);
                        return;
                    }
                    break;
                case 1:
                    id = av;
                    state = 0;
                    break;
                case 2:
                    info = av;
                    state = 0;
                    break;
                case 3:
                    logLevel = setLoglevelFromCmdLine(av);
                    state = 0;
                    break;
            }
        }

        Logger log = Logger.getAnonymousLogger();
        log.setLevel(logLevel);
        new Bailiff(id, info, log);
    }
}