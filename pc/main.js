'use strict';

const { app, BrowserWindow, ipcMain, dialog, shell, Notification, session, clipboard } = require('electron');
const path = require('path');
const fs = require('fs');
const fsp = fs.promises;
const { spawn, execFile } = require('child_process');
const crypto = require('crypto');
const { parseFile } = require('music-metadata');
const NodeID3 = require('node-id3');

const APP_VERSION = require('./package.json').version;
const IS_DEV = !app.isPackaged;
const DEFAULT_FOLDER = () => path.join(app.getPath('downloads'), 'ParDL');
const DEFAULT_PREFS = {
  theme: 'dark',
  folder: '',
  embedMetadata: true,
  embedCover: true,
  embedLyrics: true,
  organize: false,
  autoUpdate: true,
  lastEngineUpdate: ''
};

let mainWindow = null;
let prefs = null;
let activeDownload = null;
let engineInfo = { version: '', path: '', error: '' };
let engineUpdateRunning = false;

/* ------------------------------------------------------------------ */
/* Utilidades                                                          */
/* ------------------------------------------------------------------ */

function userDataPath(...parts) {
  return path.join(app.getPath('userData'), ...parts);
}

function prefsPath() { return userDataPath('prefs.json'); }
function historyPath() { return userDataPath('history.json'); }

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function writeJson(file, value) {
  const tmp = `${file}.tmp`;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2), 'utf8');
  fs.renameSync(tmp, file);
}

function getPrefs() {
  if (!prefs) {
    const loaded = readJson(prefsPath(), {});
    prefs = { ...DEFAULT_PREFS, ...(loaded && typeof loaded === 'object' ? loaded : {}) };
    if (!prefs.folder) prefs.folder = DEFAULT_FOLDER();
    for (const key of ['embedMetadata', 'embedCover', 'embedLyrics', 'organize', 'autoUpdate']) {
      prefs[key] = prefs[key] !== false;
    }
    if (!['dark', 'light', 'amoled'].includes(prefs.theme)) prefs.theme = 'dark';
  }
  return prefs;
}

function savePrefs(patch) {
  const allowed = ['theme', 'folder', 'embedMetadata', 'embedCover', 'embedLyrics', 'organize', 'autoUpdate', 'lastEngineUpdate'];
  const current = getPrefs();
  for (const key of Object.keys(patch || {})) {
    if (!allowed.includes(key)) continue;
    if (key === 'theme' && !['dark', 'light', 'amoled'].includes(patch[key])) continue;
    current[key] = patch[key];
  }
  writeJson(prefsPath(), current);
  return current;
}

function bundledToolsDir() {
  return IS_DEV ? path.join(__dirname, 'tools') : path.join(process.resourcesPath, 'tools');
}

function userToolsDir() { return userDataPath('tools'); }

function ytdlpPath() {
  const updated = path.join(userToolsDir(), 'yt-dlp.exe');
  if (fs.existsSync(updated)) return updated;
  return path.join(bundledToolsDir(), 'yt-dlp.exe');
}

function ffmpegPath() {
  return path.join(bundledToolsDir(), 'ffmpeg.exe');
}

function ffmpegDir() { return path.dirname(ffmpegPath()); }

async function prepareTools() {
  const bundledYtdlp = path.join(bundledToolsDir(), 'yt-dlp.exe');
  if (!fs.existsSync(bundledYtdlp)) {
    throw new Error(`No se encontró yt-dlp.exe en ${bundledToolsDir()}`);
  }
  if (!fs.existsSync(ffmpegPath())) {
    throw new Error(`No se encontró ffmpeg.exe en ${bundledToolsDir()}`);
  }
  await fsp.mkdir(userToolsDir(), { recursive: true });
  const userYtdlp = path.join(userToolsDir(), 'yt-dlp.exe');
  if (!fs.existsSync(userYtdlp)) {
    await fsp.copyFile(bundledYtdlp, userYtdlp);
  }
}

function validateHttpUrl(raw) {
  let value = String(raw || '').trim();
  if (!value) throw new Error('Pega una URL de vídeo o audio.');
  if (!/^https?:\/\//i.test(value)) value = `https://${value}`;
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error('La URL no es válida.');
  }
  if (!['http:', 'https:'].includes(url.protocol)) {
    throw new Error('Solo se admiten direcciones HTTP o HTTPS.');
  }
  return url.toString();
}

function runCapture(exe, args, options = {}) {
  const timeout = options.timeout || 90000;
  const maxBuffer = options.maxBuffer || 16 * 1024 * 1024;
  return new Promise((resolve, reject) => {
    execFile(exe, args, {
      windowsHide: true,
      timeout,
      maxBuffer,
      encoding: 'utf8'
    }, (error, stdout, stderr) => {
      if (error) {
        error.stdout = stdout;
        error.stderr = stderr;
        return reject(error);
      }
      resolve({ stdout, stderr });
    });
  });
}

function spawnCapture(exe, args, options = {}) {
  const timeout = options.timeout || 120000;
  return new Promise((resolve, reject) => {
    const child = spawn(exe, args, { windowsHide: true });
    let stdout = '';
    let stderr = '';
    let settled = false;
    const timer = setTimeout(() => {
      if (settled) return;
      settled = true;
      try { child.kill(); } catch { /* noop */ }
      reject(new Error('La operación tardó demasiado.'));
    }, timeout);
    child.stdout.on('data', (d) => { stdout += d.toString(); });
    child.stderr.on('data', (d) => { stderr += d.toString(); });
    child.on('error', (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      reject(error);
    });
    child.on('close', (code) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      if (code === 0) resolve({ stdout, stderr });
      else {
        const error = new Error((stderr || stdout || `Código ${code}`).trim());
        error.code = code;
        error.stdout = stdout;
        error.stderr = stderr;
        reject(error);
      }
    });
  });
}

function safeFileName(name, fallback = 'descarga') {
  let base = String(name || '')
    .replace(/[\u0000-\u001f<>:"/\\|?*]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/[. ]+$/g, '');
  if (!base) base = fallback;
  if (/^(con|prn|aux|nul|com[1-9]|lpt[1-9])$/i.test(base)) base = `_${base}`;
  return base.slice(0, 150);
}

function uniquePath(folder, base, ext) {
  let candidate = path.join(folder, `${base}${ext}`);
  if (!fs.existsSync(candidate)) return candidate;
  for (let i = 1; i < 10000; i += 1) {
    candidate = path.join(folder, `${base} (${i})${ext}`);
    if (!fs.existsSync(candidate)) return candidate;
  }
  return path.join(folder, `${base}-${Date.now()}${ext}`);
}

function normalizeText(value) {
  return String(value || '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\b(official|videoclip|video|audio|lyric|lyrics|visualizer|hd|4k|remaster|remastered)\b/g, ' ')
    .replace(/\b(feat|ft|featuring)\b.*$/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function cleanTitle(value) {
  return String(value || '')
    .replace(/\s*[\[(](official\s*(music\s*)?(video|audio|visualizer)|videoclip|lyric\s*video|audio\s*oficial)[^\])]*[\])]/gi, '')
    .replace(/\s*[\[(](hd|4k|remaster(ed)?|remasterizado)[^\])]*[\])]/gi, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }

function send(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload);
  }
}

