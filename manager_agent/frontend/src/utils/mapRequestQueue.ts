interface QueueJob {
  key: string
  priority: number
  run: () => Promise<any>
  valid: () => boolean
  resolve: (value: any) => void
}

// SDK requests share one bounded queue; route queries precede background POI searches.
export function createMapRequestQueue(options: { interval?: number; limit?: number; sleep?: (ms: number) => Promise<void> } = {}) {
  const interval = options.interval ?? 700
  const limit = options.limit ?? 48
  const sleep = options.sleep ?? (ms => new Promise(resolve => setTimeout(resolve, ms)))
  const pending = new Map<string, Promise<any>>()
  const jobs: QueueJob[] = []
  let running = false
  let disposed = false
  async function drain() {
    if (running) return
    running = true
    try {
      while (jobs.length) {
        jobs.sort((a, b) => b.priority - a.priority)
        const job = jobs.shift()!
        if (disposed || !job.valid()) {
          pending.delete(job.key)
          job.resolve({ info: 'CANCELLED' })
          continue
        }
        let value: any
        try { value = await job.run() } catch { value = { info: 'SERVICE_ERROR' } }
        pending.delete(job.key)
        job.resolve(value)
        await sleep(interval)
      }
    } finally { running = false }
  }
  return {
    promote(key: string, priority: number) {
      const job = jobs.find(job => job.key === key)
      if (job) job.priority = Math.max(job.priority, priority)
    },
    schedule(key: string, run: () => Promise<any>, valid = () => true, priority = 0): Promise<any> {
      const existing = pending.get(key)
      if (existing) {
        const queued = jobs.find(job => job.key === key)
        if (queued) queued.priority = Math.max(queued.priority, priority)
        return existing
      }
      if (disposed) return Promise.resolve({ info: 'CANCELLED' })
      if (pending.size >= limit) return Promise.resolve({ info: 'QUEUE_FULL' })
      let resolve!: (value: any) => void
      const task = new Promise<any>(done => { resolve = done })
      pending.set(key, task)
      jobs.push({ key, priority, run, valid, resolve })
      void drain()
      return task
    },
    dispose() {
      disposed = true
      for (const job of jobs.splice(0)) {
        pending.delete(job.key)
        job.resolve({ info: 'CANCELLED' })
      }
    },
  }
}
