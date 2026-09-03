package com.comp90018.storystep.data.local;

/**
 * {@link Transactions} that simply runs the work.
 *
 * <p>It provides no atomicity, and deliberately so: these tests exercise the store's rules, not
 * SQLite's transaction semantics. Rollback is proven on a device by the instrumented tests, where
 * a real database is doing the work.
 */
public final class DirectTransactions implements Transactions {

    @Override
    public void runInTransaction(Runnable work) {
        work.run();
    }
}
