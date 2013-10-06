package apps.jserver;

import java.io.*;
import java.net.*;
import edu.umass.cs.flux.runtime.TaskBase;

public abstract class HandlerBase implements TaskBase {
Socket socket_in;
String request_in;
Socket socket_out;
int length_out;
String content_out;
String output_out;
}
