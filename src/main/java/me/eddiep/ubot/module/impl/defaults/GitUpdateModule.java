package me.eddiep.ubot.module.impl.defaults;

import me.eddiep.ubot.UBot;
import me.eddiep.ubot.module.UpdateModule;
import me.eddiep.ubot.utils.UpdateType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;

public class GitUpdateModule implements UpdateModule {
    private String runningVersion;

    protected UBot ubot;
    protected File verFile;

    public GitUpdateModule(UBot ubot) {
        this(ubot, new File(ubot.getGitRepoFolder().getParentFile(), ".version"));
    }

    public GitUpdateModule(UBot ubot, File versionFile) {
        this.ubot = ubot;
        this.verFile = versionFile;

        if (!verFile.exists()) {
            ubot.getLoggerModule().warning("No version found! Assuming version 1.0.0");
            String ver = "1.0.0";
            saveVersion(ver);
        }
    }

    protected String fetchNewVersion() {
        return fetchGitVersion();
    }

    protected String fetchGitVersion() {
        try (Scanner scanner = new Scanner(verFile)) {
            String version = scanner.nextLine();
            validateVersion(version);
            return version;
        } catch (FileNotFoundException e) {
            ubot.getErrorModule().error(e);
        }

        return null;
    }

    protected void saveVersion(String version) {
        validateVersion(version);

        File verFile = new File(ubot.getGitRepoFolder().getParentFile(), ".version");

        try (PrintWriter writer = new PrintWriter(verFile)) {
            writer.write(version);
        } catch (FileNotFoundException e) {
            ubot.getErrorModule().error(e);
            return;
        }

        this.runningVersion = version;
    }

    protected void validateVersion(String version) {
        String[] split = version.split("\\.");
        if (split.length != 3)
            throw new IllegalArgumentException("Invalid version: " + version);

        try {
            int ver1 = Integer.parseInt(split[0]);
            int ver2 = Integer.parseInt(split[1]);
        } catch (Throwable t) {
            throw new IllegalArgumentException("Invalid version: " + version, t);
        }

        //The third parameter can have additional info in it

        //Remove pre-release and build metadata
        String temp = split[2].split("-")[0].split("\\+")[0];

        try {
            int ver3 = Integer.parseInt(temp);
        } catch (Throwable t) {
            throw new IllegalArgumentException("Invalid version: " + version, t);
        }
    }

    protected void setRunningVersion(String newVersion) {
        this.runningVersion = newVersion;

        if (this.runningVersion.split("-").length > 1) {
            String hash = this.runningVersion.split("-")[1];

            ubot.checkoutCommit(hash);
        }
    }

    @Override
    public void init() {
        this.runningVersion = fetchGitVersion();
    }

    @Override
    public void dispose() {

    }

    @Override
    public String getRunningVersion() {
        return this.runningVersion;
    }

    @Override
    public UpdateType checkForUpdates() {
        if (!ubot.pull()) { //Pull from repo
            return UpdateType.NONE;
        }

        String updatedVersion = fetchNewVersion();

        if (runningVersion.equals(updatedVersion))
            return UpdateType.NONE;

        UpdateType type = UpdateType.getUpdateType(runningVersion, updatedVersion);

        if (type != UpdateType.NONE) {
            setRunningVersion(updatedVersion);
        }

        return type;
    }
}
