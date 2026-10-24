/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kongbai.kculauncher.core.download.liteloader;

import com.kongbai.kculauncher.core.download.BMCLAPIDownloadProvider;
import com.kongbai.kculauncher.core.download.ComponentRemoteVersion;
import com.kongbai.kculauncher.core.download.ComponentVersionList;
import com.kongbai.kculauncher.core.task.GetTask;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.util.gson.JsonUtils;
import com.kongbai.kculauncher.core.util.io.NetworkUtils;

import java.util.Collections;

public final class LiteLoaderBMCLVersionList extends ComponentVersionList<LiteLoaderRemoteVersion> {
    private final BMCLAPIDownloadProvider downloadProvider;

    public LiteLoaderBMCLVersionList(BMCLAPIDownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return false;
    }

    private static final class LiteLoaderBMCLVersion {

        private final LiteLoaderVersion build;
        private final String version;

        public LiteLoaderBMCLVersion(LiteLoaderVersion build, String version) {
            this.build = build;
            this.version = version;
        }
    }

    @Override
    public Task<?> refreshAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Task<?> refreshAsync(String gameVersion) {
        return new GetTask(NetworkUtils.toURL(NetworkUtils.withQuery(
                        downloadProvider.getApiRoot() + "/liteloader/list", Collections.singletonMap("mcversion", gameVersion))))
                .thenApplyAsync(json -> JsonUtils.fromMaybeMalformedJson(json, LiteLoaderBMCLVersion.class))
                .thenAcceptAsync(v -> {
                    lock.writeLock().lock();
                    try {
                        versions.clear();
                        if (v == null)
                            return;
                        versions.put(gameVersion, new LiteLoaderRemoteVersion(
                                gameVersion, v.version, ComponentRemoteVersion.Type.UNCATEGORIZED,
                                Collections.singletonList(NetworkUtils.withQuery(
                                        downloadProvider.injectURL("https://bmclapi2.bangbang93.com/liteloader/download"),
                                        Collections.singletonMap("version", v.version)
                                )),
                                v.build.getTweakClass(), v.build.getLibraries()
                        ));
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }
}
