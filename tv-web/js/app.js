(function () {
  "use strict";

  var state = {
    tvbox: null,
    xtream: null,
    liveChannels: [],
    currentGroup: null
  };

  var $ = function (id) { return document.getElementById(id); };

  function setStatus(text) {
    $("status").textContent = text || "";
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

  function fetchJson(url, headers) {
    var options = { method: "GET", headers: headers || {} };
    return fetch(url, options).then(function (res) {
      if (!res.ok) throw new Error("HTTP " + res.status);
      return res.text();
    }).then(function (text) {
      return JSON.parse(text.trim());
    });
  }

  function loadTvbox() {
    var url = $("tvbox-url").value.trim();
    if (!url) { setStatus("Informe a URL da interface."); return; }
    setStatus("Carregando interface...");
    fetchJson(url).then(function (config) {
      state.tvbox = config;
      var sites = (config.sites || []).length;
      var lives = (config.lives || []).length;
      setStatus("Interface carregada: " + sites + " fontes, " + lives + " listas de TV.");
      loadTvboxLives(config);
    }).catch(function (err) {
      setStatus("Erro ao carregar interface: " + err.message);
    });
  }

  function loadTvboxLives(config) {
    var lives = config.lives || [];
    var channels = [];
    lives.forEach(function (live) {
      if (live && live.type === 1) {
        (live.channels || []).forEach(function (group) {
          (group.channels || []).forEach(function (ch) {
            channels.push({ name: ch.name, urls: ch.urls || [] });
          });
        });
      } else if (live && live.url) {
        channels.push({ name: live.name || live.url, url: live.url, remote: true });
      }
    });
    setLiveChannels(channels);
  }

  function groupOf(name) {
    var m = String(name).split("|");
    if (m.length > 1) return m[0].trim();
    var parts = String(name).split(" - ");
    if (parts.length > 1) return parts[0].trim();
    return "Geral";
  }

  function setLiveChannels(channels) {
    state.liveChannels = channels;
    var groups = {};
    channels.forEach(function (ch) {
      var g = groupOf(ch.name);
      (groups[g] = groups[g] || []).push(ch);
    });
    var sidebar = $("live-groups");
    sidebar.innerHTML = "";
    Object.keys(groups).sort().forEach(function (g) {
      var btn = document.createElement("button");
      btn.textContent = g + " (" + groups[g].length + ")";
      btn.addEventListener("click", function () {
        renderLiveGroup(g, groups[g]);
        Array.prototype.forEach.call(sidebar.querySelectorAll("button"), function (b) {
          b.classList.remove("active");
        });
        btn.classList.add("active");
      });
      sidebar.appendChild(btn);
    });
    var first = Object.keys(groups).sort()[0];
    if (first) renderLiveGroup(first, groups[first]);
  }

  function renderLiveGroup(name, channels) {
    var list = $("live-list");
    list.innerHTML = "";
    channels.forEach(function (ch) {
      var item = document.createElement("div");
      item.className = "list-item";
      item.textContent = ch.name;
      item.tabIndex = 0;
      item.addEventListener("click", function () { playChannel(ch); });
      list.appendChild(item);
    });
  }

  function playChannel(channel) {
    if (channel.remote) {
      loadRemoteM3u(channel);
      return;
    }
    play(channel.urls[0]);
  }

  function loadRemoteM3u(entry) {
    setStatus("Baixando lista...");
    fetch(entry.url).then(function (res) { return res.text(); }).then(function (text) {
      var channels = parseM3u(text);
      if (!channels.length) throw new Error("nenhum canal encontrado");
      setStatus(channels.length + " canais carregados de " + (entry.name || "lista"));
      setLiveChannels(channels);
      showTab("live");
    }).catch(function (err) {
      setStatus("Erro ao baixar lista: " + err.message);
    });
  }

  function parseM3u(text) {
    var channels = [];
    var pending = null;
    text.split("\n").forEach(function (raw) {
      var line = raw.trim();
      if (line.indexOf("#EXTINF") === 0) {
        pending = { name: line.split(",").pop().trim(), urls: [] };
      } else if (!line || line.charAt(0) === "#") {
        return;
      } else if (pending) {
        pending.urls.push(line);
        channels.push(pending);
        pending = null;
      }
    });
    return channels;
  }

  function play(url) {
    var video = $("player");
    var overlay = $("player-overlay");
    overlay.classList.remove("hidden");
    if (window.Hls && window.Hls.isSupported() && /\.m3u8($|\?)/i.test(url)) {
      if (state.hls) state.hls.destroy();
      state.hls = new window.Hls({ lowLatencyMode: true, enableWorker: true });
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

  function connectXtream() {
    var host = $("x-host").value.trim().replace(/\/+$/, "");
    var user = encodeURIComponent($("x-user").value.trim());
    var pass = encodeURIComponent($("x-pass").value.trim());
    if (!host || !user || !pass) { setStatus("Preencha host, usuário e senha."); return; }
    var base = host.indexOf("http") === 0 ? host : "http://" + host;
    var api = base + "/player_api.php?username=" + user + "&password=" + pass;
    setStatus("Conectando ao portal...");
    fetchJson(api).then(function (info) {
      if (!info || !info.user_info) throw new Error("resposta inválida do portal");
      state.xtream = { base: base, user: user, pass: pass, info: info };
      var status = info.user_info.status || "?";
      setStatus("Conectado. Status: " + status + " | " + (info.server_info && info.server_info.url ? "servidor " + info.server_info.url : ""));
      loadXtreamLive(base, user, pass);
    }).catch(function (err) {
      setStatus("Erro no portal: " + err.message);
    });
  }

  function xtreamUrl(base, user, pass, action, extra) {
    var url = base + "/player_api.php?username=" + user + "&password=" + pass + "&action=" + action;
    if (extra) url += "&" + extra;
    return url;
  }

  function loadXtreamLive(base, user, pass) {
    fetchJson(xtreamUrl(base, user, pass, "get_live_streams")).then(function (streams) {
      var channels = (streams || []).map(function (s) {
        return {
          name: s.name || ("Stream " + s.stream_id),
          urls: [base + "/live/" + user + "/" + pass + "/" + s.stream_id + ".ts"],
          raw: s
        };
      });
      setStatus(channels.length + " canais carregados.");
      setLiveChannels(channels);
      showTab("live");
    }).catch(function (err) {
      setStatus("Erro ao carregar canais: " + err.message);
    });
  }

  $("load-tvbox").addEventListener("click", loadTvbox);
  $("load-xtream").addEventListener("click", connectXtream);

  window.ArchPlayer = {
    showTab: showTab,
    play: play,
    state: state
  };
})();
