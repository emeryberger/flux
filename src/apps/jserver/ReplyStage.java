package apps.jserver;
import java.io.*;
import java.net.*;
import seda.sandStorm.api.*;
import seda.sandStorm.core.*;
import edu.umass.cs.flux.runtime.Event;

public class ReplyStage implements EventHandlerIF {
private String[] stage_names = new String[7];
private SinkIF mysink;
private ManagerIF mgr;
public void init(ConfigDataIF config) {mgr = config.getManager();mysink = config.getStage().getSink();
stage_names[0] = "Reply";
stage_names[1] = "ReadRequest";
stage_names[3] = "Listen";
stage_names[2] = "Handler";
stage_names[4] = "Page";
stage_names[5] = "Cache";
stage_names[6] = "XSLT";
}
public void handleEvent(QueueElementIF item) {if (item instanceof SedaWrapper) {SedaWrapper sw = (SedaWrapper)item;Event e = sw.getEvent();ReplyImpl in = (ReplyImpl)e.getData();in.execute();int nxt = e.pop();if (nxt != -1) {
e.setData(Conversion.convertOuts(in, nxt));
e.setType(nxt);
try {
mgr.getStage(stage_names[nxt]).getSink().enqueue(sw);
} catch (NoSuchStageException ex) {ex.printStackTrace();}
 catch (SinkException ex) {ex.printStackTrace();}
}
}
else { System.err.println("Unknown Event: "+item); }
}
public void handleEvents(QueueElementIF items[]) {for(int i=0; i<items.length; i++) {handleEvent(items[i]);}}
public void destroy() {}
}
