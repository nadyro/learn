package com.kestrel.commerce.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs.
 *
 * <p>Remember that the service runs with several replicas in production: a scheduled job runs on every replica at the
 * same time. Jobs must either be safe to run concurrently (like the outbox relay, which uses {@code SKIP LOCKED}) or
 * use a distributed lock.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
