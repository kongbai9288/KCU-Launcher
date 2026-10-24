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
package com.kongbai.kculauncher.core.auth.offline;

import static com.kongbai.kculauncher.core.util.Lang.mapOf;
import static com.kongbai.kculauncher.core.util.Pair.pair;
import static java.util.Objects.requireNonNull;

import com.kongbai.kculauncher.core.auth.Account;
import com.kongbai.kculauncher.core.auth.AuthInfo;
import com.kongbai.kculauncher.core.auth.AuthenticationException;
import com.kongbai.kculauncher.core.auth.authlibinjector.AuthlibInjectorArtifactInfo;
import com.kongbai.kculauncher.core.auth.authlibinjector.AuthlibInjectorArtifactProvider;
import com.kongbai.kculauncher.core.auth.authlibinjector.AuthlibInjectorDownloadException;
import com.kongbai.kculauncher.core.auth.yggdrasil.Texture;
import com.kongbai.kculauncher.core.auth.yggdrasil.TextureType;
import com.kongbai.kculauncher.core.fakefx.beans.binding.Bindings;
import com.kongbai.kculauncher.core.fakefx.beans.binding.ObjectBinding;
import com.kongbai.kculauncher.core.game.Arguments;
import com.kongbai.kculauncher.core.game.LaunchOptions;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.ToStringBuilder;
import com.kongbai.kculauncher.core.util.gson.UUIDTypeAdapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class OfflineAccount extends Account {

    private final AuthlibInjectorArtifactProvider downloader;
    private final String username;
    private final UUID uuid;
    private Skin skin;

    protected OfflineAccount(AuthlibInjectorArtifactProvider downloader, String username, UUID uuid, Skin skin) {
        this.downloader = requireNonNull(downloader);
        this.username = requireNonNull(username);
        this.uuid = requireNonNull(uuid);
        this.skin = skin;

        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
    }

    public AuthlibInjectorArtifactProvider getDownloader() {
        return downloader;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getCharacter() {
        return username;
    }

    @Override
    public String getIdentifier() {
        return username + ":" + username;
    }

    public Skin getSkin() {
        return skin;
    }

    public void setSkin(Skin skin) {
        this.skin = skin;
        invalidate();
    }

    protected boolean loadAuthlibInjector(Skin skin) {
        return skin != null && skin.type() != Skin.Type.DEFAULT;
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        // Using "legacy" user type here because "mojang" user type may cause "invalid session token" or "disconnected" when connecting to a game server.
        AuthInfo authInfo = new AuthInfo(username, uuid, UUIDTypeAdapter.fromUUID(UUID.randomUUID()), AuthInfo.USER_TYPE_MSA, "{}");

        if (loadAuthlibInjector(skin)) {
            CompletableFuture<AuthlibInjectorArtifactInfo> artifactTask = CompletableFuture.supplyAsync(() -> {
                try {
                    return downloader.getArtifactInfo();
                } catch (IOException e) {
                    throw new CompletionException(new AuthlibInjectorDownloadException(e));
                }
            });

            AuthlibInjectorArtifactInfo artifact;
            try {
                artifact = artifactTask.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AuthenticationException(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof AuthenticationException) {
                    throw (AuthenticationException) e.getCause();
                } else {
                    throw new AuthenticationException(e.getCause());
                }
            }

            try {
                return new OfflineAuthInfo(authInfo, artifact);
            } catch (Exception e) {
                throw new AuthenticationException(e);
            }
        } else {
            return authInfo;
        }
    }

    private class OfflineAuthInfo extends AuthInfo {
        private final AuthlibInjectorArtifactInfo artifact;
        private YggdrasilServer server;

        public OfflineAuthInfo(AuthInfo authInfo, AuthlibInjectorArtifactInfo artifact) {
            super(authInfo.getUsername(), authInfo.getUUID(), authInfo.getAccessToken(), USER_TYPE_MSA, authInfo.getUserProperties());

            this.artifact = artifact;
        }

        @Override
        public Arguments getLaunchArguments(LaunchOptions options) throws IOException {

            server = new YggdrasilServer(0);
            server.start();

            try {
                server.addCharacter(new YggdrasilServer.Character(uuid, username,
                        skin != null ? skin.load().run() : null));
            } catch (IOException e) {
                // ignore
            } catch (Exception e) {
                throw new IOException(e);
            }

            return new Arguments().addJVMArguments(
                    "-javaagent:" + artifact.getLocation().toString() + "=" + "http://localhost:" + server.getListeningPort(),
                    "-Dauthlibinjector.side=client"
            );
        }
    }

    @Override
    public AuthInfo playOffline() throws AuthenticationException {
        return new AuthInfo(username, uuid, UUIDTypeAdapter.fromUUID(UUID.randomUUID()), AuthInfo.USER_TYPE_MSA, "{}");
    }

    @Override
    public Map<Object, Object> toStorage() {
        return mapOf(
                pair("uuid", UUIDTypeAdapter.fromUUID(uuid)),
                pair("username", username),
                pair("skin", skin == null ? null : skin.toStorage())
        );
    }

    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        Map<TextureType, Texture> map = new HashMap<>();
        map.put(TextureType.SKIN, new Texture("offline", null));
        return Bindings.createObjectBinding(() -> Optional.of(map));
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("username", username)
                .append("uuid", uuid)
                .toString();
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OfflineAccount another))
            return false;
        return isPortable() == another.isPortable() && username.equals(another.username);
    }
}
