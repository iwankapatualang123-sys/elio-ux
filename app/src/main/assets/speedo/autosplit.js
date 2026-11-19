/* AutoSplit: menyesuaikan tata letak template ELIO dengan ukuran area agar tidak ada ruang terbuang.
   Area tinggi: kotak sensor dipanjangkan, isinya direnggangkan dan diperbesar sebisa muat, pod HUD dan
   jam bulat diperbesar lalu ditaruh di tengah. Area lebar: kotak sensor ke tepi. BMW M3: direntangkan
   penuh dengan teks tetap proporsional. Template asli tidak diubah; semua atribut bisa dikembalikan. */
(function () {
  'use strict';
  var BW = 440, BH = 340, TEXT_MAX = 1.3, CENTER_W = 204;
  var touched = [];

  function num(v) { return parseFloat(v) || 0; }
  function tr(t) {
    var m = /translate\(\s*([-\d.]+)[ ,]+([-\d.]+)\s*\)/.exec(t || '');
    return m ? [+m[1], +m[2]] : [0, 0];
  }
  function orig(el, name) {
    var k = 'data-o-' + name;
    if (!el.hasAttribute(k)) {
      var v = el.getAttribute(name);
      el.setAttribute(k, v === null ? '\u0000' : v);
    }
    var o = el.getAttribute(k);
    return o === '\u0000' ? null : o;
  }
  function touch(el, name, value) {
    orig(el, name);
    el.setAttribute(name, value);
    touched.push([el, name]);
  }
  function resetAll() {
    touched.forEach(function (p) {
      var o = orig(p[0], p[1]);
      if (o === null) p[0].removeAttribute(p[1]); else p[0].setAttribute(p[1], o);
    });
    touched = [];
  }
  function scalePoints(points, fy) {
    return points.trim().split(/\s+/).map(function (pt) {
      var xy = pt.split(',');
      return xy[0] + ',' + (num(xy[1]) * fy).toFixed(1);
    }).join(' ');
  }
  function hasBigCircle(g) {
    var cs = g.querySelectorAll('circle');
    for (var i = 0; i < cs.length; i++) if (num(cs[i].getAttribute('r')) >= 80) return true;
    return false;
  }

  var PAD = 6;

  /* Batas kiri/kanan bagian dalam bingkai kotak pada rentang tinggi [y0, y1]. */
  function interior(bg, pts, y0, y1) {
    if (bg.tagName === 'rect') {
      var x = num(bg.getAttribute('x')), w = num(bg.getAttribute('width'));
      return [x + PAD, x + w - PAD];
    }
    var L = -1e9, R = 1e9;
    [y0, (y0 + y1) / 2, y1].forEach(function (y) {
      var xs = [];
      for (var i = 0; i < pts.length; i++) {
        var p = pts[i], q = pts[(i + 1) % pts.length];
        if ((p[1] <= y && q[1] >= y) || (q[1] <= y && p[1] >= y)) {
          xs.push(p[1] === q[1] ? Math.min(p[0], q[0]) : p[0] + (q[0] - p[0]) * (y - p[1]) / (q[1] - p[1]));
          if (p[1] === q[1]) xs.push(Math.max(p[0], q[0]));
        }
      }
      if (xs.length) {
        L = Math.max(L, Math.min.apply(null, xs) + PAD);
        R = Math.min(R, Math.max.apply(null, xs) - PAD);
      }
    });
    return [L, R];
  }

  function isDivider(c) {
    return c.tagName === 'line' && /0\.08\)/.test(c.getAttribute('stroke') || '');
  }

  /* Kotak sensor: dipanjangkan bila area tinggi (fy > 1), lalu setiap tulisan dan garis pemisah
     dipastikan berada di dalam bingkai (digeser/diperkecil bila perlu). Dekorasi tinggi direntangkan. */
  function stretchWing(g, gt, H) {
    var bg = g.querySelector('.elio-wing');
    var bb = bg.getBBox();
    var newH = H - gt[1] - 6;
    var fy = Math.max(1, newH / bb.height);
    var pts = null;
    if (bg.tagName === 'rect') {
      if (fy > 1.01) touch(bg, 'height', newH.toFixed(1));
    } else {
      var sp = scalePoints(orig(bg, 'points'), fy);
      if (fy > 1.01) touch(bg, 'points', sp);
      pts = sp.split(/\s+/).map(function (pt) { var xy = pt.split(','); return [num(xy[0]), num(xy[1])]; });
    }
    Array.prototype.forEach.call(g.children, function (c) {
      if (c === bg) return;
      var cb = c.getBBox();
      var ot = orig(c, 'transform');
      var ct = tr(ot);
      var x = cb.x + ct[0], y = cb.y + ct[1];
      if (y < 2 && cb.height < 3) return; // garis aksen atas tetap di atas
      if ((c.tagName === 'path' || c.tagName === 'line') && cb.height > 150) {
        if (fy > 1.01) touch(c, 'transform', ('scale(1 ' + fy.toFixed(3) + ') ' + (ot || '')).trim());
        return;
      }
      var dy = (y + cb.height / 2) * (fy - 1);
      var fit = c.tagName === 'text' || c.tagName === 'g' || isDivider(c);
      var tf = 'translate(0 ' + dy.toFixed(1) + ')';
      if (fit && cb.width > 0) {
        var t = (c.tagName === 'text' || c.tagName === 'g') ? Math.min(TEXT_MAX, fy) : 1;
        var bottom = y + cb.height + dy;
        var lr = interior(bg, pts || [], bottom - cb.height * t, bottom);
        var shift = Math.max(0, lr[0] - x);
        t = Math.min(t, (lr[1] - x - shift) / cb.width);
        var ax = x, ay = y + cb.height;
        tf = 'translate(' + shift.toFixed(1) + ' ' + dy.toFixed(1) + ')';
        if (Math.abs(t - 1) > 0.01) {
          tf += ' translate(' + ax + ' ' + ay + ') scale(' + t.toFixed(3) + ') translate(' + (-ax) + ' ' + (-ay) + ')';
        }
      }
      if (tf !== 'translate(0 0.0)') touch(c, 'transform', (tf + ' ' + (ot || '')).trim());
    });
  }

  function fitWings(kids, H) {
    kids.forEach(function (e) {
      if (e.tagName !== 'g' || !e.querySelector('.elio-wing')) return;
      var b = e.getBBox();
      if (b.width <= 125 && b.height > 250) stretchWing(e, tr(orig(e, 'transform')), H);
    });
  }

  function layoutTall(svg, kids, H) {
    var pod = null, dial = null, laser = null;
    kids.forEach(function (e) {
      var ot = orig(e, 'transform'), t = tr(ot), b = e.getBBox();
      var by = b.y + t[1];
      if (e.tagName === 'g' && e.querySelector('.elio-wing') && b.width <= 125 && b.height > 250) {
        return;
      } else if (e.tagName === 'g' && hasBigCircle(e)) {
        dial = e;
      } else if (e.tagName === 'line' && e.getAttribute('stroke-dasharray')) {
        laser = e;
      } else if (e.tagName === 'g' && by < 12 && b.height < 100 && b.width < 200) {
        pod = e;
      } else if (e.tagName === 'g' && e.querySelector('.elio-wing') && b.height > 250) {
        var fyB = (H - t[1] - 6) / b.height;
        touch(e, 'transform', 'translate(' + t[0] + ' ' + t[1] + ') scale(1 ' + fyB.toFixed(3) + ')');
      }
    });

    var podBottom = 0;
    if (pod) {
      var pb = pod.getBBox();
      var kp = Math.min(1.25, H / BH, (CENTER_W - 28) / pb.width);  // jarak ~14 ke kotak sensor kiri/kanan
      touch(pod, 'transform', 'translate(220 6) scale(' + kp.toFixed(3) + ') translate(-220 -6) ' + (orig(pod, 'transform') || ''));
      podBottom = 6 + (pb.y + pb.height - 6) * kp;
    }
    if (dial) {
      var dr = dial.getBBox().width / 2;
      var top = podBottom + 14, avail = H - 8 - top;
      var k = Math.min((CENTER_W - 28) / (2 * dr), avail / (2 * dr));  // jarak ~14 ke kotak sensor
      var cy = top + avail / 2;
      touch(dial, 'transform', 'translate(220 ' + cy.toFixed(1) + ') scale(' + k.toFixed(3) + ')');
      if (laser) {
        touch(laser, 'y1', (podBottom + 2).toFixed(1));
        touch(laser, 'y2', (cy - dr * k - 2).toFixed(1));
      }
    }
  }

  function layoutWide(kids, dx) {
    kids.forEach(function (e) {
      var ot = orig(e, 'transform'), t = tr(ot), b = e.getBBox();
      var cx = b.x + t[0] + b.width / 2;
      var shift = cx < 140 ? 0 : (cx > 300 ? dx : dx / 2);
      if (shift) touch(e, 'transform', ('translate(' + shift.toFixed(1) + ' 0) ' + (ot || '')).trim());
    });
  }

  function layoutM3(svg, w, h) {
    svg.setAttribute('preserveAspectRatio', 'none');
    var sx = w / BW, sy = h / BH, s = Math.min(sx, sy);
    Array.prototype.forEach.call(svg.querySelectorAll('text'), function (t) {
      var x = num(t.getAttribute('x')), y = num(t.getAttribute('y'));
      touch(t, 'transform', 'translate(' + x + ' ' + y + ') scale(' + (s / sx).toFixed(4) + ' ' + (s / sy).toFixed(4) +
        ') translate(' + (-x) + ' ' + (-y) + ')');
    });
  }

  /* Angka kecepatan besar (HTML) diperkecil agar 3 digit + satuan muat di dalam bingkai tengah. */
  function fitReadout(box, svg, w, h) {
    var el = box.querySelector('#hondaSpeedDisplay, #speedDisplay');
    if (!el) return;
    var avail, unitW;
    if (box.id === 'cluster_m3') {
      avail = 250 * (w / BW);   // ruang di antara label tangga kiri dan kanan
      unitW = 44;
    } else {
      var vb = svg.viewBox.baseVal;
      avail = 160 * Math.min(w / vb.width, h / vb.height);
      unitW = 0;                // Honda: KM/H di bawah angka
    }
    var px = (0.94 * avail - unitW) / (3 * 0.62);
    px = Math.max(28, Math.min(px, 0.26 * window.innerHeight, 210));
    el.style.setProperty('font-size', px.toFixed(0) + 'px', 'important');
  }

  function layout() {
    if (!window.ElioSpeedo) return;
    resetAll();
    var box = document.getElementById('cluster_' + window.ElioSpeedo.style);
    if (!box) return;
    // Semua 6 tema HD sudah responsif full-press via Flexbox + SVG viewBox
    if (box.classList.contains('as-new-theme')) return;
    var svg = box.querySelector('svg');
    if (!svg) return;
    var w = svg.clientWidth || svg.getBoundingClientRect().width;
    var h = svg.clientHeight || svg.getBoundingClientRect().height;
    if (w < 10 || h < 10) return;
    if (box.id === 'cluster_m3') { layoutM3(svg, w, h); fitReadout(box, svg, w, h); return; }

    var kids = Array.prototype.filter.call(svg.children, function (e) { return e.tagName !== 'defs'; });
    var a = w / h;
    if (a < BW / BH - 0.01) {
      var H = BW / a;
      touch(svg, 'viewBox', '0 0 ' + BW + ' ' + H.toFixed(1));
      layoutTall(svg, kids, H);
      fitWings(kids, H);
    } else {
      if (a > BW / BH + 0.01) {
        var W = BH * a;
        touch(svg, 'viewBox', '0 0 ' + W.toFixed(1) + ' ' + BH);
        layoutWide(kids, W - BW);
      }
      fitWings(kids, BH);
    }
    fitReadout(box, svg, w, h);
  }

  var pending = false;
  function schedule() {
    if (pending) return;
    pending = true;
    requestAnimationFrame(function () { pending = false; layout(); });
  }

  if (window.ElioSpeedo) {
    var baseSetStyle = window.ElioSpeedo.setStyle;
    window.ElioSpeedo.setStyle = function (name) { baseSetStyle(name); schedule(); };
  }
  /* Nilai sensor yang memanjang dicek ulang agar tetap di dalam bingkai. */
  if (window.ElioBridge) {
    var baseLive = window.ElioBridge.live, lastSig = '';
    window.ElioBridge.live = function (d) {
      baseLive(d);
      var sig = Object.keys(d).map(function (k) { return String(d[k]).length; }).join(',');
      if (sig !== lastSig) { lastSig = sig; schedule(); }
    };
  }
  /* Efek warna di area tengah: makin kencang, makin tinggi naiknya (penuh pada 200 km/h). */
  var root = document.querySelector('.elio-speedo');
  var fx = root && root.querySelector('.speedo-fx');
  if (fx && !fx.querySelector('.as-speed-fill')) {
    var fill = document.createElement('div');
    fill.className = 'as-speed-fill';
    fx.appendChild(fill);
  }
  /* Data sensor datang tiap ~250 ms; nilai tampilan dikejar halus per frame layar
     (konstanta waktu ~0.22 s) sehingga jarum, busur, angka, LED, dan efek kecepatan bergerak lembut. */
  if (window.ElioSpeedo && root) {
    var baseSetSpeed = window.ElioSpeedo.setSpeed, baseSetG = window.ElioSpeedo.setGForce;
    var tS = 0, dS = 0, tG = 0, dG = 0, gOn = false, lastTs = 0, animating = false, shownS = -1;
    var fillEl = fx && fx.querySelector('.as-speed-fill');
    var step = function (ts) {
      if (lastTs && ts - lastTs < 30) { requestAnimationFrame(step); return; }
      var dt = lastTs ? Math.min(0.1, (ts - lastTs) / 1000) : 0.033;
      lastTs = ts;
      var k = 1 - Math.exp(-dt / 0.22);
      dS += (tS - dS) * k;
      dG += (tG - dG) * k;
      var doneS = Math.abs(tS - dS) < 0.25, doneG = Math.abs(tG - dG) < 0.004;
      if (doneS) dS = tS;
      if (doneG) dG = tG;
      var r = Math.round(dS);
      if (gOn && !doneG) baseSetG(dG);
      if (r !== shownS || (gOn && doneG)) { shownS = r; baseSetSpeed(r); }
      if (fillEl) fillEl.style.transform = 'translateY(' + ((1 - Math.min(1, dS / 200)) * 100).toFixed(1) + '%)';
      if (doneS && doneG) { animating = false; lastTs = 0; if (gOn) baseSetG(dG); } else requestAnimationFrame(step);
    };
    var kick = function () { if (!animating) { animating = true; requestAnimationFrame(step); } };
    window.ElioSpeedo.setSpeed = function (kmh) { tS = Math.max(0, Math.min(260, Number(kmh) || 0)); kick(); };
    window.ElioSpeedo.setGForce = function (g) {
      if (g === null || g === undefined || isNaN(Number(g))) { gOn = false; baseSetG(g); return; }
      tG = Number(g); gOn = true; kick();
    };
  }
  /* Bingkai berjalan di dalam speedometer: sudut gradasi diperbarui ~20 kali per detik
     hanya saat mode Berjalan aktif. */
  var card = root && root.querySelector('.speedo-card');
  var ring = null;
  if (card) {
    ring = document.createElement('div');
    ring.className = 'as-ring';
    card.appendChild(ring);
  }
  var runGrad = document.getElementById('asRunGrad');
  var frameSec = 9, frameTimer = 0, frameLite = false;
  function frameTick() {
    var on = root && root.classList.contains('as-frame-run');
    if (!on) { clearInterval(frameTimer); frameTimer = 0; return; }
    var a = (performance.now() / 1000 / frameSec * 360) % 360;
    if (ring) ring.style.setProperty('--as-a', a.toFixed(1) + 'deg');
    if (runGrad) runGrad.setAttribute('gradientTransform', 'rotate(' + a.toFixed(1) + ' 220 170)');
  }
  window.AutoSplitFx = {
    setFrameLite: function (on) {
      if (frameLite !== !!on && frameTimer) { clearInterval(frameTimer); frameTimer = 0; }
      frameLite = !!on;
    },
    setFrameSpeed: function (sec) {
      frameSec = Math.max(1, Number(sec) || 9);
      if (!frameTimer && root && root.classList.contains('as-frame-run')) frameTimer = setInterval(frameTick, frameLite ? 100 : 50);
    }
  };
  window.addEventListener('resize', schedule);
  window.AutoSplitLayout = { refresh: schedule };
  schedule();
})();
