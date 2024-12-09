package com.pshdev0.reddy;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class RateLimitedProcessor<T> {

    public enum Action {
        WAIT_AND_CONTINUE, STOP, SKIP_DELAY
    }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ArrayList<Queue<Function<RateLimitedProcessorData<T>, Action>>> taskQueueList = new ArrayList<>();
    private final long delayMillis;
    private final RateLimitedProcessorData<T> data;
    private int currentTrack = -1;

    public RateLimitedProcessor(long delayMillisAfterEachTaskCompleted) {
        this(delayMillisAfterEachTaskCompleted, () -> null);
    }

    public RateLimitedProcessor(long delayMillisAfterEachTaskCompleted, RateLimitedProcessorData<T> data) {
        this.delayMillis = delayMillisAfterEachTaskCompleted;
        this.data = data;
        addQueue();
    }

    public long size() {
        return taskQueueList.get(currentTrack).size();
    }

    public void addQueue() {
        taskQueueList.add(new ConcurrentLinkedQueue<>());
        currentTrack++;
    }

    public RateLimitedProcessor<T> add(Function<RateLimitedProcessorData<T>, Action> func) {
        taskQueueList.get(currentTrack).add(func);
        return this;
    }

    public void perform() {
        AtomicBoolean stop = new AtomicBoolean(false);
        int total = taskQueueList.get(currentTrack).size();
        System.out.println("Starting to perform tasks. Initial queue size: " + total);

        currentTrack = 0;

        data.setIndex(0);
        data.setSize(total);

        Runnable taskRunner = new Runnable() {
            @Override
            public void run() {
                Function<RateLimitedProcessorData<T>, RateLimitedProcessor.Action> task = null;
                task = taskQueueList.get(currentTrack).poll();
                if (task == null && currentTrack < taskQueueList.size() - 1) {
                    // move on to next queue track
                    currentTrack++;
                    task = taskQueueList.get(currentTrack).poll();
                    System.out.println("moving to next task queue");
                }

                if (task == null || stop.get()) {
                    shutDown();
                } else {
                    try {
                        var result = task.apply(data);
                        data.action.set(result);
                        if (result.equals(Action.STOP)) {
                            stop.set(true);
                        } else if (result.equals(Action.SKIP_DELAY)) {
                            scheduler.schedule(this, 0, TimeUnit.MILLISECONDS);
                        } else {
                            scheduler.schedule(this, delayMillis, TimeUnit.MILLISECONDS);
                        }
                    } catch (Exception e) {
                        System.err.println("Exception occurred during task execution:");
                        e.printStackTrace();
                        // Ensure that the next task is scheduled even if an exception occurs
                        scheduler.schedule(this, delayMillis, TimeUnit.MILLISECONDS);
                    }
                    data.incrementIndex();
                }
            }
        };

        scheduler.schedule(taskRunner, 0, TimeUnit.MILLISECONDS);
    }

    private void shutDown() {
        System.out.println("Shutting down scheduler");
        scheduler.shutdown();  // Disable new tasks from being submitted
        boolean isTerminated = false;
        int attempts = 0;

        while (!isTerminated && attempts < 120) { // check for up to 60 seconds
            try {
                isTerminated = scheduler.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Set the interrupt flag
                break;
            }
            attempts++;
        }

        // Wait a while for existing tasks to terminate
        if (!isTerminated) {
            List<Runnable> droppedTasks = scheduler.shutdownNow();  // Try to stop all actively executing tasks
            if(!droppedTasks.isEmpty()) {
                System.err.println("Scheduler did not terminate in the allowed time");
                System.err.println("Had to cancel " + droppedTasks.size() + " non-finished tasks");
            }
        }
        System.out.println("Scheduler shutdown complete");
    }
}
