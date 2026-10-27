'use strict';

(() => {
  const state = {
    info: null,
    prefs: null,
    history: [],
    providers: [],
    view: 'home',
    kind: 'video',
    analysis: null,
    qualityTab: 'video',
    selectedQuality: null,
    metadata: null,
    metadataLyrics: null,
    metadataProvider: 'itunes',
    metadataConfidence: 0,
    downloading: false,
    activeJobId: null,
    activeJob: null,
    lastResult: null,
    providerTests: {},
    webviewReady: false
  };

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const icon = (name) => `<svg aria-hidden="true"><use href="#i-${name}"></use></svg>`;

  const PAGE_KICKERS = {
    home: 'ParDL para Windows',
    download: 'Descarga',
    downloads: 'Biblioteca',
    browser: 'Explorar',
    history: 'Biblioteca',
    providers: 'Metadatos',
    settings: 'Sistema',
    about: 'ParDL'
  };

  const FONT = {
    P: ['11111', '10001', '11111', '10000', '10000'],
    A: ['11111', '10001', '11111', '10001', '10001'],
    R: ['11110', '10001', '11110', '10100', '10010'],
    D: ['11110', '10001', '10001', '10001', '11110'],
    L: ['10000', '10000', '10000', '10000', '11111']
  };

  const FALLBACK_PROVIDERS = [
    { id: 'musicbrainz', name: 'MusicBrainz', kind: 'metadata', description: 'Base de datos abierta y Cover Art Archive.', icon: 'MB' },
    { id: 'itunes', name: 'Apple Music / iTunes', kind: 'metadata', description: 'Catálogo con portadas en alta resolución.', icon: '♪' },
    { id: 'deezer', name: 'Deezer', kind: 'metadata', description: 'Metadatos comerciales, ISRC y portadas.', icon: 'D' },
    { id: 'archive', name: 'Internet Archive', kind: 'metadata', description: 'Audio libre y grabaciones históricas.', icon: 'IA' },
    { id: 'lrclib', name: 'LRCLIB', kind: 'lyrics', description: 'Letras sincronizadas y texto plano.', icon: 'LRC' }
  ];

  /* ---------------------------------------------------------------- */
  /* Utilidades                                                       */
  /* ---------------------------------------------------------------- */

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function cleanError(error) {
    let message = String(error?.message || error || 'Ha ocurrido un error.');
    message = message.replace(/^Error invoking remote method '[^']+':\s*(Error:\s*)?/i, '');
    return message.replace(/\s+/g, ' ').trim();
  }

  function formatBytes(bytes) {
    const value = Number(bytes) || 0;
    if (!value) return '';
    if (value < 1024) return `${value} B`;
    if (value < 1048576) return `${(value / 1024).toFixed(1)} KB`;
    if (value < 1073741824) return `${(value / 1048576).toFixed(1)} MB`;
    return `${(value / 1073741824).toFixed(2)} GB`;
  }

  function formatRelative(timestamp) {
    const value = Number(timestamp) || 0;
    if (!value) return '';
    const diff = Math.max(0, Date.now() - value);
    if (diff < 60000) return 'ahora';
    if (diff < 3600000) return `hace ${Math.floor(diff / 60000)} min`;
    if (diff < 86400000) return `hace ${Math.floor(diff / 3600000)} h`;
    if (diff < 604800000) return `hace ${Math.floor(diff / 86400000)} d`;
    return new Date(value).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' });
  }

  function formatViews(value) {
    const n = Number(value) || 0;
    if (!n) return '';
    if (n >= 1000000) return `${(n / 1000000).toFixed(1)} M visitas`;
    if (n >= 1000) return `${(n / 1000).toFixed(1)} K visitas`;
    return `${n} visitas`;
  }

  function toast(message, type = 'info', duration = 4200) {
    const stack = $('#toast-stack');
    if (!stack) return;
    const item = document.createElement('div');
    item.className = `toast ${type}`;
    const iconName = type === 'success' ? 'check' : type === 'error' ? 'x' : 'info';
    item.innerHTML = `${icon(iconName)}<span>${escapeHtml(message)}</span>`;
    stack.appendChild(item);
    setTimeout(() => {
      item.classList.add('out');
      setTimeout(() => item.remove(), 260);
    }, duration);
  }

  function setStatus(text) {
    const node = $('#titlebar-status');
    if (node) node.textContent = text;
  }

  function setBrowserStatus(text, ok = true) {
    const node = $('#browser-status');
    if (!node) return;
    node.innerHTML = `<span class="status-dot ${ok ? '' : 'off'}"></span>${escapeHtml(text)}`;
  }

  function currentFolder() {
    return state.prefs?.folder || state.info?.folder || '';
  }

  /* ---------------------------------------------------------------- */
  /* Marca de la palabra                                              */
  /* ---------------------------------------------------------------- */

  function drawWordmark(canvas) {
    if (!canvas) return;
    const cssWidth = canvas.clientWidth || Number(canvas.getAttribute('width')) || 120;
    const cssHeight = canvas.clientHeight || Number(canvas.getAttribute('height')) || 24;
    if (!cssWidth || !cssHeight) return;
    const dpr = Math.min(2.5, window.devicePixelRatio || 1);
    canvas.width = Math.round(cssWidth * dpr);
    canvas.height = Math.round(cssHeight * dpr);
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cssWidth, cssHeight);

    const letters = [...'PARDL'].map((letter) => FONT[letter]).filter(Boolean);
    if (!letters.length) return;
    const totalCols = letters.length * 5 + (letters.length - 1);
    const rows = 5;
    const gapX = Math.max(0.7, cssWidth / totalCols * 0.22);
    const tile = Math.min((cssWidth - gapX * (totalCols - 1)) / totalCols, (cssHeight - 4) / rows);
    if (tile <= 0) return;
    const offX = (cssWidth - (totalCols * tile + (totalCols - 1) * gapX)) / 2;
    const offY = (cssHeight - (rows * tile + (rows - 1) * gapX)) / 2;
    const gradient = ctx.createLinearGradient(0, 0, cssWidth, cssHeight);
    gradient.addColorStop(0, getComputedStyle(document.body).getPropertyValue('--accent').trim() || '#67b8ff');
    gradient.addColorStop(0.58, getComputedStyle(document.body).getPropertyValue('--accent-2').trim() || '#a893ff');
    gradient.addColorStop(1, getComputedStyle(document.body).getPropertyValue('--accent-3').trim() || '#51f0c4');
    ctx.fillStyle = gradient;
    ctx.shadowColor = getComputedStyle(document.body).getPropertyValue('--glow').trim() || 'rgba(86,156,255,.22)';
    ctx.shadowBlur = Math.max(2, tile * 0.35);

    let col = 0;
    for (const letter of letters) {
      for (let r = 0; r < rows; r += 1) {
        for (let c = 0; c < 5; c += 1) {
          if (letter[r][c] !== '1') continue;
          const x = offX + (col + c) * (tile + gapX);
          const y = offY + r * (tile + gapX);
          ctx.beginPath();
          const radius = Math.max(1, tile * 0.24);
          if (typeof ctx.roundRect === 'function') ctx.roundRect(x, y, tile, tile, radius);
          else ctx.rect(x, y, tile, tile);
          ctx.fill();
          ctx.shadowBlur = 0;
        }
      }
      col += 5;
      if (letter !== letters[letters.length - 1]) col += 1;
    }
    ctx.shadowBlur = 0;
  }

  function drawAllWordmarks() {
    drawWordmark($('#wordmark'));
    const about = $('#about-wordmark');
    if (about && about.offsetParent !== null) drawWordmark(about);
  }

  /* ---------------------------------------------------------------- */
  /* Tema y preferencias                                              */
  /* ---------------------------------------------------------------- */

  function applyTheme(theme) {
    const value = ['dark', 'light', 'amoled'].includes(theme) ? theme : 'dark';
    document.body.dataset.theme = value;
    $$('.theme-option').forEach((button) => button.classList.toggle('active', button.dataset.theme === value));
    setTimeout(drawAllWordmarks, 30);
  }

  function setTheme(theme) {
    applyTheme(theme);
    persistPrefs({ theme });
  }

  async function persistPrefs(patch) {
    state.prefs = { ...(state.prefs || {}), ...patch };
    syncPrefsUi();
    try {
      state.prefs = await window.pardl.setPrefs(patch);
      syncPrefsUi();
    } catch (error) {
      toast(`No se pudieron guardar los ajustes: ${cleanError(error)}`, 'error');
    }
  }

  function syncPrefsUi() {
    const p = state.prefs || {};
    const folder = currentFolder();
    if ($('#download-folder')) $('#download-folder').textContent = folder || 'Sin carpeta';
    if ($('#settings-folder')) $('#settings-folder').textContent = folder || 'Sin carpeta';
    const map = [
      ['#opt-metadata', 'embedMetadata'],
      ['#opt-cover', 'embedCover'],
      ['#opt-lyrics', 'embedLyrics'],
      ['#opt-organize', 'organize'],
      ['#set-metadata', 'embedMetadata'],
      ['#set-cover', 'embedCover'],
      ['#set-lyrics', 'embedLyrics'],
      ['#set-organize', 'organize'],
      ['#set-autoupdate', 'autoUpdate']
    ];
    for (const [selector, key] of map) {
      const input = $(selector);
      if (input) input.checked = p[key] !== false;
    }
    applyTheme(p.theme || 'dark');
  }

  /* ---------------------------------------------------------------- */
  /* Navegación                                                       */
  /* ---------------------------------------------------------------- */

  function setView(view) {
    if (!PAGE_KICKERS[view]) view = 'home';
    state.view = view;
    $$('.nav-item').forEach((button) => button.classList.toggle('active', button.dataset.view === view));
    $$('.view').forEach((section) => section.classList.toggle('active', section.id === `view-${view}`));
    if ($('#page-kicker')) $('#page-kicker').textContent = 'PARDL · WINDOWS';
    const main = $('#main');
    if (main) main.scrollTop = 0;
    if (view === 'about') setTimeout(drawAllWordmarks, 20);
    if (view === 'downloads') renderDownloads();
    if (view === 'history') renderHistory();
    if (view === 'home') renderRecent();
    if (view === 'download') setTimeout(() => $('#url-input')?.focus(), 80);
    if (view === 'browser') {
      setBrowserStatus('Listo para navegar');
      const webview = getWebview();
      try {
        const current = webview?.getURL();
        if (webview && (!current || current === 'about:blank')) webview.loadURL('https://www.youtube.com');
      } catch { /* noop */ }
    }
  }

  function setKind(kind) {
    state.kind = kind === 'audio' ? 'audio' : 'video';
    $$('.kind-btn').forEach((button) => button.classList.toggle('active', button.dataset.kind === state.kind));
    if (state.analysis) {
      if (state.kind === 'audio') state.qualityTab = 'audio';
      else if (state.analysis.hasVideo) state.qualityTab = 'video';
      renderQualities();
    }
  }

  /* ---------------------------------------------------------------- */
  /* Análisis y calidad                                               */
  /* ---------------------------------------------------------------- */

  function resetAnalysis() {
    state.analysis = null;
    state.selectedQuality = null;
    state.metadata = null;
    state.metadataLyrics = null;
    state.metadataConfidence = 0;
    if ($('#download-content')) $('#download-content').hidden = true;
    if ($('#download-progress')) $('#download-progress').hidden = true;
    if ($('#download-result')) $('#download-result').hidden = true;
    if ($('#form-error')) $('#form-error').hidden = true;
    if ($('#url-input')) {
      $('#url-input').value = '';
      $('#url-input').focus();
    }
    setStatus('Listo');
  }

  function showFormError(message) {
    const box = $('#form-error');
    if (!box) return;
    box.textContent = message;
    box.hidden = false;
  }

  async function analyzeCurrent() {
    const input = $('#url-input');
    const url = String(input?.value || '').trim();
    if (!url) {
      showFormError('Pega un enlace para empezar.');
      input?.focus();
      return;
    }
    if (state.downloading) {
      toast('Espera a que termine la descarga actual.', 'error');
      return;
    }
    const button = $('#btn-analyze');
    const loading = $('#analyze-loading');
    if ($('#form-error')) $('#form-error').hidden = true;
    if ($('#download-content')) $('#download-content').hidden = true;
    if (loading) loading.hidden = false;
    if (button) button.disabled = true;
    if (input) input.value = url;
    setStatus('Analizando enlace…');
    try {
      const analysis = await window.pardl.analyze(url);
      state.analysis = analysis;
      state.metadata = null;
      state.metadataLyrics = null;
      state.metadataConfidence = 0;
      if (!analysis.hasVideo) setKind('audio');
      else setKind(state.kind);
      if (state.kind === 'video' && !analysis.hasVideo) state.qualityTab = 'audio';
      state.selectedQuality = null;
      renderAnalysis();
      setStatus('Enlace analizado');
      toast('Enlace analizado correctamente.', 'success', 2600);
    } catch (error) {
      showFormError(cleanError(error));
      setStatus('No se pudo analizar');
    } finally {
      if (loading) loading.hidden = true;
      if (button) button.disabled = false;
    }
  }

  function renderAnalysis() {
    const analysis = state.analysis;
    if (!analysis) return;
    const image = $('#media-image');
    if (image) {
      if (analysis.thumbnail) {
        image.src = analysis.thumbnail;
        image.style.display = 'block';
        image.onerror = () => { image.style.display = 'none'; };
      } else {
        image.removeAttribute('src');
        image.style.display = 'none';
      }
    }
    const badges = $('#media-badges');
    if (badges) {
      badges.innerHTML = [
        `<span class="badge accent">${analysis.hasVideo ? 'Vídeo' : 'Audio'}</span>`,
        analysis.duration ? `<span class="badge">${escapeHtml(analysis.durationLabel)}</span>` : '',
        analysis.extractor ? `<span class="badge">${escapeHtml(analysis.extractor)}</span>` : ''
      ].filter(Boolean).join('');
    }
    if ($('#media-title')) $('#media-title').textContent = analysis.title;
    const subtitle = [analysis.artist, analysis.uploader].filter((v, i, arr) => v && arr.indexOf(v) === i).join(' · ');
    if ($('#media-subtitle')) $('#media-subtitle').textContent = subtitle || analysis.webpageUrl;
    const meta = $('#media-meta');
    if (meta) {
      meta.innerHTML = [
        analysis.duration ? `<span>${icon('play')}${escapeHtml(analysis.durationLabel)}</span>` : '',
        analysis.viewCount ? `<span>${icon('globe')}${escapeHtml(formatViews(analysis.viewCount))}</span>` : '',
        analysis.releaseYear ? `<span>${icon('tag')}${escapeHtml(analysis.releaseYear)}</span>` : ''
      ].filter(Boolean).join('');
    }
    if ($('#download-content')) $('#download-content').hidden = false;
    renderQualityTabs();
    renderQualities();
    renderProviderChips();
    renderMetadata();
    if ($('#metadata-query')) {
      const query = [analysis.track || analysis.title, analysis.artist || analysis.uploader].filter(Boolean).join(' · ');
      $('#metadata-query').textContent = query;
    }
    if ($('#download-folder')) $('#download-folder').textContent = currentFolder();
    if ($('#btn-download')) $('#btn-download').disabled = false;
  }

  function renderQualityTabs() {
    const tabs = $('#quality-tabs');
    if (!tabs || !state.analysis) return;
    const options = [];
    if (state.analysis.hasVideo) options.push(['video', 'Vídeo']);
    options.push(['audio', 'Audio']);
    tabs.innerHTML = options
      .map(([kind, label]) => `<button class="mini-tab ${state.qualityTab === kind ? 'active' : ''}" data-quality-tab="${kind}">${label}</button>`)
      .join('');
    $$('[data-quality-tab]', tabs).forEach((button) => {
      button.addEventListener('click', () => {
        state.qualityTab = button.dataset.qualityTab;
        state.kind = state.qualityTab;
        setKind(state.qualityTab);
        renderQualityTabs();
        renderQualities();
      });
    });
  }

  function qualityLabel(quality) {
    if (quality.label) return quality.label;
    if (quality.kind === 'video') return quality.height ? `${quality.height}p` : 'Mejor disponible';
    return `${String(quality.ext || 'audio').toUpperCase()}${quality.abr ? ` · ${Math.round(quality.abr)} kbps` : ''}`;
  }

  function renderQualities() {
    const grid = $('#quality-grid');
    if (!grid || !state.analysis) return;
    const list = state.qualityTab === 'audio' ? state.analysis.audioQualities : state.analysis.videoQualities;
    if (!list || !list.length) {
      grid.innerHTML = '<div class="empty-mini">No hay calidades disponibles para este contenido.</div>';
      state.selectedQuality = null;
      updateCta();
      return;
    }
    if (!state.selectedQuality || state.selectedQuality.kind !== state.qualityTab) {
      state.selectedQuality = list[0];
    }
    grid.innerHTML = list.map((quality, index) => {
      const active = state.selectedQuality && state.selectedQuality.id === quality.id;
      const note = quality.note || (quality.kind === 'video' ? `${quality.height}p` : 'Audio');
      return `<button class="quality-option ${active ? 'active' : ''}" data-quality-index="${index}">
        <span class="quality-check">${icon('check')}</span>
        <span class="quality-copy"><strong>${escapeHtml(qualityLabel(quality))}</strong><span>${escapeHtml(note)}</span></span>
      </button>`;
    }).join('');
    $$('[data-quality-index]', grid).forEach((button) => {
      button.addEventListener('click', () => {
        state.selectedQuality = list[Number(button.dataset.qualityIndex)];
        renderQualities();
      });
    });
    updateCta();
  }

  function updateCta() {
    const cta = $('#btn-download');
    if (!cta) return;
    const quality = state.selectedQuality;
    const label = state.qualityTab === 'audio' ? 'Descargar audio' : 'Descargar vídeo';
    cta.innerHTML = `${icon('download')}<span>${label}</span><span class="cta-quality">${quality ? escapeHtml(qualityLabel(quality)) : ''}</span>`;
    cta.disabled = state.downloading;
  }

  /* ---------------------------------------------------------------- */
  /* Metadatos y letras                                               */
  /* ---------------------------------------------------------------- */

  function renderProviderChips() {
    const container = $('#provider-chips');
    if (!container) return;
    const metadataProviders = (state.providers || []).filter((provider) => provider.kind === 'metadata');
    if (!metadataProviders.length) {
      container.innerHTML = '<span class="muted">Cargando proveedores…</span>';
      return;
    }
    if (!metadataProviders.some((provider) => provider.id === state.metadataProvider)) {
      state.metadataProvider = metadataProviders[0].id;
    }
    container.innerHTML = metadataProviders.map((provider) => (
      `<button class="provider-chip ${provider.id === state.metadataProvider ? 'active' : ''}" data-metadata-provider="${provider.id}">${escapeHtml(provider.name)}</button>`
    )).join('');
    $$('[data-metadata-provider]', container).forEach((button) => {
      button.addEventListener('click', () => {
        state.metadataProvider = button.dataset.metadataProvider;
        state.metadata = null;
        state.metadataLyrics = null;
        state.metadataConfidence = 0;
        renderProviderChips();
        renderMetadata();
      });
    });
  }

  function renderMetadata() {
    const preview = $('#metadata-preview');
    const status = $('#metadata-status');
    const lyricsBox = $('#lyrics-preview');
    if (!preview || !status) return;
    if (!state.metadata) {
      preview.hidden = true;
      if (lyricsBox) lyricsBox.hidden = true;
      status.textContent = 'Sin vincular';
      status.classList.remove('accent');
      return;
    }
    const md = state.metadata;
    preview.hidden = false;
    status.textContent = `Vinculado · ${state.metadataConfidence || 0}%`;
    status.classList.add('accent');
    const cover = $('#metadata-cover');
    if (cover) {
      cover.innerHTML = md.artworkUrl
        ? `<img src="${escapeHtml(md.artworkUrl)}" alt="Portada">`
        : '<div class="cover-placeholder">' + icon('image') + '</div>';
    }
    const fields = $('#metadata-fields');
    if (fields) {
      const values = [
        ['Título', md.title],
        ['Artista', md.artist],
        ['Álbum', md.album],
        ['Año / fecha', md.date],
        ['Género', md.genre],
        ['Sello', md.label],
        ['ISRC', md.isrc],
        ['Proveedor', md.provider || state.metadataProvider]
      ].filter(([, value]) => value);
      fields.innerHTML = values.map(([label, value]) => (
        `<div><span>${escapeHtml(label)}</span><strong title="${escapeHtml(value)}">${escapeHtml(value)}</strong></div>`
      )).join('');
    }
    const text = state.metadataLyrics?.plainLyrics || md.lyrics || state.metadataLyrics?.syncedLyrics || md.syncedLyrics;
    if (lyricsBox && text) {
      lyricsBox.hidden = false;
      const lyricsText = $('#lyrics-text');
      if (lyricsText) lyricsText.textContent = text.length > 5000 ? `${text.slice(0, 5000)}\n…` : text;
      const badge = $('#lyrics-badge');
      if (badge) badge.textContent = state.metadataLyrics?.instrumental ? 'Instrumental' : (state.metadataLyrics?.provider || 'Letras');
    } else if (lyricsBox) {
      lyricsBox.hidden = true;
    }
  }

  async function findMetadata() {
    if (!state.analysis) {
      toast('Analiza un enlace primero.', 'error');
      return;
    }
    const button = $('#btn-find-metadata');
    if (button) button.disabled = true;
    const status = $('#metadata-status');
    if (status) status.textContent = 'Buscando…';
    setStatus('Buscando metadatos…');
    try {
      const result = await window.pardl.findMetadata({
        provider: state.metadataProvider,
        title: state.analysis.track || state.analysis.title,
        artist: state.analysis.artist || state.analysis.uploader,
        album: state.analysis.album || '',
        duration: state.analysis.duration || 0
      });
      state.metadata = result.metadata || null;
      state.metadataLyrics = result.lyrics || null;
      state.metadataConfidence = result.confidence || 0;
      renderMetadata();
      if (state.metadata) {
        toast(`Metadatos vinculados con ${state.metadata.provider || state.metadataProvider}.`, 'success', 2800);
        setStatus('Metadatos vinculados');
      } else {
        toast('No se encontraron metadatos.', 'error');
      }
    } catch (error) {
      if (status) status.textContent = 'Sin vincular';
      toast(cleanError(error), 'error', 5200);
      setStatus('Búsqueda sin resultados');
    } finally {
      if (button) button.disabled = false;
    }
  }

  /* ---------------------------------------------------------------- */
  /* Descargas                                                        */
  /* ---------------------------------------------------------------- */

  async function startDownload() {
    if (!state.analysis || !state.selectedQuality) {
      toast('Analiza un enlace y elige una calidad.', 'error');
      return;
    }
    if (state.downloading) return;
    state.downloading = true;
    state.lastResult = null;
    if ($('#download-result')) $('#download-result').hidden = true;
    if ($('#download-progress')) $('#download-progress').hidden = false;
    if ($('#download-cta')) $('#download-cta').disabled = true;
    if ($('#btn-download')) $('#btn-download').disabled = true;
    if ($('#progress-label')) $('#progress-label').textContent = state.qualityTab === 'audio' ? 'Descargando audio…' : 'Descargando vídeo…';
    updateProgress({ percent: 0, speed: '', eta: '' });
    state.activeJob = {
      jobId: null,
      title: state.analysis.title,
      subtitle: `${qualityLabel(state.selectedQuality)} · ${state.qualityTab === 'audio' ? 'Audio' : 'Vídeo'}`,
      quality: qualityLabel(state.selectedQuality),
      kind: state.qualityTab
    };
    renderActiveDownload();
    setStatus('Descargando…');
    try {
      const result = await window.pardl.startDownload({
        url: state.analysis.webpageUrl || state.analysis.url,
        kind: state.qualityTab,
        formatId: state.selectedQuality.formatId || '',
        height: state.selectedQuality.height || 0,
        qualityLabel: qualityLabel(state.selectedQuality),
        title: state.analysis.title,
        artist: state.analysis.artist || state.analysis.uploader || '',
        thumbnail: state.analysis.thumbnail || '',
        metadata: state.metadata || null,
        embedMetadata: $('#opt-metadata')?.checked !== false,
        embedCover: $('#opt-cover')?.checked !== false,
        embedLyrics: $('#opt-lyrics')?.checked !== false
      });
      state.activeJobId = result.id;
      if (state.activeJob) state.activeJob.jobId = result.id;
      renderActiveDownload();
      if ($('#downloads-dot')) $('#downloads-dot').hidden = false;
    } catch (error) {
      state.downloading = false;
      state.activeJob = null;
      state.activeJobId = null;
      if ($('#download-progress')) $('#download-progress').hidden = true;
      if ($('#btn-download')) $('#btn-download').disabled = false;
      if ($('#downloads-dot')) $('#downloads-dot').hidden = true;
      updateCta();
      toast(cleanError(error), 'error', 5200);
      setStatus('Error al iniciar la descarga');
      renderActiveDownload();
    }
  }

  function updateProgress(payload) {
    const percent = Math.max(0, Math.min(100, Number(payload?.percent) || 0));
    const speed = payload?.speed && payload.speed !== 'NA' ? payload.speed : '';
    const eta = payload?.eta && payload.eta !== 'NA' ? payload.eta : '';
    const downloaded = formatBytes(payload?.downloaded);
    const total = formatBytes(payload?.total);
    if ($('#progress-percent')) $('#progress-percent').textContent = `${percent.toFixed(percent < 10 ? 1 : 0)}%`;
    if ($('#progress-fill')) $('#progress-fill').style.width = `${percent}%`;
    const speedText = speed ? `${speed}` : downloaded ? `${downloaded}${total ? ` / ${total}` : ''}` : '—';
    if ($('#progress-speed')) $('#progress-speed').textContent = speedText;
    if ($('#progress-eta')) $('#progress-eta').textContent = eta ? `Quedan ${eta}` : '—';
    if (state.activeJob) {
      state.activeJob.percent = percent;
      state.activeJob.speed = speed;
      state.activeJob.eta = eta;
      state.activeJob.downloaded = downloaded;
      state.activeJob.total = total;
    }
    renderActiveDownload();
    if (percent > 0) setStatus(`Descargando… ${percent.toFixed(0)}%`);
  }

  function renderActiveDownload() {
    const box = $('#active-download');
    const hasJob = !!state.activeJob;
    if (box) box.hidden = !hasJob;
    if ($('#downloads-dot')) $('#downloads-dot').hidden = !hasJob;
    if (!hasJob) return;
    if ($('#active-title')) $('#active-title').textContent = state.activeJob.title || 'Descargando…';
    if ($('#active-sub')) $('#active-sub').textContent = state.activeJob.subtitle || '';
    if ($('#active-percent')) $('#active-percent').textContent = `${(state.activeJob.percent || 0).toFixed(0)}%`;
    if ($('#active-fill')) $('#active-fill').style.width = `${state.activeJob.percent || 0}%`;
    if ($('#active-speed')) $('#active-speed').textContent = state.activeJob.speed || state.activeJob.downloaded || '—';
    if ($('#active-eta')) $('#active-eta').textContent = state.activeJob.eta ? `Quedan ${state.activeJob.eta}` : '—';
  }

  function finishDownloadSuccess(payload) {
    state.downloading = false;
    state.activeJob = null;
    state.activeJobId = null;
    state.lastResult = payload;
    if ($('#download-progress')) $('#download-progress').hidden = true;
    if ($('#download-result')) $('#download-result').hidden = false;
    if ($('#result-title')) $('#result-title').textContent = `${payload.title || payload.fileName || 'Archivo'}`;
    if ($('#result-tags')) {
      const tags = [];
      if (payload.quality) tags.push(`<span class="badge">${escapeHtml(payload.quality)}</span>`);
      if (payload.artist) tags.push(`<span class="badge">${escapeHtml(payload.artist)}</span>`);
      if (payload.tags?.cover) tags.push(`<span class="badge accent">${icon('image')}Portada</span>`);
      if (payload.tags?.lyrics) tags.push(`<span class="badge accent">${icon('lyrics')}Letras</span>`);
      if (payload.provider) tags.push(`<span class="badge">${escapeHtml(payload.provider)}</span>`);
      $('#result-tags').innerHTML = tags.join('');
    }
    if ($('#result-warning')) {
      $('#result-warning').hidden = !payload.warning;
      $('#result-warning').textContent = payload.warning || '';
    }
    if ($('#btn-download')) $('#btn-download').disabled = false;
    updateCta();
    setStatus('Descarga completada');
    renderActiveDownload();
    refreshHistory();
    toast(`Descarga completada: ${payload.title || payload.fileName}`, 'success', 4600);
  }

  function finishDownloadError(payload) {
    state.downloading = false;
    state.activeJob = null;
    state.activeJobId = null;
    if ($('#download-progress')) $('#download-progress').hidden = true;
    if ($('#btn-download')) $('#btn-download').disabled = false;
    updateCta();
    renderActiveDownload();
    setStatus(payload?.canceled ? 'Descarga cancelada' : 'Error de descarga');
    if (payload?.canceled) toast('Descarga cancelada.', 'info');
    else toast(`${payload?.message || 'La descarga falló.'}${payload?.detail ? ` ${payload.detail}` : ''}`, 'error', 7000);
  }

  async function cancelDownload() {
    if (!state.downloading) return;
    if ($('#progress-label')) $('#progress-label').textContent = 'Cancelando…';
    try {
      await window.pardl.cancelDownload();
    } catch (error) {
      toast(cleanError(error), 'error');
    }
  }

  function renderHistoryRows(list, compact = false) {
    if (!list || !list.length) {
      return `<div class="empty-state"><div class="empty-icon">${icon(compact ? 'download' : 'history')}</div><h3>${compact ? 'Aún no hay descargas' : 'El historial está vacío'}</h3><p>Las descargas aparecerán aquí en cuanto termines la primera.</p></div>`;
    }
    return list.map((entry) => {
      const subtitle = [entry.kind === 'audio' ? 'Audio' : 'Vídeo', entry.quality, formatRelative(entry.timestamp)]
        .filter(Boolean).join(' · ');
      return `<div class="download-row" data-entry-id="${escapeHtml(entry.id || '')}">
        <div class="download-row-art">${entry.thumbnail ? `<img src="${escapeHtml(entry.thumbnail)}" alt="">` : icon(entry.kind === 'audio' ? 'music' : 'video')}</div>
        <div class="download-row-copy"><strong title="${escapeHtml(entry.title)}">${escapeHtml(entry.title || entry.fileName || 'Archivo')}</strong><span title="${escapeHtml(entry.fileName || '')}">${escapeHtml(subtitle)}${entry.fileName ? ` · ${escapeHtml(entry.fileName)}` : ''}</span></div>
        <div class="download-row-actions">
          ${entry.filePath ? `<button class="icon-button" data-entry-action="file" data-path="${escapeHtml(entry.filePath)}" title="Abrir archivo">${icon('play')}</button><button class="icon-button" data-entry-action="folder" data-path="${escapeHtml(entry.filePath)}" title="Ver en carpeta">${icon('folder')}</button>` : ''}
          ${compact ? '' : `<button class="icon-button" data-entry-action="remove" data-id="${escapeHtml(entry.id || '')}" title="Quitar del historial">${icon('trash')}</button>`}
        </div>
      </div>`;
    }).join('');
  }

  function renderDownloads() {
    const list = state.history;
    if ($('#downloads-count')) $('#downloads-count').textContent = `${list.length} ${list.length === 1 ? 'elemento' : 'elementos'}`;
    if ($('#downloads-list')) $('#downloads-list').innerHTML = renderHistoryRows(list, false);
    renderActiveDownload();
  }

  function renderHistory() {
    if ($('#history-list')) $('#history-list').innerHTML = renderHistoryRows(state.history, false);
  }

  function renderRecent() {
    const container = $('#home-recent');
    if (!container) return;
    if (!state.history.length) {
      container.innerHTML = '<div class="empty-mini">Aún no hay descargas. Empieza con un enlace.</div>';
      return;
    }
    container.innerHTML = state.history.slice(0, 4).map((entry) => (
      `<button class="recent-item" data-recent-path="${escapeHtml(entry.filePath || '')}">
        <div class="download-row-art">${entry.thumbnail ? `<img src="${escapeHtml(entry.thumbnail)}" alt="">` : icon(entry.kind === 'audio' ? 'music' : 'video')}</div>
        <div class="recent-copy"><strong>${escapeHtml(entry.title || entry.fileName || 'Archivo')}</strong><span>${escapeHtml(entry.kind === 'audio' ? 'Audio' : 'Vídeo')} · ${escapeHtml(entry.quality || '')}</span></div>
        <span class="recent-time">${escapeHtml(formatRelative(entry.timestamp))}</span>
      </button>`
    )).join('');
    $$('[data-recent-path]', container).forEach((button) => {
      button.addEventListener('click', () => {
        const file = button.dataset.recentPath;
        if (file) openFile(file);
        else setView('history');
      });
    });
  }

  async function refreshHistory() {
    try {
      state.history = await window.pardl.history();
    } catch {
      state.history = state.history || [];
    }
    renderRecent();
    renderDownloads();
    renderHistory();
  }

  async function openFile(filePath) {
    if (!filePath) return;
    try { await window.pardl.openFile(filePath); }
    catch (error) { toast(cleanError(error), 'error'); }
  }

  async function showInFolder(filePath) {
    if (!filePath) return;
    try { await window.pardl.showInFolder(filePath); }
    catch (error) { toast(cleanError(error), 'error'); }
  }

  async function handleEntryAction(action, button) {
    const file = button.dataset.path;
    if (action === 'file') return openFile(file);
    if (action === 'folder') return showInFolder(file);
    if (action === 'remove') {
      const id = button.dataset.id;
      if (!id) return;
      try {
        state.history = await window.pardl.removeHistory(id);
        renderRecent();
        renderDownloads();
        renderHistory();
        toast('Elemento eliminado del historial.', 'info', 2200);
      } catch (error) { toast(cleanError(error), 'error'); }
    }
  }

  async function clearHistory() {
    if (!state.history.length) return;
    if (!confirm('¿Vaciar todo el historial de descargas? Los archivos no se borrarán.')) return;
    try {
      state.history = await window.pardl.clearHistory();
      renderRecent();
      renderDownloads();
      renderHistory();
      toast('Historial vaciado.', 'success', 2400);
    } catch (error) { toast(cleanError(error), 'error'); }
  }

  /* ---------------------------------------------------------------- */
  /* Navegador                                                        */
  /* ---------------------------------------------------------------- */

  function getWebview() {
    return $('#browser-view');
  }

  function normalizeBrowserInput(value) {
    let text = String(value || '').trim();
    if (!text) return null;
    if (/^(https?|about):/i.test(text)) return text;
    if (/^[\w.-]+\.[a-z]{2,}(\/|$)/i.test(text) || /^localhost(:\d+)?(\/|$)/i.test(text)) return `https://${text}`;
    if (/\s/.test(text) || !text.includes('.')) {
      return `https://www.youtube.com/results?search_query=${encodeURIComponent(text)}`;
    }
    return `https://${text}`;
  }

  function navigateBrowser(value) {
    const url = normalizeBrowserInput(value);
    if (!url) return;
    const webview = getWebview();
    if (!webview) return;
    try {
      webview.loadURL(url);
      if ($('#browser-url')) $('#browser-url').value = url;
    } catch {
      webview.setAttribute('src', url);
    }
  }

  async function resolveBrowserUrl() {
    const webview = getWebview();
    if (!webview) return '';
    const script = `(function () {
      const meta = document.querySelector('meta[property="og:url"]');
      const canonical = document.querySelector('link[rel="canonical"]');
      const video = document.querySelector('video');
      const current = video && (video.currentSrc || video.src);
      const href = location.href;
      if (location.hostname.includes('youtube.com')) {
        const id = new URL(href).searchParams.get('v');
        if (id) return 'https://www.youtube.com/watch?v=' + id;
      }
      if (meta && meta.content) return meta.content;
      if (canonical && canonical.href) return canonical.href;
      if (current && /^https?:\\/\\//i.test(current) && !/^blob:/i.test(current)) return current;
      return href;
    })()`;
    try {
      const result = await webview.executeJavaScript(script, true);
      return String(result || '');
    } catch {
      try { return webview.getURL(); } catch { return ''; }
    }
  }

  async function openBrowserInDownload(kind) {
    setBrowserStatus('Detectando el contenido…');
    const url = await resolveBrowserUrl();
    if (!url || url === 'about:blank') {
      setBrowserStatus('No se pudo detectar el enlace', false);
      toast('Abre un vídeo o una página de audio y vuelve a intentarlo.', 'error');
      return;
    }
    if (!/^https?:\/\//i.test(url)) {
      setBrowserStatus('Enlace no compatible', false);
      toast('Este contenido no tiene una URL compatible.', 'error');
      return;
    }
    setBrowserStatus('Contenido detectado');
    setView('download');
    resetAnalysis();
    setKind(kind);
    if ($('#url-input')) $('#url-input').value = url;
    await analyzeCurrent();
  }

  function bindBrowser() {
    const webview = getWebview();
    if (!webview) return;
    if ($('#browser-url')) $('#browser-url').value = '';
    const updateUrl = (url) => {
      if ($('#browser-url')) $('#browser-url').value = url || '';
    };
    webview.addEventListener('did-start-loading', () => {
      if ($('#webview-loading')) $('#webview-loading').hidden = false;
      setBrowserStatus('Cargando sitio…');
    });
    webview.addEventListener('did-stop-loading', () => {
      if ($('#webview-loading')) $('#webview-loading').hidden = true;
      setBrowserStatus('Listo para navegar');
      try {
        const url = webview.getURL();
        updateUrl(url);
        if (url && !url.startsWith('about:')) {
          if ($('#browser-url')) $('#browser-url').value = url;
        }
      } catch { /* noop */ }
    });
    webview.addEventListener('did-navigate', (event) => updateUrl(event.url));
    webview.addEventListener('did-navigate-in-page', (event) => updateUrl(event.url));
    webview.addEventListener('page-title-updated', (event) => {
      if (event.title) setBrowserStatus(event.title.slice(0, 90));
    });
    webview.addEventListener('dom-ready', () => {
      state.webviewReady = true;
    });
    webview.addEventListener('did-fail-load', (event) => {
      if (event.errorCode === -3) return;
      setBrowserStatus(`No se pudo cargar: ${event.errorDescription || 'error'}`, false);
    });

    $('#btn-back')?.addEventListener('click', () => { try { if (webview.canGoBack()) webview.goBack(); } catch { /* noop */ } });
    $('#btn-forward')?.addEventListener('click', () => { try { if (webview.canGoForward()) webview.goForward(); } catch { /* noop */ } });
    $('#btn-reload')?.addEventListener('click', () => { try { webview.reload(); } catch { /* noop */ } });
    $('#btn-go')?.addEventListener('click', () => navigateBrowser($('#browser-url')?.value));
    $('#browser-url')?.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') navigateBrowser($('#browser-url')?.value);
    });
    $('#btn-browser-external')?.addEventListener('click', () => {
      try {
        const url = webview.getURL();
        if (url && /^https?:\/\//i.test(url)) window.pardl.openExternal(url);
      } catch { /* noop */ }
    });
    $('#btn-web-audio')?.addEventListener('click', () => openBrowserInDownload('audio'));
    $('#btn-web-video')?.addEventListener('click', () => openBrowserInDownload('video'));
  }

  /* ---------------------------------------------------------------- */
  /* Proveedores                                                      */
  /* ---------------------------------------------------------------- */

  async function loadProviders() {
    try {
      state.providers = await window.pardl.providers();
    } catch {
      state.providers = FALLBACK_PROVIDERS;
    }
    renderProviderChips();
    renderProviders();
  }

  function renderProviders() {
    const grid = $('#providers-grid');
    if (!grid) return;
    grid.innerHTML = state.providers.map((provider) => {
      const test = state.providerTests[provider.id];
      const statusText = test ? (test.ok ? `OK · ${test.latency} ms` : test.message) : 'Sin probar';
      const stateClass = test ? (test.ok ? 'ok' : 'bad') : '';
      return `<div class="provider-card glass">
        <div class="provider-card-head"><div class="provider-logo">${escapeHtml(provider.icon || provider.name.slice(0, 2))}</div><div><strong>${escapeHtml(provider.name)}</strong><span>${escapeHtml(provider.kind === 'lyrics' ? 'Letras' : 'Metadatos')}</span></div></div>
        <p>${escapeHtml(provider.description || '')}</p>
        <div class="provider-card-foot"><span class="provider-state ${stateClass}" id="provider-state-${provider.id}"><span class="status-dot"></span>${escapeHtml(statusText)}</span><button class="secondary-button small" data-test-provider="${provider.id}">Probar</button></div>
      </div>`;
    }).join('');
    $$('[data-test-provider]', grid).forEach((button) => {
      button.addEventListener('click', () => testProvider(button.dataset.testProvider));
    });
  }

  async function testProvider(id) {
    const stateNode = $(`#provider-state-${id}`);
    if (stateNode) stateNode.innerHTML = '<span class="status-dot"></span>Probando…';
    try {
      const result = await window.pardl.testProvider(id);
      state.providerTests[id] = result;
      renderProviders();
      toast(`${id}: ${result.ok ? `responde en ${result.latency} ms` : result.message}`, result.ok ? 'success' : 'error', 3600);
    } catch (error) {
      state.providerTests[id] = { ok: false, latency: 0, message: cleanError(error) };
      renderProviders();
      toast(cleanError(error), 'error');
    }
  }

  async function testAllProviders() {
    const button = $('#btn-test-all');
    if (button) button.disabled = true;
    try {
      for (const provider of state.providers) {
        try {
          state.providerTests[provider.id] = await window.pardl.testProvider(provider.id);
        } catch (error) {
          state.providerTests[provider.id] = { ok: false, latency: 0, message: cleanError(error) };
        }
        renderProviders();
      }
      const good = Object.values(state.providerTests).filter((test) => test.ok).length;
      toast(`${good} de ${state.providers.length} proveedores responden.`, good ? 'success' : 'error', 3800);
    } finally {
      if (button) button.disabled = false;
    }
  }

  /* ---------------------------------------------------------------- */
  /* Ajustes                                                          */
  /* ---------------------------------------------------------------- */

  function renderSettings() {
    const info = state.info || {};
    const engine = info.engine || {};
    const engineText = engine.version ? `yt-dlp ${engine.version}` : engine.error ? 'yt-dlp no disponible' : 'yt-dlp —';
    if ($('#settings-engine')) $('#settings-engine').textContent = engine.version || engine.error || '—';
    if ($('#engine-badge')) $('#engine-badge').textContent = engine.version ? 'Al día' : 'Sin conexión';
    if ($('#stat-engine')) $('#stat-engine').textContent = engine.version ? `v${engine.version}` : '—';
    if ($('#sidebar-engine-text')) $('#sidebar-engine-text').textContent = engineText;
    if ($('#settings-ffmpeg')) $('#settings-ffmpeg').textContent = info.tools?.ffmpeg || 'Incluido';
    const dot = $('#sidebar-engine .status-dot');
    if (dot) dot.classList.toggle('off', !engine.version);
    syncPrefsUi();
  }

  async function updateEngine() {
    const button = $('#btn-update-engine');
    if (button) button.disabled = true;
    if (button) button.innerHTML = `${icon('refresh')}Actualizando…`;
    try {
      const result = await window.pardl.updateEngine();
      if (result.ok) {
        toast(result.updated ? `yt-dlp actualizado a ${result.after}.` : 'yt-dlp ya está al día.', 'success', 3600);
        state.info = { ...(state.info || {}), engine: { version: result.after, path: '', error: '' } };
        renderSettings();
      } else {
        toast(result.message || 'No se pudo actualizar.', 'error', 5200);
      }
    } catch (error) {
      toast(cleanError(error), 'error', 6200);
    } finally {
      if (button) button.disabled = false;
      if (button) button.innerHTML = `${icon('refresh')}Actualizar`;
    }
  }

  async function chooseFolder() {
    try {
      const folder = await window.pardl.chooseFolder();
      if (folder) {
        state.prefs = { ...(state.prefs || {}), folder };
        syncPrefsUi();
        toast('Carpeta de descargas actualizada.', 'success', 2400);
      }
    } catch (error) {
      toast(cleanError(error), 'error');
    }
  }

  async function openDownloadsFolder() {
    try { await window.pardl.openFolder(currentFolder()); }
    catch (error) { toast(cleanError(error), 'error'); }
  }

  async function openDataFolder() {
    try { await window.pardl.openFolder(state.info?.dataFolder || ''); }
    catch (error) { toast(cleanError(error), 'error'); }
  }

  async function clearHistoryFromSettings() {
    if (!state.history.length) return;
    if (!confirm('¿Vaciar todo el historial? Los archivos no se borrarán.')) return;
    try {
      state.history = await window.pardl.clearHistory();
      refreshHistory();
      toast('Historial vaciado.', 'success', 2200);
    } catch (error) { toast(cleanError(error), 'error'); }
  }

  /* ---------------------------------------------------------------- */
  /* Eventos                                                          */
  /* ---------------------------------------------------------------- */

  function bindTitlebar() {
    $('#btn-min')?.addEventListener('click', () => window.pardl.minimize());
    $('#btn-max')?.addEventListener('click', () => window.pardl.maximize());
    $('#btn-close')?.addEventListener('click', () => window.pardl.close());
    $('#titlebar')?.addEventListener('dblclick', (event) => {
      if (!event.target.closest('button')) window.pardl.maximize();
    });
    window.pardl.onWindowMaximized((maximized) => {
      const use = $('#max-icon use');
      if (use) use.setAttribute('href', maximized ? '#i-restore' : '#i-maximize');
      $('#btn-max')?.setAttribute('title', maximized ? 'Restaurar' : 'Maximizar');
    });
  }

  function bindNavigation() {
    $$('.nav-item').forEach((button) => {
      button.addEventListener('click', () => setView(button.dataset.view));
    });
    $$('[data-go]').forEach((button) => {
      button.addEventListener('click', () => {
        if (button.dataset.view) {
          setView(button.dataset.view);
          return;
        }
        const view = button.dataset.go;
        if (button.dataset.kind) setKind(button.dataset.kind);
        setView(view);
      });
    });
    $('#quick-folder')?.addEventListener('click', openDownloadsFolder);
    $('#quick-settings')?.addEventListener('click', () => setView('settings'));
    window.pardl.onBrowserOpenUrl((url) => {
      setView('browser');
      navigateBrowser(url);
    });
    window.addEventListener('resize', () => {
      clearTimeout(window.__wordmarkTimer);
      window.__wordmarkTimer = setTimeout(drawAllWordmarks, 80);
    });
  }

  function bindDownload() {
    $('#btn-analyze')?.addEventListener('click', analyzeCurrent);
    $('#url-input')?.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') analyzeCurrent();
    });
    $('#btn-paste')?.addEventListener('click', async () => {
      try {
        const text = await window.pardl.readClipboard();
        if (text && $('#url-input')) {
          $('#url-input').value = text.trim();
          $('#url-input').focus();
          toast('Enlace pegado.', 'info', 1500);
        } else {
          toast('El portapapeles está vacío.', 'info', 1800);
        }
      } catch {
        toast('No se pudo leer el portapapeles. Usa Ctrl+V.', 'error', 2800);
      }
    });
    $('#btn-reset')?.addEventListener('click', resetAnalysis);
    $$('.kind-btn').forEach((button) => {
      button.addEventListener('click', () => setKind(button.dataset.kind));
    });
    $('#btn-find-metadata')?.addEventListener('click', findMetadata);
    $('#btn-clear-metadata')?.addEventListener('click', () => {
      state.metadata = null;
      state.metadataLyrics = null;
      state.metadataConfidence = 0;
      renderMetadata();
      toast('Metadatos desvinculados.', 'info', 1800);
    });
    $('#btn-change-folder')?.addEventListener('click', chooseFolder);
    $('#btn-download')?.addEventListener('click', startDownload);
    $('#btn-cancel')?.addEventListener('click', cancelDownload);
    $('#btn-active-cancel')?.addEventListener('click', cancelDownload);
    $('#btn-open-file')?.addEventListener('click', () => openFile(state.lastResult?.filePath));
    $('#btn-open-folder')?.addEventListener('click', () => showInFolder(state.lastResult?.filePath));
    $('#btn-open-downloads-folder')?.addEventListener('click', openDownloadsFolder);
    $('#btn-clear-history')?.addEventListener('click', clearHistory);
    $('#btn-test-all')?.addEventListener('click', testAllProviders);
    $('#btn-update-engine')?.addEventListener('click', updateEngine);
    $('#btn-settings-folder')?.addEventListener('click', chooseFolder);
    $('#btn-settings-clear-history')?.addEventListener('click', clearHistoryFromSettings);
    $('#btn-data-folder')?.addEventListener('click', openDataFolder);
    $('#btn-github')?.addEventListener('click', () => window.pardl.openExternal('https://github.com/andrespar2/ParDL'));
    $('#btn-supported-sites')?.addEventListener('click', () => window.pardl.openExternal('https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md'));
    $$('.theme-option').forEach((button) => button.addEventListener('click', () => setTheme(button.dataset.theme)));
    const prefToggles = [
      ['#opt-metadata', 'embedMetadata'],
      ['#opt-cover', 'embedCover'],
      ['#opt-lyrics', 'embedLyrics'],
      ['#opt-organize', 'organize'],
      ['#set-metadata', 'embedMetadata'],
      ['#set-cover', 'embedCover'],
      ['#set-lyrics', 'embedLyrics'],
      ['#set-organize', 'organize'],
      ['#set-autoupdate', 'autoUpdate']
    ];
    for (const [selector, key] of prefToggles) {
      const input = $(selector);
      if (!input) continue;
      input.addEventListener('change', () => persistPrefs({ [key]: input.checked }));
    }
    document.addEventListener('click', (event) => {
      const button = event.target.closest('[data-entry-action]');
      if (button) handleEntryAction(button.dataset.entryAction, button);
    });
  }

  function bindEvents() {
    window.pardl.onDownloadProgress((payload) => {
      if (state.activeJobId && payload.jobId && payload.jobId !== state.activeJobId) return;
      updateProgress(payload);
    });
    window.pardl.onDownloadComplete((payload) => {
      if (state.activeJobId && payload.jobId && payload.jobId !== state.activeJobId) return;
      finishDownloadSuccess(payload);
    });
    window.pardl.onDownloadError((payload) => {
      if (state.activeJobId && payload.jobId && payload.jobId !== state.activeJobId) return;
      finishDownloadError(payload);
    });
    window.pardl.onEngineUpdated((payload) => {
      if (!payload) return;
      if (payload.ok) {
        state.info = { ...(state.info || {}), engine: { version: payload.after, path: '', error: '' } };
        renderSettings();
        if (payload.updated) toast(`yt-dlp actualizado a ${payload.after}.`, 'success', 3800);
      } else if (payload.message) {
        setStatus('No se pudo actualizar yt-dlp');
      }
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        if (state.view === 'download' && state.analysis && !state.downloading) {
          // Escape cierra la vista de resultados sin perder el enlace.
          if ($('#download-progress')?.hidden === false) return;
        }
      }
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'v' && document.activeElement?.id === 'url-input') {
        // El pegado nativo funciona; este atajo es solo para que el foco sea predecible.
      }
    });
  }

  /* ---------------------------------------------------------------- */
  /* Inicio                                                           */
  /* ---------------------------------------------------------------- */

  async function init() {
    bindTitlebar();
    bindNavigation();
    bindDownload();
    bindBrowser();
    bindEvents();
    document.addEventListener('error', (event) => {
      const target = event.target;
      if (target && target.tagName === 'IMG') target.style.display = 'none';
    }, true);
    drawAllWordmarks();
    setView('home');

    state.prefs = {
      theme: 'dark',
      folder: '',
      embedMetadata: true,
      embedCover: true,
      embedLyrics: true,
      organize: false,
      autoUpdate: true
    };
    const [infoResult, prefsResult, historyResult, providersResult] = await Promise.allSettled([
      window.pardl.getInfo(),
      window.pardl.getPrefs(),
      window.pardl.history(),
      window.pardl.providers()
    ]);
    if (infoResult.status === 'fulfilled') state.info = infoResult.value;
    if (prefsResult.status === 'fulfilled') state.prefs = prefsResult.value;
    if (historyResult.status === 'fulfilled') state.history = historyResult.value;
    state.providers = providersResult.status === 'fulfilled' ? providersResult.value : FALLBACK_PROVIDERS;
    const initError = [infoResult, prefsResult, historyResult, providersResult].find((result) => result.status === 'rejected');
    if (initError) toast(`No se pudo cargar todo el estado: ${cleanError(initError.reason)}`, 'error', 6000);
    state.metadataProvider = state.providers.find((provider) => provider.id === 'itunes')?.id
      || state.providers.find((provider) => provider.kind === 'metadata')?.id
      || 'itunes';
    applyTheme(state.prefs?.theme || 'dark');
    renderSettings();
    renderProviderChips();
    renderProviders();
    renderRecent();
    renderDownloads();
    renderHistory();
    setTimeout(drawAllWordmarks, 100);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
