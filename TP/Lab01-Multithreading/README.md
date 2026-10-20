# Lab 01: Multithreading in Java — report

This lab starts with creating threads by hand, then fixes a race condition, builds a bounded buffer, moves to
thread pools, finds and fixes a deadlock, and ends with a parallel word counter that can be cancelled cleanly. Everything uses only the JDK (JDK 21+); JUnit 6 is used
only for the challenge's tests.

| Folder | Task | Details |
|---|---|---|
| [`task1/`](task1/README.md) | Four ways to create a thread, `start()` vs `run()`, `join()` | `Countdown`, `CountdownTask`, `Main` |
| [`task2/`](task2/README.md) | Race condition and thread-safe counters | `Counter`, `UnsafeCounter`, `SynchronizedCounter`, `LockCounter`, `AtomicCounter`, `LongAdderCounter`, `Bench` |
| [`task3/`](task3/README.md) | Bounded buffer by hand, then with `BlockingQueue` | `Order`, `BoundedBuffer`, `OrderQueue`, `Producer`, `Consumer`, `Main`, `Main2` |
| [`task4/`](task4/README.md) | Thread pools, `Future`, virtual threads | `PrimeCounter`, `FakeDownload`, `Main` |
| [`task5/`](task5/README.md) | Deadlock diagnosis and graceful shutdown | `Bank`, `Watchdog`, `OrderedBank`, `TryLockBank`, `GracefulBank`, `deadlock.txt` |
| [`challenge/`](challenge/README.md) | Parallel word count with progress and cancellation | `WordCount`, `ReferenceWordCount`, `DataGenerator`, `WordCountTest` |

Each folder's README has the run commands and the full explanation for that task.

## Results

Measured on 2026-10-20 on a 4-core Linux machine with JDK 21. Times change from run to run; counts do not.

### Task 1: creating threads

| Run | What happens | Time |
|---|---|---|
| `Main` (`start()`) | The four countdowns overlap; "all launched" prints before they finish; all four end `TERMINATED`, only artemis `isVirtual=true` | 0.7 s |
| `Main --sequential` (`run()`) | `main` runs the countdowns one by one and prints `main: 3` for the `Runnable` ones; all four stay `NEW` | 2.5 s |

`start()` creates a new thread, `run()` is an ordinary method call on the current thread, and `join()` makes `main`
wait for each thread. The explanation the lab asks for is a comment at the top of `Main`.

### Task 2: race condition

8 threads x 1 000 000 increments, expected 8 000 000:

| Counter | Value | Time |
|---|---|---|
| `UnsafeCounter` (`count++`) | 6 300 113 / 7 996 029 / 6 383 472 (wrong every run) | 1-42 ms |
| `SynchronizedCounter` | 8 000 000 | 333-459 ms |
| `LockCounter` (`ReentrantLock`) | 8 000 000 | 285-321 ms |
| `AtomicCounter` (`AtomicLong`) | 8 000 000 | 206-336 ms |
| `LongAdderCounter` (bonus) | 8 000 000 | 31-41 ms |

`count++` is three steps (read, add, write), so two threads can overwrite each other's update. The three locks or
atomics make it one indivisible step. `LongAdder` is fastest because each thread adds to its own cell instead of
all threads fighting over one memory location. The task 2 README also answers why `get()` must be synchronized too
(visibility: without the same lock, a reader may see a stale or torn value).

### Task 3: bounded buffer

| Run | Result |
|---|---|
| `Main` (hand-written `BoundedBuffer`, capacity 5, 2 producers x 50, 3 consumers) | processed 100 orders, 3 of 3 runs |
| `Main2` (`ArrayBlockingQueue` behind the same `OrderQueue` interface) | processed 100 orders, 3 of 3 runs |
| Step 7: `if` instead of `while` and `notify()` instead of `notifyAll()` | 5 of 5 runs hung after `capacity exceeded` |

`BoundedBuffer` uses `synchronized`, `wait()` in a `while` loop and `notifyAll()`. With `if`, a thread that wakes
up does not re-check the condition, so the buffer overfills or a consumer takes from an empty buffer; with
`notify()`, the one thread woken can be the wrong kind (a producer waking a producer) and everyone ends up waiting.
`ArrayBlockingQueue` replaces all of that logic with one line. Only the last producer to finish sends the poison
pills, so no consumer stops while orders are still coming.

### Task 4: thread pools and virtual threads

| Run | Result | Time |
|---|---|---|
| Primes below 4 000 000, single thread | 283 146 | 1 535 ms |
| Same, 16 chunks on a pool of 4 threads | 283 146 (same) | 429 ms (3.6x faster) |
| 500 downloads, fixed pool of 20 platform threads | ok=489 failed=10 timedOut=1 | 5 231 ms |
| 500 downloads, virtual threads | ok=489 failed=10 timedOut=1 | 1 218 ms |
| Bonus: `CompletableFuture` + `handle()` on virtual threads | ok=489 failed=10 timedOut=1 | 1 010 ms |

