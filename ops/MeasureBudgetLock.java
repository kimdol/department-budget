import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MeasureBudgetLock {
    private static final String SQL = "select allocation, committed, spent from budget_workspace where workspace_id=? for update";

    public static void main(String[] args) throws Exception {
        try (var holder = connect(); var waiter = connect();
             var lock = holder.prepareStatement(SQL); var request = waiter.prepareStatement(SQL);
             var worker = Executors.newSingleThreadExecutor()) {
            lock.setQueryTimeout(4);
            request.setQueryTimeout(4);
            System.out.println("round,scope,hold_us,select_for_update_us,allocation");
            for (int round = 0; round <= 20; round++) {
                for (int order = 0; order < 2; order++) {
                    boolean same = (round + order) % 2 == 0;
                    read(lock,"inbox-measurement-50");
                    CountDownLatch starting = new CountDownLatch(1);
                    var measured = worker.submit(() -> {
                        long start = System.nanoTime();
                        starting.countDown();
                        long balance = read(request,same ? "inbox-measurement-50" : "inbox-measurement-51");
                        long elapsed = (System.nanoTime()-start)/1000;
                        waiter.rollback();
                        return new long[]{elapsed,balance};
                    });
                    if (!starting.await(3,TimeUnit.SECONDS)) throw new IllegalStateException("Missing waiting session");
                    long holdStart = System.nanoTime();
                    Thread.sleep(100);
                    holder.rollback();
                    long held = (System.nanoTime()-holdStart)/1000;
                    long[] row = measured.get(5,TimeUnit.SECONDS);
                    if (row[1] != 1000000) throw new IllegalStateException("Unexpected budget fixture");
                    System.out.printf("%d,%s,%d,%d,%d%n",round,same ? "same" : "other",held,row[0],row[1]);
                }
            }
        }
    }

    private static Connection connect() throws Exception {
        var db = DriverManager.getConnection(System.getenv("BUDGET_DB_URL"),System.getenv("BUDGET_DB_USER"),
                System.getenv("BUDGET_DB_PASSWORD"));
        db.setAutoCommit(false);
        return db;
    }

    private static long read(PreparedStatement statement,String workspace) throws Exception {
        statement.setString(1,workspace);
        try (var row = statement.executeQuery()) {
            if (!row.next()) throw new IllegalStateException("Missing budget fixture");
            return row.getLong(1);
        }
    }
}