function historyList() {
  const data = readJson(historyPath(), []);
  return Array.isArray(data) ? data : [];
}

function addHistory(entry) {
  const data = historyList();
  data.unshift(entry);
  writeJson(historyPath(), data.slice(0, 250));
}

/* ------------------------------------------------------------------ */
/* Motor yt-dlp                                                         */
/* ------------------------------------------------------------------ */

async function getEngineVersion() {
  try {
    const { stdout } = await runCapture(ytdlpPath(), ['--version'], { timeout: 30000 });
    const version = stdout.trim().split(/\r?\n/).pop() || '';
    engineInfo = { version, path: ytdlpPath(), error: '' };
    return engineInfo;
  } catch (error) {
    engineInfo = { version: engineInfo.version || '', path: ytdlpPath(), error: error.message };
    return engineInfo;
  }
}

async function updateEngine() {
  if (engineUpdateRunning) throw new Error('La actualización ya está en curso.');
  engineUpdateRunning = true;
  try {
    await prepareTools();
    const before = (await getEngineVersion()).version;
    let stdout = '';
    let stderr = '';
    try {
      const out = await runCapture(ytdlpPath(), ['-U', '--no-colors'], {
        timeout: 180000,
        maxBuffer: 8 * 1024 * 1024
      });
      stdout = out.stdout;
      stderr = out.stderr;
    } catch (error) {
      stdout = error.stdout || '';
      stderr = error.stderr || error.message;
      if (!/up-to-date|already/i.test(`${stdout}\n${stderr}`)) {
        throw new Error(stderr.trim() || 'No se pudo actualizar yt-dlp.');
      }
    }
    const after = (await getEngineVersion()).version;
    savePrefs({ lastEngineUpdate: new Date().toISOString() });
    return {
      ok: true,
      before,
      after,
      updated: before !== after,
      message: (stdout || stderr || '').trim().slice(0, 4000)
    };
  } finally {
    engineUpdateRunning = false;
  }
}

async function autoUpdateEngine() {
  const p = getPrefs();
  if (!p.autoUpdate) return null;
  try {
    const result = await updateEngine();
    send('engine:updated', result);
    return result;
  } catch (error) {
    send('engine:updated', { ok: false, message: error.message });
    return null;
  }
}

/* ------------------------------------------------------------------ */
/* Análisis de enlaces                                                 */
/* ------------------------------------------------------------------ */

function buildAnalysis(info, originalUrl) {
  const data = info && Array.isArray(info.entries) && info.entries.length ? info.entries[0] : info;
  if (!data || !data.id) throw new Error('No se pudo leer la información del enlace.');

  const duration = Number(data.duration) || 0;
  const title = data.track || data.title || 'Sin título';
  const artist = data.artist || data.creator || data.channel || data.uploader || '';
  const thumbnail = data.thumbnail
    || (Array.isArray(data.thumbnails) && data.thumbnails.length
      ? [...data.thumbnails].sort((a, b) => (b.width || 0) - (a.width || 0))[0].url
      : '');

  const videoFormats = (Array.isArray(data.formats) ? data.formats : [])
    .filter((f) => f && f.vcodec && f.vcodec !== 'none' && Number(f.height) > 0)
    .filter((f) => !/storyboard/i.test(String(f.format_note || f.format || '')));

  const byHeight = new Map();
  for (const f of videoFormats) {
    const height = Number(f.height);
    const current = byHeight.get(height);
    const score = (Number(f.tbr) || 0) + (Number(f.fps) >= 50 ? 0.1 : 0);
    if (!current || score > current.score) {
      byHeight.set(height, { format: f, score });
    }
  }
  const heights = [...byHeight.keys()].sort((a, b) => b - a);
  const videoQualities = [];
  if (heights.length) {
    videoQualities.push({ id: 'best', kind: 'video', height: 0, label: 'Mejor disponible', note: `${heights[0]}p o superior`, ext: 'mp4' });
    for (const height of heights) {
      const f = byHeight.get(height).format;
      const fps = Number(f.fps) >= 50 ? ` · ${Math.round(Number(f.fps))} fps` : '';
      const size = Number(f.filesize || f.filesize_approx) || 0;
      videoQualities.push({
        id: `v-${height}`,
        kind: 'video',
        height,
        label: `${height}p${fps}`,
        note: [f.ext ? String(f.ext).toUpperCase() : 'MP4', size ? `${(size / 1048576).toFixed(0)} MB aprox.` : ''].filter(Boolean).join(' · '),
        ext: 'mp4'
      });
    }
  }

  const audioFormats = (Array.isArray(data.formats) ? data.formats : [])
    .filter((f) => f && f.acodec && f.acodec !== 'none' && (!f.vcodec || f.vcodec === 'none'))
    .filter((f) => Number(f.abr) > 0 || /audio/i.test(String(f.format_note || '')));
  const audioMap = new Map();
  for (const f of audioFormats) {
    const codec = String(f.acodec || '').split('.')[0].toLowerCase();
    const abr = Number(f.abr) || 0;
    const ext = String(f.ext || 'm4a').toLowerCase();
    const key = `${codec}|${Math.round(abr / 16)}|${ext}`;
    if (!audioMap.has(key)) {
      audioMap.set(key, {
        id: `a-${f.format_id}`,
        kind: 'audio',
        formatId: f.format_id,
        codec,
        abr,
        ext,
        note: [f.format_note || '', f.audio_channels ? `${f.audio_channels} canales` : ''].filter(Boolean).join(' · ') || String(f.ext || 'audio').toUpperCase()
      });
    }
  }
  const audioQualities = [...audioMap.values()].sort((a, b) => b.abr - a.abr);
  audioQualities.push({
    id: 'mp3',
    kind: 'audio',
    formatId: 'mp3',
    codec: 'mp3',
    abr: 192,
    ext: 'mp3',
    label: 'Convertir a MP3',
    note: 'MP3 · 192 kbps · compatible con todo'
  });

  return {
    id: String(data.id),
    url: data.webpage_url || originalUrl,
    originalUrl: data.original_url || originalUrl,
    title,
    displayTitle: data.title || title,
    artist,
    track: data.track || '',
    album: data.album || '',
    releaseYear: data.release_year || '',
    uploader: data.uploader || data.channel || '',
    channel: data.channel || '',
    duration,
    durationLabel: formatDuration(duration),
    thumbnail,
    extractor: data.extractor_key || data.extractor || '',
    webpageUrl: data.webpage_url || originalUrl,
    description: String(data.description || '').slice(0, 900),
    viewCount: Number(data.view_count) || 0,
    videoQualities,
    audioQualities,
    hasVideo: heights.length > 0
  };
}

function formatDuration(seconds) {
  const total = Math.max(0, Math.round(Number(seconds) || 0));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
}

async function analyzeUrl(raw) {
  const url = validateHttpUrl(raw);
  await prepareTools();
  const { stdout } = await runCapture(
    ytdlpPath(),
    ['-J', '--no-playlist', '--no-warnings', '--no-colors', '--skip-download', '--extractor-retries', '3', url],
    { timeout: 120000, maxBuffer: 96 * 1024 * 1024 }
  );
  let info;
  try {
    info = JSON.parse(stdout);
  } catch {
    throw new Error('El motor devolvió una respuesta que no se pudo leer.');
  }
  return buildAnalysis(info, url);
}

