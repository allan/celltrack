package de.allan.celltrack;

import java.io.BufferedReader;
import android.os.Process;
import java.io.IOException;
//import java.io.InputStream;
import java.io.InputStreamReader;

public abstract class Logcat extends Thread {
	
	public abstract void onError(String msg, Throwable e);
	public abstract void onNewline(String line);

	private int numLines = 0;
	public java.lang.Process logcatProc = null;
	private java.lang.Process psProc = null;
	private static final int BUFFER_SIZE = 1024;

	public void run() {
		BufferedReader bufReader = null;
		
		try	{
			logcatProc = Runtime.getRuntime().exec(
				new String[] {"logcat", "-v", "time", "-b", "radio", "-s", "RILJ:D"});
		} catch (IOException e) {
			onError("logcat failed", e);
			return;
		}
		
		try {
			bufReader = new BufferedReader(
			    new InputStreamReader(logcatProc.getInputStream()),
			    BUFFER_SIZE
			);
			
			String line;
			while ((line = bufReader.readLine()) != null) {
				onNewline(line);
				numLines++;
			}
			
		} catch (IOException e)	{
			onError("logcat reading failed", e);
		} finally {
			if (bufReader != null)
				try { bufReader.close(); } 
			    catch (IOException e) {}
				
			stopLogcat();
		}
	}

	public void stopLogcat() {
		if (logcatProc == null)	return;
		
		logcatProc.destroy();
		logcatProc = null;
	}
	
	public void killall() {
		BufferedReader reader = null;
		
		try	{
			psProc = Runtime.getRuntime().exec(
				new String[] {"ps"});
		} catch (IOException e) {
			onError("starting 'ps' failed", e);
			return;
		}
		
		try	{
			reader = new BufferedReader(
				new InputStreamReader(psProc.getInputStream()),
			    BUFFER_SIZE
			);

			String line;
			while ((line = reader.readLine()) != null) {
				String[] sa = line.split(" +");
				try {
					if (sa[8].equals("logcat"))
						Process.killProcess(Integer.parseInt(sa[1]));
				} catch (ArrayIndexOutOfBoundsException err) {}
			}
			
		} catch (IOException e) {
			onError("failed reading ps output", e);
		} finally {
			if (reader != null)
				try { reader.close(); } catch (IOException e) {}
				
			stopLogcat();
		}		
	}

	public int getNumLines() {
		return numLines;
	}
}
