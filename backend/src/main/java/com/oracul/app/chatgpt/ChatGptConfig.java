package com.oracul.app.chatgpt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatGptProperties.class)
public class ChatGptConfig {
}
