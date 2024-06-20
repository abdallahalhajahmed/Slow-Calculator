package AE2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Solution implements CommandRunner {

    private final ConcurrentHashMap<Long, Future<Integer>> runningCalculations = new ConcurrentHashMap<>();
    private final List<Long> cancelledCalculations = new ArrayList<>();
    private ExecutorService executorService;

    public Solution() {
        executorService = Executors.newCachedThreadPool(); // This ensures the number of threads is not limited
    }

    @Override
    public String runCommand(String command) {
        try {
            Future<Integer> futureN = null;
            Long N;
            StringBuilder displayCalculations = new StringBuilder();
            int runningCount = 0; // To keep track of how many calculations are running
            String[] subString = command.split("\\s+");

            switch (subString[0].toLowerCase()) {
                case "start":
                    if (subString.length != 2) return "Invalid command";
                    N = Long.parseLong(subString[1]);
                    futureN = executorService.submit(new SlowCalculator(N)); // Start calculating

                    runningCalculations.put(N, futureN); // Add number and future calculation to concurrent hashmap

                    return "started " + N;
                case "cancel":
                    if (subString.length != 2) return "Invalid command";
                    long cancelledN = Long.parseLong(subString[1]);
                    Future<Integer> nToCancel = runningCalculations.get(cancelledN);

                    if (nToCancel != null) {
                        boolean cancellation = nToCancel.cancel(true);
                        if (cancellation) {
                            try {
                                Thread.sleep(100); // Cancel within 0.1s
                                if (nToCancel.isCancelled()) {
                                    runningCalculations.remove(cancelledN); // Remove the cancelled calculation from hashmap
                                    cancelledCalculations.add(cancelledN); // Add to List of cancelled calculations
                                    return "cancelled " + cancelledN;
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                    return "cancelled " + cancelledN; // Return if already completed or never started
                case "running":
                    if (subString.length != 1) return "Invalid command";
                    if (runningCalculations.isEmpty()) {
                        return "no calculations running";
                    }

                    for (Long n : runningCalculations.keySet()) {
                        // Check if the calculation is not already completed/cancelled
                        if (!runningCalculations.get(n).isDone() && !runningCalculations.get(n).isCancelled()) {
                            if (runningCount > 0) {
                                displayCalculations.append(" ");
                            }
                            displayCalculations.append(n);
                            runningCount++;
                        }
                    }
                    // If no calculations are running
                    if (runningCount == 0) {
                        return "no calculations running";
                    }

                    String runningString = String.format("%d calculations running: %s", runningCount, displayCalculations.toString());

                    return runningString;
                case "get":
                    if (subString.length != 2) return "Invalid command";
                    N = Long.parseLong(subString[1]);
                    futureN = runningCalculations.get(N);
                    if (futureN == null) {
                        return "Invalid command";
                    }
                    // If the calculation was started but cancelled
                    if (cancelledCalculations.contains(N)) return "cancelled";
                    // If the calculation is not yet finished
                    if (!futureN.isDone()) {
                        return "calculating";
                    }

                    try {
                        int result = futureN.get().intValue(); // Get result
                        runningCalculations.remove(N);
                        return "result is " + result;
                    } catch (CancellationException e) {
                        return "cancelled";
                    } catch (Exception e) {
                        return "Invalid command";
                    }
                case "after":
                    if (subString.length != 3) return "Invalid command";
                    long initialN = Long.parseLong(subString[1]);
                    long afterM = Long.parseLong(subString[2]);
                    Future<Integer> runningN = runningCalculations.get(initialN);

                    // Schedule M to start after N completes or after it is cancelled
                    executorService.submit(() -> {
                        try {
                            runningN.get(); // This waits for the computation of N to complete
                        } catch (InterruptedException | ExecutionException e) {
                            // Handle interruption or execution exception
                        } finally {
                            // Execute M calculation
                            Future<Integer> futureForM = executorService.submit(new SlowCalculator(afterM));
                            runningCalculations.put(afterM, futureForM);
                        }
                    });
                    return afterM + " will start after " + initialN;
                case "finish":
                    if (subString.length != 1) return "Invalid command";
                    // Wait for all calculations requested by the user to finish and do not accept new tasks
                    executorService.shutdown();
                    try {
                        if (!executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                            executorService.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        executorService.shutdownNow();
                    }
                    return "finished";
                case "abort":
                    if (subString.length != 1) return "Invalid command";
                    // Immediately stop all running calculations and discard any scheduled ones
                    for (Future<Integer> future : runningCalculations.values()) {
                        future.cancel(true);
                    }
                    runningCalculations.clear();
                    cancelledCalculations.clear();
                    return "aborted";
                default:
                    return "Invalid command";
            }
        } catch (NumberFormatException e) {
            return "Invalid command";
        }
    }
}

