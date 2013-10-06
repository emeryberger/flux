typedef struct{
	int num_readers;	// initially zero
	event writers;		// initially set
	event readers;		// initially set
}rw_lock;


void rw_read_lock(rw_lock *lock)
{
	lock->readers.wait();   // We need a read lock
	lock->writers.clear();  // No one can write lock it while we have the read lock
	lock->readers.set();    // Other people can still read lock it though
	lock->num_readers++;         // We are the n + 1th reader :)
}

void rw_write_lock(rw_lock *lock)
{
	//printf("Write lock part 1...\n");
	lock->readers.wait();   // We don't want readers thinking they can get this lock anymore
	//printf("Write lock part 2...\n");
	lock->writers.wait();   // We need to wait till it is free for a writer to get
	//printf("Write lock part 3...\n");
	
}

void rw_read_unlock(rw_lock *lock)
{
	lock->num_readers--;    // One more reader bites the dust
	//printf("num readers: %d\n", lock->num_readers);
	if (lock->num_readers == 0) {
		printf("Releasing the read lock...\n");
		lock->writers.set();		// there are no more readers, the writers can get the event
	}
}

void rw_write_unlock(rw_lock *lock)
{
	lock->writers.set();  // Someone else can write lock it now if they want
	lock->readers.set();  // Someone else can read lock it now if they want
}

void rw_lock_init(rw_lock *lock)
{
	lock->num_readers = 0;
	lock->writers.set();
	lock->readers.set();
}
