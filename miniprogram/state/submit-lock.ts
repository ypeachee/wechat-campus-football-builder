export class SubmitLock {
  private locked = false;

  tryAcquire(): boolean {
    if (this.locked) {
      return false;
    }
    this.locked = true;
    return true;
  }

  release(): void {
    this.locked = false;
  }

  isLocked(): boolean {
    return this.locked;
  }
}

export async function withSubmitLock<T>(lock: SubmitLock, task: () => Promise<T>): Promise<T | undefined> {
  if (!lock.tryAcquire()) {
    return undefined;
  }
  try {
    return await task();
  } finally {
    lock.release();
  }
}
