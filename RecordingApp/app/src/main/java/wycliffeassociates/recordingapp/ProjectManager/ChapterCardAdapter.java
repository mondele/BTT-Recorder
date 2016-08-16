package wycliffeassociates.recordingapp.ProjectManager;

import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback;
import com.bignerdranch.android.multiselector.MultiSelector;
import com.bignerdranch.android.multiselector.SwappingHolder;

import java.util.ArrayList;
import java.util.List;

import wycliffeassociates.recordingapp.R;
import wycliffeassociates.recordingapp.widgets.ChapterCard;
import wycliffeassociates.recordingapp.widgets.FourStepImageView;

/**
 * Created by leongv on 8/15/2016.
 */
public class ChapterCardAdapter extends RecyclerView.Adapter<ChapterCardAdapter.ViewHolder> {

    private AppCompatActivity mCtx;
    private Project mProject;
    private List<ChapterCard> mChapterCardList;
    private List<Integer> mExpandedCards = new ArrayList<>();
    private List<ViewHolder> mSelectedCards = new ArrayList<>();
    private MultiSelector mMultiSelector = new MultiSelector();
    private ActionMode mActionMode;


    // Constructor
    public ChapterCardAdapter(AppCompatActivity context, Project project, List<ChapterCard> chapterCardList) {
        mCtx = context;
        mProject = project;
        mChapterCardList = chapterCardList;
    }


