# nidoca-iptv
Nidoca IPTV Player is supposed to be an application where you can import and play m3u and m3u8 files.

# Requirements
The program consists of three views: Channel, Settings and TV.

# View: Channel
The view includes all stations that were loaded in the currently loaded m3u file. It contains the icon and the name of the tv station. If you click on one of the channel icons, you get to the TV view. The station is then played here. The view is responsive and floating.

The view includes a "settings" icon at the top right. Clicking on it takes you to the settings view.

The lettering "Channel" is at the top left.

Please see image list_view.png

# View: Settings

In the "settings" view you can load the desired m3u or m3u8 file via URL. If you click on the "save" button,
the entered URL will be loaded and all channels will be updated.

example m3u file:
https://raw.githubusercontent.com/PrinzMichiDE/iptv-kodi-german/master/ip-tv-german.m3u

When you enter a URL, the Standard Android keyboard is displayed.

If you click on the list icon in the top right corner, you jump back to the station list.

The lettering "Settings" is at the top left.


# View: TV

On this view, you can watch the channel.
There are 3 action icons at the top right: full screen, screen sharing (Chromecast, etc), back icon.
The station is displayed in the lower area. The station name is in the upper left corner.
When you press action icon "fullscreen" video is played in fullscreen mode
When you press action icon "screen sharing" you can stream your current station to tv, smart tv, chromecast.
When you press the "back icon" you will go back to station list view.



# Technical Details

The project can be found on Github:
https://github.com/koshisinthehouse/nidoca-iptv
It is to be developed for the Android platform and written with Java.
You should use the native Android Player for streaming and no additional library:

Example:
    package com.grexample.ooyalalive;

    import java.net.URL;
    import android.app.Activity;
    import android.net.Uri;
    import android.os.Bundle;
    import android.widget.MediaController;
    import android.widget.VideoView;

    public class Main extends Activity {

        private String urlStream;
        private VideoView myVideoView;
        private URL url;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main_vv);//***************
                myVideoView = (VideoView)this.findViewById(R.id.myVideoView);
                MediaController mc = new MediaController(this);
                myVideoView.setMediaController(mc);         
                urlStream = "http://jorgesys.net/i/irina_delivery@117489/master.m3u8";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        myVideoView.setVideoURI(Uri.parse(urlStream)); 
                    }
                });
        }
    }


The offer should also include the one-time setup of the app in the Google Android Store.




