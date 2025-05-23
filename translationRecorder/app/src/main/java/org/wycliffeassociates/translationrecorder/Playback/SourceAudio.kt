package org.wycliffeassociates.translationrecorder.Playback

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.door43.tools.reporting.Logger
import org.wycliffeassociates.io.ArchiveOfHolding
import org.wycliffeassociates.io.ArchiveOfHoldingEntry
import org.wycliffeassociates.io.ChapterVerseSection
import org.wycliffeassociates.io.LanguageLevel
import org.wycliffeassociates.translationrecorder.R
import org.wycliffeassociates.translationrecorder.SettingsPage.SettingsActivity
import org.wycliffeassociates.translationrecorder.databinding.SourceAudioBinding
import org.wycliffeassociates.translationrecorder.persistance.IDirectoryProvider
import org.wycliffeassociates.translationrecorder.persistance.IPreferenceRepository
import org.wycliffeassociates.translationrecorder.persistance.getDefaultPref
import org.wycliffeassociates.translationrecorder.project.Project
import org.wycliffeassociates.translationrecorder.project.ProjectFileUtils.chapterIntToString
import org.wycliffeassociates.translationrecorder.widgets.AudioPlayer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Created by sarabiaj on 4/13/2016.
 */
class SourceAudio @JvmOverloads constructor (
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private var mSrcPlayer: AudioPlayer? = null

    private lateinit var mProject: Project
    private lateinit var mFileName: String

    private var mChapter = 0
    private var mTemp: File? = null
    private var sourceAudioListener: OnAudioListener? = null

    interface OnAudioListener {
        fun onSourcePlay()
        fun onSourcePause()
    }

    private var _binding: SourceAudioBinding? = null
    private val binding get() = _binding!!

    init {
        _binding = SourceAudioBinding.inflate(LayoutInflater.from(context), this, true)

        if (!isInEditMode) {
            mSrcPlayer = AudioPlayer(
                binding.timeProgress,
                binding.timeDuration,
                binding.playButton,
                binding.seekBar
            )
        }

        binding.playButton.setOnClickListener {
            if (binding.playButton.isActivated) {
                pauseSource()
            } else {
                playSource()
            }
        }
    }

    private fun getFileFromString(sourceLanguage: String?, sourceLocation: String?): File? {
        if (sourceLocation.isNullOrEmpty() || sourceLanguage.isNullOrEmpty()) {
            return null
        }
        val file = File(sourceLocation)
        if (file.exists()) {
            return file
        }
        return null
    }

    private fun getExtensionIfValid(filename: String): String? {
        var extension: String? = null
        for (i in filetypes.indices) {
            if (filename.contains(filetypes[i])) {
                extension = filetypes[i]
                break
            }
        }
        return extension
    }

    @Throws(IOException::class, FileNotFoundException::class)
    private fun getAudioFromFile(
        sourceLanguage: String?,
        file: File,
        directoryProvider: IDirectoryProvider
    ): Boolean {
        try {
            file.inputStream().use { inputStream ->
                val ll = LanguageLevel()
                val aoh = ArchiveOfHolding(inputStream, ll)
                // The archive of holding entry requires the path to look for the file,
                // so that part of the name can be ignored
                // chapter and verse information is all that is necessary
                // to be identifiable at this point.
                val chapterVerseSection = ChapterVerseSection(mFileName)

                val entries = arrayListOf<ArchiveOfHoldingEntry>()

                // Add default entry if found
                aoh.getEntry(
                    chapterVerseSection,
                    sourceLanguage,
                    ll.getVersionSlug(sourceLanguage),
                    mProject.bookSlug,
                    chapterIntToString(mProject, mChapter)
                )?.let(entries::add)

                if (entries.isEmpty()) {
                    // Search for entries based on verses
                    try {
                        val chapter = chapterVerseSection.chapter
                        val firstVerse = chapterVerseSection.firstVerse.toInt()
                        val lastVerse = chapterVerseSection.lastVerse.toInt()

                        if (firstVerse > 0 && lastVerse > 0) {
                            for (verse in firstVerse..lastVerse) {
                                aoh.getEntry(
                                    ChapterVerseSection(chapter, verse.toString(), null),
                                    sourceLanguage,
                                    ll.getVersionSlug(sourceLanguage),
                                    mProject.bookSlug,
                                    chapterIntToString(mProject, mChapter)
                                )?.let { entry ->
                                    entries.add(entry)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("SourceAudio", "Error getting values from chapterVerseSection", e)
                    }
                }

                if (entries.isEmpty()) {
                    return false
                }

                val extension = getExtensionIfValid(entries.first().name)
                val tempFiles = arrayListOf<File>()

                entries.forEachIndexed { index, entry ->
                    val tempFile = File(
                        directoryProvider.externalCacheDir,
                        "temp${System.currentTimeMillis()}.$extension"
                    )
                    // if there are multiple entries, then skip to position only for the first entry
                    // consecutive entries will be skipped automatically when reading
                    val skip = if (index == 0) entry.start else 0
                    entry.getInputStream(skip).use { file ->
                        BufferedInputStream(file).use { bis ->
                            FileOutputStream(tempFile).use { fos ->
                                BufferedOutputStream(fos).use { bos ->
                                    val buffer = ByteArray(1024)
                                    var len: Int
                                    var totalRead = 0
                                    while ((bis.read(buffer).also { len = it }) != -1) {
                                        bos.write(buffer, 0, len)
                                        totalRead += len
                                    }
                                }
                            }
                        }
                    }
                    tempFiles.add(tempFile)
                }

                mTemp?.delete()

                // We don't need to merge/convert if there is only one entry
                if (tempFiles.size == 1) {
                    val first = tempFiles.first()
                    val renamed = File(first.parentFile, "temp.$extension")
                    first.renameTo(renamed)
                    mTemp = renamed
                } else {
                    mTemp = File(directoryProvider.externalCacheDir, "temp.mp3")
                    if (!AudioMerger.merge(tempFiles, mTemp!!)) {
                        mTemp?.delete()
                        mTemp = null
                    }
                    tempFiles.forEach(File::delete)
                }
            }
            return true
        } catch (e: Exception) {
            Logger.e("SourceAudio", "Error loading source audio from file", e)
        }
        return false
    }

    @Throws(IOException::class)
    private fun loadAudioFile(prefs: IPreferenceRepository, directoryProvider: IDirectoryProvider) {
        val projectSourceLocation = mProject.sourceAudioPath
        val projectSourceLanguage = mProject.sourceLanguageSlug
        val globalSourceLocation = prefs.getDefaultPref(SettingsActivity.KEY_PREF_GLOBAL_SOURCE_LOC, "")
        val globalSourceLanguage = prefs.getDefaultPref(SettingsActivity.KEY_PREF_GLOBAL_LANG_SRC, "")

        val projectFile = getFileFromString(projectSourceLanguage, projectSourceLocation)
        val globalFile = getFileFromString(globalSourceLanguage, globalSourceLocation)

        var gotFile = false
        if (projectFile != null) {
            gotFile = getAudioFromFile(projectSourceLanguage, projectFile, directoryProvider)
        }
        if (!gotFile && globalFile != null) {
            getAudioFromFile(globalSourceLanguage, globalFile, directoryProvider)
        }
    }

    fun initSrcAudio(
        project: Project,
        fileName: String,
        chapter: Int,
        directoryProvider: IDirectoryProvider,
        prefs: IPreferenceRepository
    ) {
        if (mTemp?.exists() == true) {
            mTemp?.delete()
            mTemp = null
        }
        mProject = project
        mFileName = fileName
        mChapter = chapter
        try {
            loadAudioFile(prefs, directoryProvider)
        } catch (e: IOException) {
            e.printStackTrace()
            Logger.e(this.toString(), "ERROR, IOException with source audio loading", e)
            mTemp = null
        }
        if (mTemp == null || mTemp?.exists() == false) {
            showNoSource(true)
            return
        }
        showNoSource(false)
        mTemp?.let { mSrcPlayer?.loadFile(it) }
    }

    fun playSource() {
        mSrcPlayer?.play()
        sourceAudioListener?.onSourcePlay()
    }

    fun pauseSource() {
        mSrcPlayer?.pause()
        sourceAudioListener?.onSourcePause()
    }

    @SuppressLint("SetTextI18n")
    fun reset(
        project: Project,
        fileName: String,
        chapter: Int,
        directoryProvider: IDirectoryProvider,
        prefs: IPreferenceRepository
    ) {
        mSrcPlayer?.reset()
        binding.seekBar.progress = 0
        binding.timeProgress.text = "00:00:00"
        binding.timeProgress.invalidate()

        initSrcAudio(
            project,
            fileName,
            chapter,
            directoryProvider,
            prefs
        )
    }

    fun cleanup() {
        mSrcPlayer?.cleanup()
    }

    override fun setEnabled(enable: Boolean) {
        binding.seekBar.isEnabled = enable
        binding.playButton.isEnabled = enable
        if (enable) {
            binding.timeProgress.setTextColor(resources.getColor(R.color.text_light_disabled))
            binding.timeDuration.setTextColor(resources.getColor(R.color.text_light_disabled))
        } else {
            binding.timeProgress.setTextColor(resources.getColor(R.color.text_light))
            binding.timeDuration.setTextColor(resources.getColor(R.color.text_light))
        }
    }

    private fun showNoSource(noSource: Boolean) {
        if (noSource) {
            binding.seekBar.visibility = GONE
            binding.timeProgress.visibility = GONE
            binding.timeDuration.visibility = GONE
            binding.noSourceMsg.visibility = VISIBLE
            isEnabled = false
        } else {
            binding.seekBar.visibility = VISIBLE
            binding.timeProgress.visibility = VISIBLE
            binding.timeDuration.visibility = VISIBLE
            binding.noSourceMsg.visibility = GONE
            isEnabled = true
        }
    }

    fun setSourceAudioListener(listener: OnAudioListener?) {
        sourceAudioListener = listener
    }

    companion object {
        private val filetypes = arrayOf("wav", "mp3", "mp4", "m4a", "aac", "flac", "3gp", "ogg")
    }
}
