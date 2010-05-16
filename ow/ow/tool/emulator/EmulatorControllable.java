/*
 * Copyright 2006-2007 National Institute of Advanced Industrial Science
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

package ow.tool.emulator;

import java.io.PrintStream;
import java.io.Writer;

/**
 * An application implements this interface to be controlled by an emulator.
 */
public interface EmulatorControllable {
	/**
	 * Invokes an application instance and returns a {@link Writer Writer},
	 * into which commands are written. 
	 */
	Writer invoke(String[] commandlineArgs, PrintStream out)
		throws Throwable;
}
