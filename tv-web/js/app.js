(function () {
  "use strict";

  var state = {
    xtream: null,
    tvbox: null,
    hls: null
  };

  var $ = function (id) { return document.getElementById(id); };

  /* ---------------- storage ---------------- */
  function loadCreds() {
    try {
      var raw = localStorage.getItem("arch.creds");
      return raw ? JSON.parse(raw) : null;
    } catch (e) { return null; }
  }
  function saveCreds(creds) {
    try { localStorage.setItem("arch.creds", JSON.stringify(creds)); } catch (e) {}
  }
  function clearCreds() {
    try { localStorage.removeItem("arch.creds"); } catch (e) {}
  }

  /* ---------------- http ---------------- */
  function httpGetText(url, cb) {
    var xhr = new XMLHttpRequest();
    xhr.open("GET", url, true);
    xhr.timeout = 30000;
    xhr.onload = function () {
      if (xhr.status >= 200 && xhr.status < 300) cb(null, xhr.responseText);
      else cb(new Error("HTTP " + xhr.status));
    };
    xhr.onerror = function () { cb(new Error("falha de rede")); };
    xhr.ontimeout = function () { cb(new Error("tempo esgotado")); };
    xhr.send();
  }
  function httpGetJson(url, cb) {
    httpGetText(url, function (err, text) {
      if (err) { cb(err); return; }
      try { cb(null, JSON.parse(text.trim())); } catch (e) { cb(new Error("resposta invalida")); }
    });
  }
  function setStatus(text) { var el = $("status"); if (el) el.textContent = text || ""; }
  function setLoginStatus(text) { $("login-status").textContent = text || ""; }

  /* ---------------- screens ---------------- */
  function showLogin() {
    $("login-screen").classList.remove("hidden");
    $("splash").classList.add("hidden");
    $("app").classList.add("hidden");
  }
  function showSplash() {
    $("login-screen").classList.add("hidden");
    $("splash").classList.remove("hidden");
    $("app").classList.add("hidden");
  }
  function showApp() {
    $("login-screen").classList.add("hidden");
    $("splash").classList.add("hidden");
    $("app").classList.remove("hidden");
  }

  function showTab(name) {
    ["live", "vod", "series", "config"].forEach(function (tab) {
      $("tab-" + tab).classList.toggle("hidden", tab !== name);
    });
    Array.prototype.forEach.call(document.querySelectorAll(".tab"), function (el) {
      el.classList.toggle("active", el.getAttribute("data-tab") === name);
    });
  }
  Array.prototype.forEach.call(document.querySelectorAll(".tab"), function (el) {
    el.addEventListener("click", function () { showTab(el.getAttribute("data-tab")); });
  });

  /* ---------------- xtream ---------------- */
  function xtreamApi(base, user, pass, action, extra) {
    var url = base + "/player_api.php?username=" + user + "&password=" + pass;
    if (action) url += "&action=" + action;
    if (extra) url += "&" + extra;
    return url;
  }

  function connect(host, user, pass, cb) {
    var base = host.trim().replace(/\/+$/, "");
    if (base.indexOf("http") !== 0) base = "http://" + base;
    var encUser = encodeURIComponent(user.trim());
    var encPass = encodeURIComponent(pass.trim());
    httpGetJson(xtreamApi(base, encUser, encPass, null), function (err, info) {
      if (err) { cb(err); return; }
      if (!info || !info.user_info) { cb(new Error("portal nao respondeu: verifique host, usuario e senha")); return; }
      state.xtream = { base: base, user: encUser, pass: encPass, info: info };
      cb(null, info);
    });
  }

  function onConnected() {
    saveCreds({
      host: $("x-host").value,
      user: $("x-user").value,
      pass: $("x-pass").value
    });
    showApp();
    renderAccount();
    loadLiveCategories();
    loadVodCategories();
    loadSeriesCategories();
  }

  function renderAccount() {
    var info = state.xtream && state.xtream.info && state.xtream.info.user_info;
    if (!info) { $("account-info").textContent = "Sem informacoes da conta."; return; }
    var lines = ["Usuario: " + (info.username || "?"), "Status: " + (info.status || "?")];
    if (info.exp_date) {
      var d = new Date(parseInt(info.exp_date, 10) * 1000);
      lines.push("Expira em: " + d.toLocaleDateString());
    }
    if (info.max_connections) lines.push("Conexoes: " + (info.active_cons || "0") + "/" + info.max_connections);
    $("account-info").textContent = lines.join(" | ");
  }

  function doLogin() {
    var host = $("x-host").value;
    var user = $("x-user").value;
    var pass = $("x-pass").value;
    if (!host || !user || !pass) { setLoginStatus("Preencha servidor, usuario e senha."); return; }
    setLoginStatus("Conectando...");
    $("btn-login").disabled = true;
    connect(host, user, pass, function (err, info) {
      $("btn-login").disabled = false;
      if (err) { setLoginStatus("Erro: " + err.message); return; }
      setLoginStatus("");
      showApp();
      renderAccount();
      loadLiveCategories();
      loadVodCategories();
      loadSeriesCategories();
    });
  }

  /* ---------------- live ---------------- */
  function loadLiveCategories() {
    var x = state.xtream;
    $("live-list").innerHTML = "";
    $("live-groups").innerHTML = "<p class='muted'>Carregando...</p>";
    httpGetJson(xtreamApi(x.base, x.user, x.pass, "get_live_categories"), function (err, cats) {
      if (err) { $("live-groups").innerHTML = ""; setStatus("Erro categorias: " + err.message); return; }
      var holder = $("live-groups");
      holder.innerHTML = "";
      (cats || []).forEach(function (cat) {
        var btn = document.createElement("button");
        btn.textContent = cat.category_name || "?";
        btn.addEventListener("click", function () {
          Array.prototype.forEach.call(holder.querySelectorAll("button"), function (b) { b.classList.remove("active"); });
          btn.classList.add("active");
          loadLiveChannels(cat.category_id);
        });
        holder.appendChild(btn);
      });
      if ((cats || []).length > 0) loadLiveChannels(cats[0].category_id);
    });
  }

  function loadLiveChannels(categoryId) {
    var x = state.xtream;
    var list = $("live-list");
    list.innerHTML = "<p class='muted'>Carregando canais...</p>";
    httpGetJson(xtreamApi(x.base, x.user, x.pass, "get_live_streams", "category_id=" + categoryId), function (err, streams) {
      if (err) { list.innerHTML = ""; setStatus("Erro canais: " + err.message); return; }
      list.innerHTML = "";
      (streams || []).forEach(function (s) {
        var name = s.name || ("Canal " + s.stream_id);
        var url = x.base + "/live/" + x.user + "/" + x.pass + "/" + s.stream_id + ".m3u8";
        var item = document.createElement("div");
        item.className = "list-item";
        item.tabIndex = 0;
        item.textContent = (s.num ? s.num + ". " : "") + name;
        if (s.epg_channel_id) {
          var sub = document.createElement("span");
          sub.className = "muted";
          sub.textContent = " EPG: " + s.epg_channel_id;
          item.appendChild(sub);
        }
        item.addEventListener("click", function () { play(url); });
        list.appendChild(item);
      });
      setStatus((streams || []).length + " canais na categoria.");
    });
  }

  /* ---------------- vod ---------------- */
  function loadVodCategories() {
    var x = state.xtream;
    httpGetJson(xtreamApi(x.base, x.user, x.pass, "get_vod_categories"), function (err, cats) {
      if (err) { setStatus("Erro filmes: " + err.message); return; }
      var holder = $("vod-categories");
      holder.innerHTML = "";
      (cats || []).forEach(function (cat) {
        var btn = document.createElement("button");
        btn.className = "chip";
        btn.textContent = cat.category_name || "?";
        btn.addEventListener("click", function () {
          Array.prototype.forEach.call(holder.querySelectorAll(".chip"), function (b) { b.classList.remove("active"); });
          btn.classList.add("active");
          loadVodStreams(cat.category_id);
        });
        holder.appendChild(btn);
      });
      if ((cats || []).length > 0) loadVodStreams(cats[0].category_id);
    });
  }

  function loadVodStreams(categoryId) {
    var x = state.xtream;
    var grid = $("vod-list");
    grid.innerHTML = "<p class='muted'>Carregando filmes...</p>";
    httpGetJson(xtreamApi(x.base, x.user, x.pass, "get_vod_streams", "category_id=" + categoryId), function (err, streams) {
      if (err) { grid.innerHTML = ""; setStatus("Erro: " + err.message); return; }
      grid.innerHTML = "";
      (streams || []).forEach(function (s) {
        var card = document.createElement("div");
        card.className = "card";
        card.tabIndex = 0;
        var img = document.createElement("img");
        img.src = s.stream_icon || "";
        img.onerror = function () { img.style.visibility = "hidden"; };
        card.appendChild(img);
        var cap = document.createElement("p");
        cap.textContent = s.name || "";
        card.appendChild(cap);
        card.addEventListener("click", function () {
          var url = x.base + "/movie/" + x.user + "/" + x.pass + "/" + s.stream_id + (s.container_extension ? "." + s.container_extension : "");
          setStatus("Abrindo: " + (s.name || ""));
          play(url);
        });
        grid.appendChild(card);
      });
      setStatus((streams || []).length + " filmes na categoria.");
    });
  }

  /* ---------------- series ---------------- */
  function loadSeriesCategories() {
    var x = state.xtream;
    httpGetJson(xtreamApi(x.base, x.user, x.pass, "get_series_categories"), function (err, cats) {
      if (err) { setStatus("Erro series: " + err.message); return; }
      var holder = $("series-categories");
      holder.innerHTML = "";
      (cats || []).forEach(function (cat) {
        var btn = document.createElement("button");
        btn.className = "chip";
        btn.textContent = cat.category_name || "?";
        btn.addEventListener("click", function () {
          Array.prototype.forEach.call(holder.querySelectorAll(".chip"), function (b) { b.classList.remove("active"); });
          btn.classList.add("active");
          loadSeriesList(cat.category_id);
        });
        holder.appendChild(btn);
      });
      if ((cats || []).length > 0) loadSeriesList(cats[0].category_id);
    });
  }

  function loadSeriesList(categoryId) {
    var x = state.xtream;
    var list = $("series-list");
    var eps = $("series-episodes");
    eps.classList.add("hidden");
    list.innerHTML = "<p class='muted'>Carregando series...</p>";
    httpGetJson(xtreamApi(x.base, x.user, x.pass, "get_series", "category_id=" + categoryId), function (err, series) {
      if (err) { list.innerHTML = ""; setStatus("Erro: " + err.message); return; }
      list.innerHTML = "";
      (series || []).forEach(function (s) {
        var item = document.createElement("div");
        item.className = "list-item";
        item.tabIndex = 0;
        item.textContent = s.name || "";
        item.addEventListener("click", function () { loadEpisodes(s); });
        list.appendChild(item);
      });
      setStatus((series || []).length + " series na categoria.");
    });
  }

  function loadEpisodes(seriesEntry) {
    var x = state.xtream;
    var eps = $("series-episodes");
    eps.innerHTML = "<p class='muted'>Carregando episodios...</p>";
    eps.classList.remove("hidden");
    httpGetJson(xtreamApi(x.base, x.user, x.pass, "get_series_info", "series_id=" + seriesEntry.series_id), function (err, info) {
      if (err) { eps.innerHTML = "Erro: " + err.message; return; }
      eps.innerHTML = "";
      info.episodes = info.episodes || {};
      Object.keys(info.episodes).sort(function (a, b) { return a - b; }).forEach(function (season) {
        var head = document.createElement("p");
        head.className = "season";
        head.textContent = "Temporada " + season;
        eps.appendChild(head);
        (info.episodes[season] || []).forEach(function (ep) {
          var item = document.createElement("div");
          item.className = "list-item";
          item.tabIndex = 0;
          item.textContent = "E" + (ep.episode_num || "?") + " - " + (ep.title || "Episodio");
          item.addEventListener("click", function () {
            var url = x.base + "/series/" + x.user + "/" + x.pass + "/" + ep.id + (ep.container_extension ? "." + ep.container_extension : "");
            play(url);
          });
          eps.appendChild(item);
        });
      });
      if (!eps.childElementCount) eps.innerHTML = "Nenhum episodio.";
    });
  }

  /* ---------------- tvbox ---------------- */
  function loadTvbox() {
    var url = $("tvbox-url").value.trim();
    if (!url) { setStatus("Informe a URL da interface."); return; }
    setStatus("Carregando interface...");
    httpGetText(url, function (err, text) {
      if (err) { setStatus("Erro ao carregar interface: " + err.message); return; }
      try {
        state.tvbox = JSON.parse(text.trim());
        setStatus("Interface carregada: " + (state.tvbox.sites || []).length + " fontes, " + (state.tvbox.lives || []).length + " lives.");
        document.getElementById("tvbox-url").blur();
      } catch (e) {
        setStatus("JSON invalido.");
      }
    });
  }

  /* ---------------- player ---------------- */
  function play(url) {
    var video = $("player");
    var overlay = $("player-overlay");
    overlay.classList.remove("hidden");
    if (window.Hls && window.Hls.isSupported() && /\.m3u8($|\?)/i.test(url)) {
      if (state.hls) state.hls.destroy();
      state.hls = new window.Hls();
      state.hls.loadSource(url);
      state.hls.attachMedia(video);
    } else {
      video.src = url;
    }
    video.play().catch(function () {});
  }

  function closePlayer() {
    var video = $("player");
    if (state.hls) { state.hls.destroy(); state.hls = null; }
    video.pause();
    video.src = "";
    $("player-overlay").classList.add("hidden");
  }

  $("close-player").addEventListener("click", closePlayer);
  $("btn-login").addEventListener("click", doLogin);
  $("btn-logout").addEventListener("click", function () {
    clearCreds();
    state.xtream = null;
    showLogin();
  });
  $("load-tvbox").addEventListener("click", loadTvbox);

  /* ---------------- boot ---------------- */
  var saved = loadCreds();
  if (saved && saved.host && saved.user && saved.pass) {
    showSplash();
    $("x-host").value = saved.host;
    $("x-user").value = saved.user;
    $("x-pass").value = saved.pass;
    connect(saved.host, saved.user, saved.pass, function (err) {
      if (err) { showLogin(); setLoginStatus("Sessao expirada. Entre novamente."); return; }
      showApp();
      renderAccount();
      loadLiveCategories();
      loadVodCategories();
      loadSeriesCategories();
    });
  } else {
    showLogin();
  }

  window.ArchPlayer = {
    showTab: showTab,
    play: play,
    state: state
  };
})();
