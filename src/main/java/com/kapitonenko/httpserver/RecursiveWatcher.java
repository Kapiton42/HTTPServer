package com.kapitonenko.httpserver;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import static java.nio.file.LinkOption.*;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by kapiton on 23.02.16.
 */
public class RecursiveWatcher {
    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;

    private Map<Path, byte[]> fileCash;
    private Map<Path, Boolean> fileUpdateStatus;

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    private void registerAll(final Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    RecursiveWatcher(Path dir, Map<Path, byte[]> fileCash, Map<Path, Boolean> fileUpdateStatus) throws IOException {
        this.fileCash = fileCash;
        this.fileUpdateStatus = fileUpdateStatus;
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();

        registerAll(dir);
    }

    void checkWatchService() {
        WatchKey key;
        key = watcher.poll();

        if(key != null) {
            Path dir = keys.get(key);
            if (dir == null) {
                return;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                if (kind == OVERFLOW) {
                    continue;
                }

                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path filename = dir.resolve(name);

                if (kind == ENTRY_DELETE) {
                    fileCash.remove(filename);
                    fileUpdateStatus.remove(filename);
                }
                if (kind == ENTRY_MODIFY) {
                    fileUpdateStatus.put(filename, true);
                }

                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(filename, NOFOLLOW_LINKS)) {
                            registerAll(filename);
                        }
                    } catch (IOException x) {
                        x.printStackTrace();
                    }
                }
            }

            boolean valid = key.reset();
        }
    }
}
