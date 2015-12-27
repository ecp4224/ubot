package me.eddiep.ubot.test;

import me.eddiep.ubot.UBot;
import me.eddiep.ubot.utils.CancelToken;

import java.io.File;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("You must specify a repo directory to use!");
            System.exit(1);
        }
        File file = new File(args[0]);

        UBot uBot = new UBot(file);
        CancelToken token = new CancelToken();
        uBot.startSync(token);
    }
}