/* ------------------------------------------------------------------ */
/* Descargas                                                            */
/* ------------------------------------------------------------------ */

function parseProgressLine(line) {
  if (!line.startsWith('download:')) return null;
  const rest = line.slice('download:'.length);
  const parts = rest.split('|');
  if (parts.length < 4) return null;
  const percent = parseFloat(String(parts[0]).replace('%', '').trim());
  const speed = String(parts[1] || '').trim();
  const eta = String(parts[2] || '').trim();
  const downloaded = Number(parts[3]) || 0;
  const total = Number(parts[4]) || 0;
  if (!Number.isFinite(percent) && !downloaded) return null;
  return {
    percent: Number.isFinite(percent) ? clamp(percent, 0, 100) : 0,
    speed,
    eta,
    downloaded,
    total
  };
}

function buildYtdlpArgs(job, tempDir, outputListFile) {
  const args = [
    '--no-playlist',
    '--newline',
    '--no-colors',
    '--no-warnings',
    '--retries', '10',
    '--fragment-retries', '10',
    '--concurrent-fragments', '4',
    '--windows-filenames',
    '--ffmpeg-location', ffmpegDir(),
    '-P', tempDir,
    '-o', '%(title).160B [%(id)s].%(ext)s',
    '--progress-template', 'download:%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s|%(progress.downloaded_bytes)s|%(progress.total_bytes_estimate)s',
    '--print-to-file', 'after_move:filepath', outputListFile
  ];

  if (job.kind === 'audio') {
    if (job.formatId === 'mp3' || !job.formatId) {
      args.push('-f', 'ba/b', '-x', '--audio-format', 'mp3', '--audio-quality', '192K');
    } else {
      args.push('-f', String(job.formatId));
    }
  } else {
    const height = Number(job.height) || 0;
    if (height > 0) {
      args.push('-f', `bv*[height<=${height}]+ba/b[height<=${height}]/bv*+ba/b`);
      args.push('-S', `res:${height},vcodec:h264,acodec:aac,ext:mp4:m4a`);
    } else {
      args.push('-f', 'bv*+ba/b');
      args.push('-S', 'res,vcodec:h264,acodec:aac,ext:mp4:m4a');
    }
    args.push('--merge-output-format', 'mp4');
  }
  args.push('--', job.url);
  return args;
}

function sanitizeMetadata(input) {
  const md = (input && typeof input === 'object') ? input : {};
  const stringField = (value, max = 500) => String(value || '').trim().slice(0, max);
  const numberField = (value) => {
    const n = Number(value);
    return Number.isFinite(n) && n > 0 ? Math.round(n) : 0;
  };
  return {
    provider: stringField(md.provider, 80),
    confidence: numberField(md.confidence),
    title: stringField(md.title, 300),
    artist: stringField(md.artist, 300),
    album: stringField(md.album, 300),
    albumArtist: stringField(md.albumArtist, 300),
    date: stringField(md.date || md.year, 40),
    genre: stringField(md.genre, 120),
    composer: stringField(md.composer, 300),
    producer: stringField(md.producer, 300),
    label: stringField(md.label, 300),
    trackNumber: numberField(md.trackNumber),
    discNumber: numberField(md.discNumber),
    isrc: stringField(md.isrc, 40),
    copyright: stringField(md.copyright, 300),
    credits: stringField(md.credits, 2000),
    lyrics: stringField(md.lyrics, 300000),
    syncedLyrics: stringField(md.syncedLyrics, 400000),
    artworkUrl: stringField(md.artworkUrl, 2000),
    durationSec: numberField(md.durationSec)
  };
}

async function fetchBuffer(url, timeout = 20000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      redirect: 'follow',
      headers: { 'User-Agent': 'ParDL/0.4.0 (+https://github.com/andrespar2/ParDL)' }
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return Buffer.from(await response.arrayBuffer());
  } finally {
    clearTimeout(timer);
  }
}

async function downloadCover(url, dir) {
  if (!url || !/^https?:\/\//i.test(url)) return null;
  try {
    const buffer = await fetchBuffer(url, 25000);
    if (!buffer.length || buffer.length > 12 * 1024 * 1024) return null;
    const type = buffer[0] === 0x89 && buffer[1] === 0x50 ? 'png'
      : buffer[0] === 0xff && buffer[1] === 0xd8 ? 'jpg'
        : buffer.slice(0, 4).toString() === 'RIFF' ? 'webp' : 'jpg';
    const file = path.join(dir, `cover.${type}`);
    await fsp.writeFile(file, buffer);
    if (type === 'webp') {
      const jpg = path.join(dir, 'cover.jpg');
      try {
        await spawnCapture(ffmpegPath(), ['-y', '-hide_banner', '-loglevel', 'error', '-i', file, '-c:v', 'mjpeg', jpg], { timeout: 30000 });
        await fsp.unlink(file).catch(() => {});
        return jpg;
      } catch {
        return file;
      }
    }
    return file;
  } catch {
    return null;
  }
}

function id3Tags(md, cover, options) {
  const useTags = options.embedMetadata;
  const useCover = options.embedCover && cover;
  const plainLyrics = md.lyrics || String(md.syncedLyrics || '').replace(/^\[[^\]]+\]\s*/gm, '').trim();
  const tags = {};
  const userText = [];
  if (useTags) {
    if (md.title) tags.title = md.title;
    if (md.artist) tags.artist = md.artist;
    if (md.album) tags.album = md.album;
    if (md.albumArtist) tags.performerInfo = md.albumArtist;
    if (md.date) {
      tags.date = md.date;
      const year = String(md.date).match(/^\d{4}/);
      if (year) tags.year = year[0];
    }
    if (md.genre) tags.genre = md.genre;
    if (md.trackNumber) tags.trackNumber = String(md.trackNumber);
    if (md.discNumber) tags.partOfSet = String(md.discNumber);
    if (md.composer) tags.composer = md.composer;
    if (md.label) tags.publisher = md.label;
    if (md.copyright) tags.copyright = md.copyright;
    if (md.isrc) tags.ISRC = md.isrc;
    if (md.credits) tags.comment = { language: 'eng', text: md.credits };
    if (md.producer) userText.push({ description: 'PRODUCER', text: md.producer });
    if (md.credits) userText.push({ description: 'CREDITS', text: md.credits });
  }
  if (useCover) {
    const ext = path.extname(cover).toLowerCase();
    tags.image = {
      mime: ext === '.png' ? 'image/png' : 'image/jpeg',
      type: { id: 3, name: 'front cover' },
      description: 'Portada',
      imageBuffer: fs.readFileSync(cover)
    };
  }
  if (options.embedLyrics && plainLyrics) {
    tags.unsynchronisedLyrics = { language: 'eng', text: plainLyrics };
  }
  if (userText.length) tags.userDefinedText = userText;
  return tags;
}

