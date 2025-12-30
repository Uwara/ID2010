// Agitator.java
import java.util.Map;
import java.util.logging.Logger;

// uwara Changed from Thread to Runnable for executor
class Agitator implements Runnable {
    protected Object myObj;
    protected String myCb;
    protected Object[] myArgs;
    protected java.lang.reflect.Method myMethod;
    protected Class[] myParms;
    
    private final Map<String, PlayerInterface> players;
    private final Logger log;

    public Agitator(Object obj, String cb, Object[] args, 
                    Map<String, PlayerInterface> players, Logger log) {
        this.myObj = obj;
        this.myCb = cb;
        this.myArgs = args;
        this.players = players;
        this.log = log;
        
        if (0 < args.length) {
            myParms = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                myParms[i] = args[i].getClass();
            }
        } else {
            myParms = null;
        }
    }

    public void initialize() throws java.lang.NoSuchMethodException {
        myMethod = myObj.getClass().getMethod(myCb, myParms);
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setContextClassLoader(
                myObj.getClass().getClassLoader()
            );
            myMethod.invoke(myObj, myArgs);
        } catch (Throwable t) {
            log.severe("Exception in " + myObj.getClass().getName() + 
                      ": " + t.toString());
            t.printStackTrace();
        } finally {
            // uwara Unregister player when done
            if (myObj instanceof PlayerInterface player) {
                players.remove(player.getId());
                log.fine("Player " + player.getId() + " left this Bailiff");
            }
        }
    }
}