/* =============================================================================
   ELIO UX SPEEDO — 6 Tema HD Full-Press (Mengikuti Warna Tema Utama)
   'lfa' | 'm3' (BMW G-Series) | 'mazda' | 'honda' (Civic FL5) | 'elio' | 'bmw_m'
   ============================================================================= */
(function (global) {
  'use strict';

  const STYLES = ['lfa', 'm3', 'mazda', 'honda', 'elio', 'bmw_m'];
  let root = null;
  let speed = 0;
  let realG = null;
  let style = 'elio';
  let lastGridBucket = -1;
  let ticksBuilt = false;
  let fl5BarEls = [];
  let fl5LedEls = [];

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

  function buildAllTicks() {
    if (ticksBuilt || !root) return;
    ticksBuilt = true;
    const ns = 'http://www.w3.org/2000/svg';

    // 1. ELIO Neon unobstructed arc ticks (cx=170, cy=136, r=104..116)
    const eT = $('elioTicks'), eN = $('elioNumbers');
    if (eT && eN) {
      for (let i = 0; i <= 24; i++) {
        const a = (156 + (i / 24) * 228) * Math.PI / 180;
        const maj = i % 2 === 0;
        const l = document.createElementNS(ns, 'line');
        l.setAttribute('x1', (170 + Math.cos(a) * (maj ? 105 : 109)).toFixed(1));
        l.setAttribute('y1', (136 + Math.sin(a) * (maj ? 105 : 109)).toFixed(1));
        l.setAttribute('x2', (170 + Math.cos(a) * 116).toFixed(1));
        l.setAttribute('y2', (136 + Math.sin(a) * 116).toFixed(1));
        l.setAttribute('stroke-width', maj ? '2.1' : '1.1');
        l.setAttribute('opacity', maj ? '0.9' : '0.42');
        eT.appendChild(l);
        if (maj && i % 4 === 0) {
          const t = document.createElementNS(ns, 'text');
          t.setAttribute('x', (170 + Math.cos(a) * 90).toFixed(1));
          t.setAttribute('y', (136 + Math.sin(a) * 90 + 3.5).toFixed(1));
          t.textContent = String(i * 10);
          eN.appendChild(t);
        }
      }
    }

    // 2. Lexus F-Sport HD ticks (cx=140, cy=124)
    const lT = $('lfaTicks'), lN = $('lfaNums');
    if (lT && lN) {
      for (let i = 0; i <= 24; i++) {
        const a = (135 + i * 11.25) * Math.PI / 180;
        const maj = i % 2 === 0;
        const l = document.createElementNS(ns, 'line');
        l.setAttribute('x1', (140 + Math.cos(a) * (maj ? 82 : 87)).toFixed(1));
        l.setAttribute('y1', (124 + Math.sin(a) * (maj ? 82 : 87)).toFixed(1));
        l.setAttribute('x2', (140 + Math.cos(a) * 93).toFixed(1));
        l.setAttribute('y2', (124 + Math.sin(a) * 93).toFixed(1));
        l.setAttribute('stroke-width', maj ? '2' : '1');
        l.setAttribute('opacity', maj ? '0.9' : '0.4');
        lT.appendChild(l);
        if (maj && i % 4 === 0) {
          const t = document.createElementNS(ns, 'text');
          t.setAttribute('x', (140 + Math.cos(a) * 70).toFixed(1));
          t.setAttribute('y', (124 + Math.sin(a) * 70 + 4).toFixed(1));
          t.textContent = String(i * 10);
          lN.appendChild(t);
        }
      }
    }

    // 3. Mazda KODO HD 49 precision ticks (cx=140, cy=124)
    const mT = $('mazdaTicks'), mN = $('mazdaNums');
    if (mT && mN) {
      for (let i = 0; i <= 48; i++) {
        const a = (135 + i * 5.625) * Math.PI / 180;
        const maj = i % 4 === 0, mid = i % 2 === 0;
        const r1 = maj ? 84 : (mid ? 90 : 94);
        const l = document.createElementNS(ns, 'line');
        l.setAttribute('x1', (140 + Math.cos(a) * r1).toFixed(1));
        l.setAttribute('y1', (124 + Math.sin(a) * r1).toFixed(1));
        l.setAttribute('x2', (140 + Math.cos(a) * 98).toFixed(1));
        l.setAttribute('y2', (124 + Math.sin(a) * 98).toFixed(1));
        l.setAttribute('stroke-width', maj ? '2.2' : '1');
        if (i >= 36) l.setAttribute('stroke', 'var(--theme-accent-2)');
        l.setAttribute('opacity', maj ? '0.95' : '0.45');
        mT.appendChild(l);
        if (maj && i % 8 === 0) {
          const t = document.createElementNS(ns, 'text');
          t.setAttribute('x', (140 + Math.cos(a) * 69).toFixed(1));
          t.setAttribute('y', (124 + Math.sin(a) * 69 + 4).toFixed(1));
          t.textContent = String(i * 5);
          mN.appendChild(t);
        }
      }
    }

    // 4. Honda Civic Type R FL5 — 10-LED Shift Bar + 24 Ascending Racing Segments
    const hLeds = $('hondaFl5Leds'), hBars = $('hondaFl5Bars');
    if (hLeds && hBars) {
      fl5LedEls = [];
      for (let i = 0; i < 10; i++) {
        const r = document.createElementNS(ns, 'rect');
        r.setAttribute('x', (56 + i * 17).toFixed(1));
        r.setAttribute('y', '6');
        r.setAttribute('width', '12');
        r.setAttribute('height', '4.8');
        r.setAttribute('rx', '2');
        r.setAttribute('fill', '#1e293b');
        hLeds.appendChild(r);
        fl5LedEls.push(r);
      }
      fl5BarEls = [];
      for (let i = 0; i < 24; i++) {
        const h = 11 + Math.round((i / 23) * 23);
        const r = document.createElementNS(ns, 'rect');
        r.setAttribute('x', (12 + i * 10.7).toFixed(1));
        r.setAttribute('y', String(49 - h));
        r.setAttribute('width', '8.2');
        r.setAttribute('height', String(h));
        r.setAttribute('rx', '1.6');
        r.setAttribute('fill', 'rgba(255,255,255,0.10)');
        hBars.appendChild(r);
        fl5BarEls.push(r);
      }
    }
  }

  function init(element) {
    root = element || document.querySelector('.elio-speedo');
    if (!root) throw new Error('ElioSpeedo: elemen .elio-speedo tidak ditemukan');
    buildAllTicks();
    setStyle(style);
    render();
    return api;
  }

  function ensureRoot() { if (!root) init(); }

  function applySpeedFx(kmh) {
    const glowEl = root.querySelector('.speedo-glow');
    (glowEl || root).style.setProperty('--speed-ratio', Math.min(1, kmh / 160).toFixed(2));
    const bucket = kmh < 3 ? 0 : Math.min(16, Math.ceil(kmh / 10));
    if (bucket === lastGridBucket) return;
    lastGridBucket = bucket;
    root.style.setProperty('--grid-state', bucket === 0 ? 'paused' : 'running');
    root.style.setProperty('--grid-duration', bucket === 0 ? '2s' : Math.max(0.22, 2.2 - bucket * 0.125).toFixed(2) + 's');
  }

  function render() {
    ensureRoot();
    const v = speed;
    const g = realG !== null ? realG : 0;
    const gAbs = Math.abs(g);
    const gText = gAbs.toFixed(2) + 'G';
    const r240 = Math.min(1, v / 240);
    const rLoad = Math.min(1, Math.max(r240 * 1.12, gAbs / 1.2));
    applySpeedFx(v);

    // 0. Lexus F-Sport HD
    setText('lfaSpeedDisplay', v);
    setAttr('lfaHaloBlade', 'stroke-dashoffset', Math.round(462 * (1 - r240)));
    setAttr('lfaNeedle', 'transform', `rotate(${(-135 + r240 * 270).toFixed(1)} 140 124)`);
    setText('lfaGDisplay', gText);

    // 1. BMW G-Series (iDrive 8 Live Cockpit)
    setText('speedDisplay', v);
    const m3Lh = Math.round(r240 * 160);
    const m3Rh = Math.round(rLoad * 160);
    setAttr('m3LFill', 'y', 204 - m3Lh);
    setAttr('m3LFill', 'height', m3Lh);
    setAttr('m3LCap', 'y', 204 - m3Lh);
    setAttr('m3LCap', 'opacity', m3Lh > 2 ? '0.95' : '0');
    setAttr('m3RFill', 'y', 204 - m3Rh);
    setAttr('m3RFill', 'height', m3Rh);
    setAttr('m3RCap', 'y', 204 - m3Rh);
    setAttr('m3RCap', 'opacity', m3Rh > 2 ? '0.95' : '0');
    setAttr('m3GDot', 'cx', Math.min(160, Math.max(120, Math.round(140 + g * 22))));
    setAttr('m3GDot', 'cy', Math.min(216, Math.max(176, Math.round(196 - r240 * 16))));
    setText('gScoreDisplay', gText);

    // 2. Mazda KODO HD
    setText('mazdaSpeedDisplay', v);
    setAttr('mazdaHaloArc', 'stroke-dashoffset', Math.round(485 * (1 - r240)));
    setAttr('mazdaNeedle', 'transform', `rotate(${(-135 + r240 * 270).toFixed(1)} 140 124)`);
    setText('mazdaGDisplay', gText);

    // 3. Honda Civic Type R FL5 (+R Digital Populer — follows Theme Accent!)
    setText('hondaSpeedDisplay', v);
    setAttr('hondaSpeedBlade', 'width', Math.max(8, Math.round(r240 * 212)));
    setText('hondaGText', gText);
    const ledLit = Math.round(r240 * 10);
    for (let i = 0; i < fl5LedEls.length; i++) {
      fl5LedEls[i].setAttribute('fill', i < ledLit
        ? (i < 5 ? 'var(--theme-accent)' : (i < 8 ? 'var(--theme-accent-2)' : '#ef4444'))
        : '#1e293b');
    }
    const barLit = Math.round(r240 * 24);
    for (let i = 0; i < fl5BarEls.length; i++) {
      fl5BarEls[i].setAttribute('fill', i < barLit
        ? (i < 15 ? 'var(--theme-accent)' : (i < 20 ? 'var(--theme-accent-2)' : '#ef4444'))
        : 'rgba(255,255,255,0.10)');
    }

    // 4. ELIO Neon (Innova Reborn)
    setText('elioSpeedDisplay', v);
    setAttr('elioArcProgress', 'stroke-dashoffset', Math.round(486 * (1 - r240)));
    setText('elioGDisplay', gAbs.toFixed(2) + ' G');

    // 5. BMW M Sport (Innova Reborn)
    setText('bmwMSpeedDisplay', v);
    const bmwLh = Math.round(r240 * 212);
    const bmwRh = Math.round(rLoad * 212);
    setAttr('bmwMLFill', 'y', 226 - bmwLh);
    setAttr('bmwMLFill', 'height', bmwLh);
    setAttr('bmwMLCap', 'y', 226 - bmwLh);
    setAttr('bmwMLCap', 'opacity', bmwLh > 2 ? '0.95' : '0');
    setAttr('bmwMRFill', 'y', 226 - bmwRh);
    setAttr('bmwMRFill', 'height', bmwRh);
    setAttr('bmwMRCap', 'y', 226 - bmwRh);
    setAttr('bmwMRCap', 'opacity', bmwRh > 2 ? '0.95' : '0');
    setText('bmwMGDisplay', gAbs.toFixed(1) + 'G');

    // High-G warning highlight (> 0.65G)
    const highG = gAbs >= 0.65;
    ['lfaGDisplay', 'gScoreDisplay', 'mazdaGDisplay', 'hondaGText', 'elioGDisplay', 'bmwMGDisplay'].forEach((id) => {
      const el = $(id);
      if (el) el.classList.toggle('as-high-g', highG);
    });
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
    style = STYLES.includes(name) ? name : 'elio';
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
    root.querySelectorAll('.as-unit-label').forEach((el) => { el.textContent = label; });
  }

  const api = {
    init, setSpeed, setGForce, setStyle, nextStyle, setGradient, setEco, setUnit,
    setAccent: (hex) => setGradient(hex, hex),
    getSpeed: () => speed,
    getStyle: () => style,
    STYLES
  };

  global.ElioSpeedo = api;
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => { if (!root) init(); });
  } else {
    init();
  }
})(window);
