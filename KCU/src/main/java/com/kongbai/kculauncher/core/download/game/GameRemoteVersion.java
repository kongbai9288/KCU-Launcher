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

import com.kongbai.kculauncher.core.download.DefaultDependencyManager;
import com.kongbai.kculauncher.core.download.ComponentRemoteVersion;
import com.kongbai.kculauncher.core.game.GameComponentType;
import com.kongbai.kculauncher.core.game.ReleaseType;
import com.kongbai.kculauncher.core.game.Version;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.util.versioning.GameVersionNumber;

import java.time.Instant;
import java.util.List;

public final class GameRemoteVersion extends ComponentRemoteVersion {

    private final ReleaseType type;

    public GameRemoteVersion(String gameVersion, String selfVersion, List<String> url, ReleaseType type, Instant releaseDate) {
        super(GameComponentType.GAME, gameVersion, selfVersion, releaseDate, getReleaseType(type), url);
        this.type = type;
    }

    public ReleaseType getType() {
        return type;
    }

    @Override
    public Task<Version> getInstallTask(DefaultDependencyManager dependencyManager, Version baseVersion) {
        return new GameInstallTask(dependencyManager, baseVersion, this);
    }

    @Override
    public int compareTo(ComponentRemoteVersion o) {
        if (!(o instanceof GameRemoteVersion))
            return 0;
        int dateCompare = o.getReleaseDate().compareTo(getReleaseDate());
        return dateCompare != 0 ? dateCompare : GameVersionNumber.compare(getGameVersion(), o.getGameVersion());
    }

    private static Type getReleaseType(ReleaseType type) {
        if (type == null) return Type.UNCATEGORIZED;
        switch (type) {
            case RELEASE:
                return Type.RELEASE;
            case SNAPSHOT:
                return Type.SNAPSHOT;
            case UNKNOWN:
                return Type.UNCATEGORIZED;
            case PENDING:
                return Type.PENDING;
            case UNOBFUSCATED:
                return Type.UNOBFUSCATED;
            default:
                return Type.OLD;
        }
    }
}
