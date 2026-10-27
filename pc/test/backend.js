'use strict';

/**
 * Comprobación del motor local de ParDL PC.
 * No descarga contenido: solo valida que yt-dlp, FFmpeg y las dependencias
 * de metadatos estén presentes y respondan.
 */
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const tools = path.join(root, 'tools');
const checks = [];

function fail(message) {
  checks.push({ ok: false, message });
  console.error(`  [X] ${message}`);
}

function ok(message) {
  checks.push({ ok: true, message });
  console.log(`  [OK] ${message}`);
}

console.log('\nParDL PC · comprobación del motor\n');

const ytdlp = path.join(tools, 'yt-dlp.exe');
const ffmpeg = path.join(tools, 'ffmpeg.exe');
if (!fs.existsSync(ytdlp)) fail(`Falta ${ytdlp}`);
if (!fs.existsSync(ffmpeg)) fail(`Falta ${ffmpeg}`);

if (fs.existsSync(ytdlp)) {
  try {
    const version = execFileSync(ytdlp, ['--version'], { encoding: 'utf8', timeout: 30000 }).trim();
    ok(`yt-dlp ${version}`);
  } catch (error) {
    fail(`yt-dlp no responde: ${error.message}`);
  }
}

if (fs.existsSync(ffmpeg)) {
  try {
    const out = execFileSync(ffmpeg, ['-version'], { encoding: 'utf8', timeout: 30000 });
    const version = out.split(/\r?\n/)[0];
    ok(version);
  } catch (error) {
    fail(`FFmpeg no responde: ${error.message}`);
  }
}

for (const moduleName of ['music-metadata', 'node-id3']) {
  try {
    require(moduleName);
    ok(`Dependencia ${moduleName}`);
  } catch (error) {
    fail(`No se pudo cargar ${moduleName}: ${error.message}`);
  }
}

const failed = checks.filter((check) => !check.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} comprobaciones correctas.`);
if (failed.length) process.exit(1);
console.log('Motor listo.\n');
