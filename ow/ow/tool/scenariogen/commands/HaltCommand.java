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

package ow.tool.scenariogen.commands;

import java.io.PrintStream;
import java.io.PrintWriter;

import ow.tool.scenariogen.ScenarioGeneratorContext;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class HaltCommand implements Command<ScenarioGeneratorContext> {
	private final static String[] NAMES = {"halt"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "halt <time (sec)>";
	}

	public boolean execute(ShellContext<ScenarioGeneratorContext> context) {
		ScenarioGeneratorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		// parse arguments
		if (args.length < 1) {
			out.println("Usage: " + this.getHelp());
			return false;
		}

		long time = (long)(Double.parseDouble(args[0]) * 1000.0);

		// write
		PrintWriter writer = cxt.getWriter();

		// e.g. schedule 1000 halt
		writer.print("schedule ");
		writer.print(time);
		writer.print(" halt");
		writer.println();

		writer.flush();

		return false;
	}
}
