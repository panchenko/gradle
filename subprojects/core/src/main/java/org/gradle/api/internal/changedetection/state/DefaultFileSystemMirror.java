/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalMissingFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotRoot;
import org.gradle.api.internal.tasks.execution.TaskOutputChangesListener;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.classpath.CachedJarFileStore;
import org.gradle.internal.file.DefaultFileHierarchySet;
import org.gradle.internal.file.FileHierarchySet;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * See {@link DefaultFileSystemSnapshotter} for some more details
 */
public class DefaultFileSystemMirror implements FileSystemMirror, TaskOutputChangesListener, RootBuildLifecycleListener {
    // Maps from interned absolute path for a file to known details for the file.
    private final Map<String, FileSnapshot> files = new ConcurrentHashMap<String, FileSnapshot>();
    private final Map<String, FileSnapshot> cacheFiles = new ConcurrentHashMap<String, FileSnapshot>();
    // Maps from interned absolute path for a directory to known details for the directory.
    private final Map<String, FileTreeSnapshot> trees = new ConcurrentHashMap<String, FileTreeSnapshot>();
    private final Map<String, FileTreeSnapshot> cacheTrees = new ConcurrentHashMap<String, FileTreeSnapshot>();
    // Maps from interned absolute path to a snapshot
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<String, Snapshot>();
    private final Map<String, Snapshot> cacheSnapshots = new ConcurrentHashMap<String, Snapshot>();
    private final FileHierarchySet cachedDirectories;

    private final PhysicalSnapshotRoot root = new PhysicalSnapshotRoot();

    public DefaultFileSystemMirror(List<CachedJarFileStore> fileStores) {
        FileHierarchySet cachedDirectories = DefaultFileHierarchySet.of();
        for (CachedJarFileStore fileStore : fileStores) {
            for (File file : fileStore.getFileStoreRoots()) {
                cachedDirectories = cachedDirectories.plus(file);
            }
        }
        this.cachedDirectories = cachedDirectories;
    }

    @Nullable
    @Override
    public FileSnapshot getFile(String path) {
        // Could potentially also look whether we have the details for an ancestor directory tree
        // Could possibly infer that the path refers to a directory, if we have details for a descendant path (and it's not a missing file)
        if (cachedDirectories.contains(path)) {
            return cacheFiles.get(path);
        } else {
            PhysicalSnapshot physicalSnapshot = root.find(path);
            if (physicalSnapshot == null) {
                return null;
            }
            if (physicalSnapshot instanceof PhysicalDirectorySnapshot) {
                return new DirectoryFileSnapshot(path, new RelativePath(false, physicalSnapshot.getName()), true);
            }
            if (physicalSnapshot instanceof PhysicalFileSnapshot) {
                PhysicalFileSnapshot file = (PhysicalFileSnapshot) physicalSnapshot;
                return new RegularFileSnapshot(path, new RelativePath(true, file.getName()), true, new FileHashSnapshot(file.getHash(), file.getTimestamp()));
            }
            if (physicalSnapshot instanceof PhysicalMissingFileSnapshot) {
                return new MissingFileSnapshot(path, new RelativePath(true, physicalSnapshot.getName()));
            }
            throw new IllegalStateException("Only files, dirs and missing files are possible");
        }
    }

    @Override
    public void putFile(FileSnapshot file) {
        if (cachedDirectories.contains(file.getPath())) {
            cacheFiles.put(file.getPath(), file);
        } else {
            PhysicalSnapshot snapshot;
            switch (file.getType()) {
                case Directory:
                    snapshot = new PhysicalDirectorySnapshot(file.getName());
                    break;
                case Missing:
                    snapshot = new PhysicalMissingFileSnapshot(file.getName());
                    break;
                case RegularFile:
                    FileHashSnapshot content = (FileHashSnapshot) file.getContent();
                    snapshot = new PhysicalFileSnapshot(file.getName(), content.getLastModified(),  content.getContentMd5());
                    break;
                default:
                    throw new IllegalStateException("Unknown file type");
            }
            root.add(file.getPath(), snapshot);
        }
    }

    @Nullable
    @Override
    public Snapshot getContent(String path) {
        if (cachedDirectories.contains(path)) {
            return cacheSnapshots.get(path);
        } else {
            return snapshots.get(path);
        }
    }

    @Override
    public void putContent(String path, Snapshot snapshot) {
        if (cachedDirectories.contains(path)) {
            cacheSnapshots.put(path, snapshot);
        } else {
            snapshots.put(path, snapshot);
        }
    }

    @Nullable
    @Override
    public FileTreeSnapshot getDirectoryTree(String path) {
        // Could potentially also look whether we have the details for an ancestor directory tree
        // Could possibly also short-circuit some scanning if we have details for some sub trees
        if (cachedDirectories.contains(path)) {
            return cacheTrees.get(path);
        } else {
            return trees.get(path);
        }
    }

    @Override
    public void putDirectory(FileTreeSnapshot directory) {
        if (cachedDirectories.contains(directory.getPath())) {
            cacheTrees.put(directory.getPath(), directory);
        } else {
            trees.put(directory.getPath(), directory);
        }
    }

    @Override
    public void beforeTaskOutputChanged() {
        // When the task outputs are generated, throw away all state for files that do not live in an append-only cache.
        // This is intentionally very simple, to be improved later
        root.clear();
        trees.clear();
        snapshots.clear();
    }

    @Override
    public void afterStart() {
    }

    @Override
    public void beforeComplete() {
        // We throw away all state between builds
        root.clear();
        cacheFiles.clear();
        trees.clear();
        cacheTrees.clear();
        snapshots.clear();
        cacheSnapshots.clear();
    }
}
