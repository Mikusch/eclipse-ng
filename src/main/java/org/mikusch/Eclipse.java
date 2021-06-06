package org.mikusch;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.security.auth.login.LoginException;

@SpringBootApplication
public class Eclipse {

    public static void main(String[] args) {
        SpringApplication.run(Eclipse.class, args);
    }

    @Bean(name = "jda")
    public JDA getJda() throws LoginException, InterruptedException {
        return JDABuilder.createDefault("<BOT TOKEN>").build().awaitReady();
    }
}
