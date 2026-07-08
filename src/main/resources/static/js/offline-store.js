(function (global) {
    'use strict';

    const DB_NAME = 'scripty-offline';
    const DB_VERSION = 1;
    const STORE = 'projects';

    function openDb() {
        return new Promise(function (resolve, reject) {
            const request = indexedDB.open(DB_NAME, DB_VERSION);
            request.onupgradeneeded = function () {
                const db = request.result;
                if (!db.objectStoreNames.contains(STORE)) {
                    db.createObjectStore(STORE, { keyPath: 'id' });
                }
            };
            request.onsuccess = function () { resolve(request.result); };
            request.onerror = function () { reject(request.error); };
        });
    }

    function runTx(db, mode, fn) {
        return new Promise(function (resolve, reject) {
            const tx = db.transaction(STORE, mode);
            const result = fn(tx.objectStore(STORE));
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
        return runTx(db, 'readwrite', function (store) {
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

    global.scriptyOfflineStore = {
        saveProjectSnapshot: saveProjectSnapshot,
        getProjectSnapshot: getProjectSnapshot,
        listCachedProjects: listCachedProjects
    };
}(window));
