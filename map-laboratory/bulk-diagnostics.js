/* Roadprints ZIP v1. Read the central directory and inflate one private entry at a time. */
const BulkDiagnostics = (() => {
  const decoder = new TextDecoder('utf-8', {fatal: true});
  const limits = {manifest: 32 * 1024 * 1024, record: 128 * 1024 * 1024};
  const crcTable = Uint32Array.from({length: 256}, (_, initial) => {
    let value = initial;
    for (let bit = 0; bit < 8; bit++) value = (value >>> 1) ^ (value & 1 ? 0xedb88320 : 0);
    return value >>> 0;
  });

  async function directory(file) {
    const tail = await file.slice(Math.max(0, file.size - 65557)).arrayBuffer();
    const endView = new DataView(tail);
    let end = tail.byteLength - 22;
    while (end >= 0 && (endView.getUint32(end, true) !== 0x06054b50 ||
      end + 22 + endView.getUint16(end + 20, true) !== tail.byteLength)) end--;
    if (end < 0) throw Error('Incomplete or invalid diagnostics ZIP');
    const count = endView.getUint16(end + 10, true);
    const size = endView.getUint32(end + 12, true);
    const start = endView.getUint32(end + 16, true);
    if (endView.getUint32(end + 4, true) || endView.getUint16(end + 8, true) !== count ||
        count === 0xffff || size > limits.manifest || start + size !== file.size - tail.byteLength + end)
      throw Error('Unsupported diagnostics ZIP directory');
    const bytes = await file.slice(start, start + size).arrayBuffer();
    const view = new DataView(bytes), entries = new Map();
    let cursor = 0;
    for (let i = 0; i < count; i++) {
      if (cursor + 46 > size || view.getUint32(cursor, true) !== 0x02014b50)
        throw Error('Invalid diagnostics ZIP directory');
      const flags = view.getUint16(cursor + 8, true);
      const method = view.getUint16(cursor + 10, true);
      const nameLength = view.getUint16(cursor + 28, true);
      const next = cursor + 46 + nameLength + view.getUint16(cursor + 30, true) + view.getUint16(cursor + 32, true);
      const offset = view.getUint32(cursor + 42, true);
      if (next > size || offset + 30 > start || view.getUint16(cursor + 34, true) ||
          flags & ~0x080e || (method !== 0 && method !== 8))
        throw Error('Unsupported diagnostics ZIP entry');
      const name = decoder.decode(new Uint8Array(bytes, cursor + 46, nameLength));
      const entry = {
        name, flags, method, offset, directoryStart: start,
        crc: view.getUint32(cursor + 16, true),
        compressed: view.getUint32(cursor + 20, true),
        size: view.getUint32(cursor + 24, true)
      };
      if (!/^(manifest\.json|journeys\/[1-9][0-9]*\.json)$/.test(name) || entries.has(name))
        throw Error('Unexpected or duplicate diagnostics ZIP entry');
      entries.set(name, entry);
      cursor = next;
    }
    if (cursor !== size) throw Error('Invalid diagnostics ZIP directory size');
    return entries;
  }

  async function readJson(file, entry, limit) {
    if (!entry || entry.size > limit) throw Error('Missing or oversized diagnostics ZIP entry');
    const bytes = await file.slice(entry.offset, entry.offset + 30).arrayBuffer();
    const header = new DataView(bytes);
    if (header.getUint32(0, true) !== 0x04034b50 || header.getUint16(6, true) !== entry.flags ||
        header.getUint16(8, true) !== entry.method) throw Error('Invalid diagnostics ZIP entry header');
    const nameEnd = entry.offset + 30 + header.getUint16(26, true);
    const start = nameEnd + header.getUint16(28, true);
    if (start + entry.compressed > entry.directoryStart ||
        decoder.decode(await file.slice(entry.offset + 30, nameEnd).arrayBuffer()) !== entry.name)
      throw Error('Invalid diagnostics ZIP entry location');
    let stream = file.slice(start, start + entry.compressed).stream();
    if (entry.method === 8) stream = stream.pipeThrough(new DecompressionStream('deflate-raw'));
    const reader = stream.getReader(), payload = new Uint8Array(entry.size);
    let size = 0, crc = 0xffffffff;
    try {
      for (;;) {
        const {value, done} = await reader.read();
        if (done) break;
        if (size + value.length > entry.size) throw Error('Diagnostics ZIP entry exceeds declared size');
        payload.set(value, size);
        size += value.length;
        for (const byte of value) crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
      }
    } catch (error) {
      await reader.cancel().catch(() => {});
      throw error;
    } finally {
      reader.releaseLock();
    }
    if (size !== entry.size || ((crc ^ 0xffffffff) >>> 0) !== entry.crc)
      throw Error('Diagnostics ZIP entry is damaged');
    return JSON.parse(decoder.decode(payload));
  }

  async function open(file) {
    const entries = await directory(file);
    const manifest = await readJson(file, entries.get('manifest.json'), limits.manifest);
    if (manifest?.format !== 'roadprints_bulk_journey_diagnostics' ||
        manifest.schema_version !== 1 || manifest.export_complete !== true ||
        !Array.isArray(manifest.journeys) || manifest.journey_count !== manifest.journeys.length ||
        entries.size !== manifest.journeys.length + 1)
      throw Error('Choose a complete Roadprints bulk journey diagnostics ZIP (version 1)');
    const ids = new Set();
    const journeys = manifest.journeys.map((item, index) => {
      const path = 'journeys/' + (index + 1) + '.json';
      if (typeof item?.journey_id !== 'string' || !item.journey_id || ids.has(item.journey_id) || !entries.has(path))
        throw Error('Invalid or duplicate journey in diagnostics index');
      ids.add(item.journey_id);
      return {...item, path};
    });
    const time = item => Date.parse(item.started_at) || 0;
    journeys.sort((a, b) => {
      const aTime = Date.parse(a.started_at), bTime = Date.parse(b.started_at);
      return Number.isFinite(aTime) !== Number.isFinite(bTime) ? (Number.isFinite(aTime) ? -1 : 1) :
        time(a) - time(b) || (a.journey_id < b.journey_id ? -1 : 1);
    });
    return {
      journeys,
      async read(index) {
        const item = journeys[index];
        if (!item) throw Error('Journey is outside the diagnostics index');
        const record = await readJson(file, entries.get(item.path), limits.record);
        if (record?.journey?.journey_id !== item.journey_id ||
           ['started_at', 'ended_at', 'mode', 'timezone'].some(key =>
             (typeof record.journey[key] === 'string' ? record.journey[key] : null) !== item[key]) ||
            !Array.isArray(record.stored) || !Array.isArray(record.unavailable))
          throw Error('Journey record does not match the diagnostics index');
        return record;
      }
    };
  }

  // Preserve the original replay ordinal for screenshots and direct jumps.
  function roadReplay(archive) {
    const entries = archive.journeys.map((journey, index) => ({...journey, archiveOrdinal: index + 1}))
      .filter(journey => !/^(train|rail|railway|subway|metro|tram)$/i.test(journey.mode?.trim() || ''));
    return {
      journeys: entries,
      archiveCount: archive.journeys.length,
      railHidden: archive.journeys.length - entries.length,
      read(index) {
        const entry = entries[index];
        if (!entry) throw Error('Journey is outside the replay index');
        return archive.read(entry.archiveOrdinal - 1);
      }
    };
  }

  return {open, roadReplay};
})();
if (typeof module !== 'undefined') module.exports = BulkDiagnostics;