    private ActionMode.Callback mMultiSelectMode = new ModalMultiSelectorCallback(mMultiSelector) {
        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            mMultiSelector.clearSelections();
            mMultiSelector.setSelectable(true);
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            mCtx.getMenuInflater().inflate(R.menu.chapter_menu, menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.chapters_checking_level:
                    // NOTE: Currently only pass in placeholder text
                    CheckingDialogFragment dialog = CheckingDialogFragment.newInstance("Test");
                    dialog.show(mCtx.getFragmentManager(), "CheckingDialogFragment");
                    break;
                case R.id.chapters_compile:
                    System.out.println("Multi Compile");
                    Toast.makeText(mCtx, "Multi Compile", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    System.out.println("Default action");
                    break;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mMultiSelector.setSelectable(false);
            mMultiSelector.clearSelections();
            for (ViewHolder vh : mSelectedCards) {
                 vh.mChapterCard.drop(vh);
            }
            mSelectedCards.clear();
        }
    };


    public class ViewHolder extends SwappingHolder implements View.OnClickListener,
            View.OnLongClickListener {

        public ChapterCard mChapterCard;
        public CardView mCardView;
        public RelativeLayout mCardHeader;
        public LinearLayout mCardBody, mCardContainer, mActions;
        public TextView mTitle, mElapsed, mDuration;
        public ImageView mRecordBtn, mCompileBtn, mExpandBtn;
        public ImageButton mDeleteBtn, mPlayPauseBtn;
        public FourStepImageView mCheckLevelBtn;
        public SeekBar mSeekBar;

        public ViewHolder(View view) {
            super(view, mMultiSelector);
            // Containers
            mCardView = (CardView) view.findViewById(R.id.chapter_card);
            mCardContainer = (LinearLayout) view.findViewById(R.id.chapter_card_container);
            mCardHeader = (RelativeLayout) view.findViewById(R.id.card_header);
            mCardBody = (LinearLayout) view.findViewById(R.id.card_body);
            mActions = (LinearLayout) view.findViewById(R.id.actions);

            // Views
            mTitle = (TextView) view.findViewById(R.id.title);
            mSeekBar = (SeekBar) view.findViewById(R.id.seek_bar);
            mElapsed = (TextView) view.findViewById(R.id.time_elapsed);
            mDuration = (TextView) view.findViewById(R.id.time_duration);

            // Buttons
            mCheckLevelBtn = (FourStepImageView) view.findViewById(R.id.check_level_btn);
            mRecordBtn = (ImageView) view.findViewById(R.id.record_btn);
            mCompileBtn = (ImageView) view.findViewById(R.id.compile_btn);
            mExpandBtn = (ImageView) view.findViewById(R.id.expand_btn);
            mDeleteBtn = (ImageButton) view.findViewById(R.id.delete_chapter_audio_btn);
            mPlayPauseBtn = (ImageButton) view.findViewById(R.id.play_pause_chapter_btn);

            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
            view.setLongClickable(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setSelectionModeStateListAnimator(null);
                setDefaultModeStateListAnimator(null);
            }
            setSelectionModeBackgroundDrawable(null);
            setDefaultModeBackgroundDrawable(null);
        }

        // Called on onBindViewHolder, when the view is visible on the screen
        public void  bindViewHolder(ViewHolder holder, int position, ChapterCard chapterCard) {
            // Capture the ChapterCard object
            mChapterCard = chapterCard;
            // Set card views based on the ChapterCard object
            mTitle.setText(chapterCard.getTitle());

            holder.mCompileBtn.setActivated(mChapterCard.canCompile());
            if (mChapterCard.isCompiled()) {
                mCheckLevelBtn.setVisibility(View.VISIBLE);
                mExpandBtn.setVisibility(View.VISIBLE);
            } else {
                mCheckLevelBtn.setVisibility(View.INVISIBLE);
                mExpandBtn.setVisibility(View.INVISIBLE);
            }

            setListeners(this, mChapterCard);
            // Expand card if it's already expanded before
            if (mChapterCard.isExpanded()) {
                chapterCard.expand(holder);
            } else {
                chapterCard.collapse(holder);
            }
            // Raise card, and show appropriate visual cue, if it's already selected
            if (mMultiSelector.isSelected(position, 0)) {
                mSelectedCards.add(this);
                chapterCard.raise(holder);
            } else {
                mSelectedCards.remove(this);
                chapterCard.drop(holder);
            }
        }

        @Override
        public void onClick(View view) {
            if (mChapterCard == null || !mChapterCard.isCompiled()) {
                return;
            }

            if(mMultiSelector.isSelectable()) {
                // Close card if it is expanded in multi-select mode
                if(mChapterCard.isExpanded()){
                    toggleExpansion(this, mExpandedCards, this.getAdapterPosition());
                }

                mMultiSelector.tapSelection(this);

                // Raise/drop card
                if (mMultiSelector.isSelected(this.getAdapterPosition(), 0)) {
                    mSelectedCards.add(this);
                    mChapterCard.raise(this);
                } else {
                    mSelectedCards.remove(this);
                    mChapterCard.drop(this);
                }

                // Finish action mode if all cards are de-selected
                if (mActionMode != null && mSelectedCards.size() <= 0) {
                    mActionMode.finish();
                }
            } else {
                // Go start an activity for unit list
                System.out.println("Start an acitivty");
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (!mChapterCard.isCompiled()) {
                return false;
            }

            mActionMode = mCtx.startSupportActionMode(mMultiSelectMode);
            mMultiSelector.setSelected(this, true);

            // Close card if it is expanded on entering multi-select mode
            if(mChapterCard.isExpanded()){
                toggleExpansion(this, mExpandedCards, this.getAdapterPosition());
            }

            mSelectedCards.add(this);
            mChapterCard.raise(this);
            return true;
        }
    }


    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.chapter_card, parent, false);
        // Set the view's size, margins, padding and layout params here
        return new ViewHolder(v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ChapterCard chapterCard = mChapterCardList.get(position);
        holder.bindViewHolder(holder, position, chapterCard);
    }

    @Override
    public int getItemCount() {
        return mChapterCardList.size();
    }


    public void toggleExpansion(final ChapterCardAdapter.ViewHolder vh, final List<Integer> expandedCards, final int position) {
        if (!vh.mChapterCard.isExpanded()) {
            vh.mChapterCard.expand(vh);
            if (!expandedCards.contains(position)) {
                expandedCards.add(position);
            }
        } else {
            vh.mChapterCard.collapse(vh);
            if (expandedCards.contains(position)) {
                expandedCards.remove(expandedCards.indexOf(position));
            }
        }
    }

    // Set listeners for unit and take actions
    private void setListeners(final ViewHolder holder, final ChapterCard chapterCard) {
        holder.mCheckLevelBtn.setOnClickListener(chapterCard.getCheckLevelOnClick(holder));
        holder.mCompileBtn.setOnClickListener(chapterCard.getCompileOnClick(holder));
        holder.mRecordBtn.setOnClickListener(chapterCard.getRecordOnClick(holder));
        holder.mExpandBtn.setOnClickListener(chapterCard.getExpandOnClick(holder));
        holder.mDeleteBtn.setOnClickListener(chapterCard.getDeleteOnClick(holder));
        holder.mPlayPauseBtn.setOnClickListener(chapterCard.getPlayPauseOnClick(holder));
    }
}
