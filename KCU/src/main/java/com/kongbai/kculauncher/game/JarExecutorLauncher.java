package com.kongbai.kculauncher.game;

import android.content.Context;

import com.kongbai.kculauncher.mio.JavaManager;
import com.kongbai.kculauncher.mio.manager.RendererManager;
import com.kongbai.kculauncher.setting.Profiles;
import com.kongbai.kculauncher.launcher.KCUConfig;
import com.kongbai.kculauncher.launcher.KCULauncher;
import com.kongbai.kculauncher.launcher.bridge.KCUBridge;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.launch.CacioJavaArgs;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.platform.CommandBuilder;
import com.kongbai.kculauncher.core.util.platform.MemoryUtils;

import java.io.IOException;
import java.util.List;

public class JarExecutorLauncher {

    private final Context context;

    private String destJarPath;
    private int javaVersion;

    public JarExecutorLauncher(Context context) {
        this.context = context;
    }

    public void setInfo(String destJarPath, int javaVersion) {
        this.destJarPath = destJarPath;
        this.javaVersion = javaVersion;
    }

    private CommandBuilder generateCommandLine(String args) {
        CommandBuilder res = new CommandBuilder();

        CacioJavaArgs.addCacioArgs(
                res,
                KCUBridge.DEFAULT_WIDTH + "x" + KCUBridge.DEFAULT_HEIGHT,
                "javax.swing.plaf.metal.MetalLookAndFeel",
                null,
                true,
                javaVersion == 8
        );

        res.addDefault("-Xms", MemoryUtils.findBestRAMAllocation(context) + "m");
        res.addDefault("-Xmx", MemoryUtils.findBestRAMAllocation(context) + "m");

        res.addDefault("-Duser.home=", Profiles.getSelectedProfile().getGameDir().getParent());
        res.addDefault("-Djava.io.tmpdir=", KCUPath.CACHE_DIR);
        res.addDefault("-Dorg.lwjgl.opengl.libname=", "${gl_lib_name}");

        if (args != null) {
            for (String s : args.split(" ")) {
                res.add(s);
            }
        } else {
            res.add("-jar");
            res.add(destJarPath);
        }
        return res;
    }

    public KCUBridge launch(String args) throws IOException, InterruptedException {
        final CommandBuilder command = generateCommandLine(args);

        List<String> rawCommandLine = command.asList();

        if (rawCommandLine.stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalStateException("Illegal command line " + rawCommandLine);
        }

        String[] finalArgs = rawCommandLine.toArray(new String[0]);

        KCUConfig config = new KCUConfig(
                context,
                KCUPath.LOG_DIR,
                JavaManager.getSuitableJavaVersion(javaVersion).getJavaPath(null),
                Profiles.getSelectedProfile().getGameDir().getAbsolutePath(),
                RendererManager.RENDERER_GL4ES,
                finalArgs
        );
        return KCULauncher.launchJarExecutor(config);
    }
}
