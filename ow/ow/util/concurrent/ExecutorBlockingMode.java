/*
 * Copyright 2009 Kazuyuki Shudo, and contributors.
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

package ow.util.concurrent;

import java.util.concurrent.ExecutorService;

/**
 * An enum represents what happens on a task-submitting thread
 * if the {@link ExecutorService ExecutorService} cannot provide
 * a {@ Thread Thread} to execute the submitted task.
 */
public enum ExecutorBlockingMode {
	BLOCKING,
	NON_BLOCKING,
	REJECTING
}
