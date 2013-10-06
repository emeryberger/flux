package apps.jserver;

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import edu.umass.cs.flux.runtime.*;

public class EventApp implements Runnable {
private LinkedList<Event> queue;
private boolean running;
private Object hold;
public EventApp() {queue = new LinkedList<Event>();hold = new Object();}
private boolean loop_run = true;
public void loop() {
while (loop_run) {
ListenImpl in = new ListenImpl();
in.execute();
Event e = new Event(convertOuts(in, 4));
e.push(-1);
e.setType(4);
queueEvent(e);
}
}
public void queueEvent(Event e) {synchronized (queue) { queue.addLast(e); }synchronized (hold) { hold.notify(); }}
private Event dequeueEvent() {while (queue.isEmpty()) {try {synchronized (hold) { hold.wait(); }} catch (InterruptedException ignore) {} }return queue.removeFirst();}
public void run() {running = true;while(running) {Event e = dequeueEvent();e = handleEvent(e);if (e!=null) {queueEvent(handleEvent(e));}}}
private Event handleEvent(Event e) {
TaskBase tb = e.getData();
Event nxt = null;
switch (e.getType()) {
case 0:
nxt = handleTask((ReplyBase)tb, e);
break;
case 1:
nxt = handleTask((ReadRequestBase)tb, e);
break;
case 2:
nxt = handleTask((HandlerBase)tb, e);
break;
case 3:
nxt = handleTask((ListenBase)tb, e);
break;
case 4:
nxt = handleTask((PageBase)tb, e);
break;
case 5:
nxt = handleTask((CacheBase)tb, e);
break;
case 6:
nxt = handleTask((XSLTBase)tb, e);
break;
}
return nxt;
}
Event handleTask(HandlerBase in, Event last) {
if ( (true)  &&  (TestXML.test(in.request_in)) )
{
	last.push(6); // XSLT
last.setData(convertIns(in, 5)); //Cache
last.setType(5); //Cache
}
if ( (true)  &&  (true) )
{
last.setData(convertIns(in, 5)); //Cache
last.setType(5); //Cache
}
	return last;
}
Event handleTask(PageBase in, Event last) {
	last.push(0); // Reply
	last.push(2); // Handler
last.setData(convertIns(in, 1)); //ReadRequest
last.setType(1); //ReadRequest
	return last;
}
Event handleTask(ReplyBase in,Event last) {
in.execute();
int nxt = last.pop();
if (nxt != -1) {
last.setData(convertOuts(in, nxt));
last.setType(nxt);
return last;
}
return null;
}
Event handleTask(ReadRequestBase in,Event last) {
in.execute();
int nxt = last.pop();
if (nxt != -1) {
last.setData(convertOuts(in, nxt));
last.setType(nxt);
return last;
}
return null;
}
Event handleTask(ListenBase in,Event last) {
in.execute();
int nxt = last.pop();
if (nxt != -1) {
last.setData(convertOuts(in, nxt));
last.setType(nxt);
return last;
}
return null;
}
Event handleTask(CacheBase in,Event last) {
in.execute();
int nxt = last.pop();
if (nxt != -1) {
last.setData(convertOuts(in, nxt));
last.setType(nxt);
return last;
}
return null;
}
Event handleTask(XSLTBase in,Event last) {
in.execute();
int nxt = last.pop();
if (nxt != -1) {
last.setData(convertOuts(in, nxt));
last.setType(nxt);
return last;
}
return null;
}
private TaskBase convertOuts(XSLTBase from, int to) {
TaskBase out = null;
switch(to) {
case 0: {
ReplyImpl o = new ReplyImpl();
o.socket_in = from.socket_out;
o.length_in = from.length_out;
o.content_in = from.content_out;
o.output_in = from.output_out;
out = o; }
break;
default:
throw new IllegalArgumentException("Bad Conversion!XSLT->"+to);
}
return out;
}
private TaskBase convertOuts(CacheBase from, int to) {
TaskBase out = null;
switch(to) {
case 0: {
ReplyImpl o = new ReplyImpl();
o.socket_in = from.socket_out;
o.length_in = from.length_out;
o.content_in = from.content_out;
o.output_in = from.output_out;
out = o; }
break;
case 6: {
XSLTImpl o = new XSLTImpl();
o.socket_in = from.socket_out;
o.length_in = from.length_out;
o.content_in = from.content_out;
o.file_in = from.output_out;
out = o; }
break;
default:
throw new IllegalArgumentException("Bad Conversion!Cache->"+to);
}
return out;
}
private TaskBase convertOuts(ListenBase from, int to) {
TaskBase out = null;
switch(to) {
case 1: {
ReadRequestImpl o = new ReadRequestImpl();
o.socket_in = from.socket_out;
out = o; }
break;
case 4: {
PageImpl o = new PageImpl();
o.socket_in = from.socket_out;
out = o; }
break;
default:
throw new IllegalArgumentException("Bad Conversion!Listen->"+to);
}
return out;
}
private TaskBase convertOuts(ReadRequestBase from, int to) {
TaskBase out = null;
switch(to) {
case 2: {
HandlerImpl o = new HandlerImpl();
o.socket_in = from.socket_out;
o.request_in = from.request_out;
out = o; }
break;
case 5: {
CacheImpl o = new CacheImpl();
o.socket_in = from.socket_out;
o.file_in = from.request_out;
out = o; }
break;
default:
throw new IllegalArgumentException("Bad Conversion!ReadRequest->"+to);
}
return out;
}
private TaskBase convertOuts(ReplyBase from, int to) {
TaskBase out = null;
switch(to) {
case 3: {
ListenImpl o = new ListenImpl();
out = o; }
break;
default:
throw new IllegalArgumentException("Bad Conversion!Reply->"+to);
}
return out;
}
private TaskBase convertIns(PageBase from, int to) {
TaskBase out = null;
switch(to) {
case 1: {
ReadRequestImpl o = new ReadRequestImpl();
o.socket_in = from.socket_in;
out = o; }
break;
default:
throw new IllegalArgumentException("Bad Conversion!");
}
return out;
}
private TaskBase convertIns(HandlerBase from, int to) {
TaskBase out = null;
switch(to) {
case 5: {
CacheImpl o = new CacheImpl();
o.socket_in = from.socket_in;
o.file_in = from.request_in;
out = o; }
break;
default:
throw new IllegalArgumentException("Bad Conversion!");
}
return out;
}
}
