package de.allan.celltrack;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings.Secure;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Date;
import java.util.Calendar;
import java.util.regex.*;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

public class CellTrackService extends Service {
	
	public static Logcat  logcat;
	public static Date    lastUpdate;
	static Boolean        isRunning = false;
	public static int     cellupdates = 0;
	public Date           lastDate = new Date();
	JSONObject            json = new JSONObject();
	Calendar              cal = Calendar.getInstance();
	static Boolean        updateInProgress = false;
	private static final int MSG_NEWLINE = 3;
	public static CharSequence logOutput = "";
	JSONObject []         oldjson = new JSONObject[2];
	private final IBinder mBinder = new LocalBinder();
	private int           year = cal.get(Calendar.YEAR) - 1900;
        private String myId = CellTrack.myId;
	
        public class LocalBinder extends Binder {
            CellTrackService getService() {
                return CellTrackService.this;
            }
        }

	private final Handler logcatHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
				handleNewline(msg);
		}
	};
	
	public static void postJSON(String URI, final JSONObject json) {
		//Message msg = CellTrack.pbHandler.obtainMessage(1);

		CellTrack.uiHandler.sendEmptyMessage(1);
		lastUpdate = new Date();
		updateInProgress = true;
		final HttpClient httpclient = new DefaultHttpClient();
		final HttpPost httppost = new HttpPost(URI);
		
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					StringEntity se = new StringEntity(json.toString());
					httppost.setEntity(se);
					httppost.setHeader("Accept", "application/json");
					httppost.setHeader("Content-type", "application/json");
					CellTrack.log("json: "+ json.toString());
					ResponseHandler<String> responseHandler = new BasicResponseHandler();
					String response = httpclient.execute(httppost, responseHandler);
					CellTrack.log(response);
					cellupdates++;
	                Bundle b = new Bundle();
	                b.putInt("cellupdates", cellupdates);
	                Message msg = CellTrack.uiHandler.obtainMessage(2);
	                msg.setData(b);
					CellTrack.uiHandler.sendMessage(msg);
					updateInProgress = false;
					
				} catch (ClientProtocolException e) {
					Log.v("clientprotex", e.toString());
					CellTrack.log(e.toString());
				} catch (IOException e) {
					Log.v("ioex", e.toString());
					CellTrack.log(e.toString());
				} finally {
					CellTrack.uiHandler.sendEmptyMessage(0);
				}
			}
		});
		t.start();
	} 
	
	int day = 0, month = 0, hour = 0, minute = 0, second = 0;
	int cid = 0, lac = 0, mnc = 0, mcc = 0;
	
	private void handleNewline(Message msg)	{
		JSONObject json = new JSONObject();
		String line = (String) msg.obj;
		Date thisDate = null;

		Pattern dpattern = Pattern.compile("^(\\d+)-(\\d+) (\\d+):(\\d+):(\\d+).*$");
		Matcher dmatcher = dpattern.matcher(line);
		if (dmatcher.matches()) {
			day    = Integer.parseInt(dmatcher.group(2));
			month  = Integer.parseInt(dmatcher.group(1));	
			hour   = Integer.parseInt(dmatcher.group(3));
			minute = Integer.parseInt(dmatcher.group(4));
			second = Integer.parseInt(dmatcher.group(5));

			thisDate = new Date(year, month - 1, day, hour, minute, second);

			if (lastDate.compareTo(thisDate) <= 0) {
				lastDate = new Date(year, month - 1, day, hour, minute, second);
				//TextView tw = (TextView) android.app.Activity.findViewById(R.id.status);
				// 12-26 14:53:25.157 D/RILJ ( 371): [3111]< OPERATOR {Orange F, Orange F, 20801}
				// 12-26 14:53:25.197 D/RILJ ( 371): [3113]< REGISTRATION_STATE {1, 0403, 00061E10,	9, null, null, null, null, null, null, null, null, null, null}
				//line ="12-26 14:53:25.157 D/RILJ ( 371): [3111]< OPERATOR {Orange F, Orange F, 20801}";
				//line = "02-04 11:00:00.197 D/RILJ ( 371): [3113]< REGISTRATION_STATE {1, 0403, 00061E10,	9, null, null, null, null, null, null, null, null, null, null}";		
				Pattern oppattern = Pattern.compile("^(.*) D/RILJ.*\\]< OPERATOR .*, (.*)\\}$");
				Matcher opmatcher = oppattern.matcher(line);
				if (opmatcher.matches()) {
					try {
						lac = Integer.parseInt(opmatcher.group(2).substring(0, 3));
						cid = Integer.parseInt(opmatcher.group(2).substring(3, 5));
					} catch (NumberFormatException e) {}
				}
				Pattern mccmnc = Pattern.compile("^(.*) D/RILJ.*\\]< REGISTRATION_STATE \\{\\d+, (\\w+), (\\w+),.*$");
				Matcher mmatcher = mccmnc.matcher(line);
				if (mmatcher.matches()) {
					try {
						mcc = Integer.parseInt(mmatcher.group(2), 16);
						mnc = Integer.parseInt(mmatcher.group(3), 16);
					} catch (NumberFormatException e) {}
				}

				if (   cid != 0 && lac != 0 && mcc != 0 && mnc != 0
					&& (opmatcher.matches() || mmatcher.matches())) {
					try {
						json.put("cid", cid);
						json.put("lac", lac);
						json.put("mcc", mcc);
						json.put("mnc", mnc);

						for (int i=0; i<oldjson.length; i++) {
							if (oldjson[i] == null) {
								oldjson[i] = new JSONObject();
								oldjson[i].put("meh","asdf");
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
					String old0 = oldjson[0].toString();
					String old1 = oldjson[1].toString();
					String njs = json.toString();

					if (!old0.equals(njs) && !old1.equals(njs)) {
						oldjson[1] = oldjson[0];
						oldjson[0] = json;

						postJSON("http://allan.de/loc/id/"+myId, json);
					} else
						CellTrack.log("flapping cells");
				} else Log.v("celltrack", "null values occured, wow");
			}
		}
	}
	
    @Override
    public void onCreate() {
    	isRunning = true;
    	Toast.makeText(this, "service onCreate", 1000).show();
		logcat = new Logcat() {
			public void onError(final String msg, Throwable e) {
				Toast.makeText(CellTrackService.this, msg, Toast.LENGTH_LONG).show();
			}
	
			public void onNewline(String line) {
				Message msg = logcatHandler.obtainMessage(MSG_NEWLINE);
				msg.obj = line;
				logcatHandler.sendMessage(msg);
			}
		};
	
		logcat.killall();
		logcat.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
    	isRunning = false;
    	logcat.stopLogcat();
    	logcat = null;
    	
        Toast.makeText(this, "local-service stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