async function applyId3Metadata(file, md, cover, options) {
  const tags = id3Tags(md, cover, options);
  if (!Object.keys(tags).length) return { file, warning: '', tags: null };
  const useWrite = options.embedMetadata;
  try {
    const result = useWrite ? NodeID3.write(tags, file) : NodeID3.update(tags, file);
    if (result && result instanceof Error) throw result;
    if (result === false) throw new Error('node-id3 no pudo escribir las etiquetas.');
  } catch (error) {
    return { file, warning: `No se pudieron escribir las etiquetas ID3: ${error.message}`, tags: null };
  }
  return { file, warning: '', tags: null, id3: true };
}

async function verifyTags(file) {
  try {
    const parsed = await parseFile(file, { duration: false, skipCovers: false });
    const common = parsed.common || {};
    const nativeGroups = Object.values(parsed.native || {}).filter(Array.isArray);
    const nativeTags = nativeGroups.flat();
    const lyricsTag = nativeTags.find((tag) => tag && (
      tag.id === 'USLT' || tag.id === 'SYLT' || tag.id === '©lyr'
      || /^lyrics$/i.test(String(tag.id))
      || /^TXXX:(USLT|LYRICS|SYNCEDLYRICS|LYRICS-ENG)$/i.test(String(tag.id))
    ));
    let lyrics = '';
    if (Array.isArray(common.lyrics)) {
      lyrics = common.lyrics.map((item) => (typeof item === 'string' ? item : item.text || '')).filter(Boolean).join('\n');
    } else if (typeof common.lyrics === 'string') {
      lyrics = common.lyrics;
    }
    if (!lyrics && lyricsTag) {
      lyrics = typeof lyricsTag.value === 'string' ? lyricsTag.value : (lyricsTag.value?.text || '');
    }
    return {
      title: common.title || '',
      artist: common.artist || '',
      album: common.album || '',
      cover: Array.isArray(common.picture) && common.picture.length > 0,
      lyrics,
      genre: Array.isArray(common.genre) ? common.genre.join(', ') : (common.genre || ''),
      year: common.year || '',
      isrc: Array.isArray(common.isrc) ? common.isrc.join(', ') : (common.isrc || ''),
      label: Array.isArray(common.label) ? common.label.join(', ') : (common.label || '')
    };
  } catch {
    return null;
  }
}

async function applyMetadata(file, metadata, options) {
  const md = sanitizeMetadata(metadata);
  const ext = path.extname(file).toLowerCase();
  const isVideo = options.kind === 'video' || ['.mp4', '.webm', '.mkv', '.mov'].includes(ext);
  const useCover = !isVideo && options.embedCover && md.artworkUrl && ext !== '.wav';
  const useLyrics = options.embedLyrics && (md.lyrics || md.syncedLyrics);
  const useTags = options.embedMetadata;
  if (!useTags && !useCover && !useLyrics) return { file, warning: '', tags: null };

  const dir = path.dirname(file);
  const cover = useCover ? await downloadCover(md.artworkUrl, dir) : null;

  if (ext === '.mp3') {
    const result = await applyId3Metadata(file, md, cover, options);
    return { file, warning: result.warning || '', tags: await verifyTags(file) };
  }

  const base = path.basename(file, ext);
  const output = path.join(dir, `${base}.pardl${ext}`);

  const args = ['-y', '-hide_banner', '-loglevel', 'error', '-i', file];
  if (cover) args.push('-i', cover);
  if (isVideo) {
    args.push('-map', '0', '-map_metadata', '0', '-c', 'copy');
  } else {
    args.push('-map', '0:a', '-map_metadata', '0', '-c:a', 'copy');
  }
  if (cover) {
    args.push('-map', '1:v', '-c:v', 'mjpeg', '-disposition:v', 'attached_pic');
    args.push('-metadata:s:v', 'title=Portada', '-metadata:s:v', 'comment=Cover (front)');
  }
  const tags = {};
  if (useTags) {
    Object.assign(tags, {
      title: md.title,
      artist: md.artist,
      album: md.album,
      album_artist: md.albumArtist,
      date: md.date,
      genre: md.genre,
      track: md.trackNumber || '',
      disc: md.discNumber || '',
      isrc: md.isrc,
      composer: md.composer,
      comment: md.credits,
      copyright: md.copyright,
      label: md.label,
      producer: md.producer
    });
  }
  if (useLyrics) {
    tags.lyrics = md.lyrics;
    tags.syncedlyrics = md.syncedLyrics;
  }
  const hasTagValues = Object.values(tags).some((value) => value !== '' && value !== 0 && value !== null && value !== undefined);
  if (!cover && !hasTagValues) {
    return { file, warning: '', tags: null };
  }
  for (const [key, value] of Object.entries(tags)) {
    if (value !== '' && value !== 0 && value !== null && value !== undefined) {
      args.push('-metadata', `${key}=${String(value)}`);
    }
  }
  if (ext === '.mp3') args.push('-id3v2_version', '3');
  if (ext === '.m4a' || ext === '.mp4') args.push('-movflags', '+faststart');
  args.push(output);

  let warning = '';
  try {
    await spawnCapture(ffmpegPath(), args, { timeout: 180000 });
    await fsp.unlink(file).catch(() => {});
    await fsp.rename(output, file);
  } catch (error) {
    await fsp.unlink(output).catch(() => {});
    warning = `No se pudieron escribir todas las etiquetas: ${error.message.split(/\r?\n/).pop()}`;
    if (cover) {
      // Reintenta sin portada: algunos contenedores no admiten imagen adjunta.
      const noCoverArgs = ['-y', '-hide_banner', '-loglevel', 'error', '-i', file, '-map', '0:a', '-map_metadata', '0', '-c:a', 'copy'];
      for (const [key, value] of Object.entries(tags)) {
        if (value !== '' && value !== 0 && value !== null && value !== undefined) {
          noCoverArgs.push('-metadata', `${key}=${String(value)}`);
        }
      }
      if (ext === '.mp3') noCoverArgs.push('-id3v2_version', '3');
      noCoverArgs.push(output);
      try {
        await spawnCapture(ffmpegPath(), noCoverArgs, { timeout: 180000 });
        await fsp.unlink(file).catch(() => {});
        await fsp.rename(output, file);
      } catch (retryError) {
        await fsp.unlink(output).catch(() => {});
        warning = `No se pudieron escribir las etiquetas: ${retryError.message.split(/\r?\n/).pop()}`;
      }
    }
  }

  return { file, warning, tags: await verifyTags(file) };
}

function findDownloadedFile(dir, outputListFile) {
  try {
    const list = fs.readFileSync(outputListFile, 'utf8').split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
    for (let i = list.length - 1; i >= 0; i -= 1) {
      if (list[i] && fs.existsSync(list[i])) return list[i];
    }
  } catch { /* noop */ }
  const files = fs.readdirSync(dir)
    .filter((name) => !name.endsWith('.part') && !name.endsWith('.ytdl') && !name.startsWith('cover.') && name !== 'output.txt')
    .map((name) => {
      const full = path.join(dir, name);
      let mtime = 0;
      try { mtime = fs.statSync(full).mtimeMs; } catch { /* noop */ }
      return { full, mtime, isFile: (() => { try { return fs.statSync(full).isFile(); } catch { return false; } })() };
    })
    .filter((entry) => entry.isFile)
    .sort((a, b) => b.mtime - a.mtime);
  return files.length ? files[0].full : null;
}

