package me.eddiep.ubot;

import me.eddiep.ubot.module.ErrorNotifier;
import me.eddiep.ubot.module.Logger;
import me.eddiep.ubot.module.UpdateNotifier;
import me.eddiep.ubot.module.impl.DefaultErrorNotifier;
import me.eddiep.ubot.module.impl.DefaultLogger;
import me.eddiep.ubot.module.impl.DefaultUpdateNotifier;
import me.eddiep.ubot.utils.CancelToken;
import me.eddiep.ubot.utils.PRunnable;
import me.eddiep.ubot.utils.Schedule;
import me.eddiep.ubot.utils.UpdateType;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

public final class UBot {
    private ErrorNotifier errorModule;
    private UpdateNotifier updateModule;
    private Logger loggerModule;
    private String runningVersion;
    private File gitRepoFolder;
    private Thread updateThread;
    private Repository gitRepo;
    private Git git;
    private Schedule currentTask;

    public UBot(File gitRepo) {
       init(gitRepo);
    }

    public UBot(File gitRepo, UpdateNotifier updateModule) {
        this.updateModule = updateModule;
        init(gitRepo);
    }

    public UBot(File gitRepo, UpdateNotifier updateModule, Logger logger) {
        this.updateModule = updateModule;
        this.loggerModule = logger;
        init(gitRepo);
    }

    public UBot(File gitRepo, UpdateNotifier updateModule, Logger logger, ErrorNotifier errorModule) {
        this.updateModule = updateModule;
        this.loggerModule = logger;
        this.errorModule = errorModule;
        init(gitRepo);
    }

    private void init(File gitRepo) {
        if (!gitRepo.exists() || !gitRepo.isDirectory())
            throw new IllegalArgumentException("The directory " + gitRepo.getAbsolutePath() + " does not exist or is not a directory!");

        this.gitRepoFolder = gitRepo;
        setDefaultModules();
        this.runningVersion = fetchGitVersion();

        try {
           this.gitRepo = new FileRepositoryBuilder()
                    .setGitDir(gitRepo)
                    .build();
            this.git = new Git(this.gitRepo);
        } catch (IOException e) {
            errorModule.error(e);
        }
    }

    public void startSync(CancelToken token, long interval) {
        if (token == null)
            throw new IllegalArgumentException("The cancel token cannot be null!");

        new UBotLoop(this, token, interval).run();
    }

    public void startSync(CancelToken token) {
        startSync(token, 300000);
    }

    public CancelToken startAsync() {
        return startAsync(300000);
    }

    public CancelToken startAsync(long interval) {
        CancelToken token = new CancelToken();
        updateThread = new Thread(new UBotLoop(this, token, interval)); //Every 5 minutes
        updateThread.start();
        return token;
    }

    public String fetchGitVersion() {
        File verFile = new File(gitRepoFolder, "version");
        if (!verFile.exists()) {
            loggerModule.warning("No version found! Assuming version 1.0.0");
            String ver = "1.0.0";
            saveVersion(ver);
            return ver;
        }

        try (Scanner scanner = new Scanner(verFile)) {
            String version = scanner.nextLine();
            validateVersion(version);
            return version;
        } catch (FileNotFoundException e) {
            errorModule.error(e);
        }

        return null;
    }

    public void saveVersion(String version) {
        validateVersion(version);

        File verFile = new File(gitRepoFolder, "version");

        try (PrintWriter writer = new PrintWriter(verFile)) {
            writer.write(version);
        } catch (FileNotFoundException e) {
            errorModule.error(e);
            return;
        }

        this.runningVersion = version;
    }

    public ErrorNotifier getErrorModule() {
        return errorModule;
    }

    public void setErrorModule(ErrorNotifier errorModule) {
        this.errorModule = errorModule;
    }

    public UpdateNotifier getUpdateModule() {
        return updateModule;
    }

    public void setUpdateModule(UpdateNotifier updateModule) {
        this.updateModule = updateModule;
    }

    public Logger getLoggerModule() {
        return loggerModule;
    }

    public void setLoggerModule(Logger loggerModule) {
        this.loggerModule = loggerModule;
    }

