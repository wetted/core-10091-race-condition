package com.example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.runtime.Micronaut;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class Application {

    static Micronaut buildMicronautContext(int portCandidate) {
        Micronaut micronaut = Micronaut.build("");

        Map<String, Object> properties = new HashMap<>();
        properties.putAll(serverProperties(portCandidate));

        return micronaut
                .properties(properties)
                .banner(false)
                .mapError(DependencyInjectionException.class, ex -> { // This is the actual exception that is thrown
                    System.out.println("Got exception: " + ex);
                    ex.printStackTrace();
                    return 111;
                });
    }

    static Map<String, Object> serverProperties(int port) {
        Map<String, Object> result = new HashMap<>();
        result.put("micronaut.server.port", port);
        result.put("micronaut.server.cors.enabled", "true");
        result.put("micronaut.server.cors.configurations.web.allowed-headers", "Authorization");

        return result;
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        AtomicInteger portShift = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(16);
        CopyOnWriteArrayList<Future<ApplicationContext>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 50; i++) {
            var f = executorService.submit(() -> {
                try {
                    int port = 10300 + portShift.getAndIncrement();
                    var micronaut = buildMicronautContext(port);
                    System.out.println("Starting server on: " + port);
                    return micronaut.start();
                } catch (Throwable th) {
                    System.out.println("Failed to start: " + th.getMessage());
                    return null;
                }
            });
            futures.add(f);
        }

        ArrayList<ApplicationContext> contexts = new ArrayList<>();
        for (Future<ApplicationContext> future : futures) {
            try {
                ApplicationContext ctx = future.get();
                contexts.add(ctx);
                System.out.println("Started on: " + ctx.getEnvironment().getProperty("micronaut.server.port", Integer.class).orElse(-1));
            } catch (Throwable th) {
                System.out.println("Failed to start 2: " + th.getMessage());
            }
        }

        for (ApplicationContext context : contexts) {
            try {
                context.stop();
            } catch (Throwable th) {
                System.out.println("Failed to stop: " + th.getMessage());
            }
        }
        executorService.shutdown();
    }}