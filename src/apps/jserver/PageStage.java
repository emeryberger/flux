package apps.jserver;
import java.io.*;
import java.net.*;
import seda.sandStorm.api.*;
import seda.sandStorm.core.*;
import edu.umass.cs.flux.runtime.*;

public class PageStage implements EventHandlerIF {
private SinkIF mysink;
private ManagerIF mgr;
private SinkIF nextSink;
public void init(ConfigDataIF config) throws Exception {mgr = config.getManager();nextSink = mgr.getStage("ReadRequest").getSink();mysink = config.getStage().getSink();}
public void handleEvent(QueueElementIF item) {if (item instanceof SedaWrapper) {SedaWrapper sw = (SedaWrapper)item;Event e = sw.getEvent();PageImpl in = (PageImpl)e.getData();
	e.push(0); // Reply
	e.push(2); // Handler
e.setData(Conversion.convertIns(in, 1)); //ReadRequest
e.setType(1); //ReadRequest
try {
nextSink.enqueue(item);
} catch (SinkException ex) {ex.printStackTrace();}
}
else { System.err.println("Unknown Event: "+item); }
}
public void handleEvents(QueueElementIF items[]) {for(int i=0; i<items.length; i++) {handleEvent(items[i]);}}
public void destroy() {}
}
