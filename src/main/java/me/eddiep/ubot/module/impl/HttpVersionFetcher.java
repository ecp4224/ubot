package me.eddiep.ubot.module.impl;

import me.eddiep.ubot.UBot;
import me.eddiep.ubot.module.impl.defaults.DefaultVersionFetcher;
import okhttp3.*;

import java.io.IOException;
import java.net.URL;

public class HttpVersionFetcher extends DefaultVersionFetcher {
    private URL versionUrl;
    private OkHttpClient client = new OkHttpClient();
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public HttpVersionFetcher(UBot ubot, URL versionUrl) {
        super(ubot);
        this.versionUrl = versionUrl;
    }

    @Override
    protected String fetchGitVersion() {
        Request request = new Request.Builder().url(versionUrl).build();

        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (IOException e) {
            super.ubot.getErrorModule().error(e);
        }

        return null;
    }

    @Override
    protected void saveVersion(String version) {
        validateVersion(version);

        RequestBody body = RequestBody.create(JSON, "{\"version\": \"" + version + "\"}");
        Request request = new Request.Builder()
                .url(versionUrl)
                .post(body)
                .build();

        try {
            client.newCall(request).execute();
        } catch (IOException e) {
            ubot.getLoggerModule().warning("Error saving version to " + versionUrl);
            ubot.getErrorModule().error(e);
        }
    }
}
