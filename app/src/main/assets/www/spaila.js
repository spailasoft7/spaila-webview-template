// Spaila bridge: lets web pages ask Android to do things.
(function () {
  function send(fn, args) {
    if (window.SpailaNative) {
      window.SpailaNative.postMessage(JSON.stringify({ fn: fn, args: args }));
    } else {
      console.log("Spaila: not running inside the Android app", fn, args);
    }
  }

  window.Spaila = {
    toast: function (text) {
      send("toast", [String(text)]);
    },
    showNotification: function (title, message) {
      send("showNotification", [String(title), String(message)]);
    }
  };
})();