    private void validateVersion(String version) {
        String[] split = version.split(".");
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

    public UpdateType getUpdateType(String oldVersion, String newVersion) {
        String[] oldVersionNums = oldVersion.split(".");
        String[] newVersionNums = newVersion.split(".");
        try {
            int oMajor = Integer.parseInt(oldVersionNums[0]);
            int oMinor = Integer.parseInt(oldVersionNums[1]);
            int oBugfix = Integer.parseInt(oldVersionNums[2].split("\\+")[0]);
            int oUrgent = Integer.parseInt(oldVersionNums[2].split("\\+")[1]);

            int nMajor = Integer.parseInt(newVersionNums[0]);
            int nMinor = Integer.parseInt(newVersionNums[1]);
            int nBugfix = Integer.parseInt(newVersionNums[2].split("\\+")[0]);
            int nUrgent = Integer.parseInt(newVersionNums[2].split("\\+")[1]);

            if (nUrgent > oUrgent) { //This is an urgent update, regardless of other versions
                return UpdateType.URGENT;
            }

            if (nMajor > oMajor) {
                return UpdateType.MAJOR;
            }

            if (nMinor > oMinor) {
                return UpdateType.MINOR;
            }

            if (nBugfix > oBugfix) {
                return UpdateType.BUGFIX;
            }

            return UpdateType.NONE;
        } catch (Throwable t) {
            return UpdateType.NONE;
        }
    }

    private void setDefaultModules() {
        if (updateModule == null)
            updateModule = new DefaultUpdateNotifier();

        if (errorModule == null)
            errorModule = new DefaultErrorNotifier();

        if (loggerModule == null)
            loggerModule = new DefaultLogger();
    }

    void check() {
        if (currentTask != null) {
            if (currentTask.isReady()) {
                currentTask.execute();
            }

            return;
        }

        updateModule.onPreCheck(this);

        PullCommand pull = git.pull();
        try {
            PullResult result = pull.call();

            if (!result.isSuccessful()) {
                errorModule.error(new RuntimeException("Failed to pull from LIVE. Please check the state of the repo!"));
                return;
            }

            String version = fetchGitVersion();

            if (runningVersion.equals(version))
                return; //They are the same, no need to do anymore checking

            UpdateType type = getUpdateType(runningVersion, version);
            if (type == UpdateType.NONE)
                return; //No update was detected

            Schedule<UpdateType> task = updateModule.shouldBuild(type, this);
            task.attach(new PRunnable<UpdateType>() {

                @Override
                public void run(UpdateType param) {
                    build(param);
                }
            }, type);
            this.currentTask = task;

            if (task.isReady()) {
                task.execute();
            }
        } catch (GitAPIException e) {
            errorModule.error(e);
        }
    }

    private void build(UpdateType type) {
        File buildScript = new File(gitRepoFolder, "build.sh");
        try {
            loggerModule.log("Running build script..");
            Process p = new ProcessBuilder("sh", buildScript.getAbsolutePath()).start();
            int exitVal = p.waitFor();

            if (exitVal != 0) {
                loggerModule.warning("Build script did not exit with proper exit value (got " + exitVal + ")");
            } else {
                loggerModule.log("Build complete");
            }

            Schedule<UpdateType> shouldPatch = updateModule.shouldPatch(type, this);

            shouldPatch.attach(new PRunnable<UpdateType>() {
                @Override
                public void run(UpdateType param) {
                    patch(param);
                }
            }, type);
            this.currentTask = shouldPatch;

            if (shouldPatch.isReady()) {
                shouldPatch.execute();
            }
        } catch (IOException | InterruptedException e) {
            errorModule.error(e);
        }
    }

    private void patch(UpdateType type) {
        File patchScript = new File(gitRepoFolder, "patch.sh");
        loggerModule.log("Running patch script..");

        try {
            Process p = new ProcessBuilder("sh", patchScript.getAbsolutePath()).start();
            int exitVal = p.waitFor();

            if (exitVal != 0) {
                loggerModule.warning("Build script did not exit with proper exit value (got " + exitVal + ")");
            } else {
                loggerModule.log("Patch complete");
            }

            updateModule.patchComplete(type, this);

            this.currentTask = null;
        } catch (IOException | InterruptedException e) {
            errorModule.error(e);
        }
    }
}
