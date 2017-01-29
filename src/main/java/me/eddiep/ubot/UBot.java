package me.eddiep.ubot;

import me.eddiep.ubot.module.ErrorNotifier;
import me.eddiep.ubot.module.Logger;
import me.eddiep.ubot.module.UpdateScheduler;
import me.eddiep.ubot.module.VersionFetcher;
import me.eddiep.ubot.module.impl.defaults.DefaultErrorNotifier;
import me.eddiep.ubot.module.impl.defaults.DefaultLogger;
import me.eddiep.ubot.module.impl.defaults.DefaultUpdateNotifier;
import me.eddiep.ubot.module.impl.defaults.DefaultVersionFetcher;
import me.eddiep.ubot.utils.CancelToken;
import me.eddiep.ubot.utils.PRunnable;
import me.eddiep.ubot.utils.Schedule;
import me.eddiep.ubot.utils.UpdateType;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;

public final class UBot {
    private ErrorNotifier errorModule;
    private UpdateScheduler updateModule;
    private Logger loggerModule;
    private VersionFetcher versionModule;

    private File gitRepoFolder;
    private Thread updateThread;
    private Repository gitRepo;
    private Git git;
    private Schedule currentTask;

    public UBot(File gitRepo) {
       init(gitRepo);
    }

    public UBot(File gitRepo, UpdateScheduler updateModule) {
        this.updateModule = updateModule;
        init(gitRepo);
    }

    public UBot(File gitRepo, UpdateScheduler updateModule, Logger logger) {
        this.updateModule = updateModule;
        this.loggerModule = logger;
        init(gitRepo);
    }

    public UBot(File gitRepo, UpdateScheduler updateModule, Logger logger, ErrorNotifier errorModule) {
        this.updateModule = updateModule;
        this.loggerModule = logger;
        this.errorModule = errorModule;
        init(gitRepo);
    }

    public UBot(File gitRepo, UpdateScheduler updateModule, Logger logger, ErrorNotifier errorModule, VersionFetcher versionModule) {
        this.updateModule = updateModule;
        this.loggerModule = logger;
        this.errorModule = errorModule;
        this.versionModule = versionModule;
        init(gitRepo);
    }

    private void init(File gitRepo) {
        if (!gitRepo.exists() || !gitRepo.isDirectory())
            throw new IllegalArgumentException("The directory " + gitRepo.getAbsolutePath() + " does not exist or is not a directory!");

        File test = new File(gitRepo, ".git");
        if (test.exists() && test.isDirectory()) {
            loggerModule.log(".git folder found in repo folder. Using .git folder");
            gitRepo = test;
        }

        this.gitRepoFolder = gitRepo;
        setDefaultModules();

        errorModule.init();
        loggerModule.init();
        updateModule.init();
        versionModule.init();


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

    public ErrorNotifier getErrorModule() {
        return errorModule;
    }

    public void setErrorModule(ErrorNotifier errorModule) {
        this.errorModule.dispose();
        this.errorModule = errorModule;
        this.errorModule.init();
    }

    public UpdateScheduler getUpdateModule() {
        return updateModule;
    }

    public void setUpdateModule(UpdateScheduler updateModule) {
        this.updateModule.dispose();
        this.updateModule = updateModule;
        this.updateModule.init();
    }

    public Logger getLoggerModule() {
        return loggerModule;
    }

    public void setLoggerModule(Logger loggerModule) {
        this.loggerModule.dispose();
        this.loggerModule = loggerModule;
        this.loggerModule.init();
    }

    public VersionFetcher getVersionModule() {
        return versionModule;
    }

    public void setVersionModule(VersionFetcher versionModule) {
        this.versionModule.dispose();
        this.versionModule = versionModule;
        this.versionModule.init();
    }

    public void forceCheck() {
        updateThread.interrupt(); //This will wake-up the update thread which will cause a check
    }



    private void setDefaultModules() {
        if (updateModule == null)
            updateModule = new DefaultUpdateNotifier();

        if (errorModule == null)
            errorModule = new DefaultErrorNotifier();

        if (loggerModule == null)
            loggerModule = new DefaultLogger();

        if (versionModule == null)
            versionModule = new DefaultVersionFetcher(this);
    }

    void check() {
        if (currentTask != null) {
            if (currentTask.isReady()) {
                currentTask.execute();
            }
        }

        updateModule.onPreCheck(this);

        PullCommand pull = git.pull();
        try {
            PullResult result = pull.call();

            if (!result.isSuccessful()) {
                errorModule.error(new RuntimeException("Failed to pull from LIVE. Please check the state of the repo!"));
                return;
            }

            UpdateType type = versionModule.fetchVersion();
            if (type == UpdateType.NONE)
                return;

            Schedule<UpdateType> task = updateModule.shouldBuild(type, this);
            task.attach(new PRunnable<UpdateType>() {

                @Override
                public void run(UpdateType param) {
                    build(param);
                }
            }, type);
            this.currentTask = task;
            versionModule.onUpdateScheduled();

            if (task.isReady()) {
                task.execute();
            }
        } catch (GitAPIException e) {
            errorModule.error(e);
        }
    }

    private void build(UpdateType type) {
        File buildScript = new File(gitRepoFolder.getParentFile(), "build.sh");
        try {
            loggerModule.log("Running build script..");
            Process p = new ProcessBuilder("sh", buildScript.getAbsolutePath()).directory(gitRepoFolder.getParentFile()).start();
            int exitVal = p.waitFor();

            if (exitVal != 0) {
                loggerModule.warning("Build script did not exit with proper exit value (got " + exitVal + ")");
                return;
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
        File patchScript = new File(gitRepoFolder.getParentFile(), "patch.sh");
        loggerModule.log("Running patch script..");

        try {
            Process p = new ProcessBuilder("sh", patchScript.getAbsolutePath()).directory(gitRepoFolder.getParentFile()).start();
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

    public File getGitRepoFolder() {
        return gitRepoFolder;
    }
}
