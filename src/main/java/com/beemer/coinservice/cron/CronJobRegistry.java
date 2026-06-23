package com.beemer.coinservice.cron;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@SuppressWarnings("unused")
public class CronJobRegistry {
    private final Map<String, Class<? extends CronJob>> cronJobs = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext context;

    @PostConstruct
    void buildRegistry() {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(com.beemer.coinservice.cron.annotation.CronJob.class));

        for (BeanDefinition component : provider.findCandidateComponents(this.getClass().getPackageName())) {
            try {
                Class<?> cls = Class.forName(component.getBeanClassName());
                var annotation = cls.getAnnotation(com.beemer.coinservice.cron.annotation.CronJob.class);

                if (annotation != null && CronJob.class.isAssignableFrom(cls)) {
                    cronJobs.put(annotation.name(), cls.asSubclass(CronJob.class));
                }
            } catch (ClassNotFoundException cnfe) {
                log.error("Unable to register cron job implemented by {} {}", component.getBeanClassName(), cnfe);
            }
        }
    }

    public Set<String> getAvailableCronJobs() {
        return cronJobs.keySet();
    }

    public @Nullable CronJob getInstanceForName(String name) {
        Class<? extends CronJob> cls = cronJobs.get(name);
        return cls != null ? context.getBean(cls) : null;
    }
}