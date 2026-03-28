package dev.yeon.iotsensorplatform.simulator.service;

import lombok.Getter;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class SimulatorTask {

    private final int totalCount;
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile Future<?> future;

    public SimulatorTask(int totalCount) {
        this.totalCount = totalCount;
    }

    public void setFuture(Future<?> future) {
        this.future = future;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void markDone() {
        running.set(false);
    }

    public int incrementCompleted() {
        return completedCount.incrementAndGet();
    }

    public void cancel() {
        running.set(false);
        if (future != null) {
            future.cancel(true);
        }
    }
}
