/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util;

/**
 * Simple implementation of the {@link android.util.Printer} interface
 * sending output to the given log priority and tag.
 *
 * ATL note: AOSP puts Printer in android.os and this class implements
 * that one; the fork's Printer lives in android.util (see Printer.java)
 * and LogPrinter implements it. Behaviour is otherwise AOSP's.
 */
public final class LogPrinter implements Printer {

	final int mPriority;
	final String mTag;
	final int mBuffer;

	public LogPrinter(int priority, String tag) {
		mPriority = priority;
		mTag = tag;
		mBuffer = Log.LOG_ID_MAIN;
	}

	public LogPrinter(int priority, String tag, int buffer) {
		mPriority = priority;
		mTag = tag;
		mBuffer = buffer;
	}

	public void println(String x) {
		Log.println(mPriority, mTag, x);
	}
}