The CPU-bound prime count gets faster with more threads only up to the number of cores. The downloads only
*wait* (200 ms sleep), so the 20-thread pool can only have 20 in flight at a time (25 rounds x 200 ms = 5 s),
while virtual threads let all 500 wait at once. The extra ~1 s is the 1-second timeout on the one slow download.
Failures arrive as `ExecutionException` and are counted, the slow task is cancelled with `cancel(true)`, and every
executor is closed with try-with-resources, so the program exits by itself (exit code 0, no `System.exit`).

### Task 5: deadlock

| Program | Output |
|---|---|
| `Bank` (broken, with watchdog) | `[watchdog] DEADLOCK detected between: transfer-0, transfer-1`, then hangs |
| `OrderedBank` (Fix A: lock smaller id first) | transfers=800000 total=4000 in 134 ms |
| `TryLockBank` (Fix B: `tryLock(10 ms)` + back-off) | transfers=800000 retries=14683 total=4000 in 28 370 ms |
| `GracefulBank` (interrupt after 2 s) | all 8 workers stopped in 0 ms, flags restored, total=4000 |

The deadlock comes from two threads locking the same two accounts in opposite order. `deadlock.txt` holds the
`jstack` report showing that cycle. Lock ordering removes the cycle completely, which is why Fix A is both simple and
fast. Fix B lets a thread give up a lock after 10 ms instead of waiting forever, so the cycle breaks, but each
collision costs up to 10 ms of waiting; that is why it is much slower than the lab's sample time (the checklist
asks only for 800 000 transfers, an unchanged total and the retry count). In the graceful shutdown, workers set the
interrupt flag again after catching `InterruptedException`, so the `while (!isInterrupted())` loop ends.

### Challenge: parallel word count

| Check | Result |
|---|---|
| `mvn test` | 6 tests, 0 failures (parallel = reference for 1, 4, 8 platform workers and virtual threads; `--timeout 1` stops within 3 s; SIGTERM prints `[PARTIAL]`) |
| 200 files x 50 000 words, `--top 5 --timeout 30` | DONE files=200 words=10,000,000 in 2.16 s, "the" = 12.23 % |
| Same data, `--timeout 1` | `deadline reached`, `[PARTIAL] files=41`, exit 0 |
| Ctrl+C (SIGINT) on 2 000 files | `interrupted (Ctrl+C)`, `[PARTIAL] files=385`, exited 0.14 s after the signal |
| Single-threaded reference on the same data | 10 000 000 words, same counts |

The design is one scanner, a bounded queue of 16 paths (back-pressure), N workers updating a
`ConcurrentHashMap<String, LongAdder>`, a reporter every 500 ms, and one `shutdown()` used by both the shutdown hook
and the deadline. The challenge README has the scaling table, the `LongAdder`/`merge` versus `synchronized`
comparison, and the Coffman-condition paragraph.

## Fixes made during this review

Tasks 1-3 were not in this folder, so they were rebuilt from your earlier chats: task 1 from the `Main.java` you
pasted (with your `Countdown` and `CountdownTask`), tasks 2 and 3 from the last version Claude gave you. Check that
they match the copy you will submit.

1. **Task 1: the step 5 comment was missing.** It now explains `start()` vs `run()` at the top of `Main`.
2. **Task 2: no package.** The classes are now in `edu.itc.se.lab01.task2`, matching the lab's layout.
3. **Challenge: the tool could hang forever on an unreadable file.** When reading a file failed, the worker printed
   the error and *quit*. Its poison pill was then never taken, so with few workers the scanner blocked on the full
   queue and `run()` waited on `scanner.join()` forever. Reproduced with one worker and a file without read
   permission (the process never ended). Now the worker skips that file, prints `(skipped)` and carries on; the
   same run finishes normally. Tests still pass. `challenge.zip` was updated with the fix.
4. **Task 4: two small code-quality cleanups.** `allOf(...)` used a raw `CompletableFuture[]` (compiler warning
   with `-Xlint`), now `CompletableFuture<?>[]`; and a one-element `long[]` used as a variable was replaced by a
   plain `long`. Output is unchanged.

All other code compiles without warnings and matches the task statements.

## Before you submit

- **Regenerate `task5/deadlock.txt` on your own machine** (the checklist asks for a report from your machine; the one
  here was made in a cloud container). Steps are in [`task5/README.md`](task5/README.md#steps-1-2-reproduce-the-deadlock-and-capture-it-with-jstack).
- Compare `task1/`, `task2/` and `task3/` with your own copies (see above).
