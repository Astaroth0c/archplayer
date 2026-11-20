(function () {
  "use strict";

  function closeOverlay() {
    var overlay = document.getElementById("player-overlay");
    var video = document.getElementById("player");
    if (window.ArchPlayer && window.ArchPlayer.state && window.ArchPlayer.state.hls) {
      window.ArchPlayer.state.hls.destroy();
      window.ArchPlayer.state.hls = null;
    }
    video.pause();
    video.src = "";
    overlay.classList.add("hidden");
  }

  function handleKey(event) {
    var overlay = document.getElementById("player-overlay");
    if (!overlay.classList.contains("hidden")) {
      if (event.keyCode === 10009 || event.key === "Escape" || event.key === "Backspace") {
        closeOverlay();
        event.preventDefault();
      }
      return;
    }
    if (event.keyCode === 10009 || event.key === "Escape") {
      var config = document.getElementById("tab-config");
      if (config.classList.contains("hidden")) {
        window.ArchPlayer.showTab("config");
        event.preventDefault();
      }
    }
  }

  window.addEventListener("keydown", handleKey);

  if (window.tizen) {
    try {
      window.tizen.tvinputdevice.registerKeyBatch(["MediaPlay", "MediaPause", "MediaStop"]);
    } catch (err) {
      /* registerKeyBatch unavailable */
    }
  }

  window.ArchPlayer = window.ArchPlayer || {};
})();
