package org.wycliffeassociates.translationrecorder.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDoneException
import android.database.sqlite.SQLiteOpenHelper
import com.door43.tools.reporting.Logger
import org.wycliffeassociates.translationrecorder.ProjectManager.tasks.resync.ProjectListResyncTask
import org.wycliffeassociates.translationrecorder.project.Project
import org.wycliffeassociates.translationrecorder.project.ProjectFileUtils
import org.wycliffeassociates.translationrecorder.project.ProjectPatternMatcher
import org.wycliffeassociates.translationrecorder.project.TakeInfo
import org.wycliffeassociates.translationrecorder.project.components.Anthology
import org.wycliffeassociates.translationrecorder.project.components.Book
import org.wycliffeassociates.translationrecorder.project.components.Language
import org.wycliffeassociates.translationrecorder.project.components.Mode
import org.wycliffeassociates.translationrecorder.project.components.User
import org.wycliffeassociates.translationrecorder.project.components.Version
import org.wycliffeassociates.translationrecorder.wav.WavFile
import java.io.File
import org.wycliffeassociates.translationrecorder.database.IProjectDatabaseHelper.OnLanguageNotFound
import org.wycliffeassociates.translationrecorder.database.IProjectDatabaseHelper.OnCorruptFile
import org.wycliffeassociates.translationrecorder.persistance.IDirectoryProvider

/**
 * Created by sarabiaj on 5/10/2016.
 */
