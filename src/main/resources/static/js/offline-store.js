(function (global) {
    'use strict';

    const DB_NAME = 'scripty-offline';
    const DB_VERSION = 2;
    const STORE = 'projects';
    const OUTBOX_STORE = 'outbox';

    function openDb() {
        return new Promise(function (resolve, reject) {
            const request = indexedDB.open(DB_NAME, DB_VERSION);
            request.onupgradeneeded = function () {
                const db = request.result;
                if (!db.objectStoreNames.contains(STORE)) {
                    db.createObjectStore(STORE, { keyPath: 'id' });
                }
                if (!db.objectStoreNames.contains(OUTBOX_STORE)) {
                    const outbox = db.createObjectStore(OUTBOX_STORE, { keyPath: 'id' });
                    outbox.createIndex('projectId', 'projectId', { unique: false });
                    outbox.createIndex('createdAt', 'createdAt', { unique: false });
                }
            };
            request.onsuccess = function () { resolve(request.result); };
            request.onerror = function () { reject(request.error); };
        });
    }

    function runTx(db, storeName, mode, fn) {
        return new Promise(function (resolve, reject) {
            const tx = db.transaction(storeName, mode);
            const result = fn(tx.objectStore(storeName));
            tx.oncomplete = function () {
                db.close();
                resolve(result);
            };
            tx.onerror = function () {
                db.close();
                reject(tx.error);
            };
        });
    }

    async function saveProjectSnapshot(snapshot) {
        if (!snapshot || !snapshot.id) return;
        const db = await openDb();
        return runTx(db, STORE, 'readwrite', function (store) {
            store.put(Object.assign({}, snapshot, {
                id: Number(snapshot.id),
                cachedAt: snapshot.cachedAt || Date.now()
            }));
        });
    }

    async function getProjectSnapshot(id) {
        const db = await openDb();
        return new Promise(function (resolve, reject) {
            const tx = db.transaction(STORE, 'readonly');
            const request = tx.objectStore(STORE).get(Number(id));
            request.onsuccess = function () {
                db.close();
                resolve(request.result || null);
            };
            request.onerror = function () {
                db.close();
                reject(request.error);
            };
        });
    }

    async function listCachedProjects() {
        const db = await openDb();
        return new Promise(function (resolve, reject) {
            const tx = db.transaction(STORE, 'readonly');
            const request = tx.objectStore(STORE).getAll();
            request.onsuccess = function () {
                db.close();
                const items = (request.result || []).sort(function (a, b) {
                    return (b.cachedAt || 0) - (a.cachedAt || 0);
                });
                resolve(items);
            };
            request.onerror = function () {
                db.close();
                reject(request.error);
            };
        });
    }

    async function enqueueBlockEdit(entry) {
        return enqueueOperation(Object.assign({}, entry, {
            type: 'blockEdit'
        }));
    }

    async function enqueueOperation(entry) {
        if (!entry || !entry.type) return;
        const db = await openDb();
        const record = Object.assign({}, entry, {
            id: entry.id || (entry.type + '-' + (entry.blockId || entry.tempBlockId || Date.now()) + '-' + Date.now()),
            createdAt: entry.createdAt || Date.now()
        });
        return runTx(db, OUTBOX_STORE, 'readwrite', function (store) {
            store.put(record);
        });
    }

    async function updateOperation(id, updates) {
        const db = await openDb();
        return new Promise(function (resolve, reject) {
            const tx = db.transaction(OUTBOX_STORE, 'readwrite');
            const store = tx.objectStore(OUTBOX_STORE);
            const request = store.get(id);
            request.onsuccess = function () {
                const existing = request.result;
                if (!existing) {
                    resolve(null);
                    return;
                }
                store.put(Object.assign({}, existing, updates, { id: existing.id }));
            };
            tx.oncomplete = function () {
                db.close();
                resolve();
            };
            tx.onerror = function () {
                db.close();
                reject(tx.error);
            };
        });
    }

    async function findPendingCreateByTempId(tempBlockId) {
        const db = await openDb();
        return new Promise(function (resolve, reject) {
            const tx = db.transaction(OUTBOX_STORE, 'readonly');
            const request = tx.objectStore(OUTBOX_STORE).getAll();
            request.onsuccess = function () {
                db.close();
                const match = (request.result || []).find(function (entry) {
                    return entry.type === 'blockCreateBelow' &&
                        String(entry.tempBlockId) === String(tempBlockId);
                });
                resolve(match || null);
            };
            request.onerror = function () {
                db.close();
                reject(request.error);
            };
        });
    }

    async function updatePendingCreateContent(tempBlockId, content) {
        const pending = await findPendingCreateByTempId(tempBlockId);
        if (!pending) return null;
        const payload = Object.assign({}, pending.payload, { content: content });
        await updateOperation(pending.id, { payload: payload });
        return pending.id;
    }

    async function listPendingOperations(projectId) {
        const db = await openDb();
        return new Promise(function (resolve, reject) {
            const tx = db.transaction(OUTBOX_STORE, 'readonly');
            const store = tx.objectStore(OUTBOX_STORE);
            const request = projectId != null
                ? store.index('projectId').getAll(Number(projectId))
                : store.getAll();
            request.onsuccess = function () {
                db.close();
                const items = (request.result || []).sort(function (a, b) {
                    return (a.createdAt || 0) - (b.createdAt || 0);
                });
                resolve(items);
            };
            request.onerror = function () {
                db.close();
                reject(request.error);
            };
        });
    }

    async function listPendingEdits(projectId) {
        const ops = await listPendingOperations(projectId);
        return ops.filter(function (entry) { return entry.type === 'blockEdit'; });
    }

    async function countPendingEdits(projectId) {
        const ops = await listPendingOperations(projectId);
        return ops.length;
    }

    async function removePendingOperation(id) {
        const db = await openDb();
        return runTx(db, OUTBOX_STORE, 'readwrite', function (store) {
            store.delete(id);
        });
    }

    async function removePendingEdit(id) {
        return removePendingOperation(id);
    }

    async function clearPendingEditsForBlock(blockId) {
        const db = await openDb();
        return new Promise(function (resolve, reject) {
            const tx = db.transaction(OUTBOX_STORE, 'readwrite');
            const store = tx.objectStore(OUTBOX_STORE);
            const request = store.getAll();
            request.onsuccess = function () {
                (request.result || []).forEach(function (entry) {
                    if (Number(entry.blockId) === Number(blockId)) {
                        store.delete(entry.id);
                    }
                });
            };
            tx.oncomplete = function () {
                db.close();
                resolve();
            };
            tx.onerror = function () {
                db.close();
                reject(tx.error);
            };
        });
    }

    global.scriptyOfflineStore = {
        saveProjectSnapshot: saveProjectSnapshot,
        getProjectSnapshot: getProjectSnapshot,
        listCachedProjects: listCachedProjects,
        enqueueBlockEdit: enqueueBlockEdit,
        enqueueOperation: enqueueOperation,
        listPendingEdits: listPendingEdits,
        listPendingOperations: listPendingOperations,
        countPendingEdits: countPendingEdits,
        removePendingEdit: removePendingEdit,
        removePendingOperation: removePendingOperation,
        clearPendingEditsForBlock: clearPendingEditsForBlock,
        findPendingCreateByTempId: findPendingCreateByTempId,
        updatePendingCreateContent: updatePendingCreateContent
    };
}(window));