function destinationFor(file, md, prefsValue) {
  const ext = path.extname(file);
  const baseOriginal = path.basename(file, ext);
  let base = baseOriginal;
  if (md && md.title) {
    base = md.artist ? `${md.artist} - ${md.title}` : md.title;
  }
  base = safeFileName(base, 'descarga');
  let folder = prefsValue.folder || DEFAULT_FOLDER();
  if (prefsValue.organize && md && md.artist) {
    folder = path.join(folder, safeFileName(md.artist, 'Artista'));
    if (md.album) folder = path.join(folder, safeFileName(md.album, 'Álbum'));
  }
  fs.mkdirSync(folder, { recursive: true });
  return uniquePath(folder, base, ext);
}

async function runDownload(job, ctx) {
  const { id, tempDir, outputListFile } = ctx;
  let settled = false;
  const finish = (payload, channel) => {
    if (settled) return;
    settled = true;
    activeDownload = null;
    try { mainWindow?.setProgressBar(-1); } catch { /* noop */ }
    send(channel, { jobId: id, ...payload });
  };

  const args = buildYtdlpArgs(job, tempDir, outputListFile);
  const child = spawn(ytdlpPath(), args, { windowsHide: true });
  ctx.child = child;
  let buffer = '';
  let stderr = '';
  let lastProgress = null;

  child.stdout.setEncoding('utf8');
  child.stderr.setEncoding('utf8');
  child.stdout.on('data', (chunk) => {
    buffer += chunk;
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() || '';
    for (const line of lines) {
      const progress = parseProgressLine(line);
      if (progress) {
        lastProgress = progress;
        send('download:progress', { jobId: id, ...progress });
        try { mainWindow?.setProgressBar(progress.percent / 100); } catch { /* noop */ }
      }
    }
  });
  child.stderr.on('data', (chunk) => {
    stderr += chunk.toString();
    if (stderr.length > 30000) stderr = stderr.slice(-30000);
  });
  child.on('error', (error) => {
    if (ctx.canceled) return;
    finish({ message: `No se pudo iniciar yt-dlp: ${error.message}` }, 'download:error');
  });
  child.on('close', async (code) => {
    if (ctx.canceled) {
      finish({ message: 'Descarga cancelada.', canceled: true }, 'download:error');
      return;
    }
    if (code !== 0) {
      const detail = (stderr || '').split(/\r?\n/).filter(Boolean).slice(-6).join('\n');
      finish({ message: 'La descarga falló.', detail }, 'download:error');
      return;
    }
    try {
      const found = findDownloadedFile(tempDir, outputListFile);
      if (!found) throw new Error('yt-dlp terminó, pero no se encontró el archivo descargado.');
      const md = sanitizeMetadata(job.metadata);
      const options = {
        kind: job.kind,
        embedMetadata: job.embedMetadata !== false,
        embedCover: job.embedCover !== false,
        embedLyrics: job.embedLyrics !== false
      };
      const tagged = await applyMetadata(found, md, options);
      const finalPath = destinationFor(tagged.file, md, getPrefs());
      await fsp.copyFile(tagged.file, finalPath);
      const fileName = path.basename(finalPath);
      const title = md.title || job.title || path.basename(found);
      const artist = md.artist || job.artist || '';
      const entry = {
        id,
        kind: job.kind,
        title,
        artist,
        provider: md.provider || '',
        quality: job.qualityLabel || (job.kind === 'audio' ? 'Audio' : 'Vídeo'),
        fileName,
        filePath: finalPath,
        url: job.url,
        thumbnail: job.thumbnail || '',
        tags: tagged.tags,
        warning: tagged.warning || '',
        timestamp: Date.now()
      };
      addHistory(entry);
      if (Notification.isSupported()) {
        new Notification({
          title: 'ParDL · descarga completada',
          body: `${title}${artist ? ` — ${artist}` : ''}`,
          silent: false
        }).show();
      }
      finish({
        filePath: finalPath,
        fileName,
        title,
        artist,
        kind: job.kind,
        quality: job.qualityLabel || '',
        tags: tagged.tags,
        warning: tagged.warning || '',
        provider: md.provider || '',
        historyEntry: entry
      }, 'download:complete');
    } catch (error) {
      finish({ message: error.message || 'No se pudo finalizar la descarga.' }, 'download:error');
    } finally {
      fsp.rm(tempDir, { recursive: true, force: true }).catch(() => {});
    }
  });
}

async function startDownload(job) {
  if (activeDownload) throw new Error('Ya hay una descarga en curso. Espera o cancélala.');
  await prepareTools();
  const url = validateHttpUrl(job.url);
  const kind = job.kind === 'audio' ? 'audio' : 'video';
  const id = crypto.randomUUID();
  const tempDir = path.join(app.getPath('temp'), 'pardl', id);
  await fsp.mkdir(tempDir, { recursive: true });
  const outputListFile = path.join(tempDir, 'output.txt');
  const cleanJob = {
    id,
    url,
    kind,
    formatId: String(job.formatId || ''),
    height: Number(job.height) || 0,
    qualityLabel: String(job.qualityLabel || ''),
    title: String(job.title || ''),
    artist: String(job.artist || ''),
    thumbnail: String(job.thumbnail || ''),
    metadata: job.metadata || null,
    embedMetadata: job.embedMetadata !== false,
    embedCover: job.embedCover !== false,
    embedLyrics: job.embedLyrics !== false
  };
  const ctx = { id, tempDir, outputListFile, child: null, canceled: false };
  activeDownload = ctx;
  runDownload(cleanJob, ctx).catch((error) => {
    activeDownload = null;
    send('download:error', { jobId: id, message: error.message });
  });
  return { id, tempDir };
}

function cancelDownload() {
  if (!activeDownload) return { ok: false, message: 'No hay una descarga activa.' };
  const ctx = activeDownload;
  ctx.canceled = true;
  if (ctx.child && ctx.child.pid) {
    try {
      spawn('taskkill', ['/PID', String(ctx.child.pid), '/T', '/F'], { windowsHide: true });
    } catch {
      try { ctx.child.kill(); } catch { /* noop */ }
    }
  }
  return { ok: true };
}

/* ------------------------------------------------------------------ */
/* Metadatos y letras                                                  */
/* ------------------------------------------------------------------ */

async function getJson(url, headers = {}, timeout = 20000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        'User-Agent': 'ParDL/0.4.0 (+https://github.com/andrespar2/ParDL)',
        'Accept': 'application/json',
        ...headers
      }
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

