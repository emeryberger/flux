package apps.jserver;
import java.io.*;
import java.net.*;
import seda.sandStorm.api.*;
import seda.sandStorm.core.*;
import edu.umass.cs.flux.runtime.*;

class TimerEvent implements QueueElementIF { }
public class ListenStage implements EventHandlerIF {
private SinkIF nextsink, mysink;
private ManagerIF mgr;
private ssTimer t;
public void init(ConfigDataIF config) throws Exception {mgr = config.getManager();ServerConfig.setRoot(config.getString("rootDir"));ServerConfig.setPort(config.getInt("httpPort"));nextsink = mgr.getStage("Page").getSink();mysink = config.getStage().getSink();t = new ssTimer();t.registerEvent(2000, new TimerEvent(), mysink);}
public void handleEvent(QueueElementIF item) {if (item instanceof TimerEvent) {ListenImpl task = new ListenImpl();task.execute();
Event e = new Event(Conversion.convertOuts(task, 4));
e.push(-1);
e.setType(4);
try {
nextsink.enqueue(new SedaWrapper(e));
mysink.enqueue(item);
} catch (SinkException ex) {ex.printStackTrace();}
}
else { System.err.println("Unknown Event: "+item); }
}
public void handleEvents(QueueElementIF items[]) {for(int i=0; i<items.length; i++) {handleEvent(items[i]);}}
public void destroy() {}
}
