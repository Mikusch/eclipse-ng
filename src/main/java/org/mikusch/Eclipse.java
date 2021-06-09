package org.mikusch;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;

import javax.security.auth.login.LoginException;

@SpringBootApplication
public class Eclipse {

    public static void main(String[] args) {
        SpringApplication.run(Eclipse.class, args);
    }

    @Bean(name = "jda")
    public JDA getJDA(@Value("${eclipse.discord.token}") String token) throws LoginException, InterruptedException {
        return JDABuilder.createDefault(token).build().awaitReady();
    }

    @Bean(name = "twitter")
    public Twitter getTwitter() {
        return new TwitterFactory().getInstance();
    }
}