function scoreCandidate(candidate, query) {
  const qt = normalizeText(cleanTitle(query.title));
  const ct = normalizeText(cleanTitle(candidate.title));
  const qa = normalizeText(query.artist);
  const ca = normalizeText(candidate.artist);
  let score = 0;
  if (qt && ct) {
    if (qt === ct) score += 52;
    else if (ct.includes(qt) || qt.includes(ct)) score += 38;
    else {
      const qWords = new Set(qt.split(' ').filter((w) => w.length > 2));
      const cWords = new Set(ct.split(' ').filter((w) => w.length > 2));
      let hits = 0;
      for (const w of qWords) if (cWords.has(w)) hits += 1;
      score += qWords.size ? (hits / Math.max(qWords.size, cWords.size)) * 34 : 0;
    }
  }
  if (qa && ca) {
    if (qa === ca) score += 34;
    else if (ca.includes(qa) || qa.includes(ca)) score += 25;
    else {
      const qWords = new Set(qa.split(' '));
      const cWords = new Set(ca.split(' '));
      let hits = 0;
      for (const w of qWords) if (w.length > 2 && cWords.has(w)) hits += 1;
      score += qWords.size ? (hits / qWords.size) * 16 : 0;
    }
  }
  if (query.duration && candidate.duration) {
    const diff = Math.abs(Number(query.duration) - Number(candidate.duration));
    if (diff <= 2) score += 12;
    else if (diff <= 5) score += 6;
    else if (diff > 30) score -= 12;
  }
  if (query.isrc && candidate.isrc && query.isrc.toUpperCase() === candidate.isrc.toUpperCase()) score += 40;
  return clamp(Math.round(score), 0, 100);
}

function pickBest(candidates, query) {
  if (!Array.isArray(candidates) || !candidates.length) throw new Error('No se encontraron coincidencias.');
  const ranked = candidates
    .map((candidate) => ({ ...candidate, confidence: scoreCandidate(candidate, query) }))
    .sort((a, b) => b.confidence - a.confidence);
  const best = ranked[0];
  if (!best || best.confidence < 35) {
    throw new Error('La coincidencia encontrada no es suficientemente fiable. Prueba con otro proveedor.');
  }
  return best;
}

function providerQuery(query) {
  return {
    title: cleanTitle(query.title || query.track || ''),
    artist: String(query.artist || '').trim(),
    album: String(query.album || '').trim(),
    duration: Number(query.duration) || 0,
    isrc: String(query.isrc || '').trim()
  };
}

async function findItunes(query) {
  const term = [query.title, query.artist].filter(Boolean).join(' ');
  const url = `https://itunes.apple.com/search?term=${encodeURIComponent(term)}&entity=song&limit=20&country=ES`;
  const data = await getJson(url);
  const candidates = (data.results || []).map((r) => ({
    title: r.trackName || '',
    artist: r.artistName || '',
    album: r.collectionName || '',
    albumArtist: r.artistName || '',
    date: String(r.releaseDate || '').slice(0, 10),
    genre: r.primaryGenreName || '',
    trackNumber: r.trackNumber || 0,
    discNumber: r.discNumber || 0,
    isrc: r.isrc || '',
    duration: Number(r.trackTimeMillis) ? Number(r.trackTimeMillis) / 1000 : 0,
    artworkUrl: r.artworkUrl100 ? String(r.artworkUrl100).replace(/100x100bb/, '1200x1200bb') : '',
    copyright: '',
    label: '',
    provider: 'Apple Music / iTunes',
    providerId: String(r.trackId || ''),
    raw: r
  }));
  const best = pickBest(candidates, query);
  return {
    provider: 'Apple Music / iTunes',
    providerId: best.providerId,
    confidence: best.confidence,
    metadata: {
      title: best.title,
      artist: best.artist,
      album: best.album,
      albumArtist: best.albumArtist,
      date: best.date,
      genre: best.genre,
      trackNumber: best.trackNumber,
      discNumber: best.discNumber,
      isrc: best.isrc,
      durationSec: best.duration,
      artworkUrl: best.artworkUrl,
      provider: best.provider
    }
  };
}

async function findDeezer(query) {
  const term = [query.title, query.artist].filter(Boolean).join(' ');
  const data = await getJson(`https://api.deezer.com/search?q=${encodeURIComponent(term)}&limit=20`);
  const candidates = (data.data || []).map((r) => ({
    title: r.title || '',
    artist: r.artist?.name || '',
    album: r.album?.title || '',
    albumArtist: r.artist?.name || '',
    date: '',
    genre: '',
    trackNumber: 0,
    discNumber: 0,
    isrc: '',
    duration: Number(r.duration) || 0,
    artworkUrl: r.album?.cover_xl || r.album?.cover_big || '',
    copyright: '',
    label: '',
    provider: 'Deezer',
    providerId: String(r.id || ''),
    albumId: String(r.album?.id || ''),
    raw: r
  }));
  const best = pickBest(candidates, query);
  let detail = null;
  let album = null;
  try { detail = await getJson(`https://api.deezer.com/track/${best.providerId}`); } catch { /* noop */ }
  try {
    if (best.albumId) album = await getJson(`https://api.deezer.com/album/${best.albumId}`);
  } catch { /* noop */ }
  const md = {
    title: detail?.title || best.title,
    artist: detail?.artist?.name || best.artist,
    album: detail?.album?.title || best.album,
    albumArtist: detail?.artist?.name || best.albumArtist,
    date: album?.release_date || '',
    genre: album?.genres?.data?.[0]?.name || '',
    trackNumber: Number(detail?.track_position) || best.trackNumber,
    discNumber: Number(detail?.disk_number) || best.discNumber,
    isrc: detail?.isrc || '',
    durationSec: Number(detail?.duration) || best.duration,
    artworkUrl: detail?.album?.cover_xl || album?.cover_xl || best.artworkUrl,
    label: album?.label || '',
    copyright: '',
    provider: 'Deezer'
  };
  return { provider: 'Deezer', providerId: best.providerId, confidence: best.confidence, metadata: md };
}

async function findMusicBrainz(query) {
  const clauses = [];
  if (query.title) clauses.push(`recording:"${query.title.replace(/"/g, '')}"`);
  if (query.artist) clauses.push(`artist:"${query.artist.replace(/"/g, '')}"`);
  if (query.isrc) clauses.push(`isrc:${query.isrc}`);
  const lucene = clauses.join(' AND ') || query.title;
  const url = `https://musicbrainz.org/ws/2/recording?query=${encodeURIComponent(lucene)}&fmt=json&limit=12&inc=isrcs+artist-credits+releases`;
  const data = await getJson(url, { 'User-Agent': 'ParDL/0.4.0 ( https://github.com/andrespar2/ParDL )' });
  const candidates = (data.recordings || []).map((r) => {
    const release = (r.releases || [])[0] || {};
    const artist = (r['artist-credit'] || []).map((a) => a.name || a.artist?.name || '').join(' & ');
    return {
      title: r.title || '',
      artist,
      album: release.title || '',
      albumArtist: artist,
      date: release.date || '',
      genre: '',
      trackNumber: 0,
      discNumber: 0,
      isrc: r.isrcs?.[0] || '',
      duration: Number(r.length) ? Number(r.length) / 1000 : 0,
      artworkUrl: release.id ? `https://coverartarchive.org/release/${release.id}/front-500` : '',
      copyright: '',
      label: '',
      provider: 'MusicBrainz',
      providerId: r.id || '',
      releaseId: release.id || '',
      raw: r
    };
  });
  const best = pickBest(candidates, query);
  let release = null;
  if (best.releaseId) {
    try {
      release = await getJson(`https://musicbrainz.org/ws/2/release/${best.releaseId}?fmt=json&inc=labels+genres`, { 'User-Agent': 'ParDL/0.4.0 ( https://github.com/andrespar2/ParDL )' });
    } catch { /* noop */ }
  }
  const md = {
    title: best.title,
    artist: best.artist,
    album: release?.title || best.album,
    albumArtist: best.albumArtist,
    date: release?.date || best.date,
    genre: release?.genres?.[0]?.name || best.genre,
    trackNumber: best.trackNumber,
    discNumber: best.discNumber,
    isrc: best.isrc,
    durationSec: best.duration,
    artworkUrl: best.artworkUrl,
    label: release?.labels?.[0]?.name || '',
    copyright: '',
    provider: 'MusicBrainz'
  };
  return { provider: 'MusicBrainz', providerId: best.providerId, confidence: best.confidence, metadata: md };
}

