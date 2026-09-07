---
{:title "A process is born"
 :slug "a-process-is-born"
 :locale "en"
 :date "2026-09-06"
 :category "elixir"
 :tags ["beam", "internals"]
 :excerpt "A look into the entrails for a process and how it's created"}
---

I'd like to get a better understanding of how the beam works, a natural place to start is the process.
```elixir
spawn(fn -> receive do _ -> "a shout into the void" end end)
```

Our soon to be born baby process will be spawned from the same node, it has a parent process living in the same house... sorry, node. It makes the code we need to go through a bit shorter.

We can specify some options, in elixir we'd use `Process.spawn/4`
```elixir
def spawn_with_opts do
    Process.spawn(Anatomy, :wait_for_msg, [], [:link, {:message_queue_data, :off_heap}])
end
```

Some interesting [options](https://www.erlang.org/doc/apps/erts/erlang.html#spawn_opt/4): 
- you can make it linked to the parent process.
- you can setup a monitor, a message will be sent to notify of the process's death, natural or accidental.
- you can (damn it's getting repetitive) set a min heap size, the default is 233 words, a word being 8 bytes for 64-bit VMs. it grows in a fibonacci sequence style (up to a certain point) and if you have big plans for the future of the process, knowing it will need a large heap, it can be interesting to save the gc the trouble of growing it god knows how many times. The gc copies live data over from old to new, on top of allocation cost, it adds up.
- there is `message_queue_data` this one is interesting and will probably get a post to look into it deeper. You can set it to on_heap or off_heap as the name suggests, off_heap will be lighter on GC but messages would always be allocated which has a higher cost but spares you lock contention which might be the right tradeoff for a process getting a ton of messages from many processes... anyways, have a look here for a [better explanation](https://blog.stenmans.org/theBeamBook/#_lock_free_message_passing).
- a few others I won't bother with here 

erlang calls a BIF, a built in function implemented in C as `spawn_opt_4`, it will parse the options then create the process through [`erl_create_process`](https://github.com/erlang/otp/blob/bf4d114857f84d60766882c079dc461bc1bb3aba/erts/emulator/beam/erl_process.c#L12437)


the process is represented with a ... drum roll ... [`process`](https://github.com/erlang/otp/blob/bf4d114857f84d60766882c079dc461bc1bb3aba/erts/emulator/beam/erl_process.h#L1043) struct called the PCB, process control block. Many things omitted for brevity.
```c
struct process {
    ErtsPTabElementCommon common;
    // ...
    Eterm* arg_reg;             /* Pointer to argument registers. */
    Eterm def_arg_reg[6];       /* Default array for argument registers. */

    Eterm* heap;                /* Heap start */
    Eterm* hend;                /* Heap end */
    // ...
    Uint heap_sz;               /* Size of heap in words */
    Uint min_heap_size;         /* Minimum size of heap (in words). */
    Uint min_vheap_size;        /* Minimum size of virtual heap (in words). */
    Uint max_heap_size;         /* Maximum size of heap (in words). */

    ErtsCodePtr i;              /* Program counter. */
    // ...
    Uint reds;                  /* No of reductions for this process  */
    // ...
    ErtsSignalPrivQueues sig_qs; /* Signal queues */
    // ...
    ProcDict *dictionary;        /* Process dictionary, may be NULL */
    // ...
    const ErtsCodeMFA* current; /* Current Erlang function, part of the
                                 * funcinfo:
                                 *
                                 * module(0), function(1), arity(2)
                                 *
                                 * (module and functions are tagged atoms;
                                 * arity an untagged integer).
                                 */

    // ...
    ErlOffHeap off_heap;	/* Off-heap data updated by copy_struct(). */
    // ...
    erts_atomic32_t state;      /* Process state flags (see ERTS_PSFLG_*) */
    // ...
    ErtsSignalInQueue sig_inq;
    // ...
};
```
Throughout the function it will get filled up and properly initialized. The process will be given a PID and its own spot on the global `PTab` process registry. The heap will be allocated. The execution state properly initialized: instruction pointer set to `beam_run_process`, the module, function and args setup in their registers. Some tracing stuff happens that I chose to ignore this time around. links and monitors get setup. and to finish up the process is scheduled.

In elixir we can call `Process.info/1` to get a peek
```
iex(15)> Process.info(self())
[
  current_function: {Process, :info, 1},
  initial_call: {:proc_lib, :init_p, 5},
  status: :running,
  message_queue_len: 0,
  links: [#PID<0.247.0>],
  dictionary: [...],
  trap_exit: false,
  error_handler: :error_handler,
  priority: :normal,
  group_leader: #PID<0.70.0>,
  total_heap_size: 10957,
  heap_size: 4185,
  stack_size: 58,
  reductions: 14830,
  garbage_collection: [
    max_heap_size: %{
      error_logger: true,
      include_shared_binaries: false,
      kill: true,
      size: 0
    },
    min_bin_vheap_size: 46422,
    min_heap_size: 233,
    fullsweep_after: 65535,
    minor_gcs: 3
  ],
  suspending: []
]
```

I've learned quite a bit, and I'm glad I got to read some professional C code, damn it can get complex. Next time we'll be looking at how the scheduler works: reductions and how they're used to budget process execution time fairly.