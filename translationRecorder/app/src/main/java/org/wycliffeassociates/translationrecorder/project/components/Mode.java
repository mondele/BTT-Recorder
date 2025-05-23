package org.wycliffeassociates.translationrecorder.project.components;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import org.wycliffeassociates.translationrecorder.chunkplugin.ChunkPlugin.TYPE;

/**
 * Created by sarabiaj on 7/5/2017.
 */

public class Mode extends ProjectComponent {

    public static final String CHUNK = "chunk";
    public static final String VERSE = "verse";

    public String getTypeString() {
        return (mType == TYPE.SINGLE)? "single" : "multi";
    }

    private TYPE mType;

    public Mode(String slug, String name, String type) {
        super(slug, name);
        mType = (new String("multi").equals(type))? TYPE.MULTI : TYPE.SINGLE;
    }

    public Mode(Parcel in) {
        super(in);
        mType = (TYPE) in.readSerializable();
    }

    public TYPE getType() {
        return mType;
    }

    @Override
    public String getLabel(Context context) {
        return mName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(mSlug);
        parcel.writeString(mName);
        parcel.writeSerializable(mType);
    }

    public static final Parcelable.Creator<Mode> CREATOR = new Parcelable.Creator<Mode>() {
        public Mode createFromParcel(Parcel in) {
            return new Mode(in);
        }

        public Mode[] newArray(int size) {
            return new Mode[size];
        }
    };
}
