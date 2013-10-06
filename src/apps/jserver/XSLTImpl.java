package apps.jserver;

public class XSLTImpl extends XSLTBase {
	public void execute() {
		socket_out = socket_in;
		content_out = content_in;
		length_out = length_in;
		output_out = file_in;
	}
}
