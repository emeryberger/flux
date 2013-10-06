package apps.jserver;

import java.io.*;
import java.net.*;
import edu.umass.cs.flux.runtime.TaskBase;

public abstract class CacheBase implements TaskBase {
Socket socket_in;
String file_in;
Socket socket_out;
int length_out;
String content_out;
String output_out;
}
