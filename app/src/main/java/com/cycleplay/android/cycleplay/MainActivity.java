package com.cycleplay.android.cycleplay;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final int PICK_FILE1_RESULT_CODE = 1;
    private final int PICK_FILE2_RESULT_CODE = 2;
    private Uri file1Uri = null;
    private Uri file2Uri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
    }

    /**
     * Init views and set click listeners
     */
    private void initViews() {
        Button button2 = (Button) findViewById(R.id.button2);
        Button button1 = (Button) findViewById(R.id.button1);
        Button buttonPlay = (Button) findViewById(R.id.button_play);

        button1.setOnClickListener(this);
        button2.setOnClickListener(this);
        buttonPlay.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.button_play) {
            if (file1Uri != null && file2Uri != null) {
                MusicService.initSongs(file1Uri, file2Uri);
                startService(new Intent(this, MusicService.class));
            } else {
                Toast.makeText(this, "Select both files!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/mpeg");

            if (view.getId() == R.id.button1)
                startActivityForResult(intent, PICK_FILE1_RESULT_CODE); // Pick first song
            else if (view.getId() == R.id.button2)
                startActivityForResult(intent, PICK_FILE2_RESULT_CODE); // Pick second song
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_FILE1_RESULT_CODE) {
                file1Uri = data.getData();
            } else if (requestCode == PICK_FILE2_RESULT_CODE) {
                file2Uri = data.getData();
            }
        }
    }

    /**
     * Music Service Class
     */
    public static class MusicService extends Service {

        private static MediaPlayer mediaPlayer1 = null; // Media Player for playing first song
        private static MediaPlayer mediaPlayer2 = null; // Media Player for playing second song
        private static Uri firstSongUri; // First picked file's URI
        private static Uri secondSongUri; // Second picked file's URI
        private static int crossFade = 10000; // CrossFade duration 10 seconds
        private final Handler handler = new Handler(); // Handler for managing runnable

        private float volume = 0;

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        /**
         * Runnable for playing another song with crossfade
         */
        private Runnable runnable = new Runnable() {
            @Override
            public void run() {
                playNextSongWithCF();

                handler.postDelayed(this, 1000);
            }
        };

        /**
         * Play next song with crossfade
         */
        private void playNextSongWithCF() {
            if (mediaPlayer1.isPlaying() && !mediaPlayer2.isPlaying()) {
                if (mediaPlayer1.getCurrentPosition() >= mediaPlayer1.getDuration() - crossFade) {
                    mediaPlayer2.setVolume(0, 0);
                    mediaPlayer2.start();
                    startCrossFade(mediaPlayer1, mediaPlayer2);
                }
            } else if (mediaPlayer2.isPlaying() && !mediaPlayer1.isPlaying()) {
                if (mediaPlayer2.getCurrentPosition() >= mediaPlayer2.getDuration() - crossFade) {
                    mediaPlayer1.setVolume(0, 0);
                    mediaPlayer1.start();
                    startCrossFade(mediaPlayer2, mediaPlayer1);
                }
            }
        }

        /**
         * Starts crossfade on the TimeTask
         * @param mp1 Fading out media player
         * @param mp2 Fading in media player
         */
        private void startCrossFade(final MediaPlayer mp1, final MediaPlayer mp2){
            final int FADE_DURATION = crossFade; // The duration of the fade
            // The amount of time between volume changes. The smaller this is, the smoother the fade
            final int FADE_INTERVAL = 250;
            final int MAX_VOLUME = 1; // The volume will increase from 0 to 1
            int numberOfSteps = FADE_DURATION / FADE_INTERVAL; //Calculate the number of fade steps
            // Calculate by how much the volume changes each step
            final float deltaVolume = MAX_VOLUME / (float)numberOfSteps;

            // Create a new Timer and Timer task to run the fading outside the main UI thread
            final Timer timer = new Timer(true);
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    makeFadeStep(mp1, mp2, deltaVolume); // Do a fade step
                    // Cancel and Purge the Timer if the desired volume has been reached
                    if(volume >= 1f){
                        timer.cancel();
                        timer.purge();
                        volume = 0;
                    }
                }
            };

            timer.schedule(timerTask,FADE_INTERVAL,FADE_INTERVAL);
        }

        /**
         * Makes a crossfade step
         * @param mp1 Fading out media player
         * @param mp2 Fading in media player
         * @param deltaVolume value of volume changing
         */
        public void makeFadeStep(MediaPlayer mp1, MediaPlayer mp2, float deltaVolume) {
            mp1.setVolume(1 - volume, 1 - volume);
            mp2.setVolume(volume, volume);

            volume += deltaVolume;
        }

        /**
         * Initialize file's URIs
         * @param uri1 first URI
         * @param uri2 second URI
         */
        public static void initSongs(Uri uri1, Uri uri2) {
            firstSongUri = uri1;
            secondSongUri = uri2;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (mediaPlayer1 != null) mediaPlayer1.release();
            if (mediaPlayer2 != null) mediaPlayer2.release();

            volume = 0;
            initPlayers();

            mediaPlayer1.start();

            handler.post(runnable);

            return super.onStartCommand(intent, flags, startId);
        }

        /**
         * Initialize Media Players
         */
        private void initPlayers() {
            mediaPlayer1 = new MediaPlayer();
            mediaPlayer1.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer1.setVolume(1, 1);
            try {
                mediaPlayer1.setDataSource(getApplicationContext(), firstSongUri);
                mediaPlayer1.prepare();
            } catch (Exception e) {
                Log.e("MUSIC SERVICE", "Error setting first data source", e);
            }

            mediaPlayer2 = new MediaPlayer();
            mediaPlayer2.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer2.setVolume(0, 0);
            try {
                mediaPlayer2.setDataSource(getApplicationContext(), secondSongUri);
                mediaPlayer2.prepare();
            } catch (Exception e) {
                Log.e("MUSIC SERVICE", "Error setting second data source", e);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();

            mediaPlayer1.release();
            mediaPlayer1 = null;

            mediaPlayer2.release();
            mediaPlayer2 = null;
        }
    }
}


