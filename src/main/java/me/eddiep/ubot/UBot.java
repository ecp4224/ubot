package me.eddiep.ubot;

import me.eddiep.ubot.module.ErrorNotifier;
import me.eddiep.ubot.module.LogModule;
import me.eddiep.ubot.module.SchedulerModule;
import me.eddiep.ubot.module.UpdateModule;
import me.eddiep.ubot.module.impl.defaults.DefaultErrorNotifier;
import me.eddiep.ubot.module.impl.defaults.DefaultLogModule;
import me.eddiep.ubot.module.impl.defaults.DefaultUpdateNotifier;
import me.eddiep.ubot.module.impl.defaults.GitUpdateModule;
import me.eddiep.ubot.utils.CancelToken;
import me.eddiep.ubot.utils.PRunnable;
import me.eddiep.ubot.utils.Schedule;
import me.eddiep.ubot.utils.UpdateType;
import org.eclipse.jgit.api.CheckoutCommand;
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
    private SchedulerModule schedulerModule;
    private LogModule loggerModule;
    private UpdateModule updateModule;

    private File gitRepoFolder;
    private Thread updateThread;
    private Repository gitRepo;
    private Git git;
    private Schedule currentTask;

    public UBot(File gitRepo) {
       init(gitRepo);
    }

    public UBot(File gitRepo, SchedulerModule schedulerModule) {
        this.schedulerModule = schedulerModule;
        init(gitRepo);
    }

    public UBot(File gitRepo, SchedulerModule schedulerModule, LogModule logModule) {
        this.schedulerModule = schedulerModule;
        this.loggerModule = logModule;
        init(gitRepo);
    }

    public UBot(File gitRepo, SchedulerModule schedulerModule, LogModule logModule, ErrorNotifier errorModule) {
        this.schedulerModule = schedulerModule;
        this.loggerModule = logModule;
        this.errorModule = errorModule;
        init(gitRepo);
    }

    public UBot(File gitRepo, SchedulerModule schedulerModule, LogModule logModule, ErrorNotifier errorModule, UpdateModule updateModule) {
        this.schedulerModule = schedulerModule;
        this.loggerModule = logModule;
        this.errorModule = errorModule;
        this.updateModule = updateModule;
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
        schedulerModule.init();
        updateModule.init();


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

    public SchedulerModule getSchedulerModule() {
        return schedulerModule;
    }

    public void setSchedulerModule(SchedulerModule schedulerModule) {
        this.schedulerModule.dispose();
        this.schedulerModule = schedulerModule;
        this.schedulerModule.init();
    }

    public LogModule getLoggerModule() {
        return loggerModule;
    }

    public void setLoggerModule(LogModule loggerModule) {
        this.loggerModule.dispose();
        this.loggerModule = loggerModule;
        this.loggerModule.init();
    }

    public UpdateModule getUpdateModule() {
        return updateModule;
    }

    public void setUpdateModule(UpdateModule updateModule) {
        this.updateModule.dispose();
        this.updateModule = updateModule;
        this.updateModule.init();
    }

    public void forceCheck() {
        updateThread.interrupt(); //This will wake-up the update thread which will cause a check
    }

    public void checkoutCommit(String hash) {
        CheckoutCommand cmd = git.checkout();
        try {
            cmd.setName(hash).call();
        } catch (GitAPIException e) {
            errorModule.error(e);
        }
    }

    private void setDefaultModules() {
        if (schedulerModule == null)
            schedulerModule = new DefaultUpdateNotifier();

        if (errorModule == null)
            errorModule = new DefaultErrorNotifier();

        if (loggerModule == null)
            loggerModule = new DefaultLogModule();

        if (updateModule == null)
            updateModule = new GitUpdateModule(this);
    }

    public boolean pull() {
        PullCommand pull = git.pull();
        try {
            PullResult result = pull.call();

            if (!result.isSuccessful()) {
                errorModule.error(new RuntimeException("Failed to pull from LIVE. Please check the state of the repo!"));
                return false;
            }

            return true;
        } catch (GitAPIException e) {
            errorModule.error(e);
            return false;
        }
    }

    void check() {
        if (currentTask != null) {
            if (currentTask.isReady()) {
                currentTask.execute();
            }
        }

        schedulerModule.onPreCheck(this);
        UpdateType type = updateModule.checkForUpdates();
        if (type == UpdateType.NONE)
            return;

        Schedule<UpdateType> task = schedulerModule.shouldBuild(type, this);
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
    }

    private void build(UpdateType type) {
        File buildScript = new File(gitRepoFolder.getParentFile(), "build.sh");
        if (buildScript.exists()) {
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
            } catch (IOException | InterruptedException e) {
                errorModule.error(e);
            }
        }

        Schedule<UpdateType> shouldPatch = schedulerModule.shouldPatch(type, this);

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
    }

    private void patch(UpdateType type) {
        File patchScript = new File(gitRepoFolder.getParentFile(), "patch.sh");

        if (patchScript.exists()) {
            loggerModule.log("Running patch script..");

            try {
                Process p = new ProcessBuilder("sh", patchScript.getAbsolutePath()).directory(gitRepoFolder.getParentFile()).start();
                int exitVal = p.waitFor();

                if (exitVal != 0) {
                    loggerModule.warning("Build script did not exit with proper exit value (got " + exitVal + ")");
                } else {
                    loggerModule.log("Patch complete");
                }

            } catch (IOException | InterruptedException e) {
                errorModule.error(e);
            }
        }

        schedulerModule.patchComplete(type, this);
        this.currentTask = null;
    }

    public File getGitRepoFolder() {
        return gitRepoFolder;
    }
}