async function findInternetArchive(query) {
  const q = [query.title ? `title:(${query.title})` : '', query.artist ? `creator:(${query.artist})` : '', 'mediatype:(audio)']
    .filter(Boolean).join(' AND ');
  const url = `https://archive.org/advancedsearch.php?q=${encodeURIComponent(q)}&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=date&fl[]=year&rows=8&page=1&output=json`;
  const data = await getJson(url);
  const docs = data?.response?.docs || [];
  const candidates = docs.map((doc) => {
    const creator = Array.isArray(doc.creator) ? doc.creator.join(', ') : (doc.creator || '');
    return {
      title: Array.isArray(doc.title) ? doc.title[0] : (doc.title || ''),
      artist: creator,
      album: '',
      albumArtist: creator,
      date: String(doc.date || doc.year || '').slice(0, 10),
      genre: '',
      trackNumber: 0,
      discNumber: 0,
      isrc: '',
      duration: 0,
      artworkUrl: doc.identifier ? `https://archive.org/services/img/${encodeURIComponent(doc.identifier)}` : '',
      copyright: '',
      label: '',
      provider: 'Internet Archive',
      providerId: doc.identifier || '',
      raw: doc
    };
  });
  const best = pickBest(candidates, query);
  let item = null;
  try { item = await getJson(`https://archive.org/metadata/${encodeURIComponent(best.providerId)}`); } catch { /* noop */ }
  const meta = item?.metadata || {};
  const md = {
    title: Array.isArray(meta.title) ? meta.title[0] : (meta.title || best.title),
    artist: Array.isArray(meta.creator) ? meta.creator[0] : (meta.creator || best.artist),
    album: Array.isArray(meta.album) ? meta.album[0] : (meta.album || ''),
    albumArtist: best.albumArtist,
    date: String(meta.date || best.date || '').slice(0, 10),
    genre: Array.isArray(meta.genre) ? meta.genre[0] : (meta.genre || ''),
    trackNumber: 0,
    discNumber: 0,
    isrc: '',
    durationSec: best.duration,
    artworkUrl: best.artworkUrl,
    label: Array.isArray(meta.label) ? meta.label[0] : (meta.label || ''),
    copyright: meta.licenseurl || '',
    credits: Array.isArray(meta.description) ? meta.description.join(' ') : (meta.description || ''),
    provider: 'Internet Archive'
  };
  return { provider: 'Internet Archive', providerId: best.providerId, confidence: best.confidence, metadata: md };
}

async function findMetadata(query) {
  const q = providerQuery(query);
  if (!q.title) throw new Error('No hay un título para buscar metadatos.');
  const provider = String(query.provider || 'itunes');
  let result;
  if (provider === 'deezer') result = await findDeezer(q);
  else if (provider === 'musicbrainz') result = await findMusicBrainz(q);
  else if (provider === 'archive') result = await findInternetArchive(q);
  else result = await findItunes(q);
  return result;
}

function lrcLibQuery(query) {
  return {
    track_name: cleanTitle(query.title || query.track || ''),
    artist_name: String(query.artist || '').trim(),
    album_name: String(query.album || '').trim(),
    duration: Number(query.duration) || 0
  };
}

async function findLyrics(query) {
  const q = lrcLibQuery(query);
  if (!q.track_name || !q.artist_name) return null;
  const params = new URLSearchParams({ track_name: q.track_name, artist_name: q.artist_name });
  if (q.album_name) params.set('album_name', q.album_name);
  if (q.duration) params.set('duration', String(Math.round(q.duration)));
  try {
    const search = await getJson(`https://lrclib.net/api/search?${params.toString()}`);
    const list = Array.isArray(search) ? search : [];
    if (list.length) {
      const best = list
        .map((item) => ({
          ...item,
          score: (normalizeText(item.trackName) === normalizeText(q.track_name) ? 50 : 20)
            + (normalizeText(item.artistName) === normalizeText(q.artist_name) ? 35 : 0)
            + (q.duration && item.duration && Math.abs(item.duration - q.duration) <= 2 ? 15 : 0)
        }))
        .sort((a, b) => b.score - a.score)[0];
      return {
        provider: 'LRCLIB',
        id: best.id,
        trackName: best.trackName,
        artistName: best.artistName,
        albumName: best.albumName,
        duration: best.duration,
        instrumental: !!best.instrumental,
        plainLyrics: best.plainLyrics || '',
        syncedLyrics: best.syncedLyrics || ''
      };
    }
  } catch { /* intenta get */ }
  try {
    const get = await getJson(`https://lrclib.net/api/get?${params.toString()}`);
    if (get) {
      return {
        provider: 'LRCLIB',
        id: get.id,
        trackName: get.trackName,
        artistName: get.artistName,
        albumName: get.albumName,
        duration: get.duration,
        instrumental: !!get.instrumental,
        plainLyrics: get.plainLyrics || '',
        syncedLyrics: get.syncedLyrics || ''
      };
    }
  } catch { /* sin letras */ }
  return null;
}

const PROVIDERS = [
  { id: 'musicbrainz', name: 'MusicBrainz', kind: 'metadata', description: 'Base de datos abierta y Cover Art Archive. Ideal para álbumes y sellos.', icon: 'MB' },
  { id: 'itunes', name: 'Apple Music / iTunes', kind: 'metadata', description: 'Catálogo amplio con portadas en alta resolución y fecha de lanzamiento.', icon: '♪' },
  { id: 'deezer', name: 'Deezer', kind: 'metadata', description: 'Metadatos comerciales, ISRC y portadas de alta calidad.', icon: 'D' },
  { id: 'archive', name: 'Internet Archive', kind: 'metadata', description: 'Audio libre y grabaciones históricas. Perfecto para dominio público.', icon: 'IA' },
  { id: 'lrclib', name: 'LRCLIB', kind: 'lyrics', description: 'Letras sincronizadas y texto plano, con búsqueda por duración.', icon: 'LRC' }
];

