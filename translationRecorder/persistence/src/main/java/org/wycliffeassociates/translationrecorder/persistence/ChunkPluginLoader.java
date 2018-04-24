package org.wycliffeassociates.translationrecorder.persistence;

import android.content.Context;

import org.wycliffeassociates.translationrecorder.chunkplugin.ChunkPlugin;
import org.wycliffeassociates.translationrecorder.chunkplugin.Mode;
import org.wycliffeassociates.translationrecorder.data.model.Anthology;
import org.wycliffeassociates.translationrecorder.data.model.Book;
import org.wycliffeassociates.translationrecorder.data.model.Project;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;

import dalvik.system.DexClassLoader;

/**
 * Created by sarabiaj on 9/26/2017.
 */

public class ChunkPluginLoader implements Project.ProjectPluginLoader {

    Context ctx;

    public ChunkPluginLoader(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public ChunkPlugin loadChunkPlugin(Anthology anthology, Book book, Mode.UNIT type) {
        ChunkPlugin chunks = null;
        File jarsDir = new File(ctx.getCacheDir(), "Plugins/Jars");
        jarsDir.mkdirs();
        String jarFile = new File(jarsDir, anthology.getPluginJarName()).getAbsolutePath();
        File codeDir = new File(ctx.getCacheDir(), "dex/");
        codeDir.mkdirs();
        final File optimizedDexOutputPath = new File(codeDir, "biblechunkdex");
        try {
            optimizedDexOutputPath.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        DexClassLoader classLoader = new DexClassLoader(
                jarFile,
                optimizedDexOutputPath.getAbsolutePath(),
                null,
                getClass().getClassLoader()
        );
        try {
            Class<?> plugin = classLoader.loadClass(anthology.getPluginClassName());
            Constructor<ChunkPlugin> ctr = (Constructor<ChunkPlugin>) plugin
                    .asSubclass(ChunkPlugin.class)
                    .getConstructor(type.getClass());
            chunks = ctr.newInstance(type);
        } catch (Exception e) {
            e.printStackTrace();
        }
        chunks.parseChunks(chunksInputStream(anthology, book));
        return chunks;
    }

    @Override
    public InputStream chunksInputStream(Anthology anthology, Book book) {
        try {
            return ctx.getAssets().open("chunks/" + anthology.getSlug() + "/" + book.getSlug() + "/chunks.json");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}