package apps.jserver;

import java.io.*;
import java.net.*;
import edu.umass.cs.flux.runtime.TaskBase;

public abstract class ReplyBase implements TaskBase {
Socket socket_in;
int length_in;
String content_in;
String output_in;
}