async function testProvider(id) {
  const start = Date.now();
  try {
    if (id === 'musicbrainz') {
      await getJson('https://musicbrainz.org/ws/2/recording?query=recording%3A%22Daft%20Punk%22&fmt=json&limit=1', { 'User-Agent': 'ParDL/0.4.0 ( https://github.com/andrespar2/ParDL )' });
    } else if (id === 'itunes') {
      await getJson('https://itunes.apple.com/search?term=daft%20punk&entity=song&limit=1');
    } else if (id === 'deezer') {
      await getJson('https://api.deezer.com/search?q=daft%20punk&limit=1');
    } else if (id === 'archive') {
      await getJson('https://archive.org/advancedsearch.php?q=mediatype%3A(audio)%20AND%20title%3A(public)&fl[]=identifier&rows=1&output=json');
    } else if (id === 'lrclib') {
      await getJson('https://lrclib.net/api/search?track_name=Get%20Lucky&artist_name=Daft%20Punk');
    } else {
      throw new Error('Proveedor desconocido.');
    }
    return { ok: true, latency: Date.now() - start, message: 'Respondió correctamente.' };
  } catch (error) {
    return { ok: false, latency: Date.now() - start, message: error.message || 'No disponible.' };
  }
}

/* ------------------------------------------------------------------ */
/* Ventana y IPC                                                       */
/* ------------------------------------------------------------------ */

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 820,
    minHeight: 560,
    frame: false,
    show: false,
    backgroundColor: '#08101f',
    title: 'ParDL',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      webviewTag: true,
      spellcheck: false
    }
  });
  mainWindow.setMenuBarVisibility(false);
  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('maximize', () => send('window:maximized', true));
  mainWindow.on('unmaximize', () => send('window:maximized', false));
  mainWindow.on('closed', () => { mainWindow = null; });
  if (IS_DEV) {
    mainWindow.webContents.on('before-input-event', (event, input) => {
      if (input.key === 'F12') {
        mainWindow.webContents.toggleDevTools();
        event.preventDefault();
      }
    });
  }
}

function registerIpc() {
  ipcMain.handle('app:info', async () => {
    const p = getPrefs();
    const engine = await getEngineVersion();
    return {
      version: APP_VERSION,
      electron: process.versions.electron,
      chrome: process.versions.chrome,
      node: process.versions.node,
      platform: process.platform,
      arch: process.arch,
      isPackaged: app.isPackaged,
      folder: p.folder || DEFAULT_FOLDER(),
      dataFolder: app.getPath('userData'),
      engine,
      tools: { ytdlp: ytdlpPath(), ffmpeg: ffmpegPath() }
    };
  });
  ipcMain.handle('prefs:get', () => getPrefs());
  ipcMain.handle('prefs:set', (event, patch) => savePrefs(patch));
  ipcMain.handle('clipboard:read', () => clipboard.readText());
  ipcMain.handle('dialog:chooseFolder', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Elige la carpeta de descargas',
      defaultPath: getPrefs().folder || DEFAULT_FOLDER(),
      properties: ['openDirectory', 'createDirectory']
    });
    if (result.canceled || !result.filePaths.length) return null;
    return savePrefs({ folder: result.filePaths[0] }).folder;
  });
  ipcMain.handle('engine:version', () => getEngineVersion());
  ipcMain.handle('engine:update', () => updateEngine());
  ipcMain.handle('analyze', (event, url) => analyzeUrl(url));
  ipcMain.handle('download:start', (event, job) => startDownload(job || {}));
  ipcMain.handle('download:cancel', () => cancelDownload());
  ipcMain.handle('metadata:find', async (event, query) => {
    const result = await findMetadata(query || {});
    const md = result.metadata || {};
    const lyrics = await findLyrics({
      title: md.title || query?.title,
      artist: md.artist || query?.artist,
      album: md.album || query?.album,
      duration: md.durationSec || query?.duration
    }).catch(() => null);
    if (lyrics) {
      md.lyrics = lyrics.plainLyrics || '';
      md.syncedLyrics = lyrics.syncedLyrics || '';
      md.lyricsProvider = lyrics.provider;
      md.instrumental = lyrics.instrumental;
    }
    return { ...result, metadata: md, lyrics };
  });
  ipcMain.handle('lyrics:find', (event, query) => findLyrics(query || {}));
  ipcMain.handle('providers:list', () => PROVIDERS);
  ipcMain.handle('providers:test', (event, id) => testProvider(String(id || '')));
  ipcMain.handle('history:list', () => historyList());
  ipcMain.handle('history:clear', () => { writeJson(historyPath(), []); return []; });
  ipcMain.handle('history:remove', (event, id) => {
    const list = historyList().filter((entry) => entry.id !== id);
    writeJson(historyPath(), list);
    return list;
  });
  ipcMain.handle('file:open', async (event, target) => {
    if (!target) throw new Error('Archivo no válido.');
    const error = await shell.openPath(String(target));
    if (error) throw new Error(error);
    return true;
  });
  ipcMain.handle('file:show', (event, target) => {
    if (!target) throw new Error('Archivo no válido.');
    shell.showItemInFolder(String(target));
    return true;
  });
  ipcMain.handle('folder:open', async (event, folder) => {
    const target = folder || getPrefs().folder || DEFAULT_FOLDER();
    fs.mkdirSync(target, { recursive: true });
    const error = await shell.openPath(target);
    if (error) throw new Error(error);
    return true;
  });
  ipcMain.handle('external:open', async (event, url) => {
    const safe = validateHttpUrl(url);
    await shell.openExternal(safe);
    return true;
  });
  ipcMain.on('window:minimize', () => mainWindow?.minimize());
  ipcMain.on('window:maximize', () => {
    if (!mainWindow) return;
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
  });
  ipcMain.on('window:close', () => mainWindow?.close());
  ipcMain.handle('window:isMaximized', () => !!mainWindow?.isMaximized());
}

function registerSecurity() {
  app.on('web-contents-created', (event, contents) => {
    if (contents.getType() === 'webview') {
      contents.setWindowOpenHandler(({ url }) => {
        if (/^https?:\/\//i.test(url)) send('browser:open-url', url);
        return { action: 'deny' };
      });
      contents.on('will-navigate', (navEvent, url) => {
        if (!/^https?:\/\//i.test(url) && !url.startsWith('about:')) {
          navEvent.preventDefault();
          if (/^(mailto|tel):/i.test(url)) shell.openExternal(url).catch(() => {});
        }
      });
      const s = contents.session;
      s.setPermissionRequestHandler((wc, permission, callback) => callback(false));
    }
  });
  session.defaultSession.setPermissionRequestHandler((wc, permission, callback) => callback(false));
}

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    if (process.platform === 'win32') app.setAppUserModelId('com.andrespar2.pardl.pc');
    getPrefs();
    try {
      await prepareTools();
      await getEngineVersion();
    } catch (error) {
      engineInfo = { version: '', path: ytdlpPath(), error: error.message };
    }
    try {
      const browserSession = session.fromPartition('persist:pardl-browser');
      browserSession.setPermissionRequestHandler((wc, permission, callback) => callback(false));
    } catch { /* noop */ }
    registerSecurity();
    registerIpc();
    createWindow();
    if (getPrefs().autoUpdate) {
      setTimeout(() => { autoUpdateEngine(); }, 3500);
    }
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => {
    if (activeDownload) cancelDownload();
    if (process.platform !== 'darwin') app.quit();
  });
  app.on('before-quit', () => {
    if (activeDownload) cancelDownload();
  });
}
