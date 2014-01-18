package jp.sf.fess.solr.plugin.suggest.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

import org.apache.solr.update.TransactionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

public class TransactionLogUtil {
    private static final Logger logger = LoggerFactory
            .getLogger(TransactionLogUtil.class);

    private static final String PREFIX = "suggest-";

    public static TransactionLog createSuggestTransactionLog(
            final File tlogFile, final Collection<String> globalStrings,
            final boolean openExisting) throws NoSuchMethodException,
            InstantiationException, IllegalAccessException, IOException,
            IllegalArgumentException, InvocationTargetException {
        final long start = System.currentTimeMillis();
        final File file = new File(tlogFile.getParent(), PREFIX
                + tlogFile.getName());
        Files.copy(tlogFile, file);
        if (logger.isInfoEnabled()) {
            logger.info("Create suggest trans log. took="
                    + (System.currentTimeMillis() - start) + " file="
                    + file.getAbsolutePath());
        }
        final Class<TransactionLog> cls = TransactionLog.class;
        final Constructor<TransactionLog> constructor = cls
                .getDeclaredConstructor(File.class, Collection.class,
                        Boolean.TYPE);
        constructor.setAccessible(true);
        return constructor.newInstance(file, globalStrings, openExisting);
    }

    public static void clearSuggestTransactionLog(final String dir) {
        final File d = new File(dir);
        if (!d.isDirectory()) {
            return;
        }
        for (final File f : d.listFiles()) {
            if (f.isFile() && f.getName().startsWith(PREFIX)) {
                if (!f.delete()) {
                    f.deleteOnExit();
                }
            }
        }
    }
}