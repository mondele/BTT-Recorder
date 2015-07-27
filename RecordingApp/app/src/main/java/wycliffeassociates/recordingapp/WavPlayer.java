package wycliffeassociates.recordingapp;

import android.media.MediaPlayer;

import java.io.IOException;

/**
 * Plays .Wav audio files
 */
public class WavPlayer {

    private static MediaPlayer m;
    /**
     * Plays audio given a filename.
     * @param filename the absolute path to the file to be played.
     */
    public static void play(String filename){
        m = new MediaPlayer();

        try {
            m.setDataSource(filename);
            m.prepare();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        m.start();
    }

    public static void stop(){
        if(m != null){
            m.stop();
        }
    }
    public static boolean isPlaying(){
        if(m != null)
            return m.isPlaying();
        else
            return false;
    }

}
