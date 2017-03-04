package me.eddiep.ubot.module.impl;

import me.eddiep.ubot.UBot;
import me.eddiep.ubot.module.impl.defaults.GitUpdateModule;
import okhttp3.*;

import java.io.IOException;
import java.net.URL;

public class HttpVersionFetcher extends GitUpdateModule {
    private URL versionUrl;
    private OkHttpClient client = new OkHttpClient();
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public HttpVersionFetcher(UBot ubot, URL versionUrl) {
        super(ubot);
        this.versionUrl = versionUrl;
    }

    @Override
    protected String fetchNewVersion() {
        Request request = new Request.Builder().url(versionUrl).build();

        try {
            Response response = client.newCall(request).execute();
            String version = response.body().string();

            validateVersion(version);

            return version;
        } catch (Exception e) {
            super.ubot.getErrorModule().error(e);
        }

        return getRunningVersion();
    }
}
