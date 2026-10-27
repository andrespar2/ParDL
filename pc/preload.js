'use strict';

const { contextBridge, ipcRenderer } = require('electron');

function subscribe(channel, callback) {
  const handler = (event, payload) => callback(payload);
  ipcRenderer.on(channel, handler);
  return () => ipcRenderer.removeListener(channel, handler);
}

contextBridge.exposeInMainWorld('pardl', {
  getInfo: () => ipcRenderer.invoke('app:info'),
  getPrefs: () => ipcRenderer.invoke('prefs:get'),
  setPrefs: (patch) => ipcRenderer.invoke('prefs:set', patch),
  readClipboard: () => ipcRenderer.invoke('clipboard:read'),
  chooseFolder: () => ipcRenderer.invoke('dialog:chooseFolder'),
  engineVersion: () => ipcRenderer.invoke('engine:version'),
  updateEngine: () => ipcRenderer.invoke('engine:update'),
  analyze: (url) => ipcRenderer.invoke('analyze', url),
  startDownload: (job) => ipcRenderer.invoke('download:start', job),
  cancelDownload: () => ipcRenderer.invoke('download:cancel'),
  findMetadata: (query) => ipcRenderer.invoke('metadata:find', query),
  findLyrics: (query) => ipcRenderer.invoke('lyrics:find', query),
  providers: () => ipcRenderer.invoke('providers:list'),
  testProvider: (id) => ipcRenderer.invoke('providers:test', id),
  history: () => ipcRenderer.invoke('history:list'),
  clearHistory: () => ipcRenderer.invoke('history:clear'),
  removeHistory: (id) => ipcRenderer.invoke('history:remove', id),
  openFile: (target) => ipcRenderer.invoke('file:open', target),
  showInFolder: (target) => ipcRenderer.invoke('file:show', target),
  openFolder: (folder) => ipcRenderer.invoke('folder:open', folder),
  openExternal: (url) => ipcRenderer.invoke('external:open', url),
  minimize: () => ipcRenderer.send('window:minimize'),
  maximize: () => ipcRenderer.send('window:maximize'),
  close: () => ipcRenderer.send('window:close'),
  isMaximized: () => ipcRenderer.invoke('window:isMaximized'),
  onDownloadProgress: (callback) => subscribe('download:progress', callback),
  onDownloadComplete: (callback) => subscribe('download:complete', callback),
  onDownloadError: (callback) => subscribe('download:error', callback),
  onEngineUpdated: (callback) => subscribe('engine:updated', callback),
  onWindowMaximized: (callback) => subscribe('window:maximized', callback),
  onBrowserOpenUrl: (callback) => subscribe('browser:open-url', callback)
});
