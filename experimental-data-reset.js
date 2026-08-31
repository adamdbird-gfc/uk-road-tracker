/* One-off reset before replacing the experimental coverage UI. */
(() => {
  const RESET_KEY = 'roadprints-experimental-coverage-reset-v1';
  const DB_NAME = 'roadprints-coverage';
  if (!globalThis.indexedDB || localStorage.getItem(RESET_KEY) === 'done') {
    window.RoadprintsExperimentalCoverageResetReady = Promise.resolve();
    return;
  }
  window.RoadprintsExperimentalCoverageResetReady = new Promise(resolve => {
    const request = indexedDB.deleteDatabase(DB_NAME);
    request.onsuccess = () => { localStorage.setItem(RESET_KEY, 'done'); resolve(); };
    request.onerror = () => resolve();
    request.onblocked = () => resolve();
  });
})();
