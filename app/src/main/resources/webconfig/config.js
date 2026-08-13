(function () {
  'use strict';
  var token = document.querySelector('meta[name="portal-token"]').content;
  var demo = token === '%TOKEN%';
  var fields = ['ha_url', 'ha_token', 'broker_host', 'broker_port', 'username', 'password', 'device_name'];
  var steps = [
    { chapter: 'Maison', title: 'Connectez Home Assistant', description: 'Indiquez l’adresse de votre maison et son jeton d’accès.' },
    { chapter: 'Contrôle', title: 'Configurez MQTT', description: 'Ajoutez le serveur utilisé pour piloter le panneau à distance.' },
    { chapter: 'Accueil', title: 'Choisissez vos pilules', description: 'Gardez uniquement les informations utiles au quotidien.' },
    { chapter: 'Terminé', title: 'Vérifiez la configuration', description: 'Tout est prêt. Confirmez pour appliquer les modifications.' }
  ];
  var sample = { ha_url: 'http://homeassistant.local:8123', ha_token: 'demo-token', broker_host: '192.168.1.10', broker_port: 1883, username: 'portal', password: '', device_name: 'Panneau salon' };
  var current = 0;
  var pillsLoaded = false;
  var saving = false;

  function el(id) { return document.getElementById(id); }
  function url(path) { return path + '?t=' + encodeURIComponent(token); }
  function say(id, message, kind) { var node = el(id); node.textContent = message; node.className = 'status ' + (kind || ''); }
  function fill(data) { fields.forEach(function (field) { el(field).value = data[field] === undefined ? '' : data[field]; }); }
  function setHidden(node, hidden) { node.classList.toggle('hidden', hidden); }

  function buildProgress() {
    var host = el('progress-dots');
    steps.forEach(function (_, index) { var dot = document.createElement('span'); dot.className = 'progress-dot'; dot.dataset.index = index; host.appendChild(dot); });
  }

  function showStep(index) {
    current = index;
    document.querySelectorAll('.wizard-step').forEach(function (node) { setHidden(node, Number(node.dataset.step) !== current); });
    document.querySelectorAll('.progress-dot').forEach(function (node, dotIndex) { node.classList.toggle('active', dotIndex === current); node.classList.toggle('done', dotIndex < current); });
    el('step-chapter').textContent = steps[current].chapter;
    el('step-title').textContent = steps[current].title;
    el('step-description').textContent = steps[current].description;
    el('progress-label').textContent = steps[current].chapter + ' · étape ' + (current + 1) + ' sur ' + steps.length;
    setHidden(el('back'), current === 0);
    el('next').textContent = current === steps.length - 1 ? 'Enregistrer' : 'Continuer';
    if (current === 3) updateSummary();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function validCurrentStep() {
    if (current === 0) {
      var validUrl = /^https?:\/\/.+/.test(el('ha_url').value.trim());
      var validToken = el('ha_token').value.trim().length > 0;
      setHidden(el('ha_url_error'), validUrl); setHidden(el('ha_token_error'), validToken);
      if (!validUrl) el('ha_url').focus(); else if (!validToken) el('ha_token').focus();
      return validUrl && validToken;
    }
    if (current === 1) {
      var port = Number(el('broker_port').value);
      var validMqtt = el('broker_host').value.trim().length > 0 && port >= 1 && port <= 65535;
      setHidden(el('mqtt_error'), validMqtt); if (!validMqtt) el('broker_host').focus();
      return validMqtt;
    }
    return true;
  }

  function updateSummary() {
    el('summary-ha').textContent = el('ha_url').value.replace(/^https?:\/\//, '');
    el('summary-mqtt').textContent = el('broker_host').value + ':' + el('broker_port').value;
    el('summary-device').textContent = el('device_name').value || 'Portal';
    if (pillsLoaded) el('summary-pills').textContent = document.querySelectorAll('#pills input:checked').length.toString();
  }

  function configBody() {
    var body = {}; fields.forEach(function (field) { body[field] = el(field).value; }); body.broker_port = parseInt(body.broker_port, 10) || 0; return body;
  }

  function saveAll() {
    if (saving) return;
    if (demo) { say('config_status', 'Aperçu local — aucune modification envoyée', 'ok'); el('next').textContent = 'Enregistré'; return; }
    saving = true; el('next').disabled = true; el('next').textContent = 'Enregistrement…';
    fetch(url('/api/config'), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(configBody()) })
      .then(function (response) { return response.json(); })
      .then(function (result) { if (!result.ok) throw new Error(); return pillsLoaded ? savePills() : null; })
      .then(function () { say('config_status', 'Configuration enregistrée sur le panneau', 'ok'); el('next').textContent = 'Enregistré'; })
      .catch(function () { saving = false; el('next').disabled = false; el('next').textContent = 'Réessayer'; say('config_status', 'L’enregistrement a échoué. Vérifiez la connexion.', 'err'); });
  }

  function savePills() {
    var body = Array.prototype.map.call(document.querySelectorAll('#pills input'), function (box) { return { entity_id: box.dataset.entity, enabled: box.checked }; });
    return fetch(url('/api/pills'), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(function (response) { return response.json(); }).then(function (result) { if (!result.ok) throw new Error(); });
  }

  function renderPills(items) {
    var host = el('pills'); host.innerHTML = ''; pillsLoaded = true; setHidden(host, false); setHidden(el('pills-empty'), true);
    items.forEach(function (item) {
      var row = document.createElement('label'), text = document.createElement('span'), name = document.createElement('strong'), id = document.createElement('small'), box = document.createElement('input');
      row.className = 'pill-row'; name.textContent = item.label; id.textContent = item.entity_id; box.type = 'checkbox'; box.checked = item.enabled; box.dataset.entity = item.entity_id;
      text.appendChild(name); text.appendChild(id); row.appendChild(text); row.appendChild(box); host.appendChild(row);
    });
    say('pills_status', items.length + ' appareils compatibles', 'ok');
  }

  el('next').addEventListener('click', function () { if (!validCurrentStep()) return; if (current < steps.length - 1) showStep(current + 1); else saveAll(); });
  el('back').addEventListener('click', function () { if (current > 0 && !saving) showStep(current - 1); });
  el('toggle-ha-token').addEventListener('click', function () { var input = el('ha_token'); var visible = input.type === 'text'; input.type = visible ? 'password' : 'text'; this.textContent = visible ? 'Afficher le jeton' : 'Masquer le jeton'; });
  el('load_pills').addEventListener('click', function () {
    this.disabled = true; this.textContent = 'Chargement…';
    if (demo) { renderPills([{ entity_id: 'light.salon', label: 'Salon', enabled: true }, { entity_id: 'climate.maison', label: 'Thermostat', enabled: false }]); return; }
    fetch(url('/api/entities')).then(function (response) { return response.json(); }).then(function (result) { if (!result.ok) throw new Error(); renderPills(result.items); }).catch(function () { el('load_pills').disabled = false; el('load_pills').textContent = 'Réessayer'; say('pills_status', 'Home Assistant est injoignable', 'err'); });
  });

  buildProgress(); showStep(0);
  if (demo) fill(sample); else fetch(url('/api/config')).then(function (response) { return response.json(); }).then(fill).catch(function () { say('config_status', 'Le panneau est injoignable', 'err'); });
})();
