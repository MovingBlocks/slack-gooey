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
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
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

    private final Set<String> channelsToJoin = new HashSet<>();

    private final Timer timer = new Timer(true);

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
        if (Objects.equals(sender, getName())) {
            if (channelsToJoin.add(channel)) {
                logger.info("Added '" + channel + "' to the list of channels to join");
            }
        }

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

    @Override
    protected void onDisconnect() {
        logger.info("Disconnected ..");

        long freq = Duration.ofMinutes(1).toMillis();
        timer.schedule(new ReconnectTask(this), freq);
    }

    @Override
    protected void onKick(String channel, String kickerNick, String kickerLogin, String kickerHostname, String recipientNick, String reason) {
        if (recipientNick.equalsIgnoreCase(getNick())) {
            logger.info("Got kicked .. waiting for 10 min. until joining again.");

            long freq = Duration.ofMinutes(10).toMillis();
            timer.schedule(new RejoinTask(this, channel), freq);
        }
    }

    /**
     * @param onoff true if join/part messages should be shown
     */
    public void showJoinsParts(boolean onoff) {
        showJoinsParts = onoff;
    }

    @Override
    public synchronized void dispose() {
        timer.cancel();
        super.dispose();
    }

    private void tryToReconnect() {
        if (!isConnected()) {
            try {
                logger.info("Trying to reconnect ..");
                reconnect();
                for (String channel : channelsToJoin) {
                    logger.info("Joining '" + channel + "'");
                    joinChannel(channel);
                }
            } catch (Exception e) {
                logger.warning("Could not reconnect! " + e.toString());
                timer.schedule(new ReconnectTask(this), Duration.ofMinutes(15).toMillis());
            }
        }
    }

    private void tryRejoinChannel(String channel) {
        if (Arrays.asList(getChannels()).contains(channel)) {
            return;
        }
        joinChannel(channel);
        long freq = Duration.ofMinutes(10).toMillis();
        timer.schedule(new RejoinTask(this, channel), freq);
    }

    private static class RejoinTask extends TimerTask {

        private MyBot bot;
        private String channel;

        public RejoinTask(MyBot bot, String channel) {
            this.bot = bot;
            this.channel = channel;
        }

        @Override
        public void run() {
            bot.tryRejoinChannel(channel);
        }

    }
    private static class ReconnectTask extends TimerTask {

        private final MyBot bot;

        public ReconnectTask(MyBot bot) {
            this.bot = bot;
        }

        @Override
        public void run() {
            bot.tryToReconnect();
        }

    }
}
