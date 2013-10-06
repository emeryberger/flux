package apps.jserver;
import java.io.*;
import java.net.*;
import seda.sandStorm.api.*;
import seda.sandStorm.core.*;
import edu.umass.cs.flux.runtime.*;

public class HandlerStage implements EventHandlerIF {
private SinkIF mysink;
private ManagerIF mgr;
SinkIF[] sinks = new SinkIF[2];
public void init(ConfigDataIF config) {mgr = config.getManager();
try {
sinks[0] = mgr.getStage("Cache").getSink();
sinks[1] = mgr.getStage("Cache").getSink();
} catch (NoSuchStageException ex) {ex.printStackTrace();}
mysink = config.getStage().getSink();}
public void destroy() {}
public void handleEvent(QueueElementIF item) {if (item instanceof SedaWrapper) {SedaWrapper sw = (SedaWrapper)item;Event e = sw.getEvent();SinkIF nextSink;HandlerImpl in = (HandlerImpl)e.getData();
if ( (true)  &&  (TestXML.test(in.request_in)) )
{
nextSink = sinks[0];
	e.push(6); // XSLT
e.setData(Conversion.convertIns(in, 5)); //Cache
e.setType(5); //Cache
try {
nextSink.enqueue(item);
} catch (SinkException ex) {ex.printStackTrace();}
}
if ( (true)  &&  (true) )
{
nextSink = sinks[1];
e.setData(Conversion.convertIns(in, 5)); //Cache
e.setType(5); //Cache
try {
nextSink.enqueue(item);
} catch (SinkException ex) {ex.printStackTrace();}
}
}
else { System.err.println("Unknown Event: "+item); }
}
public void handleEvents(QueueElementIF items[]) {for(int i=0; i<items.length; i++) {handleEvent(items[i]);}}
}
