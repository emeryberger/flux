package apps.jserver;
import edu.umass.cs.flux.runtime.*;
public class Conversion {
public static TaskBase convertOuts(CacheBase from, int to) {
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
public static TaskBase convertOuts(ReadRequestBase from, int to) {
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
public static TaskBase convertOuts(XSLTBase from, int to) {
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
public static TaskBase convertOuts(ReplyBase from, int to) {
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
public static TaskBase convertOuts(ListenBase from, int to) {
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
public static TaskBase convertIns(HandlerBase from, int to) {
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
public static TaskBase convertIns(PageBase from, int to) {
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
}