class ProjectDatabaseHelper(
    private val context: Context,
    private val directoryProvider: IDirectoryProvider
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), IProjectDatabaseHelper {

    override val languages: List<Language>
        get() {
            val languageList: MutableList<Language> = arrayListOf()
            val query = "SELECT * FROM " + ProjectContract.LanguageEntry.TABLE_LANGUAGE
            val db = readableDatabase
            db.beginTransaction()
            db.rawQuery(query, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val languageCodeIndex = cursor.getColumnIndex(ProjectContract.LanguageEntry.LANGUAGE_CODE)
                        val languageCode = cursor.getString(languageCodeIndex)

                        val languageNameIndex = cursor.getColumnIndex(ProjectContract.LanguageEntry.LANGUAGE_NAME)
                        val languageName = cursor.getString(languageNameIndex)

                        val language = Language(languageCode, languageName)
                        languageList.add(language)
                    } while (cursor.moveToNext())
                }
            }
            db.endTransaction()
            return languageList
        }

    override val allUsers: List<User>
        get() {
            val userList: MutableList<User> = arrayListOf()
            val query = "SELECT * FROM " + ProjectContract.UserEntry.TABLE_USER
            val db = readableDatabase
            db.rawQuery(query, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val idIndex = cursor.getColumnIndex(ProjectContract.UserEntry._ID)
                        val id = cursor.getInt(idIndex)

                        val audioIndex = cursor.getColumnIndex(ProjectContract.UserEntry.USER_AUDIO)
                        val audio = File(cursor.getString(audioIndex))

                        val hashIndex = cursor.getColumnIndex(ProjectContract.UserEntry.USER_HASH)
                        val hash = cursor.getString(hashIndex)

                        val user = User(audio, hash, id)
                        userList.add(user)
                    } while (cursor.moveToNext())
                }
            }
            return userList
        }

    override val projectPatternMatchers: List<ProjectPatternMatcher>
        get() {
            val db = readableDatabase
            val query = String.format(
                "SELECT * FROM %s",
                ProjectContract.AnthologyEntry.TABLE_ANTHOLOGY
            )
            val patterns: MutableList<ProjectPatternMatcher> = arrayListOf()
            db.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val regexIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_REGEX)
                    val regex = cursor.getString(regexIndex)

                    val groupsIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_GROUPS)
                    val groups = cursor.getString(groupsIndex)

                    patterns.add(ProjectPatternMatcher(regex, groups))
                }
            }
            return patterns
        }

    override val allProjects: List<Project>
        get() {
            val projectList: MutableList<Project> = arrayListOf()
            val query = "SELECT * FROM " + ProjectContract.ProjectEntry.TABLE_PROJECT
            val db = readableDatabase
            db.rawQuery(query, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val versionIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_VERSION_FK)
                        val targetLanguageIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_TARGET_LANGUAGE_FK)
                        val sourceLanguageIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_SOURCE_LANGUAGE_FK)
                        val sourceAudioIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_SOURCE_AUDIO_PATH)
                        val modeIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_MODE_FK)
                        val bookIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_BOOK_FK)
                        val contributorsIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_CONTRIBUTORS)

                        try {
                            val version = getVersion(cursor.getInt(versionIndex))
                            val targetLanguage = getLanguage(cursor.getInt(targetLanguageIndex))
                            //Source language could be null
                            var sourceLanguage: Language? = null
                            var sourceAudioPath: String? = null
                            if (cursor.getType(sourceLanguageIndex) == Cursor.FIELD_TYPE_INTEGER) {
                                sourceLanguage = getLanguage(cursor.getInt(sourceLanguageIndex))
                                sourceAudioPath = cursor.getString(sourceAudioIndex)
                            }
                            val mode = getMode(cursor.getInt(modeIndex))
                            val book = getBook(cursor.getInt(bookIndex))
                            val anthology = getAnthology(getAnthologyId(book.anthology))
                            val contributors = cursor.getString(contributorsIndex)

                            val project = Project(
                                targetLanguage,
                                anthology,
                                book,
                                version,
                                mode,
                                contributors,
                                sourceLanguage,
                                sourceAudioPath
                            )

                            projectList.add(project)
                        } catch (e: Exception) {
                            val projectDetails = "languageId: $targetLanguageIndex, versionId: $versionIndex, bookId: $bookIndex"
                            Logger.e(this::javaClass.name, "Error adding project $projectDetails to project list", e)
                        }
                    } while (cursor.moveToNext())
                }
            }
            return projectList
        }

    override val numProjects: Int
        get() {
            val db = readableDatabase
            val countQuery = "SELECT * FROM " + ProjectContract.ProjectEntry.TABLE_PROJECT
            var count: Int
            db.rawQuery(countQuery, null).use { cursor ->
                count = cursor.count
            }
            return count
        }

    override val anthologies: List<Anthology>
        get() {
            val anthologyList: MutableList<Anthology> = ArrayList()
            val query =
                "SELECT * FROM " + ProjectContract.AnthologyEntry.TABLE_ANTHOLOGY +
                        " ORDER BY " + ProjectContract.AnthologyEntry.ANTHOLOGY_SORT + " ASC"
            val db = readableDatabase
            db.beginTransaction()
            db.rawQuery(query, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val anthologySlugIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_SLUG)
                        val anthologySlug = cursor.getString(anthologySlugIndex)

                        val anthologyNameIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_NAME)
                        val anthologyName = cursor.getString(anthologyNameIndex)

                        val resourceIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_RESOURCE)
                        val resource = cursor.getString(resourceIndex)

                        val sortIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_SORT)
                        val sort = cursor.getInt(sortIndex)

                        val regexIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_REGEX)
                        val regex = cursor.getString(regexIndex)

                        val groupsIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_GROUPS)
                        val groups = cursor.getString(groupsIndex)

                        val maskIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_MASK)
                        val mask = cursor.getString(maskIndex)

                        val jarNameIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.PLUGIN_JAR)
                        val jarName = cursor.getString(jarNameIndex)

                        val classNameIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.PLUGIN_CLASS)
                        val className = cursor.getString(classNameIndex)

                        val anthology = Anthology(
                            anthologySlug,
                            anthologyName,
                            resource,
                            sort,
                            regex,
                            groups,
                            mask,
                            jarName,
                            className
                        )
                        anthologyList.add(anthology)
                    } while (cursor.moveToNext())
                }
            }
            db.endTransaction()
            return anthologyList
        }

    override fun updateSourceAudio(projectId: Int, projectContainingUpdatedSource: Project) {
        val sourceLanguageId = getLanguageId(projectContainingUpdatedSource.sourceLanguageSlug)
        val replaceTakeWhere = String.format("%s=?", ProjectContract.ProjectEntry._ID)
        val db = writableDatabase
        val replaceWith = ContentValues()
        replaceWith.put(
            ProjectContract.ProjectEntry.PROJECT_SOURCE_LANGUAGE_FK,
            sourceLanguageId.toString()
        )
        replaceWith.put(
            ProjectContract.ProjectEntry.PROJECT_SOURCE_AUDIO_PATH,
            projectContainingUpdatedSource.sourceAudioPath
        )
        db.update(
            ProjectContract.ProjectEntry.TABLE_PROJECT,
            replaceWith,
            replaceTakeWhere,
            arrayOf(projectId.toString())
        )
    }

    override fun projectsNeedingResync(allProjects: Set<Project>): List<Project> {
        val needingResync: MutableList<Project> = ArrayList()
        for (p in allProjects) {
            if (!projectExists(p)) {
                needingResync.add(p)
            }
        }
        return needingResync
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(ProjectContract.LanguageEntry.CREATE_LANGUAGE_TABLE)
        db.execSQL(ProjectContract.BookEntry.CREATE_BOOK_TABLE)
        db.execSQL(ProjectContract.ProjectEntry.CREATE_PROJECT_TABLE)
        db.execSQL(ProjectContract.ChapterEntry.CREATE_CHAPTER_TABLE)
        db.execSQL(ProjectContract.UnitEntry.CREATE_UNIT_TABLE)
        db.execSQL(ProjectContract.TakeEntry.CREATE_TAKE_TABLE)
        db.execSQL(ProjectContract.AnthologyEntry.CREATE_ANTHOLOGY_TABLE)
        db.execSQL(ProjectContract.ModeEntry.CREATE_MODE_TABLE)
        db.execSQL(ProjectContract.VersionEntry.CREATE_VERSION_TABLE)
        db.execSQL(ProjectContract.VersionRelationshipEntry.CREATE_VERSION_RELATIONSHIP_TABLE)
        db.execSQL(ProjectContract.UserEntry.CREATE_USER_TABLE)
        //db.execSQL(ProjectContract.ModeRelationshipEntry.CREATE_MODE_RELATIONSHIP_TABLE);
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(ProjectContract.DELETE_LANGUAGE)
        db.execSQL(ProjectContract.DELETE_BOOKS)
        db.execSQL(ProjectContract.DELETE_PROJECTS)
        db.execSQL(ProjectContract.DELETE_CHAPTERS)
        db.execSQL(ProjectContract.DELETE_UNITS)
        db.execSQL(ProjectContract.DELETE_TAKES)
        db.execSQL(ProjectContract.DELETE_ANTHOLOGIES)
        db.execSQL(ProjectContract.DELETE_VERSIONS)
        db.execSQL(ProjectContract.DELETE_MODES)
        db.execSQL(ProjectContract.DELETE_VERSION_RELATIONSHIPS)
        db.execSQL(ProjectContract.DELETE_USERS)
        //db.execSQL(ProjectContract.DELETE_MODE_RELATIONSHIPS);
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    override fun deleteAllTables() {
        val db = writableDatabase
        db.execSQL(ProjectContract.DELETE_LANGUAGE)
        db.execSQL(ProjectContract.DELETE_BOOKS)
        db.execSQL(ProjectContract.DELETE_PROJECTS)
        db.execSQL(ProjectContract.DELETE_CHAPTERS)
        db.execSQL(ProjectContract.DELETE_UNITS)
        db.execSQL(ProjectContract.DELETE_TAKES)
        db.execSQL(ProjectContract.DELETE_ANTHOLOGIES)
        db.execSQL(ProjectContract.DELETE_VERSIONS)
        db.execSQL(ProjectContract.DELETE_MODES)
        db.execSQL(ProjectContract.DELETE_VERSION_RELATIONSHIPS)
        db.execSQL(ProjectContract.DELETE_USERS)
        //db.execSQL(ProjectContract.DELETE_MODE_RELATIONSHIPS);
        onCreate(db)
    }

    override fun languageExists(languageSlug: String): Boolean {
        val db = readableDatabase
        val languageCountQuery = "SELECT COUNT(*) FROM " + ProjectContract.LanguageEntry.TABLE_LANGUAGE +
                " WHERE " + ProjectContract.LanguageEntry.LANGUAGE_CODE + "=?"
        val exists = (DatabaseUtils.longForQuery(db, languageCountQuery, arrayOf(languageSlug))) > 0
        return exists
    }

    override fun bookExists(bookSlug: String): Boolean {
        val db = readableDatabase
        val bookCountQuery = "SELECT COUNT(*) FROM " + ProjectContract.BookEntry.TABLE_BOOK +
                " WHERE " + ProjectContract.BookEntry.BOOK_SLUG + "=?"
        val exists = (DatabaseUtils.longForQuery(db, bookCountQuery, arrayOf(bookSlug))) > 0
        return exists
    }

    override fun projectExists(project: Project): Boolean {
        return projectExists(project.targetLanguageSlug, project.bookSlug, project.versionSlug)
    }

    override fun projectExists(languageSlug: String, bookSlug: String, versionSlug: String): Boolean {
        if (!languageExists(languageSlug)) {
            return false
        }
        val languageId = getLanguageId(languageSlug)
        val bookId = getBookId(bookSlug)
        val versionId = getVersionId(versionSlug)
        val db = readableDatabase
        val projectCountQuery = ("SELECT COUNT(*) FROM " + ProjectContract.ProjectEntry.TABLE_PROJECT +
                " WHERE " + ProjectContract.ProjectEntry.PROJECT_TARGET_LANGUAGE_FK + "=?" +
                " AND " + ProjectContract.ProjectEntry.PROJECT_BOOK_FK + "=? " +
                "AND " + ProjectContract.ProjectEntry.PROJECT_VERSION_FK + "=?")
        val exists = (DatabaseUtils.longForQuery(
            db,
            projectCountQuery,
            arrayOf(languageId.toString(), bookId.toString(), versionId.toString())
        )) > 0
        return exists
    }

    override fun chapterExists(project: Project, chapter: Int): Boolean {
        return chapterExists(
            project.targetLanguageSlug,
            project.bookSlug,
            project.versionSlug,
            chapter
        )
    }

    override fun chapterExists(
        languageSlug: String,
        bookSlug: String,
        versionSlug: String,
        chapter: Int
    ): Boolean {
        val projectId = getProjectId(languageSlug, bookSlug, versionSlug).toString()
        val db = readableDatabase
        val chapterCountQuery = String.format(
            "SELECT COUNT(*) FROM %s WHERE %s=? AND %s=?",
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            ProjectContract.ChapterEntry.CHAPTER_PROJECT_FK,
            ProjectContract.ChapterEntry.CHAPTER_NUMBER
        )
        val exists = (DatabaseUtils.longForQuery(
            db,
            chapterCountQuery,
            arrayOf(projectId, chapter.toString())
        )) > 0
        return exists
    }

    override fun unitExists(project: Project, chapter: Int, startVerse: Int): Boolean {
        return unitExists(
            project.targetLanguageSlug,
            project.bookSlug,
            project.versionSlug,
            chapter,
            startVerse
        )
    }

    override fun unitExists(
        languageSlug: String,
        bookSlug: String,
        versionSlug: String,
        chapter: Int,
        startVerse: Int
    ): Boolean {
        val projectId = getProjectId(languageSlug, bookSlug, versionSlug).toString()
        val chapterId = getChapterId(languageSlug, bookSlug, versionSlug, chapter).toString()
        val db = readableDatabase
        val unitCountQuery = String.format(
            "SELECT COUNT(*) FROM %s WHERE %s=? AND %s=? AND %s=?",
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.UnitEntry.UNIT_PROJECT_FK,
            ProjectContract.UnitEntry.UNIT_CHAPTER_FK,
            ProjectContract.UnitEntry.UNIT_START_VERSE
        )
        val exists = (DatabaseUtils.longForQuery(
            db,
            unitCountQuery,
            arrayOf(projectId, chapterId, startVerse.toString())
        )) > 0
        return exists
    }

    override fun takeExists(project: Project, chapter: Int, startVerse: Int, take: Int): Boolean {
        val unitId = getUnitId(project, chapter, startVerse).toString()
        val db = readableDatabase
        val takeCountQuery = String.format(
            "SELECT COUNT(*) FROM %s WHERE %s=? AND %s=?",
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.TakeEntry.TAKE_NUMBER
        )
        val exists =
            (DatabaseUtils.longForQuery(db, takeCountQuery, arrayOf(unitId, take.toString()))) > 0
        return exists
    }

    override fun takeExists(takeInfo: TakeInfo): Boolean {
        val slugs = takeInfo.projectSlugs
        val unitId = getUnitId(
            slugs.language,
            slugs.book,
            slugs.version,
            takeInfo.chapter,
            takeInfo.startVerse
        ).toString()
        val db = readableDatabase
        val takeCountQuery = String.format(
            "SELECT COUNT(*) FROM %s WHERE %s=? AND %s=?",
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.TakeEntry.TAKE_NUMBER
        )
        val exists = (DatabaseUtils.longForQuery(
            db,
            takeCountQuery,
            arrayOf(unitId, takeInfo.take.toString())
        )) > 0
        return exists
    }

    @Throws(IllegalArgumentException::class)
    override fun getLanguageId(languageSlug: String): Int {
        val db = readableDatabase
        val languageIdQuery = "SELECT " + ProjectContract.LanguageEntry._ID +
                " FROM " + ProjectContract.LanguageEntry.TABLE_LANGUAGE +
                " WHERE " + ProjectContract.LanguageEntry.LANGUAGE_CODE + "=?"
        return try {
            DatabaseUtils.longForQuery(db, languageIdQuery, arrayOf(languageSlug)).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Language slug: $languageSlug is not in the database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getVersionId(versionSlug: String): Int {
        val db = readableDatabase
        val versionIdQuery =
            "SELECT " + ProjectContract.VersionEntry._ID +
                    " FROM " + ProjectContract.VersionEntry.TABLE_VERSION +
                    " WHERE " + ProjectContract.VersionEntry.VERSION_SLUG + "=?"
        return try {
            DatabaseUtils.longForQuery(db, versionIdQuery, arrayOf(versionSlug)).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Version slug: $versionSlug is not in the database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getAnthologyId(anthologySlug: String): Int {
        val db = readableDatabase
        val anthologyIdQuery =
            "SELECT " + ProjectContract.AnthologyEntry._ID +
                    " FROM " + ProjectContract.AnthologyEntry.TABLE_ANTHOLOGY +
                    " WHERE " + ProjectContract.AnthologyEntry.ANTHOLOGY_SLUG + "=?"
        return try {
            DatabaseUtils.longForQuery(db, anthologyIdQuery, arrayOf(anthologySlug)).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Anthology slug: $anthologySlug is not in the database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getBookId(bookSlug: String): Int {
        val db = readableDatabase
        val bookIdQuery =
            "SELECT " + ProjectContract.BookEntry._ID +
                    " FROM " + ProjectContract.BookEntry.TABLE_BOOK +
                    " WHERE " + ProjectContract.BookEntry.BOOK_SLUG + "=?"
        return try {
            DatabaseUtils.longForQuery(db, bookIdQuery, arrayOf(bookSlug)).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Book slug: $bookSlug is not in the database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getProjectId(project: Project): Int {
        return getProjectId(project.targetLanguageSlug, project.bookSlug, project.versionSlug)
    }

    @Throws(IllegalArgumentException::class)
    override fun getProjectId(languageSlug: String, bookSlug: String, versionSlug: String): Int {
        val languageId = getLanguageId(languageSlug).toString()
        val bookId = getBookId(bookSlug).toString()
        val versionId = getVersionId(versionSlug).toString()
        val db = readableDatabase
        val projectIdQuery = String.format(
            "SELECT %s FROM %s WHERE %s=? AND %s=? AND %s=?",
            ProjectContract.ProjectEntry._ID,
            ProjectContract.ProjectEntry.TABLE_PROJECT,
            ProjectContract.ProjectEntry.PROJECT_TARGET_LANGUAGE_FK,
            ProjectContract.ProjectEntry.PROJECT_BOOK_FK,
            ProjectContract.ProjectEntry.PROJECT_VERSION_FK
        )
        return try {
            DatabaseUtils.longForQuery(
                db,
                projectIdQuery,
                arrayOf(languageId, bookId, versionId)
            ).toInt()
        } catch (e: SQLiteDoneException) {
            val projectId = "${languageSlug}_${bookSlug}_$versionSlug"
            throw IllegalArgumentException("Project $projectId not found in database")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getChapterId(project: Project, chapter: Int): Int {
        return getChapterId(
            project.targetLanguageSlug,
            project.bookSlug,
            project.versionSlug,
            chapter
        )
    }

    override fun getChapterId(
        languageSlug: String,
        bookSlug: String,
        versionSlug: String,
        chapter: Int
    ): Int {
        val projectId = getProjectId(languageSlug, bookSlug, versionSlug).toString()
        val db = readableDatabase
        val chapterIdQuery = String.format(
            "SELECT %s FROM %s WHERE %s=? AND %s=?",
            ProjectContract.ChapterEntry._ID,
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            ProjectContract.ChapterEntry.CHAPTER_PROJECT_FK,
            ProjectContract.ChapterEntry.CHAPTER_NUMBER
        )
        return try {
            DatabaseUtils.longForQuery(
                db,
                chapterIdQuery,
                arrayOf(projectId, chapter.toString())
            ).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Chapter not found in database")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getUnitId(project: Project, chapter: Int, startVerse: Int): Int {
        return getUnitId(
            project.targetLanguageSlug,
            project.bookSlug,
            project.versionSlug,
            chapter,
            startVerse
        )
    }

    @Throws(IllegalArgumentException::class)
    override fun getUnitId(
        languageSlug: String,
        bookSlug: String,
        versionSlug: String,
        chapter: Int,
        startVerse: Int
    ): Int {
        val projectId = getProjectId(languageSlug, bookSlug, versionSlug).toString()
        val chapterId = getChapterId(languageSlug, bookSlug, versionSlug, chapter).toString()
        val db = readableDatabase
        val unitIdQuery = String.format(
            "SELECT %s FROM %s WHERE %s=? AND %s=? AND %s=?",
            ProjectContract.UnitEntry._ID,
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.UnitEntry.UNIT_PROJECT_FK,
            ProjectContract.UnitEntry.UNIT_CHAPTER_FK,
            ProjectContract.UnitEntry.UNIT_START_VERSE
        )
        return try {
            DatabaseUtils.longForQuery(
                db,
                unitIdQuery,
                arrayOf(projectId, chapterId, startVerse.toString())
            ).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Unit not found in database")
        }
    }

    override fun getUnitId(fileName: String): Int {
        val db = readableDatabase
        val takeIdQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_FILENAME
        )
        return try {
            DatabaseUtils.longForQuery(
                db,
                takeIdQuery,
                arrayOf(fileName)
            ).toInt()
        } catch (e: SQLiteDoneException) {
            -1
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getTakeId(takeInfo: TakeInfo): Int {
        val slugs = takeInfo.projectSlugs
        Logger.w(
            this.toString(),
            "Attempting to get take id for " + slugs.language + " " + slugs.book + " " + slugs.version + " verse start " + takeInfo.startVerse + " take " + takeInfo.take
        )
        val unitId = getUnitId(
            slugs.language,
            slugs.book,
            slugs.version,
            takeInfo.chapter,
            takeInfo.startVerse
        ).toString()

        val db = readableDatabase
        val takeIdQuery = String.format(
            "SELECT %s FROM %s WHERE %s=? AND %s=?",
            ProjectContract.TakeEntry._ID,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.TakeEntry.TAKE_NUMBER
        )
        return try {
            DatabaseUtils.longForQuery(
                db,
                takeIdQuery,
                arrayOf(unitId, takeInfo.take.toString())
            ).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Take not found in database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getModeId(modeSlug: String, anthologySlug: String): Int {
        val db = readableDatabase
        val takeIdQuery = String.format(
            "SELECT %s FROM %s WHERE %s=? AND %s=?",
            ProjectContract.ModeEntry._ID,
            ProjectContract.ModeEntry.TABLE_MODE,
            ProjectContract.ModeEntry.MODE_SLUG,
            ProjectContract.ModeEntry.MODE_ANTHOLOGY_FK
        )
        return try {
            DatabaseUtils.longForQuery(
                db,
                takeIdQuery,
                arrayOf(modeSlug, getAnthologyId(anthologySlug).toString())
            ).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Mode not found in database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getTakeCount(unitId: Int): Int {
        val stringifiedId = unitId.toString()
        val db = readableDatabase
        val query = String.format(
            "SELECT COUNT(*) FROM %s WHERE %s=?",
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK
        )
        return try {
            DatabaseUtils.longForQuery(db, query, arrayOf(stringifiedId)).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Take count cannot be retrieved for unitId: $stringifiedId")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getLanguageName(languageSlug: String): String {
        val db = readableDatabase
        val languageNameQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.LanguageEntry.LANGUAGE_NAME,
            ProjectContract.LanguageEntry.TABLE_LANGUAGE,
            ProjectContract.LanguageEntry.LANGUAGE_CODE
        )
        return try {
            DatabaseUtils.stringForQuery(db, languageNameQuery, arrayOf(languageSlug))
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Language: $languageSlug not found.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getLanguageCode(id: Int): String {
        val db = readableDatabase
        val languageSlugQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.LanguageEntry.LANGUAGE_CODE,
            ProjectContract.LanguageEntry.TABLE_LANGUAGE,
            ProjectContract.LanguageEntry._ID
        )
        return try {
            DatabaseUtils.stringForQuery(db, languageSlugQuery, arrayOf(id.toString()))
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Language id not found in database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getLanguage(id: Int): Language {
        val db = readableDatabase
        val query = String.format(
            "SELECT * FROM %s WHERE %s=%s",
            ProjectContract.LanguageEntry.TABLE_LANGUAGE,
            ProjectContract.LanguageEntry._ID,
            id.toString()
        )
        db.rawQuery(query, null).use { cursor ->
            return if (cursor.moveToFirst()) {
                val languageSlugId = cursor.getColumnIndex(ProjectContract.LanguageEntry.LANGUAGE_CODE)
                val languageSlug = cursor.getString(languageSlugId)

                val languageNameId = cursor.getColumnIndex(ProjectContract.LanguageEntry.LANGUAGE_NAME)
                val languageName = cursor.getString(languageNameId)

                Language(languageSlug, languageName)
            } else {
                throw IllegalArgumentException("Language id: $id not found in database.")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getUser(id: Int): User? {
        var user: User? = null

        val db = readableDatabase
        val query = String.format(
            "SELECT * FROM %s WHERE %s=%s",
            ProjectContract.UserEntry.TABLE_USER,
            ProjectContract.UserEntry._ID,
            id.toString()
        )
        db.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val userIdIndex = cursor.getColumnIndex(ProjectContract.UserEntry._ID)
                val userId = cursor.getInt(userIdIndex)

                val audioId = cursor.getColumnIndex(ProjectContract.UserEntry.USER_AUDIO)
                val audio = File(cursor.getString(audioId))

                val hashId = cursor.getColumnIndex(ProjectContract.UserEntry.USER_HASH)
                val hash = cursor.getString(hashId)

                user = User(audio, hash, userId)
            } else {
                Logger.e("ProjectDatabaseHelper.getUser", "User id not found in database.")
            }
        }
        return user
    }

    override fun addUser(user: User) {
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.UserEntry.USER_AUDIO, user.audio!!.absolutePath)
        cv.put(ProjectContract.UserEntry.USER_HASH, user.hash)
        val result = db.insertWithOnConflict(
            ProjectContract.UserEntry.TABLE_USER,
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        user.id = result.toInt()
    }

    override fun deleteUser(hash: String): Int {
        val db = writableDatabase
        val deleteWhere = String.format("%s=?", ProjectContract.UserEntry.USER_HASH)
        val result = db.delete(ProjectContract.UserEntry.TABLE_USER, deleteWhere, arrayOf(hash))
        return result
    }

    @Throws(IllegalArgumentException::class)
    override fun getBookName(bookSlug: String): String {
        val db = readableDatabase
        val bookNameQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.BookEntry.BOOK_NAME,
            ProjectContract.BookEntry.TABLE_BOOK,
            ProjectContract.BookEntry.BOOK_SLUG
        )
        return try {
            DatabaseUtils.stringForQuery(db, bookNameQuery, arrayOf(bookSlug))
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Book slug: $bookSlug not found in database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getBookSlug(id: Int): String {
        val db = readableDatabase
        val bookSlugQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.BookEntry.BOOK_SLUG,
            ProjectContract.BookEntry.TABLE_BOOK,
            ProjectContract.BookEntry._ID
        )
        return try {
            DatabaseUtils.stringForQuery(db, bookSlugQuery, arrayOf(id.toString()))
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Book id not found in database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getMode(id: Int): Mode {
        val db = readableDatabase
        val query = String.format(
            "SELECT * FROM %s WHERE %s=%s",
            ProjectContract.ModeEntry.TABLE_MODE,
            ProjectContract.ModeEntry._ID,
            id.toString()
        )
        db.rawQuery(query, null).use { cursor ->
            return if (cursor.moveToFirst()) {
                val modeSlugId = cursor.getColumnIndex(ProjectContract.ModeEntry.MODE_SLUG)
                val modeSlug = cursor.getString(modeSlugId)

                val modeNameId = cursor.getColumnIndex(ProjectContract.ModeEntry.MODE_NAME)
                val modeName = cursor.getString(modeNameId)

                val modeTypeId = cursor.getColumnIndex(ProjectContract.ModeEntry.MODE_TYPE)
                val modeType = cursor.getString(modeTypeId)

                Mode(modeSlug, modeName, modeType)
            } else {
                throw IllegalArgumentException("Mode id not found in database.")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getBook(id: Int): Book {
        val db = readableDatabase
        val query = String.format(
            "SELECT * FROM %s WHERE %s=%s",
            ProjectContract.BookEntry.TABLE_BOOK,
            ProjectContract.BookEntry._ID,
            id.toString()
        )
        db.rawQuery(query, null).use { cursor ->
            return if (cursor.moveToFirst()) {
                val bookSlugIndex = cursor.getColumnIndex(ProjectContract.BookEntry.BOOK_SLUG)
                val bookSlug = cursor.getString(bookSlugIndex)

                val bookNameIndex = cursor.getColumnIndex(ProjectContract.BookEntry.BOOK_NAME)
                val bookName = cursor.getString(bookNameIndex)

                val bookNumberIndex = cursor.getColumnIndex(ProjectContract.BookEntry.BOOK_NUMBER)
                val bookNumber = cursor.getInt(bookNumberIndex)

                val anthologyIndex = cursor.getColumnIndex(ProjectContract.BookEntry.BOOK_ANTHOLOGY_FK)
                val anthology = getAnthologySlug(cursor.getInt(anthologyIndex))

                val localizedBookName = Book.getLocalizedName(context, bookSlug, bookName, anthology)
                Book(bookSlug, localizedBookName, anthology, bookNumber)
            } else {
                throw IllegalArgumentException("Book id not found in database.")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getVersionName(id: Int): String {
        val db = readableDatabase
        val versionSlugQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.VersionEntry.VERSION_NAME,
            ProjectContract.VersionEntry.TABLE_VERSION,
            ProjectContract.VersionEntry._ID
        )
        return try {
            DatabaseUtils.stringForQuery(db, versionSlugQuery, arrayOf(id.toString()))
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Version id not found in database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getVersionSlug(id: Int): String {
        val db = readableDatabase
        val versionSlugQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.VersionEntry.VERSION_SLUG,
            ProjectContract.VersionEntry.TABLE_VERSION,
            ProjectContract.VersionEntry._ID
        )
        return try {
            DatabaseUtils.stringForQuery(db, versionSlugQuery, arrayOf(id.toString()))
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Version id not found in database.")
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getVersion(id: Int): Version {
        val version: Version
        val db = readableDatabase
        val query = String.format(
            "SELECT * FROM %s WHERE %s=%s",
            ProjectContract.VersionEntry.TABLE_VERSION,
            ProjectContract.VersionEntry._ID,
            id.toString()
        )
        db.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val versionSlugIndex = cursor.getColumnIndex(ProjectContract.VersionEntry.VERSION_SLUG)
                val versionSlug = cursor.getString(versionSlugIndex)

                val versionNameIndex = cursor.getColumnIndex(ProjectContract.VersionEntry.VERSION_NAME)
                val versionName = cursor.getString(versionNameIndex)

                version = Version(versionSlug, versionName)
            } else {
                throw IllegalArgumentException("Version id not found in database.")
            }
        }
        return version
    }

    @Throws(IllegalArgumentException::class)
    override fun getAnthologySlug(id: Int): String {
        val db = readableDatabase
        val anthologySlugQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.AnthologyEntry.ANTHOLOGY_SLUG,
            ProjectContract.AnthologyEntry.TABLE_ANTHOLOGY,
            ProjectContract.AnthologyEntry._ID
        )
        return try {
            DatabaseUtils.stringForQuery(db, anthologySlugQuery, arrayOf(id.toString()))
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Anthology id not found in database.")
        }
    }

    //TODO
    @Throws(IllegalArgumentException::class)
    override fun getAnthologySlug(bookSlug: String): String {
        val db = readableDatabase
        val bookNameQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.BookEntry.BOOK_ANTHOLOGY_FK,
            ProjectContract.BookEntry.TABLE_BOOK,
            ProjectContract.BookEntry.BOOK_SLUG
        )
        val anthologyId = DatabaseUtils.longForQuery(db, bookNameQuery, arrayOf(bookSlug)).toInt()
        return getAnthologySlug(anthologyId)
    }

    @Throws(IllegalArgumentException::class)
    override fun getAnthology(id: Int): Anthology {
        val db = readableDatabase
        val query = String.format(
            "SELECT * FROM %s WHERE %s=%s",
            ProjectContract.AnthologyEntry.TABLE_ANTHOLOGY,
            ProjectContract.AnthologyEntry._ID,
            id.toString()
        )
        db.rawQuery(query, null).use { cursor ->
            return if (cursor.moveToFirst()) {
                val anthologySlugIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_SLUG)
                val anthologySlug = cursor.getString(anthologySlugIndex)

                val anthologyNameIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_NAME)
                val anthologyName = cursor.getString(anthologyNameIndex)

                val resourceSlugIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_RESOURCE)
                val resourceSlug = cursor.getString(resourceSlugIndex)

                val sortIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_SORT)
                val sort = cursor.getInt(sortIndex)

                val regexIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_REGEX)
                val regex = cursor.getString(regexIndex)

                val groupsIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_GROUPS)
                val groups = cursor.getString(groupsIndex)

                val maskIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.ANTHOLOGY_MASK)
                val mask = cursor.getString(maskIndex)

                val pluginClassNameIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.PLUGIN_CLASS)
                val pluginClassName = cursor.getString(pluginClassNameIndex)

                val pluginJarNameIndex = cursor.getColumnIndex(ProjectContract.AnthologyEntry.PLUGIN_JAR)
                val pluginJarName = cursor.getString(pluginJarNameIndex)

                Anthology(
                    anthologySlug,
                    anthologyName,
                    resourceSlug,
                    sort,
                    regex,
                    groups,
                    mask,
                    pluginJarName,
                    pluginClassName
                )
            } else {
                throw IllegalArgumentException("Anthology id $id not found in database.")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getBookNumber(bookSlug: String): Int {
        val db = readableDatabase
        val bookNameQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.BookEntry.BOOK_NUMBER,
            ProjectContract.BookEntry.TABLE_BOOK,
            ProjectContract.BookEntry.BOOK_SLUG
        )
        return try {
            DatabaseUtils.longForQuery(db, bookNameQuery, arrayOf(bookSlug)).toInt()
        } catch (e: SQLiteDoneException) {
            throw IllegalArgumentException("Book slug: $bookSlug not found in database.")
        }
    }

    override fun addLanguage(languageSlug: String?, name: String?) {
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.LanguageEntry.LANGUAGE_CODE, languageSlug)
        cv.put(ProjectContract.LanguageEntry.LANGUAGE_NAME, name)
        db.insertWithOnConflict(
            ProjectContract.LanguageEntry.TABLE_LANGUAGE,
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    override fun addLanguages(languages: List<Language>) {
        val db = readableDatabase
        db.beginTransaction()
        try {
            for (l in languages) {
                addLanguage(l.slug, l.name)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun addAnthology(
        anthologySlug: String?, name: String?, resource: String?, sort: Int,
        regex: String?, groups: String?, mask: String?, jarName: String?, className: String?
    ) {
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.AnthologyEntry.ANTHOLOGY_SLUG, anthologySlug)
        cv.put(ProjectContract.AnthologyEntry.ANTHOLOGY_NAME, name)
        cv.put(ProjectContract.AnthologyEntry.ANTHOLOGY_RESOURCE, resource)
        cv.put(ProjectContract.AnthologyEntry.ANTHOLOGY_SORT, sort)
        cv.put(ProjectContract.AnthologyEntry.ANTHOLOGY_REGEX, regex)
        cv.put(ProjectContract.AnthologyEntry.ANTHOLOGY_GROUPS, groups)
        cv.put(ProjectContract.AnthologyEntry.ANTHOLOGY_MASK, mask)
        cv.put(ProjectContract.AnthologyEntry.PLUGIN_JAR, jarName)
        cv.put(ProjectContract.AnthologyEntry.PLUGIN_CLASS, className)
        db.insertWithOnConflict(
            ProjectContract.AnthologyEntry.TABLE_ANTHOLOGY,
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    override fun addBook(bookSlug: String?, bookName: String?, anthologySlug: String, bookNumber: Int) {
        val anthologyId = getAnthologyId(anthologySlug)
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.BookEntry.BOOK_SLUG, bookSlug)
        cv.put(ProjectContract.BookEntry.BOOK_NAME, bookName)
        cv.put(ProjectContract.BookEntry.BOOK_ANTHOLOGY_FK, anthologyId)
        cv.put(ProjectContract.BookEntry.BOOK_NUMBER, bookNumber)
        db.insertWithOnConflict(
            ProjectContract.BookEntry.TABLE_BOOK,
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    override fun addBooks(books: List<Book>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (b in books) {
                addBook(b.slug, b.name, b.anthology, b.order)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun addMode(slug: String?, name: String?, type: String?, anthologySlug: String) {
        val anthId = getAnthologyId(anthologySlug)
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.ModeEntry.MODE_SLUG, slug)
        cv.put(ProjectContract.ModeEntry.MODE_NAME, name)
        cv.put(ProjectContract.ModeEntry.MODE_TYPE, type)
        cv.put(ProjectContract.ModeEntry.MODE_ANTHOLOGY_FK, anthId)
        db.insertWithOnConflict(
            ProjectContract.ModeEntry.TABLE_MODE,
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    override fun addModes(modes: List<Mode>, anthologySlug: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (m in modes) {
                addMode(m.slug, m.name, m.typeString, anthologySlug)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun addVersion(versionSlug: String?, versionName: String?) {
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.VersionEntry.VERSION_SLUG, versionSlug)
        cv.put(ProjectContract.VersionEntry.VERSION_NAME, versionName)
        db.insertWithOnConflict(
            ProjectContract.VersionEntry.TABLE_VERSION,
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    override fun addVersions(versions: List<Version>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (v in versions) {
                addVersion(v.slug, v.name)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    //    public void addModeRelationships(String anthologySlug, Mode[] modes) {
    //        int anthId = getAnthologyId(anthologySlug);
    //        SQLiteDatabase db = getWritableDatabase();
    //        for(Mode m : modes) {
    //            int modeId = getModeId(m.getSlug());
    //            ContentValues cv = new ContentValues();
    //            cv.put(ProjectContract.ModeRelationshipEntry.ANTHOLOGY_FK, anthId);
    //            cv.put(ProjectContract.ModeRelationshipEntry.MODE_FK, modeId);
    //            long result = db.insertWithOnConflict(ProjectContract.ModeRelationshipEntry.TABLE_MODE_RELATIONSHIP, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    //        }
    //    }

    override fun addVersionRelationships(anthologySlug: String, versions: List<Version>) {
        val anthId = getAnthologyId(anthologySlug)
        val db = writableDatabase
        for (v in versions) {
            val versionId = getVersionId(v.slug)
            val cv = ContentValues()
            cv.put(ProjectContract.VersionRelationshipEntry.ANTHOLOGY_FK, anthId)
            cv.put(ProjectContract.VersionRelationshipEntry.VERSION_FK, versionId)
            db.insertWithOnConflict(
                ProjectContract.VersionRelationshipEntry.TABLE_VERSION_RELATIONSHIP,
                null,
                cv,
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun addProject(p: Project) {
        val targetLanguageId = getLanguageId(p.targetLanguageSlug)
        var sourceLanguageId: Int? = null
        if (p.sourceLanguageSlug.isNotEmpty()) {
            sourceLanguageId = getLanguageId(p.sourceLanguageSlug)
        }
        val bookId = getBookId(p.bookSlug)
        val versionId = getVersionId(p.versionSlug)
        val modeId = getModeId(p.modeSlug, p.anthologySlug)

        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.ProjectEntry.PROJECT_TARGET_LANGUAGE_FK, targetLanguageId)
        if (sourceLanguageId != null) {
            cv.put(ProjectContract.ProjectEntry.PROJECT_SOURCE_LANGUAGE_FK, sourceLanguageId)
        }
        cv.put(ProjectContract.ProjectEntry.PROJECT_BOOK_FK, bookId)
        cv.put(ProjectContract.ProjectEntry.PROJECT_VERSION_FK, versionId)
        cv.put(ProjectContract.ProjectEntry.PROJECT_MODE_FK, modeId)
        cv.put(ProjectContract.ProjectEntry.PROJECT_CONTRIBUTORS, p.contributors)
        cv.put(ProjectContract.ProjectEntry.PROJECT_SOURCE_AUDIO_PATH, p.sourceAudioPath)
        cv.put(ProjectContract.ProjectEntry.PROJECT_NOTES, "")
        cv.put(ProjectContract.ProjectEntry.PROJECT_PROGRESS, 0)

        db.insert(ProjectContract.ProjectEntry.TABLE_PROJECT, null, cv)
    }

    @Throws(IllegalArgumentException::class)
    override fun addProject(languageSlug: String, bookSlug: String, versionSlug: String, modeSlug: String) {
        val targetLanguageId = getLanguageId(languageSlug)
        val bookId = getBookId(bookSlug)
        val versionId = getVersionId(versionSlug)
        val anthologySlug = getAnthologySlug(bookSlug)
        val modeId = getModeId(modeSlug, anthologySlug)

        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.ProjectEntry.PROJECT_TARGET_LANGUAGE_FK, targetLanguageId)
        cv.put(ProjectContract.ProjectEntry.PROJECT_BOOK_FK, bookId)
        cv.put(ProjectContract.ProjectEntry.PROJECT_VERSION_FK, versionId)
        cv.put(ProjectContract.ProjectEntry.PROJECT_MODE_FK, modeId)
        cv.put(ProjectContract.ProjectEntry.PROJECT_NOTES, "")
        cv.put(ProjectContract.ProjectEntry.PROJECT_PROGRESS, 0)

        db.insert(ProjectContract.ProjectEntry.TABLE_PROJECT, null, cv)
    }

    @Throws(IllegalArgumentException::class)
    override fun addChapter(project: Project, chapter: Int) {
        addChapter(project.targetLanguageSlug, project.bookSlug, project.versionSlug, chapter)
    }

    @Throws(IllegalArgumentException::class)
    override fun addChapter(languageSlug: String, bookSlug: String, versionSlug: String, chapter: Int) {
        val projectId = getProjectId(languageSlug, bookSlug, versionSlug)

        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.ChapterEntry.CHAPTER_PROJECT_FK, projectId)
        cv.put(ProjectContract.ChapterEntry.CHAPTER_NUMBER, chapter)
        db.insert(ProjectContract.ChapterEntry.TABLE_CHAPTER, null, cv)
    }

    @Throws(IllegalArgumentException::class)
    override fun addUnit(project: Project, chapter: Int, startVerse: Int) {
        addUnit(
            project.targetLanguageSlug,
            project.bookSlug,
            project.versionSlug,
            chapter,
            startVerse
        )
    }

    @Throws(IllegalArgumentException::class)
    override fun addUnit(
        languageSlug: String,
        bookSlug: String,
        versionSlug: String,
        chapter: Int,
        startVerse: Int
    ) {
        val projectId = getProjectId(languageSlug, bookSlug, versionSlug)
        val chapterId = getChapterId(languageSlug, bookSlug, versionSlug, chapter)

        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.UnitEntry.UNIT_PROJECT_FK, projectId)
        cv.put(ProjectContract.UnitEntry.UNIT_CHAPTER_FK, chapterId)
        cv.put(ProjectContract.UnitEntry.UNIT_START_VERSE, startVerse)
        db.insert(ProjectContract.UnitEntry.TABLE_UNIT, null, cv)
    }

    override fun addTake(
        takeInfo: TakeInfo,
        takeFilename: String?,
        modeSlug: String,
        timestamp: Long,
        rating: Int,
        userId: Int
    ) {
        val slugs = takeInfo.projectSlugs
        val bookSlug = slugs.book
        val languageSlug = slugs.language
        val versionSlug = slugs.version
        val chapter = takeInfo.chapter
        val start = takeInfo.startVerse
        if (!projectExists(languageSlug, bookSlug, versionSlug)) {
            addProject(languageSlug, bookSlug, versionSlug, modeSlug)
            addChapter(languageSlug, bookSlug, versionSlug, chapter)
            addUnit(languageSlug, bookSlug, versionSlug, chapter, start)
            //If the chapter doesn't exist, then the unit can't either
        } else if (!chapterExists(languageSlug, bookSlug, versionSlug, chapter)) {
            addChapter(languageSlug, bookSlug, versionSlug, chapter)
            addUnit(languageSlug, bookSlug, versionSlug, chapter, start)
            //chapter could exist, but unit may not yet
        } else if (!unitExists(languageSlug, bookSlug, versionSlug, chapter, start)) {
            addUnit(languageSlug, bookSlug, versionSlug, chapter, start)
        }
        val unitId = getUnitId(languageSlug, bookSlug, versionSlug, chapter, start)

        val db = writableDatabase
        val cv = ContentValues()
        cv.put(ProjectContract.TakeEntry.TAKE_UNIT_FK, unitId)
        cv.put(ProjectContract.TakeEntry.TAKE_RATING, rating)
        cv.put(ProjectContract.TakeEntry.TAKE_NOTES, "")
        cv.put(ProjectContract.TakeEntry.TAKE_NUMBER, takeInfo.take)
        cv.put(ProjectContract.TakeEntry.TAKE_FILENAME, takeFilename)
        cv.put(ProjectContract.TakeEntry.TAKE_TIMESTAMP, timestamp)
        cv.put(ProjectContract.TakeEntry.TAKE_USER_FK, userId)
        val result = db.insertWithOnConflict(
            ProjectContract.TakeEntry.TABLE_TAKE,
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        if (result > 0) {
            autoSelectTake(unitId)
        }

        println("---------------- Take added $takeFilename ------------------")
    }

    override fun getProject(projectId: Int): Project? {
        var project: Project? = null

        val query = "SELECT * FROM " + ProjectContract.ProjectEntry.TABLE_PROJECT +
                " WHERE " + ProjectContract.ProjectEntry._ID + " =" + projectId.toString()
        val db = readableDatabase
        db.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val versionIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_VERSION_FK)
                val targetLanguageIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_TARGET_LANGUAGE_FK)
                val sourceLanguageIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_SOURCE_LANGUAGE_FK)
                val sourceAudioPathIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_SOURCE_AUDIO_PATH)
                val modeIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_MODE_FK)
                val bookIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_BOOK_FK)
                val contributorsIndex = cursor.getColumnIndex(ProjectContract.ProjectEntry.PROJECT_CONTRIBUTORS)

                try {
                    val version = getVersion(cursor.getInt(versionIndex))
                    val targetLanguage = getLanguage(cursor.getInt(targetLanguageIndex))
                    //Source language could be null
                    var sourceLanguage: Language? = null
                    var sourceAudioPath: String? = null
                    if (cursor.getType(sourceLanguageIndex) == Cursor.FIELD_TYPE_INTEGER) {
                        sourceLanguage = getLanguage(cursor.getInt(sourceLanguageIndex))
                        sourceAudioPath = cursor.getString(sourceAudioPathIndex)
                    }
                    val mode = getMode(cursor.getInt(modeIndex))
                    val book = getBook(cursor.getInt(bookIndex))
                    val anthology = getAnthology(getAnthologyId(book.anthology))
                    val contributors = cursor.getString(contributorsIndex)

                    project = Project(
                        targetLanguage,
                        anthology,
                        book,
                        version,
                        mode,
                        contributors,
                        sourceLanguage,
                        sourceAudioPath
                    )
                } catch (e: Exception) {
                    Logger.e(this::javaClass.name, "Error loading project $projectId", e)
                }
            }
        }
        return project
    }

    override fun getProject(languageSlug: String, versionSlug: String, bookSlug: String): Project? {
        if (projectExists(languageSlug, bookSlug, versionSlug)) {
            val id = getProjectId(languageSlug, bookSlug, versionSlug)
            return getProject(id)
        } else {
            return null
        }
    }

    override fun getProjectId(fileName: String): Int {
        val unitId = getUnitId(fileName)
        val db = readableDatabase
        val projectIdQuery = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.UnitEntry.UNIT_PROJECT_FK,
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.UnitEntry._ID
        )
        return try {
            DatabaseUtils.longForQuery(
                db,
                projectIdQuery,
                arrayOf(unitId.toString())
            ).toInt()
        } catch (e: SQLiteDoneException) {
            -1
        }
    }

    override fun getChapterCheckingLevel(project: Project, chapter: Int): Int {
        val chapterId = getChapterId(project, chapter).toString()
        val db = readableDatabase
        val getChapter = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.ChapterEntry.CHAPTER_CHECKING_LEVEL,
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            ProjectContract.ChapterEntry._ID
        )
        val checkingLevel = DatabaseUtils.longForQuery(db, getChapter, arrayOf(chapterId)).toInt()
        return checkingLevel
    }

    override fun getTakeRating(takeInfo: TakeInfo): Int {
        val slugs = takeInfo.projectSlugs
        val unitId = getUnitId(
            slugs.language,
            slugs.book,
            slugs.version,
            takeInfo.chapter,
            takeInfo.startVerse
        ).toString()
        val db = readableDatabase
        val getTake = String.format(
            "SELECT %s FROM %s WHERE %s=? AND %s=?",
            ProjectContract.TakeEntry.TAKE_RATING,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.TakeEntry.TAKE_NUMBER
        )
        val rating = DatabaseUtils.longForQuery(db, getTake, arrayOf(unitId, takeInfo.take.toString())).toInt()
        return rating
    }

    override fun getTakeUser(takeInfo: TakeInfo): User? {
        val slugs = takeInfo.projectSlugs
        val unitId = getUnitId(
            slugs.language,
            slugs.book,
            slugs.version,
            takeInfo.chapter,
            takeInfo.startVerse
        ).toString()
        val db = readableDatabase
        val getTake = String.format(
            "SELECT %s FROM %s WHERE %s=? AND %s=?",
            ProjectContract.TakeEntry.TAKE_USER_FK,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.TakeEntry.TAKE_NUMBER
        )
        val userId = DatabaseUtils.longForQuery(
            db,
            getTake,
            arrayOf(unitId, takeInfo.take.toString())
        ).toInt()
        return getUser(userId)
    }

    private fun getSelectedTakeId(unitId: Int): Int {
        val db = readableDatabase
        val getTake = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK,
            ProjectContract.UnitEntry.TABLE_UNIT, ProjectContract.UnitEntry._ID
        )
        db.rawQuery(getTake, arrayOf(unitId.toString())).use { cursor ->
            val takeIdCol = cursor.getColumnIndex(ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK)
            if (cursor.moveToFirst()) {
                if (!cursor.isNull(takeIdCol)) {
                    val takeId = cursor.getInt(takeIdCol)
                    cursor.close()
                    return takeId
                }
            }
        }
        return -1
    }

    override fun getSelectedTakeId(
        languageSlug: String,
        bookSlug: String,
        versionSlug: String,
        chapter: Int,
        startVerse: Int
    ): Int {
        val unitId = getUnitId(languageSlug, bookSlug, versionSlug, chapter, startVerse)
        return getSelectedTakeId(unitId)
    }

    override fun getSelectedTakeNumber(
        languageSlug: String,
        bookSlug: String,
        versionSlug: String,
        chapter: Int,
        startVerse: Int
    ): Int {
        val db = readableDatabase
        val takeId = getSelectedTakeId(languageSlug, bookSlug, versionSlug, chapter, startVerse)
        if (takeId != -1) {
            val getTakeNumber = String.format(
                "SELECT %s FROM %s WHERE %s=?",
                ProjectContract.TakeEntry.TAKE_NUMBER,
                ProjectContract.TakeEntry.TABLE_TAKE,
                ProjectContract.TakeEntry._ID
            )
            db.rawQuery(getTakeNumber, arrayOf(takeId.toString())).use { cursor ->
                if (cursor.moveToFirst()) {
                    val takeNumCol = cursor.getColumnIndex(ProjectContract.TakeEntry.TAKE_NUMBER)
                    val takeNum = cursor.getInt(takeNumCol)
                    return takeNum
                }
            }
        }
        return -1
    }

    override fun getSelectedTakeNumber(takeInfo: TakeInfo): Int {
        val slugs = takeInfo.projectSlugs
        return getSelectedTakeNumber(
            slugs.language,
            slugs.book,
            slugs.version,
            takeInfo.chapter,
            takeInfo.startVerse
        )
    }

    override fun setSelectedTake(takeInfo: TakeInfo) {
        val slugs = takeInfo.projectSlugs
        val unitId = getUnitId(
            slugs.language,
            slugs.book,
            slugs.version,
            takeInfo.chapter,
            takeInfo.startVerse
        )
        val takeId = getTakeId(takeInfo)
        setSelectedTake(unitId, takeId)
    }

    override fun setSelectedTake(unitId: Int, takeId: Int) {
        val unitIdString = unitId.toString()
        val takeIdString = takeId.toString()
        val db = readableDatabase
        val replaceTakeWhere = String.format("%s=?", ProjectContract.UnitEntry._ID)
        val replaceWith = ContentValues()
        replaceWith.put(ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK, takeIdString)
        db.update(
            ProjectContract.UnitEntry.TABLE_UNIT,
            replaceWith,
            replaceTakeWhere,
            arrayOf(unitIdString)
        )
    }

    override fun setTakeRating(takeInfo: TakeInfo, rating: Int) {
        val projectSlugs = takeInfo.projectSlugs
        val unitId = getUnitId(
            projectSlugs.language,
            projectSlugs.book,
            projectSlugs.version,
            takeInfo.chapter,
            takeInfo.startVerse
        )
        val db = readableDatabase
        val replaceTakeWhere = String.format(
            "%s=? AND %s=?",
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.TakeEntry.TAKE_NUMBER
        )
        val replaceWith = ContentValues()
        replaceWith.put(ProjectContract.TakeEntry.TAKE_RATING, rating)
        val result = db.update(
            ProjectContract.TakeEntry.TABLE_TAKE,
            replaceWith,
            replaceTakeWhere,
            arrayOf(unitId.toString(), takeInfo.take.toString())
        )
        if (result > 0) {
            autoSelectTake(unitId)
        }
    }

    override fun setCheckingLevel(project: Project, chapter: Int, checkingLevel: Int) {
        val chapterId = getChapterId(project, chapter).toString()
        val db = readableDatabase
        val replaceChapterWhere = String.format("%s=?", ProjectContract.ChapterEntry._ID)
        val replaceWith = ContentValues()
        replaceWith.put(ProjectContract.ChapterEntry.CHAPTER_CHECKING_LEVEL, checkingLevel)
        db.update(
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            replaceWith,
            replaceChapterWhere,
            arrayOf(chapterId)
        )
    }

    override fun setChapterProgress(chapterId: Int, progress: Int) {
        val whereClause = String.format("%s=?", ProjectContract.ChapterEntry._ID)
        val chapterIdString = chapterId.toString()
        val db = readableDatabase
        val contentValues = ContentValues()
        contentValues.put(ProjectContract.ChapterEntry.CHAPTER_PROGRESS, progress)
        db.update(
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            contentValues,
            whereClause,
            arrayOf(chapterIdString)
        )
    }

    override fun getChapterProgress(chapterId: Int): Int {
        val chapterIdString = chapterId.toString()
        val db = readableDatabase
        val query = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.ChapterEntry.CHAPTER_PROGRESS,
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            ProjectContract.ChapterEntry._ID
        )
        val progress = DatabaseUtils.longForQuery(db, query, arrayOf(chapterIdString)).toFloat()
        return Math.round(progress)
    }

    override fun getProjectProgressSum(projectId: Int): Int {
        val projectIdString = projectId.toString()
        val db = readableDatabase
        val query = String.format(
            "SELECT SUM(%s) FROM %s WHERE %s=?",
            ProjectContract.ChapterEntry.CHAPTER_PROGRESS,
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            ProjectContract.ChapterEntry.CHAPTER_PROJECT_FK
        )
        val progress = DatabaseUtils.longForQuery(db, query, arrayOf(projectIdString)).toInt()
        return progress
    }

    override fun getProjectProgress(projectId: Int): Int {
        val projectIdString = projectId.toString()
        val db = readableDatabase
        val query = String.format(
            "SELECT %s FROM %s WHERE %s=?",
            ProjectContract.ProjectEntry.PROJECT_PROGRESS,
            ProjectContract.ProjectEntry.TABLE_PROJECT,
            ProjectContract.ProjectEntry._ID
        )
        val progress = DatabaseUtils.longForQuery(db, query, arrayOf(projectIdString)).toFloat()
        return Math.round(progress)
    }

    override fun setProjectProgress(projectId: Int, progress: Int) {
        val whereClause = String.format("%s=?", ProjectContract.ProjectEntry._ID)
        val projectIdString = projectId.toString()
        val db = readableDatabase
        val contentValues = ContentValues()
        contentValues.put(ProjectContract.ProjectEntry.PROJECT_PROGRESS, progress)
        db.update(
            ProjectContract.ProjectEntry.TABLE_PROJECT,
            contentValues,
            whereClause,
            arrayOf(projectIdString)
        )
    }

    override fun removeSelectedTake(takeInfo: TakeInfo) {
        val slugs = takeInfo.projectSlugs
        val unitId = getUnitId(
            slugs.language,
            slugs.book,
            slugs.version,
            takeInfo.chapter,
            takeInfo.startVerse
        ).toString()
        val db = readableDatabase
        val replaceTakeWhere = String.format("%s=?", ProjectContract.UnitEntry._ID)
        val replaceWith = ContentValues()
        replaceWith.putNull(ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK)
        db.update(
            ProjectContract.UnitEntry.TABLE_UNIT,
            replaceWith,
            replaceTakeWhere,
            arrayOf(unitId)
        )
    }

    override fun deleteProject(p: Project) {
        val projectId = getProjectId(p).toString()
        val db = writableDatabase
        db.beginTransaction()
        val deleteTakes = String.format(
            "DELETE FROM %s WHERE %s IN (SELECT %s FROM %s WHERE %s=?)",
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.UnitEntry._ID,
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.UnitEntry.UNIT_PROJECT_FK
        )
        db.execSQL(deleteTakes, arrayOf(projectId))
        val deleteUnits = String.format(
            "DELETE FROM %s WHERE %s=?",
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.UnitEntry.UNIT_PROJECT_FK
        )
        db.execSQL(deleteUnits, arrayOf(projectId))
        val deleteChapters = String.format(
            "DELETE FROM %s WHERE %s=?",
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            ProjectContract.ChapterEntry.CHAPTER_PROJECT_FK
        )
        db.execSQL(deleteChapters, arrayOf(projectId))
        val deleteProject = String.format(
            "DELETE FROM %s WHERE %s=?",
            ProjectContract.ProjectEntry.TABLE_PROJECT,
            ProjectContract.ProjectEntry._ID
        )
        db.execSQL(deleteProject, arrayOf(projectId))
        db.setTransactionSuccessful()
        db.endTransaction()
    }

    override fun deleteTake(takeInfo: TakeInfo) {
        val slugs = takeInfo.projectSlugs
        val unitId = getUnitId(
            slugs.language,
            slugs.book,
            slugs.version,
            takeInfo.chapter,
            takeInfo.startVerse
        )
        val takeId = getTakeId(takeInfo)
        val db = writableDatabase
        val deleteWhere = String.format(
            "%s=? AND %s=?",
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.TakeEntry.TAKE_NUMBER
        )
        //        final String deleteTake = String.format("DELETE FROM %s WHERE %s=? AND %s=?",
//                TakeEntry.TABLE_TAKE, TakeEntry.TAKE_UNIT_FK, TakeEntry.TAKE_NUMBER);
        //db.execSQL(deleteTake, new String[]{String.valueOf(unitId), String.valueOf(takeInfo.getTake())});
        val takeSelected = getSelectedTakeId(unitId)
        val result = db.delete(
            ProjectContract.TakeEntry.TABLE_TAKE,
            deleteWhere,
            arrayOf(unitId.toString(), takeInfo.take.toString())
        )
        if (result > 0 && takeSelected == takeId) {
            autoSelectTake(unitId)
        }
    }

    /**
     * Computes the number of
     *
     * @param project
     * @param numUnits
     * @return
     */
    override fun getNumStartedUnitsInProject(project: Project): Map<Int, Int> {
        val projectId = getProjectId(project).toString()
        val numStartedUnits: MutableMap<Int, Int> = HashMap()

        val numUnitsStarted = String.format(
            "SELECT %s, COUNT(%s) FROM " +
                    "(SELECT u.%s, c.%s " +
                    "FROM %s c " +
                    "LEFT JOIN %s u " +
                    "ON c.%s=u.%s " +
                    "LEFT JOIN %s t " +
                    "ON t.%s=u.%s " +
                    "WHERE c.%s=? " +
                    "AND t.%s IS NOT NULL " +
                    "GROUP BY u.%s, c.%s) " +
                    "GROUP BY %s",
            ProjectContract.ChapterEntry.CHAPTER_NUMBER, ProjectContract.ChapterEntry._ID,
            ProjectContract.UnitEntry._ID, ProjectContract.ChapterEntry.CHAPTER_NUMBER,
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.ChapterEntry._ID, ProjectContract.UnitEntry.UNIT_CHAPTER_FK,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK, ProjectContract.UnitEntry._ID,
            ProjectContract.ChapterEntry.CHAPTER_PROJECT_FK,
            ProjectContract.TakeEntry._ID,
            ProjectContract.UnitEntry._ID, ProjectContract.ChapterEntry.CHAPTER_NUMBER,
            ProjectContract.ChapterEntry.CHAPTER_NUMBER
        )

        val db = readableDatabase
        db.rawQuery(numUnitsStarted, arrayOf(projectId)).use { cursor ->
            if (cursor.count > 0) {
                cursor.moveToFirst()
                do {
                    val chapterNum = cursor.getInt(0)
                    val unitCount = cursor.getInt(1)
                    numStartedUnits[chapterNum] = unitCount
                } while (cursor.moveToNext())
                return numStartedUnits
            }
        }

        return numStartedUnits
    }

    override fun getTakesForChapterCompilation(project: Project, chapter: Int): List<String> {
        val chapterId = getChapterId(project, chapter).toString()
        var takesToCompile: MutableList<String> = arrayListOf()

        val chapterCompilationQuery = String.format(
            "SELECT name, MAX(score) FROM " +
                    "(SELECT u.%s as uid, t.%s AS name, (%s * 1000 + t.%s) + CASE WHEN %s IS NOT NULL AND %s=t.%s THEN 10000 ELSE 1 END AS score " +  //_id, name, rating, _id, chosen_take_fk, chosen_take_fk, _id
                    "FROM %s c " +  //chapters
                    "INNER JOIN %s u ON c.%s=u.%s " +  //units, id, chapter_fk
                    "INNER JOIN %s t ON t.%s=u.%s " +  //takes, unit_fk, id
                    "WHERE c.%s=?) " +  //id, project_fk
                    "GROUP BY uid",
            ProjectContract.UnitEntry._ID,
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.TakeEntry.TAKE_RATING,
            ProjectContract.TakeEntry.TAKE_NUMBER,
            ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK,
            ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK,
            ProjectContract.TakeEntry._ID,
            ProjectContract.ChapterEntry.TABLE_CHAPTER,
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.ChapterEntry._ID,
            ProjectContract.UnitEntry.UNIT_CHAPTER_FK,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.UnitEntry._ID,
            ProjectContract.ChapterEntry._ID
        )
        val db = readableDatabase
        db.rawQuery(chapterCompilationQuery, arrayOf(chapterId)).use { cursor ->
            if (cursor.count > 0) {
                takesToCompile = ArrayList()
                cursor.moveToFirst()
                do {
                    takesToCompile.add(cursor.getString(0))
                } while (cursor.moveToNext())
            }
        }
        return takesToCompile
    }

    override fun resyncProjectWithFilesystem(
        project: Project,
        takes: List<File>,
        onCorruptFile: OnCorruptFile,
        onLanguageNotFound: OnLanguageNotFound?
    ) {
        importTakesToDatabase(project, takes, onLanguageNotFound, onCorruptFile)
        if (projectExists(project)) {
            val projectId = getProjectId(project)
            val where = String.format(
                "%s.%s=?",
                ProjectContract.UnitEntry.TABLE_UNIT,
                ProjectContract.UnitEntry.UNIT_PROJECT_FK
            )
            val whereArgs = arrayOf(projectId.toString())
            removeTakesWithNoFiles(takes, where, whereArgs)
        }
    }

    override fun resyncChapterWithFilesystem(
        project: Project,
        chapter: Int,
        takes: List<File>,
        onCorruptFile: OnCorruptFile,
        onLanguageNotFound: OnLanguageNotFound?
    ) {
        importTakesToDatabase(project, takes, onLanguageNotFound, onCorruptFile)
        if (projectExists(project) && chapterExists(project, chapter)) {
            val projectId = getProjectId(project)
            val chapterId = getChapterId(project, chapter)
            val whereClause = String.format(
                "%s.%s=? AND %s.%s=?",
                ProjectContract.UnitEntry.TABLE_UNIT,
                ProjectContract.UnitEntry.UNIT_PROJECT_FK,
                ProjectContract.UnitEntry.TABLE_UNIT,
                ProjectContract.UnitEntry.UNIT_CHAPTER_FK
            )
            val whereArgs = arrayOf(projectId.toString(), chapterId.toString())
            removeTakesWithNoFiles(takes, whereClause, whereArgs)
        }
    }

    //    private void resyncWithFilesystem(List<File> takes, String whereClause, String[] whereArgs, OnLanguageNotFound callback){
    //        importTakesToDatabase(takes, callback);
    //        removeTakesWithNoFiles(takes, whereClause, whereArgs);
    //    }
    private fun importTakesToDatabase(
        project: Project,
        takes: List<File>,
        callback: OnLanguageNotFound?,
        corruptFileCallback: OnCorruptFile
    ) {
        val db = writableDatabase
        //create a temporary table to store take names from the filesystem
        db.execSQL(ProjectContract.DELETE_TEMP)
        db.execSQL(ProjectContract.TempEntry.CREATE_TEMP_TABLE)
        db.beginTransaction()
        //add all the take names to the temp table
        for (f in takes) {
            val cv = ContentValues()
            val ppm = project.patternMatcher
            ppm.match(f)
            if (ppm.matched()) {
                cv.put(ProjectContract.TempEntry.TEMP_TAKE_NAME, f.name)
                cv.put(ProjectContract.TempEntry.TEMP_TIMESTAMP, f.lastModified())
                db.insert(ProjectContract.TempEntry.TABLE_TEMP, null, cv)
            }
        }
        //compare the names of all takes from the filesystem with the takes already in the database
        //names that do not have a match (are null in the left join) in the database need to be added
        val getMissingTakes = String.format(
            "SELECT t1.%s, t1.%s FROM %s AS t1 LEFT JOIN %s AS t2 ON t1.%s=t2.%s WHERE t2.%s IS NULL",
            ProjectContract.TempEntry.TEMP_TAKE_NAME,
            ProjectContract.TempEntry.TEMP_TIMESTAMP,
            ProjectContract.TempEntry.TABLE_TEMP,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TempEntry.TEMP_TAKE_NAME,
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.TakeEntry.TAKE_FILENAME
        )
        db.rawQuery(getMissingTakes, null).use { cursor ->
            //loop through all of the missing takes and add them to the db
            if (cursor.count > 0) {
                val nameIndex = cursor.getColumnIndex(ProjectContract.TempEntry.TEMP_TAKE_NAME)
                val timestampIndex = cursor.getColumnIndex(ProjectContract.TempEntry.TEMP_TIMESTAMP)
                cursor.moveToFirst()
                do {
                    val takeName = cursor.getString(nameIndex)
                    val ppm = project.patternMatcher
                    ppm.match(takeName)
                    val takeInfo = ppm.takeInfo!!
                    val slugs = takeInfo.projectSlugs
                    if (!languageExists(slugs.language)) {
                        if (callback != null) {
                            val name = callback.requestLanguageName(slugs.language)
                            addLanguage(slugs.language, name)
                        } else {
                            addLanguage(slugs.language, "???") //missingno
                        }
                    }
                    //Need to get the mode out of the metadata because chunks of only one verse are indistinguishable from verse mode
                    val dir = ProjectFileUtils.getParentDirectory(project, takeName, directoryProvider)
                    val file = File(dir, cursor.getString(nameIndex))
                    try {
                        val wav = WavFile(file)
                        //default user; currently not enough info to be able to figure it out
                        addTake(
                            takeInfo,
                            cursor.getString(nameIndex),
                            wav.metadata.modeSlug,
                            cursor.getLong(timestampIndex),
                            0,
                            1
                        )
                    } catch (e: IllegalArgumentException) {
                        //TODO: corrupt file, prompt to fix maybe? or delete? At least tell which file is causing a problem
                        Logger.e(
                            this.toString(),
                            "Error loading wav file named: " + dir + "/" + cursor.getString(nameIndex),
                            e
                        )
                        corruptFileCallback.onCorruptFile(file)
                    }
                } while (cursor.moveToNext())
            }
        }
        db.setTransactionSuccessful()
        db.endTransaction()
    }

    /**
     * Removes takes from the database that adhere to the where clause and do not appear in the provided takes list
     * Example: takes contains a list of all takes in a chapter, the where clause matches takes with that projectId and chapterId
     * and the result is that all database entries with that projectId and chapterId without a matching file in the takes list are removed
     * from the database.
     *
     *
     * This is used to resync part of the database in the event that a user manually removed a file from an external file manager application
     *
     * @param takes       the list of files to NOT be removed from the database
     * @param whereClause which takes should be cleared from the database
     */
    private fun removeTakesWithNoFiles(
        takes: List<File>,
        whereClause: String,
        whereArgs: Array<String>
    ) {
        val db = writableDatabase
        db.beginTransaction()
        val allTakesFromAProject = String.format(
            "SELECT %s.%s as takefilename, %s.%s as takeid from %s LEFT JOIN %s ON %s.%s=%s.%s WHERE %s",
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry._ID,  //select
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.UnitEntry.TABLE_UNIT,  //tables to join takes left join units
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.UnitEntry._ID,  //ON takes.unit_fk = units._id
            whereClause
        ) //ie WHERE units.chapter_fk = ?

        val danglingReferences = String.format(
            "SELECT takefilename, takeid FROM (%s) LEFT JOIN %s as temps ON temps.%s=takefilename WHERE temps.%s IS NULL",
            allTakesFromAProject,
            ProjectContract.TempEntry.TABLE_TEMP,
            ProjectContract.TempEntry.TEMP_TAKE_NAME,
            ProjectContract.TempEntry.TEMP_TAKE_NAME
        )

        //        //find all the takes in the db that do not have a match in the filesystem
//        final String deleteDanglingReferences = String.format("SELECT t1.%s, t1.%s FROM %s AS t1 LEFT JOIN %s AS t2 ON t1.%s=t2.%s WHERE t2.%s IS NULL",
//                ProjectContract.TakeEntry.TAKE_FILENAME, ProjectContract.TakeEntry._ID, ProjectContract.TakeEntry.TABLE_TAKE, ProjectContract.TempEntry.TABLE_TEMP, ProjectContract.TempEntry.TEMP_TAKE_NAME, ProjectContract.TakeEntry.TAKE_FILENAME, ProjectContract.TakeEntry.TAKE_FILENAME);
        //Cursor c = db.rawQuery(deleteDanglingReferences, null);
        db.rawQuery(danglingReferences, whereArgs).use { cursor ->
            //for each of these takes that do not have a corresponding match, remove them from the database
            if (cursor.count > 0) {
                val idIndex = cursor.getColumnIndex("takeid")
                val deleteTake = String.format("%s=?", ProjectContract.TakeEntry._ID)
                val removeSelectedTake =
                    String.format("%s=?", ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK)
                cursor.moveToFirst()
                do {
                    val cv = ContentValues()
                    cv.putNull(ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK)
                    db.update(
                        ProjectContract.UnitEntry.TABLE_UNIT,
                        cv,
                        removeSelectedTake,
                        arrayOf(cursor.getInt(idIndex).toString())
                    )
                    db.delete(
                        ProjectContract.TakeEntry.TABLE_TAKE,
                        deleteTake,
                        arrayOf(cursor.getInt(idIndex).toString())
                    )
                } while (cursor.moveToNext())
            }
        }
        db.execSQL(ProjectContract.DELETE_TEMP)
        db.setTransactionSuccessful()
        db.endTransaction()
    }

    override fun resyncDbWithFs(
        project: Project,
        takes: List<File>,
        onCorruptFile: OnCorruptFile,
        onLanguageNotFound: OnLanguageNotFound?
    ) {
        val db = writableDatabase
        //create a temporary table to store take names from the filesystem
        db.execSQL(ProjectContract.DELETE_TEMP)
        db.execSQL(ProjectContract.TempEntry.CREATE_TEMP_TABLE)
        db.beginTransaction()
        //add all the take names to the temp table
        for (f in takes) {
            val cv = ContentValues()
            val ppm = project.patternMatcher
            ppm.match(f)
            if (ppm.matched()) {
                cv.put(ProjectContract.TempEntry.TEMP_TAKE_NAME, f.name)
                cv.put(ProjectContract.TempEntry.TEMP_TIMESTAMP, f.lastModified())
                db.insert(ProjectContract.TempEntry.TABLE_TEMP, null, cv)
            }
        }
        //compare the names of all takes from the filesystem with the takes already in the database
        //names that do not have a match (are null in the left join) in the database need to be added
        val getMissingTakes = String.format(
            "SELECT t1.%s, t1.%s FROM %s AS t1 LEFT JOIN %s AS t2 ON t1.%s=t2.%s WHERE t2.%s IS NULL",
            ProjectContract.TempEntry.TEMP_TAKE_NAME,
            ProjectContract.TempEntry.TEMP_TIMESTAMP,
            ProjectContract.TempEntry.TABLE_TEMP,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TempEntry.TEMP_TAKE_NAME,
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.TakeEntry.TAKE_FILENAME
        )
        db.rawQuery(getMissingTakes, null).use { cursor ->
            //loop through all of the missing takes and add them to the db
            if (cursor.count > 0) {
                val nameIndex = cursor.getColumnIndex(ProjectContract.TempEntry.TEMP_TAKE_NAME)
                val timestampIndex = cursor.getColumnIndex(ProjectContract.TempEntry.TEMP_TIMESTAMP)
                cursor.moveToFirst()
                do {
                    val takeName = cursor.getString(nameIndex)
                    val ppm = project.patternMatcher
                    ppm.match(takeName)
                    val takeInfo = ppm.takeInfo!!
                    val slugs = takeInfo.projectSlugs
                    if (!languageExists(slugs.language)) {
                        if (onLanguageNotFound != null) {
                            val name = onLanguageNotFound.requestLanguageName(slugs.language)
                            addLanguage(slugs.language, name)
                        } else {
                            addLanguage(slugs.language, "???") //missingno
                        }
                    }
                    //Need to get the mode out of the metadata because chunks of only one verse are indistinguishable from verse mode
                    val dir = ProjectFileUtils.getParentDirectory(project, takeName, directoryProvider)
                    val file = File(dir, cursor.getString(nameIndex))
                    try {
                        val wav = WavFile(file)
                        //default user
                        addTake(
                            takeInfo,
                            cursor.getString(nameIndex),
                            wav.metadata.modeSlug,
                            cursor.getLong(timestampIndex),
                            0,
                            1
                        )
                    } catch (e: IllegalArgumentException) {
                        //TODO: corrupt file, prompt to fix maybe? or delete? At least tell which file is causing a problem
                        Logger.e(
                            this.toString(),
                            "Error loading wav file named: " + dir + "/" + cursor.getString(nameIndex),
                            e
                        )
                        onCorruptFile.onCorruptFile(file)
                    }
                } while (cursor.moveToNext())
            }
        }

        //find all the takes in the db that do not have a match in the filesystem
//        final String deleteDanglingReferences = String.format("SELECT t1.%s, t1.%s FROM %s AS t1 LEFT JOIN %s AS t2 ON t1.%s=t2.%s WHERE t2.%s IS NULL",
//                ProjectContract.TakeEntry.TAKE_FILENAME, ProjectContract.TakeEntry._ID,
//                ProjectContract.TakeEntry.TABLE_TAKE, ProjectContract.TempEntry.TABLE_TEMP,
//                ProjectContract.TempEntry.TEMP_TAKE_NAME, ProjectContract.TakeEntry.TAKE_FILENAME,
//                ProjectContract.TakeEntry.TAKE_FILENAME);

        // select * from takes as t1
        // left join units on (t1.unit_fk=units._id AND units.project_fk=1)
        // left join stuff as t2 on t1.filename=t2.filename
        // where t2.filename is null and project_fk is not null group by number
        val deleteDanglingReferences = String.format(
            "SELECT t1.%s, t1.%s FROM %s AS t1 " +  //t1.filename t1.timestamp from takes as t1
                    "LEFT JOIN %s ON t1.%s=%s.%s AND %s.%s=?" +  //units on t1.unit_fk=units._id and units.project_fk=?
                    "LEFT JOIN %s AS t2 ON t1.%s=t2.%s " +  //temp as t2 on t1.filename=t2.filename
                    "WHERE t2.%s IS NULL AND %s IS NOT NULL " +  //t2.filename is null and project_fk is not null
                    "GROUP BY %s",  //number
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.TakeEntry._ID,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.UnitEntry._ID,
            ProjectContract.UnitEntry.TABLE_UNIT,
            ProjectContract.UnitEntry.UNIT_PROJECT_FK,
            ProjectContract.TempEntry.TABLE_TEMP,
            ProjectContract.TempEntry.TEMP_TAKE_NAME,
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.UnitEntry.UNIT_PROJECT_FK,
            ProjectContract.TakeEntry.TAKE_NUMBER
        )

        db.rawQuery(deleteDanglingReferences, null).use { cursor ->
            //for each of these takes that do not have a corresponding match, remove them from the database
            if (cursor.count > 0) {
                val idIndex = cursor.getColumnIndex(ProjectContract.TakeEntry._ID)
                val deleteTake = String.format("%s=?", ProjectContract.TakeEntry._ID)
                val removeSelectedTake =
                    String.format("%s=?", ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK)
                cursor.moveToFirst()
                do {
                    val cv = ContentValues()
                    cv.putNull(ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK)
                    db.update(
                        ProjectContract.UnitEntry.TABLE_UNIT,
                        cv,
                        removeSelectedTake,
                        arrayOf(cursor.getInt(idIndex).toString())
                    )
                    db.delete(
                        ProjectContract.TakeEntry.TABLE_TAKE,
                        deleteTake,
                        arrayOf(cursor.getInt(idIndex).toString())
                    )
                } while (cursor.moveToNext())
            }
        }
        db.setTransactionSuccessful()
        db.endTransaction()
        db.execSQL(ProjectContract.DELETE_TEMP)
    }

    override fun resyncBookWithFs(
        project: Project,
        takes: List<File>,
        languageNotFoundCallback: OnLanguageNotFound?
    ) {
        val db = writableDatabase
        //create a temporary table to store take names from the filesystem
        db.execSQL(ProjectContract.DELETE_TEMP)
        db.execSQL(ProjectContract.TempEntry.CREATE_TEMP_TABLE)
        db.beginTransaction()
        //add all the take names to the temp table
        for (f in takes) {
            val cv = ContentValues()
            val ppm = project.patternMatcher
            ppm.match(f)
            if (ppm.matched()) {
                cv.put(ProjectContract.TempEntry.TEMP_TAKE_NAME, f.name)
                cv.put(ProjectContract.TempEntry.TEMP_TIMESTAMP, f.lastModified())
                db.insert(ProjectContract.TempEntry.TABLE_TEMP, null, cv)
            }
        }
        //compare the names of all takes from the filesystem with the takes already in the database
        //names that do not have a match (are null in the left join) in the database need to be added
        val getMissingTakes = String.format(
            "SELECT t1.%s, t1.%s FROM %s AS t1 LEFT JOIN %s AS t2 ON t1.%s=t2.%s WHERE t2.%s IS NULL",
            ProjectContract.TempEntry.TEMP_TAKE_NAME,
            ProjectContract.TempEntry.TEMP_TIMESTAMP,
            ProjectContract.TempEntry.TABLE_TEMP,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TempEntry.TEMP_TAKE_NAME,
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.TakeEntry.TAKE_FILENAME
        )
        db.rawQuery(getMissingTakes, null).use { cursor ->
            //loop through all of the missing takes and add them to the db
            if (cursor.count > 0) {
                val nameIndex = cursor.getColumnIndex(ProjectContract.TempEntry.TEMP_TAKE_NAME)
                val timestampIndex = cursor.getColumnIndex(ProjectContract.TempEntry.TEMP_TIMESTAMP)
                cursor.moveToFirst()
                do {
                    val takeName = cursor.getString(nameIndex)
                    val ppm = project.patternMatcher
                    ppm.match(takeName)
                    val takeInfo = ppm.takeInfo!!
                    val slugs = takeInfo.projectSlugs
                    if (!languageExists(slugs.language)) {
                        if (languageNotFoundCallback != null) {
                            val name = languageNotFoundCallback.requestLanguageName(slugs.language)
                            addLanguage(slugs.language, name)
                        } else {
                            addLanguage(slugs.language, "???") //missingno
                        }
                    }
                    //Need to get the mode out of the metadata because chunks of only one verse are indistinguishable from verse mode
                    val dir = ProjectFileUtils.getParentDirectory(takeInfo, directoryProvider)
                    val wav = WavFile(File(dir, cursor.getString(nameIndex)))
                    addTake(
                        takeInfo,
                        cursor.getString(nameIndex),
                        wav.metadata.modeSlug,
                        cursor.getLong(timestampIndex),
                        0,
                        1
                    )
                } while (cursor.moveToNext())
            }
        }
        //find all the takes in the db that do not have a match in the filesystem
        val deleteDanglingReferences = String.format(
            "SELECT t1.%s, t1.%s FROM %s AS t1 LEFT JOIN %s AS t2 ON t1.%s=t2.%s WHERE t2.%s IS NULL",
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.TakeEntry._ID,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TempEntry.TABLE_TEMP,
            ProjectContract.TempEntry.TEMP_TAKE_NAME,
            ProjectContract.TakeEntry.TAKE_FILENAME,
            ProjectContract.TakeEntry.TAKE_FILENAME
        )
        db.rawQuery(deleteDanglingReferences, null).use { cursor ->
            //for each of these takes that do not have a corresponding match, remove them from the database
            if (cursor.count > 0) {
                val idIndex = cursor.getColumnIndex(ProjectContract.TakeEntry._ID)
                val deleteTake = String.format("%s=?", ProjectContract.TakeEntry._ID)
                val removeSelectedTake =
                    String.format("%s=?", ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK)
                cursor.moveToFirst()
                do {
                    val cv = ContentValues()
                    cv.putNull(ProjectContract.UnitEntry.UNIT_CHOSEN_TAKE_FK)
                    db.update(
                        ProjectContract.UnitEntry.TABLE_UNIT,
                        cv,
                        removeSelectedTake,
                        arrayOf(cursor.getInt(idIndex).toString())
                    )
                    db.delete(
                        ProjectContract.TakeEntry.TABLE_TAKE,
                        deleteTake,
                        arrayOf(cursor.getInt(idIndex).toString())
                    )
                } while (cursor.moveToNext())
            }
        }
        db.setTransactionSuccessful()
        db.endTransaction()
        db.execSQL(ProjectContract.DELETE_TEMP)
    }

    override fun resyncProjectsWithFs(
        allProjects: List<Project>,
        projectLevelResync: ProjectListResyncTask
    ): List<Project> {
        val newProjects: MutableList<Project> = ArrayList()
        for (p in allProjects) {
            if (!languageExists(p.targetLanguageSlug)) {
                val name = projectLevelResync.requestLanguageName(p.targetLanguageSlug)
                addLanguage(p.targetLanguageSlug, name)
            }
            if (!projectExists(p)) {
                newProjects.add(p)
            }
            addProject(p)
        }
        return newProjects
    }

    override fun autoSelectTake(unitId: Int) {
        val db = readableDatabase
        val autoSelect = String.format(
            "SELECT %s FROM %s WHERE %s=? ORDER BY %s DESC, %s DESC LIMIT 1",
            ProjectContract.TakeEntry._ID,
            ProjectContract.TakeEntry.TABLE_TAKE,
            ProjectContract.TakeEntry.TAKE_UNIT_FK,
            ProjectContract.TakeEntry.TAKE_RATING,
            ProjectContract.TakeEntry.TAKE_TIMESTAMP
        )
        db.rawQuery(autoSelect, arrayOf(unitId.toString())).use { cursor ->
            if (cursor.count > 0) {
                cursor.moveToFirst()
                val takeId = cursor.getInt(0)
                setSelectedTake(unitId, takeId)
            }
        }
    }

    override fun getBooks(anthologySlug: String): List<Book> {
        val anthId = getAnthologyId(anthologySlug)
        val bookList: MutableList<Book> = ArrayList()
        val query = "SELECT * FROM " + ProjectContract.BookEntry.TABLE_BOOK + " WHERE " +
                ProjectContract.BookEntry.BOOK_ANTHOLOGY_FK + "=" + anthId.toString() +
                " ORDER BY " + ProjectContract.BookEntry.BOOK_NUMBER + " ASC"
        val db = readableDatabase
        db.beginTransaction()
        db.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val bookSlugIndex = cursor.getColumnIndex(ProjectContract.BookEntry.BOOK_SLUG)
                    val bookSlug = cursor.getString(bookSlugIndex)

                    val bookNameIndex = cursor.getColumnIndex(ProjectContract.BookEntry.BOOK_NAME)
                    val bookName = cursor.getString(bookNameIndex)

                    val anthologyIndex = cursor.getColumnIndex(ProjectContract.BookEntry.BOOK_ANTHOLOGY_FK)
                    val anthologyId = cursor.getInt(anthologyIndex)

                    val orderIndex = cursor.getColumnIndex(ProjectContract.BookEntry.BOOK_NUMBER)
                    val order = cursor.getInt(orderIndex)

                    val localizedBookName = Book.getLocalizedName(context, bookSlug, bookName, anthologySlug)
                    bookList.add(Book(bookSlug, localizedBookName, getAnthologySlug(anthologyId), order))
                } while (cursor.moveToNext())
            }
        }
        db.endTransaction()
        return bookList
    }

    override fun getVersions(anthologySlug: String): List<Version> {
        val anthId = getAnthologyId(anthologySlug)
        val versionList: MutableList<Version> = ArrayList()
        val query =
            "SELECT * FROM " + ProjectContract.VersionRelationshipEntry.TABLE_VERSION_RELATIONSHIP +
                    " WHERE " + ProjectContract.VersionRelationshipEntry.ANTHOLOGY_FK + "=" + anthId.toString()
        val db = readableDatabase
        db.beginTransaction()
        db.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val versionIdIndex = cursor.getColumnIndex(ProjectContract.VersionRelationshipEntry.VERSION_FK)
                    val versionId = cursor.getInt(versionIdIndex)
                    val versionSlug = getVersionSlug(versionId)
                    val versionName = getVersionName(versionId)
                    versionList.add(Version(versionSlug, versionName))
                } while (cursor.moveToNext())
            }
        }
        db.endTransaction()
        return versionList
    }

    override fun getModes(anthologySlug: String): List<Mode> {
        val anthId = getAnthologyId(anthologySlug)
        val modeList: MutableList<Mode> = ArrayList()
        val query = "SELECT * FROM " + ProjectContract.ModeEntry.TABLE_MODE +
                " WHERE " + ProjectContract.ModeEntry.MODE_ANTHOLOGY_FK + "=" + anthId.toString()
        val db = readableDatabase
        db.beginTransaction()
        db.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val modeIdIndex = cursor.getColumnIndex(ProjectContract.ModeEntry._ID)
                    val modeId = cursor.getInt(modeIdIndex)
                    val mode = getMode(modeId)
                    modeList.add(mode)
                } while (cursor.moveToNext())
            }
        }
        db.endTransaction()
        return modeList
    }

    override fun updateProject(projectId: Int, project: Project) {
        try {
            val db = writableDatabase
            val replaceWith = ContentValues()
            val replaceProjectWhere = String.format("%s=?", ProjectContract.ProjectEntry._ID)

            val targetLanguageId = getLanguageId(project.targetLanguageSlug)
            if (project.sourceLanguageSlug.isNotEmpty()) {
                val sourceLanguageId = getLanguageId(project.sourceLanguageSlug)
                replaceWith.put(
                    ProjectContract.ProjectEntry.PROJECT_SOURCE_LANGUAGE_FK,
                    sourceLanguageId
                )
            }
            val bookId = getBookId(project.bookSlug)
            val versionId = getVersionId(project.versionSlug)
            val modeId = getModeId(project.modeSlug, project.anthologySlug)

            replaceWith.put(
                ProjectContract.ProjectEntry.PROJECT_TARGET_LANGUAGE_FK,
                targetLanguageId.toString()
            )
            replaceWith.put(
                ProjectContract.ProjectEntry.PROJECT_BOOK_FK,
                bookId.toString()
            )
            replaceWith.put(
                ProjectContract.ProjectEntry.PROJECT_VERSION_FK,
                versionId
            )
            replaceWith.put(
                ProjectContract.ProjectEntry.PROJECT_MODE_FK,
                modeId
            )
            db.update(
                ProjectContract.ProjectEntry.TABLE_PROJECT,
                replaceWith,
                replaceProjectWhere,
                arrayOf(projectId.toString())
            )
        } catch (e: Exception) {
            Logger.e(this::javaClass.name, "Error updating project", e)
        }
    }

    companion object {
        private const val DATABASE_VERSION = 4
        private const val DATABASE_NAME = "translation_projects"
    }
}
