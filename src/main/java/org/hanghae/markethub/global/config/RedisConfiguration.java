//package org.hanghae.markethub.global.config;
//
//import org.redisson.Redisson;
//import org.redisson.api.RedissonClient;
//import org.redisson.config.Config;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
//import org.springframework.data.redis.core.RedisTemplate;
//
//@Configuration
//public class RedisConfiguration {
//	private String redisHost = "localhost";
//
//	private int redisPort = 6379;
//
//	@Bean
//	public RedisConnectionFactory redisConnectionFactory(){
//		return new LettuceConnectionFactory(redisHost,redisPort);
//	}
//	@Bean
//	public RedisTemplate<String, String> redisTemplate() {
//		RedisTemplate<String, String> template = new RedisTemplate<>();
//		template.setConnectionFactory(redisConnectionFactory());
//		return template;
//	}
//	@Bean
//	public RedissonClient redissonClient() {
//		Config config = new Config();
//		config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
//		return Redisson.create(config);
//	}
//}
