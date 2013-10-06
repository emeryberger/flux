package apps.jserver;

import edu.umass.cs.flux.runtime.Event;
import seda.sandStorm.api.QueueElementIF;

public class SedaWrapper implements QueueElementIF {
    Event e;

    public SedaWrapper(Event e) {
	this.e = e;
    }

    public Event getEvent() {
	return e;
    }

    public void setEvent(Event e) {
	this.e = e;
    }
}