package com.healthcare.app.appointment;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Real simultaneous requests through the whole stack (not mocks): exactly one may win. */
@DisplayName("Simultaneous requests for the same slot")
class ConcurrentBookingTest extends ApiTest {

    private List<Integer> runTogether(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> t : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return t.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) statuses.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        return statuses;
    }

    private long count(List<Integer> statuses, int status) {
        return statuses.stream().filter(s -> s == status).count();
    }

    @Test
    void eightPatientsBookingTheSameSlotAtOnce_yieldExactlyOneWinner_overSeveralRounds() throws Exception {
        long doctor = doctorIdBySpecialty("Cardiology");
        List<Account> patients = new ArrayList<>();
        for (int i = 0; i < 8; i++) patients.add(newPatient());

        for (int round = 0; round < 4; round++) {
            String when = slot();
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (Account p : patients) {
                tasks.add(() -> post("/api/appointments", p.token(),
                        Map.of("doctorId", doctor, "dateTime", when, "reason", "race")).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = runTogether(tasks);

            assertEquals(1, count(statuses, 201), "round " + round + " statuses: " + statuses);
            assertEquals(7, count(statuses, 409), "round " + round + " statuses: " + statuses);
            assertFalse(statuses.stream().anyMatch(s -> s >= 500), "no server errors: " + statuses);
        }
    }

    @Test
    void twoPatientsMovingOntoTheSameSlotAtOnce_oneSucceeds_oneGetsConflict() throws Exception {
        long doctor = doctorIdBySpecialty("Dermatology");
        Account a = newPatient();
        Account b = newPatient();

        for (int round = 0; round < 5; round++) {
            long idA = book(a.token(), doctor, slot());
            long idB = book(b.token(), doctor, slot());
            String target = slot();

            List<Integer> statuses = runTogether(List.of(
                    () -> patch("/api/appointments/" + idA, a.token(), Map.of("dateTime", target)).andReturn().getResponse().getStatus(),
                    () -> patch("/api/appointments/" + idB, b.token(), Map.of("dateTime", target)).andReturn().getResponse().getStatus()));

            assertEquals(1, count(statuses, 200), "round " + round + ": " + statuses);
            assertEquals(1, count(statuses, 409), "round " + round + ": " + statuses);
        }
    }
}
