/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
 * and Technology (AIST), and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ow.tool.dhtshell;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import ow.dht.DHT;
import ow.tool.dhtshell.commands.ClearCommand;
import ow.tool.dhtshell.commands.GetCommand;
import ow.tool.dhtshell.commands.HaltCommand;
import ow.tool.dhtshell.commands.HelpCommand;
import ow.tool.dhtshell.commands.InitCommand;
import ow.tool.dhtshell.commands.LocaldataCommand;
import ow.tool.dhtshell.commands.PutCommand;
import ow.tool.dhtshell.commands.QuitCommand;
import ow.tool.dhtshell.commands.RemoveCommand;
import ow.tool.dhtshell.commands.ResumeCommand;
import ow.tool.dhtshell.commands.SetSecretCommand;
import ow.tool.dhtshell.commands.SetTTLCommand;
import ow.tool.dhtshell.commands.StatusCommand;
import ow.tool.dhtshell.commands.SuspendCommand;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.MessagePrinter;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellServer;

/**
 * The main class of DHT shell server.
 * This shell is an utility to use/test a DHT.
 */
public final class DHTShell {
	private final static String COMMAND = "owdhtshell";	// A shell/batch script provided as bin/owdhtshell

	public final static String ENCODING = "UTF-8";

	private final static Class/*Command<<DHT<String>>>*/[] COMMANDS = {
		StatusCommand.class,
		InitCommand.class,
		GetCommand.class, PutCommand.class, RemoveCommand.class,
		SetTTLCommand.class, SetSecretCommand.class,
		LocaldataCommand.class,
//		SourceCommand.class,
		HelpCommand.class,
		QuitCommand.class,
		HaltCommand.class,
		ClearCommand.class,
		SuspendCommand.class, ResumeCommand.class
	};

	private final static List<Command<DHT<String>>> commandList;
	private final static Map<String,Command<DHT<String>>> commandTable;

	static {
		commandList = ShellServer.createCommandList(COMMANDS);
		commandTable = ShellServer.createCommandTable(commandList);
	}

	public void init(DHT<String> dht, int shellPort) {
		// start a ShellServer
		ShellServer<DHT<String>> shellServ =
			new ShellServer<DHT<String>>(commandTable, commandList,
					new ShowPromptPrinter(), new NoCommandPrinter(), null,
					dht, shellPort, null);
	}

	private static class ShowPromptPrinter implements MessagePrinter {
		public void execute(PrintStream out, String hint) {
			out.print("Ready." + Shell.CRLF);
			out.flush();
		}
	}

	private static class NoCommandPrinter implements MessagePrinter {
		public void execute(PrintStream out, String hint) {
			out.print("No such command");

			if (hint != null)
				out.print(": " + hint);
			else
				out.print(".");
			out.print(Shell.CRLF);

			out.flush();
		}
	}
}
