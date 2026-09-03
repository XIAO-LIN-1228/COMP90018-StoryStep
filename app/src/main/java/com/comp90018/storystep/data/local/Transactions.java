package com.comp90018.storystep.data.local;

/**
 * Runs a unit of storage work atomically.
 *
 * <p>A one-method interface rather than a direct dependency on {@code RoomDatabase} so the store
 * that uses it holds nothing but DAOs and this — all of them plain Java types, which is what lets
 * its rules be exercised in JVM tests instead of only on a device.
 *
 * <p>Implementations must propagate a thrown exception and roll the work back with it. Several of
 * the store's invariants are enforced by throwing inside a transaction, and an implementation that
 * swallowed the exception would commit the very state the check rejected.
 */
public interface Transactions {

    void runInTransaction(Runnable work);
}
