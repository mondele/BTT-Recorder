package wycliffeassociates.recordingapp.model;

import java.util.ArrayList;
import java.util.Collection;

import wycliffeassociates.recordingapp.model.AudioItem;

/**
 * Created by Butler on 7/13/2015.
 */
public class AudioMenu {
    private String folderName;
    private Collection<AudioItem> mAudioItems;

    public AudioMenu(String name){
        folderName = name;
        mAudioItems = new ArrayList<AudioItem>();

    }

    public boolean addAudioItem(AudioItem audio){
        return mAudioItems.add(audio);
    }

    public String getAudioPB(){
        return folderName;
    }

    public Collection<AudioItem> getmAudioItems(){
        return mAudioItems;
    }

}
