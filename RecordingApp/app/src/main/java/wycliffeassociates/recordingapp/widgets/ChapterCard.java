package wycliffeassociates.recordingapp.widgets;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Toast;

import java.lang.ref.SoftReference;
import java.util.List;

import wycliffeassociates.recordingapp.ProjectManager.CheckingDialog;
import wycliffeassociates.recordingapp.ProjectManager.CompileDialog;
import wycliffeassociates.recordingapp.ProjectManager.Project;
import wycliffeassociates.recordingapp.ProjectManager.ChapterCardAdapter;
import wycliffeassociates.recordingapp.R;

/**
 * Created by leongv on 8/15/2016.
 */
public class ChapterCard {

    public interface OnClickListener extends View.OnClickListener {
        void onClick(View v, ChapterCardAdapter.ViewHolder vh, List<Integer> expandedCards, int position);
    }

    // Constants
    public int MIN_CHECKING_LEVEL = 0;
    public int MAX_CHECKING_LEVEL = 3;
    public int MIN_PROGRESS = 0;
    public int MAX_PROGRESS = 100;

    // Attributes
    private Activity mCtx;
    private Project mProject;
    private SoftReference<AudioPlayer> mAudioPlayer;
    private String mTitle = "";
    private int mCheckingLevel = 0;
    private int mProgress = 0;

    // State
    private boolean mIsEmpty = true;
    private boolean mIsCompiled = true;
    private boolean mIsExpanded = false;
    private boolean mCanCompile = true;
    private boolean mIconsClickable = true;


    // Constructor
    public ChapterCard(Activity ctx, Project proj) {
        mCtx = ctx;
        mProject = proj;
        double x = Math.random();
        if (x >= 0.6) {
            mIsEmpty = true;
            mCanCompile = false;
            mIsCompiled = false;
        } else if (x >= 0.3) {
            mIsEmpty = false;
            mCanCompile = true;
            mIsCompiled = false;
        } else {
            mIsEmpty = false;
            mCanCompile = true;
            mIsCompiled = true;
        }
    }


    // Setters
    public void setTitle(String title) {
        mTitle = title;
    }

    public void setCheckingLevel(int level) {
        if (level < MIN_CHECKING_LEVEL) {
            mCheckingLevel = MIN_CHECKING_LEVEL;
        } else if (level > MAX_CHECKING_LEVEL) {
            mCheckingLevel = MAX_CHECKING_LEVEL;
        } else {
            mCheckingLevel = level;
        }
    }

    public void setProgress(int progress) {
        if (progress < MIN_PROGRESS) {
            mProgress = MIN_PROGRESS;
        } else if (progress > MAX_PROGRESS) {
            mProgress = MAX_PROGRESS;
        } else {
            mProgress = progress;
        }
    }

    public void setIconsEnabled(ChapterCardAdapter.ViewHolder vh, boolean enabled) {
        vh.mCheckLevelBtn.setEnabled(enabled);
        vh.mCompileBtn.setEnabled(enabled);
        vh.mRecordBtn.setEnabled(enabled);
        vh.mExpandBtn.setEnabled(enabled);
    }

    public void setIconsClickable(boolean clickable) {
        mIconsClickable = clickable;
    }

    public void setIconsClickable(ChapterCardAdapter.ViewHolder vh) {
        vh.mCheckLevelBtn.setClickable(mIconsClickable);
        vh.mCompileBtn.setClickable(mIconsClickable);
        vh.mRecordBtn.setClickable(mIconsClickable);
        vh.mExpandBtn.setClickable(mIconsClickable);
    }


    // Getters
    public String getTitle() {
        return mTitle;
    }

    public int getCheckingLevel() {
        return mCheckingLevel;
    }

    public int getProgress() {
        return mProgress;
    }

    public boolean isEmpty() {
        return mIsEmpty;
    }

    public boolean isCompiled() {
        return mIsCompiled;
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    public boolean areIconsClickable() { return mIconsClickable; }


    // Public API
    public void expand(ChapterCardAdapter.ViewHolder vh) {
        // refreshAudioPlayer(vh);
        vh.mExpandBtn.setActivated(true);
        vh.mCardBody.setVisibility(View.VISIBLE);
        // vh.setIsRecyclable(false);
        mIsExpanded = true;
    }

    public void collapse(ChapterCardAdapter.ViewHolder vh) {
        vh.mCardBody.setVisibility(View.GONE);
        vh.mExpandBtn.setActivated(false);
        // vh.setIsRecyclable(true);
        mIsExpanded = false;
    }

    public void raise(ChapterCardAdapter.ViewHolder vh) {
        vh.mCardView.setCardElevation(8f);
        vh.mCardContainer.setBackgroundColor(mCtx.getResources().getColor(R.color.accent));
        vh.mTitle.setTextColor(mCtx.getResources().getColor(R.color.text_light));
        setIconsEnabled(vh, false);
        // Compile button activated status gets reset by multiSelector. This is a way to correct it.
        vh.mCompileBtn.setActivated(canCompile());
    }

    public void drop(ChapterCardAdapter.ViewHolder vh) {
        vh.mCardView.setCardElevation(2f);
        vh.mCardContainer.setBackgroundColor(mCtx.getResources().getColor(R.color.card_bg));
        vh.mTitle.setTextColor(
                mCtx.getResources().getColor(R.color.primary_text_default_material_light)
        );
        setIconsEnabled(vh, true);
        // Compile button activated status gets reset by multiSelector. This is a way to correct it.
        vh.mCompileBtn.setActivated(canCompile());
    }

    public boolean canCompile() {
        return mCanCompile;
    }

    public View.OnClickListener getCheckLevelOnClick(ChapterCardAdapter.ViewHolder vh) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // NOTE: Currently only pass in placeholder text. Replace with chapter name.
                CheckingDialog dialog = CheckingDialog.newInstance("chapter_audio_1.test");
                dialog.show(mCtx.getFragmentManager(), "single_chapter_checking_level");
            }
        };
    }

    public View.OnClickListener getCompileOnClick(ChapterCardAdapter.ViewHolder vh) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isCompiled()) {
                    CompileDialog dialog = CompileDialog.newInstance(getTitle());
                    dialog.show(mCtx.getFragmentManager(), "single_chapter_compile");
                } else {
                    System.out.println("Compile");
                    Toast.makeText(mCtx, "Compile", Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    public View.OnClickListener getRecordOnClick(ChapterCardAdapter.ViewHolder vh) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                System.out.println("Record");
                Toast.makeText(mCtx, "Record", Toast.LENGTH_SHORT).show();
            }
        };
    }

    public View.OnClickListener getExpandOnClick(final ChapterCardAdapter.ViewHolder vh) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                System.out.println("Expand");
                if (mIsExpanded) {
                    collapse(vh);
                } else {
                    expand(vh);
                }
            }
        };
    }

    public View.OnClickListener getDeleteOnClick(ChapterCardAdapter.ViewHolder vh) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                System.out.println("Delete");
                Toast.makeText(mCtx, "Delete", Toast.LENGTH_SHORT).show();
            }
        };
    }

    public View.OnClickListener getPlayPauseOnClick(ChapterCardAdapter.ViewHolder vh) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setActivated(!view.isActivated());
                System.out.println("Play/Pause");
                Toast.makeText(mCtx, "Play/Pause", Toast.LENGTH_SHORT).show();
            }
        };
    }

    public void compile() {
        // NOTE: Compile here
        System.out.println("Compile");
    }
}
