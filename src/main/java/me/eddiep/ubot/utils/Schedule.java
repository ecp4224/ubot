package me.eddiep.ubot.utils;

import java.util.Date;
import java.util.concurrent.Callable;

public class Schedule<T> {
    private Callable<Boolean> condition;
    private PRunnable<T> task;
    private T val;

    public static <T> Schedule<T> combind(final Schedule... schedules) {
        return new Schedule<>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                for (Schedule schedule : schedules) {
                    if (!schedule.isReady())
                        return false;
                }

                return true;
            }
        });
    }

    public static <T> Schedule<T> never() {
        return new Schedule<>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return false;
            }
        });
    }

    public static <T> Schedule<T> now() {
        return new Schedule<T>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return true;
            }
        });
    }

    public static <T> Schedule<T> in(final long ms) {
        final long start = System.currentTimeMillis();
        return new Schedule<T>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return System.currentTimeMillis() - start >= ms;
            }
        });
    }

    public static <T> Schedule<T> at(final Date date) {
        return new Schedule<T>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Date today = new Date();
                return today.after(date);
            }
        });
    }

    public static <T> Schedule<T> when(Callable<Boolean> condition) {
        return new Schedule<>(condition);
    }

    private Schedule(Callable<Boolean> condition) {
        this.condition = condition;
    }

    public boolean isReady() {
        try {
            return this.condition.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void attach(PRunnable<T> runnable, T val) {
        this.task = runnable;
        this.val = val;
    }

    public void execute() {
        if (this.task != null)
            this.task.run(this.val);
    }
}
