
CellTrack
=========

This is an Android app which tracks all the celltowers you came by
and draws maps of off it.


### How it works

An Android service uses the `logcat` utility to read your device's Radio
Daemon (RIL) logfile, where all the cell id's and area codes get written that
your device passes by.

When a new cell gets logged, CellTrack sends the cell id to a node.js server
which then queries googles database for the GPS coordinates for that cell and
saves those coordinates in a database.  To distinguish between devices,
CellTrack uses your randomly generated
[Android_Id](http://developer.android.com/reference/android/provider/Settings.Secure.html#ANDROID_ID)
and saves it together with your updates.


### Tryout

If you don't want to compile that crap yourself, you can open
[http://allan.de/CellTrack.apk](http://allan.de/CellTrack.apk)
in your Android browser and install the app from there.

Once your device has send some updates, you can click the logo inside
the app to open googlemaps which will display todays track.  A list of all
the days and links to those maps can be found under
`http://allan.de/loc/id/<your-id>/list`.  Your id gets display in CellTracks
log when you start the application.


### Screenshot of a busride through Ulm and the app itself:

![screenshot](http://allan.de/celltrack.png) 
