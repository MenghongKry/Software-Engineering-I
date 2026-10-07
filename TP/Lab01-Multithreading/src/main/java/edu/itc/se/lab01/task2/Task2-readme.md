# Counter Lab: race conditions and thread-safe counters
 
8 threads each call `increment()` 1 000 000 times on one shared counter.
The correct final value is **8 000 000**.
 
## Run it
 
```bash
cd src
javac -d out *.java
java -cp out Bench

or 

cd src && javac -d out *.java && java -cp out Bench
```
 
## Files
 
| File | What it is |
|---|---|
| `Counter.java` | The interface: `increment()` and `get()` |
| `UnsafeCounter.java` | Plain `long` with `count++`. Broken on purpose; recorded wrong results are in its comment |
| `SynchronizedCounter.java` | `synchronized` methods |
| `LockCounter.java` | `ReentrantLock`, with `unlock()` in `finally` |
| `AtomicCounter.java` | `AtomicLong` |
| `LongAdderCounter.java` | `LongAdder` (bonus), with a comment on why it's faster under contention |
| `Bench.java` | The harness and `main`, with sample output |
 
## Results (2-core Linux, JDK 21)
 
| Counter | Correct? | Time |
|---|---|---|
| Unsafe | No (4 234 211 / 7 192 001 / 7 000 000) | 2–33 ms |
| Synchronized | Yes | 345–444 ms |
| Lock | Yes | 198–217 ms |
| Atomic | Yes | 170–264 ms |
| LongAdder | Yes | 48–62 ms |
 
## Q: Why is it still a bug to make only `increment()` synchronized, but not `get()`?
 
`synchronized` does two jobs, and an unsynchronized `get()` loses both of them. A lock only works when every thread that touches the data uses it. If only increment() takes the lock, get() reads count without it, and two problems follow.
 
**1. Visibility: `get()` may never see the new value.**
Each CPU core can keep its own cached copy of `count`, and the JIT compiler can
keep it in a register. Leaving a `synchronized` block *publishes* the writes, but
only to a thread that later enters a block **on the same lock**. The Java Memory
Model calls this a *happens-before* relationship. An unsynchronized `get()` never
takes the lock, so there's no happens-before link, and Java doesn't guarantee it
ever sees the latest value. A reader thread could print a stale number, or loop
forever on `while (counter.get() < 10)` because the JIT reads `count` once and
reuses it.
 
**2. Atomicity: a `long` read can be torn.**
`count` is a 64-bit `long`. The Java spec (JLS §17.7) allows a non-volatile
`long` or `double` to be read or written as two separate 32-bit halves. On a
32-bit JVM, an unsynchronized `get()` could read the top half before an increment
and the bottom half after it, and return a value the counter **never actually
held**. 64-bit JVMs in practice don't do this, but the language doesn't promise it.
 
**It's a data race either way.** One thread writes `count` (inside the lock) and
another reads it (outside the lock) with no ordering between them. The JMM calls
that a *data race*, and a program with a data race is not correctly synchronized,
so you lose Java's guarantees about what the reader sees.
 
**Why it seemed to work in `Bench`.** `Bench` calls `get()` only *after* `join()`.
`join()` also creates a happens-before link (everything a thread did happens-before
another thread's `join()` on it returns), so the final read is safe *in this
harness*. The bug shows up as soon as some other thread calls `get()` while the
workers are still running, such as a progress bar or a metrics endpoint.
 
**Fixes** 
``` bash
// A: lock both methods on the same object
public synchronized void increment() { count++; }
public synchronized long get()       { return count; }

// B: volatile for reads, lock for writes
private volatile long count;
public synchronized void increment() { count++; }  // still needs the lock
public long get()                    { return count; }  // volatile makes this safe

// C: let an atomic class handle both
private final AtomicLong count = new AtomicLong();
```

## The two things to remember

Atomicity: read, change and write must happen as one step (with a lock or an atomic class).

Visibility: a thread may not see another thread's changes unless both use the same synchronization, which is why `get()` needs it too.