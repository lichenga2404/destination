package company.linkedin;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import company.linkedin.MergeIntervalWithLessGetCall.http.can.BlockedQueue;
// http://n00tc0d3r.blogspot.com/2013/08/implement-bounded-blocking-queue.html
/**
 * Write a multithreaded bounded Blocking Queue where the capacity of the queue is limited. Implement size, add, remove, and peek methods.

There could be multiple producer and consumer threads.
Producers fill up the queue. If the queue is full, producers should wait;
On the other hand, consumers take elements from the queue. If the queue is empty, consumers should wait.
Solution

To make the actions of adding or removing an element from the underlying queue, we need to either use lock or 
synchronized the relative blocks that conduct the actions.

Here is an implementation with synchronized.

One shortcoming of using synchronization is that it only allow one thread access the queue at the same time, either consumer or producer.)

Plus, we need to use notifyAll instead of notify since there could be multiple waiting producers and consumers and notify can wake up any thread 
which could be a producer or a consumer. This stackoverflow post gives a detailed example to explain notify vs. notifyAll.
 */
public class BoundedBlockingQueue<E> {
	Queue<E> queue = new LinkedList<E>();
	int capacity;
	AtomicInteger count = new AtomicInteger(0);
	
	BoundedBlockingQueue(int capacity) {
		if (capacity <= 0) {
			throw new RuntimeException("Capacity too low");
		}
		this.capacity = capacity;
	}
	
	public synchronized void add(E element) throws InterruptedException {
		if (element == null) {
			return;
		}
		
		while (count.get() == capacity) {
			this.wait();
		}
		
		queue.offer(element);
		int oldCount = count.getAndIncrement(); // There is also increamentAndGet()
		if (oldCount == 0) {
			this.notifyAll();
		}
	}
	
	public synchronized E remove() throws InterruptedException {
		E element;
		
		while (count.get() == 0) {
			this.wait();
		}
		
		element = queue.poll();
		int oldCount = count.getAndDecrement();
		if (oldCount == capacity) {
			this.notifyAll();
		}
		
		return element;
	}
	
	public synchronized E peek() {
		if (count.get() == 0) {
			return null;
		}
		
		return queue.peek();
	}
}

/**
 * Notice that if the queue was empty before add or full before remove, we need to notify other waiting threads to unblock them.
We only need to emit such notifications in the above two cases since otherwise there cannot be any waiting threads.

Here is an implementation with locks.

We use two Reentrant Locks to replace the use of synchronized methods. With separate locks for put and take, 
a consumer and a producer can access the queue at the same time (if it is neither empty nor full). 
A reentrant lock provides the same basic behaviors as a Lock does by using synchronized methods and statements. 
Beyond that, it is owned by the thread last successfully locking and thus when the same thread invokes lock() again, 
it will return immediately without lock it again.

Together with lock, we use Condition to replace the object monitor (wait and notifyAll). 
A Condition instance is intrinsically bound to a lock. Thus, we can use it to signal threads that are waiting for the associated lock. 
Even better, multiple condition instances can be associated with one single lock and each instance will have its own wait-thread-set, 
which means instead of waking up all threads waiting for a lock, we can wake up a predefined subset of such threads. 
Similar to wait(), Condition.await() can atomically release the associated lock and suspend the current thread.

We use Atomic Integer for the count of elements in the queue to ensure that the count will be updated atomically.
 */
class BoundedBlockingQueue1<E> {
	Queue<E> queue = new LinkedList<E>();
	int capacity;
	AtomicInteger count = new AtomicInteger(0);
	
	ReentrantLock addLock = new ReentrantLock();
	ReentrantLock removeLock = new ReentrantLock();
	Condition addCondition = addLock.newCondition();
	Condition removeCondition = removeLock.newCondition();
	
	BoundedBlockingQueue1(int capacity) {
		if (capacity <= 0) {
			throw new RuntimeException("Capacity too low");
		}
		this.capacity = capacity;
	}
	
	public void add(E element) throws InterruptedException {
		if (element == null) {
			return;
		}
		
		try {
			addLock.lock();
			while (count.get() == capacity) { // 这边用if还是while? H2O是用if
				addCondition.await();
			}
			
			queue.offer(element);
			int oldCount = count.getAndIncrement(); // There is also increamentAndGet()
			if (oldCount + 1 < this.capacity) { // 因为有lock 这个可能不需要呢
				addCondition.signal(); // Release add condition
			}
		} finally {
			addLock.unlock();
		}
		
		if (count.get() == 1) {	// Release remove condition
			try {
				removeLock.lock();
				removeCondition.signal();
			} finally {
				removeLock.unlock();
			}
		}
	}
	
	public E remove() throws InterruptedException {
		E element;
		try {
			removeLock.lock();
			
			while (count.get() == 0) {
				removeCondition.await();
			}
			
			element = queue.poll();
			int oldCount = count.getAndDecrement();
			if (oldCount > 1) {
				removeCondition.signal();
			}
		} finally {
			removeLock.unlock();
		}
		
		if (count.get() == capacity - 1) { // Got one space
			try {
				addLock.lock();
				addCondition.signal();
			} finally {
				addLock.unlock();
			}
		}
		
		return element;
	}
	
	public E peek() {
		if (count.get() == 0) {
			return null;
		}
		
		try {
			removeLock.lock(); // First in first out, so ADD doesnt matter
			return queue.peek();
		} finally {
			removeLock.unlock();
		}
	}
}

class BlockedQueueWithSemaphore<T> implements BlockedQueue<T>{
	Queue<T> queue = null;
	Semaphore addSemaphore = null;
	Semaphore mutex = null;
	Semaphore removeSemaphore = null;
	
	// permits - the initial number of permits available. This value may be negative, in which case releases must occur before any acquires will be granted.
	BlockedQueueWithSemaphore(int capacity) {
		this.queue = new LinkedList<T>();
		this.addSemaphore = new Semaphore(capacity);
		this.mutex = new Semaphore(1);
		this.removeSemaphore = new Semaphore(0);
	}
	
	void add(T t) {
		addSemaphore.acquire();
		
		try {
			mutex.acquire();
			queue.offer(t);
			removeSemaphore.release();
		} finally {
			mutex.release();
		}
		
	}
	
	T remove() {
		removeSemaphore.acquire(); // Since it is 0, you have to release it at least once before use it, that means, add first, then remove
		
		try {
			mutex.acquire();
			T res = queue.poll();
			addSemaphore.release();
			return res;
		} finally {
			mutex.release();
		}
	}
}

