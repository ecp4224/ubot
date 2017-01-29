package me.eddiep.ubot.module.impl.defaults;

import me.eddiep.ubot.UBot;
import me.eddiep.ubot.module.VersionFetcher;
import me.eddiep.ubot.utils.UpdateType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;

public class DefaultVersionFetcher implements VersionFetcher {
    protected String runningVersion;
    protected UBot ubot;

    public DefaultVersionFetcher(UBot ubot) {
        this.ubot = ubot;
    }

    protected String fetchGitVersion() {
        File verFile = new File(ubot.getGitRepoFolder().getParentFile(), "version");
        if (!verFile.exists()) {
            ubot.getLoggerModule().warning("No version found! Assuming version 1.0.0");
            String ver = "1.0.0";
            saveVersion(ver);
            return ver;
        }

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

        File verFile = new File(ubot.getGitRepoFolder().getParentFile(), "version");

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

    @Override
    public void init() {
        this.runningVersion = fetchGitVersion();
    }

    @Override
    public void dispose() {

    }

    @Override
    public UpdateType fetchVersion() {
        String version = fetchGitVersion();

        if (runningVersion.equals(version))
            return UpdateType.NONE; //They are the same, no need to do anymore checking

        return UpdateType.getUpdateType(runningVersion, version);
    }

    @Override
    public void onUpdateScheduled() {
        this.runningVersion = fetchGitVersion();
    }

}
