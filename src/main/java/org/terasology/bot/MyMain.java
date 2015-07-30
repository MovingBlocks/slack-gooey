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

import java.io.IOException;
import java.net.MalformedURLException;

import org.jibble.pircbot.IrcException;

/**
 * Main class
 */
public class MyMain {

    /**
     * @param args ignored
     * @throws MalformedURLException if the slack token is malformed
     */
    public static void main(String[] args) throws MalformedURLException {

        String token = System.getenv("SLACK_TOKEN");

        if (token == null) {
            System.err.println("The environment variable SLACK_TOKEN must be defined first!");
            return;
        }

        final MyBot bot = new MyBot("slack-gooey", token);
        bot.setVerbose(true);
//        bot.showJoinsParts(true);
        try {
            bot.connect("chat.freenode.net");
            bot.joinChannel("#terasology");
        } catch (IrcException | IOException e) {
            e.printStackTrace();
        }
    }
}
