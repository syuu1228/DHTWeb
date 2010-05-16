package ow.tool.util.shellframework;

import java.io.PrintStream;

public interface MessagePrinter {
	void execute(PrintStream out, String hint);
}
