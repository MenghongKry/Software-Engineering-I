## start() vs run(): what --sequential shows

**Normal mode (`start()`):** 
start() creates a NEW thread and runs `run()` on it. main continues right away,
so "all launched" prints while the countdowns are still going. The four
countdowns run at the same time, so their lines mix together and the
order changes on every run. Total time is about 0.8 s (one countdown,
because all four overlap). Each line shows its own thread name.
After `join()`, every thread is TERMINATED.

**Sequential mode (`run()`):**
run() is just a normal method call. No new thread is created; the code runs
on the main thread, one countdown after another. So:
- Total time is about 2.4 s (4 x 0.6 s), with no overlap.
- `"all launched"` prints only after every countdown has already finished.
- `gemini`, `mercury` and `artemis` print "main: 3" instead of their own name,
because CountdownTask asks `Thread.currentThread().getName()`, and the
current thread is main. apollo still prints "apollo", because Countdown
uses `getName()` on itself (the Thread object), not on the running thread.
- `join()` returns immediately, since the threads were never started.
- `getState()` is NEW for all four: the Thread objects exist but never ran.

**Rule: always call start(). Calling run() directly gives you no concurrency.**

## Key concept of Task 1
`start()` creates a new thread, so the code runs at the same time as main.
`run()` is just a method call that runs on whatever thread called it.
`join()` makes main wait until a thread is finished.
Thread states: NEW (created, never started), then `RUNNABLE` and others while running, then TERMINATED (finished).
Virtual threads are created with `Thread.ofVirtual()`. They work like normal threads in your code but are much cheaper, so `isVirtual()` is true only for artemis.
