/* =============================================================================
   ELIO SPEEDO — penggerak template 4 mode speedometer
   ElioSpeedo.setSpeed(kmh)            kecepatan 0–260 km/h
   ElioSpeedo.setGForce(g)             G-force asli (mis. dari accelerometer); null = perkiraan dari kecepatan
   ElioSpeedo.setStyle(name)           'lfa' | 'm3' | 'mazda' | 'honda'
   ElioSpeedo.nextStyle()              ganti ke mode berikutnya
   ElioSpeedo.setGradient(c1, c2)      warna gradasi, mis. ('#00E5FF', '#A855F7'); c2 kosong = warna solid
   ElioSpeedo.setEco(true|false)       Mode Hemat: hentikan animasi berkedip
   ElioSpeedo.init(element?)           opsional; default memakai elemen .elio-speedo pertama
   ============================================================================= */
(function (global) {
  'use strict';

  const STYLES = ['lfa', 'm3', 'mazda', 'honda', 'elio', 'bmw_m'];
  let root = null;
  let speed = 0;
  let realG = null;
  let style = 'elio';
  let lastGridBucket = -1;
  let elioTicksBuilt = false;

  const $ = (id) => (root ? root.querySelector('#' + id) : null);
  const setText = (id, value) => { const el = $(id); if (el) el.textContent = value; };
  const setAttr = (id, name, value) => { const el = $(id); if (el) el.setAttribute(name, value); };

  function hexToRgb(hex) {
    let h = String(hex || '').replace('#', '').trim();
    if (h.length === 3) h = h.split('').map((c) => c + c).join('');
    const n = parseInt(h, 16);
    if (Number.isNaN(n) || h.length !== 6) return { r: 0, g: 229, b: 255 };
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
  }

  function buildElioTicks() {
    if (elioTicksBuilt || !root) return;
    const tickGroup = $('elioTicks');
    const numberGroup = $('elioNumbers');
    if (!tickGroup || !numberGroup) return;
    elioTicksBuilt = true;
    const ns = 'http://www.w3.org/2000/svg';
    const cx = 280, cy = 182, totalSteps = 24;
    for (let i = 0; i <= totalSteps; i++) {
      const deg = 156 + (i / totalSteps) * 228;
      const a = deg * Math.PI / 180;
      const major = i % 2 === 0;
      const r1 = major ? 146 : 151, r2 = 159;
      const line = document.createElementNS(ns, 'line');
      line.setAttribute('x1', (cx + Math.cos(a) * r1).toFixed(1));
      line.setAttribute('y1', (cy + Math.sin(a) * r1).toFixed(1));
      line.setAttribute('x2', (cx + Math.cos(a) * r2).toFixed(1));
      line.setAttribute('y2', (cy + Math.sin(a) * r2).toFixed(1));
      line.setAttribute('stroke-width', major ? '2.2' : '1.1');
      line.setAttribute('opacity', major ? '0.9' : '0.45');
      tickGroup.appendChild(line);
      if (major) {
        const n = document.createElementNS(ns, 'text');
        n.setAttribute('x', (cx + Math.cos(a) * 124).toFixed(1));
        n.setAttribute('y', (cy + Math.sin(a) * 124 + 5).toFixed(1));
        n.textContent = String(i * 10);
        numberGroup.appendChild(n);
      }
    }
  }

  function init(element) {
    root = element || document.querySelector('.elio-speedo');
    if (!root) throw new Error('ElioSpeedo: elemen .elio-speedo tidak ditemukan');
    buildElioTicks();
    setStyle(style);
    render();
    return api;
  }

  function ensureRoot() { if (!root) init(); }

  // Efek reaktif kecepatan: cahaya makin terang, grid makin cepat mengalir.
  function applySpeedFx(kmh) {
    const glowEl = root.querySelector('.speedo-glow');
    (glowEl || root).style.setProperty('--speed-ratio', Math.min(1, kmh / 160).toFixed(2));
    const bucket = kmh < 3 ? 0 : Math.min(16, Math.ceil(kmh / 10)); // per 10 km/h agar tidak tersentak
    if (bucket === lastGridBucket) return;
    lastGridBucket = bucket;
    root.style.setProperty('--grid-state', bucket === 0 ? 'paused' : 'running');
    root.style.setProperty('--grid-duration', bucket === 0 ? '2s' : Math.max(0.22, 2.2 - bucket * 0.125).toFixed(2) + 's');
  }

  function render() {
    ensureRoot();
    const v = speed;
    const g = realG !== null ? realG : (v === 0 ? 0 : 0.05 + (v / 180) * 0.75);
    const gText = (g >= 0 ? '+' : '') + g.toFixed(2) + 'G';
    applySpeedFx(v);

    // BMW M3: bilah kecepatan (track 500) + bilah G
    setText('speedDisplay', v);
    setAttr('m3SpeedPath', 'stroke-dashoffset', Math.max(10, 500 - (v / 220) * 480));
    setAttr('m3RpmPath', 'stroke-dashoffset', Math.max(10, 500 - (Math.min(1.6, Math.abs(g)) / 1.6) * 480));
    setText('gScoreDisplay', gText);

    // Lexus F-Sport: busur halo (336) + jarum -130°..+130°
    setText('lfaSpeedDisplay', v);
    setAttr('lfaHaloBlade', 'stroke-dashoffset', Math.max(0, 336 - (v / 260) * 336));
    setAttr('lfaNeedle', 'transform', `rotate(${-130 + (v / 260) * 260} 0 0)`);
    setAttr('lfaLoadBar', 'width', Math.min(72, Math.max(4, Math.round((v / 220) * 72))));
    setText('lfaGForceDial', gText);
    setText('lfaGDisplay', gText);

    // Mazda KODO: busur (336) + jarum
    setText('mazdaSpeedDisplay', v);
    setAttr('mazdaHaloArc', 'stroke-dashoffset', Math.max(0, 336 - (v / 260) * 336));
    setAttr('mazdaNeedle', 'transform', `rotate(${-130 + (v / 260) * 260} 0 0)`);
    setText('mazdaGDisplay', gText);

    // Honda Type R: bilah (240), bola G-meter, lampu shift F1
    setText('hondaSpeedDisplay', v);
    setAttr('hondaSpeedBlade', 'stroke-dashoffset', Math.max(10, 240 - (v / 220) * 225));
    setAttr('hondaGBall', 'cx', Math.min(96, Math.max(14, 55 + g * 28)));
    setAttr('hondaGBall', 'cy', Math.min(134, Math.max(50, 92 - (v / 220) * 32)));
    setText('hondaGText', gText + ' ACCEL');
    const thresholds = [20, 40, 60, 80, 100, 120, 140, 160, 180, 200];
    for (let i = 1; i <= 10; i++) {
      const led = $('hondaLed' + i);
      if (!led) continue;
      const on = v >= thresholds[i - 1];
      led.style.opacity = on ? '1' : '0.2';
      led.style.filter = on && i >= 8 ? 'drop-shadow(0 0 6px #EE1C25)' : 'none';
    }

    // Tema 1 — ELIO Neon (Innova Reborn, standalone tanpa transmisi ECU)
    const ratio240 = Math.min(1, v / 240);
    setText('elioSpeedDisplay', v);
    setAttr('elioArcProgress', 'stroke-dashoffset', Math.round(645 * (1 - ratio240)));
    setText('elioGDisplay', Math.abs(g).toFixed(2) + ' G');

    // Tema 2 — BMW M Sport Live Cockpit (Innova Reborn, standalone tanpa transmisi ECU)
    const gRatio = Math.min(1, Math.max(ratio240 * 1.1, Math.abs(g) / 1.2));
    setText('bmwMSpeedDisplay', v);
    setAttr('bmwMBlueProgress', 'stroke-dashoffset', Math.round(285 * (1 - ratio240)));
    setAttr('bmwMRedProgress', 'stroke-dashoffset', Math.round(285 * (1 - gRatio)));
    setText('bmwMGDisplay', Math.abs(g).toFixed(1) + 'G');
  }

  function setSpeed(kmh) {
    speed = Math.max(0, Math.min(260, Math.round(Number(kmh) || 0)));
    render();
  }

  function setGForce(g) {
    realG = g === null || g === undefined || Number.isNaN(Number(g)) ? null : Number(g);
    render();
  }

  function setStyle(name) {
    ensureRoot();
    style = STYLES.includes(name) ? name : 'lfa';
    STYLES.forEach((s) => {
      const el = $('cluster_' + s);
      if (el) el.classList.toggle('hidden', s !== style);
    });
    root.setAttribute('data-style', style);
    render();
  }

  function nextStyle() {
    setStyle(STYLES[(STYLES.indexOf(style) + 1) % STYLES.length]);
    return style;
  }

  function setGradient(c1, c2) {
    ensureRoot();
    c2 = c2 || c1;
    const a = hexToRgb(c1);
    const b = hexToRgb(c2);
    const s = root.style;
    s.setProperty('--theme-accent', c1);
    s.setProperty('--theme-accent-rgb', `${a.r}, ${a.g}, ${a.b}`);
    s.setProperty('--theme-glow', `rgba(${a.r}, ${a.g}, ${a.b}, 0.5)`);
    s.setProperty('--theme-bg-subtle', `rgba(${a.r}, ${a.g}, ${a.b}, 0.08)`);
    s.setProperty('--theme-border', `rgba(${a.r}, ${a.g}, ${a.b}, 0.35)`);
    s.setProperty('--theme-accent-2', c2);
    s.setProperty('--theme-accent-2-rgb', `${b.r}, ${b.g}, ${b.b}`);
    s.setProperty('--theme-gradient', `linear-gradient(135deg, ${c1}, ${c2})`);
  }

  function setEco(on) {
    ensureRoot();
    root.classList.toggle('eco-mode', !!on);
  }

  function setUnit(u) {
    ensureRoot();
    const label = u === 'mph' ? 'mph' : 'km/h';
    root.querySelectorAll('.as-unit-label, .as-unit-sm').forEach((el) => {
      if (el.textContent !== label) el.textContent = label;
    });
  }

  const api = {
    init, setSpeed, setGForce, setStyle, nextStyle, setGradient, setEco, setUnit,
    get speed() { return speed; },
    get style() { return style; },
    STYLES: STYLES.slice(),
  };
  global.ElioSpeedo = api;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => { if (!root && document.querySelector('.elio-speedo')) init(); });
  } else if (document.querySelector('.elio-speedo')) {
    init();
  }
})(window);
