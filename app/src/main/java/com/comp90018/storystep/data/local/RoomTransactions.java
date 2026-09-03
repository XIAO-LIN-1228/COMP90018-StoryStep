package com.comp90018.storystep.data.local;

import androidx.room.RoomDatabase;

/** {@link Transactions} backed by a real Room database. */
public final class RoomTransactions implements Transactions {

    private final RoomDatabase database;

    public RoomTransactions(RoomDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        this.database = database;
    }

    @Override
    public void runInTransaction(Runnable work) {
        database.runInTransaction(work);
    }
}
