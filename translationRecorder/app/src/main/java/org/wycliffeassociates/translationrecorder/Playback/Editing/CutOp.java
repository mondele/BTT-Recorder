package org.wycliffeassociates.translationrecorder.Playback.Editing;

import android.app.ProgressDialog;

import org.wycliffeassociates.translationrecorder.AudioInfo;
import com.door43.tools.reporting.Logger;
import org.wycliffeassociates.translationrecorder.wav.WavFile;
import org.wycliffeassociates.translationrecorder.wav.WavOutputStream;

import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Vector;

import kotlin.Pair;

/**
 * Created by sarabiaj on 12/22/2015.
 */
public class CutOp {
    private Vector<Pair<Integer, Integer>> mTimeStack;
    private Vector<Pair<Integer, Integer>> mFlattenedFrameStack = new Vector<>();
    private Vector<Pair<Integer, Integer>> mUncompressedFrameStack = new Vector<>();
    private Vector<Pair<Integer, Integer>> mCompressedFrameStack = new Vector<>();
    private int mSizeTimeCut = 0;
    private int mSizeFrameCutCmp = 0;
    private int mSizeFrameCutUncmp = 0;

    private ReadPriorityLock mLock;

    public CutOp() {
        mTimeStack = new Vector<>();
        mLock = new ReadPriorityLock();
    }

    public Vector<Pair<Integer, Integer>> getFlattenedStack() {
        return mFlattenedFrameStack;
    }

    public void cut(int startFrame, int endFrame) {
        try {
            mLock.writeLock();
            Pair<Integer, Integer> temp = new Pair<>(startFrame, endFrame);
            mUncompressedFrameStack.add(temp);
            mSizeTimeCut = totalFramesRemoved();
            Logger.w(this.toString(), "Generating location stacks");
            generateTimeStack();
            generateCutStackCmpLoc();
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock the cut stack to write", e);
        } finally {
            mLock.writeUnlock();
        }
    }

    public void clear() {
        try {
            mLock.writeLock();
            mTimeStack.clear();
            mFlattenedFrameStack.clear();
            mCompressedFrameStack.clear();
            mUncompressedFrameStack.clear();
            mSizeTimeCut = 0;
            mSizeFrameCutCmp = 0;
            mSizeFrameCutUncmp = 0;
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock the cut stack to write", e);
        } finally {
            mLock.writeUnlock();
        }
    }

    public void undo() {
        try {
            mLock.readLock();
            if (mUncompressedFrameStack.isEmpty()) {
                return;
            }
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read", e);
        } finally {
            mLock.readUnlock();
        }
        try {
            mLock.writeLock();
            mUncompressedFrameStack.remove(mUncompressedFrameStack.size() - 1);
            mSizeTimeCut = totalFramesRemoved();
            generateTimeStack();
            generateCutStackCmpLoc();
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a write", e);
        } finally {
            mLock.writeUnlock();
        }
    }

