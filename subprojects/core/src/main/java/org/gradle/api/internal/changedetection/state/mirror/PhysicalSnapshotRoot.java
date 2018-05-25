/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror;

import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicalSnapshotRoot {
    private static final String FILE_PATH_SEPARATORS = File.separatorChar != '/' ? ("/" + File.separator) : File.separator;

    private final ConcurrentHashMap<String, PhysicalSnapshot> children = new ConcurrentHashMap<String, PhysicalSnapshot>();

    @Nullable
    public PhysicalSnapshot find(String path) {
        String[] segments = getPathSegments(path);
        PhysicalSnapshot child = children.get(segments[0]);
        if (child != null) {
            return child.find(segments, 1);
        }
        return null;
    }

    public void add(String path, PhysicalSnapshot snapshot) {
        String[] segments = getPathSegments(path);
        String currentSegment = segments[0];
        PhysicalSnapshot child = children.get(currentSegment);
        if (child == null) {
            PhysicalSnapshot newChild;
            if (segments.length == 1) {
                newChild = snapshot;
            } else {
                newChild = new PhysicalDirectorySnapshot(currentSegment);
            }
            child = children.putIfAbsent(currentSegment, newChild);
            if (child == null) {
                child = newChild;
            }
        }
        child.add(segments, 1, snapshot);
    }

    private String[] getPathSegments(String path) {
        return StringUtils.split(path, FILE_PATH_SEPARATORS);
    }

    public void clear() {
        children.clear();
    }
}
