/* A reset must finish committing storage before the app starts a fresh session. */
(() => {
  const RESET_KEY = 'roadprints:delete-all-in-progress:v1';

  async function clearDatabase(openDatabase) {
    const database = await openDatabase();
    try {
      const stores = Array.from(database.objectStoreNames);
      if (!stores.length) return;
      await new Promise((resolve, reject) => {
        // All stores in each database commit together. Request success alone
        // does not mean the transaction has committed.
        const transaction = database.transaction(stores, 'readwrite');
        transaction.oncomplete = resolve;
        transaction.onabort = () => reject(transaction.error || new Error('Saved data reset was aborted.'));
        transaction.onerror = () => reject(transaction.error || new Error('Saved data reset failed.'));
        for (const name of stores) transaction.objectStore(name).clear();
      });
    } finally { database.close(); }
  }

  function openLegacyCoverage() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open('roadprints-coverage');
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('Legacy coverage could not be opened.'));
      request.onblocked = () => reject(new Error('Close other Roadprints tabs and retry deleting your data.'));
    });
  }

  async function reset({storage, stopWork, whenReady, openArchive, openLegacy = openLegacyCoverage, reload}) {
    // Retain this marker on failure or page interruption. Startup resumes the
    // deletion instead of importing or matching the surviving half of a reset.
    storage.setItem(RESET_KEY, 'pending');
    stopWork();
    await whenReady();
    await clearDatabase(openArchive);
    await clearDatabase(openLegacy);
    const keys = Array.from({length: storage.length}, (_, index) => storage.key(index));
    for (const key of keys) {
      if (key !== RESET_KEY && (key === 'uk-road-tracker-progress-v1' || /^roadprints(?:[-:])/.test(key || ''))) {
        storage.removeItem(key);
      }
    }
    storage.removeItem(RESET_KEY);
    // Reload discards every old UI, ledger and import object together.
    reload();
  }

  window.RoadprintsLocalDataReset = Object.freeze({RESET_KEY, clearDatabase, reset});
})();
