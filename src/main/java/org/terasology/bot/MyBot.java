/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.bot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import org.jibble.pircbot.PircBot;

/**
 * The actual implementation of the IRC bot.
 */
public class MyBot extends PircBot
{
    private static Logger logger = Logger.getLogger(PircBot.class.getName());

    private boolean showJoinsParts;

    private final URL slackUrl;

    /**
     * @param name the name of the bot
     * @param slackToken the access token for Slack's "Incoming WebHook"
     * @throws MalformedURLException if the token is malformed
     */
    public MyBot(String name, String slackToken) throws MalformedURLException {
        this.slackUrl = new URL("https://hooks.slack.com/services/T03G8SB1X/B08C02P6F/" + slackToken);

        setName(name);
        setLogin(name);
        setVersion("1.0");
        setFinger(name);
    }

    @Override
    protected void onJoin(String channel, String sender, String login, String hostname) {
        if (!showJoinsParts) {
            return;
        }

        display(sender, String.format("%s has joined %s", sender, channel));
    }

    @Override
    protected void onPart(String channel, String sender, String login, String hostname) {
        if (!showJoinsParts) {
            return;
        }

        display(sender, String.format("%s has left %s", sender, channel));
    }

    @Override
    public void onMessage(String channel, String sender, String login, String hostname, String message) {
        display(sender, message);
    }

    private void display(String sender, String message) {
        log("Relaying message: " + message);

        try {
            HttpsURLConnection connection = (HttpsURLConnection) slackUrl.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);

            connection.connect();

            try (OutputStream os = connection.getOutputStream();
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(os))) {
                pw.write("{"
                        + "\"username\": \"" + sender + "\", "
                        + "\"text\": \"" + message + "\""
                        + "}");
            }

            try (InputStream is = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String response = reader.readLine();
                while (response != null) {
                    log(response);
                    response = reader.readLine();
                }
            }
        } catch (IOException e){
            logger.warning(e.getMessage());
        }
    }

    @Override
    public void log(String line) {
        logger.info(line);
    }

    /**
     * @param onoff true if join/part messages should be shown
     */
    public void showJoinsParts(boolean onoff) {
        showJoinsParts = onoff;
    }
}
