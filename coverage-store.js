/*
 * Local persistence foundation for multimodal coverage.
 *
 * This deliberately lives beside the existing motorway archive rather than
 * altering it: motorway progress remains stable while walking/running/cycling
 * evidence earns its own acceptance workflow.
 */
(() => {
  const DB_NAME = 'roadprints-coverage';
  const DB_VERSION = 1;
  const STORES = {
    activities: 'activities',
    evidence: 'segment-evidence',
    jobs: 'matching-jobs',
    tiles: 'network-tiles'
  };
  const MODES = new Set(['driving', 'on_foot', 'cycling']);
  const STATES = new Set(['queued', 'matching', 'matched', 'ambiguous', 'rejected', 'failed']);

  function open() {
    const resetReady = window.RoadprintsExperimentalCoverageResetReady || Promise.resolve();
    return resetReady.then(() => new Promise((resolve, reject) => {
      if (!globalThis.indexedDB) return reject(new Error('This browser does not provide IndexedDB storage.'));
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onerror = () => reject(request.error || new Error('Coverage storage could not be opened.'));
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORES.activities)) {
          const store = db.createObjectStore(STORES.activities, {keyPath: 'id'});
          store.createIndex('by-source', 'sourceId', {unique: false});
          store.createIndex('by-tile', 'tileIds', {multiEntry: true});
          store.createIndex('by-state', 'state', {unique: false});
          store.createIndex('by-mode', 'mode', {unique: false});
        }
        if (!db.objectStoreNames.contains(STORES.evidence)) {
          const store = db.createObjectStore(STORES.evidence, {keyPath: 'id'});
          store.createIndex('by-activity', 'activityId', {unique: false});
          store.createIndex('by-segment', 'segmentId', {unique: false});
          store.createIndex('by-tile', 'tileId', {unique: false});
          store.createIndex('by-acceptance', 'acceptance', {unique: false});
        }
        if (!db.objectStoreNames.contains(STORES.jobs)) {
          const store = db.createObjectStore(STORES.jobs, {keyPath: 'id'});
          store.createIndex('by-status', 'status', {unique: false});
          store.createIndex('by-source', 'sourceId', {unique: false});
        }
        if (!db.objectStoreNames.contains(STORES.tiles)) {
          const store = db.createObjectStore(STORES.tiles, {keyPath: 'id'});
          store.createIndex('by-version', 'version', {unique: false});
        }
      };
      request.onsuccess = () => resolve(request.result);
    }));
  }

  async function transaction(storeName, mode, operation) {
    const db = await open();
    try {
      return await new Promise((resolve, reject) => {
        const tx = db.transaction(storeName, mode);
        const request = operation(tx.objectStore(storeName));
        request.onerror = () => reject(request.error || new Error('Coverage storage operation failed.'));
        request.onsuccess = () => resolve(request.result);
        tx.onabort = () => reject(tx.error || new Error('Coverage storage transaction was aborted.'));
      });
    } finally { db.close(); }
  }

  function normaliseActivity(activity) {
    const mode = String(activity?.mode || 'on_foot');
    const state = String(activity?.state || 'queued');
    if (!activity?.id || !MODES.has(mode) || !STATES.has(state)) throw new Error('Invalid coverage activity.');
    const points = Array.isArray(activity.points) ? activity.points
      .filter(point => Number.isFinite(Number(point?.lat)) && Number.isFinite(Number(point?.lng)))
      .map(point => ({lat: Number(point.lat), lng: Number(point.lng)})) : [];
    if (points.length < 2) throw new Error('A coverage activity needs at least two recorded points.');
    return {
      id: String(activity.id), sourceId: String(activity.sourceId || ''), mode, state,
      startedAt: activity.startedAt || null, endedAt: activity.endedAt || null,
      tileIds: [...new Set((activity.tileIds || []).map(String))], points,
      matcherVersion: activity.matcherVersion || null, matchedGeoJson: activity.matchedGeoJson || null,
      matchError: activity.matchError || null, createdAt: activity.createdAt || new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
  }

  function normaliseEvidence(evidence) {
    const acceptance = String(evidence?.acceptance || 'ambiguous');
    if (!evidence?.id || !evidence?.activityId || !evidence?.segmentId || !['accepted', 'ambiguous', 'rejected'].includes(acceptance)) {
      throw new Error('Invalid segment evidence.');
    }
    return {
      id: String(evidence.id), activityId: String(evidence.activityId), segmentId: String(evidence.segmentId),
      tileId: String(evidence.tileId || ''), mode: String(evidence.mode || 'on_foot'), acceptance,
      confidence: Number.isFinite(Number(evidence.confidence)) ? Number(evidence.confidence) : null,
      matcherVersion: evidence.matcherVersion || null, createdAt: evidence.createdAt || new Date().toISOString()
    };
  }

  function putActivity(activity) { return transaction(STORES.activities, 'readwrite', store => store.put(normaliseActivity(activity))); }
  function putEvidence(evidence) { return transaction(STORES.evidence, 'readwrite', store => store.put(normaliseEvidence(evidence))); }
  function putJob(job) {
    if (!job?.id || !job?.sourceId || !job?.status) return Promise.reject(new Error('Invalid matching job.'));
    return transaction(STORES.jobs, 'readwrite', store => store.put({...job, updatedAt: new Date().toISOString()}));
  }
  function putTile(tile) {
    if (!tile?.id || !tile?.version || !tile?.bounds) return Promise.reject(new Error('Invalid network tile.'));
    return transaction(STORES.tiles, 'readwrite', store => store.put({...tile, updatedAt: new Date().toISOString()}));
  }
  function getAll(storeName) { return transaction(storeName, 'readonly', store => store.getAll()); }
  async function clearActivitiesForTile(tileId) {
    const db = await open();
    try {
      await new Promise((resolve, reject) => {
        const tx = db.transaction(STORES.activities, 'readwrite');
        const request = tx.objectStore(STORES.activities).index('by-tile').openCursor(IDBKeyRange.only(String(tileId)));
        request.onerror = () => reject(request.error || new Error('Coverage activity removal failed.'));
        request.onsuccess = () => { const cursor = request.result; if (cursor) { cursor.delete(); cursor.continue(); } };
        tx.oncomplete = () => resolve(); tx.onabort = () => reject(tx.error || new Error('Coverage activity removal was aborted.'));
      });
    } finally { db.close(); }
  }

  window.RoadprintsCoverageStore = Object.freeze({
    DB_NAME, DB_VERSION, STORES, putActivity, putEvidence, putJob, putTile,
    getActivities: () => getAll(STORES.activities),
    getEvidence: () => getAll(STORES.evidence),
    getJobs: () => getAll(STORES.jobs),
    getTiles: () => getAll(STORES.tiles), clearActivitiesForTile
  });
})();
