package apps.jserver;

import java.io.*;
import java.net.*;
import edu.umass.cs.flux.runtime.TaskBase;

public abstract class XSLTBase implements TaskBase {
Socket socket_in;
int length_in;
String content_in;
String file_in;
Socket socket_out;
int length_out;
String content_out;
String output_out;
}
