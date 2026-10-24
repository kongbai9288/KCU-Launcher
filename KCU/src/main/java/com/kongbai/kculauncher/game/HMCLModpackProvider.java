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
package com.kongbai.kculauncher.game;

import com.google.gson.JsonParseException;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.core.download.DefaultDependencyManager;
import com.kongbai.kculauncher.core.game.Version;
import com.kongbai.kculauncher.core.mod.MismatchedModpackTypeException;
import com.kongbai.kculauncher.core.mod.Modpack;
import com.kongbai.kculauncher.core.mod.ModpackProvider;
import com.kongbai.kculauncher.core.mod.ModpackUpdateTask;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.gson.JsonUtils;
import com.kongbai.kculauncher.core.util.io.CompressingUtils;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

public final class HMCLModpackProvider implements ModpackProvider {
    public static final HMCLModpackProvider INSTANCE = new HMCLModpackProvider();

    @Override
    public String getName() {
        return "HMCL";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        return null;
    }

    @Override
    public Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, String name, File zipFile, Modpack modpack) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof HMCLModpackManifest))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        if (!(dependencyManager.getGameRepository() instanceof KCUGameRepository)) {
            throw new IllegalArgumentException("HMCLModpackProvider requires HMCLGameRepository");
        }

        KCUGameRepository repository = (KCUGameRepository) dependencyManager.getGameRepository();
        Profile profile = repository.getProfile();

        return new ModpackUpdateTask(dependencyManager.getGameRepository(), name, new HMCLModpackInstallTask(profile, zipFile, modpack, name));
    }

    @Override
    public Modpack readManifest(ZipFile file, Path path, Charset encoding) throws IOException, JsonParseException {
        String manifestJson = CompressingUtils.readTextZipEntry(file, "modpack.json");
        Modpack manifest = JsonUtils.fromNonNullJson(manifestJson, HMCLModpack.class).setEncoding(encoding);
        String gameJson = CompressingUtils.readTextZipEntry(file, "minecraft/pack.json");
        Version game = JsonUtils.fromNonNullJson(gameJson, Version.class);
        if (game.getJar() == null)
            if (StringUtils.isBlank(manifest.getVersion()))
                throw new JsonParseException("Cannot recognize the game version of modpack " + file + ".");
            else
                manifest.setManifest(HMCLModpackManifest.INSTANCE);
        else
            manifest.setManifest(HMCLModpackManifest.INSTANCE).setGameVersion(game.getJar());
        return manifest;
    }

    private static class HMCLModpack extends Modpack {
        @Override
        public Task<?> getInstallTask(DefaultDependencyManager dependencyManager, File zipFile, String name) {
            return new HMCLModpackInstallTask(((KCUGameRepository) dependencyManager.getGameRepository()).getProfile(), zipFile, this, name);
        }
    }

}
