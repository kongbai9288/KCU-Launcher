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
package com.kongbai.kculauncher.core.download.game;

import static com.kongbai.kculauncher.core.util.Logging.LOG;

import com.kongbai.kculauncher.core.download.ComponentVersionList;
import com.kongbai.kculauncher.core.download.DownloadProvider;
import com.kongbai.kculauncher.core.task.GetTask;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.util.gson.JsonUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;

public final class GameVersionList extends ComponentVersionList<GameRemoteVersion> {
    private final DownloadProvider downloadProvider;

    public GameVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return true;
    }

    @Override
    protected Collection<GameRemoteVersion> getVersionsImpl(String gameVersion) {
        return versions.values();
    }

    @Override
    public Task<?> refreshAsync() {
        return new GetTask(downloadProvider.getVersionListURLs()).thenGetJsonAsync(GameRemoteVersions.class)
                .thenAcceptAsync(root -> {
                    GameRemoteVersions unlistedVersions = null;
                    try (Reader input = new InputStreamReader(
                            GameVersionList.class.getResourceAsStream("/assets/game/unlisted-versions.json"))) {
                        unlistedVersions = JsonUtils.GSON.fromJson(input, GameRemoteVersions.class);
                    } catch (Throwable e) {
                        LOG.log(Level.WARNING, "Failed to load unlisted versions", e);
                    }

                    lock.writeLock().lock();
                    try {
                        versions.clear();

                        if (unlistedVersions != null) {
                            for (GameRemoteVersionInfo unlistedVersion : unlistedVersions.getVersions()) {
                                versions.put(unlistedVersion.getGameVersion(), new GameRemoteVersion(
                                        unlistedVersion.getGameVersion(),
                                        unlistedVersion.getGameVersion(),
                                        Collections.singletonList(unlistedVersion.getUrl()),
                                        unlistedVersion.getType(), unlistedVersion.getReleaseTime()));
                            }
                        }

                        for (GameRemoteVersionInfo remoteVersion : root.getVersions()) {
                            versions.put(remoteVersion.getGameVersion(), new GameRemoteVersion(
                                    remoteVersion.getGameVersion(),
                                    remoteVersion.getGameVersion(),
                                    Collections.singletonList(remoteVersion.getUrl()),
                                    remoteVersion.getType(), remoteVersion.getReleaseTime()));
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }
}
