/* Equalizer latar AutoSplit. Mode: 'off' | 'bars' | 'wave' (dipilih di pengaturan aplikasi).
   Mode batang memakai model segmen pada gaya Honda. Data: ElioEq.setBands([0..1, ...]) dari Visualizer Android. */
(function () {
  'use strict';
  var root = document.querySelector('.elio-speedo');
  var fx = root.querySelector('.speedo-fx');
  var cv = document.createElement('canvas');
  cv.className = 'as-eq';
  fx.insertBefore(cv, fx.querySelector('.speedo-vignette'));
  var ctx = cv.getContext('2d');
  var bands = [];
  var mode = 'off';
  var lite = false;

  function css(name) { return getComputedStyle(root).getPropertyValue(name).trim(); }

  function size() {
    var r = cv.getBoundingClientRect(), d = 1;
    cv.width = Math.max(1, Math.round(r.width * d));
    cv.height = Math.max(1, Math.round(r.height * d));
  }

  function grad(h, a) {
    var g = ctx.createLinearGradient(0, cv.height, 0, cv.height - h);
    g.addColorStop(0, 'rgba(' + css('--theme-accent-rgb') + ',' + a + ')');
    g.addColorStop(1, 'rgba(' + css('--theme-accent-2-rgb') + ',' + (a * 0.9) + ')');
    return g;
  }

  function drawBars(segmented) {
    var n = bands.length, W = cv.width, H = cv.height, maxH = H * 0.55;
    var gap = W * 0.006, bw = (W - gap * (n + 1)) / n;
    ctx.fillStyle = grad(maxH, 0.55);
    for (var i = 0; i < n; i++) {
      var h = Math.max(2, bands[i] * maxH), x = gap + i * (bw + gap);
      if (segmented) {
        var seg = Math.max(4, H * 0.014), sg = seg * 0.45;
        for (var y = 0; y < h; y += seg + sg) ctx.fillRect(x, H - y - seg, bw, seg);
      } else {
        var r = Math.min(bw / 2, 6);
        ctx.beginPath();
        ctx.moveTo(x, H);
        ctx.lineTo(x, H - h + r);
        ctx.quadraticCurveTo(x, H - h, x + r, H - h);
        ctx.lineTo(x + bw - r, H - h);
        ctx.quadraticCurveTo(x + bw, H - h, x + bw, H - h + r);
        ctx.lineTo(x + bw, H);
        ctx.fill();
      }
    }
  }

  function drawWave() {
    var n = bands.length, W = cv.width, H = cv.height, maxH = H * 0.42;
    var layers = lite ? [[1.0, 0.5, 0]] : [[1.0, 0.5, 0], [0.7, 0.3, 0.33], [0.45, 0.18, 0.66]];
    layers.forEach(function (L) {
      var amp = L[0], alpha = L[1], ph = L[2];
      var pts = [];
      for (var i = 0; i < n; i++) {
        var j = Math.floor((i + ph * n) % n);
        pts.push([W * i / (n - 1), H - H * 0.08 - bands[j] * maxH * amp]);
      }
      ctx.beginPath();
      ctx.moveTo(0, H);
      ctx.lineTo(pts[0][0], pts[0][1]);
      for (var k = 1; k < n; k++) {
        var mx = (pts[k - 1][0] + pts[k][0]) / 2, my = (pts[k - 1][1] + pts[k][1]) / 2;
        ctx.quadraticCurveTo(pts[k - 1][0], pts[k - 1][1], mx, my);
      }
      ctx.lineTo(W, pts[n - 1][1]);
      ctx.lineTo(W, H);
      ctx.closePath();
      ctx.fillStyle = grad(maxH, alpha * 0.55);
      ctx.fill();
      ctx.lineWidth = Math.max(2, H * 0.004);
      ctx.strokeStyle = 'rgba(' + css('--theme-accent-2-rgb') + ',' + Math.min(1, alpha + 0.35) + ')';
      ctx.stroke();
    });
  }

  function draw() {
    if (cv.width !== Math.round(cv.getBoundingClientRect().width)) size();
    ctx.clearRect(0, 0, cv.width, cv.height);
    if (!bands.length || mode === 'off') return;
    var st = window.ElioSpeedo ? ElioSpeedo.style : 'lfa';
    if (mode === 'wave') drawWave(); else drawBars(st === 'honda');
  }

  var target = [], lastRecv = 0, animating = false, lastDraw = 0;
  function step(ts) {
    if (ts - lastDraw < (lite ? 64 : 30)) { requestAnimationFrame(step); return; }
    lastDraw = ts;
    if (performance.now() - lastRecv > 500) target = [];
    var active = false;
    for (var i = 0; i < bands.length; i++) {
      var t = target[i] || 0, d = bands[i];
      d += (t - d) * (t > d ? 0.9 : 0.12);
      if (d < 0.003) d = 0;
      bands[i] = d;
      if (d > 0 || t > 0.01) active = true;
    }
    draw();
    if (active && mode !== 'off') requestAnimationFrame(step); else animating = false;
  }

  window.ElioEq = {
    setMode: function (m) { mode = m; if (m === 'off') { bands = []; target = []; } draw(); },
    setLite: function (on) { if (lite !== !!on) { lite = !!on; bands = []; target = []; draw(); } },
    setBands: function (b) {
      if (mode === 'off') return;
      if (lite) {
        var h = [];
        for (var j = 0; j + 1 < b.length; j += 2) h.push((b[j] + b[j + 1]) / 2);
        b = h;
      }
      target = b.slice();
      lastRecv = performance.now();
      while (bands.length < b.length) bands.push(0);
      bands.length = b.length;
      for (var i = 0; i < b.length; i++) if (b[i] > bands[i]) bands[i] = b[i];
      draw();
      if (!animating) { animating = true; requestAnimationFrame(step); }
    },
    redraw: draw
  };
  window.addEventListener('resize', function () { size(); draw(); });
  size();
})();
