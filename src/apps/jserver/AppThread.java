package apps.jserver;

import java.io.*;
import java.net.*;
import edu.umass.cs.flux.runtime.*;

public class AppThread implements Runnable {
static ThreadPool pool = new ThreadPool(20);
private static boolean running = true;
public static void loop() {
while (running) {
ListenImpl in = new ListenImpl();
in.execute();
pool.queueTask(new AppThread(in));
}
}
ListenBase start;
public AppThread(ListenBase in) {
this.start = in;
}
public void run() {
ReadRequestImpl var_1= new ReadRequestImpl();
var_1.socket_in=start.socket_out;
var_1.execute();
HandlerImpl var_2 = new HandlerImpl();
if ( (true)  &&  (TestXML.test(var_1.request_out)) )
{
CacheImpl var_3= new CacheImpl();
var_3.socket_in=var_1.socket_out;
var_3.file_in=var_1.request_out;
var_3.execute();
XSLTImpl var_4= new XSLTImpl();
var_4.socket_in=var_3.socket_out;
var_4.length_in=var_3.length_out;
var_4.content_in=var_3.content_out;
var_4.file_in=var_3.output_out;
var_4.execute();
var_2.socket_out=var_4.socket_out;
var_2.length_out=var_4.length_out;
var_2.content_out=var_4.content_out;
var_2.output_out=var_4.output_out;
}
else if ( (true)  &&  (true) )
{
CacheImpl var_5= new CacheImpl();
var_5.socket_in=var_1.socket_out;
var_5.file_in=var_1.request_out;
var_5.execute();
var_2.socket_out=var_5.socket_out;
var_2.length_out=var_5.length_out;
var_2.content_out=var_5.content_out;
var_2.output_out=var_5.output_out;
}
ReplyImpl var_6= new ReplyImpl();
var_6.socket_in=var_2.socket_out;
var_6.length_in=var_2.length_out;
var_6.content_in=var_2.content_out;
var_6.output_in=var_2.output_out;
var_6.execute();
}
}