    //change to use flattened stack
    public int skip(int frame) {
        int max = -1;
        try {
            mLock.readLock();
            for (int i = 0; i < mFlattenedFrameStack.size(); i++) {
                if (frame >= mFlattenedFrameStack.get(i).getFirst() && frame < mFlattenedFrameStack.get(i).getSecond()) {
                    max = Math.max(mFlattenedFrameStack.get(i).getSecond(), max);
                }
            }
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read", e);
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
        return max;
    }

    public boolean hasCut() {
        try {
            mLock.readLock();
            return !mUncompressedFrameStack.isEmpty();
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read in hasCut()", e);
            //TODO: think of better approach... needs to return but shouldn't return invalid info
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
    }

    public int skipReverse(int frame) {
        int min = Integer.MAX_VALUE;
        try {
            mLock.readLock();
            for (int i = 0; i < mFlattenedFrameStack.size(); i++) {
                if (frame > mFlattenedFrameStack.get(i).getFirst() && frame <= mFlattenedFrameStack.get(i).getSecond()) {
                    min = Math.min(mFlattenedFrameStack.get(i).getFirst(), min);
                }
            }
            return min;
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read in skipReverse()", e);
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
    }

    /**
     * Computes the total time removed from cutting, for use in ACTUAL capacity computations.
     * Also generates a private simplified and ordered version of this stack, eliminating nested
     * cuts.
     * <p/>
     * Begins by sorting the stack by starting cuts in a copied stack. Beginning with the earliest
     * cut, it is added to a list and removed from the stack. For each cut in this list (beginning
     * only with one cut) cuts are added if they start between the current cut's start and end. Each
     * cut that is added is removed from the stack. When the list has been iterated over, the min
     * and max are computed, which forms a new pair to be added to mFlattenedFrameStack. The difference
     * between min and max are added to a sum. This process is repeated until the stack is empty.
     *
     * @return the total time removed from cuts.
     */
    private int totalFramesRemoved() {
        Vector<Pair<Integer, Integer>> copy = new Vector<>(mUncompressedFrameStack.capacity());
        mFlattenedFrameStack = new Vector<>();
        for (Pair<Integer, Integer> p : mUncompressedFrameStack) {
            copy.add(new Pair<>(p.getFirst(), p.getSecond()));
        }
        Collections.sort(copy, new Comparator<>() {
            @Override
            public synchronized int compare(Pair<Integer, Integer> lhs, Pair<Integer, Integer> rhs) {
                if (Objects.equals(lhs.getFirst(), rhs.getFirst())) {
                    return 0;
                } else if (lhs.getFirst() > rhs.getFirst()) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
        int sum = 0;
        Logger.w(this.toString(), "Generating flattened stack and computing time removed");
        while (!copy.isEmpty()) {
            Pair<Integer, Integer> pair = copy.firstElement();
            ArrayList<Pair<Integer, Integer>> list = new ArrayList<>();
            list.add(pair);
            for (int i = 0; i < list.size(); i++) {
                Pair<Integer, Integer> p = list.get(i);
                copy.remove(p);
                for (int j = copy.size() - 1; j >= 0; j--) {
                    Pair<Integer, Integer> q = copy.get(j);
                    if ((q.getFirst() >= p.getFirst() && q.getFirst() <= p.getSecond()) || (p.getFirst() >= q.getFirst() && p.getFirst() <= q.getSecond())) {
                        list.add(q);
                        copy.remove(q);
                    }
                }
            }
            int start = list.get(0).getFirst();
            int end = list.get(0).getSecond();
            for (int i = 1; i < list.size(); i++) {
                end = (end < list.get(i).getSecond()) ? list.get(i).getSecond() : end;
            }
            mFlattenedFrameStack.add(new Pair<>(start, end));
            sum += end - start;
        }
        mSizeFrameCutUncmp = sum;
        return sum;
    }

    //NOT THREAD SAFE make sure this function is called under a write lock
    private void generateTimeStack() {
        mSizeTimeCut = 0;
        mTimeStack = new Vector<>();
        for (Pair<Integer, Integer> p : mFlattenedFrameStack) {
            Pair<Integer, Integer> y = new Pair<>(uncompressedFrameToTime(p.getFirst()), uncompressedFrameToTime(p.getSecond()));
            mTimeStack.add(y);
            mSizeTimeCut += y.getSecond() - y.getFirst();
        }
    }

    //NOT THREAD SAFE make sure this function is called under a write lock
    private void generateCutStackUncmpLoc() {
        mSizeFrameCutUncmp = 0;
        mUncompressedFrameStack = new Vector<Pair<Integer, Integer>>();
        for (Pair<Integer, Integer> p : mFlattenedFrameStack) {
            Pair<Integer, Integer> y = new Pair<>((p.getFirst()), (p.getSecond()));
            mUncompressedFrameStack.add(y);
            mSizeFrameCutUncmp += y.getSecond() - y.getFirst();
        }
    }

    //NOT THREAD SAFE make sure this function is called under a write lock
    private void generateCutStackCmpLoc() {
        mSizeFrameCutCmp = 0;
        mCompressedFrameStack = new Vector<>();
        for (Pair<Integer, Integer> p : mFlattenedFrameStack) {
            Pair<Integer, Integer> y = new Pair<>(uncompressedToCompressed(p.getFirst()), uncompressedToCompressed(p.getSecond()));
            mCompressedFrameStack.add(y);
            mSizeFrameCutCmp += y.getSecond() - y.getFirst();
        }
    }

    public static int timeToUncmpLoc(int timeMs) {
        int seconds = timeMs / 1000;
        int ms = (timeMs - (seconds * 1000));
        int tens = ms / 10;
        return (AudioInfo.SAMPLERATE * seconds) + (ms * 44) + (tens);
    }

    public static int timeToCmpLoc(int timeMs) {
        int seconds = timeMs / 1000;
        int ms = (timeMs - (seconds * 1000));
        int tens = ms / 10;
        int idx = (AudioInfo.SAMPLERATE * seconds) + (ms * 44) + (tens);
        idx /= 25;
        return idx;
    }

    public static int uncompressedFrameToTime(int frame) {
        return (int) Math.floor(frame / 44.1);
    }

    public static int uncompressedToCompressed(int frame) {
        return (int)Math.round(frame / 25.0);
    }

    public int skipFrame(int frame, boolean compressed) {
        try {
            int max = -1;
            mLock.readLock();
            Vector<Pair<Integer, Integer>> stack = (compressed) ? mCompressedFrameStack : mFlattenedFrameStack;
            for (int i = 0; i < stack.size(); i++) {
                if (frame >= stack.get(i).getFirst() && frame < stack.get(i).getSecond()) {
                    max = Math.max(stack.get(i).getSecond(), max);
                }
            }
            return max;
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read in skipFrame()", e);
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
    }

    public int relativeLocToAbsolute(int frame, boolean compressed) {
        try {
            mLock.readLock();
            Vector<Pair<Integer, Integer>> stack = (compressed) ? mCompressedFrameStack : mFlattenedFrameStack;
            if (stack == null) {
                return frame;
            }
            for (int i = 0; i < stack.size(); i++) {
                if (frame >= stack.get(i).getFirst()) {
                    frame += stack.get(i).getSecond() - stack.get(i).getFirst();
                }
            }
            return frame;
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read in relativeLocToAbsolute()", e);
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
    }

    public boolean cutExistsInRange(int frame, int range) {
        if (hasCut()) {
            try {
                mLock.readLock();
                for (int i = 0; i < mFlattenedFrameStack.size(); i++) {
                    //if the frame is in the middle of a cut
                    if (frame >= mFlattenedFrameStack.get(i).getFirst() && frame <= mFlattenedFrameStack.get(i).getSecond()) {
                        return true;
                        //if the cut is between the frame and the end of the range
                    } else if (frame < mFlattenedFrameStack.get(i).getFirst() && (frame + range) >= mFlattenedFrameStack.get(i).getSecond()) {
                        return true;
                        //if the frame begins before the first cut, and ends after
                    } else if (frame < mFlattenedFrameStack.get(i).getFirst() && (frame + range) > mFlattenedFrameStack.get(i).getFirst()) {
                        return true;
                    }
                }
            } catch (InterruptedException e) {
                Logger.e(this.toString(), "Error trying to lock for a read in cutExistsInRange()", e);
                throw new RuntimeException(e);
            } finally {
                mLock.readUnlock();
            }
        }
        //otherwise no cut
        return false;
    }


    public int absoluteLocToRelative(int frame, boolean compressed) {
        int loc = frame;
        try {
            mLock.readLock();
            Vector<Pair<Integer, Integer>> stack = (compressed) ? mCompressedFrameStack : mFlattenedFrameStack;
            if (stack == null) {
                return loc;
            }
            for (int i = stack.size() - 1; i >= 0; i--) {
                if (frame >= stack.get(i).getSecond()) {
                    loc -= stack.get(i).getSecond() - stack.get(i).getFirst();
                }
            }
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read in absoluteLocToRelative()", e);
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
        return loc;
    }


    public int getSizeTimeCut() {
        try {
            mLock.readLock();
            return mSizeTimeCut;
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read in getSizeTimeCut()", e);
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
    }

    public int getSizeFrameCutCmp() {
        try {
            mLock.readLock();
            return mSizeFrameCutCmp;
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read in getSizeFrameCutCmp()", e);
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
    }

    public int getSizeFrameCutUncmp() {
        try {
            mLock.readLock();
            return mSizeFrameCutUncmp;
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read in getSizeFrameCutUncmp()", e);
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
    }

    public void writeCut(WavFile to, final ShortBuffer buffer, ProgressDialog pd) throws IOException {
        try {
            mLock.readLock();
            Logger.w(this.toString(), "Rewriting file to disk due to cuts");
            pd.setProgress(0);
            int audioLength = buffer.capacity();
            try (WavOutputStream bos = new WavOutputStream(to, WavOutputStream.BUFFERED)) {

                int percent = (int) Math.round((audioLength) / 100.0);
                int count = percent;
                for (int i = 0; i < audioLength; i++) {
                    int skip = skipFrame(i, false);
                    if (skip != -1) {
                        i = skip;
                        if (i >= audioLength) {
                            break;
                        }
                    }
                    short sample = buffer.get(i);
                    byte hi = (byte) (((sample >> 8) & 0xFF));
                    byte lo = (byte) (sample & 0xFF);
                    bos.write(lo);
                    bos.write(hi);
                    if (count <= 0) {
                        pd.incrementProgressBy(1);
                        count = percent;
                    }
                    count--;
                }
            }
        } catch (InterruptedException e) {
            Logger.e(this.toString(), "Error trying to lock for a read in writeCut()", e);
            throw new RuntimeException(e);
        } finally {
            mLock.readUnlock();
        }
    }
}