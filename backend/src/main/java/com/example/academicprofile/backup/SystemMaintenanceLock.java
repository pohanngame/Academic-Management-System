package com.example.academicprofile.backup;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.stereotype.Component;

@Component
public class SystemMaintenanceLock {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public boolean tryBackupLock() {
        return lock.writeLock().tryLock();
    }

    public void unlockBackup() {
        lock.writeLock().unlock();
    }

    public void lockMutation() {
        lock.readLock().lock();
    }

    public void unlockMutation() {
        lock.readLock().unlock();
    }
}
