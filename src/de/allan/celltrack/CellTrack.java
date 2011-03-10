package de.allan.celltrack;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings.Secure;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class CellTrack extends Activity {
	
	TextView tv;
	Resources res;
	ProgressBar bar;
	static TextView logtv;
	static TextView lastUpdate;
	static ProgressBar smallbar;
	static TextView numCellUpdates;
	static boolean isRunning = false;
	static ScrollView logsv;
	Drawable shape_red;
	Drawable shape_green;
	private boolean mIsBound;
	static SimpleDateFormat formatter = new SimpleDateFormat ("HH:mm:ss");
	@SuppressWarnings("unused")	private CellTrackService mBoundService = null;
	static String myId;
	
	static Handler logHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Date currentTime = new Date();
			String datestring = formatter.format(currentTime);
			String logstr = msg.getData().getString("data");
			if (isRunning) {
				int len = logtv.length();
				if (len > 10000) {
					CharSequence cs = logtv.getText();
					CharSequence scs = cs.subSequence(len - 300, len);
					logtv.setText(scs);
				}
				logtv.append("["+datestring+"] "+ logstr +"\n");
				autoScroll(logtv, logsv);
			} else { 
				CellTrackService.logOutput = CellTrackService.logOutput + "["+datestring+"] "+ logstr +"\n";
			}
		}
	};
	
	static Handler uiHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (isRunning) {
				switch (msg.what) {
				case 1: if (smallbar != null) smallbar.setVisibility(ProgressBar.VISIBLE);
						break;
				case 0: if (smallbar != null) smallbar.setVisibility(ProgressBar.INVISIBLE);
						break;
				case 2: numCellUpdates
							.setText(Integer.toString(msg.getData().getInt("cellupdates")));
						String datestring = formatter.format(CellTrackService.lastUpdate);
						lastUpdate.setText(datestring);
						break;
				}
			}
		}
	};
	
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBoundService = ((CellTrackService.LocalBinder)service).getService();
            Toast.makeText(CellTrack.this, "R.string.local_service_connected", Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            mBoundService = null;
            Toast.makeText(CellTrack.this, "R.string.local_service_disconnected", Toast.LENGTH_SHORT).show();
        }
    };
    
    void doBindService() {
        bindService(new Intent(CellTrack.this, CellTrackService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }
    
    void doUnbindService() {
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
    }
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,   
        			         WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);
        
        res = getResources();
        tv = (Button) findViewById(R.id.status);
		logtv = (TextView) findViewById(R.id.tv);
		logsv = (ScrollView) findViewById(R.id.sv);
        lastUpdate = (TextView) findViewById(R.id.lastUpdate);
		shape_red = res.getDrawable(R.drawable.gradient_box_red);
        shape_green = res.getDrawable(R.drawable.gradient_box_green);
        numCellUpdates = (TextView) findViewById(R.id.numCellUpdates);
        smallbar = (ProgressBar) findViewById(R.id.progress_small);
        smallbar.setProgressDrawable(shape_red);
        smallbar.setVisibility(ProgressBar.INVISIBLE);
        ContentResolver cr=getContentResolver();
        myId = Secure.getString(cr, Secure.ANDROID_ID);

        tv.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {
        		if (CellTrackService.isRunning) {
        			doUnbindService();
        			stopService(new Intent(CellTrack.this, CellTrackService.class));
        			tv.setBackgroundDrawable(shape_red);
        			log("service stopped");
        		} else {
        			startService(new Intent(CellTrack.this, CellTrackService.class));
        			doBindService();
        			tv.setBackgroundDrawable(shape_green);
        			log("service started");
        		}
        	}
        });
        
        Button btnStats = (Button) findViewById(R.id.btnStats);
        btnStats.setOnClickListener(new OnClickListener() {   	
			public void onClick(View v) {
				try {
					CellTrackService.postJSON("http://allan.de/loc/id/"+myId, new JSONObject());
				} catch (Exception e) {
					goBlooey(e);
					e.printStackTrace();
				}
			}
		});
        
        Button btnName = (Button) findViewById(R.id.tvname);
        btnName.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		try {
        			String uri = "geo:0,0?q=http:%2F%2Fallan.de%2Floc%2Fid%2F"+myId;  
        	        startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)));			
        		} catch(Exception e) {
        			log(e.toString());
        		}
        	}
        });
    }
    
	@Override
	public void onStart() {
		super.onStart();
		isRunning = true;
        if (CellTrackService.isRunning)	{
        	tv.setBackgroundDrawable(shape_green);
            log("CellTrack resumed");
        }
		else {
			tv.setBackgroundDrawable(shape_red);
			log("CellTrack started");
			CellTrack.log("my CellTrack ID: "+myId);
		}
	}
	
	public void onResume() {
		super.onResume();
		if (CellTrackService.lastUpdate != null) {
			String datestring = formatter.format(CellTrackService.lastUpdate);
			lastUpdate.setText(datestring);
			numCellUpdates.setText(Integer.toString(CellTrackService.cellupdates));
		} else {
			lastUpdate.setText("never");
		}
		logtv.setText(CellTrackService.logOutput);
	}
	
	public void onStop() {
		super.onStop();
		isRunning = false;
		doUnbindService();
		CellTrackService.logOutput = logtv.getText();
	}

	private void goBlooey(Throwable t) {
		AlertDialog.Builder builder=new AlertDialog.Builder(this);

		builder
			.setTitle("exception")
			.setMessage(t.toString())
			.setPositiveButton("dismiss", null)
			.show();
	}

	private static void autoScroll(final TextView tv, final ScrollView sv) {
	    sv.post(new Runnable() {
	        public void run() {
	            sv.scrollTo(0, tv.getLineHeight()*tv.getLineCount());
	        }
	    });
	}
	
	public static void log(String s) {
        Message msg = logHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putString("data", s);
        msg.setData(b);
        logHandler.sendMessage(msg);
	}
}